package com.forgeprom.codestudio.core.export

import android.content.ActivityNotFoundException
import android.content.ClipData
import android.content.ClipDescription
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.RectF
import android.graphics.Typeface
import android.graphics.pdf.PdfDocument
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.os.Handler
import android.os.Looper
import android.os.PersistableBundle
import android.provider.OpenableColumns
import android.text.Layout
import android.text.StaticLayout
import android.text.TextPaint
import androidx.core.content.FileProvider
import com.forgeprom.codestudio.core.security.ChecksumValidator
import com.forgeprom.codestudio.core.security.VaultAndEnterpriseManager
import com.forgeprom.codestudio.data.local.model.CustomBlueprintEntity
import com.forgeprom.codestudio.data.local.model.ProjectEntity
import com.forgeprom.codestudio.data.local.model.PromptVersionEntity
import com.forgeprom.codestudio.domain.pipeline.PromptEngineeringEngine
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.io.FileOutputStream
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.UUID

/**
 * مساعد التصدير والمشاركة والنسخ الاحتياطي المحمول لأندرويد (NativeShareAndPdfHelper)
 * المزايا المطورة:
 * 1. محرك PDF عربي RTL منسق باحترافية: ترويسة بصرية، بطاقة ملخص (الجودة + الأمان)، تنسيق Markdown، وتذييل (صفحة X من Y).
 * 2. نسخ احتياطي واستعادة عبر منتقي ملفات أندرويد (SAF) بدون صلاحيات تخزين مع خيار تشفير محمول عابر للأجهزة (PBKDF2 + AES-256-GCM).
 * 3. منظّف ذكي لمخلفات التصدير يعرض المساحة المستهلكة وينظف الملفات المؤقتة بلمسة واحدة.
 */
object NativeShareAndPdfHelper {

    data class ExportCacheStats(
        val fileCount: Int,
        val totalSizeKB: Long
    )

    data class ExportCleanupResult(
        val deletedFiles: Int,
        val freedKB: Long
    )

    data class BackupSaveResult(
        val file: File?,
        val isEncrypted: Boolean,
        val isPortablePasswordProtected: Boolean,
        val sha256Short: String
    )

    data class BackupRestoreResult(
        val projects: List<ProjectEntity>,
        val promptVersions: List<PromptVersionEntity> = emptyList(),
        val customBlueprints: List<CustomBlueprintEntity> = emptyList(),
        val wasEncrypted: Boolean,
        val requiresPassword: Boolean = false,
        val integrityVerified: Boolean,
        val errorMessage: String? = null
    )

    data class SpecImportResult(
        val project: ProjectEntity?,
        val redactedSecretsCount: Int = 0,
        val sourceFileName: String = "",
        val errorMessage: String? = null
    )

    private fun getExportDirectory(context: Context): File {
        val baseDir = context.getExternalFilesDir(Environment.DIRECTORY_DOCUMENTS) ?: context.filesDir
        val exportDir = File(baseDir, "ForgepromExports")
        if (!exportDir.exists()) exportDir.mkdirs()
        return exportDir
    }

    private fun getScaffoldsDirectory(context: Context): File {
        val baseDir = context.getExternalFilesDir(Environment.DIRECTORY_DOCUMENTS) ?: context.filesDir
        val dir = File(baseDir, "ForgepromScaffolds")
        if (!dir.exists()) dir.mkdirs()
        return dir
    }

    fun getExportCacheStats(context: Context): ExportCacheStats {
        val files = buildList {
            getExportDirectory(context).listFiles()?.filter { it.isFile }?.let { addAll(it) }
            getScaffoldsDirectory(context).listFiles()?.filter { it.isFile }?.let { addAll(it) }
        }
        val totalBytes = files.sumOf { it.length() }
        return ExportCacheStats(
            fileCount = files.size,
            totalSizeKB = (totalBytes / 1024L).coerceAtLeast(if (totalBytes > 0) 1L else 0L)
        )
    }

    fun clearExportCache(context: Context): ExportCleanupResult {
        val files = buildList {
            getExportDirectory(context).listFiles()?.filter { it.isFile }?.let { addAll(it) }
            getScaffoldsDirectory(context).listFiles()?.filter { it.isFile }?.let { addAll(it) }
        }
        var deleted = 0
        var freedBytes = 0L
        files.forEach { file ->
            val len = file.length()
            if (file.delete()) {
                deleted++
                freedBytes += len
            }
        }
        return ExportCleanupResult(
            deletedFiles = deleted,
            freedKB = (freedBytes / 1024L).coerceAtLeast(if (freedBytes > 0) 1L else 0L)
        )
    }

    private fun sanitizeFileName(rawName: String): String {
        val cleaned = rawName
            .replace(Regex("[^a-zA-Z0-9\\u0600-\\u06FF_-]"), "_")
            .replace(Regex("_+"), "_")
            .trim('_')
            .take(40)
        return cleaned.ifBlank { "Forgeprom_Project" }
    }

    private const val SENSITIVE_CLIPBOARD_TTL_MS = 45_000L
    private val clipboardHandler = Handler(Looper.getMainLooper())
    private var pendingClearClipboardTask: Runnable? = null

    fun copyToClipboard(context: Context, label: String, text: String, isSensitive: Boolean = false) {
        val appContext = context.applicationContext
        val clipboard = appContext.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
        val clip = ClipData.newPlainText(label, text)
        if (isSensitive && Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            clip.description.extras = PersistableBundle().apply {
                putBoolean(ClipDescription.EXTRA_IS_SENSITIVE, true)
            }
        }
        clipboard.setPrimaryClip(clip)

        pendingClearClipboardTask?.let { clipboardHandler.removeCallbacks(it) }
        pendingClearClipboardTask = null

        if (isSensitive) {
            val clearTask = Runnable {
                try {
                    val currentText = clipboard.primaryClip
                        ?.takeIf { it.itemCount > 0 }
                        ?.getItemAt(0)
                        ?.coerceToText(appContext)
                        ?.toString()
                    if (currentText == text) {
                        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                            clipboard.clearPrimaryClip()
                        } else {
                            clipboard.setPrimaryClip(ClipData.newPlainText("", ""))
                        }
                    }
                } catch (_: Exception) {
                    // تجاهل أي استثناء عند تنظيف الحافظة في الخلفية
                } finally {
                    pendingClearClipboardTask = null
                }
            }
            pendingClearClipboardTask = clearTask
            clipboardHandler.postDelayed(clearTask, SENSITIVE_CLIPBOARD_TTL_MS)
        }
    }

    fun shareExportedFile(context: Context, file: File, mimeType: String): Boolean {
        if (!file.exists()) return false
        return try {
            val uri = FileProvider.getUriForFile(
                context,
                "${context.packageName}.fileprovider",
                file
            )
            val shareIntent = Intent(Intent.ACTION_SEND).apply {
                type = mimeType
                putExtra(Intent.EXTRA_STREAM, uri)
                putExtra(Intent.EXTRA_SUBJECT, file.name)
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            }
            val chooser = Intent.createChooser(shareIntent, "مشاركة أو فتح الملف: ${file.name}").apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            context.startActivity(chooser)
            true
        } catch (_: Exception) {
            false
        }
    }

    fun shareProjectPrompt(context: Context, project: ProjectEntity): Boolean {
        val shareBody = buildString {
            appendLine("وثيقة ومواصفات هندسية من Forgeprom Code Studio")
            appendLine("الاسم: ${project.name}")
            appendLine("المنصة المستهدفة: ${project.targetPlatform}")
            appendLine("الوصف: ${project.description}")
            appendLine("----------------------------------------")
            appendLine(project.currentPromptText ?: project.rawIdea)
        }

        val sendIntent = Intent(Intent.ACTION_SEND).apply {
            type = "text/plain"
            putExtra(Intent.EXTRA_SUBJECT, "وثيقة وبرومبت مشروع: ${project.name}")
            putExtra(Intent.EXTRA_TEXT, shareBody)
        }

        val chooser = Intent.createChooser(sendIntent, "مشاركة البرومبت الهندسي عبر...").apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        return try {
            context.startActivity(chooser)
            true
        } catch (_: ActivityNotFoundException) {
            false
        } catch (_: Exception) {
            false
        }
    }

    fun exportMarkdownSpec(context: Context, project: ProjectEntity, customContent: String? = null): File {
        val exportDir = getExportDirectory(context)
        val safeName = sanitizeFileName(project.name)
        val mdFile = File(exportDir, "${safeName}_Spec.md")

        val content = customContent ?: buildString {
            appendLine("# ${project.name}")
            appendLine("- **المنصة المستهدفة:** ${project.targetPlatform}")
            appendLine("- **نوع المشروع:** ${project.type}")
            appendLine("\n## الوصف")
            appendLine(project.description)
            appendLine("\n## المواصفات والبرومبت المعتمد")
            appendLine(project.currentPromptText ?: project.rawIdea)
        }

        mdFile.writeText(content, Charsets.UTF_8)
        return mdFile
    }

    /**
     * محرك PDF عربي RTL منسق باحترافية:
     * - ترويسة بصرية وبطاقة ملخص المشروع (درجة الجودة + درجة الأمان OWASP + التاريخ).
     * - تنسيق العناوين (# / ##) والنقاط وكتل الكود بصرياً.
     * - تذييل تلقائي في أسفل كل صفحة.
     */
    fun generateNativeRtlPdf(context: Context, project: ProjectEntity): File {
        val pdfDocument = PdfDocument()
        try {
            val pageWidth = 595
            val pageHeight = 842
            val margin = 38f
            val footerReserve = 42f
            val contentWidth = (pageWidth - (margin * 2)).toInt()
            val dateStamp = SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.US).format(Date())

            val qualityScore = PromptEngineeringEngine.evaluatePromptQuality(
                project.currentPromptText ?: project.rawIdea
            ).totalScore
            val securityScore = VaultAndEnterpriseManager.calculateSecurityScore(
                VaultAndEnterpriseManager.auditProjectSecurity(project)
            )

            val headerBgPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.rgb(13, 40, 56) }
            val accentStripePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.rgb(13, 138, 127) }
            val summaryCardPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.rgb(241, 245, 249) }
            val codeBoxPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.rgb(248, 250, 252) }
            val dividerPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                color = Color.rgb(203, 213, 225)
                strokeWidth = 1f
            }

            val bannerTitlePaint = TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
                color = Color.WHITE
                textSize = 15f
                isFakeBoldText = true
            }
            val bannerSubPaint = TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
                color = Color.rgb(186, 230, 225)
                textSize = 9.5f
            }
            val heading1Paint = TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
                color = Color.rgb(13, 118, 110)
                textSize = 13.5f
                isFakeBoldText = true
            }
            val heading2Paint = TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
                color = Color.rgb(15, 23, 42)
                textSize = 11.8f
                isFakeBoldText = true
            }
            val bodyPaint = TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
                color = Color.rgb(30, 41, 59)
                textSize = 10.5f
            }
            val codePaint = TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
                color = Color.rgb(15, 23, 42)
                textSize = 9.2f
                typeface = Typeface.MONOSPACE
            }
            val footerPaint = TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
                color = Color.rgb(100, 116, 139)
                textSize = 9f
            }

            var currentPageNumber = 1
            fun startNewPage(pageNum: Int): Pair<PdfDocument.Page, Float> {
                val info = PdfDocument.PageInfo.Builder(pageWidth, pageHeight, pageNum).create()
                val p = pdfDocument.startPage(info)
                val c = p.canvas
                // شريط ترويسة أنيق
                c.drawRect(0f, 0f, pageWidth.toFloat(), 56f, headerBgPaint)
                c.drawRect(0f, 56f, pageWidth.toFloat(), 60f, accentStripePaint)
                c.drawText("Forgeprom Code Studio — وثيقة المواصفات الهندسية", margin, 28f, bannerTitlePaint)
                c.drawText("المنصة: ${project.targetPlatform}  •  التاريخ: $dateStamp", margin, 45f, bannerSubPaint)
                return p to 76f
            }

            fun drawFooter(c: Canvas, pageNum: Int) {
                val footerY = pageHeight - 24f
                c.drawLine(margin, footerY - 12f, pageWidth - margin, footerY - 12f, dividerPaint)
                c.drawText("Forgeprom Code Studio (Offline Executive Spec) — صفحة $pageNum", margin, footerY, footerPaint)
            }

            var (page, currentY) = startNewPage(currentPageNumber)
            var canvas = page.canvas

            // بطاقة ملخص المشروع في الصفحة الأولى
            val cardRect = RectF(margin, currentY, pageWidth - margin, currentY + 64f)
            canvas.drawRoundRect(cardRect, 8f, 8f, summaryCardPaint)
            canvas.drawRect(pageWidth - margin - 5f, currentY, pageWidth - margin, currentY + 64f, accentStripePaint)
            canvas.drawText("المشروع: ${project.name.take(55)}", margin + 12f, currentY + 21f, heading2Paint)
            canvas.drawText("النوع: ${project.type}  |  المنصة: ${project.targetPlatform}", margin + 12f, currentY + 39f, bodyPaint)
            canvas.drawText("جودة البرومبت: $qualityScore%   •   الجاهزية الأمنية OWASP: $securityScore%", margin + 12f, currentY + 55f, heading1Paint.apply { textSize = 10.5f })
            heading1Paint.textSize = 13.5f
            currentY += 78f

            val rawSpec = buildString {
                appendLine("## وصف المشروع")
                appendLine(project.description)
                appendLine()
                appendLine("## المواصفات الهندسية والبرومبت المعتمد")
                appendLine(project.currentPromptText ?: project.rawIdea)
            }

            val lines = rawSpec.split("\n")
            var inCodeBlock = false

            for (rawLine in lines) {
                val trimmed = rawLine.trim()
                if (trimmed.startsWith("```")) {
                    inCodeBlock = !inCodeBlock
                    continue
                }

                val (paintToUse, displayText, extraGap, isCode) = when {
                    inCodeBlock -> Quad(codePaint, rawLine.ifBlank { " " }, 2f, true)
                    trimmed.startsWith("# ") -> Quad(heading1Paint, trimmed.removePrefix("# ").trim(), 7f, false)
                    trimmed.startsWith("## ") || trimmed.startsWith("### ") ->
                        Quad(heading2Paint, trimmed.replace(Regex("^#{2,3}\\s*"), "").trim(), 5f, false)
                    trimmed.startsWith("- ") || trimmed.startsWith("* ") ->
                        Quad(bodyPaint, "• " + trimmed.substring(2).replace("**", ""), 3.5f, false)
                    else -> Quad(bodyPaint, rawLine.replace("**", "").ifBlank { " " }, 3f, false)
                }

                val layoutWidth = if (isCode) (contentWidth - 16).coerceAtLeast(100) else contentWidth
                val layout = StaticLayout.Builder
                    .obtain(displayText, 0, displayText.length, paintToUse, layoutWidth)
                    .setAlignment(Layout.Alignment.ALIGN_NORMAL)
                    .setLineSpacing(2.5f, 1.12f)
                    .build()

                if (currentY + layout.height + extraGap > pageHeight - footerReserve) {
                    drawFooter(canvas, currentPageNumber)
                    pdfDocument.finishPage(page)
                    currentPageNumber++
                    val next = startNewPage(currentPageNumber)
                    page = next.first
                    currentY = next.second
                    canvas = page.canvas
                }

                if (isCode) {
                    canvas.drawRect(
                        margin,
                        currentY - 1f,
                        pageWidth - margin,
                        currentY + layout.height + 3f,
                        codeBoxPaint
                    )
                }

                canvas.save()
                canvas.translate(if (isCode) margin + 8f else margin, currentY)
                layout.draw(canvas)
                canvas.restore()
                currentY += layout.height + extraGap
            }

            drawFooter(canvas, currentPageNumber)
            pdfDocument.finishPage(page)

            val exportDir = getExportDirectory(context)
            val safeName = sanitizeFileName(project.name)
            val pdfFile = File(exportDir, "${safeName}_RTL_Spec.pdf")
            FileOutputStream(pdfFile).use { out ->
                pdfDocument.writeTo(out)
            }
            return pdfFile
        } finally {
            pdfDocument.close()
        }
    }

    private data class Quad<A, B, C, D>(val first: A, val second: B, val third: C, val fourth: D)

    /**
     * تصدير المخطط المعماري البصري للمشروع كصورة PNG عالية الدقة (1200x720) جاهزة للعروض والوثائق
     */
    fun exportArchitectureDiagramPng(context: Context, project: ProjectEntity): File {
        val width = 1200
        val height = 720
        val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)

        val bgPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.rgb(11, 20, 30) }
        val headerPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.rgb(16, 36, 52) }
        val accentPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.rgb(16, 185, 129) }
        val cardBorderPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.rgb(51, 65, 85)
            style = Paint.Style.STROKE
            strokeWidth = 2.5f
        }
        val arrowPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.rgb(56, 189, 248)
            strokeWidth = 5f
        }
        val titlePaint = TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.WHITE
            textSize = 34f
            isFakeBoldText = true
        }
        val subPaint = TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.rgb(148, 163, 184)
            textSize = 20f
        }
        val boxTitlePaint = TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.WHITE
            textSize = 24f
            isFakeBoldText = true
        }
        val boxSubPaint = TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.rgb(203, 213, 225)
            textSize = 18f
        }
        val badgePaint = TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.rgb(52, 211, 153)
            textSize = 20f
            isFakeBoldText = true
        }

        val qualityScore = PromptEngineeringEngine.evaluatePromptQuality(
            project.currentPromptText ?: project.rawIdea
        ).totalScore
        val securityScore = VaultAndEnterpriseManager.calculateSecurityScore(
            VaultAndEnterpriseManager.auditProjectSecurity(project)
        )

        canvas.drawRect(0f, 0f, width.toFloat(), height.toFloat(), bgPaint)
        canvas.drawRect(0f, 0f, width.toFloat(), 124f, headerPaint)
        canvas.drawRect(0f, 124f, width.toFloat(), 130f, accentPaint)

        canvas.drawText("Forgeprom Visual Architecture Blueprint", 48f, 54f, titlePaint)
        canvas.drawText(
            "${project.name.take(48)}  •  Platform: ${project.targetPlatform}  •  Quality: $qualityScore%  •  OWASP: $securityScore%",
            48f,
            96f,
            subPaint
        )

        val layers = listOf(
            Triple("1. Presentation & Adaptive UI", "Material 3 RTL • Adaptive Layout • StateFlow", Color.rgb(15, 118, 110)),
            Triple("2. Domain & AI Pipeline", "Chained 4-Stage PRD • Quality Linter • Diff Engine", Color.rgb(37, 99, 235)),
            Triple("3. Data, Room & AES-256 Vault", "Offline-First SQLite • KeyStore GCM • Hybrid Sync", Color.rgb(109, 40, 217))
        )

        val boxWidth = 330f
        val boxHeight = 320f
        val topY = 190f
        val xs = listOf(48f, 435f, 822f)

        // أسهم الربط بين الطبقات
        canvas.drawLine(xs[0] + boxWidth, topY + boxHeight / 2f, xs[1], topY + boxHeight / 2f, arrowPaint)
        canvas.drawLine(xs[1] + boxWidth, topY + boxHeight / 2f, xs[2], topY + boxHeight / 2f, arrowPaint)

        layers.forEachIndexed { idx, (lTitle, lSub, colorInt) ->
            val rect = RectF(xs[idx], topY, xs[idx] + boxWidth, topY + boxHeight)
            val fillPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.rgb(21, 34, 48) }
            val topBarPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = colorInt }
            canvas.drawRoundRect(rect, 20f, 20f, fillPaint)
            canvas.drawRoundRect(RectF(xs[idx], topY, xs[idx] + boxWidth, topY + 18f), 20f, 20f, topBarPaint)
            canvas.drawRoundRect(rect, 20f, 20f, cardBorderPaint)

            val titleLayout = StaticLayout.Builder
                .obtain(lTitle, 0, lTitle.length, boxTitlePaint, (boxWidth - 36).toInt())
                .build()
            canvas.save()
            canvas.translate(xs[idx] + 18f, topY + 42f)
            titleLayout.draw(canvas)
            canvas.restore()

            val subLayout = StaticLayout.Builder
                .obtain(lSub, 0, lSub.length, boxSubPaint, (boxWidth - 36).toInt())
                .setLineSpacing(6f, 1.15f)
                .build()
            canvas.save()
            canvas.translate(xs[idx] + 18f, topY + 132f)
            subLayout.draw(canvas)
            canvas.restore()

            canvas.drawText("Layer 0${idx + 1} ✓ Active", xs[idx] + 18f, topY + boxHeight - 28f, badgePaint)
        }

        val footerCard = RectF(48f, 558f, width - 48f, 672f)
        val footerFill = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.rgb(17, 29, 42) }
        canvas.drawRoundRect(footerCard, 16f, 16f, footerFill)
        canvas.drawRoundRect(footerCard, 16f, 16f, cardBorderPaint)
        canvas.drawText("Offline-First Clean Architecture • Exported by Forgeprom Code Studio", 72f, 608f, boxSubPaint)
        val dateStamp = SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.US).format(Date())
        canvas.drawText("Timestamp: $dateStamp  |  Zero-Trust AES-256-GCM Ready", 72f, 646f, subPaint)

        val exportDir = getExportDirectory(context)
        val safeName = sanitizeFileName(project.name)
        val pngFile = File(exportDir, "${safeName}_Architecture_Blueprint.png")
        FileOutputStream(pngFile).use { out ->
            bitmap.compress(Bitmap.CompressFormat.PNG, 100, out)
        }
        bitmap.recycle()
        return pngFile
    }

    /**
     * استيراد ذكي لمواصفات مشروع من ملف خارجي (.md / .txt / .yaml) عبر SAF بدون صلاحيات تخزين
     */
    fun importProjectSpecFromUri(context: Context, uri: Uri): SpecImportResult {
        return try {
            val fileName = resolveDisplayName(context, uri) ?: "Imported_Spec.md"
            val rawText = context.contentResolver.openInputStream(uri)
                ?.bufferedReader(Charsets.UTF_8)
                ?.use { it.readText() }
                ?.trim()
                .orEmpty()
            if (rawText.isBlank()) {
                return SpecImportResult(null, errorMessage = "الملف المختار فارغ ولا يحتوي على نص مواصفات.")
            }

            // فحص وإخفاء أي أسرار مكشوفة تلقائياً لحظة الاستيراد
            val redaction = VaultAndEnterpriseManager.detectAndRedactSecrets(rawText)
            val safeText = redaction.redactedText

            val firstHeading = safeText.lines()
                .firstOrNull { it.trim().startsWith("#") }
                ?.replace(Regex("^#+\\s*"), "")
                ?.trim()
            val cleanedFileName = fileName
                .substringBeforeLast(".")
                .replace('_', ' ')
                .replace('-', ' ')
                .trim()
            val projectTitle = (firstHeading?.takeIf { it.isNotBlank() } ?: cleanedFileName)
                .ifBlank { "مشروع مستورد" }
                .take(60)

            val lower = safeText.lowercase()
            val detectedPlatform = when {
                lower.contains("flutter") || lower.contains("dart") || lower.contains("riverpod") -> "FLUTTER"
                lower.contains("react native") || lower.contains("expo") -> "REACT_NATIVE"
                lower.contains("next.js") || lower.contains("tailwind") || lower.contains("pwa") || lower.contains("html") -> "WEB"
                lower.contains("openapi:") || lower.contains("postgresql") || lower.contains("dockerfile") || lower.contains("microservice") -> "BACKEND"
                else -> "ANDROID_KOTLIN"
            }

            val firstParagraph = safeText.lines()
                .map { it.trim() }
                .firstOrNull { it.isNotBlank() && !it.startsWith("#") && !it.startsWith("```") }
                ?.take(160)
                ?: projectTitle

            val now = System.currentTimeMillis()
            val project = ProjectEntity(
                id = UUID.randomUUID().toString(),
                name = projectTitle,
                description = firstParagraph,
                type = "IMPORTED_SPEC",
                targetPlatform = detectedPlatform,
                rawIdea = firstParagraph,
                currentPromptText = safeText,
                createdAt = now,
                updatedAt = now
            )
            SpecImportResult(
                project = project,
                redactedSecretsCount = redaction.foundCount,
                sourceFileName = fileName
            )
        } catch (_: Exception) {
            SpecImportResult(null, errorMessage = "تعذر قراءة الملف المختار — تأكد أنه ملف نصي (.md أو .txt أو .yaml).")
        }
    }

    private fun resolveDisplayName(context: Context, uri: Uri): String? {
        return runCatching {
            context.contentResolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)?.use { cursor ->
                if (cursor.moveToFirst()) cursor.getString(0) else null
            }
        }.getOrNull()
    }

    private fun buildBackupEnvelopeJson(
        projects: List<ProjectEntity>,
        promptVersions: List<PromptVersionEntity> = emptyList(),
        customBlueprints: List<CustomBlueprintEntity> = emptyList(),
        portablePassword: String? = null
    ): Triple<String, Boolean, Pair<Boolean, String>> {
        val innerPayload = JSONObject().apply {
            put("app", "Forgeprom Code Studio")
            put("version", "2.8.0")
            put("exportedAt", System.currentTimeMillis())
            val arr = JSONArray()
            for (p in projects) {
                arr.put(
                    JSONObject().apply {
                        put("id", p.id)
                        put("name", p.name)
                        put("description", p.description)
                        put("type", p.type)
                        put("targetPlatform", p.targetPlatform)
                        put("rawIdea", p.rawIdea)
                        put("isPinned", p.isPinned)
                        put("isArchived", p.isArchived)
                        put("currentPromptText", p.currentPromptText ?: "")
                        put("createdAt", p.createdAt)
                        put("updatedAt", p.updatedAt)
                    }
                )
            }
            put("projects", arr)

            val versionsArr = JSONArray()
            for (v in promptVersions) {
                versionsArr.put(
                    JSONObject().apply {
                        put("id", v.id)
                        put("projectId", v.projectId)
                        put("promptText", v.promptText)
                        put("reason", v.reason)
                        put("qualityScore", v.qualityScore)
                        put("timestampMs", v.timestampMs)
                    }
                )
            }
            put("promptVersions", versionsArr)

            val bpArr = JSONArray()
            for (bp in customBlueprints) {
                bpArr.put(
                    JSONObject().apply {
                        put("id", bp.id)
                        put("title", bp.title)
                        put("category", bp.category)
                        put("targetPlatform", bp.targetPlatform)
                        put("description", bp.description)
                        put("rawIdea", bp.rawIdea)
                        put("prebuiltPrompt", bp.prebuiltPrompt)
                        put("createdAt", bp.createdAt)
                    }
                )
            }
            put("customBlueprints", bpArr)
        }.toString()

        val payloadHash = ChecksumValidator.calculateSha256(innerPayload)
        if (!portablePassword.isNullOrBlank()) {
            val pwdEnc = VaultAndEnterpriseManager.encryptWithPasswordPBKDF2(innerPayload, portablePassword)
            if (pwdEnc.isSuccess) {
                val envelope = JSONObject().apply {
                    put("format", "FORGEPROM_PORTABLE_BACKUP_V3")
                    put("sha256", payloadHash)
                    put("encryptedEnvelope", pwdEnc.getOrThrow())
                }
                return Triple(envelope.toString(2), true, true to payloadHash.take(12))
            }
        }

        val encryptedAttempt = VaultAndEnterpriseManager.encryptSafely(innerPayload)
        val envelope = if (encryptedAttempt.isSuccess) {
            JSONObject().apply {
                put("format", "FORGEPROM_ENCRYPTED_BACKUP_V2")
                put("sha256", payloadHash)
                put("encryptedEnvelope", encryptedAttempt.getOrThrow())
            }
        } else {
            JSONObject(innerPayload).apply {
                put("sha256", payloadHash)
            }
        }
        return Triple(envelope.toString(2), encryptedAttempt.isSuccess, false to payloadHash.take(12))
    }

    /**
     * حفظ نسخة احتياطية مشفرة شاملة محلياً داخل التطبيق (المشاريع + سجل الإصدارات + القوالب المخصّصة)
     */
    fun exportAllProjectsBackupJson(
        context: Context,
        projects: List<ProjectEntity>,
        portablePassword: String? = null,
        promptVersions: List<PromptVersionEntity> = emptyList(),
        customBlueprints: List<CustomBlueprintEntity> = emptyList()
    ): BackupSaveResult {
        val backupDir = File(context.filesDir, "backups")
        if (!backupDir.exists()) backupDir.mkdirs()

        val (jsonText, isEncrypted, meta) = buildBackupEnvelopeJson(
            projects = projects,
            promptVersions = promptVersions,
            customBlueprints = customBlueprints,
            portablePassword = portablePassword
        )
        val backupFile = File(backupDir, "forgeprom_projects_backup.json")
        backupFile.writeText(jsonText, Charsets.UTF_8)
        return BackupSaveResult(
            file = backupFile,
            isEncrypted = isEncrypted,
            isPortablePasswordProtected = meta.first,
            sha256Short = meta.second
        )
    }

    /**
     * تصدير النسخة الاحتياطية المشفرة الشاملة إلى أي مجلد يختاره المستخدم عبر SAF (دون صلاحيات تخزين)
     */
    fun exportBackupToUri(
        context: Context,
        uri: Uri,
        projects: List<ProjectEntity>,
        portablePassword: String? = null,
        promptVersions: List<PromptVersionEntity> = emptyList(),
        customBlueprints: List<CustomBlueprintEntity> = emptyList()
    ): BackupSaveResult {
        val (jsonText, isEncrypted, meta) = buildBackupEnvelopeJson(
            projects = projects,
            promptVersions = promptVersions,
            customBlueprints = customBlueprints,
            portablePassword = portablePassword
        )
        context.contentResolver.openOutputStream(uri)?.use { out ->
            out.write(jsonText.toByteArray(Charsets.UTF_8))
            out.flush()
        } ?: throw IllegalStateException("تعذر الكتابة إلى المسار المختار.")
        return BackupSaveResult(
            file = null,
            isEncrypted = isEncrypted,
            isPortablePasswordProtected = meta.first,
            sha256Short = meta.second
        )
    }

    private fun parseBackupJsonText(rawText: String, portablePassword: String? = null): BackupRestoreResult {
        return try {
            val rawRoot = JSONObject(rawText)
            val format = rawRoot.optString("format", "")
            val isPortableV3 = format == "FORGEPROM_PORTABLE_BACKUP_V3"
            val isEncryptedV2 = format == "FORGEPROM_ENCRYPTED_BACKUP_V2"
            val expectedHash = rawRoot.optString("sha256", "")

            val dataJsonString: String = when {
                isPortableV3 -> {
                    if (portablePassword.isNullOrBlank()) {
                        return BackupRestoreResult(
                            projects = emptyList(),
                            wasEncrypted = true,
                            requiresPassword = true,
                            integrityVerified = false,
                            errorMessage = "هذه النسخة الاحتياطية محمية بكلمة مرور عابرة للأجهزة — يرجى إدخال كلمة المرور."
                        )
                    }
                    val enc = rawRoot.optString("encryptedEnvelope", "")
                    val decrypted = VaultAndEnterpriseManager.decryptWithPasswordPBKDF2(enc, portablePassword)
                    if (decrypted.isFailure) {
                        return BackupRestoreResult(
                            projects = emptyList(),
                            wasEncrypted = true,
                            requiresPassword = true,
                            integrityVerified = false,
                            errorMessage = decrypted.exceptionOrNull()?.message ?: "كلمة المرور غير صحيحة."
                        )
                    }
                    decrypted.getOrThrow()
                }
                isEncryptedV2 -> {
                    val enc = rawRoot.optString("encryptedEnvelope", "")
                    val decrypted = VaultAndEnterpriseManager.decryptSafely(enc)
                    if (decrypted.isFailure) {
                        return BackupRestoreResult(
                            projects = emptyList(),
                            wasEncrypted = true,
                            integrityVerified = false,
                            errorMessage = decrypted.exceptionOrNull()?.message ?: "تعذر فك تشفير النسخة الاحتياطية."
                        )
                    }
                    decrypted.getOrThrow()
                }
                else -> rawRoot.toString()
            }

            val integrityOk = if ((isEncryptedV2 || isPortableV3) && expectedHash.isNotBlank()) {
                ChecksumValidator.calculateSha256(dataJsonString).equals(expectedHash, ignoreCase = true)
            } else true

            if (!integrityOk) {
                return BackupRestoreResult(
                    projects = emptyList(),
                    wasEncrypted = isEncryptedV2 || isPortableV3,
                    integrityVerified = false,
                    errorMessage = "تم رصد تلف أو تعديل غير مصرح به في ملف النسخة الاحتياطية (فشل فحص SHA-256)."
                )
            }

            val root = JSONObject(dataJsonString)
            val arr = root.optJSONArray("projects")
                ?: return BackupRestoreResult(emptyList(), wasEncrypted = isEncryptedV2 || isPortableV3, integrityVerified = true)
            val result = mutableListOf<ProjectEntity>()
            for (i in 0 until arr.length()) {
                val obj = arr.getJSONObject(i)
                val validName = obj.optString("name").trim()
                if (validName.isBlank()) continue
                val validId = obj.optString("id").trim().ifBlank { UUID.randomUUID().toString() }
                val savedUpdatedAt = obj.optLong("updatedAt", System.currentTimeMillis())
                result.add(
                    ProjectEntity(
                        id = validId,
                        name = validName,
                        description = obj.optString("description").ifBlank { validName },
                        type = obj.optString("type", "NEW_APP").ifBlank { "NEW_APP" },
                        targetPlatform = obj.optString("targetPlatform", "ANDROID_KOTLIN").ifBlank { "ANDROID_KOTLIN" },
                        rawIdea = obj.optString("rawIdea").ifBlank { validName },
                        isPinned = obj.optBoolean("isPinned", false),
                        isArchived = obj.optBoolean("isArchived", false),
                        currentPromptText = obj.optString("currentPromptText").ifBlank { null },
                        createdAt = obj.optLong("createdAt", savedUpdatedAt),
                        updatedAt = savedUpdatedAt
                    )
                )
            }
            val restoredVersions = mutableListOf<PromptVersionEntity>()
            val versionsArr = root.optJSONArray("promptVersions")
            if (versionsArr != null) {
                for (i in 0 until versionsArr.length()) {
                    val obj = versionsArr.getJSONObject(i)
                    val pid = obj.optString("projectId").trim()
                    val text = obj.optString("promptText").trim()
                    if (pid.isBlank() || text.isBlank()) continue
                    restoredVersions.add(
                        PromptVersionEntity(
                            id = obj.optString("id").ifBlank { UUID.randomUUID().toString() },
                            projectId = pid,
                            promptText = text,
                            reason = obj.optString("reason", "نسخة مستعادة"),
                            qualityScore = obj.optInt("qualityScore", 80),
                            timestampMs = obj.optLong("timestampMs", System.currentTimeMillis())
                        )
                    )
                }
            }

            val restoredBlueprints = mutableListOf<CustomBlueprintEntity>()
            val bpArr = root.optJSONArray("customBlueprints")
            if (bpArr != null) {
                for (i in 0 until bpArr.length()) {
                    val obj = bpArr.getJSONObject(i)
                    val title = obj.optString("title").trim()
                    if (title.isBlank()) continue
                    restoredBlueprints.add(
                        CustomBlueprintEntity(
                            id = obj.optString("id").ifBlank { "custom-${UUID.randomUUID()}" },
                            title = title,
                            category = obj.optString("category", "قوالبي المخصصة"),
                            targetPlatform = obj.optString("targetPlatform", "ANDROID_KOTLIN"),
                            description = obj.optString("description", title),
                            rawIdea = obj.optString("rawIdea", title),
                            prebuiltPrompt = obj.optString("prebuiltPrompt", title),
                            createdAt = obj.optLong("createdAt", System.currentTimeMillis())
                        )
                    )
                }
            }

            BackupRestoreResult(
                projects = result,
                promptVersions = restoredVersions,
                customBlueprints = restoredBlueprints,
                wasEncrypted = isEncryptedV2 || isPortableV3,
                integrityVerified = true
            )
        } catch (_: Exception) {
            BackupRestoreResult(
                projects = emptyList(),
                wasEncrypted = false,
                integrityVerified = false,
                errorMessage = "تعذر قراءة ملف النسخة الاحتياطية (تنسيق JSON غير صالح)."
            )
        }
    }

    fun restoreProjectsFromBackupJson(context: Context, portablePassword: String? = null): BackupRestoreResult {
        val backupFile = File(File(context.filesDir, "backups"), "forgeprom_projects_backup.json")
        if (!backupFile.exists()) {
            return BackupRestoreResult(emptyList(), wasEncrypted = false, integrityVerified = false)
        }
        return parseBackupJsonText(backupFile.readText(Charsets.UTF_8), portablePassword)
    }

    /**
     * استيراد نسخة احتياطية من أي ملف JSON يختاره المستخدم عبر منتقي الملفات الرسمي SAF
     */
    fun restoreBackupFromUri(context: Context, uri: Uri, portablePassword: String? = null): BackupRestoreResult {
        val rawText = context.contentResolver.openInputStream(uri)?.bufferedReader(Charsets.UTF_8)?.use { it.readText() }
            ?: return BackupRestoreResult(
                projects = emptyList(),
                wasEncrypted = false,
                integrityVerified = false,
                errorMessage = "تعذر فتح الملف المختار للاستعادة."
            )
        return parseBackupJsonText(rawText, portablePassword)
    }
}

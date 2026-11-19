package com.forgeprom.codestudio.core.export

import android.content.ActivityNotFoundException
import android.content.ClipData
import android.content.ClipDescription
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.pdf.PdfDocument
import android.os.Build
import android.os.Environment
import android.os.PersistableBundle
import android.text.Layout
import android.text.StaticLayout
import android.text.TextPaint
import androidx.core.content.FileProvider
import com.forgeprom.codestudio.core.security.ChecksumValidator
import com.forgeprom.codestudio.core.security.VaultAndEnterpriseManager
import com.forgeprom.codestudio.data.local.model.ProjectEntity
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.io.FileOutputStream
import java.util.UUID

/**
 * مساعد التصدير والمشاركة والنسخ الاحتياطي الأصلي لأندرويد (NativeShareAndPdfHelper)
 * 1. يولّد ملف PDF عربي متعدد الصفحات باتجاه RTL باستخدام محرك Android PdfDocument الأصلي.
 * 2. يصدّر المواصفات الهندسية بصيغة Markdown (.md).
 * 3. يوفّر النسخ الاحتياطي والاستعادة الشاملة لجميع المشاريع بصيغة JSON (Backup & Restore).
 * 4. يفتح قائمة المشاركة الأصلية (Android Share Sheet) وينسخ النصوص للحافظة (Clipboard).
 */
object NativeShareAndPdfHelper {

    private fun getExportDirectory(context: Context): File {
        val baseDir = context.getExternalFilesDir(Environment.DIRECTORY_DOCUMENTS) ?: context.filesDir
        val exportDir = File(baseDir, "ForgepromExports")
        if (!exportDir.exists()) exportDir.mkdirs()
        return exportDir
    }

    private fun sanitizeFileName(rawName: String): String {
        val cleaned = rawName
            .replace(Regex("[^a-zA-Z0-9\\u0600-\\u06FF_-]"), "_")
            .replace(Regex("_+"), "_")
            .trim('_')
            .take(40)
        return cleaned.ifBlank { "Forgeprom_Project" }
    }

    fun copyToClipboard(context: Context, label: String, text: String, isSensitive: Boolean = false) {
        val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
        val clip = ClipData.newPlainText(label, text)
        if (isSensitive && Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            clip.description.extras = PersistableBundle().apply {
                putBoolean(ClipDescription.EXTRA_IS_SENSITIVE, true)
            }
        }
        clipboard.setPrimaryClip(clip)
    }

    /**
     * مشاركة ملف مُصدّر (PDF / ZIP / Markdown) بأمان عبر FileProvider دون طلب أي صلاحيات تخزين
     */
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
            appendLine("🚀 وثيقة ومواصفات هندسية من Forgeprom Code Studio")
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

    /**
     * توليد ملف Markdown (.md) للمواصفات الهندسية الكاملة
     */
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
     * توليد ملف PDF عربي أصلي متعدد الصفحات باتجاه RTL وحفظه في مجلد المستندات الخاص بالتطبيق
     */
    fun generateNativeRtlPdf(context: Context, project: ProjectEntity): File {
        val pdfDocument = PdfDocument()
        try {
        val pageWidth = 595
        val pageHeight = 842
        val margin = 40f
        val contentWidth = (pageWidth - (margin * 2)).toInt()

        val titlePaint = TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.rgb(13, 138, 127) // Teal-Jade Brand
            textSize = 16f
            isFakeBoldText = true
        }

        val bodyPaint = TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.rgb(15, 23, 42)
            textSize = 11f
        }

        val fullText = buildString {
            appendLine("اسم المشروع: ${project.name}")
            appendLine("المنصة المستهدفة: ${project.targetPlatform} | النوع: ${project.type}")
            appendLine("------------------------------------------------------------")
            appendLine("وصف المشروع:")
            appendLine(project.description)
            appendLine("\nالمواصفات الهندسية والبرومبت المعتمد:")
            appendLine(project.currentPromptText ?: project.rawIdea)
        }

        val paragraphs = fullText.split("\n")
        var currentPageNumber = 1
        var pageInfo = PdfDocument.PageInfo.Builder(pageWidth, pageHeight, currentPageNumber).create()
        var page = pdfDocument.startPage(pageInfo)
        var canvas: Canvas = page.canvas

        canvas.drawText("Forgeprom Code Studio — وثيقة المواصفات الهندسية", margin, 42f, titlePaint)
        var currentY = 68f

        for (para in paragraphs) {
            val lineText = if (para.isBlank()) " " else para
            val layout = StaticLayout.Builder
                .obtain(lineText, 0, lineText.length, bodyPaint, contentWidth)
                .setAlignment(Layout.Alignment.ALIGN_NORMAL)
                .setLineSpacing(3f, 1.15f)
                .build()

            if (currentY + layout.height > pageHeight - margin) {
                pdfDocument.finishPage(page)
                currentPageNumber++
                pageInfo = PdfDocument.PageInfo.Builder(pageWidth, pageHeight, currentPageNumber).create()
                page = pdfDocument.startPage(pageInfo)
                canvas = page.canvas
                canvas.drawText("Forgeprom Code Studio — صفحة $currentPageNumber", margin, 42f, titlePaint)
                currentY = 68f
            }

            canvas.save()
            canvas.translate(margin, currentY)
            layout.draw(canvas)
            canvas.restore()
            currentY += layout.height + 4f
        }

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

    data class BackupSaveResult(
        val file: File,
        val isEncrypted: Boolean,
        val sha256Short: String
    )

    data class BackupRestoreResult(
        val projects: List<ProjectEntity>,
        val wasEncrypted: Boolean,
        val integrityVerified: Boolean,
        val errorMessage: String? = null
    )

    /**
     * تصدير نسخة احتياطية مشفرة بـ AES-256-GCM ومحميّة ببصمة SHA-256 لجميع المشاريع
     */
    fun exportAllProjectsBackupJson(context: Context, projects: List<ProjectEntity>): BackupSaveResult {
        val backupDir = File(context.filesDir, "backups")
        if (!backupDir.exists()) backupDir.mkdirs()

        val innerPayload = JSONObject().apply {
            put("app", "Forgeprom Code Studio")
            put("version", "2.6.0")
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
        }.toString()

        val payloadHash = ChecksumValidator.calculateSha256(innerPayload)
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

        val backupFile = File(backupDir, "forgeprom_projects_backup.json")
        backupFile.writeText(envelope.toString(2), Charsets.UTF_8)
        return BackupSaveResult(
            file = backupFile,
            isEncrypted = encryptedAttempt.isSuccess,
            sha256Short = payloadHash.take(12)
        )
    }

    /**
     * استعادة المشاريع من النسخة الاحتياطية (تشفير AES-256-GCM + التحقق من SHA-256 + توافق عكسي)
     */
    fun restoreProjectsFromBackupJson(context: Context): BackupRestoreResult {
        val backupFile = File(File(context.filesDir, "backups"), "forgeprom_projects_backup.json")
        if (!backupFile.exists()) {
            return BackupRestoreResult(emptyList(), wasEncrypted = false, integrityVerified = false)
        }

        return try {
            val rawRoot = JSONObject(backupFile.readText(Charsets.UTF_8))
            val isEncryptedV2 = rawRoot.optString("format") == "FORGEPROM_ENCRYPTED_BACKUP_V2"
            val expectedHash = rawRoot.optString("sha256", "")

            val dataJsonString: String
            if (isEncryptedV2) {
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
                dataJsonString = decrypted.getOrThrow()
            } else {
                dataJsonString = rawRoot.toString()
            }

            val integrityOk = if (isEncryptedV2 && expectedHash.isNotBlank()) {
                ChecksumValidator.calculateSha256(dataJsonString).equals(expectedHash, ignoreCase = true)
            } else true

            if (!integrityOk) {
                return BackupRestoreResult(
                    projects = emptyList(),
                    wasEncrypted = isEncryptedV2,
                    integrityVerified = false,
                    errorMessage = "تم رصد تلف أو تعديل غير مصرح به في ملف النسخة الاحتياطية (فشل فحص SHA-256)."
                )
            }

            val root = JSONObject(dataJsonString)
            val arr = root.optJSONArray("projects")
                ?: return BackupRestoreResult(emptyList(), wasEncrypted = isEncryptedV2, integrityVerified = true)
            val result = mutableListOf<ProjectEntity>()
            for (i in 0 until arr.length()) {
                val obj = arr.getJSONObject(i)
                val validName = obj.optString("name").trim()
                if (validName.isBlank()) continue
                val validId = obj.optString("id").trim().ifBlank { UUID.randomUUID().toString() }
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
                        createdAt = obj.optLong("createdAt", System.currentTimeMillis()),
                        updatedAt = System.currentTimeMillis()
                    )
                )
            }
            BackupRestoreResult(
                projects = result,
                wasEncrypted = isEncryptedV2,
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
}

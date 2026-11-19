package com.forgeprom.codestudio.core.export

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.pdf.PdfDocument
import android.text.Layout
import android.text.StaticLayout
import android.text.TextPaint
import com.forgeprom.codestudio.data.local.model.ProjectEntity
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.io.FileOutputStream

/**
 * مساعد التصدير والمشاركة والنسخ الاحتياطي الأصلي لأندرويد (NativeShareAndPdfHelper)
 * 1. يولّد ملف PDF عربي متعدد الصفحات باتجاه RTL باستخدام محرك Android PdfDocument الأصلي.
 * 2. يصدّر المواصفات الهندسية بصيغة Markdown (.md).
 * 3. يوفّر النسخ الاحتياطي والاستعادة الشاملة لجميع المشاريع بصيغة JSON (Backup & Restore).
 * 4. يفتح قائمة المشاركة الأصلية (Android Share Sheet) وينسخ النصوص للحافظة (Clipboard).
 */
object NativeShareAndPdfHelper {

    fun copyToClipboard(context: Context, label: String, text: String) {
        val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
        clipboard.setPrimaryClip(ClipData.newPlainText(label, text))
    }

    fun shareProjectPrompt(context: Context, project: ProjectEntity) {
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
        context.startActivity(chooser)
    }

    /**
     * توليد ملف Markdown (.md) للمواصفات الهندسية الكاملة
     */
    fun exportMarkdownSpec(context: Context, project: ProjectEntity, customContent: String? = null): File {
        val exportDir = File(context.filesDir, "exports")
        if (!exportDir.exists()) exportDir.mkdirs()

        val safeName = project.name.replace(Regex("[^a-zA-Z0-9\\u0600-\\u06FF_]"), "_").take(40)
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

        val exportDir = File(context.filesDir, "exports")
        if (!exportDir.exists()) exportDir.mkdirs()

        val safeName = project.name.replace(Regex("[^a-zA-Z0-9\\u0600-\\u06FF_]"), "_").take(40)
        val pdfFile = File(exportDir, "${safeName}_RTL_Spec.pdf")
        FileOutputStream(pdfFile).use { out ->
            pdfDocument.writeTo(out)
        }
        pdfDocument.close()

        return pdfFile
    }

    /**
     * تصدير نسخة احتياطية شاملة لجميع المشاريع بصيغة JSON
     */
    fun exportAllProjectsBackupJson(context: Context, projects: List<ProjectEntity>): File {
        val backupDir = File(context.filesDir, "backups")
        if (!backupDir.exists()) backupDir.mkdirs()

        val root = JSONObject().apply {
            put("app", "Forgeprom Code Studio")
            put("version", "2.5.0")
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
        }

        val backupFile = File(backupDir, "forgeprom_projects_backup.json")
        backupFile.writeText(root.toString(2), Charsets.UTF_8)
        return backupFile
    }

    /**
     * استعادة المشاريع من ملف النسخة الاحتياطية JSON
     */
    fun restoreProjectsFromBackupJson(context: Context): List<ProjectEntity> {
        val backupFile = File(File(context.filesDir, "backups"), "forgeprom_projects_backup.json")
        if (!backupFile.exists()) return emptyList()

        return try {
            val root = JSONObject(backupFile.readText(Charsets.UTF_8))
            val arr = root.optJSONArray("projects") ?: return emptyList()
            val result = mutableListOf<ProjectEntity>()
            for (i in 0 until arr.length()) {
                val obj = arr.getJSONObject(i)
                result.add(
                    ProjectEntity(
                        id = obj.optString("id"),
                        name = obj.optString("name"),
                        description = obj.optString("description"),
                        type = obj.optString("type", "NEW_APP"),
                        targetPlatform = obj.optString("targetPlatform", "ANDROID_KOTLIN"),
                        rawIdea = obj.optString("rawIdea"),
                        isPinned = obj.optBoolean("isPinned", false),
                        isArchived = obj.optBoolean("isArchived", false),
                        currentPromptText = obj.optString("currentPromptText").ifBlank { null },
                        createdAt = obj.optLong("createdAt", System.currentTimeMillis()),
                        updatedAt = System.currentTimeMillis()
                    )
                )
            }
            result
        } catch (_: Exception) {
            emptyList()
        }
    }
}

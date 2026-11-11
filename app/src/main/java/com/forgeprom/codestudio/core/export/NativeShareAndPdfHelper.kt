package com.forgeprom.codestudio.core.export

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
import java.io.File
import java.io.FileOutputStream

/**
 * مساعد التصدير والمشاركة الأصلي لأندرويد (NativeShareAndPdfHelper)
 * 1. يولّد ملف PDF عربي باتجاه RTL باستخدام محرك Android PdfDocument الأصلي بدون مكتبات خارجية ثقيلة.
 * 2. يفتح قائمة المشاركة الأصلية في أندرويد (Android Share Sheet) لإرسال البرومبتات عبر WhatsApp, Telegram, Gmail.
 */
object NativeShareAndPdfHelper {

    fun shareProjectPrompt(context: Context, project: ProjectEntity) {
        val shareBody = buildString {
            appendLine("🚀 مشروع هندسي من Forgeprom Code Studio")
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
     * توليد ملف PDF عربي أصلي باتجاه RTL وحفظه في مجلد المستندات الخاص بالتطبيق
     */
    fun generateNativeRtlPdf(context: Context, project: ProjectEntity): File {
        val pdfDocument = PdfDocument()
        val pageInfo = PdfDocument.PageInfo.Builder(595, 842, 1).create() // A4 Standard
        val page = pdfDocument.startPage(pageInfo)
        val canvas: Canvas = page.canvas

        val titlePaint = TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.rgb(107, 70, 193) // Deep Purple Brand
            textSize = 18f
            isFakeBoldText = true
        }

        val bodyPaint = TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.rgb(15, 23, 42)
            textSize = 11f
        }

        // رسم ترويسة الوثيقة
        canvas.drawText("Forgeprom Code Studio — وثيقة هندسية", 40f, 45f, titlePaint)

        val fullText = buildString {
            appendLine("اسم المشروع: ${project.name}")
            appendLine("المنصة المستهدفة: ${project.targetPlatform}")
            appendLine("نوع المشروع: ${project.type}")
            appendLine("\nوصف المشروع:")
            appendLine(project.description)
            appendLine("\nالبرومبت والمواصفات المعتمدة:")
            appendLine(project.currentPromptText ?: project.rawIdea)
        }

        val staticLayout = StaticLayout.Builder
            .obtain(fullText, 0, fullText.length, bodyPaint, 515)
            .setAlignment(Layout.Alignment.ALIGN_NORMAL)
            .setLineSpacing(4f, 1.2f)
            .build()

        canvas.save()
        canvas.translate(40f, 75f)
        staticLayout.draw(canvas)
        canvas.restore()

        pdfDocument.finishPage(page)

        val exportDir = File(context.filesDir, "exports")
        if (!exportDir.exists()) exportDir.mkdirs()

        val safeName = project.name.replace(Regex("[^a-zA-Z0-9\\u0600-\\u06FF_]"), "_")
        val pdfFile = File(exportDir, "${safeName}_RTL_Spec.pdf")
        FileOutputStream(pdfFile).use { out ->
            pdfDocument.writeTo(out)
        }
        pdfDocument.close()

        return pdfFile
    }
}

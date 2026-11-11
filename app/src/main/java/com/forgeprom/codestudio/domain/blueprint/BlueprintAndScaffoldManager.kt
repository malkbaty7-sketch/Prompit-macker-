package com.forgeprom.codestudio.domain.blueprint

import android.content.Context
import com.forgeprom.codestudio.data.local.model.ProjectEntity
import java.io.File
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

/**
 * مدير القوالب الهندسية الجاهزة ومُولّد هياكل المشاريع الكاملة (BlueprintAndScaffoldManager)
 * 1. يوفّر قوالب صناعية جاهزة (متجر إلكتروني، توصيل وتتبع، محاسبة ومخزون، منصة تعليمية، عيادة طبية، محفظة مالية).
 * 2. يولّد أرشيف كود مصدري متكامل (Starter Boilerplate ZIP) للمشروع المختار.
 */
object BlueprintAndScaffoldManager {

    data class IndustryBlueprint(
        val id: String,
        val title: String,
        val category: String,
        val targetPlatform: String,
        val description: String,
        val rawIdea: String,
        val prebuiltPrompt: String
    )

    fun getReadyBlueprints(): List<IndustryBlueprint> = listOf(
        IndustryBlueprint(
            id = "bp-ecommerce",
            title = "متجر إلكتروني متعدد التجار (Multi-Vendor Marketplace)",
            category = "التجارة الإلكترونية",
            targetPlatform = "FLUTTER",
            description = "منصة تسوق متكاملة مع سلة مشتريات ذكية، بوابات دفع، لوحة تحكم للتجار، وتتبع الطلبات.",
            rawIdea = "بناء تطبيق متجر إلكتروني متعدد البائعين يدعم الدفع الإلكتروني، إشعارات الطلبات، والعمل دون اتصال لتصفح الكتالوج.",
            prebuiltPrompt = "# البرومبت الهندسي: متجر متعدد التجار\n- الواجهة: Flutter RTL مع Material 3\n- التخزين المحلي: SQLite / Hive لتخزين الكتالوج والسلة محلياً\n- الميزات: لوحة بائعين، كوبونات خصم، تتبع شحنات."
        ),
        IndustryBlueprint(
            id = "bp-delivery",
            title = "منظومة توصيل وتتبع مناديب (Fleet & Delivery Tracker)",
            category = "اللوجستيات والتوصيل",
            targetPlatform = "ANDROID_KOTLIN",
            description = "تطبيق لتتبع المناديب لحظياً، توجيه المسارات، إثبات التسليم بالباركود والتوقيع، والعمل في المناطق ضعيفة التغطية.",
            rawIdea = "تطبيق أندرويد لمناديب التوصيل مع تخزين الإحداثيات محلياً عند انقطاع الشبكة ومزامنتها فور عودة الاتصال.",
            prebuiltPrompt = "# البرومبت الهندسي: تتبع المناديب والتوصيل\n- المعمارية: Kotlin + Jetpack Compose + Room + ForegroundLocationService\n- الميزات: مسح باركود الشحنات عبر ML Kit، طابور مزامنة غير متزامن عبر WorkManager."
        ),
        IndustryBlueprint(
            id = "bp-erp",
            title = "نظام محاسبة ومستودعات سحابي محلي (Smart ERP & Inventory)",
            category = "إدارة الأعمال والمحاسبة",
            targetPlatform = "ANDROID_KOTLIN",
            description = "إدارة الفواتير الضريبية، الجرد المخزني بالباركود، القيود اليومية، وتقارير الأرباح والخسائر.",
            rawIdea = "نظام محاسبي ومخزني متكامل للشركات الصغيرة والمتوسطة يدعم الفوترة الإلكترونية QR.",
            prebuiltPrompt = "# البرومبت الهندسي: نظام المحاسبة والمستودعات\n- قاعدة البيانات: Room مع علاقات الجداول (Invoices, LineItems, Ledger)\n- الميزات: توليد QR الضريبي، تقارير PDF عربية، جرد فوري بالكاميرا."
        ),
        IndustryBlueprint(
            id = "bp-edtech",
            title = "منصة تعليمية تفاعلية مع تحميل الدروس (Offline-Ready EdTech)",
            category = "التعليم الإلكتروني",
            targetPlatform = "REACT_NATIVE",
            description = "منصة دورات تدريبية واختبارات ذكية مع إمكانية تنزيل الدروس والمرفقات للمشاهدة بدون إنترنت.",
            rawIdea = "تطبيق تعليمي يدعم مشاهدة الفيديو بدون إنترنت، اختبارات تفاعلية، وشهادات إتمام.",
            prebuiltPrompt = "# البرومبت الهندسي: منصة تعليمية تفاعلية\n- الميزات: مشغل فيديو محلي مشفر، محرك اختبارات وبنك أسئلة محلي، تتبع تقدم الطالب."
        )
    )

    /**
     * توليد حزمة كود مصدري أولي (Starter Codebase Scaffolding ZIP) بناءً على المشروع
     */
    fun generateStarterBoilerplateZip(context: Context, project: ProjectEntity): File {
        val exportDir = File(context.filesDir, "scaffolds")
        if (!exportDir.exists()) exportDir.mkdirs()

        val safeName = project.name.replace(Regex("[^a-zA-Z0-9\\u0600-\\u06FF_]"), "_")
        val zipFile = File(exportDir, "${safeName}_Starter_Codebase.zip")

        ZipOutputStream(zipFile.outputStream()).use { zos ->
            // 1. README.md
            zos.putNextEntry(ZipEntry("README.md"))
            zos.write(
                """
# ${project.name} — Starter Codebase
تم توليد هذا الهيكل البرمجي تلقائياً بواسطة **Forgeprom Code Studio**.
- المنصة المستهدفة: ${project.targetPlatform}
- نوع المشروع: ${project.type}

## الوصف الهندسي
${project.description}
                """.trimIndent().toByteArray(Charsets.UTF_8)
            )
            zos.closeEntry()

            // 2. ARCHITECTURE_SPEC.md
            zos.putNextEntry(ZipEntry("docs/ARCHITECTURE_SPEC.md"))
            zos.write((project.currentPromptText ?: project.rawIdea).toByteArray(Charsets.UTF_8))
            zos.closeEntry()

            // 3. Domain Model Stub
            zos.putNextEntry(ZipEntry("src/domain/CoreModel.kt"))
            zos.write(
                """
package com.generated.app.domain

data class CoreItem(
    val id: String,
    val title: String,
    val createdAt: Long = System.currentTimeMillis()
)
                """.trimIndent().toByteArray(Charsets.UTF_8)
            )
            zos.closeEntry()
        }

        return zipFile
    }
}

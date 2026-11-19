package com.forgeprom.codestudio.domain.blueprint

import android.content.Context
import android.os.Environment
import com.forgeprom.codestudio.core.security.VaultAndEnterpriseManager
import com.forgeprom.codestudio.data.local.model.ProjectEntity
import com.forgeprom.codestudio.domain.pipeline.PromptEngineeringEngine
import java.io.File
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

/**
 * مدير القوالب الهندسية الجاهزة ومُولّد هياكل المشاريع الكاملة (BlueprintAndScaffoldManager)
 * 1. يوفّر 6 قوالب صناعية جاهزة (متجر إلكتروني، توصيل وتتبع، محاسبة ومخزون، منصة تعليمية، عيادة طبية، محفظة مالية).
 * 2. يولّد أرشيف كود مصدري متكامل متعدد الطبقات (Full Starter Boilerplate ZIP) للمشروع المختار.
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
            prebuiltPrompt = "# البرومبت الهندسي: متجر متعدد التجار\n- المعمارية: Clean Architecture + MVVM\n- الواجهة: Flutter RTL مع Material 3\n- التخزين المحلي: SQLite / Room لتخزين الكتالوج والسلة محلياً دون إنترنت\n- الأمان: تشفير رموز الجلسة عبر AES-256-GCM وفحص OWASP\n- معايير القبول: تصفح المنتجات وإضافة السلة في وضع عدم الاتصال."
        ),
        IndustryBlueprint(
            id = "bp-delivery",
            title = "منظومة توصيل وتتبع مناديب (Fleet & Delivery Tracker)",
            category = "اللوجستيات والتوصيل",
            targetPlatform = "ANDROID_KOTLIN",
            description = "تطبيق لتتبع المناديب لحظياً، توجيه المسارات، إثبات التسليم بالباركود والتوقيع، والعمل في المناطق ضعيفة التغطية.",
            rawIdea = "تطبيق أندرويد لمناديب التوصيل مع تخزين الإحداثيات محلياً عند انقطاع الشبكة ومزامنتها فور عودة الاتصال.",
            prebuiltPrompt = "# البرومبت الهندسي: تتبع المناديب والتوصيل\n- المعمارية: Kotlin + Jetpack Compose + Clean Architecture + Room\n- الميزات: مسح باركود الشحنات، طابور مزامنة محلي عند انقطاع الشبكة (Offline Queue)\n- الأمان: تشفير بيانات التسليم عبر Android KeyStore AES-256\n- معايير القبول: عدم ضياع أي تحديث حالة شحنة أثناء وضع الطيران."
        ),
        IndustryBlueprint(
            id = "bp-erp",
            title = "نظام محاسبة ومستودعات سحابي محلي (Smart ERP & Inventory)",
            category = "إدارة الأعمال والمحاسبة",
            targetPlatform = "ANDROID_KOTLIN",
            description = "إدارة الفواتير الضريبية، الجرد المخزني بالباركود، القيود اليومية، وتقارير الأرباح والخسائر.",
            rawIdea = "نظام محاسبي ومخزني متكامل للشركات الصغيرة والمتوسطة يدعم الفوترة الإلكترونية QR.",
            prebuiltPrompt = "# البرومبت الهندسي: نظام المحاسبة والمستودعات\n- المعمارية: Clean Architecture + Room SQLite\n- قاعدة البيانات: جداول (Invoices, LineItems, InventoryItems, LedgerEntries)\n- الميزات: توليد QR الضريبي، تقارير PDF عربية، جرد فوري دون إنترنت\n- الأمان: سجل تدقيق غير قابل للتعديل وتشفير AES-256."
        ),
        IndustryBlueprint(
            id = "bp-edtech",
            title = "منصة تعليمية تفاعلية مع تحميل الدروس (Offline-Ready EdTech)",
            category = "التعليم الإلكتروني",
            targetPlatform = "REACT_NATIVE",
            description = "منصة دورات تدريبية واختبارات ذكية مع إمكانية تنزيل الدروس والمرفقات للمشاهدة بدون إنترنت.",
            rawIdea = "تطبيق تعليمي يدعم مشاهدة الفيديو بدون إنترنت، اختبارات تفاعلية، وشهادات إتمام.",
            prebuiltPrompt = "# البرومبت الهندسي: منصة تعليمية تفاعلية\n- المعمارية: Clean Architecture + Offline Storage\n- الميزات: مشغل دروس محلي، محرك اختبارات وبنك أسئلة محلي دون إنترنت، تتبع تقدم الطالب\n- الواجهة: تصميم RTL عربي مريح للعين."
        ),
        IndustryBlueprint(
            id = "bp-clinic",
            title = "نظام إدارة العيادات والسجل الطبي (Smart Clinic EMR)",
            category = "الرعاية الصحية",
            targetPlatform = "ANDROID_KOTLIN",
            description = "إدارة ملفات المرضى، المواعيد، الوصفات الطبية، وتصدير التقارير الطبية المشفرة محلياً.",
            rawIdea = "تطبيق لوحي وهاتفي لإدارة العيادات الطبية وحفظ السجل الصحي للمرضى بتشفير محلي كامل.",
            prebuiltPrompt = "# البرومبت الهندسي: نظام إدارة العيادات الطبية\n- المعمارية: Kotlin + Jetpack Compose + Room Database\n- الأمان: تشفير السجلات الطبية عبر Android KeyStore AES-256-GCM وفق معايير OWASP MASVS\n- الميزات: جدولة المواعيد، إصدار الوصفات بصيغة PDF عربية، عمل كامل دون إنترنت."
        ),
        IndustryBlueprint(
            id = "bp-fintech",
            title = "محفظة مالية وإدارة مصروفات ذكية (FinTech Expense Vault)",
            category = "التقنية المالية FinTech",
            targetPlatform = "ANDROID_KOTLIN",
            description = "تتبع الميزانيات والمصروفات المتعددة العملات مع تحليلات مالية وتشفير عتادي كامل.",
            rawIdea = "تطبيق محفظة مالية شخصية وعائلية يعمل محلياً مع تقارير شهرية وتنبيهات الميزانية.",
            prebuiltPrompt = "# البرومبت الهندسي: محفظة مالية ذكية\n- المعمارية: Clean Architecture + MVVM + Room\n- الأمان: حماية البيانات المالية عبر AES-256-GCM Hardware KeyStore\n- الميزات: تقارير مالية PDF و JSON، دعم تعدد العملات، عمل محلي 100%."
        )
    )

    /**
     * توليد حزمة كود مصدري متكامل متعدد الطبقات (Full Starter Codebase Scaffolding ZIP)
     */
    fun generateStarterBoilerplateZip(
        context: Context,
        project: ProjectEntity,
        serverUrl: String = "https://api.forgeprom.local"
    ): File {
        val baseDir = context.getExternalFilesDir(Environment.DIRECTORY_DOCUMENTS) ?: context.filesDir
        val exportDir = File(baseDir, "ForgepromScaffolds")
        if (!exportDir.exists()) exportDir.mkdirs()

        val safeName = project.name
            .replace(Regex("[^a-zA-Z0-9\\u0600-\\u06FF_-]"), "_")
            .replace(Regex("_+"), "_")
            .trim('_')
            .take(40)
            .ifBlank { "Forgeprom_Project" }
        val zipFile = File(exportDir, "${safeName}_Full_Scaffold.zip")

        val pipeline = PromptEngineeringEngine.generateChainedPipeline(project, serverUrl)
        val owaspItems = VaultAndEnterpriseManager.auditProjectSecurity(project)

        ZipOutputStream(zipFile.outputStream()).use { zos ->
            // 1. README.md
            zos.putNextEntry(ZipEntry("README.md"))
            zos.write(
                """
# ${project.name} — Full Engineering Scaffold
تم توليد هذا الهيكل البرمجي المتكامل بواسطة **Forgeprom Code Studio v2.5**.
- **المنصة المستهدفة:** ${project.targetPlatform}
- **نوع المشروع:** ${project.type}

## الوصف الهندسي
${project.description}

## محتويات الأرشيف:
- `docs/CHAINED_PRD_AND_SPECS.md`: وثيقة المتطلبات المتسلسلة (PRD + Database Schema + Prompts)
- `docs/OPENAPI_3_1.yaml`: عقد الواجهات البرمجية القياسي
- `docs/SECURITY_OWASP_AUDIT.md`: تقرير التدقيق الأمني OWASP MASVS
- `app/build.gradle.kts`: إعدادات البناء والتبعيات
- `src/main/java/com/generated/app/domain/CoreModels.kt`: نماذج البيانات الأساسية
- `src/main/java/com/generated/app/data/LocalDatabase.kt`: طبقة قاعدة البيانات المحلية Room
- `src/main/java/com/generated/app/presentation/MainScreen.kt`: واجهة Jetpack Compose RTL الأولية
                """.trimIndent().toByteArray(Charsets.UTF_8)
            )
            zos.closeEntry()

            // 2. docs/CHAINED_PRD_AND_SPECS.md
            zos.putNextEntry(ZipEntry("docs/CHAINED_PRD_AND_SPECS.md"))
            zos.write(pipeline.combinedMarkdown.toByteArray(Charsets.UTF_8))
            zos.closeEntry()

            // 3. docs/OPENAPI_3_1.yaml
            zos.putNextEntry(ZipEntry("docs/OPENAPI_3_1.yaml"))
            zos.write(pipeline.apiContracts.toByteArray(Charsets.UTF_8))
            zos.closeEntry()

            // 4. docs/SECURITY_OWASP_AUDIT.md
            zos.putNextEntry(ZipEntry("docs/SECURITY_OWASP_AUDIT.md"))
            val securityMd = buildString {
                appendLine("# تقرير التدقيق الأمني (OWASP Mobile & API Security Audit)")
                appendLine("المشروع: **${project.name}**\n")
                owaspItems.forEach { item ->
                    appendLine("## [${item.severity}] ${item.code} — ${item.title}")
                    appendLine("- **التوصية:** ${item.recommendation}\n")
                }
            }
            zos.write(securityMd.toByteArray(Charsets.UTF_8))
            zos.closeEntry()

            // 5. app/build.gradle.kts
            zos.putNextEntry(ZipEntry("app/build.gradle.kts"))
            zos.write(
                """
plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
    id("org.jetbrains.kotlin.plugin.compose")
    id("com.google.devtools.ksp")
}

android {
    namespace = "com.generated.app"
    compileSdk = 35
    defaultConfig {
        applicationId = "com.generated.app"
        minSdk = 26
        targetSdk = 35
        versionCode = 1
        versionName = "1.0.0"
    }
    buildFeatures { compose = true }
}
                """.trimIndent().toByteArray(Charsets.UTF_8)
            )
            zos.closeEntry()

            // 6. Domain Layer
            zos.putNextEntry(ZipEntry("src/main/java/com/generated/app/domain/CoreModels.kt"))
            zos.write(
                """
package com.generated.app.domain

data class CoreRecord(
    val id: String,
    val title: String,
    val description: String,
    val isPinned: Boolean = false,
    val createdAt: Long = System.currentTimeMillis()
)
                """.trimIndent().toByteArray(Charsets.UTF_8)
            )
            zos.closeEntry()

            // 7. Data Layer
            zos.putNextEntry(ZipEntry("src/main/java/com/generated/app/data/LocalDatabase.kt"))
            zos.write(
                """
package com.generated.app.data

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "core_records")
data class CoreRecordEntity(
    @PrimaryKey val id: String,
    val title: String,
    val description: String,
    val createdAt: Long = System.currentTimeMillis()
)
                """.trimIndent().toByteArray(Charsets.UTF_8)
            )
            zos.closeEntry()

            // 8. Presentation Layer
            val safeKtName = escapeKotlinString(project.name)
            val safeKtDesc = escapeKotlinString(project.description)
            zos.putNextEntry(ZipEntry("src/main/java/com/generated/app/presentation/MainScreen.kt"))
            zos.write(
                """
package com.generated.app.presentation

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

@Composable
fun GeneratedMainScreen() {
    Scaffold { padding ->
        Column(modifier = Modifier.fillMaxSize().padding(padding).padding(16.dp)) {
            Text(text = "$safeKtName", style = MaterialTheme.typography.headlineSmall)
            Spacer(modifier = Modifier.height(8.dp))
            Text(text = "$safeKtDesc", style = MaterialTheme.typography.bodyMedium)
        }
    }
}
                """.trimIndent().toByteArray(Charsets.UTF_8)
            )
            zos.closeEntry()
        }

        return zipFile
    }

    private fun escapeKotlinString(raw: String): String {
        return raw
            .replace("\\", "\\\\")
            .replace("\"", "\\\"")
            .replace("$", "\\$")
            .replace("\r", " ")
            .replace("\n", "\\n")
            .trim()
    }
}

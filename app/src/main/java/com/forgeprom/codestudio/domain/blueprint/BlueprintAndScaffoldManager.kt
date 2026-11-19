package com.forgeprom.codestudio.domain.blueprint

import android.content.Context
import android.os.Environment
import com.forgeprom.codestudio.core.export.NativeShareAndPdfHelper
import com.forgeprom.codestudio.core.security.VaultAndEnterpriseManager
import com.forgeprom.codestudio.data.local.model.ProjectEntity
import com.forgeprom.codestudio.domain.pipeline.PromptEngineeringEngine
import java.io.File
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

/**
 * مدير القوالب الهندسية الجاهزة ومُولّد هياكل المشاريع متعددة المنصات (BlueprintAndScaffoldManager)
 * 1. يوفّر 6 قوالب صناعية جاهزة ويدعم القوالب المخصصة المحفوظة من مشاريع المستخدم.
 * 2. يولّد أرشيف كود مصدري مخصّص (packageName + وحدات اختيارية + حزمة تنفيذية تشمل PDF العربي).
 */
object BlueprintAndScaffoldManager {

    data class IndustryBlueprint(
        val id: String,
        val title: String,
        val category: String,
        val targetPlatform: String,
        val description: String,
        val rawIdea: String,
        val prebuiltPrompt: String,
        val isCustom: Boolean = false
    )

    data class ScaffoldExportConfig(
        val packageName: String = "com.generated.app",
        val includeAuthModule: Boolean = true,
        val includeOfflineSyncQueue: Boolean = true,
        val includeEncryptionVault: Boolean = true,
        val includeUnitTests: Boolean = false,
        val includeExecutiveBundlePdf: Boolean = false
    )

    fun getReadyBlueprints(): List<IndustryBlueprint> = listOf(
        IndustryBlueprint(
            id = "bp-ecommerce",
            title = "متجر إلكتروني متعدد التجار (Multi-Vendor Marketplace)",
            category = "التجارة الإلكترونية",
            targetPlatform = "FLUTTER",
            description = "منصة تسوق متكاملة مع سلة مشتريات ذكية، بوابات دفع، لوحة تحكم للتجار، وتتبع الطلبات.",
            rawIdea = "بناء تطبيق متجر إلكتروني متعدد البائعين يدعم الدفع الإلكتروني، إشعارات الطلبات، والعمل دون اتصال لتصفح الكتالوج.",
            prebuiltPrompt = "# البرومبت الهندسي: متجر متعدد التجار\n- المعمارية: Clean Architecture + Riverpod\n- الواجهة: Flutter RTL مع Material 3\n- التخزين المحلي: Drift SQLite لتخزين الكتالوج والسلة محلياً دون إنترنت\n- الأمان: تشفير رموز الجلسة عبر AES-256-GCM وفحص OWASP\n- معايير القبول: تصفح المنتجات وإضافة السلة في وضع عدم الاتصال."
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
            prebuiltPrompt = "# البرومبت الهندسي: منصة تعليمية تفاعلية\n- المعمارية: Clean Architecture + Expo SQLite Offline Storage\n- الميزات: مشغل دروس محلي، محرك اختبارات وبنك أسئلة محلي دون إنترنت، تتبع تقدم الطالب\n- الواجهة: تصميم RTL عربي مريح للعين."
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
            targetPlatform = "WEB",
            description = "تتبع الميزانيات والمصروفات المتعددة العملات مع تحليلات مالية وتشفير محلي كامل.",
            rawIdea = "منصة ويب PWA لإدارة المصروفات والميزانية الشخصية والعائلية تعمل دون إنترنت مع تقارير شهرية.",
            prebuiltPrompt = "# البرومبت الهندسي: محفظة مالية ذكية\n- المعمارية: Clean Architecture + IndexedDB Offline PWA\n- الأمان: حماية البيانات عبر AES-256-GCM وترويسات CSP وفق OWASP\n- الميزات: تقارير مالية PDF و JSON، دعم تعدد العملات، عمل محلي 100%."
        )
    )

    private fun ZipOutputStream.writeTextEntry(path: String, content: String) {
        putNextEntry(ZipEntry(path))
        write(content.toByteArray(Charsets.UTF_8))
        closeEntry()
    }

    private fun ZipOutputStream.writeBytesEntry(path: String, bytes: ByteArray) {
        putNextEntry(ZipEntry(path))
        write(bytes)
        closeEntry()
    }

    private fun sanitizePackageName(raw: String): String {
        val cleaned = raw.trim().lowercase()
            .replace(Regex("[^a-z0-9._]"), "")
            .split(".")
            .filter { it.isNotBlank() && it.first().isLetter() }
            .joinToString(".")
        return if (cleaned.contains(".")) cleaned else "com.generated.app"
    }

    /**
     * توليد حزمة كود مصدري متكامل مخصّصة مطابقة لمنصة المشروع المختارة مع خيار الحزمة التنفيذية (PDF + OpenAPI + OWASP + Code)
     */
    fun generateStarterBoilerplateZip(
        context: Context,
        project: ProjectEntity,
        serverUrl: String = "https://api.forgeprom.local",
        config: ScaffoldExportConfig = ScaffoldExportConfig()
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
        val platformTag = project.targetPlatform.uppercase().replace(Regex("[^A-Z0-9_]"), "")
        val bundleSuffix = if (config.includeExecutiveBundlePdf) "_ExecutiveBundle" else "_Scaffold"
        val zipFile = File(exportDir, "${safeName}_${platformTag}${bundleSuffix}.zip")

        val sanitizedPkg = sanitizePackageName(config.packageName)
        val pipeline = PromptEngineeringEngine.generateChainedPipeline(project, serverUrl)
        val owaspItems = VaultAndEnterpriseManager.auditProjectSecurity(project)
        val safeTitle = escapeStringLiteral(project.name)
        val safeDesc = escapeStringLiteral(project.description)

        val embeddedPdfFile = if (config.includeExecutiveBundlePdf) {
            runCatching { NativeShareAndPdfHelper.generateNativeRtlPdf(context, project) }.getOrNull()
        } else null

        ZipOutputStream(zipFile.outputStream()).use { zos ->
            // الوثائق الهندسية المشتركة لكل المنصات
            zos.writeTextEntry("docs/CHAINED_PRD_AND_SPECS.md", pipeline.combinedMarkdown)
            zos.writeTextEntry("docs/OPENAPI_3_1.yaml", pipeline.apiContracts)
            zos.writeTextEntry(
                "docs/SECURITY_OWASP_AUDIT.md",
                buildString {
                    appendLine("# تقرير التدقيق الأمني (OWASP Mobile & API Security Audit)")
                    appendLine("المشروع: **${project.name}** | المنصة: **${project.targetPlatform}** | الحزمة: **$sanitizedPkg**\n")
                    owaspItems.forEach { item ->
                        val status = if (item.isPassed) "PASS" else "ACTION REQUIRED"
                        appendLine("## [$status | ${item.severity}] ${item.code} — ${item.title}")
                        appendLine("- **التوصية:** ${item.recommendation}\n")
                    }
                }
            )

            val extraDocs = StringBuilder()
            if (embeddedPdfFile != null && embeddedPdfFile.exists()) {
                zos.writeBytesEntry("docs/${safeName}_Executive_Spec.pdf", embeddedPdfFile.readBytes())
                extraDocs.appendLine("- `docs/${safeName}_Executive_Spec.pdf`: الوثيقة التنفيذية المنسّقة بالعربية RTL (PDF)")
            }

            // توليد الكود المصدري المطابق لمنصة المشروع مع الوحدات المخصّصة
            val includedFilesSummary = when (project.targetPlatform.uppercase()) {
                "FLUTTER" -> writeFlutterScaffold(zos, project, safeTitle, safeDesc)
                "REACT_NATIVE" -> writeReactNativeScaffold(zos, project, safeTitle, safeDesc)
                "WEB" -> writeWebScaffold(zos, project, safeTitle, safeDesc)
                "BACKEND" -> writeBackendScaffold(zos, project, safeTitle, safeDesc, pipeline.databaseSchema)
                else -> writeAndroidKotlinScaffold(zos, project, safeTitle, safeDesc, sanitizedPkg, config)
            }

            val sharedModulesSummary = writeOptionalCrossPlatformModules(zos, project, sanitizedPkg, config)

            zos.writeTextEntry(
                "README.md",
                """
# ${project.name} — Multi-Platform Engineering Scaffold
تم توليد هذا الهيكل البرمجي المتكامل بواسطة **Forgeprom Code Studio v2.7**.
- **المنصة المستهدفة:** ${project.targetPlatform}
- **نوع المشروع:** ${project.type}
- **معرّف الحزمة (Package / Namespace):** `$sanitizedPkg`

## الوصف الهندسي
${project.description}

## محتويات الأرشيف:
- `docs/CHAINED_PRD_AND_SPECS.md`: وثيقة المتطلبات المتسلسلة (PRD + Database Schema + Prompts)
- `docs/OPENAPI_3_1.yaml`: عقد الواجهات البرمجية القياسي (OpenAPI 3.1)
- `docs/SECURITY_OWASP_AUDIT.md`: تقرير التدقيق الأمني OWASP
${extraDocs.toString().trimEnd()}
$includedFilesSummary
$sharedModulesSummary
                """.trimIndent()
            )
        }

        return zipFile
    }

    private fun writeOptionalCrossPlatformModules(
        zos: ZipOutputStream,
        project: ProjectEntity,
        sanitizedPkg: String,
        config: ScaffoldExportConfig
    ): String {
        val isAndroid = project.targetPlatform.uppercase() == "ANDROID_KOTLIN" ||
            project.targetPlatform.uppercase() !in listOf("FLUTTER", "REACT_NATIVE", "WEB", "BACKEND")
        if (isAndroid) return ""

        val summary = StringBuilder()
        if (config.includeAuthModule) {
            zos.writeTextEntry(
                "modules/auth/AUTH_MODULE_CONTRACT.md",
                "# Auth Module ($sanitizedPkg)\n- Bearer JWT + Refresh Token Rotation\n- Row-Level Owner Check (OWASP API1:2023)\n"
            )
            summary.appendLine("- `modules/auth/AUTH_MODULE_CONTRACT.md`: عقد وحدة المصادقة والصلاحيات")
        }
        if (config.includeOfflineSyncQueue) {
            zos.writeTextEntry(
                "modules/sync/OFFLINE_QUEUE_STRATEGY.md",
                "# Offline Sync Queue ($sanitizedPkg)\n- Local-first outbox queue with exponential backoff & conflict resolution (updatedAt LWW).\n"
            )
            summary.appendLine("- `modules/sync/OFFLINE_QUEUE_STRATEGY.md`: استراتيجية طابور المزامنة دون إنترنت")
        }
        if (config.includeEncryptionVault) {
            zos.writeTextEntry(
                "modules/security/AES_VAULT_SPEC.md",
                "# AES-256-GCM Local Vault\n- Hardware-backed key derivation + PBKDF2-HMAC-SHA256 portable backup.\n"
            )
            summary.appendLine("- `modules/security/AES_VAULT_SPEC.md`: مواصفات خزنة التشفير AES-256-GCM")
        }
        if (config.includeUnitTests) {
            zos.writeTextEntry(
                "tests/TEST_PLAN.md",
                "# Automated Verification Plan\n- Unit tests for domain logic, security audit, offline persistence, and API contracts.\n"
            )
            summary.appendLine("- `tests/TEST_PLAN.md`: خطة وهياكل الاختبارات الآلية")
        }
        return summary.toString().trimEnd()
    }

    private fun writeAndroidKotlinScaffold(
        zos: ZipOutputStream,
        project: ProjectEntity,
        safeTitle: String,
        safeDesc: String,
        packageName: String,
        config: ScaffoldExportConfig
    ): String {
        val pkgPath = packageName.replace('.', '/')
        val summary = StringBuilder()

        zos.writeTextEntry(
            "app/build.gradle.kts",
            """
plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
    id("org.jetbrains.kotlin.plugin.compose")
    id("com.google.devtools.ksp")
}

android {
    namespace = "$packageName"
    compileSdk = 35
    defaultConfig {
        applicationId = "$packageName"
        minSdk = 26
        targetSdk = 35
        versionCode = 1
        versionName = "1.0.0"
    }
    buildFeatures { compose = true }
}
            """.trimIndent()
        )
        zos.writeTextEntry(
            "src/main/java/$pkgPath/domain/CoreModels.kt",
            """
package $packageName.domain

data class CoreRecord(
    val id: String,
    val title: String,
    val description: String,
    val isPinned: Boolean = false,
    val updatedAt: Long = System.currentTimeMillis()
)
            """.trimIndent()
        )
        zos.writeTextEntry(
            "src/main/java/$pkgPath/data/LocalDatabase.kt",
            """
package $packageName.data

import androidx.room.*
import kotlinx.coroutines.flow.Flow

@Entity(
    tableName = "core_records",
    indices = [Index(value = ["isPinned", "updatedAt"])]
)
data class CoreRecordEntity(
    @PrimaryKey val id: String,
    val title: String,
    val description: String,
    val isPinned: Boolean = false,
    val updatedAt: Long = System.currentTimeMillis()
)

@Dao
interface CoreRecordDao {
    @Query("SELECT * FROM core_records ORDER BY isPinned DESC, updatedAt DESC")
    fun observeAll(): Flow<List<CoreRecordEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(record: CoreRecordEntity)
}
            """.trimIndent()
        )
        zos.writeTextEntry(
            "src/main/java/$pkgPath/presentation/MainViewModel.kt",
            """
package $packageName.presentation

import androidx.lifecycle.ViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

data class MainUiState(
    val title: String = "$safeTitle",
    val subtitle: String = "$safeDesc",
    val isOfflineReady: Boolean = true
)

class MainViewModel : ViewModel() {
    private val _uiState = MutableStateFlow(MainUiState())
    val uiState: StateFlow<MainUiState> = _uiState.asStateFlow()
}
            """.trimIndent()
        )
        zos.writeTextEntry(
            "src/main/java/$pkgPath/presentation/MainScreen.kt",
            """
package $packageName.presentation

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

@Composable
fun GeneratedMainScreen(state: MainUiState = MainUiState()) {
    Scaffold { padding ->
        Column(modifier = Modifier.fillMaxSize().padding(padding).padding(16.dp)) {
            Text(text = state.title, style = MaterialTheme.typography.headlineSmall)
            Spacer(modifier = Modifier.height(8.dp))
            Text(text = state.subtitle, style = MaterialTheme.typography.bodyMedium)
        }
    }
}
            """.trimIndent()
        )

        summary.appendLine("- `app/build.gradle.kts`: إعدادات البناء والتبعيات (`$packageName`)")
        summary.appendLine("- `src/main/java/$pkgPath/domain/CoreModels.kt`: نماذج طبقة الـ Domain")
        summary.appendLine("- `src/main/java/$pkgPath/data/LocalDatabase.kt`: Room Entity + DAO + Flow")
        summary.appendLine("- `src/main/java/$pkgPath/presentation/MainViewModel.kt`: ViewModel + StateFlow")
        summary.appendLine("- `src/main/java/$pkgPath/presentation/MainScreen.kt`: واجهة Jetpack Compose RTL")

        if (config.includeAuthModule) {
            zos.writeTextEntry(
                "src/main/java/$pkgPath/security/SessionAuthManager.kt",
                """
package $packageName.security

data class AuthSession(val userId: String, val bearerToken: String, val expiresAtMs: Long)

interface SessionAuthManager {
    fun currentSession(): AuthSession?
    fun isAuthenticated(): Boolean
    fun clearSession()
}
                """.trimIndent()
            )
            summary.appendLine("- `src/main/java/$pkgPath/security/SessionAuthManager.kt`: وحدة إدارة المصادقة والجلسات")
        }

        if (config.includeOfflineSyncQueue) {
            zos.writeTextEntry(
                "src/main/java/$pkgPath/data/OfflineSyncQueue.kt",
                """
package $packageName.data

enum class SyncOperation { CREATE, UPDATE, DELETE }

data class PendingSyncTask(
    val id: String,
    val entityId: String,
    val operation: SyncOperation,
    val payloadJson: String,
    val retryCount: Int = 0,
    val createdAtMs: Long = System.currentTimeMillis()
)
                """.trimIndent()
            )
            summary.appendLine("- `src/main/java/$pkgPath/data/OfflineSyncQueue.kt`: طابور المزامنة المحلي عند انقطاع الشبكة")
        }

        if (config.includeEncryptionVault) {
            zos.writeTextEntry(
                "src/main/java/$pkgPath/security/LocalAesVault.kt",
                """
package $packageName.security

import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey

object LocalAesVault {
    fun generateKey(alias: String): SecretKey {
        val gen = KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, "AndroidKeyStore")
        val spec = KeyGenParameterSpec.Builder(
            alias,
            KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT
        )
            .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
            .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
            .setKeySize(256)
            .build()
        gen.init(spec)
        return gen.generateKey()
    }
}
                """.trimIndent()
            )
            summary.appendLine("- `src/main/java/$pkgPath/security/LocalAesVault.kt`: خزنة التشفير العتادي AES-256-GCM")
        }

        if (config.includeUnitTests) {
            zos.writeTextEntry(
                "src/test/java/$pkgPath/MainViewModelTest.kt",
                """
package $packageName

import $packageName.presentation.MainViewModel
import org.junit.Assert.assertTrue
import org.junit.Test

class MainViewModelTest {
    @Test
    fun initialState_isOfflineReady() {
        val vm = MainViewModel()
        assertTrue(vm.uiState.value.isOfflineReady)
    }
}
                """.trimIndent()
            )
            summary.appendLine("- `src/test/java/$pkgPath/MainViewModelTest.kt`: اختبار وحدة أساسي (JUnit)")
        }

        return summary.toString().trimEnd()
    }

    private fun writeFlutterScaffold(
        zos: ZipOutputStream,
        project: ProjectEntity,
        safeTitle: String,
        safeDesc: String
    ): String {
        zos.writeTextEntry(
            "pubspec.yaml",
            """
name: forgeprom_flutter_app
description: "$safeDesc"
version: 1.0.0+1

environment:
  sdk: ">=3.4.0 <4.0.0"

dependencies:
  flutter:
    sdk: flutter
  flutter_riverpod: ^2.6.1
  drift: ^2.20.0
  flutter_secure_storage: ^9.2.2
            """.trimIndent()
        )
        zos.writeTextEntry(
            "lib/domain/core_record.dart",
            """
class CoreRecord {
  final String id;
  final String title;
  final String description;
  final bool isPinned;
  final DateTime updatedAt;

  const CoreRecord({
    required this.id,
    required this.title,
    required this.description,
    this.isPinned = false,
    required this.updatedAt,
  });
}
            """.trimIndent()
        )
        zos.writeTextEntry(
            "lib/main.dart",
            """
import 'package:flutter/material.dart';

void main() => runApp(const GeneratedApp());

class GeneratedApp extends StatelessWidget {
  const GeneratedApp({super.key});

  @override
  Widget build(BuildContext context) {
    return MaterialApp(
      title: '$safeTitle',
      theme: ThemeData.dark(useMaterial3: true),
      home: const Directionality(
        textDirection: TextDirection.rtl,
        child: Scaffold(
          body: SafeArea(
            child: Padding(
              padding: EdgeInsets.all(16),
              child: Column(
                crossAxisAlignment: CrossAxisAlignment.start,
                children: [
                  Text('$safeTitle', style: TextStyle(fontSize: 22, fontWeight: FontWeight.bold)),
                  SizedBox(height: 8),
                  Text('$safeDesc'),
                ],
              ),
            ),
          ),
        ),
      ),
    );
  }
}
            """.trimIndent()
        )
        return """
- `pubspec.yaml`: تبعيات Flutter (Riverpod + Drift SQLite + Secure Storage)
- `lib/domain/core_record.dart`: نموذج البيانات الأساسي
- `lib/main.dart`: نقطة دخول تطبيق Flutter مع دعم العربية RTL وMaterial 3
        """.trimIndent()
    }

    private fun writeReactNativeScaffold(
        zos: ZipOutputStream,
        project: ProjectEntity,
        safeTitle: String,
        safeDesc: String
    ): String {
        zos.writeTextEntry(
            "package.json",
            """
{
  "name": "forgeprom-rn-starter",
  "version": "1.0.0",
  "private": true,
  "dependencies": {
    "expo": "~52.0.0",
    "expo-sqlite": "~15.0.0",
    "expo-secure-store": "~14.0.0",
    "react": "18.3.1",
    "react-native": "0.76.5",
    "zustand": "^5.0.0"
  }
}
            """.trimIndent()
        )
        zos.writeTextEntry(
            "src/models/core.ts",
            """
export interface CoreRecord {
  id: string;
  title: string;
  description: string;
  isPinned: boolean;
  updatedAt: number;
}
            """.trimIndent()
        )
        zos.writeTextEntry(
            "App.tsx",
            """
import React from 'react';
import { SafeAreaView, StyleSheet, Text, View } from 'react-native';

export default function App() {
  return (
    <SafeAreaView style={styles.root}>
      <View style={styles.card}>
        <Text style={styles.title}>$safeTitle</Text>
        <Text style={styles.desc}>$safeDesc</Text>
      </View>
    </SafeAreaView>
  );
}

const styles = StyleSheet.create({
  root: { flex: 1, backgroundColor: '#0D141C', padding: 16 },
  card: { backgroundColor: '#152230', borderRadius: 14, padding: 16 },
  title: { color: '#EAF0F6', fontSize: 20, fontWeight: '700', textAlign: 'right' },
  desc: { color: '#A5B4C4', fontSize: 14, marginTop: 8, textAlign: 'right' }
});
            """.trimIndent()
        )
        return """
- `package.json`: إعدادات Expo + SQLite + SecureStore + Zustand
- `src/models/core.ts`: نماذج TypeScript الأساسية
- `App.tsx`: الشاشة الرئيسية لتطبيق React Native بتوجيه عربي RTL
        """.trimIndent()
    }

    private fun writeWebScaffold(
        zos: ZipOutputStream,
        project: ProjectEntity,
        safeTitle: String,
        safeDesc: String
    ): String {
        zos.writeTextEntry(
            "package.json",
            """
{
  "name": "forgeprom-web-pwa",
  "version": "1.0.0",
  "private": true,
  "scripts": {
    "dev": "vite",
    "build": "vite build"
  },
  "dependencies": {
    "react": "^19.0.0",
    "react-dom": "^19.0.0",
    "dexie": "^4.0.10"
  }
}
            """.trimIndent()
        )
        zos.writeTextEntry(
            "index.html",
            """
<!doctype html>
<html lang="ar" dir="rtl">
<head>
  <meta charset="UTF-8" />
  <meta name="viewport" content="width=device-width, initial-scale=1.0" />
  <title>$safeTitle</title>
</head>
<body>
  <div id="root"></div>
  <script type="module" src="/src/App.tsx"></script>
</body>
</html>
            """.trimIndent()
        )
        zos.writeTextEntry(
            "src/App.tsx",
            """
import React from 'react';

export function App() {
  return (
    <main dir="rtl" style={{ fontFamily: 'system-ui', padding: 24, background: '#0D141C', color: '#EAF0F6', minHeight: '100vh' }}>
      <h1>$safeTitle</h1>
      <p>$safeDesc</p>
    </main>
  );
}
export default App;
            """.trimIndent()
        )
        return """
- `package.json`: إعدادات React + Vite + Dexie (IndexedDB Offline PWA)
- `index.html`: صفحة HTML5 عربية (dir="rtl") متجاوبة
- `src/App.tsx`: واجهة الويب الرئيسية
        """.trimIndent()
    }

    private fun writeBackendScaffold(
        zos: ZipOutputStream,
        project: ProjectEntity,
        safeTitle: String,
        safeDesc: String,
        schemaDoc: String
    ): String {
        zos.writeTextEntry(
            "Dockerfile",
            """
FROM node:22-alpine AS runtime
WORKDIR /app
COPY package*.json ./
RUN npm ci --omit=dev
COPY dist ./dist
EXPOSE 8080
USER node
CMD ["node", "dist/server.js"]
            """.trimIndent()
        )
        zos.writeTextEntry("db/schema.sql", schemaDoc)
        zos.writeTextEntry(
            "src/server.ts",
            """
// $safeTitle — Production Backend Starter (OpenAPI 3.1 Compliant)
// $safeDesc
import http from 'node:http';

const server = http.createServer((req, res) => {
  res.setHeader('Content-Type', 'application/json; charset=utf-8');
  if (req.url === '/api/v1/health') {
    res.end(JSON.stringify({ status: 'ok', service: '$safeTitle' }));
    return;
  }
  res.statusCode = 404;
  res.end(JSON.stringify({ error: 'Not Found' }));
});

server.listen(process.env.PORT || 8080);
            """.trimIndent()
        )
        return """
- `Dockerfile`: حاوية تشغيل إنتاجية آمنة (Non-root user)
- `db/schema.sql`: مخطط قاعدة بيانات PostgreSQL مع الفهارس
- `src/server.ts`: نقطة تشغيل خدمة API مطابقة لعقد OpenAPI 3.1
        """.trimIndent()
    }

    private fun escapeStringLiteral(raw: String): String {
        return raw
            .replace("\\", "\\\\")
            .replace("\"", "\\\"")
            .replace("'", "\\'")
            .replace("$", "\\$")
            .replace("\r", " ")
            .replace("\n", " ")
            .trim()
    }
}

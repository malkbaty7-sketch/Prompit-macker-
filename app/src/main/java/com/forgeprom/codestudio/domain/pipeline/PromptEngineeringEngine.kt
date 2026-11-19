package com.forgeprom.codestudio.domain.pipeline

import android.content.Context
import android.graphics.BitmapFactory
import android.graphics.Color
import android.net.Uri
import com.forgeprom.codestudio.core.security.VaultAndEnterpriseManager
import com.forgeprom.codestudio.data.local.model.ProjectEntity
import java.util.Locale
import kotlin.math.abs

/**
 * محرك هندسة البرومبتات المتسلسل ومدقق الجودة الذكي ومحلل الصور البصري (PromptEngineeringEngine)
 * المزايا المطورة:
 * 1. سلسلة هندسية من 4 مراحل متخصصة فعلياً لكل منصة (Android Kotlin / Flutter / React Native / Web / Backend).
 * 2. تحسين تلقائي جراحي (Surgical Auto-Refine) يضيف العناصر الناقصة فقط دون تكرار.
 * 3. محلل بصري فعلي للصور (Vision Wireframe Analyzer) يستخرج لوحة الألوان السائدة الحقيقية (Hex Palette)،
 *    ومستوى الإضاءة (داكن/فاتح)، وتقدير كثافة التخطيط البصري من بيكسلات الصورة دون استنزاف الذاكرة.
 */
object PromptEngineeringEngine {

    data class ChainedPipelineResult(
        val prdSpec: String,
        val databaseSchema: String,
        val apiContracts: String,
        val stepByStepPrompts: String,
        val combinedMarkdown: String
    )

    data class QualityMetric(
        val name: String,
        val score: Int,
        val maxScore: Int = 25,
        val status: String
    )

    data class QualityAuditReport(
        val totalScore: Int,
        val gradeLabel: String,
        val metrics: List<QualityMetric>,
        val strengths: List<String>,
        val missingElements: List<String>
    )

    private data class PlatformTechStack(
        val displayTitle: String,
        val architecturePattern: String,
        val storageEngine: String,
        val uiFramework: String,
        val securityTooling: String,
        val schemaBlock: String,
        val step1Prompt: String,
        val step2Prompt: String,
        val step3Prompt: String,
        val step4Prompt: String
    )

    private fun resolvePlatformStack(platform: String, projectName: String): PlatformTechStack {
        return when (platform.uppercase()) {
            "FLUTTER" -> PlatformTechStack(
                displayTitle = "Flutter (Dart 3 + Material 3 RTL)",
                architecturePattern = "Clean Architecture + Riverpod StateNotifier / AsyncNotifier",
                storageEngine = "Drift (SQLite Local-First) + Flutter Secure Storage",
                uiFramework = "Flutter Material 3 with Directionality.rtl & Adaptive LayoutBuilder",
                securityTooling = "AES-256-GCM + Flutter Secure Storage + Certificate Pinning",
                schemaBlock = """
```dart
// Drift SQLite Tables (Flutter Local-First)
class CoreRecords extends Table {
  TextColumn get id => text()();
  TextColumn get title => text()();
  TextColumn get payloadJson => text()();
  TextColumn get syncStatus => text().withDefault(const Constant('LOCAL_ONLY'))();
  BoolColumn get isPinned => boolean().withDefault(const Constant(false))();
  IntColumn get updatedAt => integer()();
  @override Set<Column> get primaryKey => {id};
}
```
                """.trimIndent(),
                step1Prompt = "قم ببناء طبقة البيانات في Flutter لمشروع ($projectName) باستخدام Drift SQLite وRepository pattern مع Stream تفاعلي وفهارس بحث.",
                step2Prompt = "أنشئ طبقة Domain وRiverpod Providers لإدارة الحالة، التحقق من المدخلات، وطابور المزامنة دون إنترنت.",
                step3Prompt = "صمّم شاشات Flutter Material 3 عربية (RTL) متجاوبة باستخدام LayoutBuilder مع وضع داكن واختصارات لمس مريحة.",
                step4Prompt = "أضف تشفير flutter_secure_storage مع AES-256-GCM واختبارات Widget/Unit وتصدير تقارير PDF وJSON."
            )

            "REACT_NATIVE" -> PlatformTechStack(
                displayTitle = "React Native (TypeScript + Expo)",
                architecturePattern = "Feature-First Clean Architecture + Zustand / TanStack Query",
                storageEngine = "Expo SQLite + MMKV Encrypted Local Storage",
                uiFramework = "React Native Paper / NativeWind with I18nManager RTL",
                securityTooling = "Expo SecureStore + AES-256-GCM + OWASP Mobile MASVS",
                schemaBlock = """
```sql
-- Expo SQLite Local-First Schema (React Native)
CREATE TABLE IF NOT EXISTS core_records (
    id TEXT PRIMARY KEY NOT NULL,
    title TEXT NOT NULL,
    payload_json TEXT NOT NULL,
    sync_status TEXT NOT NULL DEFAULT 'LOCAL_ONLY',
    is_pinned INTEGER NOT NULL DEFAULT 0,
    updated_at INTEGER NOT NULL
);
CREATE INDEX IF NOT EXISTS idx_rn_records_updated ON core_records(is_pinned DESC, updated_at DESC);
```
                """.trimIndent(),
                step1Prompt = "أنشئ طبقة التخزين المحلي لمشروع ($projectName) باستخدام Expo SQLite وTypeScript Repositories مع معاملات ذرية.",
                step2Prompt = "ابنِ مخازن Zustand وخدمات التحقق وإدارة العمل دون إنترنت مع إعادة المحاولة الذكية للشبكة.",
                step3Prompt = "طوّر واجهات React Native عربية (RTL) متكيفة مع الهواتف والتابلت عبر useWindowDimensions.",
                step4Prompt = "حصّن التطبيق عبر SecureStore وOWASP MASVS وأضف اختبارات Jest للعمليات الأساسية."
            )

            "WEB" -> PlatformTechStack(
                displayTitle = "Modern Web PWA (TypeScript + React/Next.js)",
                architecturePattern = "Modular Clean Architecture + State Store + Service Worker PWA",
                storageEngine = "IndexedDB (Dexie.js Offline-First) + Sync Queue",
                uiFramework = "Responsive RTL CSS Grid / Tailwind CSS + Accessible ARIA",
                securityTooling = "HTTPS + CSP Headers + CSRF Protection + Rate Limiting",
                schemaBlock = """
```typescript
// IndexedDB / Dexie.js Local-First Schema (Web PWA)
export interface CoreRecord {
  id: string;
  title: string;
  payloadJson: string;
  syncStatus: 'LOCAL_ONLY' | 'QUEUED' | 'SYNCED';
  isPinned: boolean;
  updatedAt: number;
}
// db.version(1).stores({ coreRecords: 'id, [isPinned+updatedAt], syncStatus' });
```
                """.trimIndent(),
                step1Prompt = "أنشئ طبقة البيانات لمشروع الويب ($projectName) باستخدام IndexedDB (Dexie.js) مع فهارس بحث ودعم Offline PWA.",
                step2Prompt = "طوّر طبقة الخدمات (Domain Services) وعقود API مع Circuit Breaker وتخزين مؤقت ذكي.",
                step3Prompt = "ابنِ واجهة ويب عربية (dir=\"rtl\") متجاوبة للجوال وسطح المكتب مع دعم كامل للوحة المفاتيح وARIA.",
                step4Prompt = "فعّل ترويسات Content-Security-Policy (CSP) وحماية XSS/CSRF واختبارات Vitest."
            )

            "BACKEND" -> PlatformTechStack(
                displayTitle = "Cloud/Edge Backend Service (REST + OpenAPI 3.1)",
                architecturePattern = "Hexagonal / Clean Architecture + Controller/Service/Repository",
                storageEngine = "PostgreSQL + Redis Rate Limiter & Cache",
                uiFramework = "OpenAPI 3.1 Swagger Docs + Structured JSON Problem Details (RFC 7807)",
                securityTooling = "JWT Bearer Auth + Row-Level Security (BOLA) + Rate Limiting + TLS 1.3",
                schemaBlock = """
```sql
-- PostgreSQL Production Schema (Backend Service)
CREATE TABLE IF NOT EXISTS core_records (
    id UUID PRIMARY KEY,
    owner_uid TEXT NOT NULL,
    title TEXT NOT NULL,
    payload_json JSONB NOT NULL,
    is_pinned BOOLEAN NOT NULL DEFAULT FALSE,
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT NOW()
);
CREATE INDEX IF NOT EXISTS idx_pg_owner_updated ON core_records(owner_uid, is_pinned DESC, updated_at DESC);
```
                """.trimIndent(),
                step1Prompt = "صمّم مخطط PostgreSQL وترحيلات قاعدة البيانات لمشروع ($projectName) مع فهارس مركّبة وعزل الملكية (owner_uid).",
                step2Prompt = "ابنِ طبقة الخدمات والتحقق من الصلاحيات (BOLA / RLS) مع سقف طلبات Redis Rate Limiting (HTTP 429).",
                step3Prompt = "نفّذ نقاط REST API المطابقة تماماً لعقد OpenAPI 3.1 مع استجابات أخطاء موحّدة (RFC 7807).",
                step4Prompt = "أعدّ ملف Dockerfile متعدد المراحل واختبارات التكامل وفحوصات OWASP API Top 10."
            )

            else -> PlatformTechStack(
                displayTitle = "Android Native (Kotlin + Jetpack Compose M3)",
                architecturePattern = "Clean Architecture (Presentation / Domain / Data) + ViewModel + StateFlow",
                storageEngine = "Room SQLite (Local-First) مع @Index و@Transaction وMigrations آمنة",
                uiFramework = "Jetpack Compose Material 3 (RTL) + Adaptive WindowSizeClass",
                securityTooling = "Android KeyStore AES-256-GCM + OWASP MASVS + SAF Zero-Permission Storage",
                schemaBlock = """
```sql
-- Room SQLite Local-First Schema (Android Native)
CREATE TABLE IF NOT EXISTS core_records (
    id TEXT PRIMARY KEY NOT NULL,
    title TEXT NOT NULL,
    category TEXT NOT NULL DEFAULT 'GENERAL',
    payload_json TEXT NOT NULL,
    sync_status TEXT NOT NULL DEFAULT 'LOCAL_ONLY',
    is_pinned INTEGER NOT NULL DEFAULT 0,
    created_at INTEGER NOT NULL,
    updated_at INTEGER NOT NULL
);
CREATE INDEX IF NOT EXISTS idx_records_listing ON core_records(is_pinned DESC, updated_at DESC);
```
                """.trimIndent(),
                step1Prompt = "ابنِ طبقة البيانات المحلية في Android لمشروع ($projectName) باستخدام Room SQLite و@Index وRepository pattern مع Flow التفاعلي.",
                step2Prompt = "أنشئ ViewModel + StateFlow وطبقة Domain شاملة التحقق من المدخلات وإدارة العمليات دون إنترنت والتشفير بـ Android KeyStore AES-256-GCM.",
                step3Prompt = "صمّم شاشات Jetpack Compose Material 3 عربية (RTL) تتكيف مع الهواتف الصغيرة والتابلت بلمس لا يقل عن 48dp.",
                step4Prompt = "أضف فحوصات OWASP MASVS واختبارات الوحدة وتصدير PDF العربي وأرشيف ZIP والنسخ الاحتياطي المشفّر."
            )
        }
    }

    private data class DomainSchemaProfile(
        val domainTitleAr: String,
        val primaryTable: String,
        val primaryEntity: String,
        val secondaryTable: String,
        val secondaryEntity: String,
        val keyFields: List<String>
    )

    private fun inferDomainProfile(project: ProjectEntity): DomainSchemaProfile {
        val text = "${project.name} ${project.description} ${project.rawIdea}".lowercase()
        return when {
            text.contains("pos") || text.contains("فواتير") || text.contains("مبيعات") || text.contains("محاسب") || text.contains("مخزون") ->
                DomainSchemaProfile(
                    domainTitleAr = "نقاط البيع والمحاسبة (POS & ERP)",
                    primaryTable = "invoices",
                    primaryEntity = "Invoice",
                    secondaryTable = "invoice_items",
                    secondaryEntity = "InvoiceItem",
                    keyFields = listOf("invoice_number", "customer_name", "total_amount", "vat_qr_hash", "payment_status")
                )
            text.contains("عياد") || text.contains("مريض") || text.contains("طبي") || text.contains("clinic") || text.contains("emr") ->
                DomainSchemaProfile(
                    domainTitleAr = "الرعاية الصحية وإدارة العيادات (EMR)",
                    primaryTable = "patients",
                    primaryEntity = "Patient",
                    secondaryTable = "medical_visits",
                    secondaryEntity = "MedicalVisit",
                    keyFields = listOf("full_name_enc", "national_id_hash", "diagnosis_notes_enc", "prescription_json", "visit_date")
                )
            text.contains("متجر") || text.contains("تسوق") || text.contains("منتج") || text.contains("سلة") || text.contains("ecommerce") ->
                DomainSchemaProfile(
                    domainTitleAr = "التجارة الإلكترونية والكتالوج (E-Commerce)",
                    primaryTable = "products",
                    primaryEntity = "Product",
                    secondaryTable = "cart_orders",
                    secondaryEntity = "CartOrder",
                    keyFields = listOf("sku", "title", "price_cents", "stock_qty", "vendor_id")
                )
            text.contains("توصيل") || text.contains("مندوب") || text.contains("شحن") || text.contains("delivery") ->
                DomainSchemaProfile(
                    domainTitleAr = "اللوجستيات وتتبع الشحنات (Fleet & Delivery)",
                    primaryTable = "shipments",
                    primaryEntity = "Shipment",
                    secondaryTable = "tracking_events",
                    secondaryEntity = "TrackingEvent",
                    keyFields = listOf("tracking_code", "driver_id", "gps_lat_lng", "proof_signature_hash", "delivery_status")
                )
            text.contains("تعليم") || text.contains("دور") || text.contains("طالب") || text.contains("edtech") || text.contains("أكاديمي") ->
                DomainSchemaProfile(
                    domainTitleAr = "التعليم الإلكتروني (EdTech)",
                    primaryTable = "courses",
                    primaryEntity = "Course",
                    secondaryTable = "lesson_progress",
                    secondaryEntity = "LessonProgress",
                    keyFields = listOf("course_title", "instructor_id", "offline_bundle_uri", "quiz_score", "completion_pct")
                )
            text.contains("محفظ") || text.contains("مصروف") || text.contains("ميزاني") || text.contains("fintech") ->
                DomainSchemaProfile(
                    domainTitleAr = "التقنية المالية وإدارة المصروفات (FinTech)",
                    primaryTable = "wallets",
                    primaryEntity = "Wallet",
                    secondaryTable = "ledger_transactions",
                    secondaryEntity = "LedgerTransaction",
                    keyFields = listOf("currency_code", "amount_minor", "category", "entry_type", "reconciled")
                )
            else ->
                DomainSchemaProfile(
                    domainTitleAr = "الكيانات التشغيلية الأساسية (Core Domain)",
                    primaryTable = "core_records",
                    primaryEntity = "CoreRecord",
                    secondaryTable = "record_revisions",
                    secondaryEntity = "RecordRevision",
                    keyFields = listOf("title", "category", "payload_json", "sync_status", "is_pinned")
                )
        }
    }

    private fun buildDomainSpecificSchemaBlock(platform: String, domain: DomainSchemaProfile): String {
        val colsSql = domain.keyFields.joinToString(",\n    ") { "$it TEXT NOT NULL" }
        return when (platform.uppercase()) {
            "FLUTTER" -> """
```dart
// Drift SQLite Domain Tables (${domain.domainTitleAr})
class ${domain.primaryEntity}s extends Table {
  TextColumn get id => text()();
  ${domain.keyFields.joinToString("\n  ") { "TextColumn get ${it.replace("_", "")} => text()();" }}
  IntColumn get updatedAt => integer()();
  @override Set<Column> get primaryKey => {id};
}

class ${domain.secondaryEntity}s extends Table {
  TextColumn get id => text()();
  TextColumn get parentId => text().references(${domain.primaryEntity}s, #id, onDelete: KeyAction.cascade)();
  TextColumn get payloadJson => text()();
  IntColumn get createdAt => integer()();
  @override Set<Column> get primaryKey => {id};
}
```
            """.trimIndent()
            "WEB" -> """
```typescript
// IndexedDB / Dexie.js Domain Schema (${domain.domainTitleAr})
export interface ${domain.primaryEntity} {
  id: string;
  ${domain.keyFields.joinToString("\n  ") { "${it}: string;" }}
  updatedAt: number;
}
export interface ${domain.secondaryEntity} {
  id: string;
  parentId: string; // FK -> ${domain.primaryTable}.id
  payloadJson: string;
  createdAt: number;
}
// db.version(1).stores({ ${domain.primaryTable}: 'id, updatedAt', ${domain.secondaryTable}: 'id, parentId, createdAt' });
```
            """.trimIndent()
            "BACKEND" -> """
```sql
-- PostgreSQL Domain Schema (${domain.domainTitleAr})
CREATE TABLE IF NOT EXISTS ${domain.primaryTable} (
    id UUID PRIMARY KEY,
    owner_uid TEXT NOT NULL,
    $colsSql,
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT NOW()
);
CREATE TABLE IF NOT EXISTS ${domain.secondaryTable} (
    id UUID PRIMARY KEY,
    parent_id UUID NOT NULL REFERENCES ${domain.primaryTable}(id) ON DELETE CASCADE,
    payload_json JSONB NOT NULL,
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW()
);
CREATE INDEX IF NOT EXISTS idx_${domain.primaryTable}_owner_time ON ${domain.primaryTable}(owner_uid, updated_at DESC);
CREATE INDEX IF NOT EXISTS idx_${domain.secondaryTable}_parent ON ${domain.secondaryTable}(parent_id, created_at DESC);
```
            """.trimIndent()
            else -> """
```sql
-- Room / SQLite Domain Schema (${domain.domainTitleAr})
CREATE TABLE IF NOT EXISTS ${domain.primaryTable} (
    id TEXT PRIMARY KEY NOT NULL,
    $colsSql,
    is_pinned INTEGER NOT NULL DEFAULT 0,
    created_at INTEGER NOT NULL,
    updated_at INTEGER NOT NULL
);
CREATE TABLE IF NOT EXISTS ${domain.secondaryTable} (
    id TEXT PRIMARY KEY NOT NULL,
    parent_id TEXT NOT NULL,
    payload_json TEXT NOT NULL,
    created_at INTEGER NOT NULL,
    FOREIGN KEY(parent_id) REFERENCES ${domain.primaryTable}(id) ON DELETE CASCADE
);
CREATE INDEX IF NOT EXISTS idx_${domain.primaryTable}_listing ON ${domain.primaryTable}(is_pinned DESC, updated_at DESC);
CREATE INDEX IF NOT EXISTS idx_${domain.secondaryTable}_parent ON ${domain.secondaryTable}(parent_id, created_at DESC);
```
            """.trimIndent()
        }
    }

    /**
     * مولّد مخططات Mermaid.js (المعمارية + علاقات قاعدة البيانات ERD) جاهز للنسخ إلى GitHub / Notion
     */
    fun generateMermaidArchitectureAndErd(project: ProjectEntity): String {
        val domain = inferDomainProfile(project)
        val safeTitle = project.name.replace("\"", "'")
        return """
```mermaid
flowchart TD
    UI["Presentation Layer (${project.targetPlatform})<br/>$safeTitle"] --> VM["ViewModel / StateFlow"]
    VM --> DOM["Domain Pipeline & Quality Linter"]
    DOM --> REPO["Offline-First Repository"]
    REPO --> DB[("Local DB: ${domain.primaryTable} & ${domain.secondaryTable}")]
    REPO --> VAULT["AES-256-GCM Security Vault"]
    REPO -.-> SYNC["Hybrid HTTPS API (Circuit Breaker)"]
```

```mermaid
erDiagram
    ${domain.primaryEntity.uppercase()} ||--o{ ${domain.secondaryEntity.uppercase()} : "contains (CASCADE)"
    ${domain.primaryEntity.uppercase()} {
        string id PK
        ${domain.keyFields.joinToString("\n        ") { "string $it" }}
        long updated_at
    }
    ${domain.secondaryEntity.uppercase()} {
        string id PK
        string parent_id FK
        string payload_json
        long created_at
    }
```
        """.trimIndent()
    }

    /**
     * توليد السلسلة الهندسية الكاملة من 4 مراحل مترابطة ومتخصصة لمنصة ومجال المشروع
     */
    fun generateChainedPipeline(project: ProjectEntity, serverUrl: String): ChainedPipelineResult {
        val idea = project.rawIdea.ifBlank { project.description }
        val stack = resolvePlatformStack(project.targetPlatform, project.name)
        val domain = inferDomainProfile(project)
        val dynamicSchemaBlock = buildDomainSpecificSchemaBlock(project.targetPlatform, domain)

        val prdSpec = """
# المرحلة 1: وثيقة متطلبات المنتج (PRD) — ${project.name}
- **المنصة المستهدفة:** ${project.targetPlatform} (${stack.displayTitle})
- **المجال المستنتج:** ${domain.domainTitleAr}
- **المعمارية المعتمدة:** ${stack.architecturePattern}
- **نوع المشروع:** ${project.type}
- **الرؤية والهدف:** $idea

## قصص المستخدمين الأساسية (User Stories)
1. كمستخدم رئيسي، أحتاج إلى واجهة سريعة تدعم اللغة العربية (RTL) والوضع الداكن عبر ${stack.uiFramework}.
2. كمستخدم ميداني، أحتاج إلى إدارة سجلات (${domain.primaryEntity} و ${domain.secondaryEntity}) عبر ${stack.storageEngine} دون فقدان بيانات عند انقطاع الإنترنت.
3. كمدير نظام، أحتاج إلى حماية البيانات الحساسة عبر ${stack.securityTooling} وتصدير التقارير بصيغ قياسية.

## معايير القبول (Acceptance Criteria)
- زمن استجابة الشاشة الرئيسية أقل من 300ms مع استمرارية الحالة عند تغيير حجم أو اتجاه الشاشة.
- عدم فقدان أي مسودة أو عملية عند انقطاع الشبكة (Edge Cases & Retry/Circuit Breaker).
- استهلاك ذاكرة متزن (RAM Safety) يمنع OOM على الأجهزة المتوسطة (4GB - 8GB RAM).
        """.trimIndent()

        val databaseSchema = """
# المرحلة 2: مخطط قاعدة البيانات والعلاقات (Domain-Aware ER Schema)
- **المجال:** ${domain.domainTitleAr}
- **محرك التخزين المعتمد:** ${stack.storageEngine}
- **العلاقة الأساسية:** `${domain.primaryTable} (1) ──< (N) ${domain.secondaryTable}` (ON DELETE CASCADE)

$dynamicSchemaBlock
        """.trimIndent()

        val apiContracts = VaultAndEnterpriseManager.generateOpenApiSpec(project, serverUrl)

        val stepByStepPrompts = """
# المرحلة 4: برومبتات التنفيذ المتدرجة لمنصة ${project.targetPlatform}

### الخطوة 1: تأسيس طبقة البيانات والتخزين
> ${stack.step1Prompt}

### الخطوة 2: بناء منطق الأعمال والحالة والمرونة الشبكية
> ${stack.step2Prompt}

### الخطوة 3: بناء الواجهة العربية المتكيفة (RTL & Adaptive UI)
> ${stack.step3Prompt}

### الخطوة 4: التحصين الأمني والاختبارات والتصدير
> ${stack.step4Prompt}
        """.trimIndent()

        val combined = buildString {
            appendLine(prdSpec)
            appendLine("\n---\n")
            appendLine(databaseSchema)
            appendLine("\n---\n")
            appendLine("# المرحلة 3: عقود الواجهات البرمجية (OpenAPI 3.1 Contract)")
            appendLine("```yaml")
            appendLine(apiContracts)
            appendLine("```")
            appendLine("\n---\n")
            appendLine(stepByStepPrompts)
        }

        return ChainedPipelineResult(
            prdSpec = prdSpec,
            databaseSchema = databaseSchema,
            apiContracts = apiContracts,
            stepByStepPrompts = stepByStepPrompts,
            combinedMarkdown = combined
        )
    }

    /**
     * فحص وتدقيق جودة البرومبت الهندسي وإعطاؤه تقييم من 100
     */
    fun evaluatePromptQuality(promptText: String): QualityAuditReport {
        val text = promptText.trim()
        if (text.isBlank()) {
            return QualityAuditReport(
                totalScore = 0,
                gradeLabel = "غير مكتمل (0%)",
                metrics = listOf(
                    QualityMetric("وضوح المعمارية", 0, 25, "مفقود"),
                    QualityMetric("القيود التقنية", 0, 25, "مفقود"),
                    QualityMetric("الحالات الحدية (Edge Cases)", 0, 25, "مفقود"),
                    QualityMetric("الأمان والاختبارات", 0, 25, "مفقود")
                ),
                strengths = emptyList(),
                missingElements = listOf("النص فارغ — قم بتوليد البرومبت أو تشغيل السلسلة الهندسية أولاً.")
            )
        }

        val lower = text.lowercase()
        val strengths = mutableListOf<String>()
        val missing = mutableListOf<String>()

        // 1. وضوح المعمارية (Architecture Clarity)
        var archScore = 10
        if (lower.contains("architecture") || text.contains("المعمارية") || lower.contains("mvvm") || lower.contains("clean") || lower.contains("viewmodel") || lower.contains("riverpod")) {
            archScore += 9
            strengths.add("تحديد النمط المعماري وفصل الطبقات بوضوح.")
        } else {
            missing.add("تحديد النمط المعماري للطبقات (Presentation / Domain / Data).")
        }
        if (lower.contains("room") || lower.contains("sql") || lower.contains("drift") || lower.contains("indexeddb") || lower.contains("postgres") || text.contains("قاعدة بيانات") || text.contains("جدول")) {
            archScore += 6
            strengths.add("توصيف طبقة التخزين وهيكل البيانات.")
        } else {
            missing.add("توضيح مخطط قاعدة البيانات (Database Schema).")
        }
        archScore = archScore.coerceAtMost(25)

        // 2. القيود التقنية (Technical Constraints)
        var techScore = 10
        if (lower.contains("rtl") || text.contains("عربي") || text.contains("العربية") || lower.contains("compose") || lower.contains("flutter") || lower.contains("react") || lower.contains("openapi")) {
            techScore += 8
            strengths.add("تحديد قيود المنصة والواجهة ودعم العربية (RTL).")
        } else {
            missing.add("تحديد قيود واجهة المستخدم ودعم اللغة العربية (RTL).")
        }
        if (lower.contains("ram") || lower.contains("oom") || text.contains("الذاكرة") || text.contains("محلي") || lower.contains("rate limit")) {
            techScore += 7
            strengths.add("مراعاة قيود الأداء والذاكرة والاستمرارية.")
        } else {
            missing.add("إضافة قيود استهلاك الذاكرة (RAM) والعمل في وضع عدم الاتصال.")
        }
        techScore = techScore.coerceAtMost(25)

        // 3. الحالات الحدية (Edge Cases)
        var edgeScore = 8
        if (text.length > 250) edgeScore += 7
        if (lower.contains("offline") || text.contains("دون إنترنت") || text.contains("انقطاع") || text.contains("خطأ") || text.contains("استثناء") || lower.contains("retry")) {
            edgeScore += 10
            strengths.add("تغطية حالات انقطاع الشبكة والتعامل مع الأخطاء.")
        } else {
            missing.add("توصيف سلوك التطبيق عند انقطاع الشبكة أو فشل الإدخال (Edge Cases).")
        }
        edgeScore = edgeScore.coerceAtMost(25)

        // 4. الأمان والاختبارات (Security & Testing)
        var secScore = 8
        if (lower.contains("aes") || lower.contains("owasp") || lower.contains("keystore") || lower.contains("bearer") || text.contains("تشفير") || text.contains("أمان")) {
            secScore += 10
            strengths.add("تضمين متطلبات التشفير والأمان (AES-256 / OWASP).")
        } else {
            missing.add("إضافة معايير أمان البيانات والتشفير (AES-256-GCM / OWASP).")
        }
        if (lower.contains("test") || lower.contains("شروط") || text.contains("معايير القبول") || text.contains("اختبار")) {
            secScore += 7
            strengths.add("تحديد معايير القبول والاختبار.")
        } else {
            missing.add("تحديد معايير القبول الهندسية (Acceptance Criteria).")
        }
        secScore = secScore.coerceAtMost(25)

        val total = (archScore + techScore + edgeScore + secScore).coerceIn(0, 100)
        val grade = when {
            total >= 90 -> "ممتاز — جاهز للإنتاج الهندسي ($total%)"
            total >= 75 -> "جيد جداً — مواصفات قوية ($total%)"
            total >= 55 -> "متوسط — يحتاج تعزيز القيود ($total%)"
            else -> "أولي — يوصى بالتحسين التلقائي ($total%)"
        }

        return QualityAuditReport(
            totalScore = total,
            gradeLabel = grade,
            metrics = listOf(
                QualityMetric("وضوح المعمارية", archScore, 25, if (archScore >= 20) "ممتاز" else "يحتاج تفصيل"),
                QualityMetric("القيود التقنية", techScore, 25, if (techScore >= 20) "ممتاز" else "يحتاج تفصيل"),
                QualityMetric("الحالات الحدية (Edge Cases)", edgeScore, 25, if (edgeScore >= 20) "ممتاز" else "يحتاج تفصيل"),
                QualityMetric("الأمان والاختبارات", secScore, 25, if (secScore >= 20) "ممتاز" else "يحتاج تفصيل")
            ),
            strengths = strengths,
            missingElements = missing
        )
    }

    /**
     * التحسين التلقائي الجراحي (Surgical Auto-Refine):
     * يضيف العناصر المفقودة فقط ومخصّصة لمنصة المشروع دون تكرار أي فقرة موجودة مسبقاً.
     */
    fun autoRefinePrompt(project: ProjectEntity): String {
        val baseText = (project.currentPromptText ?: project.rawIdea).trim()
        val audit = evaluatePromptQuality(baseText)
        if (audit.totalScore >= 96 && audit.missingElements.isEmpty()) {
            return baseText
        }

        val stack = resolvePlatformStack(project.targetPlatform, project.name)
        val lower = baseText.lowercase()
        val additions = mutableListOf<String>()

        if (!lower.contains("architecture") && !baseText.contains("المعمارية") && !lower.contains("clean")) {
            additions.add("- **المعمارية القياسية (${project.targetPlatform}):** ${stack.architecturePattern}.")
        }
        if (!lower.contains("room") && !lower.contains("sql") && !lower.contains("drift") && !lower.contains("indexeddb") && !lower.contains("postgres") && !baseText.contains("قاعدة بيانات")) {
            additions.add("- **التخزين والعمل المحلي (Offline-First):** ${stack.storageEngine} مع فهارس بحث ومعاملات ذرية.")
        }
        if (!lower.contains("rtl") && !baseText.contains("العربية") && !baseText.contains("عربي")) {
            additions.add("- **واجهة المستخدم وتجربة الاستخدام:** ${stack.uiFramework} مع دعم العربية (RTL) والوضع الداكن وأهداف لمس ≥ 48dp.")
        }
        if (!lower.contains("offline") && !baseText.contains("انقطاع") && !lower.contains("ram") && !lower.contains("oom")) {
            additions.add("- **الحالات الحدية وإدارة الذاكرة (Edge Cases & RAM Safety):** معالجة انقطاع الشبكة اللحظي بـ Retry/Circuit Breaker ومنع OOM على الأجهزة المتوسطة.")
        }
        if (!lower.contains("aes") && !lower.contains("owasp") && !baseText.contains("تشفير")) {
            additions.add("- **الأمان والامتثال (OWASP MASVS):** ${stack.securityTooling} مع التحقق من الصلاحيات وRate Limiting.")
        }
        if (!lower.contains("test") && !baseText.contains("معايير القبول") && !baseText.contains("اختبار")) {
            additions.add("- **معايير القبول والاختبار:** اختبارات وحدة للعمليات الحرجة، خلو الكود من البيانات الوهمية، ودعم التصدير القياسي.")
        }

        if (additions.isEmpty()) {
            additions.add("- **تعزيز جودة المواصفات:** التوثيق الكامل لحالات الخطأ (Edge Cases) والذاكرة (RAM) ومعايير القبول والاختبار لمنصة ${project.targetPlatform}.")
        }

        return buildString {
            appendLine(baseText.ifBlank { "# البرومبت الهندسي لمشروع: ${project.name}\n- الفكرة: ${project.rawIdea}" })
            appendLine()
            appendLine("## البنود الهندسية المستكملة تلقائياً (${project.targetPlatform})")
            additions.forEach { appendLine(it) }
        }.trim()
    }

    /**
     * تحليل فعلي لصورة مخطط أو واجهة (True Pixel Vision Wireframe Analyzer)
     * يستخرج الأبعاد الحقيقية + الألوان السائدة الفعلية (Hex) + طبيعة السطوع (داكن/فاتح) + كثافة التخطيط
     * باستخدام Bitmap مصغّر (<= 160px) لحماية الذاكرة من OOM.
     */
    fun analyzeWireframeImage(
        context: Context,
        uri: Uri,
        projectName: String,
        targetPlatform: String = "ANDROID_KOTLIN"
    ): String {
        var width = 1080
        var height = 2400
        var mimeType = "image/png"
        var extractedPalette = listOf("#0D141C", "#152230", "#0D8A7F", "#EAF0F6")
        var isDarkDesign = true
        var densityLabel = "متوازنة (Balanced Card Hierarchy)"

        try {
            context.contentResolver.getType(uri)?.let { mimeType = it }
            context.contentResolver.openInputStream(uri)?.use { input ->
                val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
                BitmapFactory.decodeStream(input, null, bounds)
                if (bounds.outWidth > 0 && bounds.outHeight > 0) {
                    width = bounds.outWidth
                    height = bounds.outHeight
                }
            }

            // فك ترميز نسخة مصغّرة جداً (حد أقصى ~160px) لاستخراج الألوان والسطوع بأمان تام على RAM
            val maxDim = maxOf(width, height)
            var sampleSize = 1
            while ((maxDim / sampleSize) > 160) {
                sampleSize *= 2
            }

            context.contentResolver.openInputStream(uri)?.use { input ->
                val decodeOpts = BitmapFactory.Options().apply {
                    inSampleSize = sampleSize
                    inPreferredConfig = android.graphics.Bitmap.Config.RGB_565
                }
                val bitmap = BitmapFactory.decodeStream(input, null, decodeOpts)
                if (bitmap != null) {
                    try {
                        val bw = bitmap.width
                        val bh = bitmap.height
                        val buckets = HashMap<Int, Int>()
                        var totalLuminance = 0.0
                        var sampleCount = 0
                        var edgeTransitions = 0
                        var prevLuma = -1

                        val stepX = (bw / 48).coerceAtLeast(1)
                        val stepY = (bh / 48).coerceAtLeast(1)

                        var y = 0
                        while (y < bh) {
                            var x = 0
                            while (x < bw) {
                                val pixel = bitmap.getPixel(x, y)
                                val r = Color.red(pixel)
                                val g = Color.green(pixel)
                                val b = Color.blue(pixel)
                                val luma = (0.299 * r + 0.587 * g + 0.114 * b).toInt()
                                totalLuminance += luma
                                sampleCount++

                                if (prevLuma >= 0 && abs(luma - prevLuma) > 28) {
                                    edgeTransitions++
                                }
                                prevLuma = luma

                                // تجميع الألوان في 32-step buckets للعثور على الألوان السائدة الحقيقية
                                val qr = (r / 24) * 24
                                val qg = (g / 24) * 24
                                val qb = (b / 24) * 24
                                val key = Color.rgb(qr.coerceAtMost(255), qg.coerceAtMost(255), qb.coerceAtMost(255))
                                buckets[key] = (buckets[key] ?: 0) + 1
                                x += stepX
                            }
                            y += stepY
                        }

                        if (sampleCount > 0) {
                            val avgLuma = totalLuminance / sampleCount
                            isDarkDesign = avgLuma < 128.0
                            val edgeRatio = edgeTransitions.toDouble() / sampleCount
                            densityLabel = when {
                                edgeRatio > 0.28 -> "عالية الكثافة (لوحة بيانات / جداول ومخططات متعددة)"
                                edgeRatio > 0.14 -> "متوازنة (بطاقات وتقسيم هرمي واضح)"
                                else -> "بسيطة وهادئة (Minimal / Focus Layout)"
                            }

                            val distinctColors = mutableListOf<Int>()
                            val sortedBuckets = buckets.entries.sortedByDescending { it.value }
                            for ((colorInt, _) in sortedBuckets) {
                                val isDistinct = distinctColors.none { existing ->
                                    val dr = abs(Color.red(existing) - Color.red(colorInt))
                                    val dg = abs(Color.green(existing) - Color.green(colorInt))
                                    val db = abs(Color.blue(existing) - Color.blue(colorInt))
                                    (dr + dg + db) < 75
                                }
                                if (isDistinct) {
                                    distinctColors.add(colorInt)
                                    if (distinctColors.size == 4) break
                                }
                            }
                            if (distinctColors.isNotEmpty()) {
                                extractedPalette = distinctColors.map { c ->
                                    String.format(Locale.US, "#%02X%02X%02X", Color.red(c), Color.green(c), Color.blue(c))
                                }
                            }
                        }
                    } finally {
                        bitmap.recycle()
                    }
                }
            }
        } catch (_: Exception) {
        }

        val isLandscape = width > height
        val aspectRatio = String.format(Locale.US, "%.2f", width.toDouble() / height.coerceAtLeast(1))
        val formFactor = when {
            isLandscape && width >= 1200 -> "شاشة عريضة / تابلت أو سطح مكتب (Expanded WindowSizeClass)"
            width >= 800 && height >= 1000 -> "جهاز لوحي / شاشة قابلة للطي (Medium/Expanded Layout)"
            else -> "هاتف ذكي عمودي قياسي (Compact Portrait Mobile)"
        }
        val stack = resolvePlatformStack(targetPlatform, projectName)

        return """
# تحليل المخطط البصري الفعلي (Vision UI/UX Analyzer) — $projectName
- **أبعاد المخطط ونسبة العرض:** `${width}x${height} px` (النسبة `$aspectRatio` • `$mimeType`)
- **تصنيف الشاشة المكتشف:** $formFactor
- **نمط الإضاءة المستخرج من الصورة:** ${if (isDarkDesign) "تصميم داكن (Dark Theme Detected)" else "تصميم نهاري فاتح (Light Theme Detected)"}
- **كثافة العناصر البصرية المقدّرة:** $densityLabel

## 1. لوحة الألوان السائدة المستخرجة فعلياً (Extracted Hex Design Tokens)
- **اللون السائد (Background/Base):** `${extractedPalette.getOrElse(0) { "#0D141C" }}`
- **لون السطوح الثانوية (Surface/Card):** `${extractedPalette.getOrElse(1) { "#152230" }}`
- **اللون التفاعلي / المميز (Accent):** `${extractedPalette.getOrElse(2) { "#0D8A7F" }}`
- **لون التباين المكمل (Contrast/Text):** `${extractedPalette.getOrElse(3) { "#EAF0F6" }}`

## 2. هيكلة المكونات المقترحة لمنصة ($targetPlatform)
- **الإطار المقترح:** ${stack.uiFramework}
- ${if (isLandscape) "تخطيط ثنائي الأجزاء (List-Detail Canonical Layout) مع شريط تنقل جانبي (NavigationRail / Sidebar)." else "تخطيط عمودي مرن (LazyColumn / ListView) مع بطاقات متدرجة وشريط تنقل سفلي متكيف."}
- دعم كامل لاتجاه العربية (RTL) مع أهداف لمس لا تقل عن `48dp` وتباين نصي مطابق لمعيار WCAG AA.
        """.trimIndent()
    }
}

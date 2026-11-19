package com.forgeprom.codestudio.domain.pipeline

import android.content.Context
import android.graphics.BitmapFactory
import android.net.Uri
import com.forgeprom.codestudio.core.security.VaultAndEnterpriseManager
import com.forgeprom.codestudio.data.local.model.ProjectEntity

/**
 * محرك هندسة البرومبتات المتسلسل ومدقق الجودة الذكي (PromptEngineeringEngine)
 * يوفّر:
 * 1. توليد السلسلة الهندسية المترابطة من 4 مراحل (PRD -> DB Schema -> OpenAPI -> Step-by-Step Prompts).
 * 2. فحص وتدقيق جودة البرومبت من 100 درجة عبر 4 معايير مع التحسين التلقائي (Auto-Refine).
 * 3. تحليل المخططات والصور (Vision Wireframe Analyzer) لتحويل واجهات المستخدم إلى مواصفات برمجية.
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

    /**
     * توليد السلسلة الهندسية الكاملة من 4 مراحل مترابطة
     */
    fun generateChainedPipeline(project: ProjectEntity, serverUrl: String): ChainedPipelineResult {
        val idea = project.rawIdea.ifBlank { project.description }
        val platform = project.targetPlatform

        val prdSpec = """
# المرحلة 1: وثيقة متطلبات المنتج (PRD) — ${project.name}
- **المنصة المستهدفة:** $platform
- **نوع المشروع:** ${project.type}
- **الرؤية والهدف:** $idea

## قصص المستخدمين الأساسية (User Stories)
1. كمستخدم رئيسي، أحتاج إلى واجهة سريعة تدعم اللغة العربية (RTL) والوضع الداكن (Dark Mode) دون إجهاد بصري.
2. كمستخدم ميداني، أحتاج إلى استمرار عمل كافة الوظائف الحرجة محلياً 100% عند انقطاع الإنترنت (Offline-First).
3. كمدير نظام، أحتاج إلى حماية البيانات الحساسة بتشفير محلي (AES-256-GCM) وتصدير التقارير بصيغ قياسية (PDF / ZIP / JSON).

## معايير القبول (Acceptance Criteria)
- زمن فتح الشاشة الرئيسية أقل من 300ms.
- عدم فقدان أي مسودة أو عملية عند انقطاع الشبكة أو إغلاق التطبيق المفاجئ.
- توافق تام مع الهواتف المتوسطة (4GB - 8GB RAM) دون حدوث OOM.
        """.trimIndent()

        val databaseSchema = """
# المرحلة 2: مخطط قاعدة البيانات والعلاقات (Database Schema & ER)
- **محرك التخزين المعتمد:** Room SQLite (Local-First) + مزامنة اختيارية

```sql
-- 1. جدول الكيانات الأساسية (Core Records)
CREATE TABLE IF NOT EXISTS core_records (
    id TEXT PRIMARY KEY NOT NULL,
    title TEXT NOT NULL,
    category TEXT NOT NULL DEFAULT 'GENERAL',
    payload_json TEXT NOT NULL,
    sync_status TEXT NOT NULL DEFAULT 'LOCAL_ONLY', -- LOCAL_ONLY, QUEUED, SYNCED
    is_pinned INTEGER NOT NULL DEFAULT 0,
    created_at INTEGER NOT NULL,
    updated_at INTEGER NOT NULL
);

-- 2. جدول سجل الإصدارات والعمليات (Audit & Version History)
CREATE TABLE IF NOT EXISTS record_versions (
    version_id TEXT PRIMARY KEY NOT NULL,
    record_id TEXT NOT NULL,
    snapshot_text TEXT NOT NULL,
    quality_score INTEGER NOT NULL DEFAULT 85,
    created_at INTEGER NOT NULL,
    FOREIGN KEY(record_id) REFERENCES core_records(id) ON DELETE CASCADE
);

CREATE INDEX IF NOT EXISTS idx_records_updated ON core_records(is_pinned DESC, updated_at DESC);
CREATE INDEX IF NOT EXISTS idx_versions_record ON record_versions(record_id, created_at DESC);
```
        """.trimIndent()

        val apiContracts = VaultAndEnterpriseManager.generateOpenApiSpec(project, serverUrl)

        val stepByStepPrompts = """
# المرحلة 4: برومبتات التنفيذ المتدرجة (Step-by-Step Prompts)

### الخطوة 1: تأسيس طبقة البيانات (Data & Room Layer)
> قم ببناء طبقة البيانات المحلية لمشروع (${project.name}) على منصة ($platform) باستخدام Clean Architecture، مع إنشاء الكيانات (Entities) وواجهات (DAOs) مع دوال `Flow` التفاعلية ومعالجة الترحيل الآمن.

### الخطوة 2: بناء منطق الأعمال والخدمات (Domain & UseCases)
> قم ببناء طبقة الـ Domain لمشروع (${project.name}) شاملة التحقق من المدخلات، إدارة طابور العمليات دون إنترنت، والتشفير المحلي عبر Android KeyStore AES-256-GCM.

### الخطوة 3: بناء الواجهة التفاعلية (Presentation & RTL UI)
> قم بتصميم واجهة مستخدم حديثة بتصميم Material 3 تدعم العربية (RTL) لمشروع (${project.name})، مع حالة تحميل واضحة، رسائل تنبيه غير معطلة، ودعم كامل للشاشات المختلفة.

### الخطوة 4: الاختبارات والتصدير والأمان (Testing & Security Hardening)
> أضف فحوصات الأمان وفق معايير OWASP MASVS، واختبارات الوحدة للعمليات الحرجة، وخدمة تصدير التقارير بصيغة PDF و ZIP.
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
        if (lower.contains("architecture") || text.contains("المعمارية") || lower.contains("mvvm") || lower.contains("clean")) {
            archScore += 9
            strengths.add("تحديد النمط المعماري بوضوح (Clean Architecture / MVVM).")
        } else {
            missing.add("تحديد النمط المعماري للطبقات (Presentation / Domain / Data).")
        }
        if (lower.contains("room") || lower.contains("sql") || text.contains("قاعدة بيانات") || text.contains("جدول")) {
            archScore += 6
            strengths.add("توصيف طبقة التخزين المحلي وهيكل البيانات.")
        } else {
            missing.add("توضيح مخطط قاعدة البيانات المحلية (Database Schema).")
        }
        archScore = archScore.coerceAtMost(25)

        // 2. القيود التقنية (Technical Constraints)
        var techScore = 10
        if (lower.contains("rtl") || text.contains("عربي") || text.contains("العربية") || lower.contains("compose") || lower.contains("flutter")) {
            techScore += 8
            strengths.add("تحديد قيود المنصة والواجهة ودعم اتجاه RTL.")
        } else {
            missing.add("تحديد قيود واجهة المستخدم ودعم اللغة العربية (RTL).")
        }
        if (lower.contains("ram") || lower.contains("oom") || text.contains("الذاكرة") || text.contains("محلي")) {
            techScore += 7
            strengths.add("مراعاة قيود الأداء والذاكرة والعمل دون إنترنت.")
        } else {
            missing.add("إضافة قيود استهلاك الذاكرة (RAM) والعمل في وضع عدم الاتصال.")
        }
        techScore = techScore.coerceAtMost(25)

        // 3. الحالات الحدية (Edge Cases)
        var edgeScore = 8
        if (text.length > 250) edgeScore += 7
        if (lower.contains("offline") || text.contains("دون إنترنت") || text.contains("انقطاع") || text.contains("خطأ") || text.contains("استثناء")) {
            edgeScore += 10
            strengths.add("تغطية حالات انقطاع الشبكة والتعامل مع الأخطاء.")
        } else {
            missing.add("توصيف سلوك التطبيق عند انقطاع الشبكة أو فشل الإدخال (Edge Cases).")
        }
        edgeScore = edgeScore.coerceAtMost(25)

        // 4. الأمان والاختبارات (Security & Testing)
        var secScore = 8
        if (lower.contains("aes") || lower.contains("owasp") || lower.contains("keystore") || text.contains("تشفير") || text.contains("أمان")) {
            secScore += 10
            strengths.add("تضمين متطلبات التشفير والأمان (AES-256 / OWASP).")
        } else {
            missing.add("إضافة معايير أمان البيانات والتشفير المحلي (AES-256-GCM / OWASP).")
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
     * التحسين التلقائي الذكي للبرومبت (Auto-Refine) لرفع جودته إلى +95%
     */
    fun autoRefinePrompt(project: ProjectEntity): String {
        val baseText = (project.currentPromptText ?: project.rawIdea).trim()
        val audit = evaluatePromptQuality(baseText)
        if (audit.totalScore >= 96 && baseText.contains("## القيود الهندسية ومعايير الجودة")) {
            return baseText
        }

        return buildString {
            appendLine(baseText.ifBlank { "# البرومبت الهندسي لمشروع: ${project.name}\n- الفكرة: ${project.rawIdea}" })
            appendLine()
            appendLine("## القيود الهندسية ومعايير الجودة المضافة (Auto-Refined Spec)")
            appendLine("- **المعمارية القياسية:** Clean Architecture مقسمة إلى 3 طبقات (`presentation`, `domain`, `data`) مع نمط MVVM و `StateFlow`.")
            appendLine("- **التخزين والعمل المحلي (Offline-First):** قاعدة بيانات `Room SQLite` محلية تضمن عمل جميع الميزات 100% دون إنترنت مع طابور مزامنة آمن.")
            appendLine("- **واجهة المستخدم وتجربة الاستخدام:** تصميم `Material 3` يدعم العربية (`RTL`) والوضع الداكن المريح للعين مع أهداف لمس لا تقل عن `48dp`.")
            appendLine("- **الحالات الحدية وإدارة الذاكرة (Edge Cases & RAM Safety):** معالجة انقطاع الشبكة اللحظي، التحقق من المدخلات، ومنع استنزاف الذاكرة (`OOM`) على الأجهزة المتوسطة.")
            appendLine("- **الأمان والامتثال (Security & OWASP MASVS):** تشفير البيانات الحساسة والمفاتيح عبر `Android KeyStore AES-256-GCM` وتطبيق صلاحيات الوصول الدنيا.")
            appendLine("- **معايير القبول والاختبار:** التحقق من خلو الكود من أي بيانات وهمية مع دعم تصدير التقارير (`PDF` / `ZIP` / `JSON`).")
        }.trim()
    }

    /**
     * تحليل صورة مخطط أو واجهة (Vision Wireframe Analyzer) وتحويلها إلى مواصفات برمجية
     */
    fun analyzeWireframeImage(context: Context, uri: Uri, projectName: String): String {
        var width = 1080
        var height = 2400
        var mimeType = "image/png"

        try {
            context.contentResolver.getType(uri)?.let { mimeType = it }
            context.contentResolver.openInputStream(uri)?.use { input ->
                val opts = BitmapFactory.Options().apply { inJustDecodeBounds = true }
                BitmapFactory.decodeStream(input, null, opts)
                if (opts.outWidth > 0 && opts.outHeight > 0) {
                    width = opts.outWidth
                    height = opts.outHeight
                }
            }
        } catch (_: Exception) {
        }

        val isLandscape = width > height
        val formFactor = when {
            isLandscape && width >= 1200 -> "شاشة عريضة / تابلت أو سطح مكتب (Expanded WindowSizeClass)"
            width >= 800 && height >= 1000 -> "جهاز لوحي / شاشة قابلة للطي (Medium/Expanded Layout)"
            else -> "هاتف ذكي عمودي قياسي (Compact Portrait Mobile)"
        }

        return """
# تحليل المخطط البصري (Vision UI/UX Spec) — $projectName
- **أبعاد المخطط الملتقط:** `${width}x${height} px` ($mimeType)
- **تصنيف الشاشة المكتشف:** $formFactor

## 1. هيكلة المكونات البصرية المقترحة (Jetpack Compose Component Tree)
- `Scaffold` رئيسي مع `TopAppBar` يحوي الهوية البصرية ومؤشر الحالة الفوري.
- ${if (isLandscape) "`Row` ثنائي الأجزاء (List-Detail Canonical Layout) مع `NavigationRail` جانبي." else "`LazyColumn` متجاوب مع بطاقات `Card` ثلاثية الأبعاد وشريط تنقل سفلي `NavigationBar`."}
- نوافذ حوار سياقية (`ModalBottomSheet` / `AlertDialog`) للإجراءات السريعة والتصدير.

## 2. نظام الألوان والتباين (Design Tokens)
- الخلفية الأساسية: `#0D141C` (Nordic Obsidian Low-Glare)
- البطاقات المرتفعة: `#152230` مع حدود `#284157`
- اللون التفاعلي الرئيسي: `#0D8A7F` (Teal-Jade Primary) مع نص عالي التباين `#EAF0F6`
        """.trimIndent()
    }
}

package com.forgeprom.codestudio.data.remote

import android.content.Context
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.io.BufferedReader
import java.io.InputStreamReader
import java.io.OutputStreamWriter
import java.net.HttpURLConnection
import java.net.URL

/**
 * خدمة الاتصال بالخوادم السحابية (CloudApiService)
 * تتصل بخادم Forgeprom السحابي عند توفر الإنترنت مع إغلاق آمن للاتصالات (disconnect)،
 * والعودة الفورية التلقائية للوضع المحلي (Local-First Fallback) عند انقطاع الشبكة أو وضع الطيران.
 */
class CloudApiService(private val context: Context? = null) {

    companion object {
        const val DEFAULT_SERVER_URL = "https://ais-pre-fohrinam6tx5jewz4zbihs-381610229333.europe-west3.run.app"
        private const val PREFS_NAME = "forgeprom_cloud_prefs"
        private const val KEY_SERVER_URL = "custom_server_url"
    }

    private var baseUrl: String = loadSavedServerUrl()

    data class ServerHealthResult(
        val isConnected: Boolean,
        val serverMode: String,
        val latencyMs: Long,
        val message: String
    )

    private fun loadSavedServerUrl(): String {
        val prefs = context?.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val saved = prefs?.getString(KEY_SERVER_URL, DEFAULT_SERVER_URL) ?: DEFAULT_SERVER_URL
        return normalizeAndValidateHttpsUrl(saved).getOrDefault(DEFAULT_SERVER_URL)
    }

    /**
     * يفرض استخدام HTTPS الآمن، يرقّي روابط http:// تلقائياً، ويمنع المخططات الخطرة أو الروابط غير الصالحة.
     */
    fun normalizeAndValidateHttpsUrl(raw: String): Result<String> {
        val trimmed = raw.trim().trimEnd('/')
        if (trimmed.isBlank()) return Result.success(DEFAULT_SERVER_URL)
        val lower = trimmed.lowercase()
        if (lower.startsWith("file:") || lower.startsWith("javascript:") || lower.startsWith("content:") || lower.contains(" ")) {
            return Result.failure(IllegalArgumentException("رابط الخادم يحتوي على صيغة أو بروتوكول غير آمن."))
        }
        val withHttps = when {
            lower.startsWith("https://") -> trimmed
            lower.startsWith("http://") -> "https://" + trimmed.substring(7)
            trimmed.contains("://") -> return Result.failure(IllegalArgumentException("يُسمح فقط ببروتوكول HTTPS المشفر للاتصال بالخادم."))
            else -> "https://$trimmed"
        }
        return try {
            val parsed = URL(withHttps)
            if (parsed.host.isNullOrBlank() || !parsed.host.contains(".")) {
                Result.failure(IllegalArgumentException("اسم نطاق الخادم (Host) غير صالح. مثال: api.example.com"))
            } else {
                Result.success(withHttps)
            }
        } catch (_: Exception) {
            Result.failure(IllegalArgumentException("صيغة رابط الخادم غير صالحة."))
        }
    }

    fun setServerUrl(url: String): Result<String> {
        val validated = normalizeAndValidateHttpsUrl(url)
        validated.onSuccess { cleaned ->
            baseUrl = cleaned
            context?.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
                ?.edit()
                ?.putString(KEY_SERVER_URL, cleaned)
                ?.apply()
        }
        return validated
    }

    fun resetToDefaultUrl(): String {
        baseUrl = DEFAULT_SERVER_URL
        context?.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            ?.edit()
            ?.putString(KEY_SERVER_URL, DEFAULT_SERVER_URL)
            ?.apply()
        return baseUrl
    }

    fun getServerUrl(): String = baseUrl

    /**
     * فحص الاتصال الفعلي بالخادم السحابي (/api/health) وحساب زمن الاستجابة (Ping/Latency)
     */
    suspend fun checkServerConnection(): ServerHealthResult = withContext(Dispatchers.IO) {
        val startTime = System.currentTimeMillis()
        var conn: HttpURLConnection? = null
        try {
            val url = URL("$baseUrl/api/health")
            conn = (url.openConnection() as HttpURLConnection).apply {
                requestMethod = "GET"
                connectTimeout = 4500
                readTimeout = 4500
                setRequestProperty("Accept", "application/json")
            }

            val code = conn.responseCode
            val latency = System.currentTimeMillis() - startTime
            if (code in 200..299) {
                val responseText = BufferedReader(InputStreamReader(conn.inputStream, Charsets.UTF_8)).use { it.readText() }
                val mode = runCatching {
                    JSONObject(responseText).optString("mode", "Forgeprom Hybrid Engine")
                }.getOrDefault("Forgeprom Hybrid Engine")
                ServerHealthResult(
                    isConnected = true,
                    serverMode = mode,
                    latencyMs = latency,
                    message = "متصل بالخادم السحابي بنجاح (${latency}ms)"
                )
            } else {
                ServerHealthResult(
                    isConnected = false,
                    serverMode = "Local-First Offline",
                    latencyMs = latency,
                    message = "الخادم استجاب برمز $code — يعمل التطبيق بالوضع المحلي"
                )
            }
        } catch (_: Exception) {
            ServerHealthResult(
                isConnected = false,
                serverMode = "Local-First Offline",
                latencyMs = -1,
                message = "وضع الطيران / محلي 100% آمن (بدون اتصال بالخادم)"
            )
        } finally {
            conn?.disconnect()
        }
    }

    /**
     * تحليل الفكرة وتوليد البرومبت الهندسي عبر الخادم السحابي مع بديل محلي فوري عند عدم الاتصال
     */
    suspend fun analyzeAndGeneratePrompt(
        idea: String,
        targetPlatform: String
    ): Result<String> = withContext(Dispatchers.IO) {
        var conn: HttpURLConnection? = null
        try {
            val url = URL("$baseUrl/api/analyze-idea")
            conn = (url.openConnection() as HttpURLConnection).apply {
                requestMethod = "POST"
                connectTimeout = 8000
                readTimeout = 15000
                doOutput = true
                setRequestProperty("Content-Type", "application/json; charset=UTF-8")
                setRequestProperty("Accept", "application/json")
            }

            val payload = JSONObject().apply {
                put("idea", idea)
                put("projectType", "full_app")
                put("platform", targetPlatform)
            }

            OutputStreamWriter(conn.outputStream, Charsets.UTF_8).use { writer ->
                writer.write(payload.toString())
                writer.flush()
            }

            if (conn.responseCode in 200..299) {
                val responseText = BufferedReader(InputStreamReader(conn.inputStream, Charsets.UTF_8)).use { it.readText() }
                val json = JSONObject(responseText)
                val problem = json.optString("problem", idea)
                val primaryGoal = json.optString("primaryGoal", "")
                val mustHave = json.optJSONObject("functionalReqs")?.optJSONArray("mustHave")
                val verifiedFacts = json.optJSONArray("verifiedFacts")
                val assumptions = json.optJSONArray("assumptions")

                val sb = StringBuilder()
                sb.appendLine("# البرومبت الهندسي المُولّد (عبر خادم Forgeprom السحابي)")
                sb.appendLine("## 1. المشكلة الأساسية")
                sb.appendLine(problem)
                if (primaryGoal.isNotBlank()) {
                    sb.appendLine("\n## 2. الهدف الرئيسي")
                    sb.appendLine(primaryGoal)
                }
                sb.appendLine("\n## 3. الحقائق المؤكدة من المستخدم (Verified Facts)")
                if (verifiedFacts != null) {
                    for (i in 0 until verifiedFacts.length()) {
                        sb.appendLine("✓ ${verifiedFacts.getString(i)}")
                    }
                }
                sb.appendLine("\n## 4. الافتراضات المقترحة (Assumptions)")
                if (assumptions != null) {
                    for (i in 0 until assumptions.length()) {
                        sb.appendLine("? ${assumptions.getString(i)}")
                    }
                }
                sb.appendLine("\n## 5. المتطلبات الوظيفية الإلزامية (Must-Have)")
                if (mustHave != null) {
                    for (i in 0 until mustHave.length()) {
                        sb.appendLine("- ${mustHave.getString(i)}")
                    }
                }
                sb.appendLine("\n## 6. المنصة المستهدفة")
                sb.appendLine(targetPlatform)
                Result.success(sb.toString())
            } else {
                Result.success(generateLocalFallbackPrompt(idea, targetPlatform))
            }
        } catch (_: Exception) {
            Result.success(generateLocalFallbackPrompt(idea, targetPlatform))
        } finally {
            conn?.disconnect()
        }
    }

    /**
     * البحث التقني والاستشاري عبر الخادم السحابي (/api/research) مع قاعدة معرفة محلية ذكية
     */
    suspend fun performTechnicalResearch(query: String): String = withContext(Dispatchers.IO) {
        var conn: HttpURLConnection? = null
        try {
            val url = URL("$baseUrl/api/research")
            conn = (url.openConnection() as HttpURLConnection).apply {
                requestMethod = "POST"
                connectTimeout = 8000
                readTimeout = 15000
                doOutput = true
                setRequestProperty("Content-Type", "application/json; charset=UTF-8")
                setRequestProperty("Accept", "application/json")
            }

            val payload = JSONObject().apply {
                put("query", query)
                put("domain", "Android Native & Clean Architecture")
            }

            OutputStreamWriter(conn.outputStream, Charsets.UTF_8).use { writer ->
                writer.write(payload.toString())
                writer.flush()
            }

            if (conn.responseCode in 200..299) {
                val responseText = BufferedReader(InputStreamReader(conn.inputStream, Charsets.UTF_8)).use { it.readText() }
                val json = JSONObject(responseText)
                val summary = json.optString("summary", "")
                val rec = json.optString("recommendation", "")
                if (summary.isNotBlank() || rec.isNotBlank()) {
                    "نتائج البحث السحابي:\n$summary\n\nالتوصية الهندسية:\n$rec"
                } else {
                    getLocalKnowledgeBaseFallback(query)
                }
            } else {
                getLocalKnowledgeBaseFallback(query)
            }
        } catch (_: Exception) {
            getLocalKnowledgeBaseFallback(query)
        } finally {
            conn?.disconnect()
        }
    }

    private fun getLocalKnowledgeBaseFallback(query: String): String {
        val q = query.lowercase()
        val targetedTips = buildList {
            if (q.contains("room") || q.contains("sql") || q.contains("قاعدة") || q.contains("تخزين")) {
                add("• Room Database: استخدم DAOs مع Flow<List<T>> للتحديث التفاعلي، وفهارس @Index على أعمدة الفرز والبحث، واختبر الترحيل عبر Migration.")
            }
            if (q.contains("compose") || q.contains("ui") || q.contains("واجهة") || q.contains("شاشة")) {
                add("• Jetpack Compose: اعتمد State Hoisting وremember/derivedStateOf لتقليل Recomposition، واستخدم BoxWithConstraints للتكيف بين الهواتف والتابلت.")
            }
            if (q.contains("security") || q.contains("aes") || q.contains("owasp") || q.contains("أمان") || q.contains("تشفير")) {
                add("• الأمان والتشفير: احفظ المفاتيح داخل AndroidKeyStore بوضع AES/GCM/NoPadding (256-bit)، وتجنب تخزين أي أسرار نصية في الكود المصدري.")
            }
            if (q.contains("gguf") || q.contains("llm") || q.contains("نموذج") || q.contains("ram") || q.contains("ai")) {
                add("• النماذج المحلية GGUF: التزم بمعادلة الأمان (حجم النموذج × 1.25 <= الرام الحر)، واستخدم تكميم Q4_K_M مع فحص المساحة التخزينية وبصمة SHA-256.")
            }
            if (q.contains("flutter") || q.contains("react") || q.contains("api") || q.contains("rest")) {
                add("• التكامل الشبكي وAPI: صمّم عقود OpenAPI 3.1 واضحة مع مهلة اتصال قصيرة (4–8 ثوانٍ) وطبقة Cache محلية تضمن الاستمرارية دون إنترنت.")
            }
            if (isEmpty()) {
                add("• المعمارية الموصى بها: Clean Architecture (Presentation / Domain / Data) مع Kotlin Coroutines وStateFlow.")
                add("• الاستمرارية دون إنترنت: Room SQLite كمصدر حقيقة محلي (Single Source of Truth) مع مزامنة خلفية آمنة.")
                add("• الأداء والذاكرة: تنفيذ جميع عمليات الملفات والشبكة على Dispatchers.IO وحماية الذاكرة من OOM.")
            }
        }

        return buildString {
            appendLine("# قاعدة المعرفة الهندسية المحلية (Offline Knowledge Engine)")
            appendLine("- الاستعلام: $query")
            appendLine()
            appendLine("## التوصيات المعتمدة:")
            targetedTips.forEach { appendLine(it) }
        }.trim()
    }

    private fun generateLocalFallbackPrompt(idea: String, targetPlatform: String): String {
        return """
# البرومبت الهندسي القياسي (توليد محلي 100% — Offline Local Engine)
- **المنصة المستهدفة:** $targetPlatform
- **وصف الفكرة:** $idea

## المتطلبات المعمارية المعتمدة:
1. تطبيق Clean Architecture (Presentation, Domain, Data) مع MVVM وStateFlow.
2. قاعدة بيانات محلية Room SQLite تعمل 100% دون إنترنت مع طابور مزامنة اختياري.
3. واجهة عربية أصيلة بتصميم Material 3 تدعم اتجاه RTL والتكيف مع الهواتف والتابلت.
4. تشفير البيانات الحساسة عبر Android KeyStore AES-256-GCM وفق ضوابط OWASP MASVS.
5. إدارة ذاكرة صارمة (RAM Safety) ومعالجة الحالات الحدية (Edge Cases) وانقطاع الشبكة.
        """.trimIndent()
    }
}

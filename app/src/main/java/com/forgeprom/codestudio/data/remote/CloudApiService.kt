package com.forgeprom.codestudio.data.remote

import android.content.Context
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.io.BufferedReader
import java.io.InputStreamReader
import java.io.OutputStreamWriter
import java.net.HttpURLConnection
import java.net.URL
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicInteger
import java.util.concurrent.atomic.AtomicLong

/**
 * خدمة الاتصال بالخوادم السحابية والبحث التقني الهجين (CloudApiService)
 * المزايا المطورة:
 * - إعادة محاولة ذكية (Exponential Backoff Retry) للأخطاء العابرة.
 * - قاطع دائرة تلقائي (Circuit Breaker) يمنع إهدار الوقت عند تعذر الخادم ويحوّل فورياً للمحرك المحلي.
 * - ذاكرة تخزين مؤقت دائمة وفورية لنتائج البحث التقني (Persistent Research Cache بمدة صلاحية 24 ساعة).
 * - مولّد برومبتات محلي متخصص حسب المنصة عند العمل دون اتصال (Offline-First).
 */
class CloudApiService(private val context: Context? = null) {

    companion object {
        const val DEFAULT_SERVER_URL = "https://ais-pre-fohrinam6tx5jewz4zbihs-381610229333.europe-west3.run.app"
        private const val PREFS_NAME = "forgeprom_cloud_prefs"
        private const val KEY_SERVER_URL = "custom_server_url"
        private const val KEY_RESEARCH_CACHE = "research_cache_json_v2"
        private const val CACHE_TTL_MS = 24L * 60L * 60L * 1000L
        private const val MAX_CACHE_ENTRIES = 25
        private const val CIRCUIT_OPEN_DURATION_MS = 45_000L
        private const val CIRCUIT_FAILURE_THRESHOLD = 2
    }

    private var baseUrl: String = loadSavedServerUrl()
    private val consecutiveFailures = AtomicInteger(0)
    private val circuitOpenUntilMs = AtomicLong(0L)
    private val memoryResearchCache = ConcurrentHashMap<String, CachedResearchEntry>()

    data class ServerHealthResult(
        val isConnected: Boolean,
        val serverMode: String,
        val latencyMs: Long,
        val message: String
    )

    private data class CachedResearchEntry(
        val response: String,
        val savedAtMs: Long
    )

    init {
        loadResearchCacheFromPrefs()
    }

    private fun loadSavedServerUrl(): String {
        val prefs = context?.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val saved = prefs?.getString(KEY_SERVER_URL, DEFAULT_SERVER_URL) ?: DEFAULT_SERVER_URL
        return normalizeAndValidateHttpsUrl(saved).getOrDefault(DEFAULT_SERVER_URL)
    }

    private fun normalizeQueryKey(query: String): String =
        query.trim().lowercase().replace(Regex("\\s+"), " ")

    private fun loadResearchCacheFromPrefs() {
        val raw = context?.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            ?.getString(KEY_RESEARCH_CACHE, null) ?: return
        runCatching {
            val root = JSONObject(raw)
            val now = System.currentTimeMillis()
            val keys = root.keys()
            while (keys.hasNext()) {
                val key = keys.next()
                val obj = root.optJSONObject(key) ?: continue
                val savedAt = obj.optLong("savedAt", 0L)
                val response = obj.optString("response", "")
                if (response.isNotBlank() && (now - savedAt) < CACHE_TTL_MS) {
                    memoryResearchCache[key] = CachedResearchEntry(response, savedAt)
                }
            }
        }
    }

    private fun persistResearchCache() {
        val prefs = context?.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE) ?: return
        val now = System.currentTimeMillis()
        val entries = memoryResearchCache.entries
            .filter { (now - it.value.savedAtMs) < CACHE_TTL_MS }
            .sortedByDescending { it.value.savedAtMs }
            .take(MAX_CACHE_ENTRIES)

        val root = JSONObject()
        entries.forEach { (key, entry) ->
            root.put(
                key,
                JSONObject().apply {
                    put("savedAt", entry.savedAtMs)
                    put("response", entry.response)
                }
            )
        }
        prefs.edit().putString(KEY_RESEARCH_CACHE, root.toString()).apply()
    }

    fun getCachedResearchCount(): Int {
        val now = System.currentTimeMillis()
        return memoryResearchCache.values.count { (now - it.savedAtMs) < CACHE_TTL_MS }
    }

    fun clearResearchCache(): Int {
        val count = memoryResearchCache.size
        memoryResearchCache.clear()
        context?.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            ?.edit()
            ?.remove(KEY_RESEARCH_CACHE)
            ?.apply()
        return count
    }

    private fun isCircuitOpen(): Boolean {
        val openUntil = circuitOpenUntilMs.get()
        return openUntil > System.currentTimeMillis()
    }

    private fun recordServerSuccess() {
        consecutiveFailures.set(0)
        circuitOpenUntilMs.set(0L)
    }

    private fun recordServerFailure() {
        val fails = consecutiveFailures.incrementAndGet()
        if (fails >= CIRCUIT_FAILURE_THRESHOLD) {
            circuitOpenUntilMs.set(System.currentTimeMillis() + CIRCUIT_OPEN_DURATION_MS)
        }
    }

    fun resetCircuitBreaker() {
        consecutiveFailures.set(0)
        circuitOpenUntilMs.set(0L)
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
            resetCircuitBreaker()
            context?.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
                ?.edit()
                ?.putString(KEY_SERVER_URL, cleaned)
                ?.apply()
        }
        return validated
    }

    fun resetToDefaultUrl(): String {
        baseUrl = DEFAULT_SERVER_URL
        resetCircuitBreaker()
        context?.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            ?.edit()
            ?.putString(KEY_SERVER_URL, DEFAULT_SERVER_URL)
            ?.apply()
        return baseUrl
    }

    fun getServerUrl(): String = baseUrl

    /**
     * فحص الاتصال الفعلي بالخادم السحابي (/api/health) وحساب زمن الاستجابة مع إعادة محاولة ذكية
     */
    suspend fun checkServerConnection(forceCheck: Boolean = false): ServerHealthResult = withContext(Dispatchers.IO) {
        if (forceCheck) resetCircuitBreaker()

        val startTime = System.currentTimeMillis()
        var lastCode = -1
        for (attempt in 0..1) {
            var conn: HttpURLConnection? = null
            try {
                val url = URL("$baseUrl/api/health")
                conn = (url.openConnection() as HttpURLConnection).apply {
                    requestMethod = "GET"
                    connectTimeout = if (attempt == 0) 3500 else 5000
                    readTimeout = 4500
                    setRequestProperty("Accept", "application/json")
                }

                val code = conn.responseCode
                lastCode = code
                val latency = System.currentTimeMillis() - startTime
                if (code in 200..299) {
                    val responseText = BufferedReader(InputStreamReader(conn.inputStream, Charsets.UTF_8)).use { it.readText() }
                    val mode = runCatching {
                        JSONObject(responseText).optString("mode", "Forgeprom Hybrid Engine")
                    }.getOrDefault("Forgeprom Hybrid Engine")
                    recordServerSuccess()
                    return@withContext ServerHealthResult(
                        isConnected = true,
                        serverMode = mode,
                        latencyMs = latency,
                        message = "متصل بالخادم السحابي بنجاح (${latency}ms)"
                    )
                } else if (code in 500..599 && attempt == 0) {
                    delay(450L)
                    continue
                } else {
                    recordServerFailure()
                    return@withContext ServerHealthResult(
                        isConnected = false,
                        serverMode = "Local-First Offline",
                        latencyMs = latency,
                        message = "الخادم استجاب برمز $code — يعمل التطبيق بالوضع المحلي"
                    )
                }
            } catch (_: Exception) {
                if (attempt == 0) {
                    delay(400L)
                }
            } finally {
                conn?.disconnect()
            }
        }

        recordServerFailure()
        ServerHealthResult(
            isConnected = false,
            serverMode = "Local-First Offline",
            latencyMs = -1,
            message = if (lastCode > 0) {
                "تعذر الاتصال المستقر ($lastCode) — يعمل التطبيق بالوضع المحلي الفوري"
            } else {
                "وضع الطيران / محلي 100% آمن (بدون اتصال بالخادم)"
            }
        )
    }

    /**
     * تحليل الفكرة وتوليد البرومبت الهندسي عبر الخادم السحابي مع Retry + Circuit Breaker + بديل محلي متخصص بالمنصة
     */
    suspend fun analyzeAndGeneratePrompt(
        idea: String,
        targetPlatform: String
    ): Result<String> = withContext(Dispatchers.IO) {
        if (isCircuitOpen()) {
            return@withContext Result.success(generateLocalFallbackPrompt(idea, targetPlatform))
        }

        for (attempt in 0..1) {
            var conn: HttpURLConnection? = null
            try {
                val url = URL("$baseUrl/api/analyze-idea")
                conn = (url.openConnection() as HttpURLConnection).apply {
                    requestMethod = "POST"
                    connectTimeout = 6000
                    readTimeout = 12000
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

                val code = conn.responseCode
                if (code in 200..299) {
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
                    recordServerSuccess()
                    return@withContext Result.success(sb.toString())
                } else if (code in 500..599 && attempt == 0) {
                    delay(500L)
                } else {
                    recordServerFailure()
                    break
                }
            } catch (_: Exception) {
                if (attempt == 0) {
                    delay(450L)
                } else {
                    recordServerFailure()
                }
            } finally {
                conn?.disconnect()
            }
        }

        Result.success(generateLocalFallbackPrompt(idea, targetPlatform))
    }

    /**
     * البحث التقني والاستشاري عبر الخادم السحابي مع ذاكرة Cache دائمة + Circuit Breaker + قاعدة معرفة محلية
     */
    suspend fun performTechnicalResearch(query: String, bypassCache: Boolean = false): String = withContext(Dispatchers.IO) {
        val key = normalizeQueryKey(query)
        val now = System.currentTimeMillis()
        if (!bypassCache) {
            val cached = memoryResearchCache[key]
            if (cached != null && (now - cached.savedAtMs) < CACHE_TTL_MS) {
                return@withContext "⚡ [مسترجع من الذاكرة المؤقتة المحلية في <5ms]\n${cached.response}"
            }
        }

        if (!isCircuitOpen()) {
            for (attempt in 0..1) {
                var conn: HttpURLConnection? = null
                try {
                    val url = URL("$baseUrl/api/research")
                    conn = (url.openConnection() as HttpURLConnection).apply {
                        requestMethod = "POST"
                        connectTimeout = 6000
                        readTimeout = 12000
                        doOutput = true
                        setRequestProperty("Content-Type", "application/json; charset=UTF-8")
                        setRequestProperty("Accept", "application/json")
                    }

                    val payload = JSONObject().apply {
                        put("query", query)
                        put("domain", "Android Native, Multi-Platform & Clean Architecture")
                    }

                    OutputStreamWriter(conn.outputStream, Charsets.UTF_8).use { writer ->
                        writer.write(payload.toString())
                        writer.flush()
                    }

                    val code = conn.responseCode
                    if (code in 200..299) {
                        val responseText = BufferedReader(InputStreamReader(conn.inputStream, Charsets.UTF_8)).use { it.readText() }
                        val json = JSONObject(responseText)
                        val summary = json.optString("summary", "")
                        val rec = json.optString("recommendation", "")
                        if (summary.isNotBlank() || rec.isNotBlank()) {
                            val formatted = "نتائج البحث السحابي:\n$summary\n\nالتوصية الهندسية:\n$rec"
                            recordServerSuccess()
                            memoryResearchCache[key] = CachedResearchEntry(formatted, now)
                            persistResearchCache()
                            return@withContext formatted
                        }
                        break
                    } else if (code in 500..599 && attempt == 0) {
                        delay(450L)
                    } else {
                        recordServerFailure()
                        break
                    }
                } catch (_: Exception) {
                    if (attempt == 0) {
                        delay(400L)
                    } else {
                        recordServerFailure()
                    }
                } finally {
                    conn?.disconnect()
                }
            }
        }

        val localAnswer = getLocalKnowledgeBaseFallback(query)
        memoryResearchCache[key] = CachedResearchEntry(localAnswer, now)
        persistResearchCache()
        localAnswer
    }

    private fun getLocalKnowledgeBaseFallback(query: String): String {
        val q = query.lowercase()
        val targetedTips = buildList {
            if (q.contains("room") || q.contains("sql") || q.contains("قاعدة") || q.contains("تخزين") || q.contains("database")) {
                add("• Room & SQLite: استخدم DAOs مع Flow<List<T>> للتحديث التفاعلي، وفهارس @Index المركّبة على أعمدة الفرز، و@Transaction للعمليات الجماعية مع Migration صريح.")
            }
            if (q.contains("compose") || q.contains("ui") || q.contains("واجهة") || q.contains("شاشة") || q.contains("rtl")) {
                add("• Jetpack Compose: اعتمد ViewModel + StateFlow + collectAsStateWithLifecycle()، واستخدم BoxWithConstraints وWindowSizeClass للتكيف السلس بين الهواتف والتابلت.")
            }
            if (q.contains("security") || q.contains("aes") || q.contains("owasp") || q.contains("أمان") || q.contains("تشفير")) {
                add("• الأمان والتشفير: استخدم AndroidKeyStore AES-256-GCM لبيانات الجهاز الواحد، وPBKDF2WithHmacSHA256 + AES-GCM للنسخ الاحتياطية المنقولة بين الأجهزة.")
            }
            if (q.contains("gguf") || q.contains("llm") || q.contains("نموذج") || q.contains("ram") || q.contains("ai")) {
                add("• النماذج المحلية GGUF: التزم بمعادلة الأمان (حجم النموذج × 1.25 <= الرام الحر)، واستخدم ملفاً مؤقتاً (.part) مع فحص الترويسة وبصمة SHA-256 قبل الاعتماد.")
            }
            if (q.contains("flutter") || q.contains("react") || q.contains("web") || q.contains("api") || q.contains("rest")) {
                add("• التكامل الشبكي متعدد المنصات: صمّم عقود OpenAPI 3.1 واضحة مع إعادة محاولة (Exponential Backoff) وقاطع دائرة (Circuit Breaker) وCache محلي للاستمرارية دون إنترنت.")
            }
            if (isEmpty()) {
                add("• المعمارية الموصى بها: Clean Architecture (Presentation / Domain / Data) مع ViewModel وRepository وStateFlow.")
                add("• الاستمرارية دون إنترنت: قاعدة بيانات محلية كمصدر حقيقة وحيد (Single Source of Truth) مع دمج ذكي للنسخ الاحتياطية.")
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
        val stackDetails = when (targetPlatform.uppercase()) {
            "FLUTTER" -> "Flutter + Dart (Riverpod / Bloc) + Drift SQLite للتخزين المحلي + دعم RTL كامل."
            "REACT_NATIVE" -> "React Native + TypeScript (Expo Router + Zustand) + SQLite / MMKV للتخزين المحلي."
            "WEB" -> "React / Next.js + TypeScript + Tailwind CSS (RTL) + IndexedDB/PWA للعمل دون إنترنت."
            "BACKEND" -> "REST / OpenAPI 3.1 + PostgreSQL + Redis Rate Limiting + JWT/Bearer Auth + Docker."
            else -> "Kotlin + Jetpack Compose (Material 3 RTL) + Clean Architecture (ViewModel + StateFlow) + Room SQLite."
        }
        return """
# البرومبت الهندسي القياسي (توليد محلي 100% — Offline Local Engine)
- **المنصة المستهدفة:** $targetPlatform
- **المكدس التقني المعتمد:** $stackDetails
- **وصف الفكرة:** $idea

## المتطلبات المعمارية المعتمدة:
1. تطبيق Clean Architecture (Presentation, Domain, Data) مع فصل كامل لحالة الواجهة.
2. طبقة تخزين محلية تعمل 100% دون إنترنت (Offline-First) مع فهارس بحث وطابور مزامنة آمن.
3. واجهة عربية أصيلة تدعم اتجاه RTL والتكيف مع جميع أحجام الشاشات والوضع الداكن.
4. تشفير البيانات الحساسة عبر AES-256-GCM وتطبيق ضوابط OWASP MASVS و Rate Limiting.
5. إدارة ذاكرة صارمة (RAM Safety) ومعالجة الحالات الحدية (Edge Cases) وانقطاع الشبكة ومعايير قبول واضحة.
        """.trimIndent()
    }
}

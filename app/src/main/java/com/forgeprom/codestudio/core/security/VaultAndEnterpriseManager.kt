package com.forgeprom.codestudio.core.security

import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.Base64
import com.forgeprom.codestudio.data.local.model.ProjectEntity
import org.json.JSONObject
import java.security.KeyStore
import java.security.SecureRandom
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.SecretKeyFactory
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.PBEKeySpec
import javax.crypto.spec.SecretKeySpec

/**
 * خزنة الأمان العتادي والتدقيق الأمني (VaultAndEnterpriseManager)
 * المزايا المطورة:
 * - تشفير عتادي عبر AndroidKeyStore AES-256-GCM للجهاز الحالي.
 * - تشفير محمول عابر للأجهزة بكلمة مرور عبر PBKDF2WithHmacSHA256 (120,000 دورة) + AES-256-GCM.
 * - فاحص OWASP MASVS ديناميكي مع تحصين تلقائي يحقن الضوابط المفقودة فقط.
 * - مولّد عقود OpenAPI 3.1 ديناميكي يستخرج الكيان والمسارات CRUD وحماية Bearer تلقائياً.
 * - حاسبة FinOps مرنة تدعم الطلبات اليومية ومتوسط التوكنز ومقارنة نماذج 1.5B / 3B.
 */
object VaultAndEnterpriseManager {
    private const val ANDROID_KEYSTORE = "AndroidKeyStore"
    private const val KEY_ALIAS = "forgeprom_aes256_master_key"
    private const val TRANSFORMATION = "AES/GCM/NoPadding"
    private const val PBKDF2_ITERATIONS = 120_000

    data class OwaspAuditItem(
        val code: String,
        val title: String,
        val severity: String,
        val recommendation: String,
        val isPassed: Boolean = false
    )

    data class FinOpsEstimate(
        val mau: Int,
        val dailyPrompts: Int,
        val modelTierLabel: String,
        val cloudMonthlyUsd: Double,
        val hybridGgufMonthlyUsd: Double,
        val annualSavingsUsd: Double,
        val savingsPercent: Int
    )

    private fun getOrCreateSecretKey(): SecretKey {
        val keyStore = KeyStore.getInstance(ANDROID_KEYSTORE).apply { load(null) }
        keyStore.getKey(KEY_ALIAS, null)?.let { return it as SecretKey }

        val keyGenerator = KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, ANDROID_KEYSTORE)
        val spec = KeyGenParameterSpec.Builder(
            KEY_ALIAS,
            KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT
        )
            .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
            .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
            .setKeySize(256)
            .build()
        keyGenerator.init(spec)
        return keyGenerator.generateKey()
    }

    /**
     * تشفير عتادي خاص بالجهاز عبر Android KeyStore AES-256-GCM
     */
    fun encryptSafely(plainText: String): Result<String> {
        if (plainText.isBlank()) {
            return Result.failure(IllegalArgumentException("يرجى إدخال نص صالح قبل التشفير."))
        }
        return try {
            val cipher = Cipher.getInstance(TRANSFORMATION)
            cipher.init(Cipher.ENCRYPT_MODE, getOrCreateSecretKey())
            val iv = cipher.iv
            val encrypted = cipher.doFinal(plainText.toByteArray(Charsets.UTF_8))

            val json = JSONObject().apply {
                put("cipher", "AES-256-GCM-AndroidKeyStore")
                put("iv", Base64.encodeToString(iv, Base64.NO_WRAP))
                put("payload", Base64.encodeToString(encrypted, Base64.NO_WRAP))
            }
            Result.success(json.toString())
        } catch (e: Exception) {
            Result.failure(IllegalStateException("تعذر التشفير العتادي عبر KeyStore: ${e.localizedMessage ?: "خطأ غير معروف"}"))
        }
    }

    /**
     * فك التشفير العتادي للجهاز الحالي
     */
    fun decryptSafely(encryptedJson: String): Result<String> {
        if (encryptedJson.isBlank()) {
            return Result.failure(IllegalArgumentException("يرجى إدخال حزمة JSON المشفرة أولاً."))
        }
        return try {
            val json = JSONObject(encryptedJson.trim())
            if (!json.has("iv") || !json.has("payload")) {
                return Result.failure(IllegalArgumentException("صيغة النص المدخل غير صالحة لفك التشفير (يجب أن يحتوي على حقلي iv و payload)."))
            }
            val iv = Base64.decode(json.getString("iv"), Base64.NO_WRAP)
            val payload = Base64.decode(json.getString("payload"), Base64.NO_WRAP)

            val cipher = Cipher.getInstance(TRANSFORMATION)
            val spec = GCMParameterSpec(128, iv)
            cipher.init(Cipher.DECRYPT_MODE, getOrCreateSecretKey(), spec)
            Result.success(String(cipher.doFinal(payload), Charsets.UTF_8))
        } catch (_: Exception) {
            Result.failure(IllegalArgumentException("فشل فك التشفير: تأكد أن النص مُشفّر على هذا الجهاز بصيغة Forgeprom AES-256-GCM."))
        }
    }

    /**
     * تشفير محمول عابر للأجهزة بكلمة مرور عبر PBKDF2-HMAC-SHA256 + AES-256-GCM
     * يتيح نقل النسخة الاحتياطية المشفرة واستعادتها على أي هاتف أندرويد آخر بأمان تام.
     */
    fun encryptWithPasswordPBKDF2(plainText: String, password: String): Result<String> {
        if (plainText.isBlank()) return Result.failure(IllegalArgumentException("النص المراد تشفيره فارغ."))
        if (password.length < 4) return Result.failure(IllegalArgumentException("يجب أن تتكون كلمة المرور من 4 أحرف على الأقل."))

        return try {
            val rng = SecureRandom()
            val salt = ByteArray(16).also { rng.nextBytes(it) }
            val iv = ByteArray(12).also { rng.nextBytes(it) }

            val keySpec = PBEKeySpec(password.toCharArray(), salt, PBKDF2_ITERATIONS, 256)
            val secretFactory = SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256")
            val derivedBytes = secretFactory.generateSecret(keySpec).encoded
            val aesKey = SecretKeySpec(derivedBytes, "AES")

            val cipher = Cipher.getInstance(TRANSFORMATION)
            cipher.init(Cipher.ENCRYPT_MODE, aesKey, GCMParameterSpec(128, iv))
            val encrypted = cipher.doFinal(plainText.toByteArray(Charsets.UTF_8))

            val json = JSONObject().apply {
                put("cipher", "AES-256-GCM-PBKDF2")
                put("iterations", PBKDF2_ITERATIONS)
                put("salt", Base64.encodeToString(salt, Base64.NO_WRAP))
                put("iv", Base64.encodeToString(iv, Base64.NO_WRAP))
                put("payload", Base64.encodeToString(encrypted, Base64.NO_WRAP))
            }
            Result.success(json.toString())
        } catch (e: Exception) {
            Result.failure(IllegalStateException("تعذر التشفير المحمول بكلمة المرور: ${e.localizedMessage ?: "خطأ غير معروف"}"))
        }
    }

    /**
     * فك التشفير المحمول بكلمة مرور عبر PBKDF2-HMAC-SHA256 + AES-256-GCM
     */
    fun decryptWithPasswordPBKDF2(encryptedJson: String, password: String): Result<String> {
        if (password.isBlank()) return Result.failure(IllegalArgumentException("يرجى إدخال كلمة المرور لفك تشفير النسخة المحمولة."))
        return try {
            val json = JSONObject(encryptedJson.trim())
            val iterations = json.optInt("iterations", PBKDF2_ITERATIONS).coerceIn(10_000, 300_000)
            val salt = Base64.decode(json.getString("salt"), Base64.NO_WRAP)
            val iv = Base64.decode(json.getString("iv"), Base64.NO_WRAP)
            val payload = Base64.decode(json.getString("payload"), Base64.NO_WRAP)

            val keySpec = PBEKeySpec(password.toCharArray(), salt, iterations, 256)
            val secretFactory = SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256")
            val derivedBytes = secretFactory.generateSecret(keySpec).encoded
            val aesKey = SecretKeySpec(derivedBytes, "AES")

            val cipher = Cipher.getInstance(TRANSFORMATION)
            cipher.init(Cipher.DECRYPT_MODE, aesKey, GCMParameterSpec(128, iv))
            Result.success(String(cipher.doFinal(payload), Charsets.UTF_8))
        } catch (_: Exception) {
            Result.failure(IllegalArgumentException("كلمة المرور غير صحيحة أو أن ملف النسخة الاحتياطية تالف."))
        }
    }

    data class SecretRedactionResult(
        val foundCount: Int,
        val redactedText: String,
        val detectedTypes: List<String>
    )

    private val secretPatterns: List<Pair<String, Regex>> = listOf(
        "OpenAI Key" to Regex("\\bsk-[a-zA-Z0-9_\\-]{16,}\\b"),
        "Google API Key" to Regex("\\bAIza[0-9A-Za-z_\\-]{20,}\\b"),
        "GitHub Token" to Regex("\\b(ghp_[A-Za-z0-9]{20,}|github_pat_[A-Za-z0-9_]{20,})\\b"),
        "AWS Access Key" to Regex("\\bAKIA[0-9A-Z]{16}\\b"),
        "JWT Token" to Regex("\\beyJ[A-Za-z0-9_\\-]{10,}\\.[A-Za-z0-9_\\-]{10,}\\.[A-Za-z0-9_\\-]{10,}\\b"),
        "Bearer Token" to Regex("(?i)\\bBearer\\s+[A-Za-z0-9._\\-]{16,}"),
        "Inline Secret/Password" to Regex("(?i)\\b(password|passwd|api[_-]?key|client[_-]?secret|access[_-]?token)\\s*[:=]\\s*['\"][^'\"\\n]{4,}['\"]")
    )

    /**
     * كاشف ومنقّي الأسرار المكشوفة بلمسة واحدة (1-Tap Secret Redactor)
     * يكتشف مفاتيح API ورموز JWT/Bearer/AWS/GitHub وكلمات المرور النصية ويستبدلها بـ BuildConfig.SECRET_KEY.
     */
    fun detectAndRedactSecrets(text: String): SecretRedactionResult {
        if (text.isBlank()) return SecretRedactionResult(0, text, emptyList())
        var working = text
        var totalFound = 0
        val foundTypes = mutableListOf<String>()

        secretPatterns.forEach { (label, regex) ->
            val matches = regex.findAll(working).toList()
            if (matches.isNotEmpty()) {
                totalFound += matches.size
                foundTypes.add(label)
                working = regex.replace(working) { match ->
                    when (label) {
                        "Bearer Token" -> "Bearer \${BuildConfig.SECRET_KEY}"
                        "Inline Secret/Password" -> {
                            val keyName = match.groupValues.getOrNull(1).orEmpty().ifBlank { "secret" }
                            "$keyName = BuildConfig.SECRET_KEY"
                        }
                        else -> "BuildConfig.SECRET_KEY"
                    }
                }
            }
        }
        return SecretRedactionResult(
            foundCount = totalFound,
            redactedText = working,
            detectedTypes = foundTypes.distinct()
        )
    }

    fun encryptText(plainText: String): String = encryptSafely(plainText).getOrDefault(plainText)
    fun decryptText(encryptedJson: String): String = decryptSafely(encryptedJson).getOrDefault(encryptedJson)

    /**
     * فحص أمني ديناميكي متكامل وفق معايير OWASP MASVS و API Security
     */
    fun auditProjectSecurity(project: ProjectEntity): List<OwaspAuditItem> {
        val combined = "${project.name}\n${project.description}\n${project.rawIdea}\n${project.currentPromptText.orEmpty()}"
        val lower = combined.lowercase()

        val hasCleartextRisk = lower.contains("http://") && !lower.contains("https://")
        val secretScan = detectAndRedactSecrets(combined)
        val hasSecretLeakRisk = secretScan.foundCount > 0
        val hasEncryptionSpec = lower.contains("aes") || lower.contains("keystore") || lower.contains("تشفير") || lower.contains("gcm")
        val hasAccessControlSpec = lower.contains("bola") || lower.contains("rls") || lower.contains("auth") || lower.contains("صلاحيات") || lower.contains("مالك") || lower.contains("oauth") || lower.contains("bearer")
        val hasRateLimitSpec = lower.contains("rate limit") || lower.contains("rate limiting") || lower.contains("سقف الطلبات") || lower.contains("throttle") || lower.contains("429")
        val isWebOrBackend = project.targetPlatform.equals("WEB", ignoreCase = true) ||
            project.targetPlatform.equals("BACKEND", ignoreCase = true)
        val hasWebHardening = lower.contains("csp") || lower.contains("xss") || lower.contains("csrf") || lower.contains("cors")

        val items = mutableListOf(
            OwaspAuditItem(
                code = "MASVS-STORAGE-1",
                title = "تشفير التخزين المحلي عبر AES-256-GCM",
                severity = "HIGH",
                recommendation = if (hasEncryptionSpec) {
                    "✓ تم توثيق التشفير المحلي في مواصفات المشروع (${project.name})."
                } else {
                    "أضف شرط استخدام التشفير AES-256-GCM لحماية السجلات والمفاتيح المحلية."
                },
                isPassed = hasEncryptionSpec
            ),
            OwaspAuditItem(
                code = "OWASP API1:2023",
                title = "حماية صلاحيات الوصول للسجلات (BOLA / Row-Level Security)",
                severity = "CRITICAL",
                recommendation = if (hasAccessControlSpec) {
                    "✓ ضوابط التحقق من الصلاحيات والمالك موثقة في المواصفات."
                } else {
                    "فعّل التحقق من هوية المالك (Owner UID / Bearer Auth) ومنع الوصول المباشر غير المصرح."
                },
                isPassed = hasAccessControlSpec
            ),
            OwaspAuditItem(
                code = "OWASP API4:2023",
                title = "حماية نقاط الذكاء الاصطناعي والـ API بـ Rate Limiting",
                severity = "HIGH",
                recommendation = if (hasRateLimitSpec) {
                    "✓ تم تضمين قيود معدل الطلبات ومنع استنزاف الموارد."
                } else {
                    "حدد سقف الطلبات (30 طلب/دقيقة لكل مستخدم مع استجابة HTTP 429) لمنع DoS."
                },
                isPassed = hasRateLimitSpec
            ),
            OwaspAuditItem(
                code = "MASVS-NETWORK-1",
                title = "فرض التشفير الكامل للنقل (HTTPS / TLS 1.3 Only)",
                severity = if (hasCleartextRisk) "CRITICAL" else "MEDIUM",
                recommendation = if (!hasCleartextRisk) {
                    "✓ لا توجد روابط HTTP غير مشفرة في مواصفات المشروع."
                } else {
                    "تحذير: تم رصد رابط http:// غير مشفر! استبدله بـ https:// وفعّل usesCleartextTraffic=\"false\"."
                },
                isPassed = !hasCleartextRisk
            ),
            OwaspAuditItem(
                code = "MASVS-CODE-2",
                title = "خلو المواصفات من مفاتيح API أو كلمات مرور مكشوفة",
                severity = "CRITICAL",
                recommendation = if (!hasSecretLeakRisk) {
                    "✓ لم يتم رصد أي مفاتيح API أو كلمات مرور نصية مكشوفة."
                } else {
                    "تحذير حرج: تم رصد نمط يشبه مفتاح API أو كلمة مرور نصية! أزله فوراً وشفّره عبر الخزنة."
                },
                isPassed = !hasSecretLeakRisk
            )
        )

        if (isWebOrBackend) {
            items.add(
                OwaspAuditItem(
                    code = "OWASP A03:2021",
                    title = "حماية الويب والـ API من XSS و CSRF وتفعيل CSP/CORS",
                    severity = "HIGH",
                    recommendation = if (hasWebHardening) {
                        "✓ تم توثيق معايير حماية الويب والخادم (CSP/XSS/CORS)."
                    } else {
                        "أضف ترويسات Content-Security-Policy وتقييد CORS وتطهير مدخلات المستخدم وحماية CSRF."
                    },
                    isPassed = hasWebHardening
                )
            )
        }
        return items
    }

    /**
     * بناء كتلة تحصين أمني دقيقة تحقن الضوابط المفقودة فقط دون تكرار البنود الموجودة
     */
    fun buildMissingOwaspHardeningBlock(project: ProjectEntity): String {
        val missing = auditProjectSecurity(project).filter { !it.isPassed }
        if (missing.isEmpty()) return ""

        return buildString {
            appendLine("## متطلبات التحصين الأمني المضافة تلقائياً (OWASP Hardening)")
            missing.forEach { item ->
                when (item.code) {
                    "MASVS-STORAGE-1" ->
                        appendLine("- **[MASVS-STORAGE-1]** تشفير التخزين المحلي عبر AES-256-GCM (Android KeyStore / Hardware-backed Vault).")
                    "OWASP API1:2023" ->
                        appendLine("- **[OWASP API1:2023]** فرض مصادقة Bearer Auth والتحقق الصارم من ملكية السجل (BOLA / Row-Level Security).")
                    "OWASP API4:2023" ->
                        appendLine("- **[OWASP API4:2023]** تطبيق Rate Limiting (30 طلب/دقيقة مع HTTP 429) ومهلة اتصال قصيرة لمنع الاستنزاف.")
                    "MASVS-NETWORK-1" ->
                        appendLine("- **[MASVS-NETWORK-1]** فرض HTTPS/TLS 1.3 فقط وتعطيل أي اتصال نصي غير مشفّر (usesCleartextTraffic=false).")
                    "MASVS-CODE-2" ->
                        appendLine("- **[MASVS-CODE-2]** إزالة أي مفاتيح أو كلمات مرور نصية وحقنها عبر متغيرات بيئة مشفّرة فقط.")
                    "OWASP A03:2021" ->
                        appendLine("- **[OWASP A03:2021]** تفعيل Content-Security-Policy (CSP) وتقييد CORS وتطهير المدخلات ضد XSS و CSRF.")
                }
            }
        }.trim()
    }

    fun calculateSecurityScore(items: List<OwaspAuditItem>): Int {
        if (items.isEmpty()) return 100
        val passedCount = items.count { it.isPassed }
        return ((passedCount * 100) / items.size).coerceIn(0, 100)
    }

    /**
     * حاسبة FinOps التفاعلية المطورة (تأخذ بالاعتبار الطلبات اليومية وحجم النموذج المحلي المختار)
     */
    fun calculateFinOps(
        mau: Int,
        dailyPrompts: Int = 6,
        avgTokensPerPrompt: Int = 1800,
        localOffloadRatio: Double = 0.78,
        modelTierLabel: String = "Qwen 2.5 1.5B (Q4)"
    ): FinOpsEstimate {
        val safeOffload = localOffloadRatio.coerceIn(0.4, 0.95)
        val monthlyTokensM = (mau.toDouble() * dailyPrompts * 30.0 * avgTokensPerPrompt) / 1_000_000.0
        val cloudCost = (monthlyTokensM * 2.75) + (mau / 1000.0 * 8.5).coerceAtLeast(12.0)
        val hybridCost = (cloudCost * (1.0 - safeOffload)).coerceAtLeast(4.0)
        val annualSavings = ((cloudCost - hybridCost) * 12.0).coerceAtLeast(0.0)
        val savingsPct = if (cloudCost > 0.0) {
            (((cloudCost - hybridCost) / cloudCost) * 100.0).toInt().coerceIn(0, 99)
        } else 0

        return FinOpsEstimate(
            mau = mau,
            dailyPrompts = dailyPrompts,
            modelTierLabel = modelTierLabel,
            cloudMonthlyUsd = Math.round(cloudCost * 100.0) / 100.0,
            hybridGgufMonthlyUsd = Math.round(hybridCost * 100.0) / 100.0,
            annualSavingsUsd = Math.round(annualSavings * 100.0) / 100.0,
            savingsPercent = savingsPct
        )
    }

    private fun escapeYamlDoubleQuoted(raw: String): String {
        return raw
            .replace("\\", "\\\\")
            .replace("\"", "\\\"")
            .replace("\r", " ")
            .replace("\n", " ")
            .trim()
    }

    private fun inferDomainResource(project: ProjectEntity): Pair<String, String> {
        val text = "${project.name} ${project.rawIdea} ${project.description}".lowercase()
        return when {
            text.contains("pos") || text.contains("فواتير") || text.contains("مبيعات") || text.contains("محاسب") ->
                "invoices" to "InvoiceRecord"
            text.contains("clinic") || text.contains("عياد") || text.contains("مريض") || text.contains("طبي") ->
                "patients" to "PatientRecord"
            text.contains("متجر") || text.contains("تسوق") || text.contains("منتج") || text.contains("ecommerce") ->
                "products" to "ProductItem"
            text.contains("توصيل") || text.contains("مندوب") || text.contains("شحن") || text.contains("delivery") ->
                "shipments" to "ShipmentOrder"
            text.contains("تعليم") || text.contains("دور") || text.contains("طالب") || text.contains("edtech") ->
                "courses" to "CourseModule"
            text.contains("محفظ") || text.contains("مصروف") || text.contains("مالي") || text.contains("fintech") ->
                "transactions" to "LedgerTransaction"
            else -> "records" to "CoreRecord"
        }
    }

    /**
     * مولّد عقود OpenAPI 3.1 ديناميكي يستخرج الكيانات، مسارات CRUD، مصادقة Bearer، واستجابات الخطأ
     */
    fun generateOpenApiSpec(project: ProjectEntity, serverUrl: String): String {
        val safeTitle = escapeYamlDoubleQuoted(project.name)
        val safeUrl = escapeYamlDoubleQuoted(serverUrl)
        val (resourcePath, schemaName) = inferDomainResource(project)

        return """
openapi: 3.1.0
info:
  title: "$safeTitle API"
  version: "2.6.0"
  description: "Auto-generated OpenAPI 3.1 contract for ${project.targetPlatform} (${project.type})"
servers:
  - url: "$safeUrl"
security:
  - BearerAuth: []
paths:
  /api/v1/health:
    get:
      summary: "Hybrid Server Health & Latency Probe"
      security: []
      responses:
        '200':
          description: "Service Healthy"
  /api/v1/$resourcePath:
    get:
      summary: "List $schemaName items (paginated & owner-scoped)"
      parameters:
        - name: updatedSince
          in: query
          schema:
            type: integer
            format: int64
      responses:
        '200':
          description: "Successful list response"
        '401':
          description: "Unauthorized"
        '429':
          description: "Rate Limit Exceeded (OWASP API4)"
    post:
      summary: "Create or sync a $schemaName"
      requestBody:
        required: true
        content:
          application/json:
            schema:
              ${'$'}ref: '#/components/schemas/$schemaName'
      responses:
        '201':
          description: "Created"
        '400':
          description: "Validation Error"
  /api/v1/$resourcePath/{id}:
    get:
      summary: "Fetch single $schemaName by ID (BOLA Owner Checked)"
      parameters:
        - name: id
          in: path
          required: true
          schema:
            type: string
      responses:
        '200':
          description: "Found"
        '404':
          description: "Not Found"
    delete:
      summary: "Soft-delete $schemaName by ID"
      parameters:
        - name: id
          in: path
          required: true
          schema:
            type: string
      responses:
        '204':
          description: "Deleted"
components:
  securitySchemes:
    BearerAuth:
      type: http
      scheme: bearer
      bearerFormat: JWT
  schemas:
    $schemaName:
      type: object
      required: [id, title, updatedAt]
      properties:
        id:
          type: string
        title:
          type: string
        payloadJson:
          type: string
        syncStatus:
          type: string
          enum: [LOCAL_ONLY, QUEUED, SYNCED]
        updatedAt:
          type: integer
          format: int64
        """.trimIndent()
    }
}

package com.forgeprom.codestudio.core.security

import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.Base64
import com.forgeprom.codestudio.data.local.model.ProjectEntity
import org.json.JSONArray
import org.json.JSONObject
import java.security.KeyStore
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

/**
 * Native Android Enterprise Security Vault (AES-256-GCM Hardware KeyStore)
 * + OWASP Threat Modeling Auditor & FinOps Calculator & OpenAPI 3.1 Contract Generator.
 */
object VaultAndEnterpriseManager {
    private const val ANDROID_KEYSTORE = "AndroidKeyStore"
    private const val KEY_ALIAS = "forgeprom_aes256_master_key"
    private const val TRANSFORMATION = "AES/GCM/NoPadding"

    data class OwaspAuditItem(
        val code: String,
        val title: String,
        val severity: String,
        val recommendation: String,
        val isPassed: Boolean = false
    )

    data class FinOpsEstimate(
        val mau: Int,
        val cloudMonthlyUsd: Double,
        val hybridGgufMonthlyUsd: Double,
        val annualSavingsUsd: Double
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
     * Encrypts sensitive project payload using Hardware-backed Android KeyStore AES-256-GCM
     * with explicit Result reporting.
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
     * Decrypts AES-256-GCM payload from Android KeyStore with explicit validation & Result reporting.
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
        } catch (e: Exception) {
            Result.failure(IllegalArgumentException("فشل فك التشفير: تأكد أن النص مُشفّر على هذا الجهاز بصيغة Forgeprom AES-256-GCM."))
        }
    }

    /**
     * Backward-compatible wrapper returning encrypted payload or original text on failure.
     */
    fun encryptText(plainText: String): String = encryptSafely(plainText).getOrDefault(plainText)

    /**
     * Backward-compatible wrapper returning decrypted text or original input on failure.
     */
    fun decryptText(encryptedJson: String): String = decryptSafely(encryptedJson).getOrDefault(encryptedJson)

    /**
     * Runs dynamic, context-aware OWASP API & Mobile MASVS Security Audit on a ProjectEntity.
     * Inspects prompt text, raw idea, and target platform to detect real missing controls or leaks.
     */
    fun auditProjectSecurity(project: ProjectEntity): List<OwaspAuditItem> {
        val combined = "${project.name}\n${project.description}\n${project.rawIdea}\n${project.currentPromptText.orEmpty()}"
        val lower = combined.lowercase()

        val hasCleartextRisk = lower.contains("http://") && !lower.contains("https://")
        val hasSecretLeakRisk = Regex("(sk-[a-zA-Z0-9]{12,}|AIza[0-9A-Za-z\\-_]{20,}|password\\s*=\\s*['\"][^'\"]+['\"])").containsMatchIn(combined)
        val hasEncryptionSpec = lower.contains("aes") || lower.contains("keystore") || lower.contains("تشفير") || lower.contains("gcm")
        val hasAccessControlSpec = lower.contains("bola") || lower.contains("rls") || lower.contains("auth") || lower.contains("صلاحيات") || lower.contains("مالك") || lower.contains("oauth")
        val hasRateLimitSpec = lower.contains("rate limit") || lower.contains("rate limiting") || lower.contains("سقف الطلبات") || lower.contains("throttle")
        val isWeb = project.targetPlatform.equals("WEB", ignoreCase = true)
        val hasWebHardening = lower.contains("csp") || lower.contains("xss") || lower.contains("csrf") || lower.contains("owasp")

        val items = mutableListOf(
            OwaspAuditItem(
                code = "MASVS-STORAGE-1",
                title = "تشفير التخزين المحلي عبر Android KeyStore AES-256-GCM",
                severity = "HIGH",
                recommendation = if (hasEncryptionSpec) {
                    "✓ تم توثيق التشفير المحلي في مواصفات المشروع (${project.name})."
                } else {
                    "أضف شرط استخدام خزنة التشفير AES-256-GCM لحماية السجلات والمفاتيح المحلية."
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
                    "فعّل التحقق من هوية المالك (Owner UID) ومنع الوصول المباشر غير المصرح للسجلات في (${project.name})."
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
                    "حدد سقف الطلبات (مثلاً 30 طلب/دقيقة لكل مستخدم) مع مهلة اتصال قصيرة لمنع DoS."
                },
                isPassed = hasRateLimitSpec
            ),
            OwaspAuditItem(
                code = "MASVS-NETWORK-1",
                title = "فرض التشفير الكامل للنقل (HTTPS / TLS Only)",
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

        if (isWeb) {
            items.add(
                OwaspAuditItem(
                    code = "OWASP A03:2021",
                    title = "حماية تطبيقات الويب من XSS و CSRF وتفعيل CSP",
                    severity = "HIGH",
                    recommendation = if (hasWebHardening) {
                        "✓ تم توثيق معايير الحماية الخاصة بمنصة الويب."
                    } else {
                        "أضف ترويسات Content-Security-Policy وتطهير مدخلات المستخدم وحماية CSRF لتطبيق الويب."
                    },
                    isPassed = hasWebHardening
                )
            )
        }
        return items
    }

    /**
     * Computes live Security Readiness Score (0..100) from OWASP audit results.
     */
    fun calculateSecurityScore(items: List<OwaspAuditItem>): Int {
        if (items.isEmpty()) return 100
        val passedCount = items.count { it.isPassed }
        return ((passedCount * 100) / items.size).coerceIn(0, 100)
    }

    /**
     * Calculates FinOps Cloud vs On-Device GGUF Savings
     */
    fun calculateFinOps(mau: Int, dailyPrompts: Int = 5): FinOpsEstimate {
        val monthlyTokensM = (mau.toDouble() * dailyPrompts * 30 * 1800) / 1_000_000.0
        val cloudCost = (monthlyTokensM * 2.75) + (mau / 1000.0 * 8.5).coerceAtLeast(12.0)
        val hybridCost = (cloudCost * 0.22)
        val annualSavings = ((cloudCost - hybridCost) * 12.0).coerceAtLeast(0.0)
        return FinOpsEstimate(
            mau = mau,
            cloudMonthlyUsd = Math.round(cloudCost * 100.0) / 100.0,
            hybridGgufMonthlyUsd = Math.round(hybridCost * 100.0) / 100.0,
            annualSavingsUsd = Math.round(annualSavings * 100.0) / 100.0
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

    /**
     * Generates sanitized OpenAPI 3.1 YAML Specification (protected against YAML injection)
     */
    fun generateOpenApiSpec(project: ProjectEntity, serverUrl: String): String {
        val safeTitle = escapeYamlDoubleQuoted(project.name)
        val safeUrl = escapeYamlDoubleQuoted(serverUrl)
        return """
            openapi: 3.1.0
            info:
              title: "$safeTitle API"
              version: "2.5.0"
            servers:
              - url: "$safeUrl"
            paths:
              /api/v1/health:
                get:
                  summary: "Health & Hybrid Status Check"
                  responses:
                    '200':
                      description: "OK"
              /api/v1/projects:
                get:
                  summary: "List Projects"
                  responses:
                    '200':
                      description: "OK"
        """.trimIndent()
    }
}

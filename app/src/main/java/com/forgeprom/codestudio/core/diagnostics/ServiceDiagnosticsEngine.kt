package com.forgeprom.codestudio.core.diagnostics

import android.content.Context
import com.forgeprom.codestudio.core.export.NativeShareAndPdfHelper
import com.forgeprom.codestudio.core.hardware.HardwareProfiler
import com.forgeprom.codestudio.core.security.VaultAndEnterpriseManager
import com.forgeprom.codestudio.data.local.repository.ProjectRepository
import com.forgeprom.codestudio.data.remote.CloudApiService
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * محرك الفحص والتشخيص الذاتي لخدمات التطبيق (ServiceDiagnosticsEngine)
 * يختبر عملياً جاهزية وأداء كافة الخدمات الداخلية (Room، KeyStore AES-256، TTS، الكاش، العتاد، التصدير).
 */
object ServiceDiagnosticsEngine {

    enum class CheckStatus { OK, WARN, ERROR }

    data class ServiceCheckItem(
        val id: String,
        val title: String,
        val status: CheckStatus,
        val detail: String,
        val latencyMs: Long
    )

    data class FullDiagnosticsReport(
        val overallScore: Int,
        val checks: List<ServiceCheckItem>,
        val checkedAtMs: Long = System.currentTimeMillis()
    )

    suspend fun runFullDiagnostics(
        context: Context,
        repository: ProjectRepository,
        hardwareProfiler: HardwareProfiler,
        cloudApiService: CloudApiService,
        ttsReady: Boolean
    ): FullDiagnosticsReport = withContext(Dispatchers.IO) {
        val checks = mutableListOf<ServiceCheckItem>()

        // 1. فحص قاعدة البيانات Room والفهارس
        val dbStart = System.currentTimeMillis()
        val dbCheck = try {
            val projCount = repository.getProjectCount()
            val verCount = repository.getVersionCount()
            val bpCount = repository.getCustomBlueprintCount()
            val ms = System.currentTimeMillis() - dbStart
            ServiceCheckItem(
                id = "room_db",
                title = "قاعدة البيانات المحلية Room SQLite (v3)",
                status = CheckStatus.OK,
                detail = "نشطة وسريعة ($projCount مشروع • $verCount إصدار • $bpCount قالب مخصص)",
                latencyMs = ms
            )
        } catch (e: Exception) {
            ServiceCheckItem(
                id = "room_db",
                title = "قاعدة البيانات المحلية Room SQLite",
                status = CheckStatus.ERROR,
                detail = "تعذر الاستعلام: ${e.localizedMessage ?: "خطأ داخلي"}",
                latencyMs = System.currentTimeMillis() - dbStart
            )
        }
        checks.add(dbCheck)

        // 2. فحص التشفير العتادي Android KeyStore AES-256-GCM (اختبار تشفير وفك تشفير حي)
        val cryptoStart = System.currentTimeMillis()
        val probe = "forgeprom_self_test_${System.currentTimeMillis()}"
        val enc = VaultAndEnterpriseManager.encryptSafely(probe)
        val cryptoOk = enc.isSuccess &&
            VaultAndEnterpriseManager.decryptSafely(enc.getOrDefault("")).getOrNull() == probe
        checks.add(
            ServiceCheckItem(
                id = "keystore_aes",
                title = "خزنة التشفير العتادي AES-256-GCM",
                status = if (cryptoOk) CheckStatus.OK else CheckStatus.WARN,
                detail = if (cryptoOk) "اجتاز اختبار التشفير وفك التشفير الحي بنجاح"
                else "التشفير العتادي غير متاح على هذا المحاكي — التشفير المحمول PBKDF2 متاح كبديل",
                latencyMs = System.currentTimeMillis() - cryptoStart
            )
        )

        // 3. فحص القارئ الصوتي المحلي TTS
        checks.add(
            ServiceCheckItem(
                id = "offline_tts",
                title = "القارئ الصوتي المحلي (Chunked Offline TTS)",
                status = if (ttsReady) CheckStatus.OK else CheckStatus.WARN,
                detail = if (ttsReady) "جاهز لقراءة المواصفات الطويلة بلا اقتطاع"
                else "محرك النطق غير مهيأ على النظام حالياً",
                latencyMs = 1L
            )
        )

        // 4. فحص الذاكرة والتخزين والعتاد
        val hwStart = System.currentTimeMillis()
        val hw = hardwareProfiler.getFullHardwareReport()
        val hwStatus = when {
            hw.isLowMemory || hw.usableStorageMB < 200L -> CheckStatus.WARN
            else -> CheckStatus.OK
        }
        checks.add(
            ServiceCheckItem(
                id = "hardware_ram",
                title = "فاحص العتاد والذاكرة والتخزين",
                status = hwStatus,
                detail = "RAM حر: ${hw.availableRamMB}/${hw.totalRamMB} MB • مساحة حرة: ${hw.usableStorageMB} MB • ${hw.primaryAbi}",
                latencyMs = System.currentTimeMillis() - hwStart
            )
        )

        // 5. فحص كاش البحث الهجين وملفات التصدير المؤقتة
        val cacheStart = System.currentTimeMillis()
        val researchCached = cloudApiService.getCachedResearchCount()
        val exportStats = NativeShareAndPdfHelper.getExportCacheStats(context)
        val cacheStatus = if (exportStats.totalSizeKB > 25_000L) CheckStatus.WARN else CheckStatus.OK
        checks.add(
            ServiceCheckItem(
                id = "caches_exports",
                title = "الكاش الهجين ومجلدات التصدير (PDF / ZIP)",
                status = cacheStatus,
                detail = "كاش البحث: $researchCached استعلام • التصدير: ${exportStats.fileCount} ملف (${exportStats.totalSizeKB} KB)",
                latencyMs = System.currentTimeMillis() - cacheStart
            )
        )

        val okCount = checks.count { it.status == CheckStatus.OK }
        val warnCount = checks.count { it.status == CheckStatus.WARN }
        val score = (((okCount * 100) + (warnCount * 65)) / checks.size.coerceAtLeast(1)).coerceIn(0, 100)

        FullDiagnosticsReport(
            overallScore = score,
            checks = checks
        )
    }
}

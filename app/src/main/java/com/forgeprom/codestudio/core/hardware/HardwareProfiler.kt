package com.forgeprom.codestudio.core.hardware

import android.app.ActivityManager
import android.content.Context
import android.os.Build
import java.io.File

/**
 * فاحص العتاد البرمجي وإدارة الذاكرة والتخزين (HardwareProfiler)
 * معيار الأمان الصارم لمنع انهيار النظام (OOM) في الهواتف المتوسطة (4GB - 8GB RAM):
 * حجم ملف النموذج (MB) * 1.25 <= الذاكرة العشوائية الحرة المتاحة (MB)
 */
class HardwareProfiler(private val context: Context) {

    data class MemorySafetyReport(
        val isSafe: Boolean,
        val availableRamMB: Long,
        val requiredRamMB: Long,
        val differenceMB: Long,
        val recommendedThreads: Int,
        val warningMessage: String? = null
    )

    data class DeviceHardwareReport(
        val availableRamMB: Long,
        val totalRamMB: Long,
        val usableStorageMB: Long,
        val totalStorageMB: Long,
        val cpuCores: Int,
        val recommendedThreads: Int,
        val primaryAbi: String,
        val isLowMemory: Boolean,
        val memoryPressureLabel: String
    ) {
        val ramUsagePercent: Int
            get() = if (totalRamMB > 0L) {
                (((totalRamMB - availableRamMB).coerceAtLeast(0L) * 100L) / totalRamMB).toInt().coerceIn(0, 100)
            } else 0
    }

    fun getAvailableRamMB(): Long {
        val actManager = context.getSystemService(Context.ACTIVITY_SERVICE) as ActivityManager
        val memInfo = ActivityManager.MemoryInfo()
        actManager.getMemoryInfo(memInfo)
        return memInfo.availMem / (1024L * 1024L)
    }

    fun getTotalRamMB(): Long {
        val actManager = context.getSystemService(Context.ACTIVITY_SERVICE) as ActivityManager
        val memInfo = ActivityManager.MemoryInfo()
        actManager.getMemoryInfo(memInfo)
        return memInfo.totalMem / (1024L * 1024L)
    }

    fun getRecommendedThreadCount(): Int {
        val cores = Runtime.getRuntime().availableProcessors()
        return (cores - 2).coerceAtLeast(2).coerceAtMost(6)
    }

    fun getUsableStorageMB(): Long {
        val dir = context.filesDir
        return (dir.usableSpace / (1024L * 1024L)).coerceAtLeast(0L)
    }

    fun getFullHardwareReport(): DeviceHardwareReport {
        val actManager = context.getSystemService(Context.ACTIVITY_SERVICE) as ActivityManager
        val memInfo = ActivityManager.MemoryInfo()
        actManager.getMemoryInfo(memInfo)

        val availRam = memInfo.availMem / (1024L * 1024L)
        val totalRam = memInfo.totalMem / (1024L * 1024L)
        val filesDir = context.filesDir
        val usableStorage = (filesDir.usableSpace / (1024L * 1024L)).coerceAtLeast(0L)
        val totalStorage = (filesDir.totalSpace / (1024L * 1024L)).coerceAtLeast(1L)
        val cores = Runtime.getRuntime().availableProcessors()
        val abi = Build.SUPPORTED_ABIS.firstOrNull() ?: "arm64-v8a"

        val pressureLabel = when {
            memInfo.lowMemory || availRam < 600 -> "ضغط ذاكرة مرتفع (يُوصى بالوضع الخفيف)"
            availRam < 1400 -> "ذاكرة متوسطة (مناسب لنماذج 1B - 1.5B)"
            else -> "ذاكرة ممتازة (جاهز لنماذج 1.5B - 3B)"
        }

        return DeviceHardwareReport(
            availableRamMB = availRam,
            totalRamMB = totalRam,
            usableStorageMB = usableStorage,
            totalStorageMB = totalStorage,
            cpuCores = cores,
            recommendedThreads = getRecommendedThreadCount(),
            primaryAbi = abi,
            isLowMemory = memInfo.lowMemory,
            memoryPressureLabel = pressureLabel
        )
    }

    fun verifyModelMemorySafety(modelFile: File): MemorySafetyReport {
        val modelSizeMB = modelFile.length() / (1024L * 1024L)
        val requiredRamMB = (modelSizeMB * 1.25).toLong()
        val availableRamMB = getAvailableRamMB()
        val differenceMB = availableRamMB - requiredRamMB
        val isSafe = differenceMB >= 0

        val warning = if (!isSafe) {
            "الذاكرة الحرة المتاحة ($availableRamMB MB) غير كافية لتحميل النموذج بأمان. يتطلب النموذج $requiredRamMB MB على الأقل لمنع إغلاق النظام (OOM)."
        } else null

        return MemorySafetyReport(
            isSafe = isSafe,
            availableRamMB = availableRamMB,
            requiredRamMB = requiredRamMB,
            differenceMB = differenceMB,
            recommendedThreads = getRecommendedThreadCount(),
            warningMessage = warning
        )
    }
}

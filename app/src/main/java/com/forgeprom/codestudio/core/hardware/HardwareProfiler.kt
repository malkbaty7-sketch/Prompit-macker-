package com.forgeprom.codestudio.core.hardware

import android.app.ActivityManager
import android.content.Context
import java.io.File

/**
 * فاحص العتاد البرمجي وإدارة الذاكرة الصارمة (HardwareProfiler)
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

    fun getAvailableRamMB(): Long {
        val actManager = context.getSystemService(Context.ACTIVITY_SERVICE) as ActivityManager
        val memInfo = ActivityManager.MemoryInfo()
        actManager.getMemoryInfo(memInfo)
        return memInfo.availMem / (1024 * 1024)
    }

    fun getTotalRamMB(): Long {
        val actManager = context.getSystemService(Context.ACTIVITY_SERVICE) as ActivityManager
        val memInfo = ActivityManager.MemoryInfo()
        actManager.getMemoryInfo(memInfo)
        return memInfo.totalMem / (1024 * 1024)
    }

    fun getRecommendedThreadCount(): Int {
        val cores = Runtime.getRuntime().availableProcessors()
        return (cores - 2).coerceAtLeast(2).coerceAtMost(6)
    }

    fun verifyModelMemorySafety(modelFile: File): MemorySafetyReport {
        val modelSizeMB = modelFile.length() / (1024 * 1024)
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

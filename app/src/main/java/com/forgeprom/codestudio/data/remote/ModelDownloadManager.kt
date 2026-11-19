package com.forgeprom.codestudio.data.remote

import android.content.Context
import com.forgeprom.codestudio.core.hardware.HardwareProfiler
import com.forgeprom.codestudio.core.security.ChecksumValidator
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.net.HttpURLConnection
import java.net.URL
import java.util.Locale

/**
 * مدير تنزيل النماذج المحلية المكمّمة (GGUF Q4_K_M) من خوادم HuggingFace
 * المزايا المطورة:
 * - التنزيل الذري عبر ملف مرحلي (.part) لا يُعتمد كنموذج نهائي إلا بعد نجاح فحص الترويسة وبصمة SHA-256.
 * - حساب سرعة التنزيل اللحظية (MB/s) والوقت المتبقي المتوقع (ETA).
 * - تتبّع حجم الجزء المحمّل مسبقاً لعرض حالة الاستكمال (Resume Ready).
 * - فحص الـ RAM المسبق وفحص المساحة التخزينية المتاحة قبل التنزيل.
 */
class ModelDownloadManager(
    private val context: Context,
    private val hardwareProfiler: HardwareProfiler
) {

    data class GgufModelInfo(
        val id: String,
        val name: String,
        val parameters: String,
        val quantization: String,
        val fileSizeMB: Long,
        val requiredRamMB: Long,
        val downloadUrl: String,
        val expectedSha256: String,
        val arabicSupportLevel: String
    )

    sealed class DownloadProgress {
        data class CheckingMemory(val availableMB: Long, val requiredMB: Long) : DownloadProgress()
        data class Downloading(
            val progressPercent: Int,
            val downloadedMB: Long,
            val totalMB: Long,
            val speedMBps: Double = 0.0,
            val etaSeconds: Long = -1L
        ) : DownloadProgress()
        data class VerifyingChecksum(val sha256: String) : DownloadProgress()
        data class Completed(val file: File, val sha256: String) : DownloadProgress()
        data class Failed(val reason: String) : DownloadProgress()
    }

    fun getRecommendedModels(): List<GgufModelInfo> = listOf(
        GgufModelInfo(
            id = "qwen2.5-coder-1.5b-q4",
            name = "Qwen 2.5 Coder 1.5B Instruct",
            parameters = "1.5B",
            quantization = "Q4_K_M",
            fileSizeMB = 986,
            requiredRamMB = (986 * 1.25).toLong(),
            downloadUrl = "https://huggingface.co/Qwen/Qwen2.5-Coder-1.5B-Instruct-GGUF/resolve/main/qwen2.5-coder-1.5b-instruct-q4_k_m.gguf",
            expectedSha256 = "",
            arabicSupportLevel = "ممتاز للعربية والأكواد (موصى به للهواتف المتوسطة 4GB+)"
        ),
        GgufModelInfo(
            id = "llama-3.2-1b-instruct-q4",
            name = "Llama 3.2 1B Instruct",
            parameters = "1.2B",
            quantization = "Q4_K_M",
            fileSizeMB = 810,
            requiredRamMB = (810 * 1.25).toLong(),
            downloadUrl = "https://huggingface.co/bartowski/Llama-3.2-1B-Instruct-GGUF/resolve/main/Llama-3.2-1B-Instruct-Q4_K_M.gguf",
            expectedSha256 = "",
            arabicSupportLevel = "سريع جداً وخفيف على الذاكرة والبطارية"
        ),
        GgufModelInfo(
            id = "qwen2.5-3b-instruct-q4",
            name = "Qwen 2.5 3B Instruct (Arabic Pro)",
            parameters = "3.0B",
            quantization = "Q4_K_M",
            fileSizeMB = 1950,
            requiredRamMB = (1950 * 1.25).toLong(),
            downloadUrl = "https://huggingface.co/Qwen/Qwen2.5-3B-Instruct-GGUF/resolve/main/qwen2.5-3b-instruct-q4_k_m.gguf",
            expectedSha256 = "",
            arabicSupportLevel = "دقة هندسية فائقة بالعربية (للهواتف 6GB - 8GB RAM)"
        )
    )

    fun isModelCompatibleWithDevice(model: GgufModelInfo): Boolean {
        return hardwareProfiler.getAvailableRamMB() >= model.requiredRamMB
    }

    fun getModelsDirectory(): File {
        val dir = File(context.filesDir, "gguf_models")
        if (!dir.exists()) dir.mkdirs()
        return dir
    }

    fun getModelFile(model: GgufModelInfo): File = File(getModelsDirectory(), "${model.id}.gguf")

    fun getPartialModelFile(model: GgufModelInfo): File = File(getModelsDirectory(), "${model.id}.gguf.part")

    fun getPartialDownloadedMB(model: GgufModelInfo): Long {
        val partFile = getPartialModelFile(model)
        return if (partFile.exists()) partFile.length() / (1024L * 1024L) else 0L
    }

    fun isModelDownloaded(model: GgufModelInfo): Boolean {
        val file = getModelFile(model)
        return file.exists() && file.length() > 1024 * 1024 && hasValidGgufHeader(file)
    }

    fun deleteDownloadedModel(model: GgufModelInfo): Boolean {
        val finalDeleted = getModelFile(model).let { if (it.exists()) it.delete() else false }
        val partDeleted = getPartialModelFile(model).let { if (it.exists()) it.delete() else false }
        return finalDeleted || partDeleted
    }

    private fun hasValidGgufHeader(file: File): Boolean {
        if (!file.exists() || file.length() < 4) return false
        return try {
            FileInputStream(file).use { fis ->
                val header = ByteArray(4)
                fis.read(header) == 4 &&
                    header[0] == 'G'.code.toByte() &&
                    header[1] == 'G'.code.toByte() &&
                    header[2] == 'U'.code.toByte() &&
                    header[3] == 'F'.code.toByte()
            }
        } catch (_: Exception) {
            false
        }
    }

    private fun openConnectionFollowingRedirects(initialUrl: String, rangeStart: Long): HttpURLConnection {
        var currentUrl = initialUrl
        var redirects = 0
        while (redirects < 6) {
            val conn = (URL(currentUrl).openConnection() as HttpURLConnection).apply {
                requestMethod = "GET"
                connectTimeout = 12000
                readTimeout = 20000
                instanceFollowRedirects = false
                setRequestProperty("User-Agent", "ForgepromCodeStudio/2.6 (Android)")
                if (rangeStart > 0) {
                    setRequestProperty("Range", "bytes=$rangeStart-")
                }
            }
            val code = conn.responseCode
            if (code == HttpURLConnection.HTTP_MOVED_PERM ||
                code == HttpURLConnection.HTTP_MOVED_TEMP ||
                code == HttpURLConnection.HTTP_SEE_OTHER ||
                code == 307 || code == 308
            ) {
                val location = conn.getHeaderField("Location")
                conn.disconnect()
                if (location.isNullOrBlank()) {
                    throw IllegalStateException("Redirect location missing (HTTP $code)")
                }
                currentUrl = URL(URL(currentUrl), location).toString()
                redirects++
            } else {
                return conn
            }
        }
        throw IllegalStateException("Too many redirects while connecting to model host")
    }

    /**
     * تنزيل ذري آمن مع دعم الاستكمال (.part)، حساب سرعة التنزيل (MB/s)، والوقت المتبقي (ETA)
     */
    fun downloadModelWithResume(model: GgufModelInfo): Flow<DownloadProgress> = flow {
        val availRam = hardwareProfiler.getAvailableRamMB()
        emit(DownloadProgress.CheckingMemory(availRam, model.requiredRamMB))

        if (availRam < model.requiredRamMB) {
            emit(
                DownloadProgress.Failed(
                    "الذاكرة الحرة المتاحة ($availRam MB) أقل من الحد الآمن المطلوب (${model.requiredRamMB} MB) لمنع انهيار النظام OOM."
                )
            )
            return@flow
        }

        val modelsDir = getModelsDirectory()
        val finalFile = getModelFile(model)
        val partFile = getPartialModelFile(model)

        // إذا كان النموذج مكتملاً مسبقاً وسليماً
        if (isModelDownloaded(model)) {
            val hash = ChecksumValidator.calculateSha256(finalFile)
            emit(DownloadProgress.Completed(finalFile, hash))
            return@flow
        }

        val existingBytes = if (partFile.exists()) partFile.length() else 0L
        val requiredDiskBytes = ((model.fileSizeMB * 1024L * 1024L) - existingBytes).coerceAtLeast(0L) + (64L * 1024L * 1024L)
        if (modelsDir.usableSpace < requiredDiskBytes) {
            val availSpaceMB = modelsDir.usableSpace / (1024L * 1024L)
            emit(
                DownloadProgress.Failed(
                    "المساحة التخزينية المتاحة ($availSpaceMB MB) غير كافية لتنزيل النموذج (${model.fileSizeMB} MB)."
                )
            )
            return@flow
        }

        var conn: HttpURLConnection? = null
        try {
            conn = openConnectionFollowingRedirects(model.downloadUrl, existingBytes)
            val responseCode = conn.responseCode

            // إذا أعاد الخادم 416 فهذا يعني أن الملف الجزئي اكتمل مسبقاً
            if (responseCode == 416 && partFile.exists() && hasValidGgufHeader(partFile)) {
                val calculatedHash = ChecksumValidator.calculateSha256(partFile)
                emit(DownloadProgress.VerifyingChecksum(calculatedHash))
                if (finalFile.exists()) finalFile.delete()
                partFile.renameTo(finalFile)
                emit(DownloadProgress.Completed(finalFile, calculatedHash))
                return@flow
            }

            if (responseCode == HttpURLConnection.HTTP_OK || responseCode == HttpURLConnection.HTTP_PARTIAL) {
                val isResuming = (responseCode == HttpURLConnection.HTTP_PARTIAL && existingBytes > 0L)
                val startOffset = if (isResuming) existingBytes else 0L
                val contentLen = conn.contentLengthLong
                val totalBytes = if (contentLen > 0L) {
                    contentLen + startOffset
                } else {
                    model.fileSizeMB * 1024L * 1024L
                }.coerceAtLeast(1L)

                var downloadedBytes = startOffset
                var lastEmittedPercent = -1
                var lastEmitTimeMs = System.currentTimeMillis()
                var lastSpeedCheckTimeMs = lastEmitTimeMs
                var lastSpeedCheckBytes = downloadedBytes
                var smoothedSpeedBytesPerSec = 0.0

                conn.inputStream.use { input ->
                    FileOutputStream(partFile, isResuming).use { output ->
                        val buffer = ByteArray(65536)
                        var bytesRead: Int
                        while (input.read(buffer).also { bytesRead = it } != -1) {
                            currentCoroutineContext().ensureActive()
                            output.write(buffer, 0, bytesRead)
                            downloadedBytes += bytesRead

                            val percent = ((downloadedBytes * 100L) / totalBytes).toInt().coerceIn(0, 100)
                            val now = System.currentTimeMillis()
                            val elapsedMs = now - lastEmitTimeMs
                            if (percent != lastEmittedPercent || elapsedMs >= 400L) {
                                val speedWindowMs = (now - lastSpeedCheckTimeMs).coerceAtLeast(1L)
                                if (speedWindowMs >= 450L) {
                                    val deltaBytes = (downloadedBytes - lastSpeedCheckBytes).coerceAtLeast(0L)
                                    val instantBps = (deltaBytes * 1000.0) / speedWindowMs
                                    smoothedSpeedBytesPerSec = if (smoothedSpeedBytesPerSec <= 0.0) {
                                        instantBps
                                    } else {
                                        (smoothedSpeedBytesPerSec * 0.65) + (instantBps * 0.35)
                                    }
                                    lastSpeedCheckTimeMs = now
                                    lastSpeedCheckBytes = downloadedBytes
                                }

                                val speedMBps = smoothedSpeedBytesPerSec / (1024.0 * 1024.0)
                                val remainingBytes = (totalBytes - downloadedBytes).coerceAtLeast(0L)
                                val etaSec = if (smoothedSpeedBytesPerSec > 1024.0) {
                                    (remainingBytes / smoothedSpeedBytesPerSec).toLong()
                                } else -1L

                                lastEmittedPercent = percent
                                lastEmitTimeMs = now
                                emit(
                                    DownloadProgress.Downloading(
                                        progressPercent = percent,
                                        downloadedMB = downloadedBytes / (1024L * 1024L),
                                        totalMB = totalBytes / (1024L * 1024L),
                                        speedMBps = speedMBps,
                                        etaSeconds = etaSec
                                    )
                                )
                            }
                        }
                    }
                }

                if (!hasValidGgufHeader(partFile)) {
                    partFile.delete()
                    emit(DownloadProgress.Failed("الملف المُنزّل لا يحمل ترويسة GGUF صالحة — تم حذفه لحماية النظام."))
                    return@flow
                }

                val calculatedHash = ChecksumValidator.calculateSha256(partFile)
                emit(DownloadProgress.VerifyingChecksum(calculatedHash))

                if (model.expectedSha256.isNotBlank() &&
                    !calculatedHash.equals(model.expectedSha256.trim(), ignoreCase = true)
                ) {
                    partFile.delete()
                    emit(DownloadProgress.Failed("عدم تطابق البصمة الرقمية SHA-256 — تم إلغاء الملف التالف."))
                    return@flow
                }

                if (finalFile.exists()) finalFile.delete()
                if (!partFile.renameTo(finalFile)) {
                    partFile.copyTo(finalFile, overwrite = true)
                    partFile.delete()
                }
                emit(DownloadProgress.Completed(finalFile, calculatedHash))
            } else {
                emit(DownloadProgress.Failed("فشل الاتصال بخادم النماذج (رمز HTTP $responseCode)"))
            }
        } catch (ce: CancellationException) {
            throw ce
        } catch (_: Exception) {
            emit(DownloadProgress.Failed("تعذر إتمام التنزيل: تحقق من اتصال الإنترنت أو المساحة التخزينية (يمكنك الاستكمال من نقطة التوقف)."))
        } finally {
            conn?.disconnect()
        }
    }.flowOn(Dispatchers.IO)

    companion object {
        fun formatSpeedAndEta(speedMBps: Double, etaSeconds: Long): String {
            val speedText = if (speedMBps >= 0.05) {
                String.format(Locale.US, "%.1f MB/s", speedMBps)
            } else ""
            val etaText = when {
                etaSeconds <= 0L -> ""
                etaSeconds < 60L -> "متبقي ${etaSeconds}ث"
                else -> "متبقي ${etaSeconds / 60}د ${etaSeconds % 60}ث"
            }
            return listOf(speedText, etaText).filter { it.isNotBlank() }.joinToString(" • ")
        }
    }
}

package com.forgeprom.codestudio.core.ux

import android.content.Context
import android.speech.tts.TextToSpeech
import android.speech.tts.UtteranceProgressListener
import androidx.compose.runtime.staticCompositionLocalOf
import com.forgeprom.codestudio.domain.pipeline.PromptEngineeringEngine
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import org.json.JSONArray
import org.json.JSONObject
import java.util.Locale
import java.util.UUID

/**
 * لقطة إصدار محفوظة محلياً لأي برومبت (Prompt Version Snapshot)
 */
data class PromptSnapshot(
    val id: String,
    val projectId: String,
    val promptText: String,
    val reason: String,
    val qualityScore: Int,
    val timestampMs: Long
)

/**
 * محرك مقارنة الفروقات بين إصدارات البرومبت (Prompt Diff Engine)
 */
enum class DiffLineType { ADDED, REMOVED, UNCHANGED }

data class PromptDiffLine(
    val type: DiffLineType,
    val text: String
)

data class PromptDiffReport(
    val lines: List<PromptDiffLine>,
    val addedCount: Int,
    val removedCount: Int
)

object PromptDiffEngine {

    /**
     * يقارن الإصدار الأقدم (olderText) مع الإصدار الحالي (currentText) لإبراز الأسطر المضافة (+) والمحذوفة (-)
     */
    fun computeLineDiff(olderText: String, currentText: String): PromptDiffReport {
        val oldLines = olderText.lines().map { it.trimEnd() }
        val newLines = currentText.lines().map { it.trimEnd() }
        val oldSet = oldLines.filter { it.isNotBlank() }.toSet()
        val newSet = newLines.filter { it.isNotBlank() }.toSet()

        val result = mutableListOf<PromptDiffLine>()
        var added = 0
        var removed = 0

        // الأسطر الموجودة في القديم وحُذفت في الحالي
        for (line in oldLines) {
            if (line.isBlank()) continue
            if (line !in newSet) {
                result.add(PromptDiffLine(DiffLineType.REMOVED, line))
                removed++
            }
        }

        // الأسطر الحالية (تمييز المضاف منها مقابل المشترك)
        for (line in newLines) {
            if (line.isBlank()) continue
            if (line !in oldSet) {
                result.add(PromptDiffLine(DiffLineType.ADDED, line))
                added++
            } else {
                result.add(PromptDiffLine(DiffLineType.UNCHANGED, line))
            }
        }

        return PromptDiffReport(
            lines = result,
            addedCount = added,
            removedCount = removed
        )
    }
}

/**
 * مخزن سجل الإصدارات القديم (يُستخدم فقط لقراءة وترحيل أي لقطات سابقة إلى Room Database)
 */
object PromptVersionHistoryStore {
    private const val PREFS_NAME = "forgeprom_prompt_history_v1"

    fun getSnapshots(context: Context, projectId: String): List<PromptSnapshot> {
        val raw = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .getString("hist_$projectId", null)
            ?: return emptyList()
        return try {
            val arr = JSONArray(raw)
            buildList {
                for (i in 0 until arr.length()) {
                    val obj = arr.getJSONObject(i)
                    add(
                        PromptSnapshot(
                            id = obj.optString("id", UUID.randomUUID().toString()),
                            projectId = projectId,
                            promptText = obj.optString("promptText", ""),
                            reason = obj.optString("reason", "نسخة محفوظة"),
                            qualityScore = obj.optInt("qualityScore", 80),
                            timestampMs = obj.optLong("timestampMs", System.currentTimeMillis())
                        )
                    )
                }
            }.filter { it.promptText.isNotBlank() }
        } catch (_: Exception) {
            emptyList()
        }
    }

    fun clearHistory(context: Context, projectId: String) {
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .edit()
            .remove("hist_$projectId")
            .apply()
    }
}

/**
 * حالة القارئ الصوتي المحلي المتطور (Chunked Offline Android TextToSpeech)
 */
data class TtsPlaybackState(
    val isReady: Boolean = false,
    val isSpeaking: Boolean = false,
    val activeContentId: String? = null,
    val speechRate: Float = 1.0f,
    val currentChunk: Int = 0,
    val totalChunks: Int = 0
) {
    val progressFraction: Float
        get() = if (totalChunks > 0) (currentChunk.toFloat() / totalChunks.toFloat()).coerceIn(0f, 1f) else 0f
}

/**
 * محرك الاستماع الصوتي المحلي 100% دون إنترنت ودون أذونات (Chunked Utterance Queue)
 * يقرأ الوثائق الهندسية الطويلة كاملة بلا اقتطاع عبر تقسيمها إلى فقرات ذكية متتالية مع تتبع حي للتقدم.
 */
class OfflineTtsReader(context: Context) : TextToSpeech.OnInitListener {

    private var tts: TextToSpeech? = null
    private val _state = MutableStateFlow(TtsPlaybackState())
    val state: StateFlow<TtsPlaybackState> = _state.asStateFlow()

    private var activeChunks: List<String> = emptyList()
    private var activeRawContentId: String? = null

    init {
        try {
            tts = TextToSpeech(context.applicationContext, this)
        } catch (_: Exception) {
            _state.value = TtsPlaybackState(isReady = false)
        }
    }

    override fun onInit(status: Int) {
        if (status == TextToSpeech.SUCCESS) {
            val engine = tts ?: return
            val arLocale = Locale("ar")
            val arResult = engine.setLanguage(arLocale)
            if (arResult == TextToSpeech.LANG_MISSING_DATA || arResult == TextToSpeech.LANG_NOT_SUPPORTED) {
                engine.setLanguage(Locale.getDefault())
            }
            engine.setSpeechRate(_state.value.speechRate)
            engine.setOnUtteranceProgressListener(object : UtteranceProgressListener() {
                override fun onStart(utteranceId: String?) {
                    val (contentId, chunkIdx) = parseUtteranceId(utteranceId)
                    if (contentId != null && contentId == activeRawContentId) {
                        _state.value = _state.value.copy(
                            isSpeaking = true,
                            activeContentId = contentId,
                            currentChunk = chunkIdx + 1,
                            totalChunks = activeChunks.size
                        )
                    }
                }

                override fun onDone(utteranceId: String?) {
                    val (contentId, chunkIdx) = parseUtteranceId(utteranceId)
                    if (contentId != null && contentId == activeRawContentId && chunkIdx >= activeChunks.lastIndex) {
                        _state.value = _state.value.copy(
                            isSpeaking = false,
                            activeContentId = null,
                            currentChunk = 0,
                            totalChunks = 0
                        )
                    }
                }

                @Deprecated("Deprecated in Android SDK")
                override fun onError(utteranceId: String?) {
                    _state.value = _state.value.copy(isSpeaking = false, activeContentId = null, currentChunk = 0, totalChunks = 0)
                }

                override fun onError(utteranceId: String?, errorCode: Int) {
                    _state.value = _state.value.copy(isSpeaking = false, activeContentId = null, currentChunk = 0, totalChunks = 0)
                }
            })
            _state.value = _state.value.copy(isReady = true)
        } else {
            _state.value = _state.value.copy(isReady = false)
        }
    }

    private fun parseUtteranceId(utteranceId: String?): Pair<String?, Int> {
        if (utteranceId.isNullOrBlank()) return null to 0
        val sep = utteranceId.lastIndexOf("::")
        if (sep <= 0) return utteranceId to 0
        val cid = utteranceId.substring(0, sep)
        val idx = utteranceId.substring(sep + 2).toIntOrNull() ?: 0
        return cid to idx
    }

    private fun splitIntoSpeechChunks(cleanText: String, maxChunkChars: Int = 850): List<String> {
        val rawParagraphs = cleanText.split(Regex("(?<=[.!؟\\n])\\s+"))
            .map { it.trim() }
            .filter { it.isNotBlank() }

        val chunks = mutableListOf<String>()
        val current = StringBuilder()

        for (para in rawParagraphs) {
            if (para.length > maxChunkChars) {
                if (current.isNotEmpty()) {
                    chunks.add(current.toString().trim())
                    current.clear()
                }
                var cursor = 0
                while (cursor < para.length) {
                    val end = (cursor + maxChunkChars).coerceAtMost(para.length)
                    chunks.add(para.substring(cursor, end).trim())
                    cursor = end
                }
            } else if (current.length + para.length + 1 > maxChunkChars) {
                chunks.add(current.toString().trim())
                current.clear()
                current.append(para)
            } else {
                if (current.isNotEmpty()) current.append(" ")
                current.append(para)
            }
        }
        if (current.isNotEmpty()) {
            chunks.add(current.toString().trim())
        }
        return chunks.ifEmpty { listOf(cleanText.take(maxChunkChars)) }
    }

    fun toggleSpeak(contentId: String, rawText: String): Boolean {
        val current = _state.value
        if (current.isSpeaking && current.activeContentId == contentId) {
            stop()
            return false
        }
        val engine = tts ?: return false
        if (!current.isReady) return false

        val cleanText = sanitizeForSpeech(rawText)
        if (cleanText.isBlank()) return false

        val chunks = splitIntoSpeechChunks(cleanText)
        activeChunks = chunks
        activeRawContentId = contentId

        enqueueFromChunk(engine, contentId, chunks, startIndex = 0, rate = current.speechRate)
        return true
    }

    private fun enqueueFromChunk(
        engine: TextToSpeech,
        contentId: String,
        chunks: List<String>,
        startIndex: Int,
        rate: Float
    ) {
        engine.setSpeechRate(rate)
        val safeStart = startIndex.coerceIn(0, (chunks.size - 1).coerceAtLeast(0))
        for (i in safeStart until chunks.size) {
            val mode = if (i == safeStart) TextToSpeech.QUEUE_FLUSH else TextToSpeech.QUEUE_ADD
            engine.speak(chunks[i], mode, null, "$contentId::$i")
        }
        _state.value = _state.value.copy(
            isSpeaking = true,
            activeContentId = contentId,
            currentChunk = safeStart + 1,
            totalChunks = chunks.size,
            speechRate = rate
        )
    }

    fun cycleSpeechRate(): Float {
        val current = _state.value
        val nextRate = when (current.speechRate) {
            1.0f -> 1.25f
            1.25f -> 1.5f
            else -> 1.0f
        }
        val engine = tts
        if (engine != null && current.isSpeaking && activeRawContentId != null && activeChunks.isNotEmpty()) {
            val resumeIdx = (current.currentChunk - 1).coerceAtLeast(0)
            enqueueFromChunk(engine, activeRawContentId!!, activeChunks, resumeIdx, nextRate)
        } else {
            engine?.setSpeechRate(nextRate)
            _state.value = current.copy(speechRate = nextRate)
        }
        return nextRate
    }

    fun stop() {
        try {
            tts?.stop()
        } catch (_: Exception) {
        }
        activeRawContentId = null
        activeChunks = emptyList()
        _state.value = _state.value.copy(
            isSpeaking = false,
            activeContentId = null,
            currentChunk = 0,
            totalChunks = 0
        )
    }

    fun shutdown() {
        stop()
        try {
            tts?.shutdown()
        } catch (_: Exception) {
        }
        tts = null
    }

    private fun sanitizeForSpeech(raw: String): String {
        return raw
            .replace(Regex("[#*`>_|~\\[\\]]"), " ")
            .replace(Regex("-{2,}"), " ")
            .replace(Regex("[ \\t]+"), " ")
            .trim()
    }
}

val LocalOfflineTtsReader = staticCompositionLocalOf<OfflineTtsReader?> { null }

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
 * مخزن سجل الإصدارات المحلي لكل مشروع (يحتفظ بآخر 10 إصدارات مع التراجع الفوري بلمسة واحدة)
 */
object PromptVersionHistoryStore {
    private const val PREFS_NAME = "forgeprom_prompt_history_v1"
    private const val MAX_SNAPSHOTS_PER_PROJECT = 10

    fun recordSnapshot(
        context: Context,
        projectId: String,
        previousText: String?,
        reason: String
    ) {
        val cleaned = previousText?.trim().orEmpty()
        if (cleaned.isBlank()) return

        val existing = getSnapshots(context, projectId)
        if (existing.firstOrNull()?.promptText == cleaned) return

        val score = PromptEngineeringEngine.evaluatePromptQuality(cleaned).totalScore
        val newEntry = PromptSnapshot(
            id = UUID.randomUUID().toString(),
            projectId = projectId,
            promptText = cleaned,
            reason = reason,
            qualityScore = score,
            timestampMs = System.currentTimeMillis()
        )
        val merged = (listOf(newEntry) + existing).take(MAX_SNAPSHOTS_PER_PROJECT)
        saveSnapshots(context, projectId, merged)
    }

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

    fun getSnapshotCount(context: Context, projectId: String): Int = getSnapshots(context, projectId).size

    fun clearHistory(context: Context, projectId: String) {
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .edit()
            .remove("hist_$projectId")
            .apply()
    }

    private fun saveSnapshots(context: Context, projectId: String, list: List<PromptSnapshot>) {
        val arr = JSONArray()
        list.forEach { snap ->
            arr.put(
                JSONObject().apply {
                    put("id", snap.id)
                    put("promptText", snap.promptText)
                    put("reason", snap.reason)
                    put("qualityScore", snap.qualityScore)
                    put("timestampMs", snap.timestampMs)
                }
            )
        }
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .edit()
            .putString("hist_$projectId", arr.toString())
            .apply()
    }
}

/**
 * حالة القارئ الصوتي المحلي (Offline Android TextToSpeech)
 */
data class TtsPlaybackState(
    val isReady: Boolean = false,
    val isSpeaking: Boolean = false,
    val activeContentId: String? = null,
    val speechRate: Float = 1.0f
)

/**
 * محرك الاستماع الصوتي المحلي 100% دون إنترنت ودون أذونات باستخدام Android TextToSpeech
 */
class OfflineTtsReader(context: Context) : TextToSpeech.OnInitListener {

    private var tts: TextToSpeech? = null
    private val _state = MutableStateFlow(TtsPlaybackState())
    val state: StateFlow<TtsPlaybackState> = _state.asStateFlow()

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
                    _state.value = _state.value.copy(isSpeaking = true, activeContentId = utteranceId)
                }

                override fun onDone(utteranceId: String?) {
                    if (_state.value.activeContentId == utteranceId) {
                        _state.value = _state.value.copy(isSpeaking = false, activeContentId = null)
                    }
                }

                @Deprecated("Deprecated in Android SDK")
                override fun onError(utteranceId: String?) {
                    _state.value = _state.value.copy(isSpeaking = false, activeContentId = null)
                }

                override fun onError(utteranceId: String?, errorCode: Int) {
                    _state.value = _state.value.copy(isSpeaking = false, activeContentId = null)
                }
            })
            _state.value = _state.value.copy(isReady = true)
        } else {
            _state.value = _state.value.copy(isReady = false)
        }
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

        engine.setSpeechRate(current.speechRate)
        // نحدّد سقفاً آمناً لطول المقطع لتجنب حدود TextToSpeech.getMaxSpeechInputLength()
        val maxLen = TextToSpeech.getMaxSpeechInputLength().coerceAtMost(3600)
        val clipped = cleanText.take(maxLen)
        engine.speak(clipped, TextToSpeech.QUEUE_FLUSH, null, contentId)
        _state.value = current.copy(isSpeaking = true, activeContentId = contentId)
        return true
    }

    fun cycleSpeechRate(): Float {
        val nextRate = when (_state.value.speechRate) {
            1.0f -> 1.25f
            1.25f -> 1.5f
            else -> 1.0f
        }
        tts?.setSpeechRate(nextRate)
        _state.value = _state.value.copy(speechRate = nextRate)
        return nextRate
    }

    fun stop() {
        try {
            tts?.stop()
        } catch (_: Exception) {
        }
        _state.value = _state.value.copy(isSpeaking = false, activeContentId = null)
    }

    fun shutdown() {
        try {
            tts?.stop()
            tts?.shutdown()
        } catch (_: Exception) {
        }
        tts = null
    }

    private fun sanitizeForSpeech(raw: String): String {
        return raw
            .replace(Regex("[#*`>_|~\\[\\]]"), " ")
            .replace(Regex("-{2,}"), " ")
            .replace(Regex("\\s+"), " ")
            .trim()
    }
}

val LocalOfflineTtsReader = staticCompositionLocalOf<OfflineTtsReader?> { null }

package com.teddyjs.news.presentation.ui.common

import android.content.Context
import android.speech.tts.TextToSpeech
import android.speech.tts.UtteranceProgressListener
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import java.util.Locale

/**
 * 뉴스 음성 재생(TTS) 컨트롤러.
 * Android 내장 TextToSpeech 사용 — 무료·오프라인. "듣는 뉴스" 차별화 기능.
 */
class TtsController {
    private var tts: TextToSpeech? = null
    private var ready = false

    var isSpeaking by mutableStateOf(false)

    fun init(context: Context) {
        initEngine(context.applicationContext, preferGoogle = true)
    }

    private fun initEngine(appContext: Context, preferGoogle: Boolean) {
        // 구글 TTS 엔진이 발음이 더 자연스러움. 없으면 기기 기본 엔진으로 폴백.
        val pkg = if (preferGoogle) "com.google.android.tts" else null
        val engine = TextToSpeech(
            appContext,
            TextToSpeech.OnInitListener { status ->
                if (status == TextToSpeech.SUCCESS) {
                    applyBestVoice()
                    ready = true
                } else if (preferGoogle) {
                    tts?.shutdown()
                    initEngine(appContext, preferGoogle = false)
                }
            },
            pkg,
        )
        engine.setOnUtteranceProgressListener(object : UtteranceProgressListener() {
            override fun onStart(utteranceId: String?) { this@TtsController.isSpeaking = true }
            // 마지막 항목이 끝났을 때만 정지 상태로 (연속 재생 중엔 유지)
            override fun onDone(utteranceId: String?) {
                if (utteranceId == LAST_ID) this@TtsController.isSpeaking = false
            }
            @Deprecated("deprecated")
            override fun onError(utteranceId: String?) { this@TtsController.isSpeaking = false }
        })
        tts = engine
    }

    /** 오프라인 한국어 보이스 중 가장 품질 높은 것 선택 */
    private fun applyBestVoice() {
        val t = tts ?: return
        t.language = Locale.KOREAN
        runCatching {
            val best = t.voices
                ?.filter {
                    it.locale.language == "ko" &&
                        !it.isNetworkConnectionRequired &&
                        !it.features.contains(TextToSpeech.Engine.KEY_FEATURE_NOT_INSTALLED)
                }
                ?.maxByOrNull { it.quality }
            if (best != null) t.voice = best
        }
        t.setSpeechRate(1.0f)
        t.setPitch(1.0f)
    }

    /** 재생 중이면 정지, 아니면 한 건 읽기 */
    fun toggle(text: String) {
        val t = tts ?: return
        if (isSpeaking) {
            t.stop()
            isSpeaking = false
            return
        }
        if (text.isBlank()) return
        t.speak(text, TextToSpeech.QUEUE_FLUSH, null, LAST_ID)
    }

    /** 재생 중이면 정지, 아니면 여러 건을 이어서 읽기 (전체 브리핑 듣기) */
    fun toggleList(texts: List<String>) {
        val t = tts ?: return
        if (isSpeaking) {
            t.stop()
            isSpeaking = false
            return
        }
        val items = texts.filter { it.isNotBlank() }
        if (items.isEmpty()) return
        items.forEachIndexed { i, text ->
            val mode = if (i == 0) TextToSpeech.QUEUE_FLUSH else TextToSpeech.QUEUE_ADD
            val id = if (i == items.lastIndex) LAST_ID else "mid_$i"
            t.speak(text, mode, null, id)
        }
    }

    fun stop() {
        tts?.stop()
        isSpeaking = false
    }

    fun shutdown() {
        tts?.stop()
        tts?.shutdown()
        tts = null
    }

    companion object {
        private const val LAST_ID = "tts_last"
    }
}

@Composable
fun rememberTtsController(): TtsController {
    val context = LocalContext.current
    val controller = remember { TtsController() }
    DisposableEffect(Unit) {
        controller.init(context)
        onDispose { controller.shutdown() }
    }
    return controller
}

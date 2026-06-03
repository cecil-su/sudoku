package com.sudoku.game.ai

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.speech.RecognitionListener
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer

/**
 * Thin wrapper over Android [SpeechRecognizer] for Chinese voice input.
 *
 * Lifecycle (the caller MUST honor it — see DemoPlayer): create once,
 * [startListening] per utterance, [stopListening] to cancel, [destroy] on
 * teardown. Per the 10.4 resource-lifecycle rule, destroy/stop are bound to the
 * screen's onStop/onDispose — never left running in the background.
 */
class VoiceInput(context: Context) {

    private val recognizer: SpeechRecognizer? =
        if (SpeechRecognizer.isRecognitionAvailable(context))
            SpeechRecognizer.createSpeechRecognizer(context) else null

    val available: Boolean get() = recognizer != null

    fun startListening(
        onResult: (String) -> Unit,
        onError: (String) -> Unit,
        onListeningChange: (Boolean) -> Unit
    ) {
        val sr = recognizer ?: run { onError("设备不支持语音识别"); return }
        sr.setRecognitionListener(object : RecognitionListener {
            override fun onReadyForSpeech(params: Bundle?) = onListeningChange(true)
            override fun onBeginningOfSpeech() {}
            override fun onRmsChanged(rmsdB: Float) {}
            override fun onBufferReceived(buffer: ByteArray?) {}
            override fun onEndOfSpeech() = onListeningChange(false)
            override fun onError(error: Int) {
                onListeningChange(false)
                onError(errorText(error))
            }
            override fun onResults(results: Bundle?) {
                onListeningChange(false)
                val text = results
                    ?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
                    ?.firstOrNull()
                    ?.trim()
                if (text.isNullOrEmpty()) onError("没听清，请再说一次") else onResult(text)
            }
            override fun onPartialResults(partialResults: Bundle?) {}
            override fun onEvent(eventType: Int, params: Bundle?) {}
        })
        val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
            putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
            putExtra(RecognizerIntent.EXTRA_LANGUAGE, "zh-CN")
            putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, false)
        }
        onListeningChange(true)
        sr.startListening(intent)
    }

    fun stopListening() = recognizer?.stopListening() ?: Unit

    fun destroy() = recognizer?.destroy() ?: Unit

    private fun errorText(error: Int): String = when (error) {
        SpeechRecognizer.ERROR_NO_MATCH -> "没听清，请再说一次"
        SpeechRecognizer.ERROR_SPEECH_TIMEOUT -> "没有听到声音"
        SpeechRecognizer.ERROR_INSUFFICIENT_PERMISSIONS -> "缺少麦克风权限"
        SpeechRecognizer.ERROR_NETWORK, SpeechRecognizer.ERROR_NETWORK_TIMEOUT -> "网络问题，语音识别失败"
        SpeechRecognizer.ERROR_RECOGNIZER_BUSY -> "识别忙，请稍后再试"
        else -> "语音识别出错（$error）"
    }
}

package com.smartnotes.features

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.speech.RecognitionListener
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

/**
 * On-device speech to text. In continuous mode it restarts after each utterance, and starts a new
 * "Turn N:" whenever the pause between utterances is long (used by meeting mode as a speaker-change hint).
 * Must be used from the main thread.
 */
class SpeechCapture(
    context: Context,
    private val continuous: Boolean,
    private val markTurns: Boolean = false,
    private val turnGapMs: Long = 2_500,
) {

    private val recognizer: SpeechRecognizer? =
        if (SpeechRecognizer.isRecognitionAvailable(context)) SpeechRecognizer.createSpeechRecognizer(context) else null

    private val segments = StringBuilder()
    private var lastResultAt = 0L
    private var turn = 0

    private val _transcript = MutableStateFlow("")
    val transcript: StateFlow<String> = _transcript

    private val _partial = MutableStateFlow("")
    val partial: StateFlow<String> = _partial

    private val _listening = MutableStateFlow(false)
    val listening: StateFlow<Boolean> = _listening

    private val _error = MutableStateFlow<String?>(null)
    val error: StateFlow<String?> = _error

    val available: Boolean get() = recognizer != null

    private val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
        putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
        putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, true)
    }

    private val listener = object : RecognitionListener {
        override fun onResults(results: Bundle?) {
            val text = results?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)?.firstOrNull().orEmpty()
            _partial.value = ""
            if (text.isNotBlank()) append(text)
            if (continuous && _listening.value) recognizer?.startListening(intent) else _listening.value = false
        }

        override fun onPartialResults(partialResults: Bundle?) {
            _partial.value = partialResults?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)?.firstOrNull().orEmpty()
        }

        override fun onError(error: Int) {
            val recoverable = error == SpeechRecognizer.ERROR_NO_MATCH || error == SpeechRecognizer.ERROR_SPEECH_TIMEOUT
            if (continuous && _listening.value && recoverable) {
                recognizer?.startListening(intent)
            } else {
                _listening.value = false
                if (!recoverable) _error.value = "Speech recognition error ($error)"
            }
        }

        override fun onReadyForSpeech(params: Bundle?) = Unit
        override fun onBeginningOfSpeech() = Unit
        override fun onRmsChanged(rmsdB: Float) = Unit
        override fun onBufferReceived(buffer: ByteArray?) = Unit
        override fun onEndOfSpeech() = Unit
        override fun onEvent(eventType: Int, params: Bundle?) = Unit
    }

    private fun append(text: String) {
        val now = System.currentTimeMillis()
        if (markTurns) {
            if (turn == 0 || now - lastResultAt > turnGapMs) {
                turn++
                if (segments.isNotEmpty()) segments.append("\n")
                segments.append("Turn ").append(turn).append(": ")
            } else {
                segments.append(' ')
            }
        } else if (segments.isNotEmpty()) {
            segments.append(' ')
        }
        segments.append(text)
        lastResultAt = now
        _transcript.value = segments.toString()
    }

    fun start() {
        val r = recognizer ?: run { _error.value = "Speech recognition isn't available on this device"; return }
        _error.value = null
        r.setRecognitionListener(listener)
        _listening.value = true
        r.startListening(intent)
    }

    fun stop() {
        _listening.value = false
        recognizer?.stopListening()
    }

    fun reset() {
        segments.clear(); turn = 0; _transcript.value = ""; _partial.value = ""
    }

    fun destroy() {
        _listening.value = false
        recognizer?.destroy()
    }
}

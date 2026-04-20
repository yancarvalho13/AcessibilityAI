package com.example.myapplication.data.speech

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.os.SystemClock
import android.speech.RecognitionListener
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import com.example.myapplication.domain.speech.SpeechToTextApi
import com.example.myapplication.domain.speech.SpeechToTextState
import java.util.Locale
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

class AndroidSpeechToTextApi(
    private val appContext: Context,
) : SpeechToTextApi {
    private val _state = MutableStateFlow(SpeechToTextState())
    override val state: StateFlow<SpeechToTextState> = _state.asStateFlow()

    private var speechRecognizer: SpeechRecognizer? = null
    private var ignoreTransientErrorsUntilMs: Long = 0L

    override fun startListening() {
        if (_state.value.isListening) {
            return
        }

        if (!SpeechRecognizer.isRecognitionAvailable(appContext)) {
            _state.value = _state.value.copy(
                isListening = false,
                errorMessage = "Reconhecimento de voz indisponivel neste dispositivo",
            )
            return
        }

        val recognizer = getOrCreateRecognizer()
        val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH)
            .putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
            .putExtra(RecognizerIntent.EXTRA_LANGUAGE, LOCALE_PT_BR)
            .putExtra(RecognizerIntent.EXTRA_LANGUAGE_PREFERENCE, LOCALE_PT_BR)
            .putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, true)
            .putExtra(RecognizerIntent.EXTRA_MAX_RESULTS, 1)

        _state.value = _state.value.copy(
            isListening = true,
            partialText = "",
            finalText = "",
            errorMessage = null,
        )
        recognizer.startListening(intent)
    }

    override fun stopListening() {
        ignoreTransientErrorsUntilMs = SystemClock.elapsedRealtime() + TRANSIENT_ERROR_SUPPRESSION_MS
        speechRecognizer?.stopListening()
    }

    override fun cancelListening() {
        ignoreTransientErrorsUntilMs = SystemClock.elapsedRealtime() + TRANSIENT_ERROR_SUPPRESSION_MS
        speechRecognizer?.cancel()
        _state.value = _state.value.copy(isListening = false, partialText = "")
    }

    override fun release() {
        speechRecognizer?.destroy()
        speechRecognizer = null
        _state.value = SpeechToTextState()
    }

    private fun getOrCreateRecognizer(): SpeechRecognizer {
        val existing = speechRecognizer
        if (existing != null) {
            return existing
        }

        return SpeechRecognizer.createSpeechRecognizer(appContext).also { recognizer ->
            recognizer.setRecognitionListener(
                object : RecognitionListener {
                    override fun onReadyForSpeech(params: Bundle?) {
                    }

                    override fun onBeginningOfSpeech() {
                    }

                    override fun onRmsChanged(rmsdB: Float) {
                    }

                    override fun onBufferReceived(buffer: ByteArray?) {
                    }

                    override fun onEndOfSpeech() {
                        _state.value = _state.value.copy(isListening = false)
                    }

                    override fun onError(error: Int) {
                        if (shouldIgnoreTransientError(error)) {
                            _state.value = _state.value.copy(
                                isListening = false,
                                partialText = "",
                                errorMessage = null,
                            )
                            return
                        }

                        _state.value = _state.value.copy(
                            isListening = false,
                            partialText = "",
                            errorMessage = mapSpeechError(error),
                        )
                    }

                    override fun onResults(results: Bundle?) {
                        val text = results
                            ?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
                            ?.firstOrNull()
                            .orEmpty()
                            .trim()

                        _state.value = _state.value.copy(
                            isListening = false,
                            partialText = "",
                            finalText = text,
                            errorMessage = null,
                        )
                    }

                    override fun onPartialResults(partialResults: Bundle?) {
                        val partial = partialResults
                            ?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
                            ?.firstOrNull()
                            .orEmpty()
                            .trim()

                        _state.value = _state.value.copy(
                            partialText = partial,
                            errorMessage = null,
                        )
                    }

                    override fun onEvent(eventType: Int, params: Bundle?) {
                    }
                },
            )
            speechRecognizer = recognizer
        }
    }

    private fun mapSpeechError(errorCode: Int): String {
        return when (errorCode) {
            SpeechRecognizer.ERROR_AUDIO -> "Erro de audio"
            SpeechRecognizer.ERROR_CLIENT -> "Erro no cliente de reconhecimento"
            SpeechRecognizer.ERROR_INSUFFICIENT_PERMISSIONS -> "Permissao de microfone ausente"
            SpeechRecognizer.ERROR_NETWORK,
            SpeechRecognizer.ERROR_NETWORK_TIMEOUT,
            -> "Erro de rede no reconhecimento"
            SpeechRecognizer.ERROR_NO_MATCH -> "Nenhuma fala reconhecida"
            SpeechRecognizer.ERROR_RECOGNIZER_BUSY -> "Reconhecimento ocupado"
            SpeechRecognizer.ERROR_SERVER -> "Servidor de reconhecimento indisponivel"
            SpeechRecognizer.ERROR_SPEECH_TIMEOUT -> "Tempo de fala esgotado"
            else -> "Erro desconhecido no reconhecimento"
        }
    }

    private fun shouldIgnoreTransientError(errorCode: Int): Boolean {
        val withinSuppressionWindow = SystemClock.elapsedRealtime() < ignoreTransientErrorsUntilMs
        if (!withinSuppressionWindow) {
            return false
        }

        return errorCode == SpeechRecognizer.ERROR_CLIENT ||
            errorCode == SpeechRecognizer.ERROR_NO_MATCH ||
            errorCode == SpeechRecognizer.ERROR_SPEECH_TIMEOUT ||
            errorCode == SpeechRecognizer.ERROR_RECOGNIZER_BUSY
    }

    companion object {
        private val LOCALE_PT_BR = Locale.forLanguageTag("pt-BR").toLanguageTag()
        private const val TRANSIENT_ERROR_SUPPRESSION_MS = 1_200L
    }
}

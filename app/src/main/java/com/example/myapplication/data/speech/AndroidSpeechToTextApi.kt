package com.example.myapplication.data.speech

import android.content.Context
import android.content.Intent
import android.os.Bundle
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

    override fun startListening() {
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
            errorMessage = null,
        )
        recognizer.startListening(intent)
    }

    override fun stopListening() {
        speechRecognizer?.stopListening()
    }

    override fun cancelListening() {
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

    companion object {
        private val LOCALE_PT_BR = Locale.forLanguageTag("pt-BR").toLanguageTag()
    }
}

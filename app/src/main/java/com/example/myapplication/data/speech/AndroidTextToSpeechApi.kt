package com.example.myapplication.data.speech

import android.content.Context
import android.speech.tts.TextToSpeech
import android.speech.tts.UtteranceProgressListener
import com.example.myapplication.domain.speech.TextToSpeechApi
import com.example.myapplication.domain.speech.TextToSpeechState
import java.util.Locale
import java.util.UUID
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

class AndroidTextToSpeechApi(
    private val appContext: Context,
) : TextToSpeechApi {
    private val _state = MutableStateFlow(TextToSpeechState())
    override val state: StateFlow<TextToSpeechState> = _state.asStateFlow()

    private var textToSpeech: TextToSpeech? = null

    override fun speak(text: String) {
        if (text.isBlank()) {
            _state.value = _state.value.copy(errorMessage = "Texto vazio para leitura")
            return
        }

        val tts = getOrCreateTextToSpeech()
        if (!_state.value.isInitialized) {
            _state.value = _state.value.copy(errorMessage = "TTS ainda inicializando")
            return
        }

        val utteranceId = UUID.randomUUID().toString()
        val result = tts.speak(text, TextToSpeech.QUEUE_FLUSH, null, utteranceId)
        if (result == TextToSpeech.ERROR) {
            _state.value = _state.value.copy(errorMessage = "Falha ao iniciar fala")
        }
    }

    override fun stop() {
        textToSpeech?.stop()
        _state.value = _state.value.copy(isSpeaking = false)
    }

    override fun release() {
        textToSpeech?.stop()
        textToSpeech?.shutdown()
        textToSpeech = null
        _state.value = TextToSpeechState()
    }

    private fun getOrCreateTextToSpeech(): TextToSpeech {
        val existing = textToSpeech
        if (existing != null) {
            return existing
        }

        return TextToSpeech(appContext) { status ->
            if (status != TextToSpeech.SUCCESS) {
                _state.value = _state.value.copy(
                    isInitialized = false,
                    errorMessage = "Falha ao inicializar TTS",
                )
                return@TextToSpeech
            }

            val tts = textToSpeech
            if (tts == null) {
                _state.value = _state.value.copy(
                    isInitialized = false,
                    errorMessage = "TTS indisponivel",
                )
                return@TextToSpeech
            }

            val languageStatus = tts.setLanguage(Locale.forLanguageTag("pt-BR"))
            if (languageStatus == TextToSpeech.LANG_MISSING_DATA || languageStatus == TextToSpeech.LANG_NOT_SUPPORTED) {
                _state.value = _state.value.copy(
                    isInitialized = false,
                    errorMessage = "Idioma pt-BR nao suportado no TTS",
                )
                return@TextToSpeech
            }

            tts.setOnUtteranceProgressListener(
                object : UtteranceProgressListener() {
                    override fun onStart(utteranceId: String?) {
                        _state.value = _state.value.copy(isSpeaking = true, errorMessage = null)
                    }

                    override fun onDone(utteranceId: String?) {
                        _state.value = _state.value.copy(isSpeaking = false)
                    }

                    @Deprecated("Deprecated in Java")
                    override fun onError(utteranceId: String?) {
                        _state.value = _state.value.copy(
                            isSpeaking = false,
                            errorMessage = "Erro durante reproducao TTS",
                        )
                    }

                    override fun onError(utteranceId: String?, errorCode: Int) {
                        _state.value = _state.value.copy(
                            isSpeaking = false,
                            errorMessage = "Erro durante reproducao TTS",
                        )
                    }
                },
            )

            _state.value = _state.value.copy(isInitialized = true, errorMessage = null)
        }.also {
            textToSpeech = it
        }
    }
}

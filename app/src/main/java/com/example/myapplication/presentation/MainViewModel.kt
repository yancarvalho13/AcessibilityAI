package com.example.myapplication.presentation

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.example.myapplication.domain.app.AppLaunchStatus
import com.example.myapplication.domain.app.AppLauncherApi
import com.example.myapplication.domain.overlay.OverlayServiceApi
import com.example.myapplication.domain.speech.SpeechToTextApi
import com.example.myapplication.domain.voice.VoiceServiceApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

class MainViewModel(
    private val voiceServiceApi: VoiceServiceApi,
    private val overlayServiceApi: OverlayServiceApi,
    private val speechToTextApi: SpeechToTextApi,
    private val appLauncherApi: AppLauncherApi,
) : ViewModel() {
    private val infoMessage = MutableStateFlow<String?>(null)
    private val logs = MutableStateFlow<List<String>>(emptyList())
    private val lastAppCommandText = MutableStateFlow("")

    private var isWaitingForAppCommand = false
    private var lastHandledFinalSttText = ""

    private val voiceAndOverlayState = combine(
        voiceServiceApi.state,
        overlayServiceApi.state,
    ) { voiceState, overlayState ->
        voiceState to overlayState
    }

    val uiState: StateFlow<MainUiState> = combine(
        voiceAndOverlayState,
        speechToTextApi.state,
        lastAppCommandText,
        logs,
        infoMessage,
    ) { pairState, sttState, commandText, logsState, message ->
        val voiceState = pairState.first
        val overlayState = pairState.second

        MainUiState(
            isRecording = voiceState.isRecording,
            voiceLevel = voiceState.level,
            lastAudioPath = voiceState.lastRecording?.filePath,
            lastAudioSizeBytes = voiceState.lastRecording?.sizeBytes,
            overlayPermissionGranted = overlayState.hasPermission,
            isAppCommandListening = sttState.isListening,
            appCommandPartialText = sttState.partialText,
            appCommandLastText = commandText,
            logs = logsState,
            infoMessage = message,
        )
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5_000),
        initialValue = MainUiState(),
    )

    init {
        viewModelScope.launch {
            speechToTextApi.state.collect { sttState ->
                sttState.errorMessage?.let { error ->
                    if (isWaitingForAppCommand) {
                        appendLog("Comando app: erro STT ($error)")
                        isWaitingForAppCommand = false
                    }
                }

                val finalText = sttState.finalText.trim()
                if (finalText.isNotEmpty() && finalText != lastHandledFinalSttText) {
                    lastHandledFinalSttText = finalText

                    if (isWaitingForAppCommand) {
                        isWaitingForAppCommand = false
                        handleAppCommand(finalText)
                    }
                }
            }
        }
    }

    fun refreshOverlayPermission() {
        overlayServiceApi.refreshPermissionState()
    }

    fun startVoice(audioPermissionGranted: Boolean) {
        if (!audioPermissionGranted) {
            infoMessage.value = "Permissao de microfone necessaria"
            appendLog("Audio: permissao de microfone negada")
            return
        }

        voiceServiceApi.startRecording()
        appendLog("Audio: gravacao iniciada")
        infoMessage.value = null
    }

    fun stopVoice() {
        voiceServiceApi.stopRecording()
        appendLog("Audio: gravacao parada")
    }

    fun readLastAudioBytesAndLog() {
        viewModelScope.launch {
            val bytes = voiceServiceApi.getLastRecordingBytes()
            if (bytes == null || bytes.isEmpty()) {
                appendLog("Audio: arquivo nao retornado")
            } else {
                appendLog("Audio: arquivo retornado com ${bytes.size} bytes")
            }
        }
    }

    fun showOverlay() {
        overlayServiceApi.showOverlay()
        if (!overlayServiceApi.state.value.hasPermission) {
            infoMessage.value = "Permissao de overlay necessaria"
            appendLog("Overlay: permissao ausente")
            return
        }
        appendLog("Overlay: exibido")
    }

    fun openOverlaySettings() {
        overlayServiceApi.openPermissionSettings()
    }

    fun startAppCommandListening(audioPermissionGranted: Boolean) {
        if (!audioPermissionGranted) {
            infoMessage.value = "Permissao de microfone necessaria"
            appendLog("Comando app: permissao de microfone negada")
            return
        }

        isWaitingForAppCommand = true
        speechToTextApi.startListening()
        appendLog("Comando app: escutando...")
        infoMessage.value = null
    }

    fun stopAppCommandListening() {
        isWaitingForAppCommand = false
        speechToTextApi.stopListening()
        appendLog("Comando app: escuta finalizada")
    }

    private fun handleAppCommand(command: String) {
        lastAppCommandText.value = command
        appendLog("Comando app reconhecido: $command")

        val result = appLauncherApi.openByVoiceCommand(command)
        when (result.status) {
            AppLaunchStatus.Opened -> {
                appendLog("Comando app: ${result.targetApp} aberto")
            }

            AppLaunchStatus.AppNotInstalled -> {
                appendLog("Comando app: ${result.targetApp} nao instalado")
                infoMessage.value = "${result.targetApp} nao instalado"
            }

            AppLaunchStatus.NoMatch -> {
                appendLog("Comando app: nenhuma palavra-chave encontrada")
                infoMessage.value = "Comando nao reconhecido para app"
            }
        }
    }

    fun dismissInfoMessage() {
        infoMessage.update { null }
    }

    fun clearLogs() {
        logs.value = emptyList()
    }

    private fun appendLog(message: String) {
        logs.value = (logs.value + message).takeLast(MAX_LOG_LINES)
    }

    class Factory(
        private val voiceServiceApi: VoiceServiceApi,
        private val overlayServiceApi: OverlayServiceApi,
        private val speechToTextApi: SpeechToTextApi,
        private val appLauncherApi: AppLauncherApi,
    ) : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T {
            return MainViewModel(
                voiceServiceApi,
                overlayServiceApi,
                speechToTextApi,
                appLauncherApi,
            ) as T
        }
    }

    companion object {
        private const val MAX_LOG_LINES = 40
    }
}

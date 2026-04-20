package com.example.myapplication.presentation

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.example.myapplication.domain.overlay.OverlayServiceApi
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
) : ViewModel() {
    private val infoMessage = MutableStateFlow<String?>(null)
    private val logs = MutableStateFlow<List<String>>(emptyList())

    val uiState: StateFlow<MainUiState> = combine(
        voiceServiceApi.state,
        overlayServiceApi.state,
        logs,
        infoMessage,
    ) { voiceState, overlayState, logsState, message ->
        MainUiState(
            isRecording = voiceState.isRecording,
            voiceLevel = voiceState.level,
            lastAudioPath = voiceState.lastRecording?.filePath,
            lastAudioSizeBytes = voiceState.lastRecording?.sizeBytes,
            overlayPermissionGranted = overlayState.hasPermission,
            logs = logsState,
            infoMessage = message,
        )
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5_000),
        initialValue = MainUiState(),
    )

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

    fun appendHeadlessLog(message: String) {
        appendLog(message)
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
    ) : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T {
            return MainViewModel(voiceServiceApi, overlayServiceApi) as T
        }
    }

    companion object {
        private const val MAX_LOG_LINES = 40
    }
}

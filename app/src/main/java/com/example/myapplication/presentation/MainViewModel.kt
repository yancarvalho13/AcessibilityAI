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

class MainViewModel(
    private val voiceServiceApi: VoiceServiceApi,
    private val overlayServiceApi: OverlayServiceApi,
) : ViewModel() {
    private val infoMessage = MutableStateFlow<String?>(null)

    val uiState: StateFlow<MainUiState> = combine(
        voiceServiceApi.state,
        overlayServiceApi.state,
        infoMessage,
    ) { voiceState, overlayState, message ->
        MainUiState(
            isRecording = voiceState.isRecording,
            voiceLevel = voiceState.level,
            overlayPermissionGranted = overlayState.hasPermission,
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
            return
        }
        voiceServiceApi.startRecording()
        infoMessage.value = null
    }

    fun stopVoice() {
        voiceServiceApi.stopRecording()
    }

    fun showOverlay() {
        overlayServiceApi.showOverlay()
        if (!overlayServiceApi.state.value.hasPermission) {
            infoMessage.value = "Permissao de overlay necessaria"
        }
    }

    fun openOverlaySettings() {
        overlayServiceApi.openPermissionSettings()
    }

    fun dismissInfoMessage() {
        infoMessage.update { null }
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
}

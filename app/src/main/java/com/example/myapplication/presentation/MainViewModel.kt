package com.example.myapplication.presentation

import androidx.camera.view.PreviewView
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.example.myapplication.domain.analysis.SceneAnalysisApi
import com.example.myapplication.domain.camera.CameraServiceApi
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
    private val cameraServiceApi: CameraServiceApi,
    private val sceneAnalysisApi: SceneAnalysisApi,
) : ViewModel() {
    private val infoMessage = MutableStateFlow<String?>(null)
    private val logs = MutableStateFlow<List<String>>(emptyList())
    private val isAnalyzingScene = MutableStateFlow(false)
    private val lastSceneAnalysis = MutableStateFlow<String?>(null)

    private var lastCapturedPhotoBytes: ByteArray? = null

    private val coreState = combine(
        voiceServiceApi.state,
        overlayServiceApi.state,
        cameraServiceApi.state,
    ) { voiceState, overlayState, cameraState ->
        Triple(voiceState, overlayState, cameraState)
    }

    val uiState: StateFlow<MainUiState> = combine(
        coreState,
        isAnalyzingScene,
        lastSceneAnalysis,
        logs,
        infoMessage,
    ) { core, analyzingState, analysisState, logsState, message ->
        val voiceState = core.first
        val overlayState = core.second
        val cameraState = core.third

        MainUiState(
            isRecording = voiceState.isRecording,
            voiceLevel = voiceState.level,
            lastAudioPath = voiceState.lastRecording?.filePath,
            lastAudioSizeBytes = voiceState.lastRecording?.sizeBytes,
            overlayPermissionGranted = overlayState.hasPermission,
            isCameraOpen = cameraState.isCameraOpen,
            isCameraRecording = cameraState.isRecordingVideo,
            lastVideoPath = cameraState.lastVideo?.filePath,
            lastVideoSizeBytes = cameraState.lastVideo?.sizeBytes,
            isAnalyzingScene = analyzingState,
            lastSceneAnalysis = analysisState,
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

    fun dismissInfoMessage() {
        infoMessage.update { null }
    }

    fun openCamera(lifecycleOwner: LifecycleOwner, previewView: PreviewView, hasCameraPermission: Boolean) {
        if (!hasCameraPermission) {
            infoMessage.value = "Permissao de camera necessaria"
            appendLog("Camera: permissao negada")
            return
        }
        cameraServiceApi.openCamera(lifecycleOwner, previewView)
        appendLog("Camera: abertura solicitada")
        infoMessage.value = null
    }

    fun onCameraPermissionDenied() {
        infoMessage.value = "Permissao de camera necessaria"
        appendLog("Camera: permissao negada")
    }

    fun closeCamera() {
        cameraServiceApi.closeCamera()
        appendLog("Camera: fechada")
    }

    fun capturePhotoAndLog() {
        viewModelScope.launch {
            runCatching {
                cameraServiceApi.capturePhotoBytes()
            }.onSuccess { bytes ->
                lastCapturedPhotoBytes = bytes
                appendLog("Camera foto: retornou ${bytes.size} bytes")
            }.onFailure { error ->
                appendLog("Camera foto: falhou (${error.message ?: "erro"})")
            }
        }
    }

    fun startVideoRecording(hasCameraPermission: Boolean) {
        if (!hasCameraPermission) {
            infoMessage.value = "Permissao de camera necessaria"
            appendLog("Video: permissao de camera negada")
            return
        }

        runCatching {
            cameraServiceApi.startVideoRecording()
            appendLog("Video: gravacao iniciada")
            infoMessage.value = null
        }.onFailure { error ->
            appendLog("Video: falhou ao iniciar (${error.message ?: "erro"})")
        }
    }

    fun stopVideoRecordingAndLog() {
        viewModelScope.launch {
            runCatching {
                cameraServiceApi.stopVideoRecording()
            }.onSuccess { videoRef ->
                if (videoRef == null) {
                    appendLog("Video: finalizado sem arquivo")
                } else {
                    appendLog("Video: arquivo salvo em ${videoRef.filePath} (${videoRef.sizeBytes} bytes)")
                }
            }.onFailure { error ->
                appendLog("Video: falhou ao parar (${error.message ?: "erro"})")
            }
        }
    }

    fun readLastVideoBytesAndLog() {
        viewModelScope.launch {
            val bytes = cameraServiceApi.getLastVideoBytes()
            if (bytes == null || bytes.isEmpty()) {
                appendLog("Video: arquivo nao retornado")
            } else {
                appendLog("Video: arquivo retornado com ${bytes.size} bytes")
            }
        }
    }

    fun clearLogs() {
        logs.value = emptyList()
    }

    fun analyzeLastPhotoWithGemini() {
        val photoBytes = lastCapturedPhotoBytes
        if (photoBytes == null || photoBytes.isEmpty()) {
            appendLog("Gemini foto: capture uma foto antes")
            return
        }

        viewModelScope.launch {
            isAnalyzingScene.value = true
            runCatching {
                sceneAnalysisApi.analyzePhoto(photoBytes)
            }.onSuccess { analysis ->
                lastSceneAnalysis.value = analysis
                appendLog("Gemini foto: analise concluida")
            }.onFailure { error ->
                appendLog("Gemini foto: falhou (${error.message ?: "erro"})")
            }
            isAnalyzingScene.value = false
        }
    }

    fun analyzeLastVideoWithGemini() {
        viewModelScope.launch {
            val videoBytes = cameraServiceApi.getLastVideoBytes()
            if (videoBytes == null || videoBytes.isEmpty()) {
                appendLog("Gemini video: grave um video antes")
                return@launch
            }

            isAnalyzingScene.value = true
            runCatching {
                sceneAnalysisApi.analyzeVideo(videoBytes)
            }.onSuccess { analysis ->
                lastSceneAnalysis.value = analysis
                appendLog("Gemini video: analise concluida")
            }.onFailure { error ->
                appendLog("Gemini video: falhou (${error.message ?: "erro"})")
            }
            isAnalyzingScene.value = false
        }
    }

    private fun appendLog(message: String) {
        logs.value = (logs.value + message).takeLast(MAX_LOG_LINES)
    }

    class Factory(
        private val voiceServiceApi: VoiceServiceApi,
        private val overlayServiceApi: OverlayServiceApi,
        private val cameraServiceApi: CameraServiceApi,
        private val sceneAnalysisApi: SceneAnalysisApi,
    ) : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T {
            return MainViewModel(
                voiceServiceApi,
                overlayServiceApi,
                cameraServiceApi,
                sceneAnalysisApi,
            ) as T
        }
    }

    companion object {
        private const val MAX_LOG_LINES = 30
    }
}

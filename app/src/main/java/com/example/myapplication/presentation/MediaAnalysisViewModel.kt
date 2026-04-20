package com.example.myapplication.presentation

import androidx.camera.view.PreviewView
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.example.myapplication.domain.analysis.SceneAnalysisApi
import com.example.myapplication.domain.camera.CameraServiceApi
import com.example.myapplication.domain.speech.SpeechToTextApi
import com.example.myapplication.domain.speech.TextToSpeechApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

class MediaAnalysisViewModel(
    private val cameraServiceApi: CameraServiceApi,
    private val sceneAnalysisApi: SceneAnalysisApi,
    private val speechToTextApi: SpeechToTextApi,
    private val textToSpeechApi: TextToSpeechApi,
) : ViewModel() {
    private val prompt = MutableStateFlow("")
    private val infoMessage = MutableStateFlow<String?>(null)
    private val logs = MutableStateFlow<List<String>>(emptyList())
    private val isAnalyzingScene = MutableStateFlow(false)
    private val lastSceneAnalysis = MutableStateFlow<String?>(null)
    private val lastPhotoSizeBytes = MutableStateFlow<Int?>(null)

    private var lastCapturedPhotoBytes: ByteArray? = null
    private var lastHandledFinalPrompt = ""

    private val coreState = combine(
        cameraServiceApi.state,
        speechToTextApi.state,
        textToSpeechApi.state,
    ) { cameraState, sttState, ttsState ->
        Triple(cameraState, sttState, ttsState)
    }

    private val baseUiState = combine(
        prompt,
        coreState,
        isAnalyzingScene,
        lastSceneAnalysis,
        lastPhotoSizeBytes,
    ) { promptValue, core, analyzing, analysis, photoSize ->
        val cameraState = core.first
        val sttState = core.second
        val ttsState = core.third

        MediaAnalysisUiState(
            prompt = promptValue,
            isPromptListening = sttState.isListening,
            promptPartialText = sttState.partialText,
            isCameraOpen = cameraState.isCameraOpen,
            isCameraRecording = cameraState.isRecordingVideo,
            lastPhotoSizeBytes = photoSize,
            lastVideoPath = cameraState.lastVideo?.filePath,
            lastVideoSizeBytes = cameraState.lastVideo?.sizeBytes,
            isAnalyzingScene = analyzing,
            lastSceneAnalysis = analysis,
            isTextToSpeechReady = ttsState.isInitialized,
            isTextToSpeechSpeaking = ttsState.isSpeaking,
        )
    }

    val uiState: StateFlow<MediaAnalysisUiState> = combine(
        baseUiState,
        logs,
        infoMessage,
    ) { baseState, logsState, message ->
        baseState.copy(
            logs = logsState,
            infoMessage = message,
        )
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5_000),
        initialValue = MediaAnalysisUiState(),
    )

    init {
        viewModelScope.launch {
            speechToTextApi.state.collect { sttState ->
                if (sttState.errorMessage != null) {
                    appendLog("STT: erro (${sttState.errorMessage})")
                }

                val finalText = sttState.finalText.trim()
                if (finalText.isNotEmpty() && finalText != lastHandledFinalPrompt) {
                    lastHandledFinalPrompt = finalText
                    prompt.value = finalText
                    appendLog("STT: prompt reconhecido")
                }
            }
        }

        viewModelScope.launch {
            textToSpeechApi.state.collect { ttsState ->
                if (ttsState.errorMessage != null) {
                    appendLog("TTS: erro (${ttsState.errorMessage})")
                }
            }
        }
    }

    fun updatePrompt(newPrompt: String) {
        prompt.value = newPrompt
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
                lastPhotoSizeBytes.value = bytes.size
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

    fun startPromptListening(audioPermissionGranted: Boolean) {
        if (!audioPermissionGranted) {
            infoMessage.value = "Permissao de microfone necessaria"
            appendLog("STT: permissao de microfone negada")
            return
        }

        speechToTextApi.startListening()
        appendLog("STT: escuta iniciada")
        infoMessage.value = null
    }

    fun stopPromptListening() {
        speechToTextApi.stopListening()
        appendLog("STT: escuta finalizada")
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
                sceneAnalysisApi.analyzePhoto(photoBytes, prompt.value)
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
                sceneAnalysisApi.analyzeVideo(videoBytes, prompt.value)
            }.onSuccess { analysis ->
                lastSceneAnalysis.value = analysis
                appendLog("Gemini video: analise concluida")
            }.onFailure { error ->
                appendLog("Gemini video: falhou (${error.message ?: "erro"})")
            }
            isAnalyzingScene.value = false
        }
    }

    fun speakLastAnalysis() {
        val analysis = lastSceneAnalysis.value
        if (analysis.isNullOrBlank()) {
            appendLog("TTS: nenhuma resposta para leitura")
            return
        }

        textToSpeechApi.speak(analysis)
        appendLog("TTS: leitura iniciada")
    }

    fun stopSpeaking() {
        textToSpeechApi.stop()
        appendLog("TTS: leitura interrompida")
    }

    fun dismissInfoMessage() {
        infoMessage.update { null }
    }

    fun clearLogs() {
        logs.value = emptyList()
    }

    fun release() {
        speechToTextApi.release()
        textToSpeechApi.release()
        cameraServiceApi.closeCamera()
    }

    private fun appendLog(message: String) {
        logs.value = (logs.value + message).takeLast(MAX_LOG_LINES)
    }

    class Factory(
        private val cameraServiceApi: CameraServiceApi,
        private val sceneAnalysisApi: SceneAnalysisApi,
        private val speechToTextApi: SpeechToTextApi,
        private val textToSpeechApi: TextToSpeechApi,
    ) : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T {
            return MediaAnalysisViewModel(
                cameraServiceApi,
                sceneAnalysisApi,
                speechToTextApi,
                textToSpeechApi,
            ) as T
        }
    }

    companion object {
        private const val MAX_LOG_LINES = 40
    }
}

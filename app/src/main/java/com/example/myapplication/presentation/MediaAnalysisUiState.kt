package com.example.myapplication.presentation

data class MediaAnalysisUiState(
    val prompt: String = "",
    val isPromptListening: Boolean = false,
    val promptPartialText: String = "",
    val isCameraOpen: Boolean = false,
    val isCameraRecording: Boolean = false,
    val lastPhotoSizeBytes: Int? = null,
    val lastVideoPath: String? = null,
    val lastVideoSizeBytes: Long? = null,
    val isAnalyzingScene: Boolean = false,
    val lastSceneAnalysis: String? = null,
    val isTextToSpeechReady: Boolean = false,
    val isTextToSpeechSpeaking: Boolean = false,
    val logs: List<String> = emptyList(),
    val infoMessage: String? = null,
)

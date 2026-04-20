package com.example.myapplication.presentation

data class MainUiState(
    val isRecording: Boolean = false,
    val voiceLevel: Int = 0,
    val lastAudioPath: String? = null,
    val lastAudioSizeBytes: Long? = null,
    val overlayPermissionGranted: Boolean = false,
    val isCameraOpen: Boolean = false,
    val isCameraRecording: Boolean = false,
    val lastVideoPath: String? = null,
    val lastVideoSizeBytes: Long? = null,
    val isAnalyzingScene: Boolean = false,
    val lastSceneAnalysis: String? = null,
    val logs: List<String> = emptyList(),
    val infoMessage: String? = null,
)

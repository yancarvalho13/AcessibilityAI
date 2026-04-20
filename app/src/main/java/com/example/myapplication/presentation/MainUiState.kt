package com.example.myapplication.presentation

data class MainUiState(
    val isRecording: Boolean = false,
    val voiceLevel: Int = 0,
    val overlayPermissionGranted: Boolean = false,
    val infoMessage: String? = null,
)

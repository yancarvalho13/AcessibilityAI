package com.example.myapplication.domain.voice

data class VoiceServiceState(
    val isRecording: Boolean = false,
    val level: Int = 0,
    val lastRecording: VoiceRecordingRef? = null,
)

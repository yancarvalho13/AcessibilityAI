package com.example.myapplication.domain.voice

data class VoiceRecordingRef(
    val filePath: String,
    val durationMs: Long,
    val sizeBytes: Long,
    val sampleRate: Int,
    val channelCount: Int,
)

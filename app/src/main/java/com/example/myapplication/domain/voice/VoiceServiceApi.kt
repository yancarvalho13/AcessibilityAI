package com.example.myapplication.domain.voice

import kotlinx.coroutines.flow.StateFlow

interface VoiceServiceApi {
    val state: StateFlow<VoiceServiceState>

    fun startRecording()

    fun stopRecording()

    suspend fun getLastRecordingBytes(): ByteArray?
}

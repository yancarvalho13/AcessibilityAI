package com.example.myapplication.domain.speech

import kotlinx.coroutines.flow.StateFlow

interface TextToSpeechApi {
    val state: StateFlow<TextToSpeechState>

    fun speak(text: String)

    fun stop()

    fun release()
}

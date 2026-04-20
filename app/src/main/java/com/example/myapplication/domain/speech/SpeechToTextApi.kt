package com.example.myapplication.domain.speech

import kotlinx.coroutines.flow.StateFlow

interface SpeechToTextApi {
    val state: StateFlow<SpeechToTextState>

    fun startListening()

    fun stopListening()

    fun cancelListening()

    fun release()
}

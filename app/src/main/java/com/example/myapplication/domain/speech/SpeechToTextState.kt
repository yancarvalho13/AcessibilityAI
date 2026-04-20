package com.example.myapplication.domain.speech

data class SpeechToTextState(
    val isListening: Boolean = false,
    val partialText: String = "",
    val finalText: String = "",
    val errorMessage: String? = null,
)

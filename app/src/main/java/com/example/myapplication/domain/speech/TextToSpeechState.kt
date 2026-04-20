package com.example.myapplication.domain.speech

data class TextToSpeechState(
    val isInitialized: Boolean = false,
    val isSpeaking: Boolean = false,
    val errorMessage: String? = null,
)

package com.example.myapplication.domain.session.steps

import com.example.myapplication.domain.session.VoiceSessionAction

data class VoiceStepResult(
    val actions: List<VoiceSessionAction> = emptyList(),
    val nextStep: VoiceSessionStep? = null,
)

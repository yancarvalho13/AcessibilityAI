package com.example.myapplication.domain.session.steps

import com.example.myapplication.domain.session.VoiceSessionContext

interface VoiceSessionStep {
    val id: String

    fun onEnter(context: VoiceSessionContext): VoiceStepResult

    fun onUserInput(input: String, context: VoiceSessionContext): VoiceStepResult = VoiceStepResult()
}

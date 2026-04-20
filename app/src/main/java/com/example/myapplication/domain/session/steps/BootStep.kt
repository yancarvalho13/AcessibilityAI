package com.example.myapplication.domain.session.steps

import com.example.myapplication.domain.session.VoiceSessionAction
import com.example.myapplication.domain.session.VoiceSessionContext

class BootStep : VoiceSessionStep {
    override val id: String = "boot"

    override fun onEnter(context: VoiceSessionContext): VoiceStepResult {
        return VoiceStepResult(
            actions = listOf(
                VoiceSessionAction.Speak("Estou escutando"),
            ),
            nextStep = ListeningStep(),
        )
    }
}

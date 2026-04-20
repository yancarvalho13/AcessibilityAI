package com.example.myapplication.domain.session.steps

import com.example.myapplication.domain.session.VoiceSessionAction
import com.example.myapplication.domain.session.VoiceSessionContext

class EndedStep : VoiceSessionStep {
    override val id: String = "ended"

    override fun onEnter(context: VoiceSessionContext): VoiceStepResult {
        return VoiceStepResult(
            actions = listOf(
                VoiceSessionAction.StopListening,
                VoiceSessionAction.StopSession,
            ),
        )
    }
}

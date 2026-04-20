package com.example.myapplication.domain.session.steps

import com.example.myapplication.domain.session.VoiceCommandIntent
import com.example.myapplication.domain.session.VoiceCommandParser
import com.example.myapplication.domain.session.VoiceSessionAction
import com.example.myapplication.domain.session.VoiceSessionContext

class ExecuteCommandStep(
    private val intent: VoiceCommandIntent,
    private val rawCommand: String,
    private val parser: VoiceCommandParser,
) : VoiceSessionStep {
    override val id: String = "execute"

    override fun onEnter(context: VoiceSessionContext): VoiceStepResult {
        return VoiceStepResult(
            actions = listOf(
                VoiceSessionAction.StopListening,
                VoiceSessionAction.ExecuteCommand(intent, rawCommand),
            ),
            nextStep = ListeningStep(parser),
        )
    }
}

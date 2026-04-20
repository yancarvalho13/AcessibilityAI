package com.example.myapplication.domain.session.steps

import com.example.myapplication.domain.session.VoiceCommandIntent
import com.example.myapplication.domain.session.VoiceCommandParser
import com.example.myapplication.domain.session.VoiceSessionAction
import com.example.myapplication.domain.session.VoiceSessionContext

class StartVideoStep(
    private val parser: VoiceCommandParser,
) : VoiceSessionStep {
    override val id: String = "start_video"

    override fun onEnter(context: VoiceSessionContext): VoiceStepResult {
        return VoiceStepResult(
            actions = listOf(
                VoiceSessionAction.StopListening,
                VoiceSessionAction.ExecuteCommand(VoiceCommandIntent.StartVideo, "iniciar gravacao"),
            ),
            nextStep = VideoAwaitStopStep(parser),
        )
    }
}

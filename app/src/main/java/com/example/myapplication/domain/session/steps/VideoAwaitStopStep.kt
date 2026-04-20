package com.example.myapplication.domain.session.steps

import com.example.myapplication.domain.session.VoiceCommandIntent
import com.example.myapplication.domain.session.VoiceCommandParser
import com.example.myapplication.domain.session.VoiceSessionAction
import com.example.myapplication.domain.session.VoiceSessionContext

class VideoAwaitStopStep(
    private val parser: VoiceCommandParser,
) : VoiceSessionStep {
    override val id: String = "video_await_stop"

    override fun onEnter(context: VoiceSessionContext): VoiceStepResult {
        return VoiceStepResult(
            actions = listOf(
                VoiceSessionAction.StartListening,
                VoiceSessionAction.ResetTimeout,
            ),
        )
    }

    override fun onUserInput(input: String, context: VoiceSessionContext): VoiceStepResult {
        val normalized = input.trim()
        if (normalized.isBlank()) {
            return VoiceStepResult(nextStep = VideoAwaitStopStep(parser))
        }

        if (parser.isVideoStopCommand(normalized)) {
            return VoiceStepResult(nextStep = StopVideoStep(parser))
        }

        if (parser.parse(normalized) == VoiceCommandIntent.StopSession) {
            return VoiceStepResult(
                actions = listOf(VoiceSessionAction.Speak("Encerrando escuta")),
                nextStep = EndedStep(),
            )
        }

        return VoiceStepResult(
            actions = listOf(VoiceSessionAction.Speak("Diga parar para finalizar a gravacao")),
            nextStep = VideoAwaitStopStep(parser),
        )
    }
}

package com.example.myapplication.domain.session.steps

import com.example.myapplication.domain.session.VoiceCommandParser
import com.example.myapplication.domain.session.VoiceCommandIntent
import com.example.myapplication.domain.session.VoiceSessionAction
import com.example.myapplication.domain.session.VoiceSessionContext

class ListeningStep(
    private val parser: VoiceCommandParser = VoiceCommandParser(),
) : VoiceSessionStep {
    override val id: String = "listening"

    override fun onEnter(context: VoiceSessionContext): VoiceStepResult {
        return VoiceStepResult(
            actions = listOf(
                VoiceSessionAction.StartListening,
                VoiceSessionAction.ResetTimeout,
            ),
        )
    }

    override fun onUserInput(input: String, context: VoiceSessionContext): VoiceStepResult {
        val normalizedInput = input.trim()
        if (normalizedInput.isBlank()) {
            return VoiceStepResult(nextStep = ListeningStep(parser))
        }

        context.lastCommand = normalizedInput
        val commandIntent = parser.parse(normalizedInput)
        return if (commandIntent == VoiceCommandIntent.StopSession) {
            VoiceStepResult(
                actions = listOf(
                    VoiceSessionAction.Speak("Encerrando escuta"),
                ),
                nextStep = EndedStep(),
            )
        } else if (commandIntent == VoiceCommandIntent.OpenCamera) {
            VoiceStepResult(
                nextStep = CameraAwaitPhotoStep(parser),
            )
        } else {
            VoiceStepResult(
                nextStep = ExecuteCommandStep(commandIntent, normalizedInput, parser),
            )
        }
    }
}

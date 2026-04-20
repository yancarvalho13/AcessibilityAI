package com.example.myapplication.domain.session.steps

import com.example.myapplication.domain.session.VoiceCommandIntent
import com.example.myapplication.domain.session.VoiceCommandParser
import com.example.myapplication.domain.session.VoiceSessionAction
import com.example.myapplication.domain.session.VoiceSessionContext

class CameraAwaitPhotoStep(
    private val parser: VoiceCommandParser,
) : VoiceSessionStep {
    override val id: String = "camera_await_action"

    override fun onEnter(context: VoiceSessionContext): VoiceStepResult {
        return VoiceStepResult(
            actions = listOf(
                VoiceSessionAction.ExecuteCommand(VoiceCommandIntent.OpenCamera, context.lastCommand),
                VoiceSessionAction.StartListening,
                VoiceSessionAction.ResetTimeout,
            ),
        )
    }

    override fun onUserInput(input: String, context: VoiceSessionContext): VoiceStepResult {
        val normalized = input.trim()
        if (normalized.isBlank()) {
            return VoiceStepResult(nextStep = CameraAwaitPhotoStep(parser))
        }

        val intent = parser.parse(normalized)
        return when (intent) {
            VoiceCommandIntent.TakePhoto -> {
                VoiceStepResult(nextStep = CapturePhotoStep(parser))
            }

            VoiceCommandIntent.StartVideo -> {
                VoiceStepResult(nextStep = StartVideoStep(parser))
            }

            VoiceCommandIntent.StopSession -> {
                VoiceStepResult(
                    actions = listOf(VoiceSessionAction.Speak("Encerrando escuta")),
                    nextStep = EndedStep(),
                )
            }

            else -> {
                VoiceStepResult(
                    actions = listOf(VoiceSessionAction.Speak("Diga tirar foto ou iniciar gravacao")),
                    nextStep = CameraAwaitPhotoStep(parser),
                )
            }
        }
    }
}

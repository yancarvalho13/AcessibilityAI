package com.example.myapplication.domain.session.steps

import com.example.myapplication.domain.session.VoiceCommandIntent
import com.example.myapplication.domain.session.VoiceCommandParser
import com.example.myapplication.domain.session.VoiceSessionAction
import com.example.myapplication.domain.session.VoiceSessionContext

class VideoPromptStep(
    private val parser: VoiceCommandParser,
) : VoiceSessionStep {
    override val id: String = "video_prompt"

    override fun onEnter(context: VoiceSessionContext): VoiceStepResult {
        return VoiceStepResult(
            actions = listOf(
                VoiceSessionAction.StartListening,
                VoiceSessionAction.ResetTimeout,
            ),
        )
    }

    override fun onUserInput(input: String, context: VoiceSessionContext): VoiceStepResult {
        val prompt = input.trim()
        if (prompt.isBlank()) {
            return VoiceStepResult(nextStep = VideoPromptStep(parser))
        }

        if (parser.parse(prompt) == VoiceCommandIntent.StopSession) {
            return VoiceStepResult(
                actions = listOf(VoiceSessionAction.Speak("Encerrando escuta")),
                nextStep = EndedStep(),
            )
        }

        return VoiceStepResult(
            actions = listOf(
                VoiceSessionAction.StopListening,
                VoiceSessionAction.ExecuteCommand(VoiceCommandIntent.AnalyzeVideo, prompt),
            ),
            nextStep = ListeningStep(parser),
        )
    }
}

package com.example.myapplication.domain.session

sealed interface VoiceSessionAction {
    data class Speak(val text: String) : VoiceSessionAction

    data object StartListening : VoiceSessionAction

    data object StopListening : VoiceSessionAction

    data object ResetTimeout : VoiceSessionAction

    data class ExecuteCommand(
        val intent: VoiceCommandIntent,
        val rawCommand: String,
    ) : VoiceSessionAction

    data object StopSession : VoiceSessionAction
}

package com.example.myapplication.domain.session

import org.junit.Assert.assertTrue
import org.junit.Test

class VoiceSessionEngineTest {

    @Test
    fun `camera flow should chain open camera, take photo and analyze steps`() {
        val engine = VoiceSessionEngine()

        val startActions = engine.start()
        assertTrue(startActions.any { it is VoiceSessionAction.Speak && it.text.contains("Estou escutando") })
        assertTrue(startActions.any { it is VoiceSessionAction.StartListening })

        val openCameraActions = engine.onUserInput("abrir camera")
        assertTrue(
            openCameraActions.any {
                it is VoiceSessionAction.ExecuteCommand && it.intent == VoiceCommandIntent.OpenCamera
            },
        )
        assertTrue(openCameraActions.any { it is VoiceSessionAction.StartListening })

        val takePhotoActions = engine.onUserInput("tirar foto")
        assertTrue(takePhotoActions.any { it is VoiceSessionAction.StopListening })
        assertTrue(
            takePhotoActions.any {
                it is VoiceSessionAction.ExecuteCommand && it.intent == VoiceCommandIntent.TakePhoto
            },
        )
        assertTrue(takePhotoActions.any { it is VoiceSessionAction.StartListening })

        val analyzeActions = engine.onUserInput("o que esta acontecendo nesta imagem")
        assertTrue(analyzeActions.any { it is VoiceSessionAction.StopListening })
        assertTrue(
            analyzeActions.any {
                it is VoiceSessionAction.ExecuteCommand && it.intent == VoiceCommandIntent.AnalyzePhoto
            },
        )
        assertTrue(analyzeActions.any { it is VoiceSessionAction.StartListening })
    }
}

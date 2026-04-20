package com.example.myapplication.service

import com.example.myapplication.domain.analysis.SceneAnalysisApi
import com.example.myapplication.domain.app.AppLaunchStatus
import com.example.myapplication.domain.app.AppLauncherApi
import com.example.myapplication.domain.session.VoiceCommandIntent

class HeadlessCommandExecutor(
    private val appLauncherApi: AppLauncherApi,
    private val cameraController: HeadlessCameraController,
    private val sceneAnalysisApi: SceneAnalysisApi,
) {
    private var lastPhotoBytes: ByteArray? = null

    suspend fun execute(intent: VoiceCommandIntent, rawCommand: String): HeadlessCommandExecution {
        return when (intent) {
            VoiceCommandIntent.OpenWhatsApp -> {
                when (appLauncherApi.openWhatsApp().status) {
                    AppLaunchStatus.Opened -> HeadlessCommandExecution("Abrindo WhatsApp")
                    AppLaunchStatus.AppNotInstalled -> HeadlessCommandExecution("WhatsApp nao instalado")
                    AppLaunchStatus.NoMatch -> HeadlessCommandExecution("Nao consegui abrir o WhatsApp")
                }
            }

            VoiceCommandIntent.OpenYouTube -> {
                when (appLauncherApi.openYouTube().status) {
                    AppLaunchStatus.Opened -> HeadlessCommandExecution("Abrindo YouTube")
                    AppLaunchStatus.AppNotInstalled -> HeadlessCommandExecution("YouTube nao instalado")
                    AppLaunchStatus.NoMatch -> HeadlessCommandExecution("Nao consegui abrir o YouTube")
                }
            }

            VoiceCommandIntent.OpenCamera -> {
                val opened = cameraController.openCamera()
                if (opened) {
                    HeadlessCommandExecution("Camera iniciada. Diga tirar foto")
                } else {
                    HeadlessCommandExecution("Nao consegui abrir a camera")
                }
            }

            VoiceCommandIntent.TakePhoto -> {
                runCatching { cameraController.takePhotoBytes() }
                    .fold(
                        onSuccess = { bytes ->
                            if (bytes == null || bytes.isEmpty()) {
                                HeadlessCommandExecution("Nao consegui capturar a foto")
                            } else {
                                lastPhotoBytes = bytes
                                HeadlessCommandExecution("Foto capturada. O que voce quer saber?")
                            }
                        },
                        onFailure = {
                            HeadlessCommandExecution("Erro ao capturar foto")
                        },
                    )
            }

            VoiceCommandIntent.AnalyzePhoto -> {
                val photoBytes = lastPhotoBytes
                if (photoBytes == null || photoBytes.isEmpty()) {
                    HeadlessCommandExecution("Nenhuma foto capturada para analisar")
                } else {
                    runCatching {
                        sceneAnalysisApi.analyzePhoto(photoBytes, rawCommand)
                    }.fold(
                        onSuccess = { analysis ->
                            HeadlessCommandExecution(analysis)
                        },
                        onFailure = {
                            HeadlessCommandExecution("Falha ao analisar a foto")
                        },
                    )
                }
            }

            VoiceCommandIntent.StartVideo -> {
                HeadlessCommandExecution("Comando de video disponivel na aba Foto e Video")
            }

            VoiceCommandIntent.StopVideo -> {
                HeadlessCommandExecution("Parada de video disponivel na aba Foto e Video")
            }

            VoiceCommandIntent.AnalyzeVideo -> {
                HeadlessCommandExecution("Analise de video disponivel na aba Foto e Video")
            }

            VoiceCommandIntent.StopSession -> {
                HeadlessCommandExecution("Encerrando escuta")
            }

            VoiceCommandIntent.Unknown -> {
                HeadlessCommandExecution("Nao entendi o comando: $rawCommand")
            }
        }
    }
}

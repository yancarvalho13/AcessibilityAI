package com.example.myapplication.service

import com.example.myapplication.domain.analysis.SceneAnalysisApi
import com.example.myapplication.domain.app.AppLaunchStatus
import com.example.myapplication.domain.app.AppLauncherApi
import com.example.myapplication.domain.session.VoiceCommandIntent

class HeadlessCommandExecutor(
    private val appLauncherApi: AppLauncherApi,
    private val cameraController: HeadlessCameraApi,
    private val sceneAnalysisApi: SceneAnalysisApi,
) {
    private var lastPhotoBytes: ByteArray? = null

    suspend fun execute(intent: VoiceCommandIntent, rawCommand: String): HeadlessCommandExecution {
        return when (intent) {
            VoiceCommandIntent.OpenWhatsApp -> {
                when (appLauncherApi.openWhatsApp().status) {
                    AppLaunchStatus.Opened -> HeadlessCommandExecution(
                        feedbackText = "Abrindo WhatsApp",
                        logMessage = "Headless comando: WhatsApp aberto",
                    )

                    AppLaunchStatus.AppNotInstalled -> HeadlessCommandExecution(
                        feedbackText = "WhatsApp nao instalado",
                        logMessage = "Headless comando: WhatsApp nao instalado",
                    )

                    AppLaunchStatus.NoMatch -> HeadlessCommandExecution(
                        feedbackText = "Nao consegui abrir o WhatsApp",
                        logMessage = "Headless comando: sem match para WhatsApp",
                    )
                }
            }

            VoiceCommandIntent.OpenYouTube -> {
                when (appLauncherApi.openYouTube().status) {
                    AppLaunchStatus.Opened -> HeadlessCommandExecution(
                        feedbackText = "Abrindo YouTube",
                        logMessage = "Headless comando: YouTube aberto",
                    )

                    AppLaunchStatus.AppNotInstalled -> HeadlessCommandExecution(
                        feedbackText = "YouTube nao instalado",
                        logMessage = "Headless comando: YouTube nao instalado",
                    )

                    AppLaunchStatus.NoMatch -> HeadlessCommandExecution(
                        feedbackText = "Nao consegui abrir o YouTube",
                        logMessage = "Headless comando: sem match para YouTube",
                    )
                }
            }

            VoiceCommandIntent.OpenCamera -> {
                val opened = cameraController.openCamera()
                if (opened) {
                    HeadlessCommandExecution(
                        feedbackText = "Camera iniciada.",
                        logMessage = "Headless camera: aberta com sucesso",
                    )
                } else {
                    HeadlessCommandExecution(
                        feedbackText = "Nao consegui abrir a camera",
                        logMessage = "Headless camera: falha ao abrir",
                    )
                }
            }

            VoiceCommandIntent.TakePhoto -> {
                runCatching { capturePhotoWithRecovery() }
                    .fold(
                        onSuccess = { captureResult ->
                            val bytes = captureResult.bytes
                            if (bytes == null || bytes.isEmpty()) {
                                HeadlessCommandExecution(
                                    feedbackText = "Nao consegui capturar a foto",
                                    logMessage = "Headless camera: bytes vazios na captura (${captureResult.diagnostics})",
                                )
                            } else {
                                lastPhotoBytes = bytes
                                HeadlessCommandExecution(
                                    feedbackText = "Foto capturada. O que voce quer saber?",
                                    logMessage = "Headless camera: foto capturada com ${bytes.size} bytes (${captureResult.diagnostics})",
                                )
                            }
                        },
                        onFailure = { error ->
                            HeadlessCommandExecution(
                                feedbackText = "Erro ao capturar foto",
                                logMessage = "Headless camera: erro ao capturar foto (${error.message ?: UNKNOWN_ERROR})",
                            )
                        },
                    )
            }

            VoiceCommandIntent.AnalyzePhoto -> {
                val photoBytes = lastPhotoBytes
                if (photoBytes == null || photoBytes.isEmpty()) {
                    HeadlessCommandExecution(
                        feedbackText = "Nenhuma foto capturada para analisar",
                        logMessage = "Headless Gemini: analise sem foto previa",
                    )
                } else {
                    runCatching {
                        sceneAnalysisApi.analyzePhoto(photoBytes, rawCommand)
                    }.fold(
                        onSuccess = { analysis ->
                            HeadlessCommandExecution(
                                feedbackText = analysis,
                                logMessage = "Headless Gemini: analise de foto concluida",
                            )
                        },
                        onFailure = { error ->
                            HeadlessCommandExecution(
                                feedbackText = "Falha ao analisar a foto",
                                logMessage = "Headless Gemini: erro ao analisar foto (${error.message ?: UNKNOWN_ERROR})",
                            )
                        },
                    )
                }
            }

            VoiceCommandIntent.StartVideo -> {
                HeadlessCommandExecution(
                    feedbackText = "Comando de video disponivel na aba Foto e Video",
                    logMessage = "Headless comando: start video nao suportado neste fluxo",
                )
            }

            VoiceCommandIntent.StopVideo -> {
                HeadlessCommandExecution(
                    feedbackText = "Parada de video disponivel na aba Foto e Video",
                    logMessage = "Headless comando: stop video nao suportado neste fluxo",
                )
            }

            VoiceCommandIntent.AnalyzeVideo -> {
                HeadlessCommandExecution(
                    feedbackText = "Analise de video disponivel na aba Foto e Video",
                    logMessage = "Headless comando: analyze video nao suportado neste fluxo",
                )
            }

            VoiceCommandIntent.StopSession -> {
                HeadlessCommandExecution(
                    feedbackText = "Encerrando escuta",
                    logMessage = "Headless sessao: comando de parada recebido",
                )
            }

            VoiceCommandIntent.Unknown -> {
                HeadlessCommandExecution(
                    feedbackText = "Nao entendi o comando: $rawCommand",
                    logMessage = "Headless parser: comando desconhecido ($rawCommand)",
                )
            }
        }
    }

    private suspend fun capturePhotoWithRecovery(): CapturePhotoResult {
        val firstAttempt = runCatching { cameraController.takePhotoBytes() }
        val firstBytes = firstAttempt.getOrNull()
        if (firstBytes != null && firstBytes.isNotEmpty()) {
            return CapturePhotoResult(
                bytes = firstBytes,
                diagnostics = "tentativa=1",
            )
        }

        val reopenAttempt = runCatching { cameraController.openCamera() }
        val reopenSucceeded = reopenAttempt.getOrDefault(false)

        val secondAttempt = runCatching { cameraController.takePhotoBytes() }
        val secondBytes = secondAttempt.getOrNull()
        if (secondBytes != null && secondBytes.isNotEmpty()) {
            return CapturePhotoResult(
                bytes = secondBytes,
                diagnostics = "tentativa=2, reopen=$reopenSucceeded",
            )
        }

        val diagnostics = buildCaptureDiagnostics(
            reopenSucceeded = reopenSucceeded,
            firstError = firstAttempt.exceptionOrNull(),
            reopenError = reopenAttempt.exceptionOrNull(),
            secondError = secondAttempt.exceptionOrNull(),
        )
        val rootCause = secondAttempt.exceptionOrNull()
            ?: reopenAttempt.exceptionOrNull()
            ?: firstAttempt.exceptionOrNull()
        if (rootCause != null) {
            throw IllegalStateException(diagnostics, rootCause)
        }

        return CapturePhotoResult(
            bytes = null,
            diagnostics = diagnostics,
        )
    }

    private fun buildCaptureDiagnostics(
        reopenSucceeded: Boolean,
        firstError: Throwable?,
        reopenError: Throwable?,
        secondError: Throwable?,
    ): String {
        val firstMsg = firstError?.message ?: "sem_erro"
        val reopenMsg = reopenError?.message ?: "sem_erro"
        val secondMsg = secondError?.message ?: "sem_erro"
        return "reopen=$reopenSucceeded, primeira=$firstMsg, reopenErro=$reopenMsg, segunda=$secondMsg"
    }

    private data class CapturePhotoResult(
        val bytes: ByteArray?,
        val diagnostics: String,
    )

    companion object {
        private const val UNKNOWN_ERROR = "erro desconhecido"
    }
}

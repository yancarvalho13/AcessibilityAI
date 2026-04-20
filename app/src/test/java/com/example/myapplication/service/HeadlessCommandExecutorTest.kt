package com.example.myapplication.service

import com.example.myapplication.domain.analysis.SceneAnalysisApi
import com.example.myapplication.domain.app.AppLaunchResult
import com.example.myapplication.domain.app.AppLaunchStatus
import com.example.myapplication.domain.app.AppLauncherApi
import com.example.myapplication.domain.session.VoiceCommandIntent
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test
import kotlin.coroutines.cancellation.CancellationException

class HeadlessCommandExecutorTest {

    @Test
    fun `take photo should retry after failed first capture`() = runBlocking {
        val camera = FakeHeadlessCameraApi(
            openCameraResult = true,
            firstCaptureThrows = true,
            secondCaptureBytes = byteArrayOf(1, 2, 3),
        )

        val executor = HeadlessCommandExecutor(
            appLauncherApi = FakeAppLauncherApi(),
            cameraController = camera,
            sceneAnalysisApi = FakeSceneAnalysisApi(),
        )

        val result = executor.execute(VoiceCommandIntent.TakePhoto, "tirar foto")

        assertEquals("Foto capturada. O que voce quer saber?", result.feedbackText)
        assertTrue(result.logMessage?.contains("tentativa=2") == true)
        assertTrue(camera.openCameraCalls >= 1)
        assertEquals(2, camera.takePhotoCalls)
    }

    @Test
    fun `take photo should include diagnostics when both attempts fail`() = runBlocking {
        val camera = FakeHeadlessCameraApi(
            openCameraResult = false,
            firstCaptureThrows = true,
            secondCaptureThrows = true,
            secondCaptureBytes = byteArrayOf(),
        )

        val executor = HeadlessCommandExecutor(
            appLauncherApi = FakeAppLauncherApi(),
            cameraController = camera,
            sceneAnalysisApi = FakeSceneAnalysisApi(),
        )

        val result = executor.execute(VoiceCommandIntent.TakePhoto, "tirar foto")

        assertEquals("Erro ao capturar foto", result.feedbackText)
        assertTrue(result.logMessage?.contains("reopen=false") == true)
        assertTrue(result.logMessage?.contains("primeira=failed to submit capture request") == true)
    }

    @Test
    fun `analyze photo should use captured image with prompt`() = runBlocking {
        val sceneApi = FakeSceneAnalysisApi()
        val progressMessages = mutableListOf<String>()
        val executor = HeadlessCommandExecutor(
            appLauncherApi = FakeAppLauncherApi(),
            cameraController = FakeHeadlessCameraApi(
                openCameraResult = true,
                firstCaptureThrows = false,
                secondCaptureBytes = byteArrayOf(9, 9),
            ),
            sceneAnalysisApi = sceneApi,
        )

        executor.execute(VoiceCommandIntent.TakePhoto, "tirar foto")
        val analyzeResult = executor.execute(
            intent = VoiceCommandIntent.AnalyzePhoto,
            rawCommand = "descreva a foto",
            onProgress = { progress -> progressMessages += progress.feedbackText },
        )

        assertEquals("analise-ok", analyzeResult.feedbackText)
        assertTrue(progressMessages.contains("Analise em andamento"))
        assertEquals("descreva a foto", sceneApi.lastPrompt)
        assertTrue(sceneApi.lastPhotoBytes?.isNotEmpty() == true)
    }

    @Test
    fun `start stop and analyze video should use recorded bytes`() = runBlocking {
        val sceneApi = FakeSceneAnalysisApi()
        val camera = FakeHeadlessCameraApi(
            openCameraResult = true,
            firstCaptureThrows = false,
            secondCaptureBytes = byteArrayOf(1),
            videoStartResult = true,
            videoStopBytes = byteArrayOf(4, 5, 6),
        )

        val executor = HeadlessCommandExecutor(
            appLauncherApi = FakeAppLauncherApi(),
            cameraController = camera,
            sceneAnalysisApi = sceneApi,
        )

        val start = executor.execute(VoiceCommandIntent.StartVideo, "iniciar gravacao")
        assertEquals("Gravacao iniciada. Diga parar para finalizar.", start.feedbackText)

        val stop = executor.execute(VoiceCommandIntent.StopVideo, "parar")
        assertEquals("Video salvo. O que deseja fazer com o video?", stop.feedbackText)

        val analyze = executor.execute(VoiceCommandIntent.AnalyzeVideo, "resuma o video")
        assertEquals("video-ok", analyze.feedbackText)
        assertEquals("resuma o video", sceneApi.lastVideoPrompt)
        assertTrue(sceneApi.lastVideoBytes?.isNotEmpty() == true)
    }

    @Test
    fun `analyze photo should propagate cancellation`() = runBlocking {
        val sceneApi = FakeSceneAnalysisApi(throwOnAnalyzePhoto = true)
        val executor = HeadlessCommandExecutor(
            appLauncherApi = FakeAppLauncherApi(),
            cameraController = FakeHeadlessCameraApi(
                openCameraResult = true,
                firstCaptureThrows = false,
                secondCaptureBytes = byteArrayOf(7, 7),
            ),
            sceneAnalysisApi = sceneApi,
        )

        executor.execute(VoiceCommandIntent.TakePhoto, "tirar foto")

        try {
            executor.execute(VoiceCommandIntent.AnalyzePhoto, "descreva")
            fail("Expected CancellationException")
        } catch (_: CancellationException) {
        }
    }

    private class FakeHeadlessCameraApi(
        private val openCameraResult: Boolean,
        private val firstCaptureThrows: Boolean,
        private val secondCaptureThrows: Boolean = false,
        private val secondCaptureBytes: ByteArray,
        private val videoStartResult: Boolean = false,
        private val videoStopBytes: ByteArray? = null,
    ) : HeadlessCameraApi {
        var openCameraCalls: Int = 0
        var takePhotoCalls: Int = 0

        override suspend fun openCamera(): Boolean {
            openCameraCalls++
            return openCameraResult
        }

        override suspend fun takePhotoBytes(): ByteArray? {
            takePhotoCalls++
            if (takePhotoCalls == 1 && firstCaptureThrows) {
                throw IllegalStateException("failed to submit capture request")
            }
            if (takePhotoCalls == 2 && secondCaptureThrows) {
                throw IllegalStateException("camera is not active")
            }
            return secondCaptureBytes
        }

        override fun startVideoRecording(): Boolean {
            return videoStartResult
        }

        override suspend fun stopVideoBytes(): ByteArray? {
            return videoStopBytes
        }

        override fun release() {
        }
    }

    private class FakeAppLauncherApi : AppLauncherApi {
        override fun openWhatsApp(): AppLaunchResult {
            return AppLaunchResult(AppLaunchStatus.Opened, "WhatsApp")
        }

        override fun openYouTube(): AppLaunchResult {
            return AppLaunchResult(AppLaunchStatus.Opened, "YouTube")
        }

        override fun openByVoiceCommand(command: String): AppLaunchResult {
            return AppLaunchResult(AppLaunchStatus.Opened, "Unknown", command)
        }
    }

    private class FakeSceneAnalysisApi(
        private val throwOnAnalyzePhoto: Boolean = false,
    ) : SceneAnalysisApi {
        var lastPhotoBytes: ByteArray? = null
        var lastPrompt: String? = null
        var lastVideoBytes: ByteArray? = null
        var lastVideoPrompt: String? = null

        override suspend fun analyzePhoto(photoBytes: ByteArray, prompt: String?): String {
            if (throwOnAnalyzePhoto) {
                throw CancellationException("cancelled")
            }
            lastPhotoBytes = photoBytes
            lastPrompt = prompt
            return "analise-ok"
        }

        override suspend fun analyzeVideo(videoBytes: ByteArray, prompt: String?): String {
            lastVideoBytes = videoBytes
            lastVideoPrompt = prompt
            return "video-ok"
        }
    }
}

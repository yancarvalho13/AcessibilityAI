package com.example.myapplication.service

import android.content.Context
import android.content.pm.PackageManager
import android.os.SystemClock
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageCapture
import androidx.camera.core.ImageCaptureException
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.core.content.ContextCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.LifecycleRegistry
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException
import kotlinx.coroutines.delay
import kotlinx.coroutines.suspendCancellableCoroutine

class HeadlessCameraController(
    private val appContext: Context,
) : HeadlessCameraApi {
    private val mainExecutor by lazy { ContextCompat.getMainExecutor(appContext) }
    private val lifecycleOwner = HeadlessLifecycleOwner()

    private var cameraProvider: ProcessCameraProvider? = null
    private var imageCapture: ImageCapture? = null
    private var lastOpenAtMs: Long = 0L

    override suspend fun openCamera(): Boolean {
        val hasPermission = ContextCompat.checkSelfPermission(
            appContext,
            android.Manifest.permission.CAMERA,
        ) == PackageManager.PERMISSION_GRANTED
        if (!hasPermission) {
            return false
        }

        return runCatching {
            val provider = getProvider()
            val capture = ImageCapture.Builder()
                .setCaptureMode(ImageCapture.CAPTURE_MODE_MINIMIZE_LATENCY)
                .build()

            provider.unbindAll()
            provider.bindToLifecycle(
                lifecycleOwner,
                CameraSelector.DEFAULT_BACK_CAMERA,
                capture,
            )

            cameraProvider = provider
            imageCapture = capture
            lastOpenAtMs = SystemClock.elapsedRealtime()
            true
        }.getOrDefault(false)
    }

    override suspend fun takePhotoBytes(): ByteArray? {
        val capture = imageCapture ?: return null
        val provider = cameraProvider
        if (provider == null || !provider.isBound(capture)) {
            throw IllegalStateException("camera capture use case is not bound")
        }
        waitForCameraWarmupIfNeeded()

        val photoFile = File(appContext.cacheDir, "headless_${timestampNow()}.jpg")
        val outputOptions = ImageCapture.OutputFileOptions.Builder(photoFile).build()

        return suspendCancellableCoroutine { continuation ->
            capture.takePicture(
                outputOptions,
                mainExecutor,
                object : ImageCapture.OnImageSavedCallback {
                    override fun onImageSaved(outputFileResults: ImageCapture.OutputFileResults) {
                        runCatching { photoFile.readBytes() }
                            .onSuccess { bytes -> continuation.resume(bytes) }
                            .onFailure { error -> continuation.resumeWithException(error) }
                        photoFile.delete()
                    }

                    override fun onError(exception: ImageCaptureException) {
                        continuation.resumeWithException(exception)
                        photoFile.delete()
                    }
                },
            )
        }
    }

    override fun release() {
        imageCapture = null
        cameraProvider?.unbindAll()
        cameraProvider = null
        lifecycleOwner.reset()
    }

    private suspend fun waitForCameraWarmupIfNeeded() {
        val elapsed = SystemClock.elapsedRealtime() - lastOpenAtMs
        val remaining = CAMERA_WARMUP_MS - elapsed
        if (remaining > 0) {
            delay(remaining)
        }
    }

    private suspend fun getProvider(): ProcessCameraProvider {
        val future = ProcessCameraProvider.getInstance(appContext)
        return suspendCancellableCoroutine { continuation ->
            future.addListener(
                {
                    runCatching { future.get() }
                        .onSuccess { provider -> continuation.resume(provider) }
                        .onFailure { error -> continuation.resumeWithException(error) }
                },
                mainExecutor,
            )
        }
    }

    private fun timestampNow(): String {
        return SimpleDateFormat("yyyyMMdd_HHmmss", Locale.US).format(Date())
    }

    private class HeadlessLifecycleOwner : LifecycleOwner {
        private val registry = LifecycleRegistry(this)

        init {
            registry.currentState = Lifecycle.State.CREATED
            registry.currentState = Lifecycle.State.STARTED
            registry.currentState = Lifecycle.State.RESUMED
        }

        override val lifecycle: Lifecycle
            get() = registry

        fun reset() {
            registry.currentState = Lifecycle.State.DESTROYED
        }
    }

    companion object {
        private const val CAMERA_WARMUP_MS = 500L
    }
}

package com.example.myapplication.service

import android.content.Context
import android.content.pm.PackageManager
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageCapture
import androidx.camera.core.ImageCaptureException
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.core.content.ContextCompat
import androidx.lifecycle.LifecycleOwner
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException
import kotlinx.coroutines.suspendCancellableCoroutine

class HeadlessCameraController(
    private val appContext: Context,
    private val lifecycleOwner: LifecycleOwner,
) {
    private val mainExecutor by lazy { ContextCompat.getMainExecutor(appContext) }

    private var cameraProvider: ProcessCameraProvider? = null
    private var imageCapture: ImageCapture? = null

    suspend fun openCamera(): Boolean {
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
            true
        }.getOrDefault(false)
    }

    suspend fun takePhotoBytes(): ByteArray? {
        val capture = imageCapture ?: return null
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

    fun release() {
        imageCapture = null
        cameraProvider?.unbindAll()
        cameraProvider = null
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
}

package com.example.myapplication.data.camera

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageCapture
import androidx.camera.core.ImageCaptureException
import androidx.camera.core.Preview
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.video.FileOutputOptions
import androidx.camera.video.FallbackStrategy
import androidx.camera.video.Quality
import androidx.camera.video.QualitySelector
import androidx.camera.video.Recorder
import androidx.camera.video.Recording
import androidx.camera.video.VideoCapture
import androidx.camera.video.VideoRecordEvent
import androidx.camera.view.PreviewView
import androidx.core.content.ContextCompat
import androidx.lifecycle.LifecycleOwner
import com.example.myapplication.domain.camera.CameraServiceApi
import com.example.myapplication.domain.camera.CameraServiceState
import com.example.myapplication.domain.camera.CameraVideoRef
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.suspendCancellableCoroutine

class AndroidCameraServiceApi(
    private val appContext: Context,
) : CameraServiceApi {
    private val _state = MutableStateFlow(CameraServiceState())
    override val state: StateFlow<CameraServiceState> = _state.asStateFlow()

    private val mainExecutor by lazy { ContextCompat.getMainExecutor(appContext) }

    private var cameraProvider: ProcessCameraProvider? = null
    private var imageCapture: ImageCapture? = null
    private var videoCapture: VideoCapture<Recorder>? = null
    private var activeRecording: Recording? = null
    private var pendingVideoResult: CompletableDeferred<CameraVideoRef?>? = null
    private var recordingStartedAtMs: Long = 0L
    private var currentVideoFile: File? = null

    override fun openCamera(lifecycleOwner: LifecycleOwner, previewView: PreviewView) {
        val providerFuture = ProcessCameraProvider.getInstance(appContext)
        providerFuture.addListener(
            {
                runCatching {
                    val provider = providerFuture.get()
                    cameraProvider = provider

                    val preview = Preview.Builder().build().also {
                        it.surfaceProvider = previewView.surfaceProvider
                    }

                    imageCapture = ImageCapture.Builder()
                        .setCaptureMode(ImageCapture.CAPTURE_MODE_MINIMIZE_LATENCY)
                        .build()

                    val recorder = Recorder.Builder()
                        .setQualitySelector(
                            QualitySelector.from(
                                Quality.HD,
                                FallbackStrategy.lowerQualityOrHigherThan(Quality.SD),
                            ),
                        )
                        .build()

                    videoCapture = VideoCapture.withOutput(recorder)

                    provider.unbindAll()
                    provider.bindToLifecycle(
                        lifecycleOwner,
                        CameraSelector.DEFAULT_BACK_CAMERA,
                        preview,
                        imageCapture,
                        videoCapture,
                    )

                    _state.value = _state.value.copy(isCameraOpen = true)
                }.onFailure {
                    _state.value = _state.value.copy(isCameraOpen = false)
                }
            },
            mainExecutor,
        )
    }

    override suspend fun capturePhotoBytes(): ByteArray {
        val capture = imageCapture ?: error("Camera not opened. Call openCamera first.")

        val imageFile = File(appContext.cacheDir, "photo_${timestampNow()}.jpg")
        val outputOptions = ImageCapture.OutputFileOptions.Builder(imageFile).build()

        return suspendCancellableCoroutine { continuation ->
            capture.takePicture(
                outputOptions,
                mainExecutor,
                object : ImageCapture.OnImageSavedCallback {
                    override fun onImageSaved(outputFileResults: ImageCapture.OutputFileResults) {
                        runCatching { imageFile.readBytes() }
                            .onSuccess { bytes -> continuation.resume(bytes) }
                            .onFailure { error -> continuation.resumeWithException(error) }
                        imageFile.delete()
                    }

                    override fun onError(exception: ImageCaptureException) {
                        continuation.resumeWithException(exception)
                        imageFile.delete()
                    }
                },
            )
        }
    }

    override fun startVideoRecording() {
        if (activeRecording != null) {
            return
        }

        val capture = videoCapture ?: error("Camera not opened. Call openCamera first.")

        val videosDir = File(appContext.filesDir, VIDEOS_DIR_NAME)
        if (!videosDir.exists()) {
            videosDir.mkdirs()
        }

        val videoFile = File(videosDir, "video_${timestampNow()}.mp4")
        currentVideoFile = videoFile
        recordingStartedAtMs = System.currentTimeMillis()
        pendingVideoResult = CompletableDeferred()

        val outputOptions = FileOutputOptions.Builder(videoFile).build()
        val baseRecording = capture.output.prepareRecording(appContext, outputOptions)

        val recordingBuilder = if (
            ContextCompat.checkSelfPermission(appContext, Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED
        ) {
            baseRecording.withAudioEnabled()
        } else {
            baseRecording
        }

        activeRecording = recordingBuilder.start(mainExecutor) { event ->
            when (event) {
                is VideoRecordEvent.Start -> {
                    _state.value = _state.value.copy(isRecordingVideo = true)
                }

                is VideoRecordEvent.Finalize -> {
                    val finishedFile = currentVideoFile
                    val durationMs = (System.currentTimeMillis() - recordingStartedAtMs).coerceAtLeast(0L)

                    val result = if (event.hasError() || finishedFile == null || !finishedFile.exists()) {
                        null
                    } else {
                        CameraVideoRef(
                            filePath = finishedFile.absolutePath,
                            durationMs = durationMs,
                            sizeBytes = finishedFile.length(),
                            createdAtMs = recordingStartedAtMs,
                        )
                    }

                    pendingVideoResult?.complete(result)
                    pendingVideoResult = null
                    activeRecording = null
                    currentVideoFile = null
                    recordingStartedAtMs = 0L

                    _state.value = _state.value.copy(
                        isRecordingVideo = false,
                        lastVideo = result ?: _state.value.lastVideo,
                    )
                }
            }
        }
    }

    override suspend fun stopVideoRecording(): CameraVideoRef? {
        val recording = activeRecording ?: return _state.value.lastVideo
        val deferred = pendingVideoResult

        recording.stop()
        return deferred?.await() ?: _state.value.lastVideo
    }

    override suspend fun getLastVideoBytes(): ByteArray? {
        val lastVideo = _state.value.lastVideo ?: return null
        val file = File(lastVideo.filePath)
        if (!file.exists()) {
            return null
        }
        return file.readBytes()
    }

    override fun closeCamera() {
        activeRecording?.stop()
        activeRecording = null
        pendingVideoResult = null
        currentVideoFile = null
        recordingStartedAtMs = 0L

        cameraProvider?.unbindAll()
        cameraProvider = null
        imageCapture = null
        videoCapture = null

        _state.value = _state.value.copy(
            isCameraOpen = false,
            isRecordingVideo = false,
        )
    }

    private fun timestampNow(): String {
        return SimpleDateFormat("yyyyMMdd_HHmmss", Locale.US).format(Date())
    }

    companion object {
        private const val VIDEOS_DIR_NAME = "camera_videos"
    }
}

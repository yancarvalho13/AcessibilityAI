package com.example.myapplication.domain.camera

import androidx.camera.view.PreviewView
import androidx.lifecycle.LifecycleOwner
import kotlinx.coroutines.flow.StateFlow

interface CameraServiceApi {
    val state: StateFlow<CameraServiceState>

    fun openCamera(lifecycleOwner: LifecycleOwner, previewView: PreviewView)

    suspend fun capturePhotoBytes(): ByteArray

    fun startVideoRecording()

    suspend fun stopVideoRecording(): CameraVideoRef?

    suspend fun getLastVideoBytes(): ByteArray?

    fun closeCamera()
}

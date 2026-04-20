package com.example.myapplication.domain.camera

data class CameraServiceState(
    val isCameraOpen: Boolean = false,
    val isRecordingVideo: Boolean = false,
    val lastVideo: CameraVideoRef? = null,
)

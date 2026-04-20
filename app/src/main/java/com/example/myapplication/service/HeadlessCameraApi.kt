package com.example.myapplication.service

interface HeadlessCameraApi {
    suspend fun openCamera(): Boolean

    suspend fun takePhotoBytes(): ByteArray?

    fun startVideoRecording(): Boolean

    suspend fun stopVideoBytes(): ByteArray?

    fun release()
}

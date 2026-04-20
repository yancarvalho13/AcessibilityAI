package com.example.myapplication.service

interface HeadlessCameraApi {
    suspend fun openCamera(): Boolean

    suspend fun takePhotoBytes(): ByteArray?

    fun release()
}

package com.example.myapplication.domain.analysis

interface SceneAnalysisApi {
    suspend fun analyzePhoto(photoBytes: ByteArray, prompt: String? = null): String

    suspend fun analyzeVideo(videoBytes: ByteArray, prompt: String? = null): String
}

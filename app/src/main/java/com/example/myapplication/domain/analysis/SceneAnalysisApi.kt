package com.example.myapplication.domain.analysis

interface SceneAnalysisApi {
    suspend fun analyzePhoto(photoBytes: ByteArray): String

    suspend fun analyzeVideo(videoBytes: ByteArray): String
}

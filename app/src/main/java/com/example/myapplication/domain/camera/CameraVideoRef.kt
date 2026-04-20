package com.example.myapplication.domain.camera

data class CameraVideoRef(
    val filePath: String,
    val durationMs: Long,
    val sizeBytes: Long,
    val createdAtMs: Long,
)

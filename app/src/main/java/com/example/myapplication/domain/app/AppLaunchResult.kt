package com.example.myapplication.domain.app

data class AppLaunchResult(
    val status: AppLaunchStatus,
    val targetApp: String? = null,
    val commandText: String = "",
)

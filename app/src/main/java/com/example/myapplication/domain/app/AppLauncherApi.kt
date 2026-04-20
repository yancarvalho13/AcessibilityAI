package com.example.myapplication.domain.app

interface AppLauncherApi {
    fun openWhatsApp(): AppLaunchResult

    fun openYouTube(): AppLaunchResult

    fun openByVoiceCommand(command: String): AppLaunchResult
}

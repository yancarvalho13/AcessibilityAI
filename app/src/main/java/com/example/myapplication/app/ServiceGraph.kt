package com.example.myapplication.app

import android.content.Context
import com.example.myapplication.data.camera.AndroidCameraServiceApi
import com.example.myapplication.data.overlay.AndroidOverlayServiceApi
import com.example.myapplication.data.voice.AndroidVoiceServiceApi
import com.example.myapplication.domain.camera.CameraServiceApi
import com.example.myapplication.domain.overlay.OverlayServiceApi
import com.example.myapplication.domain.voice.VoiceServiceApi

class ServiceGraph(context: Context) {
    private val appContext = context.applicationContext

    val voiceServiceApi: VoiceServiceApi by lazy {
        AndroidVoiceServiceApi(appContext)
    }

    val overlayServiceApi: OverlayServiceApi by lazy {
        AndroidOverlayServiceApi(appContext)
    }

    val cameraServiceApi: CameraServiceApi by lazy {
        AndroidCameraServiceApi(appContext)
    }
}

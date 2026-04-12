package com.example.myapplication

import android.app.Service
import android.content.Intent
import android.graphics.PixelFormat
import android.provider.Settings
import android.os.IBinder
import android.view.Gravity
import android.view.LayoutInflater
import android.view.View
import android.view.WindowManager
import androidx.core.os.postDelayed
import android.os.Handler
import android.os.Looper

class OverlayService : Service() {
    private lateinit var  windowManager: WindowManager
    private var view: View? = null
    private var isAttached = false

    override fun onCreate() {
        super.onCreate()
        if (!Settings.canDrawOverlays(this)) {
            stopSelf()
            return
        }

        windowManager = getSystemService(WINDOW_SERVICE) as WindowManager
        view = LayoutInflater.from(this).inflate(R.layout.overlay_layout, null)

        val params = WindowManager.LayoutParams(
            WindowManager.LayoutParams.MATCH_PARENT,
            400,
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE,
            PixelFormat.TRANSLUCENT
        )

        params.gravity = Gravity.BOTTOM

        view?.let {
            windowManager.addView(it, params)
            isAttached = true
        }

        Handler(Looper.getMainLooper()).postDelayed({
            stopSelf()
        }, 4000)
    }
    override fun onBind(p0: Intent?): IBinder? = null

    override fun onDestroy() {
        super.onDestroy()
        if (isAttached) {
            view?.let { windowManager.removeView(it) }
        }
    }


}

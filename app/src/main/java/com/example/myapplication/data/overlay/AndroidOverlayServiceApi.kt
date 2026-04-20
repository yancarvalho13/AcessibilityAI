package com.example.myapplication.data.overlay

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.provider.Settings
import com.example.myapplication.OverlayService
import com.example.myapplication.domain.overlay.OverlayServiceApi
import com.example.myapplication.domain.overlay.OverlayServiceState
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

class AndroidOverlayServiceApi(
    private val appContext: Context,
) : OverlayServiceApi {
    private val _state = MutableStateFlow(OverlayServiceState())
    override val state: StateFlow<OverlayServiceState> = _state.asStateFlow()

    init {
        refreshPermissionState()
    }

    override fun refreshPermissionState() {
        _state.value = OverlayServiceState(hasPermission = Settings.canDrawOverlays(appContext))
    }

    override fun openPermissionSettings() {
        val settingsIntent = Intent(
            Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
            Uri.parse("package:${appContext.packageName}"),
        ).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        appContext.startActivity(settingsIntent)
    }

    override fun showOverlay() {
        refreshPermissionState()
        if (!_state.value.hasPermission) {
            return
        }

        val serviceIntent = Intent(appContext, OverlayService::class.java)
        appContext.startService(serviceIntent)
    }
}

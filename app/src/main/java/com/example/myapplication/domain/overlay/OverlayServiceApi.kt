package com.example.myapplication.domain.overlay

import kotlinx.coroutines.flow.StateFlow

interface OverlayServiceApi {
    val state: StateFlow<OverlayServiceState>

    fun refreshPermissionState()

    fun openPermissionSettings()

    fun showOverlay()
}

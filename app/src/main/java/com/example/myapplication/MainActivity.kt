package com.example.myapplication

import android.Manifest
import android.content.pm.PackageManager
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.core.content.ContextCompat
import com.example.myapplication.app.MyApplication
import com.example.myapplication.presentation.MainViewModel
import com.example.myapplication.ui.VoiceLabScreen
import androidx.lifecycle.compose.collectAsStateWithLifecycle

class MainActivity : ComponentActivity() {
    private val viewModel: MainViewModel by viewModels {
        val graph = (application as MyApplication).serviceGraph
        MainViewModel.Factory(
            voiceServiceApi = graph.voiceServiceApi,
            overlayServiceApi = graph.overlayServiceApi,
        )
    }

    private val requestAudioPermission =
        registerForActivityResult(ActivityResultContracts.RequestPermission()) { isGranted ->
            viewModel.startVoice(audioPermissionGranted = isGranted)
        }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        setContent {
            val uiState = viewModel.uiState.collectAsStateWithLifecycle()

            MaterialTheme {
                Surface {
                    VoiceLabScreen(
                        uiState = uiState.value,
                        onStartVoice = { onStartVoiceClicked() },
                        onStopVoice = viewModel::stopVoice,
                        onOpenOverlaySettings = viewModel::openOverlaySettings,
                        onShowOverlay = viewModel::showOverlay,
                    )
                }
            }
        }
    }

    override fun onResume() {
        super.onResume()
        viewModel.refreshOverlayPermission()
        viewModel.dismissInfoMessage()
    }

    private fun onStartVoiceClicked() {
        val hasPermission = ContextCompat.checkSelfPermission(
            this,
            Manifest.permission.RECORD_AUDIO,
        ) == PackageManager.PERMISSION_GRANTED

        if (hasPermission) {
            viewModel.startVoice(audioPermissionGranted = true)
            return
        }

        requestAudioPermission.launch(Manifest.permission.RECORD_AUDIO)
    }
}

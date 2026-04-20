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
import androidx.lifecycle.LifecycleOwner
import com.example.myapplication.app.MyApplication
import com.example.myapplication.presentation.MainViewModel
import com.example.myapplication.ui.VoiceLabScreen
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.camera.view.PreviewView

class MainActivity : ComponentActivity() {
    private var pendingCameraOwner: LifecycleOwner? = null
    private var pendingPreviewView: PreviewView? = null

    private val viewModel: MainViewModel by viewModels {
        val graph = (application as MyApplication).serviceGraph
        MainViewModel.Factory(
            voiceServiceApi = graph.voiceServiceApi,
            overlayServiceApi = graph.overlayServiceApi,
            cameraServiceApi = graph.cameraServiceApi,
            sceneAnalysisApi = graph.sceneAnalysisApi,
        )
    }

    private val requestAudioPermission =
        registerForActivityResult(ActivityResultContracts.RequestPermission()) { isGranted ->
            viewModel.startVoice(audioPermissionGranted = isGranted)
        }

    private val requestCameraPermission =
        registerForActivityResult(ActivityResultContracts.RequestPermission()) { isGranted ->
            val owner = pendingCameraOwner
            val preview = pendingPreviewView
            if (isGranted && owner != null && preview != null) {
                viewModel.openCamera(owner, preview, hasCameraPermission = true)
            } else {
                viewModel.onCameraPermissionDenied()
            }
            pendingCameraOwner = null
            pendingPreviewView = null
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
                        onReadAudioBytes = viewModel::readLastAudioBytesAndLog,
                        onOpenOverlaySettings = viewModel::openOverlaySettings,
                        onShowOverlay = viewModel::showOverlay,
                        onOpenCamera = ::onOpenCameraClicked,
                        onCloseCamera = viewModel::closeCamera,
                        onCapturePhoto = viewModel::capturePhotoAndLog,
                        onStartVideo = ::onStartVideoClicked,
                        onStopVideo = viewModel::stopVideoRecordingAndLog,
                        onReadVideoBytes = viewModel::readLastVideoBytesAndLog,
                        onAnalyzeLastPhoto = viewModel::analyzeLastPhotoWithGemini,
                        onAnalyzeLastVideo = viewModel::analyzeLastVideoWithGemini,
                        onClearLogs = viewModel::clearLogs,
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

    override fun onDestroy() {
        viewModel.closeCamera()
        super.onDestroy()
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

    private fun onOpenCameraClicked(lifecycleOwner: LifecycleOwner, previewView: PreviewView) {
        val hasPermission = ContextCompat.checkSelfPermission(
            this,
            Manifest.permission.CAMERA,
        ) == PackageManager.PERMISSION_GRANTED

        if (hasPermission) {
            viewModel.openCamera(lifecycleOwner, previewView, hasCameraPermission = true)
            return
        }

        pendingCameraOwner = lifecycleOwner
        pendingPreviewView = previewView
        requestCameraPermission.launch(Manifest.permission.CAMERA)
    }

    private fun onStartVideoClicked() {
        val hasPermission = ContextCompat.checkSelfPermission(
            this,
            Manifest.permission.CAMERA,
        ) == PackageManager.PERMISSION_GRANTED
        viewModel.startVideoRecording(hasCameraPermission = hasPermission)
    }
}

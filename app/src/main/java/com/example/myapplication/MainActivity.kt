package com.example.myapplication

import android.Manifest
import android.content.pm.PackageManager
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.camera.view.PreviewView
import androidx.compose.foundation.layout.Column
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
import androidx.compose.material3.Text
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.res.stringResource
import androidx.core.content.ContextCompat
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.myapplication.app.MyApplication
import com.example.myapplication.presentation.MainViewModel
import com.example.myapplication.presentation.MediaAnalysisViewModel
import com.example.myapplication.ui.MediaAnalysisScreen
import com.example.myapplication.ui.VoiceLabScreen

class MainActivity : ComponentActivity() {
    private var pendingCameraOwner: LifecycleOwner? = null
    private var pendingPreviewView: PreviewView? = null
    private var pendingAudioAction: AudioPermissionAction? = null

    private val voiceViewModel: MainViewModel by viewModels {
        val graph = (application as MyApplication).serviceGraph
        MainViewModel.Factory(
            voiceServiceApi = graph.voiceServiceApi,
            overlayServiceApi = graph.overlayServiceApi,
        )
    }

    private val mediaViewModel: MediaAnalysisViewModel by viewModels {
        val graph = (application as MyApplication).serviceGraph
        MediaAnalysisViewModel.Factory(
            cameraServiceApi = graph.cameraServiceApi,
            sceneAnalysisApi = graph.sceneAnalysisApi,
            speechToTextApi = graph.speechToTextApi,
            textToSpeechApi = graph.textToSpeechApi,
        )
    }

    private val requestAudioPermission =
        registerForActivityResult(ActivityResultContracts.RequestPermission()) { isGranted ->
            when (pendingAudioAction) {
                AudioPermissionAction.START_VOICE_SERVICE -> voiceViewModel.startVoice(isGranted)
                AudioPermissionAction.START_PROMPT_STT -> mediaViewModel.startPromptListening(isGranted)
                null -> Unit
            }
            pendingAudioAction = null
        }

    private val requestCameraPermission =
        registerForActivityResult(ActivityResultContracts.RequestPermission()) { isGranted ->
            val owner = pendingCameraOwner
            val preview = pendingPreviewView
            if (isGranted && owner != null && preview != null) {
                mediaViewModel.openCamera(owner, preview, hasCameraPermission = true)
            } else {
                mediaViewModel.onCameraPermissionDenied()
            }
            pendingCameraOwner = null
            pendingPreviewView = null
        }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        setContent {
            val voiceUiState = voiceViewModel.uiState.collectAsStateWithLifecycle()
            val mediaUiState = mediaViewModel.uiState.collectAsStateWithLifecycle()
            var selectedTabIndex by remember { mutableIntStateOf(0) }

            MaterialTheme {
                Surface {
                    Column {
                        TabRow(selectedTabIndex = selectedTabIndex) {
                            Tab(
                                selected = selectedTabIndex == 0,
                                onClick = { selectedTabIndex = 0 },
                                text = { Text(stringResource(R.string.voice_tab_title)) },
                            )
                            Tab(
                                selected = selectedTabIndex == 1,
                                onClick = { selectedTabIndex = 1 },
                                text = { Text(stringResource(R.string.media_tab_title)) },
                            )
                        }

                        when (selectedTabIndex) {
                            0 -> VoiceLabScreen(
                                uiState = voiceUiState.value,
                                onStartVoice = { onStartVoiceClicked() },
                                onStopVoice = voiceViewModel::stopVoice,
                                onReadAudioBytes = voiceViewModel::readLastAudioBytesAndLog,
                                onOpenOverlaySettings = voiceViewModel::openOverlaySettings,
                                onShowOverlay = voiceViewModel::showOverlay,
                                onClearLogs = voiceViewModel::clearLogs,
                            )

                            else -> MediaAnalysisScreen(
                                uiState = mediaUiState.value,
                                onPromptChange = mediaViewModel::updatePrompt,
                                onOpenCamera = ::onOpenCameraClicked,
                                onCloseCamera = mediaViewModel::closeCamera,
                                onCapturePhoto = mediaViewModel::capturePhotoAndLog,
                                onStartVideo = ::onStartVideoClicked,
                                onStopVideo = mediaViewModel::stopVideoRecordingAndLog,
                                onReadVideoBytes = mediaViewModel::readLastVideoBytesAndLog,
                                onStartPromptListening = ::onStartPromptListeningClicked,
                                onStopPromptListening = mediaViewModel::stopPromptListening,
                                onAnalyzePhoto = mediaViewModel::analyzeLastPhotoWithGemini,
                                onAnalyzeVideo = mediaViewModel::analyzeLastVideoWithGemini,
                                onSpeakResponse = mediaViewModel::speakLastAnalysis,
                                onStopSpeaking = mediaViewModel::stopSpeaking,
                                onClearLogs = mediaViewModel::clearLogs,
                            )
                        }
                    }
                }
            }
        }
    }

    override fun onResume() {
        super.onResume()
        voiceViewModel.refreshOverlayPermission()
        voiceViewModel.dismissInfoMessage()
        mediaViewModel.dismissInfoMessage()
    }

    override fun onDestroy() {
        mediaViewModel.release()
        super.onDestroy()
    }

    private fun onStartVoiceClicked() {
        val hasPermission = ContextCompat.checkSelfPermission(
            this,
            Manifest.permission.RECORD_AUDIO,
        ) == PackageManager.PERMISSION_GRANTED

        if (hasPermission) {
            voiceViewModel.startVoice(audioPermissionGranted = true)
            return
        }

        pendingAudioAction = AudioPermissionAction.START_VOICE_SERVICE
        requestAudioPermission.launch(Manifest.permission.RECORD_AUDIO)
    }

    private fun onStartPromptListeningClicked() {
        val hasPermission = ContextCompat.checkSelfPermission(
            this,
            Manifest.permission.RECORD_AUDIO,
        ) == PackageManager.PERMISSION_GRANTED

        if (hasPermission) {
            mediaViewModel.startPromptListening(audioPermissionGranted = true)
            return
        }

        pendingAudioAction = AudioPermissionAction.START_PROMPT_STT
        requestAudioPermission.launch(Manifest.permission.RECORD_AUDIO)
    }

    private fun onOpenCameraClicked(lifecycleOwner: LifecycleOwner, previewView: PreviewView) {
        val hasPermission = ContextCompat.checkSelfPermission(
            this,
            Manifest.permission.CAMERA,
        ) == PackageManager.PERMISSION_GRANTED

        if (hasPermission) {
            mediaViewModel.openCamera(lifecycleOwner, previewView, hasCameraPermission = true)
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
        mediaViewModel.startVideoRecording(hasPermission)
    }

    private enum class AudioPermissionAction {
        START_VOICE_SERVICE,
        START_PROMPT_STT,
    }
}

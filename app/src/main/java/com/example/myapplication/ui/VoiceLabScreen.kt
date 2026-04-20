package com.example.myapplication.ui

import androidx.camera.view.PreviewView
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.example.myapplication.R
import com.example.myapplication.presentation.MainUiState

@Composable
fun VoiceLabScreen(
    uiState: MainUiState,
    onStartVoice: () -> Unit,
    onStopVoice: () -> Unit,
    onReadAudioBytes: () -> Unit,
    onOpenOverlaySettings: () -> Unit,
    onShowOverlay: () -> Unit,
    onOpenCamera: (androidx.lifecycle.LifecycleOwner, PreviewView) -> Unit,
    onCloseCamera: () -> Unit,
    onCapturePhoto: () -> Unit,
    onStartVideo: () -> Unit,
    onStopVideo: () -> Unit,
    onReadVideoBytes: () -> Unit,
    onAnalyzeLastPhoto: () -> Unit,
    onAnalyzeLastVideo: () -> Unit,
    onClearLogs: () -> Unit,
) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val previewView = remember {
        PreviewView(context).apply {
            scaleType = PreviewView.ScaleType.FILL_CENTER
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(20.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        Text(
            text = stringResource(R.string.voice_lab_title),
            style = MaterialTheme.typography.headlineSmall,
            fontWeight = FontWeight.Bold,
        )

        Card(
            colors = CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.surfaceContainerHighest,
            ),
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Text(
                    text = if (uiState.isRecording) {
                        stringResource(R.string.voice_recording_status)
                    } else {
                        stringResource(R.string.voice_stopped_status)
                    },
                    style = MaterialTheme.typography.titleMedium,
                )

                val normalizedLevel = (uiState.voiceLevel / 100f).coerceIn(0f, 1f)
                LinearProgressIndicator(
                    progress = { normalizedLevel },
                    modifier = Modifier.fillMaxWidth(),
                )

                Text(text = stringResource(R.string.voice_level, uiState.voiceLevel))
                Text(text = stringResource(R.string.last_audio_path, uiState.lastAudioPath ?: "-"))
                Text(text = stringResource(R.string.last_audio_size, uiState.lastAudioSizeBytes ?: 0L))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    Button(
                        modifier = Modifier.weight(1f),
                        onClick = onStartVoice,
                    ) {
                        Text(stringResource(R.string.voice_start))
                    }
                    Button(
                        modifier = Modifier.weight(1f),
                        onClick = onStopVoice,
                    ) {
                        Text(stringResource(R.string.voice_stop))
                    }
                }

                Button(
                    modifier = Modifier.fillMaxWidth(),
                    onClick = onReadAudioBytes,
                ) {
                    Text(stringResource(R.string.voice_read_audio_bytes))
                }
            }
        }

        Card(
            colors = CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.surfaceContainerHighest,
            ),
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Text(
                    text = if (uiState.overlayPermissionGranted) {
                        stringResource(R.string.overlay_permission_granted)
                    } else {
                        stringResource(R.string.overlay_permission_missing)
                    },
                    style = MaterialTheme.typography.titleMedium,
                )

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    Button(
                        modifier = Modifier.weight(1f),
                        onClick = onOpenOverlaySettings,
                    ) {
                        Text(stringResource(R.string.overlay_open_permission))
                    }
                    Button(
                        modifier = Modifier.weight(1f),
                        onClick = onShowOverlay,
                    ) {
                        Text(stringResource(R.string.overlay_show))
                    }
                }
            }
        }

        Card(
            colors = CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.surfaceContainerHighest,
            ),
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Text(
                    text = stringResource(
                        if (uiState.isCameraOpen) R.string.camera_status_open else R.string.camera_status_closed,
                    ),
                    style = MaterialTheme.typography.titleMedium,
                )
                Text(
                    text = stringResource(
                        if (uiState.isCameraRecording) R.string.camera_video_recording else R.string.camera_video_idle,
                    ),
                )
                Text(text = stringResource(R.string.last_video_path, uiState.lastVideoPath ?: "-"))
                Text(text = stringResource(R.string.last_video_size, uiState.lastVideoSizeBytes ?: 0L))

                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(220.dp)
                        .background(MaterialTheme.colorScheme.surfaceVariant),
                ) {
                    AndroidView(
                        modifier = Modifier.fillMaxSize(),
                        factory = { previewView },
                    )
                }

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    Button(
                        modifier = Modifier.weight(1f),
                        onClick = { onOpenCamera(lifecycleOwner, previewView) },
                    ) {
                        Text(stringResource(R.string.camera_open))
                    }
                    Button(
                        modifier = Modifier.weight(1f),
                        onClick = onCloseCamera,
                    ) {
                        Text(stringResource(R.string.camera_close))
                    }
                }

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    Button(
                        modifier = Modifier.weight(1f),
                        onClick = onCapturePhoto,
                    ) {
                        Text(stringResource(R.string.camera_capture_photo))
                    }
                    Button(
                        modifier = Modifier.weight(1f),
                        onClick = onAnalyzeLastVideo,
                    ) {
                        Text(stringResource(R.string.gemini_analyze_last_video))
                    }
                }

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    Button(
                        modifier = Modifier.weight(1f),
                        onClick = onReadVideoBytes,
                    ) {
                        Text(stringResource(R.string.camera_read_video_bytes))
                    }
                    Button(
                        modifier = Modifier.weight(1f),
                        onClick = onAnalyzeLastPhoto,
                    ) {
                        Text(stringResource(R.string.gemini_analyze_last_photo))
                    }
                }

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    Button(
                        modifier = Modifier.weight(1f),
                        onClick = onStartVideo,
                    ) {
                        Text(stringResource(R.string.camera_start_video))
                    }
                    Button(
                        modifier = Modifier.weight(1f),
                        onClick = onStopVideo,
                    ) {
                        Text(stringResource(R.string.camera_stop_video))
                    }
                }
            }
        }

        Card(
            colors = CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.surfaceContainerHighest,
            ),
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Text(
                    text = stringResource(R.string.gemini_result_title),
                    style = MaterialTheme.typography.titleMedium,
                )
                Text(
                    text = if (uiState.isAnalyzingScene) {
                        stringResource(R.string.gemini_status_analyzing)
                    } else {
                        stringResource(R.string.gemini_status_idle)
                    },
                )
                Text(
                    text = uiState.lastSceneAnalysis ?: stringResource(R.string.gemini_no_result),
                )
            }
        }

        Card(
            colors = CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.surfaceContainerHighest,
            ),
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        text = stringResource(R.string.logs_title),
                        style = MaterialTheme.typography.titleMedium,
                    )
                    Button(onClick = onClearLogs) {
                        Text(stringResource(R.string.logs_clear))
                    }
                }

                if (uiState.logs.isEmpty()) {
                    Text(text = stringResource(R.string.logs_empty))
                } else {
                    uiState.logs.forEach { logLine ->
                        Text(text = logLine)
                    }
                }
            }
        }

        uiState.infoMessage?.let { message ->
            Text(
                text = message,
                color = MaterialTheme.colorScheme.error,
                modifier = Modifier.align(Alignment.CenterHorizontally),
            )
        }
    }
}

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
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
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
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.example.myapplication.R
import com.example.myapplication.presentation.MediaAnalysisUiState

@Composable
fun MediaAnalysisScreen(
    uiState: MediaAnalysisUiState,
    onPromptChange: (String) -> Unit,
    onOpenCamera: (LifecycleOwner, PreviewView) -> Unit,
    onCloseCamera: () -> Unit,
    onCapturePhoto: () -> Unit,
    onStartVideo: () -> Unit,
    onStopVideo: () -> Unit,
    onReadVideoBytes: () -> Unit,
    onStartPromptListening: () -> Unit,
    onStopPromptListening: () -> Unit,
    onAnalyzePhoto: () -> Unit,
    onAnalyzeVideo: () -> Unit,
    onSpeakResponse: () -> Unit,
    onStopSpeaking: () -> Unit,
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
            text = stringResource(R.string.media_tab_title),
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
                    text = stringResource(R.string.prompt_section_title),
                    style = MaterialTheme.typography.titleMedium,
                )

                OutlinedTextField(
                    value = uiState.prompt,
                    onValueChange = onPromptChange,
                    modifier = Modifier.fillMaxWidth(),
                    label = { Text(stringResource(R.string.prompt_field_label)) },
                    placeholder = { Text(stringResource(R.string.prompt_field_placeholder)) },
                )

                if (uiState.promptPartialText.isNotBlank()) {
                    Text(
                        text = stringResource(R.string.prompt_partial_text, uiState.promptPartialText),
                        style = MaterialTheme.typography.bodySmall,
                    )
                }

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    Button(
                        modifier = Modifier.weight(1f),
                        onClick = onStartPromptListening,
                    ) {
                        Text(
                            text = if (uiState.isPromptListening) {
                                stringResource(R.string.stt_listening)
                            } else {
                                stringResource(R.string.stt_start)
                            },
                        )
                    }
                    Button(
                        modifier = Modifier.weight(1f),
                        onClick = onStopPromptListening,
                    ) {
                        Text(stringResource(R.string.stt_stop))
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
                Text(text = stringResource(R.string.last_photo_size, uiState.lastPhotoSizeBytes ?: 0))
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
                        onClick = onReadVideoBytes,
                    ) {
                        Text(stringResource(R.string.camera_read_video_bytes))
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

                Text(text = uiState.lastSceneAnalysis ?: stringResource(R.string.gemini_no_result))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    Button(
                        modifier = Modifier.weight(1f),
                        onClick = onAnalyzePhoto,
                    ) {
                        Text(stringResource(R.string.gemini_analyze_last_photo))
                    }
                    Button(
                        modifier = Modifier.weight(1f),
                        onClick = onAnalyzeVideo,
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
                        onClick = onSpeakResponse,
                    ) {
                        Text(
                            text = if (uiState.isTextToSpeechSpeaking) {
                                stringResource(R.string.tts_speaking)
                            } else {
                                stringResource(R.string.tts_speak_response)
                            },
                        )
                    }
                    Button(
                        modifier = Modifier.weight(1f),
                        onClick = onStopSpeaking,
                    ) {
                        Text(stringResource(R.string.tts_stop))
                    }
                }

                Text(
                    text = if (uiState.isTextToSpeechReady) {
                        stringResource(R.string.tts_ready)
                    } else {
                        stringResource(R.string.tts_not_ready)
                    },
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
                    uiState.logs.forEach { line ->
                        Text(text = line)
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

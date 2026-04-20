package com.example.myapplication.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
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
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
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
    onStartAppCommandListening: () -> Unit,
    onStopAppCommandListening: () -> Unit,
    onClearLogs: () -> Unit,
) {
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
                    text = stringResource(R.string.app_command_title),
                    style = MaterialTheme.typography.titleMedium,
                )
                Text(
                    text = if (uiState.isAppCommandListening) {
                        stringResource(R.string.app_command_listening)
                    } else {
                        stringResource(R.string.app_command_idle)
                    },
                )

                if (uiState.appCommandPartialText.isNotBlank()) {
                    Text(
                        text = stringResource(R.string.app_command_partial, uiState.appCommandPartialText),
                    )
                }

                if (uiState.appCommandLastText.isNotBlank()) {
                    Text(
                        text = stringResource(R.string.app_command_last, uiState.appCommandLastText),
                    )
                }

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    Button(
                        modifier = Modifier.weight(1f),
                        onClick = onStartAppCommandListening,
                    ) {
                        Text(stringResource(R.string.app_command_start))
                    }
                    Button(
                        modifier = Modifier.weight(1f),
                        onClick = onStopAppCommandListening,
                    ) {
                        Text(stringResource(R.string.app_command_stop))
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

package com.example.myapplication.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
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
    onOpenOverlaySettings: () -> Unit,
    onShowOverlay: () -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
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

        uiState.infoMessage?.let { message ->
            Text(
                text = message,
                color = MaterialTheme.colorScheme.error,
                modifier = Modifier.align(Alignment.CenterHorizontally),
            )
        }
    }
}

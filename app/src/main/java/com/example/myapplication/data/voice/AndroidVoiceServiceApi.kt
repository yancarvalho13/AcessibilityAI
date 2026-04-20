package com.example.myapplication.data.voice

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import androidx.core.content.ContextCompat
import com.example.myapplication.VoiceCommandService
import com.example.myapplication.domain.voice.VoiceServiceApi
import com.example.myapplication.domain.voice.VoiceRecordingRef
import com.example.myapplication.domain.voice.VoiceServiceState
import com.example.myapplication.voice.VoiceServiceContract
import java.io.File
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

class AndroidVoiceServiceApi(
    private val appContext: Context,
) : VoiceServiceApi {
    private val _state = MutableStateFlow(VoiceServiceState())
    override val state: StateFlow<VoiceServiceState> = _state.asStateFlow()

    private val receiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            when (intent?.action) {
                VoiceServiceContract.ACTION_VOICE_LEVEL -> {
                    _state.value = _state.value.copy(
                        isRecording = intent.getBooleanExtra(VoiceServiceContract.EXTRA_IS_RECORDING, false),
                        level = intent.getIntExtra(VoiceServiceContract.EXTRA_LEVEL, 0),
                    )
                }

                VoiceServiceContract.ACTION_RECORDING_SAVED -> {
                    val filePath = intent.getStringExtra(VoiceServiceContract.EXTRA_FILE_PATH) ?: return
                    val recordingRef = VoiceRecordingRef(
                        filePath = filePath,
                        durationMs = intent.getLongExtra(VoiceServiceContract.EXTRA_DURATION_MS, 0L),
                        sizeBytes = intent.getLongExtra(VoiceServiceContract.EXTRA_SIZE_BYTES, 0L),
                        sampleRate = intent.getIntExtra(VoiceServiceContract.EXTRA_SAMPLE_RATE, 16_000),
                        channelCount = intent.getIntExtra(VoiceServiceContract.EXTRA_CHANNEL_COUNT, 1),
                    )
                    _state.value = _state.value.copy(lastRecording = recordingRef)
                }
            }
        }
    }

    init {
        ContextCompat.registerReceiver(
            appContext,
            receiver,
            IntentFilter().apply {
                addAction(VoiceServiceContract.ACTION_VOICE_LEVEL)
                addAction(VoiceServiceContract.ACTION_RECORDING_SAVED)
            },
            ContextCompat.RECEIVER_NOT_EXPORTED,
        )
    }

    override fun startRecording() {
        val serviceIntent = Intent(appContext, VoiceCommandService::class.java)
            .setAction(VoiceServiceContract.ACTION_START_RECORDING)
        ContextCompat.startForegroundService(appContext, serviceIntent)
    }

    override fun stopRecording() {
        val stopIntent = Intent(appContext, VoiceCommandService::class.java)
            .setAction(VoiceServiceContract.ACTION_STOP_RECORDING)
        appContext.startService(stopIntent)
    }

    override suspend fun getLastRecordingBytes(): ByteArray? {
        val recording = _state.value.lastRecording ?: return null
        val file = File(recording.filePath)
        if (!file.exists()) {
            return null
        }
        return file.readBytes()
    }
}

package com.example.myapplication.voice

object VoiceServiceContract {
    const val ACTION_START_RECORDING = "com.example.myapplication.action.START_RECORDING"
    const val ACTION_STOP_RECORDING = "com.example.myapplication.action.STOP_RECORDING"
    const val ACTION_VOICE_LEVEL = "com.example.myapplication.action.VOICE_LEVEL"
    const val ACTION_RECORDING_SAVED = "com.example.myapplication.action.RECORDING_SAVED"

    const val EXTRA_LEVEL = "extra_level"
    const val EXTRA_IS_RECORDING = "extra_is_recording"
    const val EXTRA_FILE_PATH = "extra_file_path"
    const val EXTRA_DURATION_MS = "extra_duration_ms"
    const val EXTRA_SIZE_BYTES = "extra_size_bytes"
    const val EXTRA_SAMPLE_RATE = "extra_sample_rate"
    const val EXTRA_CHANNEL_COUNT = "extra_channel_count"
}

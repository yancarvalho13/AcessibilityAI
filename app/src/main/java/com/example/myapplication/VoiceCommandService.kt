package com.example.myapplication

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Intent
import android.media.AudioFormat
import android.media.AudioRecord
import android.media.MediaRecorder
import android.os.Build
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import com.example.myapplication.voice.VoiceServiceContract
import java.io.File
import java.io.FileOutputStream
import java.io.RandomAccessFile
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlin.math.abs

class VoiceCommandService : Service() {
    private var audioRecord: AudioRecord? = null
    private var readBuffer: ShortArray? = null
    private val mainHandler = Handler(Looper.getMainLooper())
    private var levelTask: Runnable? = null

    private var currentOutputFile: File? = null
    private var currentOutputStream: FileOutputStream? = null
    private var currentAudioBytes: Long = 0
    private var recordingStartedAtMs: Long = 0L

    override fun onBind(p0: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        startForeground(1, createNotification())

        when (intent?.action) {
            VoiceServiceContract.ACTION_STOP_RECORDING -> stopSelf()
            else -> startRecordingIfNeeded()
        }

        return START_STICKY
    }

    private fun createNotification(): Notification {
        val channel = NotificationChannel(
            CHANNEL_ID,
            "Voice Service",
            NotificationManager.IMPORTANCE_LOW,
        )

        val manager = getSystemService(NotificationManager::class.java)
        manager.createNotificationChannel(channel)

        val showOverlayIntent = Intent(this, OverlayService::class.java)
        val overlayPendingIntent = PendingIntent.getService(
            this,
            0,
            showOverlayIntent,
            PendingIntent.FLAG_IMMUTABLE,
        )

        val stopIntent = Intent(this, VoiceCommandService::class.java)
            .setAction(VoiceServiceContract.ACTION_STOP_RECORDING)
        val stopPendingIntent = PendingIntent.getService(
            this,
            1,
            stopIntent,
            PendingIntent.FLAG_IMMUTABLE,
        )

        return Notification.Builder(this, CHANNEL_ID)
            .setContentTitle("Teste de voz ativo")
            .setContentText("Capturando microfone para validacao")
            .setSmallIcon(android.R.drawable.ic_btn_speak_now)
            .addAction(android.R.drawable.ic_menu_view, "Overlay", overlayPendingIntent)
            .addAction(android.R.drawable.ic_media_pause, "Parar", stopPendingIntent)
            .build()
    }

    private fun startRecordingIfNeeded() {
        if (audioRecord != null) {
            return
        }

        val minBufferSize = AudioRecord.getMinBufferSize(
            SAMPLE_RATE,
            AudioFormat.CHANNEL_IN_MONO,
            AudioFormat.ENCODING_PCM_16BIT,
        )

        if (minBufferSize <= 0) {
            return
        }

        val audioSource = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            MediaRecorder.AudioSource.VOICE_RECOGNITION
        } else {
            MediaRecorder.AudioSource.MIC
        }

        val record = AudioRecord(
            audioSource,
            SAMPLE_RATE,
            AudioFormat.CHANNEL_IN_MONO,
            AudioFormat.ENCODING_PCM_16BIT,
            minBufferSize,
        )

        if (record.state != AudioRecord.STATE_INITIALIZED) {
            record.release()
            return
        }

        prepareOutputWavFile()

        record.startRecording()
        audioRecord = record
        readBuffer = ShortArray(minBufferSize / 2)
        recordingStartedAtMs = System.currentTimeMillis()
        scheduleLevelBroadcast()
    }

    private fun prepareOutputWavFile() {
        val dir = File(filesDir, RECORDINGS_DIR_NAME)
        if (!dir.exists()) {
            dir.mkdirs()
        }

        val fileName = "voice_${timestampNow()}.wav"
        val outputFile = File(dir, fileName)
        val outputStream = FileOutputStream(outputFile)

        outputStream.write(ByteArray(WAV_HEADER_SIZE))
        outputStream.flush()

        currentOutputFile = outputFile
        currentOutputStream = outputStream
        currentAudioBytes = 0L
    }

    private fun scheduleLevelBroadcast() {
        levelTask?.let { mainHandler.removeCallbacks(it) }

        val task = object : Runnable {
            override fun run() {
                val record = audioRecord
                val shortBuffer = readBuffer
                if (record == null || shortBuffer == null) {
                    sendLevelBroadcast(0, false)
                    return
                }

                val read = record.read(shortBuffer, 0, shortBuffer.size)
                if (read <= 0) {
                    mainHandler.postDelayed(this, LEVEL_UPDATE_INTERVAL_MS)
                    return
                }

                appendPcmToWav(shortBuffer, read)

                var peak = 0
                for (i in 0 until read) {
                    val value = abs(shortBuffer[i].toInt())
                    if (value > peak) {
                        peak = value
                    }
                }

                val normalized = (peak * 100) / Short.MAX_VALUE
                sendLevelBroadcast(normalized.coerceIn(0, 100), true)
                mainHandler.postDelayed(this, LEVEL_UPDATE_INTERVAL_MS)
            }
        }

        levelTask = task
        mainHandler.post(task)
    }

    private fun appendPcmToWav(shortBuffer: ShortArray, samplesRead: Int) {
        val outputStream = currentOutputStream ?: return
        val bytes = ByteArray(samplesRead * BYTES_PER_SAMPLE)
        var index = 0
        for (i in 0 until samplesRead) {
            val sample = shortBuffer[i].toInt()
            bytes[index] = (sample and 0xFF).toByte()
            bytes[index + 1] = ((sample shr 8) and 0xFF).toByte()
            index += BYTES_PER_SAMPLE
        }

        outputStream.write(bytes)
        currentAudioBytes += bytes.size
    }

    private fun sendLevelBroadcast(level: Int, isRecording: Boolean) {
        val intent = Intent(VoiceServiceContract.ACTION_VOICE_LEVEL)
            .setPackage(packageName)
            .putExtra(VoiceServiceContract.EXTRA_LEVEL, level)
            .putExtra(VoiceServiceContract.EXTRA_IS_RECORDING, isRecording)
        sendBroadcast(intent)
    }

    private fun finalizeOutputFileAndBroadcast() {
        val outputStream = currentOutputStream
        val outputFile = currentOutputFile

        currentOutputStream = null
        currentOutputFile = null

        outputStream?.flush()
        outputStream?.close()

        if (outputFile == null || !outputFile.exists()) {
            return
        }

        val totalAudioBytes = currentAudioBytes
        if (totalAudioBytes <= 0) {
            outputFile.delete()
            return
        }

        writeWavHeader(
            outputFile = outputFile,
            totalAudioBytes = totalAudioBytes,
            sampleRate = SAMPLE_RATE,
            channelCount = CHANNEL_COUNT,
            bitsPerSample = BITS_PER_SAMPLE,
        )

        val durationMs = (System.currentTimeMillis() - recordingStartedAtMs).coerceAtLeast(0L)
        val savedIntent = Intent(VoiceServiceContract.ACTION_RECORDING_SAVED)
            .setPackage(packageName)
            .putExtra(VoiceServiceContract.EXTRA_FILE_PATH, outputFile.absolutePath)
            .putExtra(VoiceServiceContract.EXTRA_DURATION_MS, durationMs)
            .putExtra(VoiceServiceContract.EXTRA_SIZE_BYTES, outputFile.length())
            .putExtra(VoiceServiceContract.EXTRA_SAMPLE_RATE, SAMPLE_RATE)
            .putExtra(VoiceServiceContract.EXTRA_CHANNEL_COUNT, CHANNEL_COUNT)
        sendBroadcast(savedIntent)

        currentAudioBytes = 0L
        recordingStartedAtMs = 0L
    }

    override fun onDestroy() {
        levelTask?.let { mainHandler.removeCallbacks(it) }
        levelTask = null

        audioRecord?.run {
            try {
                stop()
            } catch (_: IllegalStateException) {
            }
            release()
        }
        audioRecord = null
        readBuffer = null

        finalizeOutputFileAndBroadcast()
        sendLevelBroadcast(0, false)
        super.onDestroy()
    }

    private fun timestampNow(): String {
        return SimpleDateFormat("yyyyMMdd_HHmmss", Locale.US).format(Date())
    }

    companion object {
        private const val CHANNEL_ID = "voice_service"
        private const val RECORDINGS_DIR_NAME = "voice_recordings"
        private const val WAV_HEADER_SIZE = 44
        private const val SAMPLE_RATE = 16_000
        private const val CHANNEL_COUNT = 1
        private const val BITS_PER_SAMPLE = 16
        private const val BYTES_PER_SAMPLE = 2
        private const val LEVEL_UPDATE_INTERVAL_MS = 300L

        private fun writeWavHeader(
            outputFile: File,
            totalAudioBytes: Long,
            sampleRate: Int,
            channelCount: Int,
            bitsPerSample: Int,
        ) {
            val totalDataLen = totalAudioBytes + 36
            val byteRate = sampleRate * channelCount * bitsPerSample / 8

            val header = ByteArray(WAV_HEADER_SIZE)
            header[0] = 'R'.code.toByte()
            header[1] = 'I'.code.toByte()
            header[2] = 'F'.code.toByte()
            header[3] = 'F'.code.toByte()

            writeIntLE(header, 4, totalDataLen.toInt())

            header[8] = 'W'.code.toByte()
            header[9] = 'A'.code.toByte()
            header[10] = 'V'.code.toByte()
            header[11] = 'E'.code.toByte()

            header[12] = 'f'.code.toByte()
            header[13] = 'm'.code.toByte()
            header[14] = 't'.code.toByte()
            header[15] = ' '.code.toByte()

            writeIntLE(header, 16, 16)
            writeShortLE(header, 20, 1)
            writeShortLE(header, 22, channelCount)
            writeIntLE(header, 24, sampleRate)
            writeIntLE(header, 28, byteRate)
            writeShortLE(header, 32, channelCount * bitsPerSample / 8)
            writeShortLE(header, 34, bitsPerSample)

            header[36] = 'd'.code.toByte()
            header[37] = 'a'.code.toByte()
            header[38] = 't'.code.toByte()
            header[39] = 'a'.code.toByte()
            writeIntLE(header, 40, totalAudioBytes.toInt())

            RandomAccessFile(outputFile, "rw").use { raf ->
                raf.seek(0)
                raf.write(header)
            }
        }

        private fun writeIntLE(target: ByteArray, offset: Int, value: Int) {
            target[offset] = (value and 0xFF).toByte()
            target[offset + 1] = ((value shr 8) and 0xFF).toByte()
            target[offset + 2] = ((value shr 16) and 0xFF).toByte()
            target[offset + 3] = ((value shr 24) and 0xFF).toByte()
        }

        private fun writeShortLE(target: ByteArray, offset: Int, value: Int) {
            target[offset] = (value and 0xFF).toByte()
            target[offset + 1] = ((value shr 8) and 0xFF).toByte()
        }
    }
}

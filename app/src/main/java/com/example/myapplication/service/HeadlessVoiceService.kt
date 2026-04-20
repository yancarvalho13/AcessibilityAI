package com.example.myapplication.service

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Intent
import android.os.Handler
import android.os.Looper
import androidx.core.app.NotificationCompat
import com.example.myapplication.R
import com.example.myapplication.data.analysis.GeminiSceneAnalysisApi
import com.example.myapplication.data.app.AndroidAppLauncherApi
import com.example.myapplication.data.speech.AndroidSpeechToTextApi
import com.example.myapplication.data.speech.AndroidTextToSpeechApi
import com.example.myapplication.domain.session.VoiceSessionAction
import com.example.myapplication.domain.session.VoiceSessionEngine
import androidx.lifecycle.LifecycleService
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch

class HeadlessVoiceService : LifecycleService() {
    private val serviceJob: Job = SupervisorJob()
    private val serviceScope = CoroutineScope(Dispatchers.Main + serviceJob)
    private val timeoutHandler = Handler(Looper.getMainLooper())

    private val speechToTextApi by lazy { AndroidSpeechToTextApi(applicationContext) }
    private val textToSpeechApi by lazy { AndroidTextToSpeechApi(applicationContext) }
    private val appLauncherApi by lazy { AndroidAppLauncherApi(applicationContext) }
    private val sceneAnalysisApi by lazy { GeminiSceneAnalysisApi() }
    private val cameraController by lazy { HeadlessCameraController(applicationContext, this) }
    private val commandExecutor by lazy {
        HeadlessCommandExecutor(
            appLauncherApi = appLauncherApi,
            cameraController = cameraController,
            sceneAnalysisApi = sceneAnalysisApi,
        )
    }
    private val engine = VoiceSessionEngine()

    private var timeoutRunnable: Runnable? = null
    private var lastHandledFinalText: String = ""

    override fun onCreate() {
        super.onCreate()
        startForeground(NOTIFICATION_ID, buildNotification())

        serviceScope.launch {
            speechToTextApi.state.collectLatest { sttState ->
                val finalText = sttState.finalText.trim()
                if (finalText.isNotEmpty() && finalText != lastHandledFinalText) {
                    lastHandledFinalText = finalText
                    processActions(engine.onUserInput(finalText))
                }

                if (sttState.errorMessage != null) {
                    processActions(listOf(VoiceSessionAction.Speak("Erro de escuta: ${sttState.errorMessage}")))
                }
            }
        }
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val action = intent?.action ?: ACTION_START_SESSION
        if (action == ACTION_START_SESSION) {
            processActions(engine.start())
        }
        return START_NOT_STICKY
    }

    override fun onDestroy() {
        clearTimeout()
        speechToTextApi.release()
        textToSpeechApi.release()
        cameraController.release()
        serviceScope.cancel()
        super.onDestroy()
    }

    private fun processActions(actions: List<VoiceSessionAction>) {
        serviceScope.launch {
            for (action in actions) {
                when (action) {
                    is VoiceSessionAction.Speak -> {
                        textToSpeechApi.speak(action.text)
                    }

                    VoiceSessionAction.StartListening -> {
                        speechToTextApi.startListening()
                    }

                    VoiceSessionAction.StopListening -> {
                        speechToTextApi.stopListening()
                    }

                    VoiceSessionAction.ResetTimeout -> {
                        resetTimeout()
                    }

                    is VoiceSessionAction.ExecuteCommand -> {
                        val execution = commandExecutor.execute(action.intent, action.rawCommand)
                        textToSpeechApi.speak(execution.feedbackText)
                    }

                    VoiceSessionAction.StopSession -> {
                        stopSelf()
                    }
                }
            }
        }
    }

    private fun resetTimeout() {
        clearTimeout()
        val runnable = Runnable {
            processActions(engine.onTimeout())
        }
        timeoutRunnable = runnable
        timeoutHandler.postDelayed(runnable, SESSION_TIMEOUT_MS)
    }

    private fun clearTimeout() {
        timeoutRunnable?.let { timeoutHandler.removeCallbacks(it) }
        timeoutRunnable = null
    }

    private fun buildNotification(): Notification {
        val channel = NotificationChannel(
            CHANNEL_ID,
            "Headless Voice Session",
            NotificationManager.IMPORTANCE_LOW,
        )
        val manager = getSystemService(NotificationManager::class.java)
        manager.createNotificationChannel(channel)

        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(android.R.drawable.ic_btn_speak_now)
            .setContentTitle(getString(R.string.headless_notification_title))
            .setContentText(getString(R.string.headless_notification_text))
            .setOngoing(true)
            .build()
    }

    companion object {
        const val ACTION_START_SESSION = "com.example.myapplication.action.START_HEADLESS_SESSION"

        private const val CHANNEL_ID = "headless_voice_session"
        private const val NOTIFICATION_ID = 101
        private const val SESSION_TIMEOUT_MS = 30_000L
    }
}

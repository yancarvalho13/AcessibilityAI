package com.example.myapplication.service

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Intent
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
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
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

class HeadlessVoiceService : LifecycleService() {
    private val serviceJob: Job = SupervisorJob()
    private val serviceScope = CoroutineScope(Dispatchers.Main + serviceJob)
    private val timeoutHandler = Handler(Looper.getMainLooper())

    private val speechToTextApi by lazy { AndroidSpeechToTextApi(applicationContext) }
    private val textToSpeechApi by lazy { AndroidTextToSpeechApi(applicationContext) }
    private val appLauncherApi by lazy { AndroidAppLauncherApi(applicationContext) }
    private val sceneAnalysisApi by lazy { GeminiSceneAnalysisApi() }
    private val cameraController by lazy { HeadlessCameraController(applicationContext) }
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
    private var suppressSttUntilMs: Long = 0L
    private var pendingSuppressedCommand: String? = null
    private var pendingCommandRunnable: Runnable? = null
    private val actionMutex = Mutex()

    override fun onCreate() {
        super.onCreate()
        startForeground(NOTIFICATION_ID, buildNotification())

        serviceScope.launch {
            speechToTextApi.state.collectLatest { sttState ->
                val finalText = sttState.finalText.trim()
                if (finalText.isNotEmpty() && finalText != lastHandledFinalText) {
                    val now = SystemClock.elapsedRealtime()
                    if (now < suppressSttUntilMs || textToSpeechApi.state.value.isSpeaking) {
                        emitLog("Headless STT: ignorado durante fala do TTS ($finalText)")
                        pendingSuppressedCommand = finalText
                        schedulePendingCommandProcessing()
                        return@collectLatest
                    }

                    lastHandledFinalText = finalText
                    emitLog("Headless STT: comando reconhecido ($finalText)")
                    processActions(engine.onUserInput(finalText))
                }

                if (sttState.errorMessage != null) {
                    val now = SystemClock.elapsedRealtime()
                    if (now < suppressSttUntilMs || textToSpeechApi.state.value.isSpeaking) {
                        emitLog("Headless STT: erro ignorado durante fala (${sttState.errorMessage})")
                        return@collectLatest
                    }

                    if (isRecoverableSttError(sttState.errorMessage)) {
                        emitLog("Headless STT: erro recuperavel (${sttState.errorMessage}), reiniciando escuta")
                        startListeningWhenReady()
                        resetTimeout()
                        return@collectLatest
                    }

                    emitLog("Headless STT: erro (${sttState.errorMessage})")
                    processActions(listOf(VoiceSessionAction.Speak("Erro de escuta: ${sttState.errorMessage}")))
                }
            }
        }
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val action = intent?.action ?: ACTION_START_SESSION
        if (action == ACTION_START_SESSION) {
            emitLog("Headless sessao: iniciando step engine")
            processActions(engine.start())
        }
        return START_NOT_STICKY
    }

    override fun onDestroy() {
        clearTimeout()
        clearPendingCommandRunnable()
        speechToTextApi.release()
        textToSpeechApi.release()
        cameraController.release()
        emitLog("Headless sessao: encerrada")
        serviceScope.cancel()
        super.onDestroy()
    }

    private fun processActions(actions: List<VoiceSessionAction>) {
        serviceScope.launch {
            actionMutex.withLock {
                for (action in actions) {
                    runCatching {
                        when (action) {
                            is VoiceSessionAction.Speak -> {
                                emitLog("Headless action: Speak")
                                reserveSttSuppression(action.text)
                                textToSpeechApi.speak(action.text)
                            }

                            VoiceSessionAction.StartListening -> {
                                emitLog("Headless action: StartListening")
                                startListeningWhenReady()
                            }

                            VoiceSessionAction.StopListening -> {
                                emitLog("Headless action: StopListening")
                                speechToTextApi.stopListening()
                            }

                            VoiceSessionAction.ResetTimeout -> {
                                emitLog("Headless action: ResetTimeout")
                                resetTimeout()
                            }

                            is VoiceSessionAction.ExecuteCommand -> {
                                emitLog("Headless action: ExecuteCommand (${action.intent})")
                                val execution = commandExecutor.execute(action.intent, action.rawCommand)
                                execution.logMessage?.let { emitLog(it) }
                                reserveSttSuppression(execution.feedbackText)
                                textToSpeechApi.speak(execution.feedbackText)
                            }

                            VoiceSessionAction.StopSession -> {
                                emitLog("Headless action: StopSession")
                                stopSelf()
                            }
                        }
                    }.onFailure { error ->
                        emitLog("Headless erro: falha ao processar action (${error.message ?: "erro desconhecido"})")
                        textToSpeechApi.speak("Erro interno ao processar o comando")
                    }
                }
            }
        }
    }

    private fun resetTimeout() {
        clearTimeout()
        val runnable = Runnable {
            emitLog("Headless sessao: timeout de 30s")
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

    private fun emitLog(message: String) {
        sendBroadcast(
            Intent(ACTION_HEADLESS_LOG)
                .setPackage(packageName)
                .putExtra(EXTRA_LOG_MESSAGE, message),
        )
    }

    private fun reserveSttSuppression(spokenText: String) {
        val minMs = 700L
        val maxMs = 3_000L
        val estimatedMs = (spokenText.length * 30L).coerceIn(minMs, maxMs)
        val now = SystemClock.elapsedRealtime()
        suppressSttUntilMs = maxOf(suppressSttUntilMs, now + estimatedMs)
    }

    private fun schedulePendingCommandProcessing() {
        clearPendingCommandRunnable()
        val runnable = Runnable {
            val pending = pendingSuppressedCommand
            if (pending.isNullOrBlank()) {
                return@Runnable
            }

            val now = SystemClock.elapsedRealtime()
            if (now < suppressSttUntilMs || textToSpeechApi.state.value.isSpeaking) {
                schedulePendingCommandProcessing()
                return@Runnable
            }

            pendingSuppressedCommand = null
            if (pending != lastHandledFinalText) {
                lastHandledFinalText = pending
                emitLog("Headless STT: processando comando apos TTS ($pending)")
                processActions(engine.onUserInput(pending))
            }
        }

        pendingCommandRunnable = runnable
        val now = SystemClock.elapsedRealtime()
        val delayMs = (suppressSttUntilMs - now).coerceAtLeast(120L)
        timeoutHandler.postDelayed(runnable, delayMs)
    }

    private fun clearPendingCommandRunnable() {
        pendingCommandRunnable?.let { timeoutHandler.removeCallbacks(it) }
        pendingCommandRunnable = null
    }

    private suspend fun startListeningWhenReady() {
        repeat(MAX_LISTEN_WAIT_TICKS) {
            val now = SystemClock.elapsedRealtime()
            val isTtsSpeaking = textToSpeechApi.state.value.isSpeaking
            if (!isTtsSpeaking && now >= suppressSttUntilMs) {
                speechToTextApi.startListening()
                return
            }
            delay(LISTEN_WAIT_TICK_MS)
        }
        speechToTextApi.startListening()
    }

    private fun isRecoverableSttError(errorMessage: String): Boolean {
        return errorMessage == "Nenhuma fala reconhecida" ||
            errorMessage == "Tempo de fala esgotado"
    }

    companion object {
        const val ACTION_START_SESSION = "com.example.myapplication.action.START_HEADLESS_SESSION"
        const val ACTION_HEADLESS_LOG = "com.example.myapplication.action.HEADLESS_LOG"
        const val EXTRA_LOG_MESSAGE = "extra_log_message"

        private const val CHANNEL_ID = "headless_voice_session"
        private const val NOTIFICATION_ID = 101
        private const val SESSION_TIMEOUT_MS = 30_000L
        private const val LISTEN_WAIT_TICK_MS = 120L
        private const val MAX_LISTEN_WAIT_TICKS = 40
    }
}

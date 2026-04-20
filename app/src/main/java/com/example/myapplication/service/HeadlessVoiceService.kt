package com.example.myapplication.service

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Intent
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import androidx.core.app.NotificationCompat
import com.example.myapplication.R
import com.example.myapplication.app.MyApplication
import com.example.myapplication.domain.session.VoiceSessionAction
import com.example.myapplication.domain.session.VoiceSessionEngine
import com.example.myapplication.domain.text.CommandTextNormalizer
import androidx.lifecycle.LifecycleService
import kotlin.coroutines.cancellation.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.async
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

    private val serviceGraph by lazy { (application as MyApplication).serviceGraph }
    private val speechToTextApi by lazy { serviceGraph.speechToTextApi }
    private val textToSpeechApi by lazy { serviceGraph.textToSpeechApi }
    private val appLauncherApi by lazy { serviceGraph.appLauncherApi }
    private val sceneAnalysisApi by lazy { serviceGraph.sceneAnalysisApi }
    private val cameraController by lazy { serviceGraph.headlessCameraApi }
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
    private var timeoutToken: Long = 0L
    private val actionMutex = Mutex()
    private var activeActionJob: Job? = null
    private var currentExecutionJob: Job? = null

    override fun onCreate() {
        super.onCreate()
        startForeground(NOTIFICATION_ID, buildNotification())

        serviceScope.launch {
            speechToTextApi.state.collectLatest { sttState ->
                val finalText = sttState.finalText.trim()
                if (finalText.isNotEmpty() && finalText != lastHandledFinalText) {
                    if (isGlobalCancelCommand(finalText)) {
                        emitLog("Headless STT: cancelamento global reconhecido ($finalText)")
                        lastHandledFinalText = finalText
                        cancelCurrentFlowAndRestart()
                        return@collectLatest
                    }

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
        when (action) {
            ACTION_START_SESSION -> {
                emitLog("Headless sessao: iniciando step engine")
                processActions(engine.start())
            }

            ACTION_CANCEL_FLOW -> {
                emitLog("Headless sessao: cancelamento solicitado por intent")
                cancelCurrentFlowAndRestart()
            }
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
        activeActionJob = serviceScope.launch {
            actionMutex.withLock {
                for (action in actions) {
                    try {
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
                                clearTimeout()

                                emitLog("Headless action: ExecuteCommand (${action.intent})")
                                val execution = runCancelableCommand(action)
                                emitExecutionFeedback(execution)
                            }

                            VoiceSessionAction.StopSession -> {
                                emitLog("Headless action: StopSession")
                                stopSelf()
                            }
                        }
                    } catch (cancelled: CancellationException) {
                        emitLog("Headless action: cancelada")
                        throw cancelled
                    } catch (error: Throwable) {
                        emitLog("Headless erro: falha ao processar action (${error.message ?: "erro desconhecido"})")
                        textToSpeechApi.speak("Erro interno ao processar o comando")
                    }
                }
            }
        }
    }

    private fun resetTimeout(timeoutMs: Long = SESSION_TIMEOUT_MS) {
        clearTimeout()
        timeoutToken += 1L
        val token = timeoutToken
        val runnable = Runnable {
            if (token != timeoutToken) {
                return@Runnable
            }

            emitLog("Headless sessao: timeout de ${timeoutMs / 1000}s")
            processActions(engine.onTimeout())
        }
        timeoutRunnable = runnable
        timeoutHandler.postDelayed(runnable, timeoutMs)
    }

    private fun clearTimeout() {
        timeoutToken += 1L
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

        val cancelIntent = Intent(this, HeadlessVoiceService::class.java)
            .setAction(ACTION_CANCEL_FLOW)
        val cancelPendingIntent = PendingIntent.getService(
            this,
            201,
            cancelIntent,
            PendingIntent.FLAG_IMMUTABLE,
        )

        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(android.R.drawable.ic_btn_speak_now)
            .setContentTitle(getString(R.string.headless_notification_title))
            .setContentText(getString(R.string.headless_notification_text))
            .addAction(android.R.drawable.ic_menu_close_clear_cancel, "Cancelar fluxo", cancelPendingIntent)
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

    private fun cancelCurrentFlowAndRestart() {
        clearTimeout()
        clearPendingCommandRunnable()
        pendingSuppressedCommand = null
        suppressSttUntilMs = 0L
        lastHandledFinalText = ""
        currentExecutionJob?.cancel(CancellationException("fluxo cancelado"))
        currentExecutionJob = null
        activeActionJob?.cancel(CancellationException("fluxo cancelado"))
        activeActionJob = null
        speechToTextApi.cancelListening()
        textToSpeechApi.stop()
        processActions(engine.start())
    }

    private suspend fun runCancelableCommand(action: VoiceSessionAction.ExecuteCommand): HeadlessCommandExecution {
        val deferred = serviceScope.async {
            commandExecutor.execute(
                intent = action.intent,
                rawCommand = action.rawCommand,
                onProgress = { progress ->
                    emitExecutionFeedback(progress)
                },
            )
        }
        currentExecutionJob = deferred
        return try {
            deferred.await()
        } finally {
            if (currentExecutionJob == deferred) {
                currentExecutionJob = null
            }
        }
    }

    private fun emitExecutionFeedback(execution: HeadlessCommandExecution) {
        execution.logMessage?.let { emitLog(it) }
        reserveSttSuppression(execution.feedbackText)
        textToSpeechApi.speak(execution.feedbackText)
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

    private fun isGlobalCancelCommand(rawText: String): Boolean {
        val normalized = CommandTextNormalizer.normalize(rawText)

        return normalized.contains("cancelar") ||
            normalized.contains("cancela") ||
            normalized.contains("encerrar fluxo") ||
            normalized.contains("parar sessao")
    }

    companion object {
        const val ACTION_START_SESSION = "com.example.myapplication.action.START_HEADLESS_SESSION"
        const val ACTION_CANCEL_FLOW = "com.example.myapplication.action.CANCEL_HEADLESS_FLOW"
        const val ACTION_HEADLESS_LOG = "com.example.myapplication.action.HEADLESS_LOG"
        const val EXTRA_LOG_MESSAGE = "extra_log_message"

        private const val CHANNEL_ID = "headless_voice_session"
        private const val NOTIFICATION_ID = 101
        private const val SESSION_TIMEOUT_MS = 30_000L
        private const val LISTEN_WAIT_TICK_MS = 120L
        private const val MAX_LISTEN_WAIT_TICKS = 40
    }
}

package com.example.myapplication.data.app

import android.content.Context
import android.content.Intent
import com.example.myapplication.domain.app.AppLaunchResult
import com.example.myapplication.domain.app.AppLaunchStatus
import com.example.myapplication.domain.app.AppLauncherApi
import java.text.Normalizer
import java.util.Locale

class AndroidAppLauncherApi(
    private val appContext: Context,
) : AppLauncherApi {

    override fun openWhatsApp(): AppLaunchResult {
        return openApp(PACKAGE_WHATSAPP, TARGET_WHATSAPP, "")
    }

    override fun openYouTube(): AppLaunchResult {
        return openApp(PACKAGE_YOUTUBE, TARGET_YOUTUBE, "")
    }

    override fun openByVoiceCommand(command: String): AppLaunchResult {
        val normalized = normalize(command)
        if (normalized.isBlank()) {
            return AppLaunchResult(
                status = AppLaunchStatus.NoMatch,
                commandText = command,
            )
        }

        val matchedTargets = mutableListOf<Pair<String, Int>>()

        findFirstKeywordIndex(normalized, WHATSAPP_KEYWORDS)?.let { idx ->
            matchedTargets += TARGET_WHATSAPP to idx
        }

        findFirstKeywordIndex(normalized, YOUTUBE_KEYWORDS)?.let { idx ->
            matchedTargets += TARGET_YOUTUBE to idx
        }

        if (matchedTargets.isEmpty()) {
            return AppLaunchResult(
                status = AppLaunchStatus.NoMatch,
                commandText = command,
            )
        }

        val target = matchedTargets.minByOrNull { it.second }?.first
        return when (target) {
            TARGET_WHATSAPP -> openApp(PACKAGE_WHATSAPP, TARGET_WHATSAPP, command)
            TARGET_YOUTUBE -> openApp(PACKAGE_YOUTUBE, TARGET_YOUTUBE, command)
            else -> AppLaunchResult(
                status = AppLaunchStatus.NoMatch,
                commandText = command,
            )
        }
    }

    private fun openApp(packageName: String, targetApp: String, commandText: String): AppLaunchResult {
        val launchIntent = appContext.packageManager.getLaunchIntentForPackage(packageName)
            ?.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)

        if (launchIntent == null) {
            return AppLaunchResult(
                status = AppLaunchStatus.AppNotInstalled,
                targetApp = targetApp,
                commandText = commandText,
            )
        }

        appContext.startActivity(launchIntent)
        return AppLaunchResult(
            status = AppLaunchStatus.Opened,
            targetApp = targetApp,
            commandText = commandText,
        )
    }

    private fun findFirstKeywordIndex(text: String, keywords: List<String>): Int? {
        var bestIndex: Int? = null
        for (keyword in keywords) {
            val idx = text.indexOf(keyword)
            if (idx >= 0 && (bestIndex == null || idx < bestIndex)) {
                bestIndex = idx
            }
        }
        return bestIndex
    }

    private fun normalize(value: String): String {
        val lower = value.lowercase(Locale.ROOT)
        val noAccents = Normalizer
            .normalize(lower, Normalizer.Form.NFD)
            .replace("\\p{Mn}+".toRegex(), "")
        return noAccents.trim()
    }

    companion object {
        private const val PACKAGE_WHATSAPP = "com.whatsapp"
        private const val PACKAGE_YOUTUBE = "com.google.android.youtube"

        private const val TARGET_WHATSAPP = "WhatsApp"
        private const val TARGET_YOUTUBE = "YouTube"

        private val WHATSAPP_KEYWORDS = listOf("whatsapp", "whatsaap", "zap", "wpp")
        private val YOUTUBE_KEYWORDS = listOf("youtube", "you tube", "yt")
    }
}

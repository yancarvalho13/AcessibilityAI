package com.example.myapplication.domain.session

import java.text.Normalizer
import java.util.Locale

class VoiceCommandParser {
    fun parse(command: String): VoiceCommandIntent {
        val normalized = normalize(command)
        if (normalized.isBlank()) {
            return VoiceCommandIntent.Unknown
        }

        if (containsAny(normalized, STOP_KEYWORDS)) {
            return VoiceCommandIntent.StopSession
        }
        if (containsAny(normalized, WHATSAPP_KEYWORDS)) {
            return VoiceCommandIntent.OpenWhatsApp
        }
        if (containsAny(normalized, YOUTUBE_KEYWORDS)) {
            return VoiceCommandIntent.OpenYouTube
        }
        if (containsAny(normalized, CAMERA_KEYWORDS)) {
            return VoiceCommandIntent.OpenCamera
        }
        if (containsAny(normalized, TAKE_PHOTO_KEYWORDS)) {
            return VoiceCommandIntent.TakePhoto
        }
        if (containsAny(normalized, VIDEO_START_KEYWORDS)) {
            return VoiceCommandIntent.StartVideo
        }
        if (containsAny(normalized, VIDEO_STOP_KEYWORDS)) {
            return VoiceCommandIntent.StopVideo
        }
        if (containsAny(normalized, ANALYZE_PHOTO_KEYWORDS)) {
            return VoiceCommandIntent.AnalyzePhoto
        }
        if (containsAny(normalized, ANALYZE_VIDEO_KEYWORDS)) {
            return VoiceCommandIntent.AnalyzeVideo
        }

        return VoiceCommandIntent.Unknown
    }

    private fun containsAny(text: String, values: List<String>): Boolean {
        return values.any { keyword -> text.contains(keyword) }
    }

    private fun normalize(text: String): String {
        val lower = text.lowercase(Locale.ROOT)
        return Normalizer
            .normalize(lower, Normalizer.Form.NFD)
            .replace("\\p{Mn}+".toRegex(), "")
            .trim()
    }

    companion object {
        private val WHATSAPP_KEYWORDS = listOf("whatsapp", "whatsaap", "zap", "wpp")
        private val YOUTUBE_KEYWORDS = listOf("youtube", "you tube", "yt")
        private val CAMERA_KEYWORDS = listOf("camera", "abrir camera")
        private val TAKE_PHOTO_KEYWORDS = listOf("tirar foto", "tire foto", "capturar foto", "bater foto")
        private val VIDEO_START_KEYWORDS = listOf("gravar video", "iniciar video", "comecar video")
        private val VIDEO_STOP_KEYWORDS = listOf("parar video", "encerrar video")
        private val ANALYZE_PHOTO_KEYWORDS = listOf("analisar foto")
        private val ANALYZE_VIDEO_KEYWORDS = listOf("analisar video")
        private val STOP_KEYWORDS = listOf("parar", "cancelar", "encerrar")
    }
}

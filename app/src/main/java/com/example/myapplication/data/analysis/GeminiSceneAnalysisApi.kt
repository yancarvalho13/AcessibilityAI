package com.example.myapplication.data.analysis

import android.util.Base64
import com.example.myapplication.BuildConfig
import com.example.myapplication.domain.analysis.SceneAnalysisApi
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONObject

class GeminiSceneAnalysisApi(
    private val client: OkHttpClient = OkHttpClient(),
) : SceneAnalysisApi {

    override suspend fun analyzePhoto(photoBytes: ByteArray, prompt: String?): String {
        return analyze(
            bytes = photoBytes,
            mimeType = "image/jpeg",
            prompt = prompt?.trim().takeUnless { it.isNullOrEmpty() } ?: BuildConfig.GEMINI_PHOTO_PROMPT,
        )
    }

    override suspend fun analyzeVideo(videoBytes: ByteArray, prompt: String?): String {
        return analyze(
            bytes = videoBytes,
            mimeType = "video/mp4",
            prompt = prompt?.trim().takeUnless { it.isNullOrEmpty() } ?: BuildConfig.GEMINI_VIDEO_PROMPT,
        )
    }

    private suspend fun analyze(bytes: ByteArray, mimeType: String, prompt: String): String {
        return withContext(Dispatchers.IO) {
            val apiKey = BuildConfig.GEMINI_API_KEY
            if (apiKey.isBlank()) {
                throw IllegalStateException("GEMINI_API_KEY vazio. Configure no arquivo .env")
            }

            val encoded = Base64.encodeToString(bytes, Base64.NO_WRAP)
            val bodyJson = buildGenerateContentBody(prompt, mimeType, encoded)

            val preferredModel = BuildConfig.GEMINI_MODEL.ifBlank { DEFAULT_MODEL }
            val preferredVersion = BuildConfig.GEMINI_API_VERSION.ifBlank { DEFAULT_VERSION }

            val requestsToTry = listOf(
                preferredVersion to preferredModel,
                "v1beta" to preferredModel,
                "v1" to preferredModel,
                preferredVersion to DEFAULT_MODEL,
                "v1beta" to DEFAULT_MODEL,
            ).distinct()

            var lastError: String = ""

            for ((apiVersion, model) in requestsToTry) {
                val url = "https://generativelanguage.googleapis.com/$apiVersion/models/$model:generateContent?key=$apiKey"
                val request = Request.Builder()
                    .url(url)
                    .post(bodyJson.toRequestBody(JSON_MEDIA_TYPE))
                    .build()

                client.newCall(request).execute().use { response ->
                    val raw = response.body?.string().orEmpty()

                    if (response.isSuccessful) {
                        return@withContext extractText(raw)
                    }

                    lastError = "[$apiVersion/$model] HTTP ${response.code}: $raw"

                    if (response.code !in RETRYABLE_STATUS_CODES) {
                        throw IllegalStateException(lastError)
                    }
                }
            }

            throw IllegalStateException("Gemini falhou em todos os endpoints tentados. Ultimo erro: $lastError")
        }
    }

    private fun buildGenerateContentBody(prompt: String, mimeType: String, dataBase64: String): String {
        return JSONObject(
            mapOf(
                "contents" to JSONArray(
                    listOf(
                        JSONObject(
                            mapOf(
                                "parts" to JSONArray(
                                    listOf(
                                        JSONObject(mapOf("text" to prompt)),
                                        JSONObject(
                                            mapOf(
                                                "inline_data" to JSONObject(
                                                    mapOf(
                                                        "mime_type" to mimeType,
                                                        "data" to dataBase64,
                                                    ),
                                                ),
                                            ),
                                        ),
                                    ),
                                ),
                            ),
                        ),
                    ),
                ),
            ),
        ).toString()
    }

    private fun extractText(rawResponse: String): String {
        val json = JSONObject(rawResponse)
        val candidates = json.optJSONArray("candidates") ?: JSONArray()
        if (candidates.length() == 0) {
            return "Sem resposta do Gemini"
        }

        val parts = candidates
            .optJSONObject(0)
            ?.optJSONObject("content")
            ?.optJSONArray("parts")
            ?: JSONArray()

        val text = StringBuilder()
        for (i in 0 until parts.length()) {
            val partText = parts.optJSONObject(i)?.optString("text").orEmpty().trim()
            if (partText.isNotEmpty()) {
                if (text.isNotEmpty()) {
                    text.append('\n')
                }
                text.append(partText)
            }
        }

        return if (text.isEmpty()) {
            "Resposta vazia do Gemini"
        } else {
            text.toString()
        }
    }

    companion object {
        private const val DEFAULT_MODEL = "gemini-2.5-flash"
        private const val DEFAULT_VERSION = "v1beta"
        private val RETRYABLE_STATUS_CODES = setOf(404, 400)
        private val JSON_MEDIA_TYPE = "application/json; charset=utf-8".toMediaType()
    }
}

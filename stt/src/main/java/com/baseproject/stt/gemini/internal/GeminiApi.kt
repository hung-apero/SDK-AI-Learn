package com.baseproject.stt.gemini.internal

import java.io.File
import java.util.Base64
import java.util.concurrent.TimeUnit
import kotlinx.serialization.json.Json
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody

internal class GeminiApi(
    private val apiKey: String,
    private val model: String,
    private val systemPrompt: String,
    private val baseUrl: String = "https://generativelanguage.googleapis.com/v1beta",
) {
    private val client = OkHttpClient.Builder()
        .connectTimeout(60, TimeUnit.SECONDS)
        .readTimeout(90, TimeUnit.SECONDS)
        .build()

    private val json = Json {
        ignoreUnknownKeys = true
        coerceInputValues = true
        encodeDefaults = true
    }

    fun generateContent(audio: File, mimeType: String): CoachPayload {
        val base64 = Base64.getEncoder().encodeToString(audio.readBytes())
        val req = GeminiRequest(
            systemInstruction = SystemInstruction(listOf(TextPart(systemPrompt))),
            contents = listOf(
                Content(
                    parts = listOf(
                        Part(inlineData = InlineData(mimeType = mimeType, data = base64))
                    )
                )
            ),
            generationConfig = GenerationConfig(),
        )
        val body = json.encodeToString(GeminiRequest.serializer(), req)
            .toRequestBody("application/json".toMediaType())
        val normalizedModel = model.removePrefix("models/")
        val url = "$baseUrl/models/$normalizedModel:generateContent?key=$apiKey"
        val httpReq = Request.Builder().url(url).post(body).build()

        client.newCall(httpReq).execute().use { resp ->
            val raw = resp.body?.string().orEmpty()
            if (!resp.isSuccessful) error("HTTP ${resp.code}: ${raw.take(500)}")
            val outer = json.decodeFromString(GeminiResponse.serializer(), raw)
            val inner = outer.candidates.firstOrNull()
                ?.content?.parts?.firstOrNull()?.text
                ?: error("empty Gemini response")
            return json.decodeFromString(CoachPayload.serializer(), stripJsonFence(inner))
        }
    }

    fun close() {
        client.dispatcher.executorService.shutdown()
        client.connectionPool.evictAll()
    }

    private fun stripJsonFence(s: String): String {
        val trimmed = s.trim()
        if (!trimmed.startsWith("```")) return trimmed
        return trimmed
            .removePrefix("```json").removePrefix("```")
            .removeSuffix("```")
            .trim()
    }
}

//
// OpenRocky — Voice-first AI Agent
// https://github.com/openrocky
//
// Developed by everettjf with the assistance of Claude Code and Codex.
// Date: 2026-04-14
// Copyright (c) 2026 everettjf. All rights reserved.
//

package com.xnu.rocky.runtime.voice.stt

import com.xnu.rocky.providers.STTProviderConfiguration
import com.xnu.rocky.runtime.LogManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import okhttp3.*
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.MediaType.Companion.toMediaTypeOrNull
import okhttp3.RequestBody.Companion.asRequestBody
import okhttp3.RequestBody.Companion.toRequestBody
import java.io.File
import java.util.concurrent.TimeUnit

/**
 * OpenAI-compatible STT client: POSTs WAV audio to /v1/audio/transcriptions.
 * Works for OpenAI, Groq, and Alibaba Cloud (compatible-mode) endpoints.
 */
class OpenAISTTClient(configuration: STTProviderConfiguration) : STTClient {
    private val config = configuration.normalized()
    private val http = OkHttpClient.Builder()
        .callTimeout(30, TimeUnit.SECONDS)
        .build()
    private val json = Json { ignoreUnknownKeys = true }

    override suspend fun transcribe(audioData: ByteArray): String = withContext(Dispatchers.IO) {
        if (audioData.isEmpty()) throw STTClientException("Empty audio buffer")
        if (config.credential.isBlank()) throw STTClientException("STT provider is not configured", STTClientException.Severity.CRITICAL)

        val baseURL = config.customHost.ifBlank { config.provider.defaultBaseURL }
        val url = "${baseURL.trimEnd('/')}/v1/audio/transcriptions"
        val wav = makeWAV(audioData)

        val bodyBuilder = MultipartBody.Builder().setType(MultipartBody.FORM)
            .addFormDataPart(
                "file", "audio.wav",
                wav.toRequestBody("audio/wav".toMediaType())
            )
            .addFormDataPart("model", config.modelID)
            .addFormDataPart("response_format", "json")

        if (config.language.isNotBlank()) bodyBuilder.addFormDataPart("language", config.language)

        val request = Request.Builder()
            .url(url)
            .addHeader("Authorization", "Bearer ${config.credential}")
            .post(bodyBuilder.build())
            .build()

        http.newCall(request).execute().use { resp ->
            val raw = resp.body?.string().orEmpty()
            if (!resp.isSuccessful) {
                LogManager.error("STT request failed: HTTP ${resp.code} ${raw.take(300)}", "STT")
                val severity = if (resp.code == 401 || resp.code == 403) STTClientException.Severity.CRITICAL else STTClientException.Severity.NORMAL
                throw STTClientException("HTTP ${resp.code}: ${raw.take(200)}", severity)
            }
            val text = runCatching {
                json.parseToJsonElement(raw).jsonObject["text"]?.jsonPrimitive?.content
            }.getOrNull() ?: throw STTClientException("Invalid STT response format")
            LogManager.info("STT transcribed: ${text.take(100)}", "STT")
            text
        }
    }
}

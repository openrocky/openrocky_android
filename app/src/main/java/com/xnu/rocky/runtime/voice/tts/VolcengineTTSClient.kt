//
// OpenRocky — Voice-first AI Agent
// https://github.com/openrocky
//
// Developed by everettjf with the assistance of Claude Code and Codex.
// Date: 2026-04-14
// Copyright (c) 2026 everettjf. All rights reserved.
//

package com.xnu.rocky.runtime.voice.tts

import android.util.Base64
import com.xnu.rocky.providers.TTSProviderConfiguration
import com.xnu.rocky.runtime.LogManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.*
import okhttp3.*
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.RequestBody.Companion.toRequestBody
import java.util.UUID
import java.util.concurrent.TimeUnit

class VolcengineTTSClient(configuration: TTSProviderConfiguration) : TTSClient {
    private val config = configuration.normalized()
    private val http = OkHttpClient.Builder().callTimeout(30, TimeUnit.SECONDS).build()
    override val outputFormat: TTSAudioFormat = TTSAudioFormat.MP3

    override suspend fun synthesize(text: String): ByteArray = withContext(Dispatchers.IO) {
        val trimmed = text.trim()
        if (trimmed.isEmpty()) throw TTSClientException("Empty text")
        if (config.credential.isBlank()) throw TTSClientException("Volcengine not configured", TTSClientException.Severity.CRITICAL)

        val base = config.customHost.ifBlank { config.provider.defaultBaseURL }
        val url = "${base.trimEnd('/')}/api/v1/tts"

        val body = buildJsonObject {
            putJsonObject("app") {
                put("appid", JsonPrimitive("default"))
                put("cluster", JsonPrimitive("volcano_tts"))
            }
            putJsonObject("user") { put("uid", JsonPrimitive("openrocky")) }
            putJsonObject("audio") {
                put("voice_type", JsonPrimitive(config.resolvedVoice))
                put("encoding", JsonPrimitive("mp3"))
                put("speed_ratio", JsonPrimitive(1.0))
            }
            putJsonObject("request") {
                put("reqid", JsonPrimitive(UUID.randomUUID().toString()))
                put("text", JsonPrimitive(trimmed))
                put("operation", JsonPrimitive("query"))
            }
        }.toString()

        val request = Request.Builder()
            .url(url)
            .addHeader("Authorization", "Bearer; ${config.credential}")
            .post(body.toRequestBody("application/json".toMediaType()))
            .build()

        http.newCall(request).execute().use { resp ->
            val bytes = resp.body?.bytes() ?: throw TTSClientException("Empty audio response")
            if (!resp.isSuccessful) {
                val raw = String(bytes).take(300)
                LogManager.error("Volcengine TTS failed: HTTP ${resp.code} $raw", "TTS")
                val severity = if (resp.code == 401 || resp.code == 403) TTSClientException.Severity.CRITICAL else TTSClientException.Severity.NORMAL
                throw TTSClientException("HTTP ${resp.code}: $raw", severity)
            }
            val decoded = runCatching {
                val audio = Json.parseToJsonElement(String(bytes)).jsonObject["data"]?.jsonPrimitive?.contentOrNull
                audio?.let { Base64.decode(it, Base64.DEFAULT) }
            }.getOrNull()
            val result = decoded ?: bytes
            if (result.isEmpty()) throw TTSClientException("Volcengine returned empty audio")
            LogManager.info("Volcengine TTS synthesized: ${result.size} bytes", "TTS")
            result
        }
    }
}

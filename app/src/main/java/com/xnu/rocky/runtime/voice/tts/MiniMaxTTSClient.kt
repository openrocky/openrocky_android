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
import java.util.concurrent.TimeUnit

class MiniMaxTTSClient(configuration: TTSProviderConfiguration) : TTSClient {
    private val config = configuration.normalized()
    private val http = OkHttpClient.Builder().callTimeout(30, TimeUnit.SECONDS).build()
    override val outputFormat: TTSAudioFormat = TTSAudioFormat.MP3

    override suspend fun synthesize(text: String): ByteArray = withContext(Dispatchers.IO) {
        val trimmed = text.trim()
        if (trimmed.isEmpty()) throw TTSClientException("Empty text")
        if (config.credential.isBlank()) throw TTSClientException("MiniMax not configured", TTSClientException.Severity.CRITICAL)

        val base = config.customHost.ifBlank { config.provider.defaultBaseURL }
        val url = "${base.trimEnd('/')}/v1/t2a_v2"

        val body = buildJsonObject {
            put("model", JsonPrimitive(config.modelID))
            put("text", JsonPrimitive(trimmed))
            putJsonObject("voice_setting") { put("voice_id", JsonPrimitive(config.resolvedVoice)) }
            putJsonObject("audio_setting") {
                put("format", JsonPrimitive("mp3"))
                put("sample_rate", JsonPrimitive(24000))
            }
        }.toString()

        val request = Request.Builder()
            .url(url)
            .addHeader("Authorization", "Bearer ${config.credential}")
            .post(body.toRequestBody("application/json".toMediaType()))
            .build()

        http.newCall(request).execute().use { resp ->
            val bytes = resp.body?.bytes() ?: throw TTSClientException("Empty audio response")
            if (!resp.isSuccessful) {
                val raw = String(bytes).take(300)
                LogManager.error("MiniMax TTS failed: HTTP ${resp.code} $raw", "TTS")
                val severity = if (resp.code == 401 || resp.code == 403) TTSClientException.Severity.CRITICAL else TTSClientException.Severity.NORMAL
                throw TTSClientException("HTTP ${resp.code}: $raw", severity)
            }
            val jsonResult = runCatching {
                val element = Json.parseToJsonElement(String(bytes))
                val audio = element.jsonObject["data"]?.jsonObject?.get("audio")?.jsonPrimitive?.contentOrNull
                audio?.let { Base64.decode(it, Base64.DEFAULT) }
            }.getOrNull()
            val audio = jsonResult ?: bytes
            if (audio.isEmpty()) throw TTSClientException("MiniMax returned empty audio")
            LogManager.info("MiniMax TTS synthesized: ${audio.size} bytes", "TTS")
            audio
        }
    }
}

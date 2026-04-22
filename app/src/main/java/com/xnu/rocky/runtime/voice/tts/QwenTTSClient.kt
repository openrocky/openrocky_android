//
// OpenRocky — Voice-first AI Agent
// https://github.com/openrocky
//
// Developed by everettjf with the assistance of Claude Code and Codex.
// Date: 2026-04-19
// Copyright (c) 2026 everettjf. All rights reserved.
//

package com.xnu.rocky.runtime.voice.tts

import com.xnu.rocky.providers.TTSProviderConfiguration
import com.xnu.rocky.runtime.LogManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.*
import okhttp3.*
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.RequestBody.Companion.toRequestBody
import java.util.concurrent.TimeUnit

class QwenTTSClient(configuration: TTSProviderConfiguration) : TTSClient {
    private val config = configuration.normalized()
    private val http = OkHttpClient.Builder().callTimeout(30, TimeUnit.SECONDS).build()
    override val outputFormat: TTSAudioFormat = TTSAudioFormat.PCM16(sampleRate = 24000)

    override suspend fun synthesize(text: String): ByteArray = withContext(Dispatchers.IO) {
        val trimmed = text.trim()
        if (trimmed.isEmpty()) throw TTSClientException("Empty text")
        if (config.credential.isBlank()) throw TTSClientException("Qwen-TTS not configured", TTSClientException.Severity.CRITICAL)

        val base = config.customHost.ifBlank { config.provider.defaultBaseURL }
        val url = "${base.trimEnd('/')}/api/v1/services/aigc/multimodal-generation/generation"

        val body = buildJsonObject {
            put("model", JsonPrimitive(config.modelID))
            putJsonObject("input") {
                put("text", JsonPrimitive(trimmed))
                put("voice", JsonPrimitive(config.resolvedVoice))
            }
        }.toString()

        val request = Request.Builder()
            .url(url)
            .addHeader("Authorization", "Bearer ${config.credential}")
            .post(body.toRequestBody("application/json".toMediaType()))
            .build()

        val audioUrl: String = http.newCall(request).execute().use { resp ->
            val raw = resp.body?.string().orEmpty()
            if (!resp.isSuccessful) {
                LogManager.error("Qwen-TTS failed: HTTP ${resp.code} ${raw.take(300)}", "TTS")
                val severity = if (resp.code == 401 || resp.code == 403) TTSClientException.Severity.CRITICAL else TTSClientException.Severity.NORMAL
                throw TTSClientException("HTTP ${resp.code}: ${raw.take(200)}", severity)
            }
            runCatching {
                Json.parseToJsonElement(raw).jsonObject["output"]?.jsonObject
                    ?.get("audio")?.jsonObject?.get("url")?.jsonPrimitive?.contentOrNull
            }.getOrNull() ?: throw TTSClientException("Invalid Qwen-TTS response")
        }

        val fetchRequest = Request.Builder().url(audioUrl).get().build()
        http.newCall(fetchRequest).execute().use { audioResp ->
            val bytes = audioResp.body?.bytes() ?: throw TTSClientException("Empty Qwen-TTS audio")
            if (!audioResp.isSuccessful || bytes.isEmpty()) {
                throw TTSClientException("Failed to download Qwen-TTS audio (HTTP ${audioResp.code})")
            }
            LogManager.info("Qwen-TTS synthesized: ${bytes.size} bytes", "TTS")
            bytes
        }
    }
}

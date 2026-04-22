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

class ZhipuGLMTTSClient(configuration: TTSProviderConfiguration) : TTSClient {
    private val config = configuration.normalized()
    private val http = OkHttpClient.Builder().callTimeout(30, TimeUnit.SECONDS).build()
    override val outputFormat: TTSAudioFormat = TTSAudioFormat.MP3

    override suspend fun synthesize(text: String): ByteArray = withContext(Dispatchers.IO) {
        val trimmed = text.trim()
        if (trimmed.isEmpty()) throw TTSClientException("Empty text")
        if (config.credential.isBlank()) throw TTSClientException("Zhipu GLM TTS not configured", TTSClientException.Severity.CRITICAL)

        val base = config.customHost.ifBlank { config.provider.defaultBaseURL }
        val url = "${base.trimEnd('/')}/api/paas/v4/audio/speech"

        val body = buildJsonObject {
            put("model", JsonPrimitive(config.modelID))
            put("input", JsonPrimitive(trimmed))
            put("voice", JsonPrimitive(config.resolvedVoice))
            put("response_format", JsonPrimitive("mp3"))
        }.toString()

        val request = Request.Builder()
            .url(url)
            .addHeader("Authorization", "Bearer ${config.credential}")
            .post(body.toRequestBody("application/json".toMediaType()))
            .build()

        http.newCall(request).execute().use { resp ->
            if (!resp.isSuccessful) {
                val raw = resp.body?.string().orEmpty()
                LogManager.error("Zhipu GLM TTS failed: HTTP ${resp.code} ${raw.take(300)}", "TTS")
                val severity = if (resp.code == 401 || resp.code == 403) TTSClientException.Severity.CRITICAL else TTSClientException.Severity.NORMAL
                throw TTSClientException("HTTP ${resp.code}: ${raw.take(200)}", severity)
            }
            val bytes = resp.body?.bytes() ?: throw TTSClientException("Empty audio response")
            if (bytes.isEmpty()) throw TTSClientException("Empty audio response")
            LogManager.info("Zhipu GLM TTS synthesized: ${bytes.size} bytes", "TTS")
            bytes
        }
    }
}

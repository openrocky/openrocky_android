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
import kotlinx.serialization.json.*
import okhttp3.*
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.RequestBody.Companion.toRequestBody
import java.util.concurrent.TimeUnit

class DeepgramSTTClient(configuration: STTProviderConfiguration) : STTClient {
    private val config = configuration.normalized()
    private val http = OkHttpClient.Builder().callTimeout(30, TimeUnit.SECONDS).build()
    private val json = Json { ignoreUnknownKeys = true }

    override suspend fun transcribe(audioData: ByteArray): String = withContext(Dispatchers.IO) {
        if (audioData.isEmpty()) throw STTClientException("Empty audio buffer")
        if (config.credential.isBlank()) throw STTClientException("Deepgram API key missing", STTClientException.Severity.CRITICAL)

        val base = config.customHost.ifBlank { config.provider.defaultBaseURL }
        val params = buildString {
            append("?model=").append(config.modelID)
            if (config.language.isNotBlank()) append("&language=").append(config.language)
            append("&smart_format=true")
        }
        val url = "${base.trimEnd('/')}/v1/listen$params"

        val wav = makeWAV(audioData)
        val request = Request.Builder()
            .url(url)
            .addHeader("Authorization", "Token ${config.credential}")
            .addHeader("Content-Type", "audio/wav")
            .post(wav.toRequestBody("audio/wav".toMediaType()))
            .build()

        http.newCall(request).execute().use { resp ->
            val raw = resp.body?.string().orEmpty()
            if (!resp.isSuccessful) {
                LogManager.error("Deepgram STT failed: HTTP ${resp.code} ${raw.take(300)}", "STT")
                val severity = if (resp.code == 401 || resp.code == 403) STTClientException.Severity.CRITICAL else STTClientException.Severity.NORMAL
                throw STTClientException("HTTP ${resp.code}: ${raw.take(200)}", severity)
            }
            val transcript = runCatching {
                val obj = json.parseToJsonElement(raw).jsonObject
                obj["results"]?.jsonObject
                    ?.get("channels")?.jsonArray?.firstOrNull()?.jsonObject
                    ?.get("alternatives")?.jsonArray?.firstOrNull()?.jsonObject
                    ?.get("transcript")?.jsonPrimitive?.contentOrNull
            }.getOrNull().orEmpty()
            if (transcript.isBlank()) throw STTClientException("Deepgram returned empty transcript")
            transcript
        }
    }
}

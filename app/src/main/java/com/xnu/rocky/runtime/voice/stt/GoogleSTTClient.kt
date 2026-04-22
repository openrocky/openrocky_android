//
// OpenRocky — Voice-first AI Agent
// https://github.com/openrocky
//
// Developed by everettjf with the assistance of Claude Code and Codex.
// Date: 2026-04-14
// Copyright (c) 2026 everettjf. All rights reserved.
//

package com.xnu.rocky.runtime.voice.stt

import android.util.Base64
import com.xnu.rocky.providers.STTProviderConfiguration
import com.xnu.rocky.runtime.LogManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.*
import okhttp3.*
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.RequestBody.Companion.toRequestBody
import java.util.concurrent.TimeUnit

class GoogleSTTClient(configuration: STTProviderConfiguration) : STTClient {
    private val config = configuration.normalized()
    private val http = OkHttpClient.Builder().callTimeout(30, TimeUnit.SECONDS).build()
    private val json = Json { ignoreUnknownKeys = true }

    override suspend fun transcribe(audioData: ByteArray): String = withContext(Dispatchers.IO) {
        if (audioData.isEmpty()) throw STTClientException("Empty audio buffer")
        if (config.credential.isBlank()) throw STTClientException("Google API key missing", STTClientException.Severity.CRITICAL)

        val base = config.customHost.ifBlank { config.provider.defaultBaseURL }
        val url = "${base.trimEnd('/')}/v1/speech:recognize?key=${config.credential}"
        val language = config.language.ifBlank { "en-US" }

        val body = buildJsonObject {
            putJsonObject("config") {
                put("encoding", JsonPrimitive("LINEAR16"))
                put("sampleRateHertz", JsonPrimitive(24000))
                put("languageCode", JsonPrimitive(language))
            }
            putJsonObject("audio") {
                put("content", JsonPrimitive(Base64.encodeToString(audioData, Base64.NO_WRAP)))
            }
        }.toString()

        val request = Request.Builder()
            .url(url)
            .post(body.toRequestBody("application/json".toMediaType()))
            .build()

        http.newCall(request).execute().use { resp ->
            val raw = resp.body?.string().orEmpty()
            if (!resp.isSuccessful) {
                LogManager.error("Google STT failed: HTTP ${resp.code} ${raw.take(300)}", "STT")
                val severity = if (resp.code == 401 || resp.code == 403) STTClientException.Severity.CRITICAL else STTClientException.Severity.NORMAL
                throw STTClientException("HTTP ${resp.code}: ${raw.take(200)}", severity)
            }
            val transcript = runCatching {
                val obj = json.parseToJsonElement(raw).jsonObject
                obj["results"]?.jsonArray?.firstOrNull()?.jsonObject
                    ?.get("alternatives")?.jsonArray?.firstOrNull()?.jsonObject
                    ?.get("transcript")?.jsonPrimitive?.contentOrNull
            }.getOrNull().orEmpty()
            if (transcript.isBlank()) throw STTClientException("Google returned empty transcript")
            transcript
        }
    }
}

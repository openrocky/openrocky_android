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
import java.net.URLEncoder
import java.util.concurrent.TimeUnit

class GoogleTTSClient(configuration: TTSProviderConfiguration) : TTSClient {
    private val config = configuration.normalized()
    private val http = OkHttpClient.Builder().callTimeout(30, TimeUnit.SECONDS).build()
    override val outputFormat: TTSAudioFormat = TTSAudioFormat.MP3

    override suspend fun synthesize(text: String): ByteArray = withContext(Dispatchers.IO) {
        val trimmed = text.trim()
        if (trimmed.isEmpty()) throw TTSClientException("Empty text")
        if (config.credential.isBlank()) throw TTSClientException("Google not configured", TTSClientException.Severity.CRITICAL)

        val base = config.customHost.ifBlank { config.provider.defaultBaseURL }
        val key = URLEncoder.encode(config.credential, "UTF-8")
        val url = "${base.trimEnd('/')}/v1/text:synthesize?key=$key"

        val voice = config.resolvedVoice
        val lang = voice.take(5).ifBlank { "en-US" }

        val body = buildJsonObject {
            putJsonObject("input") { put("text", JsonPrimitive(trimmed)) }
            putJsonObject("voice") {
                put("languageCode", JsonPrimitive(lang))
                put("name", JsonPrimitive(voice))
            }
            putJsonObject("audioConfig") {
                put("audioEncoding", JsonPrimitive("MP3"))
                put("speakingRate", JsonPrimitive(1.0))
            }
        }.toString()

        val request = Request.Builder()
            .url(url)
            .post(body.toRequestBody("application/json".toMediaType()))
            .build()

        http.newCall(request).execute().use { resp ->
            val raw = resp.body?.string().orEmpty()
            if (!resp.isSuccessful) {
                LogManager.error("Google TTS failed: HTTP ${resp.code} ${raw.take(300)}", "TTS")
                val severity = if (resp.code == 401 || resp.code == 403) TTSClientException.Severity.CRITICAL else TTSClientException.Severity.NORMAL
                throw TTSClientException("HTTP ${resp.code}: ${raw.take(200)}", severity)
            }
            val audioBase64 = runCatching {
                Json.parseToJsonElement(raw).jsonObject["audioContent"]?.jsonPrimitive?.contentOrNull
            }.getOrNull()
            val audio = audioBase64?.let { Base64.decode(it, Base64.DEFAULT) }
                ?: throw TTSClientException("Invalid Google TTS response")
            LogManager.info("Google TTS synthesized: ${audio.size} bytes", "TTS")
            audio
        }
    }
}

//
// OpenRocky — Voice-first AI Agent
// https://github.com/openrocky
//
// Developed by everettjf with the assistance of Claude Code and Codex.
// Date: 2026-04-14
// Copyright (c) 2026 everettjf. All rights reserved.
//

package com.xnu.rocky.runtime.voice.tts

import com.xnu.rocky.providers.TTSProviderConfiguration
import com.xnu.rocky.providers.TTSProviderKind

sealed class TTSAudioFormat {
    data object MP3 : TTSAudioFormat()
    data class PCM16(val sampleRate: Int) : TTSAudioFormat()
}

interface TTSClient {
    suspend fun synthesize(text: String): ByteArray
    val outputFormat: TTSAudioFormat
}

class TTSClientException(message: String, val severity: Severity = Severity.NORMAL) : Exception(message) {
    enum class Severity { NORMAL, CRITICAL }
}

object TTSClientFactory {
    fun make(configuration: TTSProviderConfiguration): TTSClient = when (configuration.provider) {
        TTSProviderKind.OPENAI -> OpenAITTSClient(configuration)
        TTSProviderKind.ZHIPU_GLM -> ZhipuGLMTTSClient(configuration)
    }
}

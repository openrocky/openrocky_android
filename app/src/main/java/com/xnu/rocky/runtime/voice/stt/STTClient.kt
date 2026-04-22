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
import com.xnu.rocky.providers.STTProviderKind

interface STTClient {
    suspend fun transcribe(audioData: ByteArray): String
}

class STTClientException(message: String, val severity: Severity = Severity.NORMAL) : Exception(message) {
    enum class Severity { NORMAL, CRITICAL }
}

object STTClientFactory {
    fun make(configuration: STTProviderConfiguration): STTClient = when (configuration.provider) {
        STTProviderKind.OPENAI, STTProviderKind.GROQ, STTProviderKind.ALI_CLOUD -> OpenAISTTClient(configuration)
        STTProviderKind.DEEPGRAM -> DeepgramSTTClient(configuration)
        STTProviderKind.AZURE_SPEECH -> AzureSTTClient(configuration)
        STTProviderKind.GOOGLE_CLOUD -> GoogleSTTClient(configuration)
    }
}

/** Wraps raw PCM16 (mono) data in a minimal WAV header. */
internal fun makeWAV(pcmData: ByteArray, sampleRate: Int = 24000, channels: Int = 1, bitsPerSample: Int = 16): ByteArray {
    val byteRate = sampleRate * channels * bitsPerSample / 8
    val blockAlign = channels * bitsPerSample / 8
    val dataSize = pcmData.size
    val fileSize = 36 + dataSize
    val header = java.io.ByteArrayOutputStream()
    fun putLE32(v: Int) { header.write(v and 0xff); header.write((v shr 8) and 0xff); header.write((v shr 16) and 0xff); header.write((v shr 24) and 0xff) }
    fun putLE16(v: Int) { header.write(v and 0xff); header.write((v shr 8) and 0xff) }
    header.write("RIFF".toByteArray())
    putLE32(fileSize)
    header.write("WAVE".toByteArray())
    header.write("fmt ".toByteArray())
    putLE32(16)
    putLE16(1)
    putLE16(channels)
    putLE32(sampleRate)
    putLE32(byteRate)
    putLE16(blockAlign)
    putLE16(bitsPerSample)
    header.write("data".toByteArray())
    putLE32(dataSize)
    header.write(pcmData)
    return header.toByteArray()
}

//
// OpenRocky — Voice-first AI Agent
// https://github.com/openrocky
//
// Developed by everettjf with the assistance of Claude Code and Codex.
// Date: 2026-04-14
// Copyright (c) 2026 everettjf. All rights reserved.
//

package com.xnu.rocky.providers

import kotlinx.serialization.Serializable

data class TTSVoice(val id: String, val name: String, val subtitle: String)

@Serializable
enum class TTSProviderKind(
    val displayName: String,
    val summary: String,
    val defaultModel: String,
    val suggestedModels: List<String>,
    val credentialTitle: String,
    val credentialPlaceholder: String,
    val apiKeyGuideURL: String,
    val defaultBaseURL: String,
    val defaultVoice: String,
    val estimatedLatency: String,
    val priceRange: String,
    val isOpenAICompatible: Boolean
) {
    OPENAI(
        displayName = "OpenAI TTS",
        summary = "High quality text-to-speech. Multiple voices, fast latency, great for English.",
        defaultModel = "tts-1",
        suggestedModels = listOf("tts-1", "tts-1-hd"),
        credentialTitle = "API Key",
        credentialPlaceholder = "sk-...",
        apiKeyGuideURL = "https://platform.openai.com/api-keys",
        defaultBaseURL = "https://api.openai.com",
        defaultVoice = "alloy",
        estimatedLatency = "~0.5-1s",
        priceRange = "$15/1M chars",
        isOpenAICompatible = true
    ),
    ZHIPU_GLM(
        displayName = "Zhipu GLM-TTS",
        summary = "Zhipu GLM-TTS via BigModel. Natural Chinese voices, OpenAI-shaped API.",
        defaultModel = "glm-tts",
        suggestedModels = listOf("glm-tts"),
        credentialTitle = "API Key",
        credentialPlaceholder = "your-api-key...",
        apiKeyGuideURL = "https://open.bigmodel.cn/usercenter/apikeys",
        defaultBaseURL = "https://open.bigmodel.cn",
        defaultVoice = "tongtong",
        estimatedLatency = "~0.5-1s",
        priceRange = "~$1/1M chars",
        isOpenAICompatible = false
    );

    fun modelDescription(modelID: String): String? = when (this to modelID) {
        OPENAI to "tts-1" -> "Standard quality, fastest response (~0.3s latency)"
        OPENAI to "tts-1-hd" -> "HD quality, richer audio detail, slightly slower"
        ZHIPU_GLM to "glm-tts" -> "Zhipu GLM-TTS — natural Chinese voices"
        else -> null
    }

    val availableVoices: List<TTSVoice>
        get() = when (this) {
            OPENAI -> listOf(
                TTSVoice("alloy", "Alloy", "Neutral and balanced"),
                TTSVoice("ash", "Ash", "Soft and warm"),
                TTSVoice("coral", "Coral", "Clear and bright"),
                TTSVoice("echo", "Echo", "Confident and deep"),
                TTSVoice("nova", "Nova", "Friendly and energetic"),
                TTSVoice("sage", "Sage", "Calm and authoritative"),
                TTSVoice("shimmer", "Shimmer", "Gentle and versatile")
            )
            ZHIPU_GLM -> listOf(
                TTSVoice("tongtong", "Tongtong", "Chinese female, warm"),
                TTSVoice("qiumu", "Qiumu", "Chinese male, calm"),
                TTSVoice("xiaochen", "Xiaochen", "Chinese female, sweet"),
                TTSVoice("xiaoxun", "Xiaoxun", "Chinese male, deep")
            )
        }
}

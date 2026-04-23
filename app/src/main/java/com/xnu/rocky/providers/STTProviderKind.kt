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

@Serializable
enum class STTProviderKind(
    val displayName: String,
    val summary: String,
    val defaultModel: String,
    val suggestedModels: List<String>,
    val credentialTitle: String,
    val credentialPlaceholder: String,
    val apiKeyGuideURL: String,
    val defaultBaseURL: String,
    val estimatedLatency: String,
    val priceRange: String,
    val isOpenAICompatible: Boolean
) {
    OPENAI(
        displayName = "OpenAI Whisper",
        summary = "Industry-leading multilingual speech recognition. Best accuracy for mixed Chinese-English audio.",
        defaultModel = "whisper-1",
        suggestedModels = listOf("whisper-1", "gpt-4o-transcribe", "gpt-4o-mini-transcribe"),
        credentialTitle = "API Key",
        credentialPlaceholder = "sk-...",
        apiKeyGuideURL = "https://platform.openai.com/api-keys",
        defaultBaseURL = "https://api.openai.com",
        estimatedLatency = "~1-2s",
        priceRange = "$0.006/min",
        isOpenAICompatible = true
    ),
    ZHIPU_GLM(
        displayName = "Zhipu GLM ASR",
        summary = "Zhipu GLM speech recognition via BigModel. OpenAI-shaped API. No VPN needed in China.",
        defaultModel = "glm-asr",
        suggestedModels = listOf("glm-asr"),
        credentialTitle = "API Key",
        credentialPlaceholder = "your-api-key...",
        apiKeyGuideURL = "https://open.bigmodel.cn/usercenter/apikeys",
        defaultBaseURL = "https://open.bigmodel.cn",
        estimatedLatency = "~1-2s",
        priceRange = "~$0.003/min",
        isOpenAICompatible = true
    );

    fun modelDescription(modelID: String): String? = when (this to modelID) {
        OPENAI to "whisper-1" -> "Classic Whisper, reliable multilingual recognition"
        OPENAI to "gpt-4o-transcribe" -> "GPT-4o powered, best accuracy, higher cost"
        OPENAI to "gpt-4o-mini-transcribe" -> "GPT-4o mini, good accuracy at lower cost"
        ZHIPU_GLM to "glm-asr" -> "Zhipu GLM ASR — strong Chinese, OpenAI-compatible"
        else -> null
    }
}

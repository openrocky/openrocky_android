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
    GROQ(
        displayName = "Groq Whisper",
        summary = "Groq-powered Whisper. Extremely fast inference with free tier. OpenAI-compatible API.",
        defaultModel = "whisper-large-v3-turbo",
        suggestedModels = listOf("whisper-large-v3-turbo", "whisper-large-v3", "distil-whisper-large-v3-en"),
        credentialTitle = "API Key",
        credentialPlaceholder = "gsk_...",
        apiKeyGuideURL = "https://console.groq.com/keys",
        defaultBaseURL = "https://api.groq.com/openai",
        estimatedLatency = "~0.3-0.5s",
        priceRange = "Free tier",
        isOpenAICompatible = true
    ),
    DEEPGRAM(
        displayName = "Deepgram",
        summary = "Ultra-low latency (~300ms). Nova-2 model with best real-time accuracy.",
        defaultModel = "nova-2",
        suggestedModels = listOf("nova-2", "nova-3", "enhanced"),
        credentialTitle = "API Key",
        credentialPlaceholder = "your-api-key...",
        apiKeyGuideURL = "https://console.deepgram.com/project/api-keys",
        defaultBaseURL = "https://api.deepgram.com",
        estimatedLatency = "~0.3s",
        priceRange = "$0.0043/min",
        isOpenAICompatible = false
    ),
    AZURE_SPEECH(
        displayName = "Azure Speech",
        summary = "Microsoft Azure Speech Services. Enterprise-grade, 100+ languages, streaming support.",
        defaultModel = "default",
        suggestedModels = listOf("default"),
        credentialTitle = "Subscription Key",
        credentialPlaceholder = "your-subscription-key...",
        apiKeyGuideURL = "https://portal.azure.com",
        defaultBaseURL = "https://eastus.stt.speech.microsoft.com",
        estimatedLatency = "~0.5-1s",
        priceRange = "$0.016/hr",
        isOpenAICompatible = false
    ),
    GOOGLE_CLOUD(
        displayName = "Google Cloud Speech",
        summary = "Google Cloud Speech-to-Text. Excellent multilingual support and accuracy.",
        defaultModel = "default",
        suggestedModels = listOf("default"),
        credentialTitle = "API Key",
        credentialPlaceholder = "your-api-key...",
        apiKeyGuideURL = "https://console.cloud.google.com/apis/credentials",
        defaultBaseURL = "https://speech.googleapis.com",
        estimatedLatency = "~0.5-1s",
        priceRange = "$0.006/min",
        isOpenAICompatible = false
    ),
    ALI_CLOUD(
        displayName = "Alibaba Cloud Paraformer",
        summary = "Alibaba's SenseVoice/Paraformer model. Top-tier Chinese recognition, no VPN needed in China.",
        defaultModel = "paraformer-v2",
        suggestedModels = listOf("paraformer-v2", "paraformer-realtime-v2"),
        credentialTitle = "API Key",
        credentialPlaceholder = "sk-...",
        apiKeyGuideURL = "https://dashscope.console.aliyun.com/apiKey",
        defaultBaseURL = "https://dashscope.aliyuncs.com/compatible-mode",
        estimatedLatency = "~0.5-1s",
        priceRange = "~$0.002/min",
        isOpenAICompatible = true
    );

    fun modelDescription(modelID: String): String? = when (this to modelID) {
        OPENAI to "whisper-1" -> "Classic Whisper, reliable multilingual recognition"
        OPENAI to "gpt-4o-transcribe" -> "GPT-4o powered, best accuracy, higher cost"
        OPENAI to "gpt-4o-mini-transcribe" -> "GPT-4o mini, good accuracy at lower cost"
        GROQ to "whisper-large-v3-turbo" -> "Fastest, recommended for real-time use"
        GROQ to "whisper-large-v3" -> "Highest accuracy, slightly slower"
        GROQ to "distil-whisper-large-v3-en" -> "English only, ultra-fast"
        DEEPGRAM to "nova-2" -> "Best accuracy, production recommended"
        DEEPGRAM to "nova-3" -> "Latest generation, improved accuracy"
        DEEPGRAM to "enhanced" -> "Enhanced model, balanced speed/accuracy"
        ALI_CLOUD to "paraformer-v2" -> "Best Chinese recognition accuracy"
        ALI_CLOUD to "paraformer-realtime-v2" -> "Optimized for real-time streaming"
        else -> null
    }
}

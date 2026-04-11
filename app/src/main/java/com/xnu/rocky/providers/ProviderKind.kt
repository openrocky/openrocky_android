//
// OpenRocky — Voice-first AI Agent
// https://github.com/openrocky
//
// Developed by everettjf with the assistance of Claude Code and Codex.
// Date: 2026-03-25
// Copyright (c) 2026 everettjf. All rights reserved.
//

package com.xnu.rocky.providers

import kotlinx.serialization.Serializable

@Serializable
enum class ProviderKind(
    val displayName: String,
    val summary: String,
    val defaultModel: String,
    val suggestedModels: List<String>,
    val apiKeyPlaceholder: String,
    val guideUrl: String,
    val baseUrl: String
) {
    OPENAI(
        displayName = "OpenAI",
        summary = "GPT-4o, GPT-5, GPT-5 mini",
        defaultModel = "gpt-4o",
        suggestedModels = listOf("gpt-5", "gpt-5-mini", "gpt-4o", "gpt-4o-mini", "o3-mini"),
        apiKeyPlaceholder = "sk-...",
        guideUrl = "https://platform.openai.com/api-keys",
        baseUrl = "https://api.openai.com/v1/"
    ),
    AZURE_OPENAI(
        displayName = "Azure OpenAI",
        summary = "Azure-hosted OpenAI models",
        defaultModel = "gpt-4o",
        suggestedModels = listOf("gpt-4o", "gpt-4o-mini", "gpt-4"),
        apiKeyPlaceholder = "Azure API Key",
        guideUrl = "https://portal.azure.com",
        baseUrl = ""
    ),
    ANTHROPIC(
        displayName = "Anthropic",
        summary = "Claude 3.7 Sonnet, Claude 4",
        defaultModel = "claude-sonnet-4-20250514",
        suggestedModels = listOf("claude-sonnet-4-20250514", "claude-3-7-sonnet-20250219", "claude-3-5-haiku-20241022"),
        apiKeyPlaceholder = "sk-ant-...",
        guideUrl = "https://console.anthropic.com/settings/keys",
        baseUrl = "https://api.anthropic.com/v1/"
    ),
    GEMINI(
        displayName = "Gemini",
        summary = "Gemini 2.5 Pro, Flash",
        defaultModel = "gemini-2.5-flash",
        suggestedModels = listOf("gemini-2.5-pro", "gemini-2.5-flash", "gemini-2.0-flash"),
        apiKeyPlaceholder = "AI...",
        guideUrl = "https://aistudio.google.com/apikey",
        baseUrl = "https://generativelanguage.googleapis.com/v1beta/"
    ),
    GROQ(
        displayName = "Groq",
        summary = "Llama 3.3 70B, Mixtral",
        defaultModel = "llama-3.3-70b-versatile",
        suggestedModels = listOf("llama-3.3-70b-versatile", "llama-3.1-8b-instant", "mixtral-8x7b-32768"),
        apiKeyPlaceholder = "gsk_...",
        guideUrl = "https://console.groq.com/keys",
        baseUrl = "https://api.groq.com/openai/v1/"
    ),
    XAI(
        displayName = "xAI",
        summary = "Grok-3 Beta",
        defaultModel = "grok-3-beta",
        suggestedModels = listOf("grok-3-beta", "grok-3-mini-beta"),
        apiKeyPlaceholder = "xai-...",
        guideUrl = "https://console.x.ai",
        baseUrl = "https://api.x.ai/v1/"
    ),
    OPENROUTER(
        displayName = "OpenRouter",
        summary = "Multi-model gateway",
        defaultModel = "openai/gpt-4o",
        suggestedModels = listOf("openai/gpt-4o", "anthropic/claude-3.5-sonnet", "google/gemini-2.0-flash-exp"),
        apiKeyPlaceholder = "sk-or-...",
        guideUrl = "https://openrouter.ai/keys",
        baseUrl = "https://openrouter.ai/api/v1/"
    ),
    DEEPSEEK(
        displayName = "DeepSeek",
        summary = "DeepSeek Chat & Reasoner",
        defaultModel = "deepseek-chat",
        suggestedModels = listOf("deepseek-chat", "deepseek-reasoner"),
        apiKeyPlaceholder = "sk-...",
        guideUrl = "https://platform.deepseek.com/api_keys",
        baseUrl = "https://api.deepseek.com/v1/"
    ),
    VOLCENGINE(
        displayName = "Volcengine Doubao",
        summary = "Doubao Seed 1.6",
        defaultModel = "doubao-seed-1-6",
        suggestedModels = listOf("doubao-seed-1-6", "doubao-1-5-pro-256k", "doubao-1-5-pro-32k"),
        apiKeyPlaceholder = "API Key",
        guideUrl = "https://console.volcengine.com/ark",
        baseUrl = "https://ark.cn-beijing.volces.com/api/v3/"
    ),
    AIPROXY(
        displayName = "AIProxy",
        summary = "Proxy-backed provider",
        defaultModel = "gpt-4o",
        suggestedModels = listOf("gpt-4o", "gpt-4o-mini"),
        apiKeyPlaceholder = "Partial Key",
        guideUrl = "https://www.aiproxy.pro",
        baseUrl = ""
    );
}

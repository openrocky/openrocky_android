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
enum class RealtimeProviderKind(
    val displayName: String,
    val summary: String,
    val defaultModel: String,
    val suggestedModels: List<String>,
    val credentialTitle: String,
    val credentialPlaceholder: String,
    val guideUrl: String
) {
    OPENAI(
        displayName = "OpenAI Realtime",
        summary = "gpt-4o-realtime, tool calls + streaming (WebRTC)",
        defaultModel = "gpt-4o-mini-realtime-preview",
        suggestedModels = listOf("gpt-4o-mini-realtime-preview", "gpt-4o-realtime-preview"),
        credentialTitle = "API Key",
        credentialPlaceholder = "sk-...",
        guideUrl = "https://platform.openai.com/api-keys"
    ),
    GLM(
        displayName = "GLM Realtime",
        summary = "Zhipu AI end-to-end realtime voice with tool calling. Optimized for Chinese. No VPN needed in China.",
        defaultModel = "glm-realtime",
        suggestedModels = listOf("glm-realtime", "glm-realtime-flash"),
        credentialTitle = "API Key",
        credentialPlaceholder = "your-api-key...",
        guideUrl = "https://open.bigmodel.cn/usercenter/apikeys"
    );
}

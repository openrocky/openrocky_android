//
// OpenRocky — Voice-first AI Agent
// https://github.com/openrocky
//
// Developed by everettjf with the assistance of Claude Code and Codex.
// Date: 2026-04-14
// Copyright (c) 2026 everettjf. All rights reserved.
//

package com.xnu.rocky.providers

data class STTProviderConfiguration(
    val provider: STTProviderKind,
    val modelID: String,
    val credential: String = "",
    val customHost: String = "",
    val language: String = ""
) {
    val isConfigured: Boolean
        get() = credential.isNotBlank() && modelID.isNotBlank()

    val maskedCredential: String
        get() {
            if (credential.length < 8) return "Not connected"
            return credential.take(4) + "••••" + credential.takeLast(4)
        }

    fun normalized(): STTProviderConfiguration = copy(
        modelID = modelID.trim().ifEmpty { provider.defaultModel },
        credential = credential.trim(),
        customHost = customHost.trim().trimEnd('/'),
        language = language.trim()
    )
}

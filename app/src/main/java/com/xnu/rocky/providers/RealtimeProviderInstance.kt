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
import java.util.UUID

@Serializable
data class RealtimeProviderInstance(
    val id: String = UUID.randomUUID().toString(),
    val name: String = "",
    val kind: RealtimeProviderKind = RealtimeProviderKind.OPENAI,
    val modelID: String = "",
    val customHost: String = "",
    val openaiVoice: String = "alloy"
) {
    val credentialKeychainKey: String
        get() = "realtime_provider_credential_$id"

    fun toConfiguration(credential: String): RealtimeProviderConfiguration = RealtimeProviderConfiguration(
        provider = kind,
        modelID = modelID.ifBlank { kind.defaultModel },
        credential = credential,
        customHost = customHost,
        openaiVoice = openaiVoice
    )
}

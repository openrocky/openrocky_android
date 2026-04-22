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
import java.util.UUID

@Serializable
data class TTSProviderInstance(
    val id: String = UUID.randomUUID().toString(),
    val name: String = "",
    val kind: TTSProviderKind = TTSProviderKind.OPENAI,
    val modelID: String = "",
    val voice: String = "",
    val customHost: String = ""
) {
    val credentialKeychainKey: String
        get() = "rocky.tts-instance.$id.credential"

    fun toConfiguration(credential: String): TTSProviderConfiguration = TTSProviderConfiguration(
        provider = kind,
        modelID = modelID.ifBlank { kind.defaultModel },
        credential = credential,
        voice = voice.ifBlank { kind.defaultVoice },
        customHost = customHost
    )
}

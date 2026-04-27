//
// OpenRocky — Voice-first AI Agent
// https://github.com/openrocky
//
// Developed by everettjf with the assistance of Claude Code and Codex.
// Copyright (c) 2026 everettjf. All rights reserved.
//

package com.xnu.rocky.providers

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/** Mirrors iOS `OpenRockyRealtimeAdvancedSettings`. Persisted only when it differs from
 *  [DEFAULT] — null on the instance means "use defaults". */
@Serializable
data class RealtimeAdvancedSettings(
    val realtimeModel: String = DEFAULT_REALTIME_MODEL,
    val transcriptionModel: String = DEFAULT_TRANSCRIPTION_MODEL,
    val inputLanguage: String? = null,
    val turnDetection: TurnDetection = TurnDetection.Semantic(),
    val temperature: Double = 0.8,
    val maxOutputTokens: Int = 1024,
    val speed: Double = 1.0,
    val allowTextOnly: Boolean = false,
    val bargeInRMSThreshold: Int = 3500
) {
    companion object {
        const val DEFAULT_REALTIME_MODEL = "gpt-realtime"
        const val DEFAULT_TRANSCRIPTION_MODEL = "gpt-4o-mini-transcribe"
        val DEFAULT = RealtimeAdvancedSettings()

        val realtimeModelOptions = listOf(
            "gpt-realtime",
            "gpt-realtime-mini",
            "gpt-4o-realtime-preview",
            "gpt-4o-mini-realtime-preview"
        )
        val transcriptionModelOptions = listOf(
            "gpt-4o-mini-transcribe",
            "gpt-4o-transcribe",
            "whisper-1"
        )
    }
}

@Serializable
sealed class TurnDetection {
    @Serializable
    @SerialName("semantic")
    data class Semantic(val eagerness: String = "auto") : TurnDetection()

    @Serializable
    @SerialName("server")
    data class Server(
        val prefixPaddingMs: Int = 300,
        val silenceDurationMs: Int = 500,
        val threshold: Double = 0.5
    ) : TurnDetection()
}

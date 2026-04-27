//
// OpenRocky — Voice-first AI Agent
// https://github.com/openrocky
//
// Developed by everettjf with the assistance of Claude Code and Codex.
// Date: 2026-03-25
// Copyright (c) 2026 everettjf. All rights reserved.
//

package com.xnu.rocky.runtime.voice

sealed class RealtimeEvent {
    data class Status(val text: String) : RealtimeEvent()
    data class SessionReady(val model: String, val features: RealtimeVoiceFeatures) : RealtimeEvent()
    data class MicrophoneActive(val active: Boolean) : RealtimeEvent()
    data object InputSpeechStarted : RealtimeEvent()
    data class UserTranscriptDelta(val text: String) : RealtimeEvent()
    data class UserTranscriptFinal(val text: String) : RealtimeEvent()
    data class AssistantTranscriptDelta(val text: String) : RealtimeEvent()
    data class AssistantTranscriptFinal(val text: String) : RealtimeEvent()
    data class AssistantAudioChunk(val pcmData: ByteArray = ByteArray(0)) : RealtimeEvent()
    data object AssistantAudioDone : RealtimeEvent()
    data class ToolCallRequested(val name: String, val arguments: String, val callID: String) : RealtimeEvent()

    /** Real token usage parsed from `response.done`. Mirrors iOS `usageReported` — replaces
     *  the prior estimate-based metering. */
    data class UsageReported(
        val inputTokens: Int,
        val outputTokens: Int,
        val totalTokens: Int,
        val inputAudioTokens: Int,
        val outputAudioTokens: Int
    ) : RealtimeEvent()

    /** Free-form error string. Kept for backwards-compat with existing call sites.
     *  New code should prefer [ErrorDetailed] so the UI can map severity to the right affordance. */
    data class Error(val message: String) : RealtimeEvent()
    data class ErrorDetailed(val detail: VoiceError) : RealtimeEvent()
}

/** Lifecycle classification of a voice-session error. Drives UI affordances:
 *  transient → silently log + status tick; userAction → blocking prompt; fatal → kill switch. */
enum class VoiceErrorSeverity {
    /** Runtime is already recovering (reconnect in progress, retry scheduled). Just surface status. */
    Transient,
    /** Session is alive but blocked until the user acts (re-login, open settings, switch provider). */
    UserAction,
    /** Session can't recover. User must restart the voice session. */
    Fatal
}

/** Actionable hint the UI can map to a button ("Open Settings", "Reconfigure", "Retry"). */
enum class VoiceErrorAction { OpenSettings, ReconfigureProvider, Retry, None }

data class VoiceError(
    val severity: VoiceErrorSeverity,
    val message: String,
    val actionHint: VoiceErrorAction = VoiceErrorAction.None
)

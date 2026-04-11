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
    data class ToolCallRequested(val name: String, val arguments: String, val callID: String) : RealtimeEvent()
    data class Error(val message: String) : RealtimeEvent()
}

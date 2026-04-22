//
// OpenRocky — Voice-first AI Agent
// https://github.com/openrocky
//
// Developed by everettjf with the assistance of Claude Code and Codex.
// Date: 2026-04-14
// Copyright (c) 2026 everettjf. All rights reserved.
//

package com.xnu.rocky.runtime

import android.content.Context
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Voice mode selection — Realtime (streaming WebRTC/WebSocket) or Classic (STT→Chat→TTS pipeline).
 */
enum class VoiceMode { REALTIME, CLASSIC }

/** Lightweight wrapper around SharedPreferences for app-wide preferences. */
class Preferences(private val context: Context) {
    companion object {
        private const val PREFS_NAME = "openrocky_prefs"
        private const val KEY_VOICE_MODE = "voice_mode"
        private const val KEY_VOICE_INTERRUPTION_ENABLED = "voice_interruption_enabled"
        private const val KEY_AUTO_COMPRESS_HISTORY = "auto_compress_history"
    }

    private val prefs get() = context.getSharedPreferences(PREFS_NAME, 0)

    private val _voiceMode = MutableStateFlow(loadVoiceMode())
    val voiceMode: StateFlow<VoiceMode> = _voiceMode.asStateFlow()

    private val _voiceInterruption = MutableStateFlow(prefs.getBoolean(KEY_VOICE_INTERRUPTION_ENABLED, false))
    val voiceInterruption: StateFlow<Boolean> = _voiceInterruption.asStateFlow()

    private val _autoCompressHistory = MutableStateFlow(prefs.getBoolean(KEY_AUTO_COMPRESS_HISTORY, true))
    val autoCompressHistory: StateFlow<Boolean> = _autoCompressHistory.asStateFlow()

    private fun loadVoiceMode(): VoiceMode = runCatching {
        VoiceMode.valueOf(prefs.getString(KEY_VOICE_MODE, VoiceMode.REALTIME.name) ?: VoiceMode.REALTIME.name)
    }.getOrDefault(VoiceMode.REALTIME)

    fun setVoiceMode(mode: VoiceMode) {
        prefs.edit().putString(KEY_VOICE_MODE, mode.name).apply()
        _voiceMode.value = mode
    }

    fun setVoiceInterruption(enabled: Boolean) {
        prefs.edit().putBoolean(KEY_VOICE_INTERRUPTION_ENABLED, enabled).apply()
        _voiceInterruption.value = enabled
    }

    fun setAutoCompressHistory(enabled: Boolean) {
        prefs.edit().putBoolean(KEY_AUTO_COMPRESS_HISTORY, enabled).apply()
        _autoCompressHistory.value = enabled
    }
}

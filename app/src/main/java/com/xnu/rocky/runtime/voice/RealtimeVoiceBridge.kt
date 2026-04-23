//
// OpenRocky — Voice-first AI Agent
// https://github.com/openrocky
//
// Developed by everettjf with the assistance of Claude Code and Codex.
// Date: 2026-03-25
// Copyright (c) 2026 everettjf. All rights reserved.
//

package com.xnu.rocky.runtime.voice

import android.content.Context
import com.xnu.rocky.providers.RealtimeProviderConfiguration
import com.xnu.rocky.providers.RealtimeProviderKind
import com.xnu.rocky.runtime.CharacterStore
import com.xnu.rocky.runtime.LogManager
import com.xnu.rocky.runtime.tools.Toolbox
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.channelFlow

class RealtimeVoiceBridge(
    private val context: Context,
    private val config: RealtimeProviderConfiguration,
    private val toolbox: Toolbox,
    private val characterStore: CharacterStore
) {
    companion object {
        private const val TAG = "VoiceBridge"
    }

    private var client: RealtimeVoiceClient? = null

    private fun makeClient(): RealtimeVoiceClient = when (config.provider) {
        RealtimeProviderKind.OPENAI -> OpenAIRealtimeVoiceClient(config, toolbox, characterStore, context)
    }

    fun start(): Flow<RealtimeEvent> = channelFlow {
        val voiceClient = makeClient()
        client = voiceClient

        voiceClient.connect().collect { event ->
            when (event) {
                is RealtimeEvent.ToolCallRequested -> {
                    send(event)
                    try {
                        val result = toolbox.execute(event.name, event.arguments)
                        voiceClient.sendToolOutput(event.callID, result)
                    } catch (e: Exception) {
                        LogManager.error("Tool execution failed: ${e.message}", TAG)
                        voiceClient.sendToolOutput(event.callID, "Error: ${e.message}")
                    }
                }
                else -> send(event)
            }
        }
    }

    fun stop() {
        LogManager.info("[BRIDGE] Stopping voice bridge", TAG)
        client = null
    }
}

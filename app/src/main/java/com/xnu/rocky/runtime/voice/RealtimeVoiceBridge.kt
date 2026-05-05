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
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.channelFlow

class RealtimeVoiceBridge(
    private val context: Context,
    private val config: RealtimeProviderConfiguration,
    private val toolbox: Toolbox,
    private val characterStore: CharacterStore,
    /**
     * Optional replay-into-the-realtime-session items. Captured at bridge
     * construction so reconnect-on-drop pushes the same priming forward and a
     * Wi-Fi blip mid-conversation doesn't reset the model's view of what the
     * user already said. Empty list means a fresh session. Mirrors iOS
     * `lastPrimingItems`.
     */
    private val primingItems: List<VoicePrimingItem> = emptyList()
) {
    companion object {
        private const val TAG = "VoiceBridge"

        /**
         * Backoff schedule for unexpected drops. Three short tries cover the
         * common Wi-Fi → cell handoff and brief server hiccups. After that we
         * give up and ask the user to tap, so we don't burn battery looping.
         * Mirrors iOS reconnectBackoffSeconds.
         */
        private val RECONNECT_BACKOFF_MS = listOf(1_000L, 3_000L, 9_000L)
    }

    private var client: RealtimeVoiceClient? = null

    private fun makeClient(): RealtimeVoiceClient = when (config.provider) {
        RealtimeProviderKind.OPENAI -> OpenAIRealtimeVoiceClient(
            config, toolbox, characterStore, context, primingItems
        )
    }

    /**
     * Streams events for one logical voice session, reconnecting transparently
     * across transport drops. The inner loop creates a fresh client each time
     * the previous one terminates with `Disconnected`; cancellation (user tap)
     * propagates out cleanly so the upstream collector sees the stop. After
     * RECONNECT_BACKOFF_MS is exhausted the session ends with a Status entry
     * inviting the user to tap reconnect.
     */
    fun start(): Flow<RealtimeEvent> = channelFlow {
        var attempts = 0
        while (true) {
            val voiceClient = makeClient()
            client = voiceClient
            var dropped = false
            try {
                voiceClient.connect().collect { event ->
                    when (event) {
                        is RealtimeEvent.Disconnected -> {
                            // Sentinel: client's internal teardown is in progress.
                            // The collect() call will return shortly; we'll fall
                            // through to the reconnect decision below.
                            dropped = true
                        }
                        is RealtimeEvent.SessionReady -> {
                            // A successful (re)connect resets the backoff so a
                            // user with a flaky connection still gets the full
                            // budget on the next drop.
                            attempts = 0
                            send(event)
                        }
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
            } catch (e: CancellationException) {
                throw e // user-initiated stop — propagate cleanly
            } catch (e: Exception) {
                LogManager.error("Voice client error: ${e.message}", TAG)
                dropped = true
            }

            client = null
            if (!dropped) break // clean termination, nothing to reconnect to

            if (attempts >= RECONNECT_BACKOFF_MS.size) {
                send(RealtimeEvent.Status("Voice session disconnected. Tap to reconnect."))
                break
            }
            send(RealtimeEvent.MicrophoneActive(false))
            send(RealtimeEvent.Status("Voice session dropped — reconnecting..."))
            delay(RECONNECT_BACKOFF_MS[attempts])
            attempts++
            send(RealtimeEvent.Status("Reconnecting (attempt $attempts/${RECONNECT_BACKOFF_MS.size})..."))
        }
    }

    fun stop() {
        LogManager.info("[BRIDGE] Stopping voice bridge", TAG)
        client = null
    }
}

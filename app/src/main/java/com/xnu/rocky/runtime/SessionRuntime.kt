//
// OpenRocky — Voice-first AI Agent
// https://github.com/openrocky
//
// Developed by everettjf with the assistance of Claude Code and Codex.
// Date: 2026-03-25
// Copyright (c) 2026 everettjf. All rights reserved.
//

package com.xnu.rocky.runtime

import android.content.Context
import com.xnu.rocky.VoiceForegroundService
import com.xnu.rocky.models.*
import com.xnu.rocky.providers.*
import com.xnu.rocky.runtime.tools.Toolbox
import com.xnu.rocky.runtime.voice.RealtimeEvent
import com.xnu.rocky.runtime.voice.RealtimeVoiceBridge
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.text.SimpleDateFormat
import java.util.*

private const val TAG = "SessionRuntime"

class SessionRuntime(
    private val context: Context,
    private val providerStore: ProviderStore,
    private val realtimeProviderStore: RealtimeProviderStore,
    private val characterStore: CharacterStore,
    private val memoryService: MemoryService,
    private val usageService: UsageService,
    private val storageProvider: PersistentStorageProvider,
    private val toolbox: Toolbox,
    private val preferences: Preferences
) {
    private val scope = CoroutineScope(Dispatchers.Main + SupervisorJob())
    private val timeFormat = SimpleDateFormat("HH:mm", Locale.getDefault())
    private val inferenceRuntime = ChatInferenceRuntime(toolbox)
    private var voiceBridge: RealtimeVoiceBridge? = null
    private var voiceJob: Job? = null

    private val _session = MutableStateFlow(PreviewSession.liveSeed())
    val session: StateFlow<OpenRockySession> = _session.asStateFlow()

    private val _isVoiceActive = MutableStateFlow(false)
    val isVoiceActive: StateFlow<Boolean> = _isVoiceActive.asStateFlow()

    private val _statusText = MutableStateFlow("")
    val statusText: StateFlow<String> = _statusText.asStateFlow()

    private var currentConversationId: String? = null
    private val chatHistory = mutableListOf<ChatMessage>()

    init {
        // Wire up subagent progress events from toolbox → session timeline + statusText.
        // The realtime model calls `delegate-task`, which spawns a sub-agent that
        // emits structured events tool-by-tool. We render each as a timeline entry
        // (so the user can watch the back-end agent work) and update statusText so
        // the live status line reflects the current step. Mirrors iOS handleSubagentEvent.
        toolbox.subagentEventHandler = { event ->
            scope.launch(Dispatchers.Main) { handleSubagentEvent(event) }
        }
        syncSubagentChatConfiguration()
    }

    private fun handleSubagentEvent(event: SubagentEvent) {
        when (event) {
            is SubagentEvent.PlanStarted -> {
                val label = if (event.subtaskCount == 1) "subtask" else "subtasks"
                _statusText.value = "Delegating ${event.subtaskCount} $label..."
                addTimeline(TimelineKind.TOOL,
                    "Delegated task → ${event.subtaskCount} $label: ${event.taskDescription.take(120)}")
            }
            is SubagentEvent.SubtaskStarted -> {
                _statusText.value = "Subtask ${event.subtaskIndex + 1}/${event.totalCount} starting..."
                addTimeline(TimelineKind.TOOL,
                    "▸ Subtask ${event.subtaskIndex + 1}/${event.totalCount}: ${event.description.take(120)}")
            }
            is SubagentEvent.ToolStarted -> {
                val prefix = if (event.isSkill) "skill" else "tool"
                _statusText.value = "Calling $prefix `${event.name}`..."
                val argsPart = if (event.argsPreview.isEmpty()) "" else " ${event.argsPreview}"
                addTimeline(TimelineKind.TOOL, "  • [$prefix] ${event.name}$argsPart")
            }
            is SubagentEvent.ToolCompleted -> {
                val prefix = if (event.isSkill) "skill" else "tool"
                val mark = if (event.succeeded) "✓" else "✗"
                val elapsedLabel = String.format(Locale.US, "%.1fs", event.elapsedSeconds)
                _statusText.value = "$mark $prefix `${event.name}` ($elapsedLabel)"
                val kind = if (event.succeeded) TimelineKind.TOOL else TimelineKind.SYSTEM
                addTimeline(kind, "    $mark ${event.name} ($elapsedLabel) → ${event.resultPreview}")
            }
            is SubagentEvent.SubtaskCompleted -> {
                val mark = if (event.succeeded) "✓" else "✗"
                val elapsedLabel = String.format(Locale.US, "%.1fs", event.elapsedSeconds)
                val kind = if (event.succeeded) TimelineKind.RESULT else TimelineKind.SYSTEM
                addTimeline(kind, "$mark Subtask done ($elapsedLabel): ${event.summary.take(120)}")
            }
            is SubagentEvent.PlanCompleted -> {
                val elapsedLabel = String.format(Locale.US, "%.1fs", event.elapsedSeconds)
                _statusText.value = "Delegate-task finished (${event.succeededCount}/${event.totalCount}, $elapsedLabel)."
                addTimeline(TimelineKind.RESULT,
                    "Delegate-task complete: ${event.succeededCount}/${event.totalCount} subtasks succeeded in $elapsedLabel")
            }
        }
    }

    fun setConversation(conversationId: String) {
        currentConversationId = conversationId
        chatHistory.clear()
        val messages = storageProvider.loadMessages(conversationId)
        for (msg in messages) {
            chatHistory.add(ChatMessage(
                role = msg.role,
                content = msg.content,
                tool_call_id = msg.toolCallId,
                name = msg.toolName
            ))
        }
    }

    fun sendTextMessage(text: String) {
        val convId = currentConversationId ?: storageProvider.createConversation().also {
            currentConversationId = it
        }
        LogManager.info("[CHAT] sendTextMessage convId=$convId text='${text.take(50)}'", TAG)

        scope.launch {
            updateSession {
                it.copy(
                    mode = SessionMode.PLANNING,
                    liveTranscript = text
                )
            }
            addTimeline(TimelineKind.SPEECH, text)

            storageProvider.appendMessage(convId, ConversationMessage(role = "user", content = text))
            chatHistory.add(ChatMessage(role = "user", content = text))
            LogManager.info("[CHAT] user message stored, chatHistory.size=${chatHistory.size}", TAG)

            val config = providerStore.activeConfiguration
            if (config == null) {
                LogManager.warning("[CHAT] No active provider configured", TAG)
                updateSession { it.copy(mode = SessionMode.READY, assistantReply = "Please configure a provider first.") }
                return@launch
            }
            LogManager.info("[CHAT] using provider=${config.provider.displayName} model=${config.modelID}", TAG)
            syncSubagentChatConfiguration()

            updateSession { it.copy(
                mode = SessionMode.EXECUTING,
                provider = ProviderStatus(
                    name = config.provider.displayName,
                    model = config.modelID,
                    isConnected = true
                )
            ) }

            if (preferences.autoCompressHistory.value) {
                inferenceRuntime.compactHistoryIfNeeded(chatHistory, config)
            }

            val systemPrompt = characterStore.systemPrompt(toolbox.toolDescriptions())
            val messagesForInference = mutableListOf(
                ChatMessage(role = "system", content = systemPrompt)
            ) + chatHistory.toMutableList()
            LogManager.info("[CHAT] messagesForInference.size=${messagesForInference.size}", TAG)

            val responseBuilder = StringBuilder()
            var deltaCount = 0

            try {
                val fullResponse = inferenceRuntime.runInference(
                    config = config,
                    messages = messagesForInference.toMutableList(),
                    tools = toolbox.chatToolDefinitions(),
                    onDelta = { delta ->
                        deltaCount++
                        responseBuilder.append(delta)
                        if (deltaCount <= 3 || deltaCount % 20 == 0) {
                            LogManager.info("[CHAT] onDelta #$deltaCount content='${delta.take(30)}' totalLen=${responseBuilder.length}", TAG)
                        }
                        updateSession { it.copy(assistantReply = responseBuilder.toString()) }
                    },
                    onToolCall = { name, args ->
                        LogManager.info("[CHAT] onToolCall name=$name args='${args.take(80)}'", TAG)
                        addTimeline(TimelineKind.TOOL, "$name → executing…")
                        updatePlan(name, PlanStepState.ACTIVE)
                        storageProvider.appendMessage(convId, ConversationMessage(
                            role = "tool_call", content = args, toolName = name
                        ))
                    },
                    onToolResult = { name, result ->
                        LogManager.info("[CHAT] onToolResult name=$name result='${result.take(80)}'", TAG)
                        addTimeline(TimelineKind.RESULT, "$name → done")
                        updatePlan(name, PlanStepState.DONE)
                        storageProvider.appendMessage(convId, ConversationMessage(
                            role = "tool_result", content = result, toolName = name
                        ))
                    },
                    onUsage = { usage ->
                        LogManager.info("[CHAT] onUsage prompt=${usage.promptTokens} completion=${usage.completionTokens} total=${usage.totalTokens}", TAG)
                        usageService.record(
                            provider = config.provider.displayName,
                            model = config.modelID,
                            category = "chat",
                            promptTokens = usage.promptTokens,
                            completionTokens = usage.completionTokens
                        )
                    }
                )

                LogManager.info("[CHAT] inference done, deltaCount=$deltaCount fullResponse.len=${fullResponse.length} response='${fullResponse.take(100)}'", TAG)
                chatHistory.add(ChatMessage(role = "assistant", content = fullResponse))
                storageProvider.appendMessage(convId, ConversationMessage(role = "assistant", content = fullResponse))
                LogManager.info("[CHAT] assistant message stored to convId=$convId, chatHistory.size=${chatHistory.size}", TAG)

                updateSession {
                    it.copy(
                        mode = SessionMode.READY,
                        assistantReply = fullResponse,
                        liveTranscript = ""
                    )
                }
            } catch (e: Exception) {
                LogManager.error("[CHAT] inference FAILED: ${e.message}", TAG)
                LogManager.error("[CHAT] exception: ${e.stackTraceToString().take(500)}", TAG)
                updateSession {
                    it.copy(
                        mode = SessionMode.READY,
                        assistantReply = "Error: ${e.message}"
                    )
                }
            }
        }
    }

    fun startVoiceSession() {
        LogManager.info("[VOICE] startVoiceSession called", TAG)
        syncSubagentChatConfiguration()
        _isVoiceActive.value = true
        _statusText.value = "Connecting…"

        // Foreground service keeps the mic / session alive when the screen locks or the user
        // leaves the app — the Android-only capability that actually makes Rocky usable hands-free.
        VoiceForegroundService.start(context)

        voiceJob = scope.launch {
            try {
                val config = realtimeProviderStore.activeConfiguration ?: run {
                    _statusText.value = "No active realtime provider. Configure one in Settings."
                    _isVoiceActive.value = false
                    return@launch
                }
                LogManager.info("[VOICE] realtime provider=${config.provider.displayName} model=${config.modelID}", TAG)
                voiceBridge = RealtimeVoiceBridge(context, config, toolbox, characterStore)
                voiceBridge?.start()?.collect { event ->
                    handleVoiceEvent(event)
                }
            } catch (e: Exception) {
                LogManager.error("Voice session error: ${e.message}", "SessionRuntime")
                _statusText.value = "Error: ${e.message}"
            } finally {
                _isVoiceActive.value = false
                VoiceForegroundService.stop(context)
            }
        }
    }

    fun stopVoiceSession() {
        voiceJob?.cancel()
        voiceBridge?.stop()
        voiceBridge = null
        _isVoiceActive.value = false
        _statusText.value = ""
        updateSession { it.copy(mode = SessionMode.READY) }
        VoiceForegroundService.stop(context)
    }

    fun newConversation(): String {
        val id = storageProvider.createConversation()
        currentConversationId = id
        chatHistory.clear()
        _session.value = PreviewSession.liveSeed()
        return id
    }

    private fun syncSubagentChatConfiguration() {
        toolbox.subagentChatConfiguration = providerStore.activeConfiguration?.normalized()
    }

    // Track current voice message IDs for real-time updates
    private var currentVoiceUserMsgId: String? = null
    private var currentVoiceAssistantMsgId: String? = null

    private fun ensureVoiceConversation() {
        if (currentConversationId == null) {
            currentConversationId = storageProvider.createConversation()
        }
    }

    private fun handleVoiceEvent(event: RealtimeEvent) {
        when (event) {
            is RealtimeEvent.Status -> _statusText.value = event.text
            is RealtimeEvent.SessionReady -> {
                _statusText.value = "Listening…"
                updateSession { it.copy(mode = SessionMode.LISTENING) }
            }
            is RealtimeEvent.MicrophoneActive -> {}
            is RealtimeEvent.InputSpeechStarted -> {
                updateSession { it.copy(mode = SessionMode.LISTENING, liveTranscript = "") }
                // Reset for new user utterance
                currentVoiceUserMsgId = null
                currentVoiceAssistantMsgId = null
            }
            is RealtimeEvent.UserTranscriptDelta -> {
                updateSession { it.copy(liveTranscript = it.liveTranscript + event.text) }
            }
            is RealtimeEvent.UserTranscriptFinal -> {
                updateSession { it.copy(liveTranscript = "") }
                // Write user message to conversation
                ensureVoiceConversation()
                val convId = currentConversationId ?: return
                storageProvider.appendMessage(convId, ConversationMessage(role = "user", content = event.text))
            }
            is RealtimeEvent.AssistantTranscriptDelta -> {
                updateSession { it.copy(
                    mode = SessionMode.EXECUTING,
                    assistantReply = it.assistantReply + event.text
                ) }
            }
            is RealtimeEvent.AssistantTranscriptFinal -> {
                updateSession { it.copy(
                    mode = SessionMode.READY,
                    assistantReply = ""
                ) }
                // Write assistant message to conversation
                ensureVoiceConversation()
                val convId = currentConversationId ?: return
                storageProvider.appendMessage(convId, ConversationMessage(role = "assistant", content = event.text))
            }
            is RealtimeEvent.ToolCallRequested -> {
                addTimeline(TimelineKind.TOOL, "${event.name} → executing…")
                updateSession { it.copy(mode = SessionMode.EXECUTING) }
            }
            is RealtimeEvent.Error -> {
                _statusText.value = "Error: ${event.message}"
                LogManager.error(event.message, "Voice")
            }
            is RealtimeEvent.ErrorDetailed -> handleVoiceError(event.detail)
            is RealtimeEvent.AssistantAudioChunk -> {}
            is RealtimeEvent.AssistantAudioDone -> {}
            is RealtimeEvent.UsageReported -> {
                val cfg = realtimeProviderStore.activeConfiguration
                if (cfg != null) {
                    usageService.record(
                        provider = cfg.provider.displayName,
                        model = cfg.modelID,
                        category = "voice",
                        promptTokens = event.inputTokens,
                        completionTokens = event.outputTokens
                    )
                }
                LogManager.info(
                    "[VOICE] usage in=${event.inputTokens} out=${event.outputTokens} " +
                        "audio_in=${event.inputAudioTokens} audio_out=${event.outputAudioTokens}",
                    "Voice"
                )
            }
        }
    }

    private fun handleVoiceError(detail: com.xnu.rocky.runtime.voice.VoiceError) {
        val prefix = when (detail.severity) {
            com.xnu.rocky.runtime.voice.VoiceErrorSeverity.Transient -> "…"
            com.xnu.rocky.runtime.voice.VoiceErrorSeverity.UserAction -> "!"
            com.xnu.rocky.runtime.voice.VoiceErrorSeverity.Fatal -> "⚠"
        }
        LogManager.error(
            "Session error [${detail.severity}] action=${detail.actionHint}: ${detail.message}",
            "Voice"
        )
        _statusText.value = "$prefix ${detail.message}"
        if (detail.severity == com.xnu.rocky.runtime.voice.VoiceErrorSeverity.Fatal) {
            updateSession { it.copy(mode = SessionMode.READY) }
        }
    }

    private fun updateSession(update: (OpenRockySession) -> OpenRockySession) {
        _session.value = update(_session.value)
    }

    private fun addTimeline(kind: TimelineKind, text: String) {
        val entry = TimelineEntry(kind = kind, time = timeFormat.format(Date()), text = text)
        updateSession { session ->
            // Keep enough recent history that one delegate-task (which can fire
            // ~6–15 tool events) doesn't immediately push the surrounding speech
            // and result entries off the buffer. Mirrors iOS cap.
            val timeline = (session.timeline + entry).takeLast(32)
            session.copy(timeline = timeline)
        }
    }

    private fun updatePlan(toolName: String, state: PlanStepState) {
        updateSession { session ->
            val plan = session.plan.map {
                if (it.title.contains(toolName, ignoreCase = true)) it.copy(state = state) else it
            }
            session.copy(plan = plan)
        }
    }

    fun destroy() {
        stopVoiceSession()
        scope.cancel()
    }
}

//
// OpenRocky — Voice-first AI Agent
// https://github.com/openrocky
//
// Developed by everettjf with the assistance of Claude Code and Codex.
// Date: 2026-04-12
// Copyright (c) 2026 everettjf. All rights reserved.
//

package com.xnu.rocky.runtime.voice

import com.xnu.rocky.providers.RealtimeProviderConfiguration
import com.xnu.rocky.runtime.CharacterStore
import com.xnu.rocky.runtime.LogManager
import com.xnu.rocky.runtime.tools.Toolbox
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.serialization.json.*
import okhttp3.*
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.util.UUID
import kotlin.math.sqrt

class GLMRealtimeVoiceClient(
    private val config: RealtimeProviderConfiguration,
    private val toolbox: Toolbox,
    private val characterStore: CharacterStore
) : RealtimeVoiceClient {

    companion object {
        private const val TAG = "GLMRealtime"

        // ── Consolidated Tool Mapping ──
        // Maps category tool name → (action → original tool name)
        // Android uses android- prefixed tools instead of apple- prefixed.
        val consolidatedToolMapping: Map<String, Map<String, String>> = mapOf(
            "location_weather" to mapOf(
                "get_location" to "android-location",
                "geocode" to "android-geocode",
                "weather" to "weather",
                "nearby" to "nearby-search"
            ),
            "calendar_reminders" to mapOf(
                "list_events" to "android-calendar-list",
                "create_event" to "android-calendar-create",
                "list_reminders" to "android-reminder-list",
                "create_reminder" to "android-reminder-create",
                "set_alarm" to "android-alarm"
            ),
            "contacts_communication" to mapOf(
                "search_contacts" to "android-contacts-search",
                "send_notification" to "notification-schedule",
                "open_url" to "open-url"
            ),
            "web_search" to mapOf(
                "search" to "web-search",
                "read_page" to "browser-read",
                "open_browser" to "browser-open"
            ),
            "files_memory" to mapOf(
                "read_file" to "file-read",
                "write_file" to "file-write",
                "memory_get" to "memory_get",
                "memory_write" to "memory_write",
                "todo" to "todo"
            ),
            "code_execute" to mapOf(
                "shell" to "shell-execute",
                "python" to "python-execute"
            ),
            "media_capture" to mapOf(
                "camera" to "camera-capture",
                "photo_pick" to "photo-pick",
                "file_pick" to "file-pick"
            ),
            "delegate_task" to mapOf("delegate" to "delegate-task")
        )

        /**
         * Resolve a consolidated GLM tool call back to the original tool name + arguments.
         * Returns (originalToolName, originalArguments) for the Toolbox to execute.
         */
        fun resolveConsolidatedToolCall(name: String, arguments: String): Pair<String, String> {
            // delegate_task is a pass-through
            if (name == "delegate_task") {
                return "delegate-task" to arguments
            }

            val json = try {
                Json.parseToJsonElement(arguments).jsonObject
            } catch (_: Exception) {
                return name to arguments
            }

            val action = json["action"]?.jsonPrimitive?.contentOrNull ?: ""
            val mapping = consolidatedToolMapping[name] ?: return name to arguments
            val originalName = mapping[action] ?: return name to arguments

            // Remove the "action" key from arguments
            val remainingArgs = JsonObject(json.filterKeys { it != "action" })
            return originalName to remainingArgs.toString()
        }
    }

    private val modelID = config.modelID.ifBlank { "glm-realtime" }
    private var socket: WebSocket? = null
    private var isReady = false
    @Volatile
    private var emitEvent: ((RealtimeEvent) -> Unit)? = null

    // Client-side VAD state
    private var isSpeaking = false
    private var silenceChunkCount = 0
    private val silenceThreshold = 8 // ~800ms at 100ms chunks
    private var audioChunkCount = 0
    private var isResponseInProgress = false
    private var pendingTranscript: String? = null

    override suspend fun connect(): Flow<RealtimeEvent> = callbackFlow {
        emitEvent = { event -> trySend(event) }

        require(config.credential.isNotBlank()) { "GLM credential is required" }

        trySend(RealtimeEvent.Status("Connecting GLM Realtime session..."))

        val baseHost = config.customHost.ifBlank { "wss://open.bigmodel.cn" }
        val url = "${baseHost.trimEnd('/')}/api/paas/v4/realtime"

        val request = Request.Builder()
            .url(url)
            .header("Authorization", "Bearer ${config.credential}")
            .build()

        val client = OkHttpClient()

        // Track connection state for handshake
        var gotCreated = false
        var gotUpdated = false

        socket = client.newWebSocket(request, object : WebSocketListener() {
            override fun onOpen(webSocket: WebSocket, response: Response) {
                LogManager.info("GLM WebSocket connected, model=$modelID", TAG)
            }

            override fun onMessage(webSocket: WebSocket, text: String) {
                val json = try {
                    Json.parseToJsonElement(text).jsonObject
                } catch (_: Exception) { return }

                val eventType = json["type"]?.jsonPrimitive?.contentOrNull ?: return

                // Handshake phase
                if (!gotCreated && eventType == "session.created") {
                    gotCreated = true
                    LogManager.info("GLM session.created received", TAG)
                    // Send session.update
                    sendSessionUpdate(webSocket)
                    return
                }
                if (!gotUpdated && eventType == "session.updated") {
                    gotUpdated = true
                    LogManager.info("GLM session.updated - configuration accepted", TAG)
                    isReady = true
                    emitEvent?.invoke(RealtimeEvent.SessionReady(
                        model = modelID,
                        features = RealtimeVoiceFeatures(
                            supportsTextInput = true,
                            supportsAssistantStreaming = true,
                            supportsToolCalls = true,
                            supportsAudioOutput = true,
                            needsMicSuspension = true
                        )
                    ))
                    emitEvent?.invoke(RealtimeEvent.Status("GLM Realtime session is ready."))
                    return
                }

                handleServerMessage(json, eventType)
            }

            override fun onFailure(webSocket: WebSocket, t: Throwable, response: Response?) {
                LogManager.error("GLM WebSocket error: ${t.message}", TAG)
                emitEvent?.invoke(RealtimeEvent.Error("Voice connection lost: ${t.message}"))
            }

            override fun onClosed(webSocket: WebSocket, code: Int, reason: String) {
                LogManager.info("GLM WebSocket closed: $code $reason", TAG)
            }
        })

        awaitClose {
            LogManager.info("GLM realtime disconnecting", TAG)
            socket?.close(1000, "Client closing")
            socket = null
            isReady = false
        }
    }

    private fun handleServerMessage(json: JsonObject, eventType: String) {
        when (eventType) {
            "session.created" -> {
                LogManager.info("GLM session.created received (in loop)", TAG)
            }

            "session.updated" -> {
                LogManager.info("GLM session.updated received (in loop)", TAG)
                if (!isReady) {
                    isReady = true
                    emitEvent?.invoke(RealtimeEvent.SessionReady(
                        model = modelID,
                        features = RealtimeVoiceFeatures(
                            supportsTextInput = true,
                            supportsAssistantStreaming = true,
                            supportsToolCalls = true,
                            supportsAudioOutput = true,
                            needsMicSuspension = true
                        )
                    ))
                }
            }

            "input_audio_buffer.speech_started" -> {
                LogManager.info("GLM: speech_started detected by server VAD", TAG)
                emitEvent?.invoke(RealtimeEvent.InputSpeechStarted)
                emitEvent?.invoke(RealtimeEvent.Status("Listening..."))
            }

            "input_audio_buffer.speech_stopped" -> {
                LogManager.info("GLM: speech_stopped detected by server VAD", TAG)
                emitEvent?.invoke(RealtimeEvent.Status("Processing..."))
            }

            "conversation.item.input_audio_transcription.completed" -> {
                val transcript = json["transcript"]?.jsonPrimitive?.contentOrNull
                if (!transcript.isNullOrEmpty()) {
                    emitEvent?.invoke(RealtimeEvent.UserTranscriptFinal(transcript))
                }
            }

            "response.audio_transcript.delta" -> {
                val delta = json["delta"]?.jsonPrimitive?.contentOrNull
                if (!delta.isNullOrEmpty()) {
                    emitEvent?.invoke(RealtimeEvent.AssistantTranscriptDelta(delta))
                }
            }

            "response.audio_transcript.done" -> {
                // Buffer transcript until response.done to avoid premature mic resume
                val transcript = json["transcript"]?.jsonPrimitive?.contentOrNull
                if (!transcript.isNullOrEmpty()) {
                    pendingTranscript = transcript
                }
            }

            "response.created" -> {
                LogManager.info("GLM: response.created - model is generating", TAG)
            }

            "response.audio.delta" -> {
                val audioData = json["delta"]?.jsonPrimitive?.contentOrNull
                if (!audioData.isNullOrEmpty()) {
                    val decoded = android.util.Base64.decode(audioData, android.util.Base64.DEFAULT)
                    emitEvent?.invoke(RealtimeEvent.AssistantAudioChunk(decoded))
                }
            }

            "response.audio.done" -> {
                LogManager.info("GLM: response.audio.done", TAG)
            }

            "response.function_call_arguments.done" -> {
                val rawName = json["name"]?.jsonPrimitive?.contentOrNull ?: ""
                val callID = json["call_id"]?.jsonPrimitive?.contentOrNull ?: UUID.randomUUID().toString()
                val rawArguments = json["arguments"]?.jsonPrimitive?.contentOrNull ?: "{}"
                val (resolvedName, resolvedArgs) = resolveConsolidatedToolCall(rawName, rawArguments)
                LogManager.info("GLM tool call: $rawName -> $resolvedName callID=$callID", TAG)
                emitEvent?.invoke(RealtimeEvent.ToolCallRequested(resolvedName, resolvedArgs, callID))
            }

            "response.done" -> {
                isResponseInProgress = false
                // Emit final transcript AFTER all audio has been delivered
                val transcript = pendingTranscript
                if (transcript != null) {
                    emitEvent?.invoke(RealtimeEvent.AssistantTranscriptFinal(transcript))
                    pendingTranscript = null
                } else {
                    // Extract transcript from response output if available
                    val resp = json["response"]?.jsonObject
                    val output = resp?.get("output")?.jsonArray
                    output?.forEach { item ->
                        val content = item.jsonObject["content"]?.jsonArray
                        content?.forEach { c ->
                            val t = c.jsonObject["transcript"]?.jsonPrimitive?.contentOrNull
                            if (!t.isNullOrEmpty()) {
                                emitEvent?.invoke(RealtimeEvent.AssistantTranscriptFinal(t))
                                return@forEach
                            }
                        }
                    }
                }
                emitEvent?.invoke(RealtimeEvent.Status("Ready for next input."))
            }

            "error" -> {
                val errorObj = json["error"]?.jsonObject
                val msg = errorObj?.get("message")?.jsonPrimitive?.contentOrNull ?: "Unknown GLM error"
                LogManager.error("GLM error: $msg", TAG)
                emitEvent?.invoke(RealtimeEvent.Error("GLM: $msg"))
            }

            "heartbeat" -> {
                // Connection keep-alive, ignore
            }

            else -> {
                LogManager.info("GLM event: $eventType", TAG)
            }
        }
    }

    private fun sendSessionUpdate(ws: WebSocket) {
        var personaPrefix = ""
        if (config.characterName.isNotBlank()) {
            personaPrefix += "Your name is ${config.characterName}. "
        }
        if (config.characterSpeakingStyle.isNotBlank()) {
            personaPrefix += "Speaking style: ${config.characterSpeakingStyle}. "
        }

        val voice = config.glmVoice.ifBlank { "tongtong" }
        val soulInstructions = characterStore.voiceSystemPrompt()
        val instructions = personaPrefix + soulInstructions + """

Voice-specific rules:
- Keep spoken replies short and natural. Do not read markdown formatting aloud.
- When you need to call tools, do NOT narrate the process. Just call the tool silently.
- After receiving tool results, directly tell the user the final answer.
- Be concise: give the answer in one or two sentences when possible.
"""

        val tools = buildConsolidatedTools()

        val sessionConfig = buildJsonObject {
            putJsonArray("modalities") { add(JsonPrimitive("audio")); add(JsonPrimitive("text")) }
            put("voice", JsonPrimitive(voice))
            put("input_audio_format", JsonPrimitive("wav"))
            put("output_audio_format", JsonPrimitive("pcm"))
            put("instructions", JsonPrimitive(instructions))
            putJsonObject("turn_detection") {
                put("type", JsonPrimitive("client_vad"))
            }
            putJsonObject("beta_fields") {
                put("chat_mode", JsonPrimitive("audio"))
                put("tts_source", JsonPrimitive("e2e"))
                put("auto_search", JsonPrimitive(false))
                putJsonObject("greeting_config") {
                    put("enable", JsonPrimitive(true))
                    put("content", JsonPrimitive(
                        config.characterGreeting.ifBlank { "你好，有什么可以帮你的吗？" }
                    ))
                }
            }
            if (tools.jsonArray.isNotEmpty()) {
                put("tools", tools)
            }
        }

        val message = buildJsonObject {
            put("type", JsonPrimitive("session.update"))
            put("session", sessionConfig)
        }

        // GLM strictly validates tool parameters — null properties/required cause 422.
        // Sanitize before sending.
        var text = message.toString()
        text = text.replace("\"properties\":null", "\"properties\":{}")
        text = text.replace("\"required\":null", "\"required\":[]")

        LogManager.info("GLM: sending ${tools.jsonArray.size} consolidated tools", TAG)
        ws.send(text)
    }

    override suspend fun disconnect() {
        socket?.close(1000, "Client closing")
        socket = null
        isReady = false
    }

    override suspend fun sendText(text: String) {
        val ws = socket ?: return
        if (!isReady) return

        val createItem = buildJsonObject {
            put("type", JsonPrimitive("conversation.item.create"))
            putJsonObject("item") {
                put("type", JsonPrimitive("message"))
                put("role", JsonPrimitive("user"))
                putJsonArray("content") {
                    addJsonObject {
                        put("type", JsonPrimitive("input_text"))
                        put("text", JsonPrimitive(text))
                    }
                }
            }
        }
        ws.send(createItem.toString())

        val respond = buildJsonObject { put("type", JsonPrimitive("response.create")) }
        ws.send(respond.toString())
    }

    override suspend fun sendAudioChunk(data: ByteArray) {
        val ws = socket ?: return
        if (!isReady) return

        // Downsample from 24kHz to 16kHz, then wrap as WAV
        val pcm16k = downsample24kTo16k(data)
        val wavData = wrapPCM16AsWAV(pcm16k, 16000)
        val wavBase64 = android.util.Base64.encodeToString(wavData, android.util.Base64.NO_WRAP)

        val message = buildJsonObject {
            put("type", JsonPrimitive("input_audio_buffer.append"))
            put("audio", JsonPrimitive(wavBase64))
        }
        ws.send(message.toString())

        audioChunkCount++
        if (audioChunkCount == 1) {
            LogManager.info("GLM: first audio chunk sent, pcm24k=${data.size}bytes pcm16k=${pcm16k.size}bytes wav=${wavData.size}bytes", TAG)
        }

        // Client-side VAD: detect silence to auto-commit
        val rms = computeRMS(pcm16k)
        val isSilent = rms < 500.0

        if (isSilent) {
            if (isSpeaking) {
                silenceChunkCount++
                if (silenceChunkCount >= silenceThreshold) {
                    isSpeaking = false
                    silenceChunkCount = 0

                    if (isResponseInProgress) {
                        LogManager.info("GLM: skipping commit, response already in progress", TAG)
                        return
                    }

                    LogManager.info("GLM: client VAD detected speech end, committing (rms=$rms)", TAG)
                    isResponseInProgress = true
                    emitEvent?.invoke(RealtimeEvent.Status("Processing..."))

                    val commitMsg = buildJsonObject { put("type", JsonPrimitive("input_audio_buffer.commit")) }
                    ws.send(commitMsg.toString())

                    val responseMsg = buildJsonObject { put("type", JsonPrimitive("response.create")) }
                    ws.send(responseMsg.toString())
                }
            }
        } else {
            if (!isSpeaking) {
                isSpeaking = true
                LogManager.info("GLM: client VAD detected speech start (rms=$rms)", TAG)
                emitEvent?.invoke(RealtimeEvent.InputSpeechStarted)
                emitEvent?.invoke(RealtimeEvent.Status("Listening..."))
            }
            silenceChunkCount = 0
        }
    }

    override suspend fun finishAudioInput() {
        val ws = socket ?: return
        if (!isReady) return
        val message = buildJsonObject { put("type", JsonPrimitive("input_audio_buffer.commit")) }
        ws.send(message.toString())
    }

    override suspend fun sendToolOutput(callID: String, output: String) {
        val ws = socket ?: return
        if (!isReady) return

        val createItem = buildJsonObject {
            put("type", JsonPrimitive("conversation.item.create"))
            putJsonObject("item") {
                put("type", JsonPrimitive("function_call_output"))
                put("call_id", JsonPrimitive(callID))
                put("output", JsonPrimitive(output))
            }
        }
        ws.send(createItem.toString())

        val respond = buildJsonObject { put("type", JsonPrimitive("response.create")) }
        ws.send(respond.toString())
    }

    override suspend fun speakText(text: String) {
        // GLM handles TTS natively via the realtime session
    }

    // ── Audio Processing ──

    private fun computeRMS(data: ByteArray): Double {
        val buffer = ByteBuffer.wrap(data).order(ByteOrder.LITTLE_ENDIAN)
        val sampleCount = data.size / 2
        if (sampleCount == 0) return 0.0
        var sumSquares = 0.0
        for (i in 0 until sampleCount) {
            val sample = buffer.getShort().toDouble()
            sumSquares += sample * sample
        }
        return sqrt(sumSquares / sampleCount)
    }

    private fun downsample24kTo16k(data: ByteArray): ByteArray {
        val sampleCount = data.size / 2
        if (sampleCount == 0) return data

        val inputBuffer = ByteBuffer.wrap(data).order(ByteOrder.LITTLE_ENDIAN)
        val inputSamples = ShortArray(sampleCount) { inputBuffer.getShort() }

        // 24kHz -> 16kHz: for every 3 input samples, produce 2 output samples
        val outputCount = (sampleCount * 2) / 3
        val output = ShortArray(outputCount)

        for (i in 0 until outputCount) {
            val srcPos = i.toDouble() * 3.0 / 2.0
            val srcIndex = srcPos.toInt()
            val frac = srcPos - srcIndex

            output[i] = if (srcIndex + 1 < sampleCount) {
                val a = inputSamples[srcIndex].toDouble()
                val b = inputSamples[srcIndex + 1].toDouble()
                (a + frac * (b - a)).toInt().coerceIn(Short.MIN_VALUE.toInt(), Short.MAX_VALUE.toInt()).toShort()
            } else if (srcIndex < sampleCount) {
                inputSamples[srcIndex]
            } else {
                0
            }
        }

        val outputBuffer = ByteBuffer.allocate(outputCount * 2).order(ByteOrder.LITTLE_ENDIAN)
        output.forEach { outputBuffer.putShort(it) }
        return outputBuffer.array()
    }

    private fun wrapPCM16AsWAV(pcmData: ByteArray, sampleRate: Int): ByteArray {
        val channels: Short = 1
        val bitsPerSample: Short = 16
        val byteRate = sampleRate * channels * (bitsPerSample / 8)
        val blockAlign = (channels * (bitsPerSample / 8)).toShort()
        val dataSize = pcmData.size
        val chunkSize = 36 + dataSize

        val buffer = ByteBuffer.allocate(44 + pcmData.size).order(ByteOrder.LITTLE_ENDIAN)
        // RIFF header
        buffer.put("RIFF".toByteArray())
        buffer.putInt(chunkSize)
        buffer.put("WAVE".toByteArray())
        // fmt subchunk
        buffer.put("fmt ".toByteArray())
        buffer.putInt(16) // subchunk1 size
        buffer.putShort(1) // PCM format
        buffer.putShort(channels)
        buffer.putInt(sampleRate)
        buffer.putInt(byteRate)
        buffer.putShort(blockAlign)
        buffer.putShort(bitsPerSample)
        // data subchunk
        buffer.put("data".toByteArray())
        buffer.putInt(dataSize)
        buffer.put(pcmData)

        return buffer.array()
    }

    // ── Consolidated Tool Definitions for GLM ──
    // GLM can only handle ~10 tools. Consolidate 28+ tools into 8 category tools.

    private fun buildConsolidatedTools(): JsonElement {
        return buildJsonArray {
            addJsonObject {
                put("type", JsonPrimitive("function"))
                put("name", JsonPrimitive("location_weather"))
                put("description", JsonPrimitive("Location and weather tools. Actions: get_location (get device GPS), geocode (address to coordinates, needs: address), weather (get weather, optional: latitude, longitude, label), nearby (search nearby places, needs: query)"))
                putJsonObject("parameters") {
                    put("type", JsonPrimitive("object"))
                    putJsonObject("properties") {
                        putJsonObject("action") { put("type", JsonPrimitive("string")); put("description", JsonPrimitive("One of: get_location, geocode, weather, nearby")) }
                        putJsonObject("address") { put("type", JsonPrimitive("string")); put("description", JsonPrimitive("For geocode: place name or address")) }
                        putJsonObject("latitude") { put("type", JsonPrimitive("number")); put("description", JsonPrimitive("For weather: latitude")) }
                        putJsonObject("longitude") { put("type", JsonPrimitive("number")); put("description", JsonPrimitive("For weather: longitude")) }
                        putJsonObject("label") { put("type", JsonPrimitive("string")); put("description", JsonPrimitive("For weather: location label")) }
                        putJsonObject("query") { put("type", JsonPrimitive("string")); put("description", JsonPrimitive("For nearby: search query")) }
                    }
                    putJsonArray("required") { add(JsonPrimitive("action")) }
                }
            }
            addJsonObject {
                put("type", JsonPrimitive("function"))
                put("name", JsonPrimitive("calendar_reminders"))
                put("description", JsonPrimitive("Calendar, reminders and alarms. Actions: list_events (needs: start_date, end_date), create_event (needs: title, start_date; optional: end_date, location, notes, all_day), list_reminders (optional: include_completed), create_reminder (needs: title; optional: due_date, notes, priority), set_alarm (needs: time in HH:mm; optional: title)"))
                putJsonObject("parameters") {
                    put("type", JsonPrimitive("object"))
                    putJsonObject("properties") {
                        putJsonObject("action") { put("type", JsonPrimitive("string")); put("description", JsonPrimitive("One of: list_events, create_event, list_reminders, create_reminder, set_alarm")) }
                        putJsonObject("title") { put("type", JsonPrimitive("string")); put("description", JsonPrimitive("Event/reminder/alarm title")) }
                        putJsonObject("start_date") { put("type", JsonPrimitive("string")); put("description", JsonPrimitive("Start date ISO-8601 with timezone e.g. 2026-04-12T09:00:00+08:00")) }
                        putJsonObject("end_date") { put("type", JsonPrimitive("string")); put("description", JsonPrimitive("End date ISO-8601")) }
                        putJsonObject("location") { put("type", JsonPrimitive("string")); put("description", JsonPrimitive("Event location")) }
                        putJsonObject("notes") { put("type", JsonPrimitive("string")); put("description", JsonPrimitive("Notes")) }
                        putJsonObject("all_day") { put("type", JsonPrimitive("boolean")); put("description", JsonPrimitive("All-day event flag")) }
                        putJsonObject("due_date") { put("type", JsonPrimitive("string")); put("description", JsonPrimitive("Reminder due date ISO-8601")) }
                        putJsonObject("priority") { put("type", JsonPrimitive("integer")); put("description", JsonPrimitive("Reminder priority 1-9")) }
                        putJsonObject("time") { put("type", JsonPrimitive("string")); put("description", JsonPrimitive("Alarm time HH:mm")) }
                        putJsonObject("include_completed") { put("type", JsonPrimitive("boolean")); put("description", JsonPrimitive("Include completed reminders")) }
                    }
                    putJsonArray("required") { add(JsonPrimitive("action")) }
                }
            }
            addJsonObject {
                put("type", JsonPrimitive("function"))
                put("name", JsonPrimitive("contacts_communication"))
                put("description", JsonPrimitive("Contacts, notifications and URLs. Actions: search_contacts (needs: query), send_notification (needs: title; optional: body, delay_seconds), open_url (needs: url)"))
                putJsonObject("parameters") {
                    put("type", JsonPrimitive("object"))
                    putJsonObject("properties") {
                        putJsonObject("action") { put("type", JsonPrimitive("string")); put("description", JsonPrimitive("One of: search_contacts, send_notification, open_url")) }
                        putJsonObject("query") { put("type", JsonPrimitive("string")); put("description", JsonPrimitive("Contact search query")) }
                        putJsonObject("title") { put("type", JsonPrimitive("string")); put("description", JsonPrimitive("Notification title")) }
                        putJsonObject("body") { put("type", JsonPrimitive("string")); put("description", JsonPrimitive("Notification body")) }
                        putJsonObject("delay_seconds") { put("type", JsonPrimitive("integer")); put("description", JsonPrimitive("Notification delay in seconds")) }
                        putJsonObject("url") { put("type", JsonPrimitive("string")); put("description", JsonPrimitive("URL to open")) }
                    }
                    putJsonArray("required") { add(JsonPrimitive("action")) }
                }
            }
            addJsonObject {
                put("type", JsonPrimitive("function"))
                put("name", JsonPrimitive("web_search"))
                put("description", JsonPrimitive("Web search and browsing. Actions: search (needs: query), read_page (needs: url), open_browser (needs: url)"))
                putJsonObject("parameters") {
                    put("type", JsonPrimitive("object"))
                    putJsonObject("properties") {
                        putJsonObject("action") { put("type", JsonPrimitive("string")); put("description", JsonPrimitive("One of: search, read_page, open_browser")) }
                        putJsonObject("query") { put("type", JsonPrimitive("string")); put("description", JsonPrimitive("Search query")) }
                        putJsonObject("url") { put("type", JsonPrimitive("string")); put("description", JsonPrimitive("URL to read or open")) }
                    }
                    putJsonArray("required") { add(JsonPrimitive("action")) }
                }
            }
            addJsonObject {
                put("type", JsonPrimitive("function"))
                put("name", JsonPrimitive("files_memory"))
                put("description", JsonPrimitive("File operations, memory and todo. Actions: read_file (needs: path), write_file (needs: path, content), memory_get (needs: key), memory_write (needs: key, value), todo (needs: action: list/add/complete/delete; optional: title, id)"))
                putJsonObject("parameters") {
                    put("type", JsonPrimitive("object"))
                    putJsonObject("properties") {
                        putJsonObject("action") { put("type", JsonPrimitive("string")); put("description", JsonPrimitive("One of: read_file, write_file, memory_get, memory_write, todo")) }
                        putJsonObject("path") { put("type", JsonPrimitive("string")); put("description", JsonPrimitive("File path")) }
                        putJsonObject("content") { put("type", JsonPrimitive("string")); put("description", JsonPrimitive("File content to write")) }
                        putJsonObject("key") { put("type", JsonPrimitive("string")); put("description", JsonPrimitive("Memory key")) }
                        putJsonObject("value") { put("type", JsonPrimitive("string")); put("description", JsonPrimitive("Memory value")) }
                        putJsonObject("title") { put("type", JsonPrimitive("string")); put("description", JsonPrimitive("Todo title")) }
                        putJsonObject("id") { put("type", JsonPrimitive("string")); put("description", JsonPrimitive("Todo ID")) }
                    }
                    putJsonArray("required") { add(JsonPrimitive("action")) }
                }
            }
            addJsonObject {
                put("type", JsonPrimitive("function"))
                put("name", JsonPrimitive("code_execute"))
                put("description", JsonPrimitive("Execute code on device. Actions: shell (needs: command), python (needs: code)"))
                putJsonObject("parameters") {
                    put("type", JsonPrimitive("object"))
                    putJsonObject("properties") {
                        putJsonObject("action") { put("type", JsonPrimitive("string")); put("description", JsonPrimitive("One of: shell, python")) }
                        putJsonObject("command") { put("type", JsonPrimitive("string")); put("description", JsonPrimitive("Shell command")) }
                        putJsonObject("code") { put("type", JsonPrimitive("string")); put("description", JsonPrimitive("Python code to execute")) }
                    }
                    putJsonArray("required") { add(JsonPrimitive("action")) }
                }
            }
            addJsonObject {
                put("type", JsonPrimitive("function"))
                put("name", JsonPrimitive("media_capture"))
                put("description", JsonPrimitive("Camera and photo. Actions: camera (take photo), photo_pick (pick from library), file_pick (pick any file)"))
                putJsonObject("parameters") {
                    put("type", JsonPrimitive("object"))
                    putJsonObject("properties") {
                        putJsonObject("action") { put("type", JsonPrimitive("string")); put("description", JsonPrimitive("One of: camera, photo_pick, file_pick")) }
                    }
                    putJsonArray("required") { add(JsonPrimitive("action")) }
                }
            }
            addJsonObject {
                put("type", JsonPrimitive("function"))
                put("name", JsonPrimitive("delegate_task"))
                put("description", JsonPrimitive("Delegate a complex multi-step task to a background agent that can use ALL tools. Use when the task requires multiple steps, combining info from different sources, or deep analysis."))
                putJsonObject("parameters") {
                    put("type", JsonPrimitive("object"))
                    putJsonObject("properties") {
                        putJsonObject("task") { put("type", JsonPrimitive("string")); put("description", JsonPrimitive("Detailed task description for the agent")) }
                    }
                    putJsonArray("required") { add(JsonPrimitive("task")) }
                }
            }
        }
    }
}

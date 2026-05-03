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
import android.media.AudioAttributes
import android.media.AudioFocusRequest
import android.media.AudioManager
import com.xnu.rocky.providers.RealtimeProviderConfiguration
import com.xnu.rocky.providers.TurnDetection
import com.xnu.rocky.runtime.CharacterStore
import com.xnu.rocky.runtime.LogManager
import com.xnu.rocky.runtime.tools.Toolbox
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.serialization.json.*
import okhttp3.*
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.RequestBody.Companion.toRequestBody
import org.webrtc.*
import org.webrtc.audio.JavaAudioDeviceModule
import java.nio.ByteBuffer

class OpenAIRealtimeVoiceClient(
    private val config: RealtimeProviderConfiguration,
    private val toolbox: Toolbox,
    private val characterStore: CharacterStore,
    private val context: Context
) : RealtimeVoiceClient {

    companion object {
        private const val TAG = "OpenAIWebRTC"
    }

    private var peerConnection: PeerConnection? = null
    private var dataChannel: DataChannel? = null
    private var factory: PeerConnectionFactory? = null
    private var audioSource: AudioSource? = null
    private var localAudioTrack: AudioTrack? = null
    private val json = Json { ignoreUnknownKeys = true }
    private val httpClient = OkHttpClient()

    @Volatile
    private var emitEvent: ((RealtimeEvent) -> Unit)? = null

    /** Tracked from `response.output_item.added`. Truncate frames must reference it. */
    @Volatile
    private var currentAssistantItemId: String? = null
    /** Wall-clock anchor for "how much assistant audio has played" — set when audio first arrives. */
    @Volatile
    private var assistantAudioStartMs: Long = 0L
    /**
     * True between `response.created` and `response.done`. Gates `cancelResponse()`
     * so we don't fire `response.cancel` when nothing is in flight — the server
     * returns `response_cancel_not_active` and (more importantly) the race with the
     * server's own `turn_detected` cancel makes subsequent `speech_stopped`
     * events stop firing. Mirrors iOS isResponseInProgress.
     */
    @Volatile
    private var isResponseInProgress: Boolean = false

    /**
     * Audio focus request held for the lifetime of a connected session. Without
     * this, phone calls / Google Assistant / nav prompts silently leave our mic
     * streaming over WebRTC while the user can't hear or speak.
     */
    private var audioFocusRequest: AudioFocusRequest? = null
    /**
     * Wall-clock time when the most recent transient audio-focus loss began.
     * Drives the auto-resume window: a brief blip (≤10s, e.g. CarPlay nav prompt)
     * resumes silently; a long one (real phone call) waits for an explicit tap.
     * Mirrors iOS interruptionStartedAt.
     */
    @Volatile
    private var interruptionStartedAtMs: Long = 0L

    override suspend fun connect(): Flow<RealtimeEvent> = callbackFlow {
        emitEvent = { event -> trySend(event) }

        trySend(RealtimeEvent.Status("Initializing WebRTC..."))

        // Initialize WebRTC
        val initOptions = PeerConnectionFactory.InitializationOptions.builder(context)
            .setFieldTrials("")
            .createInitializationOptions()
        PeerConnectionFactory.initialize(initOptions)

        val audioDeviceModule = JavaAudioDeviceModule.builder(context).createAudioDeviceModule()
        factory = PeerConnectionFactory.builder()
            .setAudioDeviceModule(audioDeviceModule)
            .createPeerConnectionFactory()

        val pcFactory = factory!!

        // Create audio source and track from microphone
        val audioConstraints = MediaConstraints().apply {
            mandatory.add(MediaConstraints.KeyValuePair("echoCancellation", "true"))
            mandatory.add(MediaConstraints.KeyValuePair("noiseSuppression", "true"))
            mandatory.add(MediaConstraints.KeyValuePair("autoGainControl", "true"))
        }
        audioSource = pcFactory.createAudioSource(audioConstraints)
        localAudioTrack = pcFactory.createAudioTrack("audio0", audioSource)
        localAudioTrack?.setEnabled(true)

        // Create PeerConnection
        val rtcConfig = PeerConnection.RTCConfiguration(emptyList()).apply {
            sdpSemantics = PeerConnection.SdpSemantics.UNIFIED_PLAN
            bundlePolicy = PeerConnection.BundlePolicy.MAXBUNDLE
        }

        val pcObserver = object : PeerConnection.Observer {
            override fun onSignalingChange(state: PeerConnection.SignalingState?) {}
            override fun onIceConnectionChange(state: PeerConnection.IceConnectionState?) {
                LogManager.info("ICE connection state: $state", TAG)
                when (state) {
                    PeerConnection.IceConnectionState.DISCONNECTED -> {
                        emitEvent?.invoke(RealtimeEvent.ErrorDetailed(VoiceError(
                            severity = VoiceErrorSeverity.Transient,
                            message = "WebRTC disconnected — retrying…",
                            actionHint = VoiceErrorAction.Retry
                        )))
                    }
                    PeerConnection.IceConnectionState.FAILED -> {
                        emitEvent?.invoke(RealtimeEvent.ErrorDetailed(VoiceError(
                            severity = VoiceErrorSeverity.Fatal,
                            message = "WebRTC connection failed",
                            actionHint = VoiceErrorAction.Retry
                        )))
                    }
                    else -> {}
                }
            }
            override fun onIceConnectionReceivingChange(receiving: Boolean) {}
            override fun onIceGatheringChange(state: PeerConnection.IceGatheringState?) {}
            override fun onIceCandidate(candidate: IceCandidate?) {}
            override fun onIceCandidatesRemoved(candidates: Array<out IceCandidate>?) {}
            override fun onAddStream(stream: MediaStream?) {}
            override fun onRemoveStream(stream: MediaStream?) {}
            override fun onDataChannel(dc: DataChannel?) {}
            override fun onRenegotiationNeeded() {}
            override fun onAddTrack(receiver: RtpReceiver?, streams: Array<out MediaStream>?) {
                // Remote audio track from OpenAI — WebRTC plays it automatically
                LogManager.info("Remote track received, AI audio will play automatically", TAG)
            }
            override fun onTrack(transceiver: RtpTransceiver?) {
                LogManager.info("onTrack: remote media track added", TAG)
            }
        }

        peerConnection = pcFactory.createPeerConnection(rtcConfig, pcObserver)
        val pc = peerConnection ?: run {
            trySend(RealtimeEvent.Error("Failed to create PeerConnection"))
            close()
            return@callbackFlow
        }

        // Request audio focus so phone calls / nav prompts / Google Assistant
        // notify us when they take over the audio device. The focus listener
        // mutes the mic + cancels in-flight responses on transient loss, then
        // auto-resumes if iOS-equivalent shouldResume conditions hold.
        requestAudioFocus()

        // Add local audio track to peer connection
        pc.addTrack(localAudioTrack, listOf("audio-stream"))

        // Create DataChannel for events
        val dcInit = DataChannel.Init().apply {
            ordered = true
        }
        dataChannel = pc.createDataChannel("oai-events", dcInit)
        dataChannel?.registerObserver(object : DataChannel.Observer {
            override fun onBufferedAmountChange(amount: Long) {}
            override fun onStateChange() {
                LogManager.info("DataChannel state: ${dataChannel?.state()}", TAG)
                if (dataChannel?.state() == DataChannel.State.OPEN) {
                    configureSession()
                }
            }
            override fun onMessage(buffer: DataChannel.Buffer) {
                val data = ByteArray(buffer.data.remaining())
                buffer.data.get(data)
                val text = String(data, Charsets.UTF_8)
                handleServerEvent(text)
            }
        })

        trySend(RealtimeEvent.Status("Creating WebRTC offer..."))

        // Create SDP offer
        val offerConstraints = MediaConstraints().apply {
            mandatory.add(MediaConstraints.KeyValuePair("OfferToReceiveAudio", "true"))
            mandatory.add(MediaConstraints.KeyValuePair("OfferToReceiveVideo", "false"))
        }

        pc.createOffer(object : SdpObserver {
            override fun onCreateSuccess(sdp: SessionDescription?) {
                if (sdp == null) {
                    emitEvent?.invoke(RealtimeEvent.Error("Failed to create SDP offer"))
                    return
                }
                pc.setLocalDescription(object : SdpObserver {
                    override fun onSetSuccess() {
                        postSdpOffer(sdp.description, pc)
                    }
                    override fun onSetFailure(error: String?) {
                        emitEvent?.invoke(RealtimeEvent.Error("Failed to set local description: $error"))
                    }
                    override fun onCreateSuccess(sdp: SessionDescription?) {}
                    override fun onCreateFailure(error: String?) {}
                }, sdp)
            }
            override fun onCreateFailure(error: String?) {
                emitEvent?.invoke(RealtimeEvent.Error("Failed to create offer: $error"))
            }
            override fun onSetSuccess() {}
            override fun onSetFailure(error: String?) {}
        }, offerConstraints)

        awaitClose { cleanupWebRTC() }
    }

    private fun postSdpOffer(offerSdp: String, pc: PeerConnection) {
        emitEvent?.invoke(RealtimeEvent.Status("Connecting to OpenAI Realtime..."))

        val baseUrl = if (config.customHost.isNotBlank()) {
            config.customHost.trimEnd('/')
                .replace("wss://", "https://")
                .replace("ws://", "http://")
        } else {
            "https://api.openai.com/v1/realtime"
        }
        val url = "$baseUrl?model=${config.modelID}"

        val request = Request.Builder()
            .url(url)
            .header("Authorization", "Bearer ${config.credential}")
            .header("Content-Type", "application/sdp")
            .post(offerSdp.toRequestBody("application/sdp".toMediaType()))
            .build()

        httpClient.newCall(request).enqueue(object : Callback {
            override fun onFailure(call: Call, e: java.io.IOException) {
                emitEvent?.invoke(RealtimeEvent.Error("SDP exchange failed: ${e.message}"))
            }

            override fun onResponse(call: Call, response: Response) {
                if (!response.isSuccessful) {
                    val body = response.body?.string() ?: ""
                    val detail = when (response.code) {
                        401, 403 -> VoiceError(
                            severity = VoiceErrorSeverity.UserAction,
                            message = "OpenAI authentication failed (${response.code}). Check your API key.",
                            actionHint = VoiceErrorAction.ReconfigureProvider
                        )
                        in 500..599 -> VoiceError(
                            severity = VoiceErrorSeverity.Transient,
                            message = "OpenAI server error (${response.code}). Retrying may help.",
                            actionHint = VoiceErrorAction.Retry
                        )
                        else -> VoiceError(
                            severity = VoiceErrorSeverity.Fatal,
                            message = "OpenAI returned ${response.code}: $body",
                            actionHint = VoiceErrorAction.ReconfigureProvider
                        )
                    }
                    emitEvent?.invoke(RealtimeEvent.ErrorDetailed(detail))
                    return
                }

                val answerSdp = response.body?.string() ?: ""
                val answer = SessionDescription(SessionDescription.Type.ANSWER, answerSdp)
                pc.setRemoteDescription(object : SdpObserver {
                    override fun onSetSuccess() {
                        LogManager.info("Remote SDP answer set successfully", TAG)
                        emitEvent?.invoke(RealtimeEvent.Status("Connected to OpenAI Realtime via WebRTC"))
                    }
                    override fun onSetFailure(error: String?) {
                        emitEvent?.invoke(RealtimeEvent.Error("Failed to set remote description: $error"))
                    }
                    override fun onCreateSuccess(sdp: SessionDescription?) {}
                    override fun onCreateFailure(error: String?) {}
                }, answer)
            }
        })
    }

    private fun handleServerEvent(text: String) {
        try {
            val event = json.parseToJsonElement(text).jsonObject
            val type = event["type"]?.jsonPrimitive?.contentOrNull ?: return

            when (type) {
                "session.created", "session.updated" -> {
                    emitEvent?.invoke(RealtimeEvent.SessionReady(
                        model = config.modelID,
                        features = RealtimeVoiceFeatures(
                            supportsTextInput = true,
                            supportsAssistantStreaming = true,
                            supportsToolCalls = true,
                            supportsAudioOutput = true,
                            needsMicSuspension = false
                        )
                    ))
                }
                "input_audio_buffer.speech_started" -> {
                    // Server-VAD path only: when the server detects speech mid-response
                    // it auto-cancels the in-flight turn with `turn_detected` and stops
                    // streaming audio. Sending our own `response.cancel` + truncate
                    // here races that — the cancel hits `response_cancel_not_active`
                    // and, more importantly, the server stops firing `speech_stopped`
                    // for subsequent utterances ("only hears me speak once" symptom).
                    // The remote audio track quiets naturally as the server stops
                    // generating, so we don't need to silence anything client-side.
                    emitEvent?.invoke(RealtimeEvent.InputSpeechStarted)
                }
                "response.created" -> {
                    isResponseInProgress = true
                }
                "response.output_item.added" -> {
                    val item = event["item"]?.jsonObject
                    val itemId = item?.get("id")?.jsonPrimitive?.contentOrNull
                    val role = item?.get("role")?.jsonPrimitive?.contentOrNull
                    if (role == "assistant" && itemId != null) {
                        currentAssistantItemId = itemId
                        assistantAudioStartMs = System.currentTimeMillis()
                    }
                }
                "response.done" -> {
                    val response = event["response"]?.jsonObject
                    val usage = response?.get("usage")?.jsonObject
                    if (usage != null) {
                        val inputTokens = usage["input_tokens"]?.jsonPrimitive?.intOrNull ?: 0
                        val outputTokens = usage["output_tokens"]?.jsonPrimitive?.intOrNull ?: 0
                        val totalTokens = usage["total_tokens"]?.jsonPrimitive?.intOrNull ?: (inputTokens + outputTokens)
                        val inputAudio = usage["input_token_details"]?.jsonObject
                            ?.get("audio_tokens")?.jsonPrimitive?.intOrNull ?: 0
                        val outputAudio = usage["output_token_details"]?.jsonObject
                            ?.get("audio_tokens")?.jsonPrimitive?.intOrNull ?: 0
                        emitEvent?.invoke(RealtimeEvent.UsageReported(
                            inputTokens = inputTokens,
                            outputTokens = outputTokens,
                            totalTokens = totalTokens,
                            inputAudioTokens = inputAudio,
                            outputAudioTokens = outputAudio
                        ))
                    }
                    currentAssistantItemId = null
                    isResponseInProgress = false
                }
                "conversation.item.input_audio_transcription.completed" -> {
                    val transcript = event["transcript"]?.jsonPrimitive?.contentOrNull ?: ""
                    emitEvent?.invoke(RealtimeEvent.UserTranscriptFinal(transcript))
                }
                "response.audio_transcript.delta" -> {
                    val delta = event["delta"]?.jsonPrimitive?.contentOrNull ?: ""
                    emitEvent?.invoke(RealtimeEvent.AssistantTranscriptDelta(delta))
                }
                "response.audio_transcript.done" -> {
                    val transcript = event["transcript"]?.jsonPrimitive?.contentOrNull ?: ""
                    emitEvent?.invoke(RealtimeEvent.AssistantTranscriptFinal(transcript))
                }
                "response.audio.done" -> {
                    emitEvent?.invoke(RealtimeEvent.AssistantAudioDone)
                }
                "response.function_call_arguments.done" -> {
                    val name = event["name"]?.jsonPrimitive?.contentOrNull ?: ""
                    val arguments = event["arguments"]?.jsonPrimitive?.contentOrNull ?: ""
                    val callId = event["call_id"]?.jsonPrimitive?.contentOrNull ?: ""
                    emitEvent?.invoke(RealtimeEvent.ToolCallRequested(name, arguments, callId))
                }
                "error" -> {
                    val error = event["error"]?.jsonObject
                    val message = error?.get("message")?.jsonPrimitive?.contentOrNull ?: "Unknown error"
                    emitEvent?.invoke(RealtimeEvent.Error(message))
                }
            }
        } catch (e: Exception) {
            LogManager.error("OpenAI Realtime parse error: ${e.message}", TAG)
        }
    }

    private fun configureSession() {
        val systemPrompt = characterStore.voiceSystemPrompt()
        val toolDefs = toolbox.realtimeToolDefinitions()
        val advanced = config.advancedSettings

        val sessionConfig = buildJsonObject {
            put("type", JsonPrimitive("session.update"))
            putJsonObject("session") {
                put("instructions", JsonPrimitive(systemPrompt))
                put("voice", JsonPrimitive(config.openaiVoice))
                putJsonArray("modalities") {
                    add(JsonPrimitive("text"))
                    if (!advanced.allowTextOnly) add(JsonPrimitive("audio"))
                }
                putJsonObject("input_audio_transcription") {
                    put("model", JsonPrimitive(advanced.transcriptionModel))
                    advanced.inputLanguage?.takeIf { it.isNotBlank() }?.let {
                        put("language", JsonPrimitive(it))
                    }
                }
                putJsonObject("turn_detection") {
                    when (val td = advanced.turnDetection) {
                        is TurnDetection.Semantic -> {
                            put("type", JsonPrimitive("semantic_vad"))
                            put("eagerness", JsonPrimitive(td.eagerness))
                        }
                        is TurnDetection.Server -> {
                            put("type", JsonPrimitive("server_vad"))
                            put("threshold", JsonPrimitive(td.threshold))
                            put("prefix_padding_ms", JsonPrimitive(td.prefixPaddingMs))
                            put("silence_duration_ms", JsonPrimitive(td.silenceDurationMs))
                        }
                    }
                }
                put("temperature", JsonPrimitive(advanced.temperature))
                put("max_response_output_tokens", JsonPrimitive(advanced.maxOutputTokens))
                put("speed", JsonPrimitive(advanced.speed))
                if (toolDefs.isNotEmpty()) {
                    putJsonArray("tools") {
                        for (tool in toolDefs) {
                            addJsonObject {
                                put("type", JsonPrimitive("function"))
                                put("name", JsonPrimitive(tool.function.name))
                                put("description", JsonPrimitive(tool.function.description))
                                put("parameters", tool.function.parameters)
                            }
                        }
                    }
                }
            }
        }
        sendDataChannelMessage(sessionConfig.toString())
    }

    private fun sendDataChannelMessage(message: String) {
        val dc = dataChannel ?: return
        if (dc.state() != DataChannel.State.OPEN) {
            LogManager.warning("DataChannel not open, cannot send message", TAG)
            return
        }
        val buffer = ByteBuffer.wrap(message.toByteArray(Charsets.UTF_8))
        dc.send(DataChannel.Buffer(buffer, false))
    }

    override suspend fun disconnect() {
        cleanupWebRTC()
    }

    override suspend fun sendText(text: String) {
        val msg = buildJsonObject {
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
        sendDataChannelMessage(msg.toString())
        val respond = buildJsonObject { put("type", JsonPrimitive("response.create")) }
        sendDataChannelMessage(respond.toString())
    }

    override suspend fun sendAudioChunk(data: ByteArray) {
        // No-op: WebRTC handles audio capture automatically via the local audio track
    }

    override suspend fun finishAudioInput() {
        // No-op: WebRTC with server VAD handles turn detection automatically
    }

    override suspend fun sendToolOutput(callID: String, output: String) {
        val msg = buildJsonObject {
            put("type", JsonPrimitive("conversation.item.create"))
            putJsonObject("item") {
                put("type", JsonPrimitive("function_call_output"))
                put("call_id", JsonPrimitive(callID))
                put("output", JsonPrimitive(output))
            }
        }
        sendDataChannelMessage(msg.toString())
        val respond = buildJsonObject { put("type", JsonPrimitive("response.create")) }
        sendDataChannelMessage(respond.toString())
    }

    override suspend fun speakText(text: String) {
        sendText(text)
    }

    override suspend fun cancelResponse() {
        // Idempotent guard: only fire if the server thinks a response is in
        // flight. Without this, calling cancel between turns returns
        // `response_cancel_not_active` and (more importantly) can stall the
        // server's VAD state machine. See the input_audio_buffer.speech_started
        // handler for the full rationale.
        if (!isResponseInProgress) return
        sendCancelResponse()
    }

    override suspend fun truncateAssistantAudio(audioEndMs: Long) {
        val itemId = currentAssistantItemId ?: return
        sendTruncate(itemId, audioEndMs)
    }

    private fun sendCancelResponse() {
        sendDataChannelMessage(buildJsonObject { put("type", JsonPrimitive("response.cancel")) }.toString())
    }

    private fun sendTruncate(itemId: String, audioEndMs: Long) {
        val msg = buildJsonObject {
            put("type", JsonPrimitive("conversation.item.truncate"))
            put("item_id", JsonPrimitive(itemId))
            put("content_index", JsonPrimitive(0))
            put("audio_end_ms", JsonPrimitive(audioEndMs))
        }
        sendDataChannelMessage(msg.toString())
    }

    private fun cleanupWebRTC() {
        LogManager.info("Cleaning up WebRTC resources", TAG)
        abandonAudioFocus()
        dataChannel?.close()
        dataChannel = null
        peerConnection?.close()
        peerConnection = null
        localAudioTrack?.dispose()
        localAudioTrack = null
        audioSource?.dispose()
        audioSource = null
        factory?.dispose()
        factory = null
        emitEvent = null
    }

    // MARK: - Audio focus (interruption handling)

    /// Auto-resume cap: a brief audio-focus loss (CarPlay nav prompt, quick
    /// Google Assistant query) silently resumes; longer ones (a real phone call)
    /// require an explicit user tap so we don't surprise them with a hot mic
    /// after they've moved on. Mirrors iOS autoResumeMaxInterruptionDuration.
    private val autoResumeMaxInterruptionMs = 10_000L

    private val audioFocusListener = AudioManager.OnAudioFocusChangeListener { focusChange ->
        when (focusChange) {
            AudioManager.AUDIOFOCUS_LOSS_TRANSIENT,
            AudioManager.AUDIOFOCUS_LOSS_TRANSIENT_CAN_DUCK -> {
                // Phone call / Siri / CarPlay nav prompt took over. Mute the mic
                // (so we stop billing audio tokens for nothing the user can hear),
                // cancel any in-flight assistant response so we don't replay stale
                // audio on resume, and time the blip for the auto-resume decision.
                LogManager.info("AudioFocus transient loss — pausing voice", TAG)
                interruptionStartedAtMs = System.currentTimeMillis()
                localAudioTrack?.setEnabled(false)
                if (isResponseInProgress) sendCancelResponse()
                emitEvent?.invoke(RealtimeEvent.Status("Voice session interrupted."))
            }
            AudioManager.AUDIOFOCUS_LOSS -> {
                // Permanent loss — Google Assistant fully launched, user-initiated
                // music app, etc. Drop focus and wait for an explicit re-engage.
                LogManager.info("AudioFocus permanent loss — waiting for tap", TAG)
                localAudioTrack?.setEnabled(false)
                if (isResponseInProgress) sendCancelResponse()
                emitEvent?.invoke(RealtimeEvent.Status("Voice session paused. Tap to resume."))
                abandonAudioFocus()
            }
            AudioManager.AUDIOFOCUS_GAIN -> {
                val elapsed = if (interruptionStartedAtMs > 0L) {
                    System.currentTimeMillis() - interruptionStartedAtMs
                } else {
                    Long.MAX_VALUE
                }
                interruptionStartedAtMs = 0L
                if (elapsed <= autoResumeMaxInterruptionMs) {
                    LogManager.info("AudioFocus auto-resuming after ${elapsed}ms", TAG)
                    localAudioTrack?.setEnabled(true)
                    emitEvent?.invoke(RealtimeEvent.Status("Voice resumed."))
                } else {
                    LogManager.info("AudioFocus regained after ${elapsed}ms — waiting for tap", TAG)
                    emitEvent?.invoke(RealtimeEvent.Status("Voice session paused. Tap to resume."))
                }
            }
        }
    }

    private fun requestAudioFocus() {
        val am = context.getSystemService(Context.AUDIO_SERVICE) as? AudioManager ?: return
        val attrs = AudioAttributes.Builder()
            .setUsage(AudioAttributes.USAGE_VOICE_COMMUNICATION)
            .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH)
            .build()
        val request = AudioFocusRequest.Builder(AudioManager.AUDIOFOCUS_GAIN)
            .setAudioAttributes(attrs)
            .setAcceptsDelayedFocusGain(true)
            .setOnAudioFocusChangeListener(audioFocusListener)
            .build()
        audioFocusRequest = request
        am.requestAudioFocus(request)
    }

    private fun abandonAudioFocus() {
        val am = context.getSystemService(Context.AUDIO_SERVICE) as? AudioManager ?: return
        audioFocusRequest?.let { am.abandonAudioFocusRequest(it) }
        audioFocusRequest = null
    }
}

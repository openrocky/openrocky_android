//
// OpenRocky — Voice-first AI Agent
// https://github.com/openrocky
//
// Developed by everettjf with the assistance of Claude Code and Codex.
// Date: 2026-04-14
// Copyright (c) 2026 everettjf. All rights reserved.
//

package com.xnu.rocky.runtime.voice

import android.content.Context
import android.media.AudioFormat
import android.media.AudioRecord
import android.media.MediaPlayer
import android.media.MediaRecorder
import com.xnu.rocky.providers.ChatMessage
import com.xnu.rocky.providers.ProviderConfiguration
import com.xnu.rocky.providers.STTProviderConfiguration
import com.xnu.rocky.providers.TTSProviderConfiguration
import com.xnu.rocky.runtime.CharacterStore
import com.xnu.rocky.runtime.ChatInferenceRuntime
import com.xnu.rocky.runtime.LogManager
import com.xnu.rocky.runtime.Preferences
import com.xnu.rocky.runtime.tools.Toolbox
import com.xnu.rocky.runtime.voice.stt.STTClientException
import com.xnu.rocky.runtime.voice.stt.STTClientFactory
import com.xnu.rocky.runtime.voice.tts.TTSClientException
import com.xnu.rocky.runtime.voice.tts.TTSClientFactory
import kotlinx.coroutines.*
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import java.io.ByteArrayOutputStream
import java.io.File
import kotlin.math.sqrt

/**
 * Classic voice pipeline: Mic → VAD → STT → Chat → TTS → Speaker.
 * Emits the same RealtimeEvent stream as RealtimeVoiceBridge so the session runtime
 * and UI layer can stay agnostic.
 */
class TraditionalVoiceBridge(
    private val context: Context,
    private val sttConfig: STTProviderConfiguration,
    private val ttsConfig: TTSProviderConfiguration,
    private val chatConfig: ProviderConfiguration,
    private val toolbox: Toolbox,
    private val characterStore: CharacterStore,
    private val preferences: Preferences
) {
    companion object {
        private const val TAG = "ClassicBridge"
        private const val SAMPLE_RATE = 16000
        private const val CHUNK_MS = 100
        private const val VAD_SPEECH_THRESHOLD = 600.0
        private const val VAD_BARGE_IN_THRESHOLD = 2500.0
        private const val VAD_SILENCE_CHUNKS = 12
        private const val BARGE_IN_CONFIRM_CHUNKS = 2
    }

    private val sttClient = STTClientFactory.make(sttConfig)
    private val ttsClient = TTSClientFactory.make(ttsConfig)
    private val inferenceRuntime = ChatInferenceRuntime(toolbox)
    private val chatHistory = mutableListOf<ChatMessage>()

    private var scope: CoroutineScope? = null
    private var recorder: AudioRecord? = null
    private var mediaPlayer: MediaPlayer? = null

    @Volatile private var isSpeaking = false
    @Volatile private var silentChunks = 0
    @Volatile private var isProcessing = false
    @Volatile private var isPlayingTTS = false
    @Volatile private var bargeInSignal = false
    @Volatile private var bargeInChunks = 0

    private val micBuffer = ByteArrayOutputStream()

    fun start(): Flow<RealtimeEvent> = callbackFlow {
        val sendEvent: (RealtimeEvent) -> Unit = { event -> trySend(event) }
        val bridgeScope = CoroutineScope(Dispatchers.Default + SupervisorJob())
        scope = bridgeScope

        sendEvent(RealtimeEvent.Status("Classic voice session starting..."))
        sendEvent(RealtimeEvent.SessionReady(
            model = "STT+Chat+TTS",
            features = RealtimeVoiceFeatures(
                supportsTextInput = true,
                supportsAssistantStreaming = true,
                supportsToolCalls = true,
                supportsAudioOutput = true,
                needsMicSuspension = true
            )
        ))
        sendEvent(RealtimeEvent.MicrophoneActive(true))

        val minBuf = AudioRecord.getMinBufferSize(SAMPLE_RATE, AudioFormat.CHANNEL_IN_MONO, AudioFormat.ENCODING_PCM_16BIT)
            .coerceAtLeast(4096)
        val rec = try {
            AudioRecord(
                MediaRecorder.AudioSource.VOICE_COMMUNICATION,
                SAMPLE_RATE,
                AudioFormat.CHANNEL_IN_MONO,
                AudioFormat.ENCODING_PCM_16BIT,
                minBuf * 2
            )
        } catch (e: SecurityException) {
            sendEvent(RealtimeEvent.ErrorDetailed(VoiceError(
                severity = VoiceErrorSeverity.UserAction,
                message = "Microphone permission denied",
                actionHint = VoiceErrorAction.OpenSettings
            )))
            close()
            return@callbackFlow
        }
        recorder = rec
        rec.startRecording()

        val chunkSize = SAMPLE_RATE * CHUNK_MS / 1000 * 2 // PCM16 = 2 bytes/sample

        bridgeScope.launch {
            val buf = ByteArray(chunkSize)
            try {
                while (isActive) {
                    val read = rec.read(buf, 0, buf.size)
                    if (read <= 0) { delay(10); continue }
                    val chunk = if (read == buf.size) buf else buf.copyOf(read)
                    handleMicChunk(chunk, sendEvent, bridgeScope)
                }
            } catch (e: Exception) {
                LogManager.error("Classic mic loop error: ${e.message}", TAG)
                sendEvent(RealtimeEvent.Error(e.message ?: "Mic error"))
            }
        }

        awaitClose {
            stopInternal()
        }
    }

    fun stop() {
        stopInternal()
    }

    private fun stopInternal() {
        runCatching { recorder?.stop() }
        runCatching { recorder?.release() }
        recorder = null
        runCatching { mediaPlayer?.stop() }
        runCatching { mediaPlayer?.release() }
        mediaPlayer = null
        scope?.cancel()
        scope = null
        micBuffer.reset()
        isSpeaking = false
        isProcessing = false
        isPlayingTTS = false
    }

    private fun handleMicChunk(pcm: ByteArray, send: (RealtimeEvent) -> Unit, scope: CoroutineScope) {
        val rms = computeRMS(pcm)

        // Barge-in: while TTS is playing, look for user speech above a higher threshold.
        if (isProcessing && isPlayingTTS) {
            val enabled = preferences.voiceInterruption.value
            if (enabled && rms > VAD_BARGE_IN_THRESHOLD) {
                bargeInChunks++
                if (bargeInChunks >= BARGE_IN_CONFIRM_CHUNKS) {
                    LogManager.info("Classic VAD: barge-in confirmed (rms=$rms)", TAG)
                    handleBargeIn(pcm, send)
                }
            } else {
                bargeInChunks = 0
            }
            return
        }

        if (isProcessing) return

        if (rms > VAD_SPEECH_THRESHOLD) {
            if (!isSpeaking) {
                isSpeaking = true
                micBuffer.reset()
                send(RealtimeEvent.InputSpeechStarted)
            }
            silentChunks = 0
            micBuffer.write(pcm)
        } else if (isSpeaking) {
            micBuffer.write(pcm)
            silentChunks++
            if (silentChunks >= VAD_SILENCE_CHUNKS) {
                isSpeaking = false
                silentChunks = 0
                val audioData = micBuffer.toByteArray()
                micBuffer.reset()
                scope.launch { processUserAudio(audioData, send) }
            }
        }
    }

    private fun handleBargeIn(pcm: ByteArray, send: (RealtimeEvent) -> Unit) {
        runCatching { mediaPlayer?.stop() }
        runCatching { mediaPlayer?.release() }
        mediaPlayer = null
        bargeInSignal = true
        isPlayingTTS = false
        isProcessing = false
        isSpeaking = true
        silentChunks = 0
        bargeInChunks = 0
        micBuffer.reset()
        micBuffer.write(pcm)
        send(RealtimeEvent.InputSpeechStarted)
        send(RealtimeEvent.Status("Listening..."))
    }

    private suspend fun processUserAudio(audioData: ByteArray, send: (RealtimeEvent) -> Unit) {
        if (audioData.size < SAMPLE_RATE / 2) {
            LogManager.info("VAD: audio too short (${audioData.size} bytes), ignoring", TAG)
            return
        }
        isProcessing = true
        send(RealtimeEvent.Status("Recognizing speech..."))
        try {
            val text = sttClient.transcribe(audioData).trim()
            if (text.isEmpty()) { isProcessing = false; return }
            send(RealtimeEvent.UserTranscriptDelta(text))
            send(RealtimeEvent.UserTranscriptFinal(text))
            runChatAndSpeak(text, send)
        } catch (e: STTClientException) {
            LogManager.error("Classic STT failed: ${e.message}", TAG)
            send(RealtimeEvent.Error("Speech recognition failed: ${e.message}"))
            isProcessing = false
        } catch (e: Exception) {
            LogManager.error("Classic STT failed: ${e.message}", TAG)
            send(RealtimeEvent.Error(e.message ?: "STT error"))
            isProcessing = false
        }
    }

    private suspend fun runChatAndSpeak(userText: String, send: (RealtimeEvent) -> Unit) {
        try {
            send(RealtimeEvent.Status("Thinking..."))
            chatHistory.add(ChatMessage(role = "user", content = userText))
            val systemPrompt = characterStore.systemPrompt(toolbox.toolDescriptions())
            val messages = mutableListOf(ChatMessage(role = "system", content = systemPrompt))
            messages.addAll(chatHistory)

            val response = StringBuilder()
            val reply = inferenceRuntime.runInference(
                config = chatConfig,
                messages = messages,
                tools = toolbox.chatToolDefinitions(),
                onDelta = { delta ->
                    response.append(delta)
                    send(RealtimeEvent.AssistantTranscriptDelta(delta))
                },
                onToolCall = { name, _ -> send(RealtimeEvent.ToolCallRequested(name, "", "")) },
                onToolResult = { _, _ -> },
                onUsage = { }
            )
            chatHistory.add(ChatMessage(role = "assistant", content = reply))
            send(RealtimeEvent.AssistantTranscriptFinal(reply))

            synthesizeAndPlay(reply, send)
        } catch (e: Exception) {
            LogManager.error("Classic chat failed: ${e.message}", TAG)
            send(RealtimeEvent.Error(e.message ?: "Chat error"))
            isProcessing = false
        }
    }

    private suspend fun synthesizeAndPlay(text: String, send: (RealtimeEvent) -> Unit) {
        val clean = stripMarkdown(text)
        if (clean.isBlank()) { isProcessing = false; return }
        val sentences = splitIntoSentences(clean).filter { it.isNotBlank() }
        if (sentences.isEmpty()) { isProcessing = false; return }

        isPlayingTTS = true
        bargeInSignal = false
        bargeInChunks = 0

        val cacheDir = context.cacheDir
        val playerScope = scope ?: return

        var nextAudio: Deferred<ByteArray?>? = playerScope.async {
            runCatching { ttsClient.synthesize(sentences[0]) }.getOrNull()
        }

        for (i in sentences.indices) {
            if (bargeInSignal) break
            val audio = nextAudio?.await()
            if (bargeInSignal) break
            nextAudio = if (i + 1 < sentences.size) {
                val nextText = sentences[i + 1]
                playerScope.async { runCatching { ttsClient.synthesize(nextText) }.getOrNull() }
            } else null

            if (audio != null) {
                playAudio(audio, cacheDir)
            }
            if (bargeInSignal) break
        }

        isPlayingTTS = false
        if (!bargeInSignal) {
            send(RealtimeEvent.AssistantAudioDone)
            send(RealtimeEvent.Status("Listening..."))
        }
        isProcessing = false
    }

    private suspend fun playAudio(data: ByteArray, cacheDir: File) = withContext(Dispatchers.IO) {
        val tempFile = File(cacheDir, "tts_${System.currentTimeMillis()}.mp3")
        try {
            tempFile.writeBytes(data)
            val player = MediaPlayer()
            mediaPlayer = player
            player.setDataSource(tempFile.absolutePath)
            player.prepare()
            player.start()
            while (player.isPlaying && !bargeInSignal) {
                delay(50)
            }
            runCatching { player.release() }
            mediaPlayer = null
        } catch (e: Exception) {
            LogManager.error("TTS playback failed: ${e.message}", TAG)
        } finally {
            tempFile.delete()
        }
    }

    private fun computeRMS(pcm: ByteArray): Double {
        if (pcm.isEmpty()) return 0.0
        var sum = 0.0
        var i = 0
        while (i + 1 < pcm.size) {
            val sample = ((pcm[i + 1].toInt() shl 8) or (pcm[i].toInt() and 0xff)).toShort().toInt()
            sum += (sample * sample).toDouble()
            i += 2
        }
        val count = pcm.size / 2
        if (count == 0) return 0.0
        return sqrt(sum / count)
    }

    private fun stripMarkdown(text: String): String = text
        .replace("**", "").replace("__", "").replace("```", "")
        .replace("`", "").replace("##", "").replace("#", "")

    private fun splitIntoSentences(text: String): List<String> {
        val out = mutableListOf<String>()
        val current = StringBuilder()
        for (c in text) {
            current.append(c)
            if (c in "。！？.!?\n") {
                val trimmed = current.toString().trim()
                if (trimmed.isNotEmpty()) out.add(trimmed)
                current.setLength(0)
            }
        }
        val tail = current.toString().trim()
        if (tail.isNotEmpty()) out.add(tail)
        return out
    }
}

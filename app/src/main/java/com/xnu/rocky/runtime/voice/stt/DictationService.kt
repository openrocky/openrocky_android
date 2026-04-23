//
// OpenRocky — Voice-first AI Agent
// https://github.com/openrocky
//
// Developed by everettjf with the assistance of Claude Code and Codex.
// Date: 2026-04-14
// Copyright (c) 2026 everettjf. All rights reserved.
//

package com.xnu.rocky.runtime.voice.stt

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.media.AudioFormat
import android.media.AudioRecord
import android.media.MediaRecorder
import androidx.core.content.ContextCompat
import com.xnu.rocky.providers.STTProviderConfiguration
import com.xnu.rocky.runtime.LogManager
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.io.ByteArrayOutputStream
import kotlin.math.sqrt

/** Dictation mode: auto-VAD (tap to start, silence to end) or push-to-talk (hold to record, release to end). */
enum class DictationMode { AutoVAD, PushToTalk }

/**
 * Inline dictation service: record mic → STT → return text to the chat composer.
 *
 * Supports two modes:
 * - **AutoVAD**: Tap to start, automatically stops on silence (1.5s) or after 30s max.
 * - **PushToTalk**: Hold to record, release (via [requestStop]) to stop and transcribe.
 *
 * Parallels OpenRockyDictationService on iOS.
 */
class DictationService(private val context: Context) {
    companion object {
        private const val TAG = "Dictation"
        private const val SAMPLE_RATE = 16000
        private const val CHUNK_MS = 100
        private const val SILENCE_CHUNKS = 15 // ~1.5s at 100ms chunks
        private const val MAX_RECORD_MS = 30_000L
        private const val SPEECH_THRESHOLD = 600.0
    }

    private val _isRecording = MutableStateFlow(false)
    val isRecording: StateFlow<Boolean> = _isRecording.asStateFlow()

    private val _audioLevel = MutableStateFlow(0f)
    val audioLevel: StateFlow<Float> = _audioLevel.asStateFlow()

    private var job: Job? = null

    /** Whether stop has been requested externally (push-to-talk release). */
    @Volatile private var stopRequested = false

    fun hasPermission(): Boolean =
        ContextCompat.checkSelfPermission(context, Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED

    fun startDictation(
        configuration: STTProviderConfiguration,
        scope: CoroutineScope,
        mode: DictationMode = DictationMode.AutoVAD,
        onResult: (String) -> Unit,
        onError: (String) -> Unit
    ) {
        if (_isRecording.value) return
        if (!configuration.isConfigured) {
            onError("STT provider is not configured. Please set up Speech-to-Text in Settings.")
            return
        }
        if (!hasPermission()) {
            onError("Microphone permission not granted")
            return
        }

        _isRecording.value = true
        stopRequested = false
        job = scope.launch(Dispatchers.IO) {
            try {
                val pcm = recordWithVAD(mode)
                val client = STTClientFactory.make(configuration)
                val text = client.transcribe(pcm).trim()
                withContext(Dispatchers.Main) {
                    _isRecording.value = false
                    _audioLevel.value = 0f
                    if (text.isNotBlank()) onResult(text)
                }
            } catch (e: STTClientException) {
                LogManager.error("Dictation failed: ${e.message}", TAG)
                withContext(Dispatchers.Main) {
                    _isRecording.value = false
                    _audioLevel.value = 0f
                    onError(e.message ?: "Dictation error")
                }
            } catch (e: Exception) {
                LogManager.error("Dictation failed: ${e.message}", TAG)
                withContext(Dispatchers.Main) {
                    _isRecording.value = false
                    _audioLevel.value = 0f
                    onError(e.message ?: "Dictation error")
                }
            }
        }
    }

    fun stopDictation() {
        job?.cancel()
        job = null
        stopRequested = false
        _isRecording.value = false
        _audioLevel.value = 0f
    }

    /** Request the recording to stop (used for push-to-talk release). */
    fun requestStop() {
        stopRequested = true
    }

    private suspend fun recordWithVAD(mode: DictationMode): ByteArray = withContext(Dispatchers.IO) {
        val minBuf = AudioRecord.getMinBufferSize(SAMPLE_RATE, AudioFormat.CHANNEL_IN_MONO, AudioFormat.ENCODING_PCM_16BIT)
            .coerceAtLeast(4096)
        val recorder = AudioRecord(
            MediaRecorder.AudioSource.VOICE_RECOGNITION,
            SAMPLE_RATE,
            AudioFormat.CHANNEL_IN_MONO,
            AudioFormat.ENCODING_PCM_16BIT,
            minBuf * 2
        )
        recorder.startRecording()
        LogManager.info("Dictation: recording started (mode=$mode)", TAG)
        val out = ByteArrayOutputStream()
        val chunkSize = SAMPLE_RATE * CHUNK_MS / 1000 * 2
        val buf = ByteArray(chunkSize)
        var silent = 0
        var hasSpeech = false
        val startNs = System.nanoTime()
        try {
            while (coroutineContext.isActive) {
                val elapsed = (System.nanoTime() - startNs) / 1_000_000
                if (elapsed > MAX_RECORD_MS) break
                val read = recorder.read(buf, 0, buf.size)
                if (read <= 0) continue
                val chunk = if (read == buf.size) buf else buf.copyOf(read)
                val rms = computeRMS(chunk)
                _audioLevel.value = (rms / 5000.0).coerceIn(0.0, 1.0).toFloat()
                out.write(chunk)

                // Push-to-talk: stop when release is signaled; skip silence detection.
                if (mode == DictationMode.PushToTalk) {
                    if (stopRequested) {
                        LogManager.info("Dictation: push-to-talk released, stopping", TAG)
                        break
                    }
                    continue
                }

                // Auto-VAD: detect silence after speech has been heard.
                if (rms > SPEECH_THRESHOLD) {
                    hasSpeech = true
                    silent = 0
                } else if (hasSpeech) {
                    silent++
                    if (silent >= SILENCE_CHUNKS) break
                }
            }
        } finally {
            runCatching { recorder.stop() }
            recorder.release()
        }
        out.toByteArray()
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
}

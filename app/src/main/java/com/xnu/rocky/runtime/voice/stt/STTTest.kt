//
// OpenRocky — Voice-first AI Agent
// https://github.com/openrocky
//
// Developed by everettjf with the assistance of Claude Code and Codex.
// Date: 2026-04-14
// Copyright (c) 2026 everettjf. All rights reserved.
//

package com.xnu.rocky.runtime.voice.stt

import android.content.Context
import android.content.pm.PackageManager
import android.media.AudioFormat
import android.media.AudioRecord
import android.media.MediaRecorder
import android.Manifest
import androidx.core.content.ContextCompat
import com.xnu.rocky.providers.STTProviderConfiguration
import com.xnu.rocky.runtime.LogManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import java.io.ByteArrayOutputStream

/** Record ~3 seconds of mic audio and pipe it through the configured STT. */
object STTTest {
    private const val TAG = "STTTest"
    private const val SAMPLE_RATE = 16000

    suspend fun record(context: Context, configuration: STTProviderConfiguration, durationMs: Long = 3000): Result<String> = withContext(Dispatchers.IO) {
        if (ContextCompat.checkSelfPermission(context, Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED) {
            return@withContext Result.failure(IllegalStateException("Microphone permission not granted"))
        }
        runCatching {
            val minBuf = AudioRecord.getMinBufferSize(SAMPLE_RATE, AudioFormat.CHANNEL_IN_MONO, AudioFormat.ENCODING_PCM_16BIT)
                .coerceAtLeast(4096)
            val recorder = AudioRecord(
                MediaRecorder.AudioSource.MIC,
                SAMPLE_RATE,
                AudioFormat.CHANNEL_IN_MONO,
                AudioFormat.ENCODING_PCM_16BIT,
                minBuf
            )
            recorder.startRecording()
            val out = ByteArrayOutputStream()
            val buf = ByteArray(minBuf)
            val startNs = System.nanoTime()
            try {
                while ((System.nanoTime() - startNs) / 1_000_000 < durationMs) {
                    val read = recorder.read(buf, 0, buf.size)
                    if (read > 0) out.write(buf, 0, read)
                    delay(20)
                }
            } finally {
                runCatching { recorder.stop() }
                recorder.release()
            }
            val pcm = out.toByteArray()
            if (pcm.isEmpty()) throw IllegalStateException("No audio captured")

            // Resample-wise we keep 16kHz. Wrap in WAV if needed by the client.
            val client = STTClientFactory.make(configuration)
            val text = client.transcribe(pcm)
            LogManager.info("STT test transcribed: ${text.take(120)}", TAG)
            text
        }
    }
}

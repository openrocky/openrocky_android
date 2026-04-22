//
// OpenRocky — Voice-first AI Agent
// https://github.com/openrocky
//
// Developed by everettjf with the assistance of Claude Code and Codex.
// Date: 2026-04-14
// Copyright (c) 2026 everettjf. All rights reserved.
//

package com.xnu.rocky.runtime.voice.tts

import android.media.MediaPlayer
import com.xnu.rocky.providers.TTSProviderConfiguration
import com.xnu.rocky.runtime.LogManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File

/** Synthesize a sample phrase for a provider and play it through the media player. */
object TTSPreview {
    private const val TAG = "TTSPreview"

    private val samples: Map<String, String> = mapOf(
        "en" to "Hello! This is a preview of the selected voice.",
        "zh" to "你好，这是所选声音的预览。"
    )

    /** Synthesize the preview and play it. Returns after playback completes. */
    suspend fun play(configuration: TTSProviderConfiguration, cacheDir: File): Result<Unit> = withContext(Dispatchers.IO) {
        runCatching {
            val client = TTSClientFactory.make(configuration)
            val sample = samples[if (isChineseVoice(configuration)) "zh" else "en"] ?: samples.getValue("en")
            val audio = client.synthesize(sample)
            val tempFile = File(cacheDir, "tts_preview_${System.currentTimeMillis()}.mp3")
            tempFile.writeBytes(audio)
            val player = MediaPlayer()
            try {
                player.setDataSource(tempFile.absolutePath)
                player.prepare()
                player.start()
                while (player.isPlaying) {
                    Thread.sleep(100)
                }
            } finally {
                runCatching { player.release() }
                tempFile.delete()
            }
            LogManager.info("TTS preview played for ${configuration.provider.displayName}", TAG)
        }.onFailure { LogManager.error("TTS preview failed: ${it.message}", TAG) }
    }

    private fun isChineseVoice(config: TTSProviderConfiguration): Boolean {
        val voice = config.resolvedVoice.lowercase()
        return voice.startsWith("zh") || voice.contains("xiao") || voice.contains("tong") ||
            voice.contains("yue") || voice.contains("long") || voice.contains("chun") ||
            voice.contains("qingse") || voice.contains("tianmei") || voice.contains("yujie") ||
            voice.contains("cmn-")
    }
}

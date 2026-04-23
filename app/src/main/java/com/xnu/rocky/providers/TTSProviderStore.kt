//
// OpenRocky — Voice-first AI Agent
// https://github.com/openrocky
//
// Developed by everettjf with the assistance of Claude Code and Codex.
// Date: 2026-04-14
// Copyright (c) 2026 everettjf. All rights reserved.
//

package com.xnu.rocky.providers

import android.content.Context
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import java.io.File

class TTSProviderStore(private val context: Context) {
    // coerceInputValues: legacy persisted instances with removed provider kinds (MINIMAX/ELEVEN_LABS/VOLCENGINE/AZURE_SPEECH/GOOGLE_CLOUD/ALI_CLOUD/QWEN_TTS) fall back to the default kind instead of crashing on load.
    private val json = Json { ignoreUnknownKeys = true; prettyPrint = true; coerceInputValues = true }
    private val dir: File get() = File(context.filesDir, "OpenRockyTTSProviders").also { it.mkdirs() }
    private val manifestFile: File get() = File(dir, "manifest.json")

    private val _instances = MutableStateFlow<List<TTSProviderInstance>>(emptyList())
    val instances: StateFlow<List<TTSProviderInstance>> = _instances.asStateFlow()

    private val _activeInstanceID = MutableStateFlow<String?>(null)
    val activeInstanceID: StateFlow<String?> = _activeInstanceID.asStateFlow()

    init { load() }

    val activeInstance: TTSProviderInstance?
        get() = _instances.value.find { it.id == _activeInstanceID.value }

    val activeConfiguration: TTSProviderConfiguration?
        get() {
            val instance = activeInstance ?: return null
            val credential = SecureStore.get(instance.credentialKeychainKey) ?: return null
            if (credential.isBlank()) return null
            return instance.toConfiguration(credential).normalized()
        }

    fun save(instance: TTSProviderInstance, credential: String) {
        if (credential.isNotBlank()) {
            SecureStore.set(instance.credentialKeychainKey, credential)
        }
        File(dir, "${instance.id}.json").writeText(json.encodeToString(instance))
        val list = _instances.value.toMutableList()
        val idx = list.indexOfFirst { it.id == instance.id }
        if (idx >= 0) list[idx] = instance else list.add(instance)
        _instances.value = list
        if (_activeInstanceID.value == null) activate(instance.id)
        saveManifest()
    }

    fun activate(id: String) {
        _activeInstanceID.value = id
        saveManifest()
    }

    fun delete(id: String) {
        SecureStore.delete("rocky.tts-instance.$id.credential")
        File(dir, "$id.json").delete()
        _instances.value = _instances.value.filter { it.id != id }
        if (_activeInstanceID.value == id) {
            _activeInstanceID.value = _instances.value.firstOrNull()?.id
        }
        saveManifest()
    }

    fun credentialFor(instance: TTSProviderInstance): String =
        SecureStore.get(instance.credentialKeychainKey) ?: ""

    private fun load() {
        val list = mutableListOf<TTSProviderInstance>()
        dir.listFiles()?.filter { it.extension == "json" && it.name != "manifest.json" }?.forEach { file ->
            try {
                list.add(json.decodeFromString<TTSProviderInstance>(file.readText()))
            } catch (_: Exception) {}
        }
        _instances.value = list
        try {
            val manifest = json.decodeFromString<Manifest>(manifestFile.readText())
            _activeInstanceID.value = manifest.activeInstanceID
        } catch (_: Exception) {
            _activeInstanceID.value = list.firstOrNull()?.id
        }
    }

    private fun saveManifest() {
        manifestFile.writeText(json.encodeToString(Manifest(_activeInstanceID.value)))
    }

    @kotlinx.serialization.Serializable
    private data class Manifest(val activeInstanceID: String? = null)
}

//
// OpenRocky — Voice-first AI Agent
// https://github.com/openrocky
//
// Developed by everettjf with the assistance of Claude Code and Codex.
// Date: 2026-03-25
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

class RealtimeProviderStore(private val context: Context) {
    private val json = Json { ignoreUnknownKeys = true; prettyPrint = true }
    private val dir: File get() = File(context.filesDir, "OpenRockyRealtimeProviders").also { it.mkdirs() }
    private val manifestFile: File get() = File(dir, "manifest.json")

    private val _instances = MutableStateFlow<List<RealtimeProviderInstance>>(emptyList())
    val instances: StateFlow<List<RealtimeProviderInstance>> = _instances.asStateFlow()

    private val _activeInstanceID = MutableStateFlow<String?>(null)
    val activeInstanceID: StateFlow<String?> = _activeInstanceID.asStateFlow()

    init {
        load()
    }

    val activeInstance: RealtimeProviderInstance?
        get() = _instances.value.find { it.id == _activeInstanceID.value }

    val activeConfiguration: RealtimeProviderConfiguration?
        get() {
            val instance = activeInstance ?: return null
            val credential = SecureStore.get(instance.credentialKeychainKey) ?: return null
            return instance.toConfiguration(credential)
        }

    fun save(instance: RealtimeProviderInstance, credential: String) {
        SecureStore.set(instance.credentialKeychainKey, credential)
        val file = File(dir, "${instance.id}.json")
        file.writeText(json.encodeToString(instance))
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
        SecureStore.delete("realtime_provider_credential_$id")
        File(dir, "$id.json").delete()
        _instances.value = _instances.value.filter { it.id != id }
        if (_activeInstanceID.value == id) {
            _activeInstanceID.value = _instances.value.firstOrNull()?.id
        }
        saveManifest()
    }

    fun credentialFor(instance: RealtimeProviderInstance): String {
        return SecureStore.get(instance.credentialKeychainKey) ?: ""
    }

    private fun load() {
        val list = mutableListOf<RealtimeProviderInstance>()
        dir.listFiles()?.filter { it.extension == "json" && it.name != "manifest.json" }?.forEach { file ->
            try {
                list.add(json.decodeFromString<RealtimeProviderInstance>(file.readText()))
            } catch (_: Exception) {}
        }
        _instances.value = list

        try {
            val manifest = json.decodeFromString<ManifestData>(manifestFile.readText())
            _activeInstanceID.value = manifest.activeInstanceID
        } catch (_: Exception) {
            _activeInstanceID.value = list.firstOrNull()?.id
        }
    }

    private fun saveManifest() {
        val data = ManifestData(activeInstanceID = _activeInstanceID.value)
        manifestFile.writeText(json.encodeToString(data))
    }

    @kotlinx.serialization.Serializable
    private data class ManifestData(val activeInstanceID: String? = null)
}

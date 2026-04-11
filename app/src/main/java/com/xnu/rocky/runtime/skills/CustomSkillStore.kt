//
// OpenRocky — Voice-first AI Agent
// https://github.com/openrocky
//
// Developed by everettjf with the assistance of Claude Code and Codex.
// Date: 2026-03-25
// Copyright (c) 2026 everettjf. All rights reserved.
//

package com.xnu.rocky.runtime.skills

import android.content.Context
import com.xnu.rocky.providers.ToolDefinition
import com.xnu.rocky.providers.ToolFunctionDef
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.withContext
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import java.io.File

class CustomSkillStore(private val context: Context) {
    private val json = Json { ignoreUnknownKeys = true; prettyPrint = true }
    private val dir: File get() = File(context.filesDir, "OpenRockyCustomSkills").also { it.mkdirs() }

    private val _skills = MutableStateFlow<List<CustomSkill>>(emptyList())
    val skills: StateFlow<List<CustomSkill>> = _skills.asStateFlow()

    init {
        seedBuiltIns()
        load()
    }

    fun save(skill: CustomSkill) {
        val file = File(dir, "${skill.id}.json")
        file.writeText(json.encodeToString(skill))
        val list = _skills.value.toMutableList()
        val idx = list.indexOfFirst { it.id == skill.id }
        if (idx >= 0) list[idx] = skill else list.add(skill)
        _skills.value = list
    }

    fun delete(id: String) {
        File(dir, "$id.json").delete()
        _skills.value = _skills.value.filter { it.id != id }
    }

    fun toggleEnabled(id: String) {
        val skill = _skills.value.find { it.id == id } ?: return
        save(skill.copy(enabled = !skill.enabled))
    }

    fun toolDefinitions(): List<ToolDefinition> {
        return _skills.value.filter { it.enabled }.map { skill ->
            ToolDefinition(function = ToolFunctionDef(
                name = CustomSkill.sanitizedToolName(skill.name),
                description = skill.description,
                parameters = buildJsonObject {
                    put("type", JsonPrimitive("object"))
                    put("properties", buildJsonObject {
                        put("input", buildJsonObject {
                            put("type", JsonPrimitive("string"))
                            put("description", JsonPrimitive("User input for this skill"))
                        })
                    })
                }
            ))
        }
    }

    fun skillPromptForTool(toolName: String): String? {
        return _skills.value.find { CustomSkill.sanitizedToolName(it.name) == toolName }?.prompt
    }

    suspend fun importFromUrl(url: String): Result<CustomSkill> = withContext(Dispatchers.IO) {
        try {
            val client = okhttp3.OkHttpClient()
            val request = okhttp3.Request.Builder().url(url).build()
            val response = client.newCall(request).execute()
            val text = response.body?.string() ?: return@withContext Result.failure(Exception("Empty response"))
            val skill = CustomSkill.fromMarkdown(text) ?: return@withContext Result.failure(Exception("Invalid skill format"))
            save(skill)
            Result.success(skill)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    fun exportSkill(id: String): String? {
        return _skills.value.find { it.id == id }?.toMarkdown()
    }

    private fun seedBuiltIns() {
        if (File(dir, ".seeded").exists()) return
        for (skill in BuiltInSkills.all) {
            val file = File(dir, "${skill.id}.json")
            if (!file.exists()) {
                file.parentFile?.mkdirs()
                file.writeText(json.encodeToString(skill))
            }
        }
        File(dir, ".seeded").createNewFile()
    }

    private fun load() {
        val list = mutableListOf<CustomSkill>()
        dir.listFiles()?.filter { it.extension == "json" }?.forEach { file ->
            try {
                list.add(json.decodeFromString<CustomSkill>(file.readText()))
            } catch (_: Exception) {}
        }
        _skills.value = list
    }
}

//
// OpenRocky — Voice-first AI Agent
// https://github.com/openrocky
//
// Developed by everettjf with the assistance of Claude Code and Codex.
// Date: 2026-04-14
// Copyright (c) 2026 everettjf. All rights reserved.
//

package com.xnu.rocky.ui.screens.providers

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.xnu.rocky.providers.TTSProviderInstance
import com.xnu.rocky.providers.TTSProviderKind
import com.xnu.rocky.runtime.voice.tts.TTSPreview
import com.xnu.rocky.ui.theme.OpenRockyPalette
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun TTSProviderInstanceEditorView(
    existingInstance: TTSProviderInstance?,
    existingCredential: String,
    onSave: (TTSProviderInstance, String) -> Unit,
    onBack: () -> Unit
) {
    val isNew = existingInstance == null
    var name by remember { mutableStateOf(existingInstance?.name ?: "") }
    var kind by remember { mutableStateOf(existingInstance?.kind ?: TTSProviderKind.OPENAI) }
    var modelID by remember { mutableStateOf(existingInstance?.modelID?.ifBlank { kind.defaultModel } ?: kind.defaultModel) }
    var voice by remember { mutableStateOf(existingInstance?.voice?.ifBlank { kind.defaultVoice } ?: kind.defaultVoice) }
    var credential by remember { mutableStateOf(existingCredential) }
    var customHost by remember { mutableStateOf(existingInstance?.customHost ?: "") }
    var showPassword by remember { mutableStateOf(false) }
    var previewing by remember { mutableStateOf(false) }
    var previewResult by remember { mutableStateOf<String?>(null) }
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(if (isNew) "Add TTS Provider" else "Edit TTS Provider", color = OpenRockyPalette.text) },
                navigationIcon = {
                    IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back", tint = OpenRockyPalette.text) }
                },
                actions = {
                    TextButton(
                        onClick = {
                            val instance = (existingInstance ?: TTSProviderInstance()).copy(
                                name = name, kind = kind, modelID = modelID,
                                voice = voice, customHost = customHost
                            )
                            onSave(instance, credential)
                            onBack()
                        },
                        enabled = credential.isNotBlank()
                    ) {
                        Text("Save", color = if (credential.isNotBlank()) OpenRockyPalette.accent else OpenRockyPalette.label)
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = OpenRockyPalette.background)
            )
        },
        containerColor = OpenRockyPalette.background
    ) { padding ->
        LazyColumn(
            modifier = Modifier.fillMaxSize().padding(padding).padding(horizontal = 16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
            contentPadding = PaddingValues(vertical = 16.dp)
        ) {
            item {
                Text("Provider", fontSize = 13.sp, fontWeight = FontWeight.SemiBold, color = OpenRockyPalette.muted)
                Spacer(Modifier.height(8.dp))
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    TTSProviderKind.entries.forEach { providerKind ->
                        FilterChip(
                            selected = kind == providerKind,
                            onClick = {
                                kind = providerKind
                                if (modelID !in providerKind.suggestedModels) modelID = providerKind.defaultModel
                                if (providerKind.availableVoices.none { it.id == voice }) voice = providerKind.defaultVoice
                            },
                            label = { Text(providerKind.displayName, fontSize = 12.sp) },
                            colors = FilterChipDefaults.filterChipColors(
                                selectedContainerColor = OpenRockyPalette.accent.copy(alpha = 0.2f),
                                containerColor = OpenRockyPalette.cardElevated
                            )
                        )
                    }
                }
                Text(kind.summary, fontSize = 11.sp, color = OpenRockyPalette.label, modifier = Modifier.padding(top = 4.dp))
                Text("Latency: ${kind.estimatedLatency} · ${kind.priceRange}", fontSize = 11.sp, color = OpenRockyPalette.label)
            }

            item {
                Text("Name (optional)", fontSize = 13.sp, fontWeight = FontWeight.SemiBold, color = OpenRockyPalette.muted)
                Spacer(Modifier.height(4.dp))
                OutlinedTextField(
                    value = name, onValueChange = { name = it },
                    placeholder = { Text(kind.displayName, color = OpenRockyPalette.label) },
                    modifier = Modifier.fillMaxWidth(), colors = rockyTextFieldColors(),
                    shape = RoundedCornerShape(12.dp), singleLine = true
                )
            }

            item {
                Text(kind.credentialTitle, fontSize = 13.sp, fontWeight = FontWeight.SemiBold, color = OpenRockyPalette.muted)
                Spacer(Modifier.height(4.dp))
                OutlinedTextField(
                    value = credential, onValueChange = { credential = it },
                    placeholder = { Text(kind.credentialPlaceholder, color = OpenRockyPalette.label) },
                    visualTransformation = if (showPassword) VisualTransformation.None else PasswordVisualTransformation(),
                    trailingIcon = {
                        IconButton(onClick = { showPassword = !showPassword }) {
                            Icon(if (showPassword) Icons.Default.VisibilityOff else Icons.Default.Visibility, null, tint = OpenRockyPalette.muted)
                        }
                    },
                    modifier = Modifier.fillMaxWidth(), colors = rockyTextFieldColors(),
                    shape = RoundedCornerShape(12.dp), singleLine = true
                )
            }

            item {
                Text("Model", fontSize = 13.sp, fontWeight = FontWeight.SemiBold, color = OpenRockyPalette.muted)
                Spacer(Modifier.height(8.dp))
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    kind.suggestedModels.forEach { m ->
                        FilterChip(
                            selected = modelID == m, onClick = { modelID = m },
                            label = { Text(m, fontSize = 12.sp) },
                            colors = FilterChipDefaults.filterChipColors(
                                selectedContainerColor = OpenRockyPalette.accent.copy(alpha = 0.2f),
                                containerColor = OpenRockyPalette.cardElevated
                            )
                        )
                    }
                }
                val desc = kind.modelDescription(modelID)
                if (!desc.isNullOrBlank()) Text(desc, fontSize = 11.sp, color = OpenRockyPalette.label, modifier = Modifier.padding(top = 4.dp))
            }

            item {
                Text("Voice", fontSize = 13.sp, fontWeight = FontWeight.SemiBold, color = OpenRockyPalette.muted)
                Spacer(Modifier.height(8.dp))
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    kind.availableVoices.forEach { v ->
                        FilterChip(
                            selected = voice == v.id, onClick = { voice = v.id },
                            label = {
                                Column {
                                    Text(v.name, fontSize = 12.sp)
                                    Text(v.subtitle, fontSize = 10.sp, color = OpenRockyPalette.label)
                                }
                            },
                            colors = FilterChipDefaults.filterChipColors(
                                selectedContainerColor = OpenRockyPalette.accent.copy(alpha = 0.2f),
                                containerColor = OpenRockyPalette.cardElevated
                            )
                        )
                    }
                }
            }

            item {
                Text("Custom Host (optional)", fontSize = 13.sp, fontWeight = FontWeight.SemiBold, color = OpenRockyPalette.muted)
                Spacer(Modifier.height(4.dp))
                OutlinedTextField(
                    value = customHost, onValueChange = { customHost = it },
                    placeholder = { Text(kind.defaultBaseURL, color = OpenRockyPalette.label) },
                    modifier = Modifier.fillMaxWidth(), colors = rockyTextFieldColors(),
                    shape = RoundedCornerShape(12.dp), singleLine = true
                )
            }

            item {
                Button(
                    onClick = {
                        previewing = true
                        previewResult = null
                        scope.launch {
                            val instance = (existingInstance ?: TTSProviderInstance()).copy(
                                name = name, kind = kind, modelID = modelID,
                                voice = voice, customHost = customHost
                            )
                            val result = TTSPreview.play(instance.toConfiguration(credential), context.cacheDir)
                            previewResult = result.fold({ "✓ Preview played" }, { "Error: ${it.message}" })
                            previewing = false
                        }
                    },
                    modifier = Modifier.fillMaxWidth(),
                    enabled = !previewing && credential.isNotBlank(),
                    colors = ButtonDefaults.buttonColors(containerColor = OpenRockyPalette.accent)
                ) {
                    if (previewing) CircularProgressIndicator(Modifier.size(18.dp), strokeWidth = 2.dp, color = OpenRockyPalette.text)
                    else Icon(Icons.Default.PlayArrow, null)
                    Spacer(Modifier.width(8.dp))
                    Text(if (previewing) "Synthesizing…" else "Preview Voice")
                }
                previewResult?.let {
                    Spacer(Modifier.height(8.dp))
                    Text(it, color = if (it.startsWith("✓")) OpenRockyPalette.success else OpenRockyPalette.error, fontSize = 12.sp)
                }
            }
        }
    }
}

//
// OpenRocky — Voice-first AI Agent
// https://github.com/openrocky
//
// Developed by everettjf with the assistance of Claude Code and Codex.
// Date: 2026-03-25
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.xnu.rocky.providers.RealtimeProviderInstance
import com.xnu.rocky.providers.RealtimeProviderKind
import com.xnu.rocky.runtime.voice.OpenAIVoice
import com.xnu.rocky.ui.theme.OpenRockyPalette

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun RealtimeProviderInstanceEditorView(
    existingInstance: RealtimeProviderInstance?,
    existingCredential: String,
    onSave: (RealtimeProviderInstance, String) -> Unit,
    onBack: () -> Unit
) {
    val isNew = existingInstance == null
    var name by remember { mutableStateOf(existingInstance?.name ?: "") }
    var kind by remember { mutableStateOf(existingInstance?.kind ?: RealtimeProviderKind.OPENAI) }
    var credential by remember { mutableStateOf(existingCredential) }
    var openaiVoice by remember { mutableStateOf(existingInstance?.openaiVoice ?: "alloy") }
    var customHost by remember { mutableStateOf(existingInstance?.customHost ?: "") }
    var showPassword by remember { mutableStateOf(false) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(if (isNew) "Add Voice Provider" else "Edit Voice Provider", color = OpenRockyPalette.text) },
                navigationIcon = {
                    IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back", tint = OpenRockyPalette.text) }
                },
                actions = {
                    TextButton(onClick = {
                        val instance = (existingInstance ?: RealtimeProviderInstance()).copy(
                            name = name, kind = kind, modelID = kind.defaultModel,
                            customHost = customHost, openaiVoice = openaiVoice
                        )
                        onSave(instance, credential)
                        onBack()
                    }, enabled = credential.isNotBlank()) {
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
            // Provider selection
            item {
                Text("Provider", fontSize = 13.sp, fontWeight = FontWeight.SemiBold, color = OpenRockyPalette.muted)
                Spacer(Modifier.height(8.dp))
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    RealtimeProviderKind.entries.forEach { providerKind ->
                        FilterChip(
                            selected = kind == providerKind, onClick = { kind = providerKind },
                            label = { Text(providerKind.displayName, fontSize = 12.sp) },
                            colors = FilterChipDefaults.filterChipColors(
                                selectedContainerColor = OpenRockyPalette.accent.copy(alpha = 0.2f),
                                containerColor = OpenRockyPalette.cardElevated
                            )
                        )
                    }
                }
                Text(kind.summary, fontSize = 11.sp, color = OpenRockyPalette.label, modifier = Modifier.padding(top = 4.dp))
            }

            // Name
            item {
                Text("Name (optional)", fontSize = 13.sp, fontWeight = FontWeight.SemiBold, color = OpenRockyPalette.muted)
                Spacer(Modifier.height(4.dp))
                OutlinedTextField(value = name, onValueChange = { name = it }, placeholder = { Text(kind.displayName, color = OpenRockyPalette.label) },
                    modifier = Modifier.fillMaxWidth(), colors = rockyTextFieldColors(), shape = RoundedCornerShape(12.dp), singleLine = true)
            }

            // API Key
            item {
                Text(kind.credentialTitle, fontSize = 13.sp, fontWeight = FontWeight.SemiBold, color = OpenRockyPalette.muted)
                Spacer(Modifier.height(4.dp))
                OutlinedTextField(value = credential, onValueChange = { credential = it },
                    placeholder = { Text(kind.credentialPlaceholder, color = OpenRockyPalette.label) },
                    visualTransformation = if (showPassword) VisualTransformation.None else PasswordVisualTransformation(),
                    trailingIcon = { IconButton(onClick = { showPassword = !showPassword }) { Icon(if (showPassword) Icons.Default.VisibilityOff else Icons.Default.Visibility, null, tint = OpenRockyPalette.muted) } },
                    modifier = Modifier.fillMaxWidth(), colors = rockyTextFieldColors(), shape = RoundedCornerShape(12.dp), singleLine = true)
            }

            // Model (display only)
            item {
                Text("Model", fontSize = 13.sp, fontWeight = FontWeight.SemiBold, color = OpenRockyPalette.muted)
                Spacer(Modifier.height(4.dp))
                Text(kind.defaultModel, fontSize = 14.sp, color = OpenRockyPalette.text)
            }

            // Custom Host
            item {
                Text("Custom Host (optional)", fontSize = 13.sp, fontWeight = FontWeight.SemiBold, color = OpenRockyPalette.muted)
                Spacer(Modifier.height(4.dp))
                OutlinedTextField(
                    value = customHost, onValueChange = { customHost = it },
                    placeholder = { Text("https://your-server.com/v1/realtime", color = OpenRockyPalette.label) },
                    modifier = Modifier.fillMaxWidth(), colors = rockyTextFieldColors(), shape = RoundedCornerShape(12.dp), singleLine = true
                )
                Text("Leave empty to use default endpoint", fontSize = 11.sp, color = OpenRockyPalette.label, modifier = Modifier.padding(top = 4.dp))
            }

            // Voice selection
            item {
                Text("Voice", fontSize = 13.sp, fontWeight = FontWeight.SemiBold, color = OpenRockyPalette.muted)
                Spacer(Modifier.height(8.dp))
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    OpenAIVoice.entries.forEach { voice ->
                        FilterChip(
                            selected = openaiVoice == voice.id, onClick = { openaiVoice = voice.id },
                            label = { Text(voice.displayName, fontSize = 12.sp) },
                            colors = FilterChipDefaults.filterChipColors(
                                selectedContainerColor = OpenRockyPalette.accent.copy(alpha = 0.2f),
                                containerColor = OpenRockyPalette.cardElevated
                            )
                        )
                    }
                }
            }
        }
    }
}

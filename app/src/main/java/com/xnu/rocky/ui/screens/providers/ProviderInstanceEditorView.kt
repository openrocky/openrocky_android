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
import com.xnu.rocky.providers.ProviderInstance
import com.xnu.rocky.providers.ProviderKind
import com.xnu.rocky.ui.theme.OpenRockyPalette

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ProviderInstanceEditorView(
    existingInstance: ProviderInstance?,
    existingCredential: String,
    onSave: (ProviderInstance, String) -> Unit,
    onTest: (ProviderInstance, String, (String) -> Unit) -> Unit,
    onBack: () -> Unit
) {
    val isNew = existingInstance == null
    var name by remember { mutableStateOf(existingInstance?.name ?: "") }
    var selectedKind by remember { mutableStateOf(existingInstance?.kind ?: ProviderKind.OPENAI) }
    var modelID by remember { mutableStateOf(existingInstance?.modelID ?: "") }
    var credential by remember { mutableStateOf(existingCredential) }
    var azureResourceName by remember { mutableStateOf(existingInstance?.azureResourceName ?: "") }
    var azureAPIVersion by remember { mutableStateOf(existingInstance?.azureAPIVersion ?: "2024-02-15-preview") }
    var aiProxyServiceURL by remember { mutableStateOf(existingInstance?.aiProxyServiceURL ?: "") }
    var customHost by remember { mutableStateOf(existingInstance?.customHost ?: "") }
    var showPassword by remember { mutableStateOf(false) }
    var testResult by remember { mutableStateOf<String?>(null) }
    var isTesting by remember { mutableStateOf(false) }
    var showKindDropdown by remember { mutableStateOf(false) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(if (isNew) "Add Provider" else "Edit Provider", color = OpenRockyPalette.text) },
                navigationIcon = {
                    IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back", tint = OpenRockyPalette.text) }
                },
                actions = {
                    TextButton(onClick = {
                        val instance = (existingInstance ?: ProviderInstance()).copy(
                            name = name, kind = selectedKind, modelID = modelID.ifBlank { selectedKind.defaultModel },
                            customHost = customHost, azureResourceName = azureResourceName, azureAPIVersion = azureAPIVersion, aiProxyServiceURL = aiProxyServiceURL
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
            // Provider kind selector
            item {
                Text("Provider", fontSize = 13.sp, fontWeight = FontWeight.SemiBold, color = OpenRockyPalette.muted)
                Spacer(Modifier.height(4.dp))
                ExposedDropdownMenuBox(expanded = showKindDropdown, onExpandedChange = { showKindDropdown = it }) {
                    OutlinedTextField(
                        value = selectedKind.displayName,
                        onValueChange = {},
                        readOnly = true,
                        trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = showKindDropdown) },
                        modifier = Modifier.fillMaxWidth().menuAnchor(),
                        colors = rockyTextFieldColors(),
                        shape = RoundedCornerShape(12.dp)
                    )
                    ExposedDropdownMenu(expanded = showKindDropdown, onDismissRequest = { showKindDropdown = false }) {
                        ProviderKind.entries.forEach { kind ->
                            DropdownMenuItem(
                                text = {
                                    Column {
                                        Text(kind.displayName, color = OpenRockyPalette.text)
                                        Text(kind.summary, fontSize = 11.sp, color = OpenRockyPalette.muted)
                                    }
                                },
                                onClick = {
                                    selectedKind = kind
                                    if (modelID.isBlank() || ProviderKind.entries.any { it.defaultModel == modelID }) {
                                        modelID = kind.defaultModel
                                    }
                                    showKindDropdown = false
                                }
                            )
                        }
                    }
                }
            }

            // Name
            item {
                Text("Name (optional)", fontSize = 13.sp, fontWeight = FontWeight.SemiBold, color = OpenRockyPalette.muted)
                Spacer(Modifier.height(4.dp))
                OutlinedTextField(
                    value = name, onValueChange = { name = it },
                    placeholder = { Text(selectedKind.displayName, color = OpenRockyPalette.label) },
                    modifier = Modifier.fillMaxWidth(), colors = rockyTextFieldColors(), shape = RoundedCornerShape(12.dp), singleLine = true
                )
            }

            // API Key
            item {
                Text("API Key", fontSize = 13.sp, fontWeight = FontWeight.SemiBold, color = OpenRockyPalette.muted)
                Spacer(Modifier.height(4.dp))
                OutlinedTextField(
                    value = credential, onValueChange = { credential = it },
                    placeholder = { Text(selectedKind.apiKeyPlaceholder, color = OpenRockyPalette.label) },
                    visualTransformation = if (showPassword) VisualTransformation.None else PasswordVisualTransformation(),
                    trailingIcon = {
                        IconButton(onClick = { showPassword = !showPassword }) {
                            Icon(if (showPassword) Icons.Default.VisibilityOff else Icons.Default.Visibility, null, tint = OpenRockyPalette.muted)
                        }
                    },
                    modifier = Modifier.fillMaxWidth(), colors = rockyTextFieldColors(), shape = RoundedCornerShape(12.dp), singleLine = true
                )
            }

            // Model
            item {
                Text("Model", fontSize = 13.sp, fontWeight = FontWeight.SemiBold, color = OpenRockyPalette.muted)
                Spacer(Modifier.height(4.dp))
                OutlinedTextField(
                    value = modelID, onValueChange = { modelID = it },
                    placeholder = { Text(selectedKind.defaultModel, color = OpenRockyPalette.label) },
                    modifier = Modifier.fillMaxWidth(), colors = rockyTextFieldColors(), shape = RoundedCornerShape(12.dp), singleLine = true
                )
                Spacer(Modifier.height(4.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    selectedKind.suggestedModels.take(3).forEach { model ->
                        SuggestionChip(
                            onClick = { modelID = model },
                            label = { Text(model, fontSize = 11.sp) },
                            colors = SuggestionChipDefaults.suggestionChipColors(
                                containerColor = OpenRockyPalette.cardElevated,
                                labelColor = OpenRockyPalette.muted
                            )
                        )
                    }
                }
            }

            // Custom Host
            item {
                Text("Custom Host (optional)", fontSize = 13.sp, fontWeight = FontWeight.SemiBold, color = OpenRockyPalette.muted)
                Spacer(Modifier.height(4.dp))
                OutlinedTextField(
                    value = customHost, onValueChange = { customHost = it },
                    placeholder = { Text(selectedKind.baseUrl.ifBlank { "https://api.example.com/v1/" }, color = OpenRockyPalette.label) },
                    modifier = Modifier.fillMaxWidth(), colors = rockyTextFieldColors(), shape = RoundedCornerShape(12.dp), singleLine = true
                )
                Text("Leave empty to use default endpoint", fontSize = 11.sp, color = OpenRockyPalette.label, modifier = Modifier.padding(top = 4.dp))
            }

            // Azure-specific
            if (selectedKind == ProviderKind.AZURE_OPENAI) {
                item {
                    Text("Azure Resource Name", fontSize = 13.sp, fontWeight = FontWeight.SemiBold, color = OpenRockyPalette.muted)
                    Spacer(Modifier.height(4.dp))
                    OutlinedTextField(
                        value = azureResourceName, onValueChange = { azureResourceName = it },
                        modifier = Modifier.fillMaxWidth(), colors = rockyTextFieldColors(), shape = RoundedCornerShape(12.dp), singleLine = true
                    )
                }
                item {
                    Text("API Version", fontSize = 13.sp, fontWeight = FontWeight.SemiBold, color = OpenRockyPalette.muted)
                    Spacer(Modifier.height(4.dp))
                    OutlinedTextField(
                        value = azureAPIVersion, onValueChange = { azureAPIVersion = it },
                        modifier = Modifier.fillMaxWidth(), colors = rockyTextFieldColors(), shape = RoundedCornerShape(12.dp), singleLine = true
                    )
                }
            }

            // AIProxy-specific
            if (selectedKind == ProviderKind.AIPROXY) {
                item {
                    Text("Service URL", fontSize = 13.sp, fontWeight = FontWeight.SemiBold, color = OpenRockyPalette.muted)
                    Spacer(Modifier.height(4.dp))
                    OutlinedTextField(
                        value = aiProxyServiceURL, onValueChange = { aiProxyServiceURL = it },
                        modifier = Modifier.fillMaxWidth(), colors = rockyTextFieldColors(), shape = RoundedCornerShape(12.dp), singleLine = true
                    )
                }
            }

            // Test connection
            item {
                Spacer(Modifier.height(8.dp))
                Button(
                    onClick = {
                        isTesting = true
                        testResult = null
                        val instance = (existingInstance ?: ProviderInstance()).copy(
                            kind = selectedKind, modelID = modelID.ifBlank { selectedKind.defaultModel },
                            customHost = customHost, azureResourceName = azureResourceName, azureAPIVersion = azureAPIVersion, aiProxyServiceURL = aiProxyServiceURL
                        )
                        onTest(instance, credential) { result ->
                            testResult = result
                            isTesting = false
                        }
                    },
                    enabled = credential.isNotBlank() && !isTesting,
                    modifier = Modifier.fillMaxWidth(),
                    colors = ButtonDefaults.buttonColors(containerColor = OpenRockyPalette.cardElevated),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    if (isTesting) {
                        CircularProgressIndicator(modifier = Modifier.size(16.dp), color = OpenRockyPalette.accent, strokeWidth = 2.dp)
                        Spacer(Modifier.width(8.dp))
                    }
                    Text(if (isTesting) "Testing…" else "Test Connection", color = OpenRockyPalette.text)
                }

                testResult?.let { result ->
                    Spacer(Modifier.height(8.dp))
                    Card(
                        colors = CardDefaults.cardColors(
                            containerColor = if (result.startsWith("Connected")) OpenRockyPalette.success.copy(alpha = 0.1f) else OpenRockyPalette.error.copy(alpha = 0.1f)
                        ),
                        shape = RoundedCornerShape(8.dp)
                    ) {
                        Text(
                            result, fontSize = 13.sp,
                            color = if (result.startsWith("Connected")) OpenRockyPalette.success else OpenRockyPalette.error,
                            modifier = Modifier.padding(12.dp)
                        )
                    }
                }
            }
        }
    }
}

@Composable
fun rockyTextFieldColors() = OutlinedTextFieldDefaults.colors(
    focusedTextColor = OpenRockyPalette.text,
    unfocusedTextColor = OpenRockyPalette.text,
    cursorColor = OpenRockyPalette.accent,
    focusedBorderColor = OpenRockyPalette.accent,
    unfocusedBorderColor = OpenRockyPalette.stroke,
    focusedContainerColor = OpenRockyPalette.cardElevated,
    unfocusedContainerColor = OpenRockyPalette.card,
)

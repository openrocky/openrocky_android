//
// OpenRocky — Voice-first AI Agent
// https://github.com/openrocky
//
// Developed by everettjf with the assistance of Claude Code and Codex.
// Date: 2026-04-14
// Copyright (c) 2026 everettjf. All rights reserved.
//

package com.xnu.rocky.ui.screens.settings

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.xnu.rocky.runtime.Preferences
import com.xnu.rocky.runtime.VoiceMode
import com.xnu.rocky.ui.theme.OpenRockyPalette

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun VoiceModeView(
    preferences: Preferences,
    onBack: () -> Unit
) {
    val voiceMode by preferences.voiceMode.collectAsStateWithLifecycle()
    val interruption by preferences.voiceInterruption.collectAsStateWithLifecycle()

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Voice Mode", color = OpenRockyPalette.text) },
                navigationIcon = {
                    IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back", tint = OpenRockyPalette.text) }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = OpenRockyPalette.background)
            )
        },
        containerColor = OpenRockyPalette.background
    ) { padding ->
        LazyColumn(
            modifier = Modifier.fillMaxSize().padding(padding).padding(horizontal = 16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
            contentPadding = PaddingValues(vertical = 16.dp)
        ) {
            item {
                Text("PIPELINE", fontSize = 11.sp, fontWeight = FontWeight.SemiBold, color = OpenRockyPalette.label)
            }
            item {
                ModeCard(
                    title = "Realtime",
                    subtitle = "End-to-end streaming via WebRTC/WebSocket. Lower latency, requires a realtime-capable provider.",
                    selected = voiceMode == VoiceMode.REALTIME,
                    onClick = { preferences.setVoiceMode(VoiceMode.REALTIME) }
                )
            }
            item {
                ModeCard(
                    title = "Classic",
                    subtitle = "Microphone → STT → Chat → TTS → Speaker. Works with any chat provider plus separate STT and TTS providers.",
                    selected = voiceMode == VoiceMode.CLASSIC,
                    onClick = { preferences.setVoiceMode(VoiceMode.CLASSIC) }
                )
            }

            item { Spacer(Modifier.height(8.dp)) }
            item {
                Text("OPTIONS", fontSize = 11.sp, fontWeight = FontWeight.SemiBold, color = OpenRockyPalette.label)
            }
            item {
                Card(
                    shape = RoundedCornerShape(12.dp),
                    colors = CardDefaults.cardColors(containerColor = OpenRockyPalette.card)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth().padding(14.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text("Allow barge-in (Classic mode)", fontSize = 14.sp, color = OpenRockyPalette.text, fontWeight = FontWeight.Medium)
                            Text("Let your voice interrupt TTS playback mid-sentence.", fontSize = 11.sp, color = OpenRockyPalette.muted)
                        }
                        Switch(checked = interruption, onCheckedChange = { preferences.setVoiceInterruption(it) })
                    }
                }
            }
        }
    }
}

@Composable
private fun ModeCard(title: String, subtitle: String, selected: Boolean, onClick: () -> Unit) {
    Card(
        modifier = Modifier.fillMaxWidth().clickable(onClick = onClick),
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(
            containerColor = if (selected) OpenRockyPalette.cardElevated else OpenRockyPalette.card
        )
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(14.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Icon(
                if (selected) Icons.Default.CheckCircle else Icons.Default.RadioButtonUnchecked,
                null,
                tint = if (selected) OpenRockyPalette.accent else OpenRockyPalette.label
            )
            Column(modifier = Modifier.weight(1f)) {
                Text(title, fontSize = 16.sp, fontWeight = FontWeight.SemiBold, color = OpenRockyPalette.text)
                Text(subtitle, fontSize = 12.sp, color = OpenRockyPalette.muted)
            }
        }
    }
}

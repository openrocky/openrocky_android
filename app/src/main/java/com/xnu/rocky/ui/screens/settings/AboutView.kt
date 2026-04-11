//
// OpenRocky — Voice-first AI Agent
// https://github.com/openrocky
//
// Developed by everettjf with the assistance of Claude Code and Codex.
// Date: 2026-03-25
// Copyright (c) 2026 everettjf. All rights reserved.
//

package com.xnu.rocky.ui.screens.settings

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.xnu.rocky.ui.theme.OpenRockyPalette

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AboutView(onBack: () -> Unit) {
    val context = LocalContext.current
    val version = try { context.packageManager.getPackageInfo(context.packageName, 0).versionName } catch (_: Exception) { "1.0" }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("About", color = OpenRockyPalette.text) },
                navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back", tint = OpenRockyPalette.text) } },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = OpenRockyPalette.background)
            )
        },
        containerColor = OpenRockyPalette.background
    ) { padding ->
        LazyColumn(
            modifier = Modifier.fillMaxSize().padding(padding).padding(horizontal = 16.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(16.dp),
            contentPadding = PaddingValues(vertical = 32.dp)
        ) {
            item {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Box(Modifier.size(80.dp).clip(CircleShape).background(OpenRockyPalette.accent.copy(alpha = 0.15f)), contentAlignment = Alignment.Center) {
                        Icon(Icons.Default.AutoAwesome, null, tint = OpenRockyPalette.accent, modifier = Modifier.size(40.dp))
                    }
                    Spacer(Modifier.height(12.dp))
                    Text("OpenRocky", fontSize = 24.sp, fontWeight = FontWeight.Bold, color = OpenRockyPalette.text)
                    Text("Version $version", fontSize = 14.sp, color = OpenRockyPalette.muted)
                    Spacer(Modifier.height(8.dp))
                    Text("Voice-first AI Agent for Android", fontSize = 14.sp, color = OpenRockyPalette.muted, textAlign = TextAlign.Center)
                }
            }

            item { HorizontalDivider(color = OpenRockyPalette.separator) }

            item { Text("LINKS", fontSize = 12.sp, fontWeight = FontWeight.SemiBold, color = OpenRockyPalette.label) }
            item { LinkRow("GitHub", "Open source repository") }
            item { LinkRow("Website", "openrocky.dev") }
            item { LinkRow("Twitter", "@openrocky") }

            item { Spacer(Modifier.height(8.dp)) }
            item { Text("COMMUNITY", fontSize = 12.sp, fontWeight = FontWeight.SemiBold, color = OpenRockyPalette.label) }
            item { LinkRow("Discord", "Join the community") }
            item { LinkRow("Telegram", "Discussion group") }

            item { Spacer(Modifier.height(16.dp)) }
            item {
                Text("Made with ♥ by everettjf", fontSize = 13.sp, color = OpenRockyPalette.label, textAlign = TextAlign.Center)
            }
        }
    }
}

@Composable
private fun LinkRow(title: String, subtitle: String) {
    Card(shape = RoundedCornerShape(10.dp), colors = CardDefaults.cardColors(containerColor = OpenRockyPalette.card)) {
        Row(Modifier.fillMaxWidth().padding(14.dp), verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text(title, fontSize = 15.sp, fontWeight = FontWeight.Medium, color = OpenRockyPalette.text)
                Text(subtitle, fontSize = 12.sp, color = OpenRockyPalette.muted)
            }
            Icon(Icons.Default.OpenInNew, null, tint = OpenRockyPalette.label, modifier = Modifier.size(18.dp))
        }
    }
}

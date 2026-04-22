//
// OpenRocky — Voice-first AI Agent
// https://github.com/openrocky
//
// Developed by everettjf with the assistance of Claude Code and Codex.
// Date: 2026-03-25
// Copyright (c) 2026 everettjf. All rights reserved.
//

package com.xnu.rocky.ui.screens.home

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowUpward
import androidx.compose.material.icons.filled.Forum
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.xnu.rocky.models.*
import com.xnu.rocky.ui.theme.OpenRockyPalette

@Composable
fun HomeScreen(
    session: OpenRockySession,
    onSendMessage: (String) -> Unit,
    onQuickTask: (QuickTask) -> Unit,
    onConversationsClick: () -> Unit,
    isDictating: Boolean = false,
    onStartDictation: (() -> Unit)? = null,
    onStopDictation: (() -> Unit)? = null,
    dictationResult: String? = null,
    onDictationConsumed: () -> Unit = {},
    modifier: Modifier = Modifier
) {
    var inputText by remember { mutableStateOf("") }
    LaunchedEffect(dictationResult) {
        val incoming = dictationResult
        if (!incoming.isNullOrBlank()) {
            inputText = if (inputText.isBlank()) incoming else "$inputText $incoming"
            onDictationConsumed()
        }
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(OpenRockyPalette.background)
    ) {
        // Content area with quick tasks
        Box(
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth(),
            contentAlignment = Alignment.Center
        ) {
            Column(
                modifier = Modifier.padding(16.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(24.dp)
            ) {
                // Title
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(
                        "Rocky",
                        fontSize = 32.sp,
                        fontWeight = FontWeight.Bold,
                        color = OpenRockyPalette.text.copy(alpha = 0.3f)
                    )
                    Spacer(Modifier.height(8.dp))
                    Text(
                        "Hey, what can I do for you?",
                        fontSize = 16.sp,
                        color = OpenRockyPalette.text.copy(alpha = 0.2f)
                    )
                }

                // Quick Tasks
                if (session.quickTasks.isNotEmpty()) {
                    QuickTasksGrid(session.quickTasks, onQuickTask)
                }
            }
        }

        // Composer Bar
        ComposerBar(
            text = inputText,
            onTextChange = { inputText = it },
            onSend = {
                if (inputText.isNotBlank()) {
                    onSendMessage(inputText)
                    inputText = ""
                }
            },
            onConversationsClick = onConversationsClick,
            isDictating = isDictating,
            onStartDictation = onStartDictation,
            onStopDictation = onStopDictation
        )
    }
}

@Composable
private fun QuickTasksGrid(tasks: List<QuickTask>, onTask: (QuickTask) -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text("Quick Tasks", fontSize = 13.sp, fontWeight = FontWeight.SemiBold, color = OpenRockyPalette.muted)

        val rows = tasks.chunked(2)
        rows.forEach { row ->
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                row.forEach { task ->
                    Card(
                        modifier = Modifier
                            .weight(1f)
                            .clickable { onTask(task) },
                        shape = RoundedCornerShape(12.dp),
                        colors = CardDefaults.cardColors(containerColor = OpenRockyPalette.card)
                    ) {
                        Row(
                            modifier = Modifier.padding(12.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Icon(
                                imageVector = task.symbol,
                                contentDescription = null,
                                tint = task.tint,
                                modifier = Modifier.size(20.dp)
                            )
                            Text(
                                text = task.title,
                                fontSize = 14.sp,
                                fontWeight = FontWeight.Medium,
                                color = OpenRockyPalette.text
                            )
                        }
                    }
                }
                if (row.size == 1) {
                    Spacer(modifier = Modifier.weight(1f))
                }
            }
        }
    }
}

@Composable
private fun ComposerBar(
    text: String,
    onTextChange: (String) -> Unit,
    onSend: () -> Unit,
    onConversationsClick: () -> Unit,
    isDictating: Boolean = false,
    onStartDictation: (() -> Unit)? = null,
    onStopDictation: (() -> Unit)? = null
) {
    Surface(
        color = OpenRockyPalette.card,
        shadowElevation = 8.dp
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 12.dp)
                .navigationBarsPadding(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            IconButton(
                onClick = onConversationsClick,
                modifier = Modifier.size(40.dp)
            ) {
                Icon(
                    Icons.Default.Forum,
                    contentDescription = "Conversations",
                    tint = OpenRockyPalette.muted,
                    modifier = Modifier.size(22.dp)
                )
            }

            BasicTextField(
                value = text,
                onValueChange = onTextChange,
                modifier = Modifier
                    .weight(1f)
                    .clip(RoundedCornerShape(20.dp))
                    .background(OpenRockyPalette.cardElevated)
                    .padding(horizontal = 16.dp, vertical = 10.dp),
                textStyle = LocalTextStyle.current.copy(
                    color = OpenRockyPalette.text,
                    fontSize = 15.sp
                ),
                cursorBrush = SolidColor(OpenRockyPalette.accent),
                singleLine = true,
                decorationBox = { innerTextField ->
                    Box {
                        if (text.isEmpty()) {
                            Text(
                                "Ask Rocky anything\u2026",
                                color = OpenRockyPalette.label,
                                fontSize = 15.sp
                            )
                        }
                        innerTextField()
                    }
                }
            )

            if (text.isBlank() && onStartDictation != null) {
                IconButton(
                    onClick = { if (isDictating) onStopDictation?.invoke() else onStartDictation() },
                    modifier = Modifier
                        .size(40.dp)
                        .clip(CircleShape)
                        .background(if (isDictating) OpenRockyPalette.error else OpenRockyPalette.cardElevated)
                ) {
                    Icon(
                        if (isDictating) Icons.Default.Stop else Icons.Default.Mic,
                        contentDescription = if (isDictating) "Stop dictation" else "Dictate",
                        tint = if (isDictating) OpenRockyPalette.background else OpenRockyPalette.muted,
                        modifier = Modifier.size(20.dp)
                    )
                }
            } else {
                IconButton(
                    onClick = onSend,
                    modifier = Modifier
                        .size(40.dp)
                        .clip(CircleShape)
                        .background(
                            if (text.isNotBlank()) OpenRockyPalette.accent else OpenRockyPalette.cardElevated
                        )
                ) {
                    Icon(
                        Icons.Default.ArrowUpward,
                        contentDescription = "Send",
                        tint = if (text.isNotBlank()) OpenRockyPalette.background else OpenRockyPalette.muted,
                        modifier = Modifier.size(20.dp)
                    )
                }
            }
        }
    }
}

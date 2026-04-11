//
// OpenRocky — Voice-first AI Agent
// https://github.com/openrocky
//
// Developed by everettjf with the assistance of Claude Code and Codex.
// Date: 2026-03-25
// Copyright (c) 2026 everettjf. All rights reserved.
//

package com.xnu.rocky.ui.screens.voice

import androidx.compose.animation.core.*
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.xnu.rocky.models.SessionMode
import com.xnu.rocky.ui.theme.OpenRockyPalette

@Composable
fun VoiceBar(
    mode: SessionMode,
    statusText: String,
    onEnd: () -> Unit,
    modifier: Modifier = Modifier
) {
    val infiniteTransition = rememberInfiniteTransition(label = "voiceBar")

    val orbScale by infiniteTransition.animateFloat(
        initialValue = 0.9f,
        targetValue = 1.1f,
        animationSpec = infiniteRepeatable(
            animation = tween(1000, easing = EaseInOut),
            repeatMode = RepeatMode.Reverse
        ),
        label = "orbScale"
    )

    Surface(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 8.dp),
        shape = RoundedCornerShape(20.dp),
        color = OpenRockyPalette.cardElevated,
        shadowElevation = 4.dp
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            // Small orb
            Box(
                modifier = Modifier.size(40.dp),
                contentAlignment = Alignment.Center
            ) {
                Canvas(modifier = Modifier.size(36.dp)) {
                    val center = Offset(size.width / 2, size.height / 2)
                    val radius = size.minDimension / 2 * orbScale

                    drawCircle(
                        brush = Brush.radialGradient(
                            colors = listOf(
                                mode.tint.copy(alpha = 0.8f),
                                mode.tint.copy(alpha = 0.2f),
                            ),
                            center = center,
                            radius = radius
                        ),
                        radius = radius * 0.8f,
                        center = center
                    )
                }
            }

            // Text
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = mode.title,
                    fontSize = 14.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = mode.tint
                )
                Text(
                    text = statusText,
                    fontSize = 12.sp,
                    color = OpenRockyPalette.muted
                )
            }

            // End button
            TextButton(
                onClick = onEnd,
                colors = ButtonDefaults.textButtonColors(contentColor = OpenRockyPalette.error)
            ) {
                Icon(Icons.Default.Stop, null, modifier = Modifier.size(16.dp))
                Spacer(Modifier.width(4.dp))
                Text("End", fontSize = 13.sp)
            }
        }
    }
}

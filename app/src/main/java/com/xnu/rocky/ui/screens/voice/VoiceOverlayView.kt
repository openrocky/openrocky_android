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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.xnu.rocky.models.SessionMode
import com.xnu.rocky.ui.theme.OpenRockyPalette

@Composable
fun VoiceOverlayView(
    mode: SessionMode,
    statusText: String,
    onEnd: () -> Unit
) {
    val infiniteTransition = rememberInfiniteTransition(label = "voicePanel")

    val orbScale by infiniteTransition.animateFloat(
        initialValue = 0.85f,
        targetValue = 1.15f,
        animationSpec = infiniteRepeatable(
            animation = tween(1200, easing = EaseInOut),
            repeatMode = RepeatMode.Reverse
        ),
        label = "orbScale"
    )

    // Fixed-height bottom panel (~2.5x composer bar)
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .height(130.dp)
            .navigationBarsPadding(),
        shape = RoundedCornerShape(topStart = 20.dp, topEnd = 20.dp),
        color = OpenRockyPalette.cardElevated,
        shadowElevation = 12.dp
    ) {
        Row(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 20.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // Animated orb
            Box(
                modifier = Modifier.size(52.dp),
                contentAlignment = Alignment.Center
            ) {
                Canvas(modifier = Modifier.size(48.dp)) {
                    val center = Offset(size.width / 2, size.height / 2)
                    val radius = size.minDimension / 2 * orbScale

                    // Outer glow
                    drawCircle(
                        brush = Brush.radialGradient(
                            colors = listOf(
                                mode.tint.copy(alpha = 0.4f),
                                mode.tint.copy(alpha = 0.0f),
                            ),
                            center = center,
                            radius = radius * 1.5f
                        ),
                        radius = radius * 1.3f,
                        center = center
                    )

                    // Main orb
                    drawCircle(
                        brush = Brush.radialGradient(
                            colors = listOf(
                                mode.tint.copy(alpha = 0.9f),
                                mode.tint.copy(alpha = 0.4f),
                            ),
                            center = center,
                            radius = radius
                        ),
                        radius = radius * 0.8f,
                        center = center
                    )
                }
            }

            // Status
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = mode.title,
                    fontSize = 16.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = mode.tint
                )
                Spacer(Modifier.height(2.dp))
                Text(
                    text = statusText.ifBlank { "Listening..." },
                    fontSize = 13.sp,
                    color = OpenRockyPalette.mutedStatic
                )
            }

            // End button
            IconButton(
                onClick = onEnd,
                modifier = Modifier
                    .size(44.dp)
                    .clip(CircleShape)
                    .background(OpenRockyPalette.error.copy(alpha = 0.15f))
            ) {
                Icon(
                    Icons.Default.Stop,
                    contentDescription = "End",
                    tint = OpenRockyPalette.error,
                    modifier = Modifier.size(22.dp)
                )
            }
        }
    }
}

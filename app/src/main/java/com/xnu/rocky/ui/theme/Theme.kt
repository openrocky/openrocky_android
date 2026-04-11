//
// OpenRocky — Voice-first AI Agent
// https://github.com/openrocky
//
// Developed by everettjf with the assistance of Claude Code and Codex.
// Date: 2026-03-25
// Copyright (c) 2026 everettjf. All rights reserved.
//

package com.xnu.rocky.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

private val DarkColorScheme = darkColorScheme(
    primary = OpenRockyPrimary,
    secondary = OpenRockySecondary,
    background = OpenRockyDarkBackground,
    surface = OpenRockyDarkSurface,
    surfaceVariant = OpenRockyDarkSurfaceVariant,
    onPrimary = Color.White,
    onBackground = Color(0xE0FFFFFF),
    onSurface = Color(0xE0FFFFFF),
    onSurfaceVariant = Color(0x8CFFFFFF),
    outline = Color(0x1AFFFFFF),
    error = OpenRockyError,
    onError = Color.White,
    surfaceContainer = OpenRockyDarkSurface,
    surfaceContainerHigh = OpenRockyDarkSurfaceVariant,
)

private val LightColorScheme = lightColorScheme(
    primary = OpenRockyPrimary,
    secondary = OpenRockySecondary,
    background = OpenRockyLightBackground,
    surface = OpenRockyLightSurface,
    surfaceVariant = OpenRockyLightSurfaceVariant,
    onPrimary = Color.White,
    onBackground = Color(0xE0000000),
    onSurface = Color(0xE0000000),
    onSurfaceVariant = Color(0x80000000),
    outline = Color(0x1A000000),
    error = OpenRockyError,
    onError = Color.White,
    surfaceContainer = OpenRockyLightSurface,
    surfaceContainerHigh = OpenRockyLightSurfaceVariant,
)

@Composable
fun OpenRockyTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit
) {
    MaterialTheme(
        colorScheme = if (darkTheme) DarkColorScheme else LightColorScheme,
        typography = OpenRockyTypography,
        content = content
    )
}

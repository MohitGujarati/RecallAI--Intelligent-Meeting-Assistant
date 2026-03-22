package com.example.recall_ai.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

/**
 * App-wide Material3 theme — clean, light, minimal.
 *
 * Design language: off-white backgrounds, white cards, navy accent.
 * No dark variant — the design is purposefully light for a professional,
 * approachable feel.
 */
private val RecallColorScheme = lightColorScheme(
    primary          = ColorNavy,
    onPrimary        = Color.White,
    primaryContainer = ColorBlueTint,

    secondary        = ColorDone,
    onSecondary      = Color.White,

    tertiary         = ColorProcessing,
    onTertiary       = Color.White,

    background       = ColorBackground,
    onBackground     = ColorOnBackground,

    surface          = ColorSurface,
    onSurface        = ColorOnBackground,
    surfaceVariant   = ColorSurfaceVariant,
    onSurfaceVariant = ColorOnSurfaceDim,

    outline          = ColorBorder,
    outlineVariant   = ColorBorder,

    error            = ColorError,
    onError          = Color.White,

    scrim            = ColorScrim
)

@Composable
fun RecallTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = RecallColorScheme,
        typography  = RecallTypography,
        content     = content
    )
}
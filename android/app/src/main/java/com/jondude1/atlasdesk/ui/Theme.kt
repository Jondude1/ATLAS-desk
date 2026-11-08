package com.jondude1.atlasdesk.ui

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

/**
 * PLACEHOLDER theme — neutral dark grays only. The real design language
 * lands after the design-research pass and Jon's pick of a direction.
 * The one functional color is the amber stale indicator.
 */
private val PlaceholderScheme = darkColorScheme(
    primary = Color(0xFF9FB0C3),
    onPrimary = Color(0xFF0B0E13),
    background = Color(0xFF0B0E13),
    onBackground = Color(0xFFE6EAF0),
    surface = Color(0xFF151A22),
    onSurface = Color(0xFFE6EAF0),
    surfaceVariant = Color(0xFF1C232E),
    onSurfaceVariant = Color(0xFF9AA7B8),
    outline = Color(0xFF232B38),
)

val StaleAmber = Color(0xFFF5B544)
val LiveGreen = Color(0xFF4CC38A)

@Composable
fun AtlasDeskTheme(content: @Composable () -> Unit) {
    MaterialTheme(colorScheme = PlaceholderScheme, content = content)
}

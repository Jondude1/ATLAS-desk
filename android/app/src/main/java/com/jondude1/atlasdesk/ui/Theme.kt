package com.jondude1.atlasdesk.ui

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

/**
 * ATLAS Desk theme — Direction A "Soft Midnight" (design decision
 * 2026-11-08): Catppuccin Mocha. Flat cards on the crust background,
 * pastel accents reserved for identity and state, never decoration.
 *
 *   crust    #11111B  app background
 *   base     #1E1E2E  raised background
 *   mantle   #181825  card fill
 *   surface0 #313244  chips, borders, icon tints
 *   text     #CDD6F4  primary text
 *   subtext0 #A6ADC8  secondary text
 */
object Mocha {
    val Crust = Color(0xFF11111B)
    val Base = Color(0xFF1E1E2E)
    val Mantle = Color(0xFF181825)
    val Surface0 = Color(0xFF313244)
    val Text = Color(0xFFCDD6F4)
    val Subtext = Color(0xFFA6ADC8)

    // Fixed per-card accents (identity, set once, never rotated)
    val Mauve = Color(0xFFCBA6F7) // Printer
    val Teal = Color(0xFF94E2D5) // House
    val Green = Color(0xFFA6E3A1) // Shop + "ok/live" state
    val Blue = Color(0xFF89B4FA) // Next Up
    val Peach = Color(0xFFFAB387) // in-progress / warm state
    val Yellow = Color(0xFFF9E2AF) // stale / caution state
    val Red = Color(0xFFF38BA8) // real alerts only
}

private val SoftMidnightScheme = darkColorScheme(
    primary = Mocha.Mauve,
    onPrimary = Mocha.Crust,
    background = Mocha.Crust,
    onBackground = Mocha.Text,
    surface = Mocha.Mantle,
    onSurface = Mocha.Text,
    surfaceVariant = Mocha.Surface0,
    onSurfaceVariant = Mocha.Subtext,
    outline = Mocha.Surface0,
    error = Mocha.Red,
)

val StaleAmber = Mocha.Yellow
val LiveGreen = Mocha.Green

@Composable
fun AtlasDeskTheme(content: @Composable () -> Unit) {
    MaterialTheme(colorScheme = SoftMidnightScheme, content = content)
}

package com.xstar.guitar.ui

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.graphics.Color

/** Shared palette for the instrument, readouts, controls and settings dialog. */
object TunerStyle {
    /** Keep the tuner visually aligned with the metronome and chord library. */
    val background get() = ToolPalette.background
    val surface get() = ToolPalette.panel
    val ink get() = ToolPalette.ink
    val secondary get() = ToolPalette.muted
    val outline get() = ToolPalette.line
    val accent get() = ToolPalette.accent
    val steady get() = ToolPalette.steady
}

/** Shared palette for the three instrument tools. */
object ToolPalette {
    private val default = PaletteColors(Color(0xFF101211), Color(0xFF1B1F1B), Color(0xFF30382E), Color(0xFFF0F2E9), Color(0xFFA0A899), Color(0xFFD3FC79), Color(0xFFFFB779), Color(0xFFD3FC79))
    private var active = default
    val background get() = active.background
    val panel get() = active.panel
    val line get() = active.line
    val ink get() = active.ink
    val muted get() = active.muted
    val accent get() = active.accent
    val attention get() = active.attention
    val steady get() = active.steady

    fun update(colors: ColorScheme) {
        active = PaletteColors(colors.background, colors.surface, colors.outline, colors.onSurface, colors.onSurfaceVariant, colors.primary, colors.tertiary, colors.secondary)
    }
}

private data class PaletteColors(
    val background: Color, val panel: Color, val line: Color, val ink: Color,
    val muted: Color, val accent: Color, val attention: Color, val steady: Color,
)

@Composable
fun TunerTheme(content: @Composable () -> Unit) {
    MaterialTheme(content = content)
}

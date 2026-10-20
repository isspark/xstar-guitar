package com.xstar.guitar.ui

import androidx.compose.material3.ColorScheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color

enum class ThemePreset(val label: String, val subtitle: String, val accent: Long) {
    Aurora("极光夜", "沉浸深色", 0xFF886CFF),
    Wood("暖木", "温和自然", 0xFFA14E2D),
    Glacier("冰川", "清晰轻量", 0xFF087FA7),
    Pulse("节奏绿", "工具荧光", 0xFFD3FC79),
}

data class ThemeSettings(
    val preset: ThemePreset = ThemePreset.Pulse,
    val accent: Long = ThemePreset.Pulse.accent,
    val glow: Boolean = true,
    val compact: Boolean = false,
    val reduceMotion: Boolean = false,
)

data class XStarColors(
    val canvas: Color,
    val raised: Color,
    val mutedSurface: Color,
    val steady: Color,
    val steadySoft: Color,
    val attention: Color,
    val glow: Color,
)

val LocalXStarColors = staticCompositionLocalOf {
    XStarColors(Color.Unspecified, Color.Unspecified, Color.Unspecified, Color.Unspecified, Color.Unspecified, Color.Unspecified, Color.Unspecified)
}

private fun colors(settings: ThemeSettings): Pair<ColorScheme, XStarColors> {
    val accent = Color(settings.accent)
    return when (settings.preset) {
        ThemePreset.Aurora -> darkColorScheme(
            primary = accent, onPrimary = Color.White, primaryContainer = accent.copy(alpha = .16f),
            background = Color(0xFF090B13), onBackground = Color(0xFFF4F5FB),
            surface = Color(0xFF121621), onSurface = Color(0xFFF4F5FB),
            surfaceVariant = Color(0xFF202536), onSurfaceVariant = Color(0xFF949CAF),
            outline = Color(0xFF343A4B), secondary = Color(0xFF54D7C4), tertiary = Color(0xFFFFB454),
        ) to XStarColors(Color(0xFF090B13), Color(0xFF181D2A), Color(0xFF202536), Color(0xFF54D7C4), Color(0xFF153B39), Color(0xFFFFB454), accent.copy(alpha = .20f))
        ThemePreset.Wood -> lightColorScheme(
            primary = accent, onPrimary = Color.White, primaryContainer = accent.copy(alpha = .14f),
            background = Color(0xFFF3EFE8), onBackground = Color(0xFF1B211D),
            surface = Color(0xFFFFFDF8), onSurface = Color(0xFF1B211D),
            surfaceVariant = Color(0xFFE8E1D7), onSurfaceVariant = Color(0xFF687169),
            outline = Color(0xFFD0C5B7), secondary = Color(0xFF147D73), tertiary = Color(0xFFB76A08),
        ) to XStarColors(Color(0xFFF3EFE8), Color(0xFFFFFAF1), Color(0xFFE8E1D7), Color(0xFF147D73), Color(0xFFD8ECE8), Color(0xFFB76A08), accent.copy(alpha = .12f))
        ThemePreset.Glacier -> lightColorScheme(
            primary = accent, onPrimary = Color.White, primaryContainer = accent.copy(alpha = .13f),
            background = Color(0xFFEEF3F7), onBackground = Color(0xFF14202A),
            surface = Color(0xFFFBFDFF), onSurface = Color(0xFF14202A),
            surfaceVariant = Color(0xFFE3EBF1), onSurfaceVariant = Color(0xFF647585),
            outline = Color(0xFFC7D5DE), secondary = Color(0xFF147D73), tertiary = Color(0xFFA86208),
         ) to XStarColors(Color(0xFFEEF3F7), Color.White, Color(0xFFE3EBF1), Color(0xFF147D73), Color(0xFFD7EEEA), Color(0xFFA86208), accent.copy(alpha = .14f))
        ThemePreset.Pulse -> darkColorScheme(
            primary = accent, onPrimary = Color(0xFF101211), primaryContainer = accent.copy(alpha = .12f),
            background = Color(0xFF101211), onBackground = Color(0xFFF0F2E9),
            surface = Color(0xFF1B1F1B), onSurface = Color(0xFFF0F2E9),
            surfaceVariant = Color(0xFF30382E), onSurfaceVariant = Color(0xFFA0A899),
            outline = Color(0xFF30382E), secondary = Color(0xFFD3FC79), tertiary = Color(0xFFFFB779),
        ) to XStarColors(Color(0xFF101211), Color(0xFF1B1F1B), Color(0xFF30382E), Color(0xFFD3FC79), Color(0xFF26321B), Color(0xFFFFB779), Color(0xFF252D20))
    }
}

@Composable
fun XStarTheme(settings: ThemeSettings, content: @Composable () -> Unit) {
    val (scheme, xstarColors) = colors(settings)
    ToolPalette.update(scheme)
    androidx.compose.runtime.CompositionLocalProvider(LocalXStarColors provides xstarColors) {
        MaterialTheme(colorScheme = scheme, typography = XStarTypography) {
            SideEffect { ToolPalette.update(scheme) }
            content()
        }
    }
}

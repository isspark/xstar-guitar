package com.xstar.guitar.ui

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ThemeSettingsTest {
    @Test
    fun defaultSettingsUsePulseTheme() {
        val settings = ThemeSettings()

        assertEquals(ThemePreset.Pulse, settings.preset)
        assertEquals(ThemePreset.Pulse.accent, settings.accent)
        assertTrue(settings.glow)
    }

    @Test
    fun presetAccentColorsAreUniqueAndOpaque() {
        val accents = ThemePreset.entries.map { it.accent }

        assertEquals(accents.size, accents.distinct().size)
        assertTrue(accents.all { it ushr 24 == 0xFFL })
    }
}

package com.xstar.guitar.ui

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ThemeSettingsTest {
    @Test
    fun defaultSettingsUseAuroraTheme() {
        val settings = ThemeSettings()

        assertEquals(ThemePreset.Aurora, settings.preset)
        assertEquals(ThemePreset.Aurora.accent, settings.accent)
        assertTrue(settings.glow)
    }

    @Test
    fun presetAccentColorsAreUniqueAndOpaque() {
        val accents = ThemePreset.entries.map { it.accent }

        assertEquals(accents.size, accents.distinct().size)
        assertTrue(accents.all { it ushr 24 == 0xFFL })
    }
}

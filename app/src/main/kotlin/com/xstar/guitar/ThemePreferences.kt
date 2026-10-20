package com.xstar.guitar

import android.content.Context
import androidx.core.content.edit
import com.xstar.guitar.ui.ThemePreset
import com.xstar.guitar.ui.ThemeSettings

class ThemePreferences(context: Context) {
    private val preferences = context.getSharedPreferences("appearance", Context.MODE_PRIVATE)

    fun load(): ThemeSettings {
        val preset = runCatching { ThemePreset.valueOf(preferences.getString("preset", null) ?: "Pulse") }.getOrDefault(ThemePreset.Pulse)
        return ThemeSettings(
            preset = preset,
            accent = preferences.getLong("accent", preset.accent),
            glow = preferences.getBoolean("glow", true),
            compact = preferences.getBoolean("compact", false),
            reduceMotion = preferences.getBoolean("reduceMotion", false),
        )
    }

    fun save(settings: ThemeSettings) {
        preferences.edit {
            putString("preset", settings.preset.name)
            putLong("accent", settings.accent)
            putBoolean("glow", settings.glow)
            putBoolean("compact", settings.compact)
            putBoolean("reduceMotion", settings.reduceMotion)
        }
    }
}

package com.xstar.guitar

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.xstar.guitar.ui.ThemeSettings
import com.xstar.guitar.ui.XStarTheme
import com.xstar.guitar.ui.ThemePreset
import androidx.core.view.WindowCompat
import com.xstar.guitar.data.XStarDatabase

class MainActivity : ComponentActivity() {
    private lateinit var themePreferences: ThemePreferences
    private var themeSettings by mutableStateOf(ThemeSettings())

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        themePreferences = ThemePreferences(this)
        themeSettings = themePreferences.load()
        val appPreferences = AppPreferences(this)
        val database = XStarDatabase.create(this)
        setContent {
            XStarTheme(themeSettings) {
                SideEffect {
                    val lightSystemBars = themeSettings.preset != ThemePreset.Aurora && themeSettings.preset != ThemePreset.Pulse
                    WindowCompat.getInsetsController(window, window.decorView).apply {
                        isAppearanceLightStatusBars = lightSystemBars
                        isAppearanceLightNavigationBars = lightSystemBars
                    }
                }
                XStarApp(
                    themeSettings = themeSettings,
                        appPreferences = appPreferences,
                        database = database,
                    onThemeSettingsChanged = {
                        themeSettings = it
                        themePreferences.save(it)
                    },
                )
            }
        }
    }
}

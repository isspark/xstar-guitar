package com.xstar.guitar.ui

import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable

/** Read composition-local theme tokens so live theme changes recompose all practice pages. */
internal object PracticeThemeColors {
    val background @Composable get() = MaterialTheme.colorScheme.background
    val panel @Composable get() = MaterialTheme.colorScheme.surface
    val line @Composable get() = MaterialTheme.colorScheme.outline
    val ink @Composable get() = MaterialTheme.colorScheme.onSurface
    val muted @Composable get() = MaterialTheme.colorScheme.onSurfaceVariant
    val accent @Composable get() = MaterialTheme.colorScheme.primary
    val attention @Composable get() = MaterialTheme.colorScheme.tertiary
}

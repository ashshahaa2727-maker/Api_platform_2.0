package com.example.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable

private val ModForgeColorScheme = darkColorScheme(
    primary = MochaMauve,
    onPrimary = MochaCrust,
    primaryContainer = MochaSurface0,
    onPrimaryContainer = MochaText,
    secondary = MochaBlue,
    onSecondary = MochaCrust,
    secondaryContainer = MochaSurface1,
    onSecondaryContainer = MochaText,
    tertiary = MochaGreen,
    onTertiary = MochaCrust,
    background = MochaCrust,
    onBackground = MochaText,
    surface = MochaMantle,
    onSurface = MochaText,
    surfaceVariant = MochaBase,
    onSurfaceVariant = MochaSubtext0,
    error = MochaRed,
    onError = MochaCrust,
    outline = MochaSurface1
)

@Composable
fun MyApplicationTheme(
    content: @Composable () -> Unit
) {
    MaterialTheme(
        colorScheme = ModForgeColorScheme,
        typography = Typography,
        content = content
    )
}

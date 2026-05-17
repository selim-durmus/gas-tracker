package com.example.gastracker.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

private val OledBlack = Color(0xFF000000)
private val OledSurfaceContainer = Color(0xFF0F0F10)
private val OledSurfaceContainerHigh = Color(0xFF161617)
private val OledOutline = Color(0xFF3A3A3D)

private val OledDarkScheme = darkColorScheme(
    primary = GasBlue80,
    secondary = GasBlueGrey80,
    tertiary = GasGold80,
    background = OledBlack,
    surface = OledBlack,
    surfaceVariant = OledSurfaceContainerHigh,
    surfaceContainerLowest = OledBlack,
    surfaceContainerLow = OledBlack,
    surfaceContainer = OledSurfaceContainer,
    surfaceContainerHigh = OledSurfaceContainerHigh,
    surfaceContainerHighest = OledSurfaceContainerHigh,
    outline = OledOutline,
    outlineVariant = OledOutline,
)

private val LightScheme = lightColorScheme(
    primary = GasBlue40,
    secondary = GasBlueGrey40,
    tertiary = GasGold40,
)

@Composable
fun GasTrackerTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit,
) {
    val colorScheme = if (darkTheme) OledDarkScheme else LightScheme
    MaterialTheme(
        colorScheme = colorScheme,
        typography = AppTypography,
        content = content,
    )
}

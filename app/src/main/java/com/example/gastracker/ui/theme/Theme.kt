package com.example.gastracker.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

private val OledBlack = Color(0xFF000000)
private val OledContainer = Color(0xFF0F0F10)
private val OledContainerHigh = Color(0xFF161617)
private val OledContainerHighest = Color(0xFF1F1F20)
private val OledOutline = Color(0xFF3A3A3D)
private val OledOutlineVariant = Color(0xFF2A2A2D)
private val OnDark = Color(0xFFE6E1E5)
private val OnDarkVariant = Color(0xFFC5C5C5)

private val OledDarkScheme = darkColorScheme(
    primary = Gold,
    onPrimary = GoldDarkText,
    primaryContainer = Gold,
    onPrimaryContainer = GoldDarkText,
    secondary = GoldMuted,
    onSecondary = GoldDarkText,
    secondaryContainer = Gold,
    onSecondaryContainer = GoldDarkText,
    tertiary = Gold,
    onTertiary = GoldDarkText,
    tertiaryContainer = GoldDeep,
    onTertiaryContainer = GoldOnContainer,
    background = OledBlack,
    onBackground = OnDark,
    surface = OledBlack,
    onSurface = OnDark,
    surfaceVariant = OledContainerHigh,
    onSurfaceVariant = OnDarkVariant,
    surfaceContainerLowest = OledBlack,
    surfaceContainerLow = OledBlack,
    surfaceContainer = OledContainer,
    surfaceContainerHigh = OledContainerHigh,
    surfaceContainerHighest = OledContainerHighest,
    outline = OledOutline,
    outlineVariant = OledOutlineVariant,
)

private val LightScheme = lightColorScheme(
    primary = GoldStrong,
    onPrimary = Color.White,
    primaryContainer = GoldLightTint,
    onPrimaryContainer = GoldDarkText,
    secondary = GoldStrong,
    onSecondary = Color.White,
    secondaryContainer = GoldLightTint,
    onSecondaryContainer = GoldDarkText,
    tertiary = GoldStrong,
    onTertiary = Color.White,
    tertiaryContainer = GoldLightTint,
    onTertiaryContainer = GoldDarkText,
    background = WarmOffWhite,
    surface = WarmOffWhite,
    surfaceVariant = WarmSurfaceVariantLight,
    outline = WarmOutlineLight,
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

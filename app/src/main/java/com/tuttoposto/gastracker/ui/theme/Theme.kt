package com.tuttoposto.gastracker.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp

// Brand: classic metallic gold on OLED black (carried over from v1).
val Gold = Color(0xFFD4AF37)
private val GoldBright = Color(0xFFE9C766)
private val GoldInk = Color(0xFF231A00)
private val GoldDeep = Color(0xFF3A2D05)
private val GoldLightContainer = Color(0xFFFFE9A8)
private val GoldLightPrimary = Color(0xFF7A5C00)

private val Black = Color(0xFF000000)

private val DarkScheme = darkColorScheme(
    primary = Gold,
    onPrimary = GoldInk,
    primaryContainer = GoldDeep,
    onPrimaryContainer = GoldBright,
    secondary = Color(0xFFCDBF9A),
    onSecondary = Color(0xFF2B2410),
    secondaryContainer = Color(0xFF2A2720),
    onSecondaryContainer = Color(0xFFEDE1C2),
    tertiary = Color(0xFF8FB8FF),
    onTertiary = Color(0xFF002E69),
    background = Black,
    onBackground = Color(0xFFECE7DF),
    surface = Black,
    onSurface = Color(0xFFECE7DF),
    surfaceVariant = Color(0xFF1C1B19),
    onSurfaceVariant = Color(0xFFB9B3A8),
    surfaceContainerLowest = Black,
    surfaceContainerLow = Color(0xFF0B0B0A),
    surfaceContainer = Color(0xFF121211),
    surfaceContainerHigh = Color(0xFF1A1A18),
    surfaceContainerHighest = Color(0xFF242320),
    outline = Color(0xFF4A4740),
    outlineVariant = Color(0xFF2C2A26),
    error = Color(0xFFFFB4AB),
    onError = Color(0xFF690005),
    errorContainer = Color(0xFF93000A),
    onErrorContainer = Color(0xFFFFDAD6),
)

private val LightScheme = lightColorScheme(
    primary = GoldLightPrimary,
    onPrimary = Color.White,
    primaryContainer = GoldLightContainer,
    onPrimaryContainer = GoldInk,
    secondary = Color(0xFF6A5D3F),
    onSecondary = Color.White,
    secondaryContainer = Color(0xFFF3E2BC),
    onSecondaryContainer = Color(0xFF241A04),
    tertiary = Color(0xFF2D5DB5),
    onTertiary = Color.White,
    background = Color(0xFFFFFBF4),
    onBackground = Color(0xFF1E1B16),
    surface = Color(0xFFFFFBF4),
    onSurface = Color(0xFF1E1B16),
    surfaceVariant = Color(0xFFEDE1CF),
    onSurfaceVariant = Color(0xFF4D4639),
    surfaceContainerLowest = Color.White,
    surfaceContainerLow = Color(0xFFFBF5EA),
    surfaceContainer = Color(0xFFF5EFE3),
    surfaceContainerHigh = Color(0xFFEFE9DD),
    surfaceContainerHighest = Color(0xFFE9E3D7),
    outline = Color(0xFF7F7667),
    outlineVariant = Color(0xFFD1C5B4),
)

/** Colors Material 3 has no slot for. */
@Immutable
data class ExtraColors(
    val chartPrimary: Color,
    val chartSecondary: Color,
    val chartGrid: Color,
    val good: Color,
    val bad: Color,
)

private val DarkExtra = ExtraColors(
    chartPrimary = Gold,
    chartSecondary = Color(0xFF8FB8FF),
    chartGrid = Color(0xFF2C2A26),
    good = Color(0xFF7FD69A),
    bad = Color(0xFFFF8A80),
)

private val LightExtra = ExtraColors(
    chartPrimary = Color(0xFFB08A10),
    chartSecondary = Color(0xFF2D5DB5),
    chartGrid = Color(0xFFE2D9C9),
    good = Color(0xFF1E7A3F),
    bad = Color(0xFFB3261E),
)

private val LocalExtraColors = staticCompositionLocalOf { DarkExtra }

object GasTheme {
    val extra: ExtraColors
        @Composable @ReadOnlyComposable get() = LocalExtraColors.current
}

private val BaseTypography = Typography()

/** Tabular figures keep columns of amounts aligned. */
private fun TextStyle.tabular() = copy(fontFeatureSettings = "tnum")

private val AppTypography = BaseTypography.copy(
    displaySmall = BaseTypography.displaySmall.copy(fontWeight = FontWeight.SemiBold).tabular(),
    headlineMedium = BaseTypography.headlineMedium.copy(fontWeight = FontWeight.SemiBold).tabular(),
    headlineSmall = BaseTypography.headlineSmall.copy(fontWeight = FontWeight.SemiBold).tabular(),
    titleLarge = BaseTypography.titleLarge.copy(fontWeight = FontWeight.SemiBold),
    titleMedium = BaseTypography.titleMedium.copy(fontWeight = FontWeight.SemiBold).tabular(),
    titleSmall = BaseTypography.titleSmall.tabular(),
    bodyLarge = BaseTypography.bodyLarge.tabular(),
    bodyMedium = BaseTypography.bodyMedium.tabular(),
    bodySmall = BaseTypography.bodySmall.tabular(),
    labelLarge = BaseTypography.labelLarge.copy(fontWeight = FontWeight.SemiBold),
    labelSmall = BaseTypography.labelSmall.copy(letterSpacing = 0.6.sp),
)

@Composable
fun GasTrackerTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit,
) {
    androidx.compose.runtime.CompositionLocalProvider(
        LocalExtraColors provides if (darkTheme) DarkExtra else LightExtra,
    ) {
        MaterialTheme(
            colorScheme = if (darkTheme) DarkScheme else LightScheme,
            typography = AppTypography,
            content = content,
        )
    }
}

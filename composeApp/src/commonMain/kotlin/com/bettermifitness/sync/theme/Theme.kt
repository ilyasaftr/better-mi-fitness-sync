package com.bettermifitness.sync.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

private val LightColorScheme = lightColorScheme(
    primary = BrandColors.Orange,
    onPrimary = Color.White,
    primaryContainer = Color(0xFFFFDBCC),
    onPrimaryContainer = BrandColors.OrangeDeep,
    secondary = BrandColors.Info,
    onSecondary = Color.White,
    secondaryContainer = BrandColors.InfoContainerLight,
    onSecondaryContainer = BrandColors.Navy,
    tertiary = BrandColors.Caution,
    onTertiary = Color.White,
    tertiaryContainer = BrandColors.CautionContainerLight,
    onTertiaryContainer = Color(0xFF3D2500),
    background = BrandColors.CanvasLight,
    onBackground = BrandColors.Ink,
    surface = BrandColors.SurfaceLight,
    onSurface = BrandColors.Ink,
    surfaceVariant = BrandColors.SurfaceVariantLight,
    onSurfaceVariant = BrandColors.InkMuted,
    outline = Color(0xFFC5CED8),
    outlineVariant = Color(0xFFDCE3EB),
    error = BrandColors.Danger,
    onError = Color.White,
    errorContainer = BrandColors.DangerContainerLight,
    onErrorContainer = Color(0xFF5C1010),
)

private val DarkColorScheme = darkColorScheme(
    primary = BrandColors.OrangeBright,
    onPrimary = BrandColors.NavyDeep,
    primaryContainer = Color(0xFF5C2E1C),
    onPrimaryContainer = Color(0xFFFFDBCC),
    secondary = Color(0xFF90CAF9),
    onSecondary = BrandColors.NavyDeep,
    secondaryContainer = BrandColors.InfoContainerDark,
    onSecondaryContainer = BrandColors.OnDark,
    tertiary = Color(0xFFFFB74D),
    onTertiary = BrandColors.NavyDeep,
    tertiaryContainer = BrandColors.CautionContainerDark,
    onTertiaryContainer = Color(0xFFFFE0A3),
    // Neutral dark surfaces (system-like) — brand orange stays primary
    background = Color(0xFF121214),
    onBackground = BrandColors.OnDark,
    surface = Color(0xFF1C1C1E),
    onSurface = BrandColors.OnDark,
    surfaceVariant = Color(0xFF2C2C2E),
    onSurfaceVariant = BrandColors.OnDarkMuted,
    outline = Color(0xFF48484A),
    outlineVariant = Color(0xFF3A3A3C),
    error = Color(0xFFEF9A9A),
    onError = Color(0xFF1C1C1E),
    errorContainer = BrandColors.DangerContainerDark,
    onErrorContainer = Color(0xFFFFDAD6),
)

@Composable
fun BetterMiFitnessSyncTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit,
) {
    MaterialTheme(
        colorScheme = if (darkTheme) DarkColorScheme else LightColorScheme,
        typography = brandTypography(),
        content = content,
    )
}

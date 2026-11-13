package com.aistudio.youthverse.rvzkmq.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

enum class AppThemeMode {
    DARK,
    AMOLED,
    LIGHT
}

private val CinematicDarkColorScheme = darkColorScheme(
    primary = VioletPrimary,
    onPrimary = Color.White,
    primaryContainer = ElevatedSurfaceDark,
    onPrimaryContainer = VioletVariant,
    secondary = CyanAccent,
    onSecondary = DeepSpaceBackground,
    secondaryContainer = SurfaceDark,
    onSecondaryContainer = CyanAccent,
    tertiary = AmberGold,
    onTertiary = DeepSpaceBackground,
    background = DeepSpaceBackground,
    onBackground = TextPrimaryDark,
    surface = SurfaceDark,
    onSurface = TextPrimaryDark,
    surfaceVariant = ElevatedSurfaceDark,
    onSurfaceVariant = TextSecondaryDark,
    outline = BorderDark,
    error = ErrorRed,
    onError = Color.White
)

private val AmoledColorScheme = darkColorScheme(
    primary = VioletPrimary,
    onPrimary = Color.White,
    primaryContainer = AmoledElevated,
    onPrimaryContainer = VioletVariant,
    secondary = CyanAccent,
    onSecondary = Color.Black,
    secondaryContainer = AmoledSurface,
    onSecondaryContainer = CyanAccent,
    tertiary = AmberGold,
    onTertiary = Color.Black,
    background = AmoledBackground,
    onBackground = TextPrimaryDark,
    surface = AmoledSurface,
    onSurface = TextPrimaryDark,
    surfaceVariant = AmoledElevated,
    onSurfaceVariant = TextSecondaryDark,
    outline = BorderDark,
    error = ErrorRed,
    onError = Color.White
)

private val LightColorScheme = lightColorScheme(
    primary = VioletPrimary,
    onPrimary = Color.White,
    primaryContainer = LightSurfaceElevated,
    onPrimaryContainer = VioletPrimary,
    secondary = CyanAccent,
    onSecondary = LightTextPrimary,
    secondaryContainer = LightSurfaceElevated,
    onSecondaryContainer = LightTextPrimary,
    tertiary = AmberGold,
    onTertiary = LightTextPrimary,
    background = LightBackground,
    onBackground = LightTextPrimary,
    surface = LightSurface,
    onSurface = LightTextPrimary,
    surfaceVariant = LightSurfaceElevated,
    onSurfaceVariant = LightTextSecondary,
    outline = LightBorder,
    error = ErrorRed,
    onError = Color.White
)

@Composable
fun YouthVerseTheme(
    themeMode: AppThemeMode = AppThemeMode.DARK,
    content: @Composable () -> Unit
) {
    val colorScheme = when (themeMode) {
        AppThemeMode.DARK -> CinematicDarkColorScheme
        AppThemeMode.AMOLED -> AmoledColorScheme
        AppThemeMode.LIGHT -> LightColorScheme
    }

    MaterialTheme(
        colorScheme = colorScheme,
        typography = Typography,
        content = content
    )
}

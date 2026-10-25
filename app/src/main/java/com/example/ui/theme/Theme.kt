package com.example.ui.theme

import androidx.compose.material3.ColorScheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

private val AdfDarkColorScheme = darkColorScheme(
    primary = AdfElectricCyan,
    onPrimary = AdfMidnightBackground,
    primaryContainer = AdfVibrantBlue,
    onPrimaryContainer = Color.White,
    secondary = AdfCyberViolet,
    onSecondary = Color.White,
    secondaryContainer = AdfSurfaceElevated,
    onSecondaryContainer = AdfElectricCyan,
    tertiary = AdfEmerald,
    onTertiary = Color.White,
    background = AdfMidnightBackground,
    onBackground = AdfTextPrimary,
    surface = AdfObsidianSurface,
    onSurface = AdfTextPrimary,
    surfaceVariant = AdfSurfaceVariant,
    onSurfaceVariant = AdfTextSecondary,
    outline = AdfBorderSubtle,
    outlineVariant = AdfBorderGlow
)

private val AdfLightColorScheme = lightColorScheme(
    primary = AdfVibrantBlue,
    onPrimary = Color.White,
    primaryContainer = Color(0xFFE0E7FF),
    onPrimaryContainer = AdfVibrantBlue,
    secondary = AdfCyberViolet,
    onSecondary = Color.White,
    background = Color(0xFF0A0F1D),
    onBackground = AdfTextPrimary,
    surface = Color(0xFF0F172A),
    onSurface = AdfTextPrimary,
    surfaceVariant = Color(0xFF1E293B),
    onSurfaceVariant = Color(0xFF94A3B8),
    outline = Color(0xFF334155)
)


@Composable
fun AcrobatTheme(
    darkTheme: Boolean = true, // Default to deep midnight ADF AIO theme
    colorScheme: ColorScheme = if (darkTheme) AdfDarkColorScheme else AdfLightColorScheme,
    content: @Composable () -> Unit
) {
    MaterialTheme(
        colorScheme = colorScheme,
        typography = Typography,
        content = content
    )
}

package com.example.ui.theme

import android.content.Context
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

enum class AppThemeStyle(val displayName: String, val badgeColor: Color) {
    LIQUID_GLASS("Liquid Glass (Default)", Color(0xFF38BDF8)),
    ACROBAT_DARK("Midnight Glass", Color(0xFF38BDF8)),
    OLED_PITCH_BLACK("OLED Deep Glass", Color(0xFFCBD5E1)),
    AMBER_SUNSET("Sunset Liquid Glass", Color(0xFFF59E0B)),
    EXECUTIVE_EMERALD("Emerald Liquid Glass", Color(0xFF10B981)),
    FROSTED_CRYSTAL("Frosted Crystal Glass", Color(0xFF818CF8))
}

/**
 * 100% Local, Offline Theme Manager for PDF AIO.
 * Sets Liquid Glass UI as default across all themes.
 */
object ThemeManager {
    private const val PREFS_NAME = "pdf_aio_theme_prefs"
    private const val KEY_THEME = "selected_theme_style"

    private val _currentTheme = MutableStateFlow(AppThemeStyle.LIQUID_GLASS)
    val currentTheme: StateFlow<AppThemeStyle> = _currentTheme.asStateFlow()

    fun initialize(context: Context) {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val savedName = prefs.getString(KEY_THEME, AppThemeStyle.LIQUID_GLASS.name)
        val theme = try {
            AppThemeStyle.valueOf(savedName ?: AppThemeStyle.LIQUID_GLASS.name)
        } catch (_: Exception) {
            AppThemeStyle.LIQUID_GLASS
        }
        _currentTheme.value = theme
    }

    fun setTheme(context: Context, themeStyle: AppThemeStyle) {
        _currentTheme.value = themeStyle
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        prefs.edit().putString(KEY_THEME, themeStyle.name).apply()
    }

    @Composable
    fun getColorScheme(style: AppThemeStyle): ColorScheme {
        return when (style) {
            AppThemeStyle.LIQUID_GLASS -> darkColorScheme(
                primary = Color(0xFF38BDF8), // Liquid Frost Cyan
                secondary = Color(0xFF818CF8), // Glass Iris
                tertiary = Color(0xFF00E5FF), // Specular Sheen
                background = Color(0xFF050814), // Deep Abyss Liquid
                surface = Color(0x2E0D162B), // Frosted Liquid Glass Surface
                surfaceVariant = Color(0x4A131F3B), // Elevated Glass
                onPrimary = Color.Black,
                onBackground = Color(0xFFF8FAFC),
                onSurface = Color(0xFFF8FAFC)
            )
            AppThemeStyle.ACROBAT_DARK -> darkColorScheme(
                primary = Color(0xFF38BDF8),
                secondary = Color(0xFF3B82F6),
                tertiary = Color(0xFF60A5FA),
                background = Color(0xFF070B14),
                surface = Color(0x33101C38),
                surfaceVariant = Color(0x4D182A54),
                onPrimary = Color.Black,
                onBackground = Color(0xFFF8FAFC),
                onSurface = Color(0xFFF8FAFC)
            )
            AppThemeStyle.OLED_PITCH_BLACK -> darkColorScheme(
                primary = Color(0xFFE2E8F0),
                secondary = Color(0xFF94A3B8),
                tertiary = Color(0xFF38BDF8),
                background = Color(0xFF000000),
                surface = Color(0xFF0A0F1D),
                surfaceVariant = Color(0xFF131B2E),
                onPrimary = Color.Black,
                onBackground = Color(0xFFF8FAFC),
                onSurface = Color(0xFFF8FAFC)
            )
            AppThemeStyle.AMBER_SUNSET -> darkColorScheme(
                primary = Color(0xFFF59E0B),
                secondary = Color(0xFFFB923C),
                tertiary = Color(0xFFFDE047),
                background = Color(0xFF100903),
                surface = Color(0x332E1A08),
                surfaceVariant = Color(0x4D45270D),
                onPrimary = Color.Black,
                onBackground = Color(0xFFFFFBEB),
                onSurface = Color(0xFFFFFBEB)
            )
            AppThemeStyle.EXECUTIVE_EMERALD -> darkColorScheme(
                primary = Color(0xFF10B981),
                secondary = Color(0xFF059669),
                tertiary = Color(0xFF34D399),
                background = Color(0xFF030D0A),
                surface = Color(0x330B241C),
                surfaceVariant = Color(0x4D11382C),
                onPrimary = Color.Black,
                onBackground = Color(0xFFECFDF5),
                onSurface = Color(0xFFECFDF5)
            )
            AppThemeStyle.FROSTED_CRYSTAL -> darkColorScheme(
                primary = Color(0xFF818CF8),
                secondary = Color(0xFF38BDF8),
                tertiary = Color(0xFFA78BFA),
                background = Color(0xFF080C1B),
                surface = Color(0x38192244),
                surfaceVariant = Color(0x5928376E),
                onPrimary = Color.Black,
                onBackground = Color(0xFFF8FAFC),
                onSurface = Color(0xFFF8FAFC)
            )
        }
    }
}

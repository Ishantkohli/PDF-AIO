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
    ACROBAT_DARK("Acrobat Dark", Color(0xFF00E5FF)),
    OLED_PITCH_BLACK("OLED Pitch Black", Color(0xFFE11D48)),
    NEON_CYBERPUNK("Neon Cyberpunk", Color(0xFFFF007F)),
    EXECUTIVE_EMERALD("Executive Emerald", Color(0xFF10B981))
}

/**
 * 100% Local, Offline Theme Manager for PDF AIO.
 * Allows switching themes instantly without network dependencies.
 */
object ThemeManager {
    private const val PREFS_NAME = "pdf_aio_theme_prefs"
    private const val KEY_THEME = "selected_theme_style"

    private val _currentTheme = MutableStateFlow(AppThemeStyle.ACROBAT_DARK)
    val currentTheme: StateFlow<AppThemeStyle> = _currentTheme.asStateFlow()

    fun initialize(context: Context) {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val savedName = prefs.getString(KEY_THEME, AppThemeStyle.ACROBAT_DARK.name)
        val theme = try {
            AppThemeStyle.valueOf(savedName ?: AppThemeStyle.ACROBAT_DARK.name)
        } catch (_: Exception) {
            AppThemeStyle.ACROBAT_DARK
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
            AppThemeStyle.ACROBAT_DARK -> darkColorScheme(
                primary = AdfElectricCyan,
                secondary = AdfVibrantBlue,
                tertiary = AdfCyberViolet,
                background = AdfMidnightBackground,
                surface = AdfObsidianSurface,
                surfaceVariant = AdfSurfaceVariant,
                onPrimary = Color.Black,
                onBackground = AdfTextPrimary,
                onSurface = AdfTextPrimary
            )
            AppThemeStyle.OLED_PITCH_BLACK -> darkColorScheme(
                primary = Color(0xFFFF2E93),
                secondary = Color(0xFF00E5FF),
                tertiary = Color(0xFFFFD700),
                background = Color(0xFF000000),
                surface = Color(0xFF0A0A0A),
                surfaceVariant = Color(0xFF141414),
                onPrimary = Color.White,
                onBackground = Color(0xFFF8FAFC),
                onSurface = Color(0xFFF8FAFC)
            )
            AppThemeStyle.NEON_CYBERPUNK -> darkColorScheme(
                primary = Color(0xFFFF007F),
                secondary = Color(0xFF00E5FF),
                tertiary = Color(0xFF9D00FF),
                background = Color(0xFF090014),
                surface = Color(0xFF140029),
                surfaceVariant = Color(0xFF220042),
                onPrimary = Color.White,
                onBackground = Color(0xFFF8FAFC),
                onSurface = Color(0xFFF8FAFC)
            )
            AppThemeStyle.EXECUTIVE_EMERALD -> darkColorScheme(
                primary = Color(0xFF10B981),
                secondary = Color(0xFF059669),
                tertiary = Color(0xFF00E5FF),
                background = Color(0xFF040F0C),
                surface = Color(0xFF0B1F1A),
                surfaceVariant = Color(0xFF132F28),
                onPrimary = Color.Black,
                onBackground = Color(0xFFF8FAFC),
                onSurface = Color(0xFFF8FAFC)
            )
        }
    }
}

package com.example.ui.components

import androidx.compose.animation.*
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Palette
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import com.example.ui.theme.AppThemeStyle
import com.example.ui.theme.ThemeManager
import com.example.ui.theme.*

/**
 * Reusable Local Theme Selector Dialog.
 * Allows switching between Acrobat Dark, OLED Pitch Black, Neon Cyberpunk, and Executive Emerald.
 */
@Composable
fun ThemeSelectorDialog(
    onDismiss: () -> Unit
) {
    val context = LocalContext.current
    val activeTheme by ThemeManager.currentTheme.collectAsState()

    Dialog(onDismissRequest = onDismiss) {
        Surface(
            shape = RoundedCornerShape(24.dp),
            color = AdfSurfaceCard,
            border = CardDefaults.outlinedCardBorder().copy(
                brush = Brush.horizontalGradient(listOf(AdfElectricCyan, AdfVibrantBlue))
            ),
            tonalElevation = 12.dp,
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp)
                .testTag("theme_selector_dialog")
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(20.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Box(
                            modifier = Modifier
                                .size(38.dp)
                                .clip(CircleShape)
                                .background(AdfElectricCyan.copy(alpha = 0.2f)),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = Icons.Default.Palette,
                                contentDescription = null,
                                tint = AdfElectricCyan,
                                modifier = Modifier.size(20.dp)
                            )
                        }
                        Spacer(modifier = Modifier.width(10.dp))
                        Text(
                            text = "App Color Themes",
                            style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                            color = AdfTextPrimary
                        )
                    }

                    IconButton(onClick = onDismiss, modifier = Modifier.size(32.dp)) {
                        Icon(Icons.Default.Close, contentDescription = "Close", tint = AdfTextSecondary)
                    }
                }

                Spacer(modifier = Modifier.height(14.dp))

                AppThemeStyle.entries.forEach { theme ->
                    val isSelected = activeTheme == theme
                    val previewColors = when (theme) {
                        AppThemeStyle.ACROBAT_DARK -> listOf(Color(0xFF070A10), Color(0xFF00E5FF), Color(0xFF2563EB))
                        AppThemeStyle.OLED_PITCH_BLACK -> listOf(Color(0xFF000000), Color(0xFFFF2E93), Color(0xFF00E5FF))
                        AppThemeStyle.NEON_CYBERPUNK -> listOf(Color(0xFF090014), Color(0xFFFF007F), Color(0xFF9D00FF))
                        AppThemeStyle.EXECUTIVE_EMERALD -> listOf(Color(0xFF040F0C), Color(0xFF10B981), Color(0xFF00E5FF))
                    }

                    Surface(
                        shape = RoundedCornerShape(14.dp),
                        color = if (isSelected) AdfMidnightSurface else AdfObsidianSurface,
                        border = CardDefaults.outlinedCardBorder().copy(
                            brush = if (isSelected) Brush.horizontalGradient(listOf(theme.badgeColor, AdfElectricCyan))
                            else Brush.horizontalGradient(listOf(AdfBorderSubtle, AdfBorderSubtle))
                        ),
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 4.dp)
                            .clickable {
                                ThemeManager.setTheme(context, theme)
                            }
                            .testTag("theme_option_${theme.name.lowercase()}")
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(12.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                // Color preview dots
                                Row(
                                    horizontalArrangement = Arrangement.spacedBy((-4).dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    previewColors.forEach { color ->
                                        Box(
                                            modifier = Modifier
                                                .size(18.dp)
                                                .clip(CircleShape)
                                                .background(color)
                                                .border(1.dp, Color.White.copy(alpha = 0.3f), CircleShape)
                                        )
                                    }
                                }

                                Spacer(modifier = Modifier.width(12.dp))

                                Column {
                                    Text(
                                        text = theme.displayName,
                                        style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Bold),
                                        color = AdfTextPrimary
                                    )
                                    Text(
                                        text = if (isSelected) "Active Theme" else "Tap to apply",
                                        style = MaterialTheme.typography.bodySmall.copy(fontSize = 11.sp),
                                        color = if (isSelected) theme.badgeColor else AdfTextSecondary
                                    )
                                }
                            }

                            if (isSelected) {
                                Icon(
                                    imageVector = Icons.Default.Check,
                                    contentDescription = "Selected",
                                    tint = theme.badgeColor,
                                    modifier = Modifier.size(20.dp)
                                )
                            }
                        }
                    }
                }

                Spacer(modifier = Modifier.height(14.dp))

                Button(
                    onClick = onDismiss,
                    modifier = Modifier.fillMaxWidth(),
                    colors = ButtonDefaults.buttonColors(containerColor = CrimsonPrimary),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Text("Apply & Close", fontWeight = FontWeight.Bold, color = Color.White)
                }
            }
        }
    }
}

package com.example.ui.components

import android.app.Activity
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.data.fps.DisplayRefreshRateManager
import com.example.data.fps.FpsMonitor
import com.example.ui.theme.*

/**
 * Dialog to view and configure High Refresh Rate (60/90/120/144/165 FPS)
 * and toggle real-time FPS telemetry HUD.
 */
@Composable
fun RefreshRateDialog(
    showFpsHud: Boolean,
    onToggleFpsHud: (Boolean) -> Unit,
    onReplayBootup: () -> Unit,
    onDismiss: () -> Unit
) {
    val context = LocalContext.current
    val activity = context as? Activity
    val currentRate by DisplayRefreshRateManager.currentRefreshRate.collectAsStateWithLifecycle()
    val maxRate by DisplayRefreshRateManager.maxHardwareFps.collectAsStateWithLifecycle()
    val availableModes by DisplayRefreshRateManager.availableModes.collectAsStateWithLifecycle()
    val liveFps by FpsMonitor.fps.collectAsStateWithLifecycle()

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    modifier = Modifier
                        .size(36.dp)
                        .clip(CircleShape)
                        .background(Color(0xFF00E5FF).copy(alpha = 0.2f)),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Default.Speed,
                        contentDescription = null,
                        tint = Color(0xFF00E5FF),
                        modifier = Modifier.size(20.dp)
                    )
                }
                Spacer(modifier = Modifier.width(10.dp))
                Column {
                    Text(
                        text = "Display & Refresh Rate",
                        color = Color.White,
                        style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold)
                    )
                    Text(
                        text = "Hardware Capability: Up to ${maxRate}Hz",
                        color = Color(0xFF38BDF8),
                        style = MaterialTheme.typography.bodySmall
                    )
                }
            }
        },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
                // Live Telemetry Banner
                Surface(
                    shape = RoundedCornerShape(12.dp),
                    color = Color(0xFF0F172A),
                    border = CardDefaults.outlinedCardBorder().copy(brush = androidx.compose.ui.graphics.SolidColor(Color(0xFF1E293B)))
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(12.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column {
                            Text("Current Display Mode", color = AcrobatTextSecondary, fontSize = 11.sp)
                            Text(
                                text = "${currentRate} Hz Active",
                                color = Color(0xFF10B981),
                                fontWeight = FontWeight.Bold,
                                fontSize = 15.sp,
                                fontFamily = FontFamily.Monospace
                            )
                        }
                        Column(horizontalAlignment = Alignment.End) {
                            Text("Real-Time Telemetry", color = AcrobatTextSecondary, fontSize = 11.sp)
                            Text(
                                text = "$liveFps FPS",
                                color = Color(0xFF38BDF8),
                                fontWeight = FontWeight.Bold,
                                fontSize = 15.sp,
                                fontFamily = FontFamily.Monospace
                            )
                        }
                    }
                }

                Text(
                    text = "Select Preferred Refresh Rate:",
                    style = MaterialTheme.typography.labelMedium,
                    color = AcrobatTextSecondary
                )

                // Refresh Rate Options (60, 90, 120, 144, 165)
                listOf(
                    165 to "165 FPS (Extreme Gaming & Esports)",
                    120 to "120 FPS (Ultra Smooth ProMotion)",
                    90 to "90 FPS (Smooth Refresh)",
                    60 to "60 FPS (Standard Battery Saver)"
                ).forEach { (fpsTier, label) ->
                    val isSelected = currentRate == fpsTier
                    val isSupported = fpsTier <= maxRate

                    Surface(
                        shape = RoundedCornerShape(10.dp),
                        color = if (isSelected) Color(0xFF0284C7).copy(alpha = 0.25f) else Color(0xFF1E293B),
                        border = if (isSelected) CardDefaults.outlinedCardBorder().copy(brush = androidx.compose.ui.graphics.SolidColor(Color(0xFF00E5FF))) else null,
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable {
                                if (activity != null) {
                                    DisplayRefreshRateManager.setTargetRefreshRate(activity, fpsTier)
                                }
                            }
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 12.dp, vertical = 10.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Column {
                                Text(
                                    text = label,
                                    color = if (isSelected) Color.White else AcrobatTextPrimary,
                                    fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                                    fontSize = 13.sp
                                )
                                Text(
                                    text = if (isSupported) "Supported by device panel" else "Adaptive software sync",
                                    color = if (isSupported) Color(0xFF10B981) else AcrobatTextMuted,
                                    fontSize = 10.sp
                                )
                            }
                            if (isSelected) {
                                Icon(
                                    imageVector = Icons.Default.CheckCircle,
                                    contentDescription = "Active",
                                    tint = Color(0xFF00E5FF),
                                    modifier = Modifier.size(18.dp)
                                )
                            }
                        }
                    }
                }

                HorizontalDivider(color = Color(0xFF1E293B))

                // Toggle On-Screen FPS HUD
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column {
                        Text("Show Real-Time FPS HUD", color = Color.White, fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
                        Text("Persistent framerate counter overlay", color = AcrobatTextSecondary, fontSize = 11.sp)
                    }
                    Switch(
                        checked = showFpsHud,
                        onCheckedChange = onToggleFpsHud,
                        colors = SwitchDefaults.colors(
                            checkedThumbColor = Color.White,
                            checkedTrackColor = Color(0xFF0284C7)
                        )
                    )
                }

                // Replay Bootup Showcase (30 FPS Lock)
                OutlinedButton(
                    onClick = {
                        onDismiss()
                        onReplayBootup()
                    },
                    border = ButtonDefaults.outlinedButtonBorder(true).copy(brush = androidx.compose.ui.graphics.SolidColor(Color(0xFF38BDF8))),
                    shape = RoundedCornerShape(8.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Icon(
                        imageVector = Icons.Default.PlayCircle,
                        contentDescription = null,
                        tint = Color(0xFF38BDF8),
                        modifier = Modifier.size(18.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("Replay Bootup Showcase (30 FPS Lock)", color = Color(0xFF38BDF8), fontSize = 12.sp)
                }
            }
        },
        confirmButton = {
            Button(
                onClick = onDismiss,
                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF0284C7))
            ) {
                Text("Done")
            }
        },
        containerColor = Color(0xFF0A0E17),
        shape = RoundedCornerShape(16.dp)
    )
}

/**
 * Floating On-Screen Real-Time FPS Telemetry HUD
 */
@Composable
fun RealTimeFpsHud(
    modifier: Modifier = Modifier
) {
    val fps by FpsMonitor.fps.collectAsStateWithLifecycle()
    val refreshRate by DisplayRefreshRateManager.currentRefreshRate.collectAsStateWithLifecycle()
    val is30FpsLocked by DisplayRefreshRateManager.isLock30FpsActive.collectAsStateWithLifecycle()

    Surface(
        shape = RoundedCornerShape(20.dp),
        color = Color(0xFF080C14).copy(alpha = 0.92f),
        border = CardDefaults.outlinedCardBorder().copy(
            brush = androidx.compose.ui.graphics.SolidColor(
                if (is30FpsLocked) Color(0xFFF59E0B) else Color(0xFF00E5FF)
            )
        ),
        shadowElevation = 8.dp,
        modifier = modifier
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .size(7.dp)
                    .clip(CircleShape)
                    .background(if (is30FpsLocked) Color(0xFFF59E0B) else Color(0xFF10B981))
            )
            Spacer(modifier = Modifier.width(6.dp))
            Text(
                text = if (is30FpsLocked) "30 FPS [LOCK]" else "$fps FPS • ${refreshRate}Hz",
                color = if (is30FpsLocked) Color(0xFFFBBF24) else Color(0xFF38BDF8),
                fontSize = 11.sp,
                fontWeight = FontWeight.Bold,
                fontFamily = FontFamily.Monospace
            )
        }
    }
}

package com.example.ui.components

import androidx.compose.animation.*
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.Schedule
import androidx.compose.material.icons.filled.SystemUpdate
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.DialogProperties
import com.example.BuildConfig
import com.example.update.AppReleaseInfo
import com.example.ui.theme.*

/**
 * Reusable Compose Alert Dialog component that displays the new version details
 * fetched from GitHub Releases, providing 'Update Now' and 'Maybe Later' buttons
 * to guide the user through the download and installation process.
 *
 * @param releaseInfo Metadata of the release (version name, title, notes, APK size, download URL).
 * @param onUpdateNow Callback invoked when the user confirms updating ("Update Now").
 * @param onMaybeLater Callback invoked when the user defers or dismisses ("Maybe Later").
 * @param currentVersionName Current installed application version (defaults to BuildConfig.VERSION_NAME).
 * @param modifier Optional modifier for the alert dialog container.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AppUpdateAlertDialog(
    releaseInfo: AppReleaseInfo,
    onUpdateNow: () -> Unit,
    onMaybeLater: () -> Unit,
    currentVersionName: String = BuildConfig.VERSION_NAME,
    modifier: Modifier = Modifier
) {
    AlertDialog(
        onDismissRequest = onMaybeLater,
        properties = DialogProperties(
            dismissOnBackPress = true,
            dismissOnClickOutside = false
        ),
        modifier = modifier
            .fillMaxWidth()
            .padding(16.dp)
            .testTag("app_update_alert_dialog"),
        shape = RoundedCornerShape(26.dp),
        containerColor = AdfSurfaceCard,
        tonalElevation = 10.dp,
        icon = {
            Box(
                modifier = Modifier
                    .size(54.dp)
                    .clip(CircleShape)
                    .background(
                        Brush.linearGradient(
                            listOf(
                                AdfElectricCyan.copy(alpha = 0.22f),
                                AdfVibrantBlue.copy(alpha = 0.22f)
                            )
                        )
                    )
                    .border(
                        width = 1.2.dp,
                        brush = Brush.linearGradient(listOf(AdfElectricCyan, AdfVibrantBlue)),
                        shape = CircleShape
                    ),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.Default.SystemUpdate,
                    contentDescription = "New Version Icon",
                    tint = AdfElectricCyan,
                    modifier = Modifier.size(28.dp)
                )
            }
        },
        title = {
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                modifier = Modifier.fillMaxWidth()
            ) {
                Text(
                    text = "New Update Available",
                    style = MaterialTheme.typography.titleLarge.copy(
                        fontWeight = FontWeight.ExtraBold,
                        letterSpacing = 0.3.sp
                    ),
                    color = AdfTextPrimary
                )

                Spacer(modifier = Modifier.height(8.dp))

                // Version Transition Pill (Installed -> New Version)
                Surface(
                    shape = RoundedCornerShape(20.dp),
                    color = AdfMidnightBackground,
                    border = CardDefaults.outlinedCardBorder().copy(
                        brush = Brush.horizontalGradient(
                            listOf(AdfBorderSubtle, AdfBorderGlow)
                        )
                    )
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp)
                    ) {
                        Text(
                            text = "v$currentVersionName",
                            style = MaterialTheme.typography.bodySmall.copy(fontSize = 11.sp),
                            color = AdfTextSecondary
                        )

                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowForward,
                            contentDescription = null,
                            tint = AdfElectricCyan,
                            modifier = Modifier
                                .size(12.dp)
                                .padding(horizontal = 2.dp)
                        )

                        Surface(
                            shape = RoundedCornerShape(12.dp),
                            color = AdfEmeraldGreen.copy(alpha = 0.2f)
                        ) {
                            Text(
                                text = "v${releaseInfo.versionName}",
                                color = AdfEmeraldGreen,
                                fontSize = 11.5.sp,
                                fontWeight = FontWeight.Bold,
                                modifier = Modifier.padding(horizontal = 7.dp, vertical = 2.dp)
                            )
                        }
                    }
                }
            }
        },
        text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 4.dp)
            ) {
                // Release Title & Size Metadata
                if (releaseInfo.releaseTitle.isNotBlank()) {
                    Text(
                        text = releaseInfo.releaseTitle,
                        style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Bold),
                        color = AdfTextPrimary
                    )
                    Spacer(modifier = Modifier.height(2.dp))
                }

                if (releaseInfo.apkSize > 0) {
                    val sizeMb = String.format("%.1f MB", releaseInfo.apkSize / (1024.0 * 1024.0))
                    Text(
                        text = "Download Size: $sizeMb",
                        style = MaterialTheme.typography.bodySmall.copy(fontSize = 11.sp),
                        color = AdfElectricCyan
                    )
                }

                Spacer(modifier = Modifier.height(12.dp))

                // Scrollable Release Notes Card
                Text(
                    text = "What's New in This Version:",
                    fontSize = 11.5.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = AdfTextSecondary
                )

                Spacer(modifier = Modifier.height(6.dp))

                val scrollState = rememberScrollState()
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(min = 70.dp, max = 160.dp)
                        .clip(RoundedCornerShape(12.dp))
                        .background(AdfMidnightSurface)
                        .border(1.dp, AdfBorderSubtle, RoundedCornerShape(12.dp))
                        .padding(12.dp)
                        .verticalScroll(scrollState)
                ) {
                    Text(
                        text = releaseInfo.releaseNotes.ifBlank { "Performance improvements, stability fixes, and enhanced PDF viewing features." },
                        style = MaterialTheme.typography.bodySmall.copy(
                            fontSize = 12.sp,
                            lineHeight = 18.sp
                        ),
                        color = AdfTextPrimary
                    )
                }

                Spacer(modifier = Modifier.height(8.dp))

                // Guidance hint
                Text(
                    text = "After downloading, you will be prompted to install. The APK will be deleted automatically upon completion.",
                    style = MaterialTheme.typography.bodySmall.copy(fontSize = 10.5.sp, lineHeight = 14.sp),
                    color = AdfTextMuted
                )
            }
        },
        confirmButton = {
            Button(
                onClick = onUpdateNow,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(48.dp)
                    .testTag("update_now_button"),
                shape = RoundedCornerShape(14.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = CrimsonPrimary,
                    contentColor = Color.White
                )
            ) {
                Icon(
                    imageVector = Icons.Default.Download,
                    contentDescription = null,
                    modifier = Modifier.size(18.dp)
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = "Update Now",
                    fontWeight = FontWeight.Bold,
                    fontSize = 14.sp
                )
            }
        },
        dismissButton = {
            OutlinedButton(
                onClick = onMaybeLater,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(48.dp)
                    .testTag("maybe_later_button"),
                shape = RoundedCornerShape(14.dp),
                border = ButtonDefaults.outlinedButtonBorder.copy(
                    brush = Brush.horizontalGradient(listOf(AdfBorderSubtle, AdfBorderSubtle))
                ),
                colors = ButtonDefaults.outlinedButtonColors(
                    contentColor = AdfTextSecondary
                )
            ) {
                Icon(
                    imageVector = Icons.Default.Schedule,
                    contentDescription = null,
                    modifier = Modifier.size(16.dp),
                    tint = AdfTextSecondary
                )
                Spacer(modifier = Modifier.width(6.dp))
                Text(
                    text = "Maybe Later",
                    fontSize = 13.5.sp,
                    fontWeight = FontWeight.Medium
                )
            }
        }
    )
}

package com.example.update

import android.content.Context
import androidx.compose.animation.*
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.BuildConfig
import com.example.ui.theme.*

/**
 * Host overlay composable for all update dialogs and progress cards.
 * Placed at the root of MainActivity Scaffold to provide global, unhindered update notifications.
 */
@Composable
fun UpdateOverlayHost(
    viewModel: UpdateViewModel,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val updateState by viewModel.updateState.collectAsStateWithLifecycle()
    val showUpdateDialog by viewModel.showUpdateDialog.collectAsStateWithLifecycle()
    val showProgressCard by viewModel.showProgressCard.collectAsStateWithLifecycle()
    val showReadyDialog by viewModel.showReadyDialog.collectAsStateWithLifecycle()
    val showSettingsDialog by viewModel.showSettingsDialog.collectAsStateWithLifecycle()

    // 1. New Update Available Popup
    if (showUpdateDialog && updateState is UpdateState.UpdateAvailable) {
        val release = (updateState as UpdateState.UpdateAvailable).releaseInfo
        UpdateAvailableDialog(
            releaseInfo = release,
            onDownload = { viewModel.startDownload(context) },
            onDismiss = { viewModel.dismissUpdateDialog() }
        )
    }

    // 2. In-App Download Progress Card
    if (showProgressCard && updateState is UpdateState.Downloading) {
        val downloadingState = updateState as UpdateState.Downloading
        DownloadProgressDialog(
            state = downloadingState,
            onCancel = { viewModel.cancelDownload() }
        )
    }

    // 3. Ready to Install Dialog
    if (showReadyDialog && updateState is UpdateState.ReadyToInstall) {
        val readyState = updateState as UpdateState.ReadyToInstall
        ReadyToInstallDialog(
            releaseInfo = readyState.releaseInfo,
            onInstall = { viewModel.installDownloadedApk(context) },
            onDismiss = { viewModel.dismissReadyDialog() }
        )
    }

    // 4. Update Settings / Custom Repository Dialog
    if (showSettingsDialog) {
        UpdateSettingsDialog(
            currentOwner = UpdateManager.getGithubOwner(context),
            currentRepo = UpdateManager.getGithubRepo(context),
            onSave = { owner, repo -> viewModel.saveRepoSettings(context, owner, repo) },
            onDismiss = { viewModel.dismissSettingsDialog() }
        )
    }
}

/**
 * Reusable update dialog displaying the new version details fetched from GitHub,
 * providing 'Update Now' and 'Maybe Later' buttons to guide the user through the download process.
 */
@Composable
fun UpdateAvailableDialog(
    releaseInfo: AppReleaseInfo,
    onDownload: () -> Unit,
    onDismiss: () -> Unit
) {
    com.example.ui.components.AppUpdateAlertDialog(
        releaseInfo = releaseInfo,
        onUpdateNow = onDownload,
        onMaybeLater = onDismiss
    )
}

/**
 * Live In-App Download Progress Card with percentage, bytes count, speed, and Cancel option.
 */
@Composable
fun DownloadProgressDialog(
    state: UpdateState.Downloading,
    onCancel: () -> Unit
) {
    Dialog(
        onDismissRequest = { /* Modal: require cancel action */ },
        properties = DialogProperties(dismissOnBackPress = false, dismissOnClickOutside = false)
    ) {
        Surface(
            shape = RoundedCornerShape(22.dp),
            color = AdfSurfaceCard,
            border = CardDefaults.outlinedCardBorder().copy(
                brush = Brush.horizontalGradient(listOf(AdfElectricCyan, AdfVibrantBlue))
            ),
            tonalElevation = 14.dp,
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp)
                .testTag("update_download_progress_card")
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(22.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(24.dp),
                        strokeWidth = 2.5.dp,
                        color = CrimsonAccent
                    )
                    Spacer(modifier = Modifier.width(12.dp))
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = "Downloading Update...",
                            style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold),
                            color = AdfTextPrimary
                        )
                        Text(
                            text = "PDF AIO v${state.releaseInfo.versionName}",
                            style = MaterialTheme.typography.bodySmall.copy(fontSize = 11.sp),
                            color = AdfTextSecondary
                        )
                    }
                    Text(
                        text = "${state.progressPercent}%",
                        style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.ExtraBold),
                        color = AdfElectricCyan
                    )
                }

                Spacer(modifier = Modifier.height(16.dp))

                // Progress Bar
                LinearProgressIndicator(
                    progress = { state.progressPercent / 100f },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(8.dp)
                        .clip(RoundedCornerShape(4.dp)),
                    color = CrimsonPrimary,
                    trackColor = AdfMidnightSurface
                )

                Spacer(modifier = Modifier.height(10.dp))

                // Metrics row: Downloaded / Total & Speed
                val downloadedMb = String.format("%.1f MB", state.bytesDownloaded / (1024.0 * 1024.0))
                val totalMb = if (state.totalBytes > 0) {
                    String.format("%.1f MB", state.totalBytes / (1024.0 * 1024.0))
                } else "Unknown"

                val speedStr = if (state.speedKbps >= 1024) {
                    String.format("%.1f MB/s", state.speedKbps / 1024.0)
                } else {
                    "${state.speedKbps} KB/s"
                }

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text(
                        text = "$downloadedMb / $totalMb",
                        fontSize = 11.sp,
                        color = AdfTextSecondary
                    )
                    Text(
                        text = speedStr,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold,
                        color = AdfElectricCyan
                    )
                }

                Spacer(modifier = Modifier.height(16.dp))

                // Cancel Button
                OutlinedButton(
                    onClick = onCancel,
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("cancel_download_button"),
                    shape = RoundedCornerShape(12.dp),
                    colors = ButtonDefaults.outlinedButtonColors(contentColor = Color.Red.copy(alpha = 0.8f)),
                    border = ButtonDefaults.outlinedButtonBorder.copy(
                        brush = Brush.horizontalGradient(listOf(Color.Red.copy(alpha = 0.4f), Color.Red.copy(alpha = 0.2f)))
                    )
                ) {
                    Icon(imageVector = Icons.Default.Close, contentDescription = null, modifier = Modifier.size(16.dp))
                    Spacer(modifier = Modifier.width(6.dp))
                    Text("Cancel Download", fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
                }
            }
        }
    }
}

/**
 * Dialog notifying the user that the APK is downloaded and prompting one-tap installation.
 */
@Composable
fun ReadyToInstallDialog(
    releaseInfo: AppReleaseInfo,
    onInstall: () -> Unit,
    onDismiss: () -> Unit
) {
    Dialog(onDismissRequest = onDismiss) {
        Surface(
            shape = RoundedCornerShape(22.dp),
            color = AdfSurfaceCard,
            border = CardDefaults.outlinedCardBorder().copy(
                brush = Brush.horizontalGradient(listOf(AdfEmeraldGreen, AdfElectricCyan))
            ),
            tonalElevation = 14.dp,
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp)
                .testTag("update_ready_dialog")
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(22.dp)
            ) {
                Box(
                    modifier = Modifier
                        .size(48.dp)
                        .clip(CircleShape)
                        .background(AdfEmeraldGreen.copy(alpha = 0.2f))
                        .border(1.dp, AdfEmeraldGreen, CircleShape),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Default.CheckCircle,
                        contentDescription = null,
                        tint = AdfEmeraldGreen,
                        modifier = Modifier.size(28.dp)
                    )
                }

                Spacer(modifier = Modifier.height(14.dp))

                Text(
                    text = "Download Complete!",
                    style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.ExtraBold),
                    color = AdfTextPrimary
                )
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = "PDF AIO v${releaseInfo.versionName} is ready. Tap 'Install Now' to apply the update. Once installed, the downloaded APK file will be automatically deleted.",
                    style = MaterialTheme.typography.bodySmall.copy(fontSize = 12.sp, lineHeight = 17.sp),
                    color = AdfTextSecondary
                )

                Spacer(modifier = Modifier.height(18.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    OutlinedButton(
                        onClick = onDismiss,
                        modifier = Modifier.weight(1f),
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        Text("Later", color = AdfTextSecondary, fontSize = 13.sp)
                    }

                    Button(
                        onClick = onInstall,
                        modifier = Modifier
                            .weight(1.3f)
                            .testTag("install_update_button"),
                        shape = RoundedCornerShape(12.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = AdfEmeraldGreen)
                    ) {
                        Icon(imageVector = Icons.Default.InstallMobile, contentDescription = null, modifier = Modifier.size(16.dp), tint = Color.White)
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("Install Now", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 13.sp)
                    }
                }
            }
        }
    }
}

/**
 * Allows the user or developer to configure the GitHub repository (owner/repo)
 * to test updates against any GitHub releases repository.
 */
@Composable
fun UpdateSettingsDialog(
    currentOwner: String,
    currentRepo: String,
    onSave: (owner: String, repo: String) -> Unit,
    onDismiss: () -> Unit
) {
    var ownerText by remember { mutableStateOf(currentOwner) }
    var repoText by remember { mutableStateOf(currentRepo) }

    Dialog(onDismissRequest = onDismiss) {
        Surface(
            shape = RoundedCornerShape(22.dp),
            color = AdfSurfaceCard,
            border = CardDefaults.outlinedCardBorder().copy(
                brush = Brush.horizontalGradient(listOf(AdfElectricCyan.copy(alpha = 0.5f), AdfVibrantBlue.copy(alpha = 0.5f)))
            ),
            tonalElevation = 12.dp,
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp)
                .testTag("update_settings_dialog")
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
                    Text(
                        text = "Update Repository",
                        style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                        color = AdfTextPrimary
                    )
                    IconButton(onClick = onDismiss, modifier = Modifier.size(28.dp)) {
                        Icon(Icons.Default.Close, contentDescription = "Close", tint = AdfTextSecondary)
                    }
                }

                Spacer(modifier = Modifier.height(6.dp))
                Text(
                    text = "Configure the GitHub repository where PDF AIO checks for new APK releases.",
                    style = MaterialTheme.typography.bodySmall.copy(fontSize = 11.sp),
                    color = AdfTextSecondary
                )

                Spacer(modifier = Modifier.height(14.dp))

                OutlinedTextField(
                    value = ownerText,
                    onValueChange = { ownerText = it },
                    label = { Text("GitHub Owner / Username") },
                    placeholder = { Text("e.g. ishantkohlikohli") },
                    singleLine = true,
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = AdfElectricCyan,
                        unfocusedBorderColor = AdfBorderSubtle,
                        focusedTextColor = AdfTextPrimary,
                        unfocusedTextColor = AdfTextPrimary
                    ),
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("github_owner_input")
                )

                Spacer(modifier = Modifier.height(10.dp))

                OutlinedTextField(
                    value = repoText,
                    onValueChange = { repoText = it },
                    label = { Text("Repository Name") },
                    placeholder = { Text("e.g. PDF-AIO") },
                    singleLine = true,
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = AdfElectricCyan,
                        unfocusedBorderColor = AdfBorderSubtle,
                        focusedTextColor = AdfTextPrimary,
                        unfocusedTextColor = AdfTextPrimary
                    ),
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("github_repo_input")
                )

                Spacer(modifier = Modifier.height(18.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    TextButton(
                        onClick = {
                            ownerText = UpdateManager.DEFAULT_GITHUB_OWNER
                            repoText = UpdateManager.DEFAULT_GITHUB_REPO
                        },
                        modifier = Modifier.weight(1f)
                    ) {
                        Text("Reset Default", color = AdfTextSecondary, fontSize = 12.sp)
                    }

                    Button(
                        onClick = {
                            onSave(ownerText.trim(), repoText.trim())
                        },
                        modifier = Modifier
                            .weight(1.4f)
                            .testTag("save_and_check_update_button"),
                        colors = ButtonDefaults.buttonColors(containerColor = CrimsonPrimary),
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        Text("Save & Check", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 13.sp)
                    }
                }
            }
        }
    }
}

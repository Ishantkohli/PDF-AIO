package com.example.ui.components

import androidx.compose.animation.*
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.SystemUpdate
import com.example.data.fps.DisplayRefreshRateManager
import com.example.ui.AppScreen
import com.example.ui.theme.*

/**
 * Optimized Core Suite Menu Sheet
 * Universal responsiveness for every phone worldwide (small, tall, foldable, tablet).
 * Shortened, elegant architecture with 100% feature & testTag preservation.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CoreSuiteMenuSheet(
    onDismiss: () -> Unit,
    onNavigate: (AppScreen) -> Unit,
    onOpenToolTab: (Int) -> Unit,
    onOpenFpsDialog: () -> Unit,
    onOpenPureViewer: () -> Unit,
    onOpenFile: () -> Unit,
    onCheckUpdates: (() -> Unit)? = null,
    onOpenUpdateSettings: (() -> Unit)? = null
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val currentRefreshRate by DisplayRefreshRateManager.currentRefreshRate.collectAsState()
    var isConversionExpanded by remember { mutableStateOf(false) }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = AdfMidnightBackground,
        scrimColor = Color.Black.copy(alpha = 0.72f),
        shape = RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp),
        dragHandle = {
            Box(
                modifier = Modifier
                    .padding(top = 10.dp, bottom = 4.dp)
                    .width(44.dp)
                    .height(4.dp)
                    .clip(CircleShape)
                    .background(AdfElectricCyan.copy(alpha = 0.6f))
            )
        }
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .navigationBarsPadding(),
            contentAlignment = Alignment.TopCenter
        ) {
            LazyColumn(
                modifier = Modifier
                    .fillMaxWidth()
                    .widthIn(max = 620.dp) // Responsive adaptive constraint for foldables and tablets worldwide
                    .padding(horizontal = 16.dp, vertical = 6.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                // Header Bar
                item {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Text(
                                    text = "CORE SUITE MENU",
                                    style = MaterialTheme.typography.titleLarge.copy(
                                        fontWeight = FontWeight.Black,
                                        letterSpacing = 1.sp
                                    ),
                                    color = AdfTextPrimary
                                )
                                Spacer(modifier = Modifier.width(8.dp))
                                Surface(
                                    shape = RoundedCornerShape(6.dp),
                                    color = AdfElectricCyan.copy(alpha = 0.15f),
                                    border = CardDefaults.outlinedCardBorder().copy(
                                        brush = Brush.horizontalGradient(listOf(AdfElectricCyan, AdfVibrantBlue))
                                    )
                                ) {
                                    Text(
                                        text = "240V • 10/10/2026",
                                        color = AdfElectricCyan,
                                        fontSize = 10.sp,
                                        fontWeight = FontWeight.Bold,
                                        modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                                    )
                                }
                            }
                            Text(
                                text = "Executive multi-tool matrix & developer hub",
                                style = MaterialTheme.typography.bodySmall,
                                color = AdfTextSecondary
                            )
                        }

                        IconButton(
                            onClick = onDismiss,
                            modifier = Modifier
                                .size(36.dp)
                                .clip(CircleShape)
                                .background(AdfSurfaceCard)
                        ) {
                            AdfIcon.Close(size = 18.dp, color = AdfTextSecondary)
                        }
                    }
                    Spacer(modifier = Modifier.height(4.dp))
                    HorizontalDivider(color = AdfBorderSubtle)
                }

                // 0. Adobe Scan Document Scanner Card
                item {
                    CoreSuiteCard(
                        tag = "scanner",
                        accentColor = AdfCoral,
                        icon = { AdfIcon.Camera(size = 24.dp, primaryColor = AdfCoral, accentColor = Color.White) },
                        title = "Scan to PDF (Adobe Scan)",
                        badge = "CAMERA",
                        subtitle = "Snap receipts, contracts & paper with Magic Color whitening",
                        actionText = "Open Document Scanner Camera →",
                        onAction = {
                            onDismiss()
                            onNavigate(AppScreen.SCANNER)
                        }
                    )
                }

                // 1. Convertion Tool Card
                item {
                    CoreSuiteCard(
                        tag = "conversion",
                        accentColor = AdfElectricCyan,
                        icon = { AdfIcon.Conversion(size = 24.dp, isAnimated = true) },
                        title = "1. Convertion",
                        badge = if (isConversionExpanded) "HIDE" else "SHOW ALL",
                        subtitle = "PDF to Word, Word to PDF, Images to PDF, etc.",
                        onHeaderClick = { isConversionExpanded = !isConversionExpanded },
                        headerAction = {
                            AdfIcon.ChevronDown(size = 18.dp, color = AdfElectricCyan)
                        },
                        actionText = "Open Full Conversion Hub Window →",
                        onAction = {
                            onDismiss()
                            onNavigate(AppScreen.CONVERSION_HUB)
                        }
                    ) {
                        AnimatedVisibility(
                            visible = isConversionExpanded,
                            enter = expandVertically() + fadeIn(),
                            exit = shrinkVertically() + fadeOut()
                        ) {
                            Column(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(top = 10.dp),
                                verticalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                HorizontalDivider(color = AdfBorderSubtle.copy(alpha = 0.5f))
                                val subItems = listOf(
                                    Triple("PDF to Word (.docx)", "Extract text and tables into editable Word docx", "DOCX" to AdfEmerald),
                                    Triple("Word to PDF", "Standardize .docx and .doc into formatted PDF sheets", "PDF" to AdfVibrantBlue),
                                    Triple("Images to PDF", "Compile camera photos & graphic files into a PDF", "PHOTOS" to AdfCoral),
                                    Triple("PDF to Images", "Render PDF pages into ultra HD PNG or JPEG files", "PNG/JPG" to AdfElectricCyan),
                                    Triple("Merge Multiple PDFs", "Combine 2 or more PDF documents into one master document", "MERGE" to AdfAmber),
                                    Triple("Organize & Rotate Pages", "Reorder sheets, rotate 90°/180°, and delete pages", "PAGES" to AdfCyberViolet)
                                )
                                subItems.forEachIndexed { idx, (title, sub, badgePair) ->
                                    val toolTab = when (idx) { 0 -> 3; 1 -> 2; 2 -> 0; 3 -> 1; 4 -> 5; else -> 6 }
                                    ConversionSubItem(
                                        title = title,
                                        subtitle = sub,
                                        badge = badgePair.first,
                                        badgeColor = badgePair.second,
                                        onClick = {
                                            onDismiss()
                                            onOpenToolTab(toolTab)
                                            onNavigate(AppScreen.TOOLS)
                                        }
                                    )
                                }
                            }
                        }
                    }
                }

                // 2. Credits
                item {
                    CoreSuiteCard(
                        tag = "credits",
                        accentColor = Color(0xFFD946EF),
                        icon = { AdfIcon.DeveloperBadge(size = 24.dp, primaryColor = AdfElectricCyan, accentColor = Color(0xFFD946EF)) },
                        title = "2. Credits",
                        badge = "DEVELOPER INFO",
                        subtitle = "Build By Ishant Kohli • Coded By Google AI Studio",
                        actionText = "View Animated Follow Me & Social Handles →",
                        onAction = {
                            onDismiss()
                            onNavigate(AppScreen.CREDITS)
                        }
                    ) {
                        Surface(
                            shape = RoundedCornerShape(10.dp),
                            color = AdfMidnightSurface,
                            border = CardDefaults.outlinedCardBorder().copy(
                                brush = Brush.horizontalGradient(listOf(Color(0xFFD946EF).copy(alpha = 0.3f), AdfElectricCyan.copy(alpha = 0.3f)))
                            ),
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 10.dp)
                        ) {
                            Column(modifier = Modifier.padding(10.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                                CreditDetailRow("Build By", "Ishant Kohli", AdfElectricCyan)
                                CreditDetailRow("Coded By", "Google AI Studio", Color(0xFFD946EF))
                                CreditDetailRow("App Official Build Date", "10/10/2026", Color(0xFF10B981))
                            }
                        }
                    }
                }

                // 3. FPS
                item {
                    CoreSuiteCard(
                        tag = "fps",
                        accentColor = Color(0xFF10B981),
                        icon = { AdfIcon.FpsSpeed(size = 24.dp) },
                        title = "3. FPS",
                        badge = "${currentRefreshRate}Hz ACTIVE",
                        subtitle = "Dynamic refresh rate tuning up to 165Hz with HUD",
                        actionText = "Open FPS Change Dialog & On-Screen HUD →",
                        onAction = {
                            onDismiss()
                            onOpenFpsDialog()
                        }
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 8.dp),
                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            listOf(60, 90, 120, 144, 165).forEach { rate ->
                                val isSelected = currentRefreshRate == rate
                                Surface(
                                    shape = RoundedCornerShape(8.dp),
                                    color = if (isSelected) Color(0xFF10B981).copy(alpha = 0.25f) else AdfMidnightSurface,
                                    border = CardDefaults.outlinedCardBorder().copy(
                                        brush = if (isSelected) Brush.horizontalGradient(listOf(Color(0xFF10B981), AdfElectricCyan))
                                        else Brush.horizontalGradient(listOf(AdfBorderSubtle, AdfBorderSubtle))
                                    ),
                                    modifier = Modifier
                                        .weight(1f)
                                        .clickable { DisplayRefreshRateManager.setPreferredRefreshRate(rate) }
                                ) {
                                    Text(
                                        text = "${rate}Hz",
                                        color = if (isSelected) Color(0xFF10B981) else AdfTextSecondary,
                                        fontSize = 11.sp,
                                        fontWeight = if (isSelected) FontWeight.ExtraBold else FontWeight.Medium,
                                        modifier = Modifier.padding(vertical = 6.dp),
                                        textAlign = TextAlign.Center
                                    )
                                }
                            }
                        }
                    }
                }

                // 4. PDF only Viewer
                item {
                    CoreSuiteCard(
                        tag = "pdf_viewer",
                        accentColor = AdfCyberViolet,
                        icon = { AdfIcon.PureViewer(size = 24.dp) },
                        title = "4. PDF only Viewer",
                        badge = "NO EDITING",
                        subtitle = "Only viewer, no edit toolbar: pure distraction-free reading",
                        actionText = "Launch Pure PDF Viewer Mode →",
                        onAction = {
                            onDismiss()
                            onOpenPureViewer()
                        }
                    )
                }

                // 5. Signature
                item {
                    CoreSuiteCard(
                        tag = "signature",
                        accentColor = AdfAmber,
                        icon = { AdfIcon.SignatureVault(size = 24.dp) },
                        title = "5. Signature",
                        badge = "VAULT",
                        subtitle = "Digital Fill & Sign Vault with stylus stroke smoothing & seal management",
                        actionText = "Open Fill & Sign Vault →",
                        onAction = {
                            onDismiss()
                            onNavigate(AppScreen.SIGNATURE_VAULT)
                        }
                    )
                }

                // 6. Make/Create a Watermark
                item {
                    CoreSuiteCard(
                        tag = "watermark",
                        accentColor = AdfElectricCyan,
                        icon = { AdfIcon.Watermark(size = 24.dp) },
                        title = "6. Make/Create a Watermark",
                        badge = "STUDIO",
                        subtitle = "Apply custom text, rotation angle (0°-90°), opacity, & tiled patterns",
                        actionText = "Open Watermark Studio & Creator →",
                        onAction = {
                            onDismiss()
                            onOpenToolTab(4)
                            onNavigate(AppScreen.TOOLS)
                        }
                    )
                }

                // 7. Check for App Updates (GitHub Releases)
                item {
                    CoreSuiteCard(
                        tag = "app_updates",
                        accentColor = AdfEmeraldGreen,
                        icon = { Icon(Icons.Default.SystemUpdate, contentDescription = null, tint = AdfEmeraldGreen, modifier = Modifier.size(24.dp)) },
                        title = "7. Check for App Updates",
                        badge = "GITHUB OTA",
                        subtitle = "Scan GitHub Releases for latest APK, download progress & auto-install",
                        actionText = "Check for Updates Now →",
                        onAction = {
                            onDismiss()
                            onCheckUpdates?.invoke()
                        },
                        headerAction = {
                            if (onOpenUpdateSettings != null) {
                                IconButton(
                                    onClick = {
                                        onDismiss()
                                        onOpenUpdateSettings()
                                    },
                                    modifier = Modifier.size(32.dp)
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.Settings,
                                        contentDescription = "Update Settings",
                                        tint = AdfTextSecondary,
                                        modifier = Modifier.size(18.dp)
                                    )
                                }
                            }
                        }
                    )
                }

                // Quick Actions Footer
                item {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        OutlinedButton(
                            onClick = {
                                onDismiss()
                                onOpenFile()
                            },
                            modifier = Modifier
                                .weight(1f)
                                .height(44.dp),
                            shape = RoundedCornerShape(12.dp),
                            border = CardDefaults.outlinedCardBorder().copy(
                                brush = Brush.horizontalGradient(listOf(AdfElectricCyan, AdfVibrantBlue))
                            )
                        ) {
                            AdfIcon.FileOpen(size = 18.dp, primaryColor = AdfElectricCyan, accentColor = AdfVibrantBlue)
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("Open a File", color = AdfElectricCyan, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                        }

                        OutlinedButton(
                            onClick = {
                                onDismiss()
                                onNavigate(AppScreen.BOOTUP_SHOWCASE)
                            },
                            modifier = Modifier
                                .weight(1f)
                                .height(44.dp),
                            shape = RoundedCornerShape(12.dp),
                            border = CardDefaults.outlinedCardBorder().copy(
                                brush = Brush.horizontalGradient(listOf(Color(0xFFD946EF), AdfElectricCyan))
                            )
                        ) {
                            AdfIcon.Crown(size = 18.dp, color = Color(0xFFD946EF))
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("Replay Bootup", color = Color(0xFFD946EF), fontSize = 12.sp, fontWeight = FontWeight.Bold)
                        }
                    }
                    Spacer(modifier = Modifier.height(16.dp))
                }
            }
        }
    }
}

/**
 * Universal, clean reusable card for Core Suite tools
 */
@Composable
private fun CoreSuiteCard(
    tag: String,
    accentColor: Color,
    icon: @Composable () -> Unit,
    title: String,
    badge: String,
    subtitle: String,
    actionText: String? = null,
    onAction: (() -> Unit)? = null,
    onHeaderClick: (() -> Unit)? = null,
    headerAction: (@Composable () -> Unit)? = null,
    content: (@Composable ColumnScope.() -> Unit)? = null
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .testTag("menu_item_$tag")
            .clip(RoundedCornerShape(16.dp))
            .border(
                width = 1.dp,
                brush = Brush.horizontalGradient(
                    listOf(accentColor.copy(alpha = 0.6f), AdfVibrantBlue.copy(alpha = 0.3f))
                ),
                shape = RoundedCornerShape(16.dp)
            ),
        colors = CardDefaults.cardColors(containerColor = AdfSurfaceCard)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(14.dp)
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .then(if (onHeaderClick != null) Modifier.clickable { onHeaderClick() } else Modifier),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.weight(1f)
                ) {
                    Box(
                        modifier = Modifier
                            .size(42.dp)
                            .clip(RoundedCornerShape(10.dp))
                            .background(accentColor.copy(alpha = 0.15f)),
                        contentAlignment = Alignment.Center
                    ) {
                        icon()
                    }
                    Spacer(modifier = Modifier.width(12.dp))
                    Column {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(
                                text = title,
                                style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                                color = AdfTextPrimary
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Surface(
                                shape = RoundedCornerShape(4.dp),
                                color = accentColor.copy(alpha = 0.15f)
                            ) {
                                Text(
                                    text = badge,
                                    color = accentColor,
                                    fontSize = 9.sp,
                                    fontWeight = FontWeight.Bold,
                                    modifier = Modifier.padding(horizontal = 5.dp, vertical = 2.dp)
                                )
                            }
                        }
                        Text(
                            text = subtitle,
                            style = MaterialTheme.typography.bodySmall,
                            color = AdfTextSecondary,
                            maxLines = 2,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                }
                headerAction?.invoke()
            }

            content?.invoke(this)

            if (actionText != null && onAction != null) {
                Spacer(modifier = Modifier.height(10.dp))
                Button(
                    onClick = onAction,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(38.dp),
                    shape = RoundedCornerShape(10.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = accentColor.copy(alpha = 0.18f),
                        contentColor = accentColor
                    )
                ) {
                    Text(
                        text = actionText,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Bold
                    )
                }
            }
        }
    }
}

@Composable
private fun ConversionSubItem(
    title: String,
    subtitle: String,
    badge: String,
    badgeColor: Color,
    onClick: () -> Unit
) {
    Surface(
        shape = RoundedCornerShape(10.dp),
        color = AdfMidnightSurface,
        border = CardDefaults.outlinedCardBorder().copy(
            brush = Brush.horizontalGradient(listOf(badgeColor.copy(alpha = 0.4f), AdfBorderSubtle))
        ),
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onClick() }
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp, vertical = 9.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = title,
                    style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Bold),
                    color = AdfTextPrimary
                )
                Text(
                    text = subtitle,
                    style = MaterialTheme.typography.bodySmall.copy(fontSize = 11.sp),
                    color = AdfTextSecondary,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
            Spacer(modifier = Modifier.width(8.dp))
            Surface(
                shape = RoundedCornerShape(4.dp),
                color = badgeColor.copy(alpha = 0.15f)
            ) {
                Text(
                    text = badge,
                    color = badgeColor,
                    fontSize = 9.sp,
                    fontWeight = FontWeight.ExtraBold,
                    modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                )
            }
        }
    }
}

@Composable
private fun CreditDetailRow(
    label: String,
    value: String,
    highlightColor: Color
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = label,
            fontSize = 12.sp,
            color = AdfTextSecondary,
            fontWeight = FontWeight.Medium
        )
        Text(
            text = value,
            fontSize = 12.sp,
            color = highlightColor,
            fontWeight = FontWeight.Bold
        )
    }
}

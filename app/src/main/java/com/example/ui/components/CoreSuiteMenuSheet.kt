package com.example.ui.components

import androidx.compose.animation.*
import androidx.compose.animation.core.*
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
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.fps.DisplayRefreshRateManager
import com.example.ui.AppScreen
import com.example.ui.theme.*

/**
 * Core Suite Menu Sheet
 * Displays the 6 core tools requested by the user:
 * 1. Convertion (PDF To Word, Word to PDF, Images to PDF, etc.)
 * 2. Credits (Build By - Ishant Kohli, Coded By - Google AI Studio, App Official Build Date - 10/10/2026)
 * 3. FPS (Display refresh rate change & sync)
 * 4. PDF only Viewer (Pure viewer without edit controls)
 * 5. Signature (Digital Signature Vault)
 * 6. Make/Create a Watermark (Watermark Studio)
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CoreSuiteMenuSheet(
    onDismiss: () -> Unit,
    onNavigate: (AppScreen) -> Unit,
    onOpenToolTab: (Int) -> Unit,
    onOpenFpsDialog: () -> Unit,
    onOpenPureViewer: () -> Unit,
    onOpenFile: () -> Unit
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
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 10.dp, bottom = 4.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Box(
                    modifier = Modifier
                        .width(44.dp)
                        .height(4.dp)
                        .clip(CircleShape)
                        .background(AdfElectricCyan.copy(alpha = 0.6f))
                )
            }
        }
    ) {
        LazyColumn(
            modifier = Modifier
                .fillMaxWidth()
                .navigationBarsPadding()
                .padding(horizontal = 20.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
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

                Spacer(modifier = Modifier.height(6.dp))
                HorizontalDivider(color = AdfBorderSubtle)
            }

            // ==========================================
            // 1. Convertion
            // ==========================================
            item {
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("menu_item_conversion")
                        .clip(RoundedCornerShape(16.dp))
                        .border(
                            width = 1.dp,
                            brush = Brush.horizontalGradient(
                                listOf(AdfElectricCyan.copy(alpha = 0.6f), AdfVibrantBlue.copy(alpha = 0.3f))
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
                                .clickable { isConversionExpanded = !isConversionExpanded },
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
                                        .background(AdfElectricCyan.copy(alpha = 0.15f)),
                                    contentAlignment = Alignment.Center
                                ) {
                                    AdfIcon.Conversion(size = 24.dp, isAnimated = true)
                                }
                                Spacer(modifier = Modifier.width(12.dp))
                                Column {
                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        Text(
                                            text = "1. Convertion",
                                            style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                                            color = AdfTextPrimary
                                        )
                                        Spacer(modifier = Modifier.width(6.dp))
                                        Surface(
                                            shape = RoundedCornerShape(4.dp),
                                            color = AdfElectricCyan.copy(alpha = 0.12f)
                                        ) {
                                            Text(
                                                text = if (isConversionExpanded) "HIDE" else "SHOW ALL",
                                                color = AdfElectricCyan,
                                                fontSize = 9.sp,
                                                fontWeight = FontWeight.Bold,
                                                modifier = Modifier.padding(horizontal = 5.dp, vertical = 2.dp)
                                            )
                                        }
                                    }
                                    Text(
                                        text = "PDF to Word, Word to PDF, Images to PDF, etc.",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = AdfTextSecondary
                                    )
                                }
                            }

                            IconButton(
                                onClick = { isConversionExpanded = !isConversionExpanded },
                                modifier = Modifier.size(32.dp)
                            ) {
                                AdfIcon.ChevronDown(
                                    size = 18.dp,
                                    color = AdfElectricCyan
                                )
                            }
                        }

                        // Submenu items (always easily accessible)
                        AnimatedVisibility(
                            visible = isConversionExpanded,
                            enter = expandVertically() + fadeIn(),
                            exit = shrinkVertically() + fadeOut()
                        ) {
                            Column(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(top = 12.dp),
                                verticalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                HorizontalDivider(color = AdfBorderSubtle.copy(alpha = 0.5f))

                                // PDF to Word
                                ConversionSubItem(
                                    title = "PDF to Word (.docx)",
                                    subtitle = "Extract text and tables into editable Microsoft Word docx",
                                    badge = "DOCX",
                                    badgeColor = AdfEmerald,
                                    onClick = {
                                        onDismiss()
                                        onOpenToolTab(3) // Tab 3: PDF to Word
                                        onNavigate(AppScreen.TOOLS)
                                    }
                                )

                                // Word to PDF
                                ConversionSubItem(
                                    title = "Word to PDF",
                                    subtitle = "Standardize .docx and .doc into formatted PDF sheets",
                                    badge = "PDF",
                                    badgeColor = AdfVibrantBlue,
                                    onClick = {
                                        onDismiss()
                                        onOpenToolTab(2) // Tab 2: Word to PDF
                                        onNavigate(AppScreen.TOOLS)
                                    }
                                )

                                // Images to PDF
                                ConversionSubItem(
                                    title = "Images to PDF",
                                    subtitle = "Compile camera photos & graphic files into a PDF",
                                    badge = "PHOTOS",
                                    badgeColor = AdfCoral,
                                    onClick = {
                                        onDismiss()
                                        onOpenToolTab(0) // Tab 0: Images to PDF
                                        onNavigate(AppScreen.TOOLS)
                                    }
                                )

                                // PDF to Images
                                ConversionSubItem(
                                    title = "PDF to Images",
                                    subtitle = "Render PDF pages into ultra HD PNG or JPEG files",
                                    badge = "PNG/JPG",
                                    badgeColor = AdfElectricCyan,
                                    onClick = {
                                        onDismiss()
                                        onOpenToolTab(1) // Tab 1: PDF to Images
                                        onNavigate(AppScreen.TOOLS)
                                    }
                                )

                                // Merge Multiple PDFs
                                ConversionSubItem(
                                    title = "Merge Multiple PDFs",
                                    subtitle = "Combine 2 or more PDF documents into one master document",
                                    badge = "MERGE",
                                    badgeColor = AdfAmber,
                                    onClick = {
                                        onDismiss()
                                        onOpenToolTab(5) // Tab 5: Merge
                                        onNavigate(AppScreen.TOOLS)
                                    }
                                )

                                // Organize Pages
                                ConversionSubItem(
                                    title = "Organize & Rotate Pages",
                                    subtitle = "Reorder sheets, rotate 90°/180°, and delete pages",
                                    badge = "PAGES",
                                    badgeColor = AdfCyberViolet,
                                    onClick = {
                                        onDismiss()
                                        onOpenToolTab(6) // Tab 6: Organize
                                        onNavigate(AppScreen.TOOLS)
                                    }
                                )
                            }
                        }

                        Spacer(modifier = Modifier.height(8.dp))
                        Button(
                            onClick = {
                                onDismiss()
                                onNavigate(AppScreen.CONVERSION_HUB)
                            },
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(38.dp),
                            shape = RoundedCornerShape(10.dp),
                            colors = ButtonDefaults.buttonColors(
                                containerColor = AdfElectricCyan.copy(alpha = 0.18f),
                                contentColor = AdfElectricCyan
                            )
                        ) {
                            Text(
                                text = "Open Full Conversion Hub Window →",
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }
                }
            }

            // ==========================================
            // 2. Credits
            // ==========================================
            item {
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("menu_item_credits")
                        .clip(RoundedCornerShape(16.dp))
                        .border(
                            width = 1.dp,
                            brush = Brush.horizontalGradient(
                                listOf(Color(0xFFD946EF).copy(alpha = 0.6f), AdfElectricCyan.copy(alpha = 0.3f))
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
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(42.dp)
                                    .clip(RoundedCornerShape(10.dp))
                                    .background(Color(0xFFD946EF).copy(alpha = 0.15f)),
                                contentAlignment = Alignment.Center
                            ) {
                                AdfIcon.DeveloperBadge(
                                    size = 24.dp,
                                    primaryColor = AdfElectricCyan,
                                    accentColor = Color(0xFFD946EF)
                                )
                            }
                            Spacer(modifier = Modifier.width(12.dp))
                            Column {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Text(
                                        text = "2. Credits",
                                        style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                                        color = AdfTextPrimary
                                    )
                                    Spacer(modifier = Modifier.width(6.dp))
                                    Surface(
                                        shape = RoundedCornerShape(4.dp),
                                        color = Color(0xFFD946EF).copy(alpha = 0.15f)
                                    ) {
                                        Text(
                                            text = "DEVELOPER INFO",
                                            color = Color(0xFFD946EF),
                                            fontSize = 9.sp,
                                            fontWeight = FontWeight.Bold,
                                            modifier = Modifier.padding(horizontal = 5.dp, vertical = 2.dp)
                                        )
                                    }
                                }
                                Text(
                                    text = "Build By Ishant Kohli • Coded By Google AI Studio",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = AdfTextSecondary
                                )
                            }
                        }

                        Spacer(modifier = Modifier.height(12.dp))

                        // Developer Details Box
                        Surface(
                            shape = RoundedCornerShape(10.dp),
                            color = AdfMidnightSurface,
                            border = CardDefaults.outlinedCardBorder().copy(
                                brush = Brush.horizontalGradient(
                                    listOf(Color(0xFFD946EF).copy(alpha = 0.3f), AdfElectricCyan.copy(alpha = 0.3f))
                                )
                            ),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Column(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(10.dp),
                                verticalArrangement = Arrangement.spacedBy(4.dp)
                            ) {
                                CreditDetailRow(label = "Build By", value = "Ishant Kohli", highlightColor = AdfElectricCyan)
                                CreditDetailRow(label = "Coded By", value = "Google AI Studio", highlightColor = Color(0xFFD946EF))
                                CreditDetailRow(label = "App Official Build Date", value = "10/10/2026", highlightColor = Color(0xFF10B981))
                            }
                        }

                        Spacer(modifier = Modifier.height(10.dp))

                        Button(
                            onClick = {
                                onDismiss()
                                onNavigate(AppScreen.CREDITS)
                            },
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(38.dp),
                            shape = RoundedCornerShape(10.dp),
                            colors = ButtonDefaults.buttonColors(
                                containerColor = Color(0xFFD946EF).copy(alpha = 0.18f),
                                contentColor = Color(0xFFD946EF)
                            )
                        ) {
                            Text(
                                text = "View Animated Follow Me & Social Handles →",
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }
                }
            }

            // ==========================================
            // 3. FPS
            // ==========================================
            item {
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("menu_item_fps")
                        .clip(RoundedCornerShape(16.dp))
                        .border(
                            width = 1.dp,
                            brush = Brush.horizontalGradient(
                                listOf(Color(0xFF10B981).copy(alpha = 0.6f), AdfElectricCyan.copy(alpha = 0.3f))
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
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(42.dp)
                                    .clip(RoundedCornerShape(10.dp))
                                    .background(Color(0xFF10B981).copy(alpha = 0.15f)),
                                contentAlignment = Alignment.Center
                            ) {
                                AdfIcon.FpsSpeed(size = 24.dp)
                            }
                            Spacer(modifier = Modifier.width(12.dp))
                            Column(modifier = Modifier.weight(1f)) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Text(
                                        text = "3. FPS",
                                        style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                                        color = AdfTextPrimary
                                    )
                                    Spacer(modifier = Modifier.width(6.dp))
                                    Surface(
                                        shape = RoundedCornerShape(4.dp),
                                        color = Color(0xFF10B981).copy(alpha = 0.15f)
                                    ) {
                                        Text(
                                            text = "${currentRefreshRate}Hz ACTIVE",
                                            color = Color(0xFF10B981),
                                            fontSize = 9.sp,
                                            fontWeight = FontWeight.Bold,
                                            modifier = Modifier.padding(horizontal = 5.dp, vertical = 2.dp)
                                        )
                                    }
                                }
                                Text(
                                    text = "Dynamic refresh rate tuning up to 165Hz with HUD",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = AdfTextSecondary
                                )
                            }
                        }

                        Spacer(modifier = Modifier.height(10.dp))

                        // Quick rate pills
                        Row(
                            modifier = Modifier.fillMaxWidth(),
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
                                        .clickable {
                                            DisplayRefreshRateManager.setPreferredRefreshRate(rate)
                                        }
                                ) {
                                    Text(
                                        text = "${rate}Hz",
                                        color = if (isSelected) Color(0xFF10B981) else AdfTextSecondary,
                                        fontSize = 11.sp,
                                        fontWeight = if (isSelected) FontWeight.ExtraBold else FontWeight.Medium,
                                        modifier = Modifier.padding(vertical = 6.dp),
                                        textAlign = androidx.compose.ui.text.style.TextAlign.Center
                                    )
                                }
                            }
                        }

                        Spacer(modifier = Modifier.height(10.dp))

                        Button(
                            onClick = {
                                onDismiss()
                                onOpenFpsDialog()
                            },
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(38.dp),
                            shape = RoundedCornerShape(10.dp),
                            colors = ButtonDefaults.buttonColors(
                                containerColor = Color(0xFF10B981).copy(alpha = 0.18f),
                                contentColor = Color(0xFF10B981)
                            )
                        ) {
                            Text(
                                text = "Open FPS Change Dialog & On-Screen HUD →",
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }
                }
            }

            // ==========================================
            // 4. PDF only Viewer
            // ==========================================
            item {
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("menu_item_pdf_viewer")
                        .clip(RoundedCornerShape(16.dp))
                        .border(
                            width = 1.dp,
                            brush = Brush.horizontalGradient(
                                listOf(AdfCyberViolet.copy(alpha = 0.6f), AdfElectricCyan.copy(alpha = 0.3f))
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
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(42.dp)
                                    .clip(RoundedCornerShape(10.dp))
                                    .background(AdfCyberViolet.copy(alpha = 0.15f)),
                                contentAlignment = Alignment.Center
                            ) {
                                AdfIcon.PureViewer(size = 24.dp)
                            }
                            Spacer(modifier = Modifier.width(12.dp))
                            Column(modifier = Modifier.weight(1f)) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Text(
                                        text = "4. PDF only Viewer",
                                        style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                                        color = AdfTextPrimary
                                    )
                                    Spacer(modifier = Modifier.width(6.dp))
                                    Surface(
                                        shape = RoundedCornerShape(4.dp),
                                        color = AdfCyberViolet.copy(alpha = 0.15f)
                                    ) {
                                        Text(
                                            text = "NO EDITING",
                                            color = AdfCyberViolet,
                                            fontSize = 9.sp,
                                            fontWeight = FontWeight.Bold,
                                            modifier = Modifier.padding(horizontal = 5.dp, vertical = 2.dp)
                                        )
                                    }
                                }
                                Text(
                                    text = "Only viewer, no edit toolbar: pure distraction-free reading",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = AdfTextSecondary
                                )
                            }
                        }

                        Spacer(modifier = Modifier.height(10.dp))

                        Button(
                            onClick = {
                                onDismiss()
                                onOpenPureViewer()
                            },
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(38.dp),
                            shape = RoundedCornerShape(10.dp),
                            colors = ButtonDefaults.buttonColors(
                                containerColor = AdfCyberViolet.copy(alpha = 0.18f),
                                contentColor = AdfCyberViolet
                            )
                        ) {
                            Text(
                                text = "Launch Pure PDF Viewer Mode →",
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }
                }
            }

            // ==========================================
            // 5. Signature
            // ==========================================
            item {
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("menu_item_signature")
                        .clip(RoundedCornerShape(16.dp))
                        .border(
                            width = 1.dp,
                            brush = Brush.horizontalGradient(
                                listOf(AdfAmber.copy(alpha = 0.6f), AdfElectricCyan.copy(alpha = 0.3f))
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
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(42.dp)
                                    .clip(RoundedCornerShape(10.dp))
                                    .background(AdfAmber.copy(alpha = 0.15f)),
                                contentAlignment = Alignment.Center
                            ) {
                                AdfIcon.SignatureVault(size = 24.dp)
                            }
                            Spacer(modifier = Modifier.width(12.dp))
                            Column(modifier = Modifier.weight(1f)) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Text(
                                        text = "5. Signature",
                                        style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                                        color = AdfTextPrimary
                                    )
                                    Spacer(modifier = Modifier.width(6.dp))
                                    Surface(
                                        shape = RoundedCornerShape(4.dp),
                                        color = AdfAmber.copy(alpha = 0.15f)
                                    ) {
                                        Text(
                                            text = "VAULT",
                                            color = AdfAmber,
                                            fontSize = 9.sp,
                                            fontWeight = FontWeight.Bold,
                                            modifier = Modifier.padding(horizontal = 5.dp, vertical = 2.dp)
                                        )
                                    }
                                }
                                Text(
                                    text = "Digital Fill & Sign Vault with stylus stroke smoothing & seal management",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = AdfTextSecondary
                                )
                            }
                        }

                        Spacer(modifier = Modifier.height(10.dp))

                        Button(
                            onClick = {
                                onDismiss()
                                onNavigate(AppScreen.SIGNATURE_VAULT)
                            },
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(38.dp),
                            shape = RoundedCornerShape(10.dp),
                            colors = ButtonDefaults.buttonColors(
                                containerColor = AdfAmber.copy(alpha = 0.18f),
                                contentColor = AdfAmber
                            )
                        ) {
                            Text(
                                text = "Open Fill & Sign Vault →",
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }
                }
            }

            // ==========================================
            // 6. Make/Create a Watermaker
            // ==========================================
            item {
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("menu_item_watermark")
                        .clip(RoundedCornerShape(16.dp))
                        .border(
                            width = 1.dp,
                            brush = Brush.horizontalGradient(
                                listOf(AdfElectricCyan.copy(alpha = 0.6f), AdfCyberViolet.copy(alpha = 0.4f))
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
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(42.dp)
                                    .clip(RoundedCornerShape(10.dp))
                                    .background(AdfElectricCyan.copy(alpha = 0.15f)),
                                contentAlignment = Alignment.Center
                            ) {
                                AdfIcon.Watermark(size = 24.dp)
                            }
                            Spacer(modifier = Modifier.width(12.dp))
                            Column(modifier = Modifier.weight(1f)) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Text(
                                        text = "6. Make/Create a Watermark",
                                        style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                                        color = AdfTextPrimary
                                    )
                                    Spacer(modifier = Modifier.width(6.dp))
                                    Surface(
                                        shape = RoundedCornerShape(4.dp),
                                        color = AdfElectricCyan.copy(alpha = 0.15f)
                                    ) {
                                        Text(
                                            text = "STUDIO",
                                            color = AdfElectricCyan,
                                            fontSize = 9.sp,
                                            fontWeight = FontWeight.Bold,
                                            modifier = Modifier.padding(horizontal = 5.dp, vertical = 2.dp)
                                        )
                                    }
                                }
                                Text(
                                    text = "Apply custom text, rotation angle (0°-90°), opacity, & tiled patterns",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = AdfTextSecondary
                                )
                            }
                        }

                        Spacer(modifier = Modifier.height(10.dp))

                        Button(
                            onClick = {
                                onDismiss()
                                onOpenToolTab(4) // Tab 4: Watermark Studio
                                onNavigate(AppScreen.TOOLS)
                            },
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(38.dp),
                            shape = RoundedCornerShape(10.dp),
                            colors = ButtonDefaults.buttonColors(
                                containerColor = AdfElectricCyan.copy(alpha = 0.18f),
                                contentColor = AdfElectricCyan
                            )
                        ) {
                            Text(
                                text = "Open Watermark Studio & Creator →",
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }
                }
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
                            .height(42.dp),
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
                            .height(42.dp),
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

                Spacer(modifier = Modifier.height(20.dp))
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

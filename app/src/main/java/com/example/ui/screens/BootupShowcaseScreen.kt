package com.example.ui.screens

import android.app.Activity
import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.draw.scale
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.R
import com.example.data.fps.DisplayRefreshRateManager
import com.example.ui.AppScreen
import com.example.ui.PdfViewModel
import com.example.ui.theme.*
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive

data class ShowcaseFeature(
    val stageNumber: Int,
    val title: String,
    val badge: String,
    val subtitle: String,
    val icon: ImageVector,
    val accentColor: Color,
    val capabilities: List<String>
)

/**
 * PDF AIO Bootup Animation Showcase
 * Strictly locked to 30 FPS (33.3ms per frame tick) showcasing all core capabilities
 * of PDF AIO, followed by unlocking maximum hardware refresh rates (60/90/120/165 FPS).
 */
@Composable
fun BootupShowcaseScreen(
    viewModel: PdfViewModel,
    onFinishBootup: () -> Unit
) {
    val context = LocalContext.current
    val activity = context as? Activity

    // Lock display to 30 FPS during bootup
    DisposableEffect(Unit) {
        if (activity != null) {
            DisplayRefreshRateManager.lock30Fps(activity)
        }
        onDispose {
            if (activity != null) {
                DisplayRefreshRateManager.unlockMaxRefreshRate(activity)
            }
        }
    }

    val maxHardwareFps by DisplayRefreshRateManager.maxHardwareFps.collectAsStateWithLifecycle()

    // STRICT 30 FPS FRAME TICKER (33.33ms per frame tick)
    var frameCount by remember { mutableIntStateOf(0) }
    val totalShowcaseFrames = 420 // ~14 seconds at 30 FPS

    LaunchedEffect(Unit) {
        val targetFrameDurationMs = 33L // 30.3 FPS lock
        while (isActive) {
            val startTime = System.currentTimeMillis()
            frameCount++
            if (frameCount >= totalShowcaseFrames) {
                // Auto finish and transition to Home with high FPS unlocked
                onFinishBootup()
                break
            }
            val elapsed = System.currentTimeMillis() - startTime
            val sleepMs = (targetFrameDurationMs - elapsed).coerceAtLeast(1L)
            delay(sleepMs)
        }
    }

    // Determine current showcase stage based on frame count (each stage is 75 frames = 2.5s)
    val currentStageIndex = when {
        frameCount < 75 -> 0  // Brand Genesis & Display Mode Lock
        frameCount < 150 -> 1 // PDF Ultra Engine
        frameCount < 225 -> 2 // Universal Converter
        frameCount < 300 -> 3 // Content Editor & Whiteout
        frameCount < 375 -> 4 // Signatures & Custom Watermark
        else -> 5             // Ready & Unlock High FPS
    }

    val features = remember {
        listOf(
            ShowcaseFeature(
                stageNumber = 1,
                title = "Hardware-Accelerated PDF Engine",
                badge = "CORE ARCHITECTURE",
                subtitle = "Fluid, responsive PDF viewing supporting up to 165Hz screens",
                icon = Icons.Default.PictureAsPdf,
                accentColor = Color(0xFF00E5FF),
                capabilities = listOf(
                    "Native Android PdfRenderer with continuous vertical scroll",
                    "Smooth multi-touch pinch zoom up to 4.5x magnification",
                    "Liquid Mode AI reflow & text-to-speech narration",
                    "Night, Sepia, and High-Contrast executive color schemes"
                )
            ),
            ShowcaseFeature(
                stageNumber = 2,
                title = "Universal Document Converter",
                badge = "BI-DIRECTIONAL ENGINE",
                subtitle = "Seamless conversion across PDF, Word, and Image formats",
                icon = Icons.Default.SwapHoriz,
                accentColor = Color(0xFF38BDF8),
                capabilities = listOf(
                    "PDF to Images (PNG & JPG with 1x, 2x HD, 3x UHD DPI)",
                    "Images to PDF (PNG, JPG, WebP, GIF, BMP, HEIF)",
                    "Word to PDF (OpenXML .docx & .doc parsing with formatting)",
                    "PDF to Word (.docx standard Microsoft Word OpenXML)",
                    "Batch export and one-tap compressed ZIP packaging"
                )
            ),
            ShowcaseFeature(
                stageNumber = 3,
                title = "Edit PDF Content & Whiteout",
                badge = "STUDIO EDITOR",
                subtitle = "Modify underlying document text and graphics directly",
                icon = Icons.Default.LayersClear,
                accentColor = Color(0xFFF43F5E),
                capabilities = listOf(
                    "Whiteout Erase: Blank out existing text and graphics seamlessly",
                    "Inline text replacement to overwrite existing PDF content",
                    "Black Redaction mode for privacy & confidential sections",
                    "Interactive annotation editing for text boxes, notes, and stamps"
                )
            ),
            ShowcaseFeature(
                stageNumber = 4,
                title = "Signature Vault & Custom Watermark",
                badge = "PRO SECURITY",
                subtitle = "Digital authorization & custom diagonal branding studio",
                icon = Icons.Default.Draw,
                accentColor = Color(0xFFA855F7),
                capabilities = listOf(
                    "Signature Vault with pen smoothing and custom ink colors",
                    "Drag, drop, and resize signatures on any document page",
                    "Custom Watermark Studio: custom text and quick presets",
                    "Adjustable rotation angle (0°-90°), opacity, and tiled patterns"
                )
            ),
            ShowcaseFeature(
                stageNumber = 5,
                title = "100% Offline & Private Local Engine",
                badge = "OFFLINE & SECURE",
                subtitle = "Zero cloud dependencies, zero external tracking, 100% on-device",
                icon = Icons.Default.Shield,
                accentColor = Color(0xFF10B981),
                capabilities = listOf(
                    "Draw & Type Digital Signatures with custom cursive styles",
                    "Directly stamp signatures onto any page of active PDFs",
                    "Instant on-device Word-to-PDF & Image-to-PDF conversion",
                    "Zero data leaves your device — complete privacy guaranteed"
                )
            )
        )
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color(0xFF070A0F))
            .systemBarsPadding()
    ) {
        // Atmospheric Background Glow
        Box(
            modifier = Modifier
                .align(Alignment.TopCenter)
                .padding(top = 40.dp)
                .size(340.dp)
                .blur(80.dp)
                .background(
                    Brush.radialGradient(
                        colors = listOf(
                            Color(0xFF00E5FF).copy(alpha = 0.22f),
                            Color(0xFF2563EB).copy(alpha = 0.15f),
                            Color.Transparent
                        )
                    )
                )
        )

        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 20.dp, vertical = 14.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.SpaceBetween
        ) {
            // TOP BAR: 30 FPS LOCK TELEMETRY HUD
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 4.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                // FPS Lock Badge
                Surface(
                    shape = RoundedCornerShape(20.dp),
                    color = Color(0xFF0F172A),
                    border = CardDefaults.outlinedCardBorder().copy(brush = Brush.linearGradient(listOf(Color(0xFF00E5FF), Color(0xFF2563EB)))),
                    shadowElevation = 6.dp
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Box(
                            modifier = Modifier
                                .size(8.dp)
                                .clip(CircleShape)
                                .background(Color(0xFF10B981))
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = "30 FPS [LOCKED]",
                            color = Color(0xFF38BDF8),
                            fontWeight = FontWeight.Bold,
                            fontSize = 12.sp,
                            fontFamily = FontFamily.Monospace,
                            letterSpacing = 0.5.sp
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            text = "• 33.3ms",
                            color = AcrobatTextMuted,
                            fontSize = 11.sp,
                            fontFamily = FontFamily.Monospace
                        )
                    }
                }

                // Skip Button
                TextButton(
                    onClick = onFinishBootup,
                    modifier = Modifier.testTag("skip_bootup_button")
                ) {
                    Text(
                        text = "Skip Showcase",
                        color = AcrobatTextSecondary,
                        fontSize = 13.sp,
                        fontWeight = FontWeight.SemiBold
                    )
                    Spacer(modifier = Modifier.width(4.dp))
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.ArrowForward,
                        contentDescription = "Skip",
                        tint = AcrobatTextSecondary,
                        modifier = Modifier.size(16.dp)
                    )
                }
            }

            // CENTER: BRAND LOGO + CURRENT SHOWCASE STAGE
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(16.dp),
                modifier = Modifier.weight(1f, fill = false)
            ) {
                // Animated App Icon Emblem
                Box(
                    modifier = Modifier.size(140.dp),
                    contentAlignment = Alignment.Center
                ) {
                    // Orbiting rotating glow ring
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .rotate((frameCount * 2.5f) % 360f)
                            .border(
                                width = 2.dp,
                                brush = Brush.sweepGradient(
                                    listOf(
                                        Color(0xFF00E5FF),
                                        Color(0xFF2563EB),
                                        Color(0xFFA855F7),
                                        Color(0xFF10B981),
                                        Color(0xFF00E5FF)
                                    )
                                ),
                                shape = CircleShape
                            )
                    )

                    // App Logo with Laser Light Sweep
                    Image(
                        painter = painterResource(id = R.drawable.pdf_aio_logo),
                        contentDescription = "ADF AIO Logo",
                        contentScale = ContentScale.Crop,
                        modifier = Modifier
                            .size(118.dp)
                            .clip(RoundedCornerShape(26.dp))
                            .border(2.dp, Color(0xFF1E293B), RoundedCornerShape(26.dp))
                    )

                    // Moving Laser Sheen Sweep
                    val sweepProgress = (frameCount % 60) / 60f
                    val sheenBrush = Brush.linearGradient(
                        colors = listOf(
                            Color.Transparent,
                            Color(0xFF00E5FF).copy(alpha = 0.25f),
                            Color.White.copy(alpha = 0.4f),
                            Color.Transparent
                        ),
                        start = Offset(sweepProgress * 360f - 120f, 0f),
                        end = Offset(sweepProgress * 360f + 120f, 260f)
                    )
                    Box(
                        modifier = Modifier
                            .size(118.dp)
                            .clip(RoundedCornerShape(26.dp))
                            .background(sheenBrush)
                    )
                }

                // Brand Title & Tagline
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(
                        text = "ADF AIO",
                        style = MaterialTheme.typography.headlineLarge.copy(
                            fontWeight = FontWeight.Black,
                            letterSpacing = 2.sp
                        ),
                        color = Color.White
                    )
                    Text(
                        text = "FUTURISTIC ALL-IN-ONE DOCUMENT SUITE",
                        style = MaterialTheme.typography.labelSmall.copy(
                            fontWeight = FontWeight.Bold,
                            letterSpacing = 1.2.sp
                        ),
                        color = Color(0xFF00E5FF)
                    )
                    Spacer(modifier = Modifier.height(6.dp))
                    // Real-time Cyber Kernel Telemetry Line
                    val terminalStatus = when {
                        frameCount < 40 -> "KERNEL: ADF AIO v2.4 HYPER-ENGINE ONLINE"
                        frameCount < 100 -> "DISPLAY: 165Hz VARIABLE REFRESH RATE DETECTED"
                        frameCount < 180 -> "CONVERTER: 7 BI-DIRECTIONAL ENGINES LOADED"
                        frameCount < 260 -> "EDITOR: WHITEOUT & INLINE TEXT ENGINE READY"
                        frameCount < 340 -> "SECURITY: ENCRYPTED SIGNATURE VAULT ACTIVE"
                        else -> "SYSTEM READY • 100% HARDWARE ACCELERATED"
                    }
                    Surface(
                        shape = RoundedCornerShape(6.dp),
                        color = Color(0xFF0F172A).copy(alpha = 0.85f),
                        border = CardDefaults.outlinedCardBorder().copy(
                            brush = Brush.horizontalGradient(
                                listOf(Color(0xFF00E5FF).copy(alpha = 0.4f), Color(0xFF2563EB).copy(alpha = 0.4f))
                            )
                        )
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.padding(horizontal = 10.dp, vertical = 3.dp)
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(6.dp)
                                    .clip(CircleShape)
                                    .background(if ((frameCount / 6) % 2 == 0) Color(0xFF00E5FF) else Color.Transparent)
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(
                                text = terminalStatus,
                                color = Color(0xFF38BDF8),
                                fontSize = 10.sp,
                                fontFamily = FontFamily.Monospace,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }
                }

                // Stage Presentation Card (Cycles through capabilities)
                val currentFeature = features.getOrNull(currentStageIndex.coerceIn(0, features.size - 1))
                if (currentFeature != null) {
                    Card(
                        shape = RoundedCornerShape(16.dp),
                        colors = CardDefaults.cardColors(containerColor = Color(0xFF0F172A).copy(alpha = 0.85f)),
                        border = CardDefaults.outlinedCardBorder().copy(brush = Brush.linearGradient(listOf(currentFeature.accentColor.copy(alpha = 0.6f), Color(0xFF1E293B)))),
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 4.dp)
                    ) {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(18.dp),
                            verticalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Box(
                                        modifier = Modifier
                                            .size(36.dp)
                                            .clip(RoundedCornerShape(8.dp))
                                            .background(currentFeature.accentColor.copy(alpha = 0.18f)),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        Icon(
                                            imageVector = currentFeature.icon,
                                            contentDescription = null,
                                            tint = currentFeature.accentColor,
                                            modifier = Modifier.size(20.dp)
                                        )
                                    }
                                    Spacer(modifier = Modifier.width(10.dp))
                                    Column {
                                        Text(
                                            text = currentFeature.badge,
                                            color = currentFeature.accentColor,
                                            fontSize = 10.sp,
                                            fontWeight = FontWeight.Black,
                                            letterSpacing = 1.sp
                                        )
                                        Text(
                                            text = currentFeature.title,
                                            color = Color.White,
                                            fontSize = 15.sp,
                                            fontWeight = FontWeight.Bold
                                        )
                                    }
                                }

                                Surface(
                                    shape = RoundedCornerShape(12.dp),
                                    color = Color(0xFF1E293B)
                                ) {
                                    Text(
                                        text = "${currentStageIndex + 1}/5",
                                        color = AcrobatTextSecondary,
                                        fontSize = 11.sp,
                                        fontWeight = FontWeight.Bold,
                                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                                    )
                                }
                            }

                            Text(
                                text = currentFeature.subtitle,
                                color = AcrobatTextSecondary,
                                fontSize = 12.sp
                            )

                            HorizontalDivider(color = Color(0xFF1E293B))

                            // Capability bullets
                            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                                currentFeature.capabilities.forEach { cap ->
                                    Row(verticalAlignment = Alignment.Top) {
                                        Icon(
                                            imageVector = Icons.Default.CheckCircle,
                                            contentDescription = null,
                                            tint = currentFeature.accentColor,
                                            modifier = Modifier
                                                .size(14.dp)
                                                .padding(top = 2.dp)
                                        )
                                        Spacer(modifier = Modifier.width(8.dp))
                                        Text(
                                            text = cap,
                                            color = AcrobatTextPrimary,
                                            fontSize = 12.sp,
                                            lineHeight = 16.sp
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }

            // BOTTOM CONTROLS & HIGH REFRESH RATE UNLOCK NOTICE
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(12.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                // High Refresh Rate Hardware Notification
                Surface(
                    shape = RoundedCornerShape(12.dp),
                    color = Color(0xFF0F172A),
                    border = CardDefaults.outlinedCardBorder().copy(brush = Brush.linearGradient(listOf(Color(0xFF334155), Color(0xFF1E293B)))),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 14.dp, vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                imageVector = Icons.Default.Speed,
                                contentDescription = null,
                                tint = Color(0xFF00E5FF),
                                modifier = Modifier.size(18.dp)
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Column {
                                Text(
                                    text = "Device Display Target: Up to ${maxHardwareFps}Hz",
                                    color = Color.White,
                                    fontSize = 12.sp,
                                    fontWeight = FontWeight.SemiBold
                                )
                                Text(
                                    text = "Ready to unlock 60 / 90 / 120 / 165 FPS on entry",
                                    color = Color(0xFF38BDF8),
                                    fontSize = 10.sp
                                )
                            }
                        }

                        // Progress indicator bar
                        Box(
                            modifier = Modifier
                                .width(70.dp)
                                .height(6.dp)
                                .clip(RoundedCornerShape(3.dp))
                                .background(Color(0xFF1E293B))
                        ) {
                            val progress = (frameCount.toFloat() / totalShowcaseFrames.toFloat()).coerceIn(0f, 1f)
                            Box(
                                modifier = Modifier
                                    .fillMaxHeight()
                                    .fillMaxWidth(progress)
                                    .background(
                                        Brush.horizontalGradient(
                                            listOf(Color(0xFF00E5FF), Color(0xFF2563EB))
                                        )
                                    )
                            )
                        }
                    }
                }

                // Primary Enter / Get Started Button
                Button(
                    onClick = onFinishBootup,
                    shape = RoundedCornerShape(14.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = Color(0xFF0284C7)
                    ),
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(54.dp)
                        .testTag("enter_pdf_aio_button")
                ) {
                    Icon(
                        imageVector = Icons.Default.RocketLaunch,
                        contentDescription = null,
                        tint = Color.White,
                        modifier = Modifier.size(20.dp)
                    )
                    Spacer(modifier = Modifier.width(10.dp))
                    Text(
                        text = "Enter PDF AIO (Unlock Max ${maxHardwareFps} FPS)",
                        color = Color.White,
                        fontWeight = FontWeight.Bold,
                        fontSize = 15.sp
                    )
                }
            }
        }
    }
}

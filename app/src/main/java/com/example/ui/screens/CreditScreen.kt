package com.example.ui.screens

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.RoundRect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.*
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.ui.AppScreen
import com.example.ui.PdfViewModel
import com.example.ui.components.AdfIcon
import com.example.ui.theme.*
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlin.math.cos
import kotlin.math.sin

/**
 * Social Platform metadata model
 */
data class SocialPlatformInfo(
    val name: String,
    val handle: String,
    val url: String,
    val primaryColor: Color,
    val accentColor: Color,
    val iconRenderer: @Composable (Modifier, Color, Color) -> Unit
)

/**
 * ADF AIO Credit & Developer Screen
 * Featuring:
 * 1. Official Build & Developer Credentials (Ishant Kohli, Google AI Studio, 10/10/2026)
 * 2. Animated Follow Me Panel inspired by reference design with pink & cyan electrical neon borders
 * 3. STRICT 30 FPS cap on the electrical animation loop
 * 4. Active ONLY while Credit section is open (lifecycle bounded, zero CPU/GPU when navigated away)
 * 5. Interactive carousel & selector for all 8 social platforms
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CreditScreen(
    viewModel: PdfViewModel,
    onBack: () -> Unit
) {
    BackHandler { onBack() }
    val context = LocalContext.current

    // All 8 official social media platforms
    val platforms = remember {
        listOf(
            SocialPlatformInfo(
                name = "Instagram",
                handle = "@ishantkohli",
                url = "https://www.instagram.com/ishantkohli/",
                primaryColor = Color(0xFFFF2E93),
                accentColor = Color(0xFFD946EF),
                iconRenderer = { modifier, p, a -> AdfIcon.Instagram(modifier = modifier, size = 58.dp, primaryColor = p, accentColor = a) }
            ),
            SocialPlatformInfo(
                name = "GitHub",
                handle = "github.com/ishantkohli",
                url = "https://github.com/ishantkohli",
                primaryColor = Color(0xFFF0F6FC),
                accentColor = AdfElectricCyan,
                iconRenderer = { modifier, p, a -> AdfIcon.GitHub(modifier = modifier, size = 58.dp, primaryColor = p, accentColor = a) }
            ),
            SocialPlatformInfo(
                name = "LinkedIn",
                handle = "in/ishantkohli",
                url = "https://www.linkedin.com/in/ishantkohli",
                primaryColor = Color(0xFF00E5FF),
                accentColor = Color(0xFF2563EB),
                iconRenderer = { modifier, p, a -> AdfIcon.LinkedIn(modifier = modifier, size = 58.dp, primaryColor = p, accentColor = a) }
            ),
            SocialPlatformInfo(
                name = "X / Twitter",
                handle = "@ishantkohli",
                url = "https://x.com/ishantkohli",
                primaryColor = Color.White,
                accentColor = AdfElectricCyan,
                iconRenderer = { modifier, p, a -> AdfIcon.XTwitter(modifier = modifier, size = 58.dp, primaryColor = p, accentColor = a) }
            ),
            SocialPlatformInfo(
                name = "YouTube",
                handle = "@ishantkohli",
                url = "https://youtube.com/@ishantkohli",
                primaryColor = Color(0xFFEF4444),
                accentColor = Color.White,
                iconRenderer = { modifier, p, a -> AdfIcon.YouTube(modifier = modifier, size = 58.dp, primaryColor = p, accentColor = a) }
            ),
            SocialPlatformInfo(
                name = "WhatsApp",
                handle = "+Direct Connect",
                url = "https://wa.me/?text=Hello%20Ishant",
                primaryColor = Color(0xFF10B981),
                accentColor = Color.White,
                iconRenderer = { modifier, p, a -> AdfIcon.WhatsApp(modifier = modifier, size = 58.dp, primaryColor = p, accentColor = a) }
            ),
            SocialPlatformInfo(
                name = "Facebook",
                handle = "facebook.com/ishantkohli",
                url = "https://facebook.com/ishantkohli",
                primaryColor = Color(0xFF3B82F6),
                accentColor = Color.White,
                iconRenderer = { modifier, p, a -> AdfIcon.Facebook(modifier = modifier, size = 58.dp, primaryColor = p, accentColor = a) }
            ),
            SocialPlatformInfo(
                name = "Reddit",
                handle = "u/ishantkohli",
                url = "https://reddit.com/u/ishantkohli",
                primaryColor = Color(0xFFFF5722),
                accentColor = Color.White,
                iconRenderer = { modifier, p, a -> AdfIcon.Reddit(modifier = modifier, size = 58.dp, primaryColor = p, accentColor = a) }
            )
        )
    }

    var selectedIndex by remember { mutableIntStateOf(0) }
    var isAutoPlayEnabled by remember { mutableStateOf(true) }

    // STRICT 30 FPS ANIMATION LOOP
    // Active ONLY while CreditScreen is mounted. When navigating back, coroutine scope is cancelled immediately.
    var frameTick by remember { mutableIntStateOf(0) }
    LaunchedEffect(Unit) {
        val targetFrameDurationMs = 33L // Exact 30.3 FPS cap
        while (isActive) {
            val start = System.currentTimeMillis()
            frameTick++
            val elapsed = System.currentTimeMillis() - start
            val sleepMs = (targetFrameDurationMs - elapsed).coerceAtLeast(1L)
            delay(sleepMs)
        }
    }

    // Auto-advance carousel smoothly every 3.8s if enabled
    LaunchedEffect(isAutoPlayEnabled) {
        while (isAutoPlayEnabled && isActive) {
            delay(3800L)
            selectedIndex = (selectedIndex + 1) % platforms.size
        }
    }

    val activePlatform = platforms[selectedIndex]

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            text = "Credits & Developer",
                            style = MaterialTheme.typography.titleMedium.copy(
                                fontWeight = FontWeight.Bold,
                                letterSpacing = 0.5.sp
                            ),
                            color = AdfTextPrimary
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Surface(
                            shape = RoundedCornerShape(4.dp),
                            color = AdfElectricCyan.copy(alpha = 0.15f)
                        ) {
                            Text(
                                text = "VERIFIED",
                                color = AdfElectricCyan,
                                fontSize = 8.sp,
                                fontWeight = FontWeight.ExtraBold,
                                modifier = Modifier.padding(horizontal = 4.dp, vertical = 2.dp)
                            )
                        }
                    }
                },
                navigationIcon = {
                    IconButton(
                        onClick = onBack,
                        modifier = Modifier.testTag("credits_back_button")
                    ) {
                        AdfIcon.ChevronLeft(size = 22.dp, color = AdfElectricCyan)
                    }
                },
                actions = {
                    // Quick Web Explore link
                    IconButton(
                        onClick = {
                            val intent = Intent(Intent.ACTION_VIEW, Uri.parse("https://ishantkohli.me/Media/media.html"))
                            context.startActivity(intent)
                        },
                        modifier = Modifier.testTag("credits_web_link_button")
                    ) {
                        AdfIcon.Crown(size = 22.dp, color = AdfElectricCyan)
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = AdfMidnightBackground
                )
            )
        },
        containerColor = AdfMidnightBackground
    ) { padding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(horizontal = 16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
            contentPadding = PaddingValues(top = 10.dp, bottom = 48.dp)
        ) {
            // ==========================================
            // Section 1: Developer Credentials Banner
            // ==========================================
            item {
                DeveloperCredentialsCard(
                    onOpenWebsite = {
                        val intent = Intent(Intent.ACTION_VIEW, Uri.parse("https://ishantkohli.me/Media/media.html"))
                        context.startActivity(intent)
                    }
                )
            }

            // ==========================================
            // Section 2: Reference Neon "Follow Me" Panel
            // ==========================================
            item {
                Column(modifier = Modifier.fillMaxWidth()) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(bottom = 6.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(
                                text = "OFFICIAL SOCIAL MEDIA",
                                style = MaterialTheme.typography.labelMedium.copy(
                                    fontWeight = FontWeight.Bold,
                                    letterSpacing = 1.sp
                                ),
                                color = AdfElectricCyan
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Surface(
                                shape = RoundedCornerShape(4.dp),
                                color = Color(0xFFD946EF).copy(alpha = 0.15f)
                            ) {
                                Text(
                                    text = "30 FPS CAPPED",
                                    color = Color(0xFFD946EF),
                                    fontSize = 8.sp,
                                    fontWeight = FontWeight.Bold,
                                    modifier = Modifier.padding(horizontal = 4.dp, vertical = 2.dp)
                                )
                            }
                        }

                        // Auto-play switch indicator
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier
                                .clip(RoundedCornerShape(6.dp))
                                .clickable { isAutoPlayEnabled = !isAutoPlayEnabled }
                                .padding(horizontal = 6.dp, vertical = 2.dp)
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(6.dp)
                                    .clip(CircleShape)
                                    .background(if (isAutoPlayEnabled) Color(0xFF10B981) else AdfTextMuted)
                            )
                            Spacer(modifier = Modifier.width(4.dp))
                            Text(
                                text = if (isAutoPlayEnabled) "Auto 3.8s" else "Manual",
                                color = AdfTextSecondary,
                                fontSize = 10.sp
                            )
                        }
                    }

                    // MAIN FUTURISTIC NEON ELECTRICAL FOLLOW ME PANEL
                    NeonElectricalFollowMePanel(
                        platform = activePlatform,
                        frameTick = frameTick,
                        onClick = {
                            val intent = Intent(Intent.ACTION_VIEW, Uri.parse(activePlatform.url))
                            context.startActivity(intent)
                        }
                    )

                    Spacer(modifier = Modifier.height(12.dp))

                    // Platform Direct Navigation Arrows & Active Indicator
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        IconButton(
                            onClick = {
                                selectedIndex = if (selectedIndex > 0) selectedIndex - 1 else platforms.size - 1
                            },
                            modifier = Modifier
                                .size(36.dp)
                                .clip(CircleShape)
                                .background(AdfObsidianSurface)
                                .border(1.dp, AdfBorderSubtle, CircleShape)
                        ) {
                            AdfIcon.ChevronLeft(size = 18.dp, color = AdfElectricCyan)
                        }

                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Text(
                                text = activePlatform.name,
                                color = Color.White,
                                fontWeight = FontWeight.Bold,
                                fontSize = 15.sp
                            )
                            Text(
                                text = activePlatform.handle,
                                color = AdfElectricCyan,
                                fontSize = 11.sp,
                                fontFamily = FontFamily.Monospace
                            )
                        }

                        IconButton(
                            onClick = {
                                selectedIndex = (selectedIndex + 1) % platforms.size
                            },
                            modifier = Modifier
                                .size(36.dp)
                                .clip(CircleShape)
                                .background(AdfObsidianSurface)
                                .border(1.dp, AdfBorderSubtle, CircleShape)
                        ) {
                            AdfIcon.ChevronRight(size = 18.dp, color = AdfElectricCyan)
                        }
                    }

                    Spacer(modifier = Modifier.height(10.dp))

                    // 8 Platform Selector Chips
                    LazyRow(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        itemsIndexed(platforms) { index, item ->
                            val isSelected = selectedIndex == index
                            Surface(
                                onClick = {
                                    selectedIndex = index
                                    isAutoPlayEnabled = false // Pause auto carousel on manual selection
                                },
                                shape = RoundedCornerShape(12.dp),
                                color = if (isSelected) item.primaryColor.copy(alpha = 0.18f) else AdfObsidianSurface,
                                border = CardDefaults.outlinedCardBorder().copy(
                                    brush = Brush.horizontalGradient(
                                        if (isSelected) {
                                            listOf(item.primaryColor, item.accentColor)
                                        } else {
                                            listOf(AdfBorderSubtle, AdfBorderSubtle)
                                        }
                                    )
                                ),
                                modifier = Modifier.testTag("social_chip_${item.name.lowercase().replace(" ", "_")}")
                            ) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp)
                                ) {
                                    Box(
                                        modifier = Modifier
                                            .size(8.dp)
                                            .clip(CircleShape)
                                            .background(if (isSelected) item.primaryColor else AdfTextMuted)
                                    )
                                    Spacer(modifier = Modifier.width(6.dp))
                                    Text(
                                        text = item.name,
                                        color = if (isSelected) Color.White else AdfTextSecondary,
                                        fontSize = 11.sp,
                                        fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium
                                    )
                                }
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(12.dp))

                    // Action Buttons: Open in Browser + Copy Link
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        Button(
                            onClick = {
                                val intent = Intent(Intent.ACTION_VIEW, Uri.parse(activePlatform.url))
                                context.startActivity(intent)
                            },
                            colors = ButtonDefaults.buttonColors(
                                containerColor = AdfElectricCyan,
                                contentColor = AdfMidnightBackground
                            ),
                            shape = RoundedCornerShape(12.dp),
                            modifier = Modifier
                                .weight(1f)
                                .height(46.dp)
                                .testTag("visit_social_profile_button")
                        ) {
                            Text(
                                text = "Follow on ${activePlatform.name}",
                                fontWeight = FontWeight.Bold,
                                fontSize = 13.sp
                            )
                        }

                        OutlinedButton(
                            onClick = {
                                val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                                val clip = ClipData.newPlainText("${activePlatform.name} URL", activePlatform.url)
                                clipboard.setPrimaryClip(clip)
                                Toast.makeText(context, "${activePlatform.name} URL copied!", Toast.LENGTH_SHORT).show()
                            },
                            colors = ButtonDefaults.outlinedButtonColors(contentColor = AdfTextSecondary),
                            shape = RoundedCornerShape(12.dp),
                            modifier = Modifier
                                .height(46.dp)
                                .testTag("copy_social_url_button")
                        ) {
                            Text("Copy Link", fontSize = 12.sp)
                        }
                    }
                }
            }

            // ==========================================
            // Section 3: Technical Architecture & Specs
            // ==========================================
            item {
                Surface(
                    shape = RoundedCornerShape(16.dp),
                    color = AdfObsidianSurface,
                    border = CardDefaults.outlinedCardBorder().copy(
                        brush = Brush.linearGradient(listOf(AdfBorderSubtle, AdfBorderGlow))
                    ),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            AdfIcon.DeveloperBadge(size = 20.dp, primaryColor = AdfElectricCyan, accentColor = AdfCyberViolet)
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = "SYSTEM ARCHITECTURE & CREDITS",
                                color = AdfTextPrimary,
                                fontWeight = FontWeight.Bold,
                                fontSize = 13.sp,
                                letterSpacing = 0.5.sp
                            )
                        }

                        Spacer(modifier = Modifier.height(10.dp))

                        ArchitectureDetailRow("Application Name", "ADF AIO (All-In-One)")
                        ArchitectureDetailRow("Lead Architect", "Ishant Kohli")
                        ArchitectureDetailRow("Engineering Partner", "Google AI Studio")
                        ArchitectureDetailRow("Official Build Date", "10/10/2026")
                        ArchitectureDetailRow("Display Refresh Rates", "60 / 90 / 120 / 144 / 165 FPS Hardware Sync")
                        ArchitectureDetailRow("Frame-Rate Limiter", "30 FPS Strict Cap on Showcase & Electrical Waves")
                        ArchitectureDetailRow("Security Layer", "Zero-Cloud On-Device Local Processing")
                        ArchitectureDetailRow("Primary Stack", "Kotlin • Jetpack Compose • Room • Coroutines Flow")
                    }
                }
            }
        }
    }
}

/**
 * Top Developer Credentials Card with Build Date & Verified Badges
 */
@Composable
fun DeveloperCredentialsCard(
    onOpenWebsite: () -> Unit
) {
    Surface(
        shape = RoundedCornerShape(18.dp),
        color = AdfObsidianSurface,
        border = CardDefaults.outlinedCardBorder().copy(
            brush = Brush.linearGradient(
                listOf(
                    AdfElectricCyan.copy(alpha = 0.5f),
                    AdfCyberViolet.copy(alpha = 0.5f)
                )
            )
        ),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(modifier = Modifier.padding(18.dp)) {
            // Header Row
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(
                        modifier = Modifier
                            .size(42.dp)
                            .clip(RoundedCornerShape(10.dp))
                            .background(
                                Brush.linearGradient(
                                    listOf(AdfElectricCyan.copy(alpha = 0.2f), AdfCyberViolet.copy(alpha = 0.2f))
                                )
                            )
                            .border(1.dp, AdfElectricCyan.copy(alpha = 0.4f), RoundedCornerShape(10.dp)),
                        contentAlignment = Alignment.Center
                    ) {
                        AdfIcon.DeveloperBadge(size = 24.dp, primaryColor = AdfElectricCyan, accentColor = Color(0xFFD946EF))
                    }
                    Spacer(modifier = Modifier.width(12.dp))
                    Column {
                        Text(
                            text = "ADF AIO OFFICIAL",
                            color = AdfElectricCyan,
                            fontSize = 11.sp,
                            fontWeight = FontWeight.ExtraBold,
                            letterSpacing = 1.sp
                        )
                        Text(
                            text = "Developer & Engineering",
                            color = Color.White,
                            fontSize = 16.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }

                Surface(
                    shape = RoundedCornerShape(6.dp),
                    color = Color(0xFF10B981).copy(alpha = 0.15f),
                    border = CardDefaults.outlinedCardBorder().copy(
                        brush = Brush.horizontalGradient(listOf(Color(0xFF10B981), AdfElectricCyan))
                    )
                ) {
                    Text(
                        text = "10/10/2026",
                        color = Color(0xFF10B981),
                        fontSize = 10.sp,
                        fontWeight = FontWeight.Bold,
                        fontFamily = FontFamily.Monospace,
                        modifier = Modifier.padding(horizontal = 6.dp, vertical = 3.dp)
                    )
                }
            }

            Spacer(modifier = Modifier.height(14.dp))
            HorizontalDivider(color = AdfBorderSubtle)
            Spacer(modifier = Modifier.height(14.dp))

            // 3 Explicit Credentials requested by user:
            // 1. Build By - Ishant Kohli
            // 2. Coded By - Google AI Studio
            // 3. App Official Build Date - 10/10/2026
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                CredentialItem(
                    label = "Build By",
                    value = "Ishant Kohli",
                    badge = "CREATOR",
                    accentColor = AdfElectricCyan
                )

                CredentialItem(
                    label = "Coded By",
                    value = "Google AI Studio",
                    badge = "AI ENGINE",
                    accentColor = Color(0xFFD946EF)
                )

                CredentialItem(
                    label = "App Official Build Date",
                    value = "10/10/2026",
                    badge = "GOLD RELEASE",
                    accentColor = Color(0xFF10B981)
                )
            }

            Spacer(modifier = Modifier.height(14.dp))

            // Portfolio link button
            Surface(
                onClick = onOpenWebsite,
                shape = RoundedCornerShape(10.dp),
                color = AdfSurfaceVariant,
                border = CardDefaults.outlinedCardBorder().copy(
                    brush = Brush.horizontalGradient(listOf(AdfElectricCyan.copy(alpha = 0.4f), AdfBorderSubtle))
                ),
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 9.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            text = "🌐 ishantkohli.me/Media",
                            color = AdfTextPrimary,
                            fontSize = 12.sp,
                            fontWeight = FontWeight.SemiBold
                        )
                    }
                    Text(
                        text = "Explore Portfolio ↗",
                        color = AdfElectricCyan,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold
                    )
                }
            }
        }
    }
}

@Composable
fun CredentialItem(
    label: String,
    value: String,
    badge: String,
    accentColor: Color
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column {
            Text(
                text = label,
                color = AdfTextMuted,
                fontSize = 11.sp,
                fontWeight = FontWeight.Medium
            )
            Text(
                text = value,
                color = AdfTextPrimary,
                fontSize = 15.sp,
                fontWeight = FontWeight.Bold
            )
        }

        Surface(
            shape = RoundedCornerShape(4.dp),
            color = accentColor.copy(alpha = 0.12f)
        ) {
            Text(
                text = badge,
                color = accentColor,
                fontSize = 9.sp,
                fontWeight = FontWeight.ExtraBold,
                modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
            )
        }
    }
}

@Composable
fun ArchitectureDetailRow(
    title: String,
    detail: String
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.Top
    ) {
        Text(
            text = title,
            color = AdfTextMuted,
            fontSize = 11.sp,
            modifier = Modifier.weight(0.45f)
        )
        Text(
            text = detail,
            color = AdfTextSecondary,
            fontSize = 11.sp,
            fontWeight = FontWeight.SemiBold,
            textAlign = TextAlign.End,
            modifier = Modifier.weight(0.55f)
        )
    }
}

/**
 * Recreating the reference screenshot:
 * - Horizontally oriented rounded rectangular panel
 * - Dark glass interior with purple/cyan gradient
 * - Top-left: "ANIMATED" pill
 * - Top-right: "⚡ 240V"
 * - Glowing Pink Neon Electrical Line along left edge and top/bottom portions
 * - Glowing Cyan Neon Electrical Line along right edge and top/bottom portions
 * - Center content: "Follow" [Platform Icon] "Me"
 * - STRICT 30 FPS electrical waveforms
 */
@Composable
fun NeonElectricalFollowMePanel(
    platform: SocialPlatformInfo,
    frameTick: Int,
    onClick: () -> Unit
) {
    val interactionSource = remember { MutableInteractionSource() }
    val isPressed by interactionSource.collectIsPressedAsState()
    val scale by animateFloatAsState(
        targetValue = if (isPressed) 0.98f else 1f,
        animationSpec = spring(stiffness = Spring.StiffnessMediumLow),
        label = "NeonPanelPressScale"
    )

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .scale(scale)
            .height(220.dp)
            .clip(RoundedCornerShape(26.dp))
            .clickable(
                interactionSource = interactionSource,
                indication = null,
                onClick = onClick
            )
            .testTag("neon_follow_me_panel")
    ) {
        // LAYER 1: Deep obsidian / glassmorphism interior gradient
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(
                    Brush.horizontalGradient(
                        colors = listOf(
                            Color(0xFF1E0A1E), // Dark magenta tint on left
                            Color(0xFF0F0E1A), // Center obsidian
                            Color(0xFF071424)  // Dark cyan tint on right
                        )
                    )
                )
        )

        // LAYER 2: Glassmorphism inner translucent reflection surface
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(12.dp)
                .clip(RoundedCornerShape(18.dp))
                .background(
                    Brush.verticalGradient(
                        listOf(
                            Color.White.copy(alpha = 0.04f),
                            Color.Transparent,
                            Color.Black.copy(alpha = 0.25f)
                        )
                    )
                )
                .border(
                    width = 1.dp,
                    brush = Brush.horizontalGradient(
                        listOf(
                            Color(0xFFFF2E93).copy(alpha = 0.25f),
                            Color.White.copy(alpha = 0.08f),
                            Color(0xFF00E5FF).copy(alpha = 0.25f)
                        )
                    ),
                    shape = RoundedCornerShape(18.dp)
                )
        )

        // LAYER 3: Animated Pink & Cyan Electrical Neon Borders
        // Strictly computed on the 30 FPS tick!
        Canvas(modifier = Modifier.fillMaxSize()) {
            drawElectricalBorders(
                tick = frameTick,
                pinkColor = Color(0xFFFF2E93),
                cyanColor = Color(0xFF00E5FF)
            )
        }

        // LAYER 4: Header Tags matching screenshot
        // Top Left: "ANIMATED" | Top Right: "⚡ 240V"
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 24.dp, vertical = 20.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            // ANIMATED Pill badge
            Surface(
                shape = RoundedCornerShape(20.dp),
                color = Color.Black.copy(alpha = 0.45f),
                border = CardDefaults.outlinedCardBorder().copy(
                    brush = Brush.horizontalGradient(
                        listOf(Color(0xFFFF2E93).copy(alpha = 0.6f), Color.White.copy(alpha = 0.3f))
                    )
                )
            ) {
                Text(
                    text = "ANIMATED",
                    color = Color.White,
                    fontSize = 10.sp,
                    fontWeight = FontWeight.Bold,
                    letterSpacing = 1.2.sp,
                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp)
                )
            }

            // ⚡ 240V badge
            Row(verticalAlignment = Alignment.CenterVertically) {
                AdfIcon.Lightning(size = 14.dp, color = Color(0xFFD946EF))
                Spacer(modifier = Modifier.width(4.dp))
                Text(
                    text = "240V",
                    color = Color(0xFFD946EF),
                    fontSize = 11.sp,
                    fontWeight = FontWeight.ExtraBold,
                    fontFamily = FontFamily.Monospace,
                    letterSpacing = 0.5.sp
                )
            }
        }

        // LAYER 5: Central "Follow [Icon] Me" content
        Row(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 24.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.Center
        ) {
            // "Follow" text
            Text(
                text = "Follow",
                style = MaterialTheme.typography.headlineLarge.copy(
                    fontWeight = FontWeight.Medium,
                    fontSize = 34.sp,
                    letterSpacing = 1.sp
                ),
                color = Color.White
            )

            Spacer(modifier = Modifier.width(24.dp))

            // Center glowing icon for active platform
            Box(
                modifier = Modifier
                    .size(72.dp),
                contentAlignment = Alignment.Center
            ) {
                // Subtle ambient glow behind platform icon
                Box(
                    modifier = Modifier
                        .size(54.dp)
                        .background(
                            Brush.radialGradient(
                                listOf(
                                    platform.primaryColor.copy(alpha = 0.35f),
                                    Color.Transparent
                                )
                            )
                        )
                )

                platform.iconRenderer(
                    Modifier.size(56.dp),
                    platform.primaryColor,
                    platform.accentColor
                )
            }

            Spacer(modifier = Modifier.width(24.dp))

            // "Me" text
            Text(
                text = "Me",
                style = MaterialTheme.typography.headlineLarge.copy(
                    fontWeight = FontWeight.Medium,
                    fontSize = 34.sp,
                    letterSpacing = 1.sp
                ),
                color = Color.White
            )
        }
    }
}

/**
 * Custom Canvas drawing routine for the energetic Pink & Cyan electrical neon current
 * with high-frequency waveforms, fluctuating sparks, and soft outer glow.
 * Strictly driven by the 30 FPS integer frame tick.
 */
fun DrawScope.drawElectricalBorders(
    tick: Int,
    pinkColor: Color,
    cyanColor: Color
) {
    val w = size.width
    val h = size.height
    val cornerR = 26.dp.toPx()
    val strokeWidth = 3.dp.toPx()

    // 1. Draw soft outer diffuse glow for the pink (left) and cyan (right) sides
    drawRoundRect(
        brush = Brush.horizontalGradient(
            colors = listOf(
                pinkColor.copy(alpha = 0.20f),
                Color.Transparent,
                cyanColor.copy(alpha = 0.20f)
            )
        ),
        topLeft = Offset(1f, 1f),
        size = Size(w - 2f, h - 2f),
        cornerRadius = CornerRadius(cornerR, cornerR),
        style = Stroke(width = 10.dp.toPx())
    )

    // 2. Generate energetic electrical lightning paths with plasma jitter
    // Left half = Pink Current (from top-center (w*0.5, 0) around top-left, left, bottom-left to bottom-center (w*0.5, h))
    val pinkPath = Path()
    val pinkGlowPath = Path()

    // Right half = Cyan Current (from top-center (w*0.5, 0) around top-right, right, bottom-right to bottom-center (w*0.5, h))
    val cyanPath = Path()
    val cyanGlowPath = Path()

    // Generate Left (Pink) Current Path
    val leftSteps = 50
    for (i in 0..leftSteps) {
        val t = i / leftSteps.toFloat() // 0 to 1
        // Electrical wave offset: sin harmonics + frame tick phase
        val waveJitter = sin(t * 22f + tick * 0.45f) * 2.2f + cos(t * 38f - tick * 0.35f) * 1.5f

        val pt = getPerimeterPoint(
            fraction = 0.5f - (t * 0.5f), // from top-center (0.5) counter-clockwise to bottom-center (1.0 or 0.0)
            width = w,
            height = h,
            radius = cornerR,
            normalOffset = waveJitter
        )

        if (i == 0) {
            pinkPath.moveTo(pt.x, pt.y)
            pinkGlowPath.moveTo(pt.x, pt.y)
        } else {
            pinkPath.lineTo(pt.x, pt.y)
            pinkGlowPath.lineTo(pt.x, pt.y)
        }
    }

    // Generate Right (Cyan) Current Path
    val rightSteps = 50
    for (i in 0..rightSteps) {
        val t = i / rightSteps.toFloat() // 0 to 1
        val waveJitter = sin(t * 24f - tick * 0.45f) * 2.2f + cos(t * 36f + tick * 0.35f) * 1.5f

        val pt = getPerimeterPoint(
            fraction = 0.5f + (t * 0.5f), // from top-center (0.5) clockwise to bottom-center (1.0)
            width = w,
            height = h,
            radius = cornerR,
            normalOffset = waveJitter
        )

        if (i == 0) {
            cyanPath.moveTo(pt.x, pt.y)
            cyanGlowPath.moveTo(pt.x, pt.y)
        } else {
            cyanPath.lineTo(pt.x, pt.y)
            cyanGlowPath.lineTo(pt.x, pt.y)
        }
    }

    // Draw Pink electrical glow & core lines
    drawPath(
        path = pinkGlowPath,
        color = pinkColor.copy(alpha = 0.45f),
        style = Stroke(width = strokeWidth * 2.5f, cap = StrokeCap.Round, join = StrokeJoin.Round)
    )
    drawPath(
        path = pinkPath,
        color = Color(0xFFFF85C2), // Hot white-pink core
        style = Stroke(width = strokeWidth, cap = StrokeCap.Round, join = StrokeJoin.Round)
    )

    // Draw Cyan electrical glow & core lines
    drawPath(
        path = cyanGlowPath,
        color = cyanColor.copy(alpha = 0.45f),
        style = Stroke(width = strokeWidth * 2.5f, cap = StrokeCap.Round, join = StrokeJoin.Round)
    )
    drawPath(
        path = cyanPath,
        color = Color(0xFFC7F9FF), // Hot white-cyan core
        style = Stroke(width = strokeWidth, cap = StrokeCap.Round, join = StrokeJoin.Round)
    )

    // 3. Electrical spark nodes at intersection points (top-center and bottom-center)
    val topSparkX = w * 0.5f + (sin(tick * 0.6f) * 4f)
    val botSparkX = w * 0.5f + (cos(tick * 0.6f) * 4f)

    drawCircle(color = Color.White, radius = 2.5.dp.toPx(), center = Offset(topSparkX, 1.dp.toPx()))
    drawCircle(color = Color(0xFFD946EF).copy(alpha = 0.5f), radius = 6.dp.toPx(), center = Offset(topSparkX, 1.dp.toPx()))

    drawCircle(color = Color.White, radius = 2.5.dp.toPx(), center = Offset(botSparkX, h - 1.dp.toPx()))
    drawCircle(color = Color(0xFF00E5FF).copy(alpha = 0.5f), radius = 6.dp.toPx(), center = Offset(botSparkX, h - 1.dp.toPx()))
}

/**
 * Calculates a point along the rounded rectangle perimeter at normalized fraction [0..1]
 * with a normal electrical jitter offset.
 */
fun getPerimeterPoint(
    fraction: Float,
    width: Float,
    height: Float,
    radius: Float,
    normalOffset: Float
): Offset {
    val norm = ((fraction % 1f) + 1f) % 1f // wrap to [0, 1)

    // Perimeter breakdown: Top Edge, Top-Right Corner, Right Edge, Bottom-Right Corner,
    // Bottom Edge, Bottom-Left Corner, Left Edge, Top-Left Corner
    val straightH = (width - 2 * radius).coerceAtLeast(1f)
    val straightV = (height - 2 * radius).coerceAtLeast(1f)
    val cornerLen = (0.5f * Math.PI.toFloat() * radius)
    val totalPerimeter = (2 * straightH) + (2 * straightV) + (4 * cornerLen)

    val targetDist = norm * totalPerimeter

    var accumulated = 0f

    // 1. Top Edge: from (radius, 0) to (width - radius, 0)
    if (targetDist <= accumulated + straightH) {
        val seg = targetDist - accumulated
        val x = radius + seg
        val y = -normalOffset
        return Offset(x, y.coerceAtLeast(0f))
    }
    accumulated += straightH

    // 2. Top-Right Corner
    if (targetDist <= accumulated + cornerLen) {
        val seg = targetDist - accumulated
        val angle = (seg / cornerLen) * (0.5f * Math.PI.toFloat()) - (0.5f * Math.PI.toFloat())
        val cx = width - radius
        val cy = radius
        val r = radius + normalOffset
        return Offset(cx + cos(angle) * r, cy + sin(angle) * r)
    }
    accumulated += cornerLen

    // 3. Right Edge: from (width, radius) to (width, height - radius)
    if (targetDist <= accumulated + straightV) {
        val seg = targetDist - accumulated
        val x = width + normalOffset
        val y = radius + seg
        return Offset(x.coerceAtMost(width), y)
    }
    accumulated += straightV

    // 4. Bottom-Right Corner
    if (targetDist <= accumulated + cornerLen) {
        val seg = targetDist - accumulated
        val angle = (seg / cornerLen) * (0.5f * Math.PI.toFloat())
        val cx = width - radius
        val cy = height - radius
        val r = radius + normalOffset
        return Offset(cx + cos(angle) * r, cy + sin(angle) * r)
    }
    accumulated += cornerLen

    // 5. Bottom Edge: from (width - radius, height) to (radius, height)
    if (targetDist <= accumulated + straightH) {
        val seg = targetDist - accumulated
        val x = (width - radius) - seg
        val y = height + normalOffset
        return Offset(x, y.coerceAtMost(height))
    }
    accumulated += straightH

    // 6. Bottom-Left Corner
    if (targetDist <= accumulated + cornerLen) {
        val seg = targetDist - accumulated
        val angle = (seg / cornerLen) * (0.5f * Math.PI.toFloat()) + (0.5f * Math.PI.toFloat())
        val cx = radius
        val cy = height - radius
        val r = radius + normalOffset
        return Offset(cx + cos(angle) * r, cy + sin(angle) * r)
    }
    accumulated += cornerLen

    // 7. Left Edge: from (0, height - radius) to (0, radius)
    if (targetDist <= accumulated + straightV) {
        val seg = targetDist - accumulated
        val x = -normalOffset
        val y = (height - radius) - seg
        return Offset(x.coerceAtLeast(0f), y)
    }
    accumulated += straightV

    // 8. Top-Left Corner
    val seg = (targetDist - accumulated).coerceAtLeast(0f)
    val angle = (seg / cornerLen) * (0.5f * Math.PI.toFloat()) + Math.PI.toFloat()
    val cx = radius
    val cy = radius
    val r = radius + normalOffset
    return Offset(cx + cos(angle) * r, cy + sin(angle) * r)
}

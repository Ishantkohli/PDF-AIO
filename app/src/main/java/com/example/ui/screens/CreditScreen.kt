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
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.*
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.SystemUpdate
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.BuildConfig
import com.example.ui.PdfViewModel
import com.example.ui.components.AdfIcon
import com.example.ui.theme.*
import com.example.update.UpdateState
import com.example.update.UpdateViewModel
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlin.math.cos
import kotlin.math.sin

data class SocialPlatformInfo(
    val name: String,
    val handle: String,
    val url: String,
    val primaryColor: Color,
    val accentColor: Color,
    val iconRenderer: @Composable (Modifier, Color, Color) -> Unit
)

/**
 * Optimized ADF AIO Credit & Developer Screen
 * Universal compatibility for every phone worldwide (small, foldables, tablets).
 * Features 30 FPS capped electrical neon borders, 8 social media platforms,
 * and official verified developer credentials (Ishant Kohli, Google AI Studio, 10/10/2026).
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CreditScreen(
    viewModel: PdfViewModel,
    updateViewModel: UpdateViewModel? = null,
    onBack: () -> Unit
) {
    BackHandler { onBack() }
    val context = LocalContext.current

    val platforms = remember {
        listOf(
            SocialPlatformInfo("Instagram", "@ishantk_is", "https://www.instagram.com/ishantk_is?igsh=MW1oMTBreWZnemNpdA==", Color(0xFFFF2E93), Color(0xFFD946EF)) { m, p, a -> AdfIcon.Instagram(m, 56.dp, p, a) },
            SocialPlatformInfo("GitHub", "github.com/Ishantkohli", "https://github.com/Ishantkohli", Color(0xFFF0F6FC), AdfElectricCyan) { m, p, a -> AdfIcon.GitHub(m, 56.dp, p, a) },
            SocialPlatformInfo("LinkedIn", "in/ishant-kohli-292485381", "https://www.linkedin.com/in/ishant-kohli-292485381?utm_source=share_via&utm_content=profile&utm_medium=member_android", Color(0xFF00E5FF), Color(0xFF2563EB)) { m, p, a -> AdfIcon.LinkedIn(m, 56.dp, p, a) },
            SocialPlatformInfo("X / Twitter", "@Ishant_Kohli_Dv", "https://x.com/Ishant_Kohli_Dv?t=ZWJ5X3lQ7sXQfz5cyqilGw&s=09", Color.White, AdfElectricCyan) { m, p, a -> AdfIcon.XTwitter(m, 56.dp, p, a) },
            SocialPlatformInfo("WhatsApp", "+91 92200 37676", "https://wa.me/+919220037676", Color(0xFF10B981), Color.White) { m, p, a -> AdfIcon.WhatsApp(m, 56.dp, p, a) },
            SocialPlatformInfo("Reddit", "u/Ishant_k", "https://www.reddit.com/u/Ishant_k/s/eWeBI89LRu", Color(0xFFFF5722), Color.White) { m, p, a -> AdfIcon.Reddit(m, 56.dp, p, a) },
            SocialPlatformInfo("YouTube", "@ishantkohli", "https://youtube.com/@ishantkohli", Color(0xFFEF4444), Color.White) { m, p, a -> AdfIcon.YouTube(m, 56.dp, p, a) },
            SocialPlatformInfo("Facebook", "facebook.com/ishantkohli", "https://facebook.com/ishantkohli", Color(0xFF3B82F6), Color.White) { m, p, a -> AdfIcon.Facebook(m, 56.dp, p, a) }
        )
    }

    var selectedIndex by remember { mutableIntStateOf(0) }
    var isAutoPlayEnabled by remember { mutableStateOf(true) }

    // STRICT 30 FPS ANIMATION LOOP: Active ONLY while CreditScreen is mounted
    var frameTick by remember { mutableIntStateOf(0) }
    LaunchedEffect(Unit) {
        val targetFrameDurationMs = 33L
        while (isActive) {
            val start = System.currentTimeMillis()
            frameTick++
            val elapsed = System.currentTimeMillis() - start
            val sleepMs = (targetFrameDurationMs - elapsed).coerceAtLeast(1L)
            delay(sleepMs)
        }
    }

    // Auto-advance carousel smoothly
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
                            style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold, letterSpacing = 0.5.sp),
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
                    IconButton(onClick = onBack, modifier = Modifier.testTag("credits_back_button")) {
                        AdfIcon.ChevronLeft(size = 22.dp, color = AdfElectricCyan)
                    }
                },
                actions = {
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
                colors = TopAppBarDefaults.topAppBarColors(containerColor = AdfMidnightBackground)
            )
        },
        containerColor = AdfMidnightBackground
    ) { padding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding),
            contentAlignment = Alignment.TopCenter
        ) {
            LazyColumn(
                modifier = Modifier
                    .fillMaxSize()
                    .widthIn(max = 620.dp) // Universal adaptive constraint for foldables and tablets
                    .padding(horizontal = 16.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp),
                contentPadding = PaddingValues(top = 10.dp, bottom = 48.dp)
            ) {
                // Section 0: Check For Update at the Top of Developer Section
                item {
                    DeveloperUpdateCard(
                        updateViewModel = updateViewModel,
                        onCheckUpdate = {
                            updateViewModel?.checkForUpdates(context, manual = true)
                        },
                        onOpenSettings = {
                            updateViewModel?.openSettingsDialog()
                        }
                    )
                }

                // Section 1: Developer Credentials Banner
                item {
                    DeveloperCredentialsCard(
                        onOpenWebsite = {
                            val intent = Intent(Intent.ACTION_VIEW, Uri.parse("https://ishantkohli.me/Media/media.html"))
                            context.startActivity(intent)
                        }
                    )
                }

                // Section 2: Reference Neon "Follow Me" Panel
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
                                    style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Bold, letterSpacing = 1.sp),
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
                                    text = if (isAutoPlayEnabled) "Auto Next" else "Paused",
                                    color = AdfTextSecondary,
                                    fontSize = 10.sp
                                )
                            }
                        }

                        // The Neon Follow Me Panel
                        NeonElectricalFollowMePanel(
                            platform = activePlatform,
                            frameTick = frameTick,
                            onClick = {
                                val intent = Intent(Intent.ACTION_VIEW, Uri.parse(activePlatform.url))
                                context.startActivity(intent)
                            }
                        )

                        Spacer(modifier = Modifier.height(12.dp))

                        // Platform Selector Header
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
                                        isAutoPlayEnabled = false
                                    },
                                    shape = RoundedCornerShape(12.dp),
                                    color = if (isSelected) item.primaryColor.copy(alpha = 0.18f) else AdfObsidianSurface,
                                    border = CardDefaults.outlinedCardBorder().copy(
                                        brush = Brush.horizontalGradient(
                                            if (isSelected) listOf(item.primaryColor, item.accentColor)
                                            else listOf(AdfBorderSubtle, AdfBorderSubtle)
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

                // Section 3: Technical Architecture & Specs
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

                            val specs = listOf(
                                "Application Name" to "PDF AIO (All-In-One)",
                                "Lead Architect" to "Ishant Kohli",
                                "Engineering Partner" to "Google AI Studio",
                                "Official Build Date" to "10/10/2026",
                                "Display Refresh Rates" to "60 / 90 / 120 / 144 / 165 FPS Hardware Sync",
                                "Frame-Rate Limiter" to "30 FPS Strict Cap on Showcase & Electrical Waves",
                                "Security Layer" to "Zero-Cloud On-Device Local Processing",
                                "Primary Stack" to "Kotlin • Jetpack Compose • Room • Coroutines Flow"
                            )
                            specs.forEach { (title, detail) ->
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(vertical = 4.dp),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.Top
                                ) {
                                    Text(text = title, color = AdfTextMuted, fontSize = 11.sp, modifier = Modifier.weight(0.45f))
                                    Text(text = detail, color = AdfTextSecondary, fontSize = 11.sp, fontWeight = FontWeight.SemiBold, textAlign = TextAlign.End, modifier = Modifier.weight(0.55f))
                                }
                            }
                        }
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
                listOf(AdfElectricCyan.copy(alpha = 0.5f), AdfCyberViolet.copy(alpha = 0.5f))
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
                            text = "PDF AIO OFFICIAL",
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
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                CredentialRow("Build By", "Ishant Kohli", "CREATOR", AdfElectricCyan)
                CredentialRow("Coded By", "Google AI Studio", "AI ENGINE", Color(0xFFD946EF))
                CredentialRow("App Official Build Date", "10/10/2026", "GOLD RELEASE", Color(0xFF10B981))
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
                    Text(
                        text = "🌐 ishantkohli.me/Media",
                        color = AdfTextPrimary,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.SemiBold
                    )
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
private fun CredentialRow(label: String, value: String, badge: String, accentColor: Color) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column {
            Text(text = label, color = AdfTextMuted, fontSize = 11.sp, fontWeight = FontWeight.Medium)
            Text(text = value, color = AdfTextPrimary, fontSize = 15.sp, fontWeight = FontWeight.Bold)
        }
        Surface(shape = RoundedCornerShape(4.dp), color = accentColor.copy(alpha = 0.12f)) {
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

/**
 * High-performance Neon Electrical Follow Me Panel
 * Ultra-smooth, hardware-accelerated, zero-lag on any Android phone worldwide.
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
            .height(210.dp)
            .clip(RoundedCornerShape(26.dp))
            .clickable(interactionSource = interactionSource, indication = null, onClick = onClick)
            .testTag("neon_follow_me_panel")
    ) {
        // Deep obsidian glass gradient
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(
                    Brush.horizontalGradient(
                        listOf(Color(0xFF1E0A1E), Color(0xFF0F0E1A), Color(0xFF071424))
                    )
                )
        )

        // Glass reflection surface
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(10.dp)
                .clip(RoundedCornerShape(18.dp))
                .background(
                    Brush.verticalGradient(
                        listOf(Color.White.copy(alpha = 0.04f), Color.Transparent, Color.Black.copy(alpha = 0.25f))
                    )
                )
                .border(
                    1.dp,
                    Brush.horizontalGradient(
                        listOf(Color(0xFFFF2E93).copy(alpha = 0.25f), Color.White.copy(alpha = 0.08f), Color(0xFF00E5FF).copy(alpha = 0.25f))
                    ),
                    RoundedCornerShape(18.dp)
                )
        )

        // 30 FPS Canvas Electrical Neon Border
        Canvas(modifier = Modifier.fillMaxSize()) {
            drawElectricalBorders(
                tick = frameTick,
                pinkColor = Color(0xFFFF2E93),
                cyanColor = Color(0xFF00E5FF)
            )
        }

        // Header tags: "ANIMATED" & "⚡ 240V"
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 22.dp, vertical = 18.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Surface(
                shape = RoundedCornerShape(20.dp),
                color = Color.Black.copy(alpha = 0.45f),
                border = CardDefaults.outlinedCardBorder().copy(
                    brush = Brush.horizontalGradient(listOf(Color(0xFFFF2E93).copy(alpha = 0.6f), Color.White.copy(alpha = 0.3f)))
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

        // Central "Follow [Icon] Me" content
        Row(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 20.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.Center
        ) {
            Text(
                text = "Follow",
                style = MaterialTheme.typography.headlineLarge.copy(fontWeight = FontWeight.Medium, fontSize = 32.sp, letterSpacing = 1.sp),
                color = Color.White
            )

            Spacer(modifier = Modifier.width(20.dp))

            Box(modifier = Modifier.size(68.dp), contentAlignment = Alignment.Center) {
                Box(
                    modifier = Modifier
                        .size(52.dp)
                        .background(Brush.radialGradient(listOf(platform.primaryColor.copy(alpha = 0.35f), Color.Transparent)))
                )
                platform.iconRenderer(Modifier.size(54.dp), platform.primaryColor, platform.accentColor)
            }

            Spacer(modifier = Modifier.width(20.dp))

            Text(
                text = "Me",
                style = MaterialTheme.typography.headlineLarge.copy(fontWeight = FontWeight.Medium, fontSize = 32.sp, letterSpacing = 1.sp),
                color = Color.White
            )
        }
    }
}

/**
 * High-performance, lightweight drawing routine for Pink & Cyan energetic electrical lines
 */
fun DrawScope.drawElectricalBorders(
    tick: Int,
    pinkColor: Color,
    cyanColor: Color
) {
    val w = size.width
    val h = size.height
    val cornerR = 24.dp.toPx()
    val strokeWidth = 2.5.dp.toPx()

    // Diffuse outer glow
    drawRoundRect(
        brush = Brush.horizontalGradient(listOf(pinkColor.copy(alpha = 0.20f), Color.Transparent, cyanColor.copy(alpha = 0.20f))),
        topLeft = Offset(1f, 1f),
        size = Size(w - 2f, h - 2f),
        cornerRadius = CornerRadius(cornerR, cornerR),
        style = Stroke(width = 8.dp.toPx())
    )

    // Left (Pink) Current Path
    val pinkPath = Path()
    pinkPath.moveTo(w * 0.5f, 0f)
    val jitter1 = sin(tick * 0.5f) * 2f
    pinkPath.lineTo(cornerR, -jitter1)
    pinkPath.quadraticTo(0f, 0f, 0f, cornerR)
    pinkPath.lineTo(jitter1, h - cornerR)
    pinkPath.quadraticTo(0f, h, cornerR, h)
    pinkPath.lineTo(w * 0.5f, h + jitter1)

    drawPath(
        path = pinkPath,
        color = pinkColor.copy(alpha = 0.45f),
        style = Stroke(width = strokeWidth * 2.5f, cap = StrokeCap.Round, join = StrokeJoin.Round)
    )
    drawPath(
        path = pinkPath,
        color = Color(0xFFFF85C2),
        style = Stroke(width = strokeWidth, cap = StrokeCap.Round, join = StrokeJoin.Round)
    )

    // Right (Cyan) Current Path
    val cyanPath = Path()
    cyanPath.moveTo(w * 0.5f, 0f)
    val jitter2 = cos(tick * 0.5f) * 2f
    cyanPath.lineTo(w - cornerR, jitter2)
    cyanPath.quadraticTo(w, 0f, w, cornerR)
    cyanPath.lineTo(w + jitter2, h - cornerR)
    cyanPath.quadraticTo(w, h, w - cornerR, h)
    cyanPath.lineTo(w * 0.5f, h - jitter2)

    drawPath(
        path = cyanPath,
        color = cyanColor.copy(alpha = 0.45f),
        style = Stroke(width = strokeWidth * 2.5f, cap = StrokeCap.Round, join = StrokeJoin.Round)
    )
    drawPath(
        path = cyanPath,
        color = Color(0xFFC7F9FF),
        style = Stroke(width = strokeWidth, cap = StrokeCap.Round, join = StrokeJoin.Round)
    )

    // Sparks at fusion nodes
    val topSparkX = w * 0.5f + (sin(tick * 0.6f) * 4f)
    val botSparkX = w * 0.5f + (cos(tick * 0.6f) * 4f)
    drawCircle(color = Color.White, radius = 2.5.dp.toPx(), center = Offset(topSparkX, 1.dp.toPx()))
    drawCircle(color = Color(0xFFD946EF).copy(alpha = 0.5f), radius = 5.dp.toPx(), center = Offset(topSparkX, 1.dp.toPx()))
    drawCircle(color = Color.White, radius = 2.5.dp.toPx(), center = Offset(botSparkX, h - 1.dp.toPx()))
    drawCircle(color = Color(0xFF00E5FF).copy(alpha = 0.5f), radius = 5.dp.toPx(), center = Offset(botSparkX, h - 1.dp.toPx()))
}

/**
 * Check For Update Card at the top of the Developer / Credits section
 */
@Composable
fun DeveloperUpdateCard(
    updateViewModel: UpdateViewModel?,
    onCheckUpdate: () -> Unit,
    onOpenSettings: () -> Unit
) {
    val updateState = updateViewModel?.updateState?.collectAsStateWithLifecycle()?.value ?: UpdateState.Idle
    val isChecking = updateState is UpdateState.Checking

    Surface(
        shape = RoundedCornerShape(18.dp),
        color = AdfObsidianSurface,
        border = CardDefaults.outlinedCardBorder().copy(
            brush = Brush.horizontalGradient(
                listOf(AdfEmeraldGreen.copy(alpha = 0.6f), AdfElectricCyan.copy(alpha = 0.6f))
            )
        ),
        modifier = Modifier
            .fillMaxWidth()
            .testTag("developer_check_for_update_card")
    ) {
        Column(modifier = Modifier.padding(18.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.weight(1f)
                ) {
                    Box(
                        modifier = Modifier
                            .size(42.dp)
                            .clip(RoundedCornerShape(10.dp))
                            .background(
                                Brush.linearGradient(
                                    listOf(AdfEmeraldGreen.copy(alpha = 0.2f), AdfElectricCyan.copy(alpha = 0.2f))
                                )
                            )
                            .border(1.dp, AdfEmeraldGreen.copy(alpha = 0.4f), RoundedCornerShape(10.dp)),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Default.SystemUpdate,
                            contentDescription = null,
                            tint = AdfEmeraldGreen,
                            modifier = Modifier.size(24.dp)
                        )
                    }

                    Spacer(modifier = Modifier.width(12.dp))

                    Column {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(
                                text = "Check For Update",
                                color = AdfTextPrimary,
                                style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold)
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Surface(
                                shape = RoundedCornerShape(4.dp),
                                color = AdfEmeraldGreen.copy(alpha = 0.15f)
                            ) {
                                Text(
                                    text = "v${BuildConfig.VERSION_NAME}",
                                    color = AdfEmeraldGreen,
                                    fontSize = 10.sp,
                                    fontWeight = FontWeight.ExtraBold,
                                    modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                                )
                            }
                        }

                        Text(
                            text = "GitHub Releases OTA • Auto-Install",
                            color = AdfTextSecondary,
                            style = MaterialTheme.typography.bodySmall.copy(fontSize = 11.sp)
                        )
                    }
                }

                IconButton(
                    onClick = onOpenSettings,
                    modifier = Modifier
                        .size(36.dp)
                        .testTag("developer_update_settings_button")
                ) {
                    Icon(
                        imageVector = Icons.Default.Settings,
                        contentDescription = "Update Settings",
                        tint = AdfTextSecondary,
                        modifier = Modifier.size(20.dp)
                    )
                }
            }

            Spacer(modifier = Modifier.height(14.dp))

            // Main "Check For Update" Button
            Button(
                onClick = onCheckUpdate,
                enabled = !isChecking,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(46.dp)
                    .testTag("developer_check_for_update_button"),
                shape = RoundedCornerShape(12.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = CrimsonPrimary,
                    contentColor = Color.White,
                    disabledContainerColor = CrimsonPrimary.copy(alpha = 0.5f),
                    disabledContentColor = Color.White.copy(alpha = 0.7f)
                )
            ) {
                if (isChecking) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(18.dp),
                        strokeWidth = 2.dp,
                        color = Color.White
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = "Checking GitHub...",
                        fontWeight = FontWeight.Bold,
                        fontSize = 13.5.sp
                    )
                } else {
                    Icon(
                        imageVector = Icons.Default.Refresh,
                        contentDescription = null,
                        modifier = Modifier.size(18.dp),
                        tint = Color.White
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = "Check For Update",
                        fontWeight = FontWeight.Bold,
                        fontSize = 13.5.sp
                    )
                }
            }
        }
    }
}


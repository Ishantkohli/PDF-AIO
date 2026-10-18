package com.example.ui.screens

import androidx.compose.animation.*
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.local.DocumentEntity
import com.example.ui.theme.*

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LiquidModeReader(
    document: DocumentEntity,
    onClose: () -> Unit,
    onReadAloud: (String) -> Unit
) {
    var fontSize by remember { mutableFloatStateOf(16f) }
    var readingTheme by remember { mutableStateOf("DARK") } // DARK, LIGHT, SEPIA, OLED
    var isFontSettingsOpen by remember { mutableStateOf(false) }

    // Pre-extracted or simulated reflowed structured content for documents
    val reflowSections = remember(document) {
        when {
            document.title.contains("NDA", ignoreCase = true) || document.title.contains("Disclosure", ignoreCase = true) -> listOf(
                "Mutual Non-Disclosure Agreement (NDA)" to """
                    This Mutual Non-Disclosure Agreement ("Agreement") is entered into on October 14, 2026, by and between AcroTech Solutions Inc., a Delaware corporation, and the Undersigned Recipient.
                """.trimIndent(),
                "1. Purpose of Disclosure" to """
                    The parties intend to explore a strategic partnership regarding Cloud Document Technologies, proprietary machine learning models, and automated document workflow pipelines. In connection with this prospective collaboration, either party may disclose confidential business and technical information.
                """.trimIndent(),
                "2. Definition of Confidential Information" to """
                    "Confidential Information" means all non-public information disclosed by one party ("Disclosing Party") to the other party ("Receiving Party"), whether orally or in writing, that is designated as confidential or that reasonably should be understood to be confidential given the nature of the information.
                    
                    This includes source code, proprietary algorithms, financial estimates, customer records, and product roadmaps.
                """.trimIndent(),
                "3. Non-Disclosure & Non-Use Obligations" to """
                    The Receiving Party agrees to:
                    • Protect Confidential Information with at least reasonable care.
                    • Not disclose Confidential Information to any third party without prior written consent.
                    • Use Confidential Information solely to evaluate the prospective business relationship.
                """.trimIndent(),
                "4. Term and Termination" to """
                    This Agreement remains in effect for a term of three (3) years from the effective date. Upon written notice, the Receiving Party must promptly return or destroy all physical and electronic copies of Confidential Information within fourteen (14) business days.
                """.trimIndent(),
                "5. Governing Law and Remedies" to """
                    This Agreement shall be governed by the laws of the State of California without regard to conflict of law principles.
                """.trimIndent()
            )
            document.title.contains("Whitepaper", ignoreCase = true) || document.title.contains("Architecture", ignoreCase = true) -> listOf(
                "Executive Abstract" to """
                    Modern enterprise document workflows demand zero-latency on-device processing combined with high-reasoning multimodal cloud intelligence. This paper presents an end-to-end architecture for client-side PDF rendering, vector annotation persistence, and contextual document reasoning with thinking models that parse dense technical specifications in sub-second timeframes.
                """.trimIndent(),
                "System Component Breakdown" to """
                    1. Client Native Rendering Engine: Leverages OS hardware accelerated rasterization via Android's PdfRenderer.
                    2. Non-Destructive Annotation Store: Maintains stroke vectors without altering original streams.
                    3. Flattening and Export Pipeline: Synthesizes vector overlays into standardized PDF 1.7 standards.
                    4. Multimodal Reasoning Assistant: Streams semantic queries through specialized thinking models.
                """.trimIndent(),
                "Performance Benchmarks" to """
                    • Standard NDA (1-5 MB): Raster latency 12 ms, AI Summary latency 1.1s.
                    • Technical Spec (15 MB): Raster latency 44 ms, AI Summary latency 1.8s.
                    • Financial Ledger (45 MB): Raster latency 110 ms, AI Summary latency 2.4s.
                """.trimIndent(),
                "Architectural Conclusion" to """
                    The hybrid architecture delivers exceptional user responsiveness while guaranteeing complete document privacy and security.
                """.trimIndent()
            )
            else -> listOf(
                document.title to """
                    Document: ${document.title} (${document.pageCount} Pages).
                    Liquid Mode dynamically reorganizes text columns, images, and tables into a responsive mobile layout.
                    Read comfortably without pinching and zooming.
                """.trimIndent(),
                "Document Overview" to """
                    Use Liquid Mode for effortless reading on small screens. You can customize font sizes, change reading warmth themes, or have Acrobat read aloud to you using the Read Aloud button.
                """.trimIndent()
            )
        }
    }

    val currentThemeColors = when (readingTheme) {
        "LIGHT" -> Triple(Color(0xFFF8FAFC), Color(0xFF0F172A), Color(0xFFE2E8F0))
        "SEPIA" -> Triple(Color(0xFFFBF0D9), Color(0xFF3E2723), Color(0xFFE7D7C1))
        "OLED" -> Triple(Color(0xFF000000), Color(0xFFF1F5F9), Color(0xFF1E1E24))
        else -> Triple(AcrobatDarkBackground, AcrobatTextPrimary, AcrobatDarkBorder)
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Box(
                            modifier = Modifier
                                .size(30.dp)
                                .clip(RoundedCornerShape(8.dp))
                                .background(
                                    Brush.linearGradient(listOf(Color(0xFF06B6D4), Color(0xFF3B82F6)))
                                ),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = Icons.Default.WaterDrop,
                                contentDescription = null,
                                tint = Color.White,
                                modifier = Modifier.size(18.dp)
                            )
                        }
                        Spacer(modifier = Modifier.width(8.dp))
                        Column {
                            Text(
                                text = "Liquid Mode™",
                                style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                                color = AcrobatTextPrimary
                            )
                            Text(
                                text = "Reflowed Mobile Reader",
                                style = MaterialTheme.typography.labelSmall,
                                color = Color(0xFF38BDF8)
                            )
                        }
                    }
                },
                navigationIcon = {
                    IconButton(onClick = onClose) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Exit Liquid Mode", tint = AcrobatTextPrimary)
                    }
                },
                actions = {
                    // Read Aloud Entire Document
                    IconButton(
                        onClick = {
                            val fullText = reflowSections.joinToString("\n\n") { "${it.first}. ${it.second}" }
                            onReadAloud(fullText)
                        },
                        modifier = Modifier.testTag("liquid_read_aloud_button")
                    ) {
                        Icon(
                            imageVector = Icons.Default.VolumeUp,
                            contentDescription = "Read Aloud",
                            tint = CrimsonAccent
                        )
                    }

                    // Font & Theme Settings Toggle
                    IconButton(
                        onClick = { isFontSettingsOpen = !isFontSettingsOpen },
                        modifier = Modifier.testTag("liquid_settings_button")
                    ) {
                        Icon(
                            imageVector = Icons.Default.FormatSize,
                            contentDescription = "Typography",
                            tint = if (isFontSettingsOpen) CrimsonAccent else AcrobatTextSecondary
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = AcrobatDarkBackground)
            )
        },
        containerColor = currentThemeColors.first
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
        ) {
            // Typography & Theme Floating Control Drawer
            AnimatedVisibility(
                visible = isFontSettingsOpen,
                enter = fadeIn() + expandVertically(),
                exit = fadeOut() + shrinkVertically()
            ) {
                Surface(
                    color = AcrobatDarkSurfaceElevated,
                    border = CardDefaults.outlinedCardBorder().copy(brush = androidx.compose.ui.graphics.SolidColor(AcrobatDarkBorder)),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text("Font Size: ${fontSize.toInt()}sp", color = AcrobatTextPrimary, style = MaterialTheme.typography.labelMedium)
                            Slider(
                                value = fontSize,
                                onValueChange = { fontSize = it },
                                valueRange = 13f..26f,
                                colors = SliderDefaults.colors(
                                    thumbColor = CrimsonPrimary,
                                    activeTrackColor = CrimsonPrimary
                                ),
                                modifier = Modifier
                                    .weight(1f)
                                    .padding(horizontal = 16.dp)
                            )
                        }

                        Spacer(modifier = Modifier.height(10.dp))

                        // Theme Selection Chips
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Text("Theme:", color = AcrobatTextSecondary, style = MaterialTheme.typography.labelSmall)
                            listOf(
                                "DARK" to "Dark Crimson",
                                "LIGHT" to "Paper",
                                "SEPIA" to "Warm",
                                "OLED" to "True Black"
                            ).forEach { (mode, label) ->
                                FilterChip(
                                    selected = readingTheme == mode,
                                    onClick = { readingTheme = mode },
                                    label = { Text(label, fontSize = 11.sp) },
                                    colors = FilterChipDefaults.filterChipColors(
                                        selectedContainerColor = CrimsonPrimary,
                                        selectedLabelColor = Color.White
                                    )
                                )
                            }
                        }
                    }
                }
            }

            // Quick Chapter / Section Jump Chips
            LazyRow(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .background(currentThemeColors.first)
                    .padding(horizontal = 16.dp, vertical = 8.dp)
            ) {
                items(reflowSections) { (sectionTitle, _) ->
                    SuggestionChip(
                        onClick = { /* Jump */ },
                        label = { Text(sectionTitle.take(20), fontSize = 11.sp) },
                        colors = SuggestionChipDefaults.suggestionChipColors(
                            containerColor = currentThemeColors.first,
                            labelColor = currentThemeColors.second
                        ),
                        border = SuggestionChipDefaults.suggestionChipBorder(
                            enabled = true,
                            borderColor = currentThemeColors.third
                        )
                    )
                }
            }

            // Reflowed Text Content
            LazyColumn(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(horizontal = 20.dp),
                verticalArrangement = Arrangement.spacedBy(20.dp),
                contentPadding = PaddingValues(top = 10.dp, bottom = 48.dp)
            ) {
                items(reflowSections) { (sectionTitle, sectionContent) ->
                    Column {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween,
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text(
                                text = sectionTitle,
                                style = MaterialTheme.typography.titleMedium.copy(
                                    fontWeight = FontWeight.Bold,
                                    fontSize = (fontSize + 3).sp
                                ),
                                color = if (readingTheme == "LIGHT" || readingTheme == "SEPIA") CrimsonPrimary else CrimsonAccent,
                                modifier = Modifier.weight(1f)
                            )

                            IconButton(
                                onClick = { onReadAloud(sectionContent) },
                                modifier = Modifier.size(32.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Default.VolumeUp,
                                    contentDescription = "Read section",
                                    tint = CrimsonAccent,
                                    modifier = Modifier.size(16.dp)
                                )
                            }
                        }

                        Spacer(modifier = Modifier.height(8.dp))

                        Text(
                            text = sectionContent,
                            style = MaterialTheme.typography.bodyMedium.copy(
                                fontSize = fontSize.sp,
                                lineHeight = (fontSize * 1.55f).sp
                            ),
                            color = currentThemeColors.second
                        )
                    }
                }
            }
        }
    }
}

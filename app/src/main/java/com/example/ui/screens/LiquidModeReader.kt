package com.example.ui.screens

import androidx.activity.compose.BackHandler
import androidx.compose.animation.*
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.data.local.DocumentEntity
import com.example.data.pdf.PdfTextExtractor
import com.example.ui.theme.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LiquidModeReader(
    document: DocumentEntity,
    onClose: () -> Unit,
    onReadAloud: (String) -> Unit
) {
    BackHandler { onClose() }

    val coroutineScope = rememberCoroutineScope()
    val listState = rememberLazyListState()
    val activeThemeStyle by ThemeManager.currentTheme.collectAsStateWithLifecycle()
    val isLiquidTheme = activeThemeStyle == AppThemeStyle.LIQUID_GLASS || activeThemeStyle == AppThemeStyle.FROSTED_CRYSTAL

    var fontSize by remember { mutableFloatStateOf(16f) }
    var readingTheme by remember { mutableStateOf("DARK") } // DARK, LIGHT, SEPIA, OLED
    var isFontSettingsOpen by remember { mutableStateOf(false) }

    var isExtracting by remember(document) { mutableStateOf(true) }
    var reflowSections by remember(document) { mutableStateOf<List<Pair<String, String>>>(emptyList()) }

    // Extract actual embedded text streams from the active PDF file asynchronously
    LaunchedEffect(document.id, document.filePath) {
        isExtracting = true
        val extracted = withContext(Dispatchers.IO) {
            val file = File(document.filePath)
            if (!file.exists() || file.length() == 0L) {
                emptyList<Pair<String, String>>()
            } else {
                val pages = PdfTextExtractor.extractPages(file)
                pages.mapNotNull { extractedPage ->
                    val cleaned = extractedPage.fullText.trim()
                    if (cleaned.isNotEmpty()) {
                        "Page ${extractedPage.pageNumber}" to cleaned
                    } else null
                }
            }
        }
        reflowSections = extracted.ifEmpty {
            listOf(
                document.title to "No embedded text stream was detected in \"${document.title}\" (${document.pageCount} pages). If this is a scanned or image-only PDF, run OCR in the Document Scanner or view pages directly in the standard PDF Reader."
            )
        }
        isExtracting = false
    }

    val currentThemeColors = when (readingTheme) {
        "LIGHT" -> Triple(Color(0xFFF8FAFC), Color(0xFF0F172A), Color(0xFFE2E8F0))
        "SEPIA" -> Triple(Color(0xFFFBF0D9), Color(0xFF3E2723), Color(0xFFE7D7C1))
        "OLED" -> Triple(Color(0xFF000000), Color(0xFFF1F5F9), Color(0xFF1E1E24))
        else -> Triple(
            if (isLiquidTheme) Color.Transparent else AcrobatDarkBackground,
            AcrobatTextPrimary,
            AcrobatDarkBorder
        )
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
                                text = "Reflow Reader",
                                style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                                color = AcrobatTextPrimary
                            )
                            Text(
                                text = "PDF (AIO) Mobile Text View",
                                style = MaterialTheme.typography.labelSmall,
                                color = Color(0xFF38BDF8)
                            )
                        }
                    }
                },
                navigationIcon = {
                    IconButton(onClick = onClose) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Exit Reflow Reader", tint = AcrobatTextPrimary)
                    }
                },
                actions = {
                    IconButton(
                        onClick = {
                            val fullText = reflowSections.joinToString("\n\n") { "${it.first}. ${it.second}" }
                            if (fullText.isNotBlank()) onReadAloud(fullText)
                        },
                        modifier = Modifier.testTag("liquid_read_aloud_button")
                    ) {
                        Icon(
                            imageVector = Icons.Default.VolumeUp,
                            contentDescription = "Read Aloud",
                            tint = CrimsonAccent
                        )
                    }

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
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = if (isLiquidTheme) GlassSurfaceDark else AcrobatDarkBackground
                )
            )
        },
        containerColor = currentThemeColors.first
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
        ) {
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

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Text("Theme:", color = AcrobatTextSecondary, style = MaterialTheme.typography.labelSmall)
                            listOf(
                                "DARK" to "Dark",
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

            if (isExtracting) {
                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        CircularProgressIndicator(color = Color(0xFF38BDF8))
                        Spacer(modifier = Modifier.height(12.dp))
                        Text(
                            text = "Extracting PDF text...",
                            color = AcrobatTextSecondary,
                            fontSize = 13.sp
                        )
                    }
                }
            } else {
                // Quick Page / Section Jump Chips
                LazyRow(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(currentThemeColors.first)
                        .padding(horizontal = 16.dp, vertical = 8.dp)
                ) {
                    itemsIndexed(reflowSections) { index, (sectionTitle, _) ->
                        SuggestionChip(
                            onClick = {
                                coroutineScope.launch {
                                    listState.animateScrollToItem(index)
                                }
                            },
                            label = { Text(sectionTitle.take(20), fontSize = 11.sp) },
                            colors = SuggestionChipDefaults.suggestionChipColors(
                                containerColor = if (currentThemeColors.first == Color.Transparent) GlassSurfaceDark else currentThemeColors.first,
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
                    state = listState,
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(horizontal = 20.dp),
                    verticalArrangement = Arrangement.spacedBy(20.dp),
                    contentPadding = PaddingValues(top = 10.dp, bottom = 48.dp)
                ) {
                    itemsIndexed(reflowSections) { _, (sectionTitle, sectionContent) ->
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
}

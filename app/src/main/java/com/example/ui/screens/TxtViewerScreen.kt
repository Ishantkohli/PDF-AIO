package com.example.ui.screens

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.text.format.Formatter
import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.ui.PdfViewModel
import com.example.ui.theme.*
import kotlinx.coroutines.launch

private enum class TxtReaderTheme(val label: String) {
    AUTO("Auto"),
    LIGHT("Paper"),
    SEPIA("Sepia"),
    OLED("OLED")
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TxtViewerScreen(
    viewModel: PdfViewModel,
    onBack: () -> Unit
) {
    val txtDoc by viewModel.currentTxtDocument.collectAsStateWithLifecycle()
    val activeThemeStyle by ThemeManager.currentTheme.collectAsStateWithLifecycle()
    val isLiquidTheme = activeThemeStyle == AppThemeStyle.LIQUID_GLASS || activeThemeStyle == AppThemeStyle.FROSTED_CRYSTAL

    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val listState = rememberLazyListState()

    var fontSizeSp by remember { mutableFloatStateOf(14.5f) }
    var useMonospace by remember { mutableStateOf(false) }
    var wordWrap by remember { mutableStateOf(true) }
    var showLineNumbers by remember { mutableStateOf(true) }
    var readerTheme by remember { mutableStateOf(TxtReaderTheme.AUTO) }
    var showSearchBar by remember { mutableStateOf(false) }
    var searchQuery by remember { mutableStateOf("") }
    var currentMatchPtr by remember { mutableIntStateOf(0) }
    var isConvertingToPdf by remember { mutableStateOf(false) }
    var copiedNotice by remember { mutableStateOf(false) }

    BackHandler { onBack() }

    val doc = txtDoc
    if (doc == null) {
        Box(
            modifier = Modifier.fillMaxSize(),
            contentAlignment = Alignment.Center
        ) {
            CircularProgressIndicator(color = AdfElectricCyan)
        }
        return
    }

    val matchingLineIndices = remember(doc.lines, searchQuery) {
        val q = searchQuery.trim()
        if (q.isEmpty()) emptyList()
        else doc.lines.mapIndexedNotNull { idx, line ->
            if (line.contains(q, ignoreCase = true)) idx else null
        }
    }

    LaunchedEffect(matchingLineIndices) {
        currentMatchPtr = 0
        if (matchingLineIndices.isNotEmpty()) {
            listState.animateScrollToItem(matchingLineIndices.first())
        }
    }

    val (paperBg, paperText, lineNumColor) = when (readerTheme) {
        TxtReaderTheme.LIGHT -> Triple(Color(0xFFF8FAFC), Color(0xFF0F172A), Color(0xFF94A3B8))
        TxtReaderTheme.SEPIA -> Triple(Color(0xFFF5EEDB), Color(0xFF3E2C1C), Color(0xFFA68B6B))
        TxtReaderTheme.OLED -> Triple(Color(0xFF000000), Color(0xFFE2E8F0), Color(0xFF475569))
        TxtReaderTheme.AUTO -> Triple(
            if (isLiquidTheme) Color.Transparent else AdfMidnightBackground,
            AdfTextPrimary,
            AdfTextMuted
        )
    }

    Scaffold(
        containerColor = if (isLiquidTheme) Color.Transparent else AdfMidnightBackground,
        topBar = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(
                        if (isLiquidTheme) GlassSurfaceDark
                        else AdfSurfaceCard
                    )
                    .statusBarsPadding()
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 10.dp, vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    // Crystalline glossy back button on Liquid themes, crisp pill on standard themes
                    Box(
                        modifier = Modifier
                            .size(40.dp)
                            .clip(CircleShape)
                            .background(
                                if (isLiquidTheme) Brush.verticalGradient(
                                    listOf(Color(0x45FFFFFF), Color(0x18FFFFFF))
                                ) else Brush.verticalGradient(
                                    listOf(AdfSurfaceVariant, AdfSurfaceVariant)
                                )
                            )
                            .border(
                                width = 1.dp,
                                brush = if (isLiquidTheme) LiquidGlassBorderGradient
                                else Brush.linearGradient(listOf(AdfBorderSubtle, AdfBorderSubtle)),
                                shape = CircleShape
                            ),
                        contentAlignment = Alignment.Center
                    ) {
                        IconButton(
                            onClick = onBack,
                            modifier = Modifier.testTag("txt_viewer_back_button")
                        ) {
                            Icon(
                                imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                                contentDescription = "Back",
                                tint = AdfElectricCyan
                            )
                        }
                    }

                    Spacer(modifier = Modifier.width(10.dp))

                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = doc.title,
                            style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold),
                            color = AdfTextPrimary,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                        Text(
                            text = "${doc.lines.size} lines • ${Formatter.formatShortFileSize(context, doc.fileSizeBytes)} • UTF-8 TXT",
                            style = MaterialTheme.typography.bodySmall.copy(fontSize = 11.sp),
                            color = AdfElectricCyan
                        )
                    }

                    IconButton(
                        onClick = { showSearchBar = !showSearchBar },
                        modifier = Modifier.testTag("txt_viewer_search_toggle")
                    ) {
                        Icon(
                            imageVector = Icons.Default.Search,
                            contentDescription = "Search Text",
                            tint = if (showSearchBar) AdfElectricCyan else AdfTextSecondary
                        )
                    }

                    IconButton(
                        onClick = {
                            val cm = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                            cm.setPrimaryClip(ClipData.newPlainText(doc.title, doc.fullText))
                            copiedNotice = true
                        },
                        modifier = Modifier.testTag("txt_viewer_copy_button")
                    ) {
                        Icon(
                            imageVector = if (copiedNotice) Icons.Default.Check else Icons.Default.ContentCopy,
                            contentDescription = "Copy All Text",
                            tint = if (copiedNotice) AdfEmerald else AdfTextSecondary
                        )
                    }

                    IconButton(
                        onClick = {
                            val sendIntent = Intent(Intent.ACTION_SEND).apply {
                                type = "text/plain"
                                putExtra(Intent.EXTRA_SUBJECT, doc.title)
                                putExtra(Intent.EXTRA_TEXT, doc.fullText.take(100_000))
                            }
                            context.startActivity(Intent.createChooser(sendIntent, "Share TXT Content"))
                        },
                        modifier = Modifier.testTag("txt_viewer_share_button")
                    ) {
                        Icon(
                            imageVector = Icons.Default.Share,
                            contentDescription = "Share Text",
                            tint = AdfTextSecondary
                        )
                    }
                }

                // In-document search bar
                AnimatedVisibility(visible = showSearchBar) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 12.dp, vertical = 6.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        OutlinedTextField(
                            value = searchQuery,
                            onValueChange = { searchQuery = it },
                            placeholder = { Text("Find in text...", fontSize = 12.sp, color = AdfTextMuted) },
                            singleLine = true,
                            colors = OutlinedTextFieldDefaults.colors(
                                focusedContainerColor = if (isLiquidTheme) GlassSurfaceElevated else AdfMidnightSurface,
                                unfocusedContainerColor = if (isLiquidTheme) GlassSurfaceElevated else AdfMidnightSurface,
                                focusedBorderColor = AdfElectricCyan,
                                unfocusedBorderColor = AdfBorderSubtle,
                                focusedTextColor = AdfTextPrimary,
                                unfocusedTextColor = AdfTextPrimary
                            ),
                            shape = RoundedCornerShape(10.dp),
                            modifier = Modifier
                                .weight(1f)
                                .testTag("txt_search_input")
                        )
                        if (matchingLineIndices.isNotEmpty()) {
                            Text(
                                text = "${currentMatchPtr + 1}/${matchingLineIndices.size}",
                                color = AdfElectricCyan,
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Bold
                            )
                            IconButton(
                                onClick = {
                                    val prev = if (currentMatchPtr - 1 < 0) matchingLineIndices.lastIndex else currentMatchPtr - 1
                                    currentMatchPtr = prev
                                    scope.launch { listState.animateScrollToItem(matchingLineIndices[prev]) }
                                },
                                modifier = Modifier.size(34.dp)
                            ) {
                                Icon(Icons.Default.KeyboardArrowUp, contentDescription = "Previous Match", tint = AdfTextPrimary)
                            }
                            IconButton(
                                onClick = {
                                    val next = (currentMatchPtr + 1) % matchingLineIndices.size
                                    currentMatchPtr = next
                                    scope.launch { listState.animateScrollToItem(matchingLineIndices[next]) }
                                },
                                modifier = Modifier.size(34.dp)
                            ) {
                                Icon(Icons.Default.KeyboardArrowDown, contentDescription = "Next Match", tint = AdfTextPrimary)
                            }
                        }
                    }
                }

                // Controls strip: font size, font family, wrap, line numbers, reading palette, Convert to PDF
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .horizontalScroll(rememberScrollState())
                        .padding(horizontal = 12.dp, vertical = 6.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    FilterChip(
                        selected = false,
                        onClick = { fontSizeSp = (fontSizeSp - 1.5f).coerceAtLeast(10f) },
                        label = { Text("A-", fontWeight = FontWeight.Bold, fontSize = 12.sp) }
                    )
                    FilterChip(
                        selected = false,
                        onClick = { fontSizeSp = (fontSizeSp + 1.5f).coerceAtMost(28f) },
                        label = { Text("A+", fontWeight = FontWeight.Bold, fontSize = 12.sp) }
                    )
                    FilterChip(
                        selected = useMonospace,
                        onClick = { useMonospace = !useMonospace },
                        label = { Text(if (useMonospace) "Mono" else "Sans", fontSize = 11.sp) }
                    )
                    FilterChip(
                        selected = wordWrap,
                        onClick = { wordWrap = !wordWrap },
                        label = { Text(if (wordWrap) "Wrap: ON" else "Wrap: OFF", fontSize = 11.sp) }
                    )
                    FilterChip(
                        selected = showLineNumbers,
                        onClick = { showLineNumbers = !showLineNumbers },
                        label = { Text("Lines", fontSize = 11.sp) }
                    )
                    TxtReaderTheme.values().forEach { t ->
                        FilterChip(
                            selected = readerTheme == t,
                            onClick = { readerTheme = t },
                            label = { Text(t.label, fontSize = 11.sp) }
                        )
                    }
                    FilledTonalButton(
                        onClick = {
                            if (!isConvertingToPdf) {
                                isConvertingToPdf = true
                                viewModel.convertTxtDocumentToPdf(doc) {
                                    isConvertingToPdf = false
                                }
                            }
                        },
                        enabled = !isConvertingToPdf,
                        contentPadding = PaddingValues(horizontal = 12.dp, vertical = 4.dp),
                        colors = ButtonDefaults.filledTonalButtonColors(
                            containerColor = AdfElectricCyan.copy(alpha = 0.2f),
                            contentColor = AdfElectricCyan
                        )
                    ) {
                        if (isConvertingToPdf) {
                            CircularProgressIndicator(modifier = Modifier.size(14.dp), strokeWidth = 2.dp, color = AdfElectricCyan)
                        } else {
                            Icon(Icons.Default.PictureAsPdf, contentDescription = null, modifier = Modifier.size(15.dp))
                        }
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("To PDF", fontSize = 11.sp, fontWeight = FontWeight.Bold)
                    }
                }
                HorizontalDivider(color = AdfBorderSubtle.copy(alpha = 0.4f))
            }
        }
    ) { innerPadding ->
        val activeHighlightLine = matchingLineIndices.getOrNull(currentMatchPtr)
        val lineHorizontalScroll = rememberScrollState()
        val fontFamily = if (useMonospace) FontFamily.Monospace else FontFamily.SansSerif

        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .background(paperBg)
                .navigationBarsPadding(),
            contentAlignment = Alignment.TopCenter
        ) {
            LazyColumn(
                state = listState,
                modifier = Modifier
                    .fillMaxSize()
                    .widthIn(max = 780.dp)
                    .then(if (!wordWrap) Modifier.horizontalScroll(lineHorizontalScroll) else Modifier),
                contentPadding = PaddingValues(horizontal = 14.dp, vertical = 14.dp),
                verticalArrangement = Arrangement.spacedBy(2.dp)
            ) {
                itemsIndexed(
                    items = doc.lines,
                    key = { idx, _ -> idx }
                ) { index, lineText ->
                    val isMatchedLine = searchQuery.isNotBlank() && lineText.contains(searchQuery, ignoreCase = true)
                    val isCurrentMatch = index == activeHighlightLine

                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .then(
                                if (isCurrentMatch) Modifier
                                    .clip(RoundedCornerShape(4.dp))
                                    .background(AdfElectricCyan.copy(alpha = 0.22f))
                                else if (isMatchedLine) Modifier
                                    .clip(RoundedCornerShape(4.dp))
                                    .background(AdfElectricCyan.copy(alpha = 0.08f))
                                else Modifier
                            )
                            .padding(vertical = 2.dp),
                        verticalAlignment = Alignment.Top
                    ) {
                        if (showLineNumbers) {
                            Text(
                                text = "${index + 1}".padStart(4, ' '),
                                fontFamily = FontFamily.Monospace,
                                fontSize = (fontSizeSp * 0.78f).sp,
                                color = lineNumColor,
                                modifier = Modifier.padding(end = 10.dp)
                            )
                        }

                        if (searchQuery.isNotBlank() && isMatchedLine) {
                            val q = searchQuery.trim()
                            val annotated = remember(lineText, q, paperText) {
                                buildAnnotatedString {
                                    var cursor = 0
                                    val lowerLine = lineText.lowercase()
                                    val lowerQ = q.lowercase()
                                    while (cursor < lineText.length) {
                                        val hit = lowerLine.indexOf(lowerQ, cursor)
                                        if (hit < 0) {
                                            append(lineText.substring(cursor))
                                            break
                                        }
                                        if (hit > cursor) {
                                            append(lineText.substring(cursor, hit))
                                        }
                                        withStyle(
                                            SpanStyle(
                                                background = Color(0xFFFACC15),
                                                color = Color(0xFF0F172A),
                                                fontWeight = FontWeight.Bold
                                            )
                                        ) {
                                            append(lineText.substring(hit, hit + q.length))
                                        }
                                        cursor = hit + q.length
                                    }
                                }
                            }
                            Text(
                                text = annotated,
                                fontFamily = fontFamily,
                                fontSize = fontSizeSp.sp,
                                lineHeight = (fontSizeSp * 1.48f).sp,
                                color = paperText,
                                softWrap = wordWrap
                            )
                        } else {
                            Text(
                                text = lineText.ifEmpty { " " },
                                fontFamily = fontFamily,
                                fontSize = fontSizeSp.sp,
                                lineHeight = (fontSizeSp * 1.48f).sp,
                                color = paperText,
                                softWrap = wordWrap
                            )
                        }
                    }
                }
            }
        }
    }
}

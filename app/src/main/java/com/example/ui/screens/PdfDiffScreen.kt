package com.example.ui.screens

import android.content.ClipData
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.Color as AndroidColor
import android.graphics.pdf.PdfRenderer
import android.net.Uri
import android.os.ParcelFileDescriptor
import android.provider.OpenableColumns
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowLeft
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.ArrowDownward
import androidx.compose.material.icons.filled.ArrowUpward
import androidx.compose.material.icons.filled.Description
import androidx.compose.material.icons.filled.Difference
import androidx.compose.material.icons.filled.IosShare
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.FileProvider
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.data.pdf.MyersDiff
import com.example.data.pdf.PdfTextExtractor
import com.example.ui.PdfViewModel
import com.example.ui.theme.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream

private enum class DiffViewMode(val label: String) {
    SIDE_BY_SIDE("Side-by-Side"),
    MYERS_UNIFIED("Myers Diff"),
    PIXEL_VISUAL("Pixel Diff")
}

private data class DiffDocData(
    val cachedPdfFile: File? = null,
    val pageLines: List<List<String>> = emptyList(),
    val allLines: List<String> = emptyList(),
    val pageCount: Int = 0,
    val error: String? = null
)

/**
 * 100% Offline Multi-Page Visual & Myers Line PDF Comparison / Diff Tool.
 * Supports synchronized side-by-side panes, deterministic Myers LCS diff with Next/Prev
 * change jumping, active-page visual pixel difference overlay, and local Markdown report export.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PdfDiffScreen(
    viewModel: PdfViewModel,
    onBack: () -> Unit
) {
    BackHandler { onBack() }

    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val activeThemeStyle by ThemeManager.currentTheme.collectAsStateWithLifecycle()
    val isLiquidTheme = activeThemeStyle == AppThemeStyle.LIQUID_GLASS || activeThemeStyle == AppThemeStyle.FROSTED_CRYSTAL
    val themeColorScheme = ThemeManager.getColorScheme(activeThemeStyle)
    val accentColor = themeColorScheme.primary

    var uriDocA by remember { mutableStateOf<Uri?>(null) }
    var uriDocB by remember { mutableStateOf<Uri?>(null) }
    var nameDocA by remember { mutableStateOf("Original Document A") }
    var nameDocB by remember { mutableStateOf("Revised Document B") }

    var dataDocA by remember { mutableStateOf<DiffDocData?>(null) }
    var dataDocB by remember { mutableStateOf<DiffDocData?>(null) }
    var loadingA by remember { mutableStateOf(false) }
    var loadingB by remember { mutableStateOf(false) }

    var currentComparePage by remember { mutableIntStateOf(0) }
    var activeViewMode by remember { mutableStateOf(DiffViewMode.MYERS_UNIFIED) }
    var activeChangeJumpIdx by remember { mutableIntStateOf(0) }

    var previewBitmapA by remember { mutableStateOf<Bitmap?>(null) }
    var previewBitmapB by remember { mutableStateOf<Bitmap?>(null) }
    var pixelDiffOverlay by remember { mutableStateOf<Pair<Bitmap, Double>?>(null) }
    var isRenderingVisualDiff by remember { mutableStateOf(false) }

    DisposableEffect(Unit) {
        onDispose {
            dataDocA?.cachedPdfFile?.delete()
            dataDocB?.cachedPdfFile?.delete()
        }
    }

    fun resolveDisplayName(uri: Uri, fallback: String): String {
        return try {
            context.contentResolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)?.use { c ->
                val idx = c.getColumnIndex(OpenableColumns.DISPLAY_NAME)
                if (idx >= 0 && c.moveToFirst()) c.getString(idx) else null
            }
        } catch (_: Exception) {
            null
        } ?: uri.lastPathSegment?.substringAfterLast('/') ?: fallback
    }

    suspend fun loadPdfForDiff(uri: Uri, prefix: String, previousFile: File?): DiffDocData = withContext(Dispatchers.IO) {
        previousFile?.delete()
        val tempFile = File(context.cacheDir, "diff_${prefix}_${System.currentTimeMillis()}.pdf")
        try {
            val copied = context.contentResolver.openInputStream(uri)?.use { input ->
                FileOutputStream(tempFile).use { output -> input.copyTo(output) }
                true
            } ?: false

            if (!copied || !tempFile.exists() || tempFile.length() == 0L) {
                tempFile.delete()
                return@withContext DiffDocData(error = "Could not open selected PDF file.")
            }

            val pages = PdfTextExtractor.extractPages(tempFile)
            val perPage = pages.map { extractedPage ->
                val rawLines = extractedPage.fullText.lines().map { it.trim() }.filter { it.isNotEmpty() }
                rawLines.ifEmpty { listOf("[Page ${extractedPage.pageNumber}: No embedded text / scanned image]") }
            }
            val combined = perPage.flatMapIndexed { idx, lines ->
                listOf("— Page ${idx + 1} —") + lines
            }

            var pageCount = pages.size
            var pfd: ParcelFileDescriptor? = null
            var renderer: PdfRenderer? = null
            try {
                pfd = ParcelFileDescriptor.open(tempFile, ParcelFileDescriptor.MODE_READ_ONLY)
                renderer = PdfRenderer(pfd)
                pageCount = renderer.pageCount
            } catch (_: Exception) {
            } finally {
                try { renderer?.close() } catch (_: Exception) {}
                try { pfd?.close() } catch (_: Exception) {}
            }

            DiffDocData(
                cachedPdfFile = tempFile,
                pageLines = perPage,
                allLines = combined.ifEmpty { listOf("No embedded text found in this PDF.") },
                pageCount = pageCount
            )
        } catch (e: Exception) {
            tempFile.delete()
            DiffDocData(error = e.message ?: "Failed to parse PDF.")
        }
    }

    suspend fun renderSinglePageBitmap(file: File?, pageIndex: Int): Bitmap? = withContext(Dispatchers.IO) {
        if (file == null || !file.exists() || pageIndex < 0) return@withContext null
        var pfd: ParcelFileDescriptor? = null
        var renderer: PdfRenderer? = null
        try {
            pfd = ParcelFileDescriptor.open(file, ParcelFileDescriptor.MODE_READ_ONLY)
            renderer = PdfRenderer(pfd)
            if (pageIndex >= renderer.pageCount) return@withContext null
            renderer.openPage(pageIndex).use { page ->
                val maxW = 720
                val scale = maxW.toFloat() / page.width.coerceAtLeast(1)
                val w = (page.width * scale).toInt().coerceAtLeast(1)
                val h = (page.height * scale).toInt().coerceAtLeast(1)
                val bmp = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
                bmp.eraseColor(AndroidColor.WHITE)
                page.render(bmp, null, null, PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY)
                bmp
            }
        } catch (_: Exception) {
            null
        } finally {
            try { renderer?.close() } catch (_: Exception) {}
            try { pfd?.close() } catch (_: Exception) {}
        }
    }

    val pickerA = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) {
            uriDocA = uri
            nameDocA = resolveDisplayName(uri, "Document A")
        }
    }

    val pickerB = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) {
            uriDocB = uri
            nameDocB = resolveDisplayName(uri, "Document B")
        }
    }

    LaunchedEffect(uriDocA) {
        val uri = uriDocA ?: return@LaunchedEffect
        loadingA = true
        dataDocA = loadPdfForDiff(uri, "a", dataDocA?.cachedPdfFile)
        currentComparePage = 0
        activeChangeJumpIdx = 0
        loadingA = false
    }

    LaunchedEffect(uriDocB) {
        val uri = uriDocB ?: return@LaunchedEffect
        loadingB = true
        dataDocB = loadPdfForDiff(uri, "b", dataDocB?.cachedPdfFile)
        currentComparePage = 0
        activeChangeJumpIdx = 0
        loadingB = false
    }

    val maxPageCount = remember(dataDocA, dataDocB) {
        maxOf(dataDocA?.pageCount ?: 0, dataDocB?.pageCount ?: 0)
    }

    // Render active comparison page bitmaps & pixel diff overlay on demand
    LaunchedEffect(dataDocA?.cachedPdfFile, dataDocB?.cachedPdfFile, currentComparePage, activeViewMode) {
        isRenderingVisualDiff = true
        val bmpA = renderSinglePageBitmap(dataDocA?.cachedPdfFile, currentComparePage)
        val bmpB = renderSinglePageBitmap(dataDocB?.cachedPdfFile, currentComparePage)
        previewBitmapA = bmpA
        previewBitmapB = bmpB
        pixelDiffOverlay = if (bmpA != null && bmpB != null) {
            withContext(Dispatchers.Default) {
                MyersDiff.computePixelDiffBitmap(bmpA, bmpB)
            }
        } else null
        isRenderingVisualDiff = false
    }

    // Compute Myers Diff for the active page (or full document summary when exporting)
    val activePageLinesA = remember(dataDocA, currentComparePage) {
        dataDocA?.pageLines?.getOrNull(currentComparePage) ?: emptyList()
    }
    val activePageLinesB = remember(dataDocB, currentComparePage) {
        dataDocB?.pageLines?.getOrNull(currentComparePage) ?: emptyList()
    }
    val myersSummary = remember(activePageLinesA, activePageLinesB) {
        MyersDiff.computeLineDiff(activePageLinesA, activePageLinesB)
    }

    val unifiedListState = rememberLazyListState()
    val scrollStateA = rememberScrollState()
    val scrollStateB = rememberScrollState()
    var isSyncingScroll by remember { mutableStateOf(false) }

    LaunchedEffect(scrollStateA, scrollStateB) {
        snapshotFlow { scrollStateA.value }.collectLatest { value ->
            if (!isSyncingScroll && scrollStateB.value != value) {
                isSyncingScroll = true
                try {
                    scrollStateB.scrollTo(value.coerceIn(0, scrollStateB.maxValue))
                } finally {
                    isSyncingScroll = false
                }
            }
        }
    }
    LaunchedEffect(scrollStateB, scrollStateA) {
        snapshotFlow { scrollStateB.value }.collectLatest { value ->
            if (!isSyncingScroll && scrollStateA.value != value) {
                isSyncingScroll = true
                try {
                    scrollStateA.scrollTo(value.coerceIn(0, scrollStateA.maxValue))
                } finally {
                    isSyncingScroll = false
                }
            }
        }
    }

    fun exportReport() {
        val docA = dataDocA ?: return
        val docB = dataDocB ?: return
        scope.launch {
            val reportFile = withContext(Dispatchers.IO) {
                val fullSummary = MyersDiff.computeLineDiff(docA.allLines, docB.allLines)
                val markdown = MyersDiff.generateComparisonReport(
                    nameA = nameDocA,
                    nameB = nameDocB,
                    pageCountA = docA.pageCount,
                    pageCountB = docB.pageCount,
                    summary = fullSummary,
                    pixelDiffPercent = pixelDiffOverlay?.second
                )
                val stagingDir = File(context.cacheDir, "share_staging").apply { if (!exists()) mkdirs() }
                val out = File(stagingDir, "PDF_Comparison_Report_${System.currentTimeMillis()}.md")
                out.writeText(markdown, Charsets.UTF_8)
                out
            }
            val uri = FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", reportFile)
            val intent = Intent(Intent.ACTION_SEND).apply {
                type = "text/markdown"
                putExtra(Intent.EXTRA_STREAM, uri)
                putExtra(Intent.EXTRA_SUBJECT, "PDF Comparison Report: $nameDocA vs $nameDocB")
                clipData = ClipData.newUri(context.contentResolver, "Comparison Report", uri)
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            }
            context.startActivity(Intent.createChooser(intent, "Export Comparison Report"))
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Box(
                            modifier = Modifier
                                .size(36.dp)
                                .clip(RoundedCornerShape(8.dp))
                                .background(accentColor.copy(alpha = 0.2f)),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(Icons.Default.Difference, contentDescription = null, tint = accentColor, modifier = Modifier.size(20.dp))
                        }
                        Spacer(modifier = Modifier.width(10.dp))
                        Column {
                            Text(
                                text = "Visual PDF Compare / Diff",
                                style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                                color = themeColorScheme.onBackground
                            )
                            Text(
                                text = "100% Local Offline Myers & Pixel Diff",
                                style = MaterialTheme.typography.bodySmall.copy(fontSize = 11.sp),
                                color = themeColorScheme.onSurfaceVariant
                            )
                        }
                    }
                },
                navigationIcon = {
                    IconButton(onClick = onBack, modifier = Modifier.testTag("pdf_diff_back_button")) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back", tint = accentColor)
                    }
                },
                actions = {
                    if (dataDocA != null && dataDocB != null && dataDocA?.error == null && dataDocB?.error == null) {
                        IconButton(
                            onClick = { exportReport() },
                            modifier = Modifier.testTag("pdf_diff_export_report")
                        ) {
                            Icon(Icons.Default.IosShare, contentDescription = "Export Diff Report", tint = accentColor)
                        }
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = if (isLiquidTheme) Color.Transparent else themeColorScheme.background
                )
            )
        },
        containerColor = if (isLiquidTheme) Color.Transparent else themeColorScheme.background
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(12.dp)
        ) {
            // Document Selector Bar
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                OutlinedButton(
                    onClick = { pickerA.launch(arrayOf("application/pdf")) },
                    modifier = Modifier.weight(1f).testTag("pdf_diff_pick_a"),
                    shape = RoundedCornerShape(12.dp),
                    border = ButtonDefaults.outlinedButtonBorder(enabled = true).copy(
                        brush = Brush.horizontalGradient(listOf(Color(0xFFEF4444), Color(0xFFEF4444)))
                    )
                ) {
                    Icon(Icons.Default.Description, contentDescription = null, tint = Color(0xFFEF4444), modifier = Modifier.size(16.dp))
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = nameDocA,
                        color = themeColorScheme.onSurface,
                        fontSize = 11.sp,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }

                OutlinedButton(
                    onClick = { pickerB.launch(arrayOf("application/pdf")) },
                    modifier = Modifier.weight(1f).testTag("pdf_diff_pick_b"),
                    shape = RoundedCornerShape(12.dp),
                    border = ButtonDefaults.outlinedButtonBorder(enabled = true).copy(
                        brush = Brush.horizontalGradient(listOf(Color(0xFF10B981), Color(0xFF10B981)))
                    )
                ) {
                    Icon(Icons.Default.Description, contentDescription = null, tint = Color(0xFF10B981), modifier = Modifier.size(16.dp))
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = nameDocB,
                        color = themeColorScheme.onSurface,
                        fontSize = 11.sp,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }

            Spacer(modifier = Modifier.height(8.dp))

            // Mode Selector + Page-Count Delta Badge
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                DiffViewMode.entries.forEach { mode ->
                    FilterChip(
                        selected = activeViewMode == mode,
                        onClick = { activeViewMode = mode },
                        label = { Text(mode.label, fontSize = 11.sp) },
                        modifier = Modifier.testTag("diff_mode_${mode.name.lowercase()}"),
                        colors = FilterChipDefaults.filterChipColors(
                            selectedContainerColor = accentColor,
                            selectedLabelColor = Color.White
                        )
                    )
                }
                Spacer(modifier = Modifier.weight(1f))
                if (dataDocA != null && dataDocB != null) {
                    val delta = (dataDocB?.pageCount ?: 0) - (dataDocA?.pageCount ?: 0)
                    if (delta != 0) {
                        Surface(
                            color = if (delta > 0) Color(0xFF10B981).copy(alpha = 0.2f) else Color(0xFFEF4444).copy(alpha = 0.2f),
                            shape = RoundedCornerShape(8.dp)
                        ) {
                            Text(
                                text = if (delta > 0) "+$delta pages" else "$delta pages",
                                color = if (delta > 0) Color(0xFF10B981) else Color(0xFFEF4444),
                                fontSize = 10.sp,
                                fontWeight = FontWeight.Bold,
                                modifier = Modifier.padding(horizontal = 6.dp, vertical = 3.dp)
                            )
                        }
                    }
                }
            }

            // Multi-page Navigation & Change-Jump Bar
            if (maxPageCount > 0) {
                Surface(
                    shape = RoundedCornerShape(10.dp),
                    color = if (isLiquidTheme) GlassSurfaceDark else themeColorScheme.surface,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 4.dp)
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 8.dp, vertical = 4.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            IconButton(
                                onClick = {
                                    if (currentComparePage > 0) {
                                        currentComparePage--
                                        activeChangeJumpIdx = 0
                                    }
                                },
                                enabled = currentComparePage > 0,
                                modifier = Modifier.size(32.dp)
                            ) {
                                Icon(Icons.AutoMirrored.Filled.KeyboardArrowLeft, contentDescription = "Previous Page")
                            }
                            Text(
                                text = "Page ${currentComparePage + 1} / $maxPageCount",
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Bold,
                                color = themeColorScheme.onSurface
                            )
                            IconButton(
                                onClick = {
                                    if (currentComparePage < maxPageCount - 1) {
                                        currentComparePage++
                                        activeChangeJumpIdx = 0
                                    }
                                },
                                enabled = currentComparePage < maxPageCount - 1,
                                modifier = Modifier.size(32.dp)
                            ) {
                                Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, contentDescription = "Next Page")
                            }
                        }

                        if (activeViewMode == DiffViewMode.MYERS_UNIFIED && myersSummary.changePositions.isNotEmpty()) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Text(
                                    text = "-${myersSummary.removedCount} / +${myersSummary.addedCount}",
                                    fontSize = 11.sp,
                                    color = accentColor,
                                    fontWeight = FontWeight.Bold
                                )
                                IconButton(
                                    onClick = {
                                        val totalChanges = myersSummary.changePositions.size
                                        activeChangeJumpIdx = if (activeChangeJumpIdx - 1 < 0) totalChanges - 1 else activeChangeJumpIdx - 1
                                        val targetLineIdx = myersSummary.changePositions[activeChangeJumpIdx]
                                        scope.launch { unifiedListState.animateScrollToItem(targetLineIdx) }
                                    },
                                    modifier = Modifier.size(30.dp)
                                ) {
                                    Icon(Icons.Default.ArrowUpward, contentDescription = "Prev Change", modifier = Modifier.size(16.dp))
                                }
                                IconButton(
                                    onClick = {
                                        val totalChanges = myersSummary.changePositions.size
                                        activeChangeJumpIdx = (activeChangeJumpIdx + 1) % totalChanges
                                        val targetLineIdx = myersSummary.changePositions[activeChangeJumpIdx]
                                        scope.launch { unifiedListState.animateScrollToItem(targetLineIdx) }
                                    },
                                    modifier = Modifier.size(30.dp)
                                ) {
                                    Icon(Icons.Default.ArrowDownward, contentDescription = "Next Change", modifier = Modifier.size(16.dp))
                                }
                            }
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(6.dp))

            val surfaceColor = if (isLiquidTheme) GlassSurfaceDark else themeColorScheme.surface

            when (activeViewMode) {
                DiffViewMode.SIDE_BY_SIDE -> {
                    val linesASet = remember(activePageLinesA) { activePageLinesA.toSet() }
                    val linesBSet = remember(activePageLinesB) { activePageLinesB.toSet() }
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .weight(1f),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        DiffDocumentPane(
                            modifier = Modifier.weight(1f).fillMaxHeight(),
                            scrollState = scrollStateA,
                            surfaceColor = surfaceColor,
                            borderColor = Color(0xFFEF4444),
                            headerTitle = "Doc A (${dataDocA?.pageCount ?: 0}p)",
                            emptyMessage = "Select Original Document A",
                            isLoading = loadingA,
                            error = dataDocA?.error,
                            lines = activePageLinesA,
                            previewBitmap = previewBitmapA,
                            otherLinesSet = linesBSet,
                            diffHighlightColor = Color(0xFFEF4444),
                            onSurfaceColor = themeColorScheme.onSurface,
                            mutedColor = themeColorScheme.onSurfaceVariant
                        )

                        DiffDocumentPane(
                            modifier = Modifier.weight(1f).fillMaxHeight(),
                            scrollState = scrollStateB,
                            surfaceColor = surfaceColor,
                            borderColor = Color(0xFF10B981),
                            headerTitle = "Doc B (${dataDocB?.pageCount ?: 0}p)",
                            emptyMessage = "Select Revised Document B",
                            isLoading = loadingB,
                            error = dataDocB?.error,
                            lines = activePageLinesB,
                            previewBitmap = previewBitmapB,
                            otherLinesSet = linesASet,
                            diffHighlightColor = Color(0xFF10B981),
                            onSurfaceColor = themeColorScheme.onSurface,
                            mutedColor = themeColorScheme.onSurfaceVariant
                        )
                    }
                }

                DiffViewMode.MYERS_UNIFIED -> {
                    Surface(
                        shape = RoundedCornerShape(14.dp),
                        color = surfaceColor,
                        modifier = Modifier
                            .fillMaxWidth()
                            .weight(1f)
                    ) {
                        when {
                            loadingA || loadingB -> {
                                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                                    CircularProgressIndicator(color = accentColor)
                                }
                            }
                            dataDocA == null || dataDocB == null -> {
                                Box(Modifier.fillMaxSize().padding(20.dp), contentAlignment = Alignment.Center) {
                                    Text(
                                        text = "Select both Original Document A and Revised Document B above to compute exact Myers line-by-line differences.",
                                        color = themeColorScheme.onSurfaceVariant,
                                        fontSize = 13.sp
                                    )
                                }
                            }
                            dataDocA?.error != null || dataDocB?.error != null -> {
                                Box(Modifier.fillMaxSize().padding(20.dp), contentAlignment = Alignment.Center) {
                                    Text(
                                        text = dataDocA?.error ?: dataDocB?.error ?: "Parse error",
                                        color = Color(0xFFEF4444),
                                        fontSize = 13.sp
                                    )
                                }
                            }
                            myersSummary.lines.isEmpty() -> {
                                Box(Modifier.fillMaxSize().padding(20.dp), contentAlignment = Alignment.Center) {
                                    Text("No text lines on Page ${currentComparePage + 1}.", color = themeColorScheme.onSurfaceVariant)
                                }
                            }
                            else -> {
                                LazyColumn(
                                    state = unifiedListState,
                                    contentPadding = PaddingValues(10.dp),
                                    verticalArrangement = Arrangement.spacedBy(4.dp),
                                    modifier = Modifier.fillMaxSize()
                                ) {
                                    itemsIndexed(myersSummary.lines) { idx, line ->
                                        val bg = when (line.type) {
                                            MyersDiff.OpType.DELETE -> Color(0xFFEF4444).copy(alpha = 0.16f)
                                            MyersDiff.OpType.INSERT -> Color(0xFF10B981).copy(alpha = 0.16f)
                                            MyersDiff.OpType.EQUAL -> Color.Transparent
                                        }
                                        val fg = when (line.type) {
                                            MyersDiff.OpType.DELETE -> Color(0xFFFCA5A5)
                                            MyersDiff.OpType.INSERT -> Color(0xFF6EE7B7)
                                            MyersDiff.OpType.EQUAL -> themeColorScheme.onSurface
                                        }
                                        val prefix = when (line.type) {
                                            MyersDiff.OpType.DELETE -> "- "
                                            MyersDiff.OpType.INSERT -> "+ "
                                            MyersDiff.OpType.EQUAL -> "  "
                                        }
                                        val isCurrentJump = myersSummary.changePositions.getOrNull(activeChangeJumpIdx) == idx
                                        Row(
                                            modifier = Modifier
                                                .fillMaxWidth()
                                                .clip(RoundedCornerShape(6.dp))
                                                .background(if (isCurrentJump && line.type != MyersDiff.OpType.EQUAL) bg.copy(alpha = 0.34f) else bg)
                                                .padding(horizontal = 8.dp, vertical = 4.dp)
                                        ) {
                                            Text(
                                                text = prefix,
                                                fontFamily = FontFamily.Monospace,
                                                fontWeight = FontWeight.Bold,
                                                color = fg,
                                                fontSize = 12.sp
                                            )
                                            Text(
                                                text = line.text,
                                                fontSize = 12.sp,
                                                lineHeight = 16.sp,
                                                color = fg
                                            )
                                        }
                                    }
                                }
                            }
                        }
                    }
                }

                DiffViewMode.PIXEL_VISUAL -> {
                    Surface(
                        shape = RoundedCornerShape(14.dp),
                        color = surfaceColor,
                        modifier = Modifier
                            .fillMaxWidth()
                            .weight(1f)
                    ) {
                        val diffPair = pixelDiffOverlay
                        when {
                            isRenderingVisualDiff || loadingA || loadingB -> {
                                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                                    CircularProgressIndicator(color = accentColor)
                                }
                            }
                            diffPair == null -> {
                                Box(Modifier.fillMaxSize().padding(20.dp), contentAlignment = Alignment.Center) {
                                    Text(
                                        text = "Select both PDFs to render Page ${currentComparePage + 1} visual pixel variance overlay.",
                                        color = themeColorScheme.onSurfaceVariant,
                                        fontSize = 13.sp
                                    )
                                }
                            }
                            else -> {
                                Column(
                                    modifier = Modifier
                                        .fillMaxSize()
                                        .padding(12.dp)
                                        .verticalScroll(rememberScrollState()),
                                    verticalArrangement = Arrangement.spacedBy(10.dp)
                                ) {
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.SpaceBetween,
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Row(verticalAlignment = Alignment.CenterVertically) {
                                            Box(modifier = Modifier.size(10.dp).clip(CircleShape).background(Color(0xFFEC4899)))
                                            Spacer(modifier = Modifier.width(6.dp))
                                            Text("Magenta = Visual Pixel Difference", fontSize = 11.sp, color = Color(0xFFEC4899), fontWeight = FontWeight.SemiBold)
                                        }
                                        Text(
                                            text = "${"%.2f".format(diffPair.second)}% changed",
                                            fontSize = 12.sp,
                                            fontWeight = FontWeight.Bold,
                                            color = themeColorScheme.onSurface
                                        )
                                    }
                                    Image(
                                        bitmap = diffPair.first.asImageBitmap(),
                                        contentDescription = "Visual pixel diff page ${currentComparePage + 1}",
                                        contentScale = ContentScale.FillWidth,
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .clip(RoundedCornerShape(10.dp))
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun DiffDocumentPane(
    modifier: Modifier,
    scrollState: androidx.compose.foundation.ScrollState,
    surfaceColor: Color,
    borderColor: Color,
    headerTitle: String,
    emptyMessage: String,
    isLoading: Boolean,
    error: String?,
    lines: List<String>,
    previewBitmap: Bitmap?,
    otherLinesSet: Set<String>,
    diffHighlightColor: Color,
    onSurfaceColor: Color,
    mutedColor: Color
) {
    Surface(
        shape = RoundedCornerShape(14.dp),
        color = surfaceColor,
        border = CardDefaults.outlinedCardBorder().copy(
            brush = Brush.horizontalGradient(listOf(borderColor.copy(alpha = 0.5f), AdfBorderSubtle))
        ),
        modifier = modifier
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(10.dp)
                .verticalScroll(scrollState)
        ) {
            when {
                isLoading -> {
                    CircularProgressIndicator(
                        color = borderColor,
                        modifier = Modifier.align(Alignment.Center)
                    )
                }
                error != null -> {
                    Text(
                        text = error,
                        color = Color(0xFFEF4444),
                        fontSize = 12.sp,
                        modifier = Modifier.align(Alignment.Center)
                    )
                }
                lines.isEmpty() && previewBitmap == null -> {
                    Text(
                        text = emptyMessage,
                        color = mutedColor,
                        fontSize = 12.sp,
                        modifier = Modifier.align(Alignment.Center)
                    )
                }
                else -> {
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Surface(
                            shape = RoundedCornerShape(8.dp),
                            color = borderColor.copy(alpha = 0.15f),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text(
                                text = headerTitle,
                                color = borderColor,
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Bold,
                                modifier = Modifier.padding(8.dp)
                            )
                        }

                        previewBitmap?.let { bmp ->
                            Image(
                                bitmap = bmp.asImageBitmap(),
                                contentDescription = "$headerTitle preview",
                                contentScale = ContentScale.FillWidth,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clip(RoundedCornerShape(8.dp))
                            )
                        }

                        lines.forEach { line ->
                            val isUniqueDiff = otherLinesSet.isNotEmpty() && !otherLinesSet.contains(line)
                            Text(
                                text = line,
                                fontSize = 11.5.sp,
                                lineHeight = 16.sp,
                                fontWeight = if (isUniqueDiff) FontWeight.SemiBold else FontWeight.Normal,
                                color = if (isUniqueDiff) diffHighlightColor else onSurfaceColor,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .then(
                                        if (isUniqueDiff) Modifier
                                            .clip(RoundedCornerShape(4.dp))
                                            .background(diffHighlightColor.copy(alpha = 0.12f))
                                            .padding(horizontal = 4.dp, vertical = 2.dp)
                                        else Modifier
                                    )
                            )
                        }
                    }
                }
            }
        }
    }
}

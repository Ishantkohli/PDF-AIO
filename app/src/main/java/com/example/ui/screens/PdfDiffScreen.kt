package com.example.ui.screens

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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Description
import androidx.compose.material.icons.filled.Difference
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.data.pdf.PdfTextExtractor
import com.example.ui.PdfViewModel
import com.example.ui.theme.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream

private data class DiffDocData(
    val lines: List<String> = emptyList(),
    val pageCount: Int = 0,
    val previewBitmap: Bitmap? = null,
    val error: String? = null
)

/**
 * 100% Offline Side-by-Side Visual & Text PDF Comparison / Diff Tool.
 * Extracts actual embedded PDF text lines and renders first-page previews locally.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PdfDiffScreen(
    viewModel: PdfViewModel,
    onBack: () -> Unit
) {
    BackHandler { onBack() }

    val context = LocalContext.current
    val activeThemeStyle by ThemeManager.currentTheme.collectAsStateWithLifecycle()
    val isLiquidTheme = activeThemeStyle == AppThemeStyle.LIQUID_GLASS || activeThemeStyle == AppThemeStyle.FROSTED_CRYSTAL
    val themeColorScheme = ThemeManager.getColorScheme(activeThemeStyle)
    val accentColor = themeColorScheme.primary

    var uriDocA by remember { mutableStateOf<Uri?>(null) }
    var uriDocB by remember { mutableStateOf<Uri?>(null) }
    var nameDocA by remember { mutableStateOf("Select Original Document A") }
    var nameDocB by remember { mutableStateOf("Select Revised Document B") }

    var dataDocA by remember { mutableStateOf<DiffDocData?>(null) }
    var dataDocB by remember { mutableStateOf<DiffDocData?>(null) }
    var loadingA by remember { mutableStateOf(false) }
    var loadingB by remember { mutableStateOf(false) }

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

    suspend fun loadPdfForDiff(uri: Uri, prefix: String): DiffDocData = withContext(Dispatchers.IO) {
        val tempFile = File(context.cacheDir, "diff_${prefix}_${System.currentTimeMillis()}.pdf")
        try {
            val copied = context.contentResolver.openInputStream(uri)?.use { input ->
                FileOutputStream(tempFile).use { output -> input.copyTo(output) }
                true
            } ?: false

            if (!copied || !tempFile.exists() || tempFile.length() == 0L) {
                return@withContext DiffDocData(error = "Could not open selected PDF file.")
            }

            val pages = PdfTextExtractor.extractPages(tempFile)
            val allLines = pages.flatMap { extractedPage ->
                val rawLines = extractedPage.fullText.lines().map { it.trim() }.filter { it.isNotEmpty() }
                if (rawLines.isEmpty()) listOf("[Page ${extractedPage.pageNumber}: No extractable text / scanned image]")
                else listOf("— Page ${extractedPage.pageNumber} —") + rawLines
            }

            var pageCount = pages.size
            var thumb: Bitmap? = null
            var pfd: ParcelFileDescriptor? = null
            var renderer: PdfRenderer? = null
            try {
                pfd = ParcelFileDescriptor.open(tempFile, ParcelFileDescriptor.MODE_READ_ONLY)
                renderer = PdfRenderer(pfd)
                pageCount = renderer.pageCount
                if (renderer.pageCount > 0) {
                    renderer.openPage(0).use { page ->
                        val maxW = 480
                        val scale = maxW.toFloat() / page.width.coerceAtLeast(1)
                        val w = (page.width * scale).toInt().coerceAtLeast(1)
                        val h = (page.height * scale).toInt().coerceAtLeast(1)
                        val bmp = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
                        bmp.eraseColor(AndroidColor.WHITE)
                        page.render(bmp, null, null, PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY)
                        thumb = bmp
                    }
                }
            } catch (_: Exception) {
            } finally {
                try { renderer?.close() } catch (_: Exception) {}
                try { pfd?.close() } catch (_: Exception) {}
            }

            DiffDocData(
                lines = allLines.ifEmpty { listOf("No embedded text found in this PDF.") },
                pageCount = pageCount,
                previewBitmap = thumb
            )
        } catch (e: Exception) {
            DiffDocData(error = e.message ?: "Failed to parse PDF.")
        } finally {
            tempFile.delete()
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
        val uri = uriDocA
        if (uri != null) {
            loadingA = true
            dataDocA = loadPdfForDiff(uri, "a")
            loadingA = false
        }
    }

    LaunchedEffect(uriDocB) {
        val uri = uriDocB
        if (uri != null) {
            loadingB = true
            dataDocB = loadPdfForDiff(uri, "b")
            loadingB = false
        }
    }

    val scrollStateA = rememberScrollState()
    val scrollStateB = rememberScrollState()
    var isSyncingScroll by remember { mutableStateOf(false) }

    // Bidirectional synchronized scrolling without feedback loops
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

    val linesASet = remember(dataDocA) { dataDocA?.lines?.toSet() ?: emptySet() }
    val linesBSet = remember(dataDocB) { dataDocB?.lines?.toSet() ?: emptySet() }

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
                                text = "100% Local Offline Diff Engine",
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

            Spacer(modifier = Modifier.height(10.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(modifier = Modifier.size(10.dp).clip(CircleShape).background(Color(0xFFEF4444)))
                    Spacer(modifier = Modifier.width(4.dp))
                    Text("Only in A / Removed", fontSize = 11.sp, color = Color(0xFFEF4444))
                    Spacer(modifier = Modifier.width(12.dp))
                    Box(modifier = Modifier.size(10.dp).clip(CircleShape).background(Color(0xFF10B981)))
                    Spacer(modifier = Modifier.width(4.dp))
                    Text("Only in B / Added", fontSize = 11.sp, color = Color(0xFF10B981))
                }

                Text("Sync Scroll Active", fontSize = 11.sp, color = accentColor, fontWeight = FontWeight.Bold)
            }

            Spacer(modifier = Modifier.height(10.dp))

            val surfaceColor = if (isLiquidTheme) GlassSurfaceDark else themeColorScheme.surface

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                // Left Panel: Document A (Original)
                DiffDocumentPane(
                    modifier = Modifier.weight(1f).fillMaxHeight(),
                    scrollState = scrollStateA,
                    surfaceColor = surfaceColor,
                    borderColor = Color(0xFFEF4444),
                    headerTitle = "Document A (${dataDocA?.pageCount ?: 0}p)",
                    emptyMessage = "Select Original Document A to compare",
                    isLoading = loadingA,
                    docData = dataDocA,
                    otherLinesSet = linesBSet,
                    diffHighlightColor = Color(0xFFEF4444),
                    onSurfaceColor = themeColorScheme.onSurface,
                    mutedColor = themeColorScheme.onSurfaceVariant
                )

                // Right Panel: Document B (Revised)
                DiffDocumentPane(
                    modifier = Modifier.weight(1f).fillMaxHeight(),
                    scrollState = scrollStateB,
                    surfaceColor = surfaceColor,
                    borderColor = Color(0xFF10B981),
                    headerTitle = "Document B (${dataDocB?.pageCount ?: 0}p)",
                    emptyMessage = "Select Revised Document B to compare",
                    isLoading = loadingB,
                    docData = dataDocB,
                    otherLinesSet = linesASet,
                    diffHighlightColor = Color(0xFF10B981),
                    onSurfaceColor = themeColorScheme.onSurface,
                    mutedColor = themeColorScheme.onSurfaceVariant
                )
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
    docData: DiffDocData?,
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
                docData == null -> {
                    Text(
                        text = emptyMessage,
                        color = mutedColor,
                        fontSize = 12.sp,
                        modifier = Modifier.align(Alignment.Center)
                    )
                }
                docData.error != null -> {
                    Text(
                        text = docData.error,
                        color = Color(0xFFEF4444),
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

                        docData.previewBitmap?.let { bmp ->
                            Image(
                                bitmap = bmp.asImageBitmap(),
                                contentDescription = "$headerTitle preview",
                                contentScale = ContentScale.FillWidth,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clip(RoundedCornerShape(8.dp))
                            )
                        }

                        docData.lines.forEach { line ->
                            val isUniqueDiff = otherLinesSet.isNotEmpty() && !otherLinesSet.contains(line) && !line.startsWith("— Page ")
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

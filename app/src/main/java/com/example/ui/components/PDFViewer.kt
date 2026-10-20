package com.example.ui.components

import android.content.Context
import android.graphics.Bitmap
import android.graphics.pdf.PdfRenderer
import android.net.Uri
import android.os.ParcelFileDescriptor
import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.*
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.example.data.local.DocumentEntity
import com.example.ui.theme.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream

/**
 * State Management for PDFViewer
 *
 * Encapsulates the active document page, page counts, navigation capabilities,
 * and page-change action dispatchers.
 */
@Stable
class PdfViewerState(
    initialPage: Int = 0
) {
    /** 0-indexed current page number */
    var currentPage by mutableIntStateOf(initialPage.coerceAtLeast(0))
        internal set

    /** Total number of pages detected in the document */
    var pageCount by mutableIntStateOf(0)
        internal set

    /** Current loading state */
    var isLoading by mutableStateOf(false)
        internal set

    /** Error message if document failed to render */
    var errorMessage by mutableStateOf<String?>(null)
        internal set

    val hasPages: Boolean
        get() = pageCount > 0

    val canGoPrevious: Boolean
        get() = currentPage > 0

    val canGoNext: Boolean
        get() = pageCount > 0 && currentPage < pageCount - 1

    /** Navigates to a specific 0-indexed page with bounds clamping */
    fun goToPage(page: Int) {
        if (pageCount > 0) {
            currentPage = page.coerceIn(0, pageCount - 1)
        } else {
            currentPage = page.coerceAtLeast(0)
        }
    }

    /** Advances to the next page if available. Returns true if advanced. */
    fun nextPage(): Boolean {
        if (canGoNext) {
            currentPage++
            return true
        }
        return false
    }

    /** Reverts to the previous page if available. Returns true if reverted. */
    fun previousPage(): Boolean {
        if (canGoPrevious) {
            currentPage--
            return true
        }
        return false
    }

    /** Jumps to the first page (index 0) */
    fun firstPage() {
        currentPage = 0
    }

    /** Jumps to the final page */
    fun lastPage() {
        if (pageCount > 0) {
            currentPage = pageCount - 1
        }
    }
}

/**
 * Creates and remembers a [PdfViewerState] across recompositions.
 */
@Composable
fun rememberPdfViewerState(initialPage: Int = 0): PdfViewerState {
    return remember { PdfViewerState(initialPage) }
}

/**
 * Result representing the asynchronous page rendering state
 */
sealed interface PdfRenderResult {
    object Idle : PdfRenderResult
    object Loading : PdfRenderResult
    data class Success(
        val bitmap: Bitmap,
        val pageIndex: Int,
        val pageWidth: Int,
        val pageHeight: Int,
        val pageCount: Int
    ) : PdfRenderResult
    data class Error(val message: String) : PdfRenderResult
}

/** Legacy interface alias for backwards compatibility */
typealias PdfViewerStateResult = PdfRenderResult

/**
 * PDFViewer Component
 *
 * Renders pages of a PDF document using a basic canvas implementation backed
 * by Android's native PdfRenderer for high-performance, OEM-resilient rendering.
 *
 * Features:
 * - Robust state management ([PdfViewerState]) for multi-page navigation
 * - Next/Previous buttons and interactive page jump controls
 * - Horizontal swipe gestures for natural page turning on canvas
 * - In-memory page bitmap cache for instantaneous page switching
 * - Direct Canvas drawing (aspect ratio preservation, drop shadow, rounded paper styling)
 * - Flexible source input: File, Uri, or DocumentEntity
 * - Dynamic canvas scaling & centering with IntOffset/IntSize
 * - Custom canvas overlay support (annotations, stamps, watermarks)
 */
@Composable
fun PDFViewer(
    modifier: Modifier = Modifier,
    file: File? = null,
    uri: Uri? = null,
    document: DocumentEntity? = null,
    state: PdfViewerState = rememberPdfViewerState(),
    enablePageNavigation: Boolean = true,
    enableSwipeGestures: Boolean = true,
    scaleFactor: Float = 2.0f,
    showPageBadge: Boolean = true,
    showShadow: Boolean = true,
    backgroundColor: Color = Color.Transparent,
    pageBackgroundColor: Color = Color.White,
    borderColor: Color = AdfBorderSubtle,
    borderWidth: Dp = 1.dp,
    cornerRadius: Dp = 8.dp,
    onClick: (() -> Unit)? = null,
    onPageChanged: ((pageIndex: Int, totalPages: Int) -> Unit)? = null,
    onRenderSuccess: ((pageCount: Int, width: Int, height: Int) -> Unit)? = null,
    customCanvasOverlay: (DrawScope.(pageWidth: Float, pageHeight: Float, scale: Float) -> Unit)? = null
) {
    val context = LocalContext.current
    var renderResult by remember { mutableStateOf<PdfRenderResult>(PdfRenderResult.Idle) }

    // Page cache to store rendered bitmaps in-memory for seamless forward/back transitions
    val pageBitmapCache = remember { mutableMapOf<Int, Bitmap>() }

    // Resolve target source
    val resolvedFile = remember(file, document) {
        file ?: document?.filePath?.let { File(it) }
    }

    // Reset cache when document source changes
    LaunchedEffect(resolvedFile, uri) {
        pageBitmapCache.clear()
        state.currentPage = 0
    }

    // Render active page asynchronously whenever source, scale, or currentPage changes
    LaunchedEffect(resolvedFile, uri, state.currentPage, scaleFactor) {
        val targetPage = state.currentPage

        // 1. Check in-memory cache first for instant response
        val cachedBmp = pageBitmapCache[targetPage]
        if (cachedBmp != null && !cachedBmp.isRecycled) {
            renderResult = PdfRenderResult.Success(
                bitmap = cachedBmp,
                pageIndex = targetPage,
                pageWidth = cachedBmp.width,
                pageHeight = cachedBmp.height,
                pageCount = state.pageCount
            )
            state.isLoading = false
            state.errorMessage = null
            onPageChanged?.invoke(targetPage, state.pageCount)
            return@LaunchedEffect
        }

        renderResult = PdfRenderResult.Loading
        state.isLoading = true

        withContext(Dispatchers.IO) {
            var pfd: ParcelFileDescriptor? = null
            var tempFile: File? = null
            var renderer: PdfRenderer? = null
            var page: PdfRenderer.Page? = null

            try {
                // Obtain ParcelFileDescriptor
                if (resolvedFile != null && resolvedFile.exists() && resolvedFile.length() > 0L) {
                    pfd = ParcelFileDescriptor.open(resolvedFile, ParcelFileDescriptor.MODE_READ_ONLY)
                } else if (uri != null) {
                    pfd = try {
                        context.contentResolver.openFileDescriptor(uri, "r")
                    } catch (e: Exception) {
                        context.contentResolver.openInputStream(uri)?.use { input ->
                            val cacheFile = File(context.cacheDir, "temp_view_${System.currentTimeMillis()}.pdf")
                            FileOutputStream(cacheFile).use { output ->
                                input.copyTo(output)
                            }
                            tempFile = cacheFile
                            ParcelFileDescriptor.open(cacheFile, ParcelFileDescriptor.MODE_READ_ONLY)
                        }
                    }
                }

                if (pfd == null) {
                    withContext(Dispatchers.Main) {
                        val msg = "Document file not accessible"
                        renderResult = PdfRenderResult.Error(msg)
                        state.errorMessage = msg
                        state.isLoading = false
                    }
                    return@withContext
                }

                renderer = PdfRenderer(pfd)
                val count = renderer.pageCount
                if (count <= 0) {
                    withContext(Dispatchers.Main) {
                        val msg = "Document contains no pages"
                        renderResult = PdfRenderResult.Error(msg)
                        state.errorMessage = msg
                        state.isLoading = false
                    }
                    return@withContext
                }

                // Update total page count in state
                val clampedPage = targetPage.coerceIn(0, count - 1)

                // Render page
                page = renderer.openPage(clampedPage)
                val rawW = page.width
                val rawH = page.height

                // Cap max dimensions to safely avoid OutOfMemory on budget devices
                val maxDim = 2400
                val scaledW = (rawW * scaleFactor).toInt().coerceIn(1, maxDim)
                val scaledH = (rawH * scaleFactor).toInt().coerceIn(1, maxDim)

                val bitmap = try {
                    Bitmap.createBitmap(scaledW, scaledH, Bitmap.Config.ARGB_8888).apply {
                        eraseColor(android.graphics.Color.WHITE)
                    }
                } catch (oom: OutOfMemoryError) {
                    pageBitmapCache.clear()
                    System.gc()
                    val fallbackW = (scaledW * 0.65f).toInt().coerceAtLeast(1)
                    val fallbackH = (scaledH * 0.65f).toInt().coerceAtLeast(1)
                    Bitmap.createBitmap(fallbackW, fallbackH, Bitmap.Config.RGB_565).apply {
                        eraseColor(android.graphics.Color.WHITE)
                    }
                }

                page.render(bitmap, null, null, PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY)

                // Store in local cache (retaining up to 4 pages on mobile to keep memory light)
                if (pageBitmapCache.size > 4) {
                    val oldestKey = pageBitmapCache.keys.firstOrNull { it != clampedPage }
                    if (oldestKey != null) {
                        pageBitmapCache.remove(oldestKey)
                    }
                }
                pageBitmapCache[clampedPage] = bitmap

                withContext(Dispatchers.Main) {
                    state.pageCount = count
                    state.currentPage = clampedPage
                    state.isLoading = false
                    state.errorMessage = null

                    renderResult = PdfRenderResult.Success(
                        bitmap = bitmap,
                        pageIndex = clampedPage,
                        pageWidth = rawW,
                        pageHeight = rawH,
                        pageCount = count
                    )
                    onPageChanged?.invoke(clampedPage, count)
                    onRenderSuccess?.invoke(count, rawW, rawH)
                }
            } catch (e: Exception) {
                e.printStackTrace()
                withContext(Dispatchers.Main) {
                    val msg = e.message ?: "Failed to render document page"
                    renderResult = PdfRenderResult.Error(msg)
                    state.errorMessage = msg
                    state.isLoading = false
                }
            } finally {
                try {
                    page?.close()
                } catch (ignored: Exception) {}
                try {
                    renderer?.close()
                } catch (ignored: Exception) {}
                try {
                    pfd?.close()
                } catch (ignored: Exception) {}
                try {
                    tempFile?.delete()
                } catch (ignored: Exception) {}
            }
        }
    }

    // Outer Container with optional horizontal swipe detection
    Box(
        modifier = modifier
            .background(backgroundColor)
            .then(
                if (enableSwipeGestures && state.pageCount > 1) {
                    Modifier.pointerInput(state.currentPage, state.pageCount) {
                        var totalDrag = 0f
                        detectHorizontalDragGestures(
                            onDragStart = { totalDrag = 0f },
                            onHorizontalDrag = { change, dragAmount ->
                                change.consume()
                                totalDrag += dragAmount
                            },
                            onDragEnd = {
                                if (totalDrag < -60f && state.canGoNext) {
                                    state.nextPage()
                                } else if (totalDrag > 60f && state.canGoPrevious) {
                                    state.previousPage()
                                }
                            }
                        )
                    }
                } else Modifier
            )
            .then(
                if (onClick != null) {
                    Modifier.clickable(onClick = onClick)
                } else Modifier
            )
            .testTag("pdf_viewer_canvas_root"),
        contentAlignment = Alignment.Center
    ) {
        when (val result = renderResult) {
            is PdfRenderResult.Idle, is PdfRenderResult.Loading -> {
                PdfViewerLoadingPlaceholder(
                    pageNumber = state.currentPage + 1,
                    cornerRadius = cornerRadius
                )
            }
            is PdfRenderResult.Error -> {
                PdfViewerErrorPlaceholder(
                    message = result.message,
                    cornerRadius = cornerRadius
                )
            }
            is PdfRenderResult.Success -> {
                val bmp = result.bitmap

                // Canvas Implementation: Render current page onto Jetpack Compose Canvas
                Canvas(
                    modifier = Modifier
                        .fillMaxSize()
                        .testTag("pdf_viewer_page_canvas")
                ) {
                    val canvasWidth = size.width
                    val canvasHeight = size.height

                    if (canvasWidth <= 0f || canvasHeight <= 0f) return@Canvas

                    val bmpWidth = bmp.width.toFloat()
                    val bmpHeight = bmp.height.toFloat()

                    // Calculate aspect-ratio scale to fit completely inside canvas
                    val scale = minOf(canvasWidth / bmpWidth, canvasHeight / bmpHeight)
                    val dstW = bmpWidth * scale
                    val dstH = bmpHeight * scale

                    // Center within canvas
                    val offsetX = (canvasWidth - dstW) / 2f
                    val offsetY = (canvasHeight - dstH) / 2f

                    // 1. Draw subtle soft drop shadow behind the page
                    if (showShadow) {
                        drawRoundRect(
                            color = Color.Black.copy(alpha = 0.35f),
                            topLeft = Offset(offsetX + 4f, offsetY + 6f),
                            size = Size(dstW, dstH),
                            cornerRadius = CornerRadius(cornerRadius.toPx(), cornerRadius.toPx())
                        )
                    }

                    // 2. Draw solid paper background
                    drawRoundRect(
                        color = pageBackgroundColor,
                        topLeft = Offset(offsetX, offsetY),
                        size = Size(dstW, dstH),
                        cornerRadius = CornerRadius(cornerRadius.toPx(), cornerRadius.toPx())
                    )

                    // 3. Draw the rendered page bitmap into canvas
                    drawImage(
                        image = bmp.asImageBitmap(),
                        dstOffset = IntOffset(offsetX.toInt(), offsetY.toInt()),
                        dstSize = IntSize(dstW.toInt(), dstH.toInt())
                    )

                    // 4. Draw page border outline
                    if (borderWidth > 0.dp) {
                        drawRoundRect(
                            color = borderColor,
                            topLeft = Offset(offsetX, offsetY),
                            size = Size(dstW, dstH),
                            cornerRadius = CornerRadius(cornerRadius.toPx(), cornerRadius.toPx()),
                            style = Stroke(width = borderWidth.toPx())
                        )
                    }

                    // 5. Custom Canvas Overlay (watermarks, signature stamps, highlights)
                    customCanvasOverlay?.invoke(this, dstW, dstH, scale)
                }

                // Interactive Multi-Page Navigation Bar Overlay
                if (enablePageNavigation && state.pageCount > 1) {
                    PdfPageNavigationBar(
                        state = state,
                        modifier = Modifier
                            .align(Alignment.BottomCenter)
                            .padding(bottom = 10.dp)
                    )
                } else if (showPageBadge && state.pageCount > 0) {
                    // Simple static page indicator when navigation is not active
                    Surface(
                        shape = RoundedCornerShape(6.dp),
                        color = Color.Black.copy(alpha = 0.72f),
                        border = CardDefaults.outlinedCardBorder().copy(
                            brush = Brush.horizontalGradient(
                                listOf(AdfElectricCyan.copy(alpha = 0.6f), AdfVibrantBlue.copy(alpha = 0.6f))
                            )
                        ),
                        modifier = Modifier
                            .align(Alignment.BottomEnd)
                            .padding(8.dp)
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.padding(horizontal = 6.dp, vertical = 3.dp)
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(6.dp)
                                    .clip(CircleShape)
                                    .background(AdfElectricCyan)
                            )
                            Spacer(modifier = Modifier.width(5.dp))
                            Text(
                                text = "Page ${state.currentPage + 1} of ${state.pageCount}",
                                color = Color.White,
                                fontSize = 10.sp,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }
                }
            }
        }
    }
}

/**
 * Floating Multi-Page Navigation Bar
 * Features: Previous, Next, Page Indicator Pill, and subtle neon cyber styling
 */
@Composable
fun PdfPageNavigationBar(
    state: PdfViewerState,
    modifier: Modifier = Modifier
) {
    Surface(
        shape = RoundedCornerShape(20.dp),
        color = AdfMidnightBackground.copy(alpha = 0.88f),
        border = CardDefaults.outlinedCardBorder().copy(
            brush = Brush.horizontalGradient(
                listOf(AdfElectricCyan.copy(alpha = 0.6f), AdfVibrantBlue.copy(alpha = 0.5f))
            )
        ),
        shadowElevation = 8.dp,
        modifier = modifier.testTag("pdf_page_navigation_bar")
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.Center,
            modifier = Modifier.padding(horizontal = 6.dp, vertical = 4.dp)
        ) {
            // Previous Page Button
            IconButton(
                onClick = { state.previousPage() },
                enabled = state.canGoPrevious,
                modifier = Modifier
                    .size(32.dp)
                    .testTag("pdf_prev_page_button")
            ) {
                AdfIcon.ChevronLeft(
                    size = 18.dp,
                    color = if (state.canGoPrevious) AdfElectricCyan else AdfTextMuted.copy(alpha = 0.4f)
                )
            }

            Spacer(modifier = Modifier.width(4.dp))

            // Page Number Indicator Pill
            Surface(
                shape = RoundedCornerShape(8.dp),
                color = AdfElectricCyan.copy(alpha = 0.12f),
                border = CardDefaults.outlinedCardBorder().copy(
                    brush = Brush.horizontalGradient(
                        listOf(AdfElectricCyan.copy(alpha = 0.4f), AdfVibrantBlue.copy(alpha = 0.3f))
                    )
                ),
                modifier = Modifier.testTag("pdf_page_indicator_pill")
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp)
                ) {
                    Box(
                        modifier = Modifier
                            .size(6.dp)
                            .clip(CircleShape)
                            .background(AdfElectricCyan)
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = "${state.currentPage + 1} / ${state.pageCount}",
                        color = AdfTextPrimary,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.ExtraBold,
                        letterSpacing = 0.5.sp
                    )
                }
            }

            Spacer(modifier = Modifier.width(4.dp))

            // Next Page Button
            IconButton(
                onClick = { state.nextPage() },
                enabled = state.canGoNext,
                modifier = Modifier
                    .size(32.dp)
                    .testTag("pdf_next_page_button")
            ) {
                AdfIcon.ChevronRight(
                    size = 18.dp,
                    color = if (state.canGoNext) AdfElectricCyan else AdfTextMuted.copy(alpha = 0.4f)
                )
            }
        }
    }
}

/**
 * Alternate casing alias for Kotlin convention convenience
 */
@Composable
fun PdfViewer(
    modifier: Modifier = Modifier,
    file: File? = null,
    uri: Uri? = null,
    document: DocumentEntity? = null,
    state: PdfViewerState = rememberPdfViewerState(),
    enablePageNavigation: Boolean = true,
    enableSwipeGestures: Boolean = true,
    scaleFactor: Float = 2.0f,
    showPageBadge: Boolean = true,
    showShadow: Boolean = true,
    backgroundColor: Color = Color.Transparent,
    pageBackgroundColor: Color = Color.White,
    borderColor: Color = AdfBorderSubtle,
    borderWidth: Dp = 1.dp,
    cornerRadius: Dp = 8.dp,
    onClick: (() -> Unit)? = null,
    onPageChanged: ((pageIndex: Int, totalPages: Int) -> Unit)? = null,
    onRenderSuccess: ((pageCount: Int, width: Int, height: Int) -> Unit)? = null,
    customCanvasOverlay: (DrawScope.(pageWidth: Float, pageHeight: Float, scale: Float) -> Unit)? = null
) = PDFViewer(
    modifier = modifier,
    file = file,
    uri = uri,
    document = document,
    state = state,
    enablePageNavigation = enablePageNavigation,
    enableSwipeGestures = enableSwipeGestures,
    scaleFactor = scaleFactor,
    showPageBadge = showPageBadge,
    showShadow = showShadow,
    backgroundColor = backgroundColor,
    pageBackgroundColor = pageBackgroundColor,
    borderColor = borderColor,
    borderWidth = borderWidth,
    cornerRadius = cornerRadius,
    onClick = onClick,
    onPageChanged = onPageChanged,
    onRenderSuccess = onRenderSuccess,
    customCanvasOverlay = customCanvasOverlay
)

/**
 * Loading Placeholder with subtle cyber pulse animation
 */
@Composable
private fun PdfViewerLoadingPlaceholder(
    pageNumber: Int,
    cornerRadius: Dp
) {
    val infiniteTransition = rememberInfiniteTransition(label = "PdfViewerShimmer")
    val alpha by infiniteTransition.animateFloat(
        initialValue = 0.25f,
        targetValue = 0.65f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 800, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "PulseAlpha"
    )

    Box(
        modifier = Modifier
            .fillMaxSize()
            .clip(RoundedCornerShape(cornerRadius))
            .background(AdfSurfaceVariant.copy(alpha = alpha))
            .border(1.dp, AdfBorderSubtle, RoundedCornerShape(cornerRadius)),
        contentAlignment = Alignment.Center
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            CircularProgressIndicator(
                modifier = Modifier.size(24.dp),
                strokeWidth = 2.5.dp,
                color = AdfElectricCyan
            )
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = "Rendering Page $pageNumber...",
                color = AdfTextSecondary,
                fontSize = 11.sp,
                fontWeight = FontWeight.Medium
            )
        }
    }
}

/**
 * Error Placeholder fallback
 */
@Composable
private fun PdfViewerErrorPlaceholder(
    message: String,
    cornerRadius: Dp
) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .clip(RoundedCornerShape(cornerRadius))
            .background(AdfObsidianSurface)
            .border(1.dp, AdfCoral.copy(alpha = 0.4f), RoundedCornerShape(cornerRadius))
            .padding(12.dp),
        contentAlignment = Alignment.Center
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            AdfIcon.PdfDoc(
                size = 28.dp,
                primaryColor = AdfCoral,
                accentColor = Color.White
            )
            Spacer(modifier = Modifier.height(6.dp))
            Text(
                text = "Document Preview Unavailable",
                color = AdfTextPrimary,
                fontSize = 12.sp,
                fontWeight = FontWeight.Bold
            )
            Spacer(modifier = Modifier.height(2.dp))
            Text(
                text = message,
                color = AdfTextMuted,
                fontSize = 10.sp,
                maxLines = 2,
                textAlign = TextAlign.Center
            )
        }
    }
}

/**
 * Dedicated Multi-Page Preview Dialog using PDFViewer with full state management
 */
@Composable
fun PDFViewerDialog(
    file: File? = null,
    uri: Uri? = null,
    document: DocumentEntity? = null,
    title: String = "Document Preview",
    initialPage: Int = 0,
    onDismiss: () -> Unit,
    onOpenFullDocument: (() -> Unit)? = null
) {
    val viewerState = rememberPdfViewerState(initialPage = initialPage)

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Card(
            modifier = Modifier
                .fillMaxWidth(0.92f)
                .fillMaxHeight(0.85f)
                .clip(RoundedCornerShape(20.dp))
                .border(
                    width = 1.dp,
                    brush = Brush.horizontalGradient(
                        listOf(AdfElectricCyan.copy(alpha = 0.6f), AdfVibrantBlue.copy(alpha = 0.4f))
                    ),
                    shape = RoundedCornerShape(20.dp)
                ),
            colors = CardDefaults.cardColors(containerColor = AdfMidnightBackground)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(16.dp)
            ) {
                // Header with current page state
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(
                                text = if (viewerState.pageCount > 1) "PAGE ${viewerState.currentPage + 1} OF ${viewerState.pageCount}" else "DOCUMENT PREVIEW",
                                style = MaterialTheme.typography.labelSmall.copy(
                                    fontWeight = FontWeight.Bold,
                                    letterSpacing = 1.sp
                                ),
                                color = AdfElectricCyan
                            )
                        }
                        Text(
                            text = title,
                            style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                            color = AdfTextPrimary,
                            maxLines = 1
                        )
                    }

                    IconButton(
                        onClick = onDismiss,
                        modifier = Modifier
                            .size(34.dp)
                            .clip(CircleShape)
                            .background(AdfSurfaceCard)
                    ) {
                        AdfIcon.Close(size = 18.dp, color = AdfTextSecondary)
                    }
                }

                Spacer(modifier = Modifier.height(10.dp))

                // Canvas PDFViewer Component with state management & swipe support
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(12.dp))
                        .background(AdfObsidianSurface)
                        .border(1.dp, AdfBorderSubtle, RoundedCornerShape(12.dp))
                        .padding(8.dp),
                    contentAlignment = Alignment.Center
                ) {
                    PDFViewer(
                        file = file,
                        uri = uri,
                        document = document,
                        state = viewerState,
                        modifier = Modifier.fillMaxSize(),
                        scaleFactor = 2.5f,
                        enablePageNavigation = true,
                        enableSwipeGestures = true,
                        showPageBadge = false,
                        showShadow = true
                    )
                }

                Spacer(modifier = Modifier.height(12.dp))

                // Action Footer with Previous / Next Quick Actions
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    OutlinedButton(
                        onClick = onDismiss,
                        modifier = Modifier.weight(1f),
                        shape = RoundedCornerShape(10.dp),
                        colors = ButtonDefaults.outlinedButtonColors(contentColor = AdfTextSecondary)
                    ) {
                        Text("Close")
                    }

                    if (onOpenFullDocument != null) {
                        Button(
                            onClick = {
                                onDismiss()
                                onOpenFullDocument()
                            },
                            modifier = Modifier.weight(1.3f),
                            shape = RoundedCornerShape(10.dp),
                            colors = ButtonDefaults.buttonColors(
                                containerColor = AdfElectricCyan,
                                contentColor = AdfMidnightBackground
                            )
                        ) {
                            Text("Open in Reader", fontWeight = FontWeight.Bold)
                        }
                    }
                }
            }
        }
    }
}

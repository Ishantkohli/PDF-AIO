package com.example.ui.components

import android.graphics.Bitmap
import android.graphics.pdf.PdfRenderer
import android.os.ParcelFileDescriptor
import androidx.compose.animation.*
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.ColorMatrix
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.ui.theme.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File

/**
 * Reusable PDF Rendering Component using Android's native PdfRenderer
 * Styled with the Acrobat Crimson theme: dark background and signature red accent controls.
 */
@Composable
fun PdfRendererView(
    file: File,
    modifier: Modifier = Modifier,
    initialPage: Int = 0,
    colorFilterMode: String = "NORMAL", // NORMAL, NIGHT, SEPIA
    onPageChanged: (Int) -> Unit = {},
    overlayContent: @Composable (pageIndex: Int, pageWidth: Float, pageHeight: Float) -> Unit = { _, _, _ -> }
) {
    var pageCount by remember { mutableIntStateOf(0) }
    var activePage by remember { mutableIntStateOf(initialPage) }
    val pageBitmaps = remember { mutableStateMapOf<Int, Bitmap>() }
    var pfd by remember { mutableStateOf<ParcelFileDescriptor?>(null) }
    var pdfRenderer by remember { mutableStateOf<PdfRenderer?>(null) }

    // Viewing Mode: CONTINUOUS (Vertical Scroll) vs SINGLE_PAGE
    var isContinuousScroll by remember { mutableStateOf(false) }

    // Zoom & Pan state
    var zoomScale by remember { mutableFloatStateOf(1f) }
    var panOffsetX by remember { mutableFloatStateOf(0f) }
    var panOffsetY by remember { mutableFloatStateOf(0f) }

    val coroutineScope = rememberCoroutineScope()
    val listState = rememberLazyListState()

    // Initialize PdfRenderer lifecycle
    DisposableEffect(file) {
        try {
            val descriptor = ParcelFileDescriptor.open(file, ParcelFileDescriptor.MODE_READ_ONLY)
            val renderer = PdfRenderer(descriptor)
            pfd = descriptor
            pdfRenderer = renderer
            pageCount = renderer.pageCount
        } catch (e: Exception) {
            e.printStackTrace()
        }

        onDispose {
            try {
                pdfRenderer?.close()
                pdfRenderer = null
                pfd?.close()
                pfd = null
                pageBitmaps.clear()
            } catch (ignored: Exception) {}
        }
    }

    // Helper function to render a page to a Bitmap asynchronously
    fun renderPageBitmap(index: Int, dpiScale: Int = 2) {
        if (pageBitmaps.containsKey(index)) return
        val renderer = pdfRenderer ?: return
        if (index < 0 || index >= pageCount) return

        coroutineScope.launch(Dispatchers.IO) {
            try {
                val page = synchronized(renderer) { renderer.openPage(index) }
                val w = page.width * dpiScale
                val h = page.height * dpiScale
                val bitmap = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
                page.render(bitmap, null, null, PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY)
                page.close()
                withContext(Dispatchers.Main) {
                    pageBitmaps[index] = bitmap
                }
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }

    // Pre-render active page and adjacent pages
    LaunchedEffect(activePage, pdfRenderer) {
        if (pdfRenderer != null) {
            renderPageBitmap(activePage)
            if (activePage + 1 < pageCount) renderPageBitmap(activePage + 1)
            if (activePage - 1 >= 0) renderPageBitmap(activePage - 1)
        }
    }

    // Reading filter matrix for dark night mode
    val nightColorFilter = remember {
        val matrix = ColorMatrix(
            floatArrayOf(
                -1f, 0f, 0f, 0f, 255f,
                0f, -1f, 0f, 0f, 255f,
                0f, 0f, -1f, 0f, 255f,
                0f, 0f, 0f, 1f, 0f
            )
        )
        ColorFilter.colorMatrix(matrix)
    }

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(AcrobatDarkBackground)
            .clipToBounds()
    ) {
        if (pageCount == 0) {
            // Loading state
            Column(
                modifier = Modifier.align(Alignment.Center),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                CircularProgressIndicator(
                    color = CrimsonPrimary,
                    modifier = Modifier.size(36.dp)
                )
                Spacer(modifier = Modifier.height(12.dp))
                Text(
                    text = "Loading PDF with PdfRenderer...",
                    style = MaterialTheme.typography.bodyMedium,
                    color = AcrobatTextSecondary
                )
            }
        } else if (isContinuousScroll) {
            // CONTINUOUS VERTICAL SCROLL MODE
            LazyColumn(
                state = listState,
                modifier = Modifier
                    .fillMaxSize()
                    .padding(horizontal = 12.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp),
                contentPadding = PaddingValues(top = 16.dp, bottom = 96.dp)
            ) {
                items(pageCount) { idx ->
                    renderPageBitmap(idx)
                    val bmp = pageBitmaps[idx]

                    Card(
                        colors = CardDefaults.cardColors(containerColor = PaperWhite),
                        shape = RoundedCornerShape(8.dp),
                        elevation = CardDefaults.cardElevation(defaultElevation = 6.dp),
                        modifier = Modifier
                            .fillMaxWidth()
                            .wrapContentHeight()
                    ) {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .aspectRatio(595f / 842f), // Standard A4 ratio
                            contentAlignment = Alignment.Center
                        ) {
                            if (bmp != null) {
                                Image(
                                    bitmap = bmp.asImageBitmap(),
                                    contentDescription = "Page ${idx + 1}",
                                    colorFilter = when (colorFilterMode) {
                                        "NIGHT" -> nightColorFilter
                                        "SEPIA" -> ColorFilter.tint(Color(0xFF8D6E63), androidx.compose.ui.graphics.BlendMode.Multiply)
                                        else -> null
                                    },
                                    modifier = Modifier.fillMaxSize()
                                )
                                overlayContent(idx, 595f, 842f)
                            } else {
                                CircularProgressIndicator(color = CrimsonPrimary, modifier = Modifier.size(24.dp))
                            }

                            // Page number badge
                            Box(
                                modifier = Modifier
                                    .align(Alignment.BottomEnd)
                                    .padding(8.dp)
                                    .clip(RoundedCornerShape(4.dp))
                                    .background(Color.Black.copy(alpha = 0.65f))
                                    .padding(horizontal = 6.dp, vertical = 2.dp)
                            ) {
                                Text(
                                    text = "${idx + 1} / $pageCount",
                                    color = Color.White,
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.SemiBold
                                )
                            }
                        }
                    }
                }
            }
        } else {
            // SINGLE PAGE INTERACTIVE ZOOM/PAN MODE
            val currentBitmap = pageBitmaps[activePage]

            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .graphicsLayer(
                        scaleX = zoomScale,
                        scaleY = zoomScale,
                        translationX = panOffsetX,
                        translationY = panOffsetY
                    )
                    .pointerInput(Unit) {
                        detectTransformGestures { _, pan, zoom, _ ->
                            zoomScale = (zoomScale * zoom).coerceIn(1f, 4.5f)
                            if (zoomScale == 1f) {
                                panOffsetX = 0f
                                panOffsetY = 0f
                            } else {
                                panOffsetX += pan.x
                                panOffsetY += pan.y
                            }
                        }
                    },
                contentAlignment = Alignment.Center
            ) {
                if (currentBitmap != null) {
                    Card(
                        colors = CardDefaults.cardColors(containerColor = PaperWhite),
                        shape = RoundedCornerShape(8.dp),
                        elevation = CardDefaults.cardElevation(defaultElevation = 8.dp),
                        modifier = Modifier
                            .fillMaxWidth(0.94f)
                            .aspectRatio(595f / 842f)
                    ) {
                        Box(modifier = Modifier.fillMaxSize()) {
                            Image(
                                bitmap = currentBitmap.asImageBitmap(),
                                contentDescription = "Page ${activePage + 1}",
                                colorFilter = when (colorFilterMode) {
                                    "NIGHT" -> nightColorFilter
                                    "SEPIA" -> ColorFilter.tint(Color(0xFF8D6E63), androidx.compose.ui.graphics.BlendMode.Multiply)
                                    else -> null
                                },
                                modifier = Modifier.fillMaxSize()
                            )
                            overlayContent(activePage, 595f, 842f)
                        }
                    }
                } else {
                    CircularProgressIndicator(color = CrimsonPrimary)
                }
            }
        }

        // ACROBAT CRIMSON FLOATING CONTROLS DOCK (Dark background with Crimson Red buttons)
        Surface(
            shape = RoundedCornerShape(20.dp),
            color = AcrobatDarkSurfaceElevated,
            shadowElevation = 10.dp,
            border = CardDefaults.outlinedCardBorder().copy(brush = androidx.compose.ui.graphics.SolidColor(AcrobatDarkBorder)),
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .padding(bottom = 16.dp)
                .navigationBarsPadding()
                .shadow(8.dp, RoundedCornerShape(20.dp))
        ) {
            Row(
                modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                // Previous Page (Crimson Red Accent Button)
                IconButton(
                    onClick = {
                        if (activePage > 0) {
                            activePage--
                            onPageChanged(activePage)
                            coroutineScope.launch {
                                if (isContinuousScroll) listState.animateScrollToItem(activePage)
                            }
                        }
                    },
                    enabled = activePage > 0,
                    modifier = Modifier
                        .size(38.dp)
                        .clip(CircleShape)
                        .background(if (activePage > 0) CrimsonPrimary else AcrobatDarkSurfaceVariant)
                        .testTag("pdf_prev_page_button")
                ) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                        contentDescription = "Previous Page",
                        tint = if (activePage > 0) Color.White else AcrobatTextMuted,
                        modifier = Modifier.size(18.dp)
                    )
                }

                // Page indicator badge
                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(8.dp))
                        .background(AcrobatDarkBackground)
                        .padding(horizontal = 10.dp, vertical = 6.dp)
                ) {
                    Text(
                        text = "Page ${activePage + 1} / $pageCount",
                        style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Bold),
                        color = AcrobatTextPrimary
                    )
                }

                // Next Page (Crimson Red Accent Button)
                IconButton(
                    onClick = {
                        if (activePage < pageCount - 1) {
                            activePage++
                            onPageChanged(activePage)
                            coroutineScope.launch {
                                if (isContinuousScroll) listState.animateScrollToItem(activePage)
                            }
                        }
                    },
                    enabled = activePage < pageCount - 1,
                    modifier = Modifier
                        .size(38.dp)
                        .clip(CircleShape)
                        .background(if (activePage < pageCount - 1) CrimsonPrimary else AcrobatDarkSurfaceVariant)
                        .testTag("pdf_next_page_button")
                ) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.ArrowForward,
                        contentDescription = "Next Page",
                        tint = if (activePage < pageCount - 1) Color.White else AcrobatTextMuted,
                        modifier = Modifier.size(18.dp)
                    )
                }

                // Divider
                Box(
                    modifier = Modifier
                        .width(1.dp)
                        .height(22.dp)
                        .background(AcrobatDarkBorder)
                )

                // Scroll Mode Toggle (Single vs Continuous)
                IconButton(
                    onClick = { isContinuousScroll = !isContinuousScroll },
                    modifier = Modifier.size(36.dp)
                ) {
                    Icon(
                        imageVector = if (isContinuousScroll) Icons.Default.ViewCarousel else Icons.Default.ViewStream,
                        contentDescription = "Toggle Scroll Mode",
                        tint = if (isContinuousScroll) CrimsonAccent else AcrobatTextSecondary,
                        modifier = Modifier.size(20.dp)
                    )
                }

                // Zoom Reset Button
                if (zoomScale > 1.05f) {
                    FilledTonalButton(
                        onClick = {
                            zoomScale = 1f
                            panOffsetX = 0f
                            panOffsetY = 0f
                        },
                        colors = ButtonDefaults.filledTonalButtonColors(
                            containerColor = CrimsonPrimary.copy(alpha = 0.2f),
                            contentColor = CrimsonAccent
                        ),
                        contentPadding = PaddingValues(horizontal = 8.dp, vertical = 2.dp),
                        shape = RoundedCornerShape(8.dp),
                        modifier = Modifier.height(30.dp)
                    ) {
                        Text(
                            text = "${(zoomScale * 100).toInt()}% ↺",
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }
            }
        }
    }
}

package com.example.ui.screens

import android.content.Intent
import androidx.activity.compose.BackHandler
import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.ui.text.TextStyle
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.automirrored.filled.Help
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.ColorMatrix
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.FileProvider
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.data.local.AnnotationEntity
import com.example.data.pdf.DocumentConverterEngine
import com.example.ui.AnnotationTool
import com.example.ui.AppScreen
import com.example.ui.PdfViewModel
import com.example.ui.components.DigitalSignatureDialog
import com.example.ui.components.WatermarkCustomizerDialog
import com.example.ui.theme.*
import org.json.JSONArray
import org.json.JSONObject

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ReaderScreen(viewModel: PdfViewModel) {
    val context = LocalContext.current
    val currentDoc by viewModel.currentDocument.collectAsStateWithLifecycle()
    val currentPage by viewModel.currentPage.collectAsStateWithLifecycle()
    val totalPages by viewModel.totalPages.collectAsStateWithLifecycle()
    val pageBitmaps by viewModel.pageBitmaps.collectAsStateWithLifecycle()
    val colorFilter by viewModel.colorFilter.collectAsStateWithLifecycle()
    val activeTool by viewModel.activeTool.collectAsStateWithLifecycle()
    val activeColor by viewModel.activeColor.collectAsStateWithLifecycle()
    val activeStrokeWidth by viewModel.activeStrokeWidth.collectAsStateWithLifecycle()
    val annotations by viewModel.currentAnnotations.collectAsStateWithLifecycle()
    val signatures by viewModel.signatures.collectAsStateWithLifecycle()

    val isLiquidMode by viewModel.isLiquidMode.collectAsStateWithLifecycle()
    val isPureViewerMode by viewModel.isPureViewerMode.collectAsStateWithLifecycle()
    val ttsIsPlaying by viewModel.ttsIsPlaying.collectAsStateWithLifecycle()
    val ttsSpeed by viewModel.ttsPlaybackSpeed.collectAsStateWithLifecycle()
    val ttsSentence by viewModel.ttsCurrentSentence.collectAsStateWithLifecycle()
    val isContinuousScroll by viewModel.isContinuousScroll.collectAsStateWithLifecycle()
    val isSearchActive by viewModel.isSearchActive.collectAsStateWithLifecycle()
    val searchQuery by viewModel.searchQuery.collectAsStateWithLifecycle()
    val searchResults by viewModel.searchResults.collectAsStateWithLifecycle()
    val currentSearchMatchIndex by viewModel.currentSearchMatchIndex.collectAsStateWithLifecycle()
    val hapticFeedback = androidx.compose.ui.platform.LocalHapticFeedback.current

    var isBarsVisible by remember { mutableStateOf(true) }
    var showThumbnailsSheet by remember { mutableStateOf(false) }
    var showStickyNoteDialog by remember { mutableStateOf(false) }
    var pendingNotePosition by remember { mutableStateOf<Offset?>(null) }
    var noteInputText by remember { mutableStateOf("") }

    val activeWatermark by viewModel.activeWatermark.collectAsStateWithLifecycle()
    var showWatermarkDialog by remember { mutableStateOf(false) }

    var showTextBoxDialog by remember { mutableStateOf(false) }
    var pendingTextBoxPosition by remember { mutableStateOf<Offset?>(null) }
    var textBoxInputText by remember { mutableStateOf("") }
    var textBoxHasWhiteoutBg by remember { mutableStateOf(true) }
    var textBoxFontSize by remember { mutableFloatStateOf(14f) }

    var showWhiteoutDialog by remember { mutableStateOf(false) }
    var pendingWhiteoutPosition by remember { mutableStateOf<Offset?>(null) }
    var whiteoutReplacementText by remember { mutableStateOf("") }
    var isWhiteoutRedact by remember { mutableStateOf(false) }

    var editingAnnotation by remember { mutableStateOf<AnnotationEntity?>(null) }
    var showConvertToImagesDialog by remember { mutableStateOf(false) }
    var showConvertToWordDialog by remember { mutableStateOf(false) }

    var selectedStampText by remember { mutableStateOf("APPROVED") }
    var showSignaturePicker by remember { mutableStateOf(false) }
    var selectedSignatureJson by remember { mutableStateOf<String?>(null) }
    var showOptionsMenu by remember { mutableStateOf(false) }

    // Zoom & Pan state
    var scale by remember { mutableFloatStateOf(1f) }
    var offsetX by remember { mutableFloatStateOf(0f) }
    var offsetY by remember { mutableFloatStateOf(0f) }

    // Smooth horizontal page slide state
    var pageSlideOffset by remember { mutableFloatStateOf(0f) }
    val animatedSlideOffset by animateFloatAsState(
        targetValue = pageSlideOffset,
        animationSpec = spring(stiffness = Spring.StiffnessMediumLow),
        label = "PageSlideOffset"
    )

    // Delete page confirmation dialog states
    var showDeletePageDialog by remember { mutableStateOf(false) }
    var isDeletingPage by remember { mutableStateOf(false) }

    // Live drawing stroke buffer for active pen/highlighter
    val currentDrawingPoints = remember { mutableStateListOf<Offset>() }
    var pageSizePx by remember { mutableStateOf(IntSize(1, 1)) }

    // Liquid Mode Reflow Screen Handler
    if (isLiquidMode && currentDoc != null) {
        LiquidModeReader(
            document = currentDoc!!,
            onClose = { viewModel.toggleLiquidMode() },
            onReadAloud = { text -> viewModel.startReadAloud(text) }
        )
        return
    }

    // Back button handling
    BackHandler {
        if (activeTool != AnnotationTool.NONE) {
            viewModel.setActiveTool(AnnotationTool.NONE)
        } else {
            viewModel.navigateTo(AppScreen.HOME)
        }
    }

    // Trigger rendering of active page + pre-fetch adjacent pages for smooth 60 FPS slide transitions
    LaunchedEffect(currentPage, currentDoc, totalPages) {
        viewModel.renderPage(currentPage)
        if (currentPage + 1 < totalPages) viewModel.renderPage(currentPage + 1)
        if (currentPage - 1 >= 0) viewModel.renderPage(currentPage - 1)
    }

    val pageAnnotations = remember(annotations, currentPage) {
        annotations.filter { it.pageIndex == currentPage }
    }

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

    Scaffold(
        topBar = {
            AnimatedVisibility(
                visible = isBarsVisible,
                enter = fadeIn() + expandVertically(),
                exit = fadeOut() + shrinkVertically()
            ) {
                Column {
                    TopAppBar(
                        title = {
                            Column {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Text(
                                        text = currentDoc?.title ?: "Document",
                                        style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                                        color = AcrobatTextPrimary,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis,
                                        modifier = Modifier.weight(1f, fill = false)
                                    )
                                    if (isPureViewerMode) {
                                        Spacer(modifier = Modifier.width(6.dp))
                                        Surface(
                                            shape = RoundedCornerShape(4.dp),
                                            color = Color(0xFF10B981).copy(alpha = 0.18f)
                                        ) {
                                            Row(
                                                verticalAlignment = Alignment.CenterVertically,
                                                modifier = Modifier.padding(horizontal = 5.dp, vertical = 2.dp)
                                            ) {
                                                com.example.ui.components.AdfIcon.PureViewer(size = 11.dp, primaryColor = Color(0xFF10B981))
                                                Spacer(modifier = Modifier.width(3.dp))
                                                Text(
                                                    text = "PURE VIEWER",
                                                    color = Color(0xFF10B981),
                                                    fontSize = 8.sp,
                                                    fontWeight = FontWeight.ExtraBold
                                                )
                                            }
                                        }
                                    }
                                }
                                Text(
                                    text = "Page ${currentPage + 1} of $totalPages",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = AcrobatTextSecondary
                                )
                            }
                        },
                        navigationIcon = {
                            IconButton(
                                onClick = { viewModel.navigateTo(AppScreen.HOME) },
                                modifier = Modifier.testTag("reader_back_button")
                            ) {
                                com.example.ui.components.AdfIcon.ChevronLeft(
                                    size = 22.dp,
                                    color = AcrobatTextPrimary
                                )
                            }
                        },
                        actions = {
                            // In-Document Search Toggle
                            IconButton(
                                onClick = { viewModel.toggleSearch() },
                                modifier = Modifier.testTag("reader_search_button")
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Search,
                                    contentDescription = "Search",
                                    tint = if (isSearchActive) CrimsonPrimary else AcrobatTextPrimary
                                )
                            }

                            // Continuous Scroll vs Single Page Toggle
                            IconButton(
                                onClick = { viewModel.toggleContinuousScroll() },
                                modifier = Modifier.testTag("reader_scroll_mode_toggle")
                            ) {
                                Icon(
                                    imageVector = if (isContinuousScroll) Icons.Default.ViewAgenda else Icons.Default.ViewDay,
                                    contentDescription = "Scroll Mode",
                                    tint = if (isContinuousScroll) CrimsonAccent else AcrobatTextSecondary
                                )
                            }

                            // Page Manager (Reorder, Rotate, Delete)
                            IconButton(
                                onClick = { viewModel.navigateTo(AppScreen.PAGE_MANAGER) },
                                modifier = Modifier.testTag("page_manager_top_button")
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Layers,
                                    contentDescription = "Manage Pages",
                                    tint = AcrobatTextSecondary
                                )
                            }

                            // Digital Signature Action Button
                            Button(
                                onClick = { viewModel.showSignatureDialog(true) },
                                colors = ButtonDefaults.buttonColors(
                                    containerColor = AdfElectricCyan,
                                    contentColor = AdfMidnightBackground
                                ),
                                contentPadding = PaddingValues(horizontal = 10.dp, vertical = 6.dp),
                                shape = RoundedCornerShape(16.dp),
                                modifier = Modifier.testTag("open_digital_signature_button")
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Draw,
                                    contentDescription = "Sign Document",
                                    tint = AdfMidnightBackground,
                                    modifier = Modifier.size(15.dp)
                                )
                                Spacer(modifier = Modifier.width(5.dp))
                                Text(
                                    text = "Sign",
                                    style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
                                    color = AdfMidnightBackground
                                )
                            }

                            // More Options Overflow Menu (Light/Night theme, Liquid Mode, Read Aloud, Thumbnails, Share)
                            Box {
                                IconButton(
                                    onClick = { showOptionsMenu = true },
                                    modifier = Modifier.testTag("reader_more_menu_button")
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.MoreVert,
                                        contentDescription = "More Options",
                                        tint = AcrobatTextSecondary
                                    )
                                }

                                DropdownMenu(
                                    expanded = showOptionsMenu,
                                    onDismissRequest = { showOptionsMenu = false },
                                    modifier = Modifier.background(AcrobatDarkSurfaceElevated)
                                ) {
                                    DropdownMenuItem(
                                        text = { Text("Theme: $colorFilter", color = AcrobatTextPrimary) },
                                        leadingIcon = {
                                            Icon(
                                                imageVector = when (colorFilter) {
                                                    "NIGHT" -> Icons.Default.DarkMode
                                                    "SEPIA" -> Icons.Default.WbSunny
                                                    else -> Icons.Default.LightMode
                                                },
                                                contentDescription = null,
                                                tint = when (colorFilter) {
                                                    "NIGHT" -> Color(0xFF60A5FA)
                                                    "SEPIA" -> Color(0xFFF59E0B)
                                                    else -> Color.White
                                                }
                                            )
                                        },
                                        onClick = {
                                            showOptionsMenu = false
                                            viewModel.cycleColorFilter()
                                        }
                                    )
                                    DropdownMenuItem(
                                        text = { Text("Liquid Mode Reflow", color = AcrobatTextPrimary) },
                                        leadingIcon = { Icon(Icons.Default.WaterDrop, contentDescription = null, tint = Color(0xFF38BDF8)) },
                                        onClick = {
                                            showOptionsMenu = false
                                            viewModel.toggleLiquidMode()
                                        }
                                    )
                                    DropdownMenuItem(
                                        text = { Text(if (ttsIsPlaying) "Stop Read Aloud" else "Read Aloud (TTS)", color = AcrobatTextPrimary) },
                                        leadingIcon = { Icon(if (ttsIsPlaying) Icons.Default.VolumeUp else Icons.Default.VolumeMute, contentDescription = null, tint = CrimsonAccent) },
                                        onClick = {
                                            showOptionsMenu = false
                                            if (ttsIsPlaying) viewModel.stopReadAloud() else viewModel.startReadAloud()
                                        }
                                    )
                                    DropdownMenuItem(
                                        text = { Text("Page Thumbnails", color = AcrobatTextPrimary) },
                                        leadingIcon = { Icon(Icons.Default.GridView, contentDescription = null, tint = AcrobatTextSecondary) },
                                        onClick = {
                                            showOptionsMenu = false
                                            showThumbnailsSheet = true
                                        }
                                    )
                                    DropdownMenuItem(
                                        text = { Text("Watermark Document", color = AcrobatTextPrimary) },
                                        leadingIcon = { Icon(Icons.Default.BrandingWatermark, contentDescription = null, tint = Color(0xFFA855F7)) },
                                        onClick = {
                                            showOptionsMenu = false
                                            showWatermarkDialog = true
                                        }
                                    )
                                    DropdownMenuItem(
                                        text = { Text("Convert to Images (PNG)", color = AcrobatTextPrimary) },
                                        leadingIcon = { Icon(Icons.Default.PhotoLibrary, contentDescription = null, tint = Color(0xFF38BDF8)) },
                                        onClick = {
                                            showOptionsMenu = false
                                            val doc = currentDoc ?: return@DropdownMenuItem
                                            viewModel.convertPdfToImages(doc) { files ->
                                                if (files.isNotEmpty()) {
                                                    val uri = FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", files.first())
                                                    val intent = Intent(Intent.ACTION_VIEW).apply {
                                                        setDataAndType(uri, "image/*")
                                                        addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                                                    }
                                                    context.startActivity(Intent.createChooser(intent, "Open Converted Image"))
                                                }
                                            }
                                        }
                                    )
                                    DropdownMenuItem(
                                        text = { Text("Convert to Word (.docx)", color = AcrobatTextPrimary) },
                                        leadingIcon = { Icon(Icons.Default.Article, contentDescription = null, tint = Color(0xFF10B981)) },
                                        onClick = {
                                            showOptionsMenu = false
                                            val doc = currentDoc ?: return@DropdownMenuItem
                                            viewModel.convertPdfToWord(doc) { wordFile ->
                                                if (wordFile != null) {
                                                    val uri = FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", wordFile)
                                                    val intent = Intent(Intent.ACTION_SEND).apply {
                                                        type = "application/vnd.openxmlformats-officedocument.wordprocessingml.document"
                                                        putExtra(Intent.EXTRA_STREAM, uri)
                                                        addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                                                    }
                                                    context.startActivity(Intent.createChooser(intent, "Share Word Document"))
                                                }
                                            }
                                        }
                                    )
                                    DropdownMenuItem(
                                        text = { Text("Export & Share PDF", color = AcrobatTextPrimary) },
                                        leadingIcon = { Icon(Icons.Default.Share, contentDescription = null, tint = AcrobatTextSecondary) },
                                        onClick = {
                                            showOptionsMenu = false
                                            viewModel.exportAnnotatedDocument { exportedFile ->
                                                if (exportedFile != null) {
                                                    val uri = FileProvider.getUriForFile(
                                                        context,
                                                        "${context.packageName}.fileprovider",
                                                        exportedFile
                                                    )
                                                    val shareIntent = Intent(Intent.ACTION_SEND).apply {
                                                        type = "application/pdf"
                                                        putExtra(Intent.EXTRA_STREAM, uri)
                                                        addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                                                    }
                                                    context.startActivity(Intent.createChooser(shareIntent, "Share Document"))
                                                }
                                            }
                                        }
                                    )
                                }
                            }
                        },
                        colors = TopAppBarDefaults.topAppBarColors(
                            containerColor = AcrobatDarkBackground
                        )
                    )

                    // In-Document Search Toolbar
                    AnimatedVisibility(
                        visible = isSearchActive,
                        enter = fadeIn() + expandVertically(),
                        exit = fadeOut() + shrinkVertically()
                    ) {
                        Surface(
                            color = AcrobatDarkSurfaceElevated,
                            modifier = Modifier
                                .fillMaxWidth()
                                .border(1.dp, AcrobatDarkBorder)
                        ) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(horizontal = 12.dp, vertical = 6.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Icon(Icons.Default.Search, contentDescription = null, tint = CrimsonPrimary, modifier = Modifier.size(20.dp))
                                Spacer(modifier = Modifier.width(8.dp))
                                BasicTextField(
                                    value = searchQuery,
                                    onValueChange = { viewModel.performSearch(it) },
                                    singleLine = true,
                                    textStyle = TextStyle(color = AcrobatTextPrimary, fontSize = 14.sp),
                                    modifier = Modifier
                                        .weight(1f)
                                        .testTag("in_doc_search_input"),
                                    decorationBox = { innerTextField ->
                                        if (searchQuery.isEmpty()) {
                                            Text("Search in document...", color = AcrobatTextSecondary, fontSize = 14.sp)
                                        }
                                        innerTextField()
                                    }
                                )
                                if (searchResults.isNotEmpty()) {
                                    Text(
                                        text = "${currentSearchMatchIndex + 1}/${searchResults.size}",
                                        color = AcrobatTextSecondary,
                                        fontSize = 12.sp,
                                        fontWeight = FontWeight.Bold,
                                        modifier = Modifier.padding(horizontal = 4.dp)
                                    )
                                    IconButton(onClick = { viewModel.previousSearchMatch() }, modifier = Modifier.size(32.dp)) {
                                        Icon(Icons.Default.KeyboardArrowUp, contentDescription = "Previous Match", tint = AcrobatTextPrimary)
                                    }
                                    IconButton(onClick = { viewModel.nextSearchMatch() }, modifier = Modifier.size(32.dp)) {
                                        Icon(Icons.Default.KeyboardArrowDown, contentDescription = "Next Match", tint = AcrobatTextPrimary)
                                    }
                                } else if (searchQuery.isNotBlank()) {
                                    Text("0 matches", color = AcrobatTextSecondary, fontSize = 12.sp, modifier = Modifier.padding(horizontal = 4.dp))
                                }
                                IconButton(onClick = { viewModel.toggleSearch(false) }, modifier = Modifier.size(32.dp)) {
                                    Icon(Icons.Default.Close, contentDescription = "Close Search", tint = AcrobatTextSecondary)
                                }
                            }
                        }
                    }
                }
            }
        },
        bottomBar = {
            AnimatedVisibility(
                visible = isBarsVisible,
                enter = fadeIn() + slideInVertically { it },
                exit = fadeOut() + slideOutVertically { it }
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(AcrobatDarkBackground)
                        .navigationBarsPadding()
                ) {
                // Floating Annotation Toolbar if active and not in Pure Viewer mode
                if (!isPureViewerMode) {
                    AnnotationToolbar(
                        viewModel = viewModel,
                        activeTool = activeTool,
                        activeColor = activeColor,
                        onSelectStamp = { stamp ->
                            selectedStampText = stamp
                            viewModel.setActiveTool(AnnotationTool.STAMP)
                        },
                        onSelectSignature = {
                            viewModel.showSignatureDialog(true)
                        },
                        onOpenWatermarkDialog = {
                            showWatermarkDialog = true
                        },
                        onClearPageAnnotations = {
                            viewModel.clearPageAnnotations(currentPage)
                        },
                        onDeleteCurrentPage = {
                            showDeletePageDialog = true
                        },
                        onOrganizePages = {
                            viewModel.navigateTo(AppScreen.PAGE_MANAGER)
                        },
                        onRotatePage = {
                            val doc = currentDoc ?: return@AnnotationToolbar
                            viewModel.rotateCurrentDocumentPage(doc, currentPage)
                        },
                        modifier = Modifier.padding(bottom = 8.dp)
                    )
                }

                // Page Navigation Bar
                Surface(
                    color = AcrobatDarkSurface,
                    border = CardDefaults.outlinedCardBorder().copy(brush = androidx.compose.ui.graphics.SolidColor(AcrobatDarkBorder))
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp, vertical = 6.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        IconButton(
                            onClick = {
                                if (currentPage > 0) viewModel.setCurrentPage(currentPage - 1)
                            },
                            enabled = currentPage > 0
                        ) {
                            Icon(
                                imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                                contentDescription = "Previous Page",
                                tint = if (currentPage > 0) AcrobatTextPrimary else AcrobatTextMuted
                            )
                        }

                        // Slider for rapid page jumping
                        Slider(
                            value = (currentPage + 1).toFloat(),
                            onValueChange = { newVal ->
                                viewModel.setCurrentPage(newVal.toInt() - 1)
                            },
                            valueRange = 1f..totalPages.coerceAtLeast(1).toFloat(),
                            steps = if (totalPages > 2) totalPages - 2 else 0,
                            colors = SliderDefaults.colors(
                                thumbColor = CrimsonPrimary,
                                activeTrackColor = CrimsonPrimary,
                                inactiveTrackColor = AcrobatDarkSurfaceVariant
                            ),
                            modifier = Modifier
                                .weight(1f)
                                .padding(horizontal = 8.dp)
                        )

                        Text(
                            text = "${currentPage + 1} / $totalPages",
                            style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.SemiBold),
                            color = AcrobatTextPrimary,
                            modifier = Modifier.widthIn(min = 48.dp),
                            textAlign = TextAlign.Center
                        )

                        IconButton(
                            onClick = {
                                if (currentPage < totalPages - 1) viewModel.setCurrentPage(currentPage + 1)
                            },
                            enabled = currentPage < totalPages - 1
                        ) {
                            Icon(
                                imageVector = Icons.AutoMirrored.Filled.ArrowForward,
                                contentDescription = "Next Page",
                                tint = if (currentPage < totalPages - 1) AcrobatTextPrimary else AcrobatTextMuted
                            )
                        }
                    }
                }
            }
        }
    },
        containerColor = when (colorFilter) {
            "NIGHT" -> Color(0xFF0F0F12)
            "SEPIA" -> Color(0xFF382F24)
            else -> Color(0xFF1E1E24)
        }
    ) { padding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .clipToBounds(),
            contentAlignment = Alignment.Center
        ) {
            if (isContinuousScroll) {
                LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(16.dp),
                    verticalArrangement = Arrangement.spacedBy(16.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    items(count = totalPages) { pageIdx ->
                        val pageBmp = pageBitmaps[pageIdx]
                        LaunchedEffect(pageIdx) {
                            if (pageBmp == null) viewModel.renderPage(pageIdx)
                        }
                        Card(
                            colors = CardDefaults.cardColors(containerColor = PaperWhite),
                            shape = RoundedCornerShape(8.dp),
                            elevation = CardDefaults.cardElevation(defaultElevation = 4.dp),
                            modifier = Modifier
                                .fillMaxWidth()
                                .widthIn(max = 680.dp)
                        ) {
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .aspectRatio(0.707f)
                                    .background(PaperWhite),
                                contentAlignment = Alignment.Center
                            ) {
                                if (pageBmp != null) {
                                    Image(
                                        bitmap = pageBmp.asImageBitmap(),
                                        contentDescription = "Page ${pageIdx + 1}",
                                        colorFilter = when (colorFilter) {
                                            "NIGHT" -> nightColorFilter
                                            "SEPIA" -> ColorFilter.tint(Color(0xFF8D6E63), androidx.compose.ui.graphics.BlendMode.Multiply)
                                            else -> null
                                        },
                                        modifier = Modifier.fillMaxSize()
                                    )
                                } else {
                                    CircularProgressIndicator(color = CrimsonPrimary, modifier = Modifier.size(28.dp))
                                }
                                Surface(
                                    color = Color.Black.copy(alpha = 0.65f),
                                    shape = RoundedCornerShape(4.dp),
                                    modifier = Modifier
                                        .align(Alignment.BottomEnd)
                                        .padding(8.dp)
                                ) {
                                    Text(
                                        text = "${pageIdx + 1} / $totalPages",
                                        color = Color.White,
                                        fontSize = 11.sp,
                                        fontWeight = FontWeight.Bold,
                                        modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                                    )
                                }
                            }
                        }
                    }
                }
            } else {
                val bitmap = pageBitmaps[currentPage]

                if (bitmap != null) {
                    // Interactive Container for PDF page + Vector annotations
                    Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .onSizeChanged { pageSizePx = it }
                        .graphicsLayer(
                            scaleX = scale,
                            scaleY = scale,
                            translationX = offsetX + animatedSlideOffset,
                            translationY = offsetY
                        )
                        // Gestures: When tools are NONE, pinch zoom & pan or horizontal slide between pages
                        .pointerInput(activeTool, scale, currentPage, totalPages) {
                            if (activeTool == AnnotationTool.NONE) {
                                awaitEachGesture {
                                    val down = awaitFirstDown(requireUnconsumed = false)
                                    var totalDragX = 0f
                                    var isMultiTouch = false
                                    pageSlideOffset = 0f

                                    do {
                                        val event = awaitPointerEvent()
                                        if (event.changes.size > 1) {
                                            isMultiTouch = true
                                            val p0 = event.changes[0]
                                            val p1 = event.changes[1]
                                            val currentDist = kotlin.math.hypot(p0.position.x - p1.position.x, p0.position.y - p1.position.y)
                                            val prevDist = kotlin.math.hypot(p0.previousPosition.x - p1.previousPosition.x, p0.previousPosition.y - p1.previousPosition.y)
                                            val zoom = if (prevDist > 0f) currentDist / prevDist else 1f
                                            val pan = (p0.position - p0.previousPosition + p1.position - p1.previousPosition) / 2f
                                            scale = (scale * zoom).coerceIn(1f, 4f)
                                            if (scale > 1.05f) {
                                                offsetX += pan.x
                                                offsetY += pan.y
                                            } else {
                                                scale = 1f
                                                offsetX = 0f
                                                offsetY = 0f
                                            }
                                            event.changes.forEach { it.consume() }
                                        } else if (!isMultiTouch) {
                                            val change = event.changes.firstOrNull()
                                            if (change != null && change.pressed) {
                                                val pan = change.position - change.previousPosition
                                                if (scale > 1.05f) {
                                                    offsetX += pan.x
                                                    offsetY += pan.y
                                                    change.consume()
                                                } else {
                                                    totalDragX += pan.x
                                                    pageSlideOffset = totalDragX
                                                    if (kotlin.math.abs(totalDragX) > 12f) {
                                                        change.consume()
                                                    }
                                                }
                                            }
                                        }
                                    } while (event.changes.any { it.pressed })

                                    if (!isMultiTouch && scale <= 1.05f) {
                                        if (totalDragX < -60f && currentPage < totalPages - 1) {
                                            // Slid from right to left (finger moved left) -> Next Page (page number increases)
                                            viewModel.setCurrentPage(currentPage + 1)
                                            hapticFeedback.performHapticFeedback(androidx.compose.ui.hapticfeedback.HapticFeedbackType.TextHandleMove)
                                        } else if (totalDragX > 60f && currentPage > 0) {
                                            // Slid from left to right (finger moved right) -> Previous Page (page number decreases)
                                            viewModel.setCurrentPage(currentPage - 1)
                                            hapticFeedback.performHapticFeedback(androidx.compose.ui.hapticfeedback.HapticFeedbackType.TextHandleMove)
                                        }
                                    }
                                    pageSlideOffset = 0f
                                }
                            }
                        }
                        // Tap & Double-Tap listener for Smart Zoom, immersion toggle, notes, stamps, signatures, text box
                        .pointerInput(activeTool, selectedStampText, selectedSignatureJson) {
                            detectTapGestures(
                                onDoubleTap = { tapOffset ->
                                    if (activeTool == AnnotationTool.NONE) {
                                        if (scale > 1.2f) {
                                            scale = 1f
                                            offsetX = 0f
                                            offsetY = 0f
                                        } else {
                                            scale = 2.5f
                                            offsetX = (pageSizePx.width / 2f - tapOffset.x) * 1.5f
                                            offsetY = (pageSizePx.height / 2f - tapOffset.y) * 1.5f
                                        }
                                        hapticFeedback.performHapticFeedback(androidx.compose.ui.hapticfeedback.HapticFeedbackType.TextHandleMove)
                                    }
                                },
                                onTap = { offset ->
                                    val normX = (offset.x / pageSizePx.width).coerceIn(0f, 1f)
                                    val normY = (offset.y / pageSizePx.height).coerceIn(0f, 1f)

                                    when (activeTool) {
                                        AnnotationTool.NONE -> {
                                            isBarsVisible = !isBarsVisible
                                        }
                                        AnnotationTool.STICKY_NOTE -> {
                                            pendingNotePosition = Offset(normX, normY)
                                            noteInputText = ""
                                            showStickyNoteDialog = true
                                            hapticFeedback.performHapticFeedback(androidx.compose.ui.hapticfeedback.HapticFeedbackType.LongPress)
                                        }
                                        AnnotationTool.TEXT_BOX -> {
                                            pendingTextBoxPosition = Offset(normX, normY)
                                            textBoxInputText = ""
                                            showTextBoxDialog = true
                                            hapticFeedback.performHapticFeedback(androidx.compose.ui.hapticfeedback.HapticFeedbackType.LongPress)
                                        }
                                        AnnotationTool.WHITEOUT -> {
                                            pendingWhiteoutPosition = Offset(normX, normY)
                                            whiteoutReplacementText = ""
                                            showWhiteoutDialog = true
                                            hapticFeedback.performHapticFeedback(androidx.compose.ui.hapticfeedback.HapticFeedbackType.LongPress)
                                        }
                                        AnnotationTool.WATERMARK -> {
                                            showWatermarkDialog = true
                                        }
                                        AnnotationTool.STAMP -> {
                                            val color = when (selectedStampText) {
                                                "APPROVED" -> 0xFF10B981
                                                "CONFIDENTIAL" -> 0xFF8B5CF6
                                                "DRAFT" -> 0xFF3B82F6
                                                else -> 0xFFDC2626
                                            }
                                            viewModel.addAnnotation(
                                                pageIndex = currentPage,
                                                type = "STAMP",
                                                textContent = selectedStampText,
                                                colorHex = color,
                                                posX = normX,
                                                posY = normY
                                            )
                                            hapticFeedback.performHapticFeedback(androidx.compose.ui.hapticfeedback.HapticFeedbackType.LongPress)
                                        }
                                        AnnotationTool.SIGNATURE -> {
                                            val paths = selectedSignatureJson
                                                ?: signatures.firstOrNull()?.pathsJson
                                            if (paths != null) {
                                                viewModel.addAnnotation(
                                                    pageIndex = currentPage,
                                                    type = "SIGNATURE",
                                                    pointsJson = paths,
                                                    posX = normX,
                                                    posY = normY,
                                                    colorHex = 0xFF1E293B
                                                )
                                                hapticFeedback.performHapticFeedback(androidx.compose.ui.hapticfeedback.HapticFeedbackType.LongPress)
                                            }
                                        }
                                        else -> {}
                                    }
                                }
                            )
                        }
                        // Drag listener for Pen & Highlighter freehand drawing
                        .pointerInput(activeTool, activeColor, activeStrokeWidth) {
                            if (activeTool == AnnotationTool.PEN || activeTool == AnnotationTool.HIGHLIGHT) {
                                detectDragGestures(
                                    onDragStart = { startOffset ->
                                        currentDrawingPoints.clear()
                                        currentDrawingPoints.add(startOffset)
                                    },
                                    onDrag = { change, dragAmount ->
                                        change.consume()
                                        currentDrawingPoints.add(change.position)
                                    },
                                    onDragEnd = {
                                        if (currentDrawingPoints.isNotEmpty()) {
                                            // Convert to JSON array of normalized points
                                            val arr = JSONArray()
                                            for (pt in currentDrawingPoints) {
                                                val obj = JSONObject()
                                                obj.put("x", (pt.x / pageSizePx.width).coerceIn(0f, 1f))
                                                obj.put("y", (pt.y / pageSizePx.height).coerceIn(0f, 1f))
                                                arr.put(obj)
                                            }
                                            viewModel.addAnnotation(
                                                pageIndex = currentPage,
                                                type = if (activeTool == AnnotationTool.HIGHLIGHT) "HIGHLIGHT" else "PEN",
                                                pointsJson = arr.toString(),
                                                colorHex = activeColor,
                                                strokeWidth = activeStrokeWidth
                                            )
                                            currentDrawingPoints.clear()
                                        }
                                    },
                                    onDragCancel = {
                                        currentDrawingPoints.clear()
                                    }
                                )
                            }
                        },
                    contentAlignment = Alignment.Center
                ) {
                    // Document Bitmap Background
                    Image(
                        bitmap = bitmap.asImageBitmap(),
                        contentDescription = "PDF Page ${currentPage + 1}",
                        colorFilter = when (colorFilter) {
                            "NIGHT" -> nightColorFilter
                            "SEPIA" -> ColorFilter.tint(Color(0xFF8D6E63), androidx.compose.ui.graphics.BlendMode.Multiply)
                            else -> null
                        },
                        modifier = Modifier
                            .fillMaxSize()
                            .background(
                                when (colorFilter) {
                                    "NIGHT" -> Color(0xFF1E1E24)
                                    "SEPIA" -> PaperSepia
                                    else -> PaperWhite
                                }
                            )
                    )

                    // Canvas Vector Overlay for Saved Annotations + Live Stroke
                    Canvas(modifier = Modifier.fillMaxSize()) {
                        val canvasW = size.width
                        val canvasH = size.height

                        // 1. Draw saved annotations for this page
                        for (ann in pageAnnotations) {
                            when (ann.type) {
                                "PEN", "HIGHLIGHT" -> {
                                    if (ann.pointsJson.isNotBlank()) {
                                        try {
                                            val pts = JSONArray(ann.pointsJson)
                                            if (pts.length() > 0) {
                                                val p = Path()
                                                for (i in 0 until pts.length()) {
                                                    val obj = pts.getJSONObject(i)
                                                    val x = obj.getDouble("x").toFloat() * canvasW
                                                    val y = obj.getDouble("y").toFloat() * canvasH
                                                    if (i == 0) p.moveTo(x, y) else p.lineTo(x, y)
                                                }
                                                val strokeAlpha = if (ann.type == "HIGHLIGHT") 0.45f else 1.0f
                                                drawPath(
                                                    path = p,
                                                    color = Color(ann.colorHex).copy(alpha = strokeAlpha),
                                                    style = Stroke(
                                                        width = ann.strokeWidth,
                                                        cap = androidx.compose.ui.graphics.StrokeCap.Round,
                                                        join = androidx.compose.ui.graphics.StrokeJoin.Round
                                                    )
                                                )
                                            }
                                        } catch (ignored: Exception) {}
                                    }
                                }
                                "STICKY_NOTE" -> {
                                    val x = ann.posX * canvasW
                                    val y = ann.posY * canvasH
                                    drawCircle(
                                        color = Color(0xFFF59E0B),
                                        radius = 14f,
                                        center = Offset(x, y)
                                    )
                                }
                                "STAMP" -> {
                                    val x = ann.posX * canvasW
                                    val y = ann.posY * canvasH
                                    drawRoundRect(
                                        color = Color(ann.colorHex),
                                        topLeft = Offset(x - 55f, y - 18f),
                                        size = androidx.compose.ui.geometry.Size(110f, 36f),
                                        cornerRadius = androidx.compose.ui.geometry.CornerRadius(6f, 6f),
                                        style = Stroke(width = 2.5f)
                                    )
                                }
                                "SIGNATURE", "TYPED_SIGNATURE" -> {
                                    if (ann.textContent.isNotBlank()) {
                                        val x = ann.posX * canvasW
                                        val y = ann.posY * canvasH
                                        drawContext.canvas.nativeCanvas.apply {
                                            val sigPaint = android.graphics.Paint().apply {
                                                color = ann.colorHex.toInt()
                                                textSize = if (ann.strokeWidth > 10f) ann.strokeWidth else 26f
                                                isAntiAlias = true
                                                isFakeBoldText = true
                                                typeface = android.graphics.Typeface.create("cursive", android.graphics.Typeface.ITALIC)
                                            }
                                            drawText(ann.textContent, x, y, sigPaint)
                                            val textWidth = sigPaint.measureText(ann.textContent)
                                            val flourishPaint = android.graphics.Paint().apply {
                                                color = ann.colorHex.toInt()
                                                strokeWidth = 2.2f
                                                style = android.graphics.Paint.Style.STROKE
                                                isAntiAlias = true
                                            }
                                            val flourishPath = android.graphics.Path().apply {
                                                moveTo(x - 6f, y + 6f)
                                                quadTo(x + textWidth * 0.55f, y + 14f, x + textWidth + 14f, y + 4f)
                                            }
                                            drawPath(flourishPath, flourishPaint)
                                        }
                                    } else if (ann.pointsJson.isNotBlank()) {
                                        try {
                                            val strokes = JSONArray(ann.pointsJson)
                                            for (s in 0 until strokes.length()) {
                                                val strokeArr = strokes.getJSONArray(s)
                                                val path = Path()
                                                for (p in 0 until strokeArr.length()) {
                                                    val pt = strokeArr.getJSONObject(p)
                                                    val px = pt.getDouble("x").toFloat() * canvasW
                                                    val py = pt.getDouble("y").toFloat() * canvasH
                                                    if (p == 0) path.moveTo(px, py) else path.lineTo(px, py)
                                                }
                                                drawPath(
                                                    path = path,
                                                    color = Color(ann.colorHex),
                                                    style = Stroke(
                                                        width = 2.5f,
                                                        cap = androidx.compose.ui.graphics.StrokeCap.Round,
                                                        join = androidx.compose.ui.graphics.StrokeJoin.Round
                                                    )
                                                )
                                            }
                                        } catch (ignored: Exception) {}
                                    }
                                }
                                "WHITEOUT", "REDACT" -> {
                                    val x = ann.posX * canvasW
                                    val y = ann.posY * canvasH
                                    val w = (if (ann.width > 0f) ann.width else 0.25f) * canvasW
                                    val h = (if (ann.height > 0f) ann.height else 0.04f) * canvasH
                                    drawRect(
                                        color = if (ann.type == "REDACT") Color.Black else Color.White,
                                        topLeft = Offset(x, y),
                                        size = androidx.compose.ui.geometry.Size(w, h)
                                    )
                                }
                            }
                        }

                        // 2. Render Live Watermark if configured
                        val wm = activeWatermark
                        if (wm != null && (wm.applyToAllPages || currentPage == 0) && wm.text.isNotBlank()) {
                            rotate(-wm.rotationAngle, Offset(canvasW / 2f, canvasH / 2f)) {
                                val wmPaint = android.graphics.Paint().apply {
                                    color = wm.colorHex.toInt()
                                    alpha = (wm.opacity * 255).toInt().coerceIn(10, 240)
                                    textSize = wm.fontSizeSp * 2.2f
                                    isFakeBoldText = true
                                    isAntiAlias = true
                                    textAlign = android.graphics.Paint.Align.CENTER
                                }
                                if (wm.isTiledPattern) {
                                    val stepX = (canvasW * 0.45f).coerceAtLeast(140f)
                                    val stepY = (canvasH * 0.22f).coerceAtLeast(100f)
                                    for (wx in (-canvasW).toInt()..(canvasW * 2).toInt() step stepX.toInt()) {
                                        for (wy in (-canvasH).toInt()..(canvasH * 2).toInt() step stepY.toInt()) {
                                            drawContext.canvas.nativeCanvas.drawText(
                                                wm.text,
                                                wx.toFloat(),
                                                wy.toFloat(),
                                                wmPaint
                                            )
                                        }
                                    }
                                } else {
                                    drawContext.canvas.nativeCanvas.drawText(
                                        wm.text,
                                        canvasW / 2f,
                                        canvasH / 2f,
                                        wmPaint
                                    )
                                }
                            }
                        }

                        // 3. Draw live stroke in progress
                        if (currentDrawingPoints.size > 1) {
                            val livePath = Path()
                            currentDrawingPoints.forEachIndexed { idx, pt ->
                                if (idx == 0) livePath.moveTo(pt.x, pt.y) else livePath.lineTo(pt.x, pt.y)
                            }
                            val liveAlpha = if (activeTool == AnnotationTool.HIGHLIGHT) 0.45f else 1f
                            drawPath(
                                path = livePath,
                                color = Color(activeColor).copy(alpha = liveAlpha),
                                style = Stroke(
                                    width = activeStrokeWidth,
                                    cap = androidx.compose.ui.graphics.StrokeCap.Round,
                                    join = androidx.compose.ui.graphics.StrokeJoin.Round
                                )
                            )
                        }
                    }

                    // Render interactive overlays for Sticky Notes & Stamps so user can read them
                    Box(modifier = Modifier.fillMaxSize()) {
                        for (ann in pageAnnotations) {
                            if (ann.type == "STICKY_NOTE") {
                                var showBubble by remember { mutableStateOf(false) }
                                Box(
                                    modifier = Modifier
                                        .offset(
                                            x = (ann.posX * pageSizePx.width).dp - 14.dp,
                                            y = (ann.posY * pageSizePx.height).dp - 14.dp
                                        )
                                        .size(28.dp)
                                        .clip(CircleShape)
                                        .clickable { showBubble = !showBubble },
                                    contentAlignment = Alignment.Center
                                ) {
                                    Text("💬", fontSize = 14.sp)

                                    if (showBubble && ann.textContent.isNotBlank()) {
                                        Surface(
                                            shape = RoundedCornerShape(8.dp),
                                            color = Color(0xFFFEF3C7),
                                            shadowElevation = 6.dp,
                                            modifier = Modifier
                                                .offset(x = 24.dp, y = (-20).dp)
                                                .widthIn(max = 200.dp)
                                                .padding(6.dp)
                                        ) {
                                            Column(modifier = Modifier.padding(8.dp)) {
                                                Text(
                                                    text = ann.textContent,
                                                    style = MaterialTheme.typography.bodySmall,
                                                    color = Color(0xFF78350F)
                                                )
                                                Spacer(modifier = Modifier.height(4.dp))
                                                TextButton(
                                                    onClick = { viewModel.deleteAnnotation(ann.id) },
                                                    contentPadding = PaddingValues(0.dp)
                                                ) {
                                                    Text("Delete", color = CrimsonPrimary, fontSize = 11.sp)
                                                }
                                            }
                                        }
                                    }
                                }
                            } else if (ann.type == "STAMP") {
                                Box(
                                    modifier = Modifier
                                        .offset(
                                            x = (ann.posX * pageSizePx.width).dp - 55.dp,
                                            y = (ann.posY * pageSizePx.height).dp - 18.dp
                                        )
                                        .size(110.dp, 36.dp),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Text(
                                        text = ann.textContent,
                                        style = MaterialTheme.typography.labelSmall.copy(
                                            fontWeight = FontWeight.Bold,
                                            letterSpacing = 1.sp
                                        ),
                                        color = Color(ann.colorHex)
                                    )
                                }
                            } else if (ann.type == "TEXT_BOX") {
                                Box(
                                    modifier = Modifier
                                        .offset(
                                            x = (ann.posX * pageSizePx.width).dp,
                                            y = (ann.posY * pageSizePx.height).dp - 10.dp
                                        )
                                        .clip(RoundedCornerShape(4.dp))
                                        .background(Color.White.copy(alpha = 0.95f))
                                        .border(1.dp, Color(0xFF38BDF8), RoundedCornerShape(4.dp))
                                        .clickable { editingAnnotation = ann }
                                        .padding(horizontal = 6.dp, vertical = 2.dp)
                                ) {
                                    val fSize = if (ann.strokeWidth > 6f) ann.strokeWidth.sp else 13.sp
                                    Text(
                                        text = ann.textContent,
                                        style = MaterialTheme.typography.bodyMedium.copy(
                                            fontWeight = FontWeight.Bold,
                                            fontSize = fSize
                                        ),
                                        color = Color(ann.colorHex)
                                    )
                                }
                            } else if (ann.type == "WHITEOUT" || ann.type == "REDACT") {
                                val isRedact = ann.type == "REDACT"
                                val wDp = ((if (ann.width > 0f) ann.width else 0.25f) * pageSizePx.width).dp
                                val hDp = ((if (ann.height > 0f) ann.height else 0.04f) * pageSizePx.height).dp
                                Box(
                                    modifier = Modifier
                                        .offset(
                                            x = (ann.posX * pageSizePx.width).dp,
                                            y = (ann.posY * pageSizePx.height).dp
                                        )
                                        .size(wDp, hDp)
                                        .background(if (isRedact) Color.Black else Color.White)
                                        .border(1.dp, if (isRedact) Color.Gray else Color(0xFFCBD5E1), RoundedCornerShape(2.dp))
                                        .clickable { editingAnnotation = ann }
                                        .padding(horizontal = 4.dp),
                                    contentAlignment = Alignment.CenterStart
                                ) {
                                    if (ann.textContent.isNotBlank()) {
                                        Text(
                                            text = ann.textContent,
                                            color = if (isRedact) Color.White else Color(ann.colorHex),
                                            fontSize = 11.sp,
                                            fontWeight = FontWeight.Bold,
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis
                                        )
                                    }
                                }
                            }
                        }
                    }

                    // Floating page slide indicators (shows upcoming page number when sliding)
                    if (animatedSlideOffset < -25f && currentPage < totalPages - 1) {
                        Surface(
                            shape = RoundedCornerShape(20.dp),
                            color = CrimsonPrimary.copy(alpha = 0.95f),
                            shadowElevation = 8.dp,
                            modifier = Modifier
                                .align(Alignment.CenterEnd)
                                .padding(end = 16.dp)
                        ) {
                            Row(
                                modifier = Modifier.padding(horizontal = 14.dp, vertical = 8.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    text = "Page ${currentPage + 2}",
                                    color = Color.White,
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 13.sp
                                )
                                Spacer(modifier = Modifier.width(6.dp))
                                Icon(
                                    imageVector = Icons.AutoMirrored.Filled.ArrowForward,
                                    contentDescription = null,
                                    tint = Color.White,
                                    modifier = Modifier.size(16.dp)
                                )
                            }
                        }
                    } else if (animatedSlideOffset > 25f && currentPage > 0) {
                        Surface(
                            shape = RoundedCornerShape(20.dp),
                            color = CrimsonPrimary.copy(alpha = 0.95f),
                            shadowElevation = 8.dp,
                            modifier = Modifier
                                .align(Alignment.CenterStart)
                                .padding(start = 16.dp)
                        ) {
                            Row(
                                modifier = Modifier.padding(horizontal = 14.dp, vertical = 8.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Icon(
                                    imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                                    contentDescription = null,
                                    tint = Color.White,
                                    modifier = Modifier.size(16.dp)
                                )
                                Spacer(modifier = Modifier.width(6.dp))
                                Text(
                                    text = "Page $currentPage",
                                    color = Color.White,
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 13.sp
                                )
                            }
                        }
                    }
                }
            } else {
                // Loading spinner while page renders
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    CircularProgressIndicator(color = CrimsonPrimary)
                    Spacer(modifier = Modifier.height(12.dp))
                    Text(
                        text = "Rendering page ${currentPage + 1}...",
                        color = AcrobatTextSecondary,
                        style = MaterialTheme.typography.bodyMedium
                    )
                }
            }
        }

        // Floating Acrobat Read Out Loud TTS Player Dock
            AnimatedVisibility(
                visible = ttsIsPlaying || ttsSentence.isNotBlank(),
                enter = fadeIn() + slideInVertically { -it },
                exit = fadeOut() + slideOutVertically { -it },
                modifier = Modifier
                    .align(Alignment.TopCenter)
                    .padding(top = 16.dp, start = 16.dp, end = 16.dp)
            ) {
                Surface(
                    shape = RoundedCornerShape(16.dp),
                    color = AcrobatDarkSurfaceElevated,
                    shadowElevation = 8.dp,
                    border = CardDefaults.outlinedCardBorder().copy(brush = androidx.compose.ui.graphics.SolidColor(AcrobatDarkBorder)),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Box(
                            modifier = Modifier
                                .size(36.dp)
                                .clip(CircleShape)
                                .background(CrimsonPrimary),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = if (ttsIsPlaying) Icons.Default.GraphicEq else Icons.Default.VolumeMute,
                                contentDescription = null,
                                tint = Color.White,
                                modifier = Modifier.size(20.dp)
                            )
                        }

                        Spacer(modifier = Modifier.width(10.dp))

                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = if (ttsIsPlaying) "Acrobat Read Aloud" else "Read Aloud Paused",
                                style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Bold),
                                color = CrimsonAccent
                            )
                            Text(
                                text = ttsSentence.ifBlank { "Reading document text..." },
                                style = MaterialTheme.typography.bodySmall,
                                color = AcrobatTextPrimary,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }

                        // Speed Toggle
                        TextButton(
                            onClick = {
                                val nextSpeed = when (ttsSpeed) {
                                    1.0f -> 1.25f
                                    1.25f -> 1.5f
                                    1.5f -> 2.0f
                                    2.0f -> 0.75f
                                    else -> 1.0f
                                }
                                viewModel.setTtsSpeed(nextSpeed)
                            },
                            contentPadding = PaddingValues(horizontal = 6.dp)
                        ) {
                            Text("${ttsSpeed}x", fontWeight = FontWeight.Bold, color = AcrobatTextPrimary)
                        }

                        IconButton(
                            onClick = { viewModel.stopReadAloud() },
                            modifier = Modifier.size(32.dp)
                        ) {
                            Icon(Icons.Default.Close, contentDescription = "Stop", tint = AcrobatTextMuted)
                        }
                    }
                }
            }
        }
    }

    // Enhanced Form Text Box & Edit Text Input Dialog
    if (showTextBoxDialog) {
        var selectedColor by remember { mutableLongStateOf(activeColor) }
        AlertDialog(
            onDismissRequest = { showTextBoxDialog = false },
            title = {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Default.TextFields, contentDescription = null, tint = CrimsonAccent, modifier = Modifier.size(20.dp))
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("Add / Edit Text", color = AcrobatTextPrimary, fontWeight = FontWeight.Bold)
                }
            },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    OutlinedTextField(
                        value = textBoxInputText,
                        onValueChange = { textBoxInputText = it },
                        placeholder = { Text("Enter text (Name, Date, Form field...)") },
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedTextColor = AcrobatTextPrimary,
                            unfocusedTextColor = AcrobatTextPrimary,
                            focusedBorderColor = CrimsonPrimary,
                            unfocusedBorderColor = AcrobatDarkBorder
                        ),
                        modifier = Modifier.fillMaxWidth()
                    )

                    // Font Size Choice
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text("Size:", color = AcrobatTextSecondary, fontSize = 12.sp)
                        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                            listOf(12f to "S", 14f to "M", 18f to "L", 24f to "XL").forEach { (sz, lbl) ->
                                FilterChip(
                                    selected = textBoxFontSize == sz,
                                    onClick = { textBoxFontSize = sz },
                                    label = { Text(lbl, fontSize = 11.sp) },
                                    colors = FilterChipDefaults.filterChipColors(
                                        selectedContainerColor = CrimsonPrimary,
                                        selectedLabelColor = Color.White
                                    )
                                )
                            }
                        }
                    }

                    // Text Color Choice
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text("Color:", color = AcrobatTextSecondary, fontSize = 12.sp)
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            listOf(0xFF0F172AL to "Black", 0xFFDC2626L to "Red", 0xFF2563EBL to "Blue", 0xFF059669L to "Green").forEach { (c, _) ->
                                Box(
                                    modifier = Modifier
                                        .size(26.dp)
                                        .clip(CircleShape)
                                        .background(Color(c))
                                        .border(
                                            width = if (selectedColor == c) 2.5.dp else 1.dp,
                                            color = if (selectedColor == c) Color.White else Color.Transparent,
                                            shape = CircleShape
                                        )
                                        .clickable { selectedColor = c }
                                )
                            }
                        }
                    }

                    // Whiteout Background Toggle
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text("Opaque Whiteout Backdrop", style = MaterialTheme.typography.bodySmall, color = AcrobatTextPrimary)
                        Switch(
                            checked = textBoxHasWhiteoutBg,
                            onCheckedChange = { textBoxHasWhiteoutBg = it },
                            colors = SwitchDefaults.colors(checkedThumbColor = Color.White, checkedTrackColor = CrimsonPrimary)
                        )
                    }
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        val pos = pendingTextBoxPosition
                        if (pos != null && textBoxInputText.isNotBlank()) {
                            viewModel.addAnnotation(
                                pageIndex = currentPage,
                                type = "TEXT_BOX",
                                textContent = textBoxInputText,
                                posX = pos.x,
                                posY = pos.y,
                                width = if (textBoxHasWhiteoutBg) (textBoxInputText.length * 0.022f).coerceIn(0.12f, 0.9f) else 0f,
                                height = if (textBoxHasWhiteoutBg) 0.04f else 0f,
                                strokeWidth = textBoxFontSize,
                                colorHex = selectedColor
                            )
                        }
                        showTextBoxDialog = false
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = CrimsonPrimary)
                ) {
                    Text("Place Text")
                }
            },
            dismissButton = {
                TextButton(onClick = { showTextBoxDialog = false }) {
                    Text("Cancel", color = AcrobatTextSecondary)
                }
            },
            containerColor = AcrobatDarkSurfaceElevated
        )
    }

    // Whiteout & Content Redaction Dialog
    if (showWhiteoutDialog) {
        var whiteoutWidthChoice by remember { mutableFloatStateOf(0.35f) }
        var whiteoutHeightChoice by remember { mutableFloatStateOf(0.04f) }
        var isRedactStyle by remember { mutableStateOf(false) }

        AlertDialog(
            onDismissRequest = { showWhiteoutDialog = false },
            title = {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Default.LayersClear, contentDescription = null, tint = Color(0xFFF43F5E), modifier = Modifier.size(20.dp))
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("Whiteout / Content Erase", color = AcrobatTextPrimary, fontWeight = FontWeight.Bold)
                }
            },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Text(
                        text = "Blanks out underlying PDF text or graphics. You can also type replacement text to edit the content directly.",
                        style = MaterialTheme.typography.bodySmall,
                        color = AcrobatTextSecondary
                    )

                    OutlinedTextField(
                        value = whiteoutReplacementText,
                        onValueChange = { whiteoutReplacementText = it },
                        label = { Text("Replacement Text (Optional)") },
                        placeholder = { Text("Type new text to overlay...") },
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedTextColor = AcrobatTextPrimary,
                            unfocusedTextColor = AcrobatTextPrimary,
                            focusedBorderColor = CrimsonPrimary,
                            unfocusedBorderColor = AcrobatDarkBorder
                        ),
                        modifier = Modifier.fillMaxWidth()
                    )

                    // Width presets
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text("Area Width:", color = AcrobatTextSecondary, fontSize = 12.sp)
                        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                            listOf(0.18f to "Word", 0.45f to "Half", 0.85f to "Full Line").forEach { (w, lbl) ->
                                FilterChip(
                                    selected = whiteoutWidthChoice == w,
                                    onClick = { whiteoutWidthChoice = w },
                                    label = { Text(lbl, fontSize = 11.sp) },
                                    colors = FilterChipDefaults.filterChipColors(
                                        selectedContainerColor = CrimsonPrimary,
                                        selectedLabelColor = Color.White
                                    )
                                )
                            }
                        }
                    }

                    // Style: Whiteout (Erase) vs Black (Redact)
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text("Black Redaction Box", style = MaterialTheme.typography.bodySmall, color = AcrobatTextPrimary)
                        Switch(
                            checked = isRedactStyle,
                            onCheckedChange = { isRedactStyle = it },
                            colors = SwitchDefaults.colors(checkedThumbColor = Color.White, checkedTrackColor = Color.Black)
                        )
                    }
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        val pos = pendingWhiteoutPosition
                        if (pos != null) {
                            viewModel.addWhiteout(
                                pageIndex = currentPage,
                                posX = pos.x,
                                posY = pos.y,
                                width = whiteoutWidthChoice,
                                height = whiteoutHeightChoice,
                                replacementText = whiteoutReplacementText,
                                colorHex = if (isRedactStyle) 0xFF000000L else 0xFFFFFFFFL
                            )
                        }
                        showWhiteoutDialog = false
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = CrimsonPrimary)
                ) {
                    Text("Apply Whiteout")
                }
            },
            dismissButton = {
                TextButton(onClick = { showWhiteoutDialog = false }) {
                    Text("Cancel", color = AcrobatTextSecondary)
                }
            },
            containerColor = AcrobatDarkSurfaceElevated
        )
    }

    // Delete Current Page Confirmation Dialog
    if (showDeletePageDialog) {
        AlertDialog(
            onDismissRequest = { if (!isDeletingPage) showDeletePageDialog = false },
            containerColor = AcrobatDarkSurfaceElevated,
            icon = {
                Icon(
                    imageVector = Icons.Default.DeleteOutline,
                    contentDescription = null,
                    tint = Color(0xFFEF4444),
                    modifier = Modifier.size(32.dp)
                )
            },
            title = {
                Text(
                    text = "Delete Page ${currentPage + 1}?",
                    color = AcrobatTextPrimary,
                    fontWeight = FontWeight.Bold
                )
            },
            text = {
                Text(
                    text = if (totalPages <= 1) {
                        "This is the only page in the document. You cannot delete the last remaining page."
                    } else {
                        "Are you sure you want to permanently delete Page ${currentPage + 1} of $totalPages from this PDF document? A clean copy without this page will be saved."
                    },
                    color = AcrobatTextSecondary,
                    fontSize = 13.sp
                )
            },
            confirmButton = {
                if (totalPages > 1) {
                    Button(
                        onClick = {
                            val doc = currentDoc ?: return@Button
                            isDeletingPage = true
                            viewModel.deleteCurrentPage(doc, currentPage) {
                                isDeletingPage = false
                                showDeletePageDialog = false
                            }
                        },
                        enabled = !isDeletingPage,
                        colors = ButtonDefaults.buttonColors(
                            containerColor = Color(0xFFEF4444),
                            contentColor = Color.White
                        )
                    ) {
                        if (isDeletingPage) {
                            CircularProgressIndicator(color = Color.White, modifier = Modifier.size(16.dp))
                        } else {
                            Text("Delete Page", fontWeight = FontWeight.Bold)
                        }
                    }
                }
            },
            dismissButton = {
                TextButton(
                    onClick = { showDeletePageDialog = false },
                    enabled = !isDeletingPage
                ) {
                    Text(if (totalPages <= 1) "Close" else "Cancel", color = AcrobatTextSecondary)
                }
            }
        )
    }

    // Edit Existing Annotation Dialog
    editingAnnotation?.let { targetAnn ->
        var editText by remember { mutableStateOf(targetAnn.textContent) }
        AlertDialog(
            onDismissRequest = { editingAnnotation = null },
            title = { Text("Edit ${targetAnn.type.replace('_', ' ')}", color = AcrobatTextPrimary, fontWeight = FontWeight.Bold) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    OutlinedTextField(
                        value = editText,
                        onValueChange = { editText = it },
                        label = { Text("Content") },
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedTextColor = AcrobatTextPrimary,
                            unfocusedTextColor = AcrobatTextPrimary,
                            focusedBorderColor = CrimsonPrimary
                        ),
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        viewModel.updateAnnotation(targetAnn.copy(textContent = editText))
                        editingAnnotation = null
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = CrimsonPrimary)
                ) {
                    Text("Save Changes")
                }
            },
            dismissButton = {
                Row {
                    TextButton(onClick = {
                        viewModel.deleteAnnotation(targetAnn.id)
                        editingAnnotation = null
                    }) {
                        Text("Delete", color = Color(0xFFEF4444))
                    }
                    TextButton(onClick = { editingAnnotation = null }) {
                        Text("Cancel", color = AcrobatTextSecondary)
                    }
                }
            },
            containerColor = AcrobatDarkSurfaceElevated
        )
    }

    // Custom Watermark Studio Dialog
    if (showWatermarkDialog) {
        WatermarkCustomizerDialog(
            initialOptions = activeWatermark,
            onDismiss = { showWatermarkDialog = false },
            onApply = { options ->
                viewModel.setWatermarkOptions(options)
            },
            onRemove = {
                viewModel.setWatermarkOptions(null)
            }
        )
    }

    // Sticky Note Input Dialog
    if (showStickyNoteDialog) {
        AlertDialog(
            onDismissRequest = { showStickyNoteDialog = false },
            title = { Text("Add Sticky Note", color = AcrobatTextPrimary) },
            text = {
                OutlinedTextField(
                    value = noteInputText,
                    onValueChange = { noteInputText = it },
                    placeholder = { Text("Enter comment or note...") },
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedTextColor = AcrobatTextPrimary,
                        unfocusedTextColor = AcrobatTextPrimary,
                        focusedBorderColor = CrimsonPrimary
                    ),
                    modifier = Modifier.fillMaxWidth()
                )
            },
            confirmButton = {
                Button(
                    onClick = {
                        val pos = pendingNotePosition
                        if (pos != null && noteInputText.isNotBlank()) {
                            viewModel.addAnnotation(
                                pageIndex = currentPage,
                                type = "STICKY_NOTE",
                                textContent = noteInputText,
                                posX = pos.x,
                                posY = pos.y
                            )
                        }
                        showStickyNoteDialog = false
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = CrimsonPrimary)
                ) {
                    Text("Attach Note")
                }
            },
            dismissButton = {
                TextButton(onClick = { showStickyNoteDialog = false }) {
                    Text("Cancel", color = AcrobatTextSecondary)
                }
            },
            containerColor = AcrobatDarkSurfaceElevated
        )
    }

    // Signature Placement Picker Dialog
    if (showSignaturePicker) {
        AlertDialog(
            onDismissRequest = { showSignaturePicker = false },
            title = { Text("Select Signature", color = AcrobatTextPrimary) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text(
                        text = "Choose a signature to place on the page, then tap anywhere on the document:",
                        style = MaterialTheme.typography.bodySmall,
                        color = AcrobatTextSecondary
                    )
                    signatures.forEach { sig ->
                        Card(
                            onClick = {
                                selectedSignatureJson = sig.pathsJson
                                showSignaturePicker = false
                            },
                            colors = CardDefaults.cardColors(containerColor = AcrobatDarkSurface),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(12.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Icon(Icons.Default.Draw, contentDescription = null, tint = CrimsonPrimary)
                                Spacer(modifier = Modifier.width(10.dp))
                                Text(
                                    text = sig.title,
                                    color = AcrobatTextPrimary,
                                    style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.SemiBold)
                                )
                            }
                        }
                    }
                }
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        showSignaturePicker = false
                        viewModel.navigateTo(AppScreen.SIGNATURE_VAULT)
                    }
                ) {
                    Text("+ Create New", color = CrimsonPrimary)
                }
            },
            dismissButton = {
                TextButton(onClick = { showSignaturePicker = false }) {
                    Text("Cancel", color = AcrobatTextSecondary)
                }
            },
            containerColor = AcrobatDarkSurfaceElevated
        )
    }

    // Thumbnails Grid Sheet for jumping across pages
    if (showThumbnailsSheet) {
        ModalBottomSheet(
            onDismissRequest = { showThumbnailsSheet = false },
            containerColor = AcrobatDarkSurfaceElevated
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp)
            ) {
                Text(
                    text = "Document Pages ($totalPages)",
                    style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                    color = AcrobatTextPrimary,
                    modifier = Modifier.padding(bottom = 12.dp)
                )

                LazyVerticalGrid(
                    columns = GridCells.Fixed(3),
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(max = 420.dp)
                ) {
                    items(totalPages) { pageIdx ->
                        val isCurrent = pageIdx == currentPage
                        val thumbBitmap = pageBitmaps[pageIdx]

                        Card(
                            onClick = {
                                viewModel.setCurrentPage(pageIdx)
                                showThumbnailsSheet = false
                            },
                            colors = CardDefaults.cardColors(containerColor = AcrobatDarkSurface),
                            border = if (isCurrent) {
                                CardDefaults.outlinedCardBorder().copy(brush = androidx.compose.ui.graphics.SolidColor(CrimsonPrimary), width = 2.dp)
                            } else null,
                            shape = RoundedCornerShape(8.dp),
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(130.dp)
                        ) {
                            Box(
                                modifier = Modifier.fillMaxSize(),
                                contentAlignment = Alignment.Center
                            ) {
                                if (thumbBitmap != null) {
                                    Image(
                                        bitmap = thumbBitmap.asImageBitmap(),
                                        contentDescription = "Page ${pageIdx + 1}",
                                        modifier = Modifier.fillMaxSize()
                                    )
                                } else {
                                    LaunchedEffect(pageIdx) { viewModel.renderPage(pageIdx) }
                                    Icon(Icons.Default.Description, contentDescription = null, tint = AcrobatTextMuted)
                                }
                                Box(
                                    modifier = Modifier
                                        .align(Alignment.BottomCenter)
                                        .fillMaxWidth()
                                        .background(Color.Black.copy(alpha = 0.6f))
                                        .padding(vertical = 2.dp),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Text(
                                        text = "Page ${pageIdx + 1}",
                                        style = MaterialTheme.typography.labelSmall,
                                        color = if (isCurrent) CrimsonAccent else Color.White
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    // Digital Signature Studio Modal
    val isSignatureDialogOpen by viewModel.isSignatureDialogVisible.collectAsStateWithLifecycle()
    if (isSignatureDialogOpen) {
        DigitalSignatureDialog(
            viewModel = viewModel,
            activeDocumentTitle = currentDoc?.title,
            currentPageIndex = currentPage,
            totalPages = totalPages,
            savedSignatures = signatures,
            onDismiss = { viewModel.showSignatureDialog(false) }
        )
    }
}

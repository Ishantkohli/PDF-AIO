package com.example.ui.screens

import android.app.Activity
import android.content.ClipData
import android.content.Intent
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.ui.text.AnnotatedString
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
import androidx.compose.ui.graphics.Brush
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
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.FileProvider
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.data.local.AnnotationEntity
import com.example.data.local.StorageRenameCoordinator
import com.example.data.pdf.DocumentConverterEngine
import com.example.data.pdf.PdfRedactionEngine
import com.example.data.pdf.PdfTextLayoutEngine
import com.example.data.pdf.ShareCoordinator
import com.example.ui.AnnotationTool
import com.example.ui.AppScreen
import com.example.ui.PdfViewModel
import com.example.ui.components.DigitalSignatureDialog
import com.example.ui.components.FormFieldType
import com.example.ui.components.FormFillOverlay
import com.example.ui.components.WatermarkCustomizerDialog
import com.example.ui.screens.reader.BookmarksBottomSheet
import com.example.ui.screens.reader.ProviderDeniedSaveCopyDialog
import com.example.ui.screens.reader.RenameDocumentDialog
import com.example.ui.screens.reader.SelectionFloatingActionBar
import com.example.ui.screens.reader.ShareOptionsBottomSheet
import com.example.ui.screens.reader.computeFittedPageRect
import com.example.ui.screens.reader.pointerToFittedNormalized
import com.example.ui.theme.*
import org.json.JSONArray
import org.json.JSONObject
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt

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
    val activeOpacity by viewModel.activeOpacity.collectAsStateWithLifecycle()
    val annotations by viewModel.currentAnnotations.collectAsStateWithLifecycle()
    val signatures by viewModel.signatures.collectAsStateWithLifecycle()
    val currentPageTextLayout by viewModel.currentPageTextLayout.collectAsStateWithLifecycle()
    val currentBookmarks by viewModel.currentBookmarks.collectAsStateWithLifecycle()

    val isLiquidMode by viewModel.isLiquidMode.collectAsStateWithLifecycle()
    val isPureViewerMode by viewModel.isPureViewerMode.collectAsStateWithLifecycle()
    val ttsIsPlaying by viewModel.ttsIsPlaying.collectAsStateWithLifecycle()
    val ttsSpeed by viewModel.ttsPlaybackSpeed.collectAsStateWithLifecycle()
    val ttsSentence by viewModel.ttsCurrentSentence.collectAsStateWithLifecycle()
    val isContinuousScroll by viewModel.isContinuousScroll.collectAsStateWithLifecycle()
    val is4KUltraClarityEnabled by viewModel.is4KUltraClarityEnabled.collectAsStateWithLifecycle()
    val is4KActive by viewModel.is4KActive.collectAsStateWithLifecycle()
    val isSearchActive by viewModel.isSearchActive.collectAsStateWithLifecycle()
    val searchQuery by viewModel.searchQuery.collectAsStateWithLifecycle()
    val searchResults by viewModel.searchResults.collectAsStateWithLifecycle()
    val currentSearchMatchIndex by viewModel.currentSearchMatchIndex.collectAsStateWithLifecycle()
    val hapticFeedback = androidx.compose.ui.platform.LocalHapticFeedback.current
    val clipboardManager = LocalClipboardManager.current
    val density = LocalDensity.current

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

    var selectedStampText by remember { mutableStateOf("APPROVED") }
    var showSignaturePicker by remember { mutableStateOf(false) }
    var selectedSignatureJson by remember { mutableStateOf<String?>(null) }
    var selectedSignatureAnnId by remember { mutableStateOf<Long?>(null) }
    val signatureDragOffsets = remember { mutableStateMapOf<Long, Offset>() }
    var showOptionsMenu by remember { mutableStateOf(false) }

    // Clickable Header Title Menu + Real Storage Rename + Save Copy As fallback
    var showTitleMenu by remember { mutableStateOf(false) }
    var showRenameDialog by remember { mutableStateOf(false) }
    var isRenamingFile by remember { mutableStateOf(false) }
    var renameErrorText by remember { mutableStateOf<String?>(null) }
    var providerDeniedInfo by remember { mutableStateOf<StorageRenameCoordinator.RenameResult.ProviderDenied?>(null) }
    val saveCopyLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.CreateDocument("application/pdf")
    ) { destUri ->
        if (destUri != null) {
            viewModel.saveRenamedCopyToUri(destUri) { _ ->
                providerDeniedInfo = null
            }
        }
    }

    // 4-Option Share Sheet & Bookmarks Sheet
    var showShareSheet by remember { mutableStateOf(false) }
    var isPreparingShare by remember { mutableStateOf(false) }
    var showBookmarksSheet by remember { mutableStateOf(false) }

    // Permanent Redaction Sanitization States
    val redactCount = remember(annotations) { annotations.count { it.type == "REDACT" } }
    var showApplyRedactionDialog by remember { mutableStateOf(false) }
    var isApplyingRedaction by remember { mutableStateOf(false) }
    var redactionResultInfo by remember { mutableStateOf<PdfRedactionEngine.RedactionResult?>(null) }

    // Text Selection State (normalized glyph indices inside currentPageTextLayout)
    var selectionRange by remember(currentDoc?.id, currentPage) { mutableStateOf<IntRange?>(null) }
    var selectionAnchorIdx by remember(currentDoc?.id, currentPage) { mutableIntStateOf(0) }
    var selectionExpandStage by remember(currentDoc?.id, currentPage) { mutableIntStateOf(0) }
    var selectionUnavailableNotice by remember(currentDoc?.id, currentPage) { mutableStateOf<String?>(null) }

    val activeThemeStyle by ThemeManager.currentTheme.collectAsStateWithLifecycle()
    val themeColorScheme = ThemeManager.getColorScheme(activeThemeStyle)
    val themePrimary = themeColorScheme.primary
    val isLiquidTheme = activeThemeStyle == AppThemeStyle.LIQUID_GLASS || activeThemeStyle == AppThemeStyle.FROSTED_CRYSTAL

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

    // Live drawing stroke buffer for active pen/highlighter/underline/strike
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
        when {
            selectionRange != null -> selectionRange = null
            activeTool != AnnotationTool.NONE -> viewModel.setActiveTool(AnnotationTool.NONE)
            else -> viewModel.navigateTo(AppScreen.HOME)
        }
    }

    LaunchedEffect(activeTool, currentPage) {
        if (activeTool == AnnotationTool.TEXT_SELECT) {
            viewModel.loadPageTextLayout(currentPage)
        }
        if (activeTool != AnnotationTool.NONE && activeTool != AnnotationTool.TEXT_SELECT) {
            selectionRange = null
            selectionUnavailableNotice = null
        }
    }

    // Trigger rendering of active page + pre-fetch adjacent pages for smooth slide transitions
    LaunchedEffect(currentPage, currentDoc, totalPages) {
        scale = 1f
        offsetX = 0f
        offsetY = 0f
        selectedSignatureAnnId = null
        signatureDragOffsets.clear()
        selectionRange = null
        selectionUnavailableNotice = null
        viewModel.renderPage(currentPage)
        viewModel.loadPageTextLayout(currentPage)
        if (currentPage + 1 < totalPages) viewModel.renderPage(currentPage + 1)
        if (currentPage - 1 >= 0) viewModel.renderPage(currentPage - 1)
    }

    // Auto-4K Ultra-Clarity Engine: Re-render page at super high DPI when user zooms in
    LaunchedEffect(scale, currentPage) {
        if (scale > 1.25f && is4KUltraClarityEnabled) {
            viewModel.renderPageHighRes(currentPage, scale)
        }
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
                            Box {
                                Column(
                                    modifier = Modifier
                                        .clip(RoundedCornerShape(8.dp))
                                        .clickable { showTitleMenu = true }
                                        .padding(horizontal = 4.dp, vertical = 2.dp)
                                        .testTag("reader_header_title_button")
                                ) {
                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        Text(
                                            text = currentDoc?.title ?: "Document",
                                            style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                                            color = AcrobatTextPrimary,
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis,
                                            modifier = Modifier.weight(1f, fill = false)
                                        )
                                        Icon(
                                            imageVector = Icons.Default.ArrowDropDown,
                                            contentDescription = "Document Filename Actions",
                                            tint = AcrobatTextSecondary,
                                            modifier = Modifier.size(18.dp)
                                        )
                                        if (isPureViewerMode) {
                                            Spacer(modifier = Modifier.width(4.dp))
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
                                                        text = "PURE",
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

                                DropdownMenu(
                                    expanded = showTitleMenu,
                                    onDismissRequest = { showTitleMenu = false },
                                    modifier = Modifier.background(AcrobatDarkSurfaceElevated)
                                ) {
                                    DropdownMenuItem(
                                        text = { Text("Rename File", color = AcrobatTextPrimary, fontWeight = FontWeight.Medium) },
                                        leadingIcon = {
                                            Icon(Icons.Default.DriveFileRenameOutline, contentDescription = null, tint = AdfElectricCyan)
                                        },
                                        modifier = Modifier.testTag("menu_rename_file"),
                                        onClick = {
                                            showTitleMenu = false
                                            renameErrorText = null
                                            showRenameDialog = true
                                        }
                                    )
                                    DropdownMenuItem(
                                        text = { Text("Close", color = AcrobatTextSecondary) },
                                        leadingIcon = {
                                            Icon(Icons.Default.Close, contentDescription = null, tint = AcrobatTextSecondary)
                                        },
                                        modifier = Modifier.testTag("menu_close_rename"),
                                        onClick = {
                                            showTitleMenu = false
                                        }
                                    )
                                }
                            }
                        },
                        navigationIcon = {
                            Box(modifier = Modifier.padding(start = 8.dp, end = 4.dp)) {
                                CrystallineGlossyBackButton(
                                    onClick = { viewModel.navigateTo(AppScreen.HOME) },
                                    isLiquidTheme = isLiquidTheme,
                                    primaryColor = themePrimary
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

                            // 4-Option Share Sheet Button
                            IconButton(
                                onClick = { showShareSheet = true },
                                modifier = Modifier.testTag("reader_share_button")
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Share,
                                    contentDescription = "Share Document",
                                    tint = AdfElectricCyan
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

                            // More Options Overflow Menu
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
                                        text = {
                                            Text(
                                                text = if (isContinuousScroll) "Single Page Mode" else "Continuous Scroll",
                                                color = AcrobatTextPrimary
                                            )
                                        },
                                        leadingIcon = {
                                            Icon(
                                                imageVector = if (isContinuousScroll) Icons.Default.ViewDay else Icons.Default.ViewAgenda,
                                                contentDescription = null,
                                                tint = AcrobatTextSecondary
                                            )
                                        },
                                        onClick = {
                                            showOptionsMenu = false
                                            viewModel.toggleContinuousScroll()
                                        }
                                    )
                                    DropdownMenuItem(
                                        text = { Text("Organize Pages", color = AcrobatTextPrimary) },
                                        leadingIcon = { Icon(Icons.Default.Layers, contentDescription = null, tint = AcrobatTextSecondary) },
                                        onClick = {
                                            showOptionsMenu = false
                                            viewModel.navigateTo(AppScreen.PAGE_MANAGER)
                                        }
                                    )
                                    DropdownMenuItem(
                                        text = { Text("Bookmarks (${currentBookmarks.size})", color = AcrobatTextPrimary) },
                                        leadingIcon = { Icon(Icons.Default.Bookmarks, contentDescription = null, tint = Color(0xFFF59E0B)) },
                                        modifier = Modifier.testTag("reader_bookmarks_button"),
                                        onClick = {
                                            showOptionsMenu = false
                                            showBookmarksSheet = true
                                        }
                                    )
                                    DropdownMenuItem(
                                        text = {
                                            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
                                                Text("4K Auto-Ultra Clarity", color = AcrobatTextPrimary, modifier = Modifier.weight(1f))
                                                if (is4KUltraClarityEnabled) {
                                                    Text("ON", color = Color(0xFF10B981), fontSize = 11.sp, fontWeight = FontWeight.ExtraBold)
                                                } else {
                                                    Text("OFF", color = AcrobatTextMuted, fontSize = 11.sp, fontWeight = FontWeight.Normal)
                                                }
                                            }
                                        },
                                        leadingIcon = { Icon(Icons.Default.AutoAwesome, contentDescription = null, tint = Color(0xFF10B981)) },
                                        onClick = {
                                            showOptionsMenu = false
                                            viewModel.toggle4KUltraClarityEngine()
                                        }
                                    )
                                    DropdownMenuItem(
                                        text = { Text("Reflow Reader Mode", color = AcrobatTextPrimary) },
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
                                        text = { Text("Share / Export (PDF, TXT, DOCX)...", color = AcrobatTextPrimary, fontWeight = FontWeight.SemiBold) },
                                        leadingIcon = { Icon(Icons.Default.Share, contentDescription = null, tint = AdfElectricCyan) },
                                        onClick = {
                                            showOptionsMenu = false
                                            showShareSheet = true
                                        }
                                    )
                                    if (redactCount > 0) {
                                        DropdownMenuItem(
                                            text = { Text("Apply Permanent Redaction ($redactCount)", color = Color(0xFFF43F5E), fontWeight = FontWeight.Bold) },
                                            leadingIcon = { Icon(Icons.Default.Security, contentDescription = null, tint = Color(0xFFF43F5E)) },
                                            modifier = Modifier.testTag("menu_apply_redaction"),
                                            onClick = {
                                                showOptionsMenu = false
                                                showApplyRedactionDialog = true
                                            }
                                        )
                                    }
                                }
                            }
                        },
                        colors = TopAppBarDefaults.topAppBarColors(
                            containerColor = if (isLiquidTheme) Color.Transparent else AcrobatDarkBackground
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
                    color = if (isLiquidTheme) GlassSurfaceDark else themeColorScheme.surface,
                    border = CardDefaults.outlinedCardBorder().copy(
                        brush = if (isLiquidTheme) LiquidGlassBorderGradient else androidx.compose.ui.graphics.SolidColor(AcrobatDarkBorder)
                    )
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
                                tint = if (currentPage > 0) themePrimary else AcrobatTextMuted
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
                                thumbColor = themePrimary,
                                activeTrackColor = themePrimary,
                                inactiveTrackColor = themePrimary.copy(alpha = 0.25f)
                            ),
                            modifier = Modifier
                                .weight(1f)
                                .padding(horizontal = 8.dp)
                        )

                        Text(
                            text = "${currentPage + 1} / $totalPages",
                            style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Bold),
                            color = if (isLiquidTheme) Color.White else themeColorScheme.onSurface,
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
                                tint = if (currentPage < totalPages - 1) themePrimary else AcrobatTextMuted
                            )
                        }
                    }
                }
            }
        }
    },
        containerColor = if (isLiquidTheme) Color.Transparent else when (colorFilter) {
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
                        var cardScale by remember { mutableFloatStateOf(1f) }
                        var cardOffsetX by remember { mutableFloatStateOf(0f) }
                        var cardOffsetY by remember { mutableFloatStateOf(0f) }

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
                                .graphicsLayer(
                                    scaleX = cardScale,
                                    scaleY = cardScale,
                                    translationX = cardOffsetX,
                                    translationY = cardOffsetY
                                )
                                .pointerInput(Unit) {
                                    detectTransformGestures { _, pan, zoom, _ ->
                                        val newZoom = (cardScale * zoom).coerceIn(1f, 5f)
                                        cardScale = newZoom
                                        if (newZoom > 1.05f) {
                                            val maxPanX = (size.width * (newZoom - 1f) / 2f).coerceAtLeast(0f)
                                            val maxPanY = (size.height * (newZoom - 1f) / 2f).coerceAtLeast(0f)
                                            cardOffsetX = (cardOffsetX + pan.x * 1.85f).coerceIn(-maxPanX, maxPanX)
                                            cardOffsetY = (cardOffsetY + pan.y * 1.85f).coerceIn(-maxPanY, maxPanY)
                                        } else {
                                            cardScale = 1f
                                            cardOffsetX = 0f
                                            cardOffsetY = 0f
                                        }
                                    }
                                }
                                .pointerInput(Unit) {
                                    detectTapGestures(
                                        onDoubleTap = {
                                            if (cardScale > 1.2f) {
                                                cardScale = 1f
                                                cardOffsetX = 0f
                                                cardOffsetY = 0f
                                            } else {
                                                cardScale = 2.5f
                                                cardOffsetX = 0f
                                                cardOffsetY = 0f
                                            }
                                        }
                                    )
                                }
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
                    val fittedRect = remember(pageSizePx, bitmap.width, bitmap.height) {
                        computeFittedPageRect(pageSizePx, bitmap.width, bitmap.height)
                    }

                    // Helper closure to begin or update word/char text selection at pointer offset
                    val startWordSelectionAt: (Offset) -> Unit = { ptrOffset ->
                        val norm = pointerToFittedNormalized(ptrOffset, fittedRect)
                        val layout = currentPageTextLayout
                        if (layout == null) {
                            viewModel.loadPageTextLayout(currentPage)
                            selectionUnavailableNotice = "Inspecting PDF text layer..."
                        } else if (!layout.isSelectable) {
                            selectionRange = null
                            selectionUnavailableNotice = layout.unavailableReason
                                ?: "Scanned or image-only page: no embedded text layer available."
                            viewModel.postStatusMessage(selectionUnavailableNotice!!)
                        } else {
                            selectionUnavailableNotice = null
                            val glyphIdx = PdfTextLayoutEngine.findNearestGlyphIndex(layout, norm.x, norm.y)
                            if (glyphIdx != null) {
                                val wordRange = PdfTextLayoutEngine.expandToWord(layout, glyphIdx)
                                selectionAnchorIdx = wordRange.first
                                selectionRange = wordRange
                                selectionExpandStage = 0
                                hapticFeedback.performHapticFeedback(androidx.compose.ui.hapticfeedback.HapticFeedbackType.LongPress)
                            }
                        }
                    }

                    // Interactive Container for PDF page + Text Selection + Vector annotations
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
                        .pointerInput(activeTool, pageSizePx) {
                            if (activeTool == AnnotationTool.NONE) {
                                detectTransformGestures { _, pan, zoom, _ ->
                                    val newScale = (scale * zoom).coerceIn(1f, 6f)
                                    scale = newScale
                                    if (newScale > 1.05f) {
                                        val maxPanX = ((pageSizePx.width.toFloat() * newScale - pageSizePx.width.toFloat()) / 2f).coerceAtLeast(0f)
                                        val maxPanY = ((pageSizePx.height.toFloat() * newScale - pageSizePx.height.toFloat()) / 2f).coerceAtLeast(0f)
                                        offsetX = (offsetX + pan.x * 1.85f).coerceIn(-maxPanX, maxPanX)
                                        offsetY = (offsetY + pan.y * 1.85f).coerceIn(-maxPanY, maxPanY)
                                    } else {
                                        scale = 1f
                                        offsetX = 0f
                                        offsetY = 0f
                                        if (kotlin.math.abs(pan.x) > 10f && selectionRange == null) {
                                            pageSlideOffset += pan.x
                                            if (pageSlideOffset < -70f && currentPage < totalPages - 1) {
                                                viewModel.setCurrentPage(currentPage + 1)
                                                pageSlideOffset = 0f
                                                hapticFeedback.performHapticFeedback(androidx.compose.ui.hapticfeedback.HapticFeedbackType.TextHandleMove)
                                            } else if (pageSlideOffset > 70f && currentPage > 0) {
                                                viewModel.setCurrentPage(currentPage - 1)
                                                pageSlideOffset = 0f
                                                hapticFeedback.performHapticFeedback(androidx.compose.ui.hapticfeedback.HapticFeedbackType.TextHandleMove)
                                            }
                                        }
                                    }
                                }
                            }
                        }
                        // Tap, Double-Tap & Direct Long-Press text selection
                        .pointerInput(activeTool, selectedStampText, selectedSignatureJson, fittedRect, currentPageTextLayout) {
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
                                onLongPress = { longPressOffset ->
                                    if (activeTool == AnnotationTool.NONE || activeTool == AnnotationTool.TEXT_SELECT) {
                                        startWordSelectionAt(longPressOffset)
                                    }
                                },
                                onTap = { offset ->
                                    val norm = pointerToFittedNormalized(offset, fittedRect)
                                    val normX = norm.x
                                    val normY = norm.y

                                    when (activeTool) {
                                        AnnotationTool.NONE -> {
                                            when {
                                                selectionRange != null -> {
                                                    selectionRange = null
                                                    selectionUnavailableNotice = null
                                                }
                                                selectedSignatureAnnId != null -> selectedSignatureAnnId = null
                                                else -> isBarsVisible = !isBarsVisible
                                            }
                                        }
                                        AnnotationTool.TEXT_SELECT -> {
                                            startWordSelectionAt(offset)
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
                                            isWhiteoutRedact = false
                                            showWhiteoutDialog = true
                                            hapticFeedback.performHapticFeedback(androidx.compose.ui.hapticfeedback.HapticFeedbackType.LongPress)
                                        }
                                        AnnotationTool.REDACT -> {
                                            pendingWhiteoutPosition = Offset(normX, normY)
                                            whiteoutReplacementText = ""
                                            isWhiteoutRedact = true
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
                                            if (paths == null) {
                                                viewModel.showSignatureDialog(true)
                                            } else {
                                                val trimmed = paths.trimStart()
                                                val isTyped = trimmed.startsWith("{") && trimmed.contains("TYPED")
                                                if (isTyped) {
                                                    try {
                                                        val obj = JSONObject(paths)
                                                        val txt = obj.optString("text", "").ifBlank { "Signature" }
                                                        val col = obj.optLong("colorHex", 0xFF1E3A8AL) and 0xFFFFFFFFL
                                                        viewModel.addAnnotation(
                                                            pageIndex = currentPage,
                                                            type = "TYPED_SIGNATURE",
                                                            textContent = txt,
                                                            colorHex = col,
                                                            strokeWidth = 26f,
                                                            posX = normX.coerceIn(0.02f, 0.85f),
                                                            posY = normY.coerceIn(0.05f, 0.96f)
                                                        )
                                                    } catch (_: Exception) {
                                                        viewModel.showSignatureDialog(true)
                                                    }
                                                } else {
                                                    viewModel.addAnnotation(
                                                        pageIndex = currentPage,
                                                        type = "SIGNATURE",
                                                        pointsJson = paths,
                                                        posX = (normX - 0.15f).coerceIn(0.01f, 0.70f),
                                                        posY = (normY - 0.06f).coerceIn(0.01f, 0.88f),
                                                        width = 0.30f,
                                                        height = 0.12f,
                                                        colorHex = 0xFF1E3A8AL
                                                    )
                                                }
                                                hapticFeedback.performHapticFeedback(androidx.compose.ui.hapticfeedback.HapticFeedbackType.LongPress)
                                            }
                                        }
                                        else -> {}
                                    }
                                }
                            )
                        }
                        // Drag listener for Pen, Highlighter, Underline, Strikethrough, and Text Selection sweep
                        .pointerInput(activeTool, activeColor, activeStrokeWidth, activeOpacity, fittedRect, currentPageTextLayout) {
                            if (activeTool == AnnotationTool.PEN ||
                                activeTool == AnnotationTool.HIGHLIGHT ||
                                activeTool == AnnotationTool.UNDERLINE ||
                                activeTool == AnnotationTool.STRIKETHROUGH
                            ) {
                                detectDragGestures(
                                    onDragStart = { startOffset ->
                                        currentDrawingPoints.clear()
                                        currentDrawingPoints.add(startOffset)
                                    },
                                    onDrag = { change, _ ->
                                        change.consume()
                                        currentDrawingPoints.add(change.position)
                                    },
                                    onDragEnd = {
                                        if (currentDrawingPoints.isNotEmpty()) {
                                            val arr = JSONArray()
                                            for (pt in currentDrawingPoints) {
                                                val norm = pointerToFittedNormalized(pt, fittedRect)
                                                val obj = JSONObject()
                                                obj.put("x", norm.x.toDouble())
                                                obj.put("y", norm.y.toDouble())
                                                arr.put(obj)
                                            }
                                            val annType = when (activeTool) {
                                                AnnotationTool.HIGHLIGHT -> "HIGHLIGHT"
                                                AnnotationTool.UNDERLINE -> "UNDERLINE"
                                                AnnotationTool.STRIKETHROUGH -> "STRIKETHROUGH"
                                                else -> "PEN"
                                            }
                                            viewModel.addAnnotation(
                                                pageIndex = currentPage,
                                                type = annType,
                                                pointsJson = arr.toString(),
                                                colorHex = activeColor,
                                                strokeWidth = activeStrokeWidth,
                                                opacity = activeOpacity
                                            )
                                            currentDrawingPoints.clear()
                                        }
                                    },
                                    onDragCancel = {
                                        currentDrawingPoints.clear()
                                    }
                                )
                            } else if (activeTool == AnnotationTool.TEXT_SELECT) {
                                detectDragGestures(
                                    onDragStart = { startOffset ->
                                        val layout = currentPageTextLayout
                                        if (layout == null) {
                                            viewModel.loadPageTextLayout(currentPage)
                                        } else if (!layout.isSelectable) {
                                            selectionUnavailableNotice = layout.unavailableReason
                                                ?: "Scanned or image-only page: no embedded text layer available."
                                        } else {
                                            selectionUnavailableNotice = null
                                            val norm = pointerToFittedNormalized(startOffset, fittedRect)
                                            val idx = PdfTextLayoutEngine.findNearestGlyphIndex(layout, norm.x, norm.y)
                                            if (idx != null) {
                                                selectionAnchorIdx = idx
                                                selectionRange = idx..idx
                                                selectionExpandStage = 0
                                            }
                                        }
                                    },
                                    onDrag = { change, _ ->
                                        change.consume()
                                        val layout = currentPageTextLayout ?: return@detectDragGestures
                                        if (!layout.isSelectable) return@detectDragGestures
                                        val norm = pointerToFittedNormalized(change.position, fittedRect)
                                        val idx = PdfTextLayoutEngine.findNearestGlyphIndex(layout, norm.x, norm.y)
                                        if (idx != null) {
                                            val a = min(selectionAnchorIdx, idx)
                                            val b = max(selectionAnchorIdx, idx)
                                            selectionRange = a..b
                                        }
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

                    // Canvas Vector Overlay for Saved Annotations, Text Selection Highlight + Live Stroke
                    Canvas(modifier = Modifier.fillMaxSize()) {
                        val originX = fittedRect.left
                        val originY = fittedRect.top
                        val pageW = fittedRect.width.coerceAtLeast(1f)
                        val pageH = fittedRect.height.coerceAtLeast(1f)

                        // 0. Draw Native Text Selection Highlight Rectangles + Caret Bars
                        val layoutForHighlight = currentPageTextLayout
                        val activeSelRange = selectionRange
                        if (layoutForHighlight != null && layoutForHighlight.isSelectable && activeSelRange != null) {
                            val selRects = PdfTextLayoutEngine.getSelectionHighlightRects(layoutForHighlight, activeSelRange)
                            for (r in selRects) {
                                val rLeft = originX + r.left * pageW
                                val rTop = originY + r.top * pageH
                                val rW = ((r.right - r.left) * pageW).coerceAtLeast(4f)
                                val rH = ((r.bottom - r.top) * pageH).coerceAtLeast(10f)
                                drawRect(
                                    color = Color(0x5538BDF8),
                                    topLeft = Offset(rLeft, rTop),
                                    size = androidx.compose.ui.geometry.Size(rW, rH)
                                )
                            }
                            if (selRects.isNotEmpty()) {
                                val firstR = selRects.first()
                                val lastR = selRects.last()
                                val startX = originX + firstR.left * pageW
                                val startTop = originY + firstR.top * pageH
                                val startBot = originY + firstR.bottom * pageH
                                val endX = originX + lastR.right * pageW
                                val endTop = originY + lastR.top * pageH
                                val endBot = originY + lastR.bottom * pageH

                                drawLine(Color(0xFF0EA5E9), Offset(startX, startTop), Offset(startX, startBot), strokeWidth = 4f)
                                drawCircle(Color(0xFF0EA5E9), radius = 11f, center = Offset(startX, startBot + 10f))
                                drawLine(Color(0xFF0EA5E9), Offset(endX, endTop), Offset(endX, endBot), strokeWidth = 4f)
                                drawCircle(Color(0xFF0EA5E9), radius = 11f, center = Offset(endX, endBot + 10f))
                            }
                        }

                        // 1. Draw saved annotations for this page using exact fittedRect mapping
                        for (ann in pageAnnotations) {
                            val annAlpha = ann.opacity.coerceIn(0.15f, 1f)
                            when (ann.type) {
                                "PEN", "HIGHLIGHT", "UNDERLINE", "STRIKETHROUGH" -> {
                                    if (ann.pointsJson.isNotBlank()) {
                                        try {
                                            val pts = JSONArray(ann.pointsJson)
                                            if (pts.length() > 0) {
                                                val p = Path()
                                                for (i in 0 until pts.length()) {
                                                    val obj = pts.getJSONObject(i)
                                                    val x = originX + obj.getDouble("x").toFloat() * pageW
                                                    val y = originY + obj.getDouble("y").toFloat() * pageH
                                                    if (i == 0) p.moveTo(x, y) else p.lineTo(x, y)
                                                }
                                                val strokeAlpha = if (ann.type == "HIGHLIGHT") minOf(annAlpha, 0.45f) else annAlpha
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
                                    } else if ((ann.type == "UNDERLINE" || ann.type == "STRIKETHROUGH") && ann.width > 0f) {
                                        val x1 = originX + ann.posX * pageW
                                        val x2 = originX + (ann.posX + ann.width) * pageW
                                        val y = originY + ann.posY * pageH
                                        drawLine(
                                            color = Color(ann.colorHex).copy(alpha = annAlpha),
                                            start = Offset(x1, y),
                                            end = Offset(x2, y),
                                            strokeWidth = ann.strokeWidth.coerceAtLeast(2.5f)
                                        )
                                    }
                                }
                                "STICKY_NOTE" -> {
                                    val x = originX + ann.posX * pageW
                                    val y = originY + ann.posY * pageH
                                    drawCircle(
                                        color = Color(0xFFF59E0B),
                                        radius = 14f,
                                        center = Offset(x, y)
                                    )
                                }
                                "STAMP" -> {
                                    val x = originX + ann.posX * pageW
                                    val y = originY + ann.posY * pageH
                                    drawRoundRect(
                                        color = Color(ann.colorHex).copy(alpha = annAlpha),
                                        topLeft = Offset(x - 55f, y - 18f),
                                        size = androidx.compose.ui.geometry.Size(110f, 36f),
                                        cornerRadius = androidx.compose.ui.geometry.CornerRadius(6f, 6f),
                                        style = Stroke(width = 2.5f)
                                    )
                                }
                                "SIGNATURE", "TYPED_SIGNATURE" -> {
                                    val liveDrag = signatureDragOffsets[ann.id] ?: Offset.Zero
                                    if (ann.textContent.isNotBlank()) {
                                        val x = originX + (ann.posX + liveDrag.x) * pageW
                                        val y = originY + (ann.posY + liveDrag.y) * pageH
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
                                        val sigW = (if (ann.width > 0.05f) ann.width else 0.30f) * pageW
                                        val sigH = (if (ann.height > 0.03f) ann.height else 0.12f) * pageH
                                        val baseX = originX + (ann.posX + liveDrag.x) * pageW
                                        val baseY = originY + (ann.posY + liveDrag.y) * pageH
                                        try {
                                            val strokes = JSONArray(ann.pointsJson)
                                            for (s in 0 until strokes.length()) {
                                                val strokeArr = strokes.getJSONArray(s)
                                                val path = Path()
                                                for (p in 0 until strokeArr.length()) {
                                                    val pt = strokeArr.getJSONObject(p)
                                                    val px = baseX + pt.getDouble("x").toFloat() * sigW
                                                    val py = baseY + pt.getDouble("y").toFloat() * sigH
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
                                    val x = originX + ann.posX * pageW
                                    val y = originY + ann.posY * pageH
                                    val w = (if (ann.width > 0f) ann.width else 0.25f) * pageW
                                    val h = (if (ann.height > 0f) ann.height else 0.04f) * pageH
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
                        if (wm != null && (wm.applyToAllPages || currentPage == 0) && (wm.text.isNotBlank() || !wm.imagePath.isNullOrBlank())) {
                            val wmLogoBmp = wm.imagePath?.let { p ->
                                val f = java.io.File(p)
                                if (f.exists() && f.length() > 0L) android.graphics.BitmapFactory.decodeFile(f.absolutePath) else null
                            }
                            rotate(-wm.rotationAngle, Offset(originX + pageW / 2f, originY + pageH / 2f)) {
                                if (wmLogoBmp != null) {
                                    val logoPaint = android.graphics.Paint(android.graphics.Paint.ANTI_ALIAS_FLAG or android.graphics.Paint.FILTER_BITMAP_FLAG).apply {
                                        alpha = (wm.opacity * 255).toInt().coerceIn(15, 250)
                                    }
                                    val span = minOf(pageW, pageH) * wm.imageScale.coerceIn(0.12f, 0.85f)
                                    val ratio = wmLogoBmp.height.toFloat() / wmLogoBmp.width.coerceAtLeast(1)
                                    val drawW = span
                                    val drawH = span * ratio
                                    if (wm.isTiledPattern) {
                                        val stepX = (drawW * 1.6f).coerceAtLeast(140f)
                                        val stepY = (drawH * 1.8f).coerceAtLeast(140f)
                                        for (wx in (-pageW).toInt()..(pageW * 2).toInt() step stepX.toInt()) {
                                            for (wy in (-pageH).toInt()..(pageH * 2).toInt() step stepY.toInt()) {
                                                val dst = android.graphics.RectF(
                                                    originX + wx - drawW / 2f,
                                                    originY + wy - drawH / 2f,
                                                    originX + wx + drawW / 2f,
                                                    originY + wy + drawH / 2f
                                                )
                                                drawContext.canvas.nativeCanvas.drawBitmap(wmLogoBmp, null, dst, logoPaint)
                                            }
                                        }
                                    } else {
                                        val cx = originX + pageW / 2f
                                        val cy = originY + if (wm.text.isNotBlank()) pageH * 0.44f else pageH / 2f
                                        val dst = android.graphics.RectF(cx - drawW / 2f, cy - drawH / 2f, cx + drawW / 2f, cy + drawH / 2f)
                                        drawContext.canvas.nativeCanvas.drawBitmap(wmLogoBmp, null, dst, logoPaint)
                                    }
                                }
                                if (wm.text.isNotBlank()) {
                                    val wmPaint = android.graphics.Paint().apply {
                                        color = wm.colorHex.toInt()
                                        alpha = (wm.opacity * 255).toInt().coerceIn(10, 240)
                                        textSize = wm.fontSizeSp * 2.2f
                                        isFakeBoldText = true
                                        isAntiAlias = true
                                        textAlign = android.graphics.Paint.Align.CENTER
                                    }
                                    if (wm.isTiledPattern) {
                                        val stepX = (pageW * 0.45f).coerceAtLeast(140f)
                                        val stepY = (pageH * 0.22f).coerceAtLeast(100f)
                                        for (wx in (-pageW).toInt()..(pageW * 2).toInt() step stepX.toInt()) {
                                            for (wy in (-pageH).toInt()..(pageH * 2).toInt() step stepY.toInt()) {
                                                drawContext.canvas.nativeCanvas.drawText(
                                                    wm.text,
                                                    originX + wx.toFloat(),
                                                    originY + wy.toFloat(),
                                                    wmPaint
                                                )
                                            }
                                        }
                                    } else {
                                        val textY = if (wmLogoBmp != null) originY + pageH * 0.62f else originY + pageH / 2f
                                        drawContext.canvas.nativeCanvas.drawText(
                                            wm.text,
                                            originX + pageW / 2f,
                                            textY,
                                            wmPaint
                                        )
                                    }
                                }
                            }
                            wmLogoBmp?.recycle()
                        }

                        // 3. Draw live stroke in progress
                        if (currentDrawingPoints.size > 1) {
                            val livePath = Path()
                            currentDrawingPoints.forEachIndexed { idx, pt ->
                                if (idx == 0) livePath.moveTo(pt.x, pt.y) else livePath.lineTo(pt.x, pt.y)
                            }
                            val liveAlpha = if (activeTool == AnnotationTool.HIGHLIGHT) minOf(activeOpacity, 0.45f) else activeOpacity
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

                    // Draggable Start / End Teardrop Selection Handle Hit Targets (always win over background gestures)
                    val layoutForHandles = currentPageTextLayout
                    val currentRange = selectionRange
                    if (layoutForHandles != null && layoutForHandles.isSelectable && currentRange != null) {
                        val handleRects = remember(layoutForHandles, currentRange) {
                            PdfTextLayoutEngine.getSelectionHighlightRects(layoutForHandles, currentRange)
                        }
                        if (handleRects.isNotEmpty()) {
                            val firstRect = handleRects.first()
                            val lastRect = handleRects.last()
                            val startHandleX = fittedRect.left + firstRect.left * fittedRect.width
                            val startHandleY = fittedRect.top + firstRect.bottom * fittedRect.height
                            val endHandleX = fittedRect.left + lastRect.right * fittedRect.width
                            val endHandleY = fittedRect.top + lastRect.bottom * fittedRect.height
                            val hitRadiusPx = with(density) { 24.dp.roundToPx() }

                            // Start handle drag target
                            Box(
                                modifier = Modifier
                                    .offset {
                                        IntOffset(
                                            (startHandleX - hitRadiusPx).roundToInt(),
                                            (startHandleY - hitRadiusPx / 2).roundToInt()
                                        )
                                    }
                                    .size(48.dp)
                                    .testTag("selection_start_handle")
                                    .pointerInput(layoutForHandles, fittedRect) {
                                        detectDragGestures { change, dragAmount ->
                                            change.consume()
                                            val curStart = selectionRange?.first ?: return@detectDragGestures
                                            val curEnd = selectionRange?.last ?: return@detectDragGestures
                                            val startGlyph = layoutForHandles.glyphs.getOrNull(curStart) ?: return@detectDragGestures
                                            val curPx = Offset(
                                                fittedRect.left + startGlyph.left * fittedRect.width,
                                                fittedRect.top + ((startGlyph.top + startGlyph.bottom) * 0.5f) * fittedRect.height
                                            )
                                            val targetNorm = pointerToFittedNormalized(curPx + dragAmount, fittedRect)
                                            val newIdx = PdfTextLayoutEngine.findNearestGlyphIndex(layoutForHandles, targetNorm.x, targetNorm.y)
                                            if (newIdx != null) {
                                                selectionRange = min(newIdx, curEnd)..max(newIdx, curEnd)
                                            }
                                        }
                                    }
                            )

                            // End handle drag target
                            Box(
                                modifier = Modifier
                                    .offset {
                                        IntOffset(
                                            (endHandleX - hitRadiusPx).roundToInt(),
                                            (endHandleY - hitRadiusPx / 2).roundToInt()
                                        )
                                    }
                                    .size(48.dp)
                                    .testTag("selection_end_handle")
                                    .pointerInput(layoutForHandles, fittedRect) {
                                        detectDragGestures { change, dragAmount ->
                                            change.consume()
                                            val curStart = selectionRange?.first ?: return@detectDragGestures
                                            val curEnd = selectionRange?.last ?: return@detectDragGestures
                                            val endGlyph = layoutForHandles.glyphs.getOrNull(curEnd) ?: return@detectDragGestures
                                            val curPx = Offset(
                                                fittedRect.left + endGlyph.right * fittedRect.width,
                                                fittedRect.top + ((endGlyph.top + endGlyph.bottom) * 0.5f) * fittedRect.height
                                            )
                                            val targetNorm = pointerToFittedNormalized(curPx + dragAmount, fittedRect)
                                            val newIdx = PdfTextLayoutEngine.findNearestGlyphIndex(layoutForHandles, targetNorm.x, targetNorm.y)
                                            if (newIdx != null) {
                                                selectionRange = min(curStart, newIdx)..max(curStart, newIdx)
                                            }
                                        }
                                    }
                            )
                        }
                    }

                    // Render interactive overlays for Sticky Notes, Stamps, Text Boxes, Redact/Whiteout, and Form Fields mapped inside fittedRect
                    Box(modifier = Modifier.fillMaxSize()) {
                        for (ann in pageAnnotations) {
                            if (ann.type == "STICKY_NOTE") {
                                var showBubble by remember { mutableStateOf(false) }
                                val noteX = with(density) { (fittedRect.left + ann.posX * fittedRect.width).toDp() } - 14.dp
                                val noteY = with(density) { (fittedRect.top + ann.posY * fittedRect.height).toDp() } - 14.dp
                                Box(
                                    modifier = Modifier
                                        .offset(x = noteX, y = noteY)
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
                                val stampX = with(density) { (fittedRect.left + ann.posX * fittedRect.width).toDp() } - 55.dp
                                val stampY = with(density) { (fittedRect.top + ann.posY * fittedRect.height).toDp() } - 18.dp
                                Box(
                                    modifier = Modifier
                                        .offset(x = stampX, y = stampY)
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
                                val boxX = with(density) { (fittedRect.left + ann.posX * fittedRect.width).toDp() }
                                val boxY = with(density) { (fittedRect.top + ann.posY * fittedRect.height).toDp() } - 10.dp
                                Box(
                                    modifier = Modifier
                                        .offset(x = boxX, y = boxY)
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
                            } else if (ann.type == "FORM_FIELD" && activeTool != AnnotationTool.FORM_FIELD) {
                                val fieldX = with(density) { (fittedRect.left + ann.posX * fittedRect.width).toDp() }
                                val fieldY = with(density) { (fittedRect.top + ann.posY * fittedRect.height).toDp() }
                                Surface(
                                    shape = RoundedCornerShape(4.dp),
                                    color = AdfMidnightSurface.copy(alpha = 0.85f),
                                    border = androidx.compose.foundation.BorderStroke(1.dp, AdfElectricCyan.copy(alpha = 0.7f)),
                                    modifier = Modifier
                                        .offset(x = fieldX, y = fieldY)
                                        .clickable { editingAnnotation = ann }
                                ) {
                                    Text(
                                        text = ann.textContent,
                                        color = AdfElectricCyan,
                                        fontSize = 11.sp,
                                        fontWeight = FontWeight.Bold,
                                        modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                                    )
                                }
                            } else if (ann.type == "WHITEOUT" || ann.type == "REDACT") {
                                val isRedact = ann.type == "REDACT"
                                val wDp = with(density) { ((if (ann.width > 0f) ann.width else 0.25f) * fittedRect.width).toDp() }
                                val hDp = with(density) { ((if (ann.height > 0f) ann.height else 0.04f) * fittedRect.height).toDp() }
                                val wX = with(density) { (fittedRect.left + ann.posX * fittedRect.width).toDp() }
                                val wY = with(density) { (fittedRect.top + ann.posY * fittedRect.height).toDp() }
                                Box(
                                    modifier = Modifier
                                        .offset(x = wX, y = wY)
                                        .size(wDp, hDp)
                                        .background(if (isRedact) Color.Black else Color.White)
                                        .border(1.dp, if (isRedact) Color(0xFFEF4444) else Color(0xFFCBD5E1), RoundedCornerShape(2.dp))
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
                                    } else if (isRedact) {
                                        Text(
                                            text = "MARKED FOR REDACTION",
                                            color = Color(0xFFEF4444),
                                            fontSize = 8.sp,
                                            fontWeight = FontWeight.Bold,
                                            maxLines = 1
                                        )
                                    }
                                }
                            } else if ((ann.type == "SIGNATURE" || ann.type == "TYPED_SIGNATURE") &&
                                pageSizePx.width > 0 && pageSizePx.height > 0
                            ) {
                                val isTypedSig = ann.textContent.isNotBlank()
                                val liveDrag = signatureDragOffsets[ann.id] ?: Offset.Zero
                                val textSizePx = if (ann.strokeWidth > 10f) ann.strokeWidth else 26f
                                val sigWNorm = if (isTypedSig) {
                                    val measuredW = remember(ann.textContent, textSizePx) {
                                        android.graphics.Paint().apply {
                                            textSize = textSizePx
                                            isFakeBoldText = true
                                            typeface = android.graphics.Typeface.create("cursive", android.graphics.Typeface.ITALIC)
                                        }.measureText(ann.textContent)
                                    }
                                    ((measuredW + 32f) / pageSizePx.width.toFloat()).coerceIn(0.14f, 0.75f)
                                } else {
                                    if (ann.width > 0.05f) ann.width else 0.30f
                                }
                                val sigHNorm = if (isTypedSig) {
                                    ((textSizePx + 26f) / pageSizePx.height.toFloat()).coerceIn(0.05f, 0.30f)
                                } else {
                                    if (ann.height > 0.03f) ann.height else 0.12f
                                }

                                val boxLeftPx = if (isTypedSig) {
                                    fittedRect.left + (ann.posX + liveDrag.x) * fittedRect.width - 10f
                                } else {
                                    fittedRect.left + (ann.posX + liveDrag.x) * fittedRect.width
                                }
                                val boxTopPx = if (isTypedSig) {
                                    fittedRect.top + (ann.posY + liveDrag.y) * fittedRect.height - textSizePx - 4f
                                } else {
                                    fittedRect.top + (ann.posY + liveDrag.y) * fittedRect.height
                                }

                                val boxX = with(density) { boxLeftPx.toDp() }
                                val boxY = with(density) { boxTopPx.toDp() }
                                val boxW = with(density) { (sigWNorm * fittedRect.width).toDp() }.coerceAtLeast(72.dp)
                                val boxH = with(density) { (sigHNorm * fittedRect.height).toDp() }.coerceAtLeast(36.dp)
                                val isSelected = selectedSignatureAnnId == ann.id
                                val canMoveSignatures = activeTool != AnnotationTool.PEN && activeTool != AnnotationTool.HIGHLIGHT

                                Box(
                                    modifier = Modifier
                                        .offset(x = boxX, y = boxY)
                                        .size(width = boxW, height = boxH)
                                        .then(
                                            if (isSelected) {
                                                Modifier
                                                    .clip(RoundedCornerShape(8.dp))
                                                    .background(AdfElectricCyan.copy(alpha = 0.08f))
                                                    .border(1.5.dp, AdfElectricCyan, RoundedCornerShape(8.dp))
                                            } else {
                                                Modifier
                                            }
                                        )
                                        .then(
                                            if (canMoveSignatures) {
                                                Modifier
                                                    .pointerInput(ann.id, canMoveSignatures) {
                                                        detectTapGestures {
                                                            selectedSignatureAnnId = if (selectedSignatureAnnId == ann.id) null else ann.id
                                                        }
                                                    }
                                                    .pointerInput(ann.id, canMoveSignatures, pageSizePx, sigWNorm, sigHNorm) {
                                                        detectDragGestures(
                                                            onDragStart = {
                                                                selectedSignatureAnnId = ann.id
                                                                signatureDragOffsets[ann.id] = Offset.Zero
                                                                hapticFeedback.performHapticFeedback(androidx.compose.ui.hapticfeedback.HapticFeedbackType.TextHandleMove)
                                                            },
                                                            onDrag = { change, dragAmount ->
                                                                change.consume()
                                                                val cur = signatureDragOffsets[ann.id] ?: Offset.Zero
                                                                val nextX = cur.x + dragAmount.x / pageSizePx.width.toFloat()
                                                                val nextY = cur.y + dragAmount.y / pageSizePx.height.toFloat()
                                                                val minPosX = if (isTypedSig) 0.01f else 0f
                                                                val maxPosX = if (isTypedSig) 0.94f else (1f - sigWNorm).coerceAtLeast(0f)
                                                                val minPosY = if (isTypedSig) 0.03f else 0f
                                                                val maxPosY = if (isTypedSig) 0.98f else (1f - sigHNorm).coerceAtLeast(0f)
                                                                signatureDragOffsets[ann.id] = Offset(
                                                                    (ann.posX + nextX).coerceIn(minPosX, maxPosX) - ann.posX,
                                                                    (ann.posY + nextY).coerceIn(minPosY, maxPosY) - ann.posY
                                                                )
                                                            },
                                                            onDragEnd = {
                                                                val finalDrag = signatureDragOffsets.remove(ann.id) ?: Offset.Zero
                                                                val minPosX = if (isTypedSig) 0.01f else 0f
                                                                val maxPosX = if (isTypedSig) 0.94f else (1f - sigWNorm).coerceAtLeast(0f)
                                                                val minPosY = if (isTypedSig) 0.03f else 0f
                                                                val maxPosY = if (isTypedSig) 0.98f else (1f - sigHNorm).coerceAtLeast(0f)
                                                                val updatedX = (ann.posX + finalDrag.x).coerceIn(minPosX, maxPosX)
                                                                val updatedY = (ann.posY + finalDrag.y).coerceIn(minPosY, maxPosY)
                                                                viewModel.updateAnnotation(
                                                                    ann.copy(
                                                                        posX = updatedX,
                                                                        posY = updatedY,
                                                                        width = if (isTypedSig) ann.width else sigWNorm,
                                                                        height = if (isTypedSig) ann.height else sigHNorm
                                                                    )
                                                                )
                                                            },
                                                            onDragCancel = {
                                                                signatureDragOffsets.remove(ann.id)
                                                            }
                                                        )
                                                    }
                                            } else {
                                                Modifier
                                            }
                                        )
                                        .testTag("movable_signature_${ann.id}")
                                )

                                // Floating Resize + Delete Bar above/below selected signature
                                if (isSelected) {
                                    val barAbove = (ann.posY + liveDrag.y) > 0.14f
                                    val barOffsetY = if (barAbove) (boxY - 52.dp).coerceAtLeast(4.dp) else (boxY + boxH + 4.dp)
                                    val maxBarX = (with(density) { pageSizePx.width.toDp() } - 216.dp).coerceAtLeast(8.dp)
                                    val barOffsetX = boxX.coerceIn(8.dp, maxBarX)
                                    Surface(
                                        shape = RoundedCornerShape(24.dp),
                                        color = Color(0xF20F172A),
                                        border = androidx.compose.foundation.BorderStroke(1.dp, AdfElectricCyan.copy(alpha = 0.7f)),
                                        shadowElevation = 6.dp,
                                        modifier = Modifier.offset(x = barOffsetX, y = barOffsetY)
                                    ) {
                                        Row(
                                            verticalAlignment = Alignment.CenterVertically,
                                            modifier = Modifier.padding(horizontal = 4.dp)
                                        ) {
                                            IconButton(
                                                onClick = {
                                                    if (isTypedSig) {
                                                        val newSize = (textSizePx - 4f).coerceIn(14f, 64f)
                                                        viewModel.updateAnnotation(ann.copy(strokeWidth = newSize))
                                                    } else {
                                                        val newW = (sigWNorm * 0.85f).coerceIn(0.14f, 0.75f)
                                                        val newH = (sigHNorm * 0.85f).coerceIn(0.06f, 0.35f)
                                                        viewModel.updateAnnotation(ann.copy(width = newW, height = newH))
                                                    }
                                                }
                                            ) {
                                                Icon(Icons.Default.Remove, contentDescription = "Shrink signature", tint = Color.White, modifier = Modifier.size(18.dp))
                                            }
                                            Text(
                                                text = "Drag",
                                                color = AdfElectricCyan,
                                                fontSize = 11.sp,
                                                fontWeight = FontWeight.Bold,
                                                modifier = Modifier.padding(horizontal = 2.dp)
                                            )
                                            IconButton(
                                                onClick = {
                                                    if (isTypedSig) {
                                                        val newSize = (textSizePx + 4f).coerceIn(14f, 64f)
                                                        viewModel.updateAnnotation(ann.copy(strokeWidth = newSize))
                                                    } else {
                                                        val newW = (sigWNorm * 1.18f).coerceIn(0.14f, 0.75f)
                                                        val newH = (sigHNorm * 1.18f).coerceIn(0.06f, 0.35f)
                                                        viewModel.updateAnnotation(ann.copy(width = newW, height = newH))
                                                    }
                                                }
                                            ) {
                                                Icon(Icons.Default.Add, contentDescription = "Enlarge signature", tint = Color.White, modifier = Modifier.size(18.dp))
                                            }
                                            IconButton(
                                                onClick = {
                                                    viewModel.deleteAnnotation(ann.id)
                                                    selectedSignatureAnnId = null
                                                }
                                            ) {
                                                Icon(Icons.Default.DeleteOutline, contentDescription = "Delete signature", tint = Color(0xFFEF4444), modifier = Modifier.size(18.dp))
                                            }
                                        }
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

                    // 4K Auto-Ultra Clarity Active Badge on Zoom
                    if (scale > 1.2f && is4KUltraClarityEnabled) {
                        Surface(
                            shape = RoundedCornerShape(14.dp),
                            color = Color(0xF20F172A),
                            border = CardDefaults.outlinedCardBorder().copy(
                                brush = Brush.horizontalGradient(
                                    listOf(AdfElectricCyan, Color(0xFF10B981))
                                )
                            ),
                            shadowElevation = 8.dp,
                            modifier = Modifier
                                .align(Alignment.TopEnd)
                                .padding(top = 16.dp, end = 16.dp)
                                .testTag("reader_4k_badge")
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp)
                            ) {
                                Box(
                                    modifier = Modifier
                                        .size(7.dp)
                                        .clip(CircleShape)
                                        .background(Color(0xFF10B981))
                                )
                                Spacer(modifier = Modifier.width(6.dp))
                                Text(
                                    text = "✨ 4K Ultra-Clarity Active (${String.format(java.util.Locale.US, "%.1fx", scale)})",
                                    color = Color.White,
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.ExtraBold
                                )
                            }
                        }
                    }

                    // In-Document Search Active Highlight Badge
                    if (isSearchActive && searchQuery.isNotBlank() && searchResults.contains(currentPage)) {
                        Surface(
                            shape = RoundedCornerShape(20.dp),
                            color = Color(0xFFF59E0B),
                            shadowElevation = 8.dp,
                            modifier = Modifier
                                .align(Alignment.TopCenter)
                                .padding(top = 16.dp)
                                .testTag("search_match_highlight_badge")
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                modifier = Modifier.padding(horizontal = 14.dp, vertical = 6.dp)
                            ) {
                                Icon(Icons.Default.Search, contentDescription = null, tint = Color.Black, modifier = Modifier.size(16.dp))
                                Spacer(modifier = Modifier.width(6.dp))
                                Text(
                                    text = "Match Found on Page ${currentPage + 1}: \"$searchQuery\"",
                                    color = Color.Black,
                                    fontSize = 12.sp,
                                    fontWeight = FontWeight.ExtraBold
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

            // Persistent Form Fill Overlay when Form Tool is active
            if (activeTool == AnnotationTool.FORM_FIELD) {
                FormFillOverlay(
                    isEditing = true,
                    persistedFields = pageAnnotations.filter { it.type == "FORM_FIELD" },
                    onAddPersistedField = { fieldType, xRatio, yRatio, defaultVal ->
                        viewModel.addAnnotation(
                            pageIndex = currentPage,
                            type = "FORM_FIELD",
                            posX = xRatio,
                            posY = yRatio,
                            colorHex = 0xFF0F172AL,
                            strokeWidth = 13f,
                            textContent = defaultVal,
                            extraData = fieldType.name
                        )
                    },
                    onUpdatePersistedField = { updated -> viewModel.updateAnnotation(updated) },
                    onDeletePersistedField = { id -> viewModel.deleteAnnotation(id) },
                    onClose = { viewModel.setActiveTool(AnnotationTool.NONE) }
                )
            }

            // Mode hint or honest scanned-page unavailability notice
            val layoutState = currentPageTextLayout
            val bannerMessage = when {
                selectionUnavailableNotice != null -> selectionUnavailableNotice
                activeTool == AnnotationTool.TEXT_SELECT && layoutState != null && !layoutState.isSelectable ->
                    layoutState.unavailableReason ?: "Scanned or image-only page: no embedded text layer available."
                activeTool == AnnotationTool.TEXT_SELECT && selectionRange == null ->
                    "Text Selection Mode: tap or drag across text to select"
                else -> null
            }
            if (!bannerMessage.isNullOrBlank()) {
                Surface(
                    shape = RoundedCornerShape(20.dp),
                    color = Color(0xEE0F172A),
                    border = androidx.compose.foundation.BorderStroke(1.dp, AdfElectricCyan.copy(alpha = 0.5f)),
                    shadowElevation = 6.dp,
                    modifier = Modifier
                        .align(Alignment.TopCenter)
                        .padding(top = 12.dp, start = 16.dp, end = 16.dp)
                        .testTag("text_selection_notice_banner")
                ) {
                    Text(
                        text = bannerMessage,
                        color = Color.White,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Medium,
                        modifier = Modifier.padding(horizontal = 14.dp, vertical = 7.dp)
                    )
                }
            }

            // Floating Selection Contextual Action Bar
            val activeLayout = currentPageTextLayout
            val curSel = selectionRange
            if (activeLayout != null && activeLayout.isSelectable && curSel != null) {
                val selectedString = remember(activeLayout, curSel) {
                    PdfTextLayoutEngine.getSelectedText(activeLayout, curSel)
                }
                if (selectedString.isNotBlank()) {
                    val stageLabel = when (selectionExpandStage % 3) {
                        0 -> "Sentence"
                        1 -> "Paragraph"
                        else -> "Word"
                    }
                    SelectionFloatingActionBar(
                        selectedText = selectedString,
                        expandStageLabel = stageLabel,
                        onCopy = {
                            clipboardManager.setText(AnnotatedString(selectedString))
                            viewModel.postStatusMessage("Copied ${selectedString.length} characters to clipboard")
                        },
                        onExpandSelection = {
                            val stage = selectionExpandStage % 3
                            selectionRange = when (stage) {
                                0 -> PdfTextLayoutEngine.expandToSentence(activeLayout, curSel.first)
                                1 -> PdfTextLayoutEngine.expandToParagraph(activeLayout, curSel.first)
                                else -> PdfTextLayoutEngine.expandToWord(activeLayout, curSel.first)
                            }
                            selectionExpandStage = (selectionExpandStage + 1) % 3
                        },
                        onSelectAll = {
                            if (activeLayout.glyphs.isNotEmpty()) {
                                selectionRange = 0..activeLayout.glyphs.lastIndex
                            }
                        },
                        onUnderlineSelection = {
                            val rects = PdfTextLayoutEngine.getSelectionHighlightRects(activeLayout, curSel)
                            rects.forEach { r ->
                                viewModel.addAnnotation(
                                    pageIndex = currentPage,
                                    type = "UNDERLINE",
                                    posX = r.left,
                                    posY = r.bottom,
                                    width = (r.right - r.left).coerceAtLeast(0.02f),
                                    colorHex = 0xFF2563EB,
                                    strokeWidth = 3f
                                )
                            }
                            selectionRange = null
                        },
                        onStrikeSelection = {
                            val rects = PdfTextLayoutEngine.getSelectionHighlightRects(activeLayout, curSel)
                            rects.forEach { r ->
                                viewModel.addAnnotation(
                                    pageIndex = currentPage,
                                    type = "STRIKETHROUGH",
                                    posX = r.left,
                                    posY = (r.top + r.bottom) * 0.5f,
                                    width = (r.right - r.left).coerceAtLeast(0.02f),
                                    colorHex = 0xFFDC2626,
                                    strokeWidth = 3f
                                )
                            }
                            selectionRange = null
                        },
                        onSearch = {
                            val query = selectedString.trim().replace("\n", " ").take(60)
                            selectionRange = null
                            viewModel.toggleSearch(true)
                            viewModel.performSearch(query)
                        },
                        onShareText = {
                            val sendIntent = Intent(Intent.ACTION_SEND).apply {
                                type = "text/plain"
                                putExtra(Intent.EXTRA_TEXT, selectedString)
                                putExtra(Intent.EXTRA_SUBJECT, "Excerpt from ${currentDoc?.title ?: "Document"}")
                            }
                            context.startActivity(Intent.createChooser(sendIntent, "Share Selected Text"))
                        },
                        onClear = {
                            selectionRange = null
                            selectionUnavailableNotice = null
                        },
                        modifier = Modifier
                            .align(Alignment.BottomCenter)
                            .padding(bottom = 12.dp)
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
                                text = if (ttsIsPlaying) "PDF (AIO) Read Aloud" else "Read Aloud Paused",
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

            // Floating Permanent Redaction Action Pill when REDACT boxes exist and selection bar is inactive
            if (redactCount > 0 && selectionRange == null && !isPureViewerMode) {
                Button(
                    onClick = { showApplyRedactionDialog = true },
                    colors = ButtonDefaults.buttonColors(
                        containerColor = Color(0xFFDC2626),
                        contentColor = Color.White
                    ),
                    shape = RoundedCornerShape(24.dp),
                    elevation = ButtonDefaults.buttonElevation(defaultElevation = 6.dp),
                    modifier = Modifier
                        .align(Alignment.BottomCenter)
                        .padding(bottom = 14.dp)
                        .testTag("apply_redaction_button")
                ) {
                    Icon(Icons.Default.VerifiedUser, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = "Apply Permanent Redaction ($redactCount)",
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Bold
                    )
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
        var isRedactStyle by remember(isWhiteoutRedact) { mutableStateOf(isWhiteoutRedact) }

        AlertDialog(
            onDismissRequest = { showWhiteoutDialog = false },
            title = {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Default.LayersClear, contentDescription = null, tint = Color(0xFFF43F5E), modifier = Modifier.size(20.dp))
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = if (isRedactStyle) "Mark Area for Redaction" else "Whiteout / Content Erase",
                        color = AcrobatTextPrimary,
                        fontWeight = FontWeight.Bold
                    )
                }
            },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Text(
                        text = if (isRedactStyle) {
                            "Marks an area for redaction. Run 'Apply Permanent Redaction' to strip underlying PDF text, objects & metadata cleanly."
                        } else {
                            "Blanks out visual content on the page. You can also type replacement text to overlay."
                        },
                        style = MaterialTheme.typography.bodySmall,
                        color = AcrobatTextSecondary
                    )

                    OutlinedTextField(
                        value = whiteoutReplacementText,
                        onValueChange = { whiteoutReplacementText = it },
                        label = { Text("Replacement Text (Optional)") },
                        placeholder = { Text("Type new text or leave blank...") },
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
                                colorHex = if (isRedactStyle) 0xFF000000L else 0xFFFFFFFFL,
                                isRedaction = isRedactStyle
                            )
                        }
                        showWhiteoutDialog = false
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = CrimsonPrimary)
                ) {
                    Text(if (isRedactStyle) "Mark Redaction" else "Apply Whiteout")
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
                        text = "Choose a signature and tap anywhere on the page to place it, then drag it freely to adjust:",
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
                    if (signatures.isEmpty()) {
                        Text(
                            text = "No saved signatures yet. Open Signature Studio to type or draw one.",
                            style = MaterialTheme.typography.bodySmall,
                            color = AcrobatTextMuted
                        )
                    }
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        showSignaturePicker = false
                        viewModel.showSignatureDialog(true)
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = CrimsonPrimary)
                ) {
                    Text("Signature Studio")
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

    // Real Storage Rename Dialog
    if (showRenameDialog && currentDoc != null) {
        RenameDocumentDialog(
            currentTitle = currentDoc!!.title,
            isRenaming = isRenamingFile,
            externalError = renameErrorText,
            onDismiss = { showRenameDialog = false },
            onConfirmRename = { requestedName ->
                isRenamingFile = true
                renameErrorText = null
                viewModel.renameActiveDocument(requestedName) { result ->
                    isRenamingFile = false
                    when (result) {
                        is StorageRenameCoordinator.RenameResult.Success -> {
                            showRenameDialog = false
                        }
                        is StorageRenameCoordinator.RenameResult.ValidationError -> {
                            renameErrorText = result.reason
                        }
                        is StorageRenameCoordinator.RenameResult.NameCollision -> {
                            renameErrorText = result.reason
                        }
                        is StorageRenameCoordinator.RenameResult.Failure -> {
                            renameErrorText = result.reason
                        }
                        is StorageRenameCoordinator.RenameResult.ProviderDenied -> {
                            showRenameDialog = false
                            providerDeniedInfo = result
                        }
                    }
                }
            }
        )
    }

    // External Provider Rename Denied -> Offer Save Copy As
    providerDeniedInfo?.let { denied ->
        ProviderDeniedSaveCopyDialog(
            reason = denied.reason,
            suggestedFileName = denied.suggestedFileName,
            onDismiss = { providerDeniedInfo = null },
            onSaveCopyAs = {
                saveCopyLauncher.launch(denied.suggestedFileName)
            }
        )
    }

    // 4-Option Share Modal Bottom Sheet
    if (showShareSheet && currentDoc != null) {
        ShareOptionsBottomSheet(
            doc = currentDoc!!,
            annotationCount = annotations.size,
            hasWatermark = activeWatermark != null,
            isPreparingShare = isPreparingShare,
            onDismiss = { showShareSheet = false },
            onSelectOption = { option, customName ->
                isPreparingShare = true
                viewModel.prepareShare(option = option, customPdfName = customName) { result ->
                    isPreparingShare = false
                    result.onSuccess { prepared ->
                        showShareSheet = false
                        context.startActivity(ShareCoordinator.buildShareIntent(context, prepared))
                    }.onFailure { err ->
                        viewModel.postStatusMessage(err.localizedMessage ?: "Failed to prepare share file")
                    }
                }
            }
        )
    }

    // Bookmarks Bottom Sheet
    if (showBookmarksSheet) {
        BookmarksBottomSheet(
            currentPage = currentPage,
            bookmarks = currentBookmarks,
            onDismiss = { showBookmarksSheet = false },
            onAddBookmark = { pageIdx, label -> viewModel.addBookmark(pageIdx, label) },
            onSelectBookmark = { pageIdx -> viewModel.setCurrentPage(pageIdx) },
            onDeleteBookmark = { id -> viewModel.deleteBookmark(id) }
        )
    }

    // Permanent Redaction & Metadata Scrub Warning Dialog
    if (showApplyRedactionDialog && currentDoc != null) {
        val redactedPageCount = remember(annotations) {
            annotations.filter { it.type == "REDACT" }.map { it.pageIndex }.distinct().size
        }
        AlertDialog(
            onDismissRequest = { if (!isApplyingRedaction) showApplyRedactionDialog = false },
            containerColor = AcrobatDarkSurfaceElevated,
            icon = {
                Icon(
                    imageVector = Icons.Default.Security,
                    contentDescription = null,
                    tint = Color(0xFFF43F5E),
                    modifier = Modifier.size(32.dp)
                )
            },
            title = {
                Text(
                    text = "Apply Permanent Redaction?",
                    color = AcrobatTextPrimary,
                    fontWeight = FontWeight.Bold
                )
            },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(
                        text = "$redactCount redaction box(es) across $redactedPageCount page(s) will be permanently baked into the PDF.",
                        color = AcrobatTextPrimary,
                        fontSize = 13.sp,
                        fontWeight = FontWeight.SemiBold
                    )
                    Text(
                        text = "• Redacted pages are converted into flattened lossless raster images, permanently destroying underlying text operators, links, and vector layers inside those pages (selectable text on redacted pages will no longer exist).",
                        color = AcrobatTextSecondary,
                        fontSize = 12.sp
                    )
                    Text(
                        text = "• Document metadata (Author, Title, Subject, Keywords, XMP), AcroForm fields, and page annotations are permanently scrubbed.",
                        color = AcrobatTextSecondary,
                        fontSize = 12.sp
                    )
                    Text(
                        text = "• The output PDF is automatically inspected to verify redacted text is unrecoverable before saving.",
                        color = Color(0xFF10B981),
                        fontSize = 12.sp
                    )
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        isApplyingRedaction = true
                        viewModel.applyPermanentRedaction(currentDoc!!, overwriteOriginal = false) { res ->
                            isApplyingRedaction = false
                            showApplyRedactionDialog = false
                            redactionResultInfo = res
                        }
                    },
                    enabled = !isApplyingRedaction,
                    colors = ButtonDefaults.buttonColors(containerColor = AdfElectricCyan, contentColor = AdfMidnightBackground),
                    modifier = Modifier.testTag("confirm_redaction_copy_button")
                ) {
                    if (isApplyingRedaction) {
                        CircularProgressIndicator(color = AdfMidnightBackground, modifier = Modifier.size(16.dp))
                    } else {
                        Text("Save Sanitized Copy", fontWeight = FontWeight.Bold)
                    }
                }
            },
            dismissButton = {
                Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                    TextButton(
                        onClick = {
                            isApplyingRedaction = true
                            viewModel.applyPermanentRedaction(currentDoc!!, overwriteOriginal = true) { res ->
                                isApplyingRedaction = false
                                showApplyRedactionDialog = false
                                redactionResultInfo = res
                            }
                        },
                        enabled = !isApplyingRedaction,
                        modifier = Modifier.testTag("confirm_redaction_overwrite_button")
                    ) {
                        Text("Overwrite Original", color = Color(0xFFF43F5E), fontWeight = FontWeight.SemiBold)
                    }
                    TextButton(
                        onClick = { showApplyRedactionDialog = false },
                        enabled = !isApplyingRedaction
                    ) {
                        Text("Cancel", color = AcrobatTextSecondary)
                    }
                }
            }
        )
    }

    // Post-Redaction Verification Summary Dialog
    redactionResultInfo?.let { res ->
        AlertDialog(
            onDismissRequest = { redactionResultInfo = null },
            containerColor = AcrobatDarkSurfaceElevated,
            icon = {
                Icon(
                    imageVector = if (res.success && res.verifiedUnrecoverable) Icons.Default.VerifiedUser else Icons.Default.ErrorOutline,
                    contentDescription = null,
                    tint = if (res.success && res.verifiedUnrecoverable) Color(0xFF10B981) else Color(0xFFEF4444),
                    modifier = Modifier.size(32.dp)
                )
            },
            title = {
                Text(
                    text = if (res.success && res.verifiedUnrecoverable) "Redaction Verified & Complete" else "Redaction Verification Failed",
                    color = AcrobatTextPrimary,
                    fontWeight = FontWeight.Bold
                )
            },
            text = {
                if (res.success && res.verifiedUnrecoverable) {
                    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        Text("File: ${res.outputFile?.name ?: "Sanitized PDF"}", color = AcrobatTextPrimary, fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
                        Text("• ${res.redactedPages.size} page(s) permanently sanitized (${res.boxesApplied} box(es))", color = AcrobatTextSecondary, fontSize = 12.sp)
                        Text("• ${res.verifiedSnippetsCleared} pre-redaction text region(s) confirmed unrecoverable", color = Color(0xFF10B981), fontSize = 12.sp)
                        Text("• Metadata, XMP, AcroForm & annotations scrubbed: ${if (res.metadataScrubbed) "Verified" else "No"}", color = AcrobatTextSecondary, fontSize = 12.sp)
                    }
                } else {
                    Text(
                        text = res.errorMessage ?: "Sanitization could not be verified.",
                        color = Color(0xFFEF4444),
                        fontSize = 13.sp
                    )
                }
            },
            confirmButton = {
                Button(
                    onClick = { redactionResultInfo = null },
                    colors = ButtonDefaults.buttonColors(containerColor = AdfElectricCyan, contentColor = AdfMidnightBackground)
                ) {
                    Text("Done", fontWeight = FontWeight.Bold)
                }
            }
        )
    }
}

/**
 * Crystalline Specular 3D Glossy Liquid Glass Back Button
 * Renders a glossy glass capsule lens when Liquid Theme is selected,
 * and an adaptive clean back icon for other themes.
 */
@Composable
fun CrystallineGlossyBackButton(
    onClick: () -> Unit,
    isLiquidTheme: Boolean,
    primaryColor: Color = AdfElectricCyan,
    modifier: Modifier = Modifier
) {
    if (isLiquidTheme) {
        Surface(
            onClick = onClick,
            shape = RoundedCornerShape(14.dp),
            color = Color(0x35FFFFFF),
            border = androidx.compose.foundation.BorderStroke(
                width = 1.2.dp,
                brush = androidx.compose.ui.graphics.Brush.linearGradient(
                    listOf(
                        Color.White.copy(alpha = 0.9f),
                        primaryColor.copy(alpha = 0.6f),
                        Color.White.copy(alpha = 0.35f)
                    )
                )
            ),
            shadowElevation = 8.dp,
            modifier = modifier
                .size(42.dp)
                .testTag("reader_back_button")
        ) {
            Box(
                modifier = Modifier.fillMaxSize(),
                contentAlignment = Alignment.Center
            ) {
                // Specular Glass Glossy Arc Reflection
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .fillMaxHeight(0.48f)
                        .align(Alignment.TopCenter)
                        .background(
                            androidx.compose.ui.graphics.Brush.verticalGradient(
                                listOf(
                                    Color.White.copy(alpha = 0.55f),
                                    Color.White.copy(alpha = 0.08f)
                                )
                            )
                        )
                )

                Icon(
                    imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                    contentDescription = "Back",
                    tint = Color.White,
                    modifier = Modifier.size(20.dp)
                )
            }
        }
    } else {
        IconButton(
            onClick = onClick,
            modifier = modifier.testTag("reader_back_button")
        ) {
            Icon(
                imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                contentDescription = "Back",
                tint = MaterialTheme.colorScheme.onSurface
            )
        }
    }
}

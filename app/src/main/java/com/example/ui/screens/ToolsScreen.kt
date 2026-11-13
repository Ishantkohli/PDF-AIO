package com.example.ui.screens

import android.net.Uri
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.CallMerge
import androidx.compose.material.icons.automirrored.filled.RotateRight
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import android.content.Intent
import androidx.compose.ui.platform.LocalContext
import coil.compose.AsyncImage
import com.example.data.local.DocumentEntity
import com.example.data.pdf.DocumentConverterEngine
import com.example.data.pdf.PdfExportEngine
import com.example.data.pdf.PdfMergeItem
import com.example.ui.AppScreen
import com.example.ui.PdfViewModel
import com.example.ui.theme.*
import java.text.SimpleDateFormat
import java.util.*

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ToolsScreen(viewModel: PdfViewModel) {
    val activeTabState by viewModel.selectedToolTab.collectAsStateWithLifecycle()
    var selectedToolTab by remember(activeTabState) { mutableIntStateOf(activeTabState) }
    val documents by viewModel.documents.collectAsStateWithLifecycle()
    val currentDoc by viewModel.currentDocument.collectAsStateWithLifecycle()

    val activeThemeStyle by ThemeManager.currentTheme.collectAsStateWithLifecycle()
    val isLiquidTheme = activeThemeStyle == AppThemeStyle.LIQUID_GLASS || activeThemeStyle == AppThemeStyle.FROSTED_CRYSTAL

    BackHandler {
        viewModel.navigateTo(AppScreen.HOME)
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        text = "PDF Studio Tools",
                        style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                        color = AcrobatTextPrimary
                    )
                },
                navigationIcon = {
                    IconButton(onClick = { viewModel.navigateTo(AppScreen.HOME) }) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = "Back",
                            tint = AcrobatTextPrimary
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = if (isLiquidTheme) Color.Transparent else AcrobatDarkBackground)
            )
        },
        containerColor = if (isLiquidTheme) Color.Transparent else AcrobatDarkBackground
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
        ) {
            // Scrollable Studio Tools Tab Row
            ScrollableTabRow(
                selectedTabIndex = selectedToolTab,
                containerColor = if (isLiquidTheme) GlassSurfaceDark else AcrobatDarkSurface,
                contentColor = CrimsonPrimary,
                edgePadding = 12.dp,
                divider = { HorizontalDivider(color = AcrobatDarkBorder) }
            ) {
                Tab(
                    selected = selectedToolTab == 0,
                    onClick = { selectedToolTab = 0 },
                    text = { Text("Images to PDF", fontWeight = if (selectedToolTab == 0) FontWeight.Bold else FontWeight.Normal) }
                )
                Tab(
                    selected = selectedToolTab == 1,
                    onClick = { selectedToolTab = 1 },
                    text = { Text("PDF to Images", fontWeight = if (selectedToolTab == 1) FontWeight.Bold else FontWeight.Normal) }
                )
                Tab(
                    selected = selectedToolTab == 2,
                    onClick = { selectedToolTab = 2 },
                    text = { Text("Word to PDF", fontWeight = if (selectedToolTab == 2) FontWeight.Bold else FontWeight.Normal) }
                )
                Tab(
                    selected = selectedToolTab == 3,
                    onClick = { selectedToolTab = 3 },
                    text = { Text("PDF to Word", fontWeight = if (selectedToolTab == 3) FontWeight.Bold else FontWeight.Normal) }
                )
                Tab(
                    selected = selectedToolTab == 4,
                    onClick = { selectedToolTab = 4 },
                    text = { Text("Watermark Studio", fontWeight = if (selectedToolTab == 4) FontWeight.Bold else FontWeight.Normal) }
                )
                Tab(
                    selected = selectedToolTab == 5,
                    onClick = { selectedToolTab = 5 },
                    text = { Text("Merge PDFs", fontWeight = if (selectedToolTab == 5) FontWeight.Bold else FontWeight.Normal) }
                )
                Tab(
                    selected = selectedToolTab == 6,
                    onClick = { selectedToolTab = 6 },
                    text = { Text("Organize Pages", fontWeight = if (selectedToolTab == 6) FontWeight.Bold else FontWeight.Normal) }
                )
                Tab(
                    selected = selectedToolTab == 7,
                    onClick = { selectedToolTab = 7 },
                    text = { Text("Compress PDF", fontWeight = if (selectedToolTab == 7) FontWeight.Bold else FontWeight.Normal) }
                )
            }

            when (selectedToolTab) {
                0 -> ImageToPdfTab(viewModel = viewModel)
                1 -> PdfToImagesTab(viewModel = viewModel, documents = documents)
                2 -> WordToPdfTab(viewModel = viewModel)
                3 -> PdfToWordTab(viewModel = viewModel, documents = documents)
                4 -> WatermarkStudioTab(viewModel = viewModel, documents = documents, currentDoc = currentDoc)
                5 -> MergePdfsTab(viewModel = viewModel, documents = documents)
                6 -> OrganizePagesTab(viewModel = viewModel, documents = documents, currentDoc = currentDoc)
                7 -> com.example.ui.screens.tools.PdfCompressorTab(viewModel = viewModel, documents = documents, currentDoc = currentDoc)
            }
        }
    }
}

@Composable
fun ImageToPdfTab(viewModel: PdfViewModel) {
    val selectedImages = remember { mutableStateListOf<Uri>() }
    var pdfTitle by remember { mutableStateOf("Photos_Document") }
    var selectedPageSize by remember { mutableStateOf(DocumentConverterEngine.PdfPageSize.A4) }
    var selectedFitMode by remember { mutableStateOf(DocumentConverterEngine.ImageFitMode.FIT_WITH_MARGINS) }
    var compressionQuality by remember { mutableIntStateOf(90) }
    var isProcessing by remember { mutableStateOf(false) }

    // Modern Android Photo Picker (PNG, JPG, WebP, HEIF, etc.)
    val photoPickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.PickMultipleVisualMedia()
    ) { uris ->
        if (uris.isNotEmpty()) {
            selectedImages.addAll(uris)
        }
    }

    // Generic Image Picker for all formats (including BMP, GIF, WebP, etc.)
    val allImagesPickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetMultipleContents()
    ) { uris ->
        if (uris.isNotEmpty()) {
            selectedImages.addAll(uris)
        }
    }

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
        contentPadding = PaddingValues(bottom = 32.dp)
    ) {
        item {
            Card(
                colors = CardDefaults.cardColors(containerColor = AcrobatDarkSurface),
                shape = RoundedCornerShape(12.dp),
                border = CardDefaults.outlinedCardBorder().copy(brush = androidx.compose.ui.graphics.SolidColor(AcrobatDarkBorder)),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Text(
                        text = "Images to PDF Compiler",
                        style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                        color = AcrobatTextPrimary
                    )
                    Text(
                        text = "Convert any image formats (PNG, JPG, WebP, BMP, GIF, HEIF) into a professional multi-page PDF document.",
                        style = MaterialTheme.typography.bodySmall,
                        color = AcrobatTextSecondary
                    )

                    OutlinedTextField(
                        value = pdfTitle,
                        onValueChange = { pdfTitle = it },
                        label = { Text("Output PDF Title") },
                        singleLine = true,
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedTextColor = AcrobatTextPrimary,
                            unfocusedTextColor = AcrobatTextPrimary,
                            focusedBorderColor = CrimsonPrimary,
                            unfocusedBorderColor = AcrobatDarkBorder,
                            focusedLabelColor = CrimsonAccent,
                            unfocusedLabelColor = AcrobatTextSecondary
                        ),
                        modifier = Modifier.fillMaxWidth()
                    )

                    // Page Size Selection
                    Column {
                        Text("Page Dimensions", style = MaterialTheme.typography.labelSmall, color = AcrobatTextSecondary)
                        Spacer(modifier = Modifier.height(4.dp))
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            DocumentConverterEngine.PdfPageSize.values().forEach { size ->
                                FilterChip(
                                    selected = selectedPageSize == size,
                                    onClick = { selectedPageSize = size },
                                    label = { Text(size.displayName, fontSize = 11.sp) },
                                    colors = FilterChipDefaults.filterChipColors(
                                        selectedContainerColor = CrimsonPrimary,
                                        selectedLabelColor = Color.White
                                    )
                                )
                            }
                        }
                    }

                    // Layout Fit Mode
                    Column {
                        Text("Image Layout Fit", style = MaterialTheme.typography.labelSmall, color = AcrobatTextSecondary)
                        Spacer(modifier = Modifier.height(4.dp))
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            listOf(
                                DocumentConverterEngine.ImageFitMode.FIT_WITH_MARGINS to "Margins",
                                DocumentConverterEngine.ImageFitMode.FULL_BLEED to "Full Bleed",
                                DocumentConverterEngine.ImageFitMode.CENTER_ORIGINAL to "Center"
                            ).forEach { (mode, label) ->
                                FilterChip(
                                    selected = selectedFitMode == mode,
                                    onClick = { selectedFitMode = mode },
                                    label = { Text(label, fontSize = 11.sp) },
                                    colors = FilterChipDefaults.filterChipColors(
                                        selectedContainerColor = CrimsonPrimary,
                                        selectedLabelColor = Color.White
                                    )
                                )
                            }
                        }
                    }

                    // Add Images Buttons
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Button(
                            onClick = {
                                photoPickerLauncher.launch(
                                    PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)
                                )
                            },
                            colors = ButtonDefaults.buttonColors(containerColor = AcrobatDarkSurfaceElevated),
                            modifier = Modifier.weight(1f)
                        ) {
                            Icon(Icons.Default.PhotoLibrary, contentDescription = null, tint = CrimsonAccent, modifier = Modifier.size(18.dp))
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("Photos", color = AcrobatTextPrimary, fontSize = 12.sp)
                        }

                        Button(
                            onClick = { allImagesPickerLauncher.launch("image/*") },
                            colors = ButtonDefaults.buttonColors(containerColor = AcrobatDarkSurfaceElevated),
                            modifier = Modifier.weight(1f)
                        ) {
                            Icon(Icons.Default.AddPhotoAlternate, contentDescription = null, tint = Color(0xFF38BDF8), modifier = Modifier.size(18.dp))
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("All Formats", color = AcrobatTextPrimary, fontSize = 12.sp)
                        }
                    }
                }
            }
        }

        item {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text(
                    text = "Selected Images (${selectedImages.size})",
                    style = MaterialTheme.typography.titleSmall,
                    color = AcrobatTextSecondary
                )
                if (selectedImages.isNotEmpty()) {
                    TextButton(onClick = { selectedImages.clear() }) {
                        Text("Clear All", color = CrimsonPrimary)
                    }
                }
            }
        }

        if (selectedImages.isEmpty()) {
            item {
                Text(
                    text = "No images selected yet. Tap 'Photos' or 'All Formats' above to add images.",
                    style = MaterialTheme.typography.bodySmall,
                    color = AcrobatTextMuted,
                    modifier = Modifier.padding(vertical = 12.dp)
                )
            }
        } else {
            itemsIndexed(selectedImages) { index, uri ->
                Card(
                    colors = CardDefaults.cardColors(containerColor = AcrobatDarkSurface),
                    shape = RoundedCornerShape(10.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        AsyncImage(
                            model = uri,
                            contentDescription = null,
                            contentScale = ContentScale.Crop,
                            modifier = Modifier
                                .size(54.dp)
                                .clip(RoundedCornerShape(8.dp))
                        )
                        Spacer(modifier = Modifier.width(10.dp))
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = "Page ${index + 1}: ${uri.lastPathSegment ?: "Image"}",
                                style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.SemiBold),
                                color = AcrobatTextPrimary,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                            Text("Format: Auto-detected", color = AcrobatTextMuted, fontSize = 11.sp)
                        }

                        // Move Up
                        if (index > 0) {
                            IconButton(
                                onClick = {
                                    val item = selectedImages.removeAt(index)
                                    selectedImages.add(index - 1, item)
                                },
                                modifier = Modifier.size(32.dp)
                            ) {
                                Icon(Icons.Default.ArrowUpward, contentDescription = "Move Up", tint = AcrobatTextSecondary, modifier = Modifier.size(16.dp))
                            }
                        }

                        // Move Down
                        if (index < selectedImages.size - 1) {
                            IconButton(
                                onClick = {
                                    val item = selectedImages.removeAt(index)
                                    selectedImages.add(index + 1, item)
                                },
                                modifier = Modifier.size(32.dp)
                            ) {
                                Icon(Icons.Default.ArrowDownward, contentDescription = "Move Down", tint = AcrobatTextSecondary, modifier = Modifier.size(16.dp))
                            }
                        }

                        IconButton(
                            onClick = { selectedImages.remove(uri) },
                            modifier = Modifier.size(32.dp)
                        ) {
                            Icon(Icons.Default.Delete, contentDescription = "Remove", tint = CrimsonPrimary, modifier = Modifier.size(18.dp))
                        }
                    }
                }
            }

            item {
                Button(
                    onClick = {
                        isProcessing = true
                        viewModel.convertImagesToPdfAdvanced(
                            imageUris = selectedImages,
                            title = pdfTitle,
                            pageSize = selectedPageSize,
                            fitMode = selectedFitMode,
                            compressionQuality = compressionQuality
                        ) {
                            isProcessing = false
                        }
                    },
                    enabled = !isProcessing,
                    colors = ButtonDefaults.buttonColors(containerColor = CrimsonPrimary),
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(50.dp)
                        .testTag("convert_images_to_pdf_button")
                ) {
                    if (isProcessing) {
                        CircularProgressIndicator(color = Color.White, modifier = Modifier.size(24.dp))
                    } else {
                        Icon(Icons.Default.PictureAsPdf, contentDescription = null)
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("Compile into PDF (${selectedImages.size} pages)")
                    }
                }
            }
        }
    }
}

private fun formatFileSizeString(bytes: Long): String {
    if (bytes <= 0) return "0 KB"
    val kb = bytes / 1024.0
    val mb = kb / 1024.0
    return if (mb >= 1.0) {
        String.format(Locale.getDefault(), "%.1f MB", mb)
    } else {
        String.format(Locale.getDefault(), "%.0f KB", kb)
    }
}

@Composable
fun MergePdfsTab(viewModel: PdfViewModel, documents: List<DocumentEntity>) {
    val context = LocalContext.current

    val pdfMergeQueue = remember { mutableStateListOf<PdfMergeItem>() }
    var mergedTitle by remember {
        mutableStateOf("Merged_Master_${SimpleDateFormat("dd_MMM_yyyy", Locale.getDefault()).format(Date())}")
    }
    var insertSeparators by remember { mutableStateOf(false) }
    var isMerging by remember { mutableStateOf(false) }
    var showLibraryPickerModal by remember { mutableStateOf(false) }
    var mergedSuccessDoc by remember { mutableStateOf<DocumentEntity?>(null) }

    // SAF File Picker for device storage PDFs
    val safPdfPickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenMultipleDocuments()
    ) { uris ->
        if (uris.isNotEmpty()) {
            uris.forEach { uri ->
                val fileName = PdfExportEngine.getFileNameFromUri(context, uri)
                val pageCount = PdfExportEngine.getPdfPageCountFromUri(context, uri)
                val fileSize = PdfExportEngine.getFileSizeBytesFromUri(context, uri)
                pdfMergeQueue.add(
                    PdfMergeItem(
                        uri = uri,
                        title = fileName,
                        pageCount = pageCount,
                        fileSizeBytes = fileSize,
                        isFromLibrary = false
                    )
                )
            }
        }
    }

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
        contentPadding = PaddingValues(bottom = 32.dp)
    ) {
        // 1. Header & Configuration Card
        item {
            Card(
                colors = CardDefaults.cardColors(containerColor = AcrobatDarkSurface),
                shape = RoundedCornerShape(14.dp),
                border = CardDefaults.outlinedCardBorder().copy(brush = androidx.compose.ui.graphics.SolidColor(AcrobatDarkBorder)),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp)
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Box(
                            modifier = Modifier
                                .size(36.dp)
                                .clip(RoundedCornerShape(8.dp))
                                .background(AdfAmber.copy(alpha = 0.15f)),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = Icons.AutoMirrored.Filled.CallMerge,
                                contentDescription = null,
                                tint = AdfAmber
                            )
                        }
                        Spacer(modifier = Modifier.width(10.dp))
                        Column {
                            Text(
                                text = "PDF File Merging Utility",
                                style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                                color = AcrobatTextPrimary
                            )
                            Text(
                                text = "Batch combine PDF files into a single master document",
                                style = MaterialTheme.typography.bodySmall,
                                color = AcrobatTextSecondary
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(14.dp))

                    OutlinedTextField(
                        value = mergedTitle,
                        onValueChange = { mergedTitle = it },
                        label = { Text("Output PDF Title", color = AdfAmber) },
                        singleLine = true,
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedContainerColor = AcrobatDarkSurfaceElevated,
                            unfocusedContainerColor = AcrobatDarkSurfaceElevated,
                            focusedTextColor = AcrobatTextPrimary,
                            unfocusedTextColor = AcrobatTextPrimary,
                            focusedBorderColor = AdfAmber
                        ),
                        shape = RoundedCornerShape(10.dp),
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("merged_pdf_title_input")
                    )

                    Spacer(modifier = Modifier.height(10.dp))

                    // Separator Sheet Option Toggle
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(8.dp))
                            .background(AcrobatDarkSurfaceElevated)
                            .padding(horizontal = 12.dp, vertical = 6.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = "Insert Section Separators",
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Bold,
                                color = AcrobatTextPrimary
                            )
                            Text(
                                text = "Adds a clean white separator sheet between documents",
                                fontSize = 10.sp,
                                color = AcrobatTextMuted
                            )
                        }
                        Switch(
                            checked = insertSeparators,
                            onCheckedChange = { insertSeparators = it },
                            colors = SwitchDefaults.colors(checkedThumbColor = AdfAmber, checkedTrackColor = AdfAmber.copy(alpha = 0.3f)),
                            modifier = Modifier.testTag("insert_separators_switch")
                        )
                    }
                }
            }
        }

        // 2. Add Documents Action Dock
        item {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                // Add from Device Storage (SAF)
                Button(
                    onClick = {
                        safPdfPickerLauncher.launch(arrayOf("application/pdf"))
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = AdfAmber),
                    shape = RoundedCornerShape(10.dp),
                    modifier = Modifier
                        .weight(1f)
                        .height(46.dp)
                        .testTag("pick_device_pdfs_button")
                ) {
                    Icon(imageVector = Icons.Default.FolderOpen, contentDescription = null, tint = AcrobatDarkBackground, modifier = Modifier.size(18.dp))
                    Spacer(modifier = Modifier.width(6.dp))
                    Text("Device Files", color = AcrobatDarkBackground, fontWeight = FontWeight.Bold, fontSize = 12.sp)
                }

                // Add from App Library
                OutlinedButton(
                    onClick = { showLibraryPickerModal = true },
                    shape = RoundedCornerShape(10.dp),
                    colors = ButtonDefaults.outlinedButtonColors(contentColor = AdfAmber),
                    border = BorderStroke(1.dp, AdfAmber),
                    modifier = Modifier
                        .weight(1f)
                        .height(46.dp)
                        .testTag("pick_library_pdfs_button")
                ) {
                    Icon(imageVector = Icons.Default.LibraryBooks, contentDescription = null, tint = AdfAmber, modifier = Modifier.size(18.dp))
                    Spacer(modifier = Modifier.width(6.dp))
                    Text("App Library", fontWeight = FontWeight.Bold, fontSize = 12.sp)
                }
            }
        }

        // 3. Queue Section Title & Metrics Summary Card
        item {
            Card(
                colors = CardDefaults.cardColors(containerColor = AcrobatDarkSurfaceElevated),
                shape = RoundedCornerShape(10.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 14.dp, vertical = 10.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            text = "MERGE QUEUE (${pdfMergeQueue.size} FILES)",
                            fontSize = 11.sp,
                            fontWeight = FontWeight.ExtraBold,
                            color = AcrobatTextSecondary
                        )
                    }

                    val totalPages = pdfMergeQueue.sumOf { it.pageCount } + (if (insertSeparators && pdfMergeQueue.size > 1) pdfMergeQueue.size - 1 else 0)
                    val totalBytes = pdfMergeQueue.sumOf { it.fileSizeBytes }
                    Text(
                        text = "$totalPages Total Pages • ${formatFileSizeString(totalBytes)}",
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold,
                        color = AdfAmber
                    )
                }
            }
        }

        // 4. Merge Queue Items with Re-ordering Controls
        if (pdfMergeQueue.isEmpty()) {
            item {
                Surface(
                    shape = RoundedCornerShape(12.dp),
                    color = AcrobatDarkSurface,
                    border = BorderStroke(1.dp, AcrobatDarkBorder),
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 12.dp)
                ) {
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        modifier = Modifier.padding(24.dp)
                    ) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.CallMerge,
                            contentDescription = null,
                            tint = AcrobatTextMuted,
                            modifier = Modifier.size(40.dp)
                        )
                        Spacer(modifier = Modifier.height(10.dp))
                        Text(
                            text = "No PDF Files Added Yet",
                            fontWeight = FontWeight.Bold,
                            color = AcrobatTextPrimary,
                            fontSize = 14.sp
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = "Tap 'Device Files' or 'App Library' above to choose 2 or more PDFs to combine.",
                            color = AcrobatTextMuted,
                            fontSize = 11.sp,
                            textAlign = androidx.compose.ui.text.style.TextAlign.Center
                        )
                    }
                }
            }
        } else {
            itemsIndexed(pdfMergeQueue, key = { _, item -> item.id }) { index, item ->
                Card(
                    colors = CardDefaults.cardColors(containerColor = AcrobatDarkSurface),
                    shape = RoundedCornerShape(10.dp),
                    border = BorderStroke(1.dp, AcrobatDarkBorder),
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("merge_queue_item_$index")
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(10.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        // Position Index Pill
                        Box(
                            modifier = Modifier
                                .size(30.dp)
                                .clip(CircleShape)
                                .background(AdfAmber.copy(alpha = 0.2f)),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text = "${index + 1}",
                                fontSize = 12.sp,
                                fontWeight = FontWeight.ExtraBold,
                                color = AdfAmber
                            )
                        }

                        Spacer(modifier = Modifier.width(10.dp))

                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = item.title,
                                style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold),
                                color = AcrobatTextPrimary,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Text(
                                    text = "${item.pageCount} Pages • ${formatFileSizeString(item.fileSizeBytes)}",
                                    fontSize = 11.sp,
                                    color = AcrobatTextSecondary
                                )
                                Spacer(modifier = Modifier.width(6.dp))
                                Surface(
                                    shape = RoundedCornerShape(4.dp),
                                    color = if (item.isFromLibrary) CrimsonPrimary.copy(alpha = 0.15f) else AdfElectricCyan.copy(alpha = 0.15f)
                                ) {
                                    Text(
                                        text = if (item.isFromLibrary) "LIBRARY" else "STORAGE",
                                        color = if (item.isFromLibrary) CrimsonPrimary else AdfElectricCyan,
                                        fontSize = 8.sp,
                                        fontWeight = FontWeight.Bold,
                                        modifier = Modifier.padding(horizontal = 4.dp, vertical = 1.dp)
                                    )
                                }
                            }
                        }

                        // Re-order Move Up Button
                        IconButton(
                            onClick = {
                                if (index > 0) {
                                    val temp = pdfMergeQueue[index]
                                    pdfMergeQueue[index] = pdfMergeQueue[index - 1]
                                    pdfMergeQueue[index - 1] = temp
                                }
                            },
                            enabled = index > 0,
                            modifier = Modifier.size(32.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.ArrowUpward,
                                contentDescription = "Move Up",
                                tint = if (index > 0) AdfAmber else AcrobatTextMuted,
                                modifier = Modifier.size(18.dp)
                            )
                        }

                        // Re-order Move Down Button
                        IconButton(
                            onClick = {
                                if (index < pdfMergeQueue.size - 1) {
                                    val temp = pdfMergeQueue[index]
                                    pdfMergeQueue[index] = pdfMergeQueue[index + 1]
                                    pdfMergeQueue[index + 1] = temp
                                }
                            },
                            enabled = index < pdfMergeQueue.size - 1,
                            modifier = Modifier.size(32.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.ArrowDownward,
                                contentDescription = "Move Down",
                                tint = if (index < pdfMergeQueue.size - 1) AdfAmber else AcrobatTextMuted,
                                modifier = Modifier.size(18.dp)
                            )
                        }

                        // Remove Button
                        IconButton(
                            onClick = { pdfMergeQueue.removeAt(index) },
                            modifier = Modifier.size(32.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.Close,
                                contentDescription = "Remove File",
                                tint = CrimsonPrimary,
                                modifier = Modifier.size(18.dp)
                            )
                        }
                    }
                }
            }
        }

        // 5. Final Merge Execution Action Button
        item {
            Spacer(modifier = Modifier.height(6.dp))
            Button(
                onClick = {
                    if (pdfMergeQueue.size >= 2) {
                        isMerging = true
                        viewModel.mergePdfsFromItems(
                            pdfItems = pdfMergeQueue.toList(),
                            mergedTitle = mergedTitle,
                            insertSeparators = insertSeparators,
                            onDone = { doc ->
                                isMerging = false
                                mergedSuccessDoc = doc
                            }
                        )
                    }
                },
                enabled = pdfMergeQueue.size >= 2 && !isMerging,
                colors = ButtonDefaults.buttonColors(containerColor = AdfAmber),
                shape = RoundedCornerShape(12.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .height(52.dp)
                    .testTag("merge_pdfs_action_button")
            ) {
                if (isMerging) {
                    CircularProgressIndicator(color = AcrobatDarkBackground, modifier = Modifier.size(24.dp), strokeWidth = 2.dp)
                    Spacer(modifier = Modifier.width(10.dp))
                    Text("Combining ${pdfMergeQueue.size} PDFs...", color = AcrobatDarkBackground, fontWeight = FontWeight.ExtraBold)
                } else {
                    Icon(Icons.AutoMirrored.Filled.CallMerge, contentDescription = null, tint = AcrobatDarkBackground)
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = if (pdfMergeQueue.size < 2) "Add at least 2 PDFs to Merge" else "Merge ${pdfMergeQueue.size} PDF Files Now",
                        color = AcrobatDarkBackground,
                        fontWeight = FontWeight.ExtraBold,
                        fontSize = 14.sp
                    )
                }
            }
        }
    }

    // Modal: App Library PDF Selection Dialog
    if (showLibraryPickerModal) {
        val selectedIds = remember { mutableStateListOf<Long>() }
        AlertDialog(
            onDismissRequest = { showLibraryPickerModal = false },
            containerColor = AcrobatDarkSurface,
            shape = RoundedCornerShape(16.dp),
            title = {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Default.LibraryBooks, contentDescription = null, tint = AdfAmber)
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("Select PDFs from App Library", fontSize = 16.sp, fontWeight = FontWeight.Bold, color = AcrobatTextPrimary)
                }
            },
            text = {
                Column(modifier = Modifier.fillMaxWidth()) {
                    if (documents.isEmpty()) {
                        Text("No PDFs found in your app library.", color = AcrobatTextMuted, fontSize = 12.sp)
                    } else {
                        LazyColumn(
                            modifier = Modifier.heightIn(max = 280.dp),
                            verticalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            items(documents, key = { it.id }) { doc ->
                                val checked = selectedIds.contains(doc.id)
                                Surface(
                                    onClick = {
                                        if (checked) selectedIds.remove(doc.id) else selectedIds.add(doc.id)
                                    },
                                    shape = RoundedCornerShape(8.dp),
                                    color = if (checked) AdfAmber.copy(alpha = 0.15f) else AcrobatDarkSurfaceElevated,
                                    border = BorderStroke(1.dp, if (checked) AdfAmber else AcrobatDarkBorder),
                                    modifier = Modifier.fillMaxWidth()
                                ) {
                                    Row(
                                        verticalAlignment = Alignment.CenterVertically,
                                        modifier = Modifier.padding(10.dp)
                                    ) {
                                        Checkbox(
                                            checked = checked,
                                            onCheckedChange = { isChecked ->
                                                if (isChecked) selectedIds.add(doc.id) else selectedIds.remove(doc.id)
                                            },
                                            colors = CheckboxDefaults.colors(checkedColor = AdfAmber)
                                        )
                                        Spacer(modifier = Modifier.width(6.dp))
                                        Column(modifier = Modifier.weight(1f)) {
                                            Text(doc.title, fontWeight = FontWeight.Bold, color = AcrobatTextPrimary, fontSize = 12.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                            Text("${doc.pageCount} Pages • ${formatFileSizeString(doc.fileSizeBytes)}", color = AcrobatTextSecondary, fontSize = 10.sp)
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        val selectedDocs = documents.filter { selectedIds.contains(it.id) }
                        selectedDocs.forEach { doc ->
                            pdfMergeQueue.add(
                                PdfMergeItem(
                                    uri = Uri.parse(doc.uriString),
                                    title = doc.title,
                                    pageCount = doc.pageCount,
                                    fileSizeBytes = doc.fileSizeBytes,
                                    isFromLibrary = true
                                )
                            )
                        }
                        showLibraryPickerModal = false
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = AdfAmber),
                    shape = RoundedCornerShape(8.dp),
                    enabled = selectedIds.isNotEmpty()
                ) {
                    Text("Add Selected (${selectedIds.size})", color = AcrobatDarkBackground, fontWeight = FontWeight.Bold)
                }
            },
            dismissButton = {
                OutlinedButton(
                    onClick = { showLibraryPickerModal = false },
                    shape = RoundedCornerShape(8.dp)
                ) {
                    Text("Cancel", color = AcrobatTextMuted)
                }
            }
        )
    }

    // Modal: PDF Merging Success Dialog
    mergedSuccessDoc?.let { doc ->
        AlertDialog(
            onDismissRequest = { mergedSuccessDoc = null },
            containerColor = AcrobatDarkSurface,
            shape = RoundedCornerShape(18.dp),
            title = {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(
                        modifier = Modifier
                            .size(38.dp)
                            .clip(RoundedCornerShape(8.dp))
                            .background(AdfEmerald.copy(alpha = 0.15f)),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(Icons.Default.PictureAsPdf, contentDescription = null, tint = AdfEmerald)
                    }
                    Spacer(modifier = Modifier.width(10.dp))
                    Text("PDFs Merged Successfully!", color = AcrobatTextPrimary, fontWeight = FontWeight.Bold, fontSize = 17.sp)
                }
            },
            text = {
                Column {
                    Text(
                        text = "Your PDF documents have been compiled into a single unified master document.",
                        color = AcrobatTextSecondary,
                        fontSize = 13.sp
                    )
                    Spacer(modifier = Modifier.height(10.dp))
                    Surface(
                        shape = RoundedCornerShape(8.dp),
                        color = AcrobatDarkSurfaceElevated,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(modifier = Modifier.padding(12.dp)) {
                            Text(doc.title, fontWeight = FontWeight.Bold, color = AcrobatTextPrimary, fontSize = 13.sp)
                            Spacer(modifier = Modifier.height(2.dp))
                            Text("${doc.pageCount} Pages • ${formatFileSizeString(doc.fileSizeBytes)} • Saved to device", color = AdfElectricCyan, fontSize = 11.sp)
                        }
                    }
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        mergedSuccessDoc = null
                        viewModel.openDocument(doc)
                        viewModel.navigateTo(AppScreen.READER)
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = AdfAmber),
                    shape = RoundedCornerShape(10.dp),
                    modifier = Modifier.testTag("open_merged_pdf_button")
                ) {
                    Text("Open & View PDF", color = AcrobatDarkBackground, fontWeight = FontWeight.Bold)
                }
            },
            dismissButton = {
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    OutlinedButton(
                        onClick = {
                            val shareUri = Uri.parse(doc.uriString)
                            val intent = Intent(Intent.ACTION_SEND).apply {
                                type = "application/pdf"
                                putExtra(Intent.EXTRA_STREAM, shareUri)
                                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                            }
                            context.startActivity(Intent.createChooser(intent, "Share Merged PDF"))
                        },
                        shape = RoundedCornerShape(10.dp)
                    ) {
                        Text("Share", color = AdfAmber)
                    }
                    OutlinedButton(
                        onClick = {
                            mergedSuccessDoc = null
                            pdfMergeQueue.clear()
                        },
                        shape = RoundedCornerShape(10.dp)
                    ) {
                        Text("New Merge", color = AcrobatTextMuted)
                    }
                }
            }
        )
    }
}

@Composable
fun OrganizePagesTab(
    viewModel: PdfViewModel,
    documents: List<DocumentEntity>,
    currentDoc: DocumentEntity?
) {
    var targetDoc by remember { mutableStateOf(currentDoc ?: documents.firstOrNull()) }
    val totalPages = targetDoc?.pageCount ?: 0
    val pageBitmaps by viewModel.pageBitmaps.collectAsStateWithLifecycle()

    val includedPages = remember(targetDoc) {
        mutableStateListOf<Int>().apply {
            addAll(0 until totalPages)
        }
    }
    val pageRotations = remember(targetDoc) { mutableStateMapOf<Int, Int>() }
    var newTitle by remember(targetDoc) { mutableStateOf("${targetDoc?.title ?: "Doc"}_reorganized") }

    LaunchedEffect(targetDoc) {
        val doc = targetDoc ?: return@LaunchedEffect
        for (i in 0 until doc.pageCount) {
            viewModel.renderPage(i)
        }
    }

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
        contentPadding = PaddingValues(bottom = 32.dp)
    ) {
        item {
            Card(
                colors = CardDefaults.cardColors(containerColor = AcrobatDarkSurface),
                shape = RoundedCornerShape(12.dp),
                border = CardDefaults.outlinedCardBorder().copy(brush = androidx.compose.ui.graphics.SolidColor(AcrobatDarkBorder)),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp)
                ) {
                    Text(
                        text = "Organize, Rotate & Delete Pages",
                        style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                        color = AcrobatTextPrimary
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = "Rotate individual pages 90° or deselect pages you want to delete.",
                        style = MaterialTheme.typography.bodySmall,
                        color = AcrobatTextSecondary
                    )

                    Spacer(modifier = Modifier.height(10.dp))

                    Text(
                        text = "Target Document: ${targetDoc?.title ?: "None"}",
                        style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.SemiBold),
                        color = CrimsonAccent
                    )

                    Spacer(modifier = Modifier.height(10.dp))

                    OutlinedTextField(
                        value = newTitle,
                        onValueChange = { newTitle = it },
                        label = { Text("Output Title") },
                        singleLine = true,
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedContainerColor = AcrobatDarkSurfaceElevated,
                            unfocusedContainerColor = AcrobatDarkSurfaceElevated,
                            focusedTextColor = AcrobatTextPrimary,
                            unfocusedTextColor = AcrobatTextPrimary,
                            focusedBorderColor = CrimsonPrimary
                        ),
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            }
        }

        if (totalPages > 0 && targetDoc != null) {
            item {
                Text(
                    text = "Pages Grid (${includedPages.size} of $totalPages kept):",
                    style = MaterialTheme.typography.titleSmall,
                    color = AcrobatTextSecondary
                )
            }

            item {
                LazyVerticalGrid(
                    columns = GridCells.Fixed(2),
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(max = 480.dp)
                ) {
                    items(totalPages) { pageIdx ->
                        val isKept = includedPages.contains(pageIdx)
                        val rot = pageRotations[pageIdx] ?: 0
                        val bmp = pageBitmaps[pageIdx]

                        Card(
                            colors = CardDefaults.cardColors(
                                containerColor = if (isKept) AcrobatDarkSurface else Color(0xFF1E1E24).copy(alpha = 0.5f)
                            ),
                            shape = RoundedCornerShape(10.dp),
                            border = if (isKept) CardDefaults.outlinedCardBorder().copy(brush = androidx.compose.ui.graphics.SolidColor(AcrobatDarkBorder)) else null,
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(180.dp)
                        ) {
                            Column(modifier = Modifier.fillMaxSize()) {
                                Box(
                                    modifier = Modifier
                                        .weight(1f)
                                        .fillMaxWidth()
                                        .background(Color.White),
                                    contentAlignment = Alignment.Center
                                ) {
                                    if (bmp != null) {
                                        Image(
                                            bitmap = bmp.asImageBitmap(),
                                            contentDescription = null,
                                            modifier = Modifier.fillMaxSize()
                                        )
                                    } else {
                                        Icon(Icons.Default.Description, contentDescription = null, tint = Color.Gray)
                                    }
                                }

                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .background(AcrobatDarkSurfaceElevated)
                                        .padding(horizontal = 6.dp, vertical = 4.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.SpaceBetween
                                ) {
                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        Checkbox(
                                            checked = isKept,
                                            onCheckedChange = { checked ->
                                                if (checked) includedPages.add(pageIdx) else includedPages.remove(pageIdx)
                                            },
                                            colors = CheckboxDefaults.colors(checkedColor = CrimsonPrimary),
                                            modifier = Modifier.size(28.dp)
                                        )
                                        Text(
                                            text = "P${pageIdx + 1}",
                                            style = MaterialTheme.typography.labelMedium,
                                            color = AcrobatTextPrimary
                                        )
                                    }

                                    IconButton(
                                        onClick = {
                                            val current = pageRotations[pageIdx] ?: 0
                                            pageRotations[pageIdx] = (current + 90) % 360
                                        },
                                        modifier = Modifier.size(28.dp)
                                    ) {
                                        Icon(
                                            imageVector = Icons.AutoMirrored.Filled.RotateRight,
                                            contentDescription = "Rotate 90",
                                            tint = if (rot != 0) CrimsonAccent else AcrobatTextSecondary,
                                            modifier = Modifier.size(18.dp)
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }

            item {
                Button(
                    onClick = {
                        val doc = targetDoc ?: return@Button
                        viewModel.organizeDocumentPages(
                            doc = doc,
                            includedIndices = includedPages.toList(),
                            rotations = pageRotations.toMap(),
                            newTitle = newTitle
                        ) {}
                    },
                    enabled = includedPages.isNotEmpty(),
                    colors = ButtonDefaults.buttonColors(containerColor = CrimsonPrimary),
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(50.dp)
                ) {
                    Icon(Icons.Default.Check, contentDescription = null)
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("Export Reorganized PDF")
                }
            }
        }
    }
}

package com.example.ui.screens

import android.net.Uri
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
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
import coil.compose.AsyncImage
import com.example.data.local.DocumentEntity
import com.example.data.pdf.DocumentConverterEngine
import com.example.ui.AppScreen
import com.example.ui.PdfViewModel
import com.example.ui.theme.*

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ToolsScreen(viewModel: PdfViewModel) {
    val activeTabState by viewModel.selectedToolTab.collectAsStateWithLifecycle()
    var selectedToolTab by remember(activeTabState) { mutableIntStateOf(activeTabState) }
    val documents by viewModel.documents.collectAsStateWithLifecycle()
    val currentDoc by viewModel.currentDocument.collectAsStateWithLifecycle()

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
                colors = TopAppBarDefaults.topAppBarColors(containerColor = AcrobatDarkBackground)
            )
        },
        containerColor = AcrobatDarkBackground
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
        ) {
            // Scrollable Studio Tools Tab Row
            ScrollableTabRow(
                selectedTabIndex = selectedToolTab,
                containerColor = AcrobatDarkSurface,
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
            }

            when (selectedToolTab) {
                0 -> ImageToPdfTab(viewModel = viewModel)
                1 -> PdfToImagesTab(viewModel = viewModel, documents = documents)
                2 -> WordToPdfTab(viewModel = viewModel)
                3 -> PdfToWordTab(viewModel = viewModel, documents = documents)
                4 -> WatermarkStudioTab(viewModel = viewModel, documents = documents, currentDoc = currentDoc)
                5 -> MergePdfsTab(viewModel = viewModel, documents = documents)
                6 -> OrganizePagesTab(viewModel = viewModel, documents = documents, currentDoc = currentDoc)
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

@Composable
fun MergePdfsTab(viewModel: PdfViewModel, documents: List<DocumentEntity>) {
    val selectedDocIds = remember { mutableStateListOf<Long>() }
    var mergedTitle by remember { mutableStateOf("Merged_Master_Document") }
    var isMerging by remember { mutableStateOf(false) }

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
                        text = "Combine Multiple PDFs into One",
                        style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                        color = AcrobatTextPrimary
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = "Select 2 or more PDF files from your library to merge them in sequence.",
                        style = MaterialTheme.typography.bodySmall,
                        color = AcrobatTextSecondary
                    )

                    Spacer(modifier = Modifier.height(14.dp))

                    OutlinedTextField(
                        value = mergedTitle,
                        onValueChange = { mergedTitle = it },
                        label = { Text("Merged File Title") },
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

        item {
            Text(
                text = "Select Files to Merge (${selectedDocIds.size} selected):",
                style = MaterialTheme.typography.titleSmall,
                color = AcrobatTextSecondary
            )
        }

        items(documents, key = { it.id }) { doc ->
            val isChecked = selectedDocIds.contains(doc.id)
            Card(
                onClick = {
                    if (isChecked) selectedDocIds.remove(doc.id) else selectedDocIds.add(doc.id)
                },
                colors = CardDefaults.cardColors(containerColor = AcrobatDarkSurface),
                border = if (isChecked) CardDefaults.outlinedCardBorder().copy(brush = androidx.compose.ui.graphics.SolidColor(CrimsonPrimary)) else null,
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(12.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Checkbox(
                        checked = isChecked,
                        onCheckedChange = { checked ->
                            if (checked) selectedDocIds.add(doc.id) else selectedDocIds.remove(doc.id)
                        },
                        colors = CheckboxDefaults.colors(checkedColor = CrimsonPrimary)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = doc.title,
                            style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.SemiBold),
                            color = AcrobatTextPrimary
                        )
                        Text(
                            text = "${doc.pageCount} pages",
                            style = MaterialTheme.typography.bodySmall,
                            color = AcrobatTextMuted
                        )
                    }
                }
            }
        }

        item {
            Button(
                onClick = {
                    val docsToMerge = documents.filter { selectedDocIds.contains(it.id) }
                    if (docsToMerge.size >= 2) {
                        isMerging = true
                        viewModel.mergePdfs(docsToMerge, mergedTitle) {
                            isMerging = false
                        }
                    }
                },
                enabled = selectedDocIds.size >= 2 && !isMerging,
                colors = ButtonDefaults.buttonColors(containerColor = CrimsonPrimary),
                modifier = Modifier
                    .fillMaxWidth()
                    .height(50.dp)
                    .testTag("merge_pdfs_action_button")
            ) {
                if (isMerging) {
                    CircularProgressIndicator(color = Color.White, modifier = Modifier.size(24.dp))
                } else {
                    Icon(Icons.AutoMirrored.Filled.CallMerge, contentDescription = null)
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("Merge ${selectedDocIds.size} Documents")
                }
            }
        }
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

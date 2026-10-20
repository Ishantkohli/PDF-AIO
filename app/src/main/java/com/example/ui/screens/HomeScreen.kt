package com.example.ui.screens

import android.content.Context
import android.text.format.Formatter
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.R
import com.example.data.fps.DisplayRefreshRateManager
import com.example.data.local.DeviceFileItem
import com.example.data.local.DeviceFileType
import com.example.data.local.DocumentEntity
import com.example.ui.AppScreen
import com.example.ui.PdfViewModel
import com.example.ui.components.AdfIcon
import com.example.ui.components.CoreSuiteMenuSheet
import com.example.ui.components.PDFViewer
import com.example.ui.components.PDFViewerDialog
import com.example.ui.components.RefreshRateDialog
import com.example.ui.theme.*
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Unified representation of a document for ADF AIO home list
 */
data class UnifiedDocItem(
    val id: String,
    val title: String,
    val isPdf: Boolean,
    val sizeBytes: Long,
    val dateModified: Long,
    val pageCount: Int = 0,
    val isFavorite: Boolean = false,
    val isSample: Boolean = false,
    val originalDoc: DocumentEntity? = null,
    val deviceFile: DeviceFileItem? = null
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HomeScreen(viewModel: PdfViewModel) {
    val documents by viewModel.documents.collectAsStateWithLifecycle()
    val deviceFiles by viewModel.deviceFiles.collectAsStateWithLifecycle()
    val isScanning by viewModel.isScanningDeviceFiles.collectAsStateWithLifecycle()
    val currentRefreshRate by DisplayRefreshRateManager.currentRefreshRate.collectAsStateWithLifecycle()
    val showFpsHud by viewModel.showFpsHud.collectAsStateWithLifecycle()

    val context = LocalContext.current
    var searchQuery by remember { mutableStateOf("") }
    var selectedFilterTab by remember { mutableIntStateOf(0) } // 0: All, 1: PDFs, 2: Word Docs, 3: Starred
    var showRefreshRateDialog by remember { mutableStateOf(false) }
    var showCoreSuiteMenu by remember { mutableStateOf(false) }
    var previewDoc by remember { mutableStateOf<UnifiedDocItem?>(null) }

    // Auto-scan device documents on mount
    LaunchedEffect(Unit) {
        viewModel.scanDeviceDocuments()
    }

    // System file picker for opening any PDF or Word document
    val documentPickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument()
    ) { uri ->
        uri?.let {
            viewModel.importPdfFromUri(it)
        }
    }

    // Combine database documents with scanned device documents without duplication
    val unifiedDocs = remember(documents, deviceFiles) {
        val list = mutableListOf<UnifiedDocItem>()

        // 1. Database documents
        documents.forEach { doc ->
            list.add(
                UnifiedDocItem(
                    id = "db_${doc.id}",
                    title = doc.title,
                    isPdf = true,
                    sizeBytes = doc.fileSizeBytes,
                    dateModified = doc.lastOpenedTimestamp,
                    pageCount = doc.pageCount,
                    isFavorite = doc.isFavorite,
                    isSample = doc.isSample,
                    originalDoc = doc
                )
            )
        }

        // 2. Device files scanned from storage
        deviceFiles.forEach { file ->
            val alreadyInDb = list.any { it.title.equals(file.title, ignoreCase = true) }
            if (!alreadyInDb) {
                list.add(
                    UnifiedDocItem(
                        id = "dev_${file.id}",
                        title = file.title,
                        isPdf = file.fileType == DeviceFileType.PDF,
                        sizeBytes = file.sizeBytes,
                        dateModified = file.dateModified,
                        pageCount = if (file.fileType == DeviceFileType.PDF) 1 else 0,
                        isFavorite = false,
                        isSample = false,
                        deviceFile = file
                    )
                )
            }
        }

        list.sortedByDescending { it.dateModified }
    }

    // Filter documents based on search query and category tab
    val filteredDocs = remember(unifiedDocs, searchQuery, selectedFilterTab) {
        unifiedDocs.filter { doc ->
            val matchesQuery = doc.title.contains(searchQuery, ignoreCase = true)
            val matchesTab = when (selectedFilterTab) {
                1 -> doc.isPdf
                2 -> !doc.isPdf // Word / Other
                3 -> doc.isFavorite
                else -> true
            }
            matchesQuery && matchesTab
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
                                .clip(RoundedCornerShape(9.dp))
                                .background(
                                    Brush.linearGradient(
                                        listOf(AdfElectricCyan.copy(alpha = 0.25f), AdfVibrantBlue.copy(alpha = 0.25f))
                                    )
                                )
                                .border(
                                    width = 1.dp,
                                    brush = Brush.linearGradient(listOf(AdfElectricCyan, AdfVibrantBlue)),
                                    shape = RoundedCornerShape(9.dp)
                                ),
                            contentAlignment = Alignment.Center
                        ) {
                            Image(
                                painter = painterResource(id = R.drawable.pdf_aio_logo),
                                contentDescription = "ADF AIO Emblem",
                                modifier = Modifier.size(26.dp)
                            )
                        }
                        Spacer(modifier = Modifier.width(10.dp))
                        Column {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Text(
                                    text = "ADF AIO",
                                    style = MaterialTheme.typography.titleMedium.copy(
                                        fontWeight = FontWeight.ExtraBold,
                                        letterSpacing = 0.8.sp
                                    ),
                                    color = AdfTextPrimary
                                )
                                Spacer(modifier = Modifier.width(6.dp))
                                Surface(
                                    shape = RoundedCornerShape(4.dp),
                                    color = AdfElectricCyan.copy(alpha = 0.12f),
                                    border = CardDefaults.outlinedCardBorder().copy(
                                        brush = Brush.horizontalGradient(listOf(AdfElectricCyan.copy(alpha = 0.5f), AdfVibrantBlue.copy(alpha = 0.5f)))
                                    )
                                ) {
                                    Text(
                                        text = "${currentRefreshRate}Hz",
                                        color = AdfElectricCyan,
                                        fontSize = 9.sp,
                                        fontWeight = FontWeight.Bold,
                                        modifier = Modifier.padding(horizontal = 5.dp, vertical = 2.dp)
                                    )
                                }
                            }
                            Text(
                                text = "ULTRA DOCUMENT SUITE",
                                style = MaterialTheme.typography.bodySmall.copy(
                                    fontSize = 9.sp,
                                    letterSpacing = 1.2.sp,
                                    fontWeight = FontWeight.SemiBold
                                ),
                                color = AdfElectricCyan
                            )
                        }
                    }
                },
                actions = {
                    // Rescan Device Storage
                    IconButton(
                        onClick = { viewModel.scanDeviceDocuments() },
                        modifier = Modifier.testTag("scan_device_docs_button")
                    ) {
                        AdfIcon.Conversion(
                            size = 20.dp,
                            isAnimated = isScanning,
                            primaryColor = if (isScanning) AdfElectricCyan else AdfTextSecondary
                        )
                    }

                    // FPS & Refresh Rate Configuration
                    IconButton(
                        onClick = { showRefreshRateDialog = true },
                        modifier = Modifier.testTag("fps_rate_top_button")
                    ) {
                        AdfIcon.FpsSpeed(
                            size = 22.dp,
                            primaryColor = AdfElectricCyan,
                            accentColor = Color(0xFF10B981)
                        )
                    }

                    // Developer & Credits Screen
                    IconButton(
                        onClick = { viewModel.navigateTo(AppScreen.CREDITS) },
                        modifier = Modifier.testTag("credits_top_button")
                    ) {
                        AdfIcon.DeveloperBadge(
                            size = 22.dp,
                            primaryColor = AdfElectricCyan,
                            accentColor = Color(0xFFD946EF)
                        )
                    }

                    // 3 Lines / 3 Dots Menu (replacing Crown icon as requested)
                    IconButton(
                        onClick = { showCoreSuiteMenu = true },
                        modifier = Modifier.testTag("core_suite_menu_button")
                    ) {
                        Box(
                            modifier = Modifier
                                .size(36.dp)
                                .clip(RoundedCornerShape(9.dp))
                                .background(AdfElectricCyan.copy(alpha = 0.15f))
                                .border(
                                    width = 1.dp,
                                    brush = Brush.linearGradient(listOf(AdfElectricCyan, AdfVibrantBlue)),
                                    shape = RoundedCornerShape(9.dp)
                                ),
                            contentAlignment = Alignment.Center
                        ) {
                            AdfIcon.MenuThreeLines(
                                size = 20.dp,
                                color = AdfElectricCyan
                            )
                        }
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = AdfMidnightBackground
                )
            )
        },
        floatingActionButton = {
            ExtendedFloatingActionButton(
                onClick = {
                    documentPickerLauncher.launch(
                        arrayOf(
                            "application/pdf",
                            "application/msword",
                            "application/vnd.openxmlformats-officedocument.wordprocessingml.document"
                        )
                    )
                },
                containerColor = AdfElectricCyan,
                contentColor = AdfMidnightBackground,
                icon = {
                    AdfIcon.FileOpen(
                        size = 20.dp,
                        primaryColor = AdfMidnightBackground,
                        accentColor = AdfVibrantBlue
                    )
                },
                text = {
                    Text(
                        text = "Open File",
                        fontWeight = FontWeight.Bold,
                        letterSpacing = 0.5.sp
                    )
                },
                shape = RoundedCornerShape(16.dp),
                modifier = Modifier
                    .testTag("open_file_fab")
                    .navigationBarsPadding()
            )
        },
        containerColor = AdfMidnightBackground
    ) { padding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding),
            contentPadding = PaddingValues(bottom = 96.dp)
        ) {
            // ==========================================
            // ==========================================
            // Section 1: Core Suite Quick Menu Bar & Shortcuts
            // ==========================================
            item {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 6.dp)
                ) {
                    // Tap-to-open Core Suite Menu Banner
                    Card(
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("core_suite_quick_banner")
                            .clip(RoundedCornerShape(16.dp))
                            .clickable { showCoreSuiteMenu = true }
                            .border(
                                width = 1.dp,
                                brush = Brush.horizontalGradient(
                                    listOf(
                                        AdfElectricCyan.copy(alpha = 0.6f),
                                        AdfCyberViolet.copy(alpha = 0.4f),
                                        AdfVibrantBlue.copy(alpha = 0.5f)
                                    )
                                ),
                                shape = RoundedCornerShape(16.dp)
                            ),
                        colors = CardDefaults.cardColors(containerColor = AdfSurfaceCard)
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 14.dp, vertical = 10.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                modifier = Modifier.weight(1f)
                            ) {
                                Box(
                                    modifier = Modifier
                                        .size(38.dp)
                                        .clip(RoundedCornerShape(10.dp))
                                        .background(AdfElectricCyan.copy(alpha = 0.15f)),
                                    contentAlignment = Alignment.Center
                                ) {
                                    AdfIcon.MenuThreeLines(size = 20.dp, color = AdfElectricCyan)
                                }
                                Spacer(modifier = Modifier.width(10.dp))
                                Column {
                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        Text(
                                            text = "CORE SUITE MENU",
                                            style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold),
                                            color = AdfTextPrimary
                                        )
                                        Spacer(modifier = Modifier.width(6.dp))
                                        Surface(
                                            shape = RoundedCornerShape(4.dp),
                                            color = AdfElectricCyan.copy(alpha = 0.12f)
                                        ) {
                                            Text(
                                                text = "6 ESSENTIAL TOOLS",
                                                color = AdfElectricCyan,
                                                fontSize = 8.5.sp,
                                                fontWeight = FontWeight.ExtraBold,
                                                modifier = Modifier.padding(horizontal = 5.dp, vertical = 2.dp)
                                            )
                                        }
                                    }
                                    Text(
                                        text = "1. Convertion • 2. Credits • 3. FPS • 4. PDF Viewer • 5. Signature • 6. Watermark",
                                        style = MaterialTheme.typography.bodySmall.copy(fontSize = 11.sp),
                                        color = AdfTextSecondary,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                }
                            }

                            AdfIcon.ChevronRight(
                                size = 20.dp,
                                color = AdfElectricCyan
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(8.dp))

                    // Fast Horizontal Shortcut Pills
                    LazyRow(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        item {
                            QuickToolChip(
                                title = "1. Convertion",
                                badge = "HUB",
                                color = AdfElectricCyan,
                                onClick = { viewModel.navigateTo(AppScreen.CONVERSION_HUB) }
                            )
                        }
                        item {
                            QuickToolChip(
                                title = "2. Credits",
                                badge = "10/10/2026",
                                color = Color(0xFFD946EF),
                                onClick = { viewModel.navigateTo(AppScreen.CREDITS) }
                            )
                        }
                        item {
                            QuickToolChip(
                                title = "3. FPS",
                                badge = "${currentRefreshRate}Hz",
                                color = Color(0xFF10B981),
                                onClick = { showRefreshRateDialog = true }
                            )
                        }
                        item {
                            QuickToolChip(
                                title = "4. PDF Viewer",
                                badge = "NO EDIT",
                                color = AdfCyberViolet,
                                onClick = {
                                    viewModel.setPureViewerMode(true)
                                    val firstDoc = documents.firstOrNull()
                                    if (firstDoc != null) {
                                        viewModel.openDocument(firstDoc)
                                    } else {
                                        documentPickerLauncher.launch(arrayOf("application/pdf"))
                                    }
                                }
                            )
                        }
                        item {
                            QuickToolChip(
                                title = "5. Signature",
                                badge = "VAULT",
                                color = AdfAmber,
                                onClick = { viewModel.navigateTo(AppScreen.SIGNATURE_VAULT) }
                            )
                        }
                        item {
                            QuickToolChip(
                                title = "6. Watermark",
                                badge = "STUDIO",
                                color = AdfElectricCyan,
                                onClick = {
                                    viewModel.setSelectedToolTab(4)
                                    viewModel.navigateTo(AppScreen.TOOLS)
                                }
                            )
                        }
                    }
                }
            }

            // ==========================================
            // Section 2: Storage Document Library
            // ==========================================
            item {
                Column(modifier = Modifier.padding(horizontal = 16.dp, vertical = 6.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(
                                text = "DEVICE STORAGE FILES",
                                style = MaterialTheme.typography.labelMedium.copy(
                                    fontWeight = FontWeight.Bold,
                                    letterSpacing = 1.sp
                                ),
                                color = AdfTextSecondary
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Surface(
                                shape = RoundedCornerShape(10.dp),
                                color = AdfSurfaceVariant,
                                border = CardDefaults.outlinedCardBorder().copy(brush = Brush.horizontalGradient(listOf(AdfBorderSubtle, AdfBorderGlow)))
                            ) {
                                Text(
                                    text = "${filteredDocs.size}",
                                    color = AdfElectricCyan,
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.Bold,
                                    modifier = Modifier.padding(horizontal = 7.dp, vertical = 1.dp)
                                )
                            }
                        }

                        if (isScanning) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                CircularProgressIndicator(
                                    modifier = Modifier.size(12.dp),
                                    strokeWidth = 2.dp,
                                    color = AdfElectricCyan
                                )
                                Spacer(modifier = Modifier.width(6.dp))
                                Text(
                                    text = "Scanning...",
                                    color = AdfElectricCyan,
                                    fontSize = 10.sp
                                )
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(8.dp))

                    // Futuristic Search Input Bar
                    OutlinedTextField(
                        value = searchQuery,
                        onValueChange = { searchQuery = it },
                        placeholder = {
                            Text(
                                text = "Search PDF and Word files on phone...",
                                color = AdfTextMuted,
                                fontSize = 13.sp
                            )
                        },
                        leadingIcon = {
                            AdfIcon.Search(
                                size = 20.dp,
                                primaryColor = AdfElectricCyan,
                                accentColor = AdfVibrantBlue
                            )
                        },
                        trailingIcon = {
                            if (searchQuery.isNotEmpty()) {
                                IconButton(onClick = { searchQuery = "" }) {
                                    AdfIcon.Close(size = 18.dp, color = AdfTextSecondary)
                                }
                            }
                        },
                        singleLine = true,
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedContainerColor = AdfObsidianSurface,
                            unfocusedContainerColor = AdfObsidianSurface,
                            focusedBorderColor = AdfElectricCyan,
                            unfocusedBorderColor = AdfBorderSubtle,
                            focusedTextColor = AdfTextPrimary,
                            unfocusedTextColor = AdfTextPrimary
                        ),
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("search_device_documents_input")
                    )

                    Spacer(modifier = Modifier.height(10.dp))

                    // Category Filter Pills
                    val filterCategories = listOf("All Files", "PDFs Only", "Word (.docx)", "Starred ⭐")
                    LazyRow(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        items(filterCategories.size) { index ->
                            val isSelected = selectedFilterTab == index
                            Surface(
                                onClick = { selectedFilterTab = index },
                                shape = RoundedCornerShape(20.dp),
                                color = if (isSelected) AdfElectricCyan.copy(alpha = 0.15f) else AdfObsidianSurface,
                                border = CardDefaults.outlinedCardBorder().copy(
                                    brush = if (isSelected) {
                                        Brush.horizontalGradient(listOf(AdfElectricCyan, AdfVibrantBlue))
                                    } else {
                                        Brush.horizontalGradient(listOf(AdfBorderSubtle, AdfBorderSubtle))
                                    }
                                ),
                                modifier = Modifier.testTag("filter_tab_$index")
                            ) {
                                Text(
                                    text = filterCategories[index],
                                    color = if (isSelected) AdfElectricCyan else AdfTextSecondary,
                                    fontSize = 12.sp,
                                    fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                                    modifier = Modifier.padding(horizontal = 14.dp, vertical = 7.dp)
                                )
                            }
                        }
                    }
                }
            }

            // ==========================================
            // Section 3: List of Phone Documents
            // ==========================================
            if (filteredDocs.isEmpty()) {
                item {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(top = 40.dp, bottom = 20.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        AdfIcon.FolderEmpty(
                            size = 54.dp,
                            primaryColor = AdfTextMuted,
                            accentColor = AdfElectricCyan
                        )
                        Spacer(modifier = Modifier.height(12.dp))
                        Text(
                            text = if (searchQuery.isNotEmpty()) "No matching files found" else "No documents scanned on storage",
                            color = AdfTextSecondary,
                            style = MaterialTheme.typography.bodyLarge,
                            fontWeight = FontWeight.SemiBold
                        )
                        Spacer(modifier = Modifier.height(6.dp))
                        Text(
                            text = "Tap 'Open File' or rescan storage to load files",
                            color = AdfTextMuted,
                            style = MaterialTheme.typography.bodySmall
                        )
                        Spacer(modifier = Modifier.height(14.dp))
                        Button(
                            onClick = { viewModel.scanDeviceDocuments() },
                            colors = ButtonDefaults.buttonColors(containerColor = AdfSurfaceVariant),
                            shape = RoundedCornerShape(10.dp)
                        ) {
                            Text("Rescan Device Files", color = AdfElectricCyan, fontSize = 12.sp)
                        }
                    }
                }
            } else {
                items(filteredDocs, key = { it.id }) { item ->
                    UnifiedDocumentCard(
                        doc = item,
                        context = context,
                        onOpenStandard = {
                            viewModel.setPureViewerMode(false)
                            if (item.originalDoc != null) {
                                viewModel.openDocument(item.originalDoc)
                            } else if (item.deviceFile != null) {
                                viewModel.importPdfFromUri(item.deviceFile.uri)
                            }
                        },
                        onOpenPureViewer = {
                            viewModel.setPureViewerMode(true)
                            if (item.originalDoc != null) {
                                viewModel.openDocument(item.originalDoc)
                            } else if (item.deviceFile != null) {
                                viewModel.importPdfFromUri(item.deviceFile.uri)
                            }
                        },
                        onOrganize = {
                            if (item.originalDoc != null) {
                                viewModel.openDocument(item.originalDoc)
                                viewModel.navigateTo(AppScreen.PAGE_MANAGER)
                            }
                        },
                        onConvertToOther = {
                            viewModel.setSelectedToolTab(if (item.isPdf) 3 else 2) // PDF to Word or Word to PDF
                            viewModel.navigateTo(AppScreen.TOOLS)
                        },
                        onToggleFavorite = {
                            if (item.originalDoc != null) {
                                viewModel.toggleFavorite(item.originalDoc)
                            }
                        },
                        onDelete = {
                            if (item.originalDoc != null) {
                                viewModel.deleteDocument(item.originalDoc)
                            }
                        },
                        onPreviewFirstPage = {
                            previewDoc = item
                        }
                    )
                }
            }
        }
    }

    if (showRefreshRateDialog) {
        RefreshRateDialog(
            showFpsHud = showFpsHud,
            onToggleFpsHud = { viewModel.setShowFpsHud(it) },
            onReplayBootup = { viewModel.navigateTo(AppScreen.BOOTUP_SHOWCASE) },
            onDismiss = { showRefreshRateDialog = false }
        )
    }

    if (showCoreSuiteMenu) {
        CoreSuiteMenuSheet(
            onDismiss = { showCoreSuiteMenu = false },
            onNavigate = { screen -> viewModel.navigateTo(screen) },
            onOpenToolTab = { tab ->
                viewModel.setSelectedToolTab(tab)
                viewModel.navigateTo(AppScreen.TOOLS)
            },
            onOpenFpsDialog = { showRefreshRateDialog = true },
            onOpenPureViewer = {
                viewModel.setPureViewerMode(true)
                val firstDoc = documents.firstOrNull()
                if (firstDoc != null) {
                    viewModel.openDocument(firstDoc)
                } else {
                    documentPickerLauncher.launch(arrayOf("application/pdf"))
                }
            },
            onOpenFile = {
                documentPickerLauncher.launch(
                    arrayOf(
                        "application/pdf",
                        "application/msword",
                        "application/vnd.openxmlformats-officedocument.wordprocessingml.document"
                    )
                )
            }
        )
    }

    // First Page Canvas PDFViewer Dialog
    previewDoc?.let { doc ->
        val file = doc.originalDoc?.filePath?.let { File(it) }
        PDFViewerDialog(
            file = file,
            uri = doc.deviceFile?.uri,
            document = doc.originalDoc,
            title = doc.title,
            onDismiss = { previewDoc = null },
            onOpenFullDocument = {
                viewModel.setPureViewerMode(false)
                if (doc.originalDoc != null) {
                    viewModel.openDocument(doc.originalDoc)
                } else if (doc.deviceFile != null) {
                    viewModel.importPdfFromUri(doc.deviceFile.uri)
                }
            }
        )
    }
}

@Composable
fun QuickToolChip(
    title: String,
    badge: String,
    color: Color,
    onClick: () -> Unit
) {
    Surface(
        shape = RoundedCornerShape(10.dp),
        color = AdfSurfaceCard,
        border = CardDefaults.outlinedCardBorder().copy(
            brush = Brush.horizontalGradient(listOf(color.copy(alpha = 0.4f), AdfBorderSubtle))
        ),
        modifier = Modifier.clickable { onClick() }
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp)
        ) {
            Text(
                text = title,
                fontSize = 11.5.sp,
                fontWeight = FontWeight.Bold,
                color = AdfTextPrimary
            )
            Spacer(modifier = Modifier.width(6.dp))
            Surface(
                shape = RoundedCornerShape(4.dp),
                color = color.copy(alpha = 0.15f)
            ) {
                Text(
                    text = badge,
                    fontSize = 8.5.sp,
                    fontWeight = FontWeight.ExtraBold,
                    color = color,
                    modifier = Modifier.padding(horizontal = 4.dp, vertical = 1.dp)
                )
            }
        }
    }
}

/**
 * Modern ADF AIO 2-Column Menu Card with subtle glow and press feedback
 */
@Composable
fun AdfMenuCard(
    title: String,
    subtitle: String,
    badge: String,
    accentColor: Color,
    icon: @Composable () -> Unit,
    modifier: Modifier = Modifier,
    onClick: () -> Unit
) {
    val interactionSource = remember { MutableInteractionSource() }
    val isPressed by interactionSource.collectIsPressedAsState()
    val scale by animateFloatAsState(
        targetValue = if (isPressed) 0.97f else 1f,
        animationSpec = spring(stiffness = Spring.StiffnessMediumLow),
        label = "MenuScale"
    )

    Card(
        onClick = onClick,
        interactionSource = interactionSource,
        colors = CardDefaults.cardColors(containerColor = AdfObsidianSurface),
        shape = RoundedCornerShape(14.dp),
        border = CardDefaults.outlinedCardBorder().copy(
            brush = Brush.linearGradient(
                listOf(
                    if (isPressed) accentColor else AdfBorderSubtle,
                    AdfSurfaceVariant
                )
            )
        ),
        modifier = modifier
            .scale(scale)
            .height(96.dp)
            .testTag("menu_card_${title.replace(' ', '_').lowercase()}")
    ) {
        Box(modifier = Modifier.fillMaxSize()) {
            // Subtle ambient corner glow
            Box(
                modifier = Modifier
                    .size(40.dp)
                    .align(Alignment.TopEnd)
                    .background(
                        Brush.radialGradient(
                            listOf(accentColor.copy(alpha = 0.20f), Color.Transparent)
                        )
                    )
            )

            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(10.dp),
                verticalArrangement = Arrangement.SpaceBetween
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Box(
                        modifier = Modifier
                            .size(34.dp)
                            .clip(RoundedCornerShape(8.dp))
                            .background(accentColor.copy(alpha = 0.12f)),
                        contentAlignment = Alignment.Center
                    ) {
                        icon()
                    }

                    Surface(
                        shape = RoundedCornerShape(4.dp),
                        color = accentColor.copy(alpha = 0.15f)
                    ) {
                        Text(
                            text = badge,
                            color = accentColor,
                            fontSize = 8.sp,
                            fontWeight = FontWeight.ExtraBold,
                            modifier = Modifier.padding(horizontal = 4.dp, vertical = 2.dp)
                        )
                    }
                }

                Column {
                    Text(
                        text = title,
                        style = MaterialTheme.typography.titleSmall.copy(
                            fontWeight = FontWeight.Bold,
                            fontSize = 13.sp
                        ),
                        color = AdfTextPrimary,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Text(
                        text = subtitle,
                        style = MaterialTheme.typography.bodySmall.copy(fontSize = 10.sp),
                        color = AdfTextMuted,
                        maxLines = 1
                    )
                }
            }
        }
    }
}

/**
 * Full Width Banner Menu Card for Open File
 */
@Composable
fun AdfMenuWideCard(
    title: String,
    subtitle: String,
    badge: String,
    accentColor: Color,
    icon: @Composable () -> Unit,
    onClick: () -> Unit
) {
    val interactionSource = remember { MutableInteractionSource() }
    val isPressed by interactionSource.collectIsPressedAsState()
    val scale by animateFloatAsState(
        targetValue = if (isPressed) 0.98f else 1f,
        animationSpec = spring(stiffness = Spring.StiffnessMediumLow),
        label = "WideMenuScale"
    )

    Card(
        onClick = onClick,
        interactionSource = interactionSource,
        colors = CardDefaults.cardColors(containerColor = AdfObsidianSurface),
        shape = RoundedCornerShape(14.dp),
        border = CardDefaults.outlinedCardBorder().copy(
            brush = Brush.linearGradient(
                listOf(AdfElectricCyan.copy(alpha = 0.4f), AdfVibrantBlue.copy(alpha = 0.4f))
            )
        ),
        modifier = Modifier
            .fillMaxWidth()
            .scale(scale)
            .testTag("menu_card_open_file")
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .size(42.dp)
                    .clip(RoundedCornerShape(10.dp))
                    .background(accentColor.copy(alpha = 0.15f)),
                contentAlignment = Alignment.Center
            ) {
                icon()
            }

            Spacer(modifier = Modifier.width(12.dp))

            Column(modifier = Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = title,
                        style = MaterialTheme.typography.titleSmall.copy(
                            fontWeight = FontWeight.Bold,
                            fontSize = 14.sp
                        ),
                        color = AdfTextPrimary
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Surface(
                        shape = RoundedCornerShape(4.dp),
                        color = accentColor.copy(alpha = 0.15f)
                    ) {
                        Text(
                            text = badge,
                            color = AdfElectricCyan,
                            fontSize = 8.sp,
                            fontWeight = FontWeight.Bold,
                            modifier = Modifier.padding(horizontal = 4.dp, vertical = 2.dp)
                        )
                    }
                }
                Text(
                    text = subtitle,
                    style = MaterialTheme.typography.bodySmall.copy(fontSize = 11.sp),
                    color = AdfTextMuted,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }

            AdfIcon.ChevronRight(size = 20.dp, color = AdfElectricCyan)
        }
    }
}

/**
 * Unified Card displaying either a PDF or Word document with bespoke custom icons
 */
@Composable
fun UnifiedDocumentCard(
    doc: UnifiedDocItem,
    context: Context,
    onOpenStandard: () -> Unit,
    onOpenPureViewer: () -> Unit,
    onOrganize: () -> Unit,
    onConvertToOther: () -> Unit,
    onToggleFavorite: () -> Unit,
    onDelete: () -> Unit,
    onPreviewFirstPage: () -> Unit = {}
) {
    var showMenu by remember { mutableStateOf(false) }
    val dateFormat = remember { SimpleDateFormat("MMM d, yyyy", Locale.getDefault()) }

    Card(
        onClick = onOpenStandard,
        colors = CardDefaults.cardColors(containerColor = AdfObsidianSurface),
        shape = RoundedCornerShape(14.dp),
        border = CardDefaults.outlinedCardBorder().copy(
            brush = Brush.linearGradient(
                listOf(
                    AdfBorderSubtle,
                    if (doc.isPdf) AdfCoral.copy(alpha = 0.2f) else AdfVibrantBlue.copy(alpha = 0.2f)
                )
            )
        ),
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 5.dp)
            .testTag("unified_doc_${doc.id}")
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Bespoke Custom Document Icon / First Page Canvas Preview
            Box(
                modifier = Modifier
                    .size(48.dp)
                    .clip(RoundedCornerShape(10.dp))
                    .background(
                        if (doc.isPdf) AdfCoral.copy(alpha = 0.12f) else AdfVibrantBlue.copy(alpha = 0.12f)
                    )
                    .clickable(enabled = doc.isPdf) { onPreviewFirstPage() },
                contentAlignment = Alignment.Center
            ) {
                if (doc.isPdf) {
                    val file = doc.originalDoc?.filePath?.let { File(it) }
                    if ((file != null && file.exists()) || doc.deviceFile?.uri != null) {
                        PDFViewer(
                            file = file,
                            uri = doc.deviceFile?.uri,
                            document = doc.originalDoc,
                            modifier = Modifier.fillMaxSize(),
                            scaleFactor = 1.0f,
                            showPageBadge = false,
                            showShadow = false,
                            cornerRadius = 6.dp
                        )
                    } else {
                        AdfIcon.PdfDoc(
                            size = 30.dp,
                            primaryColor = AdfCoral,
                            accentColor = Color.White
                        )
                    }
                } else {
                    AdfIcon.WordDoc(
                        size = 30.dp,
                        primaryColor = AdfVibrantBlue,
                        accentColor = Color.White
                    )
                }
            }

            Spacer(modifier = Modifier.width(12.dp))

            // Document Details
            Column(modifier = Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = doc.title,
                        style = MaterialTheme.typography.titleMedium.copy(
                            fontWeight = FontWeight.SemiBold,
                            fontSize = 14.sp
                        ),
                        color = AdfTextPrimary,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f, fill = false)
                    )

                    Spacer(modifier = Modifier.width(6.dp))

                    Surface(
                        shape = RoundedCornerShape(4.dp),
                        color = if (doc.isPdf) AdfCoral.copy(alpha = 0.15f) else AdfVibrantBlue.copy(alpha = 0.15f)
                    ) {
                        Text(
                            text = if (doc.isPdf) "PDF" else "DOCX",
                            style = MaterialTheme.typography.labelSmall.copy(
                                fontSize = 8.sp,
                                fontWeight = FontWeight.ExtraBold
                            ),
                            color = if (doc.isPdf) AdfCoral else Color(0xFF60A5FA),
                            modifier = Modifier.padding(horizontal = 4.dp, vertical = 1.dp)
                        )
                    }

                    if (doc.isSample) {
                        Spacer(modifier = Modifier.width(4.dp))
                        Surface(
                            shape = RoundedCornerShape(4.dp),
                            color = AdfElectricCyan.copy(alpha = 0.15f)
                        ) {
                            Text(
                                text = "SAMPLE",
                                style = MaterialTheme.typography.labelSmall.copy(
                                    fontSize = 8.sp,
                                    fontWeight = FontWeight.ExtraBold
                                ),
                                color = AdfElectricCyan,
                                modifier = Modifier.padding(horizontal = 4.dp, vertical = 1.dp)
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.height(4.dp))

                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    if (doc.isPdf && doc.pageCount > 0) {
                        Text(
                            text = "${doc.pageCount} pgs",
                            style = MaterialTheme.typography.bodySmall.copy(fontSize = 11.sp),
                            color = AdfTextSecondary
                        )
                        Text("•", color = AdfTextMuted, style = MaterialTheme.typography.bodySmall)
                    }

                    if (doc.sizeBytes > 0) {
                        Text(
                            text = Formatter.formatShortFileSize(context, doc.sizeBytes),
                            style = MaterialTheme.typography.bodySmall.copy(fontSize = 11.sp),
                            color = AdfTextSecondary
                        )
                        Text("•", color = AdfTextMuted, style = MaterialTheme.typography.bodySmall)
                    }

                    if (doc.dateModified > 0) {
                        Text(
                            text = dateFormat.format(Date(doc.dateModified)),
                            style = MaterialTheme.typography.bodySmall.copy(fontSize = 11.sp),
                            color = AdfTextMuted
                        )
                    }
                }
            }

            // Quick Pure Viewer Button
            IconButton(
                onClick = onOpenPureViewer,
                modifier = Modifier.size(36.dp)
            ) {
                AdfIcon.PureViewer(
                    size = 18.dp,
                    primaryColor = AdfElectricCyan,
                    accentColor = Color(0xFF10B981)
                )
            }

            // Favorite Star
            IconButton(
                onClick = onToggleFavorite,
                modifier = Modifier.size(36.dp)
            ) {
                AdfIcon.Star(
                    size = 18.dp,
                    isFilled = doc.isFavorite,
                    fillColor = Color(0xFFFBBF24),
                    outlineColor = if (doc.isFavorite) Color(0xFFFBBF24) else AdfTextMuted
                )
            }

            // More Options Dropdown Menu
            Box {
                IconButton(
                    onClick = { showMenu = true },
                    modifier = Modifier.size(36.dp)
                ) {
                    AdfIcon.MenuDots(
                        size = 18.dp,
                        color = AdfTextSecondary
                    )
                }

                DropdownMenu(
                    expanded = showMenu,
                    onDismissRequest = { showMenu = false },
                    modifier = Modifier.background(AcrobatDarkSurfaceElevated)
                ) {
                    DropdownMenuItem(
                        text = { Text("Open Editor / Reader", color = AdfTextPrimary) },
                        leadingIcon = { AdfIcon.Tools(size = 18.dp, primaryColor = AdfElectricCyan) },
                        onClick = {
                            showMenu = false
                            onOpenStandard()
                        }
                    )
                    DropdownMenuItem(
                        text = { Text("Open Pure Viewer (No Edit)", color = AdfTextPrimary) },
                        leadingIcon = { AdfIcon.PureViewer(size = 18.dp, primaryColor = Color(0xFF10B981)) },
                        onClick = {
                            showMenu = false
                            onOpenPureViewer()
                        }
                    )
                    if (doc.isPdf) {
                        DropdownMenuItem(
                            text = { Text("Preview First Page (Canvas)", color = AdfElectricCyan) },
                            leadingIcon = { AdfIcon.PureViewer(size = 18.dp, primaryColor = AdfElectricCyan) },
                            onClick = {
                                showMenu = false
                                onPreviewFirstPage()
                            }
                        )
                    }
                    if (doc.isPdf) {
                        DropdownMenuItem(
                            text = { Text("Organize Pages", color = AdfTextPrimary) },
                            leadingIcon = { AdfIcon.Layers(size = 18.dp, primaryColor = AdfElectricCyan) },
                            onClick = {
                                showMenu = false
                                onOrganize()
                            }
                        )
                        DropdownMenuItem(
                            text = { Text("Convert to Word (.docx)", color = AdfTextPrimary) },
                            leadingIcon = { AdfIcon.WordDoc(size = 18.dp, primaryColor = AdfVibrantBlue) },
                            onClick = {
                                showMenu = false
                                onConvertToOther()
                            }
                        )
                    } else {
                        DropdownMenuItem(
                            text = { Text("Convert to PDF", color = AdfTextPrimary) },
                            leadingIcon = { AdfIcon.PdfDoc(size = 18.dp, primaryColor = AdfCoral) },
                            onClick = {
                                showMenu = false
                                onConvertToOther()
                            }
                        )
                    }
                    if (doc.originalDoc != null) {
                        DropdownMenuItem(
                            text = { Text("Delete Document", color = AdfCoral) },
                            leadingIcon = { AdfIcon.Delete(size = 18.dp, color = AdfCoral) },
                            onClick = {
                                showMenu = false
                                onDelete()
                            }
                        )
                    }
                }
            }
        }
    }
}

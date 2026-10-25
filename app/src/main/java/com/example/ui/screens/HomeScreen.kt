package com.example.ui.screens

import android.app.Activity
import android.content.Context
import android.os.Build
import android.os.Environment
import android.text.format.Formatter
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.*
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
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
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.R
import com.example.data.fps.DisplayRefreshRateManager
import com.example.data.local.DeviceDocumentScanner
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

private val unifiedDocDateFormat = SimpleDateFormat("MMM d, yyyy", Locale.getDefault())

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
    val fileType: DeviceFileType = DeviceFileType.PDF,
    val originalDoc: DocumentEntity? = null,
    val deviceFile: DeviceFileItem? = null
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HomeScreen(
    viewModel: PdfViewModel,
    updateViewModel: com.example.update.UpdateViewModel? = null
) {
    val documents by viewModel.documents.collectAsStateWithLifecycle()
    val deviceFiles by viewModel.deviceFiles.collectAsStateWithLifecycle()
    val isScanning by viewModel.isScanningDeviceFiles.collectAsStateWithLifecycle()
    val currentRefreshRate by DisplayRefreshRateManager.currentRefreshRate.collectAsStateWithLifecycle()
    val showFpsHud by viewModel.showFpsHud.collectAsStateWithLifecycle()

    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current

    var searchQuery by remember { mutableStateOf("") }
    var selectedFilterTab by remember { mutableIntStateOf(0) }
    var showRefreshRateDialog by remember { mutableStateOf(false) }
    var showCoreSuiteMenu by remember { mutableStateOf(false) }
    var showStoragePermissionDialog by remember { mutableStateOf(false) }
    var previewDoc by remember { mutableStateOf<UnifiedDocItem?>(null) }

    var hasStoragePerm by remember {
        mutableStateOf(DeviceDocumentScanner.hasStoragePermission(context))
    }

    val activity = context as? Activity

    // Auto re-check permission, unlock hardware refresh rate & ensure 60+ FPS on resume
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                if (activity != null) {
                    DisplayRefreshRateManager.unlockMaxRefreshRate(activity)
                    if (DisplayRefreshRateManager.currentRefreshRate.value < 60) {
                        DisplayRefreshRateManager.setTargetRefreshRate(activity, 60)
                    }
                }
                val currentPerm = DeviceDocumentScanner.hasStoragePermission(context)
                if (currentPerm != hasStoragePerm) {
                    hasStoragePerm = currentPerm
                    if (currentPerm) {
                        viewModel.scanDeviceDocuments()
                    }
                }
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    LaunchedEffect(Unit) {
        if (activity != null) {
            DisplayRefreshRateManager.unlockMaxRefreshRate(activity)
            if (DisplayRefreshRateManager.currentRefreshRate.value < 60) {
                DisplayRefreshRateManager.setTargetRefreshRate(activity, 60)
            }
        }
        val savedFolder = DeviceDocumentScanner.getPersistedTreeUri(context)
        if (savedFolder != null) {
            viewModel.scanFolderTree(savedFolder)
        }
        viewModel.scanDeviceDocuments()
    }

    val documentPickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument()
    ) { uri ->
        uri?.let {
            try {
                context.contentResolver.takePersistableUriPermission(
                    it,
                    android.content.Intent.FLAG_GRANT_READ_URI_PERMISSION
                )
            } catch (ignored: Exception) {}
            DeviceDocumentScanner.recordStoragePermissionGranted(context)
            hasStoragePerm = true
            viewModel.importPdfFromUri(it)
        }
    }

    val folderPickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocumentTree()
    ) { treeUri ->
        treeUri?.let { uri ->
            try {
                context.contentResolver.takePersistableUriPermission(
                    uri,
                    android.content.Intent.FLAG_GRANT_READ_URI_PERMISSION or android.content.Intent.FLAG_GRANT_WRITE_URI_PERMISSION
                )
            } catch (ignored: Exception) {}
            DeviceDocumentScanner.savePersistedTreeUri(context, uri)
            hasStoragePerm = true
            viewModel.scanFolderTree(uri)
        }
    }

    val manageStorageLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.StartActivityForResult()
    ) {
        DeviceDocumentScanner.recordStoragePermissionGranted(context)
        hasStoragePerm = true
        viewModel.scanDeviceDocuments()
    }

    val multiplePermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestMultiplePermissions()
    ) {
        DeviceDocumentScanner.recordStoragePermissionGranted(context)
        hasStoragePerm = true
        viewModel.scanDeviceDocuments()
    }

    fun triggerFullScan() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            if (!Environment.isExternalStorageManager()) {
                showStoragePermissionDialog = true
            } else {
                hasStoragePerm = true
                viewModel.scanDeviceDocuments()
            }
        } else {
            val readGranted = androidx.core.content.ContextCompat.checkSelfPermission(
                context,
                android.Manifest.permission.READ_EXTERNAL_STORAGE
            ) == android.content.pm.PackageManager.PERMISSION_GRANTED
            if (!readGranted) {
                multiplePermissionLauncher.launch(
                    arrayOf(
                        android.Manifest.permission.READ_EXTERNAL_STORAGE,
                        android.Manifest.permission.WRITE_EXTERNAL_STORAGE
                    )
                )
            } else {
                hasStoragePerm = true
                viewModel.scanDeviceDocuments()
            }
        }
    }

    val unifiedDocs = remember(documents, deviceFiles) {
        val list = mutableListOf<UnifiedDocItem>()
        // DB documents
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
                    fileType = DeviceFileType.PDF,
                    originalDoc = doc
                )
            )
        }
        // Scanned device files (PDF, DOCX, DOC, XLSX, TXT)
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
                        fileType = file.fileType,
                        deviceFile = file
                    )
                )
            }
        }
        list.sortedByDescending { it.dateModified }
    }

    val filteredDocs = remember(unifiedDocs, searchQuery, selectedFilterTab) {
        val query = searchQuery.trim().lowercase()
        unifiedDocs.filter { doc ->
            val ext = when {
                doc.deviceFile != null -> {
                    val p = doc.deviceFile.path
                    if (p.isNotEmpty() && p.contains(".")) p.substringAfterLast('.').lowercase()
                    else doc.deviceFile.title.substringAfterLast('.', "").lowercase()
                }
                doc.originalDoc != null -> {
                    doc.originalDoc.filePath.substringAfterLast('.', "").lowercase().ifEmpty { "pdf" }
                }
                doc.isPdf -> "pdf"
                else -> ""
            }

            val typeBadge = when (doc.fileType) {
                DeviceFileType.PDF -> "pdf"
                DeviceFileType.WORD_DOCX -> "docx"
                DeviceFileType.WORD_DOC -> "doc"
                DeviceFileType.EXCEL -> "xlsx"
                DeviceFileType.POWERPOINT -> "pptx"
                DeviceFileType.TEXT -> "txt"
                else -> "file"
            }

            val matchesQuery = if (query.isEmpty()) {
                true
            } else {
                val cleanQuery = if (query.startsWith(".")) query.removePrefix(".") else query
                // 1. Matches filename
                doc.title.contains(query, ignoreCase = true) ||
                // 2. Matches full filename including extension
                "${doc.title}.$ext".contains(query, ignoreCase = true) ||
                // 3. Matches file extension (e.g. "pdf", ".pdf", "docx", ".docx", "doc", "txt", "xlsx")
                ext == cleanQuery ||
                ".$ext" == query ||
                ext.startsWith(cleanQuery) ||
                typeBadge.equals(cleanQuery, ignoreCase = true) ||
                // 4. Matches filepath if available
                doc.deviceFile?.path?.contains(query, ignoreCase = true) == true ||
                doc.originalDoc?.filePath?.contains(query, ignoreCase = true) == true
            }

            val matchesTab = when (selectedFilterTab) {
                1 -> doc.isPdf
                2 -> !doc.isPdf
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
                                .background(Brush.linearGradient(listOf(AdfElectricCyan.copy(alpha = 0.25f), AdfVibrantBlue.copy(alpha = 0.25f))))
                                .border(1.dp, Brush.linearGradient(listOf(AdfElectricCyan, AdfVibrantBlue)), RoundedCornerShape(9.dp)),
                            contentAlignment = Alignment.Center
                        ) {
                            Image(
                                painter = painterResource(id = R.drawable.pdf_aio_logo),
                                contentDescription = "PDF AIO Emblem",
                                modifier = Modifier.size(26.dp)
                            )
                        }
                        Spacer(modifier = Modifier.width(10.dp))
                        Column {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Text(
                                    text = "PDF AIO",
                                    style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.ExtraBold, letterSpacing = 0.8.sp),
                                    color = AdfTextPrimary
                                )
                                Spacer(modifier = Modifier.width(6.dp))
                                Surface(
                                    shape = RoundedCornerShape(4.dp),
                                    color = AdfElectricCyan.copy(alpha = 0.12f),
                                    border = CardDefaults.outlinedCardBorder().copy(brush = Brush.horizontalGradient(listOf(AdfElectricCyan, AdfVibrantBlue))),
                                    modifier = Modifier.clickable { showRefreshRateDialog = true }
                                ) {
                                    Text(
                                        text = "${currentRefreshRate}Hz",
                                        color = AdfElectricCyan,
                                        fontSize = 9.sp,
                                        fontWeight = FontWeight.ExtraBold,
                                        modifier = Modifier.padding(horizontal = 4.dp, vertical = 1.dp)
                                    )
                                }
                            }
                            Text(
                                text = "Next-Gen PDF & Office Suite",
                                style = MaterialTheme.typography.bodySmall.copy(fontSize = 11.sp),
                                color = AdfTextSecondary
                            )
                        }
                    }
                },
                actions = {
                    // Rescan Device Storage Button
                    IconButton(
                        onClick = { triggerFullScan() },
                        modifier = Modifier.testTag("rescan_storage_button")
                    ) {
                        if (isScanning) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(20.dp),
                                strokeWidth = 2.dp,
                                color = AdfElectricCyan
                            )
                        } else {
                            AdfIcon.Refresh(size = 20.dp, color = AdfElectricCyan)
                        }
                    }

                    // FPS Tuning Dialog
                    IconButton(
                        onClick = { showRefreshRateDialog = true },
                        modifier = Modifier.testTag("refresh_rate_toggle_button")
                    ) {
                        AdfIcon.FpsSpeed(size = 20.dp)
                    }

                    // 3 Lines Core Suite Menu Button
                    IconButton(
                        onClick = { showCoreSuiteMenu = true },
                        modifier = Modifier
                            .testTag("core_suite_menu_button")
                            .size(42.dp)
                    ) {
                        Box(
                            modifier = Modifier
                                .size(34.dp)
                                .clip(RoundedCornerShape(8.dp))
                                .background(AdfElectricCyan.copy(alpha = 0.15f))
                                .border(1.dp, Brush.linearGradient(listOf(AdfElectricCyan, AdfVibrantBlue)), RoundedCornerShape(8.dp)),
                            contentAlignment = Alignment.Center
                        ) {
                            AdfIcon.MenuThreeLines(size = 18.dp, color = AdfElectricCyan)
                        }
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = AdfMidnightBackground)
            )
        },
        floatingActionButton = {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp),
                modifier = Modifier.navigationBarsPadding()
            ) {
                FloatingActionButton(
                    onClick = {
                        documentPickerLauncher.launch(
                            arrayOf(
                                "application/pdf",
                                "application/msword",
                                "application/vnd.openxmlformats-officedocument.wordprocessingml.document",
                                "text/plain"
                            )
                        )
                    },
                    containerColor = AdfSurfaceCard,
                    contentColor = AdfElectricCyan,
                    shape = RoundedCornerShape(14.dp),
                    modifier = Modifier
                        .size(48.dp)
                        .testTag("open_file_fab")
                ) {
                    AdfIcon.FileOpen(size = 20.dp, primaryColor = AdfElectricCyan, accentColor = AdfVibrantBlue)
                }

                ExtendedFloatingActionButton(
                    onClick = { viewModel.navigateTo(AppScreen.SCANNER) },
                    containerColor = AdfElectricCyan,
                    contentColor = AdfMidnightBackground,
                    shape = RoundedCornerShape(16.dp),
                    icon = { AdfIcon.Camera(size = 20.dp, primaryColor = AdfMidnightBackground, accentColor = AdfCoral) },
                    text = { Text("Scan Document", fontWeight = FontWeight.ExtraBold, fontSize = 13.sp) },
                    modifier = Modifier.testTag("scan_document_fab")
                )
            }
        },
        containerColor = AdfMidnightBackground
    ) { padding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding),
            contentAlignment = Alignment.TopCenter
        ) {
            LazyColumn(
                modifier = Modifier
                    .fillMaxSize()
                    .widthIn(max = 680.dp), // Responsive adaptive layout for all screens globally
                contentPadding = PaddingValues(bottom = 96.dp)
            ) {
                // Storage Access Permission Interactive Card (when needed)
                if (!hasStoragePerm) {
                    item {
                        Card(
                            shape = RoundedCornerShape(14.dp),
                            colors = CardDefaults.cardColors(containerColor = AdfObsidianSurface),
                            border = CardDefaults.outlinedCardBorder().copy(
                                brush = Brush.horizontalGradient(
                                    listOf(AdfElectricCyan.copy(alpha = 0.6f), AdfCyberViolet.copy(alpha = 0.6f))
                                )
                            ),
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 16.dp, vertical = 6.dp)
                                .testTag("storage_permission_banner")
                        ) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(12.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Box(
                                    modifier = Modifier
                                        .size(40.dp)
                                        .clip(RoundedCornerShape(10.dp))
                                        .background(AdfElectricCyan.copy(alpha = 0.15f)),
                                    contentAlignment = Alignment.Center
                                ) {
                                    AdfIcon.FileOpen(size = 20.dp, primaryColor = AdfElectricCyan, accentColor = AdfVibrantBlue)
                                }
                                Spacer(modifier = Modifier.width(10.dp))
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        text = "Scan Entire Phone Storage",
                                        fontWeight = FontWeight.Bold,
                                        fontSize = 13.sp,
                                        color = AdfTextPrimary
                                    )
                                    Text(
                                        text = "Allow All Files Access to discover all PDFs, Word & office files",
                                        fontSize = 11.sp,
                                        color = AdfTextSecondary
                                    )
                                }
                                Spacer(modifier = Modifier.width(8.dp))
                                Button(
                                    onClick = { triggerFullScan() },
                                    colors = ButtonDefaults.buttonColors(containerColor = AdfElectricCyan),
                                    shape = RoundedCornerShape(8.dp),
                                    contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp)
                                ) {
                                    Text("Grant", color = AdfMidnightBackground, fontSize = 11.5.sp, fontWeight = FontWeight.ExtraBold)
                                }
                                Spacer(modifier = Modifier.width(4.dp))
                                IconButton(
                                    onClick = {
                                        DeviceDocumentScanner.recordStoragePermissionGranted(context)
                                        hasStoragePerm = true
                                    },
                                    modifier = Modifier.size(30.dp)
                                ) {
                                    Icon(Icons.Default.Close, contentDescription = "Dismiss", tint = AdfTextMuted, modifier = Modifier.size(16.dp))
                                }
                            }
                        }
                    }
                }

                // Section 1: Scan to PDF & Core Suite Quick Menu Bar
                item {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp, vertical = 4.dp)
                    ) {
                        // Hero Scan to PDF Card (Adobe Scan Engine)
                        Card(
                            onClick = { viewModel.navigateTo(AppScreen.SCANNER) },
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(16.dp))
                                .border(
                                    width = 1.dp,
                                    brush = Brush.horizontalGradient(
                                        listOf(AdfCoral, AdfElectricCyan)
                                    ),
                                    shape = RoundedCornerShape(16.dp)
                                )
                                .testTag("hero_scan_document_card"),
                            colors = CardDefaults.cardColors(containerColor = AdfObsidianSurface)
                        ) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(14.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Box(
                                    modifier = Modifier
                                        .size(44.dp)
                                        .clip(RoundedCornerShape(12.dp))
                                        .background(AdfCoral.copy(alpha = 0.16f))
                                        .border(1.dp, AdfCoral.copy(alpha = 0.4f), RoundedCornerShape(12.dp)),
                                    contentAlignment = Alignment.Center
                                ) {
                                    AdfIcon.Camera(size = 24.dp, primaryColor = AdfCoral, accentColor = Color.White)
                                }
                                Spacer(modifier = Modifier.width(12.dp))
                                Column(modifier = Modifier.weight(1f)) {
                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        Text(
                                            text = "SCAN TO PDF",
                                            style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.ExtraBold),
                                            color = AdfTextPrimary
                                        )
                                        Spacer(modifier = Modifier.width(6.dp))
                                        Surface(
                                            shape = RoundedCornerShape(4.dp),
                                            color = AdfCoral.copy(alpha = 0.18f)
                                        ) {
                                            Text(
                                                text = "ADOBE SCAN",
                                                color = AdfCoral,
                                                fontSize = 8.5.sp,
                                                fontWeight = FontWeight.ExtraBold,
                                                modifier = Modifier.padding(horizontal = 5.dp, vertical = 1.dp)
                                            )
                                        }
                                    }
                                    Text(
                                        text = "Snap receipts, notes & contracts with Magic Color paper whitening",
                                        style = MaterialTheme.typography.bodySmall.copy(fontSize = 11.sp),
                                        color = AdfTextSecondary
                                    )
                                }
                                AdfIcon.ChevronRight(size = 20.dp, color = AdfElectricCyan)
                            }
                        }

                        Spacer(modifier = Modifier.height(8.dp))

                        Card(
                            modifier = Modifier
                                .fillMaxWidth()
                                .testTag("core_suite_quick_banner")
                                .clip(RoundedCornerShape(14.dp))
                                .clickable { showCoreSuiteMenu = true }
                                .border(
                                    width = 1.dp,
                                    brush = Brush.horizontalGradient(
                                        listOf(AdfElectricCyan.copy(alpha = 0.6f), AdfCyberViolet.copy(alpha = 0.4f), AdfVibrantBlue.copy(alpha = 0.5f))
                                    ),
                                    shape = RoundedCornerShape(14.dp)
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
                                            .size(36.dp)
                                            .clip(RoundedCornerShape(9.dp))
                                            .background(AdfElectricCyan.copy(alpha = 0.15f)),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        AdfIcon.MenuThreeLines(size = 18.dp, color = AdfElectricCyan)
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
                                                    text = "7 TOOLS",
                                                    color = AdfElectricCyan,
                                                    fontSize = 8.5.sp,
                                                    fontWeight = FontWeight.ExtraBold,
                                                    modifier = Modifier.padding(horizontal = 5.dp, vertical = 2.dp)
                                                )
                                            }
                                        }
                                        Text(
                                            text = "Scan • Convert • Credits • FPS • Pure Viewer • Sign • Watermark",
                                            style = MaterialTheme.typography.bodySmall.copy(fontSize = 11.sp),
                                            color = AdfTextSecondary,
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis
                                        )
                                    }
                                }
                                AdfIcon.ChevronRight(size = 20.dp, color = AdfElectricCyan)
                            }
                        }

                        Spacer(modifier = Modifier.height(8.dp))

                        // Fast Horizontal Shortcut Pills
                        LazyRow(
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            item {
                                QuickToolChip("1. Scan PDF", "CAMERA", AdfCoral) {
                                    viewModel.navigateTo(AppScreen.SCANNER)
                                }
                            }
                            item {
                                QuickToolChip("2. Convertion", "HUB", AdfElectricCyan) {
                                    viewModel.navigateTo(AppScreen.CONVERSION_HUB)
                                }
                            }
                            item {
                                QuickToolChip("3. Credits", "10/10/2026", Color(0xFFD946EF)) {
                                    viewModel.navigateTo(AppScreen.CREDITS)
                                }
                            }
                            item {
                                QuickToolChip("4. FPS", "${currentRefreshRate}Hz", Color(0xFF10B981)) {
                                    showRefreshRateDialog = true
                                }
                            }
                            item {
                                QuickToolChip("5. Pure Viewer", "NO EDIT", AdfCyberViolet) {
                                    viewModel.setPureViewerMode(true)
                                    val firstDoc = documents.firstOrNull()
                                    if (firstDoc != null) {
                                        viewModel.openDocument(firstDoc)
                                    } else {
                                        documentPickerLauncher.launch(arrayOf("application/pdf"))
                                    }
                                }
                            }
                            item {
                                QuickToolChip("6. Signature", "VAULT", AdfAmber) {
                                    viewModel.navigateTo(AppScreen.SIGNATURE_VAULT)
                                }
                            }
                            item {
                                QuickToolChip("7. Watermark", "STUDIO", AdfElectricCyan) {
                                    viewModel.setSelectedToolTab(4)
                                    viewModel.navigateTo(AppScreen.TOOLS)
                                }
                            }
                        }
                    }
                }

                // Section 2: Storage Document Library Header & Search
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
                                    style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Bold, letterSpacing = 1.sp),
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

                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Surface(
                                    onClick = { folderPickerLauncher.launch(null) },
                                    shape = RoundedCornerShape(8.dp),
                                    color = AdfElectricCyan.copy(alpha = 0.12f),
                                    border = CardDefaults.outlinedCardBorder().copy(
                                        brush = Brush.horizontalGradient(listOf(AdfElectricCyan, AdfVibrantBlue))
                                    ),
                                    modifier = Modifier.testTag("scan_folder_chip")
                                ) {
                                    Row(
                                        verticalAlignment = Alignment.CenterVertically,
                                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                                    ) {
                                        AdfIcon.FileOpen(size = 12.dp, primaryColor = AdfElectricCyan, accentColor = AdfElectricCyan)
                                        Spacer(modifier = Modifier.width(4.dp))
                                        Text(text = "Pick Folder", color = AdfElectricCyan, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                                    }
                                }

                                if (isScanning) {
                                    Spacer(modifier = Modifier.width(8.dp))
                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        CircularProgressIndicator(modifier = Modifier.size(12.dp), strokeWidth = 2.dp, color = AdfElectricCyan)
                                        Spacer(modifier = Modifier.width(4.dp))
                                        Text(text = "Scanning...", color = AdfElectricCyan, fontSize = 10.sp)
                                    }
                                }
                            }
                        }

                        Spacer(modifier = Modifier.height(8.dp))

                        // Search Input filtering by filename or extension
                        OutlinedTextField(
                            value = searchQuery,
                            onValueChange = { searchQuery = it },
                            placeholder = {
                                Text(
                                    text = "Search by filename or extension (.pdf, .docx, report)...",
                                    color = AdfTextMuted,
                                    fontSize = 13.sp,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                            },
                            leadingIcon = { AdfIcon.Search(size = 20.dp, primaryColor = AdfElectricCyan, accentColor = AdfVibrantBlue) },
                            trailingIcon = {
                                if (searchQuery.isNotEmpty()) {
                                    IconButton(
                                        onClick = { searchQuery = "" },
                                        modifier = Modifier.testTag("clear_search_button")
                                    ) {
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

                        // Category & Extension Quick Filter Pills
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
                                        brush = if (isSelected) Brush.horizontalGradient(listOf(AdfElectricCyan, AdfVibrantBlue))
                                        else Brush.horizontalGradient(listOf(AdfBorderSubtle, AdfBorderSubtle))
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

                        // Quick Extension Filter Chips
                        Spacer(modifier = Modifier.height(8.dp))
                        val extensionFilters = listOf(".pdf", ".docx", ".doc", ".txt", ".xlsx")
                        LazyRow(
                            horizontalArrangement = Arrangement.spacedBy(6.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            item {
                                Text(
                                    text = "Extensions:",
                                    color = AdfTextMuted,
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.Medium,
                                    modifier = Modifier.padding(end = 2.dp)
                                )
                            }
                            items(extensionFilters) { ext ->
                                val isExtSelected = searchQuery.equals(ext, ignoreCase = true) ||
                                        searchQuery.equals(ext.removePrefix("."), ignoreCase = true)
                                Surface(
                                    onClick = {
                                        searchQuery = if (isExtSelected) "" else ext
                                    },
                                    shape = RoundedCornerShape(10.dp),
                                    color = if (isExtSelected) AdfElectricCyan.copy(alpha = 0.2f) else AdfSurfaceCard,
                                    border = CardDefaults.outlinedCardBorder().copy(
                                        brush = if (isExtSelected) Brush.horizontalGradient(listOf(AdfElectricCyan, AdfVibrantBlue))
                                        else Brush.horizontalGradient(listOf(AdfBorderSubtle, AdfBorderSubtle))
                                    ),
                                    modifier = Modifier.testTag("filter_ext_${ext.removePrefix(".")}")
                                ) {
                                    Text(
                                        text = ext,
                                        color = if (isExtSelected) AdfElectricCyan else AdfTextSecondary,
                                        fontSize = 11.sp,
                                        fontWeight = if (isExtSelected) FontWeight.Bold else FontWeight.Normal,
                                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                                    )
                                }
                            }
                        }

                        if (searchQuery.isNotEmpty()) {
                            Spacer(modifier = Modifier.height(6.dp))
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.SpaceBetween,
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Text(
                                    text = "Found ${filteredDocs.size} matching \"$searchQuery\"",
                                    fontSize = 11.5.sp,
                                    color = AdfElectricCyan,
                                    fontWeight = FontWeight.Medium
                                )
                                Text(
                                    text = "Clear filter",
                                    fontSize = 11.5.sp,
                                    color = AdfTextSecondary,
                                    fontWeight = FontWeight.Bold,
                                    modifier = Modifier
                                        .clickable { searchQuery = "" }
                                        .padding(4.dp)
                                )
                            }
                        }
                    }
                }

                // Section 3: List of Phone Documents
                if (filteredDocs.isEmpty()) {
                    item {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(top = 40.dp, bottom = 20.dp),
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            AdfIcon.FolderEmpty(size = 54.dp, primaryColor = AdfTextMuted, accentColor = AdfElectricCyan)
                            Spacer(modifier = Modifier.height(12.dp))
                            Text(
                                text = if (searchQuery.isNotEmpty()) "No files match \"$searchQuery\"" else "No documents found on storage",
                                color = AdfTextSecondary,
                                style = MaterialTheme.typography.bodyLarge,
                                fontWeight = FontWeight.SemiBold
                            )
                            Spacer(modifier = Modifier.height(6.dp))
                            Text(
                                text = if (searchQuery.isNotEmpty()) "Try searching by another filename or extension" else "Scan whole storage or pick a folder to display your files",
                                color = AdfTextMuted,
                                style = MaterialTheme.typography.bodySmall
                            )
                            Spacer(modifier = Modifier.height(16.dp))
                            if (searchQuery.isNotEmpty()) {
                                Button(
                                    onClick = { searchQuery = "" },
                                    colors = ButtonDefaults.buttonColors(containerColor = AdfElectricCyan),
                                    shape = RoundedCornerShape(10.dp)
                                ) {
                                    AdfIcon.Close(size = 14.dp, color = AdfMidnightBackground)
                                    Spacer(modifier = Modifier.width(6.dp))
                                    Text("Clear Search Filter", color = AdfMidnightBackground, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                                }
                            } else {
                                Row(
                                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Button(
                                        onClick = { triggerFullScan() },
                                        colors = ButtonDefaults.buttonColors(containerColor = AdfElectricCyan),
                                        shape = RoundedCornerShape(10.dp)
                                    ) {
                                        AdfIcon.Refresh(size = 14.dp, color = AdfMidnightBackground)
                                        Spacer(modifier = Modifier.width(6.dp))
                                        Text("Scan Storage", color = AdfMidnightBackground, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                                    }

                                    Button(
                                        onClick = { folderPickerLauncher.launch(null) },
                                        colors = ButtonDefaults.buttonColors(containerColor = AdfSurfaceVariant),
                                        shape = RoundedCornerShape(10.dp)
                                    ) {
                                        AdfIcon.FileOpen(size = 14.dp, primaryColor = AdfElectricCyan, accentColor = AdfElectricCyan)
                                        Spacer(modifier = Modifier.width(6.dp))
                                        Text("Pick Folder", color = AdfElectricCyan, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                                    }
                                }
                            }
                        }
                    }
                } else {
                    items(
                        items = filteredDocs,
                        key = { it.id },
                        contentType = { it.fileType }
                    ) { item ->
                        UnifiedDocumentCard(
                            doc = item,
                            context = context,
                            onOpenStandard = {
                                viewModel.setPureViewerMode(false)
                                if (item.originalDoc != null) {
                                    viewModel.openDocument(item.originalDoc)
                                } else if (item.deviceFile != null) {
                                    viewModel.openDeviceFile(item.deviceFile, pureViewer = false)
                                }
                            },
                            onOpenPureViewer = {
                                viewModel.setPureViewerMode(true)
                                if (item.originalDoc != null) {
                                    viewModel.openDocument(item.originalDoc)
                                } else if (item.deviceFile != null) {
                                    viewModel.openDeviceFile(item.deviceFile, pureViewer = true)
                                }
                            },
                            onOrganize = {
                                if (item.originalDoc != null) {
                                    viewModel.openDocument(item.originalDoc)
                                    viewModel.navigateTo(AppScreen.PAGE_MANAGER)
                                } else if (item.deviceFile != null) {
                                    viewModel.openDeviceFile(item.deviceFile, pureViewer = false)
                                }
                            },
                            onConvertToOther = {
                                viewModel.setSelectedToolTab(if (item.isPdf) 3 else 2)
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
    }

    // Storage Permission Explanation Dialog
    if (showStoragePermissionDialog) {
        AlertDialog(
            onDismissRequest = {
                showStoragePermissionDialog = false
                DeviceDocumentScanner.recordStoragePermissionGranted(context)
                hasStoragePerm = true
            },
            containerColor = AdfSurfaceCard,
            shape = RoundedCornerShape(18.dp),
            title = {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    AdfIcon.FileOpen(size = 22.dp, primaryColor = AdfElectricCyan, accentColor = AdfVibrantBlue)
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = "Storage Permission",
                        color = AdfTextPrimary,
                        fontWeight = FontWeight.Bold,
                        fontSize = 17.sp
                    )
                }
            },
            text = {
                Text(
                    text = "To scan and display all PDF, Word (.docx), Excel, and text files stored across your entire internal storage and SD cards, PDF AIO needs All Files Access permission in Android Settings.\n\nYou can also select any specific folder via Storage Access Framework (one-time grant).",
                    color = AdfTextSecondary,
                    fontSize = 13.5.sp,
                    lineHeight = 19.sp
                )
            },
            confirmButton = {
                Button(
                    onClick = {
                        showStoragePermissionDialog = false
                        manageStorageLauncher.launch(DeviceDocumentScanner.getManageStorageIntent(context))
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = AdfElectricCyan),
                    shape = RoundedCornerShape(10.dp)
                ) {
                    Text("Grant All Files Access", color = AdfMidnightBackground, fontWeight = FontWeight.Bold, fontSize = 12.sp)
                }
            },
            dismissButton = {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    TextButton(
                        onClick = {
                            showStoragePermissionDialog = false
                            DeviceDocumentScanner.recordStoragePermissionGranted(context)
                            hasStoragePerm = true
                        }
                    ) {
                        Text("Don't Ask Again", color = AdfTextMuted, fontSize = 11.5.sp)
                    }
                    OutlinedButton(
                        onClick = {
                            showStoragePermissionDialog = false
                            folderPickerLauncher.launch(null)
                        },
                        shape = RoundedCornerShape(10.dp)
                    ) {
                        Text("Select Folder (SAF)", color = AdfElectricCyan, fontSize = 12.sp)
                    }
                }
            }
        )
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
                        "application/vnd.openxmlformats-officedocument.wordprocessingml.document",
                        "text/plain"
                    )
                )
            },
            onCheckUpdates = {
                updateViewModel?.checkForUpdates(context, manual = true)
            },
            onOpenUpdateSettings = {
                updateViewModel?.openSettingsDialog()
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
                    viewModel.openDeviceFile(doc.deviceFile, pureViewer = false)
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
 * Unified Card displaying either a PDF, Word, Excel, or Text document with bespoke custom icons
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

    val badgeColor = when (doc.fileType) {
        DeviceFileType.PDF -> AdfCoral
        DeviceFileType.WORD_DOCX, DeviceFileType.WORD_DOC -> Color(0xFF3B82F6)
        DeviceFileType.EXCEL -> Color(0xFF10B981)
        DeviceFileType.POWERPOINT -> Color(0xFFF97316)
        DeviceFileType.TEXT -> Color(0xFF8B5CF6)
        else -> AdfElectricCyan
    }

    val badgeText = when (doc.fileType) {
        DeviceFileType.PDF -> "PDF"
        DeviceFileType.WORD_DOCX -> "DOCX"
        DeviceFileType.WORD_DOC -> "DOC"
        DeviceFileType.EXCEL -> "XLS"
        DeviceFileType.POWERPOINT -> "PPT"
        DeviceFileType.TEXT -> "TXT"
        else -> "FILE"
    }

    Card(
        onClick = onOpenStandard,
        colors = CardDefaults.cardColors(containerColor = AdfObsidianSurface),
        shape = RoundedCornerShape(14.dp),
        border = CardDefaults.outlinedCardBorder().copy(
            brush = Brush.linearGradient(
                listOf(
                    AdfBorderSubtle,
                    badgeColor.copy(alpha = 0.25f)
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
            // Icon / Document Type Badge (Instant GPU-rendered vector, silky-smooth 60+ FPS, eliminates back-button glitch)
            Box(
                modifier = Modifier
                    .size(46.dp)
                    .clip(RoundedCornerShape(12.dp))
                    .background(badgeColor.copy(alpha = 0.14f))
                    .border(1.dp, badgeColor.copy(alpha = 0.28f), RoundedCornerShape(12.dp))
                    .clickable(enabled = doc.isPdf) { onPreviewFirstPage() },
                contentAlignment = Alignment.Center
            ) {
                when (doc.fileType) {
                    DeviceFileType.PDF -> {
                        AdfIcon.PdfDoc(size = 28.dp, primaryColor = AdfCoral, accentColor = Color.White)
                    }
                    DeviceFileType.WORD_DOCX, DeviceFileType.WORD_DOC -> {
                        AdfIcon.WordDoc(size = 28.dp, primaryColor = Color(0xFF3B82F6), accentColor = Color.White)
                    }
                    DeviceFileType.EXCEL -> {
                        AdfIcon.FileOpen(size = 26.dp, primaryColor = Color(0xFF10B981), accentColor = Color.White)
                    }
                    DeviceFileType.POWERPOINT -> {
                        AdfIcon.FileOpen(size = 26.dp, primaryColor = Color(0xFFF97316), accentColor = Color.White)
                    }
                    DeviceFileType.TEXT -> {
                        AdfIcon.FileOpen(size = 26.dp, primaryColor = Color(0xFF8B5CF6), accentColor = Color.White)
                    }
                    else -> {
                        AdfIcon.FileOpen(size = 26.dp, primaryColor = badgeColor, accentColor = Color.White)
                    }
                }
            }

            Spacer(modifier = Modifier.width(12.dp))

            // Details
            Column(modifier = Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = doc.title,
                        style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.SemiBold, fontSize = 14.sp),
                        color = AdfTextPrimary,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f, fill = false)
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Surface(
                        shape = RoundedCornerShape(4.dp),
                        color = badgeColor.copy(alpha = 0.15f)
                    ) {
                        Text(
                            text = badgeText,
                            style = MaterialTheme.typography.labelSmall.copy(fontSize = 8.sp, fontWeight = FontWeight.ExtraBold),
                            color = badgeColor,
                            modifier = Modifier.padding(horizontal = 4.dp, vertical = 1.dp)
                        )
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
                            text = unifiedDocDateFormat.format(Date(doc.dateModified)),
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
                AdfIcon.PureViewer(size = 18.dp, primaryColor = AdfElectricCyan, accentColor = Color(0xFF10B981))
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
                    AdfIcon.MenuDots(size = 18.dp, color = AdfTextSecondary)
                }

                DropdownMenu(
                    expanded = showMenu,
                    onDismissRequest = { showMenu = false },
                    modifier = Modifier.background(AcrobatDarkSurfaceElevated)
                ) {
                    DropdownMenuItem(
                        text = { Text("Open Reader & Tools", color = AdfTextPrimary) },
                        leadingIcon = { AdfIcon.Tools(size = 18.dp, primaryColor = AdfElectricCyan) },
                        onClick = {
                            showMenu = false
                            onOpenStandard()
                        }
                    )
                    DropdownMenuItem(
                        text = { Text("Pure Viewer (Distraction Free)", color = AdfTextPrimary) },
                        leadingIcon = { AdfIcon.PureViewer(size = 18.dp, primaryColor = Color(0xFF10B981)) },
                        onClick = {
                            showMenu = false
                            onOpenPureViewer()
                        }
                    )
                    if (doc.isPdf) {
                        DropdownMenuItem(
                            text = { Text("Preview First Page", color = AdfElectricCyan) },
                            leadingIcon = { AdfIcon.PureViewer(size = 18.dp, primaryColor = AdfElectricCyan) },
                            onClick = {
                                showMenu = false
                                onPreviewFirstPage()
                            }
                        )
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
                            text = { Text("Convert to PDF & View", color = AdfTextPrimary) },
                            leadingIcon = { AdfIcon.PdfDoc(size = 18.dp, primaryColor = AdfCoral) },
                            onClick = {
                                showMenu = false
                                onOpenStandard()
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

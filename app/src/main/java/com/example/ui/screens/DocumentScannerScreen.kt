package com.example.ui.screens

import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color as AndroidColor
import android.graphics.Paint
import android.net.Uri
import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.*
import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.RotateLeft
import androidx.compose.material.icons.automirrored.filled.RotateRight
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.FileProvider
import com.example.data.local.DocumentEntity
import com.example.data.scan.DocumentOcrEngine
import com.example.data.scan.DocumentScanProcessor
import com.example.data.scan.OcrPageResult
import com.example.data.scan.ScanFilter
import com.example.data.scan.ScannedPage
import com.example.ui.AppScreen
import com.example.ui.PdfViewModel
import com.example.ui.components.AdfIcon
import com.example.ui.theme.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream
import java.text.SimpleDateFormat
import java.util.*

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DocumentScannerScreen(
    viewModel: PdfViewModel,
    onBack: () -> Unit
) {
    BackHandler { onBack() }

    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()
    val activeThemeStyle by ThemeManager.currentTheme.collectAsState()
    val isLiquidTheme = activeThemeStyle == AppThemeStyle.LIQUID_GLASS || activeThemeStyle == AppThemeStyle.FROSTED_CRYSTAL

    val scannedPages = remember { mutableStateListOf<ScannedPage>() }
    var selectedPageIndex by remember { mutableIntStateOf(0) }
    var isCaptureViewActive by remember { mutableStateOf(true) }
    var isFlashOn by remember { mutableStateOf(false) }
    var isCropOverlayActive by remember { mutableStateOf(false) }
    var documentTitle by remember {
        mutableStateOf("Scan_${SimpleDateFormat("dd_MMM_yyyy", Locale.getDefault()).format(Date())}")
    }

    var isProcessingPdf by remember { mutableStateOf(false) }
    var savedPdfEntity by remember { mutableStateOf<DocumentEntity?>(null) }
    var previewBitmap by remember { mutableStateOf<Bitmap?>(null) }
    var filterThumbnailsMap by remember { mutableStateOf<Map<ScanFilter, Bitmap>>(emptyMap()) }

    var isRunningOcr by remember { mutableStateOf(false) }
    var ocrResultsMap by remember { mutableStateOf<Map<Int, OcrPageResult>>(emptyMap()) }
    var showOcrDialog by remember { mutableStateOf(false) }
    var editedTextMap by remember { mutableStateOf<MutableMap<Int, String>>(mutableMapOf()) }

    var tempCameraImageUri by remember { mutableStateOf<Uri?>(null) }
    val trackedTempFiles = remember { mutableListOf<File>() }

    DisposableEffect(Unit) {
        onDispose {
            trackedTempFiles.forEach { file ->
                try {
                    if (file.exists()) file.delete()
                } catch (_: Exception) {}
            }
            trackedTempFiles.clear()
        }
    }

    // Creates a temporary file in cache for full-resolution camera captures
    fun createTempImageUri(): Uri {
        val tempFile = File.createTempFile(
            "scan_capture_${System.currentTimeMillis()}",
            ".jpg",
            context.cacheDir
        ).apply { createNewFile() }
        trackedTempFiles.add(tempFile)
        return FileProvider.getUriForFile(
            context,
            "${context.packageName}.fileprovider",
            tempFile
        )
    }

    // Camera Take-Picture Launcher
    val cameraLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.TakePicture()
    ) { success ->
        if (success && tempCameraImageUri != null) {
            val newPage = ScannedPage(
                sourceUri = tempCameraImageUri!!,
                filter = ScanFilter.WHITE_GREY,
                filterIntensity = 1.0f
            )
            scannedPages.add(newPage)
            selectedPageIndex = scannedPages.size - 1
            isCaptureViewActive = false
        }
    }

    // Camera Permission Launcher
    val cameraPermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { granted ->
        if (granted) {
            val uri = createTempImageUri()
            tempCameraImageUri = uri
            cameraLauncher.launch(uri)
        } else {
            Toast.makeText(context, "Camera permission needed to snap documents", Toast.LENGTH_SHORT).show()
        }
    }

    // Gallery / Photos Multi-Picker Launcher
    val photoPickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.PickMultipleVisualMedia()
    ) { uris ->
        if (uris.isNotEmpty()) {
            uris.forEach { uri ->
                scannedPages.add(
                    ScannedPage(
                        sourceUri = uri,
                        filter = ScanFilter.WHITE_GREY,
                        filterIntensity = 1.0f
                    )
                )
            }
            selectedPageIndex = scannedPages.size - 1
            isCaptureViewActive = false
        }
    }

    fun launchCameraCapture() {
        val hasCamPerm = androidx.core.content.ContextCompat.checkSelfPermission(
            context,
            android.Manifest.permission.CAMERA
        ) == android.content.pm.PackageManager.PERMISSION_GRANTED

        if (hasCamPerm) {
            val uri = createTempImageUri()
            tempCameraImageUri = uri
            cameraLauncher.launch(uri)
        } else {
            cameraPermissionLauncher.launch(android.Manifest.permission.CAMERA)
        }
    }

    // Helper to generate a crisp realistic demo document for emulator testing
    fun createSampleDocument(type: String) {
        coroutineScope.launch(Dispatchers.IO) {
            val bmp = Bitmap.createBitmap(1200, 1600, Bitmap.Config.ARGB_8888)
            val c = Canvas(bmp)
            c.drawColor(AndroidColor.WHITE)

            val p = Paint(Paint.ANTI_ALIAS_FLAG)
            p.color = AndroidColor.rgb(18, 18, 22)

            // Header Banner
            p.textSize = 52f
            p.isFakeBoldText = true
            c.drawText(if (type == "contract") "MUTUAL NON-DISCLOSURE AGREEMENT" else "OFFICIAL PAYMENT INVOICE", 80f, 160f, p)

            p.textSize = 28f
            p.isFakeBoldText = false
            p.color = AndroidColor.GRAY
            c.drawText("PDF (AIO) Document Scanner • 100% On-Device Engine", 80f, 210f, p)
            c.drawLine(80f, 240f, 1120f, 240f, p)

            // Body text lines
            p.color = AndroidColor.DKGRAY
            p.textSize = 32f
            val sampleLines = if (type == "contract") {
                listOf(
                    "1. PURPOSE OF DISCLOSURE: The Parties wish to explore",
                    "a strategic business relationship concerning technology architecture.",
                    "",
                    "2. CONFIDENTIALITY OBLIGATIONS: The receiving party agrees to protect",
                    "proprietary technical schematics, software logic, and trade secrets.",
                    "",
                    "3. TERM & TERMINATION: This Agreement remains in effect for two (2)",
                    "years from the Effective Date set forth below.",
                    "",
                    "4. GOVERNING LAW: This document constitutes a binding agreement.",
                    "",
                    "Authorized Signatures:",
                    "[Digital Stamp Verified • 100% On-Device Local Protocol]"
                )
            } else {
                listOf(
                    "INVOICE #: INV-2026-08912          DATE: Oct 28, 2026",
                    "BILLED TO: Enterprise Global Corp.",
                    "",
                    "ITEM DESCRIPTION                     QTY    RATE      AMOUNT",
                    "-------------------------------------------------------------",
                    "PDF (AIO) Pro Document Suite License  1     $199.00   $199.00",
                    "Hardware Refresh Rate Accelerator     1     $49.00    $49.00",
                    "Offline Digital Signature Vault       1     $79.00    $79.00",
                    "-------------------------------------------------------------",
                    "TOTAL AMOUNT DUE:                                     $327.00",
                    "",
                    "PAYMENT STATUS: PAID IN FULL [APPROVED]"
                )
            }

            var y = 320f
            sampleLines.forEach { line ->
                c.drawText(line, 80f, y, p)
                y += 56f
            }

            // Save demo to file
            val sampleFile = File(context.cacheDir, "sample_scan_${System.currentTimeMillis()}.png")
            FileOutputStream(sampleFile).use { out ->
                bmp.compress(Bitmap.CompressFormat.PNG, 100, out)
            }
            bmp.recycle()
            trackedTempFiles.add(sampleFile)

            val page = ScannedPage(
                sourceUri = Uri.fromFile(sampleFile),
                filter = ScanFilter.WHITE_GREY,
                filterIntensity = 1.0f
            )

            withContext(Dispatchers.Main) {
                scannedPages.add(page)
                selectedPageIndex = scannedPages.size - 1
                isCaptureViewActive = false
            }
        }
    }

    val activePage = scannedPages.getOrNull(selectedPageIndex)
    val activeFilter = activePage?.filter ?: ScanFilter.WHITE_GREY
    val activeRotation = activePage?.rotationDegrees ?: 0
    val activeIntensity = activePage?.filterIntensity ?: 1.0f

    var cachedRawBitmap by remember { mutableStateOf<Bitmap?>(null) }
    var cachedUri by remember { mutableStateOf<Uri?>(null) }

    // Asynchronously refresh the high-resolution preview bitmap with in-memory caching
    LaunchedEffect(
        selectedPageIndex,
        activeFilter,
        activeRotation,
        activeIntensity,
        isCaptureViewActive,
        activePage?.id,
        scannedPages.size
    ) {
        if (!isCaptureViewActive && activePage != null) {
            val loadedRaw = if (cachedRawBitmap != null && cachedUri == activePage.sourceUri) {
                cachedRawBitmap
            } else {
                withContext(Dispatchers.IO) {
                    DocumentScanProcessor.loadBitmapFromUri(context, activePage.sourceUri)
                }?.also {
                    cachedRawBitmap = it
                    cachedUri = activePage.sourceUri
                }
            }

            if (loadedRaw != null) {
                val processed = withContext(Dispatchers.Default) {
                    DocumentScanProcessor.processBitmap(
                        source = loadedRaw,
                        filter = activeFilter,
                        rotationDegrees = activeRotation,
                        filterIntensity = activeIntensity
                    )
                }
                previewBitmap = processed
            }
        }
    }

    // Generate live thumbnail previews for each filter option in the filter bar
    LaunchedEffect(selectedPageIndex, isCaptureViewActive, activePage?.sourceUri) {
        if (!isCaptureViewActive && activePage != null) {
            withContext(Dispatchers.IO) {
                val raw = DocumentScanProcessor.loadBitmapFromUri(context, activePage.sourceUri, maxDimension = 300)
                if (raw != null) {
                    val thumbs = mutableMapOf<ScanFilter, Bitmap>()
                    ScanFilter.entries.forEach { filterOption ->
                        val thumb = DocumentScanProcessor.createFilterPreviewThumbnail(raw, filterOption, targetSize = 100)
                        thumbs[filterOption] = thumb
                    }
                    withContext(Dispatchers.Main) {
                        filterThumbnailsMap = thumbs
                    }
                }
            }
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Box(
                            modifier = Modifier
                                .size(34.dp)
                                .clip(RoundedCornerShape(8.dp))
                                .background(AdfElectricCyan.copy(alpha = 0.15f))
                                .border(1.dp, Brush.linearGradient(listOf(AdfElectricCyan, AdfVibrantBlue)), RoundedCornerShape(8.dp)),
                            contentAlignment = Alignment.Center
                        ) {
                            AdfIcon.Camera(size = 20.dp, primaryColor = AdfElectricCyan, accentColor = AdfCoral)
                        }
                        Spacer(modifier = Modifier.width(10.dp))
                        Column {
                            Text(
                                text = "Document Scanner",
                                style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                                color = AdfTextPrimary
                            )
                            Text(
                                text = if (scannedPages.isEmpty()) "Live Camera • Align Document" else "${scannedPages.size} ${if (scannedPages.size == 1) "Page" else "Pages"} Scanned",
                                style = MaterialTheme.typography.bodySmall.copy(fontSize = 11.sp),
                                color = AdfTextSecondary
                            )
                        }
                    }
                },
                navigationIcon = {
                    IconButton(
                        onClick = onBack,
                        modifier = Modifier.testTag("scanner_back_button")
                    ) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = "Back",
                            tint = AdfTextPrimary
                        )
                    }
                },
                actions = {
                    // Flash Toggle
                    IconButton(
                        onClick = {
                            isFlashOn = !isFlashOn
                            Toast.makeText(context, if (isFlashOn) "Flash Torch Active" else "Torch Off", Toast.LENGTH_SHORT).show()
                        },
                        modifier = Modifier.testTag("scanner_flash_toggle")
                    ) {
                        Icon(
                            imageVector = if (isFlashOn) Icons.Default.FlashOn else Icons.Default.FlashOff,
                            contentDescription = "Toggle Flash",
                            tint = if (isFlashOn) AdfAmber else AdfTextMuted
                        )
                    }

                    // Add Page icon when reviewing
                    if (!isCaptureViewActive) {
                        IconButton(
                            onClick = { isCaptureViewActive = true },
                            modifier = Modifier.testTag("scanner_add_page_header_button")
                        ) {
                            Icon(
                                imageVector = Icons.Default.AddAPhoto,
                                contentDescription = "Add Page",
                                tint = AdfElectricCyan
                            )
                        }
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = if (isLiquidTheme) GlassSurfaceDark else AdfMidnightBackground
                )
            )
        },
        containerColor = if (isLiquidTheme) Color.Transparent else AdfMidnightBackground
    ) { padding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
        ) {
            if (isLiquidTheme) {
                LiquidGlassBackground {}
            }

            if (isCaptureViewActive) {
                // ==============================================================
                // VIEW 1: LIVE DOCUMENT VIEWFINDER & SHUTTER DOCK
                // ==============================================================
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(16.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.SpaceBetween
                ) {
                    // Header Guidance Pill
                    Surface(
                        shape = RoundedCornerShape(20.dp),
                        color = if (isLiquidTheme) GlassSurfaceDark else AdfObsidianSurface,
                        border = CardDefaults.outlinedCardBorder().copy(
                            brush = Brush.horizontalGradient(listOf(AdfElectricCyan.copy(alpha = 0.5f), AdfVibrantBlue.copy(alpha = 0.5f)))
                        )
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.padding(horizontal = 14.dp, vertical = 8.dp)
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(8.dp)
                                    .clip(CircleShape)
                                    .background(AdfElectricCyan)
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = "Auto edge detection • Position document in view",
                                color = AdfTextPrimary,
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Medium
                            )
                        }
                    }

                    // Central Viewfinder Reticle Frame (A4 document shape)
                    Box(
                        modifier = Modifier
                            .fillMaxWidth(0.92f)
                            .weight(1f)
                            .padding(vertical = 12.dp)
                            .clip(RoundedCornerShape(16.dp))
                            .background(if (isLiquidTheme) Color(0x3B0F172A) else Color(0xFF0F172A))
                            .border(
                                width = 2.dp,
                                brush = Brush.linearGradient(
                                    listOf(AdfElectricCyan, AdfVibrantBlue, AdfElectricCyan)
                                ),
                                shape = RoundedCornerShape(16.dp)
                            ),
                        contentAlignment = Alignment.Center
                    ) {
                        // Corner Target Reticle Brackets
                        Canvas(modifier = Modifier.fillMaxSize()) {
                            val w = size.width
                            val h = size.height
                            val arm = 36f
                            val stroke = 6f
                            val col = AdfElectricCyan

                            // Top-Left
                            drawLine(col, androidx.compose.ui.geometry.Offset(24f, 24f), androidx.compose.ui.geometry.Offset(24f + arm, 24f), stroke)
                            drawLine(col, androidx.compose.ui.geometry.Offset(24f, 24f), androidx.compose.ui.geometry.Offset(24f, 24f + arm), stroke)

                            // Top-Right
                            drawLine(col, androidx.compose.ui.geometry.Offset(w - 24f, 24f), androidx.compose.ui.geometry.Offset(w - 24f - arm, 24f), stroke)
                            drawLine(col, androidx.compose.ui.geometry.Offset(w - 24f, 24f), androidx.compose.ui.geometry.Offset(w - 24f, 24f + arm), stroke)

                            // Bottom-Left
                            drawLine(col, androidx.compose.ui.geometry.Offset(24f, h - 24f), androidx.compose.ui.geometry.Offset(24f + arm, h - 24f), stroke)
                            drawLine(col, androidx.compose.ui.geometry.Offset(24f, h - 24f), androidx.compose.ui.geometry.Offset(24f, h - 24f - arm), stroke)

                            // Bottom-Right
                            drawLine(col, androidx.compose.ui.geometry.Offset(w - 24f, h - 24f), androidx.compose.ui.geometry.Offset(w - 24f - arm, h - 24f), stroke)
                            drawLine(col, androidx.compose.ui.geometry.Offset(w - 24f, h - 24f), androidx.compose.ui.geometry.Offset(w - 24f, h - 24f - arm), stroke)
                        }

                        // Guidance Watermark
                        Column(
                            horizontalAlignment = Alignment.CenterHorizontally,
                            modifier = Modifier.padding(24.dp)
                        ) {
                            AdfIcon.Camera(size = 56.dp, primaryColor = AdfElectricCyan, accentColor = AdfCoral)
                            Spacer(modifier = Modifier.height(14.dp))
                            Text(
                                text = "READY TO SCAN",
                                style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.ExtraBold, letterSpacing = 1.sp),
                                color = AdfTextPrimary
                            )
                            Spacer(modifier = Modifier.height(6.dp))
                            Text(
                                text = "Tap the round Shutter button below to capture, or pick existing documents from your device library.",
                                style = MaterialTheme.typography.bodySmall,
                                color = AdfTextSecondary,
                                textAlign = TextAlign.Center
                            )

                            Spacer(modifier = Modifier.height(16.dp))

                            // One-tap sample document buttons (for instant emulator testing)
                            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                OutlinedButton(
                                    onClick = { createSampleDocument("contract") },
                                    shape = RoundedCornerShape(8.dp),
                                    colors = ButtonDefaults.outlinedButtonColors(contentColor = AdfElectricCyan),
                                    modifier = Modifier.testTag("scan_sample_contract_button")
                                ) {
                                    Text("Demo Contract", fontSize = 11.sp, fontWeight = FontWeight.Bold)
                                }
                                OutlinedButton(
                                    onClick = { createSampleDocument("invoice") },
                                    shape = RoundedCornerShape(8.dp),
                                    colors = ButtonDefaults.outlinedButtonColors(contentColor = AdfCoral),
                                    modifier = Modifier.testTag("scan_sample_invoice_button")
                                ) {
                                    Text("Demo Invoice", fontSize = 11.sp, fontWeight = FontWeight.Bold)
                                }
                            }
                        }
                    }

                    // Bottom Shutter & Controls Dock
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 20.dp, vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        // Import from Photos
                        IconButton(
                            onClick = {
                                photoPickerLauncher.launch(
                                    PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)
                                )
                            },
                            modifier = Modifier
                                .size(52.dp)
                                .clip(CircleShape)
                                .background(if (isLiquidTheme) GlassSurfaceDark else AdfSurfaceCard)
                                .border(1.dp, AdfBorderSubtle, CircleShape)
                                .testTag("scanner_pick_photos_button")
                        ) {
                            Icon(
                                imageVector = Icons.Default.PhotoLibrary,
                                contentDescription = "Import Photos",
                                tint = AdfElectricCyan
                            )
                        }

                        // Central Shutter Button with glowing outer ring
                        Box(
                            modifier = Modifier
                                .size(82.dp)
                                .clip(CircleShape)
                                .background(AdfElectricCyan.copy(alpha = 0.2f))
                                .border(2.dp, AdfElectricCyan, CircleShape)
                                .clickable { launchCameraCapture() }
                                .testTag("scanner_shutter_button"),
                            contentAlignment = Alignment.Center
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(64.dp)
                                    .clip(CircleShape)
                                    .background(Color.White)
                                    .shadow(elevation = 6.dp, shape = CircleShape)
                            )
                        }

                        // Done / Review badge (if any pages already scanned)
                        if (scannedPages.isNotEmpty()) {
                            BadgeBox(
                                count = scannedPages.size,
                                onClick = { isCaptureViewActive = false }
                            )
                        } else {
                            Spacer(modifier = Modifier.size(52.dp))
                        }
                    }
                }
            } else {
                // ==============================================================
                // VIEW 2: DOCUMENT REVIEW & INTERACTIVE FILTER STUDIO
                // ==============================================================
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(horizontal = 14.dp, vertical = 6.dp)
                ) {
                    // Document Title Input & Direct Page Tools
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(bottom = 8.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        OutlinedTextField(
                            value = documentTitle,
                            onValueChange = { documentTitle = it },
                            label = { Text("PDF Document Title", fontSize = 11.sp, color = AdfElectricCyan) },
                            singleLine = true,
                            colors = OutlinedTextFieldDefaults.colors(
                                focusedTextColor = AdfTextPrimary,
                                unfocusedTextColor = AdfTextPrimary,
                                focusedBorderColor = AdfElectricCyan,
                                unfocusedBorderColor = AdfBorderSubtle,
                                focusedContainerColor = if (isLiquidTheme) GlassSurfaceDark else AdfObsidianSurface,
                                unfocusedContainerColor = if (isLiquidTheme) GlassSurfaceDark else AdfObsidianSurface
                            ),
                            shape = RoundedCornerShape(10.dp),
                            modifier = Modifier
                                .weight(1f)
                                .testTag("scanned_pdf_title_input")
                        )

                        Spacer(modifier = Modifier.width(8.dp))

                        // OCR Text Recognition Button
                        IconButton(
                            onClick = {
                                if (scannedPages.isEmpty()) return@IconButton
                                isRunningOcr = true
                                coroutineScope.launch {
                                    try {
                                        val results = DocumentOcrEngine.processPagesOcr(context, scannedPages.toList())
                                        ocrResultsMap = results
                                        val initialEdits = mutableMapOf<Int, String>()
                                        results.forEach { (idx, res) -> initialEdits[idx] = res.fullText }
                                        editedTextMap = initialEdits
                                        isRunningOcr = false
                                        showOcrDialog = true
                                        Toast.makeText(context, "OCR Complete: Extracted text from ${results.size} pages", Toast.LENGTH_SHORT).show()
                                    } catch (e: Exception) {
                                        e.printStackTrace()
                                        isRunningOcr = false
                                        Toast.makeText(context, "OCR Error: ${e.message}", Toast.LENGTH_SHORT).show()
                                    }
                                }
                            },
                            modifier = Modifier
                                .size(44.dp)
                                .clip(RoundedCornerShape(10.dp))
                                .background(AdfElectricCyan.copy(alpha = 0.15f))
                                .border(1.dp, AdfElectricCyan.copy(alpha = 0.5f), RoundedCornerShape(10.dp))
                                .testTag("scanner_ocr_button")
                        ) {
                            if (isRunningOcr) {
                                CircularProgressIndicator(
                                    modifier = Modifier.size(20.dp),
                                    color = AdfElectricCyan,
                                    strokeWidth = 2.dp
                                )
                            } else {
                                Icon(
                                    imageVector = Icons.Default.FindInPage,
                                    contentDescription = "Extract Text (OCR)",
                                    tint = AdfElectricCyan
                                )
                            }
                        }

                        Spacer(modifier = Modifier.width(6.dp))

                        // Toggle Crop Overlay
                        IconButton(
                            onClick = {
                                isCropOverlayActive = !isCropOverlayActive
                                Toast.makeText(
                                    context,
                                    if (isCropOverlayActive) "Edge cropping alignment active" else "Crop guides hidden",
                                    Toast.LENGTH_SHORT
                                ).show()
                            },
                            modifier = Modifier
                                .size(44.dp)
                                .clip(RoundedCornerShape(10.dp))
                                .background(if (isCropOverlayActive) AdfElectricCyan.copy(alpha = 0.25f) else (if (isLiquidTheme) GlassSurfaceDark else AdfSurfaceCard))
                                .border(1.dp, if (isCropOverlayActive) AdfElectricCyan else AdfBorderSubtle, RoundedCornerShape(10.dp))
                                .testTag("scanner_crop_button")
                        ) {
                            Icon(
                                imageVector = Icons.Default.Crop,
                                contentDescription = "Adjust Crop",
                                tint = if (isCropOverlayActive) AdfElectricCyan else AdfTextPrimary
                            )
                        }
                    }

                    // Main Interactive Document Preview Box
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .weight(1f)
                            .clip(RoundedCornerShape(14.dp))
                            .background(if (isLiquidTheme) GlassSurfaceDark else AdfObsidianSurface)
                            .border(
                                1.dp,
                                if (isLiquidTheme) LiquidGlassBorderGradient else Brush.horizontalGradient(listOf(AdfBorderSubtle, AdfBorderSubtle)),
                                RoundedCornerShape(14.dp)
                            )
                            .padding(8.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        val bmp = previewBitmap
                        if (bmp != null) {
                            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                                Image(
                                    bitmap = bmp.asImageBitmap(),
                                    contentDescription = "Scanned Page Preview",
                                    modifier = Modifier
                                        .fillMaxSize()
                                        .shadow(elevation = 6.dp, shape = RoundedCornerShape(4.dp)),
                                    contentScale = ContentScale.Fit
                                )

                                // Interactive Crop Boundary Overlay lines + Apply Auto-Trim Button
                                if (isCropOverlayActive) {
                                    Canvas(modifier = Modifier.fillMaxSize()) {
                                        val w = size.width
                                        val h = size.height
                                        val pad = 24f
                                        val strokeWidth = 4f
                                        val lineCol = AdfElectricCyan

                                        // Edge rectangle boundary
                                        drawRect(
                                            color = lineCol,
                                            topLeft = androidx.compose.ui.geometry.Offset(pad, pad),
                                            size = androidx.compose.ui.geometry.Size(w - (pad * 2), h - (pad * 2)),
                                            style = androidx.compose.ui.graphics.drawscope.Stroke(
                                                width = strokeWidth,
                                                pathEffect = androidx.compose.ui.graphics.PathEffect.dashPathEffect(floatArrayOf(12f, 12f))
                                            )
                                        )

                                        // Corner Handles
                                        val handleR = 14f
                                        val handleColor = AdfCoral
                                        drawCircle(handleColor, handleR, androidx.compose.ui.geometry.Offset(pad, pad))
                                        drawCircle(handleColor, handleR, androidx.compose.ui.geometry.Offset(w - pad, pad))
                                        drawCircle(handleColor, handleR, androidx.compose.ui.geometry.Offset(pad, h - pad))
                                        drawCircle(handleColor, handleR, androidx.compose.ui.geometry.Offset(w - pad, h - pad))
                                    }

                                    Button(
                                        onClick = {
                                            val pageToCrop = activePage ?: return@Button
                                            val idx = selectedPageIndex
                                            if (idx !in scannedPages.indices) return@Button
                                            coroutineScope.launch {
                                                val croppedUri = withContext(Dispatchers.IO) {
                                                    val raw = DocumentScanProcessor.loadBitmapFromUri(context, pageToCrop.sourceUri)
                                                        ?: return@withContext null
                                                    val trimmed = DocumentScanProcessor.trimPageMargins(raw, 0.06f)
                                                    if (trimmed !== raw && !raw.isRecycled) raw.recycle()
                                                    val outFile = File(context.cacheDir, "scan_crop_${System.currentTimeMillis()}.jpg")
                                                    FileOutputStream(outFile).use { out ->
                                                        trimmed.compress(Bitmap.CompressFormat.JPEG, 92, out)
                                                    }
                                                    trimmed.recycle()
                                                    trackedTempFiles.add(outFile)
                                                    Uri.fromFile(outFile)
                                                }
                                                if (croppedUri != null) {
                                                    cachedRawBitmap = null
                                                    cachedUri = null
                                                    scannedPages[idx] = pageToCrop.copy(
                                                        sourceUri = croppedUri,
                                                        id = UUID.randomUUID().toString()
                                                    )
                                                    isCropOverlayActive = false
                                                    Toast.makeText(context, "Margins trimmed cleanly", Toast.LENGTH_SHORT).show()
                                                }
                                            }
                                        },
                                        colors = ButtonDefaults.buttonColors(containerColor = AdfCoral),
                                        shape = RoundedCornerShape(18.dp),
                                        contentPadding = PaddingValues(horizontal = 14.dp, vertical = 6.dp),
                                        modifier = Modifier
                                            .align(Alignment.TopCenter)
                                            .padding(top = 12.dp)
                                            .testTag("scanner_apply_crop_button")
                                    ) {
                                        Icon(Icons.Default.Crop, contentDescription = null, tint = Color.White, modifier = Modifier.size(16.dp))
                                        Spacer(modifier = Modifier.width(6.dp))
                                        Text("Apply Margin Crop", color = Color.White, fontSize = 11.5.sp, fontWeight = FontWeight.Bold)
                                    }
                                }
                            }
                        } else {
                            CircularProgressIndicator(color = AdfElectricCyan)
                        }

                        // Floating Quick Action Controls Bar (Rotate L/R, Move L/R, Duplicate, Delete)
                        Surface(
                            shape = RoundedCornerShape(24.dp),
                            color = Color(0xCC0B1329),
                            border = BorderStroke(1.dp, AdfElectricCyan.copy(alpha = 0.4f)),
                            modifier = Modifier
                                .align(Alignment.BottomCenter)
                                .padding(bottom = 10.dp)
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(4.dp),
                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                            ) {
                                // Rotate Left (270 degrees clockwise / 90 counter-clockwise)
                                IconButton(
                                    onClick = {
                                        if (activePage != null && selectedPageIndex in scannedPages.indices) {
                                            val newRot = (activePage.rotationDegrees + 270) % 360
                                            scannedPages[selectedPageIndex] = activePage.copy(
                                                rotationDegrees = newRot,
                                                id = UUID.randomUUID().toString()
                                            )
                                        }
                                    },
                                    modifier = Modifier.size(36.dp).testTag("rotate_left_button")
                                ) {
                                    Icon(
                                        imageVector = Icons.AutoMirrored.Filled.RotateLeft,
                                        contentDescription = "Rotate Left",
                                        tint = AdfElectricCyan,
                                        modifier = Modifier.size(18.dp)
                                    )
                                }

                                // Rotate Right (90 degrees clockwise)
                                IconButton(
                                    onClick = {
                                        if (activePage != null && selectedPageIndex in scannedPages.indices) {
                                            val newRot = (activePage.rotationDegrees + 90) % 360
                                            scannedPages[selectedPageIndex] = activePage.copy(
                                                rotationDegrees = newRot,
                                                id = UUID.randomUUID().toString()
                                            )
                                        }
                                    },
                                    modifier = Modifier.size(36.dp).testTag("rotate_right_button")
                                ) {
                                    Icon(
                                        imageVector = Icons.AutoMirrored.Filled.RotateRight,
                                        contentDescription = "Rotate Right",
                                        tint = AdfElectricCyan,
                                        modifier = Modifier.size(18.dp)
                                    )
                                }

                                VerticalDivider(
                                    modifier = Modifier.height(20.dp),
                                    color = AdfBorderSubtle
                                )

                                // Move Left
                                IconButton(
                                    onClick = {
                                        if (selectedPageIndex > 0) {
                                            val current = scannedPages.removeAt(selectedPageIndex)
                                            scannedPages.add(selectedPageIndex - 1, current)
                                            selectedPageIndex -= 1
                                        }
                                    },
                                    enabled = selectedPageIndex > 0,
                                    modifier = Modifier.size(36.dp).testTag("move_left_button")
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.ChevronLeft,
                                        contentDescription = "Move Left",
                                        tint = if (selectedPageIndex > 0) AdfTextPrimary else AdfTextMuted,
                                        modifier = Modifier.size(20.dp)
                                    )
                                }

                                // Move Right
                                IconButton(
                                    onClick = {
                                        if (selectedPageIndex < scannedPages.size - 1) {
                                            val current = scannedPages.removeAt(selectedPageIndex)
                                            scannedPages.add(selectedPageIndex + 1, current)
                                            selectedPageIndex += 1
                                        }
                                    },
                                    enabled = selectedPageIndex < scannedPages.size - 1,
                                    modifier = Modifier.size(36.dp).testTag("move_right_button")
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.ChevronRight,
                                        contentDescription = "Move Right",
                                        tint = if (selectedPageIndex < scannedPages.size - 1) AdfTextPrimary else AdfTextMuted,
                                        modifier = Modifier.size(20.dp)
                                    )
                                }

                                VerticalDivider(
                                    modifier = Modifier.height(20.dp),
                                    color = AdfBorderSubtle
                                )

                                // Duplicate Page
                                IconButton(
                                    onClick = {
                                        if (activePage != null) {
                                            val dup = activePage.copy(id = UUID.randomUUID().toString())
                                            scannedPages.add(selectedPageIndex + 1, dup)
                                            selectedPageIndex += 1
                                            Toast.makeText(context, "Page duplicated", Toast.LENGTH_SHORT).show()
                                        }
                                    },
                                    modifier = Modifier.size(36.dp).testTag("duplicate_page_button")
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.ContentCopy,
                                        contentDescription = "Duplicate Page",
                                        tint = AdfElectricCyan,
                                        modifier = Modifier.size(16.dp)
                                    )
                                }

                                // Delete Active Page
                                IconButton(
                                    onClick = {
                                        if (scannedPages.isNotEmpty()) {
                                            scannedPages.removeAt(selectedPageIndex)
                                            if (scannedPages.isEmpty()) {
                                                isCaptureViewActive = true
                                            } else {
                                                selectedPageIndex = selectedPageIndex.coerceIn(0, scannedPages.size - 1)
                                            }
                                        }
                                    },
                                    modifier = Modifier.size(36.dp).testTag("scanner_delete_page_button")
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.Delete,
                                        contentDescription = "Delete Page",
                                        tint = AdfCoral,
                                        modifier = Modifier.size(18.dp)
                                    )
                                }
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(8.dp))

                    // ==============================================================
                    // INTERACTIVE FILTER SELECTION & FINE-TUNING BAR
                    // ==============================================================
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(12.dp))
                            .background(if (isLiquidTheme) GlassSurfaceDark else AdfSurfaceCard)
                            .border(1.dp, AdfBorderSubtle, RoundedCornerShape(12.dp))
                            .padding(8.dp)
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = "ENHANCEMENT FILTER",
                                style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold, letterSpacing = 1.sp),
                                color = AdfTextSecondary
                            )

                            Text(
                                text = "Apply to all (${scannedPages.size})",
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Bold,
                                color = AdfElectricCyan,
                                modifier = Modifier
                                    .clickable {
                                        if (activePage != null) {
                                            val currentF = activePage.filter
                                            val currentI = activePage.filterIntensity
                                            for (i in scannedPages.indices) {
                                                scannedPages[i] = scannedPages[i].copy(
                                                    filter = currentF,
                                                    filterIntensity = currentI,
                                                    id = UUID.randomUUID().toString()
                                                )
                                            }
                                            Toast.makeText(context, "Applied ${currentF.label} to all ${scannedPages.size} pages", Toast.LENGTH_SHORT).show()
                                        }
                                    }
                                    .padding(horizontal = 4.dp, vertical = 2.dp)
                            )
                        }

                        Spacer(modifier = Modifier.height(6.dp))

                        // Filter Cards Row with Live Thumbnail Previews
                        LazyRow(
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            itemsIndexed(ScanFilter.entries.toTypedArray()) { _, filterOption ->
                                val isSelected = activeFilter == filterOption
                                val thumbBmp = filterThumbnailsMap[filterOption]

                                Surface(
                                    onClick = {
                                        if (activePage != null && selectedPageIndex in scannedPages.indices) {
                                            scannedPages[selectedPageIndex] = activePage.copy(
                                                filter = filterOption,
                                                id = UUID.randomUUID().toString()
                                            )
                                        }
                                    },
                                    shape = RoundedCornerShape(10.dp),
                                    color = if (isSelected) AdfElectricCyan.copy(alpha = 0.18f) else Color(0x3B1E293B),
                                    border = BorderStroke(
                                        width = if (isSelected) 2.dp else 1.dp,
                                        brush = if (isSelected) Brush.horizontalGradient(listOf(AdfElectricCyan, AdfVibrantBlue))
                                        else Brush.horizontalGradient(listOf(AdfBorderSubtle, AdfBorderSubtle))
                                    ),
                                    modifier = Modifier
                                        .width(96.dp)
                                        .testTag("filter_chip_${filterOption.name}")
                                ) {
                                    Column(
                                        horizontalAlignment = Alignment.CenterHorizontally,
                                        modifier = Modifier.padding(6.dp)
                                    ) {
                                        // Miniature Filter Thumbnail Preview
                                        Box(
                                            modifier = Modifier
                                                .size(width = 84.dp, height = 50.dp)
                                                .clip(RoundedCornerShape(6.dp))
                                                .background(Color.Black),
                                            contentAlignment = Alignment.Center
                                        ) {
                                            if (thumbBmp != null) {
                                                Image(
                                                    bitmap = thumbBmp.asImageBitmap(),
                                                    contentDescription = filterOption.label,
                                                    contentScale = ContentScale.Crop,
                                                    modifier = Modifier.fillMaxSize()
                                                )
                                            } else {
                                                Text(
                                                    text = filterOption.label.take(2),
                                                    fontSize = 12.sp,
                                                    color = AdfTextMuted
                                                )
                                            }

                                            if (isSelected) {
                                                Box(
                                                    modifier = Modifier
                                                        .size(16.dp)
                                                        .align(Alignment.TopEnd)
                                                        .padding(2.dp)
                                                        .clip(CircleShape)
                                                        .background(AdfElectricCyan),
                                                    contentAlignment = Alignment.Center
                                                ) {
                                                    Icon(
                                                        imageVector = Icons.Default.Check,
                                                        contentDescription = null,
                                                        tint = AdfMidnightBackground,
                                                        modifier = Modifier.size(12.dp)
                                                    )
                                                }
                                            }
                                        }

                                        Spacer(modifier = Modifier.height(4.dp))

                                        Text(
                                            text = filterOption.label,
                                            fontSize = 11.sp,
                                            fontWeight = if (isSelected) FontWeight.ExtraBold else FontWeight.Medium,
                                            color = if (isSelected) AdfElectricCyan else AdfTextPrimary,
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis
                                        )
                                    }
                                }
                            }
                        }

                        // Fine-Tuning Intensity Slider (Visible when active filter is not ORIGINAL)
                        if (activeFilter != ScanFilter.ORIGINAL && activePage != null) {
                            Spacer(modifier = Modifier.height(6.dp))
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    text = "Contrast Level: ${(activeIntensity * 100).toInt()}%",
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = AdfElectricCyan,
                                    modifier = Modifier.width(115.dp)
                                )

                                Slider(
                                    value = activeIntensity,
                                    onValueChange = { newInt ->
                                        if (selectedPageIndex in scannedPages.indices) {
                                            scannedPages[selectedPageIndex] = activePage.copy(
                                                filterIntensity = newInt,
                                                id = UUID.randomUUID().toString()
                                            )
                                        }
                                    },
                                    valueRange = 0.4f..2.0f,
                                    colors = SliderDefaults.colors(
                                        thumbColor = AdfElectricCyan,
                                        activeTrackColor = AdfElectricCyan,
                                        inactiveTrackColor = AdfBorderSubtle
                                    ),
                                    modifier = Modifier
                                        .weight(1f)
                                        .height(28.dp)
                                        .testTag("filter_intensity_slider")
                                )
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(8.dp))

                    // ==============================================================
                    // MULTI-PAGE THUMBNAIL TRAY
                    // ==============================================================
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text(
                            text = "PAGE GALLERY (${scannedPages.size})",
                            style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold, letterSpacing = 1.sp),
                            color = AdfTextSecondary
                        )

                        Text(
                            text = "+ Add More",
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold,
                            color = AdfElectricCyan,
                            modifier = Modifier
                                .clickable { isCaptureViewActive = true }
                                .padding(horizontal = 4.dp, vertical = 2.dp)
                        )
                    }

                    Spacer(modifier = Modifier.height(4.dp))

                    LazyRow(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        itemsIndexed(scannedPages) { index, page ->
                            val isCurrent = index == selectedPageIndex
                            Surface(
                                onClick = { selectedPageIndex = index },
                                shape = RoundedCornerShape(8.dp),
                                color = if (isLiquidTheme) GlassSurfaceDark else AdfSurfaceCard,
                                border = BorderStroke(
                                    width = if (isCurrent) 2.dp else 1.dp,
                                    color = if (isCurrent) AdfElectricCyan else AdfBorderSubtle
                                ),
                                modifier = Modifier
                                    .size(width = 52.dp, height = 66.dp)
                                    .testTag("page_thumb_$index")
                            ) {
                                Box(
                                    contentAlignment = Alignment.Center,
                                    modifier = Modifier.fillMaxSize()
                                ) {
                                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                        Text(
                                            text = "${index + 1}",
                                            fontSize = 15.sp,
                                            fontWeight = FontWeight.ExtraBold,
                                            color = if (isCurrent) AdfElectricCyan else AdfTextSecondary
                                        )
                                        Text(
                                            text = page.filter.label.take(5),
                                            fontSize = 8.sp,
                                            color = AdfTextMuted,
                                            maxLines = 1
                                        )
                                    }
                                }
                            }
                        }

                        // Add Page Tile
                        item {
                            Surface(
                                onClick = { isCaptureViewActive = true },
                                shape = RoundedCornerShape(8.dp),
                                color = if (isLiquidTheme) GlassSurfaceDark else AdfObsidianSurface,
                                border = BorderStroke(1.dp, Brush.horizontalGradient(listOf(AdfElectricCyan, AdfVibrantBlue))),
                                modifier = Modifier
                                    .size(width = 52.dp, height = 66.dp)
                                    .testTag("add_page_thumbnail_button")
                            ) {
                                Column(
                                    horizontalAlignment = Alignment.CenterHorizontally,
                                    verticalArrangement = Arrangement.Center,
                                    modifier = Modifier.fillMaxSize()
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.Add,
                                        contentDescription = "Add Page",
                                        tint = AdfElectricCyan,
                                        modifier = Modifier.size(18.dp)
                                    )
                                    Text(
                                        text = "Add",
                                        fontSize = 9.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = AdfElectricCyan
                                    )
                                }
                            }
                        }
                    }

                    if (ocrResultsMap.isNotEmpty()) {
                        Spacer(modifier = Modifier.height(6.dp))
                        Surface(
                            shape = RoundedCornerShape(8.dp),
                            color = AdfElectricCyan.copy(alpha = 0.12f),
                            border = BorderStroke(1.dp, AdfElectricCyan.copy(alpha = 0.3f)),
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable { showOcrDialog = true }
                                .testTag("ocr_embedded_badge")
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.SpaceBetween,
                                modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp)
                            ) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Icon(
                                        imageVector = Icons.Default.Search,
                                        contentDescription = null,
                                        tint = AdfElectricCyan,
                                        modifier = Modifier.size(16.dp)
                                    )
                                    Spacer(modifier = Modifier.width(6.dp))
                                    Text(
                                        text = "Searchable Text Layer Embedded (${ocrResultsMap.size} ${if (ocrResultsMap.size == 1) "Page" else "Pages"})",
                                        fontSize = 11.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = AdfElectricCyan
                                    )
                                }
                                Text(
                                    text = "Review OCR",
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.ExtraBold,
                                    color = AdfTextPrimary
                                )
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(6.dp))

                    // Auto-OCR Status Badge
                    Surface(
                        shape = RoundedCornerShape(8.dp),
                        color = AdfElectricCyan.copy(alpha = 0.08f),
                        border = BorderStroke(1.dp, AdfElectricCyan.copy(alpha = 0.25f)),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.Center,
                            modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.Search,
                                contentDescription = null,
                                tint = AdfElectricCyan,
                                modifier = Modifier.size(14.dp)
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(
                                text = "Auto-OCR Active • Generates Searchable Dual-Layer PDF",
                                fontSize = 10.5.sp,
                                fontWeight = FontWeight.Bold,
                                color = AdfElectricCyan
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(6.dp))

                    // Final Action: Save as Searchable PDF
                    Button(
                        onClick = {
                            if (scannedPages.isEmpty()) {
                                Toast.makeText(context, "Please scan at least one page", Toast.LENGTH_SHORT).show()
                                return@Button
                            }
                            isProcessingPdf = true
                            coroutineScope.launch {
                                try {
                                    // Automatic OCR Text Extraction & Indexing
                                    val finalOcrMap = if (ocrResultsMap.isNotEmpty()) {
                                        ocrResultsMap
                                    } else {
                                        DocumentOcrEngine.processPagesOcr(context, scannedPages.toList())
                                    }

                                    val pdfFile = DocumentScanProcessor.generatePdf(
                                        context = context,
                                        pages = scannedPages.toList(),
                                        title = documentTitle,
                                        pageSizeA4 = true,
                                        ocrResults = finalOcrMap.ifEmpty { null },
                                        ocrTextOverrides = editedTextMap.takeIf { it.isNotEmpty() }
                                    )
                                    viewModel.registerScannedPdf(pdfFile, documentTitle) { savedEntity ->
                                        isProcessingPdf = false
                                        savedPdfEntity = savedEntity
                                    }
                                } catch (e: Exception) {
                                    e.printStackTrace()
                                    isProcessingPdf = false
                                    Toast.makeText(context, "Error saving PDF: ${e.message}", Toast.LENGTH_LONG).show()
                                }
                            }
                        },
                        colors = ButtonDefaults.buttonColors(containerColor = AdfElectricCyan),
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(48.dp)
                            .testTag("save_scanned_pdf_button")
                    ) {
                        if (isProcessingPdf) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(20.dp),
                                color = AdfMidnightBackground,
                                strokeWidth = 2.dp
                            )
                            Spacer(modifier = Modifier.width(10.dp))
                            Text("OCR Extracting & Compiling Searchable PDF...", color = AdfMidnightBackground, fontWeight = FontWeight.Bold, fontSize = 12.sp)
                        } else {
                            Icon(
                                imageVector = Icons.Default.PictureAsPdf,
                                contentDescription = null,
                                tint = AdfMidnightBackground
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = "Save as Searchable PDF (${scannedPages.size} ${if (scannedPages.size == 1) "Page" else "Pages"})",
                                color = AdfMidnightBackground,
                                fontWeight = FontWeight.ExtraBold,
                                fontSize = 13.5.sp
                            )
                        }
                    }
                }
            }
        }
    }

    // ==============================================================
    // PDF CREATION SUCCESS MODAL DIALOG
    // ==============================================================
    savedPdfEntity?.let { doc ->
        AlertDialog(
            onDismissRequest = { savedPdfEntity = null },
            containerColor = if (isLiquidTheme) GlassSurfaceDark else AdfSurfaceCard,
            shape = RoundedCornerShape(18.dp),
            title = {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(
                        modifier = Modifier
                            .size(36.dp)
                            .clip(RoundedCornerShape(8.dp))
                            .background(AdfCoral.copy(alpha = 0.15f)),
                        contentAlignment = Alignment.Center
                    ) {
                        AdfIcon.PdfDoc(size = 24.dp, primaryColor = AdfCoral, accentColor = Color.White)
                    }
                    Spacer(modifier = Modifier.width(10.dp))
                    Text(
                        text = "PDF Document Saved!",
                        color = AdfTextPrimary,
                        fontWeight = FontWeight.Bold,
                        fontSize = 17.sp
                    )
                }
            },
            text = {
                Column {
                    Text(
                        text = "Your document has been compiled into a high-resolution PDF and added to your PDF (AIO) library.",
                        color = AdfTextSecondary,
                        fontSize = 13.sp,
                        lineHeight = 18.sp
                    )
                    Spacer(modifier = Modifier.height(10.dp))
                    Surface(
                        shape = RoundedCornerShape(8.dp),
                        color = if (isLiquidTheme) GlassSurfaceDark else AdfObsidianSurface,
                        border = CardDefaults.outlinedCardBorder().copy(brush = Brush.horizontalGradient(listOf(AdfBorderSubtle, AdfBorderSubtle))),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(modifier = Modifier.padding(10.dp)) {
                            Text(
                                text = doc.title,
                                fontWeight = FontWeight.Bold,
                                color = AdfTextPrimary,
                                fontSize = 13.sp,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                            Spacer(modifier = Modifier.height(2.dp))
                            Text(
                                text = "${doc.pageCount} Pages • Saved to device storage",
                                color = AdfElectricCyan,
                                fontSize = 11.sp
                            )
                        }
                    }
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        savedPdfEntity = null
                        viewModel.openDocument(doc)
                        viewModel.navigateTo(AppScreen.READER)
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = AdfElectricCyan),
                    shape = RoundedCornerShape(10.dp),
                    modifier = Modifier.testTag("open_scanned_pdf_in_reader_button")
                ) {
                    Text("Open & Sign", color = AdfMidnightBackground, fontWeight = FontWeight.Bold)
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
                            context.startActivity(Intent.createChooser(intent, "Share Scanned PDF"))
                        },
                        shape = RoundedCornerShape(10.dp)
                    ) {
                        Text("Share", color = AdfElectricCyan)
                    }

                    OutlinedButton(
                        onClick = {
                            savedPdfEntity = null
                            scannedPages.clear()
                            isCaptureViewActive = true
                        },
                        shape = RoundedCornerShape(10.dp)
                    ) {
                        Text("Scan Another", color = AdfTextSecondary)
                    }
                }
            }
        )
    }

    // ==============================================================
    // OCR TEXT REVIEW & EDITOR MODAL DIALOG
    // ==============================================================
    if (showOcrDialog) {
        val clipboardManager = LocalClipboardManager.current
        var dialogSelectedPageIndex by remember { mutableIntStateOf(selectedPageIndex.coerceIn(0, (ocrResultsMap.size - 1).coerceAtLeast(0))) }

        AlertDialog(
            onDismissRequest = { showOcrDialog = false },
            containerColor = if (isLiquidTheme) GlassSurfaceDark else AdfSurfaceCard,
            shape = RoundedCornerShape(18.dp),
            title = {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Box(
                            modifier = Modifier
                                .size(36.dp)
                                .clip(RoundedCornerShape(8.dp))
                                .background(AdfElectricCyan.copy(alpha = 0.15f)),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(imageVector = Icons.Default.DocumentScanner, contentDescription = null, tint = AdfElectricCyan)
                        }
                        Spacer(modifier = Modifier.width(10.dp))
                        Column {
                            Text(
                                text = "OCR Text Extraction",
                                color = AdfTextPrimary,
                                fontWeight = FontWeight.ExtraBold,
                                fontSize = 16.sp
                            )
                            Text(
                                text = "Searchable & Editable Document Layer",
                                color = AdfTextSecondary,
                                fontSize = 11.sp
                            )
                        }
                    }
                }
            },
            text = {
                Column(modifier = Modifier.fillMaxWidth()) {
                    if (ocrResultsMap.size > 1) {
                        LazyRow(
                            horizontalArrangement = Arrangement.spacedBy(6.dp),
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(bottom = 10.dp)
                        ) {
                            items(ocrResultsMap.size) { pIdx ->
                                FilterChip(
                                    selected = dialogSelectedPageIndex == pIdx,
                                    onClick = { dialogSelectedPageIndex = pIdx },
                                    label = { Text("Page ${pIdx + 1}", fontSize = 11.sp) },
                                    colors = FilterChipDefaults.filterChipColors(
                                        selectedContainerColor = AdfElectricCyan.copy(alpha = 0.2f),
                                        selectedLabelColor = AdfElectricCyan
                                    )
                                )
                            }
                        }
                    }

                    val pageText = editedTextMap[dialogSelectedPageIndex] ?: ""

                    OutlinedTextField(
                        value = pageText,
                        onValueChange = { newTxt ->
                            val updated = editedTextMap.toMutableMap()
                            updated[dialogSelectedPageIndex] = newTxt
                            editedTextMap = updated
                        },
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(200.dp)
                            .testTag("ocr_text_editor_input"),
                        label = { Text("Extracted Text (Editable)", fontSize = 11.sp, color = AdfElectricCyan) },
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedTextColor = AdfTextPrimary,
                            unfocusedTextColor = AdfTextPrimary,
                            focusedBorderColor = AdfElectricCyan,
                            unfocusedBorderColor = AdfBorderSubtle,
                            focusedContainerColor = if (isLiquidTheme) GlassSurfaceDark else AdfObsidianSurface,
                            unfocusedContainerColor = if (isLiquidTheme) GlassSurfaceDark else AdfObsidianSurface
                        ),
                        shape = RoundedCornerShape(10.dp)
                    )

                    Spacer(modifier = Modifier.height(10.dp))

                    Row(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        // Copy to Clipboard Button
                        OutlinedButton(
                            onClick = {
                                val fullDocText = editedTextMap.entries.sortedBy { it.key }
                                    .joinToString("\n\n--- Page ---\n\n") { it.value }
                                clipboardManager.setText(AnnotatedString(fullDocText))
                                Toast.makeText(context, "Copied OCR text to clipboard", Toast.LENGTH_SHORT).show()
                            },
                            shape = RoundedCornerShape(8.dp),
                            modifier = Modifier.weight(1f).testTag("ocr_copy_clipboard_button")
                        ) {
                            Icon(imageVector = Icons.Default.ContentCopy, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(4.dp))
                            Text("Copy Text", fontSize = 11.sp)
                        }

                        // Export .txt file
                        OutlinedButton(
                            onClick = {
                                coroutineScope.launch {
                                    try {
                                        val file = DocumentOcrEngine.exportOcrTextToFile(context, ocrResultsMap, documentTitle)
                                        val uri = FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", file)
                                        val intent = Intent(Intent.ACTION_SEND).apply {
                                            type = "text/plain"
                                            putExtra(Intent.EXTRA_STREAM, uri)
                                            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                                        }
                                        context.startActivity(Intent.createChooser(intent, "Share OCR Text File"))
                                    } catch (e: Exception) {
                                        Toast.makeText(context, "Export Error: ${e.message}", Toast.LENGTH_SHORT).show()
                                    }
                                }
                            },
                            shape = RoundedCornerShape(8.dp),
                            modifier = Modifier.weight(1f).testTag("ocr_export_txt_button")
                        ) {
                            Icon(imageVector = Icons.Default.FileDownload, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(4.dp))
                            Text("Export .txt", fontSize = 11.sp)
                        }
                    }
                }
            },
            confirmButton = {
                Button(
                    onClick = { showOcrDialog = false },
                    colors = ButtonDefaults.buttonColors(containerColor = AdfElectricCyan),
                    shape = RoundedCornerShape(10.dp),
                    modifier = Modifier.testTag("ocr_dialog_confirm_button")
                ) {
                    Text("Embed as Dual-Layer PDF", color = AdfMidnightBackground, fontWeight = FontWeight.Bold)
                }
            }
        )
    }
}

@Composable
fun BadgeBox(count: Int, onClick: () -> Unit) {
    Surface(
        onClick = onClick,
        shape = CircleShape,
        color = AdfElectricCyan,
        modifier = Modifier
            .size(52.dp)
            .testTag("scanner_review_pages_badge")
    ) {
        Box(contentAlignment = Alignment.Center) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text(
                    text = "$count",
                    fontSize = 15.sp,
                    fontWeight = FontWeight.ExtraBold,
                    color = AdfMidnightBackground
                )
                Text(
                    text = "VIEW",
                    fontSize = 8.sp,
                    fontWeight = FontWeight.Bold,
                    color = AdfMidnightBackground
                )
            }
        }
    }
}


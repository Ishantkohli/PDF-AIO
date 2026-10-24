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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.FileProvider
import com.example.data.local.DocumentEntity
import com.example.data.scan.DocumentScanProcessor
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

    val scannedPages = remember { mutableStateListOf<ScannedPage>() }
    var selectedPageIndex by remember { mutableIntStateOf(0) }
    var isCaptureViewActive by remember { mutableStateOf(true) }
    var isFlashOn by remember { mutableStateOf(false) }
    var documentTitle by remember {
        mutableStateOf("Scan_${SimpleDateFormat("dd_MMM_yyyy", Locale.getDefault()).format(Date())}")
    }

    var isProcessingPdf by remember { mutableStateOf(false) }
    var savedPdfEntity by remember { mutableStateOf<DocumentEntity?>(null) }
    var previewBitmap by remember { mutableStateOf<Bitmap?>(null) }

    var tempCameraImageUri by remember { mutableStateOf<Uri?>(null) }

    // Creates a temporary file in cache for full-resolution camera captures
    fun createTempImageUri(): Uri {
        val tempFile = File.createTempFile(
            "scan_capture_${System.currentTimeMillis()}",
            ".jpg",
            context.cacheDir
        ).apply { createNewFile() }
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
                filter = ScanFilter.MAGIC_COLOR
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
                        filter = ScanFilter.MAGIC_COLOR
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
            c.drawText("PDF AIO Adobe Scan Engine • Document Verified", 80f, 210f, p)
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
                    "[Digital Stamp Verified • 100% On-Device ADF Protocol]"
                )
            } else {
                listOf(
                    "INVOICE #: INV-2026-08912          DATE: Oct 21, 2026",
                    "BILLED TO: Enterprise Global Corp.",
                    "",
                    "ITEM DESCRIPTION                     QTY    RATE      AMOUNT",
                    "-------------------------------------------------------------",
                    "PDF AIO Pro Document Suite License    1     $199.00   $199.00",
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

            val page = ScannedPage(
                sourceUri = Uri.fromFile(sampleFile),
                filter = ScanFilter.MAGIC_COLOR
            )

            withContext(Dispatchers.Main) {
                scannedPages.add(page)
                selectedPageIndex = scannedPages.size - 1
                isCaptureViewActive = false
            }
        }
    }

    // Refresh active preview bitmap whenever selected page or its filter/rotation changes
    LaunchedEffect(scannedPages.toList(), selectedPageIndex, isCaptureViewActive) {
        if (!isCaptureViewActive && scannedPages.isNotEmpty()) {
            val activePage = scannedPages.getOrNull(selectedPageIndex) ?: return@LaunchedEffect
            withContext(Dispatchers.IO) {
                val raw = DocumentScanProcessor.loadBitmapFromUri(context, activePage.sourceUri)
                if (raw != null) {
                    val processed = DocumentScanProcessor.processBitmap(
                        source = raw,
                        filter = activePage.filter,
                        rotationDegrees = activePage.rotationDegrees
                    )
                    withContext(Dispatchers.Main) {
                        previewBitmap = processed
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
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Text(
                                    text = "PDF AIO SCANNER",
                                    style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.ExtraBold),
                                    color = AdfTextPrimary
                                )
                                Spacer(modifier = Modifier.width(6.dp))
                                Surface(
                                    shape = RoundedCornerShape(4.dp),
                                    color = AdfCoral.copy(alpha = 0.15f)
                                ) {
                                    Text(
                                        text = "ADOBE SCAN ENGINE",
                                        color = AdfCoral,
                                        fontSize = 8.sp,
                                        fontWeight = FontWeight.ExtraBold,
                                        modifier = Modifier.padding(horizontal = 4.dp, vertical = 1.dp)
                                    )
                                }
                            }
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
                            Toast.makeText(context, if (isFlashOn) "Torch Active" else "Torch Off", Toast.LENGTH_SHORT).show()
                        },
                        modifier = Modifier.testTag("scanner_flash_toggle")
                    ) {
                        Icon(
                            imageVector = if (isFlashOn) Icons.Default.FlashOn else Icons.Default.FlashOff,
                            contentDescription = "Toggle Flash",
                            tint = if (isFlashOn) AdfAmber else AdfTextMuted
                        )
                    }

                    // Add Page / Retake icon if reviewing
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
                colors = TopAppBarDefaults.topAppBarColors(containerColor = AdfMidnightBackground)
            )
        },
        containerColor = AdfMidnightBackground
    ) { padding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
        ) {
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
                        color = AdfObsidianSurface,
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
                                text = "Auto-detecting edges • Align document in frame",
                                color = AdfTextPrimary,
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Medium
                            )
                        }
                    }

                    // Central Viewfinder Reticle Frame (A4 document shape)
                    Box(
                        modifier = Modifier
                            .fillMaxWidth(0.90f)
                            .weight(1f)
                            .padding(vertical = 16.dp)
                            .clip(RoundedCornerShape(16.dp))
                            .background(Color(0xFF0F172A))
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
                            AdfIcon.Camera(size = 54.dp, primaryColor = AdfElectricCyan, accentColor = AdfCoral)
                            Spacer(modifier = Modifier.height(14.dp))
                            Text(
                                text = "READY TO SCAN",
                                style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.ExtraBold, letterSpacing = 1.sp),
                                color = AdfTextPrimary
                            )
                            Spacer(modifier = Modifier.height(6.dp))
                            Text(
                                text = "Tap the round Shutter button below to snap your document, or select existing pages from your photo gallery.",
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
                            .padding(horizontal = 20.dp, vertical = 12.dp),
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
                                .size(50.dp)
                                .clip(CircleShape)
                                .background(AdfSurfaceCard)
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
                                .size(80.dp)
                                .clip(CircleShape)
                                .background(AdfElectricCyan.copy(alpha = 0.2f))
                                .border(2.dp, AdfElectricCyan, CircleShape)
                                .clickable { launchCameraCapture() }
                                .testTag("scanner_shutter_button"),
                            contentAlignment = Alignment.Center
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(62.dp)
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
                            Spacer(modifier = Modifier.size(50.dp))
                        }
                    }
                }
            } else {
                // ==============================================================
                // VIEW 2: ADOBE SCAN DOCUMENT REVIEW & ENHANCEMENT STUDIO
                // ==============================================================
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(horizontal = 16.dp, vertical = 8.dp)
                ) {
                    // Title Rename & Page Indicator Header
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(bottom = 10.dp),
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
                                unfocusedBorderColor = AdfBorderSubtle
                            ),
                            shape = RoundedCornerShape(10.dp),
                            modifier = Modifier
                                .weight(1f)
                                .testTag("scanned_pdf_title_input")
                        )

                        Spacer(modifier = Modifier.width(10.dp))

                        // Rotate 90° Clockwise Button
                        IconButton(
                            onClick = {
                                val current = scannedPages.getOrNull(selectedPageIndex)
                                if (current != null) {
                                    current.rotationDegrees = (current.rotationDegrees + 90) % 360
                                    // Trigger re-render
                                    scannedPages[selectedPageIndex] = current.copy()
                                }
                            },
                            modifier = Modifier
                                .size(46.dp)
                                .clip(RoundedCornerShape(10.dp))
                                .background(AdfSurfaceCard)
                                .border(1.dp, AdfBorderSubtle, RoundedCornerShape(10.dp))
                                .testTag("scanner_rotate_page_button")
                        ) {
                            Icon(
                                imageVector = Icons.AutoMirrored.Filled.RotateRight,
                                contentDescription = "Rotate 90 Degrees",
                                tint = AdfElectricCyan
                            )
                        }

                        Spacer(modifier = Modifier.width(8.dp))

                        // Delete Active Page Button
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
                            modifier = Modifier
                                .size(46.dp)
                                .clip(RoundedCornerShape(10.dp))
                                .background(AdfCoral.copy(alpha = 0.12f))
                                .border(1.dp, AdfCoral.copy(alpha = 0.4f), RoundedCornerShape(10.dp))
                                .testTag("scanner_delete_page_button")
                        ) {
                            Icon(
                                imageVector = Icons.Default.Delete,
                                contentDescription = "Delete Page",
                                tint = AdfCoral
                            )
                        }
                    }

                    // Main Document Page Preview Canvas
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .weight(1f)
                            .clip(RoundedCornerShape(14.dp))
                            .background(AdfObsidianSurface)
                            .border(1.dp, AdfBorderSubtle, RoundedCornerShape(14.dp))
                            .padding(10.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        val bmp = previewBitmap
                        if (bmp != null) {
                            Image(
                                bitmap = bmp.asImageBitmap(),
                                contentDescription = "Scanned Page Preview",
                                modifier = Modifier
                                    .fillMaxSize()
                                    .shadow(elevation = 6.dp, shape = RoundedCornerShape(4.dp)),
                                contentScale = ContentScale.Fit
                            )
                        } else {
                            CircularProgressIndicator(color = AdfElectricCyan)
                        }
                    }

                    Spacer(modifier = Modifier.height(10.dp))

                    // Adobe Scan Enhancement Filter Selector Row
                    Text(
                        text = "DOCUMENT ENHANCEMENT FILTER",
                        style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold, letterSpacing = 1.sp),
                        color = AdfTextSecondary
                    )
                    Spacer(modifier = Modifier.height(6.dp))

                    LazyRow(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        val activePage = scannedPages.getOrNull(selectedPageIndex)
                        items(ScanFilter.values().size) { idx ->
                            val filter = ScanFilter.values()[idx]
                            val isSelected = activePage?.filter == filter
                            Surface(
                                onClick = {
                                    if (activePage != null) {
                                        activePage.filter = filter
                                        scannedPages[selectedPageIndex] = activePage.copy()
                                    }
                                },
                                shape = RoundedCornerShape(10.dp),
                                color = if (isSelected) AdfElectricCyan.copy(alpha = 0.18f) else AdfSurfaceCard,
                                border = CardDefaults.outlinedCardBorder().copy(
                                    brush = if (isSelected) Brush.horizontalGradient(listOf(AdfElectricCyan, AdfVibrantBlue))
                                    else Brush.horizontalGradient(listOf(AdfBorderSubtle, AdfBorderSubtle))
                                ),
                                modifier = Modifier.testTag("filter_chip_${filter.name}")
                            ) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp)
                                ) {
                                    Box(
                                        modifier = Modifier
                                            .size(8.dp)
                                            .clip(CircleShape)
                                            .background(if (isSelected) AdfElectricCyan else AdfTextMuted)
                                    )
                                    Spacer(modifier = Modifier.width(6.dp))
                                    Column {
                                        Text(
                                            text = filter.label,
                                            fontSize = 11.5.sp,
                                            fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                                            color = if (isSelected) AdfElectricCyan else AdfTextPrimary
                                        )
                                    }
                                }
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(12.dp))

                    // Multi-page Thumbnail Strip
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text(
                            text = "PAGES (${scannedPages.size})",
                            style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold, letterSpacing = 1.sp),
                            color = AdfTextSecondary
                        )

                        Text(
                            text = "Apply filter to all pages",
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold,
                            color = AdfElectricCyan,
                            modifier = Modifier
                                .clickable {
                                    val currentFilter = scannedPages.getOrNull(selectedPageIndex)?.filter ?: ScanFilter.MAGIC_COLOR
                                    for (i in scannedPages.indices) {
                                        scannedPages[i].filter = currentFilter
                                    }
                                    Toast.makeText(context, "Applied ${currentFilter.label} to all ${scannedPages.size} pages", Toast.LENGTH_SHORT).show()
                                }
                                .padding(4.dp)
                        )
                    }

                    Spacer(modifier = Modifier.height(6.dp))

                    LazyRow(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        itemsIndexed(scannedPages) { index, page ->
                            val isCurrent = index == selectedPageIndex
                            Surface(
                                onClick = { selectedPageIndex = index },
                                shape = RoundedCornerShape(8.dp),
                                color = AdfSurfaceCard,
                                border = BorderStroke(
                                    width = if (isCurrent) 2.dp else 1.dp,
                                    color = if (isCurrent) AdfElectricCyan else AdfBorderSubtle
                                ),
                                modifier = Modifier
                                    .size(width = 54.dp, height = 70.dp)
                                    .testTag("page_thumb_$index")
                            ) {
                                Box(
                                    contentAlignment = Alignment.Center,
                                    modifier = Modifier.fillMaxSize()
                                ) {
                                    Text(
                                        text = "${index + 1}",
                                        fontSize = 14.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = if (isCurrent) AdfElectricCyan else AdfTextSecondary
                                    )
                                }
                            }
                        }

                        // Add More Pages Button
                        item {
                            Surface(
                                onClick = { isCaptureViewActive = true },
                                shape = RoundedCornerShape(8.dp),
                                color = AdfObsidianSurface,
                                border = BorderStroke(1.dp, Brush.horizontalGradient(listOf(AdfElectricCyan, AdfVibrantBlue))),
                                modifier = Modifier
                                    .size(width = 54.dp, height = 70.dp)
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
                                        modifier = Modifier.size(20.dp)
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

                    Spacer(modifier = Modifier.height(14.dp))

                    // Final Action: Save as PDF
                    Button(
                        onClick = {
                            if (scannedPages.isEmpty()) {
                                Toast.makeText(context, "Please scan at least one page", Toast.LENGTH_SHORT).show()
                                return@Button
                            }
                            isProcessingPdf = true
                            coroutineScope.launch {
                                try {
                                    val pdfFile = DocumentScanProcessor.generatePdf(
                                        context = context,
                                        pages = scannedPages.toList(),
                                        title = documentTitle,
                                        pageSizeA4 = true
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
                            .height(50.dp)
                            .testTag("save_scanned_pdf_button")
                    ) {
                        if (isProcessingPdf) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(22.dp),
                                color = AdfMidnightBackground,
                                strokeWidth = 2.dp
                            )
                            Spacer(modifier = Modifier.width(10.dp))
                            Text("Generating High-Res PDF...", color = AdfMidnightBackground, fontWeight = FontWeight.Bold)
                        } else {
                            Icon(
                                imageVector = Icons.Default.PictureAsPdf,
                                contentDescription = null,
                                tint = AdfMidnightBackground
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = "Save as PDF (${scannedPages.size} ${if (scannedPages.size == 1) "Page" else "Pages"})",
                                color = AdfMidnightBackground,
                                fontWeight = FontWeight.ExtraBold,
                                fontSize = 14.sp
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
            containerColor = AdfSurfaceCard,
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
                        text = "Your document has been compiled into a high-resolution PDF and added to your PDF AIO library.",
                        color = AdfTextSecondary,
                        fontSize = 13.sp,
                        lineHeight = 18.sp
                    )
                    Spacer(modifier = Modifier.height(10.dp))
                    Surface(
                        shape = RoundedCornerShape(8.dp),
                        color = AdfObsidianSurface,
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
}

@Composable
fun BadgeBox(count: Int, onClick: () -> Unit) {
    Surface(
        onClick = onClick,
        shape = CircleShape,
        color = AdfElectricCyan,
        modifier = Modifier
            .size(50.dp)
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

package com.example.ui.screens.tools

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.text.format.Formatter
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.*
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.FileProvider
import com.example.data.local.DocumentEntity
import com.example.data.pdf.PdfCompressorEngine
import com.example.ui.PdfViewModel
import com.example.ui.theme.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PdfCompressorTab(
    viewModel: PdfViewModel,
    documents: List<DocumentEntity>,
    currentDoc: DocumentEntity? = null
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    var selectedFile by remember { mutableStateOf<File?>(null) }
    var selectedFileUri by remember { mutableStateOf<Uri?>(null) }
    var selectedFileName by remember { mutableStateOf("No file selected") }
    var selectedFileSize by remember { mutableStateOf(0L) }

    var selectedPreset by remember { mutableStateOf(PdfCompressorEngine.CompressionPreset.RECOMMENDED) }
    var customQuality by remember { mutableFloatStateOf(70f) }
    var customScale by remember { mutableFloatStateOf(0.85f) }
    var stripMetadata by remember { mutableStateOf(true) }
    var stripAnnotations by remember { mutableStateOf(false) }

    var isCompressing by remember { mutableStateOf(false) }
    var progressCurrentPage by remember { mutableIntStateOf(0) }
    var progressTotalPages by remember { mutableIntStateOf(0) }
    var compressionResult by remember { mutableStateOf<PdfCompressorEngine.CompressionResult?>(null) }

    // Initialize with current document if available
    LaunchedEffect(currentDoc) {
        if (currentDoc != null && selectedFile == null) {
            val file = File(currentDoc.filePath)
            if (file.exists()) {
                selectedFile = file
                selectedFileName = file.name
                selectedFileSize = file.length()
            }
        }
    }

    // Document Picker for choosing PDF from storage
    val pdfPickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument()
    ) { uri ->
        uri?.let {
            scope.launch(Dispatchers.IO) {
                try {
                    val tempFile = File(context.cacheDir, "compress_source_${System.currentTimeMillis()}.pdf")
                    context.contentResolver.openInputStream(uri)?.use { input ->
                        tempFile.outputStream().use { output -> input.copyTo(output) }
                    }
                    withContext(Dispatchers.Main) {
                        selectedFile = tempFile
                        selectedFileUri = uri
                        selectedFileName = getFileNameFromUri(context, uri) ?: tempFile.name
                        selectedFileSize = tempFile.length()
                        compressionResult = null
                    }
                } catch (e: Exception) {
                    withContext(Dispatchers.Main) {
                        Toast.makeText(context, "Could not load selected PDF", Toast.LENGTH_SHORT).show()
                    }
                }
            }
        }
    }

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        // Header Banner Card
        item {
            Card(
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(containerColor = AdfSurfaceCard),
                modifier = Modifier
                    .fillMaxWidth()
                    .border(width = 0.5.dp, color = AdfBorderSubtle, shape = RoundedCornerShape(16.dp))
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Box(
                        contentAlignment = Alignment.Center,
                        modifier = Modifier
                            .size(44.dp)
                            .clip(CircleShape)
                            .background(AdfElectricCyan.copy(alpha = 0.15f))
                    ) {
                        Icon(
                            imageVector = Icons.Default.Compress,
                            contentDescription = "Compress PDF",
                            tint = AdfElectricCyan,
                            modifier = Modifier.size(24.dp)
                        )
                    }
                    Spacer(modifier = Modifier.width(14.dp))
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = "PDF File Compressor",
                            color = Color.White,
                            fontSize = 16.sp,
                            fontWeight = FontWeight.Bold
                        )
                        Text(
                            text = "Shrink file sizes by optimizing images & stripping metadata",
                            color = AdfTextMuted,
                            fontSize = 11.sp
                        )
                    }
                }
            }
        }

        // File Selection Section
        item {
            Card(
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(containerColor = Color(0xFF1E1E2E)),
                modifier = Modifier
                    .fillMaxWidth()
                    .border(width = 0.5.dp, color = AdfBorderSubtle, shape = RoundedCornerShape(16.dp))
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp)
                ) {
                    Text(
                        text = "1. Select PDF Document",
                        color = Color.White,
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Bold
                    )
                    Spacer(modifier = Modifier.height(10.dp))

                    if (selectedFile != null) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier
                                .fillMaxWidth()
                                .background(AdfSurfaceCard, RoundedCornerShape(12.dp))
                                .border(0.5.dp, AdfElectricCyan.copy(alpha = 0.3f), RoundedCornerShape(12.dp))
                                .padding(12.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.PictureAsPdf,
                                contentDescription = "PDF",
                                tint = AdfCoral,
                                modifier = Modifier.size(28.dp)
                            )
                            Spacer(modifier = Modifier.width(12.dp))
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = selectedFileName,
                                    color = Color.White,
                                    fontSize = 13.sp,
                                    fontWeight = FontWeight.SemiBold,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                                Text(
                                    text = "Original Size: ${Formatter.formatShortFileSize(context, selectedFileSize)}",
                                    color = AdfElectricCyan,
                                    fontSize = 11.sp
                                )
                            }
                            IconButton(
                                onClick = { pdfPickerLauncher.launch(arrayOf("application/pdf")) }
                            ) {
                                Icon(Icons.Default.FolderOpen, contentDescription = "Change PDF", tint = AdfElectricCyan)
                            }
                        }
                    } else {
                        Button(
                            onClick = { pdfPickerLauncher.launch(arrayOf("application/pdf")) },
                            colors = ButtonDefaults.buttonColors(containerColor = AdfElectricCyan),
                            shape = RoundedCornerShape(12.dp),
                            modifier = Modifier.fillMaxWidth().testTag("select_pdf_compress_btn")
                        ) {
                            Icon(Icons.Default.UploadFile, contentDescription = null, tint = AdfMidnightBackground)
                            Spacer(modifier = Modifier.width(8.dp))
                            Text("Choose PDF to Compress", color = AdfMidnightBackground, fontWeight = FontWeight.Bold)
                        }
                    }
                }
            }
        }

        // Compression Presets Selector
        item {
            Card(
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(containerColor = Color(0xFF1E1E2E)),
                modifier = Modifier
                    .fillMaxWidth()
                    .border(width = 0.5.dp, color = AdfBorderSubtle, shape = RoundedCornerShape(16.dp))
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp)
                ) {
                    Text(
                        text = "2. Select Compression Quality Profile",
                        color = Color.White,
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Bold
                    )
                    Spacer(modifier = Modifier.height(12.dp))

                    PdfCompressorEngine.CompressionPreset.values().forEach { preset ->
                        val isSelected = selectedPreset == preset
                        Card(
                            shape = RoundedCornerShape(12.dp),
                            colors = CardDefaults.cardColors(
                                containerColor = if (isSelected) AdfElectricCyan.copy(alpha = 0.15f) else AdfSurfaceCard
                            ),
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 4.dp)
                                .border(
                                    width = if (isSelected) 1.5.dp else 0.5.dp,
                                    color = if (isSelected) AdfElectricCyan else AdfBorderSubtle,
                                    shape = RoundedCornerShape(12.dp)
                                )
                                .clickable { selectedPreset = preset }
                                .testTag("preset_${preset.name.lowercase()}")
                        ) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(12.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Column(modifier = Modifier.weight(1f)) {
                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        Text(
                                            text = preset.label,
                                            color = Color.White,
                                            fontSize = 13.5.sp,
                                            fontWeight = FontWeight.Bold
                                        )
                                        Spacer(modifier = Modifier.width(8.dp))
                                        Surface(
                                            shape = RoundedCornerShape(4.dp),
                                            color = AdfCoral.copy(alpha = 0.2f)
                                        ) {
                                            Text(
                                                text = preset.estimatedReduction,
                                                color = AdfCoral,
                                                fontSize = 10.sp,
                                                fontWeight = FontWeight.Bold,
                                                modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                                            )
                                        }
                                    }
                                    Text(
                                        text = preset.subtitle,
                                        color = AdfTextMuted,
                                        fontSize = 11.sp
                                    )
                                }
                                RadioButton(
                                    selected = isSelected,
                                    onClick = { selectedPreset = preset },
                                    colors = RadioButtonDefaults.colors(selectedColor = AdfElectricCyan)
                                )
                            }
                        }
                    }

                    // Custom Controls Expansion
                    if (selectedPreset == PdfCompressorEngine.CompressionPreset.CUSTOM) {
                        Spacer(modifier = Modifier.height(12.dp))
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .background(AdfSurfaceCard, RoundedCornerShape(12.dp))
                                .padding(12.dp)
                        ) {
                            Text(
                                text = "Custom Image Quality: ${customQuality.toInt()}%",
                                color = AdfElectricCyan,
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Bold
                            )
                            Slider(
                                value = customQuality,
                                onValueChange = { customQuality = it },
                                valueRange = 15f..95f,
                                colors = SliderDefaults.colors(thumbColor = AdfElectricCyan, activeTrackColor = AdfElectricCyan)
                            )

                            Spacer(modifier = Modifier.height(8.dp))
                            Text(
                                text = "Custom Resolution Scale: ${String.format("%.2f", customScale)}x",
                                color = AdfElectricCyan,
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Bold
                            )
                            Slider(
                                value = customScale,
                                onValueChange = { customScale = it },
                                valueRange = 0.5f..1.2f,
                                colors = SliderDefaults.colors(thumbColor = AdfElectricCyan, activeTrackColor = AdfElectricCyan)
                            )
                        }
                    }
                }
            }
        }

        // Options Toggles
        item {
            Card(
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(containerColor = Color(0xFF1E1E2E)),
                modifier = Modifier
                    .fillMaxWidth()
                    .border(width = 0.5.dp, color = AdfBorderSubtle, shape = RoundedCornerShape(16.dp))
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp)
                ) {
                    Text(
                        text = "3. Optimization Settings",
                        color = Color.White,
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Bold
                    )
                    Spacer(modifier = Modifier.height(8.dp))

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text("Strip Redundant Metadata", color = Color.White, fontSize = 12.5.sp, fontWeight = FontWeight.SemiBold)
                            Text("Remove author, producer, XML tags & header comments", color = AdfTextMuted, fontSize = 10.5.sp)
                        }
                        Switch(
                            checked = stripMetadata,
                            onCheckedChange = { stripMetadata = it },
                            colors = SwitchDefaults.colors(checkedThumbColor = AdfElectricCyan)
                        )
                    }

                    HorizontalDivider(color = AdfBorderSubtle, modifier = Modifier.padding(vertical = 8.dp))

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text("Optimize Page Bitmaps", color = Color.White, fontSize = 12.5.sp, fontWeight = FontWeight.SemiBold)
                            Text("Re-sample embedded page graphics to efficient JPEG stream", color = AdfTextMuted, fontSize = 10.5.sp)
                        }
                        Switch(
                            checked = true,
                            onCheckedChange = {},
                            enabled = false,
                            colors = SwitchDefaults.colors(checkedThumbColor = AdfElectricCyan)
                        )
                    }
                }
            }
        }

        // Action & Progress Section
        item {
            Column(modifier = Modifier.fillMaxWidth()) {
                if (isCompressing) {
                    Card(
                        shape = RoundedCornerShape(16.dp),
                        colors = CardDefaults.cardColors(containerColor = Color(0xFF1E1E2E)),
                        modifier = Modifier
                            .fillMaxWidth()
                            .border(1.dp, AdfElectricCyan, RoundedCornerShape(16.dp))
                            .padding(16.dp)
                    ) {
                        Column(
                            horizontalAlignment = Alignment.CenterHorizontally,
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text(
                                text = "Compressing PDF Document...",
                                color = Color.White,
                                fontSize = 14.sp,
                                fontWeight = FontWeight.Bold
                            )
                            Spacer(modifier = Modifier.height(8.dp))
                            if (progressTotalPages > 0) {
                                LinearProgressIndicator(
                                    progress = { progressCurrentPage.toFloat() / progressTotalPages.toFloat() },
                                    modifier = Modifier.fillMaxWidth().height(8.dp).clip(RoundedCornerShape(4.dp)),
                                    color = AdfElectricCyan,
                                    trackColor = AdfSurfaceCard
                                )
                                Spacer(modifier = Modifier.height(6.dp))
                                Text(
                                    text = "Processing page $progressCurrentPage of $progressTotalPages",
                                    color = AdfElectricCyan,
                                    fontSize = 11.sp
                                )
                            } else {
                                CircularProgressIndicator(color = AdfElectricCyan, modifier = Modifier.size(24.dp))
                            }
                        }
                    }
                } else {
                    Button(
                        onClick = {
                            val file = selectedFile
                            if (file == null) {
                                Toast.makeText(context, "Please select a PDF file first", Toast.LENGTH_SHORT).show()
                                return@Button
                            }

                            isCompressing = true
                            progressCurrentPage = 0
                            progressTotalPages = 0

                            scope.launch(Dispatchers.IO) {
                                val options = PdfCompressorEngine.CompressionOptions(
                                    preset = selectedPreset,
                                    customJpegQuality = customQuality.toInt(),
                                    customScaleFactor = customScale,
                                    stripMetadata = stripMetadata,
                                    stripAnnotations = stripAnnotations
                                )

                                val result = PdfCompressorEngine.compressPdf(
                                    context = context,
                                    inputFile = file,
                                    options = options,
                                    onProgress = { current, total ->
                                        progressCurrentPage = current
                                        progressTotalPages = total
                                    }
                                )

                                withContext(Dispatchers.Main) {
                                    isCompressing = false
                                    compressionResult = result
                                    if (result.isSuccess && result.outputFile != null) {
                                        viewModel.importPdfFromUri(Uri.fromFile(result.outputFile))
                                        Toast.makeText(context, "PDF Compressed Successfully!", Toast.LENGTH_SHORT).show()
                                    } else {
                                        Toast.makeText(context, result.errorMessage ?: "Compression failed", Toast.LENGTH_LONG).show()
                                    }
                                }
                            }
                        },
                        colors = ButtonDefaults.buttonColors(containerColor = AdfElectricCyan),
                        shape = RoundedCornerShape(14.dp),
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(52.dp)
                            .testTag("compress_pdf_action_button")
                    ) {
                        Icon(Icons.Default.Compress, contentDescription = null, tint = AdfMidnightBackground)
                        Spacer(modifier = Modifier.width(10.dp))
                        Text(
                            text = "Compress PDF Document",
                            color = AdfMidnightBackground,
                            fontSize = 15.sp,
                            fontWeight = FontWeight.ExtraBold
                        )
                    }
                }
            }
        }

        // Result Card
        compressionResult?.let { result ->
            if (result.isSuccess && result.outputFile != null) {
                item {
                    Card(
                        shape = RoundedCornerShape(16.dp),
                        colors = CardDefaults.cardColors(containerColor = Color(0xFF132A24)),
                        modifier = Modifier
                            .fillMaxWidth()
                            .border(1.dp, AdfEmerald, RoundedCornerShape(16.dp))
                    ) {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(16.dp)
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(Icons.Default.CheckCircle, contentDescription = "Success", tint = AdfEmerald, modifier = Modifier.size(24.dp))
                                Spacer(modifier = Modifier.width(8.dp))
                                Text(
                                    text = "Compression Complete!",
                                    color = Color.White,
                                    fontSize = 15.sp,
                                    fontWeight = FontWeight.Bold
                                )
                            }

                            Spacer(modifier = Modifier.height(12.dp))

                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Column {
                                    Text("Original Size", color = AdfTextMuted, fontSize = 11.sp)
                                    Text(
                                        Formatter.formatShortFileSize(context, result.originalSizeBytes),
                                        color = Color.White,
                                        fontSize = 14.sp,
                                        fontWeight = FontWeight.Bold
                                    )
                                }

                                Icon(Icons.Default.ArrowForward, contentDescription = null, tint = AdfEmerald)

                                Column {
                                    Text("Compressed Size", color = AdfTextMuted, fontSize = 11.sp)
                                    Text(
                                        Formatter.formatShortFileSize(context, result.compressedSizeBytes),
                                        color = AdfEmerald,
                                        fontSize = 14.sp,
                                        fontWeight = FontWeight.Bold
                                    )
                                }

                                Surface(
                                    shape = RoundedCornerShape(8.dp),
                                    color = AdfEmerald.copy(alpha = 0.2f)
                                ) {
                                    Text(
                                        text = "-${String.format("%.1f", result.reductionPercent)}%",
                                        color = AdfEmerald,
                                        fontSize = 14.sp,
                                        fontWeight = FontWeight.ExtraBold,
                                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp)
                                    )
                                }
                            }

                            Spacer(modifier = Modifier.height(14.dp))

                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(10.dp)
                            ) {
                                Button(
                                    onClick = {
                                        val uri = FileProvider.getUriForFile(
                                            context,
                                            "${context.packageName}.fileprovider",
                                            result.outputFile
                                        )
                                        val shareIntent = Intent(Intent.ACTION_SEND).apply {
                                            type = "application/pdf"
                                            putExtra(Intent.EXTRA_STREAM, uri)
                                            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                                        }
                                        context.startActivity(Intent.createChooser(shareIntent, "Share Compressed PDF"))
                                    },
                                    colors = ButtonDefaults.buttonColors(containerColor = AdfEmerald),
                                    shape = RoundedCornerShape(10.dp),
                                    modifier = Modifier.weight(1f)
                                ) {
                                    Icon(Icons.Default.Share, contentDescription = "Share", tint = AdfMidnightBackground, modifier = Modifier.size(16.dp))
                                    Spacer(modifier = Modifier.width(6.dp))
                                    Text("Share File", color = AdfMidnightBackground, fontSize = 12.5.sp, fontWeight = FontWeight.Bold)
                                }

                                OutlinedButton(
                                    onClick = {
                                        val uri = Uri.fromFile(result.outputFile)
                                        viewModel.importPdfFromUri(uri)
                                    },
                                    border = androidx.compose.foundation.BorderStroke(1.dp, AdfEmerald),
                                    shape = RoundedCornerShape(10.dp),
                                    modifier = Modifier.weight(1f)
                                ) {
                                    Icon(Icons.Default.Visibility, contentDescription = "Open", tint = AdfEmerald, modifier = Modifier.size(16.dp))
                                    Spacer(modifier = Modifier.width(6.dp))
                                    Text("Open PDF", color = AdfEmerald, fontSize = 12.5.sp, fontWeight = FontWeight.Bold)
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

private fun getFileNameFromUri(context: Context, uri: Uri): String? {
    var name: String? = null
    if (uri.scheme == "content") {
        val cursor = context.contentResolver.query(uri, null, null, null, null)
        cursor?.use {
            if (it.moveToFirst()) {
                val index = it.getColumnIndex(android.provider.OpenableColumns.DISPLAY_NAME)
                if (index != -1) name = it.getString(index)
            }
        }
    }
    if (name == null) {
        name = uri.path?.let { p ->
            val cut = p.lastIndexOf('/')
            if (cut != -1) p.substring(cut + 1) else p
        }
    }
    return name
}

package com.example.ui.screens

import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.net.Uri
import android.provider.OpenableColumns
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.automirrored.filled.Article
import androidx.compose.material.icons.automirrored.filled.BrandingWatermark
import androidx.compose.material.icons.automirrored.filled.DriveFileMove
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
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
import coil.compose.AsyncImage
import com.example.data.local.DocumentEntity
import com.example.data.pdf.DocumentConverterEngine
import com.example.ui.PdfViewModel
import com.example.ui.components.WatermarkCustomizerDialog
import com.example.ui.theme.*
import kotlinx.coroutines.launch
import java.io.File

// ==========================================
// 1. PDF TO IMAGES TAB
// ==========================================

@Composable
fun PdfToImagesTab(
    viewModel: PdfViewModel,
    documents: List<DocumentEntity>
) {
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()
    var selectedDoc by remember { mutableStateOf(documents.firstOrNull()) }
    var outputFormat by remember { mutableStateOf("PNG") } // PNG or JPG
    var dpiScale by remember { mutableIntStateOf(2) } // 1x, 2x (HD), 3x (Ultra HD)
    var isConverting by remember { mutableStateOf(false) }
    var convertedImages by remember { mutableStateOf<List<File>>(emptyList()) }
    var zipFileForShare by remember { mutableStateOf<File?>(null) }

    // Custom PDF Picker
    val pdfPickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument()
    ) { uri ->
        if (uri != null) {
            viewModel.importPdfFromUri(uri)
        }
    }

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
        contentPadding = PaddingValues(bottom = 40.dp)
    ) {
        // Source PDF Selector
        item {
            Card(
                colors = CardDefaults.cardColors(containerColor = AcrobatDarkSurface),
                shape = RoundedCornerShape(12.dp)
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "Select PDF Document",
                            style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold),
                            color = AcrobatTextPrimary
                        )
                        TextButton(onClick = { pdfPickerLauncher.launch(arrayOf("application/pdf")) }) {
                            Icon(Icons.Default.FileOpen, contentDescription = null, modifier = Modifier.size(16.dp), tint = CrimsonAccent)
                            Spacer(modifier = Modifier.width(4.dp))
                            Text("Pick from Files", color = CrimsonAccent, fontSize = 12.sp)
                        }
                    }

                    Spacer(modifier = Modifier.height(10.dp))

                    if (documents.isEmpty()) {
                        Text("No PDF documents found. Open or pick a PDF first.", color = AcrobatTextSecondary, style = MaterialTheme.typography.bodySmall)
                    } else {
                        LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            items(documents) { doc ->
                                val isSelected = selectedDoc?.id == doc.id
                                FilterChip(
                                    selected = isSelected,
                                    onClick = { selectedDoc = doc },
                                    label = { Text(doc.title.take(18), maxLines = 1, overflow = TextOverflow.Ellipsis) },
                                    leadingIcon = {
                                        Icon(
                                            Icons.Default.PictureAsPdf,
                                            contentDescription = null,
                                            tint = if (isSelected) CrimsonAccent else AcrobatTextMuted,
                                            modifier = Modifier.size(16.dp)
                                        )
                                    },
                                    colors = FilterChipDefaults.filterChipColors(
                                        selectedContainerColor = CrimsonPrimary.copy(alpha = 0.25f),
                                        selectedLabelColor = Color.White,
                                        containerColor = AcrobatDarkSurfaceElevated,
                                        labelColor = AcrobatTextSecondary
                                    )
                                )
                            }
                        }
                    }
                }
            }
        }

        // Export Configuration
        item {
            Card(
                colors = CardDefaults.cardColors(containerColor = AcrobatDarkSurface),
                shape = RoundedCornerShape(12.dp)
            ) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(14.dp)
                ) {
                    Text(
                        text = "Image Export Settings",
                        style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold),
                        color = AcrobatTextPrimary
                    )

                    // Format: PNG vs JPG
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text("Output Format", color = AcrobatTextSecondary, style = MaterialTheme.typography.bodyMedium)
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            FilterChip(
                                selected = outputFormat == "PNG",
                                onClick = { outputFormat = "PNG" },
                                label = { Text("PNG (Lossless)") },
                                colors = FilterChipDefaults.filterChipColors(
                                    selectedContainerColor = CrimsonPrimary,
                                    selectedLabelColor = Color.White
                                )
                            )
                            FilterChip(
                                selected = outputFormat == "JPG",
                                onClick = { outputFormat = "JPG" },
                                label = { Text("JPEG") },
                                colors = FilterChipDefaults.filterChipColors(
                                    selectedContainerColor = CrimsonPrimary,
                                    selectedLabelColor = Color.White
                                )
                            )
                        }
                    }

                    // Resolution Quality: 1x, 2x, 3x
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column {
                            Text("Resolution Quality", color = AcrobatTextSecondary, style = MaterialTheme.typography.bodyMedium)
                            Text(
                                text = when (dpiScale) {
                                    1 -> "Standard (72 DPI)"
                                    2 -> "High Definition (144 DPI)"
                                    else -> "Ultra HD (216 DPI)"
                                },
                                style = MaterialTheme.typography.labelSmall,
                                color = CrimsonAccent
                            )
                        }
                        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                            listOf(1 to "1x", 2 to "2x HD", 3 to "3x UHD").forEach { (scale, lbl) ->
                                FilterChip(
                                    selected = dpiScale == scale,
                                    onClick = { dpiScale = scale },
                                    label = { Text(lbl, fontSize = 11.sp) },
                                    colors = FilterChipDefaults.filterChipColors(
                                        selectedContainerColor = CrimsonPrimary,
                                        selectedLabelColor = Color.White
                                    )
                                )
                            }
                        }
                    }

                    // Convert Button
                    Button(
                        onClick = {
                            val doc = selectedDoc ?: return@Button
                            isConverting = true
                            convertedImages = emptyList()
                            val format = if (outputFormat == "PNG") Bitmap.CompressFormat.PNG else Bitmap.CompressFormat.JPEG
                            viewModel.convertPdfToImages(
                                doc = doc,
                                format = format,
                                dpiScale = dpiScale
                            ) { files ->
                                isConverting = false
                                convertedImages = files
                                coroutineScope.launch {
                                    if (files.isNotEmpty()) {
                                        val zip = File(context.cacheDir, "${doc.title}_images.zip")
                                        DocumentConverterEngine.createZipFromFiles(files, zip)
                                        zipFileForShare = zip
                                    }
                                }
                            }
                        },
                        enabled = selectedDoc != null && !isConverting,
                        colors = ButtonDefaults.buttonColors(containerColor = CrimsonPrimary),
                        shape = RoundedCornerShape(8.dp),
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(48.dp)
                            .testTag("convert_pdf_to_images_button")
                    ) {
                        if (isConverting) {
                            CircularProgressIndicator(color = Color.White, modifier = Modifier.size(22.dp))
                            Spacer(modifier = Modifier.width(8.dp))
                            Text("Extracting PDF Pages...")
                        } else {
                            Icon(Icons.Default.PhotoLibrary, contentDescription = null)
                            Spacer(modifier = Modifier.width(8.dp))
                            Text("Convert Pages to $outputFormat Images")
                        }
                    }
                }
            }
        }

        // Converted Results Preview & Share
        if (convertedImages.isNotEmpty()) {
            item {
                Card(
                    colors = CardDefaults.cardColors(containerColor = AcrobatDarkSurfaceElevated),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Column(
                        modifier = Modifier.padding(16.dp),
                        verticalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = "Converted Images (${convertedImages.size} pages)",
                                style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold),
                                color = AcrobatTextPrimary
                            )

                            // Share / Export All as ZIP
                            zipFileForShare?.let { zip ->
                                FilledTonalButton(
                                    onClick = {
                                        val uri = FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", zip)
                                        val intent = Intent(Intent.ACTION_SEND).apply {
                                            type = "application/zip"
                                            putExtra(Intent.EXTRA_STREAM, uri)
                                            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                                        }
                                        context.startActivity(Intent.createChooser(intent, "Share Images ZIP"))
                                    },
                                    colors = ButtonDefaults.filledTonalButtonColors(
                                        containerColor = CrimsonPrimary.copy(alpha = 0.2f),
                                        contentColor = CrimsonAccent
                                    ),
                                    contentPadding = PaddingValues(horizontal = 10.dp, vertical = 4.dp)
                                ) {
                                    Icon(Icons.Default.Share, contentDescription = null, modifier = Modifier.size(16.dp))
                                    Spacer(modifier = Modifier.width(4.dp))
                                    Text("Share All (ZIP)", fontSize = 11.sp, fontWeight = FontWeight.Bold)
                                }
                            }
                        }

                        LazyRow(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                            itemsIndexed(convertedImages) { idx, imgFile ->
                                Card(
                                    shape = RoundedCornerShape(8.dp),
                                    colors = CardDefaults.cardColors(containerColor = PaperWhite),
                                    modifier = Modifier
                                        .width(110.dp)
                                        .height(150.dp)
                                        .clickable {
                                            val uri = FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", imgFile)
                                            val intent = Intent(Intent.ACTION_VIEW).apply {
                                                setDataAndType(uri, "image/*")
                                                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                                            }
                                            context.startActivity(Intent.createChooser(intent, "Open Image"))
                                        }
                                ) {
                                    Box(modifier = Modifier.fillMaxSize()) {
                                        AsyncImage(
                                            model = imgFile,
                                            contentDescription = "Page ${idx + 1}",
                                            contentScale = ContentScale.Crop,
                                            modifier = Modifier.fillMaxSize()
                                        )
                                        Box(
                                            modifier = Modifier
                                                .align(Alignment.BottomCenter)
                                                .fillMaxWidth()
                                                .background(Color.Black.copy(alpha = 0.65f))
                                                .padding(vertical = 2.dp),
                                            contentAlignment = Alignment.Center
                                        ) {
                                            Text("Page ${idx + 1}", color = Color.White, fontSize = 10.sp, fontWeight = FontWeight.Bold)
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

// ==========================================
// 2. WORD TO PDF TAB
// ==========================================

@Composable
fun WordToPdfTab(viewModel: PdfViewModel) {
    val context = LocalContext.current
    var selectedWordUri by remember { mutableStateOf<Uri?>(null) }
    var selectedWordName by remember { mutableStateOf("") }
    var customPdfTitle by remember { mutableStateOf("") }
    var isConverting by remember { mutableStateOf(false) }

    val wordPicker = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument()
    ) { uri ->
        if (uri != null) {
            selectedWordUri = uri
            var name = "Document.docx"
            try {
                context.contentResolver.query(uri, null, null, null, null)?.use { cursor ->
                    if (cursor.moveToFirst()) {
                        val nameIndex = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
                        if (nameIndex != -1) name = cursor.getString(nameIndex)
                    }
                }
            } catch (ignored: Exception) {}
            selectedWordName = name
            customPdfTitle = name.substringBeforeLast(".")
        }
    }

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        item {
            Card(
                colors = CardDefaults.cardColors(containerColor = AcrobatDarkSurface),
                shape = RoundedCornerShape(12.dp)
            ) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Box(
                            modifier = Modifier
                                .size(40.dp)
                                .clip(CircleShape)
                                .background(Color(0xFF2563EB).copy(alpha = 0.2f)),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(Icons.Default.Description, contentDescription = null, tint = Color(0xFF3B82F6))
                        }
                        Spacer(modifier = Modifier.width(12.dp))
                        Column {
                            Text("Word to PDF Converter", style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold), color = AcrobatTextPrimary)
                            Text("Converts .docx and .doc files into formatted PDF", style = MaterialTheme.typography.bodySmall, color = AcrobatTextSecondary)
                        }
                    }

                    HorizontalDivider(color = AcrobatDarkBorder)

                    if (selectedWordUri == null) {
                        OutlinedButton(
                            onClick = {
                                wordPicker.launch(
                                    arrayOf(
                                        "application/vnd.openxmlformats-officedocument.wordprocessingml.document",
                                        "application/msword",
                                        "application/octet-stream"
                                    )
                                )
                            },
                            colors = ButtonDefaults.outlinedButtonColors(contentColor = CrimsonAccent),
                            border = ButtonDefaults.outlinedButtonBorder(true).copy(brush = androidx.compose.ui.graphics.SolidColor(CrimsonPrimary)),
                            shape = RoundedCornerShape(8.dp),
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(52.dp)
                        ) {
                            Icon(Icons.Default.UploadFile, contentDescription = null)
                            Spacer(modifier = Modifier.width(8.dp))
                            Text("Choose Word Document (.docx / .doc)")
                        }
                    } else {
                        Card(
                            colors = CardDefaults.cardColors(containerColor = AcrobatDarkSurfaceElevated),
                            shape = RoundedCornerShape(8.dp)
                        ) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(12.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Row(
                                    modifier = Modifier.weight(1f),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Icon(Icons.AutoMirrored.Filled.Article, contentDescription = null, tint = Color(0xFF3B82F6), modifier = Modifier.size(24.dp))
                                    Spacer(modifier = Modifier.width(10.dp))
                                    Column {
                                        Text(selectedWordName, color = AcrobatTextPrimary, fontWeight = FontWeight.Bold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                        Text("Word Document ready for conversion", color = AcrobatTextSecondary, fontSize = 11.sp)
                                    }
                                }

                                IconButton(onClick = { selectedWordUri = null }) {
                                    Icon(Icons.Default.Close, contentDescription = "Remove", tint = AcrobatTextMuted)
                                }
                            }
                        }

                        OutlinedTextField(
                            value = customPdfTitle,
                            onValueChange = { customPdfTitle = it },
                            label = { Text("Output PDF Title") },
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

                        Button(
                            onClick = {
                                val uri = selectedWordUri ?: return@Button
                                isConverting = true
                                viewModel.convertWordToPdf(
                                    wordUri = uri,
                                    docTitle = customPdfTitle.ifBlank { "Word_Converted" }
                                ) { newDoc ->
                                    isConverting = false
                                    if (newDoc != null) {
                                        selectedWordUri = null
                                    }
                                }
                            },
                            enabled = !isConverting,
                            colors = ButtonDefaults.buttonColors(containerColor = CrimsonPrimary),
                            shape = RoundedCornerShape(8.dp),
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(48.dp)
                                .testTag("convert_word_to_pdf_action_button")
                        ) {
                            if (isConverting) {
                                CircularProgressIndicator(color = Color.White, modifier = Modifier.size(22.dp))
                                Spacer(modifier = Modifier.width(8.dp))
                                Text("Converting Word to PDF...")
                            } else {
                                Icon(Icons.Default.PictureAsPdf, contentDescription = null)
                                Spacer(modifier = Modifier.width(8.dp))
                                Text("Convert and Open in PDF Reader")
                            }
                        }
                    }
                }
            }
        }
    }
}

// ==========================================
// 3. PDF TO WORD TAB
// ==========================================

@Composable
fun PdfToWordTab(
    viewModel: PdfViewModel,
    documents: List<DocumentEntity>
) {
    val context = LocalContext.current
    var selectedDoc by remember { mutableStateOf(documents.firstOrNull()) }
    var isConverting by remember { mutableStateOf(false) }
    var convertedWordFile by remember { mutableStateOf<File?>(null) }

    val pdfPickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument()
    ) { uri ->
        if (uri != null) {
            viewModel.importPdfFromUri(uri)
        }
    }

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        item {
            Card(
                colors = CardDefaults.cardColors(containerColor = AcrobatDarkSurface),
                shape = RoundedCornerShape(12.dp)
            ) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(14.dp)
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Box(
                            modifier = Modifier
                                .size(40.dp)
                                .clip(CircleShape)
                                .background(Color(0xFF10B981).copy(alpha = 0.2f)),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(Icons.AutoMirrored.Filled.Article, contentDescription = null, tint = Color(0xFF10B981))
                        }
                        Spacer(modifier = Modifier.width(12.dp))
                        Column {
                            Text("PDF to Word (.docx) Converter", style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold), color = AcrobatTextPrimary)
                            Text("Extracts document content into Microsoft Word format", style = MaterialTheme.typography.bodySmall, color = AcrobatTextSecondary)
                        }
                    }

                    HorizontalDivider(color = AcrobatDarkBorder)

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text("Select PDF to Convert", style = MaterialTheme.typography.labelMedium, color = AcrobatTextSecondary)
                        TextButton(onClick = { pdfPickerLauncher.launch(arrayOf("application/pdf")) }) {
                            Icon(Icons.Default.FileOpen, contentDescription = null, modifier = Modifier.size(16.dp), tint = CrimsonAccent)
                            Spacer(modifier = Modifier.width(4.dp))
                            Text("Pick File", color = CrimsonAccent, fontSize = 12.sp)
                        }
                    }

                    if (documents.isEmpty()) {
                        Text("No PDF documents in library.", color = AcrobatTextMuted, fontSize = 12.sp)
                    } else {
                        LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            items(documents) { doc ->
                                val isSelected = selectedDoc?.id == doc.id
                                FilterChip(
                                    selected = isSelected,
                                    onClick = { selectedDoc = doc },
                                    label = { Text(doc.title.take(18)) },
                                    leadingIcon = {
                                        Icon(Icons.Default.PictureAsPdf, contentDescription = null, tint = if (isSelected) CrimsonAccent else AcrobatTextMuted, modifier = Modifier.size(16.dp))
                                    },
                                    colors = FilterChipDefaults.filterChipColors(
                                        selectedContainerColor = CrimsonPrimary.copy(alpha = 0.25f),
                                        selectedLabelColor = Color.White,
                                        containerColor = AcrobatDarkSurfaceElevated,
                                        labelColor = AcrobatTextSecondary
                                    )
                                )
                            }
                        }
                    }

                    Button(
                        onClick = {
                            val doc = selectedDoc ?: return@Button
                            isConverting = true
                            convertedWordFile = null
                            viewModel.convertPdfToWord(doc) { outFile ->
                                isConverting = false
                                convertedWordFile = outFile
                            }
                        },
                        enabled = selectedDoc != null && !isConverting,
                        colors = ButtonDefaults.buttonColors(containerColor = CrimsonPrimary),
                        shape = RoundedCornerShape(8.dp),
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(48.dp)
                            .testTag("convert_pdf_to_word_action_button")
                    ) {
                        if (isConverting) {
                            CircularProgressIndicator(color = Color.White, modifier = Modifier.size(22.dp))
                            Spacer(modifier = Modifier.width(8.dp))
                            Text("Exporting to Word...")
                        } else {
                            Icon(Icons.AutoMirrored.Filled.DriveFileMove, contentDescription = null)
                            Spacer(modifier = Modifier.width(8.dp))
                            Text("Convert to Word (.docx)")
                        }
                    }

                    // Converted Word File Result
                    convertedWordFile?.let { wordFile ->
                        Card(
                            colors = CardDefaults.cardColors(containerColor = AcrobatDarkSurfaceElevated),
                            shape = RoundedCornerShape(8.dp)
                        ) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(12.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Row(
                                    modifier = Modifier.weight(1f),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Icon(Icons.Default.CheckCircle, contentDescription = null, tint = Color(0xFF10B981), modifier = Modifier.size(24.dp))
                                    Spacer(modifier = Modifier.width(10.dp))
                                    Column {
                                        Text(wordFile.name, color = AcrobatTextPrimary, fontWeight = FontWeight.Bold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                        Text("${wordFile.length() / 1024} KB • OpenXML Format", color = AcrobatTextSecondary, fontSize = 11.sp)
                                    }
                                }

                                Button(
                                    onClick = {
                                        val uri = FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", wordFile)
                                        val intent = Intent(Intent.ACTION_SEND).apply {
                                            type = "application/vnd.openxmlformats-officedocument.wordprocessingml.document"
                                            putExtra(Intent.EXTRA_STREAM, uri)
                                            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                                        }
                                        context.startActivity(Intent.createChooser(intent, "Share Word Document"))
                                    },
                                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF2563EB)),
                                    contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp)
                                ) {
                                    Icon(Icons.Default.Share, contentDescription = null, modifier = Modifier.size(16.dp))
                                    Spacer(modifier = Modifier.width(4.dp))
                                    Text("Share Word", fontSize = 12.sp)
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

// ==========================================
// 4. WATERMARK STUDIO TAB
// ==========================================

@Composable
fun WatermarkStudioTab(
    viewModel: PdfViewModel,
    documents: List<DocumentEntity>,
    currentDoc: DocumentEntity?
) {
    var selectedDoc by remember { mutableStateOf(currentDoc ?: documents.firstOrNull()) }
    var watermarkText by remember { mutableStateOf("CONFIDENTIAL") }
    var colorHex by remember { mutableLongStateOf(0xFFDC2626L) }
    var opacity by remember { mutableFloatStateOf(0.25f) }
    var rotationAngle by remember { mutableFloatStateOf(45f) }
    var fontSizeSp by remember { mutableFloatStateOf(38f) }
    var isTiledPattern by remember { mutableStateOf(false) }
    var applyToAllPages by remember { mutableStateOf(true) }
    var isProcessing by remember { mutableStateOf(false) }

    val presets = listOf("CONFIDENTIAL", "DRAFT", "COPY", "APPROVED", "SAMPLE", "DO NOT COPY")
    val colors = listOf(
        0xFFDC2626L to "Crimson",
        0xFF475569L to "Slate",
        0xFF2563EBL to "Blue",
        0xFF16A34AL to "Green",
        0xFFD97706L to "Amber",
        0xFF7C3AEDL to "Purple"
    )

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
        contentPadding = PaddingValues(bottom = 40.dp)
    ) {
        // Document Selector
        item {
            Card(
                colors = CardDefaults.cardColors(containerColor = AcrobatDarkSurface),
                shape = RoundedCornerShape(12.dp)
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text(
                        text = "Target PDF Document",
                        style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold),
                        color = AcrobatTextPrimary
                    )
                    Spacer(modifier = Modifier.height(10.dp))
                    if (documents.isEmpty()) {
                        Text("No documents available.", color = AcrobatTextSecondary)
                    } else {
                        LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            items(documents) { doc ->
                                val isSelected = selectedDoc?.id == doc.id
                                FilterChip(
                                    selected = isSelected,
                                    onClick = { selectedDoc = doc },
                                    label = { Text(doc.title.take(18)) },
                                    leadingIcon = {
                                        Icon(Icons.Default.PictureAsPdf, contentDescription = null, tint = if (isSelected) CrimsonAccent else AcrobatTextMuted, modifier = Modifier.size(16.dp))
                                    },
                                    colors = FilterChipDefaults.filterChipColors(
                                        selectedContainerColor = CrimsonPrimary.copy(alpha = 0.25f),
                                        selectedLabelColor = Color.White,
                                        containerColor = AcrobatDarkSurfaceElevated,
                                        labelColor = AcrobatTextSecondary
                                    )
                                )
                            }
                        }
                    }
                }
            }
        }

        // Live Simulated Sheet Preview
        item {
            Card(
                shape = RoundedCornerShape(12.dp),
                colors = CardDefaults.cardColors(containerColor = PaperWhite),
                elevation = CardDefaults.cardElevation(defaultElevation = 6.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .height(160.dp)
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(16.dp),
                    contentAlignment = Alignment.Center
                ) {
                    // Document placeholder lines
                    Column(
                        modifier = Modifier.fillMaxSize(),
                        verticalArrangement = Arrangement.SpaceEvenly
                    ) {
                        repeat(6) {
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth(if (it == 0) 0.5f else 0.95f)
                                    .height(7.dp)
                                    .clip(RoundedCornerShape(3.dp))
                                    .background(Color(0xFFE2E8F0))
                            )
                        }
                    }

                    // Watermark Preview
                    if (watermarkText.isNotBlank()) {
                        if (isTiledPattern) {
                            Column(
                                modifier = Modifier
                                    .fillMaxSize()
                                    .rotate(-rotationAngle),
                                verticalArrangement = Arrangement.SpaceAround,
                                horizontalAlignment = Alignment.CenterHorizontally
                            ) {
                                repeat(3) {
                                    Text(
                                        text = watermarkText,
                                        fontWeight = FontWeight.Bold,
                                        fontSize = (fontSizeSp * 0.38f).sp,
                                        color = Color(colorHex).copy(alpha = opacity),
                                        textAlign = TextAlign.Center
                                    )
                                }
                            }
                        } else {
                            Text(
                                text = watermarkText,
                                fontWeight = FontWeight.Bold,
                                fontSize = (fontSizeSp * 0.6f).sp,
                                color = Color(colorHex).copy(alpha = opacity),
                                textAlign = TextAlign.Center,
                                modifier = Modifier.rotate(-rotationAngle)
                            )
                        }
                    }
                }
            }
        }

        // Watermark Controls
        item {
            Card(
                colors = CardDefaults.cardColors(containerColor = AcrobatDarkSurface),
                shape = RoundedCornerShape(12.dp)
            ) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(14.dp)
                ) {
                    Text("Watermark Configuration", style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold), color = AcrobatTextPrimary)

                    OutlinedTextField(
                        value = watermarkText,
                        onValueChange = { watermarkText = it },
                        label = { Text("Watermark Text") },
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

                    // Presets
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        presets.take(3).forEach { p ->
                            FilterChip(
                                selected = watermarkText == p,
                                onClick = { watermarkText = p },
                                label = { Text(p, fontSize = 11.sp) },
                                colors = FilterChipDefaults.filterChipColors(
                                    selectedContainerColor = CrimsonPrimary,
                                    selectedLabelColor = Color.White
                                )
                            )
                        }
                    }

                    // Colors
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        colors.forEach { (cHex, _) ->
                            Box(
                                modifier = Modifier
                                    .size(34.dp)
                                    .clip(CircleShape)
                                    .background(Color(cHex))
                                    .border(
                                        width = if (colorHex == cHex) 3.dp else 1.dp,
                                        color = if (colorHex == cHex) Color.White else Color.Transparent,
                                        shape = CircleShape
                                    )
                                    .clickable { colorHex = cHex }
                            )
                        }
                    }

                    // Sliders
                    Column {
                        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                            Text("Opacity: ${(opacity * 100).toInt()}%", color = AcrobatTextSecondary, style = MaterialTheme.typography.labelSmall)
                            Text("Angle: ${rotationAngle.toInt()}°", color = AcrobatTextSecondary, style = MaterialTheme.typography.labelSmall)
                        }
                        Slider(
                            value = opacity,
                            onValueChange = { opacity = it },
                            valueRange = 0.10f..0.80f,
                            colors = SliderDefaults.colors(thumbColor = CrimsonPrimary, activeTrackColor = CrimsonPrimary)
                        )
                    }

                    // Repeated Grid Switch
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text("Tiled Repeated Diagonal Pattern", color = AcrobatTextPrimary, style = MaterialTheme.typography.bodySmall)
                        Switch(
                            checked = isTiledPattern,
                            onCheckedChange = { isTiledPattern = it },
                            colors = SwitchDefaults.colors(checkedThumbColor = Color.White, checkedTrackColor = CrimsonPrimary)
                        )
                    }

                    // Apply Button
                    Button(
                        onClick = {
                            val doc = selectedDoc ?: return@Button
                            isProcessing = true
                            val options = DocumentConverterEngine.WatermarkOptions(
                                text = watermarkText.trim(),
                                colorHex = colorHex,
                                opacity = opacity,
                                rotationAngle = rotationAngle,
                                fontSizeSp = fontSizeSp,
                                isTiledPattern = isTiledPattern,
                                applyToAllPages = applyToAllPages
                            )
                            viewModel.stampWatermarkOnDocument(doc, options) {
                                isProcessing = false
                            }
                        },
                        enabled = selectedDoc != null && watermarkText.isNotBlank() && !isProcessing,
                        colors = ButtonDefaults.buttonColors(containerColor = CrimsonPrimary),
                        shape = RoundedCornerShape(8.dp),
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(48.dp)
                            .testTag("stamp_watermark_action_button")
                    ) {
                        if (isProcessing) {
                            CircularProgressIndicator(color = Color.White, modifier = Modifier.size(22.dp))
                            Spacer(modifier = Modifier.width(8.dp))
                            Text("Stamping Watermark...")
                        } else {
                            Icon(Icons.AutoMirrored.Filled.BrandingWatermark, contentDescription = null)
                            Spacer(modifier = Modifier.width(8.dp))
                            Text("Stamp Watermark & Save PDF")
                        }
                    }
                }
            }
        }
    }
}

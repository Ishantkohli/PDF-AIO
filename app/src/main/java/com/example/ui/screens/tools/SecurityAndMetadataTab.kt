package com.example.ui.screens.tools

import android.content.ClipData
import android.content.Intent
import android.net.Uri
import android.provider.OpenableColumns
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.FileProvider
import com.example.data.local.DocumentEntity
import com.example.data.pdf.PdfSecurityEngine
import com.example.ui.AppScreen
import com.example.ui.PdfViewModel
import com.example.ui.theme.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream

/**
 * 100% Local PDF Security (AES-128 / AES-256 Encryption & Password Removal)
 * and Document Metadata Inspector / Editor powered by Apache PDFBox.
 */
@Composable
fun SecurityAndMetadataTab(
    viewModel: PdfViewModel,
    documents: List<DocumentEntity>,
    currentDoc: DocumentEntity?
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    var selectedDoc by remember(currentDoc, documents) {
        mutableStateOf(currentDoc ?: documents.firstOrNull())
    }
    var externalTempFile by remember { mutableStateOf<File?>(null) }
    var externalTitle by remember { mutableStateOf<String?>(null) }

    var metadataInfo by remember { mutableStateOf<PdfSecurityEngine.PdfMetadataInfo?>(null) }
    var isBusy by remember { mutableStateOf(false) }
    var feedbackBanner by remember { mutableStateOf<String?>(null) }
    var createdOutputDoc by remember { mutableStateOf<DocumentEntity?>(null) }

    // Editable metadata fields
    var metaTitle by remember { mutableStateOf("") }
    var metaAuthor by remember { mutableStateOf("") }
    var metaSubject by remember { mutableStateOf("") }
    var metaKeywords by remember { mutableStateOf("") }

    // Encryption / Unlock states
    var protectUserPassword by remember { mutableStateOf("") }
    var protectOwnerPassword by remember { mutableStateOf("") }
    var allowPrinting by remember { mutableStateOf(true) }
    var allowCopying by remember { mutableStateOf(false) }
    var useAes256 by remember { mutableStateOf(true) }
    var unlockPassword by remember { mutableStateOf("") }

    DisposableEffect(Unit) {
        onDispose {
            externalTempFile?.delete()
        }
    }

    val activeFile = externalTempFile ?: selectedDoc?.let { File(it.filePath) }
    val activeTitle = externalTitle ?: selectedDoc?.title ?: "No PDF selected"

    LaunchedEffect(activeFile?.absolutePath) {
        val f = activeFile ?: return@LaunchedEffect
        if (!f.exists()) return@LaunchedEffect
        val res = PdfSecurityEngine.readMetadata(context, f)
        res.onSuccess { info ->
            metadataInfo = info
            metaTitle = info.title.ifBlank { selectedDoc?.title.orEmpty() }
            metaAuthor = info.author
            metaSubject = info.subject
            metaKeywords = info.keywords
        }
    }

    val externalPicker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) {
            scope.launch(Dispatchers.IO) {
                var display = "Selected.pdf"
                context.contentResolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)?.use { c ->
                    val idx = c.getColumnIndex(OpenableColumns.DISPLAY_NAME)
                    if (idx >= 0 && c.moveToFirst()) display = c.getString(idx) ?: display
                }
                externalTempFile?.delete()
                val tmp = File(context.cacheDir, "sec_src_${System.currentTimeMillis()}.pdf")
                val copied = context.contentResolver.openInputStream(uri)?.use { input ->
                    FileOutputStream(tmp).use { out -> input.copyTo(out) }
                    true
                } ?: false
                if (copied && tmp.exists()) {
                    withContext(Dispatchers.Main) {
                        selectedDoc = null
                        externalTempFile = tmp
                        externalTitle = display.removeSuffix(".pdf")
                        feedbackBanner = null
                    }
                }
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
        // 1. Target Document Card
        item {
            Card(
                colors = CardDefaults.cardColors(containerColor = AcrobatDarkSurface),
                shape = RoundedCornerShape(14.dp),
                border = BorderStroke(1.dp, AcrobatDarkBorder),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Default.Security, contentDescription = null, tint = AdfElectricCyan)
                        Spacer(modifier = Modifier.width(8.dp))
                        Column(modifier = Modifier.weight(1f)) {
                            Text("PDF Security & Metadata Studio", fontWeight = FontWeight.Bold, color = AcrobatTextPrimary)
                            Text("On-device AES encryption, password unlock & PDF metadata editor", fontSize = 11.sp, color = AcrobatTextSecondary)
                        }
                    }

                    Text(
                        text = "Active File: $activeTitle",
                        color = AdfElectricCyan,
                        fontWeight = FontWeight.SemiBold,
                        fontSize = 13.sp,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )

                    metadataInfo?.let { info ->
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            Surface(
                                color = if (info.isEncrypted) Color(0xFFEF4444).copy(alpha = 0.2f) else Color(0xFF10B981).copy(alpha = 0.2f),
                                shape = RoundedCornerShape(6.dp)
                            ) {
                                Text(
                                    text = if (info.isEncrypted) "ENCRYPTED" else "UNLOCKED",
                                    color = if (info.isEncrypted) Color(0xFFFCA5A5) else Color(0xFF6EE7B7),
                                    fontSize = 10.sp,
                                    fontWeight = FontWeight.Bold,
                                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp)
                                )
                            }
                            if (info.pageCount > 0) {
                                Surface(color = AcrobatDarkSurfaceElevated, shape = RoundedCornerShape(6.dp)) {
                                    Text(
                                        text = "${info.pageCount} pages",
                                        color = AcrobatTextSecondary,
                                        fontSize = 10.sp,
                                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp)
                                    )
                                }
                            }
                        }
                    }

                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                        OutlinedButton(
                            onClick = { externalPicker.launch(arrayOf("application/pdf")) },
                            shape = RoundedCornerShape(10.dp),
                            modifier = Modifier.weight(1f).testTag("sec_pick_external_pdf")
                        ) {
                            Icon(Icons.Default.FolderOpen, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("Browse Storage", fontSize = 12.sp)
                        }

                        if (documents.isNotEmpty()) {
                            var showDocDropdown by remember { mutableStateOf(false) }
                            Box(modifier = Modifier.weight(1f)) {
                                OutlinedButton(
                                    onClick = { showDocDropdown = true },
                                    shape = RoundedCornerShape(10.dp),
                                    modifier = Modifier.fillMaxWidth().testTag("sec_pick_library_pdf")
                                ) {
                                    Icon(Icons.Default.LibraryBooks, contentDescription = null, modifier = Modifier.size(16.dp))
                                    Spacer(modifier = Modifier.width(6.dp))
                                    Text("Library", fontSize = 12.sp)
                                }
                                DropdownMenu(
                                    expanded = showDocDropdown,
                                    onDismissRequest = { showDocDropdown = false }
                                ) {
                                    documents.take(12).forEach { doc ->
                                        DropdownMenuItem(
                                            text = { Text(doc.title, maxLines = 1, overflow = TextOverflow.Ellipsis) },
                                            onClick = {
                                                showDocDropdown = false
                                                externalTempFile?.delete()
                                                externalTempFile = null
                                                externalTitle = null
                                                selectedDoc = doc
                                                feedbackBanner = null
                                            }
                                        )
                                    }
                                }
                            }
                        }
                    }

                    feedbackBanner?.let { msg ->
                        Text(msg, color = AdfElectricCyan, fontSize = 12.sp, fontWeight = FontWeight.Medium)
                    }
                }
            }
        }

        // 2. Password Protection (Encrypt PDF)
        item {
            Card(
                colors = CardDefaults.cardColors(containerColor = AcrobatDarkSurface),
                shape = RoundedCornerShape(14.dp),
                border = BorderStroke(1.dp, AcrobatDarkBorder),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text("Protect PDF with Password (AES)", fontWeight = FontWeight.Bold, color = AcrobatTextPrimary)

                    OutlinedTextField(
                        value = protectUserPassword,
                        onValueChange = { protectUserPassword = it },
                        label = { Text("Open Password (Required)") },
                        visualTransformation = PasswordVisualTransformation(),
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth().testTag("sec_protect_user_pwd")
                    )
                    OutlinedTextField(
                        value = protectOwnerPassword,
                        onValueChange = { protectOwnerPassword = it },
                        label = { Text("Owner / Permissions Password (Optional)") },
                        visualTransformation = PasswordVisualTransformation(),
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth().testTag("sec_protect_owner_pwd")
                    )

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text("AES Encryption Strength", color = AcrobatTextSecondary, fontSize = 12.sp)
                        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                            FilterChip(
                                selected = !useAes256,
                                onClick = { useAes256 = false },
                                label = { Text("AES-128", fontSize = 11.sp) }
                            )
                            FilterChip(
                                selected = useAes256,
                                onClick = { useAes256 = true },
                                label = { Text("AES-256", fontSize = 11.sp) }
                            )
                        }
                    }

                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                        Text("Allow Printing", color = AcrobatTextPrimary, fontSize = 13.sp)
                        Switch(checked = allowPrinting, onCheckedChange = { allowPrinting = it })
                    }
                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                        Text("Allow Text Copying", color = AcrobatTextPrimary, fontSize = 13.sp)
                        Switch(checked = allowCopying, onCheckedChange = { allowCopying = it })
                    }

                    Button(
                        onClick = {
                            val src = activeFile ?: return@Button
                            isBusy = true
                            viewModel.protectPdfWithPassword(
                                sourceFile = src,
                                baseTitle = activeTitle,
                                userPassword = protectUserPassword,
                                ownerPassword = protectOwnerPassword.ifBlank { protectUserPassword },
                                allowPrinting = allowPrinting,
                                allowCopying = allowCopying,
                                keyLengthBits = if (useAes256) 256 else 128
                            ) { result ->
                                isBusy = false
                                result.onSuccess { outDoc ->
                                    createdOutputDoc = outDoc
                                    protectUserPassword = ""
                                    protectOwnerPassword = ""
                                    feedbackBanner = "Protected copy saved: ${outDoc.title}.pdf"
                                }.onFailure { err ->
                                    feedbackBanner = err.localizedMessage ?: "Encryption failed."
                                }
                            }
                        },
                        enabled = activeFile != null && protectUserPassword.isNotBlank() && !isBusy,
                        colors = ButtonDefaults.buttonColors(containerColor = CrimsonPrimary),
                        modifier = Modifier.fillMaxWidth().testTag("sec_encrypt_button")
                    ) {
                        Icon(Icons.Default.Lock, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("Create Password-Protected Copy", fontWeight = FontWeight.Bold)
                    }
                }
            }
        }

        // 3. Unlock Password-Protected PDF
        item {
            Card(
                colors = CardDefaults.cardColors(containerColor = AcrobatDarkSurface),
                shape = RoundedCornerShape(14.dp),
                border = BorderStroke(1.dp, AcrobatDarkBorder),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text("Unlock / Remove Password", fontWeight = FontWeight.Bold, color = AcrobatTextPrimary)
                    OutlinedTextField(
                        value = unlockPassword,
                        onValueChange = { unlockPassword = it },
                        label = { Text("Current PDF Password") },
                        visualTransformation = PasswordVisualTransformation(),
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth().testTag("sec_unlock_pwd_input")
                    )
                    Button(
                        onClick = {
                            val src = activeFile ?: return@Button
                            isBusy = true
                            viewModel.unlockProtectedPdf(
                                sourceFile = src,
                                baseTitle = activeTitle,
                                password = unlockPassword
                            ) { result ->
                                isBusy = false
                                result.onSuccess { outDoc ->
                                    createdOutputDoc = outDoc
                                    unlockPassword = ""
                                    feedbackBanner = "Unlocked PDF saved: ${outDoc.title}.pdf"
                                }.onFailure { err ->
                                    feedbackBanner = err.localizedMessage ?: "Failed to unlock PDF."
                                }
                            }
                        },
                        enabled = activeFile != null && unlockPassword.isNotBlank() && !isBusy,
                        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF10B981), contentColor = Color.Black),
                        modifier = Modifier.fillMaxWidth().testTag("sec_unlock_button")
                    ) {
                        Icon(Icons.Default.LockOpen, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("Unlock & Save Copy", fontWeight = FontWeight.Bold)
                    }
                }
            }
        }

        // 4. Document Metadata Inspector & Editor
        item {
            Card(
                colors = CardDefaults.cardColors(containerColor = AcrobatDarkSurface),
                shape = RoundedCornerShape(14.dp),
                border = BorderStroke(1.dp, AcrobatDarkBorder),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text("Edit PDF Metadata Properties", fontWeight = FontWeight.Bold, color = AcrobatTextPrimary)
                    OutlinedTextField(
                        value = metaTitle,
                        onValueChange = { metaTitle = it },
                        label = { Text("Title") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth().testTag("sec_meta_title")
                    )
                    OutlinedTextField(
                        value = metaAuthor,
                        onValueChange = { metaAuthor = it },
                        label = { Text("Author") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth().testTag("sec_meta_author")
                    )
                    OutlinedTextField(
                        value = metaSubject,
                        onValueChange = { metaSubject = it },
                        label = { Text("Subject") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth().testTag("sec_meta_subject")
                    )
                    OutlinedTextField(
                        value = metaKeywords,
                        onValueChange = { metaKeywords = it },
                        label = { Text("Keywords (comma-separated)") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth().testTag("sec_meta_keywords")
                    )
                    Button(
                        onClick = {
                            val src = activeFile ?: return@Button
                            isBusy = true
                            viewModel.updatePdfMetadata(
                                doc = selectedDoc,
                                sourceFile = src,
                                title = metaTitle,
                                author = metaAuthor,
                                subject = metaSubject,
                                keywords = metaKeywords
                            ) { result ->
                                isBusy = false
                                result.onSuccess { msg ->
                                    feedbackBanner = msg
                                }.onFailure { err ->
                                    feedbackBanner = err.localizedMessage ?: "Metadata update failed."
                                }
                            }
                        },
                        enabled = activeFile != null && !isBusy,
                        colors = ButtonDefaults.buttonColors(containerColor = AdfElectricCyan, contentColor = AdfMidnightBackground),
                        modifier = Modifier.fillMaxWidth().testTag("sec_save_meta_button")
                    ) {
                        Icon(Icons.Default.Save, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("Save Metadata to PDF", fontWeight = FontWeight.Bold)
                    }
                }
            }
        }
    }

    createdOutputDoc?.let { outDoc ->
        AlertDialog(
            onDismissRequest = { createdOutputDoc = null },
            containerColor = AcrobatDarkSurfaceElevated,
            title = { Text("Security Output Ready", color = AcrobatTextPrimary, fontWeight = FontWeight.Bold) },
            text = { Text("${outDoc.title}.pdf saved to your library (${outDoc.pageCount} pages).", color = AcrobatTextSecondary) },
            confirmButton = {
                Button(
                    onClick = {
                        createdOutputDoc = null
                        val pdfFile = File(outDoc.filePath)
                        val shareUri = FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", pdfFile)
                        val intent = Intent(Intent.ACTION_SEND).apply {
                            type = "application/pdf"
                            putExtra(Intent.EXTRA_STREAM, shareUri)
                            putExtra(Intent.EXTRA_SUBJECT, "${outDoc.title}.pdf")
                            clipData = ClipData.newUri(context.contentResolver, "${outDoc.title}.pdf", shareUri)
                            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                        }
                        context.startActivity(Intent.createChooser(intent, "Share ${outDoc.title}.pdf"))
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = AdfElectricCyan, contentColor = AdfMidnightBackground)
                ) {
                    Text("Share PDF", fontWeight = FontWeight.Bold)
                }
            },
            dismissButton = {
                TextButton(onClick = { createdOutputDoc = null }) {
                    Text("Close", color = AcrobatTextSecondary)
                }
            }
        )
    }
}

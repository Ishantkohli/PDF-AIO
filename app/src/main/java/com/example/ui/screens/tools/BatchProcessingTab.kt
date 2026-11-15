package com.example.ui.screens.tools

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.local.DocumentEntity
import com.example.data.pdf.DocumentConverterEngine
import com.example.data.pdf.PdfCompressorEngine
import com.example.ui.PdfViewModel
import com.example.ui.theme.*

/**
 * Batch Processing Studio for running local bulk operations across multiple library PDFs:
 * - Batch Compress (with preset choice and live progress count)
 * - Batch Watermark (apply custom text watermark across all selected PDFs)
 * - Batch Rename (uniform <Prefix>_01 sequential rename synced with storage)
 */
@Composable
fun BatchProcessingTab(
    viewModel: PdfViewModel,
    documents: List<DocumentEntity>
) {
    val selectedIds = remember { mutableStateListOf<Long>() }
    var isProcessing by remember { mutableStateOf(false) }
    var progressLabel by remember { mutableStateOf("") }
    var selectedPreset by remember { mutableStateOf(PdfCompressorEngine.CompressionPreset.RECOMMENDED) }
    var watermarkText by remember { mutableStateOf("CONFIDENTIAL") }
    var renamePrefix by remember { mutableStateOf("BatchDoc") }
    var renameStart by remember { mutableStateOf("1") }

    val selectedDocs = remember(documents, selectedIds.toList()) {
        documents.filter { selectedIds.contains(it.id) }
    }

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
        contentPadding = PaddingValues(bottom = 32.dp)
    ) {
        item {
            Card(
                colors = CardDefaults.cardColors(containerColor = AcrobatDarkSurface),
                shape = RoundedCornerShape(14.dp),
                border = BorderStroke(1.dp, AcrobatDarkBorder),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text("Batch Processing Studio", fontWeight = FontWeight.Bold, color = AcrobatTextPrimary)
                            Text("Select multiple PDFs below (${selectedDocs.size} selected)", fontSize = 11.5.sp, color = AdfElectricCyan)
                        }
                        TextButton(onClick = {
                            if (selectedIds.size == documents.size) {
                                selectedIds.clear()
                            } else {
                                selectedIds.clear()
                                selectedIds.addAll(documents.map { it.id })
                            }
                        }) {
                            Text(if (selectedIds.size == documents.size && documents.isNotEmpty()) "Clear" else "Select All")
                        }
                    }

                    if (isProcessing && progressLabel.isNotBlank()) {
                        LinearProgressIndicator(modifier = Modifier.fillMaxWidth(), color = AdfElectricCyan)
                        Text(progressLabel, color = AdfElectricCyan, fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
                    }
                }
            }
        }

        // Batch Actions Panel
        item {
            Card(
                colors = CardDefaults.cardColors(containerColor = AcrobatDarkSurface),
                shape = RoundedCornerShape(14.dp),
                border = BorderStroke(1.dp, AcrobatDarkBorder),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Text("1. Batch Compress", fontWeight = FontWeight.Bold, color = AcrobatTextPrimary, fontSize = 14.sp)
                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        listOf(
                            PdfCompressorEngine.CompressionPreset.HIGH_QUALITY to "High Quality",
                            PdfCompressorEngine.CompressionPreset.RECOMMENDED to "Balanced",
                            PdfCompressorEngine.CompressionPreset.EXTREME to "Max Shrink"
                        ).forEach { (preset, label) ->
                            FilterChip(
                                selected = selectedPreset == preset,
                                onClick = { selectedPreset = preset },
                                label = { Text(label, fontSize = 11.sp) }
                            )
                        }
                    }
                    Button(
                        onClick = {
                            isProcessing = true
                            progressLabel = "Compressing 0/${selectedDocs.size}..."
                            viewModel.batchCompressDocuments(
                                docs = selectedDocs,
                                preset = selectedPreset,
                                onProgress = { done, total -> progressLabel = "Compressing $done/$total..." },
                                onDone = { succeeded ->
                                    isProcessing = false
                                    progressLabel = "Batch compressed $succeeded PDF(s)."
                                }
                            )
                        },
                        enabled = selectedDocs.isNotEmpty() && !isProcessing,
                        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF10B981), contentColor = Color.Black),
                        modifier = Modifier.fillMaxWidth().testTag("batch_compress_button")
                    ) {
                        Icon(Icons.Default.Compress, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("Batch Compress (${selectedDocs.size})", fontWeight = FontWeight.Bold)
                    }

                    HorizontalDivider(color = AcrobatDarkBorder)

                    Text("2. Batch Watermark", fontWeight = FontWeight.Bold, color = AcrobatTextPrimary, fontSize = 14.sp)
                    OutlinedTextField(
                        value = watermarkText,
                        onValueChange = { watermarkText = it },
                        label = { Text("Watermark Label") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                    Button(
                        onClick = {
                            isProcessing = true
                            progressLabel = "Watermarking 0/${selectedDocs.size}..."
                            viewModel.batchWatermarkDocuments(
                                docs = selectedDocs,
                                options = DocumentConverterEngine.WatermarkOptions(text = watermarkText),
                                onProgress = { done, total -> progressLabel = "Watermarking $done/$total..." },
                                onDone = { succeeded ->
                                    isProcessing = false
                                    progressLabel = "Stamped watermark on $succeeded PDF(s)."
                                }
                            )
                        },
                        enabled = selectedDocs.isNotEmpty() && watermarkText.isNotBlank() && !isProcessing,
                        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFA855F7), contentColor = Color.White),
                        modifier = Modifier.fillMaxWidth().testTag("batch_watermark_button")
                    ) {
                        Icon(Icons.Default.BrandingWatermark, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("Batch Watermark (${selectedDocs.size})", fontWeight = FontWeight.Bold)
                    }

                    HorizontalDivider(color = AcrobatDarkBorder)

                    Text("3. Batch Rename", fontWeight = FontWeight.Bold, color = AcrobatTextPrimary, fontSize = 14.sp)
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        OutlinedTextField(
                            value = renamePrefix,
                            onValueChange = { renamePrefix = it },
                            label = { Text("Prefix") },
                            singleLine = true,
                            modifier = Modifier.weight(2f)
                        )
                        OutlinedTextField(
                            value = renameStart,
                            onValueChange = { ch -> if (ch.all { it.isDigit() }) renameStart = ch },
                            label = { Text("Start #") },
                            singleLine = true,
                            modifier = Modifier.weight(1f)
                        )
                    }
                    Button(
                        onClick = {
                            isProcessing = true
                            val startNum = renameStart.toIntOrNull() ?: 1
                            viewModel.batchRenameDocuments(
                                docs = selectedDocs,
                                prefix = renamePrefix,
                                startNumber = startNum
                            ) { succeeded, failed ->
                                isProcessing = false
                                progressLabel = "Renamed $succeeded file(s)" + if (failed > 0) ", $failed skipped" else ""
                            }
                        },
                        enabled = selectedDocs.isNotEmpty() && renamePrefix.isNotBlank() && !isProcessing,
                        colors = ButtonDefaults.buttonColors(containerColor = AdfElectricCyan, contentColor = AdfMidnightBackground),
                        modifier = Modifier.fillMaxWidth().testTag("batch_rename_button")
                    ) {
                        Icon(Icons.Default.DriveFileRenameOutline, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("Batch Rename (${selectedDocs.size})", fontWeight = FontWeight.Bold)
                    }
                }
            }
        }

        // Document Multi-Select Checklist
        if (documents.isEmpty()) {
            item {
                Text("Your library has no PDF documents yet.", color = AcrobatTextMuted, fontSize = 13.sp)
            }
        } else {
            items(documents, key = { it.id }) { doc ->
                val checked = selectedIds.contains(doc.id)
                Surface(
                    onClick = { if (checked) selectedIds.remove(doc.id) else selectedIds.add(doc.id) },
                    shape = RoundedCornerShape(10.dp),
                    color = if (checked) AdfElectricCyan.copy(alpha = 0.14f) else AcrobatDarkSurface,
                    border = BorderStroke(1.dp, if (checked) AdfElectricCyan else AcrobatDarkBorder),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth().padding(horizontal = 10.dp, vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Checkbox(
                            checked = checked,
                            onCheckedChange = { c -> if (c) selectedIds.add(doc.id) else selectedIds.remove(doc.id) }
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = doc.title,
                                color = AcrobatTextPrimary,
                                fontWeight = FontWeight.SemiBold,
                                fontSize = 13.sp,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                            Text(
                                text = "${doc.pageCount} pages • ${doc.folderCategory}",
                                color = AcrobatTextSecondary,
                                fontSize = 11.sp
                            )
                        }
                    }
                }
            }
        }
    }
}

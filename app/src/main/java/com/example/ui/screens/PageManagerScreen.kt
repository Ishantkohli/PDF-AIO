package com.example.ui.screens

import androidx.activity.compose.BackHandler
import androidx.compose.animation.*
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.itemsIndexed
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.ui.AppScreen
import com.example.ui.PdfViewModel
import com.example.ui.theme.*

/**
 * Acrobat Page Manager Screen (Elite/Crazy Level)
 * Complete page organization studio: Reorder, Move to Top/End, Rotate (90/180/270),
 * Duplicate, Insert Blank/Notes Page, Multi-Select Batch Actions, Extract to new PDF,
 * 2/3 column layout toggle, and Instant Reset.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PageManagerScreen(viewModel: PdfViewModel) {
    val currentDoc by viewModel.currentDocument.collectAsStateWithLifecycle()
    val pageBitmaps by viewModel.pageBitmaps.collectAsStateWithLifecycle()

    val totalOriginalPages = currentDoc?.pageCount ?: 0

    // List of page indices in current layout.
    // -1 represents an inserted Blank Page, -2 represents an inserted Notes Page.
    val pageOrder = remember(currentDoc) {
        mutableStateListOf<Int>().apply {
            for (i in 0 until totalOriginalPages) add(i)
        }
    }

    // Map of position in pageOrder -> rotation angle (0, 90, 180, 270)
    val pageRotations = remember(currentDoc) {
        mutableStateMapOf<Int, Int>()
    }

    // Multi-select state
    val selectedPositions = remember { mutableStateListOf<Int>() }
    var isMultiSelectMode by remember { mutableStateOf(false) }

    // Grid layout: 2 columns vs 3 columns
    var gridColumns by remember { mutableIntStateOf(2) }

    var isSaving by remember { mutableStateOf(false) }
    var showExtractDialog by remember { mutableStateOf(false) }
    var extractTitle by remember { mutableStateOf("") }

    // Pre-render initial visible thumbnails batch smoothly
    LaunchedEffect(currentDoc) {
        val initialBatch = minOf(totalOriginalPages, 8)
        for (i in 0 until initialBatch) {
            viewModel.renderPage(i)
        }
    }

    BackHandler {
        viewModel.navigateTo(AppScreen.READER)
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text(
                            text = if (isMultiSelectMode) "${selectedPositions.size} Selected" else "Page Manager",
                            style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                            color = AcrobatTextPrimary
                        )
                        Text(
                            text = "${pageOrder.size} pages • ${currentDoc?.title ?: "Document"}",
                            style = MaterialTheme.typography.bodySmall,
                            color = AcrobatTextSecondary,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                },
                navigationIcon = {
                    IconButton(
                        onClick = {
                            if (isMultiSelectMode) {
                                isMultiSelectMode = false
                                selectedPositions.clear()
                            } else {
                                viewModel.navigateTo(AppScreen.READER)
                            }
                        },
                        modifier = Modifier.testTag("page_manager_back_button")
                    ) {
                        Icon(
                            imageVector = if (isMultiSelectMode) Icons.Default.Close else Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = "Back",
                            tint = AcrobatTextPrimary
                        )
                    }
                },
                actions = {
                    // Reset to Original Order & Rotations
                    IconButton(
                        onClick = {
                            pageOrder.clear()
                            for (i in 0 until totalOriginalPages) pageOrder.add(i)
                            pageRotations.clear()
                            selectedPositions.clear()
                            isMultiSelectMode = false
                        },
                        modifier = Modifier.testTag("reset_pages_button")
                    ) {
                        Icon(
                            imageVector = Icons.Default.RestartAlt,
                            contentDescription = "Reset Changes",
                            tint = AcrobatTextSecondary
                        )
                    }

                    // Multi-select mode toggle
                    IconButton(
                        onClick = {
                            isMultiSelectMode = !isMultiSelectMode
                            if (!isMultiSelectMode) selectedPositions.clear()
                        },
                        modifier = Modifier.testTag("multi_select_toggle_button")
                    ) {
                        Icon(
                            imageVector = if (isMultiSelectMode) Icons.Default.CheckCircle else Icons.Default.CheckCircleOutline,
                            contentDescription = "Multi Select",
                            tint = if (isMultiSelectMode) CrimsonAccent else AcrobatTextSecondary
                        )
                    }

                    // Save & Apply Button (Acrobat Crimson Signature Red Accent)
                    Button(
                        onClick = {
                            val doc = currentDoc ?: return@Button
                            if (pageOrder.isEmpty()) return@Button
                            isSaving = true
                            viewModel.organizeDocumentPages(
                                doc = doc,
                                includedIndices = pageOrder.toList(),
                                rotations = pageRotations.toMap(),
                                newTitle = "${doc.title}_organized"
                            ) {
                                isSaving = false
                            }
                        },
                        enabled = pageOrder.isNotEmpty() && !isSaving,
                        colors = ButtonDefaults.buttonColors(
                            containerColor = CrimsonPrimary,
                            contentColor = Color.White
                        ),
                        shape = RoundedCornerShape(12.dp),
                        contentPadding = PaddingValues(horizontal = 14.dp, vertical = 6.dp),
                        modifier = Modifier
                            .padding(end = 8.dp)
                            .testTag("save_organized_pages_button")
                    ) {
                        if (isSaving) {
                            CircularProgressIndicator(color = Color.White, modifier = Modifier.size(16.dp))
                        } else {
                            Icon(Icons.Default.Check, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(4.dp))
                            Text("Apply", fontWeight = FontWeight.Bold)
                        }
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = AcrobatDarkBackground
                )
            )
        },
        bottomBar = {
            // Acrobat Crimson Bottom Action Dock
            Surface(
                color = AcrobatDarkSurfaceElevated,
                border = CardDefaults.outlinedCardBorder().copy(brush = androidx.compose.ui.graphics.SolidColor(AcrobatDarkBorder)),
                modifier = Modifier
                    .fillMaxWidth()
                    .navigationBarsPadding()
            ) {
                Column(modifier = Modifier.padding(horizontal = 10.dp, vertical = 8.dp)) {
                    // Action Buttons Row
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        // Move to Top / Earlier
                        IconButton(
                            onClick = {
                                if (selectedPositions.size == 1) {
                                    val idx = selectedPositions.first()
                                    if (idx > 0) {
                                        val item = pageOrder.removeAt(idx)
                                        val rot = pageRotations.remove(item)
                                        pageOrder.add(idx - 1, item)
                                        if (rot != null) pageRotations[item] = rot
                                        selectedPositions.clear()
                                        selectedPositions.add(idx - 1)
                                    }
                                }
                            },
                            enabled = selectedPositions.size == 1 && selectedPositions.first() > 0,
                            modifier = Modifier
                                .size(38.dp)
                                .clip(CircleShape)
                                .background(if (selectedPositions.size == 1 && selectedPositions.first() > 0) CrimsonPrimary else AcrobatDarkSurfaceVariant)
                                .testTag("move_page_earlier_button")
                        ) {
                            Icon(
                                imageVector = Icons.Default.ArrowBack,
                                contentDescription = "Move Earlier",
                                tint = if (selectedPositions.size == 1 && selectedPositions.first() > 0) Color.White else AcrobatTextMuted,
                                modifier = Modifier.size(18.dp)
                            )
                        }

                        // Rotate 90° Button (Rotates selected or all)
                        Button(
                            onClick = {
                                if (selectedPositions.isNotEmpty()) {
                                    for (pos in selectedPositions) {
                                        val pageItem = pageOrder[pos]
                                        val cur = pageRotations[pageItem] ?: 0
                                        pageRotations[pageItem] = (cur + 90) % 360
                                    }
                                } else {
                                    for (pageItem in pageOrder) {
                                        val cur = pageRotations[pageItem] ?: 0
                                        pageRotations[pageItem] = (cur + 90) % 360
                                    }
                                }
                            },
                            colors = ButtonDefaults.buttonColors(
                                containerColor = CrimsonPrimary,
                                contentColor = Color.White
                            ),
                            shape = RoundedCornerShape(10.dp),
                            contentPadding = PaddingValues(horizontal = 10.dp, vertical = 6.dp),
                            modifier = Modifier.testTag("rotate_selected_button")
                        ) {
                            Icon(Icons.AutoMirrored.Filled.RotateRight, contentDescription = "Rotate", modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(4.dp))
                            Text(
                                text = if (selectedPositions.isNotEmpty()) "Rotate (${selectedPositions.size})" else "Rotate All 90°",
                                style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold)
                            )
                        }

                        // Duplicate Button
                        IconButton(
                            onClick = {
                                if (selectedPositions.size == 1) {
                                    val idx = selectedPositions.first()
                                    val item = pageOrder[idx]
                                    pageOrder.add(idx + 1, item)
                                    selectedPositions.clear()
                                    selectedPositions.add(idx + 1)
                                }
                            },
                            enabled = selectedPositions.size == 1,
                            modifier = Modifier
                                .size(38.dp)
                                .clip(CircleShape)
                                .background(if (selectedPositions.size == 1) CrimsonPrimary else AcrobatDarkSurfaceVariant)
                                .testTag("duplicate_page_button")
                        ) {
                            Icon(
                                imageVector = Icons.Default.ContentCopy,
                                contentDescription = "Duplicate Page",
                                tint = if (selectedPositions.size == 1) Color.White else AcrobatTextMuted,
                                modifier = Modifier.size(18.dp)
                            )
                        }

                        // Extract Selected to new PDF
                        IconButton(
                            onClick = {
                                if (selectedPositions.isNotEmpty()) {
                                    extractTitle = "${currentDoc?.title ?: "Doc"}_extracted"
                                    showExtractDialog = true
                                }
                            },
                            enabled = selectedPositions.isNotEmpty(),
                            modifier = Modifier
                                .size(38.dp)
                                .clip(CircleShape)
                                .background(if (selectedPositions.isNotEmpty()) CrimsonPrimary else AcrobatDarkSurfaceVariant)
                                .testTag("extract_pages_button")
                        ) {
                            Icon(
                                imageVector = Icons.Default.DriveFileMove,
                                contentDescription = "Extract to PDF",
                                tint = if (selectedPositions.isNotEmpty()) Color.White else AcrobatTextMuted,
                                modifier = Modifier.size(18.dp)
                            )
                        }

                        // Delete Selected Pages
                        IconButton(
                            onClick = {
                                if (selectedPositions.isNotEmpty() && pageOrder.size > selectedPositions.size) {
                                    val sortedDesc = selectedPositions.sortedDescending()
                                    for (pos in sortedDesc) {
                                        pageOrder.removeAt(pos)
                                    }
                                    selectedPositions.clear()
                                }
                            },
                            enabled = selectedPositions.isNotEmpty() && pageOrder.size > selectedPositions.size,
                            modifier = Modifier
                                .size(38.dp)
                                .clip(CircleShape)
                                .background(if (selectedPositions.isNotEmpty() && pageOrder.size > selectedPositions.size) Color(0xFFEF4444) else AcrobatDarkSurfaceVariant)
                                .testTag("delete_selected_pages_button")
                        ) {
                            Icon(
                                imageVector = Icons.Default.Delete,
                                contentDescription = "Delete Selected",
                                tint = if (selectedPositions.isNotEmpty() && pageOrder.size > selectedPositions.size) Color.White else AcrobatTextMuted,
                                modifier = Modifier.size(18.dp)
                            )
                        }

                        // Move Later
                        IconButton(
                            onClick = {
                                if (selectedPositions.size == 1) {
                                    val idx = selectedPositions.first()
                                    if (idx < pageOrder.size - 1) {
                                        val item = pageOrder.removeAt(idx)
                                        val rot = pageRotations.remove(item)
                                        pageOrder.add(idx + 1, item)
                                        if (rot != null) pageRotations[item] = rot
                                        selectedPositions.clear()
                                        selectedPositions.add(idx + 1)
                                    }
                                }
                            },
                            enabled = selectedPositions.size == 1 && selectedPositions.first() < pageOrder.size - 1,
                            modifier = Modifier
                                .size(38.dp)
                                .clip(CircleShape)
                                .background(if (selectedPositions.size == 1 && selectedPositions.first() < pageOrder.size - 1) CrimsonPrimary else AcrobatDarkSurfaceVariant)
                                .testTag("move_page_later_button")
                        ) {
                            Icon(
                                imageVector = Icons.Default.ArrowForward,
                                contentDescription = "Move Later",
                                tint = if (selectedPositions.size == 1 && selectedPositions.first() < pageOrder.size - 1) Color.White else AcrobatTextMuted,
                                modifier = Modifier.size(18.dp)
                            )
                        }
                    }
                }
            }
        },
        containerColor = AcrobatDarkBackground
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(horizontal = 14.dp)
        ) {
            // Quick Toolbar: Add blank page, Add notes page, Select All, Column Density Toggle
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 6.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.CenterVertically) {
                    // Insert Blank Page Chip
                    AssistChip(
                        onClick = {
                            val insertAt = if (selectedPositions.isNotEmpty()) selectedPositions.last() + 1 else pageOrder.size
                            pageOrder.add(insertAt, -1)
                        },
                        label = { Text("+ Blank", fontSize = 11.sp, fontWeight = FontWeight.SemiBold) },
                        leadingIcon = { Icon(Icons.Default.AddBox, contentDescription = null, modifier = Modifier.size(14.dp), tint = CrimsonAccent) },
                        colors = AssistChipDefaults.assistChipColors(containerColor = AcrobatDarkSurfaceElevated, labelColor = AcrobatTextPrimary),
                        border = BorderStroke(1.dp, AcrobatDarkBorder)
                    )

                    // Insert Notes Page Chip
                    AssistChip(
                        onClick = {
                            val insertAt = if (selectedPositions.isNotEmpty()) selectedPositions.last() + 1 else pageOrder.size
                            pageOrder.add(insertAt, -2)
                        },
                        label = { Text("+ Notes", fontSize = 11.sp, fontWeight = FontWeight.SemiBold) },
                        leadingIcon = { Icon(Icons.Default.NoteAdd, contentDescription = null, modifier = Modifier.size(14.dp), tint = CrimsonAccent) },
                        colors = AssistChipDefaults.assistChipColors(containerColor = AcrobatDarkSurfaceElevated, labelColor = AcrobatTextPrimary),
                        border = BorderStroke(1.dp, AcrobatDarkBorder)
                    )
                }

                Row(horizontalArrangement = Arrangement.spacedBy(4.dp), verticalAlignment = Alignment.CenterVertically) {
                    if (isMultiSelectMode) {
                        TextButton(
                            onClick = {
                                if (selectedPositions.size == pageOrder.size) {
                                    selectedPositions.clear()
                                } else {
                                    selectedPositions.clear()
                                    for (i in pageOrder.indices) selectedPositions.add(i)
                                }
                            },
                            contentPadding = PaddingValues(horizontal = 6.dp, vertical = 2.dp)
                        ) {
                            Text(
                                text = if (selectedPositions.size == pageOrder.size) "Deselect All" else "Select All",
                                color = CrimsonAccent,
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }

                    // Grid Density Toggle (2 vs 3 columns)
                    IconButton(
                        onClick = { gridColumns = if (gridColumns == 2) 3 else 2 },
                        modifier = Modifier.size(32.dp)
                    ) {
                        Icon(
                            imageVector = if (gridColumns == 2) Icons.Default.ViewModule else Icons.Default.GridView,
                            contentDescription = "Toggle Grid Columns",
                            tint = AcrobatTextSecondary,
                            modifier = Modifier.size(18.dp)
                        )
                    }
                }
            }

            // Grid of Pages
            LazyVerticalGrid(
                columns = GridCells.Fixed(gridColumns),
                horizontalArrangement = Arrangement.spacedBy(10.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp),
                contentPadding = PaddingValues(bottom = 24.dp),
                modifier = Modifier.fillMaxSize()
            ) {
                itemsIndexed(pageOrder) { currentPosition, originalIndex ->
                    val isSelected = selectedPositions.contains(currentPosition)
                    val rotation = pageRotations[originalIndex] ?: 0
                    val bitmap = if (originalIndex >= 0) pageBitmaps[originalIndex] else null

                    LaunchedEffect(originalIndex) {
                        if (originalIndex >= 0 && bitmap == null) {
                            viewModel.renderPage(originalIndex)
                        }
                    }

                    Card(
                        onClick = {
                            if (isMultiSelectMode) {
                                if (isSelected) selectedPositions.remove(currentPosition)
                                else selectedPositions.add(currentPosition)
                            } else {
                                if (isSelected) {
                                    selectedPositions.remove(currentPosition)
                                } else {
                                    selectedPositions.clear()
                                    selectedPositions.add(currentPosition)
                                }
                            }
                        },
                        colors = CardDefaults.cardColors(
                            containerColor = if (isSelected) AcrobatDarkSurfaceElevated else AcrobatDarkSurface
                        ),
                        shape = RoundedCornerShape(12.dp),
                        border = if (isSelected) {
                            CardDefaults.outlinedCardBorder().copy(
                                brush = androidx.compose.ui.graphics.SolidColor(CrimsonPrimary),
                                width = 2.5.dp
                            )
                        } else {
                            CardDefaults.outlinedCardBorder().copy(
                                brush = androidx.compose.ui.graphics.SolidColor(AcrobatDarkBorder)
                            )
                        },
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(if (gridColumns == 2) 220.dp else 170.dp)
                            .testTag("page_card_$currentPosition")
                    ) {
                        Column(modifier = Modifier.fillMaxSize()) {
                            // Page Thumbnail Canvas
                            Box(
                                modifier = Modifier
                                    .weight(1f)
                                    .fillMaxWidth()
                                    .background(PaperWhite)
                                    .padding(6.dp),
                                contentAlignment = Alignment.Center
                            ) {
                                when {
                                    originalIndex == -1 -> {
                                        // Inserted Blank Page
                                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                            Icon(Icons.Default.InsertDriveFile, contentDescription = null, tint = Color.LightGray, modifier = Modifier.size(32.dp))
                                            Spacer(modifier = Modifier.height(4.dp))
                                            Text("Blank Page", color = Color.Gray, fontSize = 10.sp, fontWeight = FontWeight.Bold)
                                        }
                                    }
                                    originalIndex == -2 -> {
                                        // Inserted Lined Notes Page
                                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                            Icon(Icons.Default.EditNote, contentDescription = null, tint = CrimsonPrimary, modifier = Modifier.size(32.dp))
                                            Spacer(modifier = Modifier.height(4.dp))
                                            Text("Meeting Notes", color = CrimsonPrimaryVariant, fontSize = 10.sp, fontWeight = FontWeight.Bold)
                                        }
                                    }
                                    bitmap != null -> {
                                        Image(
                                            bitmap = bitmap.asImageBitmap(),
                                            contentDescription = "Page ${currentPosition + 1}",
                                            modifier = Modifier
                                                .fillMaxSize()
                                                .graphicsLayer(rotationZ = rotation.toFloat())
                                        )
                                    }
                                    else -> {
                                        CircularProgressIndicator(
                                            color = CrimsonPrimary,
                                            modifier = Modifier.size(24.dp)
                                        )
                                    }
                                }

                                // Selection Checkmark Badge
                                if (isSelected) {
                                    Box(
                                        modifier = Modifier
                                            .align(Alignment.TopStart)
                                            .size(22.dp)
                                            .clip(CircleShape)
                                            .background(CrimsonPrimary),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        Icon(Icons.Default.Check, contentDescription = null, tint = Color.White, modifier = Modifier.size(14.dp))
                                    }
                                }

                                // Rotation badge
                                if (rotation != 0) {
                                    Box(
                                        modifier = Modifier
                                            .align(Alignment.TopEnd)
                                            .clip(RoundedCornerShape(4.dp))
                                            .background(CrimsonPrimary)
                                            .padding(horizontal = 4.dp, vertical = 2.dp)
                                    ) {
                                        Text(
                                            text = "⟳ $rotation°",
                                            color = Color.White,
                                            fontSize = 9.sp,
                                            fontWeight = FontWeight.Bold
                                        )
                                    }
                                }
                            }

                            // Footer Bar for Page
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .background(AcrobatDarkSurfaceVariant)
                                    .padding(horizontal = 6.dp, vertical = 4.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Column {
                                    Text(
                                        text = "Page ${currentPosition + 1}",
                                        style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
                                        color = if (isSelected) CrimsonAccent else AcrobatTextPrimary
                                    )
                                    Text(
                                        text = when (originalIndex) {
                                            -1 -> "Inserted Blank"
                                            -2 -> "Inserted Note"
                                            else -> "Orig #${originalIndex + 1}"
                                        },
                                        style = MaterialTheme.typography.bodySmall.copy(fontSize = 9.sp),
                                        color = AcrobatTextMuted
                                    )
                                }

                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    // Individual Rotate Button
                                    IconButton(
                                        onClick = {
                                            val cur = pageRotations[originalIndex] ?: 0
                                            pageRotations[originalIndex] = (cur + 90) % 360
                                        },
                                        modifier = Modifier.size(26.dp)
                                    ) {
                                        Icon(
                                            imageVector = Icons.AutoMirrored.Filled.RotateRight,
                                            contentDescription = "Rotate",
                                            tint = if (rotation != 0) CrimsonAccent else AcrobatTextSecondary,
                                            modifier = Modifier.size(14.dp)
                                        )
                                    }

                                    // Individual Delete Button
                                    IconButton(
                                        onClick = {
                                            if (pageOrder.size > 1) {
                                                pageOrder.removeAt(currentPosition)
                                                selectedPositions.remove(currentPosition)
                                            }
                                        },
                                        enabled = pageOrder.size > 1,
                                        modifier = Modifier.size(26.dp)
                                    ) {
                                        Icon(
                                            imageVector = Icons.Default.Delete,
                                            contentDescription = "Delete Page",
                                            tint = if (pageOrder.size > 1) CrimsonPrimary else AcrobatTextMuted,
                                            modifier = Modifier.size(14.dp)
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    // Extract Selected Pages Dialog
    if (showExtractDialog) {
        AlertDialog(
            onDismissRequest = { showExtractDialog = false },
            title = {
                Text(
                    text = "Extract ${selectedPositions.size} Pages to New PDF",
                    style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                    color = AcrobatTextPrimary
                )
            },
            text = {
                Column {
                    Text(
                        text = "Create a separate standalone PDF document containing only the selected pages:",
                        style = MaterialTheme.typography.bodyMedium,
                        color = AcrobatTextSecondary
                    )
                    Spacer(modifier = Modifier.height(12.dp))
                    OutlinedTextField(
                        value = extractTitle,
                        onValueChange = { extractTitle = it },
                        label = { Text("New Document Name") },
                        singleLine = true,
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = CrimsonPrimary,
                            cursorColor = CrimsonPrimary
                        ),
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        val doc = currentDoc ?: return@Button
                        val indicesToExtract = selectedPositions.sorted().map { pageOrder[it] }
                        showExtractDialog = false
                        viewModel.extractPagesToNewDocument(
                            doc = doc,
                            includedIndices = indicesToExtract,
                            rotations = pageRotations.toMap(),
                            newTitle = extractTitle
                        ) {
                            // Handled in ViewModel
                        }
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = CrimsonPrimary)
                ) {
                    Text("Extract Now", fontWeight = FontWeight.Bold)
                }
            },
            dismissButton = {
                TextButton(onClick = { showExtractDialog = false }) {
                    Text("Cancel", color = AcrobatTextSecondary)
                }
            },
            containerColor = AcrobatDarkSurfaceElevated
        )
    }
}

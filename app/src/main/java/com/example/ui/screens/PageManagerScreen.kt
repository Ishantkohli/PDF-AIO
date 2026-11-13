package com.example.ui.screens

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.ArrowForward
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
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.ui.AppScreen
import com.example.ui.PdfViewModel
import com.example.ui.theme.*

/**
 * Unique identity per arranged page slot so duplicating, rotating, moving,
 * inserting blank/notes pages, and batch-deleting never collide on originalIndex.
 */
private data class ManagedPageItem(
    val uid: Long,
    val originalIndex: Int, // -1 = Blank Page, -2 = Notes Page
    val rotationDegrees: Int = 0
)

/**
 * PDF (AIO) Page Manager Screen
 * Page organization studio: Reorder, Move Earlier/Later, Rotate (90/180/270),
 * Duplicate, Insert Blank/Notes Page, Multi-Select Batch Actions, Extract to new PDF,
 * 2/3 column layout toggle, and Instant Reset.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PageManagerScreen(viewModel: PdfViewModel) {
    val currentDoc by viewModel.currentDocument.collectAsStateWithLifecycle()
    val pageBitmaps by viewModel.pageBitmaps.collectAsStateWithLifecycle()

    val totalOriginalPages = currentDoc?.pageCount ?: 0

    var nextUid by remember(currentDoc) { mutableLongStateOf(totalOriginalPages.toLong() + 1L) }

    val pageItems = remember(currentDoc) {
        mutableStateListOf<ManagedPageItem>().apply {
            for (i in 0 until totalOriginalPages) {
                add(ManagedPageItem(uid = i.toLong(), originalIndex = i, rotationDegrees = 0))
            }
        }
    }

    val selectedUids = remember(currentDoc) { mutableStateListOf<Long>() }
    var isMultiSelectMode by remember(currentDoc) { mutableStateOf(false) }

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

    val selectedPositionSingle: Int? = if (selectedUids.size == 1) {
        pageItems.indexOfFirst { it.uid == selectedUids.first() }.takeIf { it >= 0 }
    } else null

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text(
                            text = if (isMultiSelectMode) "${selectedUids.size} Selected" else "Page Manager",
                            style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                            color = AcrobatTextPrimary
                        )
                        Text(
                            text = "${pageItems.size} pages • ${currentDoc?.title ?: "Document"}",
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
                                selectedUids.clear()
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
                    IconButton(
                        onClick = {
                            pageItems.clear()
                            for (i in 0 until totalOriginalPages) {
                                pageItems.add(ManagedPageItem(uid = nextUid++, originalIndex = i, rotationDegrees = 0))
                            }
                            selectedUids.clear()
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

                    IconButton(
                        onClick = {
                            isMultiSelectMode = !isMultiSelectMode
                            if (!isMultiSelectMode) selectedUids.clear()
                        },
                        modifier = Modifier.testTag("multi_select_toggle_button")
                    ) {
                        Icon(
                            imageVector = if (isMultiSelectMode) Icons.Default.CheckCircle else Icons.Default.CheckCircleOutline,
                            contentDescription = "Multi Select",
                            tint = if (isMultiSelectMode) CrimsonAccent else AcrobatTextSecondary
                        )
                    }

                    Button(
                        onClick = {
                            val doc = currentDoc ?: return@Button
                            if (pageItems.isEmpty()) return@Button
                            isSaving = true
                            viewModel.organizeDocumentPagesWithRotations(
                                doc = doc,
                                orderedPages = pageItems.map { it.originalIndex to it.rotationDegrees },
                                newTitle = "${doc.title}_organized"
                            ) {
                                isSaving = false
                            }
                        },
                        enabled = pageItems.isNotEmpty() && !isSaving,
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
            Surface(
                color = AcrobatDarkSurfaceElevated,
                border = CardDefaults.outlinedCardBorder().copy(brush = androidx.compose.ui.graphics.SolidColor(AcrobatDarkBorder)),
                modifier = Modifier
                    .fillMaxWidth()
                    .navigationBarsPadding()
            ) {
                Column(modifier = Modifier.padding(horizontal = 10.dp, vertical = 8.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        val canMoveEarlier = selectedPositionSingle != null && selectedPositionSingle > 0
                        IconButton(
                            onClick = {
                                val idx = selectedPositionSingle ?: return@IconButton
                                if (idx > 0) {
                                    val item = pageItems.removeAt(idx)
                                    pageItems.add(idx - 1, item)
                                }
                            },
                            enabled = canMoveEarlier,
                            modifier = Modifier
                                .size(38.dp)
                                .clip(CircleShape)
                                .background(if (canMoveEarlier) CrimsonPrimary else AcrobatDarkSurfaceVariant)
                                .testTag("move_page_earlier_button")
                        ) {
                            Icon(
                                imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                                contentDescription = "Move Earlier",
                                tint = if (canMoveEarlier) Color.White else AcrobatTextMuted,
                                modifier = Modifier.size(18.dp)
                            )
                        }

                        Button(
                            onClick = {
                                val uidSet = selectedUids.toSet()
                                for (i in pageItems.indices) {
                                    val item = pageItems[i]
                                    if (uidSet.isEmpty() || uidSet.contains(item.uid)) {
                                        pageItems[i] = item.copy(rotationDegrees = (item.rotationDegrees + 90) % 360)
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
                                text = if (selectedUids.isNotEmpty()) "Rotate (${selectedUids.size})" else "Rotate All 90°",
                                style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold)
                            )
                        }

                        IconButton(
                            onClick = {
                                val idx = selectedPositionSingle ?: return@IconButton
                                val item = pageItems[idx]
                                val copyItem = item.copy(uid = nextUid++)
                                pageItems.add(idx + 1, copyItem)
                                selectedUids.clear()
                                selectedUids.add(copyItem.uid)
                            },
                            enabled = selectedPositionSingle != null,
                            modifier = Modifier
                                .size(38.dp)
                                .clip(CircleShape)
                                .background(if (selectedPositionSingle != null) CrimsonPrimary else AcrobatDarkSurfaceVariant)
                                .testTag("duplicate_page_button")
                        ) {
                            Icon(
                                imageVector = Icons.Default.ContentCopy,
                                contentDescription = "Duplicate Page",
                                tint = if (selectedPositionSingle != null) Color.White else AcrobatTextMuted,
                                modifier = Modifier.size(18.dp)
                            )
                        }

                        IconButton(
                            onClick = {
                                if (selectedUids.isNotEmpty()) {
                                    extractTitle = "${currentDoc?.title ?: "Doc"}_extracted"
                                    showExtractDialog = true
                                }
                            },
                            enabled = selectedUids.isNotEmpty(),
                            modifier = Modifier
                                .size(38.dp)
                                .clip(CircleShape)
                                .background(if (selectedUids.isNotEmpty()) CrimsonPrimary else AcrobatDarkSurfaceVariant)
                                .testTag("extract_pages_button")
                        ) {
                            Icon(
                                imageVector = Icons.Default.DriveFileMove,
                                contentDescription = "Extract to PDF",
                                tint = if (selectedUids.isNotEmpty()) Color.White else AcrobatTextMuted,
                                modifier = Modifier.size(18.dp)
                            )
                        }

                        val canDeleteSelected = selectedUids.isNotEmpty() && pageItems.size > selectedUids.size
                        IconButton(
                            onClick = {
                                if (canDeleteSelected) {
                                    val toRemove = selectedUids.toSet()
                                    pageItems.removeAll { toRemove.contains(it.uid) }
                                    selectedUids.clear()
                                }
                            },
                            enabled = canDeleteSelected,
                            modifier = Modifier
                                .size(38.dp)
                                .clip(CircleShape)
                                .background(if (canDeleteSelected) Color(0xFFEF4444) else AcrobatDarkSurfaceVariant)
                                .testTag("delete_selected_pages_button")
                        ) {
                            Icon(
                                imageVector = Icons.Default.Delete,
                                contentDescription = "Delete Selected",
                                tint = if (canDeleteSelected) Color.White else AcrobatTextMuted,
                                modifier = Modifier.size(18.dp)
                            )
                        }

                        val canMoveLater = selectedPositionSingle != null && selectedPositionSingle < pageItems.size - 1
                        IconButton(
                            onClick = {
                                val idx = selectedPositionSingle ?: return@IconButton
                                if (idx < pageItems.size - 1) {
                                    val item = pageItems.removeAt(idx)
                                    pageItems.add(idx + 1, item)
                                }
                            },
                            enabled = canMoveLater,
                            modifier = Modifier
                                .size(38.dp)
                                .clip(CircleShape)
                                .background(if (canMoveLater) CrimsonPrimary else AcrobatDarkSurfaceVariant)
                                .testTag("move_page_later_button")
                        ) {
                            Icon(
                                imageVector = Icons.AutoMirrored.Filled.ArrowForward,
                                contentDescription = "Move Later",
                                tint = if (canMoveLater) Color.White else AcrobatTextMuted,
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
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 6.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.CenterVertically) {
                    AssistChip(
                        onClick = {
                            val lastSelPos = selectedUids.lastOrNull()?.let { uid -> pageItems.indexOfFirst { it.uid == uid } }
                            val insertAt = if (lastSelPos != null && lastSelPos >= 0) (lastSelPos + 1).coerceAtMost(pageItems.size) else pageItems.size
                            pageItems.add(insertAt, ManagedPageItem(uid = nextUid++, originalIndex = -1, rotationDegrees = 0))
                        },
                        label = { Text("+ Blank", fontSize = 11.sp, fontWeight = FontWeight.SemiBold) },
                        leadingIcon = { Icon(Icons.Default.AddBox, contentDescription = null, modifier = Modifier.size(14.dp), tint = CrimsonAccent) },
                        colors = AssistChipDefaults.assistChipColors(containerColor = AcrobatDarkSurfaceElevated, labelColor = AcrobatTextPrimary),
                        border = BorderStroke(1.dp, AcrobatDarkBorder)
                    )

                    AssistChip(
                        onClick = {
                            val lastSelPos = selectedUids.lastOrNull()?.let { uid -> pageItems.indexOfFirst { it.uid == uid } }
                            val insertAt = if (lastSelPos != null && lastSelPos >= 0) (lastSelPos + 1).coerceAtMost(pageItems.size) else pageItems.size
                            pageItems.add(insertAt, ManagedPageItem(uid = nextUid++, originalIndex = -2, rotationDegrees = 0))
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
                                if (selectedUids.size == pageItems.size) {
                                    selectedUids.clear()
                                } else {
                                    selectedUids.clear()
                                    pageItems.forEach { selectedUids.add(it.uid) }
                                }
                            },
                            contentPadding = PaddingValues(horizontal = 6.dp, vertical = 2.dp)
                        ) {
                            Text(
                                text = if (selectedUids.size == pageItems.size) "Deselect All" else "Select All",
                                color = CrimsonAccent,
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }

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

            LazyVerticalGrid(
                columns = GridCells.Fixed(gridColumns),
                horizontalArrangement = Arrangement.spacedBy(10.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp),
                contentPadding = PaddingValues(bottom = 24.dp),
                modifier = Modifier.fillMaxSize()
            ) {
                itemsIndexed(pageItems, key = { _, item -> item.uid }) { currentPosition, pageItem ->
                    val originalIndex = pageItem.originalIndex
                    val rotation = pageItem.rotationDegrees
                    val isSelected = selectedUids.contains(pageItem.uid)
                    val bitmap = if (originalIndex >= 0) pageBitmaps[originalIndex] else null

                    LaunchedEffect(originalIndex) {
                        if (originalIndex >= 0 && bitmap == null) {
                            viewModel.renderPage(originalIndex)
                        }
                    }

                    Card(
                        onClick = {
                            if (isMultiSelectMode) {
                                if (isSelected) selectedUids.remove(pageItem.uid)
                                else selectedUids.add(pageItem.uid)
                            } else {
                                if (isSelected) {
                                    selectedUids.remove(pageItem.uid)
                                } else {
                                    selectedUids.clear()
                                    selectedUids.add(pageItem.uid)
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
                                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                            Icon(Icons.Default.InsertDriveFile, contentDescription = null, tint = Color.LightGray, modifier = Modifier.size(32.dp))
                                            Spacer(modifier = Modifier.height(4.dp))
                                            Text("Blank Page", color = Color.Gray, fontSize = 10.sp, fontWeight = FontWeight.Bold)
                                        }
                                    }
                                    originalIndex == -2 -> {
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
                                    IconButton(
                                        onClick = {
                                            pageItems[currentPosition] = pageItem.copy(rotationDegrees = (rotation + 90) % 360)
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

                                    IconButton(
                                        onClick = {
                                            if (pageItems.size > 1) {
                                                pageItems.removeAt(currentPosition)
                                                selectedUids.remove(pageItem.uid)
                                            }
                                        },
                                        enabled = pageItems.size > 1,
                                        modifier = Modifier.size(26.dp)
                                    ) {
                                        Icon(
                                            imageVector = Icons.Default.Delete,
                                            contentDescription = "Delete Page",
                                            tint = if (pageItems.size > 1) CrimsonPrimary else AcrobatTextMuted,
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

    if (showExtractDialog) {
        AlertDialog(
            onDismissRequest = { showExtractDialog = false },
            title = {
                Text(
                    text = "Extract ${selectedUids.size} Pages to New PDF",
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
                        val selSet = selectedUids.toSet()
                        val orderedSelected = pageItems
                            .filter { selSet.contains(it.uid) }
                            .map { it.originalIndex to it.rotationDegrees }
                        showExtractDialog = false
                        viewModel.extractPagesWithRotations(
                            doc = doc,
                            orderedPages = orderedSelected,
                            newTitle = extractTitle
                        ) {}
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

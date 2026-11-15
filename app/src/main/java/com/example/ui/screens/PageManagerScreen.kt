package com.example.ui.screens

import android.net.Uri
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectDragGesturesAfterLongPress
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.itemsIndexed
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
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
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.zIndex
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.data.pdf.PdfExportEngine
import com.example.ui.AppScreen
import com.example.ui.PdfViewModel
import com.example.ui.theme.*

/**
 * Unique identity per arranged page slot so duplicating, rotating, replacing,
 * inserting blank/notes pages, drag-reordering, and batch-deleting never collide.
 */
private data class ManagedPageItem(
    val uid: Long,
    val originalIndex: Int, // -1 = Blank Page, -2 = Notes Page, -3 = Replaced Slot
    val rotationDegrees: Int = 0,
    val replacementImageUri: String? = null,
    val replacementPdfUri: String? = null,
    val replacementLabel: String? = null
)

/**
 * PDF (AIO) Page Manager Screen — Organizer 2.0
 * Long-press drag-and-drop reorder, Move Earlier/Later, Rotate (90/180/270),
 * Duplicate, Replace Page (Image or PDF page), Split PDF (Ranges or Every N Pages),
 * Duplicate-Page Detection, Insert Blank/Notes Page, Multi-Select Batch Actions,
 * Extract to new PDF, 2/3 column grid, and Instant Reset.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PageManagerScreen(viewModel: PdfViewModel) {
    val currentDoc by viewModel.currentDocument.collectAsStateWithLifecycle()
    val pageBitmaps by viewModel.pageBitmaps.collectAsStateWithLifecycle()
    val haptic = LocalHapticFeedback.current

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
    var gridColumns by remember { mutableIntStateOf(2) }

    var isSaving by remember { mutableStateOf(false) }
    var showExtractDialog by remember { mutableStateOf(false) }
    var extractTitle by remember { mutableStateOf("") }

    // Organizer 2.0 state: Split, Replace Page, Duplicate Detection, Drag-Reorder
    var showSplitDialog by remember { mutableStateOf(false) }
    var showReplaceDialog by remember { mutableStateOf(false) }
    var targetReplaceUid by remember { mutableStateOf<Long?>(null) }

    var isScanningDuplicates by remember { mutableStateOf(false) }
    var duplicateMatches by remember(currentDoc) { mutableStateOf<List<PdfExportEngine.DuplicatePageMatch>>(emptyList()) }
    var showDuplicateDialog by remember { mutableStateOf(false) }

    val gridState = rememberLazyGridState()
    var draggedUid by remember { mutableStateOf<Long?>(null) }
    var dragOffset by remember { mutableStateOf(Offset.Zero) }

    val replaceImageLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.PickVisualMedia()
    ) { uri: Uri? ->
        val uid = targetReplaceUid ?: return@rememberLauncherForActivityResult
        if (uri != null) {
            val idx = pageItems.indexOfFirst { it.uid == uid }
            if (idx >= 0) {
                pageItems[idx] = pageItems[idx].copy(
                    originalIndex = -3,
                    replacementImageUri = uri.toString(),
                    replacementPdfUri = null,
                    replacementLabel = "Image Page"
                )
            }
        }
    }

    val replacePdfLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument()
    ) { uri: Uri? ->
        val uid = targetReplaceUid ?: return@rememberLauncherForActivityResult
        if (uri != null) {
            val idx = pageItems.indexOfFirst { it.uid == uid }
            if (idx >= 0) {
                pageItems[idx] = pageItems[idx].copy(
                    originalIndex = -3,
                    replacementImageUri = null,
                    replacementPdfUri = uri.toString(),
                    replacementLabel = "PDF Page #1"
                )
            }
        }
    }

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

    val duplicateTargetIndices = remember(duplicateMatches) {
        duplicateMatches.map { it.secondPageIndex }.toSet()
    }
    val allDuplicateInvolvedIndices = remember(duplicateMatches) {
        duplicateMatches.flatMap { listOf(it.firstPageIndex, it.secondPageIndex) }.toSet()
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text(
                            text = if (isMultiSelectMode) "${selectedUids.size} Selected" else "Organizer 2.0",
                            style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                            color = AcrobatTextPrimary
                        )
                        Text(
                            text = "${pageItems.size} pages • Drag & hold any card to reorder",
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
                            duplicateMatches = emptyList()
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
                            val hasReplacements = pageItems.any {
                                !it.replacementImageUri.isNullOrBlank() || !it.replacementPdfUri.isNullOrBlank()
                            }
                            if (hasReplacements) {
                                viewModel.organizeDocumentPagesAdvanced(
                                    doc = doc,
                                    slots = pageItems.map {
                                        PdfExportEngine.PageSlotSpec(
                                            originalIndex = it.originalIndex,
                                            rotationDegrees = it.rotationDegrees,
                                            replacementImageUri = it.replacementImageUri,
                                            replacementPdfUri = it.replacementPdfUri,
                                            replacementPdfPageIndex = 0
                                        )
                                    },
                                    newTitle = "${doc.title}_organized"
                                ) {
                                    isSaving = false
                                }
                            } else {
                                viewModel.organizeDocumentPagesWithRotations(
                                    doc = doc,
                                    orderedPages = pageItems.map { it.originalIndex to it.rotationDegrees },
                                    newTitle = "${doc.title}_organized"
                                ) {
                                    isSaving = false
                                }
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
                                text = if (selectedUids.isNotEmpty()) "Rotate (${selectedUids.size})" else "Rotate All",
                                style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold)
                            )
                        }

                        // Replace Page Button
                        IconButton(
                            onClick = {
                                val idx = selectedPositionSingle ?: return@IconButton
                                targetReplaceUid = pageItems[idx].uid
                                showReplaceDialog = true
                            },
                            enabled = selectedPositionSingle != null,
                            modifier = Modifier
                                .size(38.dp)
                                .clip(CircleShape)
                                .background(if (selectedPositionSingle != null) Color(0xFF0EA5E9) else AcrobatDarkSurfaceVariant)
                                .testTag("replace_page_button")
                        ) {
                            Icon(
                                imageVector = Icons.Default.FindReplace,
                                contentDescription = "Replace Page",
                                tint = if (selectedPositionSingle != null) Color.White else AcrobatTextMuted,
                                modifier = Modifier.size(18.dp)
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
            // Organizer 2.0 Action Chips Row
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
                        colors = AssistChipDefaults.assistChipColors(containerColor = AcrobatDarkSurfaceElevated, labelColor = AcrobatTextPrimary),
                        border = BorderStroke(1.dp, AcrobatDarkBorder),
                        modifier = Modifier.testTag("insert_blank_page_chip")
                    )

                    AssistChip(
                        onClick = {
                            val lastSelPos = selectedUids.lastOrNull()?.let { uid -> pageItems.indexOfFirst { it.uid == uid } }
                            val insertAt = if (lastSelPos != null && lastSelPos >= 0) (lastSelPos + 1).coerceAtMost(pageItems.size) else pageItems.size
                            pageItems.add(insertAt, ManagedPageItem(uid = nextUid++, originalIndex = -2, rotationDegrees = 0))
                        },
                        label = { Text("+ Notes", fontSize = 11.sp, fontWeight = FontWeight.SemiBold) },
                        colors = AssistChipDefaults.assistChipColors(containerColor = AcrobatDarkSurfaceElevated, labelColor = AcrobatTextPrimary),
                        border = BorderStroke(1.dp, AcrobatDarkBorder),
                        modifier = Modifier.testTag("insert_notes_page_chip")
                    )

                    AssistChip(
                        onClick = { showSplitDialog = true },
                        enabled = totalOriginalPages >= 2,
                        label = { Text("Split", fontSize = 11.sp, fontWeight = FontWeight.SemiBold) },
                        leadingIcon = { Icon(Icons.Default.CallSplit, contentDescription = null, modifier = Modifier.size(14.dp), tint = Color(0xFF38BDF8)) },
                        colors = AssistChipDefaults.assistChipColors(containerColor = AcrobatDarkSurfaceElevated, labelColor = Color(0xFF38BDF8)),
                        border = BorderStroke(1.dp, Color(0xFF38BDF8).copy(alpha = 0.5f)),
                        modifier = Modifier.testTag("split_pdf_chip")
                    )

                    AssistChip(
                        onClick = {
                            val doc = currentDoc ?: return@AssistChip
                            isScanningDuplicates = true
                            viewModel.detectDuplicatePages(doc) { matches ->
                                isScanningDuplicates = false
                                duplicateMatches = matches
                                if (matches.isNotEmpty()) {
                                    showDuplicateDialog = true
                                }
                            }
                        },
                        enabled = totalOriginalPages >= 2 && !isScanningDuplicates,
                        label = {
                            Text(
                                if (isScanningDuplicates) "..." else if (duplicateMatches.isNotEmpty()) "Dups (${duplicateMatches.size})" else "Duplicates",
                                fontSize = 11.sp,
                                fontWeight = FontWeight.SemiBold
                            )
                        },
                        colors = AssistChipDefaults.assistChipColors(
                            containerColor = if (duplicateMatches.isNotEmpty()) Color(0xFFF59E0B).copy(alpha = 0.2f) else AcrobatDarkSurfaceElevated,
                            labelColor = if (duplicateMatches.isNotEmpty()) Color(0xFFFBBF24) else AcrobatTextPrimary
                        ),
                        border = BorderStroke(1.dp, if (duplicateMatches.isNotEmpty()) Color(0xFFF59E0B) else AcrobatDarkBorder),
                        modifier = Modifier.testTag("find_duplicates_chip")
                    )
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

            if (isMultiSelectMode) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(bottom = 4.dp),
                    horizontalArrangement = Arrangement.End
                ) {
                    TextButton(
                        onClick = {
                            if (selectedUids.size == pageItems.size) {
                                selectedUids.clear()
                            } else {
                                selectedUids.clear()
                                pageItems.forEach { selectedUids.add(it.uid) }
                            }
                        },
                        contentPadding = PaddingValues(horizontal = 8.dp, vertical = 2.dp)
                    ) {
                        Text(
                            text = if (selectedUids.size == pageItems.size) "Deselect All" else "Select All (${pageItems.size})",
                            color = CrimsonAccent,
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }
            }

            // Grid with genuine long-press drag-and-drop card reordering
            LazyVerticalGrid(
                state = gridState,
                columns = GridCells.Fixed(gridColumns),
                horizontalArrangement = Arrangement.spacedBy(10.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp),
                contentPadding = PaddingValues(bottom = 24.dp),
                modifier = Modifier
                    .fillMaxSize()
                    .pointerInput(pageItems.size, gridColumns) {
                        detectDragGesturesAfterLongPress(
                            onDragStart = { startOffset ->
                                val hitItem = gridState.layoutInfo.visibleItemsInfo.firstOrNull { info ->
                                    startOffset.x >= info.offset.x &&
                                        startOffset.x <= info.offset.x + info.size.width &&
                                        startOffset.y >= info.offset.y &&
                                        startOffset.y <= info.offset.y + info.size.height
                                }
                                if (hitItem != null && hitItem.index in pageItems.indices) {
                                    draggedUid = pageItems[hitItem.index].uid
                                    dragOffset = Offset.Zero
                                    haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                                }
                            },
                            onDrag = { change, dragAmount ->
                                val activeUid = draggedUid ?: return@detectDragGesturesAfterLongPress
                                change.consume()
                                dragOffset += dragAmount
                                val currentIdx = pageItems.indexOfFirst { it.uid == activeUid }
                                if (currentIdx < 0) return@detectDragGesturesAfterLongPress
                                val draggedInfo = gridState.layoutInfo.visibleItemsInfo.firstOrNull { it.index == currentIdx }
                                    ?: return@detectDragGesturesAfterLongPress

                                val centerX = draggedInfo.offset.x + draggedInfo.size.width / 2f + dragOffset.x
                                val centerY = draggedInfo.offset.y + draggedInfo.size.height / 2f + dragOffset.y

                                val targetInfo = gridState.layoutInfo.visibleItemsInfo.firstOrNull { info ->
                                    info.index != currentIdx &&
                                        info.index in pageItems.indices &&
                                        centerX >= info.offset.x &&
                                        centerX <= info.offset.x + info.size.width &&
                                        centerY >= info.offset.y &&
                                        centerY <= info.offset.y + info.size.height
                                }
                                if (targetInfo != null) {
                                    val moving = pageItems.removeAt(currentIdx)
                                    pageItems.add(targetInfo.index, moving)
                                    dragOffset = Offset(
                                        x = dragOffset.x + (draggedInfo.offset.x - targetInfo.offset.x),
                                        y = dragOffset.y + (draggedInfo.offset.y - targetInfo.offset.y)
                                    )
                                }
                            },
                            onDragEnd = {
                                draggedUid = null
                                dragOffset = Offset.Zero
                            },
                            onDragCancel = {
                                draggedUid = null
                                dragOffset = Offset.Zero
                            }
                        )
                    }
            ) {
                itemsIndexed(pageItems, key = { _, item -> item.uid }) { currentPosition, pageItem ->
                    val originalIndex = pageItem.originalIndex
                    val rotation = pageItem.rotationDegrees
                    val isSelected = selectedUids.contains(pageItem.uid)
                    val isDraggingThis = draggedUid == pageItem.uid
                    val isSuspectedDuplicate = originalIndex >= 0 && allDuplicateInvolvedIndices.contains(originalIndex)
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
                        border = when {
                            isDraggingThis -> CardDefaults.outlinedCardBorder().copy(
                                brush = androidx.compose.ui.graphics.SolidColor(Color(0xFF38BDF8)),
                                width = 3.dp
                            )
                            isSelected -> CardDefaults.outlinedCardBorder().copy(
                                brush = androidx.compose.ui.graphics.SolidColor(CrimsonPrimary),
                                width = 2.5.dp
                            )
                            isSuspectedDuplicate -> CardDefaults.outlinedCardBorder().copy(
                                brush = androidx.compose.ui.graphics.SolidColor(Color(0xFFF59E0B)),
                                width = 2.dp
                            )
                            else -> CardDefaults.outlinedCardBorder().copy(
                                brush = androidx.compose.ui.graphics.SolidColor(AcrobatDarkBorder)
                            )
                        },
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(if (gridColumns == 2) 220.dp else 170.dp)
                            .zIndex(if (isDraggingThis) 10f else 0f)
                            .graphicsLayer {
                                if (isDraggingThis) {
                                    translationX = dragOffset.x
                                    translationY = dragOffset.y
                                    scaleX = 1.05f
                                    scaleY = 1.05f
                                }
                            }
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
                                    originalIndex == -3 -> {
                                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                            Icon(Icons.Default.FindReplace, contentDescription = null, tint = Color(0xFF0EA5E9), modifier = Modifier.size(32.dp))
                                            Spacer(modifier = Modifier.height(4.dp))
                                            Text(
                                                pageItem.replacementLabel ?: "Replaced Page",
                                                color = Color(0xFF0369A1),
                                                fontSize = 10.sp,
                                                fontWeight = FontWeight.Bold
                                            )
                                        }
                                    }
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
                                } else if (isSuspectedDuplicate) {
                                    Box(
                                        modifier = Modifier
                                            .align(Alignment.TopStart)
                                            .clip(RoundedCornerShape(4.dp))
                                            .background(Color(0xFFF59E0B))
                                            .padding(horizontal = 4.dp, vertical = 2.dp)
                                    ) {
                                        Text(
                                            text = if (duplicateTargetIndices.contains(originalIndex)) "DUP COPY" else "HAS DUP",
                                            color = Color.Black,
                                            fontSize = 8.sp,
                                            fontWeight = FontWeight.ExtraBold
                                        )
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
                                            -3 -> pageItem.replacementLabel ?: "Replaced"
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

    // Extract Pages Dialog
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
                            .filter { selSet.contains(it.uid) && it.originalIndex != -3 }
                            .map { it.originalIndex to it.rotationDegrees }
                        showExtractDialog = false
                        if (orderedSelected.isNotEmpty()) {
                            viewModel.extractPagesWithRotations(
                                doc = doc,
                                orderedPages = orderedSelected,
                                newTitle = extractTitle
                            ) {}
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

    // Split PDF Dialog
    if (showSplitDialog) {
        var splitByRanges by remember { mutableStateOf(false) }
        var pagesPerPartText by remember { mutableStateOf("1") }
        var rangeSpecText by remember { mutableStateOf("1-${(totalOriginalPages / 2).coerceAtLeast(1)}, ${(totalOriginalPages / 2 + 1).coerceAtMost(totalOriginalPages)}-$totalOriginalPages") }
        var splitBaseName by remember { mutableStateOf("${currentDoc?.title ?: "Doc"}_split") }

        AlertDialog(
            onDismissRequest = { showSplitDialog = false },
            containerColor = AcrobatDarkSurfaceElevated,
            title = {
                Text("Split PDF Document", fontWeight = FontWeight.Bold, color = AcrobatTextPrimary)
            },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text(
                        "Total pages: $totalOriginalPages. Splits are saved as separate PDF files in your library.",
                        style = MaterialTheme.typography.bodySmall,
                        color = AcrobatTextSecondary
                    )
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        FilterChip(
                            selected = !splitByRanges,
                            onClick = { splitByRanges = false },
                            label = { Text("Every N Pages") }
                        )
                        FilterChip(
                            selected = splitByRanges,
                            onClick = { splitByRanges = true },
                            label = { Text("Custom Ranges") }
                        )
                    }
                    if (!splitByRanges) {
                        OutlinedTextField(
                            value = pagesPerPartText,
                            onValueChange = { pagesPerPartText = it.filter { ch -> ch.isDigit() } },
                            label = { Text("Pages per split file") },
                            singleLine = true,
                            modifier = Modifier.fillMaxWidth()
                        )
                    } else {
                        OutlinedTextField(
                            value = rangeSpecText,
                            onValueChange = { rangeSpecText = it },
                            label = { Text("Ranges (e.g. 1-3, 4-6)") },
                            singleLine = true,
                            modifier = Modifier.fillMaxWidth()
                        )
                    }
                    OutlinedTextField(
                        value = splitBaseName,
                        onValueChange = { splitBaseName = it },
                        label = { Text("Output Base Filename") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        val doc = currentDoc ?: return@Button
                        val groups = if (splitByRanges) {
                            PdfExportEngine.parseSplitRanges(rangeSpecText, totalOriginalPages)
                        } else {
                            val chunk = (pagesPerPartText.toIntOrNull() ?: 1).coerceIn(1, totalOriginalPages.coerceAtLeast(1))
                            PdfExportEngine.buildChunkSplitGroups(totalOriginalPages, chunk)
                        }
                        if (groups.isNotEmpty()) {
                            showSplitDialog = false
                            viewModel.splitPdfDocument(doc, groups, splitBaseName)
                        }
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF0EA5E9))
                ) {
                    Text("Split PDF", fontWeight = FontWeight.Bold)
                }
            },
            dismissButton = {
                TextButton(onClick = { showSplitDialog = false }) {
                    Text("Cancel", color = AcrobatTextSecondary)
                }
            }
        )
    }

    // Replace Page Source Chooser Dialog
    if (showReplaceDialog) {
        AlertDialog(
            onDismissRequest = { showReplaceDialog = false },
            containerColor = AcrobatDarkSurfaceElevated,
            title = { Text("Replace Selected Page", fontWeight = FontWeight.Bold, color = AcrobatTextPrimary) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text(
                        "Choose an image from your gallery or another PDF file's first page to replace this page slot, then tap Apply.",
                        style = MaterialTheme.typography.bodySmall,
                        color = AcrobatTextSecondary
                    )
                    Button(
                        onClick = {
                            showReplaceDialog = false
                            replaceImageLauncher.launch(
                                PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)
                            )
                        },
                        modifier = Modifier.fillMaxWidth(),
                        colors = ButtonDefaults.buttonColors(containerColor = CrimsonPrimary)
                    ) {
                        Icon(Icons.Default.Image, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("Replace with Image")
                    }
                    OutlinedButton(
                        onClick = {
                            showReplaceDialog = false
                            replacePdfLauncher.launch(arrayOf("application/pdf"))
                        },
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Icon(Icons.Default.PictureAsPdf, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("Replace with PDF Page #1")
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { showReplaceDialog = false }) {
                    Text("Close")
                }
            }
        )
    }

    // Duplicate Page Matches Dialog
    if (showDuplicateDialog && duplicateMatches.isNotEmpty()) {
        AlertDialog(
            onDismissRequest = { showDuplicateDialog = false },
            containerColor = AcrobatDarkSurfaceElevated,
            title = {
                Text("Suspected Duplicate Pages", fontWeight = FontWeight.Bold, color = AcrobatTextPrimary)
            },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    duplicateMatches.take(8).forEach { m ->
                        Text(
                            text = "• Page ${m.firstPageIndex + 1} ≈ Page ${m.secondPageIndex + 1} (${m.similarityPercent}% — ${m.reason})",
                            style = MaterialTheme.typography.bodySmall,
                            color = AcrobatTextSecondary
                        )
                    }
                    if (duplicateMatches.size > 8) {
                        Text("...and ${duplicateMatches.size - 8} more pairs.", style = MaterialTheme.typography.labelSmall, color = AcrobatTextMuted)
                    }
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        val removeTargets = duplicateTargetIndices
                        val remaining = pageItems.filterNot { it.originalIndex in removeTargets }
                        if (remaining.isNotEmpty()) {
                            pageItems.clear()
                            pageItems.addAll(remaining)
                            selectedUids.clear()
                        }
                        showDuplicateDialog = false
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFF59E0B), contentColor = Color.Black)
                ) {
                    Text("Remove Extra Copies", fontWeight = FontWeight.Bold)
                }
            },
            dismissButton = {
                TextButton(onClick = { showDuplicateDialog = false }) {
                    Text("Keep All", color = AcrobatTextSecondary)
                }
            }
        )
    }
}


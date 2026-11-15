package com.example.ui.screens.reader

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.local.BookmarkEntity
import com.example.data.local.DocumentEntity
import com.example.data.local.StorageRenameCoordinator
import com.example.data.pdf.ShareCoordinator
import com.example.ui.theme.*
import kotlin.math.min

/**
 * Computes the exact rendered page bounds inside a container for [ContentScale.Fit],
 * guaranteeing normalized coordinates [0..1] align across aspect ratios, zoom, crop, and rotation.
 */
fun computeFittedPageRect(
    containerSize: IntSize,
    bitmapWidth: Int,
    bitmapHeight: Int
): Rect {
    val cw = containerSize.width.toFloat().coerceAtLeast(1f)
    val ch = containerSize.height.toFloat().coerceAtLeast(1f)
    val bw = bitmapWidth.toFloat().coerceAtLeast(1f)
    val bh = bitmapHeight.toFloat().coerceAtLeast(1f)
    val scaleFactor = min(cw / bw, ch / bh)
    val fittedW = bw * scaleFactor
    val fittedH = bh * scaleFactor
    val left = (cw - fittedW) * 0.5f
    val top = (ch - fittedH) * 0.5f
    return Rect(left, top, left + fittedW, top + fittedH)
}

fun pointerToFittedNormalized(offset: Offset, fittedRect: Rect): Offset {
    val w = fittedRect.width.coerceAtLeast(1f)
    val h = fittedRect.height.coerceAtLeast(1f)
    return Offset(
        x = ((offset.x - fittedRect.left) / w).coerceIn(0f, 1f),
        y = ((offset.y - fittedRect.top) / h).coerceIn(0f, 1f)
    )
}

@Composable
fun SelectionFloatingActionBar(
    selectedText: String,
    expandStageLabel: String,
    onCopy: () -> Unit,
    onExpandSelection: () -> Unit,
    onSelectAll: () -> Unit,
    onUnderlineSelection: () -> Unit,
    onStrikeSelection: () -> Unit,
    onSearch: () -> Unit,
    onShareText: () -> Unit,
    onClear: () -> Unit,
    modifier: Modifier = Modifier
) {
    Surface(
        shape = RoundedCornerShape(24.dp),
        color = Color(0xF50F172A),
        shadowElevation = 10.dp,
        border = androidx.compose.foundation.BorderStroke(1.dp, AdfElectricCyan.copy(alpha = 0.65f)),
        modifier = modifier
            .padding(horizontal = 12.dp)
            .testTag("selection_action_bar")
    ) {
        Column(modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp)) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.padding(horizontal = 4.dp, vertical = 2.dp)
            ) {
                Text(
                    text = "${selectedText.length} chars selected",
                    color = AdfElectricCyan,
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Bold
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = selectedText.replace("\n", " "),
                    color = AcrobatTextSecondary,
                    fontSize = 11.sp,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f)
                )
            }
            Row(
                modifier = Modifier.horizontalScroll(rememberScrollState()),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(2.dp)
            ) {
                SelectionActionChip(
                    icon = Icons.Default.ContentCopy,
                    label = "Copy",
                    tint = Color(0xFF10B981),
                    tag = "selection_copy_button",
                    onClick = onCopy
                )
                SelectionActionChip(
                    icon = Icons.Default.UnfoldMore,
                    label = expandStageLabel,
                    tint = AdfElectricCyan,
                    tag = "selection_expand_button",
                    onClick = onExpandSelection
                )
                SelectionActionChip(
                    icon = Icons.Default.SelectAll,
                    label = "All",
                    tint = AcrobatTextPrimary,
                    tag = "selection_all_button",
                    onClick = onSelectAll
                )
                SelectionActionChip(
                    icon = Icons.Default.FormatUnderlined,
                    label = "Underline",
                    tint = Color(0xFF60A5FA),
                    tag = "selection_underline_button",
                    onClick = onUnderlineSelection
                )
                SelectionActionChip(
                    icon = Icons.Default.FormatStrikethrough,
                    label = "Strike",
                    tint = Color(0xFFF472B6),
                    tag = "selection_strike_button",
                    onClick = onStrikeSelection
                )
                SelectionActionChip(
                    icon = Icons.Default.Search,
                    label = "Search",
                    tint = Color(0xFFF59E0B),
                    tag = "selection_search_button",
                    onClick = onSearch
                )
                SelectionActionChip(
                    icon = Icons.Default.Share,
                    label = "Share",
                    tint = Color(0xFFA855F7),
                    tag = "selection_share_button",
                    onClick = onShareText
                )
                IconButton(
                    onClick = onClear,
                    modifier = Modifier
                        .size(40.dp)
                        .testTag("selection_clear_button")
                ) {
                    Icon(
                        imageVector = Icons.Default.Close,
                        contentDescription = "Clear text selection",
                        tint = AcrobatTextMuted,
                        modifier = Modifier.size(18.dp)
                    )
                }
            }
        }
    }
}

@Composable
private fun SelectionActionChip(
    icon: ImageVector,
    label: String,
    tint: Color,
    tag: String,
    onClick: () -> Unit
) {
    Surface(
        shape = RoundedCornerShape(14.dp),
        color = tint.copy(alpha = 0.14f),
        modifier = Modifier
            .minimumInteractiveComponentSize()
            .clip(RoundedCornerShape(14.dp))
            .clickable(onClick = onClick)
            .testTag(tag)
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 7.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(icon, contentDescription = label, tint = tint, modifier = Modifier.size(15.dp))
            Spacer(modifier = Modifier.width(4.dp))
            Text(text = label, color = Color.White, fontSize = 11.sp, fontWeight = FontWeight.SemiBold)
        }
    }
}

@Composable
fun RenameDocumentDialog(
    currentTitle: String,
    isRenaming: Boolean,
    externalError: String?,
    onDismiss: () -> Unit,
    onConfirmRename: (String) -> Unit
) {
    var inputName by remember(currentTitle) {
        mutableStateOf(currentTitle.removeSuffix(".pdf").removeSuffix(".PDF"))
    }
    var localError by remember(externalError) { mutableStateOf(externalError) }

    val validation = remember(inputName) {
        StorageRenameCoordinator.validateFileName(inputName)
    }

    AlertDialog(
        onDismissRequest = { if (!isRenaming) onDismiss() },
        containerColor = AcrobatDarkSurfaceElevated,
        modifier = Modifier.testTag("rename_file_dialog"),
        title = {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    imageVector = Icons.Default.DriveFileRenameOutline,
                    contentDescription = null,
                    tint = AdfElectricCyan,
                    modifier = Modifier.size(22.dp)
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text("Rename PDF File", color = AcrobatTextPrimary, fontWeight = FontWeight.Bold)
            }
        },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text(
                    text = "Renames the PDF in storage and updates your library, bookmarks, annotations, and Share output.",
                    color = AcrobatTextSecondary,
                    fontSize = 12.sp
                )
                OutlinedTextField(
                    value = inputName,
                    onValueChange = {
                        inputName = it
                        localError = null
                    },
                    singleLine = true,
                    enabled = !isRenaming,
                    label = { Text("Filename") },
                    suffix = { Text(".pdf", color = AcrobatTextMuted) },
                    isError = localError != null || (inputName.isNotEmpty() && validation is StorageRenameCoordinator.ValidationResult.Invalid),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedTextColor = AcrobatTextPrimary,
                        unfocusedTextColor = AcrobatTextPrimary,
                        focusedBorderColor = AdfElectricCyan,
                        unfocusedBorderColor = AcrobatDarkBorder
                    ),
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("rename_input_field")
                )
                val displayError = localError
                    ?: (validation as? StorageRenameCoordinator.ValidationResult.Invalid)?.reason
                if (!displayError.isNullOrBlank()) {
                    Text(
                        text = displayError,
                        color = Color(0xFFEF4444),
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Medium,
                        modifier = Modifier.testTag("rename_error_text")
                    )
                } else if (validation is StorageRenameCoordinator.ValidationResult.Valid) {
                    Text(
                        text = "Output: ${validation.fullFileName}",
                        color = Color(0xFF10B981),
                        fontSize = 12.sp
                    )
                }
            }
        },
        confirmButton = {
            Button(
                onClick = { onConfirmRename(inputName) },
                enabled = !isRenaming && validation is StorageRenameCoordinator.ValidationResult.Valid,
                colors = ButtonDefaults.buttonColors(containerColor = CrimsonPrimary),
                modifier = Modifier.testTag("rename_confirm_button")
            ) {
                if (isRenaming) {
                    CircularProgressIndicator(color = Color.White, modifier = Modifier.size(16.dp))
                } else {
                    Text("Rename", fontWeight = FontWeight.Bold)
                }
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss, enabled = !isRenaming) {
                Text("Cancel", color = AcrobatTextSecondary)
            }
        }
    )
}

@Composable
fun ProviderDeniedSaveCopyDialog(
    reason: String,
    suggestedFileName: String,
    onDismiss: () -> Unit,
    onSaveCopyAs: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = AcrobatDarkSurfaceElevated,
        modifier = Modifier.testTag("provider_denied_dialog"),
        icon = {
            Icon(
                imageVector = Icons.Default.WarningAmber,
                contentDescription = null,
                tint = Color(0xFFF59E0B),
                modifier = Modifier.size(30.dp)
            )
        },
        title = {
            Text("Direct Storage Rename Restricted", color = AcrobatTextPrimary, fontWeight = FontWeight.Bold)
        },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(text = reason, color = AcrobatTextSecondary, fontSize = 13.sp)
                Text(
                    text = "Save a renamed copy (\"$suggestedFileName\") directly to your chosen folder instead?",
                    color = AcrobatTextPrimary,
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Medium
                )
            }
        },
        confirmButton = {
            Button(
                onClick = onSaveCopyAs,
                colors = ButtonDefaults.buttonColors(containerColor = CrimsonPrimary),
                modifier = Modifier.testTag("rename_save_copy_as_button")
            ) {
                Text("Save Copy As...", fontWeight = FontWeight.Bold)
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel", color = AcrobatTextSecondary)
            }
        }
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ShareOptionsBottomSheet(
    doc: DocumentEntity,
    annotationCount: Int,
    hasWatermark: Boolean,
    isPreparingShare: Boolean,
    onDismiss: () -> Unit,
    onSelectOption: (ShareCoordinator.ShareOption, customName: String?) -> Unit
) {
    var showCustomInput by remember { mutableStateOf(false) }
    var customFileName by remember(doc.id, doc.title) {
        mutableStateOf(doc.title.removeSuffix(".pdf").removeSuffix(".PDF"))
    }
    val customValidation = remember(customFileName) {
        StorageRenameCoordinator.validateFileName(customFileName)
    }

    ModalBottomSheet(
        onDismissRequest = { if (!isPreparingShare) onDismiss() },
        containerColor = AcrobatDarkSurfaceElevated,
        modifier = Modifier.testTag("share_options_sheet")
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp)
                .padding(bottom = 28.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Default.Share, contentDescription = null, tint = AdfElectricCyan)
                Spacer(modifier = Modifier.width(10.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = "Share Document",
                        style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                        color = AcrobatTextPrimary
                    )
                    Text(
                        text = buildString {
                            append("${doc.title}.pdf")
                            if (annotationCount > 0 || hasWatermark) {
                                append(" • Includes ")
                                if (annotationCount > 0) append("$annotationCount annotations")
                                if (annotationCount > 0 && hasWatermark) append(" & ")
                                if (hasWatermark) append("watermark")
                            }
                        },
                        color = AcrobatTextSecondary,
                        fontSize = 12.sp,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
                if (isPreparingShare) {
                    CircularProgressIndicator(color = AdfElectricCyan, modifier = Modifier.size(22.dp))
                }
            }

            HorizontalDivider(color = AcrobatDarkBorder)

            ShareOptionCard(
                icon = Icons.Default.PictureAsPdf,
                accentColor = CrimsonPrimary,
                title = "1. Share as PDF (With Filename)",
                subtitle = "Shares \"${doc.title}.pdf\" with all annotations and watermarks preserved.",
                tag = "share_opt_pdf_original",
                enabled = !isPreparingShare,
                onClick = { onSelectOption(ShareCoordinator.ShareOption.PDF_ORIGINAL_NAME, null) }
            )

            ShareOptionCard(
                icon = Icons.Default.Description,
                accentColor = Color(0xFF38BDF8),
                title = "2. Share as TXT",
                subtitle = "Extracts readable text to \"${doc.title}.txt\". Images, scans & layout formatting are omitted.",
                tag = "share_opt_txt",
                enabled = !isPreparingShare,
                onClick = { onSelectOption(ShareCoordinator.ShareOption.TXT_PLAIN, null) }
            )

            ShareOptionCard(
                icon = Icons.Default.Article,
                accentColor = Color(0xFF10B981),
                title = "3. Share as DOCX",
                subtitle = "Exports \"${doc.title}.docx\" with editable paragraphs. Complex multi-column layouts may simplify.",
                tag = "share_opt_docx",
                enabled = !isPreparingShare,
                onClick = { onSelectOption(ShareCoordinator.ShareOption.DOCX_WORD, null) }
            )

            ShareOptionCard(
                icon = Icons.Default.DriveFileRenameOutline,
                accentColor = Color(0xFFA855F7),
                title = "4. Share as PDF With Custom Filename",
                subtitle = "Enter a custom filename before opening Android share chooser.",
                tag = "share_opt_pdf_custom",
                enabled = !isPreparingShare,
                onClick = { showCustomInput = !showCustomInput }
            )

            if (showCustomInput) {
                Surface(
                    shape = RoundedCornerShape(14.dp),
                    color = AcrobatDarkSurface,
                    border = androidx.compose.foundation.BorderStroke(1.dp, Color(0xFFA855F7).copy(alpha = 0.5f)),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(
                        modifier = Modifier.padding(12.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        OutlinedTextField(
                            value = customFileName,
                            onValueChange = { customFileName = it },
                            singleLine = true,
                            label = { Text("Custom PDF Filename") },
                            suffix = { Text(".pdf", color = AcrobatTextMuted) },
                            isError = customValidation is StorageRenameCoordinator.ValidationResult.Invalid,
                            colors = OutlinedTextFieldDefaults.colors(
                                focusedTextColor = AcrobatTextPrimary,
                                unfocusedTextColor = AcrobatTextPrimary,
                                focusedBorderColor = Color(0xFFA855F7),
                                unfocusedBorderColor = AcrobatDarkBorder
                            ),
                            modifier = Modifier
                                .fillMaxWidth()
                                .testTag("share_custom_filename_input")
                        )
                        if (customValidation is StorageRenameCoordinator.ValidationResult.Invalid) {
                            Text(
                                text = customValidation.reason,
                                color = Color(0xFFEF4444),
                                fontSize = 11.sp
                            )
                        }
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.End
                        ) {
                            Button(
                                onClick = {
                                    onSelectOption(ShareCoordinator.ShareOption.PDF_CUSTOM_NAME, customFileName)
                                },
                                enabled = !isPreparingShare && customValidation is StorageRenameCoordinator.ValidationResult.Valid,
                                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFA855F7)),
                                modifier = Modifier.testTag("share_custom_confirm_button")
                            ) {
                                Text("Share Custom PDF", fontWeight = FontWeight.Bold)
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun ShareOptionCard(
    icon: ImageVector,
    accentColor: Color,
    title: String,
    subtitle: String,
    tag: String,
    enabled: Boolean,
    onClick: () -> Unit
) {
    Surface(
        onClick = onClick,
        enabled = enabled,
        shape = RoundedCornerShape(14.dp),
        color = AcrobatDarkSurface,
        border = androidx.compose.foundation.BorderStroke(1.dp, AcrobatDarkBorder),
        modifier = Modifier
            .fillMaxWidth()
            .minimumInteractiveComponentSize()
            .testTag(tag)
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 14.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .size(40.dp)
                    .clip(CircleShape)
                    .background(accentColor.copy(alpha = 0.16f)),
                contentAlignment = Alignment.Center
            ) {
                Icon(icon, contentDescription = null, tint = accentColor, modifier = Modifier.size(20.dp))
            }
            Spacer(modifier = Modifier.width(12.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = title,
                    color = AcrobatTextPrimary,
                    fontWeight = FontWeight.Bold,
                    fontSize = 13.5.sp
                )
                Spacer(modifier = Modifier.height(2.dp))
                Text(
                    text = subtitle,
                    color = AcrobatTextSecondary,
                    fontSize = 11.5.sp,
                    lineHeight = 15.sp
                )
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun BookmarksBottomSheet(
    currentPage: Int,
    bookmarks: List<BookmarkEntity>,
    onDismiss: () -> Unit,
    onAddBookmark: (pageIndex: Int, label: String) -> Unit,
    onSelectBookmark: (Int) -> Unit,
    onDeleteBookmark: (Long) -> Unit
) {
    var newLabel by remember(currentPage) { mutableStateOf("Page ${currentPage + 1}") }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        containerColor = AcrobatDarkSurfaceElevated,
        modifier = Modifier.testTag("bookmarks_sheet")
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 18.dp)
                .padding(bottom = 28.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Default.Bookmarks, contentDescription = null, tint = Color(0xFFF59E0B))
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = "Document Bookmarks (${bookmarks.size})",
                    style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                    color = AcrobatTextPrimary
                )
            }

            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                OutlinedTextField(
                    value = newLabel,
                    onValueChange = { newLabel = it },
                    singleLine = true,
                    label = { Text("Bookmark Page ${currentPage + 1}") },
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedTextColor = AcrobatTextPrimary,
                        unfocusedTextColor = AcrobatTextPrimary,
                        focusedBorderColor = Color(0xFFF59E0B),
                        unfocusedBorderColor = AcrobatDarkBorder
                    ),
                    modifier = Modifier.weight(1f)
                )
                Button(
                    onClick = { onAddBookmark(currentPage, newLabel) },
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFF59E0B), contentColor = Color.Black),
                    modifier = Modifier.minimumInteractiveComponentSize()
                ) {
                    Text("Add", fontWeight = FontWeight.Bold)
                }
            }

            if (bookmarks.isEmpty()) {
                Text(
                    text = "No bookmarks yet. Add one above to jump quickly across sections.",
                    color = AcrobatTextMuted,
                    fontSize = 12.5.sp,
                    modifier = Modifier.padding(vertical = 8.dp)
                )
            } else {
                LazyColumn(
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(max = 280.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    items(bookmarks, key = { it.id }) { bm ->
                        Surface(
                            onClick = {
                                onSelectBookmark(bm.pageIndex)
                                onDismiss()
                            },
                            shape = RoundedCornerShape(12.dp),
                            color = AcrobatDarkSurface,
                            border = androidx.compose.foundation.BorderStroke(1.dp, AcrobatDarkBorder),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Row(
                                modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Icon(Icons.Default.Bookmark, contentDescription = null, tint = Color(0xFFF59E0B), modifier = Modifier.size(18.dp))
                                Spacer(modifier = Modifier.width(10.dp))
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(bm.label, color = AcrobatTextPrimary, fontWeight = FontWeight.SemiBold, fontSize = 13.sp)
                                    Text("Page ${bm.pageIndex + 1}", color = AcrobatTextSecondary, fontSize = 11.sp)
                                }
                                IconButton(
                                    onClick = { onDeleteBookmark(bm.id) },
                                    modifier = Modifier.size(40.dp)
                                ) {
                                    Icon(Icons.Default.DeleteOutline, contentDescription = "Remove bookmark", tint = Color(0xFFEF4444), modifier = Modifier.size(18.dp))
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

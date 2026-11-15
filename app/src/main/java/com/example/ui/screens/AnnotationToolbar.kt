package com.example.ui.screens

import androidx.compose.animation.*
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.BrandingWatermark
import androidx.compose.material.icons.automirrored.filled.Comment
import androidx.compose.material.icons.automirrored.filled.Redo
import androidx.compose.material.icons.automirrored.filled.RotateRight
import androidx.compose.material.icons.automirrored.filled.Undo
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.ui.AnnotationTool
import com.example.ui.PdfViewModel
import com.example.ui.theme.*
import kotlin.math.roundToInt

@Composable
fun AnnotationToolbar(
    viewModel: PdfViewModel,
    activeTool: AnnotationTool,
    activeColor: Long,
    onSelectStamp: (String) -> Unit,
    onSelectSignature: () -> Unit,
    onOpenWatermarkDialog: () -> Unit = {},
    onClearPageAnnotations: () -> Unit,
    onDeleteCurrentPage: () -> Unit = {},
    onOrganizePages: () -> Unit = {},
    onRotatePage: () -> Unit = {},
    modifier: Modifier = Modifier
) {
    var showColorPicker by remember { mutableStateOf(false) }
    var showStampMenu by remember { mutableStateOf(false) }
    val scrollState = rememberScrollState()
    val canUndo by viewModel.canUndo.collectAsStateWithLifecycle()
    val canRedo by viewModel.canRedo.collectAsStateWithLifecycle()
    val activeOpacity by viewModel.activeOpacity.collectAsStateWithLifecycle()

    val supportsColorPalette = activeTool == AnnotationTool.PEN ||
        activeTool == AnnotationTool.HIGHLIGHT ||
        activeTool == AnnotationTool.UNDERLINE ||
        activeTool == AnnotationTool.STRIKETHROUGH

    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 12.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        // Sub-palette for Pen / Highlighter / Underline / Strikethrough Colors, Widths & Opacity
        AnimatedVisibility(
            visible = showColorPicker && supportsColorPalette,
            enter = fadeIn() + expandVertically(),
            exit = fadeOut() + shrinkVertically()
        ) {
            Surface(
                shape = RoundedCornerShape(16.dp),
                color = AcrobatDarkSurfaceElevated,
                shadowElevation = 8.dp,
                border = CardDefaults.outlinedCardBorder().copy(brush = androidx.compose.ui.graphics.SolidColor(AcrobatDarkBorder)),
                modifier = Modifier.padding(bottom = 8.dp)
            ) {
                Column(
                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Row(
                        modifier = Modifier.horizontalScroll(rememberScrollState()),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        val colors: List<Pair<Long, String>> = if (activeTool == AnnotationTool.HIGHLIGHT) {
                            listOf(
                                0x66FDE047L to "Yellow",
                                0x6686EFACL to "Green",
                                0x66F472B6L to "Pink",
                                0x6667E8F9L to "Cyan",
                                0x66C084FCL to "Violet"
                            )
                        } else {
                            listOf(
                                0xFFDC2626L to "Crimson Red",
                                0xFF2563EBL to "Royal Blue",
                                0xFF0F172AL to "Obsidian Black",
                                0xFF059669L to "Emerald Green",
                                0xFFF59E0BL to "Amber",
                                0xFF9333EAL to "Purple"
                            )
                        }

                        colors.forEach { (colorVal, _) ->
                            Box(
                                modifier = Modifier
                                    .size(28.dp)
                                    .clip(CircleShape)
                                    .background(Color(colorVal))
                                    .border(
                                        width = if (activeColor == colorVal) 2.5.dp else 1.dp,
                                        color = if (activeColor == colorVal) Color.White else Color.Transparent,
                                        shape = CircleShape
                                    )
                                    .clickable {
                                        viewModel.setActiveColor(colorVal)
                                    }
                            )
                        }

                        Spacer(modifier = Modifier.width(6.dp))
                        Box(modifier = Modifier.width(1.dp).height(20.dp).background(AcrobatDarkBorder))
                        Spacer(modifier = Modifier.width(6.dp))

                        // Stroke width choices
                        listOf(2f to "Fine", 4f to "Medium", 8f to "Thick").forEach { (width, label) ->
                            TextButton(
                                onClick = {
                                    viewModel.setActiveStrokeWidth(width)
                                },
                                contentPadding = PaddingValues(horizontal = 8.dp, vertical = 2.dp)
                            ) {
                                Text(
                                    text = label,
                                    style = MaterialTheme.typography.labelSmall,
                                    color = if (viewModel.activeStrokeWidth.value == width) CrimsonAccent else AcrobatTextSecondary,
                                    fontWeight = if (viewModel.activeStrokeWidth.value == width) FontWeight.Bold else FontWeight.Normal
                                )
                            }
                        }
                    }

                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Text(
                            text = "Opacity ${(activeOpacity * 100).roundToInt()}%",
                            style = MaterialTheme.typography.labelSmall,
                            color = AcrobatTextSecondary,
                            modifier = Modifier.widthIn(min = 82.dp)
                        )
                        Slider(
                            value = activeOpacity,
                            onValueChange = { viewModel.setActiveOpacity(it) },
                            valueRange = 0.25f..1.0f,
                            colors = SliderDefaults.colors(
                                thumbColor = CrimsonAccent,
                                activeTrackColor = CrimsonAccent
                            ),
                            modifier = Modifier
                                .weight(1f)
                                .height(26.dp)
                        )
                    }
                }
            }
        }

        // Primary Floating Slideable Pill Toolbar
        Surface(
            shape = RoundedCornerShape(24.dp),
            color = AcrobatDarkSurfaceElevated,
            shadowElevation = 10.dp,
            border = CardDefaults.outlinedCardBorder().copy(brush = androidx.compose.ui.graphics.SolidColor(AcrobatDarkBorder)),
            modifier = Modifier
                .fillMaxWidth()
                .shadow(8.dp, RoundedCornerShape(24.dp))
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .horizontalScroll(scrollState)
                    .padding(horizontal = 8.dp, vertical = 6.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                // Undo
                ToolIconButton(
                    icon = Icons.AutoMirrored.Filled.Undo,
                    label = "Undo",
                    isSelected = false,
                    enabled = canUndo,
                    selectedColor = AdfElectricCyan,
                    customTestTag = "tool_undo",
                    onClick = { viewModel.undoLastAnnotation() }
                )

                // Redo
                ToolIconButton(
                    icon = Icons.AutoMirrored.Filled.Redo,
                    label = "Redo",
                    isSelected = false,
                    enabled = canRedo,
                    selectedColor = AdfElectricCyan,
                    customTestTag = "tool_redo",
                    onClick = { viewModel.redoLastAnnotation() }
                )

                // Select Text Tool
                ToolIconButton(
                    icon = Icons.Default.SelectAll,
                    label = "Select Text",
                    isSelected = activeTool == AnnotationTool.TEXT_SELECT,
                    selectedColor = Color(0xFF38BDF8),
                    customTestTag = "tool_select_text",
                    onClick = {
                        viewModel.setActiveTool(AnnotationTool.TEXT_SELECT)
                        showColorPicker = false
                    }
                )

                // Freehand Pen
                ToolIconButton(
                    icon = Icons.Default.Edit,
                    label = "Pen",
                    isSelected = activeTool == AnnotationTool.PEN,
                    selectedColor = CrimsonPrimary,
                    onClick = {
                        if (activeTool == AnnotationTool.PEN) {
                            showColorPicker = !showColorPicker
                        } else {
                            viewModel.setActiveTool(AnnotationTool.PEN)
                            viewModel.setActiveColor(0xFFDC2626)
                            viewModel.setActiveStrokeWidth(4f)
                            showColorPicker = true
                        }
                    }
                )

                // Highlighter
                ToolIconButton(
                    icon = Icons.Default.BorderColor,
                    label = "Highlight",
                    isSelected = activeTool == AnnotationTool.HIGHLIGHT,
                    selectedColor = Color(0xFFFBBF24),
                    onClick = {
                        if (activeTool == AnnotationTool.HIGHLIGHT) {
                            showColorPicker = !showColorPicker
                        } else {
                            viewModel.setActiveTool(AnnotationTool.HIGHLIGHT)
                            viewModel.setActiveColor(0x66FDE047)
                            viewModel.setActiveStrokeWidth(16f)
                            showColorPicker = true
                        }
                    }
                )

                // Underline
                ToolIconButton(
                    icon = Icons.Default.FormatUnderlined,
                    label = "Underline",
                    isSelected = activeTool == AnnotationTool.UNDERLINE,
                    selectedColor = Color(0xFF10B981),
                    onClick = {
                        if (activeTool == AnnotationTool.UNDERLINE) {
                            showColorPicker = !showColorPicker
                        } else {
                            viewModel.setActiveTool(AnnotationTool.UNDERLINE)
                            viewModel.setActiveColor(0xFF2563EB)
                            viewModel.setActiveStrokeWidth(3f)
                            showColorPicker = true
                        }
                    }
                )

                // Strikethrough
                ToolIconButton(
                    icon = Icons.Default.FormatStrikethrough,
                    label = "Strike",
                    isSelected = activeTool == AnnotationTool.STRIKETHROUGH,
                    selectedColor = Color(0xFFEC4899),
                    onClick = {
                        if (activeTool == AnnotationTool.STRIKETHROUGH) {
                            showColorPicker = !showColorPicker
                        } else {
                            viewModel.setActiveTool(AnnotationTool.STRIKETHROUGH)
                            viewModel.setActiveColor(0xFFDC2626)
                            viewModel.setActiveStrokeWidth(3f)
                            showColorPicker = true
                        }
                    }
                )

                // Sticky Note
                ToolIconButton(
                    icon = Icons.AutoMirrored.Filled.Comment,
                    label = "Note",
                    isSelected = activeTool == AnnotationTool.STICKY_NOTE,
                    selectedColor = Color(0xFFF59E0B),
                    onClick = {
                        viewModel.setActiveTool(AnnotationTool.STICKY_NOTE)
                        showColorPicker = false
                    }
                )

                // Stamp Tool
                Box {
                    ToolIconButton(
                        icon = Icons.Default.Verified,
                        label = "Stamp",
                        isSelected = activeTool == AnnotationTool.STAMP,
                        selectedColor = Color(0xFF10B981),
                        onClick = {
                            viewModel.setActiveTool(AnnotationTool.STAMP)
                            showStampMenu = true
                            showColorPicker = false
                        }
                    )

                    DropdownMenu(
                        expanded = showStampMenu,
                        onDismissRequest = { showStampMenu = false },
                        modifier = Modifier.background(AcrobatDarkSurfaceElevated)
                    ) {
                        listOf(
                            "APPROVED" to Color(0xFF10B981),
                            "CONFIDENTIAL" to Color(0xFF8B5CF6),
                            "DRAFT" to Color(0xFF3B82F6),
                            "URGENT" to Color(0xFFDC2626)
                        ).forEach { (stampText, color) ->
                            DropdownMenuItem(
                                text = {
                                    Text(
                                        text = stampText,
                                        fontWeight = FontWeight.Bold,
                                        color = color,
                                        letterSpacing = 1.sp
                                    )
                                },
                                onClick = {
                                    showStampMenu = false
                                    onSelectStamp(stampText)
                                }
                            )
                        }
                    }
                }

                // Digital Signature Placement
                ToolIconButton(
                    icon = Icons.Default.Draw,
                    label = "Sign",
                    isSelected = activeTool == AnnotationTool.SIGNATURE,
                    selectedColor = Color(0xFF8B5CF6),
                    onClick = {
                        viewModel.setActiveTool(AnnotationTool.SIGNATURE)
                        showColorPicker = false
                        onSelectSignature()
                    }
                )

                // Text Box / Edit Text
                ToolIconButton(
                    icon = Icons.Default.TextFields,
                    label = "Text",
                    isSelected = activeTool == AnnotationTool.TEXT_BOX,
                    selectedColor = Color(0xFF38BDF8),
                    onClick = {
                        viewModel.setActiveTool(AnnotationTool.TEXT_BOX)
                        showColorPicker = false
                    }
                )

                // Form Fill Assistant
                ToolIconButton(
                    icon = Icons.Default.DynamicForm,
                    label = "Form",
                    isSelected = activeTool == AnnotationTool.FORM_FIELD,
                    selectedColor = AdfElectricCyan,
                    onClick = {
                        viewModel.setActiveTool(AnnotationTool.FORM_FIELD)
                        showColorPicker = false
                    }
                )

                // Whiteout / Edit Content
                ToolIconButton(
                    icon = Icons.Default.LayersClear,
                    label = "Whiteout",
                    isSelected = activeTool == AnnotationTool.WHITEOUT,
                    selectedColor = Color(0xFFF43F5E),
                    onClick = {
                        viewModel.setActiveTool(AnnotationTool.WHITEOUT)
                        showColorPicker = false
                    }
                )

                // Permanent Redaction Mark
                ToolIconButton(
                    icon = Icons.Default.VisibilityOff,
                    label = "Redact",
                    isSelected = activeTool == AnnotationTool.REDACT,
                    selectedColor = Color(0xFFEF4444),
                    onClick = {
                        viewModel.setActiveTool(AnnotationTool.REDACT)
                        showColorPicker = false
                    }
                )

                // Watermark Customizer
                ToolIconButton(
                    icon = Icons.AutoMirrored.Filled.BrandingWatermark,
                    label = "Watermark",
                    isSelected = activeTool == AnnotationTool.WATERMARK,
                    selectedColor = Color(0xFFA855F7),
                    onClick = {
                        viewModel.setActiveTool(AnnotationTool.WATERMARK)
                        showColorPicker = false
                        onOpenWatermarkDialog()
                    }
                )

                // Delete Page Quick Tool
                ToolIconButton(
                    icon = Icons.Default.DeleteOutline,
                    label = "Delete Page",
                    isSelected = false,
                    selectedColor = Color(0xFFEF4444),
                    onClick = {
                        showColorPicker = false
                        onDeleteCurrentPage()
                    }
                )

                // Rotate Page Quick Tool
                ToolIconButton(
                    icon = Icons.AutoMirrored.Filled.RotateRight,
                    label = "Rotate",
                    isSelected = false,
                    selectedColor = Color(0xFF06B6D4),
                    onClick = {
                        showColorPicker = false
                        onRotatePage()
                    }
                )

                // Page Manager / Organize Quick Tool
                ToolIconButton(
                    icon = Icons.Default.ViewAgenda,
                    label = "Pages",
                    isSelected = false,
                    selectedColor = AdfElectricCyan,
                    onClick = {
                        showColorPicker = false
                        onOrganizePages()
                    }
                )

                // Eraser / Clear Page Annotations
                ToolIconButton(
                    icon = Icons.Default.DeleteSweep,
                    label = "Clear",
                    isSelected = false,
                    selectedColor = CrimsonPrimary,
                    onClick = onClearPageAnnotations
                )

                // Dismiss Tools
                IconButton(
                    onClick = {
                        viewModel.setActiveTool(AnnotationTool.NONE)
                        showColorPicker = false
                    },
                    modifier = Modifier.size(48.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.Close,
                        contentDescription = "Close Tools",
                        tint = AcrobatTextMuted,
                        modifier = Modifier.size(18.dp)
                    )
                }
            }
        }
    }
}

@Composable
fun ToolIconButton(
    icon: ImageVector,
    label: String,
    isSelected: Boolean,
    selectedColor: Color,
    enabled: Boolean = true,
    customTestTag: String? = null,
    onClick: () -> Unit
) {
    Surface(
        shape = RoundedCornerShape(12.dp),
        color = if (isSelected) selectedColor.copy(alpha = 0.22f) else Color.Transparent,
        border = CardDefaults.outlinedCardBorder().copy(
            brush = androidx.compose.ui.graphics.SolidColor(
                if (isSelected) selectedColor else Color.Transparent
            )
        ),
        modifier = Modifier
            .minimumInteractiveComponentSize()
            .alpha(if (enabled) 1f else 0.38f)
            .clip(RoundedCornerShape(12.dp))
            .clickable(enabled = enabled, onClick = onClick)
            .testTag(customTestTag ?: "tool_${label.lowercase().replace(" ", "_")}")
    ) {
        Column(
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 6.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Icon(
                imageVector = icon,
                contentDescription = label,
                tint = if (isSelected) selectedColor else AcrobatTextSecondary,
                modifier = Modifier.size(20.dp)
            )
            Spacer(modifier = Modifier.height(2.dp))
            Text(
                text = label,
                color = if (isSelected) selectedColor else AcrobatTextMuted,
                fontSize = 10.sp,
                fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium
            )
        }
    }
}

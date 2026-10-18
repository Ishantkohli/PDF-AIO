package com.example.ui.screens

import androidx.compose.animation.*
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.BrandingWatermark
import androidx.compose.material.icons.automirrored.filled.Comment
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.ui.AnnotationTool
import com.example.ui.PdfViewModel
import com.example.ui.theme.*

@Composable
fun AnnotationToolbar(
    viewModel: PdfViewModel,
    activeTool: AnnotationTool,
    activeColor: Long,
    onSelectStamp: (String) -> Unit,
    onSelectSignature: () -> Unit,
    onOpenWatermarkDialog: () -> Unit = {},
    onClearPageAnnotations: () -> Unit,
    modifier: Modifier = Modifier
) {
    var showColorPicker by remember { mutableStateOf(false) }
    var showStampMenu by remember { mutableStateOf(false) }

    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        // Sub-palette for Pen / Highlighter Colors & Widths
        AnimatedVisibility(
            visible = showColorPicker && (activeTool == AnnotationTool.PEN || activeTool == AnnotationTool.HIGHLIGHT),
            enter = fadeIn() + expandVertically(),
            exit = fadeOut() + shrinkVertically()
        ) {
            Surface(
                shape = RoundedCornerShape(16.dp),
                color = AcrobatDarkSurfaceElevated,
                shadowElevation = 8.dp,
                border = CardDefaults.outlinedCardBorder().copy(brush = androidx.compose.ui.graphics.SolidColor(AcrobatDarkBorder)),
                modifier = Modifier
                    .padding(bottom = 8.dp)
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    val colors: List<Pair<Long, String>> = if (activeTool == AnnotationTool.HIGHLIGHT) {
                        listOf(
                            0x66FDE047L to "Yellow",
                            0x6686EFACL to "Green",
                            0x66F472B6L to "Pink",
                            0x6667E8F9L to "Cyan"
                        )
                    } else {
                        listOf(
                            0xFFDC2626L to "Crimson Red",
                            0xFF2563EBL to "Royal Blue",
                            0xFF0F172AL to "Obsidian Black",
                            0xFF059669L to "Emerald Green"
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
                                    showColorPicker = false
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
                                showColorPicker = false
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
            }
        }

        // Primary Floating Pill Toolbar
        Surface(
            shape = RoundedCornerShape(24.dp),
            color = AcrobatDarkSurfaceElevated,
            shadowElevation = 10.dp,
            border = CardDefaults.outlinedCardBorder().copy(brush = androidx.compose.ui.graphics.SolidColor(AcrobatDarkBorder)),
            modifier = Modifier.shadow(8.dp, RoundedCornerShape(24.dp))
        ) {
            Row(
                modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(4.dp)
            ) {
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

                // Whiteout / Redact & Edit Content
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

                // Eraser / Clear Page
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
                    modifier = Modifier.size(38.dp)
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
    onClick: () -> Unit
) {
    Box(
        modifier = Modifier
            .size(42.dp)
            .clip(CircleShape)
            .background(if (isSelected) selectedColor.copy(alpha = 0.2f) else Color.Transparent)
            .clickable(onClick = onClick)
            .testTag("tool_${label.lowercase()}"),
        contentAlignment = Alignment.Center
    ) {
        Icon(
            imageVector = icon,
            contentDescription = label,
            tint = if (isSelected) selectedColor else AcrobatTextSecondary,
            modifier = Modifier.size(20.dp)
        )
    }
}

package com.example.ui.components

import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.local.AnnotationEntity
import com.example.ui.theme.*
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

enum class FormFieldType(val title: String) {
    TEXT_INPUT("Text Field"),
    CHECKBOX("Checkbox"),
    DATE_PICKER("Date Stamp"),
    SIGNATURE("Signature")
}

data class PlacedFormField(
    val id: Long = System.currentTimeMillis(),
    val type: FormFieldType,
    var xRatio: Float,
    var yRatio: Float,
    var value: String = ""
)

/**
 * Interactive Local Offline PDF Form Filling Assistant.
 * When backed by persisted [persistedFields] + CRUD callbacks, every field saves cleanly into Room
 * and flattens into exported/shared PDFs.
 */
@Composable
fun FormFillOverlay(
    isEditing: Boolean,
    onClose: () -> Unit,
    modifier: Modifier = Modifier,
    persistedFields: List<AnnotationEntity> = emptyList(),
    onAddPersistedField: ((type: FormFieldType, xRatio: Float, yRatio: Float, value: String) -> Unit)? = null,
    onUpdatePersistedField: ((AnnotationEntity) -> Unit)? = null,
    onDeletePersistedField: ((Long) -> Unit)? = null
) {
    val density = LocalDensity.current
    var overlaySizePx by remember { mutableStateOf(IntSize.Zero) }
    var activeTool by remember { mutableStateOf(FormFieldType.TEXT_INPUT) }
    val localFields = remember { mutableStateListOf<PlacedFormField>() }
    var selectedLocalField by remember { mutableStateOf<PlacedFormField?>(null) }
    var selectedEntityField by remember { mutableStateOf<AnnotationEntity?>(null) }
    var inputText by remember { mutableStateOf("") }

    if (!isEditing) return

    val usePersistence = onAddPersistedField != null

    Box(
        modifier = modifier
            .fillMaxSize()
            .onSizeChanged { overlaySizePx = it }
            .pointerInput(activeTool, usePersistence) {
                detectTapGestures { offset ->
                    val w = size.width.coerceAtLeast(1)
                    val h = size.height.coerceAtLeast(1)
                    val xRatio = (offset.x / w).coerceIn(0f, 0.92f)
                    val yRatio = (offset.y / h).coerceIn(0f, 0.92f)

                    val defaultValue = when (activeTool) {
                        FormFieldType.TEXT_INPUT -> "Tap to edit"
                        FormFieldType.CHECKBOX -> "✓"
                        FormFieldType.DATE_PICKER -> SimpleDateFormat("MMM d, yyyy", Locale.getDefault()).format(Date())
                        FormFieldType.SIGNATURE -> "[ Signature ]"
                    }

                    if (usePersistence) {
                        onAddPersistedField?.invoke(activeTool, xRatio, yRatio, defaultValue)
                    } else {
                        val newField = PlacedFormField(
                            type = activeTool,
                            xRatio = xRatio,
                            yRatio = yRatio,
                            value = defaultValue
                        )
                        localFields.add(newField)
                        if (activeTool == FormFieldType.TEXT_INPUT) {
                            selectedLocalField = newField
                            inputText = ""
                        }
                    }
                }
            }
    ) {
        if (usePersistence) {
            persistedFields.forEach { entity ->
                val isCheckbox = entity.extraData.contains("CHECKBOX")
                val offsetX = if (overlaySizePx.width > 0) with(density) { (entity.posX * overlaySizePx.width).toDp() } else 0.dp
                val offsetY = if (overlaySizePx.height > 0) with(density) { (entity.posY * overlaySizePx.height).toDp() } else 0.dp
                Surface(
                    shape = RoundedCornerShape(6.dp),
                    color = AdfMidnightSurface.copy(alpha = 0.92f),
                    border = CardDefaults.outlinedCardBorder().copy(
                        brush = androidx.compose.ui.graphics.Brush.horizontalGradient(listOf(AdfElectricCyan, AdfVibrantBlue))
                    ),
                    modifier = Modifier
                        .offset(x = offsetX, y = offsetY)
                        .clickable {
                            if (isCheckbox) {
                                val nextVal = if (entity.textContent == "✓" || entity.textContent == "true") "☐" else "✓"
                                onUpdatePersistedField?.invoke(entity.copy(textContent = nextVal))
                            } else {
                                selectedEntityField = entity
                                inputText = if (entity.textContent == "Tap to edit") "" else entity.textContent
                            }
                        }
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = entity.textContent.ifBlank { "Tap to edit" },
                            color = AdfElectricCyan,
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Bold
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Icon(
                            imageVector = Icons.Default.Close,
                            contentDescription = "Delete Field",
                            tint = Color(0xFFEF4444),
                            modifier = Modifier
                                .size(14.dp)
                                .clickable { onDeletePersistedField?.invoke(entity.id) }
                        )
                    }
                }
            }
        } else {
            localFields.forEach { field ->
                val offsetX = if (overlaySizePx.width > 0) with(density) { (field.xRatio * overlaySizePx.width).toDp() } else 0.dp
                val offsetY = if (overlaySizePx.height > 0) with(density) { (field.yRatio * overlaySizePx.height).toDp() } else 0.dp
                Surface(
                    shape = RoundedCornerShape(6.dp),
                    color = AdfMidnightSurface.copy(alpha = 0.9f),
                    border = CardDefaults.outlinedCardBorder().copy(
                        brush = androidx.compose.ui.graphics.Brush.horizontalGradient(listOf(AdfElectricCyan, AdfVibrantBlue))
                    ),
                    modifier = Modifier
                        .offset(x = offsetX, y = offsetY)
                        .clickable {
                            if (field.type == FormFieldType.CHECKBOX) {
                                field.value = if (field.value == "☑" || field.value == "✓") "☐" else "✓"
                            } else {
                                selectedLocalField = field
                                inputText = field.value
                            }
                        }
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = field.value,
                            color = AdfElectricCyan,
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Bold
                        )
                        Spacer(modifier = Modifier.width(4.dp))
                        Icon(
                            imageVector = Icons.Default.Close,
                            contentDescription = "Delete Field",
                            tint = Color.Red.copy(alpha = 0.8f),
                            modifier = Modifier
                                .size(14.dp)
                                .clickable { localFields.remove(field) }
                        )
                    }
                }
            }
        }

        // Bottom Form Fill Control Toolbar
        Surface(
            shape = RoundedCornerShape(topStart = 20.dp, topEnd = 20.dp),
            color = AdfObsidianSurface,
            tonalElevation = 12.dp,
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .fillMaxWidth()
                .testTag("form_fill_toolbar")
        ) {
            Column(modifier = Modifier.padding(14.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Default.Edit, contentDescription = null, tint = AdfElectricCyan, modifier = Modifier.size(20.dp))
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = "Form Fill Assistant (Tap page to place)",
                            fontWeight = FontWeight.Bold,
                            fontSize = 13.sp,
                            color = AdfTextPrimary
                        )
                    }

                    TextButton(onClick = onClose) {
                        Text("Done", color = AdfElectricCyan, fontWeight = FontWeight.Bold)
                    }
                }

                Spacer(modifier = Modifier.height(8.dp))

                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    FormFieldType.entries.forEach { type ->
                        val isSelected = activeTool == type
                        FilterChip(
                            selected = isSelected,
                            onClick = { activeTool = type },
                            label = { Text(type.title, fontSize = 11.sp) },
                            colors = FilterChipDefaults.filterChipColors(
                                selectedContainerColor = CrimsonPrimary,
                                selectedLabelColor = Color.White
                            )
                        )
                    }
                }
            }
        }
    }

    // Input Dialog for editing persisted or local form field text
    if (selectedLocalField != null || selectedEntityField != null) {
        AlertDialog(
            onDismissRequest = {
                selectedLocalField = null
                selectedEntityField = null
            },
            containerColor = AcrobatDarkSurfaceElevated,
            title = { Text("Edit Form Value", color = AcrobatTextPrimary, fontWeight = FontWeight.Bold) },
            text = {
                OutlinedTextField(
                    value = inputText,
                    onValueChange = { inputText = it },
                    label = { Text("Value") },
                    singleLine = true,
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedTextColor = AcrobatTextPrimary,
                        unfocusedTextColor = AcrobatTextPrimary,
                        focusedBorderColor = CrimsonPrimary,
                        unfocusedBorderColor = AcrobatDarkBorder
                    ),
                    modifier = Modifier.fillMaxWidth()
                )
            },
            confirmButton = {
                Button(
                    onClick = {
                        selectedLocalField?.let { it.value = inputText.ifBlank { "Tap to edit" } }
                        selectedEntityField?.let { entity ->
                            onUpdatePersistedField?.invoke(entity.copy(textContent = inputText.ifBlank { "Tap to edit" }))
                        }
                        selectedLocalField = null
                        selectedEntityField = null
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = CrimsonPrimary)
                ) {
                    Text("Save")
                }
            },
            dismissButton = {
                TextButton(
                    onClick = {
                        selectedLocalField = null
                        selectedEntityField = null
                    }
                ) {
                    Text("Cancel", color = AcrobatTextSecondary)
                }
            }
        )
    }
}

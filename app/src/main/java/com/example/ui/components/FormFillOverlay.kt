package com.example.ui.components

import android.widget.Toast
import androidx.compose.animation.*
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.*
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
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
 * Tap anywhere on the document canvas to place text fields, checkboxes, dates, or signatures!
 */
@Composable
fun FormFillOverlay(
    isEditing: Boolean,
    onClose: () -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    var activeTool by remember { mutableStateOf(FormFieldType.TEXT_INPUT) }
    val placedFields = remember { mutableStateListOf<PlacedFormField>() }
    var selectedFieldForInput by remember { mutableStateOf<PlacedFormField?>(null) }
    var inputText by remember { mutableStateOf("") }

    if (!isEditing) return

    Box(
        modifier = modifier
            .fillMaxSize()
            .pointerInput(activeTool) {
                detectTapGestures { offset ->
                    val xRatio = offset.x / size.width
                    val yRatio = offset.y / size.height

                    val defaultValue = when (activeTool) {
                        FormFieldType.TEXT_INPUT -> "Tap to edit"
                        FormFieldType.CHECKBOX -> "☑"
                        FormFieldType.DATE_PICKER -> SimpleDateFormat("MMM d, yyyy", Locale.getDefault()).format(Date())
                        FormFieldType.SIGNATURE -> "[ Signature ]"
                    }

                    val newField = PlacedFormField(
                        type = activeTool,
                        xRatio = xRatio,
                        yRatio = yRatio,
                        value = defaultValue
                    )
                    placedFields.add(newField)
                    if (activeTool == FormFieldType.TEXT_INPUT) {
                        selectedFieldForInput = newField
                        inputText = ""
                    }
                }
            }
    ) {
        // Render placed form fields over document canvas
        placedFields.forEach { field ->
            Box(
                modifier = Modifier
                    .fillMaxSize()
            ) {
                Surface(
                    shape = RoundedCornerShape(6.dp),
                    color = AdfMidnightSurface.copy(alpha = 0.9f),
                    border = CardDefaults.outlinedCardBorder().copy(
                        brush = androidx.compose.ui.graphics.Brush.horizontalGradient(listOf(AdfElectricCyan, AdfVibrantBlue))
                    ),
                    modifier = Modifier
                        .offset(
                            x = (field.xRatio * 320).dp,
                            y = (field.yRatio * 500).dp
                        )
                        .clickable {
                            if (field.type == FormFieldType.CHECKBOX) {
                                field.value = if (field.value == "☑") "☐" else "☑"
                            } else {
                                selectedFieldForInput = field
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
                                .clickable { placedFields.remove(field) }
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
                            text = "Form Fill Assistant",
                            fontWeight = FontWeight.Bold,
                            fontSize = 13.5.sp,
                            color = AdfTextPrimary
                        )
                    }

                    TextButton(onClick = onClose) {
                        Text("Done", color = AdfElectricCyan, fontWeight = FontWeight.Bold)
                    }
                }

                Spacer(modifier = Modifier.height(8.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
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

    // Input Dialog for editing field text
    selectedFieldForInput?.let { field ->
        AlertDialog(
            onDismissRequest = { selectedFieldForInput = null },
            title = { Text("Edit Form Value") },
            text = {
                OutlinedTextField(
                    value = inputText,
                    onValueChange = { inputText = it },
                    label = { Text("Value") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
            },
            confirmButton = {
                Button(
                    onClick = {
                        field.value = inputText
                        selectedFieldForInput = null
                    }
                ) {
                    Text("Save")
                }
            },
            dismissButton = {
                TextButton(onClick = { selectedFieldForInput = null }) {
                    Text("Cancel")
                }
            }
        )
    }
}

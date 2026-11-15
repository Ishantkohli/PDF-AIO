package com.example.ui.components

import android.graphics.BitmapFactory
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.BrandingWatermark
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import com.example.data.pdf.DocumentConverterEngine
import com.example.ui.theme.*
import java.io.File
import java.io.FileOutputStream

@Composable
fun WatermarkCustomizerDialog(
    initialOptions: DocumentConverterEngine.WatermarkOptions? = null,
    onDismiss: () -> Unit,
    onApply: (DocumentConverterEngine.WatermarkOptions) -> Unit,
    onRemove: (() -> Unit)? = null
) {
    val context = LocalContext.current
    val current = initialOptions ?: DocumentConverterEngine.WatermarkOptions()
    var text by remember { mutableStateOf(current.text) }
    var colorHex by remember { mutableLongStateOf(current.colorHex) }
    var opacity by remember { mutableFloatStateOf(current.opacity) }
    var rotationAngle by remember { mutableFloatStateOf(current.rotationAngle) }
    var fontSizeSp by remember { mutableFloatStateOf(current.fontSizeSp) }
    var isTiledPattern by remember { mutableStateOf(current.isTiledPattern) }
    var applyToAllPages by remember { mutableStateOf(current.applyToAllPages) }
    var imagePath by remember { mutableStateOf(current.imagePath) }
    var imageScale by remember { mutableFloatStateOf(current.imageScale) }
    var watermarkMode by remember {
        mutableStateOf(
            when {
                !current.imagePath.isNullOrBlank() && current.text.isNotBlank() -> 2
                !current.imagePath.isNullOrBlank() -> 1
                else -> 0
            }
        )
    }

    val logoPickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.PickVisualMedia()
    ) { uri ->
        if (uri != null) {
            runCatching {
                val assetDir = File(context.filesDir, "watermark_assets").apply { if (!exists()) mkdirs() }
                val dest = File(assetDir, "wm_logo_${System.currentTimeMillis()}.png")
                context.contentResolver.openInputStream(uri)?.use { input ->
                    FileOutputStream(dest).use { output -> input.copyTo(output) }
                }
                if (dest.exists() && dest.length() > 0L) {
                    imagePath = dest.absolutePath
                    if (watermarkMode == 0) watermarkMode = 1
                }
            }
        }
    }

    val logoPreviewBitmap = remember(imagePath) {
        imagePath?.let { path ->
            val f = File(path)
            if (f.exists() && f.length() > 0L) {
                BitmapFactory.decodeFile(f.absolutePath)?.asImageBitmap()
            } else null
        }
    }

    val presets = listOf("CONFIDENTIAL", "DRAFT", "COPY", "APPROVED", "SAMPLE", "DO NOT COPY")
    val colors = listOf(
        0xFFDC2626L to "Crimson",
        0xFF475569L to "Slate",
        0xFF2563EBL to "Blue",
        0xFF16A34AL to "Green",
        0xFFD97706L to "Amber",
        0xFF7C3AEDL to "Purple"
    )

    Dialog(onDismissRequest = onDismiss) {
        Card(
            shape = RoundedCornerShape(20.dp),
            colors = CardDefaults.cardColors(containerColor = AcrobatDarkSurfaceElevated),
            elevation = CardDefaults.cardElevation(defaultElevation = 12.dp),
            modifier = Modifier
                .fillMaxWidth()
                .wrapContentHeight()
                .padding(vertical = 16.dp)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(20.dp)
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(14.dp)
            ) {
                // Header
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Box(
                            modifier = Modifier
                                .size(36.dp)
                                .clip(CircleShape)
                                .background(CrimsonPrimary.copy(alpha = 0.2f)),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = Icons.AutoMirrored.Filled.BrandingWatermark,
                                contentDescription = null,
                                tint = CrimsonAccent,
                                modifier = Modifier.size(20.dp)
                            )
                        }
                        Spacer(modifier = Modifier.width(10.dp))
                        Text(
                            text = "Custom Watermark",
                            style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                            color = AcrobatTextPrimary
                        )
                    }

                    IconButton(onClick = onDismiss, modifier = Modifier.size(32.dp)) {
                        Icon(Icons.Default.Close, contentDescription = "Close", tint = AcrobatTextSecondary)
                    }
                }

                // Mode Switcher: Text / Logo Image / Both
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    listOf("Text", "Logo Image", "Text + Logo").forEachIndexed { idx, label ->
                        FilterChip(
                            selected = watermarkMode == idx,
                            onClick = {
                                watermarkMode = idx
                                if (idx != 1 && text.isBlank()) text = "CONFIDENTIAL"
                            },
                            label = { Text(label, fontSize = 11.5.sp, fontWeight = FontWeight.SemiBold) },
                            modifier = Modifier.weight(1f).testTag("watermark_mode_$idx")
                        )
                    }
                }

                // Live Mini Preview Card
                Card(
                    shape = RoundedCornerShape(10.dp),
                    colors = CardDefaults.cardColors(containerColor = PaperWhite),
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(132.dp)
                ) {
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(12.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Column(
                            modifier = Modifier.fillMaxSize(),
                            verticalArrangement = Arrangement.SpaceEvenly
                        ) {
                            repeat(5) {
                                Box(
                                    modifier = Modifier
                                        .fillMaxWidth(if (it == 0) 0.6f else 0.9f)
                                        .height(6.dp)
                                        .clip(RoundedCornerShape(3.dp))
                                        .background(Color(0xFFE2E8F0))
                                )
                            }
                        }

                        Column(
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.Center,
                            modifier = Modifier.rotate(-rotationAngle)
                        ) {
                            if (watermarkMode != 0 && logoPreviewBitmap != null) {
                                Image(
                                    bitmap = logoPreviewBitmap,
                                    contentDescription = "Watermark Logo Preview",
                                    contentScale = ContentScale.Fit,
                                    modifier = Modifier
                                        .size((42 + imageScale * 50).dp)
                                        .alpha(opacity)
                                )
                            }
                            if (watermarkMode != 1 && text.isNotBlank()) {
                                if (isTiledPattern) {
                                    repeat(2) {
                                        Text(
                                            text = text,
                                            fontWeight = FontWeight.Bold,
                                            fontSize = (fontSizeSp * 0.36f).sp,
                                            color = Color(colorHex).copy(alpha = opacity),
                                            textAlign = TextAlign.Center
                                        )
                                    }
                                } else {
                                    Text(
                                        text = text,
                                        fontWeight = FontWeight.Bold,
                                        fontSize = (fontSizeSp * 0.52f).sp,
                                        color = Color(colorHex).copy(alpha = opacity),
                                        textAlign = TextAlign.Center
                                    )
                                }
                            }
                        }
                    }
                }

                // Logo Image Picker (for modes 1 and 2)
                if (watermarkMode != 0) {
                    OutlinedButton(
                        onClick = {
                            logoPickerLauncher.launch(
                                PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)
                            )
                        },
                        modifier = Modifier.fillMaxWidth().testTag("pick_watermark_logo_button")
                    ) {
                        Icon(Icons.Default.Image, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            if (logoPreviewBitmap != null) "Change Logo Image" else "Choose Logo Image",
                            fontWeight = FontWeight.SemiBold
                        )
                    }

                    if (logoPreviewBitmap != null) {
                        Column {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Text("Logo Size", style = MaterialTheme.typography.labelSmall, color = AcrobatTextSecondary)
                                Text("${(imageScale * 100).toInt()}%", style = MaterialTheme.typography.labelSmall, color = CrimsonAccent, fontWeight = FontWeight.Bold)
                            }
                            Slider(
                                value = imageScale,
                                onValueChange = { imageScale = it },
                                valueRange = 0.15f..0.75f,
                                colors = SliderDefaults.colors(
                                    thumbColor = CrimsonPrimary,
                                    activeTrackColor = CrimsonPrimary,
                                    inactiveTrackColor = AcrobatDarkBorder
                                )
                            )
                        }
                    }
                }

                // Text Input & Presets (for modes 0 and 2)
                if (watermarkMode != 1) {
                    OutlinedTextField(
                        value = text,
                        onValueChange = { text = it },
                        label = { Text("Watermark Text") },
                        placeholder = { Text("e.g. CONFIDENTIAL, DRAFT, My Company") },
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedTextColor = AcrobatTextPrimary,
                            unfocusedTextColor = AcrobatTextPrimary,
                            focusedBorderColor = CrimsonPrimary,
                            unfocusedBorderColor = AcrobatDarkBorder,
                            focusedLabelColor = CrimsonAccent,
                            unfocusedLabelColor = AcrobatTextSecondary
                        ),
                        modifier = Modifier.fillMaxWidth()
                    )

                    Column {
                        Text(
                            text = "Quick Presets",
                            style = MaterialTheme.typography.labelSmall,
                            color = AcrobatTextSecondary
                        )
                        Spacer(modifier = Modifier.height(6.dp))
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            presets.take(3).forEach { p ->
                                FilterChip(
                                    selected = text == p,
                                    onClick = { text = p },
                                    label = { Text(p, fontSize = 11.sp, fontWeight = FontWeight.SemiBold) },
                                    colors = FilterChipDefaults.filterChipColors(
                                        selectedContainerColor = CrimsonPrimary.copy(alpha = 0.25f),
                                        selectedLabelColor = CrimsonAccent,
                                        containerColor = AcrobatDarkSurface,
                                        labelColor = AcrobatTextSecondary
                                    )
                                )
                            }
                        }
                        Spacer(modifier = Modifier.height(4.dp))
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            presets.drop(3).forEach { p ->
                                FilterChip(
                                    selected = text == p,
                                    onClick = { text = p },
                                    label = { Text(p, fontSize = 11.sp, fontWeight = FontWeight.SemiBold) },
                                    colors = FilterChipDefaults.filterChipColors(
                                        selectedContainerColor = CrimsonPrimary.copy(alpha = 0.25f),
                                        selectedLabelColor = CrimsonAccent,
                                        containerColor = AcrobatDarkSurface,
                                        labelColor = AcrobatTextSecondary
                                    )
                                )
                            }
                        }
                    }

                    Column {
                        Text(
                            text = "Watermark Color",
                            style = MaterialTheme.typography.labelSmall,
                            color = AcrobatTextSecondary
                        )
                        Spacer(modifier = Modifier.height(6.dp))
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            colors.forEach { (cHex, _) ->
                                Box(
                                    modifier = Modifier
                                        .size(32.dp)
                                        .clip(CircleShape)
                                        .background(Color(cHex))
                                        .border(
                                            width = if (colorHex == cHex) 3.dp else 1.dp,
                                            color = if (colorHex == cHex) Color.White else Color.Transparent,
                                            shape = CircleShape
                                        )
                                        .clickable { colorHex = cHex }
                                )
                            }
                        }
                    }
                }

                // Opacity Slider
                Column {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text("Opacity", style = MaterialTheme.typography.labelSmall, color = AcrobatTextSecondary)
                        Text("${(opacity * 100).toInt()}%", style = MaterialTheme.typography.labelSmall, color = CrimsonAccent, fontWeight = FontWeight.Bold)
                    }
                    Slider(
                        value = opacity,
                        onValueChange = { opacity = it },
                        valueRange = 0.10f..0.85f,
                        colors = SliderDefaults.colors(
                            thumbColor = CrimsonPrimary,
                            activeTrackColor = CrimsonPrimary,
                            inactiveTrackColor = AcrobatDarkBorder
                        )
                    )
                }

                // Rotation Angle Slider
                Column {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text("Rotation Angle", style = MaterialTheme.typography.labelSmall, color = AcrobatTextSecondary)
                        Text("${rotationAngle.toInt()}°", style = MaterialTheme.typography.labelSmall, color = CrimsonAccent, fontWeight = FontWeight.Bold)
                    }
                    Slider(
                        value = rotationAngle,
                        onValueChange = { rotationAngle = it },
                        valueRange = -90f..90f,
                        colors = SliderDefaults.colors(
                            thumbColor = CrimsonPrimary,
                            activeTrackColor = CrimsonPrimary,
                            inactiveTrackColor = AcrobatDarkBorder
                        )
                    )
                }

                // Tiling & Page Scope Switches
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text("Diagonal Repeated Tiling", style = MaterialTheme.typography.bodySmall, color = AcrobatTextPrimary, fontWeight = FontWeight.SemiBold)
                        Text("Repeat watermark pattern across entire page", style = MaterialTheme.typography.labelSmall, color = AcrobatTextMuted)
                    }
                    Switch(
                        checked = isTiledPattern,
                        onCheckedChange = { isTiledPattern = it },
                        colors = SwitchDefaults.colors(checkedThumbColor = Color.White, checkedTrackColor = CrimsonPrimary)
                    )
                }

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text("Apply to All Pages", style = MaterialTheme.typography.bodySmall, color = AcrobatTextPrimary, fontWeight = FontWeight.SemiBold)
                        Text(if (applyToAllPages) "Stamps every page in document" else "Stamps cover page only", style = MaterialTheme.typography.labelSmall, color = AcrobatTextMuted)
                    }
                    Switch(
                        checked = applyToAllPages,
                        onCheckedChange = { applyToAllPages = it },
                        colors = SwitchDefaults.colors(checkedThumbColor = Color.White, checkedTrackColor = CrimsonPrimary)
                    )
                }

                // Actions
                val canApply = when (watermarkMode) {
                    0 -> text.isNotBlank()
                    1 -> !imagePath.isNullOrBlank()
                    else -> text.isNotBlank() || !imagePath.isNullOrBlank()
                }
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    if (onRemove != null && initialOptions != null) {
                        OutlinedButton(
                            onClick = {
                                onRemove()
                                onDismiss()
                            },
                            colors = ButtonDefaults.outlinedButtonColors(contentColor = Color(0xFFEF4444)),
                            modifier = Modifier.weight(1f)
                        ) {
                            Text("Remove")
                        }
                    }

                    Button(
                        onClick = {
                            if (canApply) {
                                onApply(
                                    DocumentConverterEngine.WatermarkOptions(
                                        text = if (watermarkMode == 1) "" else text.trim(),
                                        colorHex = colorHex,
                                        opacity = opacity,
                                        rotationAngle = rotationAngle,
                                        fontSizeSp = fontSizeSp,
                                        isTiledPattern = isTiledPattern,
                                        applyToAllPages = applyToAllPages,
                                        imagePath = if (watermarkMode == 0) null else imagePath,
                                        imageScale = imageScale
                                    )
                                )
                            }
                            onDismiss()
                        },
                        enabled = canApply,
                        colors = ButtonDefaults.buttonColors(containerColor = CrimsonPrimary),
                        modifier = Modifier
                            .weight(if (onRemove != null && initialOptions != null) 1f else 2f)
                            .testTag("apply_watermark_dialog_button")
                    ) {
                        Text("Apply Watermark", fontWeight = FontWeight.Bold)
                    }
                }
            }
        }
    }
}


package com.example.ui.components

import androidx.compose.animation.*
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
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
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.example.data.local.SignatureEntity
import com.example.ui.PdfViewModel
import com.example.ui.theme.*
import org.json.JSONArray
import org.json.JSONObject

enum class SignatureMode {
    TYPE,
    DRAW,
    VAULT
}

enum class SignatureCursiveStyle(val label: String, val fontStyle: FontStyle, val fontWeight: FontWeight, val fontFamily: FontFamily) {
    CURSIVE_FLOW("Cursive Flow", FontStyle.Italic, FontWeight.SemiBold, FontFamily.Cursive),
    CALLIGRAPHY("Calligraphy", FontStyle.Italic, FontWeight.Bold, FontFamily.Serif),
    EXECUTIVE("Executive Ink", FontStyle.Italic, FontWeight.ExtraBold, FontFamily.SansSerif),
    ARTISTIC("Artistic Script", FontStyle.Normal, FontWeight.Bold, FontFamily.Cursive)
}

data class InkColorOption(val name: String, val color: Color, val hex: Long)

val InkColors = listOf(
    InkColorOption("Navy Blue", Color(0xFF1E3A8A), 0xFF1E3A8AL),
    InkColorOption("Obsidian", Color(0xFF0F172A), 0xFF0F172AL),
    InkColorOption("Crimson", Color(0xFFDC2626), 0xFFDC2626L),
    InkColorOption("Emerald", Color(0xFF065F46), 0xFF065F46L)
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DigitalSignatureDialog(
    viewModel: PdfViewModel,
    activeDocumentTitle: String?,
    currentPageIndex: Int,
    totalPages: Int,
    savedSignatures: List<SignatureEntity>,
    onDismiss: () -> Unit
) {
    var mode by remember { mutableStateOf(SignatureMode.TYPE) }

    // Type signature states
    var typedName by remember { mutableStateOf("Ishant Kohli") }
    var selectedStyle by remember { mutableStateOf(SignatureCursiveStyle.CURSIVE_FLOW) }
    var selectedInkColor by remember { mutableStateOf(InkColors[0]) }
    var includeFlourish by remember { mutableStateOf(true) }

    // Draw signature states
    val completedStrokes = remember { mutableStateListOf<List<Offset>>() }
    val currentStroke = remember { mutableStateListOf<Offset>() }
    var strokeThickness by remember { mutableFloatStateOf(3.5f) }
    var canvasSize by remember { mutableStateOf(IntSize(1, 1)) }

    // Placement & Target page states
    var targetPage by remember { mutableIntStateOf(currentPageIndex.coerceIn(0, (totalPages - 1).coerceAtLeast(0))) }
    var placementPreset by remember { mutableStateOf("BOTTOM_RIGHT") } // BOTTOM_RIGHT, BOTTOM_LEFT, CENTER, TOP_RIGHT
    var saveToVault by remember { mutableStateOf(true) }
    var selectedSavedSignature by remember { mutableStateOf<SignatureEntity?>(null) }

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Surface(
            modifier = Modifier
                .fillMaxWidth(0.95f)
                .fillMaxHeight(0.92f)
                .clip(RoundedCornerShape(24.dp))
                .border(1.dp, AdfElectricCyan.copy(alpha = 0.3f), RoundedCornerShape(24.dp)),
            color = AdfMidnightBackground,
            shadowElevation = 16.dp
        ) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(20.dp)
            ) {
                // Header
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Surface(
                            shape = RoundedCornerShape(12.dp),
                            color = AdfElectricCyan.copy(alpha = 0.15f),
                            modifier = Modifier.size(42.dp)
                        ) {
                            Box(contentAlignment = Alignment.Center) {
                                Icon(
                                    imageVector = Icons.Default.Draw,
                                    contentDescription = null,
                                    tint = AdfElectricCyan,
                                    modifier = Modifier.size(24.dp)
                                )
                            }
                        }
                        Spacer(modifier = Modifier.width(12.dp))
                        Column {
                            Text(
                                text = "Digital Signature Studio",
                                style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                                color = AdfTextPrimary
                            )
                            Text(
                                text = if (activeDocumentTitle != null) "Apply directly to $activeDocumentTitle" else "Create and save to vault",
                                style = MaterialTheme.typography.bodySmall,
                                color = AdfTextSecondary
                            )
                        }
                    }

                    IconButton(
                        onClick = onDismiss,
                        modifier = Modifier.testTag("close_signature_dialog")
                    ) {
                        Icon(
                            imageVector = Icons.Default.Close,
                            contentDescription = "Close",
                            tint = AdfTextSecondary
                        )
                    }
                }

                Spacer(modifier = Modifier.height(14.dp))

                // Mode Tabs (Type, Draw, Vault)
                SingleChoiceSegmentedButtonRow(
                    modifier = Modifier.fillMaxWidth()
                ) {
                    SegmentedButton(
                        selected = mode == SignatureMode.TYPE,
                        onClick = { mode = SignatureMode.TYPE },
                        shape = SegmentedButtonDefaults.itemShape(index = 0, count = 3),
                        colors = SegmentedButtonDefaults.colors(
                            activeContainerColor = AdfElectricCyan,
                            activeContentColor = AdfMidnightBackground,
                            inactiveContainerColor = AdfSurfaceCard,
                            inactiveContentColor = AdfTextSecondary
                        )
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Default.Keyboard, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("Type", fontWeight = FontWeight.Bold, fontSize = 12.sp)
                        }
                    }

                    SegmentedButton(
                        selected = mode == SignatureMode.DRAW,
                        onClick = { mode = SignatureMode.DRAW },
                        shape = SegmentedButtonDefaults.itemShape(index = 1, count = 3),
                        colors = SegmentedButtonDefaults.colors(
                            activeContainerColor = AdfElectricCyan,
                            activeContentColor = AdfMidnightBackground,
                            inactiveContainerColor = AdfSurfaceCard,
                            inactiveContentColor = AdfTextSecondary
                        )
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Default.Gesture, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("Draw", fontWeight = FontWeight.Bold, fontSize = 12.sp)
                        }
                    }

                    SegmentedButton(
                        selected = mode == SignatureMode.VAULT,
                        onClick = { mode = SignatureMode.VAULT },
                        shape = SegmentedButtonDefaults.itemShape(index = 2, count = 3),
                        colors = SegmentedButtonDefaults.colors(
                            activeContainerColor = AdfElectricCyan,
                            activeContentColor = AdfMidnightBackground,
                            inactiveContainerColor = AdfSurfaceCard,
                            inactiveContentColor = AdfTextSecondary
                        )
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Default.FolderSpecial, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("Vault (${savedSignatures.size})", fontWeight = FontWeight.Bold, fontSize = 12.sp)
                        }
                    }
                }

                Spacer(modifier = Modifier.height(14.dp))

                // Scrollable Content
                LazyColumn(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxWidth(),
                    verticalArrangement = Arrangement.spacedBy(14.dp)
                ) {
                    when (mode) {
                        SignatureMode.TYPE -> {
                            // Text Input
                            item {
                                OutlinedTextField(
                                    value = typedName,
                                    onValueChange = { typedName = it },
                                    label = { Text("Signer Name") },
                                    placeholder = { Text("e.g. Ishant Kohli") },
                                    singleLine = true,
                                    colors = OutlinedTextFieldDefaults.colors(
                                        focusedContainerColor = AdfSurfaceCard,
                                        unfocusedContainerColor = AdfSurfaceCard,
                                        focusedTextColor = AdfTextPrimary,
                                        unfocusedTextColor = AdfTextPrimary,
                                        focusedBorderColor = AdfElectricCyan,
                                        unfocusedBorderColor = AdfBorderSubtle
                                    ),
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .testTag("type_signature_input")
                                )
                            }

                            // Style Selection (4 Cursive & Handwriting Styles)
                            item {
                                Text(
                                    text = "Handwriting Style",
                                    style = MaterialTheme.typography.titleSmall,
                                    color = AdfTextSecondary
                                )
                                Spacer(modifier = Modifier.height(6.dp))
                                LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                    items(SignatureCursiveStyle.values()) { style ->
                                        Surface(
                                            shape = RoundedCornerShape(10.dp),
                                            color = if (selectedStyle == style) AdfVibrantBlue.copy(alpha = 0.25f) else AdfSurfaceCard,
                                            border = CardDefaults.outlinedCardBorder().copy(
                                                brush = androidx.compose.ui.graphics.SolidColor(
                                                    if (selectedStyle == style) AdfElectricCyan else AdfBorderSubtle
                                                )
                                            ),
                                            modifier = Modifier.clickable { selectedStyle = style }
                                        ) {
                                            Column(
                                                modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp),
                                                horizontalAlignment = Alignment.CenterHorizontally
                                            ) {
                                                Text(
                                                    text = typedName.ifBlank { "Signature" },
                                                    fontSize = 17.sp,
                                                    fontStyle = style.fontStyle,
                                                    fontWeight = style.fontWeight,
                                                    fontFamily = style.fontFamily,
                                                    color = if (selectedStyle == style) AdfElectricCyan else AdfTextPrimary
                                                )
                                                Spacer(modifier = Modifier.height(4.dp))
                                                Text(
                                                    text = style.label,
                                                    fontSize = 10.sp,
                                                    color = AdfTextMuted
                                                )
                                            }
                                        }
                                    }
                                }
                            }

                            // Live Preview Card
                            item {
                                Text(
                                    text = "Signature Preview",
                                    style = MaterialTheme.typography.titleSmall,
                                    color = AdfTextSecondary
                                )
                                Spacer(modifier = Modifier.height(6.dp))
                                Card(
                                    colors = CardDefaults.cardColors(containerColor = Color(0xFFFDFBF7)), // Crisp parchment paper
                                    shape = RoundedCornerShape(12.dp),
                                    border = CardDefaults.outlinedCardBorder().copy(brush = androidx.compose.ui.graphics.SolidColor(Color(0xFFE2E8F0))),
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .height(130.dp)
                                ) {
                                    Box(
                                        modifier = Modifier
                                            .fillMaxSize()
                                            .padding(16.dp),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                            Text(
                                                text = typedName.ifBlank { "Your Signature" },
                                                fontSize = 32.sp,
                                                fontStyle = selectedStyle.fontStyle,
                                                fontWeight = selectedStyle.fontWeight,
                                                fontFamily = selectedStyle.fontFamily,
                                                color = selectedInkColor.color,
                                                textAlign = TextAlign.Center
                                            )
                                            if (includeFlourish) {
                                                Spacer(modifier = Modifier.height(2.dp))
                                                Canvas(
                                                    modifier = Modifier
                                                        .width(180.dp)
                                                        .height(12.dp)
                                                ) {
                                                    val path = Path().apply {
                                                        moveTo(10f, 6f)
                                                        quadraticTo(size.width * 0.5f, 14f, size.width - 10f, 4f)
                                                    }
                                                    drawPath(
                                                        path = path,
                                                        color = selectedInkColor.color,
                                                        style = Stroke(
                                                            width = 2.5f,
                                                            cap = StrokeCap.Round,
                                                            join = StrokeJoin.Round
                                                        )
                                                    )
                                                }
                                            }
                                        }

                                        // Certified Seal Watermark
                                        Surface(
                                            shape = RoundedCornerShape(4.dp),
                                            color = selectedInkColor.color.copy(alpha = 0.08f),
                                            modifier = Modifier.align(Alignment.BottomEnd)
                                        ) {
                                            Text(
                                                text = "DIGITALLY VERIFIED",
                                                color = selectedInkColor.color,
                                                fontSize = 8.sp,
                                                fontWeight = FontWeight.Bold,
                                                modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                                            )
                                        }
                                    }
                                }
                            }
                        }

                        SignatureMode.DRAW -> {
                            // Touch Drawing Canvas
                            item {
                                Text(
                                    text = "Finger / Stylus Drawing Pad",
                                    style = MaterialTheme.typography.titleSmall,
                                    color = AdfTextSecondary
                                )
                                Spacer(modifier = Modifier.height(6.dp))

                                Card(
                                    colors = CardDefaults.cardColors(containerColor = Color(0xFFFDFBF7)),
                                    shape = RoundedCornerShape(12.dp),
                                    border = CardDefaults.outlinedCardBorder().copy(brush = androidx.compose.ui.graphics.SolidColor(Color(0xFFE2E8F0))),
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .height(180.dp)
                                        .onSizeChanged { canvasSize = it }
                                ) {
                                    Box(modifier = Modifier.fillMaxSize()) {
                                        Canvas(
                                            modifier = Modifier
                                                .fillMaxSize()
                                                .pointerInput(Unit) {
                                                    detectDragGestures(
                                                        onDragStart = { offset ->
                                                            currentStroke.clear()
                                                            currentStroke.add(offset)
                                                        },
                                                        onDrag = { change, _ ->
                                                            change.consume()
                                                            currentStroke.add(change.position)
                                                        },
                                                        onDragEnd = {
                                                            if (currentStroke.isNotEmpty()) {
                                                                completedStrokes.add(currentStroke.toList())
                                                                currentStroke.clear()
                                                            }
                                                        },
                                                        onDragCancel = {
                                                            currentStroke.clear()
                                                        }
                                                    )
                                                }
                                        ) {
                                            // Baseline guide
                                            drawLine(
                                                color = Color(0xFFE2E8F0),
                                                start = Offset(24f, size.height * 0.75f),
                                                end = Offset(size.width - 24f, size.height * 0.75f),
                                                strokeWidth = 1.5f
                                            )

                                            // Draw finished strokes
                                            for (stroke in completedStrokes) {
                                                if (stroke.size > 1) {
                                                    val path = Path()
                                                    stroke.forEachIndexed { i, pt ->
                                                        if (i == 0) path.moveTo(pt.x, pt.y) else path.lineTo(pt.x, pt.y)
                                                    }
                                                    drawPath(
                                                        path = path,
                                                        color = selectedInkColor.color,
                                                        style = Stroke(
                                                            width = strokeThickness,
                                                            cap = StrokeCap.Round,
                                                            join = StrokeJoin.Round
                                                        )
                                                    )
                                                }
                                            }

                                            // Draw active stroke
                                            if (currentStroke.size > 1) {
                                                val livePath = Path()
                                                currentStroke.forEachIndexed { i, pt ->
                                                    if (i == 0) livePath.moveTo(pt.x, pt.y) else livePath.lineTo(pt.x, pt.y)
                                                }
                                                drawPath(
                                                    path = livePath,
                                                    color = selectedInkColor.color,
                                                    style = Stroke(
                                                        width = strokeThickness,
                                                        cap = StrokeCap.Round,
                                                        join = StrokeJoin.Round
                                                    )
                                                )
                                            }
                                        }

                                        if (completedStrokes.isEmpty() && currentStroke.isEmpty()) {
                                            Text(
                                                text = "✍️ Draw your signature here with finger or stylus",
                                                style = MaterialTheme.typography.bodySmall,
                                                color = Color(0xFF94A3B8),
                                                modifier = Modifier
                                                    .align(Alignment.Center)
                                                    .padding(bottom = 20.dp)
                                            )
                                        }
                                    }
                                }

                                Spacer(modifier = Modifier.height(8.dp))

                                // Clear / Undo buttons
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.End,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    OutlinedButton(
                                        onClick = {
                                            if (completedStrokes.isNotEmpty()) {
                                                completedStrokes.removeAt(completedStrokes.size - 1)
                                            }
                                        },
                                        enabled = completedStrokes.isNotEmpty(),
                                        colors = ButtonDefaults.outlinedButtonColors(contentColor = AdfTextSecondary)
                                    ) {
                                        Icon(Icons.Default.Undo, contentDescription = null, modifier = Modifier.size(14.dp))
                                        Spacer(modifier = Modifier.width(4.dp))
                                        Text("Undo")
                                    }

                                    Spacer(modifier = Modifier.width(8.dp))

                                    OutlinedButton(
                                        onClick = {
                                            completedStrokes.clear()
                                            currentStroke.clear()
                                        },
                                        enabled = completedStrokes.isNotEmpty(),
                                        colors = ButtonDefaults.outlinedButtonColors(contentColor = AdfCoral)
                                    ) {
                                        Icon(Icons.Default.Clear, contentDescription = null, modifier = Modifier.size(14.dp))
                                        Spacer(modifier = Modifier.width(4.dp))
                                        Text("Clear")
                                    }
                                }
                            }
                        }

                        SignatureMode.VAULT -> {
                            if (savedSignatures.isEmpty()) {
                                item {
                                    Surface(
                                        shape = RoundedCornerShape(12.dp),
                                        color = AdfSurfaceCard,
                                        modifier = Modifier.fillMaxWidth()
                                    ) {
                                        Column(
                                            modifier = Modifier.padding(24.dp),
                                            horizontalAlignment = Alignment.CenterHorizontally
                                        ) {
                                            Icon(
                                                imageVector = Icons.Default.FolderOpen,
                                                contentDescription = null,
                                                tint = AdfTextMuted,
                                                modifier = Modifier.size(36.dp)
                                            )
                                            Spacer(modifier = Modifier.height(8.dp))
                                            Text(
                                                text = "No saved signatures in vault",
                                                style = MaterialTheme.typography.titleSmall,
                                                color = AdfTextPrimary
                                            )
                                            Text(
                                                text = "Type or draw a signature above and save it for rapid reuse.",
                                                style = MaterialTheme.typography.bodySmall,
                                                color = AdfTextMuted,
                                                textAlign = TextAlign.Center
                                            )
                                        }
                                    }
                                }
                            } else {
                                items(savedSignatures, key = { it.id }) { sig ->
                                    val isSelected = selectedSavedSignature?.id == sig.id
                                    Surface(
                                        shape = RoundedCornerShape(12.dp),
                                        color = if (isSelected) AdfVibrantBlue.copy(alpha = 0.25f) else AdfSurfaceCard,
                                        border = CardDefaults.outlinedCardBorder().copy(
                                            brush = androidx.compose.ui.graphics.SolidColor(
                                                if (isSelected) AdfElectricCyan else AdfBorderSubtle
                                            )
                                        ),
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .clickable { selectedSavedSignature = sig }
                                    ) {
                                        Row(
                                            modifier = Modifier
                                                .fillMaxWidth()
                                                .padding(12.dp),
                                            verticalAlignment = Alignment.CenterVertically
                                        ) {
                                            // Canvas or text preview
                                            Surface(
                                                color = Color.White,
                                                shape = RoundedCornerShape(8.dp),
                                                modifier = Modifier.size(80.dp, 44.dp)
                                            ) {
                                                val isTyped = sig.pathsJson.contains("TYPED")
                                                if (isTyped) {
                                                    val previewText = try {
                                                        JSONObject(sig.pathsJson).optString("text", sig.title)
                                                    } catch (e: Exception) {
                                                        sig.title
                                                    }
                                                    Box(contentAlignment = Alignment.Center) {
                                                        Text(
                                                            text = previewText,
                                                            fontSize = 14.sp,
                                                            fontStyle = FontStyle.Italic,
                                                            fontWeight = FontWeight.Bold,
                                                            fontFamily = FontFamily.Cursive,
                                                            color = Color(0xFF1E3A8A),
                                                            maxLines = 1
                                                        )
                                                    }
                                                } else {
                                                    Canvas(modifier = Modifier.fillMaxSize()) {
                                                        try {
                                                            val strokes = JSONArray(sig.pathsJson)
                                                            for (s in 0 until strokes.length()) {
                                                                val strokeArr = strokes.getJSONArray(s)
                                                                val path = Path()
                                                                for (p in 0 until strokeArr.length()) {
                                                                    val pt = strokeArr.getJSONObject(p)
                                                                    val px = pt.getDouble("x").toFloat() * size.width
                                                                    val py = pt.getDouble("y").toFloat() * size.height
                                                                    if (p == 0) path.moveTo(px, py) else path.lineTo(px, py)
                                                                }
                                                                drawPath(
                                                                    path = path,
                                                                    color = Color(0xFF1E3A8A),
                                                                    style = Stroke(width = 1.8f)
                                                                )
                                                            }
                                                        } catch (ignored: Exception) {}
                                                    }
                                                }
                                            }

                                            Spacer(modifier = Modifier.width(12.dp))

                                            Column(modifier = Modifier.weight(1f)) {
                                                Text(
                                                    text = sig.title,
                                                    style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold),
                                                    color = AdfTextPrimary
                                                )
                                                Text(
                                                    text = if (sig.pathsJson.contains("TYPED")) "Typed Cursive Signature" else "Freehand Drawn Signature",
                                                    style = MaterialTheme.typography.bodySmall,
                                                    color = AdfTextMuted
                                                )
                                            }

                                            if (isSelected) {
                                                Icon(
                                                    imageVector = Icons.Default.CheckCircle,
                                                    contentDescription = "Selected",
                                                    tint = AdfElectricCyan
                                                )
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }

                    // Common Controls: Ink Color Palette (for Type & Draw modes)
                    if (mode != SignatureMode.VAULT) {
                        item {
                            Text(
                                text = "Ink Color",
                                style = MaterialTheme.typography.titleSmall,
                                color = AdfTextSecondary
                            )
                            Spacer(modifier = Modifier.height(6.dp))
                            Row(
                                horizontalArrangement = Arrangement.spacedBy(12.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                InkColors.forEach { ink ->
                                    val isSelected = selectedInkColor.hex == ink.hex
                                    Box(
                                        modifier = Modifier
                                            .size(36.dp)
                                            .clip(CircleShape)
                                            .background(ink.color)
                                            .border(
                                                width = if (isSelected) 3.dp else 1.dp,
                                                color = if (isSelected) AdfElectricCyan else Color.Transparent,
                                                shape = CircleShape
                                            )
                                            .clickable { selectedInkColor = ink },
                                        contentAlignment = Alignment.Center
                                    ) {
                                        if (isSelected) {
                                            Icon(
                                                imageVector = Icons.Default.Check,
                                                contentDescription = null,
                                                tint = Color.White,
                                                modifier = Modifier.size(16.dp)
                                            )
                                        }
                                    }
                                }
                            }
                        }
                    }

                    // Target Page & Placement Controls (When stamping on active document)
                    if (activeDocumentTitle != null && totalPages > 0) {
                        item {
                            HorizontalDivider(color = AdfBorderSubtle)
                            Spacer(modifier = Modifier.height(4.dp))
                            Text(
                                text = "Apply to Specific Page",
                                style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold),
                                color = AdfTextPrimary
                            )
                            Spacer(modifier = Modifier.height(8.dp))

                            // Page Selector Row
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Text(
                                    text = "Target Page:",
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = AdfTextSecondary
                                )

                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    IconButton(
                                        onClick = { if (targetPage > 0) targetPage-- },
                                        enabled = targetPage > 0
                                    ) {
                                        Icon(
                                            imageVector = Icons.Default.Remove,
                                            contentDescription = "Previous Page",
                                            tint = if (targetPage > 0) AdfTextPrimary else AdfTextMuted
                                        )
                                    }

                                    Surface(
                                        shape = RoundedCornerShape(8.dp),
                                        color = AdfSurfaceCard,
                                        border = CardDefaults.outlinedCardBorder().copy(brush = androidx.compose.ui.graphics.SolidColor(AdfBorderSubtle)),
                                        modifier = Modifier.padding(horizontal = 4.dp)
                                    ) {
                                        Text(
                                            text = "Page ${targetPage + 1} of $totalPages",
                                            style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Bold),
                                            color = AdfElectricCyan,
                                            modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp)
                                        )
                                    }

                                    IconButton(
                                        onClick = { if (targetPage < totalPages - 1) targetPage++ },
                                        enabled = targetPage < totalPages - 1
                                    ) {
                                        Icon(
                                            imageVector = Icons.Default.Add,
                                            contentDescription = "Next Page",
                                            tint = if (targetPage < totalPages - 1) AdfTextPrimary else AdfTextMuted
                                        )
                                    }
                                }
                            }

                            Spacer(modifier = Modifier.height(10.dp))

                            // Placement Position Presets
                            Text(
                                text = "Position on Page",
                                style = MaterialTheme.typography.bodySmall,
                                color = AdfTextSecondary
                            )
                            Spacer(modifier = Modifier.height(6.dp))
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                listOf(
                                    "BOTTOM_RIGHT" to "Bottom Right",
                                    "BOTTOM_LEFT" to "Bottom Left",
                                    "CENTER" to "Center",
                                    "TOP_RIGHT" to "Top Right"
                                ).forEach { (key, label) ->
                                    val isSelected = placementPreset == key
                                    FilterChip(
                                        selected = isSelected,
                                        onClick = { placementPreset = key },
                                        label = { Text(label, fontSize = 11.sp) },
                                        colors = FilterChipDefaults.filterChipColors(
                                            selectedContainerColor = AdfElectricCyan,
                                            selectedLabelColor = AdfMidnightBackground,
                                            containerColor = AdfSurfaceCard,
                                            labelColor = AdfTextSecondary
                                        ),
                                        border = FilterChipDefaults.filterChipBorder(
                                            enabled = true,
                                            selected = isSelected,
                                            borderColor = if (isSelected) AdfElectricCyan else AdfBorderSubtle
                                        )
                                    )
                                }
                            }
                        }
                    }

                    // Save to Vault Checkbox
                    if (mode != SignatureMode.VAULT) {
                        item {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                modifier = Modifier.clickable { saveToVault = !saveToVault }
                            ) {
                                Checkbox(
                                    checked = saveToVault,
                                    onCheckedChange = { saveToVault = it },
                                    colors = CheckboxDefaults.colors(
                                        checkedColor = AdfElectricCyan,
                                        checkmarkColor = AdfMidnightBackground
                                    )
                                )
                                Spacer(modifier = Modifier.width(6.dp))
                                Text(
                                    text = "Save this signature to Vault for future reuse",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = AdfTextPrimary
                                )
                            }
                        }
                    }
                }

                Spacer(modifier = Modifier.height(14.dp))

                // Bottom Action Buttons
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    OutlinedButton(
                        onClick = onDismiss,
                        colors = ButtonDefaults.outlinedButtonColors(contentColor = AdfTextSecondary)
                    ) {
                        Text("Cancel")
                    }

                    Button(
                        onClick = {
                            val (posX, posY) = when (placementPreset) {
                                "BOTTOM_RIGHT" -> 0.60f to 0.82f
                                "BOTTOM_LEFT" -> 0.12f to 0.82f
                                "CENTER" -> 0.35f to 0.50f
                                "TOP_RIGHT" -> 0.60f to 0.15f
                                else -> 0.60f to 0.82f
                            }

                            when (mode) {
                                SignatureMode.TYPE -> {
                                    val name = typedName.ifBlank { "Ishant Kohli" }
                                    viewModel.stampSignature(
                                        pageIndex = targetPage,
                                        isTyped = true,
                                        typedText = name,
                                        strokePathsJson = "",
                                        colorHex = selectedInkColor.hex,
                                        posX = posX,
                                        posY = posY,
                                        saveToVault = saveToVault,
                                        vaultTitle = "$name ($selectedStyle)"
                                    )
                                    onDismiss()
                                }
                                SignatureMode.DRAW -> {
                                    if (completedStrokes.isNotEmpty() && canvasSize.width > 0 && canvasSize.height > 0) {
                                        val strokesJson = JSONArray()
                                        for (stroke in completedStrokes) {
                                            val strokeArr = JSONArray()
                                            for (pt in stroke) {
                                                val obj = JSONObject()
                                                obj.put("x", (pt.x / canvasSize.width).toDouble())
                                                obj.put("y", (pt.y / canvasSize.height).toDouble())
                                                strokeArr.put(obj)
                                            }
                                            strokesJson.put(strokeArr)
                                        }

                                        viewModel.stampSignature(
                                            pageIndex = targetPage,
                                            isTyped = false,
                                            typedText = "",
                                            strokePathsJson = strokesJson.toString(),
                                            colorHex = selectedInkColor.hex,
                                            posX = posX,
                                            posY = posY,
                                            saveToVault = saveToVault,
                                            vaultTitle = "Drawn Signature"
                                        )
                                        onDismiss()
                                    }
                                }
                                SignatureMode.VAULT -> {
                                    val sig = selectedSavedSignature
                                    if (sig != null) {
                                        val isTyped = sig.pathsJson.contains("TYPED")
                                        val text = if (isTyped) {
                                            try {
                                                JSONObject(sig.pathsJson).optString("text", sig.title)
                                            } catch (e: Exception) {
                                                sig.title
                                            }
                                        } else ""

                                        viewModel.stampSignature(
                                            pageIndex = targetPage,
                                            isTyped = isTyped,
                                            typedText = text,
                                            strokePathsJson = if (isTyped) "" else sig.pathsJson,
                                            colorHex = selectedInkColor.hex,
                                            posX = posX,
                                            posY = posY,
                                            saveToVault = false
                                        )
                                        onDismiss()
                                    }
                                }
                            }
                        },
                        enabled = when (mode) {
                            SignatureMode.TYPE -> typedName.isNotBlank()
                            SignatureMode.DRAW -> completedStrokes.isNotEmpty()
                            SignatureMode.VAULT -> selectedSavedSignature != null
                        },
                        shape = RoundedCornerShape(12.dp),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = AdfElectricCyan,
                            contentColor = AdfMidnightBackground
                        ),
                        modifier = Modifier.testTag("apply_signature_button")
                    ) {
                        Icon(
                            imageVector = Icons.Default.CheckCircle,
                            contentDescription = null,
                            modifier = Modifier.size(18.dp)
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            text = if (activeDocumentTitle != null) "Stamp on Page ${targetPage + 1}" else "Save to Vault",
                            fontWeight = FontWeight.Bold
                        )
                    }
                }
            }
        }
    }
}

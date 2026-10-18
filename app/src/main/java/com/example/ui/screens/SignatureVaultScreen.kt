package com.example.ui.screens

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Draw
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.data.local.SignatureEntity
import com.example.ui.AppScreen
import com.example.ui.PdfViewModel
import com.example.ui.theme.*
import org.json.JSONArray
import org.json.JSONObject

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SignatureVaultScreen(viewModel: PdfViewModel) {
    val signatures by viewModel.signatures.collectAsStateWithLifecycle()
    var signatureTitle by remember { mutableStateOf("My Official Signature") }
    var penColor by remember { mutableStateOf(Color(0xFF1E3A8A)) } // Deep ink blue

    // Multi-stroke drawing state
    val completedStrokes = remember { mutableStateListOf<List<Offset>>() }
    val currentStroke = remember { mutableStateListOf<Offset>() }
    var canvasSize by remember { mutableStateOf(IntSize(1, 1)) }

    BackHandler {
        viewModel.navigateTo(AppScreen.HOME)
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            text = "Fill & Sign Vault",
                            style = MaterialTheme.typography.titleMedium.copy(
                                fontWeight = FontWeight.Bold,
                                letterSpacing = 0.5.sp
                            ),
                            color = AdfTextPrimary
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Surface(
                            shape = RoundedCornerShape(4.dp),
                            color = AdfElectricCyan.copy(alpha = 0.15f)
                        ) {
                            Text(
                                text = "ENCRYPTED",
                                color = AdfElectricCyan,
                                fontSize = 8.sp,
                                fontWeight = FontWeight.ExtraBold,
                                modifier = Modifier.padding(horizontal = 4.dp, vertical = 2.dp)
                            )
                        }
                    }
                },
                navigationIcon = {
                    IconButton(onClick = { viewModel.navigateTo(AppScreen.HOME) }) {
                        com.example.ui.components.AdfIcon.ChevronLeft(
                            size = 22.dp,
                            color = AdfTextPrimary
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = AdfMidnightBackground
                )
            )
        },
        containerColor = AdfMidnightBackground
    ) { padding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(horizontal = 16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
            contentPadding = PaddingValues(bottom = 32.dp)
        ) {
            // Draw Signature Card
            item {
                Text(
                    text = "Draw Digital Signature",
                    style = MaterialTheme.typography.titleSmall,
                    color = AcrobatTextSecondary,
                    modifier = Modifier.padding(top = 8.dp)
                )

                Spacer(modifier = Modifier.height(6.dp))

                OutlinedTextField(
                    value = signatureTitle,
                    onValueChange = { signatureTitle = it },
                    label = { Text("Signature Label") },
                    singleLine = true,
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedContainerColor = AcrobatDarkSurface,
                        unfocusedContainerColor = AcrobatDarkSurface,
                        focusedTextColor = AcrobatTextPrimary,
                        unfocusedTextColor = AcrobatTextPrimary,
                        focusedBorderColor = CrimsonPrimary,
                        unfocusedBorderColor = AcrobatDarkBorder
                    ),
                    modifier = Modifier.fillMaxWidth()
                )

                Spacer(modifier = Modifier.height(10.dp))

                // Smooth Touch Pad Canvas
                Card(
                    colors = CardDefaults.cardColors(containerColor = Color.White),
                    shape = RoundedCornerShape(12.dp),
                    border = CardDefaults.outlinedCardBorder().copy(brush = androidx.compose.ui.graphics.SolidColor(AcrobatDarkBorder)),
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(200.dp)
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
                                start = Offset(30f, size.height * 0.75f),
                                end = Offset(size.width - 30f, size.height * 0.75f),
                                strokeWidth = 1.5f
                            )

                            // Draw completed strokes
                            for (stroke in completedStrokes) {
                                if (stroke.size > 1) {
                                    val path = Path()
                                    stroke.forEachIndexed { i, pt ->
                                        if (i == 0) path.moveTo(pt.x, pt.y) else path.lineTo(pt.x, pt.y)
                                    }
                                    drawPath(
                                        path = path,
                                        color = penColor,
                                        style = Stroke(
                                            width = 3.5f,
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
                                    color = penColor,
                                    style = Stroke(
                                        width = 3.5f,
                                        cap = StrokeCap.Round,
                                        join = StrokeJoin.Round
                                    )
                                )
                            }
                        }

                        if (completedStrokes.isEmpty() && currentStroke.isEmpty()) {
                            Text(
                                text = "✍️ Sign here with your finger or stylus",
                                style = MaterialTheme.typography.bodySmall,
                                color = Color(0xFF94A3B8),
                                modifier = Modifier
                                    .align(Alignment.Center)
                                    .padding(bottom = 20.dp)
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.height(10.dp))

                // Canvas controls: Ink Color, Clear, Save
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        listOf(
                            Color(0xFF1E3A8A) to "Blue Ink",
                            Color(0xFF0F172A) to "Black Ink",
                            Color(0xFFDC2626) to "Red Ink"
                        ).forEach { (color, _) ->
                            Box(
                                modifier = Modifier
                                    .size(28.dp)
                                    .clip(CircleShape)
                                    .background(color)
                                    .border(
                                        width = if (penColor == color) 2.5.dp else 1.dp,
                                        color = if (penColor == color) CrimsonAccent else Color.Transparent,
                                        shape = CircleShape
                                    )
                                    .clickable { penColor = color }
                            )
                        }
                    }

                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        OutlinedButton(
                            onClick = {
                                completedStrokes.clear()
                                currentStroke.clear()
                            },
                            enabled = completedStrokes.isNotEmpty(),
                            colors = ButtonDefaults.outlinedButtonColors(contentColor = AdfTextSecondary)
                        ) {
                            com.example.ui.components.AdfIcon.Close(size = 14.dp, color = AdfTextSecondary)
                            Spacer(modifier = Modifier.width(4.dp))
                            Text("Clear")
                        }

                        Button(
                            onClick = {
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
                                    viewModel.saveSignature(
                                        title = signatureTitle.ifBlank { "Signature" },
                                        pathsJson = strokesJson.toString()
                                    )
                                    completedStrokes.clear()
                                }
                            },
                            enabled = completedStrokes.isNotEmpty(),
                            colors = ButtonDefaults.buttonColors(
                                containerColor = AdfElectricCyan,
                                contentColor = AdfMidnightBackground
                            ),
                            shape = RoundedCornerShape(10.dp),
                            modifier = Modifier.testTag("save_signature_button")
                        ) {
                            com.example.ui.components.AdfIcon.SignatureVault(
                                size = 16.dp,
                                primaryColor = AdfMidnightBackground,
                                accentColor = AdfVibrantBlue
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("Save to Vault", fontWeight = FontWeight.Bold)
                        }
                    }
                }
            }

            // Saved Signatures List
            item {
                Text(
                    text = "Saved Signatures (${signatures.size})",
                    style = MaterialTheme.typography.titleSmall,
                    color = AcrobatTextSecondary,
                    modifier = Modifier.padding(top = 16.dp)
                )
            }

            if (signatures.isEmpty()) {
                item {
                    Text(
                        text = "No saved signatures yet. Draw and save one above to stamp it on any PDF.",
                        style = MaterialTheme.typography.bodySmall,
                        color = AcrobatTextMuted,
                        modifier = Modifier.padding(vertical = 12.dp)
                    )
                }
            } else {
                items(signatures, key = { it.id }) { sig ->
                    SavedSignatureCard(
                        signature = sig,
                        onDelete = { viewModel.deleteSignature(sig.id) }
                    )
                }
            }
        }
    }
}

@Composable
fun SavedSignatureCard(
    signature: SignatureEntity,
    onDelete: () -> Unit
) {
    Card(
        colors = CardDefaults.cardColors(containerColor = AcrobatDarkSurface),
        shape = RoundedCornerShape(12.dp),
        border = CardDefaults.outlinedCardBorder().copy(brush = androidx.compose.ui.graphics.SolidColor(AcrobatDarkBorder)),
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Preview canvas
            Surface(
                color = Color.White,
                shape = RoundedCornerShape(8.dp),
                modifier = Modifier.size(100.dp, 50.dp)
            ) {
                Canvas(modifier = Modifier.fillMaxSize()) {
                    try {
                        val strokes = JSONArray(signature.pathsJson)
                        for (s in 0 until strokes.length()) {
                            val strokeArr = strokes.getJSONArray(s)
                            val path = Path()
                            for (p in 0 until strokeArr.length()) {
                                val pt = strokeArr.getJSONObject(p)
                                val x = pt.getDouble("x").toFloat() * size.width
                                val y = pt.getDouble("y").toFloat() * size.height
                                if (p == 0) path.moveTo(x, y) else path.lineTo(x, y)
                            }
                            drawPath(
                                path = path,
                                color = Color(0xFF1E3A8A),
                                style = Stroke(
                                    width = 2f,
                                    cap = StrokeCap.Round,
                                    join = StrokeJoin.Round
                                )
                            )
                        }
                    } catch (ignored: Exception) {}
                }
            }

            Spacer(modifier = Modifier.width(14.dp))

            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = signature.title,
                    style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.SemiBold),
                    color = AcrobatTextPrimary
                )
                Text(
                    text = "Ready to stamp on any page",
                    style = MaterialTheme.typography.bodySmall,
                    color = AcrobatTextMuted
                )
            }

            IconButton(onClick = onDelete) {
                com.example.ui.components.AdfIcon.Delete(
                    size = 20.dp,
                    color = AdfCoral
                )
            }
        }
    }
}

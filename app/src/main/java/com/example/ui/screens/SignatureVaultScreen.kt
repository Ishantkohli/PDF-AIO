package com.example.ui.screens

import androidx.activity.compose.BackHandler
import androidx.compose.animation.*
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.data.local.SignatureEntity
import com.example.ui.AppScreen
import com.example.ui.PdfViewModel
import com.example.ui.components.DigitalSignatureDialog
import com.example.ui.components.InkColors
import com.example.ui.components.SignatureCursiveStyle
import com.example.ui.theme.*
import org.json.JSONArray
import org.json.JSONObject

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SignatureVaultScreen(viewModel: PdfViewModel) {
    val signatures by viewModel.signatures.collectAsStateWithLifecycle()
    val documents by viewModel.documents.collectAsStateWithLifecycle()
    val currentDoc by viewModel.currentDocument.collectAsStateWithLifecycle()
    val currentPage by viewModel.currentPage.collectAsStateWithLifecycle()
    val totalPages by viewModel.totalPages.collectAsStateWithLifecycle()

    var showSignDialog by remember { mutableStateOf(false) }
    var stampingSignature by remember { mutableStateOf<SignatureEntity?>(null) }

    // Quick inline typed signature creator states
    var quickTypedName by remember { mutableStateOf("") }
    var selectedStyle by remember { mutableStateOf(SignatureCursiveStyle.CURSIVE_FLOW) }
    var selectedInkColor by remember { mutableStateOf(InkColors[0]) }

    BackHandler {
        viewModel.navigateTo(AppScreen.HOME)
    }

    if (showSignDialog) {
        DigitalSignatureDialog(
            viewModel = viewModel,
            activeDocumentTitle = currentDoc?.title,
            currentPageIndex = currentPage,
            totalPages = totalPages,
            savedSignatures = signatures,
            onDismiss = { showSignDialog = false }
        )
    }

    // Modal dialog to stamp a saved signature onto a document
    if (stampingSignature != null) {
        val targetSig = stampingSignature!!
        var selectedDocId by remember { mutableLongStateOf(currentDoc?.id ?: documents.firstOrNull()?.id ?: 0L) }
        val targetDoc = documents.find { it.id == selectedDocId } ?: currentDoc
        val docPages = targetDoc?.pageCount ?: 1
        var stampTargetPage by remember { mutableIntStateOf(0) }
        var stampPlacement by remember { mutableStateOf("BOTTOM_RIGHT") }

        AlertDialog(
            onDismissRequest = { stampingSignature = null },
            containerColor = AdfMidnightBackground,
            title = {
                Text(
                    text = "Stamp Signature to Document",
                    color = AdfTextPrimary,
                    fontWeight = FontWeight.Bold,
                    fontSize = 17.sp
                )
            },
            text = {
                Column(
                    modifier = Modifier.fillMaxWidth(),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Text(
                        text = "Signature: \"${targetSig.title}\"",
                        color = AdfElectricCyan,
                        fontSize = 13.sp,
                        fontWeight = FontWeight.SemiBold
                    )

                    // Target Document Picker
                    Text(text = "Target PDF Document:", color = AdfTextSecondary, fontSize = 12.sp)
                    LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        items(documents) { doc ->
                            val isSel = doc.id == selectedDocId
                            Surface(
                                shape = RoundedCornerShape(8.dp),
                                color = if (isSel) AdfElectricCyan.copy(alpha = 0.2f) else AdfSurfaceCard,
                                border = CardDefaults.outlinedCardBorder().copy(
                                    brush = androidx.compose.ui.graphics.SolidColor(
                                        if (isSel) AdfElectricCyan else AdfBorderSubtle
                                    )
                                ),
                                modifier = Modifier.clickable { selectedDocId = doc.id }
                            ) {
                                Text(
                                    text = doc.title.take(18),
                                    fontSize = 11.sp,
                                    color = if (isSel) AdfElectricCyan else AdfTextPrimary,
                                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp)
                                )
                            }
                        }
                    }

                    // Target Page Number
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(text = "Stamp onto Page:", color = AdfTextSecondary, fontSize = 12.sp)
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            IconButton(
                                onClick = { stampTargetPage = (stampTargetPage - 1).coerceAtLeast(0) },
                                enabled = stampTargetPage > 0
                            ) {
                                Icon(Icons.Default.Remove, contentDescription = "Prev Page", tint = AdfTextPrimary)
                            }
                            Text(
                                text = "Page ${stampTargetPage + 1} of $docPages",
                                color = AdfTextPrimary,
                                fontWeight = FontWeight.Bold,
                                fontSize = 12.sp
                            )
                            IconButton(
                                onClick = { stampTargetPage = (stampTargetPage + 1).coerceAtMost(docPages - 1) },
                                enabled = stampTargetPage < docPages - 1
                            ) {
                                Icon(Icons.Default.Add, contentDescription = "Next Page", tint = AdfTextPrimary)
                            }
                        }
                    }

                    // Placement Position
                    Text(text = "Placement Preset:", color = AdfTextSecondary, fontSize = 12.sp)
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        listOf(
                            "BOTTOM_RIGHT" to "Bottom Right",
                            "BOTTOM_LEFT" to "Bottom Left",
                            "CENTER" to "Center"
                        ).forEach { (preset, label) ->
                            val isSelected = stampPlacement == preset
                            Surface(
                                shape = RoundedCornerShape(8.dp),
                                color = if (isSelected) AdfElectricCyan else AdfSurfaceCard,
                                modifier = Modifier
                                    .weight(1f)
                                    .clickable { stampPlacement = preset }
                            ) {
                                Text(
                                    text = label,
                                    color = if (isSelected) AdfMidnightBackground else AdfTextPrimary,
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.Bold,
                                    textAlign = TextAlign.Center,
                                    modifier = Modifier.padding(vertical = 8.dp)
                                )
                            }
                        }
                    }
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        val (posX, posY) = when (stampPlacement) {
                            "BOTTOM_RIGHT" -> 0.60f to 0.82f
                            "BOTTOM_LEFT" -> 0.12f to 0.82f
                            "CENTER" -> 0.35f to 0.50f
                            else -> 0.60f to 0.82f
                        }

                        val isTyped = targetSig.pathsJson.contains("TYPED")
                        val text = if (isTyped) {
                            try {
                                JSONObject(targetSig.pathsJson).optString("text", targetSig.title)
                            } catch (e: Exception) {
                                targetSig.title
                            }
                        } else ""

                        if (targetDoc != null && currentDoc?.id != targetDoc.id) {
                            viewModel.openDocument(targetDoc)
                        }

                        viewModel.stampSignature(
                            pageIndex = stampTargetPage,
                            isTyped = isTyped,
                            typedText = text,
                            strokePathsJson = if (isTyped) "" else targetSig.pathsJson,
                            colorHex = 0xFF1E3A8AL,
                            posX = posX,
                            posY = posY,
                            saveToVault = false
                        )
                        stampingSignature = null
                        viewModel.navigateTo(AppScreen.READER)
                    },
                    colors = ButtonDefaults.buttonColors(
                        containerColor = AdfElectricCyan,
                        contentColor = AdfMidnightBackground
                    )
                ) {
                    Text("Apply & Open PDF", fontWeight = FontWeight.Bold)
                }
            },
            dismissButton = {
                TextButton(onClick = { stampingSignature = null }) {
                    Text("Cancel", color = AdfTextSecondary)
                }
            }
        )
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
                                text = "100% LOCAL",
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
                actions = {
                    FilledTonalButton(
                        onClick = { showSignDialog = true },
                        colors = ButtonDefaults.filledTonalButtonColors(
                            containerColor = AdfElectricCyan.copy(alpha = 0.2f),
                            contentColor = AdfElectricCyan
                        ),
                        shape = RoundedCornerShape(10.dp),
                        contentPadding = PaddingValues(horizontal = 10.dp, vertical = 6.dp)
                    ) {
                        Icon(Icons.Default.Draw, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("Signature Studio", fontSize = 12.sp, fontWeight = FontWeight.Bold)
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
            // Quick Studio Launcher Banner
            item {
                Surface(
                    shape = RoundedCornerShape(16.dp),
                    color = AdfSurfaceCard,
                    border = CardDefaults.outlinedCardBorder().copy(
                        brush = androidx.compose.ui.graphics.SolidColor(AdfBorderSubtle)
                    ),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = "Digital Signature Vault",
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 15.sp,
                                    color = AdfTextPrimary
                                )
                                Text(
                                    text = "Create vector signatures by finger, stylus, or cursive typing.",
                                    fontSize = 12.sp,
                                    color = AdfTextSecondary
                                )
                            }
                            Button(
                                onClick = { showSignDialog = true },
                                colors = ButtonDefaults.buttonColors(
                                    containerColor = AdfElectricCyan,
                                    contentColor = AdfMidnightBackground
                                ),
                                shape = RoundedCornerShape(10.dp)
                            ) {
                                Icon(Icons.Default.Add, contentDescription = null, modifier = Modifier.size(16.dp))
                                Spacer(modifier = Modifier.width(4.dp))
                                Text("Create", fontWeight = FontWeight.Bold, fontSize = 12.sp)
                            }
                        }
                    }
                }
            }

            // Quick In-Place Cursive Typed Generator
            item {
                Card(
                    colors = CardDefaults.cardColors(containerColor = AdfSurfaceCard),
                    shape = RoundedCornerShape(16.dp),
                    border = CardDefaults.outlinedCardBorder().copy(
                        brush = androidx.compose.ui.graphics.SolidColor(AdfBorderSubtle)
                    ),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(16.dp),
                        verticalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        Text(
                            text = "Quick Cursive Generator",
                            style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold),
                            color = AdfTextPrimary
                        )

                        OutlinedTextField(
                            value = quickTypedName,
                            onValueChange = { quickTypedName = it },
                            label = { Text("Signer Full Name") },
                            placeholder = { Text("e.g. Ishant Kohli") },
                            singleLine = true,
                            colors = OutlinedTextFieldDefaults.colors(
                                focusedContainerColor = AdfMidnightBackground,
                                unfocusedContainerColor = AdfMidnightBackground,
                                focusedTextColor = AdfTextPrimary,
                                unfocusedTextColor = AdfTextPrimary,
                                focusedBorderColor = AdfElectricCyan,
                                unfocusedBorderColor = AdfBorderSubtle
                            ),
                            modifier = Modifier
                                .fillMaxWidth()
                                .testTag("vault_quick_typed_input")
                        )

                        // Style choices
                        LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            items(SignatureCursiveStyle.values()) { style ->
                                val isSelected = selectedStyle == style
                                Surface(
                                    shape = RoundedCornerShape(8.dp),
                                    color = if (isSelected) AdfElectricCyan.copy(alpha = 0.2f) else AdfMidnightBackground,
                                    border = CardDefaults.outlinedCardBorder().copy(
                                        brush = androidx.compose.ui.graphics.SolidColor(
                                            if (isSelected) AdfElectricCyan else AdfBorderSubtle
                                        )
                                    ),
                                    modifier = Modifier.clickable { selectedStyle = style }
                                ) {
                                    Text(
                                        text = style.label,
                                        fontSize = 11.sp,
                                        fontStyle = style.fontStyle,
                                        fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
                                        color = if (isSelected) AdfElectricCyan else AdfTextPrimary,
                                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp)
                                    )
                                }
                            }
                        }

                        // Save Quick Signature Button
                        Button(
                            onClick = {
                                if (quickTypedName.isNotBlank()) {
                                    val typedObj = JSONObject()
                                    typedObj.put("type", "TYPED")
                                    typedObj.put("text", quickTypedName.trim())
                                    typedObj.put("style", selectedStyle.name)
                                    typedObj.put("colorHex", selectedInkColor.hex)
                                    viewModel.saveSignature(
                                        title = quickTypedName.trim(),
                                        pathsJson = typedObj.toString()
                                    )
                                    quickTypedName = ""
                                }
                            },
                            enabled = quickTypedName.isNotBlank(),
                            colors = ButtonDefaults.buttonColors(
                                containerColor = AdfElectricCyan,
                                contentColor = AdfMidnightBackground
                            ),
                            shape = RoundedCornerShape(10.dp),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Icon(Icons.Default.BookmarkAdd, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("Save Quick Signature to Vault", fontWeight = FontWeight.Bold)
                        }
                    }
                }
            }

            // Saved Signatures Header
            item {
                Row(
                    modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "Saved Signatures (${signatures.size})",
                        style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold),
                        color = AdfTextPrimary
                    )
                    Text(
                        text = "Tap 'Stamp' to apply to PDF",
                        style = MaterialTheme.typography.bodySmall,
                        color = AdfTextMuted
                    )
                }
            }

            if (signatures.isEmpty()) {
                item {
                    Surface(
                        shape = RoundedCornerShape(12.dp),
                        color = AdfSurfaceCard,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(
                            modifier = Modifier.padding(28.dp),
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            Icon(
                                imageVector = Icons.Default.Draw,
                                contentDescription = null,
                                tint = AdfTextMuted,
                                modifier = Modifier.size(40.dp)
                            )
                            Spacer(modifier = Modifier.height(10.dp))
                            Text(
                                text = "No saved signatures yet.",
                                style = MaterialTheme.typography.bodyMedium,
                                color = AdfTextSecondary
                            )
                            Spacer(modifier = Modifier.height(4.dp))
                            Text(
                                text = "Use the quick generator above or launch Signature Studio to draw.",
                                style = MaterialTheme.typography.bodySmall,
                                color = AdfTextMuted,
                                textAlign = TextAlign.Center
                            )
                        }
                    }
                }
            } else {
                items(signatures, key = { it.id }) { sig ->
                    SavedSignatureCard(
                        signature = sig,
                        onStampToPdf = { stampingSignature = sig },
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
    onStampToPdf: () -> Unit,
    onDelete: () -> Unit
) {
    val isTyped = signature.pathsJson.contains("TYPED")

    Card(
        colors = CardDefaults.cardColors(containerColor = AdfSurfaceCard),
        shape = RoundedCornerShape(14.dp),
        border = CardDefaults.outlinedCardBorder().copy(
            brush = androidx.compose.ui.graphics.SolidColor(AdfBorderSubtle)
        ),
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(14.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Preview surface (cream parchment backing)
            Surface(
                color = Color(0xFFFDFBF7),
                shape = RoundedCornerShape(8.dp),
                border = CardDefaults.outlinedCardBorder().copy(
                    brush = androidx.compose.ui.graphics.SolidColor(Color(0xFFE2E8F0))
                ),
                modifier = Modifier.size(96.dp, 52.dp)
            ) {
                if (isTyped) {
                    val previewText = try {
                        JSONObject(signature.pathsJson).optString("text", signature.title)
                    } catch (e: Exception) {
                        signature.title
                    }
                    Box(
                        modifier = Modifier.fillMaxSize().padding(horizontal = 4.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = previewText,
                            fontSize = 15.sp,
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
            }

            Spacer(modifier = Modifier.width(14.dp))

            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = signature.title,
                    style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold),
                    color = AdfTextPrimary
                )
                Text(
                    text = if (isTyped) "Typed Cursive Signature" else "Freehand Drawn Signature",
                    style = MaterialTheme.typography.bodySmall,
                    color = AdfTextSecondary
                )
            }

            // Quick Stamp button
            FilledTonalButton(
                onClick = onStampToPdf,
                colors = ButtonDefaults.filledTonalButtonColors(
                    containerColor = AdfElectricCyan.copy(alpha = 0.2f),
                    contentColor = AdfElectricCyan
                ),
                shape = RoundedCornerShape(8.dp),
                contentPadding = PaddingValues(horizontal = 8.dp, vertical = 4.dp)
            ) {
                Icon(Icons.Default.PostAdd, contentDescription = null, modifier = Modifier.size(15.dp))
                Spacer(modifier = Modifier.width(4.dp))
                Text("Stamp", fontSize = 11.sp, fontWeight = FontWeight.Bold)
            }

            Spacer(modifier = Modifier.width(4.dp))

            IconButton(onClick = onDelete) {
                Icon(
                    imageVector = Icons.Default.DeleteOutline,
                    contentDescription = "Delete",
                    tint = AdfCoral,
                    modifier = Modifier.size(20.dp)
                )
            }
        }
    }
}

package com.example.ui.components

import android.widget.Toast
import androidx.compose.animation.core.*
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
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
import androidx.compose.ui.draw.scale
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.ui.AppScreen
import com.example.ui.theme.*
import com.example.widget.*

data class WidgetOption(
    val id: String,
    val title: String,
    val subtitle: String,
    val sizeLabel: String,
    val providerClass: Class<*>,
    val badgeColor: Color
)

@Composable
fun WidgetStudioCard(
    modifier: Modifier = Modifier,
    onNavigateToScreen: (AppScreen) -> Unit = {},
    onSelectToolTab: (Int) -> Unit = {}
) {
    val context = LocalContext.current
    var selectedWidgetId by remember { mutableStateOf("scanner") }
    var isFlashSimulated by remember { mutableStateOf(false) }

    // Infinite animation transition for interactive preview effects
    val infiniteTransition = rememberInfiniteTransition(label = "WidgetAnimation")
    
    // Laser line scanning offset (0f to 1f) for Scanner widget
    val laserOffset by infiniteTransition.animateFloat(
        initialValue = 0.05f,
        targetValue = 0.95f,
        animationSpec = infiniteRepeatable(
            animation = tween(1800, easing = LinearEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "LaserOffset"
    )

    // Pulsing scale for shoot / action buttons
    val pulseScale by infiniteTransition.animateFloat(
        initialValue = 0.96f,
        targetValue = 1.04f,
        animationSpec = infiniteRepeatable(
            animation = tween(1000, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "PulseScale"
    )

    val widgetOptions = listOf(
        WidgetOption("all_in_one", "PDF AIO Suite", "Interactive 6-Tool Quick Dock", "4 x 2", PdfAioAllInOneWidgetProvider::class.java, AdfElectricCyan),
        WidgetOption("scanner", "Scanner", "Live Viewfinder Camera & Shoot", "2 x 2", ScanDocumentWidgetProvider::class.java, AdfCoral),
        WidgetOption("pdf_to_word", "PDF to Word", "Convert PDF → DOCX", "2 x 2", PdfToWordWidgetProvider::class.java, AdfEmerald),
        WidgetOption("word_to_pdf", "Word to PDF", "Compile DOCX → PDF", "2 x 2", WordToPdfWidgetProvider::class.java, Color(0xFF60A5FA)),
        WidgetOption("img_to_pdf", "Images to PDF", "Compile Photos → PDF", "2 x 2", ImagesToPdfWidgetProvider::class.java, AdfCyberViolet),
        WidgetOption("pdf_to_img", "PDF to Images", "Extract PNG Images", "2 x 2", PdfToImagesWidgetProvider::class.java, AdfAmber),
        WidgetOption("developer", "Developer", "60 FPS Telemetry & Build Info", "2 x 2", DeveloperWidgetProvider::class.java, Color(0xFFF38BA8))
    )

    Card(
        shape = RoundedCornerShape(18.dp),
        colors = CardDefaults.cardColors(containerColor = AdfSurfaceCard),
        elevation = CardDefaults.cardElevation(defaultElevation = 3.dp),
        modifier = modifier
            .fillMaxWidth()
            .border(width = 1.dp, brush = Brush.linearGradient(listOf(AdfElectricCyan.copy(alpha = 0.5f), AdfBorderSubtle)), shape = RoundedCornerShape(18.dp))
            .testTag("widget_studio_card")
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(14.dp)
        ) {
            // Header Row
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(
                        contentAlignment = Alignment.Center,
                        modifier = Modifier
                            .size(38.dp)
                            .clip(CircleShape)
                            .background(AdfElectricCyan.copy(alpha = 0.15f))
                            .border(1.dp, AdfElectricCyan.copy(alpha = 0.4f), CircleShape)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Widgets,
                            contentDescription = "Widgets Studio",
                            tint = AdfElectricCyan,
                            modifier = Modifier.size(22.dp)
                        )
                    }
                    Spacer(modifier = Modifier.width(10.dp))
                    Column {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(
                                text = "Interactive Home Widgets Studio",
                                color = Color.White,
                                fontSize = 15.sp,
                                fontWeight = FontWeight.ExtraBold
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Surface(
                                shape = RoundedCornerShape(4.dp),
                                color = AdfElectricCyan.copy(alpha = 0.15f)
                            ) {
                                Text(
                                    text = "7 WIDGETS",
                                    color = AdfElectricCyan,
                                    fontSize = 8.5.sp,
                                    fontWeight = FontWeight.ExtraBold,
                                    modifier = Modifier.padding(horizontal = 4.dp, vertical = 1.dp)
                                )
                            }
                        }
                        Text(
                            text = "Tap to test live interactive widgets & pin directly to your phone home screen",
                            color = AdfTextMuted,
                            fontSize = 11.sp
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(12.dp))

            // Widget Selector Tabs Row
            LazyRow(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                contentPadding = PaddingValues(end = 4.dp)
            ) {
                items(widgetOptions) { item ->
                    val isSelected = selectedWidgetId == item.id
                    Surface(
                        onClick = { selectedWidgetId = item.id },
                        shape = RoundedCornerShape(10.dp),
                        color = if (isSelected) item.badgeColor.copy(alpha = 0.2f) else Color(0xFF1E1E2E),
                        border = BorderStroke(
                            width = if (isSelected) 1.5.dp else 0.5.dp,
                            color = if (isSelected) item.badgeColor else AdfBorderSubtle
                        ),
                        modifier = Modifier.testTag("widget_tab_${item.id}")
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp)
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(7.dp)
                                    .clip(CircleShape)
                                    .background(if (isSelected) item.badgeColor else AdfTextMuted)
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(
                                text = item.title,
                                color = if (isSelected) item.badgeColor else Color.White,
                                fontSize = 12.sp,
                                fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium
                            )
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(12.dp))

            // ==============================================================
            // DYNAMIC INTERACTIVE WIDGET PREVIEW DISPLAY AREA
            // ==============================================================
            val activeOption = widgetOptions.first { it.id == selectedWidgetId }

            Surface(
                shape = RoundedCornerShape(14.dp),
                color = Color(0xFF111827),
                border = BorderStroke(1.dp, activeOption.badgeColor.copy(alpha = 0.4f)),
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 2.dp)
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(14.dp)
                ) {
                    // Header Bar inside preview
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Surface(
                                shape = RoundedCornerShape(4.dp),
                                color = activeOption.badgeColor.copy(alpha = 0.18f)
                            ) {
                                Text(
                                    text = activeOption.sizeLabel,
                                    color = activeOption.badgeColor,
                                    fontSize = 10.sp,
                                    fontWeight = FontWeight.ExtraBold,
                                    modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                                )
                            }
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = "LIVE INTERACTIVE PREVIEW",
                                color = AdfTextMuted,
                                fontSize = 10.sp,
                                fontWeight = FontWeight.Bold,
                                letterSpacing = 0.5.sp
                            )
                        }

                        // Add Pin Action Button
                        IconButton(
                            onClick = {
                                if (WidgetPinHelper.isPinningSupported(context)) {
                                    WidgetPinHelper.requestPinWidget(context, activeOption.providerClass)
                                    Toast.makeText(context, "Adding ${activeOption.title} widget to Home Screen...", Toast.LENGTH_SHORT).show()
                                } else {
                                    Toast.makeText(context, "Long-press home screen -> Widgets -> PDF AIO -> ${activeOption.title}", Toast.LENGTH_LONG).show()
                                }
                            },
                            modifier = Modifier
                                .size(32.dp)
                                .clip(CircleShape)
                                .background(activeOption.badgeColor.copy(alpha = 0.15f))
                        ) {
                            Icon(
                                imageVector = Icons.Default.Add,
                                contentDescription = "Pin Widget",
                                tint = activeOption.badgeColor,
                                modifier = Modifier.size(18.dp)
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(12.dp))

                    // Specific Interactive Content based on selected widget
                    when (selectedWidgetId) {
                        "scanner" -> {
                            // 📷 SCANNER WIDGET: Small camera preview with live viewfinder laser & SHOOT shutter button
                            Column(
                                horizontalAlignment = Alignment.CenterHorizontally,
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Box(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .height(130.dp)
                                        .clip(RoundedCornerShape(12.dp))
                                        .background(if (isFlashSimulated) Color(0xFF1E293B) else Color(0xFF0F172A))
                                        .border(1.5.dp, Brush.linearGradient(listOf(AdfCoral, AdfElectricCyan)), RoundedCornerShape(12.dp))
                                        .clickable {
                                            Toast.makeText(context, "Opening PDF Camera Scanner...", Toast.LENGTH_SHORT).show()
                                            onNavigateToScreen(AppScreen.SCANNER)
                                        },
                                    contentAlignment = Alignment.Center
                                ) {
                                    // Animated Scanning Laser Line
                                    Canvas(modifier = Modifier.fillMaxSize()) {
                                        val yPos = size.height * laserOffset
                                        drawLine(
                                            color = AdfElectricCyan,
                                            start = androidx.compose.ui.geometry.Offset(10f, yPos),
                                            end = androidx.compose.ui.geometry.Offset(size.width - 10f, yPos),
                                            strokeWidth = 3f
                                        )
                                    }

                                    // Reticle corner brackets
                                    Canvas(modifier = Modifier.fillMaxSize()) {
                                        val w = size.width
                                        val h = size.height
                                        val arm = 24f
                                        val stroke = 4f
                                        val col = AdfCoral

                                        // TL
                                        drawLine(col, androidx.compose.ui.geometry.Offset(16f, 16f), androidx.compose.ui.geometry.Offset(16f + arm, 16f), stroke)
                                        drawLine(col, androidx.compose.ui.geometry.Offset(16f, 16f), androidx.compose.ui.geometry.Offset(16f, 16f + arm), stroke)

                                        // TR
                                        drawLine(col, androidx.compose.ui.geometry.Offset(w - 16f, 16f), androidx.compose.ui.geometry.Offset(w - 16f - arm, 16f), stroke)
                                        drawLine(col, androidx.compose.ui.geometry.Offset(w - 16f, 16f), androidx.compose.ui.geometry.Offset(w - 16f, 16f + arm), stroke)

                                        // BL
                                        drawLine(col, androidx.compose.ui.geometry.Offset(16f, h - 16f), androidx.compose.ui.geometry.Offset(16f + arm, h - 16f), stroke)
                                        drawLine(col, androidx.compose.ui.geometry.Offset(16f, h - 16f), androidx.compose.ui.geometry.Offset(16f, h - 16f - arm), stroke)

                                        // BR
                                        drawLine(col, androidx.compose.ui.geometry.Offset(w - 16f, h - 16f), androidx.compose.ui.geometry.Offset(w - 16f - arm, h - 16f), stroke)
                                        drawLine(col, androidx.compose.ui.geometry.Offset(w - 16f, h - 16f), androidx.compose.ui.geometry.Offset(w - 16f, h - 16f - arm), stroke)
                                    }

                                    // Viewfinder Content
                                    Row(
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.SpaceBetween,
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .padding(horizontal = 16.dp)
                                    ) {
                                        Column {
                                            Text("PHONE CAMERA VIEW", color = AdfTextMuted, fontSize = 9.sp, fontWeight = FontWeight.Bold)
                                            Text("Live Viewfinder Active", color = Color.White, fontSize = 12.sp, fontWeight = FontWeight.ExtraBold)
                                            Text("Tap SHOOT button to scan document", color = AdfElectricCyan, fontSize = 10.sp)
                                        }

                                        // Flash Torch Simulation Toggle
                                        IconButton(
                                            onClick = {
                                                isFlashSimulated = !isFlashSimulated
                                                Toast.makeText(context, if (isFlashSimulated) "Torch Flash ON" else "Torch Flash OFF", Toast.LENGTH_SHORT).show()
                                            },
                                            modifier = Modifier
                                                .size(36.dp)
                                                .clip(CircleShape)
                                                .background(if (isFlashSimulated) AdfAmber.copy(alpha = 0.3f) else Color(0x33FFFFFF))
                                        ) {
                                            Icon(
                                                imageVector = if (isFlashSimulated) Icons.Default.FlashOn else Icons.Default.FlashOff,
                                                contentDescription = "Toggle Torch",
                                                tint = if (isFlashSimulated) AdfAmber else Color.White
                                            )
                                        }
                                    }

                                    // Central Round Animated SHOOT Button
                                    Box(
                                        modifier = Modifier
                                            .align(Alignment.BottomCenter)
                                            .padding(bottom = 8.dp)
                                            .scale(pulseScale)
                                            .size(50.dp)
                                            .clip(CircleShape)
                                            .background(AdfCoral.copy(alpha = 0.25f))
                                            .border(2.dp, AdfCoral, CircleShape)
                                            .clickable {
                                                Toast.makeText(context, "SNAP! Opening Document Scanner...", Toast.LENGTH_SHORT).show()
                                                onNavigateToScreen(AppScreen.SCANNER)
                                            },
                                        contentAlignment = Alignment.Center
                                    ) {
                                        Box(
                                            modifier = Modifier
                                                .size(36.dp)
                                                .clip(CircleShape)
                                                .background(Color.White)
                                                .shadow(4.dp, CircleShape)
                                        )
                                    }
                                }
                            }
                        }

                        "all_in_one" -> {
                            // 📄 ALL-IN-ONE WIDGET: Interactive 6-Tool Quick Action Grid
                            Column(modifier = Modifier.fillMaxWidth()) {
                                Text("INTERACTIVE TOOLBAR GRID (TAP ANY TOOL)", color = AdfElectricCyan, fontSize = 10.sp, fontWeight = FontWeight.Bold)
                                Spacer(modifier = Modifier.height(8.dp))

                                Row(
                                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                                    modifier = Modifier.fillMaxWidth()
                                ) {
                                    // Tool 1: Scanner
                                    WidgetToolButton(
                                        icon = Icons.Default.CameraAlt,
                                        label = "Scan",
                                        color = AdfCoral,
                                        modifier = Modifier.weight(1f),
                                        onClick = { onNavigateToScreen(AppScreen.SCANNER) }
                                    )
                                    // Tool 2: PDF to Word
                                    WidgetToolButton(
                                        icon = Icons.Default.Description,
                                        label = "PDF→Word",
                                        color = AdfEmerald,
                                        modifier = Modifier.weight(1f),
                                        onClick = {
                                            onSelectToolTab(3)
                                            onNavigateToScreen(AppScreen.TOOLS)
                                        }
                                    )
                                    // Tool 3: Word to PDF
                                    WidgetToolButton(
                                        icon = Icons.Default.PictureAsPdf,
                                        label = "Word→PDF",
                                        color = Color(0xFF60A5FA),
                                        modifier = Modifier.weight(1f),
                                        onClick = {
                                            onSelectToolTab(2)
                                            onNavigateToScreen(AppScreen.TOOLS)
                                        }
                                    )
                                }

                                Spacer(modifier = Modifier.height(6.dp))

                                Row(
                                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                                    modifier = Modifier.fillMaxWidth()
                                ) {
                                    // Tool 4: Img to PDF
                                    WidgetToolButton(
                                        icon = Icons.Default.PhotoLibrary,
                                        label = "Img→PDF",
                                        color = AdfCyberViolet,
                                        modifier = Modifier.weight(1f),
                                        onClick = {
                                            onSelectToolTab(0)
                                            onNavigateToScreen(AppScreen.TOOLS)
                                        }
                                    )
                                    // Tool 5: PDF to Img
                                    WidgetToolButton(
                                        icon = Icons.Default.Collections,
                                        label = "PDF→Img",
                                        color = AdfAmber,
                                        modifier = Modifier.weight(1f),
                                        onClick = {
                                            onSelectToolTab(1)
                                            onNavigateToScreen(AppScreen.TOOLS)
                                        }
                                    )
                                    // Tool 6: Dev Info
                                    WidgetToolButton(
                                        icon = Icons.Default.Code,
                                        label = "Dev Info",
                                        color = Color(0xFFF38BA8),
                                        modifier = Modifier.weight(1f),
                                        onClick = { onNavigateToScreen(AppScreen.CREDITS) }
                                    )
                                }
                            }
                        }

                        "pdf_to_word" -> {
                            // 📝 PDF TO WORD: Interactive transfer graphic & action button
                            Column(modifier = Modifier.fillMaxWidth()) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.SpaceEvenly,
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .height(80.dp)
                                        .clip(RoundedCornerShape(10.dp))
                                        .background(Color(0xFF1E293B))
                                        .padding(10.dp)
                                ) {
                                    // PDF Badge
                                    Surface(shape = RoundedCornerShape(8.dp), color = AdfCoral.copy(alpha = 0.2f)) {
                                        Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.padding(8.dp)) {
                                            Icon(Icons.Default.PictureAsPdf, contentDescription = null, tint = AdfCoral, modifier = Modifier.size(24.dp))
                                            Text("DOCUMENT.PDF", fontSize = 8.sp, fontWeight = FontWeight.Bold, color = AdfCoral)
                                        }
                                    }

                                    // Transfer Arrow
                                    Icon(Icons.Default.ArrowForward, contentDescription = null, tint = AdfEmerald, modifier = Modifier.size(24.dp))

                                    // DOCX Badge
                                    Surface(shape = RoundedCornerShape(8.dp), color = AdfEmerald.copy(alpha = 0.2f)) {
                                        Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.padding(8.dp)) {
                                            Icon(Icons.Default.Description, contentDescription = null, tint = AdfEmerald, modifier = Modifier.size(24.dp))
                                            Text("EDITABLE.DOCX", fontSize = 8.sp, fontWeight = FontWeight.Bold, color = AdfEmerald)
                                        }
                                    }
                                }

                                Spacer(modifier = Modifier.height(10.dp))

                                Button(
                                    onClick = {
                                        onSelectToolTab(3)
                                        onNavigateToScreen(AppScreen.TOOLS)
                                    },
                                    colors = ButtonDefaults.buttonColors(containerColor = AdfEmerald),
                                    shape = RoundedCornerShape(10.dp),
                                    modifier = Modifier.fillMaxWidth().height(42.dp)
                                ) {
                                    Icon(Icons.Default.Transform, contentDescription = null, tint = AdfMidnightBackground)
                                    Spacer(modifier = Modifier.width(8.dp))
                                    Text("Convert PDF → Word (DOCX)", color = AdfMidnightBackground, fontWeight = FontWeight.ExtraBold, fontSize = 12.5.sp)
                                }
                            }
                        }

                        "word_to_pdf" -> {
                            // 📄 WORD TO PDF: Interactive page-flip preview
                            Column(modifier = Modifier.fillMaxWidth()) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.SpaceEvenly,
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .height(80.dp)
                                        .clip(RoundedCornerShape(10.dp))
                                        .background(Color(0xFF1E293B))
                                        .padding(10.dp)
                                ) {
                                    // DOCX Badge
                                    Surface(shape = RoundedCornerShape(8.dp), color = Color(0xFF60A5FA).copy(alpha = 0.2f)) {
                                        Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.padding(8.dp)) {
                                            Icon(Icons.Default.Article, contentDescription = null, tint = Color(0xFF60A5FA), modifier = Modifier.size(24.dp))
                                            Text("REPORT.DOCX", fontSize = 8.sp, fontWeight = FontWeight.Bold, color = Color(0xFF60A5FA))
                                        }
                                    }

                                    // Transfer Arrow
                                    Icon(Icons.Default.ArrowForward, contentDescription = null, tint = Color(0xFF60A5FA), modifier = Modifier.size(24.dp))

                                    // PDF Badge
                                    Surface(shape = RoundedCornerShape(8.dp), color = AdfCoral.copy(alpha = 0.2f)) {
                                        Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.padding(8.dp)) {
                                            Icon(Icons.Default.PictureAsPdf, contentDescription = null, tint = AdfCoral, modifier = Modifier.size(24.dp))
                                            Text("COMPILED.PDF", fontSize = 8.sp, fontWeight = FontWeight.Bold, color = AdfCoral)
                                        }
                                    }
                                }

                                Spacer(modifier = Modifier.height(10.dp))

                                Button(
                                    onClick = {
                                        onSelectToolTab(2)
                                        onNavigateToScreen(AppScreen.TOOLS)
                                    },
                                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF60A5FA)),
                                    shape = RoundedCornerShape(10.dp),
                                    modifier = Modifier.fillMaxWidth().height(42.dp)
                                ) {
                                    Icon(Icons.Default.PictureAsPdf, contentDescription = null, tint = AdfMidnightBackground)
                                    Spacer(modifier = Modifier.width(8.dp))
                                    Text("Compile Word (DOCX) → PDF", color = AdfMidnightBackground, fontWeight = FontWeight.ExtraBold, fontSize = 12.5.sp)
                                }
                            }
                        }

                        "img_to_pdf" -> {
                            // 🖼️ IMAGES TO PDF: Gallery carousel preview
                            Column(modifier = Modifier.fillMaxWidth()) {
                                Row(
                                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                                    modifier = Modifier.fillMaxWidth()
                                ) {
                                    repeat(3) { idx ->
                                        Surface(
                                            shape = RoundedCornerShape(8.dp),
                                            color = AdfCyberViolet.copy(alpha = 0.15f),
                                            border = BorderStroke(1.dp, AdfCyberViolet.copy(alpha = 0.4f)),
                                            modifier = Modifier
                                                .weight(1f)
                                                .height(70.dp)
                                        ) {
                                            Column(
                                                horizontalAlignment = Alignment.CenterHorizontally,
                                                verticalArrangement = Arrangement.Center
                                            ) {
                                                Icon(Icons.Default.Image, contentDescription = null, tint = AdfCyberViolet, modifier = Modifier.size(20.dp))
                                                Text("Photo ${idx + 1}", fontSize = 9.sp, color = AdfTextMuted)
                                            }
                                        }
                                    }
                                }

                                Spacer(modifier = Modifier.height(10.dp))

                                Button(
                                    onClick = {
                                        onSelectToolTab(0)
                                        onNavigateToScreen(AppScreen.TOOLS)
                                    },
                                    colors = ButtonDefaults.buttonColors(containerColor = AdfCyberViolet),
                                    shape = RoundedCornerShape(10.dp),
                                    modifier = Modifier.fillMaxWidth().height(42.dp)
                                ) {
                                    Icon(Icons.Default.Collections, contentDescription = null, tint = Color.White)
                                    Spacer(modifier = Modifier.width(8.dp))
                                    Text("Compile Gallery Photos → PDF", color = Color.White, fontWeight = FontWeight.ExtraBold, fontSize = 12.5.sp)
                                }
                            }
                        }

                        "pdf_to_img" -> {
                            // 🖼️ PDF TO IMAGES: Extraction preview
                            Column(modifier = Modifier.fillMaxWidth()) {
                                Row(
                                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                                    modifier = Modifier.fillMaxWidth()
                                ) {
                                    repeat(3) { idx ->
                                        Surface(
                                            shape = RoundedCornerShape(8.dp),
                                            color = AdfAmber.copy(alpha = 0.15f),
                                            border = BorderStroke(1.dp, AdfAmber.copy(alpha = 0.4f)),
                                            modifier = Modifier
                                                .weight(1f)
                                                .height(70.dp)
                                        ) {
                                            Column(
                                                horizontalAlignment = Alignment.CenterHorizontally,
                                                verticalArrangement = Arrangement.Center
                                            ) {
                                                Icon(Icons.Default.PhotoSizeSelectActual, contentDescription = null, tint = AdfAmber, modifier = Modifier.size(20.dp))
                                                Text("Page_${idx + 1}.png", fontSize = 8.5.sp, color = AdfAmber, fontWeight = FontWeight.Bold)
                                            }
                                        }
                                    }
                                }

                                Spacer(modifier = Modifier.height(10.dp))

                                Button(
                                    onClick = {
                                        onSelectToolTab(1)
                                        onNavigateToScreen(AppScreen.TOOLS)
                                    },
                                    colors = ButtonDefaults.buttonColors(containerColor = AdfAmber),
                                    shape = RoundedCornerShape(10.dp),
                                    modifier = Modifier.fillMaxWidth().height(42.dp)
                                ) {
                                    Icon(Icons.Default.FileDownload, contentDescription = null, tint = AdfMidnightBackground)
                                    Spacer(modifier = Modifier.width(8.dp))
                                    Text("Extract High-Res PNG Images", color = AdfMidnightBackground, fontWeight = FontWeight.ExtraBold, fontSize = 12.5.sp)
                                }
                            }
                        }

                        "developer" -> {
                            // 🛠️ DEVELOPER WIDGET: Live system telemetry monitor
                            Column(modifier = Modifier.fillMaxWidth()) {
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .clip(RoundedCornerShape(10.dp))
                                        .background(Color(0xFF1E293B))
                                        .padding(10.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.SpaceBetween
                                ) {
                                    Column {
                                        Text("PDF AIO ENGINE TELEMETRY", color = Color(0xFFF38BA8), fontSize = 9.sp, fontWeight = FontWeight.Bold)
                                        Text("60 FPS Performance Lock Active", color = Color.White, fontSize = 12.sp, fontWeight = FontWeight.ExtraBold)
                                        Text("Version 5.1.6 Pro • Kotlin Compose Engine", color = AdfTextMuted, fontSize = 10.sp)
                                    }

                                    Surface(
                                        shape = CircleShape,
                                        color = Color(0xFF10B981).copy(alpha = 0.2f),
                                        border = BorderStroke(1.dp, Color(0xFF10B981))
                                    ) {
                                        Row(
                                            verticalAlignment = Alignment.CenterVertically,
                                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                                        ) {
                                            Box(
                                                modifier = Modifier
                                                    .size(6.dp)
                                                    .clip(CircleShape)
                                                    .background(Color(0xFF10B981))
                                            )
                                            Spacer(modifier = Modifier.width(4.dp))
                                            Text("60 FPS", color = Color(0xFF10B981), fontSize = 10.sp, fontWeight = FontWeight.Bold)
                                        }
                                    }
                                }

                                Spacer(modifier = Modifier.height(10.dp))

                                Button(
                                    onClick = { onNavigateToScreen(AppScreen.CREDITS) },
                                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFF38BA8)),
                                    shape = RoundedCornerShape(10.dp),
                                    modifier = Modifier.fillMaxWidth().height(42.dp)
                                ) {
                                    Icon(Icons.Default.DeveloperMode, contentDescription = null, tint = AdfMidnightBackground)
                                    Spacer(modifier = Modifier.width(8.dp))
                                    Text("Open Developer Telemetry & Credits", color = AdfMidnightBackground, fontWeight = FontWeight.ExtraBold, fontSize = 12.5.sp)
                                }
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(10.dp))

                    // Pin Widget to Phone Home Screen Button
                    Button(
                        onClick = {
                            if (WidgetPinHelper.isPinningSupported(context)) {
                                WidgetPinHelper.requestPinWidget(context, activeOption.providerClass)
                                Toast.makeText(context, "Adding ${activeOption.title} to Phone Desktop...", Toast.LENGTH_SHORT).show()
                            } else {
                                Toast.makeText(context, "To add: Long-press home screen -> Widgets -> PDF AIO", Toast.LENGTH_LONG).show()
                            }
                        },
                        colors = ButtonDefaults.buttonColors(containerColor = activeOption.badgeColor.copy(alpha = 0.15f)),
                        shape = RoundedCornerShape(10.dp),
                        modifier = Modifier.fillMaxWidth().height(36.dp)
                    ) {
                        Icon(Icons.Default.Add, contentDescription = null, tint = activeOption.badgeColor, modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            text = "+ Pin ${activeOption.title} Widget to Home Screen",
                            color = activeOption.badgeColor,
                            fontSize = 11.5.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }
            }
        }
    }
}

@Composable
fun WidgetToolButton(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    label: String,
    color: Color,
    modifier: Modifier = Modifier,
    onClick: () -> Unit
) {
    Surface(
        onClick = onClick,
        shape = RoundedCornerShape(10.dp),
        color = color.copy(alpha = 0.15f),
        border = BorderStroke(1.dp, color.copy(alpha = 0.35f)),
        modifier = modifier.height(60.dp)
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
            modifier = Modifier.padding(4.dp)
        ) {
            Icon(imageVector = icon, contentDescription = label, tint = color, modifier = Modifier.size(20.dp))
            Spacer(modifier = Modifier.height(2.dp))
            Text(
                text = label,
                color = Color.White,
                fontSize = 10.sp,
                fontWeight = FontWeight.Bold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}

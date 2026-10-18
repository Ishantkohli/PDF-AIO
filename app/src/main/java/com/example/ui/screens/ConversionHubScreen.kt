package com.example.ui.screens

import androidx.activity.compose.BackHandler
import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.ui.AppScreen
import com.example.ui.PdfViewModel
import com.example.ui.components.AdfIcon
import com.example.ui.theme.*

enum class ConversionIconKind {
    IMAGES_TO_PDF,
    PDF_TO_IMAGES,
    WORD_TO_PDF,
    PDF_TO_WORD,
    WATERMARK,
    MERGE,
    ORGANIZE
}

data class ConversionAction(
    val id: Int,
    val title: String,
    val subtitle: String,
    val tag: String,
    val iconKind: ConversionIconKind,
    val iconBgColor: Color,
    val accentColor: Color,
    val supportedFormats: String,
    val targetTab: Int // Tab index in ToolsScreen
)

/**
 * ADF AIO Conversion Hub Screen
 * Displays a dedicated futuristic window showcasing all document conversion capabilities
 * (Images to PDF, PDF to Images, Word to PDF, PDF to Word, Watermark Studio, Merge PDFs, Organize Pages).
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ConversionHubScreen(
    viewModel: PdfViewModel,
    onSelectConversion: (Int) -> Unit,
    onBack: () -> Unit
) {
    BackHandler { onBack() }

    val conversions = remember {
        listOf(
            ConversionAction(
                id = 0,
                title = "Images to PDF",
                subtitle = "Compile camera photos & graphic files into formatted PDF sheets",
                tag = "MOST POPULAR",
                iconKind = ConversionIconKind.IMAGES_TO_PDF,
                iconBgColor = AdfCoral.copy(alpha = 0.15f),
                accentColor = AdfCoral,
                supportedFormats = "PNG, JPG, JPEG, WebP, GIF, BMP, HEIF",
                targetTab = 0
            ),
            ConversionAction(
                id = 1,
                title = "PDF to Images",
                subtitle = "Render and export PDF pages as high-resolution image files or ZIP archive",
                tag = "ULTRA HD (216 DPI)",
                iconKind = ConversionIconKind.PDF_TO_IMAGES,
                iconBgColor = AdfElectricCyan.copy(alpha = 0.15f),
                accentColor = AdfElectricCyan,
                supportedFormats = "Lossless PNG, JPEG (72, 144, 216 DPI)",
                targetTab = 1
            ),
            ConversionAction(
                id = 2,
                title = "Word to PDF",
                subtitle = "Convert Microsoft Word documents into standardized PDF pages",
                tag = "OPENXML PARSER",
                iconKind = ConversionIconKind.WORD_TO_PDF,
                iconBgColor = AdfVibrantBlue.copy(alpha = 0.15f),
                accentColor = Color(0xFF60A5FA),
                supportedFormats = ".docx, .doc, OpenDocument Text",
                targetTab = 2
            ),
            ConversionAction(
                id = 3,
                title = "PDF to Word (.docx)",
                subtitle = "Extract document content, structure, and text into editable Microsoft Word",
                tag = "EDITABLE DOCX",
                iconKind = ConversionIconKind.PDF_TO_WORD,
                iconBgColor = AdfEmerald.copy(alpha = 0.15f),
                accentColor = AdfEmerald,
                supportedFormats = "Microsoft Word (.docx), Google Docs ready",
                targetTab = 3
            ),
            ConversionAction(
                id = 4,
                title = "Watermark Studio",
                subtitle = "Apply custom watermark text, rotation angle (0°-90°), opacity, and tiled patterns",
                tag = "CUSTOM BRANDING",
                iconKind = ConversionIconKind.WATERMARK,
                iconBgColor = AdfCyberViolet.copy(alpha = 0.15f),
                accentColor = AdfCyberViolet,
                supportedFormats = "Any PDF (Presets: CONFIDENTIAL, DRAFT, APPROVED, etc.)",
                targetTab = 4
            ),
            ConversionAction(
                id = 5,
                title = "Merge Multiple PDFs",
                subtitle = "Combine 2 or more PDF documents into a single master document",
                tag = "BATCH COMBINE",
                iconKind = ConversionIconKind.MERGE,
                iconBgColor = AdfAmber.copy(alpha = 0.15f),
                accentColor = AdfAmber,
                supportedFormats = "Multiple PDF files in custom sequence",
                targetTab = 5
            ),
            ConversionAction(
                id = 6,
                title = "Organize & Rotate Pages",
                subtitle = "Reorder pages, rotate 90°/180°/270°, and remove unwanted sheets",
                tag = "PAGE MANAGER",
                iconKind = ConversionIconKind.ORGANIZE,
                iconBgColor = Color(0xFFEC4899).copy(alpha = 0.15f),
                accentColor = Color(0xFFEC4899),
                supportedFormats = "Visual thumbnail page grid",
                targetTab = 6
            )
        )
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(
                                text = "Conversion Hub",
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
                                    text = "7 ENGINES",
                                    color = AdfElectricCyan,
                                    fontSize = 8.sp,
                                    fontWeight = FontWeight.ExtraBold,
                                    modifier = Modifier.padding(horizontal = 4.dp, vertical = 2.dp)
                                )
                            }
                        }
                        Text(
                            text = "ADF AIO Universal Document Transformer",
                            style = MaterialTheme.typography.bodySmall.copy(fontSize = 11.sp),
                            color = AdfElectricCyan
                        )
                    }
                },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        AdfIcon.ChevronLeft(size = 22.dp, color = AdfElectricCyan)
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
            verticalArrangement = Arrangement.spacedBy(10.dp),
            contentPadding = PaddingValues(top = 10.dp, bottom = 48.dp)
        ) {
            // Futuristic Header Banner
            item {
                Surface(
                    shape = RoundedCornerShape(16.dp),
                    color = AdfObsidianSurface,
                    border = CardDefaults.outlinedCardBorder().copy(
                        brush = Brush.linearGradient(listOf(AdfElectricCyan.copy(alpha = 0.4f), AdfVibrantBlue.copy(alpha = 0.4f)))
                    ),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(14.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Box(
                            modifier = Modifier
                                .size(46.dp)
                                .clip(RoundedCornerShape(12.dp))
                                .background(AdfElectricCyan.copy(alpha = 0.12f)),
                            contentAlignment = Alignment.Center
                        ) {
                            AdfIcon.Conversion(size = 26.dp, isAnimated = true)
                        }
                        Spacer(modifier = Modifier.width(12.dp))
                        Column {
                            Text(
                                text = "Zero-Cloud Local Conversions",
                                color = AdfTextPrimary,
                                fontWeight = FontWeight.Bold,
                                fontSize = 14.sp
                            )
                            Text(
                                text = "100% on-device processing. No data leaves your phone.",
                                color = AdfTextSecondary,
                                fontSize = 11.sp
                            )
                        }
                    }
                }
            }

            items(conversions, key = { it.id }) { action ->
                val interactionSource = remember { MutableInteractionSource() }
                val isPressed by interactionSource.collectIsPressedAsState()
                val scale by animateFloatAsState(
                    targetValue = if (isPressed) 0.98f else 1f,
                    animationSpec = spring(stiffness = Spring.StiffnessMediumLow),
                    label = "CardPressScale"
                )

                Card(
                    onClick = { onSelectConversion(action.targetTab) },
                    interactionSource = interactionSource,
                    shape = RoundedCornerShape(14.dp),
                    colors = CardDefaults.cardColors(containerColor = AdfObsidianSurface),
                    border = CardDefaults.outlinedCardBorder().copy(
                        brush = Brush.linearGradient(
                            listOf(
                                if (isPressed) action.accentColor else AdfBorderSubtle,
                                action.accentColor.copy(alpha = 0.25f)
                            )
                        )
                    ),
                    modifier = Modifier
                        .fillMaxWidth()
                        .scale(scale)
                        .testTag("conversion_action_${action.id}")
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(14.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        // Bespoke icon for each conversion
                        Box(
                            modifier = Modifier
                                .size(48.dp)
                                .clip(RoundedCornerShape(12.dp))
                                .background(action.iconBgColor),
                            contentAlignment = Alignment.Center
                        ) {
                            when (action.iconKind) {
                                ConversionIconKind.IMAGES_TO_PDF -> {
                                    AdfIcon.Camera(size = 26.dp, primaryColor = action.accentColor)
                                }
                                ConversionIconKind.PDF_TO_IMAGES -> {
                                    AdfIcon.Conversion(size = 26.dp, primaryColor = action.accentColor, isAnimated = false)
                                }
                                ConversionIconKind.WORD_TO_PDF -> {
                                    AdfIcon.WordDoc(size = 26.dp, primaryColor = action.accentColor)
                                }
                                ConversionIconKind.PDF_TO_WORD -> {
                                    AdfIcon.WordDoc(size = 26.dp, primaryColor = action.accentColor)
                                }
                                ConversionIconKind.WATERMARK -> {
                                    AdfIcon.Watermark(size = 26.dp, primaryColor = action.accentColor)
                                }
                                ConversionIconKind.MERGE -> {
                                    AdfIcon.Merge(size = 26.dp, primaryColor = action.accentColor)
                                }
                                ConversionIconKind.ORGANIZE -> {
                                    AdfIcon.Layers(size = 26.dp, primaryColor = action.accentColor)
                                }
                            }
                        }

                        Spacer(modifier = Modifier.width(14.dp))

                        Column(modifier = Modifier.weight(1f)) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                Text(
                                    text = action.title,
                                    color = AdfTextPrimary,
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 15.sp
                                )
                                Surface(
                                    shape = RoundedCornerShape(4.dp),
                                    color = action.accentColor.copy(alpha = 0.15f)
                                ) {
                                    Text(
                                        text = action.tag,
                                        color = action.accentColor,
                                        fontSize = 8.sp,
                                        fontWeight = FontWeight.ExtraBold,
                                        modifier = Modifier.padding(horizontal = 5.dp, vertical = 2.dp)
                                    )
                                }
                            }

                            Spacer(modifier = Modifier.height(3.dp))

                            Text(
                                text = action.subtitle,
                                color = AdfTextSecondary,
                                fontSize = 12.sp,
                                lineHeight = 16.sp
                            )

                            Spacer(modifier = Modifier.height(4.dp))

                            Text(
                                text = "Formats: ${action.supportedFormats}",
                                color = AdfTextMuted,
                                fontSize = 10.sp
                            )
                        }

                        AdfIcon.ChevronRight(size = 20.dp, color = action.accentColor)
                    }
                }
            }
        }
    }
}

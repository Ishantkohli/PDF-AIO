package com.example.ui.screens

import androidx.activity.compose.BackHandler
import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.automirrored.filled.Notes
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil.compose.AsyncImage
import com.example.data.pdf.PptPresentation
import com.example.data.pdf.PptSlide
import com.example.ui.PdfViewModel
import com.example.ui.theme.*
import kotlinx.coroutines.delay

@Composable
fun PptViewerScreen(
    viewModel: PdfViewModel,
    onBack: () -> Unit = {}
) {
    val presentationState by viewModel.currentPresentation.collectAsStateWithLifecycle()
    val slideIndex by viewModel.presentationSlideIndex.collectAsStateWithLifecycle()
    val isSlideshowMode by viewModel.isPresentationSlideshowMode.collectAsStateWithLifecycle()
    val isLaserPointerActive by viewModel.laserPointerActive.collectAsStateWithLifecycle()
    val showNotes by viewModel.showSpeakerNotes.collectAsStateWithLifecycle()

    BackHandler {
        if (isSlideshowMode) {
            viewModel.togglePresentationSlideshow()
        } else {
            viewModel.closePresentation()
            onBack()
        }
    }

    val currentPpt = presentationState ?: return

    val currentSlide = currentPpt.slides.getOrNull(slideIndex)
        ?: currentPpt.slides.firstOrNull()
        ?: PptSlide(1, currentPpt.title, listOf("No slide content available"))

    if (isSlideshowMode) {
        SlideshowFullscreenView(
            presentation = currentPpt,
            currentIndex = slideIndex,
            isLaserActive = isLaserPointerActive,
            onIndexChange = { newIdx -> viewModel.setPresentationSlideIndex(newIdx) },
            onExitSlideshow = { viewModel.togglePresentationSlideshow() },
            onToggleLaser = { viewModel.toggleLaserPointer() }
        )
    } else {
        StandardPptViewerLayout(
            presentation = currentPpt,
            currentSlide = currentSlide,
            slideIndex = slideIndex,
            showNotes = showNotes,
            isLaserActive = isLaserPointerActive,
            onBack = {
                viewModel.closePresentation()
                onBack()
            },
            onPrev = { viewModel.prevPresentationSlide() },
            onNext = { viewModel.nextPresentationSlide() },
            onSelectSlide = { idx -> viewModel.setPresentationSlideIndex(idx) },
            onToggleSlideshow = { viewModel.togglePresentationSlideshow() },
            onToggleLaser = { viewModel.toggleLaserPointer() },
            onToggleNotes = { viewModel.toggleSpeakerNotes() }
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun StandardPptViewerLayout(
    presentation: PptPresentation,
    currentSlide: PptSlide,
    slideIndex: Int,
    showNotes: Boolean,
    isLaserActive: Boolean,
    onBack: () -> Unit,
    onPrev: () -> Unit,
    onNext: () -> Unit,
    onSelectSlide: (Int) -> Unit,
    onToggleSlideshow: () -> Unit,
    onToggleLaser: () -> Unit,
    onToggleNotes: () -> Unit
) {
    var laserPos by remember { mutableStateOf<Offset?>(null) }

    val activeThemeStyle by ThemeManager.currentTheme.collectAsStateWithLifecycle()
    val isLiquidTheme = activeThemeStyle == AppThemeStyle.LIQUID_GLASS || activeThemeStyle == AppThemeStyle.FROSTED_CRYSTAL

    Scaffold(
        topBar = {
            Surface(
                color = if (isLiquidTheme) GlassSurfaceDark else AdfMidnightSurface,
                tonalElevation = 6.dp,
                modifier = Modifier
                    .fillMaxWidth()
                    .border(
                        width = 1.dp,
                        brush = if (isLiquidTheme) LiquidGlassBorderGradient else Brush.horizontalGradient(listOf(AdfBorderSubtle, AdfBorderSubtle)),
                        shape = androidx.compose.ui.graphics.RectangleShape
                    )
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .statusBarsPadding()
                        .padding(horizontal = 12.dp, vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    IconButton(
                        onClick = onBack,
                        modifier = Modifier.testTag("ppt_viewer_back_button")
                    ) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = "Back",
                            tint = Color.White
                        )
                    }

                    Column(
                        modifier = Modifier
                            .weight(1f)
                            .padding(horizontal = 8.dp)
                    ) {
                        Text(
                            text = presentation.title,
                            style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                            color = Color.White,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                        Text(
                            text = "Slide ${slideIndex + 1} of ${presentation.totalSlides} • Native PowerPoint Deck",
                            style = MaterialTheme.typography.bodySmall.copy(fontSize = 11.sp),
                            color = AdfElectricCyan
                        )
                    }

                    IconButton(
                        onClick = onToggleLaser,
                        modifier = Modifier
                            .clip(CircleShape)
                            .background(if (isLaserActive) Color.Red.copy(alpha = 0.3f) else Color.Transparent)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Highlight,
                            contentDescription = "Laser Pointer Mode",
                            tint = if (isLaserActive) Color.Red else AdfTextMuted
                        )
                    }

                    IconButton(
                        onClick = onToggleNotes,
                        modifier = Modifier
                            .clip(CircleShape)
                            .background(if (showNotes) AdfElectricCyan.copy(alpha = 0.2f) else Color.Transparent)
                    ) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.Notes,
                            contentDescription = "Speaker Notes",
                            tint = if (showNotes) AdfElectricCyan else AdfTextMuted
                        )
                    }

                    IconButton(
                        onClick = onToggleSlideshow,
                        modifier = Modifier
                            .clip(RoundedCornerShape(10.dp))
                            .background(
                                Brush.horizontalGradient(listOf(AdfElectricCyan, AdfVibrantBlue))
                            )
                    ) {
                        Icon(
                            imageVector = Icons.Default.PlayArrow,
                            contentDescription = "Start Presentation Slideshow",
                            tint = Color.Black
                        )
                    }
                }
            }
        },
        bottomBar = {
            Surface(
                color = if (isLiquidTheme) GlassSurfaceDark else AdfMidnightSurface,
                tonalElevation = 8.dp,
                modifier = Modifier
                    .fillMaxWidth()
                    .border(
                        width = 1.dp,
                        brush = if (isLiquidTheme) LiquidGlassBorderGradient else Brush.horizontalGradient(listOf(AdfBorderSubtle, AdfBorderSubtle)),
                        shape = androidx.compose.ui.graphics.RectangleShape
                    )
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .navigationBarsPadding()
                        .padding(vertical = 8.dp)
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .horizontalScroll(rememberScrollState())
                            .padding(horizontal = 12.dp, vertical = 4.dp),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        for ((idx, slide) in presentation.slides.withIndex()) {
                            val isSelected = idx == slideIndex
                            Surface(
                                onClick = { onSelectSlide(idx) },
                                shape = RoundedCornerShape(10.dp),
                                color = if (isSelected) AdfElectricCyan.copy(alpha = 0.2f) else AdfMidnightBackground,
                                border = CardDefaults.outlinedCardBorder().copy(
                                    brush = if (isSelected) Brush.horizontalGradient(listOf(AdfElectricCyan, AdfVibrantBlue))
                                    else Brush.horizontalGradient(listOf(AdfBorderSubtle, AdfBorderSubtle))
                                ),
                                modifier = Modifier
                                    .width(76.dp)
                                    .height(48.dp)
                            ) {
                                Box(
                                    modifier = Modifier
                                        .fillMaxSize()
                                        .padding(4.dp),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                        Text(
                                            text = "SLIDE ${idx + 1}",
                                            fontSize = 9.sp,
                                            fontWeight = FontWeight.Bold,
                                            color = if (isSelected) AdfElectricCyan else AdfTextMuted
                                        )
                                        Text(
                                            text = slide.title,
                                            fontSize = 8.sp,
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis,
                                            color = Color.White.copy(alpha = 0.8f)
                                        )
                                    }
                                }
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(4.dp))

                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        OutlinedButton(
                            onClick = onPrev,
                            enabled = slideIndex > 0,
                            shape = RoundedCornerShape(12.dp),
                            border = ButtonDefaults.outlinedButtonBorder.copy(
                                brush = Brush.horizontalGradient(listOf(AdfBorderSubtle, AdfBorderSubtle))
                            )
                        ) {
                            Icon(imageVector = Icons.AutoMirrored.Filled.ArrowBack, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("Previous", fontSize = 12.sp)
                        }

                        Text(
                            text = "${slideIndex + 1} / ${presentation.totalSlides}",
                            fontSize = 13.sp,
                            fontWeight = FontWeight.Bold,
                            color = AdfElectricCyan
                        )

                        Button(
                            onClick = onNext,
                            enabled = slideIndex < presentation.totalSlides - 1,
                            shape = RoundedCornerShape(12.dp),
                            colors = ButtonDefaults.buttonColors(containerColor = AdfElectricCyan, contentColor = Color.Black)
                        ) {
                            Text("Next", fontSize = 12.sp, fontWeight = FontWeight.Bold)
                            Spacer(modifier = Modifier.width(6.dp))
                            Icon(imageVector = Icons.AutoMirrored.Filled.ArrowForward, contentDescription = null, modifier = Modifier.size(16.dp))
                        }
                    }
                }
            }
        },
        containerColor = if (isLiquidTheme) Color.Transparent else AdfMidnightBackground
    ) { innerPadding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .padding(16.dp),
            contentAlignment = Alignment.Center
        ) {
            Column(
                modifier = Modifier.fillMaxSize(),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxWidth()
                        .aspectRatio(16f / 9f)
                        .shadow(16.dp, RoundedCornerShape(20.dp))
                        .clip(RoundedCornerShape(20.dp))
                        .background(
                            Brush.verticalGradient(
                                listOf(
                                    Color(0xFF1E2230),
                                    Color(0xFF121520)
                                )
                            )
                        )
                        .border(
                            width = 1.5.dp,
                            brush = Brush.horizontalGradient(
                                listOf(
                                    Color.White.copy(alpha = 0.2f),
                                    Color.White.copy(alpha = 0.05f)
                                )
                            ),
                            shape = RoundedCornerShape(20.dp)
                        )
                        .pointerInput(isLaserActive) {
                            if (isLaserActive) {
                                detectDragGestures(
                                    onDragStart = { offset -> laserPos = offset },
                                    onDrag = { change, _ ->
                                        laserPos = change.position
                                        change.consume()
                                    },
                                    onDragEnd = { laserPos = null },
                                    onDragCancel = { laserPos = null }
                                )
                            }
                        }
                ) {
                    AnimatedContent(
                        targetState = currentSlide,
                        label = "SlideContentTransition",
                        transitionSpec = {
                            fadeIn(tween(300)) + slideInHorizontally(tween(350)) { it / 2 } togetherWith
                                    fadeOut(tween(250)) + slideOutHorizontally(tween(300)) { -it / 2 }
                        }
                    ) { slide ->
                        SlideCanvasContent(slide = slide)
                    }

                    if (isLaserActive && laserPos != null) {
                        Canvas(modifier = Modifier.fillMaxSize()) {
                            drawCircle(
                                color = Color.Red.copy(alpha = 0.35f),
                                radius = 24.dp.toPx(),
                                center = laserPos!!
                            )
                            drawCircle(
                                color = Color.Red,
                                radius = 8.dp.toPx(),
                                center = laserPos!!
                            )
                            drawCircle(
                                color = Color.White,
                                radius = 3.dp.toPx(),
                                center = laserPos!!
                            )
                        }
                    }
                }

                if (showNotes) {
                    Spacer(modifier = Modifier.height(12.dp))
                    Surface(
                        shape = RoundedCornerShape(16.dp),
                        color = AdfMidnightSurface,
                        border = CardDefaults.outlinedCardBorder().copy(
                            brush = Brush.horizontalGradient(listOf(AdfElectricCyan.copy(alpha = 0.5f), AdfVibrantBlue.copy(alpha = 0.3f)))
                        ),
                        modifier = Modifier
                            .fillMaxWidth()
                            .heightIn(max = 140.dp)
                    ) {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(12.dp)
                                .verticalScroll(rememberScrollState())
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(
                                    imageVector = Icons.AutoMirrored.Filled.Notes,
                                    contentDescription = null,
                                    tint = AdfElectricCyan,
                                    modifier = Modifier.size(16.dp)
                                )
                                Spacer(modifier = Modifier.width(6.dp))
                                Text(
                                    text = "Speaker Notes & Highlights",
                                    fontSize = 12.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = AdfElectricCyan
                                )
                            }
                            Spacer(modifier = Modifier.height(4.dp))
                            Text(
                                text = currentSlide.speakerNotes.ifBlank {
                                    "No custom speaker notes attached to slide ${currentSlide.slideNumber}."
                                },
                                fontSize = 12.sp,
                                color = Color.White.copy(alpha = 0.9f),
                                lineHeight = 16.sp
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun SlideCanvasContent(slide: PptSlide) {
    val scrollState = rememberScrollState()

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(24.dp)
            .verticalScroll(scrollState),
        verticalArrangement = Arrangement.Top
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Surface(
                shape = RoundedCornerShape(8.dp),
                color = AdfElectricCyan.copy(alpha = 0.2f)
            ) {
                Text(
                    text = "SLIDE ${slide.slideNumber}",
                    color = AdfElectricCyan,
                    fontSize = 10.sp,
                    fontWeight = FontWeight.ExtraBold,
                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp)
                )
            }

            Text(
                text = "PDF AIO Presentation Engine",
                color = AdfTextMuted,
                fontSize = 10.sp
            )
        }

        Spacer(modifier = Modifier.height(14.dp))

        Text(
            text = slide.title,
            style = MaterialTheme.typography.titleLarge.copy(
                fontWeight = FontWeight.ExtraBold,
                letterSpacing = 0.2.sp
            ),
            color = Color.White,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis
        )

        Spacer(modifier = Modifier.height(8.dp))

        Box(
            modifier = Modifier
                .width(60.dp)
                .height(3.dp)
                .clip(RoundedCornerShape(2.dp))
                .background(
                    try {
                        Color(android.graphics.Color.parseColor(slide.accentColorHex))
                    } catch (e: Exception) {
                        AdfElectricCyan
                    }
                )
        )

        Spacer(modifier = Modifier.height(16.dp))

        if (slide.imageUris.isNotEmpty()) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(120.dp)
                    .horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                for (imgUri in slide.imageUris) {
                    AsyncImage(
                        model = imgUri,
                        contentDescription = "Slide Media",
                        modifier = Modifier
                            .height(120.dp)
                            .clip(RoundedCornerShape(12.dp))
                            .border(width = 1.dp, color = AdfBorderSubtle, shape = RoundedCornerShape(12.dp))
                    )
                }
            }
            Spacer(modifier = Modifier.height(16.dp))
        }

        for (paragraph in slide.paragraphs) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 4.dp),
                verticalAlignment = Alignment.Top
            ) {
                Box(
                    modifier = Modifier
                        .padding(top = 6.dp, end = 10.dp)
                        .size(6.dp)
                        .clip(CircleShape)
                        .background(AdfElectricCyan)
                )
                Text(
                    text = paragraph,
                    style = MaterialTheme.typography.bodyMedium.copy(
                        fontSize = 14.sp,
                        lineHeight = 20.sp
                    ),
                    color = Color.White.copy(alpha = 0.95f)
                )
            }
        }
    }
}

@Composable
private fun SlideshowFullscreenView(
    presentation: PptPresentation,
    currentIndex: Int,
    isLaserActive: Boolean,
    onIndexChange: (Int) -> Unit,
    onExitSlideshow: () -> Unit,
    onToggleLaser: () -> Unit
) {
    var isAutoPlay by remember { mutableStateOf(false) }
    val autoPlayTimerSeconds = 3
    var laserPos by remember { mutableStateOf<Offset?>(null) }

    LaunchedEffect(isAutoPlay, currentIndex) {
        if (isAutoPlay) {
            delay(autoPlayTimerSeconds * 1000L)
            val nextIndex = (currentIndex + 1) % presentation.totalSlides
            onIndexChange(nextIndex)
        }
    }

    val currentSlide = presentation.slides.getOrNull(currentIndex)
        ?: presentation.slides.first()

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black)
            .pointerInput(isLaserActive) {
                if (isLaserActive) {
                    detectDragGestures(
                        onDragStart = { offset -> laserPos = offset },
                        onDrag = { change, _ ->
                            laserPos = change.position
                            change.consume()
                        },
                        onDragEnd = { laserPos = null },
                        onDragCancel = { laserPos = null }
                    )
                }
            }
    ) {
        AnimatedContent(
            targetState = currentSlide,
            label = "FullscreenSlideTransition",
            transitionSpec = {
                (fadeIn(tween(400)) + scaleIn(tween(400), initialScale = 0.92f)) togetherWith
                        (fadeOut(tween(350)) + scaleOut(tween(350), targetScale = 1.05f))
            },
            modifier = Modifier.fillMaxSize()
        ) { slide ->
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(horizontal = 32.dp, vertical = 24.dp),
                contentAlignment = Alignment.Center
            ) {
                Surface(
                    shape = RoundedCornerShape(24.dp),
                    color = Color(0xFF161A26),
                    border = CardDefaults.outlinedCardBorder().copy(
                        brush = Brush.horizontalGradient(
                            listOf(
                                Color.White.copy(alpha = 0.15f),
                                Color.White.copy(alpha = 0.05f)
                            )
                        )
                    ),
                    modifier = Modifier
                        .fillMaxWidth()
                        .aspectRatio(16f / 9f)
                        .shadow(24.dp, RoundedCornerShape(24.dp))
                ) {
                    SlideCanvasContent(slide = slide)
                }
            }
        }

        if (isLaserActive && laserPos != null) {
            Canvas(modifier = Modifier.fillMaxSize()) {
                drawCircle(
                    color = Color.Red.copy(alpha = 0.4f),
                    radius = 30.dp.toPx(),
                    center = laserPos!!
                )
                drawCircle(
                    color = Color.Red,
                    radius = 10.dp.toPx(),
                    center = laserPos!!
                )
                drawCircle(
                    color = Color.White,
                    radius = 4.dp.toPx(),
                    center = laserPos!!
                )
            }
        }

        Surface(
            shape = RoundedCornerShape(30.dp),
            color = Color.Black.copy(alpha = 0.75f),
            border = CardDefaults.outlinedCardBorder().copy(
                brush = Brush.horizontalGradient(listOf(AdfBorderSubtle, AdfBorderGlow))
            ),
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .padding(bottom = 24.dp)
        ) {
            Row(
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                IconButton(onClick = onExitSlideshow) {
                    Icon(imageVector = Icons.Default.Close, contentDescription = "Exit Slideshow", tint = Color.White)
                }

                IconButton(
                    onClick = { onIndexChange((currentIndex - 1 + presentation.totalSlides) % presentation.totalSlides) }
                ) {
                    Icon(imageVector = Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Previous Slide", tint = Color.White)
                }

                Text(
                    text = "${currentIndex + 1} / ${presentation.totalSlides}",
                    color = AdfElectricCyan,
                    fontWeight = FontWeight.Bold,
                    fontSize = 14.sp
                )

                IconButton(
                    onClick = { onIndexChange((currentIndex + 1) % presentation.totalSlides) }
                ) {
                    Icon(imageVector = Icons.AutoMirrored.Filled.ArrowForward, contentDescription = "Next Slide", tint = Color.White)
                }

                IconButton(
                    onClick = onToggleLaser,
                    modifier = Modifier
                        .clip(CircleShape)
                        .background(if (isLaserActive) Color.Red.copy(alpha = 0.4f) else Color.Transparent)
                ) {
                    Icon(
                        imageVector = Icons.Default.Highlight,
                        contentDescription = "Laser Pointer",
                        tint = if (isLaserActive) Color.Red else Color.White
                    )
                }

                IconButton(
                    onClick = { isAutoPlay = !isAutoPlay },
                    modifier = Modifier
                        .clip(CircleShape)
                        .background(if (isAutoPlay) AdfElectricCyan.copy(alpha = 0.3f) else Color.Transparent)
                ) {
                    Icon(
                        imageVector = if (isAutoPlay) Icons.Default.Pause else Icons.Default.PlayArrow,
                        contentDescription = "Auto Play",
                        tint = if (isAutoPlay) AdfElectricCyan else Color.White
                    )
                }
            }
        }
    }
}

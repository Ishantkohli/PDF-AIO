package com.example.ui.screens.scanner

import android.content.Context
import android.net.Uri
import android.widget.Toast
import androidx.camera.core.Camera
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.ImageCapture
import androidx.camera.core.ImageCaptureException
import androidx.camera.core.Preview
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.CameraAlt
import androidx.compose.material.icons.filled.PhotoLibrary
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.example.data.scan.DocumentEdgeDetector
import com.example.ui.theme.*
import java.io.File
import java.util.concurrent.Executors

/**
 * In-App Dedicated CameraX Live Viewfinder + Real-Time Edge Quad Detection & Auto-Capture.
 * Never launches the device OEM camera app; runs 100% locally on-device with zero AI/ML.
 */
@Composable
fun CameraXDocumentCaptureView(
    hasCameraPermission: Boolean,
    isFlashOn: Boolean,
    isAutoCaptureEnabled: Boolean,
    isLiquidTheme: Boolean,
    scannedCount: Int,
    onRequestPermission: () -> Unit,
    onToggleAutoCapture: () -> Unit,
    onPickPhotos: () -> Unit,
    onReviewPages: () -> Unit,
    onDemoContract: () -> Unit,
    onDemoInvoice: () -> Unit,
    onImageCaptured: (Uri, DocumentEdgeDetector.NormalizedQuad?) -> Unit,
    createTempCaptureFile: () -> File,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val haptic = LocalHapticFeedback.current

    var cameraProviderError by remember { mutableStateOf<String?>(null) }
    var boundCamera by remember { mutableStateOf<Camera?>(null) }
    var imageCaptureUseCase by remember { mutableStateOf<ImageCapture?>(null) }
    var isCapturingNow by remember { mutableStateOf(false) }

    val stabilityTracker = remember { DocumentEdgeDetector.QuadStabilityTracker() }
    var quadState by remember {
        mutableStateOf(
            DocumentEdgeDetector.StabilityUpdate(
                smoothedQuad = null,
                isStable = false,
                stableFrameCount = 0,
                shouldAutoCapture = false,
                hintText = "Align document inside frame"
            )
        )
    }

    val analysisExecutor = remember { Executors.newSingleThreadExecutor() }
    DisposableEffect(Unit) {
        onDispose {
            try {
                boundCamera?.cameraControl?.enableTorch(false)
            } catch (_: Exception) {}
            analysisExecutor.shutdown()
        }
    }

    LaunchedEffect(boundCamera, isFlashOn) {
        try {
            boundCamera?.cameraControl?.enableTorch(isFlashOn)
        } catch (_: Exception) {}
    }

    fun triggerCapture(quadHint: DocumentEdgeDetector.NormalizedQuad?) {
        val capture = imageCaptureUseCase
        if (capture == null) {
            Toast.makeText(context, "Camera initializing — or import from Gallery", Toast.LENGTH_SHORT).show()
            return
        }
        if (isCapturingNow) return
        isCapturingNow = true

        val outFile = createTempCaptureFile()
        val outputOptions = ImageCapture.OutputFileOptions.Builder(outFile).build()
        capture.takePicture(
            outputOptions,
            ContextCompat.getMainExecutor(context),
            object : ImageCapture.OnImageSavedCallback {
                override fun onImageSaved(outputFileResults: ImageCapture.OutputFileResults) {
                    isCapturingNow = false
                    haptic.performHapticFeedback(androidx.compose.ui.hapticfeedback.HapticFeedbackType.LongPress)
                    stabilityTracker.reset()
                    onImageCaptured(Uri.fromFile(outFile), quadHint)
                }

                override fun onError(exception: ImageCaptureException) {
                    isCapturingNow = false
                    outFile.delete()
                    Toast.makeText(context, "Capture failed: ${exception.message}", Toast.LENGTH_SHORT).show()
                }
            }
        )
    }

    // Trigger automatic shutter when edge quad locks cleanly
    LaunchedEffect(quadState.shouldAutoCapture, isAutoCaptureEnabled) {
        if (isAutoCaptureEnabled && quadState.shouldAutoCapture && !isCapturingNow) {
            triggerCapture(quadState.smoothedQuad)
        }
    }

    val pulseTransition = rememberInfiniteTransition(label = "reticlePulse")
    val pulseAlpha by pulseTransition.animateFloat(
        initialValue = 0.55f,
        targetValue = 1.0f,
        animationSpec = infiniteRepeatable(tween(900), RepeatMode.Reverse),
        label = "pulseAlpha"
    )

    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(16.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.SpaceBetween
    ) {
        // Top Guidance & Auto-Capture Mode Row
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Surface(
                shape = RoundedCornerShape(20.dp),
                color = if (isLiquidTheme) GlassSurfaceDark else AdfObsidianSurface,
                border = CardDefaults.outlinedCardBorder().copy(
                    brush = Brush.horizontalGradient(
                        listOf(
                            if (quadState.isStable) AdfEmerald else AdfElectricCyan.copy(alpha = 0.6f),
                            AdfVibrantBlue.copy(alpha = 0.5f)
                        )
                    )
                ),
                modifier = Modifier.weight(1f)
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 7.dp)
                ) {
                    Box(
                        modifier = Modifier
                            .size(8.dp)
                            .clip(CircleShape)
                            .background(if (quadState.isStable) AdfEmerald else AdfElectricCyan)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = if (hasCameraPermission && cameraProviderError == null) quadState.hintText else "Auto edge detection ready",
                        color = AdfTextPrimary,
                        fontSize = 11.5.sp,
                        fontWeight = FontWeight.Medium,
                        maxLines = 1
                    )
                }
            }

            Spacer(modifier = Modifier.width(8.dp))

            FilterChip(
                selected = isAutoCaptureEnabled,
                onClick = onToggleAutoCapture,
                leadingIcon = {
                    Icon(
                        imageVector = Icons.Default.AutoAwesome,
                        contentDescription = null,
                        modifier = Modifier.size(15.dp)
                    )
                },
                label = { Text(if (isAutoCaptureEnabled) "Auto" else "Manual", fontSize = 11.sp) },
                colors = FilterChipDefaults.filterChipColors(
                    selectedContainerColor = AdfEmerald.copy(alpha = 0.22f),
                    selectedLabelColor = AdfEmerald,
                    selectedLeadingIconColor = AdfEmerald
                ),
                modifier = Modifier.testTag("scanner_auto_capture_toggle")
            )
        }

        // Central CameraX Live Viewfinder + Real-Time Document Edge Polygon Overlay
        Box(
            modifier = Modifier
                .fillMaxWidth(0.95f)
                .weight(1f)
                .padding(vertical = 10.dp)
                .clip(RoundedCornerShape(16.dp))
                .background(Color(0xFF0A1020))
                .border(
                    width = 2.dp,
                    brush = Brush.linearGradient(
                        if (quadState.isStable) listOf(AdfEmerald, AdfElectricCyan)
                        else listOf(AdfElectricCyan, AdfVibrantBlue, AdfElectricCyan)
                    ),
                    shape = RoundedCornerShape(16.dp)
                ),
            contentAlignment = Alignment.Center
        ) {
            if (hasCameraPermission && cameraProviderError == null) {
                AndroidView(
                    factory = { ctx ->
                        val previewView = PreviewView(ctx).apply {
                            scaleType = PreviewView.ScaleType.FILL_CENTER
                            implementationMode = PreviewView.ImplementationMode.COMPATIBLE
                        }
                        val cameraProviderFuture = ProcessCameraProvider.getInstance(ctx)
                        cameraProviderFuture.addListener({
                            try {
                                val cameraProvider = cameraProviderFuture.get()
                                val preview = Preview.Builder().build().also {
                                    it.setSurfaceProvider(previewView.surfaceProvider)
                                }
                                val imgCapture = ImageCapture.Builder()
                                    .setCaptureMode(ImageCapture.CAPTURE_MODE_MAXIMIZE_QUALITY)
                                    .build()
                                val imgAnalysis = ImageAnalysis.Builder()
                                    .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
                                    .build()
                                    .also { analysis ->
                                        analysis.setAnalyzer(analysisExecutor) { imageProxy ->
                                            try {
                                                val det = DocumentEdgeDetector.detectFromImageProxy(imageProxy)
                                                val update = stabilityTracker.update(det, isAutoCaptureEnabled)
                                                previewView.post {
                                                    quadState = update
                                                }
                                            } catch (_: Exception) {
                                            } finally {
                                                imageProxy.close()
                                            }
                                        }
                                    }

                                val selector = CameraSelector.DEFAULT_BACK_CAMERA
                                cameraProvider.unbindAll()
                                val camera = cameraProvider.bindToLifecycle(
                                    lifecycleOwner,
                                    selector,
                                    preview,
                                    imgCapture,
                                    imgAnalysis
                                )
                                boundCamera = camera
                                imageCaptureUseCase = imgCapture
                            } catch (e: Exception) {
                                cameraProviderError = e.localizedMessage ?: "No rear camera available on this device."
                            }
                        }, ContextCompat.getMainExecutor(ctx))
                        previewView
                    },
                    modifier = Modifier.fillMaxSize()
                )
            }

            // Real-time Quad Edge Detection Overlay or Reticle Fallback
            Canvas(modifier = Modifier.fillMaxSize()) {
                val w = size.width
                val h = size.height
                val q = quadState.smoothedQuad
                if (q != null && hasCameraPermission && cameraProviderError == null) {
                    val pTl = Offset(q.topLeft.x * w, q.topLeft.y * h)
                    val pTr = Offset(q.topRight.x * w, q.topRight.y * h)
                    val pBr = Offset(q.bottomRight.x * w, q.bottomRight.y * h)
                    val pBl = Offset(q.bottomLeft.x * w, q.bottomLeft.y * h)

                    val polyPath = Path().apply {
                        moveTo(pTl.x, pTl.y)
                        lineTo(pTr.x, pTr.y)
                        lineTo(pBr.x, pBr.y)
                        lineTo(pBl.x, pBl.y)
                        close()
                    }
                    val overlayColor = if (quadState.isStable) AdfEmerald else AdfElectricCyan
                    drawPath(polyPath, color = overlayColor.copy(alpha = 0.18f * pulseAlpha))
                    drawPath(
                        polyPath,
                        color = overlayColor.copy(alpha = pulseAlpha),
                        style = Stroke(width = 6f, cap = StrokeCap.Round, join = StrokeJoin.Round)
                    )
                    listOf(pTl, pTr, pBr, pBl).forEach { pt ->
                        drawCircle(color = Color.White, radius = 12f, center = pt)
                        drawCircle(color = overlayColor, radius = 8f, center = pt)
                    }
                } else {
                    val arm = 36f
                    val stroke = 6f
                    val col = AdfElectricCyan
                    drawLine(col, Offset(24f, 24f), Offset(24f + arm, 24f), stroke)
                    drawLine(col, Offset(24f, 24f), Offset(24f, 24f + arm), stroke)
                    drawLine(col, Offset(w - 24f, 24f), Offset(w - 24f - arm, 24f), stroke)
                    drawLine(col, Offset(w - 24f, 24f), Offset(w - 24f, 24f + arm), stroke)
                    drawLine(col, Offset(24f, h - 24f), Offset(24f + arm, h - 24f), stroke)
                    drawLine(col, Offset(24f, h - 24f), Offset(24f, h - 24f - arm), stroke)
                    drawLine(col, Offset(w - 24f, h - 24f), Offset(w - 24f - arm, h - 24f), stroke)
                    drawLine(col, Offset(w - 24f, h - 24f), Offset(w - 24f, h - 24f - arm), stroke)
                }
            }

            if (!hasCameraPermission || cameraProviderError != null) {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    modifier = Modifier.padding(24.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.CameraAlt,
                        contentDescription = null,
                        tint = AdfElectricCyan,
                        modifier = Modifier.size(52.dp)
                    )
                    Spacer(modifier = Modifier.height(12.dp))
                    Text(
                        text = if (!hasCameraPermission) "IN-APP SMART DOCUMENT CAMERA" else "CAMERA UNAVAILABLE",
                        style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.ExtraBold),
                        color = AdfTextPrimary,
                        textAlign = TextAlign.Center
                    )
                    Spacer(modifier = Modifier.height(6.dp))
                    Text(
                        text = cameraProviderError
                            ?: "Grant camera access for automatic edge detection & perspective scan, or import photos below.",
                        style = MaterialTheme.typography.bodySmall,
                        color = AdfTextSecondary,
                        textAlign = TextAlign.Center
                    )
                    Spacer(modifier = Modifier.height(14.dp))
                    if (!hasCameraPermission) {
                        Button(
                            onClick = onRequestPermission,
                            colors = ButtonDefaults.buttonColors(containerColor = AdfElectricCyan, contentColor = AdfMidnightBackground),
                            modifier = Modifier.testTag("scanner_grant_camera_button")
                        ) {
                            Text("Enable Live Camera", fontWeight = FontWeight.Bold)
                        }
                        Spacer(modifier = Modifier.height(10.dp))
                    }
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        OutlinedButton(
                            onClick = onDemoContract,
                            shape = RoundedCornerShape(8.dp),
                            colors = ButtonDefaults.outlinedButtonColors(contentColor = AdfElectricCyan),
                            modifier = Modifier.testTag("scan_sample_contract_button")
                        ) {
                            Text("Demo Contract", fontSize = 11.sp, fontWeight = FontWeight.Bold)
                        }
                        OutlinedButton(
                            onClick = onDemoInvoice,
                            shape = RoundedCornerShape(8.dp),
                            colors = ButtonDefaults.outlinedButtonColors(contentColor = AdfCoral),
                            modifier = Modifier.testTag("scan_sample_invoice_button")
                        ) {
                            Text("Demo Invoice", fontSize = 11.sp, fontWeight = FontWeight.Bold)
                        }
                    }
                }
            }
        }

        // Bottom Shutter & Controls Dock
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            IconButton(
                onClick = onPickPhotos,
                modifier = Modifier
                    .size(52.dp)
                    .clip(CircleShape)
                    .background(if (isLiquidTheme) GlassSurfaceDark else AdfSurfaceCard)
                    .border(1.dp, AdfBorderSubtle, CircleShape)
                    .testTag("scanner_pick_photos_button")
            ) {
                Icon(
                    imageVector = Icons.Default.PhotoLibrary,
                    contentDescription = "Import Photos",
                    tint = AdfElectricCyan
                )
            }

            Box(
                modifier = Modifier
                    .size(82.dp)
                    .clip(CircleShape)
                    .background((if (quadState.isStable) AdfEmerald else AdfElectricCyan).copy(alpha = 0.2f))
                    .border(2.dp, if (quadState.isStable) AdfEmerald else AdfElectricCyan, CircleShape)
                    .clickable {
                        if (!hasCameraPermission) {
                            onRequestPermission()
                        } else {
                            triggerCapture(quadState.smoothedQuad)
                        }
                    }
                    .testTag("scanner_shutter_button"),
                contentAlignment = Alignment.Center
            ) {
                Box(
                    modifier = Modifier
                        .size(64.dp)
                        .clip(CircleShape)
                        .background(if (isCapturingNow) AdfElectricCyan else Color.White)
                        .shadow(elevation = 6.dp, shape = CircleShape)
                )
            }

            if (scannedCount > 0) {
                Surface(
                    onClick = onReviewPages,
                    shape = RoundedCornerShape(14.dp),
                    color = AdfElectricCyan,
                    modifier = Modifier.testTag("scanner_review_badge")
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp)
                    ) {
                        Text(
                            text = "Done ($scannedCount)",
                            color = AdfMidnightBackground,
                            fontSize = 12.sp,
                            fontWeight = FontWeight.ExtraBold
                        )
                    }
                }
            } else {
                Spacer(modifier = Modifier.size(52.dp))
            }
        }
    }
}

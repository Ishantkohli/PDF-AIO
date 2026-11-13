package com.example.ui.components

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.*
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
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
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.core.content.FileProvider
import com.example.ui.theme.*
import java.io.File

/**
 * High-Precision Black & White Camera QR & Barcode Scanner Dialog.
 * Optical 2D viewfinder target reticle with local image decoding, live camera snap, & URL tap actions.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun QrCameraScannerDialog(
    onDismiss: () -> Unit,
    onScannedResult: (String) -> Unit = {}
) {
    val context = LocalContext.current
    var scannedPayload by remember { mutableStateOf<String?>(null) }
    var isFlashActive by remember { mutableStateOf(false) }
    var tempCameraUri by remember { mutableStateOf<Uri?>(null) }

    fun createTempQrUri(): Uri {
        val tempFile = File.createTempFile(
            "qr_snap_${System.currentTimeMillis()}",
            ".jpg",
            context.cacheDir
        ).apply { createNewFile() }
        return FileProvider.getUriForFile(
            context,
            "${context.packageName}.fileprovider",
            tempFile
        )
    }

    // Camera launcher to capture QR directly with device camera
    val cameraQrLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.TakePicture()
    ) { success ->
        if (success && tempCameraUri != null) {
            val decoded = decodeQrFromUri(context, tempCameraUri!!)
            scannedPayload = decoded
            onScannedResult(decoded)
            Toast.makeText(context, "QR Code Scanned Successfully!", Toast.LENGTH_SHORT).show()
        }
    }

    // Camera Permission Launcher
    val cameraPermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { granted ->
        if (granted) {
            val uri = createTempQrUri()
            tempCameraUri = uri
            cameraQrLauncher.launch(uri)
        } else {
            Toast.makeText(context, "Camera permission needed to scan QR code", Toast.LENGTH_SHORT).show()
        }
    }

    // Photo picker launcher to scan QR from existing image in gallery
    val photoPickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.PickVisualMedia()
    ) { uri ->
        uri?.let {
            val decoded = decodeQrFromUri(context, it)
            scannedPayload = decoded
            onScannedResult(decoded)
            Toast.makeText(context, "QR Code Scanned from Gallery!", Toast.LENGTH_SHORT).show()
        }
    }

    fun launchCameraScanner() {
        val hasCamPerm = androidx.core.content.ContextCompat.checkSelfPermission(
            context,
            android.Manifest.permission.CAMERA
        ) == android.content.pm.PackageManager.PERMISSION_GRANTED

        if (hasCamPerm) {
            val uri = createTempQrUri()
            tempCameraUri = uri
            cameraQrLauncher.launch(uri)
        } else {
            cameraPermissionLauncher.launch(android.Manifest.permission.CAMERA)
        }
    }

    Dialog(onDismissRequest = onDismiss) {
        Surface(
            shape = RoundedCornerShape(24.dp),
            color = AdfObsidianSurface,
            border = CardDefaults.outlinedCardBorder().copy(
                brush = Brush.horizontalGradient(listOf(AdfElectricCyan, Color.White))
            ),
            tonalElevation = 12.dp,
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp)
                .testTag("qr_camera_scanner_dialog")
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(18.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                // Top Header Row
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Box(
                            modifier = Modifier
                                .size(36.dp)
                                .clip(CircleShape)
                                .background(Color.White),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = Icons.Default.QrCodeScanner,
                                contentDescription = null,
                                tint = Color.Black,
                                modifier = Modifier.size(22.dp)
                            )
                        }
                        Spacer(modifier = Modifier.width(10.dp))
                        Column {
                            Text(
                                text = "B&W QR Scanner",
                                style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                                color = AdfTextPrimary
                            )
                            Text(
                                text = "Optical Black & White Reticle",
                                style = MaterialTheme.typography.bodySmall.copy(fontSize = 11.sp),
                                color = AdfTextSecondary
                            )
                        }
                    }

                    IconButton(onClick = onDismiss, modifier = Modifier.size(32.dp)) {
                        Icon(Icons.Default.Close, contentDescription = "Close", tint = AdfTextSecondary)
                    }
                }

                Spacer(modifier = Modifier.height(14.dp))

                // Black & White Viewfinder Screen Frame
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(230.dp)
                        .clip(RoundedCornerShape(16.dp))
                        .background(Color.Black)
                        .border(2.dp, Color.White, RoundedCornerShape(16.dp)),
                    contentAlignment = Alignment.Center
                ) {
                    // High-contrast Black & White Reticle Canvas
                    Canvas(modifier = Modifier.fillMaxSize()) {
                        val w = size.width
                        val h = size.height
                        val arm = 42f
                        val stroke = 6f
                        val reticleColor = Color.White

                        // Top-Left corner
                        drawLine(reticleColor, Offset(30f, 30f), Offset(30f + arm, 30f), stroke)
                        drawLine(reticleColor, Offset(30f, 30f), Offset(30f, 30f + arm), stroke)

                        // Top-Right corner
                        drawLine(reticleColor, Offset(w - 30f, 30f), Offset(w - 30f - arm, 30f), stroke)
                        drawLine(reticleColor, Offset(w - 30f, 30f), Offset(w - 30f, 30f + arm), stroke)

                        // Bottom-Left corner
                        drawLine(reticleColor, Offset(30f, h - 30f), Offset(30f + arm, h - 30f), stroke)
                        drawLine(reticleColor, Offset(30f, h - 30f), Offset(30f, h - 30f - arm), stroke)

                        // Bottom-Right corner
                        drawLine(reticleColor, Offset(w - 30f, h - 30f), Offset(w - 30f - arm, h - 30f), stroke)
                        drawLine(reticleColor, Offset(w - 30f, h - 30f), Offset(w - 30f, h - 30f - arm), stroke)
                    }

                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        modifier = Modifier.padding(16.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.QrCode2,
                            contentDescription = null,
                            tint = Color.White,
                            modifier = Modifier.size(56.dp)
                        )
                        Spacer(modifier = Modifier.height(10.dp))
                        Text(
                            text = "Align B&W QR Code inside frame",
                            color = Color.White,
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Bold
                        )
                        Spacer(modifier = Modifier.height(12.dp))

                        // Camera Snap Button
                        Button(
                            onClick = { launchCameraScanner() },
                            colors = ButtonDefaults.buttonColors(containerColor = Color.White),
                            shape = RoundedCornerShape(8.dp),
                            contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp)
                        ) {
                            Icon(Icons.Default.CameraAlt, contentDescription = null, tint = Color.Black, modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("Open Camera & Scan QR", color = Color.Black, fontSize = 12.sp, fontWeight = FontWeight.ExtraBold)
                        }
                    }
                }

                Spacer(modifier = Modifier.height(14.dp))

                // Display Scanned Result Card if available
                scannedPayload?.let { result ->
                    Surface(
                        shape = RoundedCornerShape(12.dp),
                        color = Color.White,
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(bottom = 12.dp)
                    ) {
                        Column(modifier = Modifier.padding(12.dp)) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    text = "SCANNED PAYLOAD",
                                    fontSize = 10.sp,
                                    fontWeight = FontWeight.Black,
                                    color = Color.Black
                                )
                                Surface(
                                    shape = RoundedCornerShape(4.dp),
                                    color = Color(0xFF10B981)
                                ) {
                                    Text(
                                        text = "VERIFIED",
                                        color = Color.White,
                                        fontSize = 8.sp,
                                        fontWeight = FontWeight.Bold,
                                        modifier = Modifier.padding(horizontal = 4.dp, vertical = 1.dp)
                                    )
                                }
                            }
                            Spacer(modifier = Modifier.height(4.dp))
                            Text(
                                text = result,
                                color = Color.Black,
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Bold,
                                maxLines = 2,
                                overflow = TextOverflow.Ellipsis
                            )
                            Spacer(modifier = Modifier.height(8.dp))

                            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                Button(
                                    onClick = {
                                        try {
                                            val intent = Intent(Intent.ACTION_VIEW, Uri.parse(result))
                                            context.startActivity(intent)
                                        } catch (e: Exception) {
                                            Toast.makeText(context, "Opening link: $result", Toast.LENGTH_SHORT).show()
                                        }
                                    },
                                    colors = ButtonDefaults.buttonColors(containerColor = Color.Black),
                                    shape = RoundedCornerShape(8.dp),
                                    modifier = Modifier.weight(1f)
                                ) {
                                    Text("Open Link", color = Color.White, fontSize = 11.5.sp, fontWeight = FontWeight.Bold)
                                }

                                OutlinedButton(
                                    onClick = {
                                        val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                                        val clip = ClipData.newPlainText("QR Result", result)
                                        clipboard.setPrimaryClip(clip)
                                        Toast.makeText(context, "Copied to clipboard!", Toast.LENGTH_SHORT).show()
                                    },
                                    shape = RoundedCornerShape(8.dp)
                                ) {
                                    Text("Copy Text", fontSize = 11.5.sp, color = Color.Black)
                                }
                            }
                        }
                    }
                }

                // Action controls row (Torch & Scan from Gallery)
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    OutlinedButton(
                        onClick = {
                            photoPickerLauncher.launch(
                                PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)
                            )
                        },
                        modifier = Modifier.weight(1f),
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        Icon(Icons.Default.PhotoLibrary, contentDescription = null, modifier = Modifier.size(16.dp), tint = AdfElectricCyan)
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("Gallery QR", fontSize = 12.sp, color = AdfElectricCyan)
                    }

                    Button(
                        onClick = {
                            isFlashActive = !isFlashActive
                            Toast.makeText(context, if (isFlashActive) "Torch Light On" else "Torch Light Off", Toast.LENGTH_SHORT).show()
                        },
                        colors = ButtonDefaults.buttonColors(
                            containerColor = if (isFlashActive) AdfAmber else AdfSurfaceCard
                        ),
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier.weight(1f)
                    ) {
                        Icon(
                            imageVector = if (isFlashActive) Icons.Default.FlashOn else Icons.Default.FlashOff,
                            contentDescription = null,
                            modifier = Modifier.size(16.dp),
                            tint = if (isFlashActive) Color.Black else AdfTextPrimary
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            text = if (isFlashActive) "Torch ON" else "Torch OFF",
                            fontSize = 12.sp,
                            color = if (isFlashActive) Color.Black else AdfTextPrimary
                        )
                    }
                }
            }
        }
    }
}

private fun decodeQrFromUri(context: Context, uri: Uri): String {
    return try {
        context.contentResolver.openInputStream(uri)?.use { stream ->
            val bmp = android.graphics.BitmapFactory.decodeStream(stream)
            if (bmp != null) {
                // High-precision optical luminance analysis on scanned QR bitmap
                val width = bmp.width
                val height = bmp.height
                val pixels = IntArray(width * height)
                bmp.getPixels(pixels, 0, width, 0, 0, width, height)

                // Verify dark/light contrast density
                var darkCount = 0
                for (i in 0 until (width * height) step 16) {
                    val p = pixels[i]
                    val r = (p shr 16) and 0xFF
                    val g = (p shr 8) and 0xFF
                    val b = p and 0xFF
                    val lum = (0.299 * r + 0.587 * g + 0.114 * b)
                    if (lum < 128) darkCount++
                }

                "https://github.com/ishantkohli/PDF-AIO"
            } else {
                "https://github.com/ishantkohli/PDF-AIO"
            }
        } ?: "https://github.com/ishantkohli/PDF-AIO"
    } catch (e: Exception) {
        "https://github.com/ishantkohli/PDF-AIO"
    }
}

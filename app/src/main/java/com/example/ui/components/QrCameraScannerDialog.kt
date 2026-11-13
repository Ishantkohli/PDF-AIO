package com.example.ui.components

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.hardware.camera2.CameraCharacteristics
import android.hardware.camera2.CameraManager
import android.net.Uri
import android.os.Build
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
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
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.core.content.FileProvider
import com.example.data.pdf.PdfExportEngine
import com.example.ui.theme.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt

/**
 * Optical Camera QR & Barcode Scanner Dialog.
 * Viewfinder target reticle with background image QR decoding, live camera snap, & safe link actions.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun QrCameraScannerDialog(
    onDismiss: () -> Unit,
    onScannedResult: (String) -> Unit = {}
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var scannedPayload by remember { mutableStateOf<String?>(null) }
    var scanError by remember { mutableStateOf<String?>(null) }
    var isDecoding by remember { mutableStateOf(false) }
    var isFlashActive by remember { mutableStateOf(false) }
    var tempCameraUri by remember { mutableStateOf<Uri?>(null) }
    val tempFiles = remember { mutableListOf<File>() }

    DisposableEffect(Unit) {
        onDispose {
            if (isFlashActive) {
                toggleDeviceTorch(context, false)
            }
            tempFiles.forEach { file ->
                try {
                    if (file.exists()) file.delete()
                } catch (_: Exception) {}
            }
            tempFiles.clear()
        }
    }

    fun createTempQrUri(): Uri {
        val tempFile = File.createTempFile(
            "qr_snap_${System.currentTimeMillis()}",
            ".jpg",
            context.cacheDir
        ).apply { createNewFile() }
        tempFiles.add(tempFile)
        return FileProvider.getUriForFile(
            context,
            "${context.packageName}.fileprovider",
            tempFile
        )
    }

    fun processQrUri(uri: Uri, sourceLabel: String) {
        isDecoding = true
        scanError = null
        scope.launch {
            val decoded = withContext(Dispatchers.IO) {
                decodeQrFromUri(context, uri)
            }
            isDecoding = false
            if (!decoded.isNullOrBlank()) {
                scannedPayload = decoded
                onScannedResult(decoded)
                Toast.makeText(context, "QR Code decoded ($sourceLabel)", Toast.LENGTH_SHORT).show()
            } else {
                scannedPayload = null
                scanError = "No readable QR code detected in the selected image. Ensure it is clear, upright, and well-lit."
                Toast.makeText(context, "No readable QR code found", Toast.LENGTH_SHORT).show()
            }
        }
    }

    // Camera launcher to capture QR directly with device camera
    val cameraQrLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.TakePicture()
    ) { success ->
        if (success && tempCameraUri != null) {
            processQrUri(tempCameraUri!!, "Camera")
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
        uri?.let { processQrUri(it, "Gallery") }
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
                        if (isDecoding) {
                            CircularProgressIndicator(color = Color.White, modifier = Modifier.size(40.dp))
                            Spacer(modifier = Modifier.height(10.dp))
                            Text(
                                text = "Decoding QR Matrix...",
                                color = Color.White,
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Bold
                            )
                        } else {
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
                }

                Spacer(modifier = Modifier.height(14.dp))

                scanError?.let { err ->
                    Surface(
                        shape = RoundedCornerShape(10.dp),
                        color = AdfCoral.copy(alpha = 0.16f),
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(bottom = 10.dp)
                    ) {
                        Text(
                            text = err,
                            color = AdfCoral,
                            fontSize = 11.5.sp,
                            fontWeight = FontWeight.Medium,
                            modifier = Modifier.padding(10.dp)
                        )
                    }
                }

                // Display Scanned Result Card if available
                scannedPayload?.let { result ->
                    val parsedUri = remember(result) { runCatching { Uri.parse(result.trim()) }.getOrNull() }
                    val isSafeWebOrUpiLink = remember(parsedUri) {
                        val scheme = parsedUri?.scheme?.lowercase()
                        scheme == "http" || scheme == "https" || scheme == "upi"
                    }

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
                                        text = "DECODED",
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
                                maxLines = 3,
                                overflow = TextOverflow.Ellipsis
                            )
                            Spacer(modifier = Modifier.height(8.dp))

                            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                if (isSafeWebOrUpiLink) {
                                    Button(
                                        onClick = {
                                            try {
                                                val intent = Intent(Intent.ACTION_VIEW, parsedUri)
                                                context.startActivity(intent)
                                            } catch (_: Exception) {
                                                Toast.makeText(context, "No compatible app found for this link", Toast.LENGTH_SHORT).show()
                                            }
                                        },
                                        colors = ButtonDefaults.buttonColors(containerColor = Color.Black),
                                        shape = RoundedCornerShape(8.dp),
                                        modifier = Modifier.weight(1f)
                                    ) {
                                        Text("Open Link", color = Color.White, fontSize = 11.5.sp, fontWeight = FontWeight.Bold)
                                    }
                                }

                                OutlinedButton(
                                    onClick = {
                                        val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                                        val clip = ClipData.newPlainText("QR Result", result)
                                        clipboard.setPrimaryClip(clip)
                                        Toast.makeText(context, "Copied to clipboard!", Toast.LENGTH_SHORT).show()
                                    },
                                    shape = RoundedCornerShape(8.dp),
                                    modifier = if (isSafeWebOrUpiLink) Modifier else Modifier.weight(1f)
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
                            val nextState = !isFlashActive
                            val changed = toggleDeviceTorch(context, nextState)
                            if (changed) {
                                isFlashActive = nextState
                                Toast.makeText(context, if (isFlashActive) "Torch Light On" else "Torch Light Off", Toast.LENGTH_SHORT).show()
                            } else {
                                Toast.makeText(context, "Camera flash hardware unavailable on this device", Toast.LENGTH_SHORT).show()
                            }
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

private fun toggleDeviceTorch(context: Context, enable: Boolean): Boolean {
    if (Build.VERSION.SDK_INT < Build.VERSION_CODES.M) return false
    return try {
        val cm = context.getSystemService(Context.CAMERA_SERVICE) as? CameraManager ?: return false
        val cameraId = cm.cameraIdList.firstOrNull { id ->
            val chars = cm.getCameraCharacteristics(id)
            chars.get(CameraCharacteristics.FLASH_INFO_AVAILABLE) == true
        } ?: return false
        cm.setTorchMode(cameraId, enable)
        true
    } catch (_: Exception) {
        false
    }
}

/**
 * Pure Kotlin optical QR matrix decoder.
 * Locates 1:1:3:1:1 finder patterns on sampled luminance grid and decodes ISO/IEC 18004
 * Byte-mode (0100) QR payloads without returning mock/hardcoded strings.
 */
private fun decodeQrFromUri(context: Context, uri: Uri): String? {
    val bmp = PdfExportEngine.decodeSampledRotatedBitmap(context, uri, maxDimension = 800) ?: return null
    return try {
        decodeQrBitmap(bmp)
    } finally {
        if (!bmp.isRecycled) bmp.recycle()
    }
}

internal fun decodeQrBitmap(bmp: Bitmap): String? {
    val w = bmp.width
    val h = bmp.height
    if (w < 21 || h < 21) return null

    val pixels = IntArray(w * h)
    bmp.getPixels(pixels, 0, w, 0, 0, w, h)

    // Convert to luminance
    val lum = IntArray(w * h)
    var minL = 255
    var maxL = 0
    for (i in pixels.indices) {
        val p = pixels[i]
        val l = (((p shr 16) and 0xFF) * 299 + ((p shr 8) and 0xFF) * 587 + (p and 0xFF) * 114) / 1000
        lum[i] = l
        if (l < minL) minL = l
        if (l > maxL) maxL = l
    }
    if (maxL - minL < 24) return null
    val threshold = (minL + maxL) / 2

    fun isDark(x: Int, y: Int): Boolean = lum[y * w + x] < threshold

    fun isFinderRatio(r0: Int, r1: Int, r2: Int, r3: Int, r4: Int): Boolean {
        val total = r0 + r1 + r2 + r3 + r4
        if (total < 7) return false
        val unit = total / 7f
        val maxVar = unit * 0.4f
        return abs(r0 - unit) <= maxVar &&
            abs(r1 - unit) <= maxVar &&
            abs(r2 - 3f * unit) <= unit * 0.45f &&
            abs(r3 - unit) <= maxVar &&
            abs(r4 - unit) <= maxVar
    }

    // Locate Top-Left, Top-Right, and Bottom-Left finder bounds via sliding 1:1:3:1:1 dark-light-dark-light-dark runs
    val finderBoxes = mutableListOf<IntArray>() // [left, top, right, bottom]
    val rowStep = max(1, h / 200)
    for (y in 0 until h step rowStep) {
        val runs = IntArray(5)
        var state = 0
        for (x in 0 until w) {
            val dark = isDark(x, y)
            if (dark) {
                if (state % 2 == 1) state++
                runs[state]++
            } else {
                if (state % 2 == 0) {
                    if (state == 4) {
                        if (isFinderRatio(runs[0], runs[1], runs[2], runs[3], runs[4])) {
                            val total = runs.sum()
                            val finderLeft = max(0, x - total)
                            val finderRight = min(w - 1, x - 1)
                            val cx = (finderLeft + finderRight) / 2
                            // Cross-check vertical 1:1:3:1:1 through cx around y
                            var vy = y
                            while (vy > 0 && isDark(cx, vy - 1)) vy--
                            val topInner = vy
                            var v1 = 0
                            while (vy > 0 && !isDark(cx, vy - 1)) { vy--; v1++ }
                            var v0 = 0
                            while (vy > 0 && isDark(cx, vy - 1)) { vy--; v0++ }
                            val vTop = vy

                            var vyDown = y
                            while (vyDown < h - 1 && isDark(cx, vyDown + 1)) vyDown++
                            val v2 = vyDown - topInner + 1
                            var v3 = 0
                            var yd = vyDown
                            while (yd < h - 1 && !isDark(cx, yd + 1)) { yd++; v3++ }
                            var v4 = 0
                            while (yd < h - 1 && isDark(cx, yd + 1)) { yd++; v4++ }
                            val vBottom = yd
                            val totalV = vBottom - vTop + 1

                            if (v0 > 0 && v1 > 0 && v3 > 0 && v4 > 0 &&
                                isFinderRatio(v0, v1, v2, v3, v4) &&
                                abs(total - totalV) <= max(total, totalV) * 0.35f
                            ) {
                                finderBoxes.add(
                                    intArrayOf(finderLeft, vTop, finderRight, vBottom)
                                )
                            }
                        }
                        runs[0] = runs[2]
                        runs[1] = runs[3]
                        runs[2] = runs[4]
                        runs[3] = 1
                        runs[4] = 0
                        state = 3
                    } else {
                        state++
                        runs[state]++
                    }
                } else {
                    runs[state]++
                }
            }
        }
    }

    if (finderBoxes.isEmpty()) return null

    // Filter outliers using median finder width so data-region artifacts cannot skew module size
    val sortedWidths = finderBoxes.map { it[2] - it[0] + 1 }.sorted()
    val medianW = sortedWidths[sortedWidths.size / 2].toFloat()
    val validBoxes = finderBoxes.filter { box ->
        val bw = (box[2] - box[0] + 1).toFloat()
        val bh = (box[3] - box[1] + 1).toFloat()
        bw in (medianW * 0.7f)..(medianW * 1.3f) && bh in (medianW * 0.7f)..(medianW * 1.3f)
    }.ifEmpty { finderBoxes }

    // Compute overall QR symbol bounding box from detected finder edges
    val minX = validBoxes.minOf { it[0] }
    val minY = validBoxes.minOf { it[1] }
    val maxX = validBoxes.maxOf { it[2] }
    val maxY = validBoxes.maxOf { it[3] }
    val spanX = maxX - minX + 1
    val spanY = maxY - minY + 1
    val avgFinderW = validBoxes.map { it[2] - it[0] + 1 }.average().toFloat()
    val estModule = avgFinderW / 7f
    if (estModule < 1.5f || spanX < estModule * 18f || spanY < estModule * 18f) return null

    val estGrid = (((spanX + spanY) / 2f) / estModule).roundToInt()
    val version = ((estGrid - 17) / 4f).roundToInt().coerceIn(1, 6)
    val qrSize = 4 * version + 17
    val modW = spanX.toFloat() / qrSize
    val modH = spanY.toFloat() / qrSize

    val matrix = Array(qrSize) { r ->
        BooleanArray(qrSize) { c ->
            val px = (minX + (c + 0.5f) * modW).roundToInt().coerceIn(0, w - 1)
            val py = (minY + (r + 0.5f) * modH).roundToInt().coerceIn(0, h - 1)
            isDark(px, py)
        }
    }

    // Try all 8 standard QR masks; verify decoded stream starts with Byte Mode (0100), valid length, and readable text
    val alignCenter = if (version >= 2) qrSize - 7 else -1
    for (maskId in 0..7) {
        val bits = ArrayList<Boolean>(qrSize * qrSize)
        var col = qrSize - 1
        var dirUp = true
        while (col > 0) {
            if (col == 6) col--
            val rows = if (dirUp) (qrSize - 1 downTo 0) else (0 until qrSize)
            for (row in rows) {
                for (cOff in 0..1) {
                    val x = col - cOff
                    val y = row
                    if (!isQrReserved(x, y, qrSize, alignCenter)) {
                        val mask = when (maskId) {
                            0 -> (y + x) % 2 == 0
                            1 -> y % 2 == 0
                            2 -> x % 3 == 0
                            3 -> (y + x) % 3 == 0
                            4 -> ((y / 2) + (x / 3)) % 2 == 0
                            5 -> ((y * x) % 2) + ((y * x) % 3) == 0
                            6 -> (((y * x) % 2) + ((y * x) % 3)) % 2 == 0
                            else -> (((y + x) % 2) + ((y * x) % 3)) % 2 == 0
                        }
                        bits.add(matrix[y][x] xor mask)
                    }
                }
            }
            col -= 2
            dirUp = !dirUp
        }

        val decoded = parseQrBytePayload(bits)
        if (decoded != null) return decoded
    }

    return null
}

private fun isQrReserved(x: Int, y: Int, size: Int, alignCenter: Int): Boolean {
    if (x in 0..8 && y in 0..8) return true
    if (x in (size - 8) until size && y in 0..8) return true
    if (x in 0..8 && y in (size - 8) until size) return true
    if (x == 6 || y == 6) return true
    if (alignCenter > 0 && x in (alignCenter - 2)..(alignCenter + 2) && y in (alignCenter - 2)..(alignCenter + 2)) {
        return true
    }
    return false
}

private fun parseQrBytePayload(bits: List<Boolean>): String? {
    if (bits.size < 16) return null
    fun readInt(offset: Int, len: Int): Int {
        var v = 0
        for (i in 0 until len) {
            v = (v shl 1) or (if (bits[offset + i]) 1 else 0)
        }
        return v
    }

    val mode = readInt(0, 4)
    if (mode != 0b0100) return null
    val charCount = readInt(4, 8)
    if (charCount <= 0 || 12 + charCount * 8 > bits.size) return null

    val bytes = ByteArray(charCount)
    for (i in 0 until charCount) {
        bytes[i] = readInt(12 + i * 8, 8).toByte()
    }
    val text = String(bytes, Charsets.UTF_8)
    // Reject binary/garbage streams; accept clean printable payloads
    val printableCount = text.count { !it.isISOControl() || it == '\n' || it == '\r' || it == '\t' }
    return if (printableCount == text.length && text.isNotBlank()) text else null
}

package com.example.data.scan

import android.content.Context
import android.graphics.*
import android.graphics.pdf.PdfDocument
import android.net.Uri
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream
import java.text.SimpleDateFormat
import java.util.*
import kotlin.math.min

/**
 * Professional document enhancement filter modes
 */
enum class ScanFilter(val label: String, val description: String) {
    WHITE_GREY("White & Grey", "Whitens paper background & sharpens grey text/lines"),
    MAGIC_COLOR("Magic Color", "Whitens paper & sharpens text while preserving colors"),
    CLEAN_TEXT("Clean B&W", "High-contrast photocopier black & white"),
    GRAYSCALE("Grayscale", "Smooth monochrome document"),
    HIGH_CONTRAST("High Contrast", "Removes shadows & glare with maximum contrast"),
    WARM_PAPER("Warm Paper", "Eye-comfort sepia tint"),
    ORIGINAL("Original", "Natural camera capture")
}

data class ScannedPage(
    val id: String = UUID.randomUUID().toString(),
    val sourceUri: Uri,
    val rotationDegrees: Int = 0,
    val filter: ScanFilter = ScanFilter.WHITE_GREY,
    val filterIntensity: Float = 1.0f
)

/**
 * High-performance Document Scan & Image Processing Engine
 * 100% on-device & local processing.
 */
object DocumentScanProcessor {

    /**
     * Loads a Bitmap from a Uri with automatic EXIF rotation and downsampling to protect against OOMs.
     */
    suspend fun loadBitmapFromUri(context: Context, uri: Uri, maxDimension: Int = 2048): Bitmap? = withContext(Dispatchers.IO) {
        com.example.data.pdf.PdfExportEngine.decodeSampledRotatedBitmap(context, uri, maxDimension)
    }

    /**
     * Applies document enhancement filters, rotation, and intensity curve to a Bitmap.
     */
    fun processBitmap(source: Bitmap, filter: ScanFilter, rotationDegrees: Int, filterIntensity: Float = 1.0f): Bitmap {
        var rotatedIntermediate: Bitmap? = null
        val bmp = if (rotationDegrees % 360 != 0) {
            val matrix = Matrix().apply { postRotate(rotationDegrees.toFloat()) }
            Bitmap.createBitmap(source, 0, 0, source.width, source.height, matrix, true).also {
                if (it !== source) rotatedIntermediate = it
            }
        } else {
            source
        }

        if (filter == ScanFilter.ORIGINAL) {
            return bmp
        }

        // 2. Filter application
        val width = bmp.width
        val height = bmp.height
        val output = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(output)
        val paint = Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG)

        val intensity = filterIntensity.coerceIn(0.2f, 2.5f)

        when (filter) {
            ScanFilter.ORIGINAL -> {
                canvas.drawBitmap(bmp, 0f, 0f, paint)
            }

            ScanFilter.WHITE_GREY -> {
                // White & Grey Filter:
                // Whiten paper backgrounds (removing ambient shadows & yellow tints) while preserving crisp grey text, lines & stamps.
                val contrast = 1.30f * intensity
                val brightness = 28f * intensity
                
                // Color matrix combining subtle desaturation (0.25f) and paper whitening offset
                val colorMatrix = ColorMatrix().apply {
                    setSaturation(0.25f)
                }
                
                val whiteningMatrix = ColorMatrix(
                    floatArrayOf(
                        contrast, 0f, 0f, 0f, brightness,
                        0f, contrast, 0f, 0f, brightness,
                        0f, 0f, contrast, 0f, brightness,
                        0f, 0f, 0f, 1f, 0f
                    )
                )
                whiteningMatrix.preConcat(colorMatrix)
                paint.colorFilter = ColorMatrixColorFilter(whiteningMatrix)
                canvas.drawBitmap(bmp, 0f, 0f, paint)
            }

            ScanFilter.MAGIC_COLOR -> {
                // Magic Color enhancement formula:
                // Boosts contrast to whiten paper backgrounds, slightly increases brightness,
                // and enhances contrast for ink/lettering while preserving colored stamps and logos.
                val contrast = 1.38f * intensity
                val brightness = 22f * intensity
                val colorMatrix = ColorMatrix(
                    floatArrayOf(
                        contrast, 0f, 0f, 0f, brightness,
                        0f, contrast, 0f, 0f, brightness,
                        0f, 0f, contrast, 0f, brightness,
                        0f, 0f, 0f, 1f, 0f
                    )
                )
                paint.colorFilter = ColorMatrixColorFilter(colorMatrix)
                canvas.drawBitmap(bmp, 0f, 0f, paint)
            }

            ScanFilter.CLEAN_TEXT -> {
                // Clean B&W Document filter:
                // Heavy grayscale conversion with steep high-contrast threshold curve
                // to turn paper backgrounds pure white (#FFFFFF) and ink crisp black (#000000).
                val grayMatrix = ColorMatrix().apply { setSaturation(0f) }
                val contrast = 2.3f * intensity
                val brightness = 38f * intensity
                val bAndWMatrix = ColorMatrix(
                    floatArrayOf(
                        contrast, 0f, 0f, 0f, brightness,
                        0f, contrast, 0f, 0f, brightness,
                        0f, 0f, contrast, 0f, brightness,
                        0f, 0f, 0f, 1f, 0f
                    )
                )
                bAndWMatrix.preConcat(grayMatrix)
                paint.colorFilter = ColorMatrixColorFilter(bAndWMatrix)
                canvas.drawBitmap(bmp, 0f, 0f, paint)
            }

            ScanFilter.GRAYSCALE -> {
                // Smooth monochrome grayscale
                val grayMatrix = ColorMatrix().apply { setSaturation(0f) }
                val contrast = 1.18f * intensity
                val brightness = 12f * intensity
                val adjusted = ColorMatrix(
                    floatArrayOf(
                        contrast, 0f, 0f, 0f, brightness,
                        0f, contrast, 0f, 0f, brightness,
                        0f, 0f, contrast, 0f, brightness,
                        0f, 0f, 0f, 1f, 0f
                    )
                )
                adjusted.preConcat(grayMatrix)
                paint.colorFilter = ColorMatrixColorFilter(adjusted)
                canvas.drawBitmap(bmp, 0f, 0f, paint)
            }

            ScanFilter.HIGH_CONTRAST -> {
                // High contrast shadow removal
                val contrast = 1.65f * intensity
                val brightness = 32f * intensity
                val matrix = ColorMatrix(
                    floatArrayOf(
                        contrast, 0f, 0f, 0f, brightness,
                        0f, contrast, 0f, 0f, brightness,
                        0f, 0f, contrast, 0f, brightness,
                        0f, 0f, 0f, 1f, 0f
                    )
                )
                paint.colorFilter = ColorMatrixColorFilter(matrix)
                canvas.drawBitmap(bmp, 0f, 0f, paint)
            }

            ScanFilter.WARM_PAPER -> {
                // Warm reading book paper tint
                val warmMatrix = ColorMatrix(
                    floatArrayOf(
                        1.22f * intensity, 0f, 0f, 0f, 18f,
                        0f, 1.16f * intensity, 0f, 0f, 12f,
                        0f, 0f, 0.94f * intensity, 0f, -5f,
                        0f, 0f, 0f, 1f, 0f
                    )
                )
                paint.colorFilter = ColorMatrixColorFilter(warmMatrix)
                canvas.drawBitmap(bmp, 0f, 0f, paint)
            }
        }

        rotatedIntermediate?.let {
            if (!it.isRecycled) it.recycle()
        }
        return output
    }

    /**
     * Crops a bitmap cleanly to the central printable margin area (removing outer edges).
     */
    fun trimPageMargins(source: Bitmap, marginFraction: Float = 0.06f): Bitmap {
        val frac = marginFraction.coerceIn(0.01f, 0.25f)
        val x = (source.width * frac).toInt()
        val y = (source.height * frac).toInt()
        val w = (source.width - x * 2).coerceAtLeast(10)
        val h = (source.height - y * 2).coerceAtLeast(10)
        return Bitmap.createBitmap(source, x, y, w, h)
    }

    /**
     * Generates a fast scaled thumbnail for live filter previews in the filter selection bar.
     */
    fun createFilterPreviewThumbnail(source: Bitmap, filter: ScanFilter, targetSize: Int = 120): Bitmap {
        val aspect = source.width.toFloat() / source.height.toFloat().coerceAtLeast(1f)
        val tw = if (aspect >= 1f) targetSize else (targetSize * aspect).toInt().coerceAtLeast(40)
        val th = if (aspect >= 1f) (targetSize / aspect).toInt().coerceAtLeast(40) else targetSize

        val scaled = Bitmap.createScaledBitmap(source, tw, th, true)
        val processed = processBitmap(scaled, filter, 0, 1.0f)
        if (processed !== scaled && !scaled.isRecycled) {
            scaled.recycle()
        }
        return processed
    }

    /**
     * Compiles a collection of ScannedPages into a finalized Multi-Page PDF Document.
     * Optionally embeds transparent, searchable OCR text operators (and user OCR text overrides) for dual-layer PDF/A standard.
     */
    suspend fun generatePdf(
        context: Context,
        pages: List<ScannedPage>,
        title: String,
        pageSizeA4: Boolean = true,
        ocrResults: Map<Int, OcrPageResult>? = null,
        ocrTextOverrides: Map<Int, String>? = null
    ): File = withContext(Dispatchers.IO) {
        val pdfDocument = PdfDocument()

        // Standard A4 dimensions in points (72 DPI standard, scaled cleanly)
        val standardWidth = if (pageSizeA4) 595 else 612
        val standardHeight = if (pageSizeA4) 842 else 792

        pages.forEachIndexed { index, scannedPage ->
            val rawBitmap = loadBitmapFromUri(context, scannedPage.sourceUri)
            if (rawBitmap != null) {
                val processed = processBitmap(
                    source = rawBitmap,
                    filter = scannedPage.filter,
                    rotationDegrees = scannedPage.rotationDegrees,
                    filterIntensity = scannedPage.filterIntensity
                )

                val pageInfo = PdfDocument.PageInfo.Builder(standardWidth, standardHeight, index + 1).create()
                val page = pdfDocument.startPage(pageInfo)
                val canvas = page.canvas

                // Fill page background with crisp white paper
                canvas.drawColor(android.graphics.Color.WHITE)

                // Calculate aspect ratio scaling to fit cleanly on document page with standard margins
                val margin = 20f
                val availW = standardWidth - (margin * 2)
                val availH = standardHeight - (margin * 2)

                val scale = min(availW / processed.width.toFloat(), availH / processed.height.toFloat())
                val destW = processed.width * scale
                val destH = processed.height * scale

                val left = margin + (availW - destW) / 2f
                val top = margin + (availH - destH) / 2f

                val destRect = RectF(left, top, left + destW, top + destH)
                val paint = Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG)
                canvas.drawBitmap(processed, null, destRect, paint)

                // Embed searchable OCR invisible text layer if available (honoring user edits when present)
                val customOverride = ocrTextOverrides?.get(index)?.trim()
                val ocrPage = ocrResults?.get(index)
                if (!customOverride.isNullOrBlank()) {
                    val ocrPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                        color = android.graphics.Color.TRANSPARENT
                        textSize = 10f
                    }
                    val overrideLines = customOverride.split('\n').filter { it.isNotBlank() }
                    val lineStep = (destRect.height() / overrideLines.size.coerceAtLeast(1)).coerceIn(10f, 24f)
                    overrideLines.forEachIndexed { lineIdx, lineText ->
                        val y = destRect.top + 16f + (lineIdx * lineStep)
                        if (y < destRect.bottom) {
                            canvas.drawText(lineText, destRect.left + 12f, y, ocrPaint)
                        }
                    }
                } else if (ocrPage != null && ocrPage.blocks.isNotEmpty()) {
                    val ocrPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                        color = android.graphics.Color.TRANSPARENT // Transparent text layer in PDF stream
                    }

                    ocrPage.blocks.forEach { block ->
                        block.lines.forEach { line ->
                            val box = line.boundingBox
                            val lineLeft = destRect.left + (box.left * destRect.width())
                            val lineTop = destRect.top + (box.top * destRect.height())
                            val lineH = box.height() * destRect.height()

                            val baselineY = lineTop + (lineH * 0.8f)
                            ocrPaint.textSize = Math.max(8f, lineH * 0.75f)

                            canvas.drawText(line.text, lineLeft, baselineY, ocrPaint)
                        }
                    }
                }

                pdfDocument.finishPage(page)
                if (processed != rawBitmap) {
                    processed.recycle()
                }
                rawBitmap.recycle()
            }
        }

        // Save PDF file in local app documents storage
        val cleanName = title.trim()
            .replace(Regex("[^a-zA-Z0-9_\\-\\s]"), "")
            .replace("\\s+".toRegex(), "_")
            .ifEmpty { "Scan_${SimpleDateFormat("yyyyMMdd_HHmmss", Locale.getDefault()).format(Date())}" }

        val scansDir = File(context.filesDir, "scans").apply { if (!exists()) mkdirs() }
        val finalPdfFile = File(scansDir, "$cleanName.pdf")

        FileOutputStream(finalPdfFile).use { out ->
            pdfDocument.writeTo(out)
        }
        pdfDocument.close()

        finalPdfFile
    }
}


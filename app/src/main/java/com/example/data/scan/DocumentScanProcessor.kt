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
 * Filter modes inspired by Adobe Scan
 */
enum class ScanFilter(val label: String, val description: String) {
    MAGIC_COLOR("Magic Color", "Whitens paper & sharpens text"),
    CLEAN_TEXT("Clean B&W", "High-contrast photocopier black & white"),
    GRAYSCALE("Grayscale", "Smooth monochrome document"),
    ORIGINAL("Original", "Natural camera capture"),
    WARM_PAPER("Warm Paper", "Eye-comfort sepia tint")
}

data class ScannedPage(
    val id: String = UUID.randomUUID().toString(),
    val sourceUri: Uri,
    var rotationDegrees: Int = 0,
    var filter: ScanFilter = ScanFilter.MAGIC_COLOR
)

/**
 * High-performance Document Scan & Image Processing Engine
 * 100% on-device & local processing.
 */
object DocumentScanProcessor {

    /**
     * Loads a Bitmap from a Uri with automatic downsampling to protect against OOMs.
     */
    suspend fun loadBitmapFromUri(context: Context, uri: Uri, maxDimension: Int = 2048): Bitmap? = withContext(Dispatchers.IO) {
        try {
            val boundsOptions = BitmapFactory.Options().apply { inJustDecodeBounds = true }
            context.contentResolver.openInputStream(uri)?.use { stream ->
                BitmapFactory.decodeStream(stream, null, boundsOptions)
            }

            var inSampleSize = 1
            val maxSide = maxOf(boundsOptions.outWidth, boundsOptions.outHeight)
            while (maxSide / (inSampleSize * 2) >= maxDimension) {
                inSampleSize *= 2
            }

            val decodeOptions = BitmapFactory.Options().apply {
                this.inSampleSize = inSampleSize
                inPreferredConfig = Bitmap.Config.ARGB_8888
            }

            context.contentResolver.openInputStream(uri)?.use { stream ->
                BitmapFactory.decodeStream(stream, null, decodeOptions)
            }
        } catch (e: Exception) {
            e.printStackTrace()
            null
        }
    }

    /**
     * Applies Adobe Scan-style document enhancement filters and rotation to a Bitmap.
     */
    fun processBitmap(source: Bitmap, filter: ScanFilter, rotationDegrees: Int): Bitmap {
        var bmp = source

        // 1. Rotation if needed
        if (rotationDegrees % 360 != 0) {
            val matrix = Matrix().apply { postRotate(rotationDegrees.toFloat()) }
            bmp = Bitmap.createBitmap(bmp, 0, 0, bmp.width, bmp.height, matrix, true)
        }

        // 2. Filter application
        val width = bmp.width
        val height = bmp.height
        val output = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(output)
        val paint = Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG)

        when (filter) {
            ScanFilter.ORIGINAL -> {
                canvas.drawBitmap(bmp, 0f, 0f, paint)
            }

            ScanFilter.MAGIC_COLOR -> {
                // Adobe Scan "Magic Color" formula:
                // Boosts contrast to whiten paper backgrounds, slightly increases brightness,
                // and enhances contrast for ink/lettering while preserving colored stamps and logos.
                val contrast = 1.35f
                val brightness = 20f
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
                // Adobe Scan "B&W Document" filter:
                // Heavy grayscale conversion with steep high-contrast threshold curve
                // to turn paper backgrounds pure white (#FFFFFF) and ink crisp black (#000000).
                val grayMatrix = ColorMatrix().apply { setSaturation(0f) }
                val contrast = 2.2f
                val brightness = 35f
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
                val contrast = 1.15f
                val brightness = 10f
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

            ScanFilter.WARM_PAPER -> {
                // Warm reading book paper tint
                val warmMatrix = ColorMatrix(
                    floatArrayOf(
                        1.2f, 0f, 0f, 0f, 15f,
                        0f, 1.15f, 0f, 0f, 10f,
                        0f, 0f, 0.95f, 0f, -5f,
                        0f, 0f, 0f, 1f, 0f
                    )
                )
                paint.colorFilter = ColorMatrixColorFilter(warmMatrix)
                canvas.drawBitmap(bmp, 0f, 0f, paint)
            }
        }

        return output
    }

    /**
     * Compiles a collection of ScannedPages into a finalized Multi-Page PDF Document.
     */
    suspend fun generatePdf(
        context: Context,
        pages: List<ScannedPage>,
        title: String,
        pageSizeA4: Boolean = true
    ): File = withContext(Dispatchers.IO) {
        val pdfDocument = PdfDocument()

        // Standard A4 dimensions in points (72 DPI standard, scaled cleanly)
        val standardWidth = if (pageSizeA4) 595 else 612
        val standardHeight = if (pageSizeA4) 842 else 792

        pages.forEachIndexed { index, scannedPage ->
            val rawBitmap = loadBitmapFromUri(context, scannedPage.sourceUri)
            if (rawBitmap != null) {
                val processed = processBitmap(rawBitmap, scannedPage.filter, scannedPage.rotationDegrees)

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

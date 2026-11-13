package com.example.data.pdf

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.pdf.PdfDocument
import android.graphics.pdf.PdfRenderer
import android.os.ParcelFileDescriptor
import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.FileOutputStream
import java.util.Locale

object PdfCompressorEngine {

    private const val TAG = "PdfCompressorEngine"

    enum class CompressionPreset(
        val label: String,
        val subtitle: String,
        val jpegQuality: Int,
        val scaleFactor: Float,
        val estimatedReduction: String
    ) {
        HIGH_QUALITY("High Quality", "Minor compression (~20-35% size reduction)", 85, 1.0f, "-30%"),
        RECOMMENDED("Recommended", "Balanced quality & file size (~45-70% reduction)", 70, 0.85f, "-60%"),
        EXTREME("Extreme Shrink", "Maximum compression for email & web (~70-90% reduction)", 50, 0.7f, "-80%"),
        CUSTOM("Custom Profile", "User-specified quality & scale factors", 75, 0.9f, "Custom")
    }

    data class CompressionOptions(
        val preset: CompressionPreset = CompressionPreset.RECOMMENDED,
        val customJpegQuality: Int = 70,
        val customScaleFactor: Float = 0.85f,
        val stripMetadata: Boolean = true,
        val stripAnnotations: Boolean = false,
        val optimizeGraphics: Boolean = true
    )

    data class CompressionResult(
        val isSuccess: Boolean,
        val outputFile: File?,
        val originalSizeBytes: Long,
        val compressedSizeBytes: Long,
        val savedSizeBytes: Long,
        val reductionPercent: Float,
        val pageCount: Int,
        val timeTakenMs: Long,
        val errorMessage: String? = null
    )

    /**
     * Compress PDF document by downsampling image content and stripping redundant metadata & stream comments.
     */
    suspend fun compressPdf(
        context: Context,
        inputFile: File,
        options: CompressionOptions = CompressionOptions(),
        onProgress: (currentPage: Int, totalPages: Int) -> Unit = { _, _ -> }
    ): CompressionResult = withContext(Dispatchers.IO) {
        val startTime = System.currentTimeMillis()
        val originalSize = inputFile.length()

        if (!inputFile.exists() || originalSize == 0L) {
            return@withContext CompressionResult(
                isSuccess = false,
                outputFile = null,
                originalSizeBytes = 0,
                compressedSizeBytes = 0,
                savedSizeBytes = 0,
                reductionPercent = 0f,
                pageCount = 0,
                timeTakenMs = 0,
                errorMessage = "Source PDF file does not exist or is empty."
            )
        }

        val quality = if (options.preset == CompressionPreset.CUSTOM) {
            options.customJpegQuality.coerceIn(10, 100)
        } else {
            options.preset.jpegQuality
        }

        val scale = if (options.preset == CompressionPreset.CUSTOM) {
            options.customScaleFactor.coerceIn(0.3f, 1.5f)
        } else {
            options.preset.scaleFactor
        }

        val outputDir = File(context.cacheDir, "compressed_pdfs").apply { mkdirs() }
        val outputFile = File(outputDir, "${inputFile.nameWithoutExtension}_compressed_${System.currentTimeMillis()}.pdf")

        var pfd: ParcelFileDescriptor? = null
        var renderer: PdfRenderer? = null
        var pdfDocument: PdfDocument? = null

        try {
            pfd = ParcelFileDescriptor.open(inputFile, ParcelFileDescriptor.MODE_READ_ONLY)
            renderer = PdfRenderer(pfd)
            val totalPages = renderer.pageCount

            pdfDocument = PdfDocument()

            for (i in 0 until totalPages) {
                onProgress(i + 1, totalPages)

                val page = renderer.openPage(i)
                val origW: Int
                val origH: Int
                val bitmap: Bitmap
                try {
                    origW = page.width
                    origH = page.height

                    // Target render dimensions with quality scaling factor
                    val targetW = (origW * scale).toInt().coerceAtLeast(100)
                    val targetH = (origH * scale).toInt().coerceAtLeast(100)

                    bitmap = Bitmap.createBitmap(targetW, targetH, Bitmap.Config.ARGB_8888)
                    val canvas = Canvas(bitmap)
                    canvas.drawColor(Color.WHITE)

                    // Render page content
                    page.render(bitmap, null, null, PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY)
                } finally {
                    try { page.close() } catch (ignored: Exception) {}
                }

                // Compress bitmap to JPEG byte stream
                val jpegStream = ByteArrayOutputStream()
                bitmap.compress(Bitmap.CompressFormat.JPEG, quality, jpegStream)
                val compressedBytes = jpegStream.toByteArray()
                jpegStream.close()

                val decodedBitmap = android.graphics.BitmapFactory.decodeByteArray(compressedBytes, 0, compressedBytes.size)
                bitmap.recycle()

                // Write page back to standard PDF document (PostScript / PDF 1.4 spec)
                val pageInfo = PdfDocument.PageInfo.Builder(origW, origH, i + 1).create()
                val pdfPage = pdfDocument.startPage(pageInfo)
                val pageCanvas = pdfPage.canvas

                if (decodedBitmap != null) {
                    val destRect = android.graphics.Rect(0, 0, origW, origH)
                    val paint = android.graphics.Paint().apply {
                        isFilterBitmap = true
                        isAntiAlias = true
                    }
                    pageCanvas.drawBitmap(decodedBitmap, null, destRect, paint)
                    decodedBitmap.recycle()
                } else {
                    pageCanvas.drawColor(Color.WHITE)
                }

                pdfDocument.finishPage(pdfPage)
            }

            // Write PDF document stream
            FileOutputStream(outputFile).use { fos ->
                pdfDocument.writeTo(fos)
            }

            // Note: Android's PdfDocument writes a clean re-encoded PDF stream without carrying over
            // source XMP/Author metadata dictionaries, so no post-write binary mutation is needed
            // (mutating byte lengths post-write would invalidate the PDF xref offset table).

            val compressedSize = outputFile.length()
            val savedBytes = (originalSize - compressedSize).coerceAtLeast(0L)
            val reductionPercent = if (originalSize > 0) {
                ((originalSize - compressedSize).toFloat() / originalSize.toFloat()) * 100f
            } else 0f

            val timeTaken = System.currentTimeMillis() - startTime

            Log.d(TAG, "Compressed ${inputFile.name}: $originalSize -> $compressedSize bytes (${String.format(Locale.US, "%.1f", reductionPercent)}% saved in ${timeTaken}ms)")

            CompressionResult(
                isSuccess = true,
                outputFile = outputFile,
                originalSizeBytes = originalSize,
                compressedSizeBytes = compressedSize,
                savedSizeBytes = savedBytes,
                reductionPercent = reductionPercent,
                pageCount = totalPages,
                timeTakenMs = timeTaken
            )
        } catch (e: Exception) {
            Log.e(TAG, "Failed to compress PDF: ${inputFile.name}", e)
            CompressionResult(
                isSuccess = false,
                outputFile = null,
                originalSizeBytes = originalSize,
                compressedSizeBytes = 0,
                savedSizeBytes = 0,
                reductionPercent = 0f,
                pageCount = 0,
                timeTakenMs = System.currentTimeMillis() - startTime,
                errorMessage = e.localizedMessage ?: "PDF compression failed"
            )
        } finally {
            try { renderer?.close() } catch (ignored: Exception) {}
            try { pfd?.close() } catch (ignored: Exception) {}
            try { pdfDocument?.close() } catch (ignored: Exception) {}
        }
    }
}

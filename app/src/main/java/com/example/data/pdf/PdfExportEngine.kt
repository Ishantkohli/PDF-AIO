package com.example.data.pdf

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Matrix
import android.graphics.Paint
import android.graphics.Rect
import android.graphics.RectF
import android.graphics.pdf.PdfDocument
import android.graphics.pdf.PdfRenderer
import android.media.ExifInterface
import android.net.Uri
import android.os.ParcelFileDescriptor
import com.example.data.local.AnnotationEntity
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import java.io.File
import java.io.FileOutputStream

import android.provider.OpenableColumns
import java.util.UUID

data class PdfMergeItem(
    val id: String = UUID.randomUUID().toString(),
    val uri: Uri,
    val title: String,
    val pageCount: Int,
    val fileSizeBytes: Long,
    val isFromLibrary: Boolean = false
)

object PdfExportEngine {

    /**
     * Extracts readable file display name from Uri (SAF or File Provider)
     */
    fun getFileNameFromUri(context: Context, uri: Uri): String {
        var result: String? = null
        if (uri.scheme == "content") {
            try {
                context.contentResolver.query(uri, null, null, null, null)?.use { cursor ->
                    if (cursor.moveToFirst()) {
                        val index = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
                        if (index != -1) {
                            result = cursor.getString(index)
                        }
                    }
                }
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
        if (result == null) {
            result = uri.path
            val cut = result?.lastIndexOf('/') ?: -1
            if (cut != -1) {
                result = result?.substring(cut + 1)
            }
        }
        return (result ?: "Document_${System.currentTimeMillis()}.pdf").removeSuffix(".pdf").removeSuffix(".PDF") + ".pdf"
    }

    /**
     * Inspects page count of a PDF file specified by Uri
     */
    fun getPdfPageCountFromUri(context: Context, uri: Uri): Int {
        return try {
            context.contentResolver.openFileDescriptor(uri, "r")?.use { pfd ->
                PdfRenderer(pfd).use { renderer ->
                    renderer.pageCount
                }
            } ?: 1
        } catch (e: Exception) {
            1
        }
    }

    /**
     * Determines byte length of PDF file specified by Uri
     */
    fun getFileSizeBytesFromUri(context: Context, uri: Uri): Long {
        return try {
            context.contentResolver.openFileDescriptor(uri, "r")?.use { pfd ->
                pfd.statSize
            } ?: 0L
        } catch (e: Exception) {
            0L
        }
    }

    suspend fun mergePdfs(
        pdfFiles: List<File>,
        outputFile: File
    ): Boolean = withContext(Dispatchers.IO) {
        val newDoc = PdfDocument()
        var totalPages = 0

        try {
            for (file in pdfFiles) {
                var pfd: ParcelFileDescriptor? = null
                var renderer: PdfRenderer? = null
                try {
                    pfd = ParcelFileDescriptor.open(file, ParcelFileDescriptor.MODE_READ_ONLY)
                    renderer = PdfRenderer(pfd)
                    for (i in 0 until renderer.pageCount) {
                        val page = renderer.openPage(i)
                        val w = page.width
                        val h = page.height
                        val scale = if (maxOf(w, h) > 1600) 1 else 2
                        val bitmap = Bitmap.createBitmap(w * scale, h * scale, Bitmap.Config.ARGB_8888)
                        bitmap.eraseColor(Color.WHITE)
                        page.render(bitmap, null, null, PdfRenderer.Page.RENDER_MODE_FOR_PRINT)
                        page.close()

                        totalPages++
                        val pageInfo = PdfDocument.PageInfo.Builder(w, h, totalPages).create()
                        val newPage = newDoc.startPage(pageInfo)
                        newPage.canvas.drawColor(Color.WHITE)
                        newPage.canvas.drawBitmap(bitmap, null, Rect(0, 0, w, h), null)
                        bitmap.recycle()
                        newDoc.finishPage(newPage)
                    }
                } finally {
                    try { renderer?.close() } catch (ignored: Exception) {}
                    try { pfd?.close() } catch (ignored: Exception) {}
                }
            }

            FileOutputStream(outputFile).use { fos -> newDoc.writeTo(fos) }
            true
        } catch (e: Exception) {
            e.printStackTrace()
            false
        } finally {
            try { newDoc.close() } catch (ignored: Exception) {}
        }
    }

    /**
     * Advanced Multi-File PDF Merger supporting both internal app storage and SAF Uris.
     * Combines multiple PDF files in sequence into a single unified output PDF document.
     */
    suspend fun mergePdfsFromUris(
        context: Context,
        pdfItems: List<PdfMergeItem>,
        outputFile: File,
        insertSeparators: Boolean = false,
        onProgress: (Int, Int) -> Unit = { _, _ -> }
    ): Boolean = withContext(Dispatchers.IO) {
        val newDoc = PdfDocument()
        var totalMergedPages = 0
        val totalFiles = pdfItems.size

        try {
            pdfItems.forEachIndexed { index, item ->
                var pfd: ParcelFileDescriptor? = null
                var renderer: PdfRenderer? = null
                try {
                    pfd = context.contentResolver.openFileDescriptor(item.uri, "r")
                    if (pfd != null) {
                        renderer = PdfRenderer(pfd)
                        val pageCount = renderer.pageCount

                        for (i in 0 until pageCount) {
                            val page = renderer.openPage(i)
                            val w = page.width
                            val h = page.height

                            val scale = if (maxOf(w, h) > 1600) 1 else 2
                            val bitmap = Bitmap.createBitmap(w * scale, h * scale, Bitmap.Config.ARGB_8888)
                            bitmap.eraseColor(Color.WHITE)
                            page.render(bitmap, null, null, PdfRenderer.Page.RENDER_MODE_FOR_PRINT)
                            page.close()

                            totalMergedPages++
                            val pageInfo = PdfDocument.PageInfo.Builder(w, h, totalMergedPages).create()
                            val newPage = newDoc.startPage(pageInfo)
                            newPage.canvas.drawColor(Color.WHITE)
                            newPage.canvas.drawBitmap(bitmap, null, Rect(0, 0, w, h), null)
                            bitmap.recycle()
                            newDoc.finishPage(newPage)
                        }

                        if (insertSeparators && index < totalFiles - 1) {
                            totalMergedPages++
                            val sepWidth = 595
                            val sepHeight = 842
                            val sepInfo = PdfDocument.PageInfo.Builder(sepWidth, sepHeight, totalMergedPages).create()
                            val sepPage = newDoc.startPage(sepInfo)
                            val canvas = sepPage.canvas
                            canvas.drawColor(Color.WHITE)

                            val borderPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                                color = Color.rgb(226, 232, 240)
                                style = Paint.Style.STROKE
                                strokeWidth = 3f
                            }
                            canvas.drawRect(40f, 40f, sepWidth - 40f, sepHeight - 40f, borderPaint)

                            val textPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                                color = Color.rgb(71, 85, 105)
                                textSize = 18f
                                isFakeBoldText = true
                                textAlign = Paint.Align.CENTER
                            }
                            canvas.drawText("SECTION BREAK", sepWidth / 2f, sepHeight / 2f - 20f, textPaint)

                            val subPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                                color = Color.rgb(100, 116, 139)
                                textSize = 13f
                                textAlign = Paint.Align.CENTER
                            }
                            val nextName = pdfItems.getOrNull(index + 1)?.title ?: "Next Document"
                            canvas.drawText("End of ${item.title} • Next: $nextName", sepWidth / 2f, sepHeight / 2f + 15f, subPaint)

                            newDoc.finishPage(sepPage)
                        }
                    }
                } catch (e: Exception) {
                    e.printStackTrace()
                } finally {
                    try { renderer?.close() } catch (ignored: Exception) {}
                    try { pfd?.close() } catch (ignored: Exception) {}
                }
                onProgress(index + 1, totalFiles)
            }

            FileOutputStream(outputFile).use { fos -> newDoc.writeTo(fos) }
            true
        } catch (e: Exception) {
            e.printStackTrace()
            false
        } finally {
            try { newDoc.close() } catch (ignored: Exception) {}
        }
    }

    suspend fun exportAnnotatedPdf(
        sourceFile: File,
        annotations: List<AnnotationEntity>,
        outputFile: File
    ): Boolean = withContext(Dispatchers.IO) {
        var pfd: ParcelFileDescriptor? = null
        var renderer: PdfRenderer? = null
        val newDoc = PdfDocument()

        try {
            pfd = ParcelFileDescriptor.open(sourceFile, ParcelFileDescriptor.MODE_READ_ONLY)
            renderer = PdfRenderer(pfd)
            val pageCount = renderer.pageCount

            for (pageIdx in 0 until pageCount) {
                val page = renderer.openPage(pageIdx)
                val width = page.width
                val height = page.height

                // Render background page cleanly with white backing and OOM-safe scale
                val scale = if (maxOf(width, height) > 1600) 1 else 2
                val pageBitmap = Bitmap.createBitmap(width * scale, height * scale, Bitmap.Config.ARGB_8888)
                pageBitmap.eraseColor(Color.WHITE)
                page.render(pageBitmap, null, null, PdfRenderer.Page.RENDER_MODE_FOR_PRINT)
                page.close()

                val pageInfo = PdfDocument.PageInfo.Builder(width, height, pageIdx + 1).create()
                val docPage = newDoc.startPage(pageInfo)
                val canvas = docPage.canvas
                canvas.drawColor(Color.WHITE)

                // Draw base page
                canvas.drawBitmap(pageBitmap, null, Rect(0, 0, width, height), null)
                pageBitmap.recycle()

                // Draw annotations for this page
                val pageAnnotations = annotations.filter { it.pageIndex == pageIdx }
                drawAnnotationsOnCanvas(canvas, pageAnnotations, width.toFloat(), height.toFloat())

                newDoc.finishPage(docPage)
            }

            FileOutputStream(outputFile).use { fos ->
                newDoc.writeTo(fos)
            }
            true
        } catch (e: Exception) {
            e.printStackTrace()
            false
        } finally {
            try { newDoc.close() } catch (ignored: Exception) {}
            try { renderer?.close() } catch (ignored: Exception) {}
            try { pfd?.close() } catch (ignored: Exception) {}
        }
    }

    /**
     * Decodes a content URI bitmap safely with automatic EXIF orientation correction
     * and inSampleSize downsampling so large camera photos do not cause OutOfMemoryError.
     */
    fun decodeSampledRotatedBitmap(
        context: Context,
        uri: Uri,
        maxDimension: Int = 2048
    ): Bitmap? {
        return try {
            val orientation = context.contentResolver.openInputStream(uri)?.use { exifStream ->
                try {
                    ExifInterface(exifStream).getAttributeInt(
                        ExifInterface.TAG_ORIENTATION,
                        ExifInterface.ORIENTATION_NORMAL
                    )
                } catch (_: Exception) {
                    ExifInterface.ORIENTATION_NORMAL
                }
            } ?: ExifInterface.ORIENTATION_NORMAL

            val boundsOpts = BitmapFactory.Options().apply { inJustDecodeBounds = true }
            context.contentResolver.openInputStream(uri)?.use { stream ->
                BitmapFactory.decodeStream(stream, null, boundsOpts)
            }

            var inSampleSize = 1
            val rawW = boundsOpts.outWidth
            val rawH = boundsOpts.outHeight
            if (rawW > maxDimension || rawH > maxDimension) {
                while ((rawW / inSampleSize) > maxDimension || (rawH / inSampleSize) > maxDimension) {
                    inSampleSize *= 2
                }
            }

            val decodeOpts = BitmapFactory.Options().apply {
                this.inSampleSize = inSampleSize
                inPreferredConfig = Bitmap.Config.ARGB_8888
            }
            val decoded = context.contentResolver.openInputStream(uri)?.use { stream ->
                BitmapFactory.decodeStream(stream, null, decodeOpts)
            } ?: return null

            val rotationAngle = when (orientation) {
                ExifInterface.ORIENTATION_ROTATE_90 -> 90f
                ExifInterface.ORIENTATION_ROTATE_180 -> 180f
                ExifInterface.ORIENTATION_ROTATE_270 -> 270f
                else -> 0f
            }
            if (rotationAngle == 0f) {
                decoded
            } else {
                val matrix = Matrix().apply { postRotate(rotationAngle) }
                val rotated = Bitmap.createBitmap(decoded, 0, 0, decoded.width, decoded.height, matrix, true)
                if (rotated !== decoded) decoded.recycle()
                rotated
            }
        } catch (e: Exception) {
            e.printStackTrace()
            null
        }
    }

    suspend fun convertImagesToPdf(
        context: Context,
        imageUris: List<Uri>,
        outputFile: File
    ): Boolean = withContext(Dispatchers.IO) {
        val newDoc = PdfDocument()
        val pageWidth = 595 // A4 standard
        val pageHeight = 842

        try {
            var writtenPage = 0
            for (uri in imageUris) {
                val bitmap = decodeSampledRotatedBitmap(context, uri, 2048) ?: continue
                writtenPage++
                val pageInfo = PdfDocument.PageInfo.Builder(pageWidth, pageHeight, writtenPage).create()
                val page = newDoc.startPage(pageInfo)
                val canvas = page.canvas
                canvas.drawColor(Color.WHITE)

                // Scale image maintaining aspect ratio
                val scale = Math.min(
                    (pageWidth - 60f) / bitmap.width.coerceAtLeast(1),
                    (pageHeight - 60f) / bitmap.height.coerceAtLeast(1)
                )
                val destW = bitmap.width * scale
                val destH = bitmap.height * scale
                val left = (pageWidth - destW) / 2f
                val top = (pageHeight - destH) / 2f

                canvas.drawBitmap(bitmap, null, RectF(left, top, left + destW, top + destH), null)
                bitmap.recycle()
                newDoc.finishPage(page)
            }

            if (writtenPage == 0) return@withContext false
            FileOutputStream(outputFile).use { fos -> newDoc.writeTo(fos) }
            true
        } catch (e: Exception) {
            e.printStackTrace()
            false
        } finally {
            try { newDoc.close() } catch (ignored: Exception) {}
        }
    }

    suspend fun organizePages(
        sourceFile: File,
        includedPageIndices: List<Int>,
        pageRotations: Map<Int, Int>, // pageIndex to degrees (0, 90, 180, 270)
        outputFile: File
    ): Boolean = organizePagesWithRotations(
        sourceFile = sourceFile,
        orderedPages = includedPageIndices.map { idx -> idx to (pageRotations[idx] ?: 0) },
        outputFile = outputFile
    )

    suspend fun organizePagesWithRotations(
        sourceFile: File,
        orderedPages: List<Pair<Int, Int>>, // Pairs of (originalPageIndex, rotationDegrees)
        outputFile: File
    ): Boolean = withContext(Dispatchers.IO) {
        var pfd: ParcelFileDescriptor? = null
        var renderer: PdfRenderer? = null
        val newDoc = PdfDocument()

        try {
            pfd = ParcelFileDescriptor.open(sourceFile, ParcelFileDescriptor.MODE_READ_ONLY)
            renderer = PdfRenderer(pfd)

            var newPageNum = 1
            for ((pageIdx, rawRotation) in orderedPages) {
                if (pageIdx == -1 || pageIdx == -2) {
                    val targetW = 595
                    val targetH = 842
                    val pageInfo = PdfDocument.PageInfo.Builder(targetW, targetH, newPageNum++).create()
                    val newPage = newDoc.startPage(pageInfo)
                    val canvas = newPage.canvas
                    canvas.drawColor(Color.WHITE)
                    if (pageIdx == -2) {
                        // Lined notes page
                        val headerPaint = Paint().apply {
                            color = Color.rgb(220, 38, 38)
                            textSize = 14f
                            isFakeBoldText = true
                            isAntiAlias = true
                        }
                        val linePaint = Paint().apply {
                            color = Color.rgb(226, 232, 240)
                            strokeWidth = 1f
                            isAntiAlias = true
                        }
                        val marginPaint = Paint().apply {
                            color = Color.rgb(248, 113, 113)
                            strokeWidth = 1.5f
                            isAntiAlias = true
                        }
                        canvas.drawText("NOTES / MEMORANDUM", 50f, 50f, headerPaint)
                        canvas.drawLine(50f, 65f, targetW - 50f, 65f, headerPaint)
                        canvas.drawLine(70f, 80f, 70f, targetH - 40f, marginPaint)
                        for (y in 90..800 step 26) {
                            canvas.drawLine(40f, y.toFloat(), targetW - 40f, y.toFloat(), linePaint)
                        }
                    }
                    newDoc.finishPage(newPage)
                    continue
                }
                if (pageIdx < 0 || pageIdx >= renderer.pageCount) continue
                val page = renderer.openPage(pageIdx)
                val w = page.width
                val h = page.height
                val rotation = ((rawRotation % 360) + 360) % 360

                val scale = if (maxOf(w, h) > 1600) 1 else 2
                val bitmap = Bitmap.createBitmap(w * scale, h * scale, Bitmap.Config.ARGB_8888)
                bitmap.eraseColor(Color.WHITE)
                page.render(bitmap, null, null, PdfRenderer.Page.RENDER_MODE_FOR_PRINT)
                page.close()

                val targetW = if (rotation == 90 || rotation == 270) h else w
                val targetH = if (rotation == 90 || rotation == 270) w else h

                val pageInfo = PdfDocument.PageInfo.Builder(targetW, targetH, newPageNum++).create()
                val newPage = newDoc.startPage(pageInfo)
                val canvas = newPage.canvas
                canvas.drawColor(Color.WHITE)

                if (rotation != 0) {
                    canvas.save()
                    canvas.translate(targetW / 2f, targetH / 2f)
                    canvas.rotate(rotation.toFloat())
                    canvas.translate(-w / 2f, -h / 2f)
                    canvas.drawBitmap(bitmap, null, Rect(0, 0, w, h), null)
                    canvas.restore()
                } else {
                    canvas.drawBitmap(bitmap, null, Rect(0, 0, targetW, targetH), null)
                }

                bitmap.recycle()
                newDoc.finishPage(newPage)
            }

            FileOutputStream(outputFile).use { fos -> newDoc.writeTo(fos) }
            true
        } catch (e: Exception) {
            e.printStackTrace()
            false
        } finally {
            try { newDoc.close() } catch (ignored: Exception) {}
            try { renderer?.close() } catch (ignored: Exception) {}
            try { pfd?.close() } catch (ignored: Exception) {}
        }
    }

    data class PageSlotSpec(
        val originalIndex: Int, // -1 = Blank, -2 = Notes, -3 = Replaced with image/PDF
        val rotationDegrees: Int = 0,
        val replacementImageUri: String? = null,
        val replacementPdfUri: String? = null,
        val replacementPdfPageIndex: Int = 0
    )

    data class DuplicatePageMatch(
        val firstPageIndex: Int,
        val secondPageIndex: Int,
        val similarityPercent: Int,
        val reason: String
    )

    suspend fun organizePagesAdvanced(
        context: Context,
        sourceFile: File,
        slots: List<PageSlotSpec>,
        outputFile: File
    ): Boolean = withContext(Dispatchers.IO) {
        if (slots.isEmpty()) return@withContext false
        var pfd: ParcelFileDescriptor? = null
        var renderer: PdfRenderer? = null
        val newDoc = PdfDocument()

        try {
            pfd = ParcelFileDescriptor.open(sourceFile, ParcelFileDescriptor.MODE_READ_ONLY)
            renderer = PdfRenderer(pfd)

            var newPageNum = 1
            for (slot in slots) {
                val rotation = ((slot.rotationDegrees % 360) + 360) % 360

                // Case 1: Inserted Replacement Image
                if (!slot.replacementImageUri.isNullOrBlank()) {
                    val imgUri = Uri.parse(slot.replacementImageUri)
                    val rawBmp = decodeSampledRotatedBitmap(context, imgUri, 1800)
                    if (rawBmp != null) {
                        val baseW = 595
                        val baseH = 842
                        val targetW = if (rotation == 90 || rotation == 270) baseH else baseW
                        val targetH = if (rotation == 90 || rotation == 270) baseW else baseH
                        val pageInfo = PdfDocument.PageInfo.Builder(targetW, targetH, newPageNum++).create()
                        val newPage = newDoc.startPage(pageInfo)
                        val canvas = newPage.canvas
                        canvas.drawColor(Color.WHITE)

                        val scale = minOf((targetW - 24f) / rawBmp.width.coerceAtLeast(1), (targetH - 24f) / rawBmp.height.coerceAtLeast(1))
                        val drawW = rawBmp.width * scale
                        val drawH = rawBmp.height * scale
                        val left = (targetW - drawW) / 2f
                        val top = (targetH - drawH) / 2f
                        canvas.save()
                        if (rotation != 0) {
                            canvas.rotate(rotation.toFloat(), targetW / 2f, targetH / 2f)
                        }
                        canvas.drawBitmap(rawBmp, null, RectF(left, top, left + drawW, top + drawH), null)
                        canvas.restore()
                        rawBmp.recycle()
                        newDoc.finishPage(newPage)
                        continue
                    }
                }

                // Case 2: Inserted Replacement Page from External PDF
                if (!slot.replacementPdfUri.isNullOrBlank()) {
                    val repUri = Uri.parse(slot.replacementPdfUri)
                    var repPfd: ParcelFileDescriptor? = null
                    var repRenderer: PdfRenderer? = null
                    try {
                        repPfd = context.contentResolver.openFileDescriptor(repUri, "r")
                        if (repPfd != null) {
                            repRenderer = PdfRenderer(repPfd)
                            val repIdx = slot.replacementPdfPageIndex.coerceIn(0, (repRenderer.pageCount - 1).coerceAtLeast(0))
                            if (repRenderer.pageCount > 0) {
                                val repPage = repRenderer.openPage(repIdx)
                                val w = repPage.width
                                val h = repPage.height
                                val scale = if (maxOf(w, h) > 1600) 1 else 2
                                val bmp = Bitmap.createBitmap(w * scale, h * scale, Bitmap.Config.ARGB_8888)
                                bmp.eraseColor(Color.WHITE)
                                repPage.render(bmp, null, null, PdfRenderer.Page.RENDER_MODE_FOR_PRINT)
                                repPage.close()

                                val targetW = if (rotation == 90 || rotation == 270) h else w
                                val targetH = if (rotation == 90 || rotation == 270) w else h
                                val pageInfo = PdfDocument.PageInfo.Builder(targetW, targetH, newPageNum++).create()
                                val newPage = newDoc.startPage(pageInfo)
                                val canvas = newPage.canvas
                                canvas.drawColor(Color.WHITE)
                                if (rotation != 0) {
                                    canvas.save()
                                    canvas.translate(targetW / 2f, targetH / 2f)
                                    canvas.rotate(rotation.toFloat())
                                    canvas.translate(-w / 2f, -h / 2f)
                                    canvas.drawBitmap(bmp, null, Rect(0, 0, w, h), null)
                                    canvas.restore()
                                } else {
                                    canvas.drawBitmap(bmp, null, Rect(0, 0, targetW, targetH), null)
                                }
                                bmp.recycle()
                                newDoc.finishPage(newPage)
                                continue
                            }
                        }
                    } finally {
                        try { repRenderer?.close() } catch (_: Exception) {}
                        try { repPfd?.close() } catch (_: Exception) {}
                    }
                }

                // Case 3: Blank or Notes Inserted Page
                if (slot.originalIndex == -1 || slot.originalIndex == -2) {
                    val targetW = if (rotation == 90 || rotation == 270) 842 else 595
                    val targetH = if (rotation == 90 || rotation == 270) 595 else 842
                    val pageInfo = PdfDocument.PageInfo.Builder(targetW, targetH, newPageNum++).create()
                    val newPage = newDoc.startPage(pageInfo)
                    val canvas = newPage.canvas
                    canvas.drawColor(Color.WHITE)
                    if (slot.originalIndex == -2) {
                        val headerPaint = Paint().apply {
                            color = Color.rgb(220, 38, 38)
                            textSize = 14f
                            isFakeBoldText = true
                            isAntiAlias = true
                        }
                        val linePaint = Paint().apply {
                            color = Color.rgb(226, 232, 240)
                            strokeWidth = 1f
                            isAntiAlias = true
                        }
                        val marginPaint = Paint().apply {
                            color = Color.rgb(248, 113, 113)
                            strokeWidth = 1.5f
                            isAntiAlias = true
                        }
                        canvas.drawText("NOTES / MEMORANDUM", 50f, 50f, headerPaint)
                        canvas.drawLine(50f, 65f, targetW - 50f, 65f, headerPaint)
                        canvas.drawLine(70f, 80f, 70f, targetH - 40f, marginPaint)
                        for (y in 90..(targetH - 40) step 26) {
                            canvas.drawLine(40f, y.toFloat(), targetW - 40f, y.toFloat(), linePaint)
                        }
                    }
                    newDoc.finishPage(newPage)
                    continue
                }

                // Case 4: Standard Page from source PDF
                if (slot.originalIndex !in 0 until renderer.pageCount) continue
                val page = renderer.openPage(slot.originalIndex)
                val w = page.width
                val h = page.height
                val scale = if (maxOf(w, h) > 1600) 1 else 2
                val bitmap = Bitmap.createBitmap(w * scale, h * scale, Bitmap.Config.ARGB_8888)
                bitmap.eraseColor(Color.WHITE)
                page.render(bitmap, null, null, PdfRenderer.Page.RENDER_MODE_FOR_PRINT)
                page.close()

                val targetW = if (rotation == 90 || rotation == 270) h else w
                val targetH = if (rotation == 90 || rotation == 270) w else h

                val pageInfo = PdfDocument.PageInfo.Builder(targetW, targetH, newPageNum++).create()
                val newPage = newDoc.startPage(pageInfo)
                val canvas = newPage.canvas
                canvas.drawColor(Color.WHITE)

                if (rotation != 0) {
                    canvas.save()
                    canvas.translate(targetW / 2f, targetH / 2f)
                    canvas.rotate(rotation.toFloat())
                    canvas.translate(-w / 2f, -h / 2f)
                    canvas.drawBitmap(bitmap, null, Rect(0, 0, w, h), null)
                    canvas.restore()
                } else {
                    canvas.drawBitmap(bitmap, null, Rect(0, 0, targetW, targetH), null)
                }
                bitmap.recycle()
                newDoc.finishPage(newPage)
            }

            FileOutputStream(outputFile).use { fos -> newDoc.writeTo(fos) }
            true
        } catch (e: Exception) {
            e.printStackTrace()
            false
        } finally {
            try { newDoc.close() } catch (_: Exception) {}
            try { renderer?.close() } catch (_: Exception) {}
            try { pfd?.close() } catch (_: Exception) {}
        }
    }

    /**
     * Parses range expressions like "1-3, 4-5, 6" (1-based) into 0-based page index groups.
     */
    fun parseSplitRanges(rangeSpec: String, totalPages: Int): List<List<Int>> {
        if (totalPages <= 0) return emptyList()
        val groups = mutableListOf<List<Int>>()
        val tokens = rangeSpec.split(',').map { it.trim() }.filter { it.isNotEmpty() }
        for (token in tokens) {
            if (token.contains('-')) {
                val parts = token.split('-').map { it.trim().toIntOrNull() }
                if (parts.size == 2 && parts[0] != null && parts[1] != null) {
                    val start = parts[0]!!.coerceIn(1, totalPages)
                    val end = parts[1]!!.coerceIn(1, totalPages)
                    val range = if (start <= end) (start..end) else (start downTo end)
                    val group = range.map { it - 1 }
                    if (group.isNotEmpty()) groups.add(group)
                }
            } else {
                val single = token.toIntOrNull()
                if (single != null && single in 1..totalPages) {
                    groups.add(listOf(single - 1))
                }
            }
        }
        return groups
    }

    fun buildChunkSplitGroups(totalPages: Int, pagesPerPart: Int): List<List<Int>> {
        if (totalPages <= 0 || pagesPerPart <= 0) return emptyList()
        return (0 until totalPages).chunked(pagesPerPart)
    }

    /**
     * Splits source PDF into multiple valid standalone PDFs using PDFBox (preserving native vector/text layers)
     * with automatic fallback to PdfRenderer + PdfDocument.
     */
    suspend fun splitPdf(
        context: Context,
        sourceFile: File,
        outputDir: File,
        baseTitle: String,
        pageGroups: List<List<Int>>
    ): List<File> = withContext(Dispatchers.IO) {
        if (!sourceFile.exists() || pageGroups.isEmpty()) return@withContext emptyList()
        if (!outputDir.exists()) outputDir.mkdirs()

        val createdFiles = mutableListOf<File>()
        val cleanBase = com.example.data.local.StorageRenameCoordinator.sanitizeForStorageFileName(
            baseTitle,
            fallback = "Split"
        )

        val pdfBoxSucceeded = try {
            PdfBoxInitializer.ensureInitialized(context)
            com.tom_roush.pdfbox.pdmodel.PDDocument.load(sourceFile).use { srcDoc ->
                if (srcDoc.isEncrypted) return@use false
                pageGroups.forEachIndexed { idx, group ->
                    val validIndices = group.filter { it in 0 until srcDoc.numberOfPages }
                    if (validIndices.isEmpty()) return@forEachIndexed
                    val outFile = File(outputDir, "${cleanBase}_part_${idx + 1}.pdf")
                    com.tom_roush.pdfbox.pdmodel.PDDocument().use { partDoc ->
                        for (pIdx in validIndices) {
                            partDoc.importPage(srcDoc.getPage(pIdx))
                        }
                        partDoc.save(outFile)
                    }
                    if (outFile.exists() && outFile.length() > 0L) {
                        createdFiles.add(outFile)
                    }
                }
                createdFiles.isNotEmpty()
            }
        } catch (_: Exception) {
            createdFiles.forEach { it.delete() }
            createdFiles.clear()
            false
        }

        if (pdfBoxSucceeded) return@withContext createdFiles

        // Reject obviously invalid/corrupted non-PDF files cleanly without launching PdfRenderer
        val hasPdfHeader = runCatching {
            if (sourceFile.length() < 8L) return@runCatching false
            val buf = ByteArray(5)
            sourceFile.inputStream().use { it.read(buf) }
            String(buf, Charsets.US_ASCII) == "%PDF-"
        }.getOrDefault(false)
        if (!hasPdfHeader) return@withContext emptyList()

        // Fallback via PdfRenderer + PdfDocument
        pageGroups.forEachIndexed { idx, group ->
            val outFile = File(outputDir, "${cleanBase}_part_${idx + 1}.pdf")
            val ok = organizePagesWithRotations(
                sourceFile = sourceFile,
                orderedPages = group.map { it to 0 },
                outputFile = outFile
            )
            if (ok && outFile.exists() && outFile.length() > 0L) {
                createdFiles.add(outFile)
            }
        }
        createdFiles
    }

    /**
     * Deterministic duplicate-page detector using perceptual 8x8 dHash + extracted page text comparison.
     */
    suspend fun detectDuplicatePages(
        context: Context,
        sourceFile: File
    ): List<DuplicatePageMatch> = withContext(Dispatchers.IO) {
        if (!sourceFile.exists() || sourceFile.length() <= 0L) return@withContext emptyList()
        val hashes = mutableListOf<Long>()
        val pageTexts = mutableListOf<String>()

        var pfd: ParcelFileDescriptor? = null
        var renderer: PdfRenderer? = null
        try {
            pfd = ParcelFileDescriptor.open(sourceFile, ParcelFileDescriptor.MODE_READ_ONLY)
            renderer = PdfRenderer(pfd)
            val count = renderer.pageCount
            if (count < 2) return@withContext emptyList()

            val extracted = PdfTextExtractor.extractPages(sourceFile)
            for (i in 0 until count) {
                val page = renderer.openPage(i)
                val bmp = Bitmap.createBitmap(72, 72, Bitmap.Config.ARGB_8888)
                bmp.eraseColor(Color.WHITE)
                page.render(bmp, null, null, PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY)
                page.close()
                hashes.add(computeDHash64(bmp))
                bmp.recycle()

                val text = extracted.getOrNull(i)?.fullText
                    ?.lowercase()
                    ?.replace(Regex("\\s+"), " ")
                    ?.trim()
                    ?: ""
                pageTexts.add(text)
            }

            val matches = mutableListOf<DuplicatePageMatch>()
            for (i in 0 until count) {
                for (j in (i + 1) until count) {
                    val dist = java.lang.Long.bitCount(hashes[i] xor hashes[j])
                    val visualSim = ((64 - dist) * 100) / 64
                    val textA = pageTexts[i]
                    val textB = pageTexts[j]
                    val exactTextMatch = textA.length >= 24 && textA == textB

                    if (exactTextMatch || visualSim >= 90) {
                        val finalSim = if (exactTextMatch) maxOf(visualSim, 98) else visualSim
                        val reason = when {
                            exactTextMatch && visualSim >= 90 -> "Identical layout & text"
                            exactTextMatch -> "Identical text content"
                            else -> "Near-identical page visuals ($finalSim%)"
                        }
                        matches.add(DuplicatePageMatch(i, j, finalSim, reason))
                    }
                }
            }
            matches
        } catch (_: Exception) {
            emptyList()
        } finally {
            try { renderer?.close() } catch (_: Exception) {}
            try { pfd?.close() } catch (_: Exception) {}
        }
    }

    private fun computeDHash64(source: Bitmap): Long {
        val scaled = Bitmap.createScaledBitmap(source, 9, 8, true)
        var hash = 0L
        var bit = 0
        for (y in 0 until 8) {
            for (x in 0 until 8) {
                val p1 = scaled.getPixel(x, y)
                val p2 = scaled.getPixel(x + 1, y)
                val l1 = (Color.red(p1) * 30 + Color.green(p1) * 59 + Color.blue(p1) * 11) / 100
                val l2 = (Color.red(p2) * 30 + Color.green(p2) * 59 + Color.blue(p2) * 11) / 100
                if (l1 > l2) {
                    hash = hash or (1L shl bit)
                }
                bit++
            }
        }
        if (scaled !== source) scaled.recycle()
        return hash
    }

    private fun drawAnnotationsOnCanvas(
        canvas: Canvas,
        annotations: List<AnnotationEntity>,
        pageWidth: Float,
        pageHeight: Float
    ) {
        for (ann in annotations) {
            val clampedAlpha = (ann.opacity.coerceIn(0.1f, 1f) * 255).toInt()
            when (ann.type) {
                "PEN", "HIGHLIGHT", "UNDERLINE", "STRIKETHROUGH" -> {
                    if (ann.pointsJson.isNotBlank()) {
                        val paint = Paint().apply {
                            color = ann.colorHex.toInt()
                            strokeWidth = ann.strokeWidth
                            style = Paint.Style.STROKE
                            strokeCap = Paint.Cap.ROUND
                            strokeJoin = Paint.Join.ROUND
                            isAntiAlias = true
                            alpha = if (ann.type == "HIGHLIGHT") minOf(clampedAlpha, 115) else clampedAlpha
                        }
                        try {
                            val arr = JSONArray(ann.pointsJson)
                            if (arr.length() > 0) {
                                val path = android.graphics.Path()
                                for (i in 0 until arr.length()) {
                                    val pt = arr.getJSONObject(i)
                                    val x = (pt.getDouble("x").toFloat()) * pageWidth
                                    val y = (pt.getDouble("y").toFloat()) * pageHeight
                                    if (i == 0) path.moveTo(x, y) else path.lineTo(x, y)
                                }
                                canvas.drawPath(path, paint)
                            }
                        } catch (e: Exception) {
                            e.printStackTrace()
                        }
                    } else if ((ann.type == "UNDERLINE" || ann.type == "STRIKETHROUGH") && ann.width > 0f) {
                        val paint = Paint().apply {
                            color = ann.colorHex.toInt()
                            strokeWidth = ann.strokeWidth.coerceAtLeast(2f)
                            style = Paint.Style.STROKE
                            strokeCap = Paint.Cap.ROUND
                            isAntiAlias = true
                            alpha = clampedAlpha
                        }
                        val x1 = ann.posX * pageWidth
                        val x2 = (ann.posX + ann.width) * pageWidth
                        val y = ann.posY * pageHeight
                        canvas.drawLine(x1, y, x2, y, paint)
                    }
                }
                "FORM_FIELD" -> {
                    val x = ann.posX * pageWidth
                    val y = ann.posY * pageHeight
                    val textPaint = Paint().apply {
                        color = ann.colorHex.toInt()
                        textSize = if (ann.strokeWidth > 6f) ann.strokeWidth else 13f
                        isAntiAlias = true
                        typeface = android.graphics.Typeface.DEFAULT
                    }
                    if (ann.extraData.contains("CHECKBOX")) {
                        val boxSize = 14f
                        val strokeP = Paint().apply {
                            color = ann.colorHex.toInt()
                            style = Paint.Style.STROKE
                            strokeWidth = 1.6f
                            isAntiAlias = true
                        }
                        canvas.drawRect(RectF(x, y - boxSize, x + boxSize, y), strokeP)
                        if (ann.textContent == "true" || ann.textContent == "✓") {
                            canvas.drawText("✓", x + 2.5f, y - 2f, textPaint)
                        }
                    } else {
                        canvas.drawText(ann.textContent, x, y, textPaint)
                    }
                }
                "STICKY_NOTE" -> {
                    val x = ann.posX * pageWidth
                    val y = ann.posY * pageHeight
                    val badgePaint = Paint().apply {
                        color = Color.rgb(245, 158, 11)
                        style = Paint.Style.FILL
                        isAntiAlias = true
                    }
                    val textPaint = Paint().apply {
                        color = Color.rgb(15, 23, 42)
                        textSize = 10f
                        isAntiAlias = true
                    }
                    canvas.drawCircle(x, y, 12f, badgePaint)
                    canvas.drawText("💬", x - 6f, y + 4f, textPaint)
                    if (ann.textContent.isNotBlank()) {
                        val bgPaint = Paint().apply {
                            color = Color.rgb(254, 243, 199)
                            style = Paint.Style.FILL
                        }
                        canvas.drawRoundRect(RectF(x + 14f, y - 10f, x + 160f, y + 30f), 4f, 4f, bgPaint)
                        canvas.drawText(ann.textContent.take(24), x + 20f, y + 10f, textPaint)
                    }
                }
                "STAMP" -> {
                    val x = ann.posX * pageWidth
                    val y = ann.posY * pageHeight
                    val stampPaint = Paint().apply {
                        color = ann.colorHex.toInt()
                        style = Paint.Style.STROKE
                        strokeWidth = 2.5f
                        isAntiAlias = true
                        alpha = clampedAlpha
                    }
                    val stampText = Paint().apply {
                        color = ann.colorHex.toInt()
                        textSize = 14f
                        isFakeBoldText = true
                        isAntiAlias = true
                        alpha = clampedAlpha
                    }
                    val stampRect = RectF(x - 60f, y - 18f, x + 60f, y + 18f)
                    canvas.drawRoundRect(stampRect, 6f, 6f, stampPaint)
                    canvas.drawText(ann.textContent, x - 48f, y + 5f, stampText)
                }
                "SIGNATURE", "TYPED_SIGNATURE" -> {
                    val x = ann.posX * pageWidth
                    val y = ann.posY * pageHeight
                    if (ann.textContent.isNotBlank()) {
                        val sigPaint = Paint().apply {
                            color = ann.colorHex.toInt()
                            textSize = if (ann.strokeWidth > 10f) ann.strokeWidth else 26f
                            isAntiAlias = true
                            isFakeBoldText = true
                            typeface = android.graphics.Typeface.create("cursive", android.graphics.Typeface.ITALIC)
                        }
                        canvas.drawText(ann.textContent, x, y, sigPaint)
                        val linePaint = Paint().apply {
                            color = ann.colorHex.toInt()
                            strokeWidth = 2f
                            style = Paint.Style.STROKE
                            isAntiAlias = true
                        }
                        val textWidth = sigPaint.measureText(ann.textContent)
                        val flourishPath = android.graphics.Path().apply {
                            moveTo(x - 6f, y + 6f)
                            quadTo(x + textWidth * 0.55f, y + 14f, x + textWidth + 14f, y + 4f)
                        }
                        canvas.drawPath(flourishPath, linePaint)
                    } else if (ann.pointsJson.isNotBlank()) {
                        val sigW = (if (ann.width > 0.05f) ann.width else 0.30f) * pageWidth
                        val sigH = (if (ann.height > 0.03f) ann.height else 0.12f) * pageHeight
                        val sigPaint = Paint().apply {
                            color = ann.colorHex.toInt()
                            strokeWidth = 2.5f
                            style = Paint.Style.STROKE
                            strokeCap = Paint.Cap.ROUND
                            strokeJoin = Paint.Join.ROUND
                            isAntiAlias = true
                        }
                        try {
                            val strokes = JSONArray(ann.pointsJson)
                            for (s in 0 until strokes.length()) {
                                val strokeArr = strokes.getJSONArray(s)
                                val path = android.graphics.Path()
                                for (p in 0 until strokeArr.length()) {
                                    val pt = strokeArr.getJSONObject(p)
                                    val px = x + (pt.getDouble("x").toFloat()) * sigW
                                    val py = y + (pt.getDouble("y").toFloat()) * sigH
                                    if (p == 0) path.moveTo(px, py) else path.lineTo(px, py)
                                }
                                canvas.drawPath(path, sigPaint)
                            }
                        } catch (e: Exception) {
                            e.printStackTrace()
                        }
                    }
                }
                "TEXT_BOX" -> {
                    val x = ann.posX * pageWidth
                    val y = ann.posY * pageHeight
                    val fontSize = if (ann.strokeWidth > 6f) ann.strokeWidth else 14f
                    val textPaint = Paint().apply {
                        color = ann.colorHex.toInt()
                        textSize = fontSize
                        isAntiAlias = true
                        typeface = android.graphics.Typeface.DEFAULT_BOLD
                    }
                    if (ann.width > 0f && ann.height > 0f) {
                        val w = ann.width * pageWidth
                        val h = ann.height * pageHeight
                        val bgPaint = Paint().apply {
                            color = Color.WHITE
                            style = Paint.Style.FILL
                        }
                        val borderPaint = Paint().apply {
                            color = Color.rgb(203, 213, 225)
                            style = Paint.Style.STROKE
                            strokeWidth = 1f
                        }
                        val rect = RectF(x, y - (fontSize * 0.85f), x + w, y - (fontSize * 0.85f) + h)
                        canvas.drawRoundRect(rect, 4f, 4f, bgPaint)
                        canvas.drawRoundRect(rect, 4f, 4f, borderPaint)
                    }
                    canvas.drawText(ann.textContent, x, y, textPaint)
                }
                "WHITEOUT", "REDACT" -> {
                    val x = ann.posX * pageWidth
                    val y = ann.posY * pageHeight
                    val w = (if (ann.width > 0f) ann.width else 0.25f) * pageWidth
                    val h = (if (ann.height > 0f) ann.height else 0.04f) * pageHeight
                    val rect = RectF(x, y, x + w, y + h)
                    val fillPaint = Paint().apply {
                        color = if (ann.type == "REDACT") Color.BLACK else Color.WHITE
                        style = Paint.Style.FILL
                    }
                    canvas.drawRect(rect, fillPaint)
                    if (ann.textContent.isNotBlank()) {
                        val replacePaint = Paint().apply {
                            color = ann.colorHex.toInt()
                            textSize = 12f
                            isAntiAlias = true
                            typeface = android.graphics.Typeface.DEFAULT_BOLD
                        }
                        canvas.drawText(ann.textContent, x + 6f, y + (h * 0.7f), replacePaint)
                    }
                }
                "WATERMARK" -> {
                    val watermarkPaint = Paint().apply {
                        color = ann.colorHex.toInt()
                        alpha = 60
                        textSize = (ann.strokeWidth * 2.5f).coerceIn(24f, 72f)
                        isFakeBoldText = true
                        isAntiAlias = true
                        textAlign = Paint.Align.CENTER
                    }
                    canvas.save()
                    canvas.rotate(-45f, pageWidth / 2f, pageHeight / 2f)
                    canvas.drawText(ann.textContent.ifBlank { "CONFIDENTIAL" }, pageWidth / 2f, pageHeight / 2f, watermarkPaint)
                    canvas.restore()
                }
            }
        }
    }
}

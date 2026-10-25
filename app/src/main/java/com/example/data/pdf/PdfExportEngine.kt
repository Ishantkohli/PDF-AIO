package com.example.data.pdf

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Rect
import android.graphics.RectF
import android.graphics.pdf.PdfDocument
import android.graphics.pdf.PdfRenderer
import android.net.Uri
import android.os.ParcelFileDescriptor
import com.example.data.local.AnnotationEntity
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import java.io.File
import java.io.FileOutputStream

object PdfExportEngine {

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

                // Render background page at 2x resolution for crispness
                val pageBitmap = Bitmap.createBitmap(width * 2, height * 2, Bitmap.Config.ARGB_8888)
                page.render(pageBitmap, null, null, PdfRenderer.Page.RENDER_MODE_FOR_PRINT)
                page.close()

                val pageInfo = PdfDocument.PageInfo.Builder(width, height, pageIdx + 1).create()
                val docPage = newDoc.startPage(pageInfo)
                val canvas = docPage.canvas

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

    suspend fun convertImagesToPdf(
        context: Context,
        imageUris: List<Uri>,
        outputFile: File
    ): Boolean = withContext(Dispatchers.IO) {
        val newDoc = PdfDocument()
        val pageWidth = 595 // A4 standard
        val pageHeight = 842

        try {
            for ((idx, uri) in imageUris.withIndex()) {
                val inputStream = context.contentResolver.openInputStream(uri) ?: continue
                val bitmap = BitmapFactory.decodeStream(inputStream)
                inputStream.close()

                if (bitmap != null) {
                    val pageInfo = PdfDocument.PageInfo.Builder(pageWidth, pageHeight, idx + 1).create()
                    val page = newDoc.startPage(pageInfo)
                    val canvas = page.canvas

                    // Scale image maintaining aspect ratio
                    val scale = Math.min(
                        (pageWidth - 60f) / bitmap.width,
                        (pageHeight - 60f) / bitmap.height
                    )
                    val destW = bitmap.width * scale
                    val destH = bitmap.height * scale
                    val left = (pageWidth - destW) / 2f
                    val top = (pageHeight - destH) / 2f

                    canvas.drawBitmap(bitmap, null, RectF(left, top, left + destW, top + destH), null)
                    bitmap.recycle()
                    newDoc.finishPage(page)
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
                        val bitmap = Bitmap.createBitmap(w * 2, h * 2, Bitmap.Config.ARGB_8888)
                        page.render(bitmap, null, null, PdfRenderer.Page.RENDER_MODE_FOR_PRINT)
                        page.close()

                        totalPages++
                        val pageInfo = PdfDocument.PageInfo.Builder(w, h, totalPages).create()
                        val newPage = newDoc.startPage(pageInfo)
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

    suspend fun organizePages(
        sourceFile: File,
        includedPageIndices: List<Int>,
        pageRotations: Map<Int, Int>, // pageIndex to degrees (0, 90, 180, 270)
        outputFile: File
    ): Boolean = withContext(Dispatchers.IO) {
        var pfd: ParcelFileDescriptor? = null
        var renderer: PdfRenderer? = null
        val newDoc = PdfDocument()

        try {
            pfd = ParcelFileDescriptor.open(sourceFile, ParcelFileDescriptor.MODE_READ_ONLY)
            renderer = PdfRenderer(pfd)

            var newPageNum = 1
            for (pageIdx in includedPageIndices) {
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
                val rotation = pageRotations[pageIdx] ?: 0

                val bitmap = Bitmap.createBitmap(w * 2, h * 2, Bitmap.Config.ARGB_8888)
                page.render(bitmap, null, null, PdfRenderer.Page.RENDER_MODE_FOR_PRINT)
                page.close()

                val targetW = if (rotation == 90 || rotation == 270) h else w
                val targetH = if (rotation == 90 || rotation == 270) w else h

                val pageInfo = PdfDocument.PageInfo.Builder(targetW, targetH, newPageNum++).create()
                val newPage = newDoc.startPage(pageInfo)
                val canvas = newPage.canvas

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

    private fun drawAnnotationsOnCanvas(
        canvas: Canvas,
        annotations: List<AnnotationEntity>,
        pageWidth: Float,
        pageHeight: Float
    ) {
        for (ann in annotations) {
            when (ann.type) {
                "PEN", "HIGHLIGHT" -> {
                    if (ann.pointsJson.isNotBlank()) {
                        val paint = Paint().apply {
                            color = ann.colorHex.toInt()
                            strokeWidth = ann.strokeWidth
                            style = Paint.Style.STROKE
                            strokeCap = Paint.Cap.ROUND
                            strokeJoin = Paint.Join.ROUND
                            isAntiAlias = true
                            if (ann.type == "HIGHLIGHT") {
                                alpha = 110 // Translucent highlighter
                            }
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
                    }
                }
                "STICKY_NOTE" -> {
                    val x = ann.posX * pageWidth
                    val y = ann.posY * pageHeight
                    // Draw comment badge
                    val badgePaint = Paint().apply {
                        color = Color.rgb(245, 158, 11) // Amber
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
                    }
                    val stampText = Paint().apply {
                        color = ann.colorHex.toInt()
                        textSize = 14f
                        isFakeBoldText = true
                        isAntiAlias = true
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
                        // Underline flourish
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
                        val sigPaint = Paint().apply {
                            color = ann.colorHex.toInt()
                            strokeWidth = 2.5f
                            style = Paint.Style.STROKE
                            strokeCap = Paint.Cap.ROUND
                            strokeJoin = Paint.Join.ROUND
                            isAntiAlias = true
                        }
                        try {
                            // Multiple stroke paths inside signature
                            val strokes = JSONArray(ann.pointsJson)
                            for (s in 0 until strokes.length()) {
                                val strokeArr = strokes.getJSONArray(s)
                                val path = android.graphics.Path()
                                for (p in 0 until strokeArr.length()) {
                                    val pt = strokeArr.getJSONObject(p)
                                    val px = (pt.getDouble("x").toFloat()) * pageWidth
                                    val py = (pt.getDouble("y").toFloat()) * pageHeight
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

                    // Optional whiteout backdrop if width > 0
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

                    // If replacement text was provided, draw it over the whiteout
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
                        alpha = 60 // 25% opacity
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

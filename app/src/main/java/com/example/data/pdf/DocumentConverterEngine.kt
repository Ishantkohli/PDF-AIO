package com.example.data.pdf

import android.content.Context
import android.graphics.*
import android.graphics.pdf.PdfDocument
import android.graphics.pdf.PdfRenderer
import android.net.Uri
import android.os.ParcelFileDescriptor
import android.util.Log
import android.util.Xml
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.xmlpull.v1.XmlPullParser
import java.io.*
import java.util.zip.*

/**
 * High-performance Document Conversion Engine
 * Supports:
 * 1. PDF to Images (PNG / JPG with resolution & DPI options, batch ZIP export)
 * 2. Images (PNG, JPG, WebP, BMP, GIF) to PDF (custom margins, fit/fill, page size, compression)
 * 3. Word (.docx / .doc) to PDF (OpenXML parsing, paragraph formatting, headings, page layout)
 * 4. PDF to Word (.docx) (Decompressed stream + ToUnicode CMap extraction & rendered image fallback for scanned pages)
 * 5. PDF to TXT & TXT to PDF (Clean UTF-8 conversion)
 * 6. Watermark Application (custom text, opacity, angle, color, repeated diagonal tiling)
 */
object DocumentConverterEngine {

    private const val TAG = "DocumentConverter"

    data class WatermarkOptions(
        val text: String = "CONFIDENTIAL",
        val colorHex: Long = 0xFFDC2626,
        val opacity: Float = 0.25f,
        val rotationAngle: Float = 45f,
        val fontSizeSp: Float = 38f,
        val isTiledPattern: Boolean = false,
        val applyToAllPages: Boolean = true
    )

    enum class ImageFitMode {
        FIT_WITH_MARGINS,
        FULL_BLEED,
        CENTER_ORIGINAL
    }

    enum class PdfPageSize(val widthPoints: Int, val heightPoints: Int, val displayName: String) {
        A4(595, 842, "A4 (Standard)"),
        US_LETTER(612, 792, "US Letter"),
        AUTO(0, 0, "Auto (Match Image)")
    }

    // ==========================================
    // 1. PDF TO IMAGES
    // ==========================================

    suspend fun convertPdfToImages(
        context: Context,
        pdfFile: File,
        outputFormat: Bitmap.CompressFormat = Bitmap.CompressFormat.PNG,
        dpiScale: Int = 2,
        pageIndices: List<Int>? = null
    ): List<File> = withContext(Dispatchers.IO) {
        val outputFiles = mutableListOf<File>()
        val exportDir = File(context.cacheDir, "pdf_images_${System.currentTimeMillis()}").apply { mkdirs() }
        val ext = if (outputFormat == Bitmap.CompressFormat.PNG) "png" else "jpg"

        var pfd: ParcelFileDescriptor? = null
        var renderer: PdfRenderer? = null

        try {
            pfd = ParcelFileDescriptor.open(pdfFile, ParcelFileDescriptor.MODE_READ_ONLY)
            renderer = PdfRenderer(pfd)
            val totalPages = renderer.pageCount
            val pagesToRender = pageIndices ?: (0 until totalPages).toList()

            for (pageIdx in pagesToRender) {
                if (pageIdx < 0 || pageIdx >= totalPages) continue
                val page = renderer.openPage(pageIdx)
                val targetW = (page.width * dpiScale).coerceAtLeast(1)
                val targetH = (page.height * dpiScale).coerceAtLeast(1)

                val bitmap = Bitmap.createBitmap(targetW, targetH, Bitmap.Config.ARGB_8888)
                val canvas = Canvas(bitmap)
                canvas.drawColor(Color.WHITE)
                page.render(bitmap, null, null, PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY)
                page.close()

                val outFile = File(exportDir, "${pdfFile.nameWithoutExtension}_page_${pageIdx + 1}.$ext")
                FileOutputStream(outFile).use { fos ->
                    val quality = if (outputFormat == Bitmap.CompressFormat.PNG) 100 else 92
                    bitmap.compress(outputFormat, quality, fos)
                }
                bitmap.recycle()
                outputFiles.add(outFile)
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error converting PDF to images", e)
        } finally {
            try { renderer?.close() } catch (ignored: Exception) {}
            try { pfd?.close() } catch (ignored: Exception) {}
        }
        outputFiles
    }

    suspend fun createZipFromFiles(files: List<File>, zipFile: File): Boolean = withContext(Dispatchers.IO) {
        try {
            ZipOutputStream(FileOutputStream(zipFile)).use { zos ->
                val buffer = ByteArray(8192)
                for (file in files) {
                    val entry = ZipEntry(file.name)
                    zos.putNextEntry(entry)
                    FileInputStream(file).use { fis ->
                        var len: Int
                        while (fis.read(buffer).also { len = it } > 0) {
                            zos.write(buffer, 0, len)
                        }
                    }
                    zos.closeEntry()
                }
            }
            true
        } catch (e: Exception) {
            Log.e(TAG, "Failed to create ZIP", e)
            false
        }
    }

    // ==========================================
    // 2. IMAGES (ALL FORMATS) TO PDF
    // ==========================================

    suspend fun convertImagesToPdfAdvanced(
        context: Context,
        imageUris: List<Uri>,
        outputFile: File,
        pageSize: PdfPageSize = PdfPageSize.A4,
        fitMode: ImageFitMode = ImageFitMode.FIT_WITH_MARGINS,
        compressionQuality: Int = 90
    ): Boolean = withContext(Dispatchers.IO) {
        val newDoc = PdfDocument()
        try {
            val safeQuality = compressionQuality.coerceIn(15, 100)
            val maxDim = when {
                safeQuality >= 85 -> 2048
                safeQuality >= 60 -> 1600
                else -> 1200
            }
            var pageNum = 0
            for (uri in imageUris) {
                val decoded = PdfExportEngine.decodeSampledRotatedBitmap(context, uri, maxDim) ?: continue
                val rawBitmap = if (safeQuality < 100) {
                    val baos = ByteArrayOutputStream()
                    decoded.compress(Bitmap.CompressFormat.JPEG, safeQuality, baos)
                    val compressed = BitmapFactory.decodeByteArray(baos.toByteArray(), 0, baos.size())
                    if (compressed != null) {
                        decoded.recycle()
                        compressed
                    } else {
                        decoded
                    }
                } else {
                    decoded
                }

                pageNum++
                val targetPageW = if (pageSize == PdfPageSize.AUTO) rawBitmap.width else pageSize.widthPoints
                val targetPageH = if (pageSize == PdfPageSize.AUTO) rawBitmap.height else pageSize.heightPoints

                val pageInfo = PdfDocument.PageInfo.Builder(targetPageW, targetPageH, pageNum).create()
                val page = newDoc.startPage(pageInfo)
                val canvas = page.canvas
                canvas.drawColor(Color.WHITE)

                when (fitMode) {
                    ImageFitMode.FULL_BLEED -> {
                        val rect = RectF(0f, 0f, targetPageW.toFloat(), targetPageH.toFloat())
                        canvas.drawBitmap(rawBitmap, null, rect, null)
                    }
                    ImageFitMode.CENTER_ORIGINAL -> {
                        val left = (targetPageW - rawBitmap.width) / 2f
                        val top = (targetPageH - rawBitmap.height) / 2f
                        canvas.drawBitmap(rawBitmap, left, top, null)
                    }
                    ImageFitMode.FIT_WITH_MARGINS -> {
                        val margin = if (pageSize == PdfPageSize.AUTO) 0f else 36f
                        val availW = targetPageW - (margin * 2)
                        val availH = targetPageH - (margin * 2)

                        val scale = minOf(
                            availW / rawBitmap.width.coerceAtLeast(1),
                            availH / rawBitmap.height.coerceAtLeast(1)
                        )
                        val destW = rawBitmap.width * scale
                        val destH = rawBitmap.height * scale
                        val left = margin + (availW - destW) / 2f
                        val top = margin + (availH - destH) / 2f

                        val destRect = RectF(left, top, left + destW, top + destH)
                        canvas.drawBitmap(rawBitmap, null, destRect, null)
                    }
                }

                rawBitmap.recycle()
                newDoc.finishPage(page)
            }

            if (pageNum == 0) return@withContext false

            FileOutputStream(outputFile).use { fos ->
                newDoc.writeTo(fos)
            }
            true
        } catch (e: Exception) {
            Log.e(TAG, "Error converting images to PDF", e)
            false
        } finally {
            try { newDoc.close() } catch (ignored: Exception) {}
        }
    }

    // ==========================================
    // 3. WORD (.DOCX / .DOC) TO PDF
    // ==========================================

    data class WordParagraph(
        val text: String,
        val isHeading: Boolean = false,
        val headingLevel: Int = 1,
        val isBold: Boolean = false,
        val isItalic: Boolean = false,
        val isBullet: Boolean = false
    )

    suspend fun convertWordToPdf(
        context: Context,
        wordUri: Uri,
        outputFile: File,
        docTitle: String = "Word Document"
    ): Boolean = withContext(Dispatchers.IO) {
        try {
            val paragraphs = mutableListOf<WordParagraph>()
            val inputStream = context.contentResolver.openInputStream(wordUri) ?: return@withContext false

            val tempWordFile = File(context.cacheDir, "temp_word_${System.currentTimeMillis()}.bin")
            FileOutputStream(tempWordFile).use { fos ->
                inputStream.copyTo(fos)
            }
            inputStream.close()

            val isDocx = try {
                ZipFile(tempWordFile).use { zip -> zip.getEntry("word/document.xml") != null }
            } catch (e: Exception) {
                false
            }

            if (isDocx) {
                paragraphs.addAll(parseDocxFile(tempWordFile))
            } else {
                paragraphs.addAll(parseLegacyDocFile(tempWordFile))
            }

            tempWordFile.delete()

            if (paragraphs.isEmpty()) {
                paragraphs.add(WordParagraph(text = docTitle, isHeading = true, headingLevel = 1))
                paragraphs.add(WordParagraph(text = "This document was converted from a Word processing file."))
            }

            renderParagraphsToPdf(paragraphs, outputFile, docTitle)
            true
        } catch (e: Exception) {
            Log.e(TAG, "Error converting Word to PDF", e)
            false
        }
    }

    private fun parseDocxFile(docxFile: File): List<WordParagraph> {
        val result = mutableListOf<WordParagraph>()
        try {
            ZipFile(docxFile).use { zip ->
                val entry = zip.getEntry("word/document.xml") ?: return emptyList()
                zip.getInputStream(entry).use { xmlStream ->
                    val parser = Xml.newPullParser()
                    parser.setFeature(XmlPullParser.FEATURE_PROCESS_NAMESPACES, true)
                    parser.setInput(xmlStream, "UTF-8")

                    var eventType = parser.eventType
                    var currentText = StringBuilder()
                    var isBold = false
                    var isItalic = false
                    var isHeading = false
                    var headingLevel = 1
                    var isBullet = false
                    var inParagraph = false

                    while (eventType != XmlPullParser.END_DOCUMENT) {
                        val localName = parser.name?.substringAfter(':') ?: ""
                        when (eventType) {
                            XmlPullParser.START_TAG -> {
                                when (localName) {
                                    "p" -> {
                                        inParagraph = true
                                        currentText = StringBuilder()
                                        isBold = false
                                        isItalic = false
                                        isHeading = false
                                        headingLevel = 1
                                        isBullet = false
                                    }
                                    "pStyle" -> {
                                        val styleVal = parser.getAttributeValue(null, "val")
                                            ?: parser.getAttributeValue("http://schemas.openxmlformats.org/wordprocessingml/2006/main", "val")
                                            ?: ""
                                        if (styleVal.contains("Heading", ignoreCase = true) ||
                                            styleVal.contains("Title", ignoreCase = true)
                                        ) {
                                            isHeading = true
                                            headingLevel = styleVal.filter { it.isDigit() }.toIntOrNull() ?: 1
                                        }
                                    }
                                    "numPr" -> isBullet = true
                                    "b" -> isBold = true
                                    "i" -> isItalic = true
                                    "t" -> {
                                        if (inParagraph) {
                                            val text = parser.nextText()
                                            currentText.append(text)
                                        }
                                    }
                                    "tab" -> if (inParagraph) currentText.append("    ")
                                    "br", "cr" -> if (inParagraph) currentText.append("\n")
                                }
                            }
                            XmlPullParser.END_TAG -> {
                                if (localName == "p" && inParagraph) {
                                    inParagraph = false
                                    val paragraphText = currentText.toString().trim()
                                    if (paragraphText.isNotBlank()) {
                                        result.add(
                                            WordParagraph(
                                                text = paragraphText,
                                                isHeading = isHeading,
                                                headingLevel = headingLevel,
                                                isBold = isBold,
                                                isItalic = isItalic,
                                                isBullet = isBullet
                                            )
                                        )
                                    }
                                }
                            }
                        }
                        eventType = parser.next()
                    }
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error parsing docx", e)
        }
        return result
    }

    private fun parseLegacyDocFile(docFile: File): List<WordParagraph> {
        val result = mutableListOf<WordParagraph>()
        try {
            val bytes = docFile.readBytes()
            val textBuilder = StringBuilder()
            var printableCount = 0

            for (b in bytes) {
                val c = b.toInt().toChar()
                if (c in ' '..'~' || c == '\n' || c == '\r' || c == '\t') {
                    textBuilder.append(c)
                    printableCount++
                } else if (textBuilder.length > 20 && printableCount > 10) {
                    textBuilder.append('\n')
                    printableCount = 0
                }
            }

            val lines = textBuilder.toString().split('\n')
            for (line in lines) {
                val trimmed = line.trim()
                if (trimmed.length > 3 && trimmed.any { it.isLetterOrDigit() }) {
                    result.add(
                        WordParagraph(
                            text = trimmed,
                            isHeading = trimmed.length < 50 && trimmed == trimmed.uppercase(),
                            isBullet = trimmed.startsWith("-") || trimmed.startsWith("*")
                        )
                    )
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error reading legacy doc", e)
        }
        return result
    }

    private fun renderParagraphsToPdf(
        paragraphs: List<WordParagraph>,
        outputFile: File,
        docTitle: String
    ) {
        val doc = PdfDocument()
        val pageW = 595
        val pageH = 842
        val margin = 54f
        val contentW = pageW - (margin * 2)

        val titlePaint = Paint().apply {
            color = Color.rgb(20, 20, 25)
            textSize = 18f
            isFakeBoldText = true
            isAntiAlias = true
        }
        val heading1Paint = Paint().apply {
            color = Color.rgb(220, 38, 38)
            textSize = 14.5f
            isFakeBoldText = true
            isAntiAlias = true
        }
        val heading2Paint = Paint().apply {
            color = Color.rgb(30, 41, 59)
            textSize = 12.5f
            isFakeBoldText = true
            isAntiAlias = true
        }
        val bodyPaint = Paint().apply {
            color = Color.rgb(51, 65, 85)
            textSize = 10f
            isAntiAlias = true
        }
        val bodyBoldPaint = Paint().apply {
            color = Color.rgb(30, 41, 59)
            textSize = 10f
            isFakeBoldText = true
            isAntiAlias = true
        }
        val footerPaint = Paint().apply {
            color = Color.rgb(148, 163, 184)
            textSize = 8.5f
            isAntiAlias = true
        }
        val linePaint = Paint().apply {
            color = Color.rgb(226, 232, 240)
            strokeWidth = 1f
            isAntiAlias = true
        }

        var pageNumber = 1
        var currentPageInfo = PdfDocument.PageInfo.Builder(pageW, pageH, pageNumber).create()
        var currentPage = doc.startPage(currentPageInfo)
        var canvas = currentPage.canvas
        var cursorY = margin + 20f

        fun startNewPage() {
            canvas.drawText("Page $pageNumber", pageW - margin - 40f, pageH - 24f, footerPaint)
            doc.finishPage(currentPage)
            pageNumber++
            currentPageInfo = PdfDocument.PageInfo.Builder(pageW, pageH, pageNumber).create()
            currentPage = doc.startPage(currentPageInfo)
            canvas = currentPage.canvas
            cursorY = margin + 20f
        }

        canvas.drawText(docTitle.replace("_", " ").take(65), margin, cursorY, titlePaint)
        cursorY += 12f
        canvas.drawLine(margin, cursorY, pageW - margin, cursorY, linePaint)
        cursorY += 22f

        for (p in paragraphs) {
            val paint = when {
                p.isHeading && p.headingLevel == 1 -> heading1Paint
                p.isHeading -> heading2Paint
                p.isBold -> bodyBoldPaint
                else -> bodyPaint
            }

            val prefix = if (p.isBullet) "•  " else ""
            val subLines = (prefix + p.text).replace("\r\n", "\n").replace('\r', '\n').split('\n')

            for (subLine in subLines) {
                val words = subLine.split(Regex("\\s+"))
                var lineBuilder = StringBuilder()

                for (word in words) {
                    if (word.isEmpty()) continue
                    val testLine = if (lineBuilder.isEmpty()) word else "$lineBuilder $word"
                    val measuredW = paint.measureText(testLine)

                    if (measuredW > contentW && lineBuilder.isNotEmpty()) {
                        if (cursorY > pageH - margin - 30f) startNewPage()
                        canvas.drawText(lineBuilder.toString(), margin, cursorY, paint)
                        cursorY += paint.textSize * 1.45f
                        lineBuilder = StringBuilder(word)
                    } else {
                        lineBuilder = StringBuilder(testLine)
                    }
                }

                if (lineBuilder.isNotEmpty()) {
                    if (cursorY > pageH - margin - 30f) startNewPage()
                    canvas.drawText(lineBuilder.toString(), margin, cursorY, paint)
                    cursorY += (paint.textSize * 1.45f)
                } else {
                    cursorY += paint.textSize * 0.7f
                }
            }
            cursorY += if (p.isHeading) 10f else 5f
        }

        canvas.drawText("Page $pageNumber", pageW - margin - 40f, pageH - 24f, footerPaint)
        doc.finishPage(currentPage)

        FileOutputStream(outputFile).use { fos ->
            doc.writeTo(fos)
        }
        doc.close()
    }

    // ==========================================
    // 4. PDF TO WORD (.DOCX)
    // ==========================================

    private data class DocxPageContent(
        val pageNumber: Int,
        val paragraphs: List<String>,
        val renderedImageJpeg: ByteArray? = null,
        val imageWidthPx: Int = 0,
        val imageHeightPx: Int = 0
    )

    suspend fun convertPdfToWord(
        context: Context,
        pdfFile: File,
        outputDocxFile: File,
        docTitle: String = "Exported Document"
    ): Boolean = withContext(Dispatchers.IO) {
        var pfd: ParcelFileDescriptor? = null
        var renderer: PdfRenderer? = null
        val docxPages = mutableListOf<DocxPageContent>()

        try {
            pfd = ParcelFileDescriptor.open(pdfFile, ParcelFileDescriptor.MODE_READ_ONLY)
            renderer = PdfRenderer(pfd)
            val totalPdfPages = renderer.pageCount

            // Extract real decompressed & ToUnicode-mapped text per page
            val extractedPages = PdfTextExtractor.extractPages(pdfFile)

            for (pageIdx in 0 until totalPdfPages) {
                val pageNum = pageIdx + 1
                val textParas = extractedPages.getOrNull(pageIdx)?.paragraphs
                    ?.map { PdfTextExtractor.sanitizeExtractedLine(it) }
                    ?.filter { it.isNotBlank() }
                    ?: emptyList()

                if (textParas.isNotEmpty()) {
                    docxPages.add(
                        DocxPageContent(
                            pageNumber = pageNum,
                            paragraphs = textParas
                        )
                    )
                } else {
                    // Image/Scanned PDF page (no selectable text layer) -> render crisp JPEG page image into DOCX
                    val page = renderer.openPage(pageIdx)
                    val targetW = (page.width * 2).coerceIn(400, 1400)
                    val scaleFactor = targetW.toFloat() / page.width.coerceAtLeast(1)
                    val targetH = (page.height * scaleFactor).toInt().coerceAtLeast(1)

                    val bitmap = Bitmap.createBitmap(targetW, targetH, Bitmap.Config.ARGB_8888)
                    val canvas = Canvas(bitmap)
                    canvas.drawColor(Color.WHITE)
                    page.render(bitmap, null, null, PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY)
                    page.close()

                    val jpegOut = ByteArrayOutputStream()
                    bitmap.compress(Bitmap.CompressFormat.JPEG, 88, jpegOut)
                    bitmap.recycle()

                    docxPages.add(
                        DocxPageContent(
                            pageNumber = pageNum,
                            paragraphs = emptyList(),
                            renderedImageJpeg = jpegOut.toByteArray(),
                            imageWidthPx = targetW,
                            imageHeightPx = targetH
                        )
                    )
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error converting PDF to Word", e)
            val fallbackPages = PdfTextExtractor.extractPages(pdfFile)
            if (fallbackPages.isNotEmpty()) {
                for (fp in fallbackPages) {
                    docxPages.add(DocxPageContent(fp.pageNumber, fp.paragraphs))
                }
            }
        } finally {
            try { renderer?.close() } catch (ignored: Exception) {}
            try { pfd?.close() } catch (ignored: Exception) {}
        }

        if (docxPages.isEmpty()) {
            docxPages.add(
                DocxPageContent(
                    pageNumber = 1,
                    paragraphs = listOf("No readable text content found in this PDF document.")
                )
            )
        }

        writeDocxFile(outputDocxFile, docTitle, docxPages)
    }

    private fun writeDocxFile(
        outputFile: File,
        title: String,
        pages: List<DocxPageContent>
    ): Boolean {
        return try {
            val hasImages = pages.any { it.renderedImageJpeg != null }
            val imageRels = mutableListOf<Pair<String, String>>() // rId -> media/page_N.jpg

            ZipOutputStream(FileOutputStream(outputFile)).use { zos ->
                // 1. [Content_Types].xml
                zos.putNextEntry(ZipEntry("[Content_Types].xml"))
                val jpgContentType = if (hasImages) {
                    "\n    <Default Extension=\"jpg\" ContentType=\"image/jpeg\"/>"
                } else ""
                val contentTypesXml = """<?xml version="1.0" encoding="UTF-8" standalone="yes"?>
<Types xmlns="http://schemas.openxmlformats.org/package/2006/content-types">
    <Default Extension="rels" ContentType="application/vnd.openxmlformats-package.relationships+xml"/>
    <Default Extension="xml" ContentType="application/xml"/>$jpgContentType
    <Override PartName="/word/document.xml" ContentType="application/vnd.openxmlformats-officedocument.wordprocessingml.document.main+xml"/>
    <Override PartName="/word/styles.xml" ContentType="application/vnd.openxmlformats-officedocument.wordprocessingml.styles+xml"/>
</Types>"""
                zos.write(contentTypesXml.toByteArray(Charsets.UTF_8))
                zos.closeEntry()

                // 2. _rels/.rels
                zos.putNextEntry(ZipEntry("_rels/.rels"))
                val rootRelsXml = """<?xml version="1.0" encoding="UTF-8" standalone="yes"?>
<Relationships xmlns="http://schemas.openxmlformats.org/package/2006/relationships">
    <Relationship Id="rId1" Type="http://schemas.openxmlformats.org/officeDocument/2006/relationships/officeDocument" Target="word/document.xml"/>
</Relationships>"""
                zos.write(rootRelsXml.toByteArray(Charsets.UTF_8))
                zos.closeEntry()

                // 3. Write page images (for scanned / image-only pages)
                var nextRelNum = 2
                val pageRelIdMap = HashMap<Int, String>()
                for (p in pages) {
                    val imgBytes = p.renderedImageJpeg ?: continue
                    val fileName = "page_${p.pageNumber}.jpg"
                    val rId = "rId${nextRelNum++}"
                    imageRels.add(rId to "media/$fileName")
                    pageRelIdMap[p.pageNumber] = rId

                    zos.putNextEntry(ZipEntry("word/media/$fileName"))
                    zos.write(imgBytes)
                    zos.closeEntry()
                }

                // 4. word/_rels/document.xml.rels
                zos.putNextEntry(ZipEntry("word/_rels/document.xml.rels"))
                val docRelsBuilder = StringBuilder()
                docRelsBuilder.append("""<?xml version="1.0" encoding="UTF-8" standalone="yes"?>
<Relationships xmlns="http://schemas.openxmlformats.org/package/2006/relationships">
    <Relationship Id="rId1" Type="http://schemas.openxmlformats.org/officeDocument/2006/relationships/styles" Target="styles.xml"/>""")
                for ((rId, target) in imageRels) {
                    docRelsBuilder.append(
                        "\n    <Relationship Id=\"$rId\" Type=\"http://schemas.openxmlformats.org/officeDocument/2006/relationships/image\" Target=\"$target\"/>"
                    )
                }
                docRelsBuilder.append("\n</Relationships>")
                zos.write(docRelsBuilder.toString().toByteArray(Charsets.UTF_8))
                zos.closeEntry()

                // 5. word/styles.xml
                zos.putNextEntry(ZipEntry("word/styles.xml"))
                val stylesXml = """<?xml version="1.0" encoding="UTF-8" standalone="yes"?>
<w:styles xmlns:w="http://schemas.openxmlformats.org/wordprocessingml/2006/main">
    <w:style w:type="paragraph" w:default="1" w:styleId="Normal">
        <w:name w:val="Normal"/>
        <w:pPr><w:spacing w:after="120" w:line="276" w:lineRule="auto"/></w:pPr>
        <w:rPr>
            <w:rFonts w:ascii="Calibri" w:hAnsi="Calibri" w:cs="Calibri"/>
            <w:sz w:val="22"/>
            <w:color w:val="1E293B"/>
        </w:rPr>
    </w:style>
    <w:style w:type="paragraph" w:styleId="Heading1">
        <w:name w:val="heading 1"/>
        <w:pPr><w:spacing w:before="120" w:after="160"/></w:pPr>
        <w:rPr>
            <w:rFonts w:ascii="Calibri" w:hAnsi="Calibri"/>
            <w:b/>
            <w:sz w:val="32"/>
            <w:color w:val="0F172A"/>
        </w:rPr>
    </w:style>
</w:styles>"""
                zos.write(stylesXml.toByteArray(Charsets.UTF_8))
                zos.closeEntry()

                // 6. word/document.xml
                zos.putNextEntry(ZipEntry("word/document.xml"))
                val docBody = StringBuilder()
                docBody.append("""<?xml version="1.0" encoding="UTF-8" standalone="yes"?>
<w:document xmlns:w="http://schemas.openxmlformats.org/wordprocessingml/2006/main" xmlns:r="http://schemas.openxmlformats.org/officeDocument/2006/relationships" xmlns:wp="http://schemas.openxmlformats.org/drawingml/2006/wordprocessingDrawing" xmlns:a="http://schemas.openxmlformats.org/drawingml/2006/main" xmlns:pic="http://schemas.openxmlformats.org/drawingml/2006/picture">
    <w:body>
""")
                val escapedTitle = escapeXml(stripInvalidXmlChars(title.replace("_", " ")))
                docBody.append("""        <w:p>
            <w:pPr><w:pStyle w:val="Heading1"/></w:pPr>
            <w:r><w:t xml:space="preserve">$escapedTitle</w:t></w:r>
        </w:p>
""")

                var docPrId = 1
                pages.forEachIndexed { pageIdx, pageContent ->
                    if (pages.size > 1) {
                        docBody.append("""        <w:p>
            <w:pPr><w:pBdr><w:bottom w:val="single" w:sz="4" w:space="2" w:color="CBD5E1"/></w:pBdr></w:pPr>
            <w:r><w:rPr><w:b/><w:sz w:val="18"/><w:color w:val="64748B"/></w:rPr><w:t>Page ${pageContent.pageNumber}</w:t></w:r>
        </w:p>
""")
                    }

                    for (para in pageContent.paragraphs) {
                        val safePara = stripInvalidXmlChars(para)
                        if (safePara.isNotBlank()) {
                            val escaped = escapeXml(safePara)
                            docBody.append("""        <w:p>
            <w:r><w:t xml:space="preserve">$escaped</w:t></w:r>
        </w:p>
""")
                        }
                    }

                    val rId = pageRelIdMap[pageContent.pageNumber]
                    if (rId != null && pageContent.imageWidthPx > 0 && pageContent.imageHeightPx > 0) {
                        // Fit cleanly within A4 printable width (approx 5,900,000 EMU = ~6.45 inches)
                        val maxWidthEmu = 5760000L
                        val aspect = pageContent.imageHeightPx.toDouble() / pageContent.imageWidthPx.toDouble()
                        val cx = maxWidthEmu
                        val cy = (maxWidthEmu * aspect).toLong().coerceIn(500000L, 8400000L)
                        val imgId = docPrId++
                        docBody.append("""        <w:p>
            <w:pPr><w:jc w:val="center"/></w:pPr>
            <w:r>
                <w:drawing>
                    <wp:inline distT="0" distB="0" distL="0" distR="0">
                        <wp:extent cx="$cx" cy="$cy"/>
                        <wp:docPr id="$imgId" name="Page_${pageContent.pageNumber}"/>
                        <a:graphic>
                            <a:graphicData uri="http://schemas.openxmlformats.org/drawingml/2006/picture">
                                <pic:pic>
                                    <pic:nvPicPr>
                                        <pic:cNvPr id="$imgId" name="page_${pageContent.pageNumber}.jpg"/>
                                        <pic:cNvPicPr/>
                                    </pic:nvPicPr>
                                    <pic:blipFill>
                                        <a:blip r:embed="$rId"/>
                                        <a:stretch><a:fillRect/></a:stretch>
                                    </pic:blipFill>
                                    <pic:spPr>
                                        <a:xfrm><a:off x="0" y="0"/><a:ext cx="$cx" cy="$cy"/></a:xfrm>
                                        <a:prstGeom prst="rect"><a:avLst/></a:prstGeom>
                                    </pic:spPr>
                                </pic:pic>
                            </a:graphicData>
                        </a:graphic>
                    </wp:inline>
                </w:drawing>
            </w:r>
        </w:p>
""")
                    }

                    if (pageIdx < pages.lastIndex && rId != null) {
                        docBody.append("""        <w:p><w:r><w:br w:type="page"/></w:r></w:p>
""")
                    }
                }

                docBody.append("""        <w:sectPr>
            <w:pgSz w:w="11906" w:h="16838"/>
            <w:pgMar w:top="1440" w:right="1440" w:bottom="1440" w:left="1440"/>
        </w:sectPr>
    </w:body>
</w:document>""")
                zos.write(docBody.toString().toByteArray(Charsets.UTF_8))
                zos.closeEntry()
            }
            true
        } catch (e: Exception) {
            Log.e(TAG, "Error writing .docx file", e)
            false
        }
    }

    private fun stripInvalidXmlChars(str: String): String {
        val sb = StringBuilder(str.length)
        for (ch in str) {
            val code = ch.code
            val valid = code == 0x9 || code == 0xA || code == 0xD ||
                (code in 0x20..0xD7FF) || (code in 0xE000..0xFFFD)
            if (valid) sb.append(ch)
        }
        return sb.toString()
    }

    private fun escapeXml(str: String): String {
        return str.replace("&", "&amp;")
            .replace("<", "&lt;")
            .replace(">", "&gt;")
            .replace("\"", "&quot;")
            .replace("'", "&apos;")
    }

    // ==========================================
    // 5. WATERMARK ENGINE
    // ==========================================

    suspend fun stampWatermarkOnPdf(
        sourcePdf: File,
        outputPdf: File,
        options: WatermarkOptions
    ): Boolean = withContext(Dispatchers.IO) {
        var pfd: ParcelFileDescriptor? = null
        var renderer: PdfRenderer? = null
        val newDoc = PdfDocument()

        try {
            pfd = ParcelFileDescriptor.open(sourcePdf, ParcelFileDescriptor.MODE_READ_ONLY)
            renderer = PdfRenderer(pfd)
            val totalPages = renderer.pageCount

            val watermarkPaint = Paint().apply {
                color = options.colorHex.toInt()
                alpha = (options.opacity * 255).toInt().coerceIn(10, 240)
                textSize = options.fontSizeSp * 2.2f
                isFakeBoldText = true
                isAntiAlias = true
                textAlign = Paint.Align.CENTER
            }

            for (pageIdx in 0 until totalPages) {
                val page = renderer.openPage(pageIdx)
                val w = page.width
                val h = page.height

                val scale = if (maxOf(w, h) > 1600) 1 else 2
                val pageBmp = Bitmap.createBitmap(w * scale, h * scale, Bitmap.Config.ARGB_8888)
                pageBmp.eraseColor(Color.WHITE)
                page.render(pageBmp, null, null, PdfRenderer.Page.RENDER_MODE_FOR_PRINT)
                page.close()

                val pageInfo = PdfDocument.PageInfo.Builder(w, h, pageIdx + 1).create()
                val newPage = newDoc.startPage(pageInfo)
                val canvas = newPage.canvas
                canvas.drawColor(Color.WHITE)

                canvas.drawBitmap(pageBmp, null, Rect(0, 0, w, h), null)
                pageBmp.recycle()

                if (options.applyToAllPages || pageIdx == 0) {
                    canvas.save()
                    if (options.isTiledPattern) {
                        canvas.rotate(-options.rotationAngle, w / 2f, h / 2f)
                        val stepX = (w * 0.45f).coerceAtLeast(150f)
                        val stepY = (h * 0.22f).coerceAtLeast(120f)
                        for (x in (-w).toInt()..(w * 2).toInt() step stepX.toInt()) {
                            for (y in (-h).toInt()..(h * 2).toInt() step stepY.toInt()) {
                                canvas.drawText(options.text, x.toFloat(), y.toFloat(), watermarkPaint)
                            }
                        }
                    } else {
                        canvas.rotate(-options.rotationAngle, w / 2f, h / 2f)
                        canvas.drawText(options.text, w / 2f, h / 2f, watermarkPaint)
                    }
                    canvas.restore()
                }

                newDoc.finishPage(newPage)
            }

            FileOutputStream(outputPdf).use { fos ->
                newDoc.writeTo(fos)
            }
            true
        } catch (e: Exception) {
            Log.e(TAG, "Error stamping watermark", e)
            false
        } finally {
            try { newDoc.close() } catch (ignored: Exception) {}
            try { renderer?.close() } catch (ignored: Exception) {}
            try { pfd?.close() } catch (ignored: Exception) {}
        }
    }

    // ==========================================
    // 6. PDF TO TXT & TXT TO PDF
    // ==========================================

    suspend fun convertPdfToTxt(
        pdfFile: File,
        outputTxtFile: File,
        docTitle: String = "Exported Text"
    ): Boolean = withContext(Dispatchers.IO) {
        try {
            val pages = PdfTextExtractor.extractPages(pdfFile)
            val sb = StringBuilder()
            sb.appendLine(docTitle.replace("_", " "))
            sb.appendLine("=".repeat(minOf(48, docTitle.length.coerceAtLeast(12))))
            sb.appendLine()

            if (pages.isEmpty()) {
                sb.appendLine("No selectable text layer was detected in this PDF.")
            } else {
                for (page in pages) {
                    if (pages.size > 1) {
                        sb.appendLine("--- Page ${page.pageNumber} ---")
                        sb.appendLine()
                    }
                    if (page.paragraphs.isNotEmpty()) {
                        for (para in page.paragraphs) {
                            sb.appendLine(para)
                            sb.appendLine()
                        }
                    } else {
                        sb.appendLine("[Scanned/Image Page ${page.pageNumber}: no selectable text layer]")
                        sb.appendLine()
                    }
                }
            }

            outputTxtFile.writeText(sb.toString().trimEnd() + "\n", Charsets.UTF_8)
            true
        } catch (e: Exception) {
            Log.e(TAG, "Error converting PDF to TXT", e)
            false
        }
    }

    suspend fun convertTxtToPdf(
        context: Context,
        txtUri: Uri,
        outputFile: File,
        docTitle: String = "Text Document"
    ): Boolean = withContext(Dispatchers.IO) {
        try {
            val paragraphs = mutableListOf<WordParagraph>()
            context.contentResolver.openInputStream(txtUri)?.use { input ->
                BufferedReader(InputStreamReader(input, Charsets.UTF_8)).use { reader ->
                    var line: String? = reader.readLine()
                    while (line != null) {
                        val trimmed = line.trimEnd()
                        if (trimmed.isNotBlank()) {
                            paragraphs.add(WordParagraph(text = trimmed))
                        }
                        line = reader.readLine()
                    }
                }
            }
            if (paragraphs.isEmpty()) {
                paragraphs.add(WordParagraph(text = "(Empty text file)"))
            }
            renderParagraphsToPdf(paragraphs, outputFile, docTitle)
            true
        } catch (e: Exception) {
            Log.e(TAG, "Error converting TXT to PDF", e)
            false
        }
    }
}

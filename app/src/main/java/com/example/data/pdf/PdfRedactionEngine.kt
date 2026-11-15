package com.example.data.pdf

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.RectF
import android.graphics.pdf.PdfRenderer
import android.os.ParcelFileDescriptor
import com.example.data.local.AnnotationEntity
import com.tom_roush.pdfbox.pdmodel.PDDocument
import com.tom_roush.pdfbox.pdmodel.PDDocumentInformation
import com.tom_roush.pdfbox.pdmodel.PDPage
import com.tom_roush.pdfbox.pdmodel.PDPageContentStream
import com.tom_roush.pdfbox.pdmodel.common.PDRectangle
import com.tom_roush.pdfbox.pdmodel.graphics.image.LosslessFactory
import com.tom_roush.pdfbox.text.PDFTextStripper
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File

/**
 * Permanent PDF Redaction & Metadata Sanitization Engine.
 *
 * Security model:
 * 1. Captures any embedded text tokens located inside normalized REDACT regions prior to processing.
 * 2. Scrubs PDDocumentInformation (Author, Title, Subject, Keywords, Creator, Producer, Dates),
 *    XMP catalog metadata, AcroForm interactive fields, and page annotations on redacted pages.
 * 3. Completely replaces each page containing REDACT annotations with a fresh PDPage containing only
 *    a flattened rasterized image where the redaction rectangles have already been painted solid black,
 *    so original text operators, vector drawings, annotations, and underlying layers do not exist in
 *    the saved PDF file.
 * 4. Programmatically verifies the saved output file using PDFBox + PdfTextExtractor to confirm
 *    metadata is scrubbed, annotations are removed, and pre-redaction text is unrecoverable.
 */
object PdfRedactionEngine {

    data class RedactionResult(
        val success: Boolean,
        val verifiedUnrecoverable: Boolean,
        val outputFile: File? = null,
        val redactedPages: List<Int> = emptyList(),
        val boxesApplied: Int = 0,
        val verifiedSnippetsCleared: Int = 0,
        val metadataScrubbed: Boolean = false,
        val errorMessage: String? = null
    )

    suspend fun applyPermanentRedaction(
        context: Context,
        sourceFile: File,
        outputFile: File,
        annotations: List<AnnotationEntity>
    ): RedactionResult = withContext(Dispatchers.IO) {
        if (!sourceFile.exists() || sourceFile.length() == 0L) {
            return@withContext RedactionResult(
                success = false,
                verifiedUnrecoverable = false,
                errorMessage = "Source PDF file is missing or empty."
            )
        }

        val redactionsByPage = annotations
            .filter { it.type == "REDACT" }
            .groupBy { it.pageIndex }

        if (redactionsByPage.isEmpty()) {
            return@withContext RedactionResult(
                success = false,
                verifiedUnrecoverable = false,
                errorMessage = "No redaction regions marked on this document."
            )
        }

        PdfBoxInitializer.ensureInitialized(context)

        // 1. Collect pre-redaction text snippets inside every marked redaction box for verification
        val preRedactionSnippetsByPage = mutableMapOf<Int, List<String>>()
        for ((pageIdx, pageBoxes) in redactionsByPage) {
            val layout = PdfTextLayoutEngine.extractPageLayout(context, sourceFile, pageIdx)
            if (layout.isSelectable) {
                val pageTokens = mutableListOf<String>()
                for (box in pageBoxes) {
                    val left = box.posX
                    val top = box.posY
                    val right = box.posX + (if (box.width > 0f) box.width else 0.25f)
                    val bottom = box.posY + (if (box.height > 0f) box.height else 0.04f)
                    val inside = layout.glyphs.filter { g ->
                        !g.ch.isWhitespace() &&
                            g.right >= left && g.left <= right &&
                            g.bottom >= top && g.top <= bottom
                    }.joinToString("") { it.ch.toString() }.trim()
                    if (inside.length >= 3) {
                        pageTokens.add(inside)
                    }
                }
                if (pageTokens.isNotEmpty()) {
                    preRedactionSnippetsByPage[pageIdx] = pageTokens
                }
            }
        }

        // 2. Render sanitized pages & rebuild clean PDF without underlying content streams on redacted pages
        var pfd: ParcelFileDescriptor? = null
        var renderer: PdfRenderer? = null
        val tempFile = File(outputFile.parentFile ?: context.cacheDir, ".redact_tmp_${System.currentTimeMillis()}.pdf")

        try {
            PDDocument.load(sourceFile).use { srcDoc ->
                if (srcDoc.isEncrypted) {
                    return@withContext RedactionResult(
                        success = false,
                        verifiedUnrecoverable = false,
                        errorMessage = "Document is encrypted. Decrypt it before applying permanent redaction."
                    )
                }

                pfd = ParcelFileDescriptor.open(sourceFile, ParcelFileDescriptor.MODE_READ_ONLY)
                renderer = PdfRenderer(pfd)
                val totalPages = renderer.pageCount

                PDDocument().use { outDoc ->
                    val emptyInfo = PDDocumentInformation()
                    outDoc.documentInformation = emptyInfo
                    outDoc.documentCatalog.metadata = null
                    outDoc.documentCatalog.acroForm = null

                    val nonRedactByPage = annotations
                        .filter { it.type != "REDACT" }
                        .groupBy { it.pageIndex }

                    for (pageIdx in 0 until totalPages) {
                        val pageRedactions = redactionsByPage[pageIdx]
                        if (pageRedactions.isNullOrEmpty()) {
                            // Keep unredacted pages with scrubbed annotations
                            val imported = outDoc.importPage(srcDoc.getPage(pageIdx))
                            imported.annotations = emptyList()
                        } else {
                            // Render clean rasterized image of page -> paint permanent opaque black boxes -> fresh PDPage
                            val pdfPage = renderer.openPage(pageIdx)
                            val pageW = pdfPage.width.coerceAtLeast(1)
                            val pageH = pdfPage.height.coerceAtLeast(1)
                            val scale = if (maxOf(pageW, pageH) > 1200) 2 else 3
                            val bmpW = (pageW * scale).coerceAtMost(3200)
                            val bmpH = (pageH * scale).coerceAtMost(3200)

                            val bitmap = Bitmap.createBitmap(bmpW, bmpH, Bitmap.Config.ARGB_8888)
                            val canvas = Canvas(bitmap)
                            canvas.drawColor(Color.WHITE)
                            pdfPage.render(bitmap, null, null, PdfRenderer.Page.RENDER_MODE_FOR_PRINT)
                            pdfPage.close()

                            // Bake other annotations on that page if any (optional visual consistency)
                            val otherAnns = nonRedactByPage[pageIdx].orEmpty()
                            if (otherAnns.isNotEmpty()) {
                                val tempPaint = Paint(Paint.ANTI_ALIAS_FLAG)
                                for (ann in otherAnns) {
                                    if (ann.type == "WHITEOUT") {
                                        tempPaint.color = Color.WHITE
                                        val rx = ann.posX * bmpW
                                        val ry = ann.posY * bmpH
                                        val rw = (if (ann.width > 0f) ann.width else 0.25f) * bmpW
                                        val rh = (if (ann.height > 0f) ann.height else 0.04f) * bmpH
                                        canvas.drawRect(rx, ry, rx + rw, ry + rh, tempPaint)
                                    }
                                }
                            }

                            // Permanently overwrite redacted pixels in bitmap
                            val redactPaint = Paint().apply {
                                color = Color.BLACK
                                style = Paint.Style.FILL
                            }
                            for (box in pageRedactions) {
                                val x = (box.posX.coerceIn(0f, 1f)) * bmpW
                                val y = (box.posY.coerceIn(0f, 1f)) * bmpH
                                val w = (if (box.width > 0f) box.width else 0.25f) * bmpW
                                val h = (if (box.height > 0f) box.height else 0.04f) * bmpH
                                canvas.drawRect(RectF(x, y, x + w, y + h), redactPaint)
                            }

                            val pdPage = PDPage(PDRectangle(pageW.toFloat(), pageH.toFloat()))
                            outDoc.addPage(pdPage)
                            val pdImage = LosslessFactory.createFromImage(outDoc, bitmap)
                            bitmap.recycle()

                            PDPageContentStream(outDoc, pdPage).use { cs ->
                                cs.drawImage(pdImage, 0f, 0f, pageW.toFloat(), pageH.toFloat())
                            }
                        }
                    }

                    outDoc.save(tempFile)
                }
            }

            try { renderer?.close() } catch (_: Exception) {}
            try { pfd?.close() } catch (_: Exception) {}
            renderer = null
            pfd = null

            // 3. Programmatic unrecoverability verification BEFORE finalizing outputFile
            val verification = verifySanitizedPdf(
                context = context,
                sanitizedFile = tempFile,
                redactedPageIndices = redactionsByPage.keys.toList(),
                preRedactionSnippetsByPage = preRedactionSnippetsByPage
            )

            if (!verification.verifiedUnrecoverable) {
                tempFile.delete()
                return@withContext verification.copy(
                    success = false,
                    outputFile = null,
                    errorMessage = verification.errorMessage ?: "Sanitization verification failed."
                )
            }

            outputFile.parentFile?.mkdirs()
            if (outputFile.exists()) outputFile.delete()
            if (!tempFile.renameTo(outputFile)) {
                tempFile.copyTo(outputFile, overwrite = true)
                tempFile.delete()
            }

            val totalBoxes = redactionsByPage.values.sumOf { it.size }
            verification.copy(
                success = true,
                verifiedUnrecoverable = true,
                outputFile = outputFile,
                redactedPages = redactionsByPage.keys.sorted(),
                boxesApplied = totalBoxes
            )
        } catch (e: Exception) {
            tempFile.delete()
            RedactionResult(
                success = false,
                verifiedUnrecoverable = false,
                errorMessage = e.localizedMessage ?: "Redaction sanitization failed."
            )
        } finally {
            try { renderer?.close() } catch (_: Exception) {}
            try { pfd?.close() } catch (_: Exception) {}
        }
    }

    /**
     * Programmatically inspects the generated PDF to ensure redacted pages contain ZERO
     * recoverable text operators, ZERO annotations, and scrubbed document metadata.
     */
    fun verifySanitizedPdf(
        context: Context,
        sanitizedFile: File,
        redactedPageIndices: List<Int>,
        preRedactionSnippetsByPage: Map<Int, List<String>>
    ): RedactionResult {
        if (!sanitizedFile.exists() || sanitizedFile.length() == 0L) {
            return RedactionResult(
                success = false,
                verifiedUnrecoverable = false,
                errorMessage = "Sanitized output file was not written."
            )
        }

        PdfBoxInitializer.ensureInitialized(context)

        return try {
            PDDocument.load(sanitizedFile).use { doc ->
                val info = doc.documentInformation
                val hasResidualInfo = !info.author.isNullOrBlank() ||
                    !info.title.isNullOrBlank() ||
                    !info.subject.isNullOrBlank() ||
                    !info.keywords.isNullOrBlank() ||
                    !info.creator.isNullOrBlank()
                val hasResidualXmp = doc.documentCatalog.metadata != null
                val metadataScrubbed = !hasResidualInfo && !hasResidualXmp

                if (!metadataScrubbed) {
                    return@use RedactionResult(
                        success = false,
                        verifiedUnrecoverable = false,
                        metadataScrubbed = false,
                        errorMessage = "Verification failed: residual metadata still present."
                    )
                }

                var snippetsCleared = 0
                for (pageIdx in redactedPageIndices) {
                    if (pageIdx !in 0 until doc.numberOfPages) continue
                    val page = doc.getPage(pageIdx)
                    if (page.annotations.isNotEmpty()) {
                        return@use RedactionResult(
                            success = false,
                            verifiedUnrecoverable = false,
                            metadataScrubbed = true,
                            errorMessage = "Verification failed: interactive annotations remain on page ${pageIdx + 1}."
                        )
                    }

                    val stripper = PDFTextStripper().apply {
                        startPage = pageIdx + 1
                        endPage = pageIdx + 1
                    }
                    val extractedPdfBoxText = stripper.getText(doc).trim()
                    if (extractedPdfBoxText.isNotEmpty()) {
                        return@use RedactionResult(
                            success = false,
                            verifiedUnrecoverable = false,
                            metadataScrubbed = true,
                            errorMessage = "Verification failed: residual text layer detected on page ${pageIdx + 1}."
                        )
                    }

                    // Cross-check custom PdfTextExtractor stream parser too
                    val customExtracted = PdfTextExtractor.extractPages(sanitizedFile)
                        .getOrNull(pageIdx)?.fullText?.trim().orEmpty()
                    if (customExtracted.isNotEmpty()) {
                        return@use RedactionResult(
                            success = false,
                            verifiedUnrecoverable = false,
                            metadataScrubbed = true,
                            errorMessage = "Verification failed: stream text recoverable on page ${pageIdx + 1}."
                        )
                    }

                    val expectedCleared = preRedactionSnippetsByPage[pageIdx].orEmpty()
                    snippetsCleared += expectedCleared.size
                }

                RedactionResult(
                    success = true,
                    verifiedUnrecoverable = true,
                    outputFile = sanitizedFile,
                    redactedPages = redactedPageIndices.sorted(),
                    verifiedSnippetsCleared = snippetsCleared,
                    metadataScrubbed = true
                )
            }
        } catch (e: Exception) {
            RedactionResult(
                success = false,
                verifiedUnrecoverable = false,
                errorMessage = "Verification inspection error: ${e.localizedMessage}"
            )
        }
    }
}

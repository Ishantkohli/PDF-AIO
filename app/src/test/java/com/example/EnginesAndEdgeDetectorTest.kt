package com.example

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Path
import android.graphics.PointF
import androidx.test.core.app.ApplicationProvider
import com.example.data.pdf.MyersDiff
import com.example.data.pdf.PdfBoxInitializer
import com.example.data.pdf.PdfExportEngine
import com.example.data.scan.DocumentEdgeDetector
import com.tom_roush.pdfbox.pdmodel.PDDocument
import com.tom_roush.pdfbox.pdmodel.PDPage
import com.tom_roush.pdfbox.pdmodel.PDPageContentStream
import com.tom_roush.pdfbox.pdmodel.font.PDType1Font
import com.tom_roush.pdfbox.text.PDFTextStripper
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.io.File

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class EnginesAndEdgeDetectorTest {

    private lateinit var context: Context
    private lateinit var tempDir: File

    @Before
    fun setUp() {
        context = ApplicationProvider.getApplicationContext()
        PdfBoxInitializer.ensureInitialized(context)
        tempDir = File(context.cacheDir, "engines_${System.nanoTime()}").apply { mkdirs() }
    }

    @After
    fun tearDown() {
        tempDir.deleteRecursively()
    }

    @Test
    fun myersDiff_computesAccurateLineOperationsAndReport() {
        val docA = listOf("Header Clause", "Amount: $100", "Footer Notes")
        val docB = listOf("Header Clause", "Amount: $150", "Footer Notes", "Appendix A")

        val summary = MyersDiff.computeLineDiff(docA, docB)
        assertEquals(2, summary.equalCount)
        assertEquals(1, summary.removedCount)
        assertEquals(2, summary.addedCount)
        assertEquals(3, summary.changePositions.size)

        val report = MyersDiff.generateComparisonReport(
            nameA = "Draft.pdf",
            nameB = "Final.pdf",
            pageCountA = 2,
            pageCountB = 3,
            summary = summary,
            pixelDiffPercent = 12.5
        )
        assertTrue(report.contains("- Amount: $100"))
        assertTrue(report.contains("+ Amount: $150"))
        assertTrue(report.contains("+1 added"))
        assertTrue(report.contains("12.50%"))
    }

    @Test
    fun myersDiff_pixelDiffBitmapDetectsVarianceAndIdenticalFrames() {
        val bmpA = Bitmap.createBitmap(40, 40, Bitmap.Config.ARGB_8888)
        val bmpB = Bitmap.createBitmap(40, 40, Bitmap.Config.ARGB_8888)
        val pixelsA = IntArray(40 * 40) { Color.WHITE }
        val pixelsB = IntArray(40 * 40) { idx ->
            val x = idx % 40
            val y = idx / 40
            if (x < 20 && y < 20) Color.BLACK else Color.WHITE
        }
        bmpA.setPixels(pixelsA, 0, 40, 0, 0, 40, 40)
        bmpB.setPixels(pixelsB, 0, 40, 0, 0, 40, 40)

        try {
            val (identicalBmp, identicalPct) = MyersDiff.computePixelDiffBitmap(bmpA, bmpA)
            identicalBmp.recycle()
            assertEquals(0.0, identicalPct, 0.001)

            val (diffBmp, diffPct) = MyersDiff.computePixelDiffBitmap(bmpA, bmpB)
            diffBmp.recycle()
            assertEquals(25.0, diffPct, 0.5)
        } finally {
            bmpA.recycle()
            bmpB.recycle()
        }
    }

    @Test
    fun documentEdgeDetector_detectsTiltedPaperAndRejectsBlankFrameAndTracksStability() {
        // Synthetic 320x420 dark desk with tilted white paper quad filled deterministically via pixels
        val w = 320
        val h = 420
        val desk = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
        val poly = listOf(
            PointF(52f, 64f),
            PointF(270f, 80f),
            PointF(282f, 360f),
            PointF(42f, 348f)
        )
        val deskBg = Color.rgb(22, 26, 34)
        val textColor = Color.rgb(40, 40, 40)
        val pixels = IntArray(w * h) { deskBg }
        for (y in 0 until h) {
            for (x in 0 until w) {
                if (pointInPolygon(x.toFloat(), y.toFloat(), poly)) {
                    val isTextBand = (y in 115..305) && ((y - 115) % 28 < 4) && (x in 80..230)
                    pixels[y * w + x] = if (isTextBand) textColor else Color.WHITE
                }
            }
        }
        desk.setPixels(pixels, 0, w, 0, 0, w, h)

        val blank = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888).apply {
            setPixels(IntArray(w * h) { Color.WHITE }, 0, w, 0, 0, w, h)
        }

        try {
            val detection = DocumentEdgeDetector.detectFromBitmap(desk)
            assertNotNull("Expected valid paper quad on high-contrast desk", detection.quad)
            assertTrue(detection.areaFraction in 0.16f..0.96f)
            assertTrue(detection.confidenceScore >= 0.25f)

            val blankResult = DocumentEdgeDetector.detectFromBitmap(blank)
            assertNull(blankResult.quad)

            // Temporal stability + cooldown test
            val tracker = DocumentEdgeDetector.QuadStabilityTracker(
                requiredStableFrames = 5,
                maxCornerDrift = 0.04f,
                autoCaptureCooldownMs = 2200L
            )
            var t = 10_000L
            var capturedOnFrame = -1
            for (frame in 1..6) {
                val update = tracker.update(detection, autoCaptureEnabled = true, nowMs = t)
                if (update.shouldAutoCapture) capturedOnFrame = frame
                t += 150L
            }
            assertEquals(5, capturedOnFrame)
            // Next frame within cooldown should NOT re-trigger immediately
            val cooldownUpdate = tracker.update(detection, autoCaptureEnabled = true, nowMs = t)
            assertFalse(cooldownUpdate.shouldAutoCapture)
        } finally {
            desk.recycle()
            blank.recycle()
        }
    }

    private fun pointInPolygon(x: Float, y: Float, poly: List<PointF>): Boolean {
        var inside = false
        var j = poly.lastIndex
        for (i in poly.indices) {
            val xi = poly[i].x
            val yi = poly[i].y
            val xj = poly[j].x
            val yj = poly[j].y
            val intersect = ((yi > y) != (yj > y)) &&
                (x < (xj - xi) * (y - yi) / ((yj - yi).takeIf { it != 0f } ?: 1e-6f) + xi)
            if (intersect) inside = !inside
            j = i
        }
        return inside
    }

    @Test
    fun pdfExportEngine_parsesRangesAndSplitsMultiPagePdfWithTextPreserved() = runBlocking {
        val parsed = PdfExportEngine.parseSplitRanges("1-2, 3, 5-4", totalPages = 5)
        assertEquals(listOf(listOf(0, 1), listOf(2), listOf(4, 3)), parsed)

        val chunks = PdfExportEngine.buildChunkSplitGroups(totalPages = 5, pagesPerPart = 2)
        assertEquals(listOf(listOf(0, 1), listOf(2, 3), listOf(4)), chunks)

        val srcPdf = File(tempDir, "four_pages.pdf")
        PDDocument().use { doc ->
            for (p in 1..4) {
                val page = PDPage()
                doc.addPage(page)
                PDPageContentStream(doc, page).use { cs ->
                    cs.beginText()
                    cs.setFont(PDType1Font.HELVETICA, 12f)
                    cs.newLineAtOffset(72f, 700f)
                    cs.showText("PageMarker_$p")
                    cs.endText()
                }
            }
            doc.save(srcPdf)
        }

        val splitDir = File(tempDir, "split_out").apply { mkdirs() }
        val parts = PdfExportEngine.splitPdf(
            context = context,
            sourceFile = srcPdf,
            outputDir = splitDir,
            baseTitle = "Manual",
            pageGroups = listOf(listOf(0, 1), listOf(2, 3))
        )
        assertEquals(2, parts.size)

        PDDocument.load(parts[0]).use { p1 ->
            assertEquals(2, p1.numberOfPages)
            val txt = PDFTextStripper().getText(p1)
            assertTrue(txt.contains("PageMarker_1"))
            assertTrue(txt.contains("PageMarker_2"))
            assertFalse(txt.contains("PageMarker_3"))
        }
        PDDocument.load(parts[1]).use { p2 ->
            assertEquals(2, p2.numberOfPages)
            val txt = PDFTextStripper().getText(p2)
            assertTrue(txt.contains("PageMarker_3"))
            assertTrue(txt.contains("PageMarker_4"))
        }

        // Corrupted PDF resilience
        val corrupt = File(tempDir, "corrupt.pdf").apply { writeText("INVALID_PDF_BYTES") }
        val emptySplit = PdfExportEngine.splitPdf(
            context = context,
            sourceFile = corrupt,
            outputDir = splitDir,
            baseTitle = "Broken",
            pageGroups = listOf(listOf(0))
        )
        assertTrue(emptySplit.isEmpty())
    }
}

package com.example

import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.Color
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.example.data.local.DocumentEntity
import com.example.data.local.PdfDatabase
import com.example.data.local.StorageRenameCoordinator
import com.example.data.pdf.PdfBoxInitializer
import com.example.data.pdf.PdfTextLayoutEngine
import com.example.data.pdf.ShareCoordinator
import com.tom_roush.pdfbox.pdmodel.PDDocument
import com.tom_roush.pdfbox.pdmodel.PDPage
import com.tom_roush.pdfbox.pdmodel.PDPageContentStream
import com.tom_roush.pdfbox.pdmodel.font.PDType1Font
import com.tom_roush.pdfbox.pdmodel.graphics.image.LosslessFactory
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.io.File

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class PdfLayoutRenameShareTest {

    private lateinit var context: Context
    private lateinit var tempDir: File
    private lateinit var db: PdfDatabase

    @Before
    fun setUp() {
        context = ApplicationProvider.getApplicationContext()
        PdfBoxInitializer.ensureInitialized(context)
        tempDir = File(context.cacheDir, "test_suite_${System.nanoTime()}").apply { mkdirs() }
        db = Room.inMemoryDatabaseBuilder(context, PdfDatabase::class.java)
            .allowMainThreadQueries()
            .build()
    }

    @After
    fun tearDown() {
        db.close()
        tempDir.deleteRecursively()
    }

    @Test
    fun pdfTextLayoutEngine_extractsGlyphsAndExpandsWordSentenceParagraph() {
        val pdfFile = createTextPdf(
            File(tempDir, "selectable.pdf"),
            listOf(
                "Hello PDF World. Second sentence here!",
                "",
                "Paragraph two starts cleanly."
            )
        )

        val layout = PdfTextLayoutEngine.extractPageLayout(context, pdfFile, 0)
        assertTrue(layout.isSelectable)
        assertTrue(layout.fullText.contains("Hello PDF World."))
        assertTrue(layout.glyphs.isNotEmpty())

        val firstAlpha = layout.glyphs.first { it.ch == 'P' }
        val hitIdx = PdfTextLayoutEngine.findNearestGlyphIndex(
            layout,
            (firstAlpha.left + firstAlpha.right) * 0.5f,
            (firstAlpha.top + firstAlpha.bottom) * 0.5f
        )
        assertNotNull(hitIdx)

        val wordRange = PdfTextLayoutEngine.expandToWord(layout, hitIdx!!)
        assertEquals("PDF", PdfTextLayoutEngine.getSelectedText(layout, wordRange))

        val sentenceRange = PdfTextLayoutEngine.expandToSentence(layout, hitIdx)
        val sentenceText = PdfTextLayoutEngine.getSelectedText(layout, sentenceRange)
        assertTrue(sentenceText.contains("Hello PDF World"))
        assertFalse(sentenceText.contains("Second sentence"))

        val paraRange = PdfTextLayoutEngine.expandToParagraph(layout, hitIdx)
        val paraText = PdfTextLayoutEngine.getSelectedText(layout, paraRange)
        assertTrue(paraText.contains("Second sentence here!"))
        assertFalse(paraText.contains("Paragraph two"))

        val rects = PdfTextLayoutEngine.getSelectionHighlightRects(layout, sentenceRange)
        assertTrue(rects.isNotEmpty())
        assertTrue(rects.all { it.left in 0f..1f && it.right >= it.left })
    }

    @Test
    fun pdfTextLayoutEngine_marksImageOnlyPdfAsNonSelectableHonestly() {
        val pdfFile = File(tempDir, "scanned_image_only.pdf")
        val bmp = Bitmap.createBitmap(120, 160, Bitmap.Config.ARGB_8888).apply {
            eraseColor(Color.WHITE)
        }
        try {
            PDDocument().use { doc ->
                val page = PDPage()
                doc.addPage(page)
                val img = LosslessFactory.createFromImage(doc, bmp)
                PDPageContentStream(doc, page).use { cs ->
                    cs.drawImage(img, 50f, 500f, 200f, 200f)
                }
                doc.save(pdfFile)
            }
        } finally {
            bmp.recycle()
        }

        val layout = PdfTextLayoutEngine.extractPageLayout(context, pdfFile, 0)
        assertFalse(layout.isSelectable)
        assertTrue(layout.glyphs.isEmpty())
        assertTrue(layout.unavailableReason?.contains("image-only", ignoreCase = true) == true)
    }

    @Test
    fun storageRenameCoordinator_validatesAndRenamesFileAndRoomSynchronously() = runBlocking {
        assertTrue(StorageRenameCoordinator.validateFileName("   ") is StorageRenameCoordinator.ValidationResult.Invalid)
        assertTrue(StorageRenameCoordinator.validateFileName("..") is StorageRenameCoordinator.ValidationResult.Invalid)
        assertTrue(StorageRenameCoordinator.validateFileName("bad/name.pdf") is StorageRenameCoordinator.ValidationResult.Invalid)
        assertTrue(StorageRenameCoordinator.validateFileName("a".repeat(125)) is StorageRenameCoordinator.ValidationResult.Invalid)

        val valid = StorageRenameCoordinator.validateFileName("Quarterly Report.pdf")
        assertTrue(valid is StorageRenameCoordinator.ValidationResult.Valid)
        assertEquals("Quarterly Report", (valid as StorageRenameCoordinator.ValidationResult.Valid).baseName)
        assertEquals("Quarterly Report.pdf", valid.fullFileName)

        val oldFile = createTextPdf(File(tempDir, "OldContract.pdf"), listOf("Signed Agreement"))
        val expectedBytes = oldFile.readBytes()

        val dao = db.pdfDao()
        val id = dao.insertDocument(
            DocumentEntity(
                uriString = android.net.Uri.fromFile(oldFile).toString(),
                title = "OldContract",
                filePath = oldFile.absolutePath,
                pageCount = 1,
                fileSizeBytes = oldFile.length()
            )
        )
        val doc = dao.getDocumentByIdSync(id)!!

        // Insert collision target
        val otherFile = createTextPdf(File(tempDir, "CollisionDoc.pdf"), listOf("Other"))
        dao.insertDocument(
            DocumentEntity(
                uriString = android.net.Uri.fromFile(otherFile).toString(),
                title = "CollisionDoc",
                filePath = otherFile.absolutePath,
                pageCount = 1,
                fileSizeBytes = otherFile.length()
            )
        )

        val collision = StorageRenameCoordinator.renameDocument(context, dao, doc, "CollisionDoc")
        assertTrue(collision is StorageRenameCoordinator.RenameResult.NameCollision)
        assertTrue(oldFile.exists())

        val result = StorageRenameCoordinator.renameDocument(context, dao, doc, "Final_Contract_v2")
        assertTrue(result is StorageRenameCoordinator.RenameResult.Success)
        val updated = (result as StorageRenameCoordinator.RenameResult.Success).updatedDoc

        val newFile = File(tempDir, "Final_Contract_v2.pdf")
        assertTrue(newFile.exists())
        assertFalse(oldFile.exists())
        assertArrayEquals(expectedBytes, newFile.readBytes())
        assertEquals("Final_Contract_v2", updated.title)
        assertEquals(newFile.absolutePath, updated.filePath)
        assertEquals("Final_Contract_v2", dao.getDocumentByIdSync(id)?.title)
    }

    @Test
    fun storageRenameCoordinator_reportsProviderDeniedWithoutCorruptingLocalCopy() = runBlocking {
        val localCache = createTextPdf(File(tempDir, "ExternalCached.pdf"), listOf("SAF copy"))
        val dao = db.pdfDao()
        val id = dao.insertDocument(
            DocumentEntity(
                uriString = android.net.Uri.fromFile(localCache).toString(),
                title = "ExternalCached",
                filePath = localCache.absolutePath,
                pageCount = 1,
                sourceUri = "content://com.android.externalstorage.documents/document/primary%3ADocs%2FExternalCached.pdf"
            )
        )
        val doc = dao.getDocumentByIdSync(id)!!

        val outcome = StorageRenameCoordinator.renameDocument(context, dao, doc, "RenamedCopy")
        assertTrue(outcome is StorageRenameCoordinator.RenameResult.ProviderDenied)
        val denied = outcome as StorageRenameCoordinator.RenameResult.ProviderDenied
        assertEquals("RenamedCopy.pdf", denied.suggestedFileName)
        assertTrue(localCache.exists())
        assertEquals("ExternalCached", dao.getDocumentByIdSync(id)?.title)
    }

    @Test
    fun shareCoordinator_preparesAllFourFormatsWithCleanFilenamesAndFileProviderUri() = runBlocking {
        val sourcePdf = createTextPdf(
            File(tempDir, "Budget_2026.pdf"),
            listOf("Annual Operating Budget 2026", "Total Revenue: 120000")
        )
        val doc = DocumentEntity(
            id = 42L,
            uriString = android.net.Uri.fromFile(sourcePdf).toString(),
            title = "Budget_2026",
            filePath = sourcePdf.absolutePath,
            pageCount = 1,
            fileSizeBytes = sourcePdf.length()
        )

        // 1. Original PDF
        val origRes = ShareCoordinator.prepareShare(
            context = context,
            doc = doc,
            option = ShareCoordinator.ShareOption.PDF_ORIGINAL_NAME,
            annotations = emptyList(),
            activeWatermark = null
        )
        assertTrue(origRes.isSuccess)
        val orig = origRes.getOrThrow()
        assertEquals("Budget_2026.pdf", orig.stagedFile.name)
        assertEquals("application/pdf", orig.mimeType)
        assertArrayEquals(sourcePdf.readBytes(), orig.stagedFile.readBytes())
        assertTrue(orig.contentUri.toString().startsWith("content://${context.packageName}.fileprovider/"))

        val chooserIntent = ShareCoordinator.buildShareIntent(context, orig)
        assertEquals(Intent.ACTION_CHOOSER, chooserIntent.action)
        val innerIntent = chooserIntent.getParcelableExtra(Intent.EXTRA_INTENT, Intent::class.java)
        assertNotNull(innerIntent)
        assertNotNull(innerIntent?.clipData)
        assertEquals("Budget_2026.pdf", innerIntent?.getStringExtra(Intent.EXTRA_SUBJECT))
        assertTrue((innerIntent!!.flags and Intent.FLAG_GRANT_READ_URI_PERMISSION) != 0)

        // 2. Custom PDF Name + invalid rejection
        val invalidCustom = ShareCoordinator.prepareShare(
            context = context,
            doc = doc,
            option = ShareCoordinator.ShareOption.PDF_CUSTOM_NAME,
            annotations = emptyList(),
            activeWatermark = null,
            customPdfName = "bad/file*name"
        )
        assertTrue(invalidCustom.isFailure)

        val customRes = ShareCoordinator.prepareShare(
            context = context,
            doc = doc,
            option = ShareCoordinator.ShareOption.PDF_CUSTOM_NAME,
            annotations = emptyList(),
            activeWatermark = null,
            customPdfName = "Client_Invoice_May"
        )
        assertTrue(customRes.isSuccess)
        assertEquals("Client_Invoice_May.pdf", customRes.getOrThrow().stagedFile.name)

        // 3. Plain TXT
        val txtRes = ShareCoordinator.prepareShare(
            context = context,
            doc = doc,
            option = ShareCoordinator.ShareOption.TXT_PLAIN,
            annotations = emptyList(),
            activeWatermark = null
        )
        assertTrue(txtRes.isSuccess)
        val txt = txtRes.getOrThrow()
        assertEquals("Budget_2026.txt", txt.stagedFile.name)
        assertEquals("text/plain", txt.mimeType)
        assertTrue(txt.stagedFile.readText().contains("Annual Operating Budget"))

        // 4. Word DOCX
        val docxRes = ShareCoordinator.prepareShare(
            context = context,
            doc = doc,
            option = ShareCoordinator.ShareOption.DOCX_WORD,
            annotations = emptyList(),
            activeWatermark = null
        )
        assertTrue(docxRes.isSuccess)
        val docx = docxRes.getOrThrow()
        assertEquals("Budget_2026.docx", docx.stagedFile.name)
        val header = docx.stagedFile.readBytes().take(2)
        assertEquals('P'.code.toByte(), header[0])
        assertEquals('K'.code.toByte(), header[1])

        // Staging expiry cleanup
        val staleDir = File(File(context.cacheDir, "share_staging"), "stale_folder").apply {
            mkdirs()
            setLastModified(System.currentTimeMillis() - 10_000_000L)
        }
        ShareCoordinator.cleanupExpiredStaging(context, maxAgeMs = 1_000L)
        assertFalse(staleDir.exists())
        assertTrue(docx.stagedFile.exists())
    }

    private fun createTextPdf(file: File, lines: List<String>): File {
        PDDocument().use { doc ->
            val page = PDPage()
            doc.addPage(page)
            PDPageContentStream(doc, page).use { cs ->
                var y = 740f
                for (line in lines) {
                    if (line.isNotEmpty()) {
                        cs.beginText()
                        cs.setFont(PDType1Font.HELVETICA, 12f)
                        cs.newLineAtOffset(72f, y)
                        cs.showText(line)
                        cs.endText()
                    }
                    y -= 24f
                }
            }
            doc.save(file)
        }
        return file
    }
}

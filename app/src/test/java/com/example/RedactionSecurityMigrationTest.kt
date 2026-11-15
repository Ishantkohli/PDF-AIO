package com.example

import android.content.Context
import androidx.room.Room
import androidx.sqlite.db.SupportSQLiteDatabase
import androidx.sqlite.db.SupportSQLiteOpenHelper
import androidx.sqlite.db.framework.FrameworkSQLiteOpenHelperFactory
import androidx.test.core.app.ApplicationProvider
import com.example.data.local.AnnotationEntity
import com.example.data.local.BookmarkEntity
import com.example.data.local.PdfDatabase
import com.example.data.pdf.PdfBoxInitializer
import com.example.data.pdf.PdfRedactionEngine
import com.example.data.pdf.PdfSecurityEngine
import com.tom_roush.pdfbox.pdmodel.PDDocument
import com.tom_roush.pdfbox.pdmodel.PDDocumentInformation
import com.tom_roush.pdfbox.pdmodel.PDPage
import com.tom_roush.pdfbox.pdmodel.PDPageContentStream
import com.tom_roush.pdfbox.pdmodel.font.PDType1Font
import com.tom_roush.pdfbox.pdmodel.interactive.annotation.PDAnnotationText
import com.tom_roush.pdfbox.text.PDFTextStripper
import kotlinx.coroutines.runBlocking
import org.junit.After
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
class RedactionSecurityMigrationTest {

    private lateinit var context: Context
    private lateinit var tempDir: File

    @Before
    fun setUp() {
        context = ApplicationProvider.getApplicationContext()
        PdfBoxInitializer.ensureInitialized(context)
        tempDir = File(context.cacheDir, "sec_mig_${System.nanoTime()}").apply { mkdirs() }
    }

    @After
    fun tearDown() {
        tempDir.deleteRecursively()
    }

    @Test
    fun pdfSecurityEngine_encryptsDecryptsAndRoundTripsMetadataLocally() = runBlocking {
        val sourcePdf = File(tempDir, "plain.pdf")
        PDDocument().use { doc ->
            val page = PDPage()
            doc.addPage(page)
            PDPageContentStream(doc, page).use { cs ->
                cs.beginText()
                cs.setFont(PDType1Font.HELVETICA, 12f)
                cs.newLineAtOffset(72f, 700f)
                cs.showText("Confidential Financial Terms")
                cs.endText()
            }
            doc.save(sourcePdf)
        }

        val updatedMetaFile = File(tempDir, "with_meta.pdf")
        val metaWrite = PdfSecurityEngine.updateMetadata(
            context = context,
            sourceFile = sourcePdf,
            outputFile = updatedMetaFile,
            title = "Q4 Audit Report",
            author = "Ishant Kohli",
            subject = "Finance",
            keywords = "audit, confidential"
        )
        assertTrue(metaWrite.isSuccess)

        val metaRead = PdfSecurityEngine.readMetadata(context, updatedMetaFile)
        assertTrue(metaRead.isSuccess)
        val info = metaRead.getOrThrow()
        assertEquals("Q4 Audit Report", info.title)
        assertEquals("Ishant Kohli", info.author)
        assertEquals("Finance", info.subject)
        assertEquals("audit, confidential", info.keywords)
        assertEquals(1, info.pageCount)
        assertFalse(info.isEncrypted)

        val encryptedFile = File(tempDir, "encrypted.pdf")
        val encRes = PdfSecurityEngine.encryptPdf(
            context = context,
            sourceFile = updatedMetaFile,
            outputFile = encryptedFile,
            userPassword = "Secret@530",
            allowPrinting = true,
            allowCopying = false,
            keyLengthBits = 256
        )
        assertTrue(encRes.isSuccess)
        assertTrue(encryptedFile.length() > 0L)

        // Wrong password must fail cleanly
        val decryptedWrong = File(tempDir, "wrong_dec.pdf")
        val wrongRes = PdfSecurityEngine.decryptPdf(context, encryptedFile, "WrongPass", decryptedWrong)
        assertTrue(wrongRes.isFailure)
        assertTrue(wrongRes.exceptionOrNull()?.message?.contains("Incorrect password") == true)

        // Locked inspection reports isEncrypted == true
        val lockedInfo = PdfSecurityEngine.readMetadata(context, encryptedFile)
        assertTrue(lockedInfo.isSuccess)
        assertTrue(lockedInfo.getOrThrow().isEncrypted)

        // Right password decrypts cleanly and preserves text
        val decryptedFile = File(tempDir, "decrypted.pdf")
        val rightRes = PdfSecurityEngine.decryptPdf(context, encryptedFile, "Secret@530", decryptedFile)
        assertTrue(rightRes.isSuccess)
        PDDocument.load(decryptedFile).use { unlocked ->
            assertFalse(unlocked.isEncrypted)
            val text = PDFTextStripper().getText(unlocked)
            assertTrue(text.contains("Confidential Financial Terms"))
        }
    }

    @Test
    fun pdfRedactionEngine_verifySanitizedPdfRejectsResidualTextAnnotationsAndMetadata() {
        val unsanitizedPdf = File(tempDir, "unsanitized.pdf")
        PDDocument().use { doc ->
            doc.documentInformation = PDDocumentInformation().apply {
                author = "Leaked Author"
                title = "Internal Secret"
            }
            val page = PDPage()
            doc.addPage(page)
            page.annotations.add(PDAnnotationText().apply { contents = "Hidden comment" })
            PDPageContentStream(doc, page).use { cs ->
                cs.beginText()
                cs.setFont(PDType1Font.HELVETICA, 12f)
                cs.newLineAtOffset(72f, 700f)
                cs.showText("SSN-123-45-6789")
                cs.endText()
            }
            doc.save(unsanitizedPdf)
        }

        // Verification MUST reject an unsanitized PDF with metadata/annotations/text
        val rejectCheck = PdfRedactionEngine.verifySanitizedPdf(
            context = context,
            sanitizedFile = unsanitizedPdf,
            redactedPageIndices = listOf(0),
            preRedactionSnippetsByPage = mapOf(0 to listOf("SSN-123-45-6789"))
        )
        assertFalse(rejectCheck.verifiedUnrecoverable)
        assertFalse(rejectCheck.metadataScrubbed)

        // Now verify a genuinely scrubbed image-only PDF passes verification
        val scrubbedPdf = File(tempDir, "scrubbed.pdf")
        PDDocument().use { doc ->
            doc.documentInformation = PDDocumentInformation()
            doc.documentCatalog.metadata = null
            doc.addPage(PDPage())
            doc.save(scrubbedPdf)
        }
        val passCheck = PdfRedactionEngine.verifySanitizedPdf(
            context = context,
            sanitizedFile = scrubbedPdf,
            redactedPageIndices = listOf(0),
            preRedactionSnippetsByPage = mapOf(0 to listOf("SSN-123-45-6789"))
        )
        assertTrue(passCheck.verifiedUnrecoverable)
        assertTrue(passCheck.metadataScrubbed)
        assertEquals(1, passCheck.verifiedSnippetsCleared)
    }

    @Test
    fun pdfRedactionEngine_handlesInvalidOrEmptyRedactionsSafely() = runBlocking {
        val samplePdf = File(tempDir, "sample_redact.pdf")
        PDDocument().use { doc ->
            val page = PDPage()
            doc.addPage(page)
            PDPageContentStream(doc, page).use { cs ->
                cs.beginText()
                cs.setFont(PDType1Font.HELVETICA, 12f)
                cs.newLineAtOffset(72f, 700f)
                cs.showText("SSN-123-45-6789")
                cs.endText()
            }
            doc.save(samplePdf)
        }

        val noBoxesResult = PdfRedactionEngine.applyPermanentRedaction(
            context = context,
            sourceFile = samplePdf,
            outputFile = File(tempDir, "out_none.pdf"),
            annotations = emptyList()
        )
        assertFalse(noBoxesResult.success)

        val encryptedFile = File(tempDir, "enc_redact.pdf")
        PdfSecurityEngine.encryptPdf(context, samplePdf, encryptedFile, "lock123")
        val redactAnn = AnnotationEntity(
            documentId = 1L,
            pageIndex = 0,
            type = "REDACT",
            posX = 0.05f,
            posY = 0.05f,
            width = 0.5f,
            height = 0.1f
        )
        val encOutcome = PdfRedactionEngine.applyPermanentRedaction(
            context = context,
            sourceFile = encryptedFile,
            outputFile = File(tempDir, "out_enc.pdf"),
            annotations = listOf(redactAnn)
        )
        assertFalse(encOutcome.success)
    }

    @Test
    fun roomMigration1To2_preservesExistingDocumentsAndAnnotationsAndCreatesBookmarks() = runBlocking {
        val dbName = "migration_test_${System.nanoTime()}.db"
        val dbFile = context.getDatabasePath(dbName)
        dbFile.parentFile?.mkdirs()

        // Create and populate exact v1 database schema manually via SupportSQLiteOpenHelper
        val config = SupportSQLiteOpenHelper.Configuration.builder(context)
            .name(dbName)
            .callback(object : SupportSQLiteOpenHelper.Callback(1) {
                override fun onCreate(db: SupportSQLiteDatabase) {
                    db.execSQL(
                        """
                        CREATE TABLE IF NOT EXISTS documents (
                            id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                            uriString TEXT NOT NULL,
                            title TEXT NOT NULL,
                            filePath TEXT NOT NULL,
                            pageCount INTEGER NOT NULL,
                            lastReadPage INTEGER NOT NULL,
                            readingMode TEXT NOT NULL,
                            colorFilter TEXT NOT NULL,
                            isFavorite INTEGER NOT NULL,
                            isSample INTEGER NOT NULL,
                            lastOpenedTimestamp INTEGER NOT NULL,
                            fileSizeBytes INTEGER NOT NULL
                        )
                        """.trimIndent()
                    )
                    db.execSQL(
                        """
                        CREATE TABLE IF NOT EXISTS annotations (
                            id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                            documentId INTEGER NOT NULL,
                            pageIndex INTEGER NOT NULL,
                            type TEXT NOT NULL,
                            pointsJson TEXT NOT NULL,
                            colorHex INTEGER NOT NULL,
                            strokeWidth REAL NOT NULL,
                            posX REAL NOT NULL,
                            posY REAL NOT NULL,
                            width REAL NOT NULL,
                            height REAL NOT NULL,
                            textContent TEXT NOT NULL,
                            timestamp INTEGER NOT NULL
                        )
                        """.trimIndent()
                    )
                    db.execSQL(
                        """
                        CREATE TABLE IF NOT EXISTS signatures (
                            id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                            title TEXT NOT NULL,
                            pathsJson TEXT NOT NULL,
                            createdTimestamp INTEGER NOT NULL
                        )
                        """.trimIndent()
                    )
                    db.execSQL(
                        """
                        INSERT INTO documents (id, uriString, title, filePath, pageCount, lastReadPage, readingMode, colorFilter, isFavorite, isSample, lastOpenedTimestamp, fileSizeBytes)
                        VALUES (10, 'file:///tmp/legacy.pdf', 'LegacyDoc', '/tmp/legacy.pdf', 12, 4, 'HORIZONTAL', 'SEPIA', 1, 0, 1700000000000, 4096)
                        """.trimIndent()
                    )
                    db.execSQL(
                        """
                        INSERT INTO annotations (id, documentId, pageIndex, type, pointsJson, colorHex, strokeWidth, posX, posY, width, height, textContent, timestamp)
                        VALUES (25, 10, 4, 'STICKY_NOTE', '', -16776961, 4.0, 0.2, 0.3, 0.1, 0.05, 'Important clause', 1700000001000)
                        """.trimIndent()
                    )
                }

                override fun onUpgrade(db: SupportSQLiteDatabase, oldVersion: Int, newVersion: Int) {}
            })
            .build()

        FrameworkSQLiteOpenHelperFactory().create(config).writableDatabase.close()

        val migratedDb = Room.databaseBuilder(context, PdfDatabase::class.java, dbName)
            .addMigrations(PdfDatabase.MIGRATION_1_2)
            .allowMainThreadQueries()
            .build()

        try {
            val dao = migratedDb.pdfDao()
            val migratedDoc = dao.getDocumentByIdSync(10L)
            assertNotNull(migratedDoc)
            assertEquals("LegacyDoc", migratedDoc!!.title)
            assertEquals(4, migratedDoc.lastReadPage)
            assertTrue(migratedDoc.isFavorite)
            assertFalse(migratedDoc.isPinned)
            assertEquals("General", migratedDoc.folderCategory)

            val anns = dao.getAllAnnotationsForDoc(10L)
            assertEquals(1, anns.size)
            assertEquals("Important clause", anns[0].textContent)
            assertEquals(1.0f, anns[0].opacity, 0.001f)

            val bookmarkId = dao.insertBookmark(
                BookmarkEntity(documentId = 10L, pageIndex = 4, label = "Key Clause")
            )
            assertTrue(bookmarkId > 0L)
            val bookmarks = dao.getBookmarksForDocSync(10L)
            assertEquals(1, bookmarks.size)
            assertEquals("Key Clause", bookmarks[0].label)
        } finally {
            migratedDb.close()
            context.deleteDatabase(dbName)
        }
    }
}

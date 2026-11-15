package com.example.data.pdf

import android.content.ClipData
import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.core.content.FileProvider
import com.example.data.local.AnnotationEntity
import com.example.data.local.DocumentEntity
import com.example.data.local.StorageRenameCoordinator
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.util.UUID

/**
 * Rebuilt sharing subsystem fixing the `editingDocument.pdf` / `Edited_<ts>_<ts>` bug.
 * Decouples internal working files from staged user-facing share filenames so Android's
 * FileProvider URI, ClipData, OpenableColumns.DISPLAY_NAME, and MIME type reflect the
 * exact user-visible or custom filename cleanly.
 */
object ShareCoordinator {

    enum class ShareOption {
        PDF_ORIGINAL_NAME,
        TXT_PLAIN,
        DOCX_WORD,
        PDF_CUSTOM_NAME
    }

    data class PreparedShare(
        val stagedFile: File,
        val contentUri: Uri,
        val mimeType: String,
        val displayName: String
    )

    private const val STAGING_DIR_NAME = "share_staging"
    private const val MAX_STAGE_AGE_MS = 2 * 60 * 60 * 1000L // 2 hours

    fun cleanupExpiredStaging(context: Context, maxAgeMs: Long = MAX_STAGE_AGE_MS) {
        val stagingRoot = File(context.cacheDir, STAGING_DIR_NAME)
        if (!stagingRoot.exists()) return
        val cutoff = System.currentTimeMillis() - maxAgeMs
        stagingRoot.listFiles()?.forEach { child ->
            if (child.lastModified() < cutoff) {
                child.deleteRecursively()
            }
        }
    }

    suspend fun prepareShare(
        context: Context,
        doc: DocumentEntity,
        option: ShareOption,
        annotations: List<AnnotationEntity>,
        activeWatermark: DocumentConverterEngine.WatermarkOptions?,
        customPdfName: String? = null
    ): Result<PreparedShare> = withContext(Dispatchers.IO) {
        cleanupExpiredStaging(context)

        val sourceFile = File(doc.filePath)
        if (!sourceFile.exists() || sourceFile.length() == 0L) {
            return@withContext Result.failure(IllegalStateException("Source PDF file does not exist."))
        }

        val cleanBaseName = StorageRenameCoordinator.sanitizeForStorageFileName(doc.title, fallback = "Document")
        val token = UUID.randomUUID().toString().replace("-", "").take(8)
        val stagingDir = File(File(context.cacheDir, STAGING_DIR_NAME), token).apply { mkdirs() }

        return@withContext try {
            when (option) {
                ShareOption.PDF_ORIGINAL_NAME -> {
                    val fileName = "$cleanBaseName.pdf"
                    val stagedFile = File(stagingDir, fileName)
                    val ok = writePdfForShare(sourceFile, stagedFile, annotations, activeWatermark)
                    if (!ok || !stagedFile.exists() || stagedFile.length() == 0L) {
                        return@withContext Result.failure(IllegalStateException("Failed to prepare PDF for sharing."))
                    }
                    Result.success(buildPrepared(context, stagedFile, "application/pdf", fileName))
                }

                ShareOption.PDF_CUSTOM_NAME -> {
                    val validation = StorageRenameCoordinator.validateFileName(customPdfName.orEmpty())
                    if (validation is StorageRenameCoordinator.ValidationResult.Invalid) {
                        return@withContext Result.failure(IllegalArgumentException(validation.reason))
                    }
                    val valid = validation as StorageRenameCoordinator.ValidationResult.Valid
                    val stagedFile = File(stagingDir, valid.fullFileName)
                    val ok = writePdfForShare(sourceFile, stagedFile, annotations, activeWatermark)
                    if (!ok || !stagedFile.exists() || stagedFile.length() == 0L) {
                        return@withContext Result.failure(IllegalStateException("Failed to prepare custom-named PDF for sharing."))
                    }
                    Result.success(buildPrepared(context, stagedFile, "application/pdf", valid.fullFileName))
                }

                ShareOption.TXT_PLAIN -> {
                    val fileName = "$cleanBaseName.txt"
                    val stagedFile = File(stagingDir, fileName)
                    val ok = DocumentConverterEngine.convertPdfToTxt(
                        pdfFile = sourceFile,
                        outputTxtFile = stagedFile,
                        docTitle = cleanBaseName,
                        context = context
                    )
                    if (!ok || !stagedFile.exists() || stagedFile.length() == 0L) {
                        return@withContext Result.failure(IllegalStateException("Failed to extract plain text (.txt) from PDF."))
                    }
                    Result.success(buildPrepared(context, stagedFile, "text/plain", fileName))
                }

                ShareOption.DOCX_WORD -> {
                    val fileName = "$cleanBaseName.docx"
                    val stagedFile = File(stagingDir, fileName)
                    val ok = DocumentConverterEngine.convertPdfToWord(
                        context = context,
                        pdfFile = sourceFile,
                        outputDocxFile = stagedFile,
                        docTitle = cleanBaseName
                    )
                    if (!ok || !stagedFile.exists() || stagedFile.length() == 0L) {
                        return@withContext Result.failure(IllegalStateException("Failed to generate Word (.docx) file from PDF."))
                    }
                    Result.success(
                        buildPrepared(
                            context,
                            stagedFile,
                            "application/vnd.openxmlformats-officedocument.wordprocessingml.document",
                            fileName
                        )
                    )
                }
            }
        } catch (e: Exception) {
            stagingDir.deleteRecursively()
            Result.failure(e)
        }
    }

    fun buildShareIntent(context: Context, prepared: PreparedShare): Intent {
        val sendIntent = Intent(Intent.ACTION_SEND).apply {
            type = prepared.mimeType
            putExtra(Intent.EXTRA_STREAM, prepared.contentUri)
            putExtra(Intent.EXTRA_SUBJECT, prepared.displayName)
            putExtra(Intent.EXTRA_TITLE, prepared.displayName)
            clipData = ClipData.newUri(context.contentResolver, prepared.displayName, prepared.contentUri)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        return Intent.createChooser(sendIntent, "Share ${prepared.displayName}")
    }

    private fun buildPrepared(
        context: Context,
        stagedFile: File,
        mimeType: String,
        displayName: String
    ): PreparedShare {
        val contentUri = FileProvider.getUriForFile(
            context,
            "${context.packageName}.fileprovider",
            stagedFile
        )
        return PreparedShare(
            stagedFile = stagedFile,
            contentUri = contentUri,
            mimeType = mimeType,
            displayName = displayName
        )
    }

    private suspend fun writePdfForShare(
        sourceFile: File,
        stagedFile: File,
        annotations: List<AnnotationEntity>,
        activeWatermark: DocumentConverterEngine.WatermarkOptions?
    ): Boolean {
        val hasAnnotations = annotations.isNotEmpty()
        val hasWatermark = activeWatermark != null

        // Fast path: unmodified document — preserve original PDF bytes cleanly without re-rasterizing
        if (!hasAnnotations && !hasWatermark) {
            FileInputStream(sourceFile).use { input ->
                FileOutputStream(stagedFile).use { output ->
                    input.copyTo(output)
                }
            }
            return stagedFile.exists() && stagedFile.length() > 0L
        }

        if (hasAnnotations && !hasWatermark) {
            return PdfExportEngine.exportAnnotatedPdf(sourceFile, annotations, stagedFile)
        }

        if (!hasAnnotations && hasWatermark) {
            return DocumentConverterEngine.stampWatermarkOnPdf(sourceFile, stagedFile, activeWatermark!!)
        }

        val tempAnnotated = File(stagedFile.parentFile, ".tmp_annotated.pdf")
        return try {
            val annOk = PdfExportEngine.exportAnnotatedPdf(sourceFile, annotations, tempAnnotated)
            if (!annOk) return false
            DocumentConverterEngine.stampWatermarkOnPdf(tempAnnotated, stagedFile, activeWatermark!!)
        } finally {
            tempAnnotated.delete()
        }
    }
}

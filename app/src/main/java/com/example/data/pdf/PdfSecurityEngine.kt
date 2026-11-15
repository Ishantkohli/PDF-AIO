package com.example.data.pdf

import android.content.Context
import com.tom_roush.pdfbox.pdmodel.PDDocument
import com.tom_roush.pdfbox.pdmodel.PDDocumentInformation
import com.tom_roush.pdfbox.pdmodel.encryption.AccessPermission
import com.tom_roush.pdfbox.pdmodel.encryption.InvalidPasswordException
import com.tom_roush.pdfbox.pdmodel.encryption.StandardProtectionPolicy
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File

/**
 * 100% Local PDF Security (AES-128 / AES-256 encryption & password removal) and Metadata Inspector/Editor.
 * Powered by Apache PDFBox on-device.
 */
object PdfSecurityEngine {

    data class PdfMetadataInfo(
        val title: String,
        val author: String,
        val subject: String,
        val keywords: String,
        val creator: String,
        val producer: String,
        val pageCount: Int,
        val isEncrypted: Boolean
    )

    suspend fun encryptPdf(
        context: Context,
        sourceFile: File,
        outputFile: File,
        userPassword: String,
        ownerPassword: String = userPassword,
        allowPrinting: Boolean = true,
        allowCopying: Boolean = false,
        keyLengthBits: Int = 256
    ): Result<File> = withContext(Dispatchers.IO) {
        if (!sourceFile.exists() || sourceFile.length() == 0L) {
            return@withContext Result.failure(IllegalStateException("Source PDF file does not exist."))
        }
        if (userPassword.isBlank()) {
            return@withContext Result.failure(IllegalArgumentException("Password cannot be blank."))
        }
        PdfBoxInitializer.ensureInitialized(context)
        val tmpFile = File(outputFile.parentFile ?: context.cacheDir, ".enc_tmp_${System.currentTimeMillis()}.pdf")
        return@withContext try {
            PDDocument.load(sourceFile).use { doc ->
                if (doc.isEncrypted) {
                    return@use Result.failure(IllegalStateException("PDF is already password-protected. Unlock it first."))
                }
                val permissions = AccessPermission().apply {
                    setCanPrint(allowPrinting)
                    setCanPrintFaithful(allowPrinting)
                    setCanExtractContent(allowCopying)
                    setCanExtractForAccessibility(true)
                    setCanModify(false)
                    setCanModifyAnnotations(false)
                }
                val validKeyLen = if (keyLengthBits == 128) 128 else 256
                val policy = StandardProtectionPolicy(ownerPassword, userPassword, permissions).apply {
                    encryptionKeyLength = validKeyLen
                }
                doc.protect(policy)
                doc.save(tmpFile)
                outputFile.parentFile?.mkdirs()
                if (outputFile.exists()) outputFile.delete()
                if (!tmpFile.renameTo(outputFile)) {
                    tmpFile.copyTo(outputFile, overwrite = true)
                    tmpFile.delete()
                }
                Result.success(outputFile)
            }
        } catch (e: Exception) {
            tmpFile.delete()
            Result.failure(e)
        }
    }

    suspend fun decryptPdf(
        context: Context,
        sourceFile: File,
        password: String,
        outputFile: File
    ): Result<File> = withContext(Dispatchers.IO) {
        if (!sourceFile.exists() || sourceFile.length() == 0L) {
            return@withContext Result.failure(IllegalStateException("Source PDF file does not exist."))
        }
        PdfBoxInitializer.ensureInitialized(context)
        val tmpFile = File(outputFile.parentFile ?: context.cacheDir, ".dec_tmp_${System.currentTimeMillis()}.pdf")
        return@withContext try {
            PDDocument.load(sourceFile, password).use { doc ->
                doc.isAllSecurityToBeRemoved = true
                doc.save(tmpFile)
                outputFile.parentFile?.mkdirs()
                if (outputFile.exists()) outputFile.delete()
                if (!tmpFile.renameTo(outputFile)) {
                    tmpFile.copyTo(outputFile, overwrite = true)
                    tmpFile.delete()
                }
                Result.success(outputFile)
            }
        } catch (_: InvalidPasswordException) {
            tmpFile.delete()
            Result.failure(IllegalArgumentException("Incorrect password for this PDF."))
        } catch (e: Exception) {
            tmpFile.delete()
            Result.failure(e)
        }
    }

    suspend fun readMetadata(
        context: Context,
        file: File,
        password: String = ""
    ): Result<PdfMetadataInfo> = withContext(Dispatchers.IO) {
        if (!file.exists() || file.length() == 0L) {
            return@withContext Result.failure(IllegalStateException("PDF file does not exist."))
        }
        PdfBoxInitializer.ensureInitialized(context)
        return@withContext try {
            PDDocument.load(file, password).use { doc ->
                val info = doc.documentInformation
                Result.success(
                    PdfMetadataInfo(
                        title = info.title.orEmpty(),
                        author = info.author.orEmpty(),
                        subject = info.subject.orEmpty(),
                        keywords = info.keywords.orEmpty(),
                        creator = info.creator.orEmpty(),
                        producer = info.producer.orEmpty(),
                        pageCount = doc.numberOfPages,
                        isEncrypted = doc.isEncrypted
                    )
                )
            }
        } catch (_: InvalidPasswordException) {
            Result.success(
                PdfMetadataInfo(
                    title = file.nameWithoutExtension,
                    author = "",
                    subject = "",
                    keywords = "",
                    creator = "",
                    producer = "",
                    pageCount = 0,
                    isEncrypted = true
                )
            )
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun updateMetadata(
        context: Context,
        sourceFile: File,
        outputFile: File,
        title: String,
        author: String,
        subject: String,
        keywords: String
    ): Result<File> = withContext(Dispatchers.IO) {
        if (!sourceFile.exists() || sourceFile.length() == 0L) {
            return@withContext Result.failure(IllegalStateException("Source PDF file does not exist."))
        }
        PdfBoxInitializer.ensureInitialized(context)
        val tmpFile = File(outputFile.parentFile ?: context.cacheDir, ".meta_tmp_${System.currentTimeMillis()}.pdf")
        return@withContext try {
            PDDocument.load(sourceFile).use { doc ->
                if (doc.isEncrypted) {
                    return@use Result.failure(IllegalStateException("Unlock encrypted PDF before editing metadata."))
                }
                val info = doc.documentInformation ?: PDDocumentInformation()
                info.title = title.trim().ifEmpty { null }
                info.author = author.trim().ifEmpty { null }
                info.subject = subject.trim().ifEmpty { null }
                info.keywords = keywords.trim().ifEmpty { null }
                doc.documentInformation = info
                doc.save(tmpFile)

                outputFile.parentFile?.mkdirs()
                if (outputFile.exists()) outputFile.delete()
                if (!tmpFile.renameTo(outputFile)) {
                    tmpFile.copyTo(outputFile, overwrite = true)
                    tmpFile.delete()
                }
                Result.success(outputFile)
            }
        } catch (e: Exception) {
            tmpFile.delete()
            Result.failure(e)
        }
    }
}

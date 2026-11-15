package com.example.data.local

import android.content.ContentValues
import android.content.Context
import android.net.Uri
import android.provider.DocumentsContract
import android.provider.MediaStore
import android.provider.OpenableColumns
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream

/**
 * Coordinates safe filename validation, actual storage rename (local File, SAF DocumentsContract,
 * and MediaStore), collision checks, and transactional Room DB updates.
 * Never claims a rename succeeded unless the verified destination exists with the new filename.
 */
object StorageRenameCoordinator {

    sealed class ValidationResult {
        data class Valid(val baseName: String, val fullFileName: String) : ValidationResult()
        data class Invalid(val reason: String) : ValidationResult()
    }

    sealed class RenameResult {
        data class Success(val updatedDoc: DocumentEntity) : RenameResult()
        data class ValidationError(val reason: String) : RenameResult()
        data class NameCollision(val reason: String) : RenameResult()
        data class ProviderDenied(val reason: String, val suggestedFileName: String) : RenameResult()
        data class Failure(val reason: String) : RenameResult()
    }

    private val illegalCharRegex = Regex("[\\\\/:*?\"<>|\\u0000-\\u001F]")

    fun validateFileName(rawInput: String): ValidationResult {
        val trimmed = rawInput.trim()
        if (trimmed.isEmpty()) {
            return ValidationResult.Invalid("Filename cannot be empty.")
        }

        val withoutPdfExt = if (trimmed.endsWith(".pdf", ignoreCase = true)) {
            trimmed.dropLast(4).trim()
        } else {
            trimmed
        }

        if (withoutPdfExt.isEmpty() || withoutPdfExt == "." || withoutPdfExt == "..") {
            return ValidationResult.Invalid("Please enter a valid document name.")
        }

        if (illegalCharRegex.containsMatchIn(withoutPdfExt)) {
            return ValidationResult.Invalid("Filename cannot contain \\ / : * ? \" < > | or control characters.")
        }

        if (withoutPdfExt.length > 120) {
            return ValidationResult.Invalid("Filename must be 120 characters or fewer.")
        }

        val fullName = "$withoutPdfExt.pdf"
        return ValidationResult.Valid(baseName = withoutPdfExt, fullFileName = fullName)
    }

    fun sanitizeForStorageFileName(rawTitle: String, fallback: String = "Document"): String {
        val stripped = rawTitle.trim()
            .removeSuffix(".pdf")
            .removeSuffix(".PDF")
            .replace(illegalCharRegex, "_")
            .trim('.', ' ')
        return stripped.ifBlank { fallback }
    }

    suspend fun renameDocument(
        context: Context,
        dao: PdfDao,
        doc: DocumentEntity,
        rawNewName: String
    ): RenameResult = withContext(Dispatchers.IO) {
        val validation = validateFileName(rawNewName)
        if (validation is ValidationResult.Invalid) {
            return@withContext RenameResult.ValidationError(validation.reason)
        }
        val valid = validation as ValidationResult.Valid
        val currentBase = doc.title.removeSuffix(".pdf").removeSuffix(".PDF").trim()
        if (valid.baseName == currentBase) {
            return@withContext RenameResult.ValidationError("The new filename is the same as the current name.")
        }

        // Check database title collision first
        val dbCollision = dao.findDocumentByTitleExcluding(valid.baseName, doc.id)
        if (dbCollision != null) {
            return@withContext RenameResult.NameCollision(
                "Another document named \"${valid.fullFileName}\" already exists in your library."
            )
        }

        val sourceFile = File(doc.filePath)
        if (!sourceFile.exists() || sourceFile.length() == 0L) {
            return@withContext RenameResult.Failure("Source PDF file could not be found on disk.")
        }

        val parentDir = sourceFile.parentFile
            ?: return@withContext RenameResult.Failure("Invalid document storage directory.")

        val targetLocalFile = File(parentDir, valid.fullFileName)
        if (targetLocalFile.exists() && targetLocalFile.absolutePath != sourceFile.absolutePath) {
            return@withContext RenameResult.NameCollision(
                "A file named \"${valid.fullFileName}\" already exists in this folder."
            )
        }

        // Step 1: If document originated from an external content:// URI (SAF or MediaStore),
        // rename the actual provider object first. Never fake success if the provider denies it.
        var updatedSourceUriString = doc.sourceUri
        if (doc.sourceUri.isNotBlank()) {
            val extUri = runCatching { Uri.parse(doc.sourceUri) }.getOrNull()
            if (extUri != null && extUri.scheme == "content") {
                val providerOutcome = tryRenameExternalContentUri(context, extUri, valid.fullFileName)
                when (providerOutcome) {
                    is ExternalRenameOutcome.Renamed -> {
                        updatedSourceUriString = providerOutcome.newUri.toString()
                    }
                    is ExternalRenameOutcome.Denied -> {
                        return@withContext RenameResult.ProviderDenied(
                            reason = providerOutcome.reason,
                            suggestedFileName = valid.fullFileName
                        )
                    }
                }
            }
        }

        // Step 2: Rename app-local file safely (never delete original until destination is verified).
        val expectedLength = sourceFile.length()
        val localRenamed = renameLocalFileSafely(sourceFile, targetLocalFile, expectedLength)
        if (!localRenamed) {
            return@withContext RenameResult.Failure(
                "Could not rename file in local storage. Original file was preserved safely."
            )
        }

        // Step 3: Commit Room DB update transactionally.
        val updatedDoc = dao.renameDocumentTransactionally(
            id = doc.id,
            newTitle = valid.baseName,
            newFilePath = targetLocalFile.absolutePath,
            newUriString = Uri.fromFile(targetLocalFile).toString(),
            newSourceUri = updatedSourceUriString
        ) ?: doc.copy(
            title = valid.baseName,
            filePath = targetLocalFile.absolutePath,
            uriString = Uri.fromFile(targetLocalFile).toString(),
            sourceUri = updatedSourceUriString
        )

        RenameResult.Success(updatedDoc)
    }

    suspend fun saveCopyToDestinationUri(
        context: Context,
        sourceFile: File,
        destinationUri: Uri
    ): Boolean = withContext(Dispatchers.IO) {
        if (!sourceFile.exists() || sourceFile.length() == 0L) return@withContext false
        return@withContext try {
            context.contentResolver.openOutputStream(destinationUri, "w")?.use { output ->
                FileInputStream(sourceFile).use { input ->
                    input.copyTo(output)
                }
                output.flush()
            } != null
        } catch (e: Exception) {
            false
        }
    }

    private sealed class ExternalRenameOutcome {
        data class Renamed(val newUri: Uri) : ExternalRenameOutcome()
        data class Denied(val reason: String) : ExternalRenameOutcome()
    }

    private fun tryRenameExternalContentUri(
        context: Context,
        uri: Uri,
        newDisplayName: String
    ): ExternalRenameOutcome {
        val resolver = context.contentResolver
        return try {
            if (DocumentsContract.isDocumentUri(context, uri)) {
                val renamedUri = DocumentsContract.renameDocument(resolver, uri, newDisplayName)
                    ?: return ExternalRenameOutcome.Denied(
                        "The storage provider did not allow renaming this external document."
                    )
                val verifiedName = queryDisplayName(context, renamedUri)
                if (verifiedName != null && verifiedName.equals(newDisplayName, ignoreCase = true)) {
                    ExternalRenameOutcome.Renamed(renamedUri)
                } else {
                    ExternalRenameOutcome.Denied(
                        "Storage provider reported a different filename after rename."
                    )
                }
            } else if (uri.authority == MediaStore.AUTHORITY || uri.toString().contains("media/")) {
                val values = ContentValues().apply {
                    put(MediaStore.MediaColumns.DISPLAY_NAME, newDisplayName)
                }
                val rows = resolver.update(uri, values, null, null)
                if (rows > 0) {
                    val verifiedName = queryDisplayName(context, uri)
                    if (verifiedName != null && verifiedName.equals(newDisplayName, ignoreCase = true)) {
                        ExternalRenameOutcome.Renamed(uri)
                    } else {
                        ExternalRenameOutcome.Denied(
                            "Android MediaStore did not verify the updated filename."
                        )
                    }
                } else {
                    ExternalRenameOutcome.Denied(
                        "Android MediaStore denied modifying this external file (write permission required)."
                    )
                }
            } else {
                ExternalRenameOutcome.Denied(
                    "External content provider (${uri.authority ?: "unknown"}) does not permit direct file renaming."
                )
            }
        } catch (se: SecurityException) {
            ExternalRenameOutcome.Denied(
                "Permission denied by external storage provider. Use 'Save Copy As' to export a renamed PDF."
            )
        } catch (uoe: UnsupportedOperationException) {
            ExternalRenameOutcome.Denied(
                "This document provider does not support renaming in place. Use 'Save Copy As' instead."
            )
        } catch (e: Exception) {
            ExternalRenameOutcome.Denied(
                e.localizedMessage ?: "External provider could not rename this file."
            )
        }
    }

    private fun queryDisplayName(context: Context, uri: Uri): String? {
        return try {
            context.contentResolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)?.use { c ->
                if (c.moveToFirst()) {
                    val idx = c.getColumnIndex(OpenableColumns.DISPLAY_NAME)
                    if (idx != -1) c.getString(idx) else null
                } else null
            }
        } catch (e: Exception) {
            null
        }
    }

    private fun renameLocalFileSafely(source: File, target: File, expectedLength: Long): Boolean {
        if (source.absolutePath == target.absolutePath) return true
        // Fast path: atomic POSIX rename on same filesystem
        if (source.renameTo(target) && target.exists() && target.length() == expectedLength) {
            return true
        }
        // Fallback: copy -> verify byte size -> delete original
        val tempTarget = File(target.parentFile, ".tmp_rename_${System.nanoTime()}.pdf")
        return try {
            FileInputStream(source).use { input ->
                FileOutputStream(tempTarget).use { output ->
                    input.copyTo(output)
                    output.fd.sync()
                }
            }
            if (!tempTarget.exists() || tempTarget.length() != expectedLength) {
                tempTarget.delete()
                return false
            }
            if (tempTarget.renameTo(target) && target.exists() && target.length() == expectedLength) {
                source.delete()
                true
            } else {
                tempTarget.delete()
                false
            }
        } catch (e: Exception) {
            tempTarget.delete()
            false
        }
    }
}

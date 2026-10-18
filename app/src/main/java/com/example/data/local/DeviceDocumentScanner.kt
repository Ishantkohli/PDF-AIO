package com.example.data.local

import android.content.ContentUris
import android.content.Context
import android.net.Uri
import android.os.Build
import android.provider.MediaStore
import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File

data class DeviceFileItem(
    val id: Long,
    val title: String,
    val uri: Uri,
    val path: String,
    val mimeType: String,
    val sizeBytes: Long,
    val dateModified: Long,
    val fileType: DeviceFileType
)

enum class DeviceFileType {
    PDF,
    WORD_DOCX,
    WORD_DOC,
    OTHER
}

object DeviceDocumentScanner {
    private const val TAG = "DeviceDocumentScanner"

    suspend fun scanDeviceDocuments(context: Context): List<DeviceFileItem> = withContext(Dispatchers.IO) {
        val resultList = mutableListOf<DeviceFileItem>()
        val contentResolver = context.contentResolver

        val collectionUri = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            MediaStore.Files.getContentUri(MediaStore.VOLUME_EXTERNAL)
        } else {
            MediaStore.Files.getContentUri("external")
        }

        val projection = arrayOf(
            MediaStore.Files.FileColumns._ID,
            MediaStore.Files.FileColumns.DISPLAY_NAME,
            MediaStore.Files.FileColumns.MIME_TYPE,
            MediaStore.Files.FileColumns.SIZE,
            MediaStore.Files.FileColumns.DATE_MODIFIED,
            MediaStore.Files.FileColumns.DATA
        )

        // Query for PDF and Word files
        val selection = "${MediaStore.Files.FileColumns.MIME_TYPE} = ? OR " +
                "${MediaStore.Files.FileColumns.MIME_TYPE} = ? OR " +
                "${MediaStore.Files.FileColumns.MIME_TYPE} = ? OR " +
                "${MediaStore.Files.FileColumns.DISPLAY_NAME} LIKE ? OR " +
                "${MediaStore.Files.FileColumns.DISPLAY_NAME} LIKE ? OR " +
                "${MediaStore.Files.FileColumns.DISPLAY_NAME} LIKE ?"

        val selectionArgs = arrayOf(
            "application/pdf",
            "application/vnd.openxmlformats-officedocument.wordprocessingml.document",
            "application/msword",
            "%.pdf",
            "%.docx",
            "%.doc"
        )

        val sortOrder = "${MediaStore.Files.FileColumns.DATE_MODIFIED} DESC"

        try {
            contentResolver.query(collectionUri, projection, selection, selectionArgs, sortOrder)?.use { cursor ->
                val idCol = cursor.getColumnIndexOrThrow(MediaStore.Files.FileColumns._ID)
                val nameCol = cursor.getColumnIndexOrThrow(MediaStore.Files.FileColumns.DISPLAY_NAME)
                val mimeCol = cursor.getColumnIndexOrThrow(MediaStore.Files.FileColumns.MIME_TYPE)
                val sizeCol = cursor.getColumnIndexOrThrow(MediaStore.Files.FileColumns.SIZE)
                val dateCol = cursor.getColumnIndexOrThrow(MediaStore.Files.FileColumns.DATE_MODIFIED)
                val dataCol = cursor.getColumnIndex(MediaStore.Files.FileColumns.DATA)

                while (cursor.moveToNext()) {
                    val id = cursor.getLong(idCol)
                    val name = cursor.getString(nameCol) ?: "Document_$id"
                    val mime = cursor.getString(mimeCol) ?: ""
                    val size = cursor.getLong(sizeCol)
                    val dateModified = cursor.getLong(dateCol)
                    val path = if (dataCol >= 0) cursor.getString(dataCol) ?: "" else ""

                    val contentUri = ContentUris.withAppendedId(collectionUri, id)

                    val type = when {
                        name.endsWith(".pdf", ignoreCase = true) || mime == "application/pdf" -> DeviceFileType.PDF
                        name.endsWith(".docx", ignoreCase = true) || mime.contains("wordprocessingml") -> DeviceFileType.WORD_DOCX
                        name.endsWith(".doc", ignoreCase = true) || mime == "application/msword" -> DeviceFileType.WORD_DOC
                        else -> DeviceFileType.OTHER
                    }

                    resultList.add(
                        DeviceFileItem(
                            id = id,
                            title = name,
                            uri = contentUri,
                            path = path,
                            mimeType = mime,
                            sizeBytes = size,
                            dateModified = dateModified * 1000L,
                            fileType = type
                        )
                    )
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error scanning device documents via MediaStore", e)
        }

        // Also check app internal files directory for downloaded or created documents
        try {
            val internalFiles = context.filesDir.listFiles() ?: emptyArray()
            for (f in internalFiles) {
                if (f.isFile && (f.name.endsWith(".pdf", true) || f.name.endsWith(".docx", true))) {
                    val type = if (f.name.endsWith(".pdf", true)) DeviceFileType.PDF else DeviceFileType.WORD_DOCX
                    resultList.add(
                        DeviceFileItem(
                            id = f.hashCode().toLong(),
                            title = f.name,
                            uri = Uri.fromFile(f),
                            path = f.absolutePath,
                            mimeType = if (type == DeviceFileType.PDF) "application/pdf" else "application/vnd.openxmlformats-officedocument.wordprocessingml.document",
                            sizeBytes = f.length(),
                            dateModified = f.lastModified(),
                            fileType = type
                        )
                    )
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error scanning internal documents", e)
        }

        resultList.distinctBy { it.title }
    }
}

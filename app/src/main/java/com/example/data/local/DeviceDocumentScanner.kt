package com.example.data.local

import android.content.ContentUris
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.DocumentsContract
import android.provider.MediaStore
import android.provider.Settings
import android.util.Log
import androidx.core.content.ContextCompat
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
    EXCEL,
    POWERPOINT,
    TEXT,
    OTHER
}

/**
 * Universal Whole-Device Document Scanner
 * Scans entire Internal and External Storage (SD cards, USB OTG, Downloads, Documents, Messaging apps).
 * Supports MANAGE_EXTERNAL_STORAGE (Android 11-15) and READ_EXTERNAL_STORAGE (Android 5-10),
 * as well as SAF DocumentTree folder scanning and robust MediaStore queries.
 */
object DeviceDocumentScanner {
    private const val TAG = "DeviceDocumentScanner"

    /**
     * Checks if the app has permission to scan the entire device storage.
     */
    fun hasStoragePermission(context: Context): Boolean {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            try {
                Environment.isExternalStorageManager()
            } catch (e: Exception) {
                false
            }
        } else {
            ContextCompat.checkSelfPermission(
                context,
                android.Manifest.permission.READ_EXTERNAL_STORAGE
            ) == PackageManager.PERMISSION_GRANTED
        }
    }

    /**
     * Returns the intent to request full storage access (All Files Access on Android 11+).
     */
    fun getManageStorageIntent(context: Context): Intent {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            try {
                Intent(Settings.ACTION_MANAGE_APP_ALL_FILES_ACCESS_PERMISSION).apply {
                    data = Uri.parse("package:${context.packageName}")
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                }
            } catch (e: Exception) {
                try {
                    Intent(Settings.ACTION_MANAGE_ALL_FILES_ACCESS_PERMISSION).apply {
                        addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                    }
                } catch (e2: Exception) {
                    Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
                        data = Uri.parse("package:${context.packageName}")
                        addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                    }
                }
            }
        } else {
            Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
                data = Uri.parse("package:${context.packageName}")
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
        }
    }

    /**
     * Deep scans the entire phone: internal storage, SD cards, Downloads, Documents,
     * WhatsApp, Telegram, and MediaStore.
     */
    suspend fun scanDeviceDocuments(context: Context): List<DeviceFileItem> = withContext(Dispatchers.IO) {
        val resultList = mutableListOf<DeviceFileItem>()
        val visitedKeys = HashSet<String>()

        val hasFullPerm = hasStoragePermission(context)

        // 1. Direct Whole Storage Walk (Primary Internal Storage & Secondary SD cards)
        if (hasFullPerm || Build.VERSION.SDK_INT < Build.VERSION_CODES.Q) {
            try {
                // A. Primary Internal Storage Root (e.g. /storage/emulated/0, /sdcard)
                val primaryStorage = Environment.getExternalStorageDirectory()
                if (primaryStorage != null && primaryStorage.exists() && primaryStorage.canRead()) {
                    scanDirectoryRecursively(primaryStorage, resultList, visitedKeys, maxDepth = 6)
                }

                // B. External SD Cards & Secondary Storage Volumes under /storage
                val storageRoot = File("/storage")
                if (storageRoot.exists() && storageRoot.canRead()) {
                    val volumes = storageRoot.listFiles() ?: emptyArray()
                    for (vol in volumes) {
                        if (vol.isDirectory && vol.name != "emulated" && vol.name != "self" && !vol.name.startsWith(".")) {
                            scanDirectoryRecursively(vol, resultList, visitedKeys, maxDepth = 6)
                        }
                    }
                }
            } catch (e: Exception) {
                Log.w(TAG, "Direct storage walk exception: ${e.message}")
            }
        }

        // 2. Specific Public and App directories (Downloads, Documents, WhatsApp, Telegram)
        val targetedDirs = listOfNotNull(
            Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS),
            Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOCUMENTS),
            File("/storage/emulated/0/Download"),
            File("/storage/emulated/0/Downloads"),
            File("/storage/emulated/0/Documents"),
            File("/storage/emulated/0/Document"),
            File("/storage/emulated/0/PDF"),
            File("/storage/emulated/0/Android/media/com.whatsapp/WhatsApp/Media/WhatsApp Documents"),
            File("/storage/emulated/0/WhatsApp/Media/WhatsApp Documents"),
            File("/storage/emulated/0/Telegram/Telegram Documents"),
            File("/storage/emulated/0/Bluetooth"),
            context.getExternalFilesDir(null),
            context.filesDir
        )

        for (dir in targetedDirs) {
            try {
                if (dir.exists() && dir.canRead()) {
                    scanDirectoryRecursively(dir, resultList, visitedKeys, maxDepth = 4)
                }
            } catch (e: Exception) {
                Log.w(TAG, "Targeted dir scan failed for ${dir.path}: ${e.message}")
            }
        }

        // 3. MediaStore Files Query (Finds any indexed files across device without SQL functions)
        try {
            val filesUri = MediaStore.Files.getContentUri("external")
            queryMediaStoreSafely(context, filesUri, resultList, visitedKeys)
        } catch (e: Exception) {
            Log.w(TAG, "MediaStore.Files query failed: ${e.message}")
        }

        // 4. MediaStore Downloads Query on Android 10+
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            try {
                val downloadsUri = MediaStore.Downloads.getContentUri(MediaStore.VOLUME_EXTERNAL)
                queryMediaStoreSafely(context, downloadsUri, resultList, visitedKeys)
            } catch (e: Exception) {
                Log.w(TAG, "MediaStore.Downloads query failed: ${e.message}")
            }
        }

        // Sort descending by last modified date, distinct by title + file size
        resultList.distinctBy { "${it.title.lowercase()}_${it.sizeBytes}" }
            .sortedByDescending { it.dateModified }
    }

    private fun scanDirectoryRecursively(
        dir: File,
        resultList: MutableList<DeviceFileItem>,
        visitedKeys: HashSet<String>,
        maxDepth: Int
    ) {
        if (maxDepth < 0 || !dir.exists() || !dir.canRead()) return
        val pathStr = dir.absolutePath
        // Skip hidden folders and system-restricted app private folders to avoid security exceptions
        if (dir.name.startsWith(".") || pathStr.contains("/Android/data") || pathStr.contains("/Android/obb")) {
            return
        }

        val files = dir.listFiles() ?: return
        for (f in files) {
            if (resultList.size >= 12000) return // Safety ceiling

            if (f.isDirectory) {
                if (!f.name.startsWith(".")) {
                    scanDirectoryRecursively(f, resultList, visitedKeys, maxDepth - 1)
                }
            } else if (f.isFile && f.length() > 0) {
                val name = f.name
                val ext = f.extension.lowercase()
                val type = getFileTypeFromExtension(ext)

                if (type != null) {
                    val abs = f.absolutePath
                    val key = "${name.lowercase()}_${f.length()}"
                    if (visitedKeys.add(key)) {
                        resultList.add(
                            DeviceFileItem(
                                id = abs.hashCode().toLong(),
                                title = name,
                                uri = Uri.fromFile(f),
                                path = abs,
                                mimeType = getMimeTypeForExtension(ext),
                                sizeBytes = f.length(),
                                dateModified = f.lastModified(),
                                fileType = type
                            )
                        )
                    }
                }
            }
        }
    }

    private fun queryMediaStoreSafely(
        context: Context,
        collectionUri: Uri,
        resultList: MutableList<DeviceFileItem>,
        visitedKeys: HashSet<String>
    ) {
        val projection = arrayOf(
            MediaStore.MediaColumns._ID,
            MediaStore.MediaColumns.DISPLAY_NAME,
            MediaStore.MediaColumns.MIME_TYPE,
            MediaStore.MediaColumns.SIZE,
            MediaStore.MediaColumns.DATE_MODIFIED,
            MediaStore.MediaColumns.DATA
        )

        // Safe query without SQL functions to prevent SQLiteException on OEM phones
        val selection = "${MediaStore.MediaColumns.SIZE} > 0"

        context.contentResolver.query(
            collectionUri,
            projection,
            selection,
            null,
            "${MediaStore.MediaColumns.DATE_MODIFIED} DESC"
        )?.use { cursor ->
            val idCol = cursor.getColumnIndex(MediaStore.MediaColumns._ID)
            val nameCol = cursor.getColumnIndex(MediaStore.MediaColumns.DISPLAY_NAME)
            val mimeCol = cursor.getColumnIndex(MediaStore.MediaColumns.MIME_TYPE)
            val sizeCol = cursor.getColumnIndex(MediaStore.MediaColumns.SIZE)
            val dateCol = cursor.getColumnIndex(MediaStore.MediaColumns.DATE_MODIFIED)
            val dataCol = cursor.getColumnIndex(MediaStore.MediaColumns.DATA)

            while (cursor.moveToNext()) {
                val id = if (idCol != -1) cursor.getLong(idCol) else 0L
                val name = (if (nameCol != -1) cursor.getString(nameCol) else null) ?: "Document_$id"
                val mime = (if (mimeCol != -1) cursor.getString(mimeCol) else null) ?: ""
                val size = if (sizeCol != -1) cursor.getLong(sizeCol) else 0L
                val rawDate = if (dateCol != -1) cursor.getLong(dateCol) else 0L
                val dateModified = if (rawDate > 100000000000L) rawDate else rawDate * 1000L
                val path = if (dataCol != -1) cursor.getString(dataCol) ?: "" else ""

                val ext = name.substringAfterLast('.', "").lowercase()
                val type = getFileTypeFromExtension(ext) ?: getFileTypeFromMime(mime)

                if (type != null) {
                    val contentUri = ContentUris.withAppendedId(collectionUri, id)
                    val key = "${name.lowercase()}_$size"
                    if (visitedKeys.add(key)) {
                        resultList.add(
                            DeviceFileItem(
                                id = id,
                                title = name,
                                uri = if (path.isNotEmpty() && File(path).exists()) Uri.fromFile(File(path)) else contentUri,
                                path = path,
                                mimeType = mime.ifEmpty { getMimeTypeForExtension(ext) },
                                sizeBytes = size,
                                dateModified = if (dateModified > 0) dateModified else System.currentTimeMillis(),
                                fileType = type
                            )
                        )
                    }
                }
            }
        }
    }

    /**
     * Scans a user-selected folder tree via SAF (Storage Access Framework).
     */
    suspend fun scanDocumentTree(context: Context, rootTreeUri: Uri): List<DeviceFileItem> = withContext(Dispatchers.IO) {
        val results = mutableListOf<DeviceFileItem>()
        val visitedKeys = HashSet<String>()
        try {
            val docId = DocumentsContract.getTreeDocumentId(rootTreeUri)
            val childrenUri = DocumentsContract.buildChildDocumentsUriUsingTree(rootTreeUri, docId)

            val projection = arrayOf(
                DocumentsContract.Document.COLUMN_DOCUMENT_ID,
                DocumentsContract.Document.COLUMN_DISPLAY_NAME,
                DocumentsContract.Document.COLUMN_MIME_TYPE,
                DocumentsContract.Document.COLUMN_SIZE,
                DocumentsContract.Document.COLUMN_LAST_MODIFIED
            )

            context.contentResolver.query(childrenUri, projection, null, null, null)?.use { cursor ->
                val idCol = cursor.getColumnIndex(DocumentsContract.Document.COLUMN_DOCUMENT_ID)
                val nameCol = cursor.getColumnIndex(DocumentsContract.Document.COLUMN_DISPLAY_NAME)
                val mimeCol = cursor.getColumnIndex(DocumentsContract.Document.COLUMN_MIME_TYPE)
                val sizeCol = cursor.getColumnIndex(DocumentsContract.Document.COLUMN_SIZE)
                val dateCol = cursor.getColumnIndex(DocumentsContract.Document.COLUMN_LAST_MODIFIED)

                while (cursor.moveToNext()) {
                    val childDocId = if (idCol != -1) cursor.getString(idCol) ?: "" else ""
                    val name = (if (nameCol != -1) cursor.getString(nameCol) else null) ?: "Document"
                    val mime = (if (mimeCol != -1) cursor.getString(mimeCol) else null) ?: ""
                    val size = if (sizeCol != -1) cursor.getLong(sizeCol) else 0L
                    val dateModified = if (dateCol != -1) cursor.getLong(dateCol) else System.currentTimeMillis()

                    val ext = name.substringAfterLast('.', "").lowercase()
                    val type = getFileTypeFromExtension(ext) ?: getFileTypeFromMime(mime)

                    if (type != null) {
                        val fileDocUri = DocumentsContract.buildDocumentUriUsingTree(rootTreeUri, childDocId)
                        val key = "${name.lowercase()}_$size"
                        if (visitedKeys.add(key)) {
                            results.add(
                                DeviceFileItem(
                                    id = childDocId.hashCode().toLong(),
                                    title = name,
                                    uri = fileDocUri,
                                    path = "",
                                    mimeType = mime.ifEmpty { getMimeTypeForExtension(ext) },
                                    sizeBytes = size,
                                    dateModified = dateModified,
                                    fileType = type
                                )
                            )
                        }
                    }
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error scanning document tree: ${e.message}", e)
        }
        results
    }

    private fun getFileTypeFromExtension(ext: String): DeviceFileType? {
        return when (ext) {
            "pdf" -> DeviceFileType.PDF
            "docx" -> DeviceFileType.WORD_DOCX
            "doc" -> DeviceFileType.WORD_DOC
            "xlsx", "xls" -> DeviceFileType.EXCEL
            "pptx", "ppt" -> DeviceFileType.POWERPOINT
            "txt" -> DeviceFileType.TEXT
            else -> null
        }
    }

    private fun getFileTypeFromMime(mime: String): DeviceFileType? {
        return when {
            mime.contains("pdf", ignoreCase = true) -> DeviceFileType.PDF
            mime.contains("wordprocessingml", ignoreCase = true) -> DeviceFileType.WORD_DOCX
            mime.contains("msword", ignoreCase = true) -> DeviceFileType.WORD_DOC
            mime.contains("spreadsheetml", ignoreCase = true) || mime.contains("excel", ignoreCase = true) -> DeviceFileType.EXCEL
            mime.contains("presentationml", ignoreCase = true) || mime.contains("powerpoint", ignoreCase = true) -> DeviceFileType.POWERPOINT
            mime.startsWith("text/", ignoreCase = true) -> DeviceFileType.TEXT
            else -> null
        }
    }

    private fun getMimeTypeForExtension(ext: String): String {
        return when (ext) {
            "pdf" -> "application/pdf"
            "docx" -> "application/vnd.openxmlformats-officedocument.wordprocessingml.document"
            "doc" -> "application/msword"
            "xlsx" -> "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet"
            "xls" -> "application/vnd.ms-excel"
            "pptx" -> "application/vnd.openxmlformats-officedocument.presentationml.presentation"
            "ppt" -> "application/vnd.ms-powerpoint"
            "txt" -> "text/plain"
            else -> "application/octet-stream"
        }
    }
}

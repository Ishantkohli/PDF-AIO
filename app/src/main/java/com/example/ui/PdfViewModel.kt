package com.example.ui

import android.app.Application
import android.graphics.Bitmap
import android.net.Uri
import android.os.ParcelFileDescriptor
import android.provider.OpenableColumns
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.data.local.AnnotationEntity
import com.example.data.local.BookmarkEntity
import com.example.data.local.DocumentEntity
import com.example.data.local.PdfDatabase
import com.example.data.local.SignatureEntity
import com.example.data.local.StorageRenameCoordinator
import com.example.data.pdf.DocumentConverterEngine
import com.example.data.pdf.PdfCompressorEngine
import com.example.data.pdf.PdfExportEngine
import com.example.data.pdf.PdfRedactionEngine
import com.example.data.pdf.PdfRendererManager
import com.example.data.pdf.PdfSecurityEngine
import com.example.data.pdf.PdfTextLayoutEngine
import com.example.data.pdf.SampleDocumentGenerator
import com.example.data.pdf.ShareCoordinator
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream

enum class AppScreen {
    BOOTUP_SHOWCASE,
    HOME,
    SCANNER,
    CONVERSION_HUB,
    READER,
    TOOLS,
    SIGNATURE_VAULT,
    PAGE_MANAGER,
    SUPPORT,
    CREDITS,
    PDF_DIFF,
    FEEDBACK,
    TXT_VIEWER,
    LEGAL_CENTER
}

enum class AnnotationTool {
    NONE,
    TEXT_SELECT,
    PEN,
    HIGHLIGHT,
    UNDERLINE,
    STRIKETHROUGH,
    STICKY_NOTE,
    STAMP,
    SIGNATURE,
    TEXT_BOX,
    WHITEOUT,
    REDACT,
    FORM_FIELD,
    WATERMARK,
    ERASER
}

class PdfViewModel(application: Application) : AndroidViewModel(application) {

    private val db = PdfDatabase.getInstance(application)
    private val dao = db.pdfDao()

    val documents: StateFlow<List<DocumentEntity>> = dao.getAllDocuments()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val signatures: StateFlow<List<SignatureEntity>> = dao.getAllSignatures()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    // Startup initialization block intentionally placed below all StateFlow/field initializers.

    private fun removeSampleFiles() {
        viewModelScope.launch(Dispatchers.IO) {
            try {
                SampleDocumentGenerator.deleteSampleFiles(getApplication())
                dao.deleteSampleDocuments()
            } catch (_: Exception) {}
        }
    }

    // Navigation state
    private val _currentScreen = MutableStateFlow(AppScreen.HOME)
    val currentScreen: StateFlow<AppScreen> = _currentScreen.asStateFlow()

    fun navigateTo(screen: AppScreen) {
        if (screen != AppScreen.READER) {
            ttsManager.stop()
        }
        _currentScreen.value = screen
    }

    // FPS Telemetry HUD state
    private val _showFpsHud = MutableStateFlow(false)
    val showFpsHud: StateFlow<Boolean> = _showFpsHud.asStateFlow()

    fun setShowFpsHud(show: Boolean) {
        _showFpsHud.value = show
    }

    // Selected Tab in Tools Screen
    private val _selectedToolTab = MutableStateFlow(0)
    val selectedToolTab: StateFlow<Int> = _selectedToolTab.asStateFlow()

    fun setSelectedToolTab(tabIndex: Int) {
        _selectedToolTab.value = tabIndex
    }

    // Pure Viewer Mode (Zero annotation/edit toolbars clutter)
    private val _isPureViewerMode = MutableStateFlow(false)
    val isPureViewerMode: StateFlow<Boolean> = _isPureViewerMode.asStateFlow()

    fun setPureViewerMode(isViewerOnly: Boolean) {
        _isPureViewerMode.value = isViewerOnly
    }

    // TXT Viewer State
    data class TxtDocumentState(
        val title: String,
        val uri: Uri,
        val lines: List<String>,
        val fileSizeBytes: Long,
        val fullText: String
    )

    private val _currentTxtDocument = MutableStateFlow<TxtDocumentState?>(null)
    val currentTxtDocument: StateFlow<TxtDocumentState?> = _currentTxtDocument.asStateFlow()

    fun openTxtDocument(txtUri: Uri, title: String = "Text Document") {
        viewModelScope.launch(Dispatchers.IO) {
            val context = getApplication<Application>()
            try {
                var resolvedTitle = title
                var resolvedSize = 0L
                context.contentResolver.query(txtUri, null, null, null, null)?.use { cursor ->
                    val nameIdx = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
                    val sizeIdx = cursor.getColumnIndex(OpenableColumns.SIZE)
                    if (cursor.moveToFirst()) {
                        if (nameIdx != -1) resolvedTitle = cursor.getString(nameIdx) ?: resolvedTitle
                        if (sizeIdx != -1) resolvedSize = cursor.getLong(sizeIdx)
                    }
                }
                if (resolvedSize <= 0L && txtUri.scheme == "file") {
                    txtUri.path?.let { p ->
                        val f = File(p)
                        if (f.exists()) resolvedSize = f.length()
                    }
                }

                val rawBytes = context.contentResolver.openInputStream(txtUri)?.use { it.readBytes() }
                    ?: throw IllegalStateException("Unable to read text file")
                if (resolvedSize <= 0L) resolvedSize = rawBytes.size.toLong()

                val text = when {
                    rawBytes.size >= 3 && (rawBytes[0].toInt() and 0xFF) == 0xEF &&
                        (rawBytes[1].toInt() and 0xFF) == 0xBB && (rawBytes[2].toInt() and 0xFF) == 0xBF ->
                        String(rawBytes, 3, rawBytes.size - 3, Charsets.UTF_8)
                    rawBytes.size >= 2 && (rawBytes[0].toInt() and 0xFF) == 0xFF &&
                        (rawBytes[1].toInt() and 0xFF) == 0xFE ->
                        String(rawBytes, 2, rawBytes.size - 2, Charsets.UTF_16LE)
                    rawBytes.size >= 2 && (rawBytes[0].toInt() and 0xFF) == 0xFE &&
                        (rawBytes[1].toInt() and 0xFF) == 0xFF ->
                        String(rawBytes, 2, rawBytes.size - 2, Charsets.UTF_16BE)
                    else -> String(rawBytes, Charsets.UTF_8)
                }.replace("\r\n", "\n").replace('\r', '\n')

                val lines = text.split('\n')
                val state = TxtDocumentState(
                    title = resolvedTitle.ifBlank { "Document.txt" },
                    uri = txtUri,
                    lines = lines,
                    fileSizeBytes = resolvedSize,
                    fullText = text
                )
                withContext(Dispatchers.Main) {
                    _currentTxtDocument.value = state
                    _currentScreen.value = AppScreen.TXT_VIEWER
                }
            } catch (e: Exception) {
                android.util.Log.e("PdfViewModel", "Failed to open TXT file", e)
                withContext(Dispatchers.Main) {
                    _statusMessage.value = "Could not open TXT file: ${e.localizedMessage ?: "Access denied"}"
                }
            }
        }
    }

    fun closeTxtDocument() {
        _currentTxtDocument.value = null
        _currentScreen.value = AppScreen.HOME
    }

    fun convertTxtDocumentToPdf(
        txtState: TxtDocumentState,
        onDone: (DocumentEntity?) -> Unit = {}
    ) {
        convertTxtUriToPdf(txtState.uri, txtState.title, onDone)
    }

    // Device Document Files (PDFs, Word .docx, .doc)
    private val _deviceFiles = MutableStateFlow<List<com.example.data.local.DeviceFileItem>>(emptyList())
    val deviceFiles: StateFlow<List<com.example.data.local.DeviceFileItem>> = _deviceFiles.asStateFlow()

    private val _isScanningDeviceFiles = MutableStateFlow(false)
    val isScanningDeviceFiles: StateFlow<Boolean> = _isScanningDeviceFiles.asStateFlow()

    fun scanDeviceDocuments() {
        viewModelScope.launch {
            _isScanningDeviceFiles.value = true
            try {
                val scanned = com.example.data.local.DeviceDocumentScanner.scanDeviceDocuments(getApplication())
                _deviceFiles.value = scanned
                _statusMessage.value = if (scanned.isNotEmpty()) "Found ${scanned.size} documents on device storage" else "Scan finished (0 documents found in standard folders)"
            } catch (e: Exception) {
                android.util.Log.e("PdfViewModel", "Failed to scan device documents", e)
                _statusMessage.value = "Storage scan encountered an issue: ${e.message}"
            } finally {
                _isScanningDeviceFiles.value = false
            }
        }
    }

    fun scanFolderTree(treeUri: Uri) {
        viewModelScope.launch {
            _isScanningDeviceFiles.value = true
            try {
                val scanned = com.example.data.local.DeviceDocumentScanner.scanDocumentTree(getApplication(), treeUri)
                val combined = (_deviceFiles.value + scanned).distinctBy { "${it.title}_${it.sizeBytes}" }
                _deviceFiles.value = combined
                _statusMessage.value = "Scanned ${scanned.size} documents from chosen folder!"
            } catch (e: Exception) {
                android.util.Log.e("PdfViewModel", "Failed to scan folder tree", e)
                _statusMessage.value = "Folder scan error: ${e.message}"
            } finally {
                _isScanningDeviceFiles.value = false
            }
        }
    }

    fun openDeviceFile(fileItem: com.example.data.local.DeviceFileItem, pureViewer: Boolean = false) {
        viewModelScope.launch {
            _isPureViewerMode.value = pureViewer
            when (fileItem.fileType) {
                com.example.data.local.DeviceFileType.PDF -> {
                    importPdfFromUri(fileItem.uri)
                }
                com.example.data.local.DeviceFileType.TEXT -> {
                    openTxtDocument(fileItem.uri, fileItem.title)
                }
                else -> {
                    _statusMessage.value = "Only PDF and TXT files open in the viewer. Use Converter for other formats."
                }
            }
        }
    }

    // Active Reader State
    private val _currentDocument = MutableStateFlow<DocumentEntity?>(null)
    val currentDocument: StateFlow<DocumentEntity?> = _currentDocument.asStateFlow()

    private var rendererManager: PdfRendererManager? = null

    private val _currentPage = MutableStateFlow(0)
    val currentPage: StateFlow<Int> = _currentPage.asStateFlow()

    private val _totalPages = MutableStateFlow(1)
    val totalPages: StateFlow<Int> = _totalPages.asStateFlow()

    private val _pageBitmaps = MutableStateFlow<Map<Int, Bitmap>>(emptyMap())
    val pageBitmaps: StateFlow<Map<Int, Bitmap>> = _pageBitmaps.asStateFlow()

    private val _colorFilter = MutableStateFlow("NORMAL") // NORMAL, NIGHT, SEPIA
    val colorFilter: StateFlow<String> = _colorFilter.asStateFlow()

    private val _readingMode = MutableStateFlow("VERTICAL") // VERTICAL, HORIZONTAL
    val readingMode: StateFlow<String> = _readingMode.asStateFlow()

    // Annotations & Undo/Redo history for active document
    private val _currentAnnotations = MutableStateFlow<List<AnnotationEntity>>(emptyList())
    val currentAnnotations: StateFlow<List<AnnotationEntity>> = _currentAnnotations.asStateFlow()

    private sealed class AnnotationAction {
        data class Added(val annotation: AnnotationEntity) : AnnotationAction()
        data class Deleted(val annotation: AnnotationEntity) : AnnotationAction()
        data class Updated(val previous: AnnotationEntity, val updated: AnnotationEntity) : AnnotationAction()
        data class ClearedPage(val removed: List<AnnotationEntity>) : AnnotationAction()
    }

    private val undoStack = ArrayDeque<AnnotationAction>()
    private val redoStack = ArrayDeque<AnnotationAction>()
    private val _canUndo = MutableStateFlow(false)
    val canUndo: StateFlow<Boolean> = _canUndo.asStateFlow()
    private val _canRedo = MutableStateFlow(false)
    val canRedo: StateFlow<Boolean> = _canRedo.asStateFlow()

    private fun syncUndoRedoFlags() {
        _canUndo.value = undoStack.isNotEmpty()
        _canRedo.value = redoStack.isNotEmpty()
    }

    // Bookmarks for active document
    private val _currentBookmarks = MutableStateFlow<List<BookmarkEntity>>(emptyList())
    val currentBookmarks: StateFlow<List<BookmarkEntity>> = _currentBookmarks.asStateFlow()
    private var bookmarksJob: Job? = null

    // Per-page native PDF text selection layout cache
    private val _currentPageTextLayout = MutableStateFlow<PdfTextLayoutEngine.PageTextLayout?>(null)
    val currentPageTextLayout: StateFlow<PdfTextLayoutEngine.PageTextLayout?> = _currentPageTextLayout.asStateFlow()
    private var cachedLayoutFileKey: String = ""
    private val pageLayoutCache = mutableMapOf<Int, PdfTextLayoutEngine.PageTextLayout>()

    // Active Annotation Tool
    private val _activeTool = MutableStateFlow(AnnotationTool.NONE)
    val activeTool: StateFlow<AnnotationTool> = _activeTool.asStateFlow()

    private val _activeColor = MutableStateFlow(0xFFDC2626) // Crimson Red by default
    val activeColor: StateFlow<Long> = _activeColor.asStateFlow()

    private val _activeStrokeWidth = MutableStateFlow(4f)
    val activeStrokeWidth: StateFlow<Float> = _activeStrokeWidth.asStateFlow()

    private val _activeOpacity = MutableStateFlow(1.0f)
    val activeOpacity: StateFlow<Float> = _activeOpacity.asStateFlow()

    fun setActiveOpacity(opacity: Float) {
        _activeOpacity.value = opacity.coerceIn(0.15f, 1.0f)
    }

    // Digital Signature Modal State
    private val _isSignatureDialogVisible = MutableStateFlow(false)
    val isSignatureDialogVisible: StateFlow<Boolean> = _isSignatureDialogVisible.asStateFlow()

    fun showSignatureDialog(visible: Boolean = true) {
        _isSignatureDialogVisible.value = visible
    }

    // Status / Toast Messages
    private val _statusMessage = MutableStateFlow<String?>(null)
    val statusMessage: StateFlow<String?> = _statusMessage.asStateFlow()

    fun postStatusMessage(message: String) {
        _statusMessage.value = message
    }

    // TTS Read Aloud Engine
    private val ttsManager = com.example.data.pdf.TtsManager(application)
    val ttsIsPlaying: StateFlow<Boolean> = ttsManager.isPlaying
    val ttsPlaybackSpeed: StateFlow<Float> = ttsManager.playbackSpeed
    val ttsCurrentSentence: StateFlow<String> = ttsManager.currentSentence

    // Liquid Mode Reflow State
    private val _isLiquidMode = MutableStateFlow(false)
    val isLiquidMode: StateFlow<Boolean> = _isLiquidMode.asStateFlow()

    // Continuous Vertical Scroll Mode
    private val _isContinuousScroll = MutableStateFlow(false)
    val isContinuousScroll: StateFlow<Boolean> = _isContinuousScroll.asStateFlow()

    fun toggleContinuousScroll() {
        _isContinuousScroll.value = !_isContinuousScroll.value
    }

    // 4K Auto Ultra-Clarity Zoom Engine State
    private val _is4KUltraClarityEnabled = MutableStateFlow(true)
    val is4KUltraClarityEnabled: StateFlow<Boolean> = _is4KUltraClarityEnabled.asStateFlow()

    private val _is4KActive = MutableStateFlow(false)
    val is4KActive: StateFlow<Boolean> = _is4KActive.asStateFlow()

    fun toggle4KUltraClarityEngine() {
        val next = !_is4KUltraClarityEnabled.value
        _is4KUltraClarityEnabled.value = next
        _statusMessage.value = if (next) "4K Auto Ultra-Clarity Engine Enabled (Crisp on Zoom)" else "4K Ultra-Clarity Disabled"
    }

    // Watermark Configuration State
    private val _activeWatermark = MutableStateFlow<DocumentConverterEngine.WatermarkOptions?>(null)
    val activeWatermark: StateFlow<DocumentConverterEngine.WatermarkOptions?> = _activeWatermark.asStateFlow()

    fun setWatermarkOptions(options: DocumentConverterEngine.WatermarkOptions?) {
        _activeWatermark.value = options
    }

    // In-Document Search State
    private val _isSearchActive = MutableStateFlow(false)
    val isSearchActive: StateFlow<Boolean> = _isSearchActive.asStateFlow()

    private val _searchQuery = MutableStateFlow("")
    val searchQuery: StateFlow<String> = _searchQuery.asStateFlow()

    private val _searchResults = MutableStateFlow<List<Int>>(emptyList())
    val searchResults: StateFlow<List<Int>> = _searchResults.asStateFlow()

    private val _currentSearchMatchIndex = MutableStateFlow(0)
    val currentSearchMatchIndex: StateFlow<Int> = _currentSearchMatchIndex.asStateFlow()

    fun toggleSearch(active: Boolean? = null) {
        val next = active ?: !_isSearchActive.value
        _isSearchActive.value = next
        if (!next) {
            _searchQuery.value = ""
            _searchResults.value = emptyList()
            _currentSearchMatchIndex.value = 0
        }
    }

    fun performSearch(query: String) {
        _searchQuery.value = query
        if (query.isBlank()) {
            _searchResults.value = emptyList()
            _currentSearchMatchIndex.value = 0
            return
        }
        val doc = _currentDocument.value ?: return
        val total = _totalPages.value
        val q = query.trim().lowercase()

        viewModelScope.launch(Dispatchers.IO) {
            val matches = mutableListOf<Int>()
            val pdfFile = File(doc.filePath)

            for (p in 0 until total) {
                var matched = false

                // 1. Full-text extraction from PDF page streams
                if (pdfFile.exists() && pdfFile.length() > 0L) {
                    val extractedText = extractPdfPageText(pdfFile, p)
                    if (extractedText.lowercase().contains(q)) {
                        matched = true
                    }
                }

                // 2. Search annotations on this page (text box, sticky note, typed signature)
                if (!matched) {
                    val pageAnn = _currentAnnotations.value.filter { it.pageIndex == p }
                    if (pageAnn.any { it.textContent.lowercase().contains(q) }) {
                        matched = true
                    }
                }

                // 3. Search document title or page number ("3", "page 3")
                if (!matched) {
                    if (doc.title.lowercase().contains(q) || q == "${p + 1}" || q == "page ${p + 1}") {
                        matched = true
                    }
                }

                if (matched) {
                    matches.add(p)
                }
            }

            withContext(Dispatchers.Main) {
                _searchResults.value = matches
                _currentSearchMatchIndex.value = 0
                if (matches.isNotEmpty()) {
                    setCurrentPage(matches[0])
                }
            }
        }
    }

    private var cachedExtractedPath: String? = null
    private var cachedExtractedPages: List<com.example.data.pdf.PdfTextExtractor.ExtractedPage> = emptyList()

    init {
        viewModelScope.launch(Dispatchers.IO) {
            try {
                val cacheFiles = application.cacheDir.listFiles()
                cacheFiles?.forEach { file ->
                    if (file.name.startsWith("imp_ppt_") || file.name.startsWith("temp_ppt_") || file.name.startsWith("ppt_media_")) {
                        file.deleteRecursively()
                    }
                }
                ShareCoordinator.cleanupExpiredStaging(application)
            } catch (_: Exception) {}
        }
        removeSampleFiles()
        scanDeviceDocuments()
    }

    private fun countPdfPagesSafely(file: File): Int {
        return ParcelFileDescriptor.open(file, ParcelFileDescriptor.MODE_READ_ONLY).use { pfd ->
            android.graphics.pdf.PdfRenderer(pfd).use { renderer ->
                renderer.pageCount
            }
        }
    }

    private fun extractPdfPageText(file: File, pageIndex: Int): String {
        return try {
            val cacheKey = "${file.absolutePath}_${file.lastModified()}"
            if (cachedExtractedPath != cacheKey) {
                cachedExtractedPages = com.example.data.pdf.PdfTextExtractor.extractPages(file)
                cachedExtractedPath = cacheKey
            }
            cachedExtractedPages.getOrNull(pageIndex)?.fullText ?: ""
        } catch (e: Exception) {
            android.util.Log.e("PdfViewModel", "Failed to extract text from page $pageIndex", e)
            ""
        }
    }

    fun nextSearchMatch() {
        val results = _searchResults.value
        if (results.isEmpty()) return
        val nextIdx = (_currentSearchMatchIndex.value + 1) % results.size
        _currentSearchMatchIndex.value = nextIdx
        setCurrentPage(results[nextIdx])
    }

    fun previousSearchMatch() {
        val results = _searchResults.value
        if (results.isEmpty()) return
        val prevIdx = if (_currentSearchMatchIndex.value - 1 < 0) results.size - 1 else _currentSearchMatchIndex.value - 1
        _currentSearchMatchIndex.value = prevIdx
        setCurrentPage(results[prevIdx])
    }

    fun openDocument(doc: DocumentEntity) {
        viewModelScope.launch {
            rendererManager?.close()
            rendererManager = null

            val file = File(doc.filePath)
            if (!file.exists()) {
                _statusMessage.value = "Document file not found"
                return@launch
            }

            try {
                val manager = PdfRendererManager(file)
                rendererManager = manager
                _currentDocument.value = doc
                _totalPages.value = manager.pageCount
                _currentPage.value = doc.lastReadPage.coerceIn(0, (manager.pageCount - 1).coerceAtLeast(0))
                _colorFilter.value = doc.colorFilter
                _readingMode.value = doc.readingMode
                _currentScreen.value = AppScreen.READER
                _pageBitmaps.value = emptyMap()
                _currentPageTextLayout.value = null
                pageLayoutCache.clear()
                cachedLayoutFileKey = "${file.absolutePath}_${file.lastModified()}"
                undoStack.clear()
                redoStack.clear()
                syncUndoRedoFlags()

                loadAnnotationsForDoc(doc.id)
                observeBookmarksForDoc(doc.id)
                dao.updateProgress(doc.id, _currentPage.value)

                renderPage(_currentPage.value)
                if (_currentPage.value + 1 < manager.pageCount) renderPage(_currentPage.value + 1)
                loadPageTextLayout(_currentPage.value)
            } catch (e: Exception) {
                _statusMessage.value = "Failed to open PDF: ${e.localizedMessage}"
            }
        }
    }

    fun loadPageTextLayout(pageIndex: Int) {
        val doc = _currentDocument.value ?: return
        val file = File(doc.filePath)
        if (!file.exists()) return
        val fileKey = "${file.absolutePath}_${file.lastModified()}"
        if (cachedLayoutFileKey != fileKey) {
            cachedLayoutFileKey = fileKey
            pageLayoutCache.clear()
        }
        pageLayoutCache[pageIndex]?.let { cached ->
            if (pageIndex == _currentPage.value) {
                _currentPageTextLayout.value = cached
            }
            return
        }
        viewModelScope.launch(Dispatchers.IO) {
            val layout = PdfTextLayoutEngine.extractPageLayout(getApplication(), file, pageIndex)
            pageLayoutCache[pageIndex] = layout
            if (_currentPage.value == pageIndex) {
                _currentPageTextLayout.value = layout
            }
        }
    }

    fun importPdfFromUri(uri: Uri) {
        viewModelScope.launch(Dispatchers.IO) {
            val context = getApplication<Application>()
            try {
                try {
                    context.contentResolver.takePersistableUriPermission(
                        uri,
                        android.content.Intent.FLAG_GRANT_READ_URI_PERMISSION or
                            android.content.Intent.FLAG_GRANT_WRITE_URI_PERMISSION
                    )
                } catch (_: Exception) {
                    try {
                        context.contentResolver.takePersistableUriPermission(
                            uri,
                            android.content.Intent.FLAG_GRANT_READ_URI_PERMISSION
                        )
                    } catch (_: Exception) {}
                }

                var title = "Imported Document"
                context.contentResolver.query(uri, null, null, null, null)?.use { cursor ->
                    val nameIdx = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
                    if (cursor.moveToFirst() && nameIdx != -1) {
                        title = cursor.getString(nameIdx) ?: title
                    }
                }
                if (title == "Imported Document" && uri.scheme == "file") {
                    uri.path?.let { p -> title = File(p).name }
                }

                val lowerTitle = title.lowercase()
                val mimeType = context.contentResolver.getType(uri)?.lowercase() ?: ""
                if (lowerTitle.endsWith(".txt") || mimeType == "text/plain") {
                    openTxtDocument(uri, title)
                    return@launch
                } else if (
                    lowerTitle.endsWith(".docx") || lowerTitle.endsWith(".doc") ||
                    lowerTitle.endsWith(".pptx") || lowerTitle.endsWith(".ppt") ||
                    lowerTitle.endsWith(".xlsx") || lowerTitle.endsWith(".xls") ||
                    lowerTitle.endsWith(".rtf")
                ) {
                    withContext(Dispatchers.Main) {
                        _statusMessage.value = "Only PDF & TXT viewers are enabled. Use Converter tab to convert Word files to PDF."
                    }
                    return@launch
                }

                val docsDir = File(context.filesDir, "imported_pdfs").apply { if (!exists()) mkdirs() }
                val baseTitle = StorageRenameCoordinator.sanitizeForStorageFileName(title, fallback = "Document")
                var destFile = File(docsDir, "$baseTitle.pdf")
                if (destFile.exists()) {
                    var suffix = 1
                    while (destFile.exists()) {
                        destFile = File(docsDir, "${baseTitle}_$suffix.pdf")
                        suffix++
                    }
                }

                context.contentResolver.openInputStream(uri)?.use { input ->
                    FileOutputStream(destFile).use { output ->
                        input.copyTo(output)
                    }
                }

                if (destFile.exists() && destFile.length() > 0) {
                    val pages = countPdfPagesSafely(destFile)
                    val externalSourceUri = if (uri.scheme == "content") uri.toString() else ""
                    val newDoc = DocumentEntity(
                        uriString = Uri.fromFile(destFile).toString(),
                        title = baseTitle,
                        filePath = destFile.absolutePath,
                        pageCount = pages,
                        fileSizeBytes = destFile.length(),
                        sourceUri = externalSourceUri
                    )
                    val id = dao.insertDocument(newDoc)
                    withContext(Dispatchers.Main) {
                        openDocument(newDoc.copy(id = id))
                    }
                } else {
                    withContext(Dispatchers.Main) {
                        _statusMessage.value = "Selected file is empty."
                    }
                }
            } catch (e: Exception) {
                withContext(Dispatchers.Main) {
                    _statusMessage.value = "Could not open PDF: file may be password protected or corrupted."
                }
            }
        }
    }

    fun registerScannedPdf(pdfFile: File, title: String, onRegistered: (DocumentEntity) -> Unit) {
        viewModelScope.launch(Dispatchers.IO) {
            try {
                val pages = countPdfPagesSafely(pdfFile)
                val cleanBase = StorageRenameCoordinator.sanitizeForStorageFileName(title, fallback = "Scan")
                val newDoc = DocumentEntity(
                    uriString = Uri.fromFile(pdfFile).toString(),
                    title = cleanBase,
                    filePath = pdfFile.absolutePath,
                    pageCount = pages,
                    fileSizeBytes = pdfFile.length(),
                    lastOpenedTimestamp = System.currentTimeMillis(),
                    folderCategory = "Scanned"
                )
                val id = dao.insertDocument(newDoc)
                val savedDoc = newDoc.copy(id = id)
                scanDeviceDocuments()
                withContext(Dispatchers.Main) {
                    onRegistered(savedDoc)
                }
            } catch (e: Exception) {
                withContext(Dispatchers.Main) {
                    _statusMessage.value = "Failed to register scanned document: ${e.localizedMessage}"
                }
            }
        }
    }

    fun renderPage(pageIndex: Int) {
        viewModelScope.launch {
            val manager = rendererManager ?: return@launch
            if (pageIndex < 0 || pageIndex >= manager.pageCount) return@launch
            if (_pageBitmaps.value.containsKey(pageIndex)) return@launch

            val bmp = manager.renderPage(pageIndex, densityDpi = 2)
            if (bmp != null) {
                val updated = _pageBitmaps.value.toMutableMap()
                updated[pageIndex] = bmp
                // Bound active bitmap memory footprint for worldwide low-RAM phones (Oppo, Realme, Samsung A-series)
                val maxActiveBitmaps = if (manager.isLowMemoryDevice) 8 else 18
                if (updated.size > maxActiveBitmaps) {
                    val current = _currentPage.value
                    val keysToEvict = updated.keys.sortedByDescending { kotlin.math.abs(it - current) }
                    for (k in keysToEvict.take(updated.size - maxActiveBitmaps)) {
                        updated.remove(k)
                    }
                }
                _pageBitmaps.value = updated
            }
        }
    }

    fun renderPageHighRes(pageIndex: Int, zoomScale: Float) {
        viewModelScope.launch {
            val manager = rendererManager ?: return@launch
            if (pageIndex < 0 || pageIndex >= manager.pageCount) return@launch
            if (!_is4KUltraClarityEnabled.value) return@launch

            val targetDpi = (zoomScale * 2.2f).toInt().coerceIn(3, 5)
            val bmp = manager.renderPage(pageIndex, densityDpi = targetDpi, autoSharpen4K = true)
            if (bmp != null) {
                val updated = _pageBitmaps.value.toMutableMap()
                updated[pageIndex] = bmp
                _pageBitmaps.value = updated
                _is4KActive.value = true
            }
        }
    }

    fun setCurrentPage(page: Int) {
        val total = _totalPages.value
        val clamped = page.coerceIn(0, (total - 1).coerceAtLeast(0))
        _currentPage.value = clamped
        renderPage(clamped)
        if (clamped + 1 < total) renderPage(clamped + 1)
        if (clamped - 1 >= 0) renderPage(clamped - 1)
        loadPageTextLayout(clamped)

        val doc = _currentDocument.value
        if (doc != null) {
            viewModelScope.launch(Dispatchers.IO) {
                dao.updateProgress(doc.id, clamped)
            }
        }
    }

    fun toggleReadingMode() {
        val newMode = if (_readingMode.value == "VERTICAL") "HORIZONTAL" else "VERTICAL"
        _readingMode.value = newMode
    }

    fun cycleColorFilter() {
        val next = when (_colorFilter.value) {
            "NORMAL" -> "NIGHT"
            "NIGHT" -> "SEPIA"
            else -> "NORMAL"
        }
        _colorFilter.value = next
    }

    fun toggleFavorite(doc: DocumentEntity) {
        viewModelScope.launch(Dispatchers.IO) {
            dao.setFavorite(doc.id, !doc.isFavorite)
            if (_currentDocument.value?.id == doc.id) {
                _currentDocument.value = _currentDocument.value?.copy(isFavorite = !doc.isFavorite)
            }
        }
    }

    fun togglePin(doc: DocumentEntity) {
        viewModelScope.launch(Dispatchers.IO) {
            dao.setPinned(doc.id, !doc.isPinned)
            if (_currentDocument.value?.id == doc.id) {
                _currentDocument.value = _currentDocument.value?.copy(isPinned = !doc.isPinned)
            }
        }
    }

    fun updateDocumentCategory(doc: DocumentEntity, category: String) {
        viewModelScope.launch(Dispatchers.IO) {
            dao.setFolderCategory(doc.id, category)
            if (_currentDocument.value?.id == doc.id) {
                _currentDocument.value = _currentDocument.value?.copy(folderCategory = category)
            }
        }
    }

    fun deleteDocument(doc: DocumentEntity) {
        viewModelScope.launch(Dispatchers.IO) {
            dao.clearAllAnnotationsForDoc(doc.id)
            dao.deleteDocument(doc)
            try {
                val f = File(doc.filePath)
                if (f.exists() && !doc.isSample) {
                    f.delete()
                }
            } catch (_: Exception) {}
        }
    }

    // --- Bookmarks ---
    private fun observeBookmarksForDoc(docId: Long) {
        bookmarksJob?.cancel()
        bookmarksJob = viewModelScope.launch {
            dao.getBookmarksForDoc(docId).collect { list ->
                _currentBookmarks.value = list
            }
        }
    }

    fun addBookmark(pageIndex: Int, label: String) {
        val doc = _currentDocument.value ?: return
        val cleanLabel = label.trim().ifBlank { "Page ${pageIndex + 1}" }
        viewModelScope.launch(Dispatchers.IO) {
            dao.insertBookmark(
                BookmarkEntity(
                    documentId = doc.id,
                    pageIndex = pageIndex,
                    label = cleanLabel
                )
            )
            withContext(Dispatchers.Main) {
                _statusMessage.value = "Bookmark added: $cleanLabel"
            }
        }
    }

    fun deleteBookmark(bookmarkId: Long) {
        viewModelScope.launch(Dispatchers.IO) {
            dao.deleteBookmarkById(bookmarkId)
        }
    }

    // --- Rename Active Document & Storage Sync ---
    fun renameActiveDocument(
        newName: String,
        onResult: (StorageRenameCoordinator.RenameResult) -> Unit
    ) {
        val doc = _currentDocument.value ?: return
        viewModelScope.launch {
            val result = StorageRenameCoordinator.renameDocument(
                context = getApplication(),
                dao = dao,
                doc = doc,
                rawNewName = newName
            )
            if (result is StorageRenameCoordinator.RenameResult.Success) {
                val updated = result.updatedDoc
                _currentDocument.value = updated
                // Re-bind PdfRendererManager cleanly to the renamed file without resetting page or annotations
                withContext(Dispatchers.IO) {
                    val file = File(updated.filePath)
                    if (file.exists()) {
                        rendererManager?.close()
                        rendererManager = runCatching { PdfRendererManager(file) }.getOrNull()
                        cachedLayoutFileKey = "${file.absolutePath}_${file.lastModified()}"
                    }
                }
                _statusMessage.value = "Renamed to ${updated.title}.pdf"
            }
            onResult(result)
        }
    }

    fun saveRenamedCopyToUri(
        destinationUri: Uri,
        onDone: (Boolean) -> Unit
    ) {
        val doc = _currentDocument.value ?: run {
            onDone(false)
            return
        }
        viewModelScope.launch {
            val ok = StorageRenameCoordinator.saveCopyToDestinationUri(
                context = getApplication(),
                sourceFile = File(doc.filePath),
                destinationUri = destinationUri
            )
            _statusMessage.value = if (ok) "Saved renamed copy to selected folder." else "Failed to write copy."
            onDone(ok)
        }
    }

    // --- Annotation Actions + Undo / Redo ---
    private fun loadAnnotationsForDoc(docId: Long) {
        viewModelScope.launch(Dispatchers.IO) {
            val list = dao.getAllAnnotationsForDoc(docId)
            _currentAnnotations.value = list
        }
    }

    fun setActiveTool(tool: AnnotationTool) {
        _activeTool.value = if (_activeTool.value == tool) AnnotationTool.NONE else tool
    }

    fun setActiveColor(color: Long) {
        _activeColor.value = color
    }

    fun setActiveStrokeWidth(width: Float) {
        _activeStrokeWidth.value = width
    }

    fun addAnnotation(
        pageIndex: Int,
        type: String,
        pointsJson: String = "",
        colorHex: Long = _activeColor.value,
        strokeWidth: Float = _activeStrokeWidth.value,
        posX: Float = 0f,
        posY: Float = 0f,
        width: Float = 0f,
        height: Float = 0f,
        textContent: String = "",
        opacity: Float = _activeOpacity.value,
        extraData: String = ""
    ) {
        val doc = _currentDocument.value ?: return
        val annotation = AnnotationEntity(
            documentId = doc.id,
            pageIndex = pageIndex,
            type = type,
            pointsJson = pointsJson,
            colorHex = colorHex,
            strokeWidth = strokeWidth,
            posX = posX,
            posY = posY,
            width = width,
            height = height,
            textContent = textContent,
            opacity = opacity,
            extraData = extraData
        )
        viewModelScope.launch(Dispatchers.IO) {
            val id = dao.insertAnnotation(annotation)
            val saved = annotation.copy(id = id)
            _currentAnnotations.value = _currentAnnotations.value + saved
            undoStack.addLast(AnnotationAction.Added(saved))
            redoStack.clear()
            syncUndoRedoFlags()
        }
    }

    fun deleteAnnotation(annotationId: Long) {
        val existing = _currentAnnotations.value.firstOrNull { it.id == annotationId }
        viewModelScope.launch(Dispatchers.IO) {
            dao.deleteAnnotationById(annotationId)
            _currentAnnotations.value = _currentAnnotations.value.filter { it.id != annotationId }
            if (existing != null) {
                undoStack.addLast(AnnotationAction.Deleted(existing))
                redoStack.clear()
                syncUndoRedoFlags()
            }
        }
    }

    fun clearPageAnnotations(pageIndex: Int) {
        val doc = _currentDocument.value ?: return
        val removed = _currentAnnotations.value.filter { it.pageIndex == pageIndex }
        viewModelScope.launch(Dispatchers.IO) {
            dao.clearPageAnnotations(doc.id, pageIndex)
            _currentAnnotations.value = _currentAnnotations.value.filter { it.pageIndex != pageIndex }
            if (removed.isNotEmpty()) {
                undoStack.addLast(AnnotationAction.ClearedPage(removed))
                redoStack.clear()
                syncUndoRedoFlags()
            }
        }
    }

    fun updateAnnotation(annotation: AnnotationEntity) {
        val previous = _currentAnnotations.value.firstOrNull { it.id == annotation.id }
        viewModelScope.launch(Dispatchers.IO) {
            dao.updateAnnotation(annotation)
            _currentAnnotations.value = _currentAnnotations.value.map { if (it.id == annotation.id) annotation else it }
            if (previous != null && previous != annotation) {
                undoStack.addLast(AnnotationAction.Updated(previous, annotation))
                redoStack.clear()
                syncUndoRedoFlags()
            }
        }
    }

    fun undoLastAnnotation() {
        val action = undoStack.removeLastOrNull() ?: return
        viewModelScope.launch(Dispatchers.IO) {
            when (action) {
                is AnnotationAction.Added -> {
                    dao.deleteAnnotationById(action.annotation.id)
                    _currentAnnotations.value = _currentAnnotations.value.filter { it.id != action.annotation.id }
                }
                is AnnotationAction.Deleted -> {
                    val newId = dao.insertAnnotation(action.annotation.copy(id = 0))
                    val restored = action.annotation.copy(id = newId)
                    _currentAnnotations.value = _currentAnnotations.value + restored
                }
                is AnnotationAction.Updated -> {
                    dao.updateAnnotation(action.previous)
                    _currentAnnotations.value = _currentAnnotations.value.map {
                        if (it.id == action.previous.id) action.previous else it
                    }
                }
                is AnnotationAction.ClearedPage -> {
                    val restoredList = action.removed.map { item ->
                        val newId = dao.insertAnnotation(item.copy(id = 0))
                        item.copy(id = newId)
                    }
                    _currentAnnotations.value = _currentAnnotations.value + restoredList
                }
            }
            redoStack.addLast(action)
            syncUndoRedoFlags()
        }
    }

    fun redoLastAnnotation() {
        val action = redoStack.removeLastOrNull() ?: return
        viewModelScope.launch(Dispatchers.IO) {
            when (action) {
                is AnnotationAction.Added -> {
                    val newId = dao.insertAnnotation(action.annotation.copy(id = 0))
                    val restored = action.annotation.copy(id = newId)
                    _currentAnnotations.value = _currentAnnotations.value + restored
                    undoStack.addLast(AnnotationAction.Added(restored))
                    syncUndoRedoFlags()
                    return@launch
                }
                is AnnotationAction.Deleted -> {
                    dao.deleteAnnotationById(action.annotation.id)
                    _currentAnnotations.value = _currentAnnotations.value.filter { it.id != action.annotation.id }
                }
                is AnnotationAction.Updated -> {
                    dao.updateAnnotation(action.updated)
                    _currentAnnotations.value = _currentAnnotations.value.map {
                        if (it.id == action.updated.id) action.updated else it
                    }
                }
                is AnnotationAction.ClearedPage -> {
                    action.removed.firstOrNull()?.let { sample ->
                        dao.clearPageAnnotations(sample.documentId, sample.pageIndex)
                        _currentAnnotations.value = _currentAnnotations.value.filter { it.pageIndex != sample.pageIndex }
                    }
                }
            }
            undoStack.addLast(action)
            syncUndoRedoFlags()
        }
    }

    fun addWhiteout(
        pageIndex: Int,
        posX: Float,
        posY: Float,
        width: Float = 0.25f,
        height: Float = 0.04f,
        replacementText: String = "",
        colorHex: Long = 0xFFFFFFFFL,
        isRedaction: Boolean = false
    ) {
        addAnnotation(
            pageIndex = pageIndex,
            type = if (isRedaction) "REDACT" else "WHITEOUT",
            posX = posX,
            posY = posY,
            width = width,
            height = height,
            colorHex = if (isRedaction) 0xFF000000L else colorHex,
            textContent = replacementText
        )
        _statusMessage.value = when {
            isRedaction -> "Redaction box marked (apply permanent sanitization to strip data)"
            replacementText.isNotBlank() -> "Text edited & replaced"
            else -> "Whiteout applied"
        }
    }

    // --- 4-Option Share Rebuild ---
    fun prepareShare(
        option: ShareCoordinator.ShareOption,
        customPdfName: String? = null,
        onResult: (Result<ShareCoordinator.PreparedShare>) -> Unit
    ) {
        val doc = _currentDocument.value ?: return
        viewModelScope.launch {
            val res = ShareCoordinator.prepareShare(
                context = getApplication(),
                doc = doc,
                option = option,
                annotations = _currentAnnotations.value,
                activeWatermark = _activeWatermark.value,
                customPdfName = customPdfName
            )
            onResult(res)
        }
    }

    fun exportAnnotatedDocument(onExported: (File?) -> Unit) {
        val doc = _currentDocument.value ?: return
        viewModelScope.launch {
            val res = ShareCoordinator.prepareShare(
                context = getApplication(),
                doc = doc,
                option = ShareCoordinator.ShareOption.PDF_ORIGINAL_NAME,
                annotations = _currentAnnotations.value,
                activeWatermark = _activeWatermark.value
            )
            res.onSuccess { prepared ->
                _statusMessage.value = "PDF ready: ${prepared.displayName}"
                onExported(prepared.stagedFile)
            }.onFailure {
                _statusMessage.value = "Failed to export PDF"
                onExported(null)
            }
        }
    }

    // --- Document Conversion Engine Integrations ---

    fun convertPdfToImages(
        doc: DocumentEntity,
        format: Bitmap.CompressFormat = Bitmap.CompressFormat.PNG,
        dpiScale: Int = 2,
        pageIndices: List<Int>? = null,
        onDone: (List<File>) -> Unit
    ) {
        viewModelScope.launch(Dispatchers.IO) {
            val context = getApplication<Application>()
            val sourceFile = File(doc.filePath)
            val images = DocumentConverterEngine.convertPdfToImages(
                context = context,
                pdfFile = sourceFile,
                outputFormat = format,
                dpiScale = dpiScale,
                pageIndices = pageIndices
            )
            withContext(Dispatchers.Main) {
                if (images.isNotEmpty()) {
                    _statusMessage.value = "Converted ${images.size} pages to ${if (format == Bitmap.CompressFormat.PNG) "PNG" else "JPG"} images!"
                } else {
                    _statusMessage.value = "Failed to convert PDF pages"
                }
                onDone(images)
            }
        }
    }

    fun convertImagesToPdfAdvanced(
        imageUris: List<Uri>,
        title: String,
        pageSize: DocumentConverterEngine.PdfPageSize = DocumentConverterEngine.PdfPageSize.A4,
        fitMode: DocumentConverterEngine.ImageFitMode = DocumentConverterEngine.ImageFitMode.FIT_WITH_MARGINS,
        compressionQuality: Int = 90,
        onDone: (DocumentEntity?) -> Unit
    ) {
        viewModelScope.launch(Dispatchers.IO) {
            val context = getApplication<Application>()
            val docsDir = File(context.filesDir, "converted_pdfs").apply { if (!exists()) mkdirs() }
            val cleanTitle = title.ifBlank { "Photos_Compilation" }.replace(" ", "_")
            val outFile = File(docsDir, "${cleanTitle}_${System.currentTimeMillis()}.pdf")

            val success = DocumentConverterEngine.convertImagesToPdfAdvanced(
                context = context,
                imageUris = imageUris,
                outputFile = outFile,
                pageSize = pageSize,
                fitMode = fitMode,
                compressionQuality = compressionQuality
            )

            if (success) {
                val pages = countPdfPagesSafely(outFile)

                val newDoc = DocumentEntity(
                    uriString = Uri.fromFile(outFile).toString(),
                    title = cleanTitle,
                    filePath = outFile.absolutePath,
                    pageCount = pages,
                    fileSizeBytes = outFile.length()
                )
                val id = dao.insertDocument(newDoc)
                withContext(Dispatchers.Main) {
                    _statusMessage.value = "Created PDF with $pages pages!"
                    val inserted = newDoc.copy(id = id)
                    openDocument(inserted)
                    onDone(inserted)
                }
            } else {
                withContext(Dispatchers.Main) {
                    _statusMessage.value = "Failed to convert images to PDF"
                    onDone(null)
                }
            }
        }
    }

    fun convertWordToPdf(
        wordUri: Uri,
        docTitle: String,
        onDone: (DocumentEntity?) -> Unit
    ) {
        viewModelScope.launch(Dispatchers.IO) {
            val context = getApplication<Application>()
            val docsDir = File(context.filesDir, "word_converted_pdfs").apply { if (!exists()) mkdirs() }
            val cleanTitle = docTitle.ifBlank { "Word_Document" }
                .replace(".docx", "")
                .replace(".doc", "")
                .replace(" ", "_")
            val outFile = File(docsDir, "${cleanTitle}_${System.currentTimeMillis()}.pdf")

            val success = DocumentConverterEngine.convertWordToPdf(
                context = context,
                wordUri = wordUri,
                outputFile = outFile,
                docTitle = cleanTitle
            )

            if (success) {
                val pages = countPdfPagesSafely(outFile)

                val newDoc = DocumentEntity(
                    uriString = Uri.fromFile(outFile).toString(),
                    title = cleanTitle,
                    filePath = outFile.absolutePath,
                    pageCount = pages,
                    fileSizeBytes = outFile.length()
                )
                val id = dao.insertDocument(newDoc)
                withContext(Dispatchers.Main) {
                    _statusMessage.value = "Converted Word to $pages-page PDF!"
                    val inserted = newDoc.copy(id = id)
                    openDocument(inserted)
                    onDone(inserted)
                }
            } else {
                withContext(Dispatchers.Main) {
                    _statusMessage.value = "Failed to convert Word document"
                    onDone(null)
                }
            }
        }
    }

    fun convertPdfToWord(
        doc: DocumentEntity,
        onDone: (File?) -> Unit
    ) {
        viewModelScope.launch(Dispatchers.IO) {
            val context = getApplication<Application>()
            val docsDir = File(context.filesDir, "word_exports").apply { if (!exists()) mkdirs() }
            val cleanTitle = doc.title.replace(".pdf", "").replace(" ", "_")
            val outFile = File(docsDir, "${cleanTitle}_converted.docx")
            val sourceFile = File(doc.filePath)

            val success = DocumentConverterEngine.convertPdfToWord(
                context = context,
                pdfFile = sourceFile,
                outputDocxFile = outFile,
                docTitle = cleanTitle
            )

            withContext(Dispatchers.Main) {
                if (success) {
                    _statusMessage.value = "Converted PDF to Word (.docx)!"
                    onDone(outFile)
                } else {
                    _statusMessage.value = "Failed to convert PDF to Word"
                    onDone(null)
                }
            }
        }
    }

    fun convertPdfToTxt(
        doc: DocumentEntity,
        onDone: (File?) -> Unit
    ) {
        viewModelScope.launch(Dispatchers.IO) {
            val context = getApplication<Application>()
            val docsDir = File(context.filesDir, "txt_exports").apply { if (!exists()) mkdirs() }
            val cleanTitle = doc.title.replace(".pdf", "").replace(" ", "_")
            val outFile = File(docsDir, "${cleanTitle}_converted.txt")
            val sourceFile = File(doc.filePath)

            val success = DocumentConverterEngine.convertPdfToTxt(
                pdfFile = sourceFile,
                outputTxtFile = outFile,
                docTitle = cleanTitle,
                context = context
            )

            withContext(Dispatchers.Main) {
                if (success) {
                    _statusMessage.value = "Converted PDF to TXT!"
                    onDone(outFile)
                } else {
                    _statusMessage.value = "Failed to convert PDF to TXT"
                    onDone(null)
                }
            }
        }
    }

    fun convertTxtUriToPdf(
        txtUri: Uri,
        docTitle: String,
        onDone: (DocumentEntity?) -> Unit
    ) {
        viewModelScope.launch(Dispatchers.IO) {
            val context = getApplication<Application>()
            val docsDir = File(context.filesDir, "txt_converted_pdfs").apply { if (!exists()) mkdirs() }
            val cleanTitle = docTitle.ifBlank { "Text_Document" }
                .replace(".txt", "", ignoreCase = true)
                .replace(" ", "_")
            val outFile = File(docsDir, "${cleanTitle}_${System.currentTimeMillis()}.pdf")

            val success = DocumentConverterEngine.convertTxtToPdf(
                context = context,
                txtUri = txtUri,
                outputFile = outFile,
                docTitle = cleanTitle
            )

            if (success) {
                val pages = countPdfPagesSafely(outFile)

                val newDoc = DocumentEntity(
                    uriString = Uri.fromFile(outFile).toString(),
                    title = cleanTitle,
                    filePath = outFile.absolutePath,
                    pageCount = pages,
                    fileSizeBytes = outFile.length()
                )
                val id = dao.insertDocument(newDoc)
                withContext(Dispatchers.Main) {
                    _statusMessage.value = "Converted TXT to $pages-page PDF!"
                    val inserted = newDoc.copy(id = id)
                    openDocument(inserted)
                    onDone(inserted)
                }
            } else {
                withContext(Dispatchers.Main) {
                    _statusMessage.value = "Failed to convert TXT to PDF"
                    onDone(null)
                }
            }
        }
    }

    fun stampWatermarkOnDocument(
        doc: DocumentEntity,
        options: DocumentConverterEngine.WatermarkOptions,
        onDone: (DocumentEntity?) -> Unit
    ) {
        viewModelScope.launch(Dispatchers.IO) {
            val context = getApplication<Application>()
            val docsDir = File(context.filesDir, "watermarked_pdfs").apply { if (!exists()) mkdirs() }
            val cleanTitle = "${doc.title.replace(".pdf", "")}_watermarked"
            val outFile = File(docsDir, "${cleanTitle}_${System.currentTimeMillis()}.pdf")
            val sourceFile = File(doc.filePath)

            val success = DocumentConverterEngine.stampWatermarkOnPdf(
                sourcePdf = sourceFile,
                outputPdf = outFile,
                options = options
            )

            if (success) {
                val pages = countPdfPagesSafely(outFile)

                val newDoc = DocumentEntity(
                    uriString = Uri.fromFile(outFile).toString(),
                    title = cleanTitle,
                    filePath = outFile.absolutePath,
                    pageCount = pages,
                    fileSizeBytes = outFile.length()
                )
                val id = dao.insertDocument(newDoc)
                withContext(Dispatchers.Main) {
                    _statusMessage.value = "Watermark stamped on $pages pages!"
                    val inserted = newDoc.copy(id = id)
                    openDocument(inserted)
                    onDone(inserted)
                }
            } else {
                withContext(Dispatchers.Main) {
                    _statusMessage.value = "Failed to apply watermark"
                    onDone(null)
                }
            }
        }
    }

    // --- Signatures Vault ---
    fun saveSignature(title: String, pathsJson: String) {
        viewModelScope.launch(Dispatchers.IO) {
            dao.insertSignature(SignatureEntity(title = title, pathsJson = pathsJson))
            _statusMessage.value = "Signature saved to vault!"
        }
    }

    fun deleteSignature(id: Long) {
        viewModelScope.launch(Dispatchers.IO) {
            dao.deleteSignatureById(id)
        }
    }

    // --- Tools Hub Actions ---
    fun createPdfFromImages(imageUris: List<Uri>, title: String, onDone: () -> Unit) {
        viewModelScope.launch(Dispatchers.IO) {
            val context = getApplication<Application>()
            val docsDir = File(context.filesDir, "scanned_pdfs")
            if (!docsDir.exists()) docsDir.mkdirs()

            val cleanTitle = title.ifBlank { "Scan_${System.currentTimeMillis()}" }
            val outFile = File(docsDir, "${cleanTitle.replace(' ', '_')}.pdf")

            val success = PdfExportEngine.convertImagesToPdf(context, imageUris, outFile)
            if (success) {
                val pages = countPdfPagesSafely(outFile)

                val newDoc = DocumentEntity(
                    uriString = Uri.fromFile(outFile).toString(),
                    title = cleanTitle,
                    filePath = outFile.absolutePath,
                    pageCount = pages,
                    fileSizeBytes = outFile.length()
                )
                val id = dao.insertDocument(newDoc)
                withContext(Dispatchers.Main) {
                    _statusMessage.value = "PDF created with $pages pages!"
                    openDocument(newDoc.copy(id = id))
                    onDone()
                }
            } else {
                withContext(Dispatchers.Main) {
                    _statusMessage.value = "Failed to convert images to PDF"
                    onDone()
                }
            }
        }
    }

    fun mergePdfs(docsToMerge: List<DocumentEntity>, mergedTitle: String, onDone: () -> Unit) {
        viewModelScope.launch(Dispatchers.IO) {
            val context = getApplication<Application>()
            val docsDir = File(context.filesDir, "merged_pdfs")
            if (!docsDir.exists()) docsDir.mkdirs()

            val cleanTitle = mergedTitle.ifBlank { "Merged_${System.currentTimeMillis()}" }
            val outFile = File(docsDir, "${cleanTitle.replace(' ', '_')}.pdf")

            val files = docsToMerge.map { File(it.filePath) }
            val success = PdfExportEngine.mergePdfs(files, outFile)
            if (success) {
                val pages = countPdfPagesSafely(outFile)

                val newDoc = DocumentEntity(
                    uriString = Uri.fromFile(outFile).toString(),
                    title = cleanTitle,
                    filePath = outFile.absolutePath,
                    pageCount = pages,
                    fileSizeBytes = outFile.length()
                )
                val id = dao.insertDocument(newDoc)
                withContext(Dispatchers.Main) {
                    _statusMessage.value = "Merged into $pages-page document!"
                    openDocument(newDoc.copy(id = id))
                    onDone()
                }
            } else {
                withContext(Dispatchers.Main) {
                    _statusMessage.value = "Failed to merge PDF documents"
                    onDone()
                }
            }
        }
    }

    fun mergePdfsFromItems(
        pdfItems: List<com.example.data.pdf.PdfMergeItem>,
        mergedTitle: String,
        insertSeparators: Boolean,
        onDone: (DocumentEntity?) -> Unit
    ) {
        viewModelScope.launch(Dispatchers.IO) {
            val context = getApplication<Application>()
            val docsDir = File(context.filesDir, "merged_pdfs")
            if (!docsDir.exists()) docsDir.mkdirs()

            val cleanTitle = mergedTitle.trim().ifBlank {
                "Merged_${java.text.SimpleDateFormat("yyyyMMdd_HHmmss", java.util.Locale.getDefault()).format(java.util.Date())}"
            }
            val outFile = File(docsDir, "${cleanTitle.replace(' ', '_')}.pdf")

            val success = PdfExportEngine.mergePdfsFromUris(context, pdfItems, outFile, insertSeparators)
            if (success) {
                val pages = countPdfPagesSafely(outFile)

                val newDoc = DocumentEntity(
                    uriString = Uri.fromFile(outFile).toString(),
                    title = cleanTitle,
                    filePath = outFile.absolutePath,
                    pageCount = pages,
                    fileSizeBytes = outFile.length()
                )
                val id = dao.insertDocument(newDoc)
                val insertedDoc = newDoc.copy(id = id)
                withContext(Dispatchers.Main) {
                    _statusMessage.value = "Merged ${pdfItems.size} files into $pages-page PDF!"
                    onDone(insertedDoc)
                }
            } else {
                withContext(Dispatchers.Main) {
                    _statusMessage.value = "Failed to merge PDF documents"
                    onDone(null)
                }
            }
        }
    }

    fun organizeDocumentPages(
        doc: DocumentEntity,
        includedIndices: List<Int>,
        rotations: Map<Int, Int>,
        newTitle: String,
        onDone: () -> Unit
    ) {
        organizeDocumentPagesWithRotations(
            doc = doc,
            orderedPages = includedIndices.map { idx -> idx to (rotations[idx] ?: 0) },
            newTitle = newTitle,
            onDone = onDone
        )
    }

    fun organizeDocumentPagesWithRotations(
        doc: DocumentEntity,
        orderedPages: List<Pair<Int, Int>>,
        newTitle: String,
        onDone: () -> Unit
    ) {
        viewModelScope.launch(Dispatchers.IO) {
            val context = getApplication<Application>()
            val docsDir = File(context.filesDir, "organized_pdfs")
            if (!docsDir.exists()) docsDir.mkdirs()

            val cleanTitle = newTitle.ifBlank { "${doc.title}_organized" }
            val outFile = File(docsDir, "${cleanTitle.replace(' ', '_')}.pdf")
            val source = File(doc.filePath)

            val success = PdfExportEngine.organizePagesWithRotations(source, orderedPages, outFile)
            if (success) {
                val pages = countPdfPagesSafely(outFile)

                val newDoc = DocumentEntity(
                    uriString = Uri.fromFile(outFile).toString(),
                    title = cleanTitle,
                    filePath = outFile.absolutePath,
                    pageCount = pages,
                    fileSizeBytes = outFile.length()
                )
                val id = dao.insertDocument(newDoc)
                withContext(Dispatchers.Main) {
                    _statusMessage.value = "Pages organized into new PDF!"
                    openDocument(newDoc.copy(id = id))
                    onDone()
                }
            } else {
                withContext(Dispatchers.Main) {
                    _statusMessage.value = "Failed to organize pages"
                    onDone()
                }
            }
        }
    }

    fun deleteCurrentPage(
        doc: DocumentEntity,
        pageIndex: Int,
        onDone: (Boolean) -> Unit = {}
    ) {
        if (doc.pageCount <= 1) {
            _statusMessage.value = "Cannot delete the only page in the document"
            onDone(false)
            return
        }
        val remaining = (0 until doc.pageCount).filter { it != pageIndex }
        val newTargetPage = pageIndex.coerceAtMost(remaining.size - 1)
        val cleanTitle = if (doc.title.endsWith("_edited")) doc.title else "${doc.title}_edited"
        organizeDocumentPages(
            doc = doc,
            includedIndices = remaining,
            rotations = emptyMap(),
            newTitle = cleanTitle
        ) {
            setCurrentPage(newTargetPage)
            _statusMessage.value = "Page ${pageIndex + 1} deleted successfully"
            onDone(true)
        }
    }

    fun rotateCurrentDocumentPage(
        doc: DocumentEntity,
        pageIndex: Int,
        degrees: Int = 90,
        onDone: () -> Unit = {}
    ) {
        organizeDocumentPages(
            doc = doc,
            includedIndices = (0 until doc.pageCount).toList(),
            rotations = mapOf(pageIndex to degrees),
            newTitle = "${doc.title}_rotated"
        ) {
            _statusMessage.value = "Page ${pageIndex + 1} rotated"
            onDone()
        }
    }

    fun extractPagesToNewDocument(
        doc: DocumentEntity,
        includedIndices: List<Int>,
        rotations: Map<Int, Int>,
        newTitle: String,
        onDone: () -> Unit
    ) {
        extractPagesWithRotations(
            doc = doc,
            orderedPages = includedIndices.map { idx -> idx to (rotations[idx] ?: 0) },
            newTitle = newTitle,
            onDone = onDone
        )
    }

    fun extractPagesWithRotations(
        doc: DocumentEntity,
        orderedPages: List<Pair<Int, Int>>,
        newTitle: String,
        onDone: () -> Unit
    ) {
        viewModelScope.launch(Dispatchers.IO) {
            val context = getApplication<Application>()
            val docsDir = File(context.filesDir, "extracted_pdfs")
            if (!docsDir.exists()) docsDir.mkdirs()

            val cleanTitle = newTitle.ifBlank { "${doc.title}_extracted" }
            val outFile = File(docsDir, "${cleanTitle.replace(' ', '_')}.pdf")
            val source = File(doc.filePath)

            val success = PdfExportEngine.organizePagesWithRotations(source, orderedPages, outFile)
            if (success) {
                val pages = countPdfPagesSafely(outFile)

                val newDoc = DocumentEntity(
                    uriString = Uri.fromFile(outFile).toString(),
                    title = cleanTitle,
                    filePath = outFile.absolutePath,
                    pageCount = pages,
                    fileSizeBytes = outFile.length()
                )
                val id = dao.insertDocument(newDoc)
                withContext(Dispatchers.Main) {
                    _statusMessage.value = "Extracted $pages pages into new PDF!"
                    openDocument(newDoc.copy(id = id))
                    onDone()
                }
            } else {
                withContext(Dispatchers.Main) {
                    _statusMessage.value = "Failed to extract pages"
                    onDone()
                }
            }
        }
    }

    fun organizeDocumentPagesAdvanced(
        doc: DocumentEntity,
        slots: List<PdfExportEngine.PageSlotSpec>,
        newTitle: String,
        onDone: () -> Unit
    ) {
        viewModelScope.launch(Dispatchers.IO) {
            val context = getApplication<Application>()
            val docsDir = File(context.filesDir, "organized_pdfs").apply { if (!exists()) mkdirs() }
            val cleanTitle = StorageRenameCoordinator.sanitizeForStorageFileName(
                newTitle,
                fallback = "${doc.title}_organized"
            )
            val outFile = File(docsDir, "${cleanTitle}_${System.currentTimeMillis()}.pdf")
            val success = PdfExportEngine.organizePagesAdvanced(
                context = context,
                sourceFile = File(doc.filePath),
                slots = slots,
                outputFile = outFile
            )
            if (success && outFile.exists() && outFile.length() > 0L) {
                val pages = countPdfPagesSafely(outFile)
                val newDoc = DocumentEntity(
                    uriString = Uri.fromFile(outFile).toString(),
                    title = cleanTitle,
                    filePath = outFile.absolutePath,
                    pageCount = pages,
                    fileSizeBytes = outFile.length(),
                    folderCategory = doc.folderCategory
                )
                val id = dao.insertDocument(newDoc)
                withContext(Dispatchers.Main) {
                    _statusMessage.value = "Updated document ($pages pages) saved!"
                    openDocument(newDoc.copy(id = id))
                    onDone()
                }
            } else {
                withContext(Dispatchers.Main) {
                    _statusMessage.value = "Failed to organize pages"
                    onDone()
                }
            }
        }
    }

    fun splitPdfDocument(
        doc: DocumentEntity,
        pageGroups: List<List<Int>>,
        baseTitle: String,
        onDone: (List<DocumentEntity>) -> Unit = {}
    ) {
        viewModelScope.launch(Dispatchers.IO) {
            val context = getApplication<Application>()
            val splitDir = File(context.filesDir, "split_pdfs").apply { if (!exists()) mkdirs() }
            val createdFiles = PdfExportEngine.splitPdf(
                context = context,
                sourceFile = File(doc.filePath),
                outputDir = splitDir,
                baseTitle = baseTitle,
                pageGroups = pageGroups
            )
            val createdDocs = mutableListOf<DocumentEntity>()
            for (file in createdFiles) {
                val pages = runCatching { countPdfPagesSafely(file) }.getOrDefault(1)
                val newDoc = DocumentEntity(
                    uriString = Uri.fromFile(file).toString(),
                    title = file.nameWithoutExtension,
                    filePath = file.absolutePath,
                    pageCount = pages,
                    fileSizeBytes = file.length(),
                    folderCategory = doc.folderCategory
                )
                val id = dao.insertDocument(newDoc)
                createdDocs.add(newDoc.copy(id = id))
            }
            withContext(Dispatchers.Main) {
                _statusMessage.value = if (createdDocs.isNotEmpty()) {
                    "Split into ${createdDocs.size} PDF files in your library!"
                } else {
                    "Unable to split PDF (verify page range or encryption)"
                }
                onDone(createdDocs)
            }
        }
    }

    fun detectDuplicatePages(
        doc: DocumentEntity,
        onResult: (List<PdfExportEngine.DuplicatePageMatch>) -> Unit
    ) {
        viewModelScope.launch(Dispatchers.IO) {
            val matches = PdfExportEngine.detectDuplicatePages(getApplication(), File(doc.filePath))
            withContext(Dispatchers.Main) {
                _statusMessage.value = if (matches.isEmpty()) {
                    "No duplicate pages detected."
                } else {
                    "Found ${matches.size} likely duplicate page pair(s)."
                }
                onResult(matches)
            }
        }
    }

    fun batchRenameDocuments(
        docs: List<DocumentEntity>,
        prefix: String,
        startNumber: Int = 1,
        onDone: (Int, Int) -> Unit = { _, _ -> }
    ) {
        viewModelScope.launch {
            val cleanPrefix = StorageRenameCoordinator.sanitizeForStorageFileName(prefix, fallback = "Doc")
            var succeeded = 0
            var failed = 0
            docs.forEachIndexed { index, doc ->
                val num = (startNumber + index).toString().padStart(2, '0')
                val targetName = "${cleanPrefix}_$num"
                val result = StorageRenameCoordinator.renameDocument(
                    context = getApplication(),
                    dao = dao,
                    doc = doc,
                    rawNewName = targetName
                )
                if (result is StorageRenameCoordinator.RenameResult.Success) {
                    succeeded++
                    if (_currentDocument.value?.id == doc.id) {
                        _currentDocument.value = result.updatedDoc
                    }
                } else {
                    failed++
                }
            }
            _statusMessage.value = if (failed == 0) {
                "Renamed $succeeded document(s) cleanly."
            } else {
                "Renamed $succeeded document(s); $failed skipped/denied by storage."
            }
            onDone(succeeded, failed)
        }
    }

    fun batchUpdateCategory(docs: List<DocumentEntity>, category: String) {
        val cleanCategory = category.trim().ifBlank { "General" }
        viewModelScope.launch(Dispatchers.IO) {
            docs.forEach { dao.setFolderCategory(it.id, cleanCategory) }
            withContext(Dispatchers.Main) {
                _statusMessage.value = "Assigned ${docs.size} document(s) to $cleanCategory"
            }
        }
    }

    fun batchDeleteDocuments(docs: List<DocumentEntity>) {
        viewModelScope.launch(Dispatchers.IO) {
            docs.forEach { doc ->
                dao.clearAllAnnotationsForDoc(doc.id)
                dao.deleteDocument(doc)
                try {
                    val f = File(doc.filePath)
                    if (f.exists() && !doc.isSample) f.delete()
                } catch (_: Exception) {}
            }
            withContext(Dispatchers.Main) {
                _statusMessage.value = "Deleted ${docs.size} document(s)"
            }
        }
    }

    fun protectPdfWithPassword(
        sourceFile: File,
        baseTitle: String,
        userPassword: String,
        ownerPassword: String,
        allowPrinting: Boolean,
        allowCopying: Boolean,
        keyLengthBits: Int,
        onDone: (Result<DocumentEntity>) -> Unit
    ) {
        viewModelScope.launch(Dispatchers.IO) {
            val context = getApplication<Application>()
            val secDir = File(context.filesDir, "secured_pdfs").apply { if (!exists()) mkdirs() }
            val cleanBase = StorageRenameCoordinator.sanitizeForStorageFileName(baseTitle, fallback = "Document")
            var outFile = File(secDir, "${cleanBase}_protected.pdf")
            var suffix = 1
            while (outFile.exists()) {
                outFile = File(secDir, "${cleanBase}_protected_$suffix.pdf")
                suffix++
            }
            val res = PdfSecurityEngine.encryptPdf(
                context = context,
                sourceFile = sourceFile,
                outputFile = outFile,
                userPassword = userPassword,
                ownerPassword = ownerPassword,
                allowPrinting = allowPrinting,
                allowCopying = allowCopying,
                keyLengthBits = keyLengthBits
            )
            if (res.isSuccess) {
                val pages = runCatching { countPdfPagesSafely(outFile) }.getOrDefault(0)
                val newDoc = DocumentEntity(
                    uriString = Uri.fromFile(outFile).toString(),
                    title = outFile.nameWithoutExtension,
                    filePath = outFile.absolutePath,
                    pageCount = pages,
                    fileSizeBytes = outFile.length(),
                    lastOpenedTimestamp = System.currentTimeMillis()
                )
                val id = dao.insertDocument(newDoc)
                val saved = newDoc.copy(id = id)
                withContext(Dispatchers.Main) {
                    _statusMessage.value = "PDF encrypted (AES-$keyLengthBits) -> ${saved.title}.pdf"
                    onDone(Result.success(saved))
                }
            } else {
                val err = res.exceptionOrNull() ?: IllegalStateException("Encryption failed.")
                withContext(Dispatchers.Main) {
                    _statusMessage.value = err.localizedMessage ?: "Encryption failed."
                    onDone(Result.failure(err))
                }
            }
        }
    }

    fun unlockProtectedPdf(
        sourceFile: File,
        baseTitle: String,
        password: String,
        onDone: (Result<DocumentEntity>) -> Unit
    ) {
        viewModelScope.launch(Dispatchers.IO) {
            val context = getApplication<Application>()
            val secDir = File(context.filesDir, "secured_pdfs").apply { if (!exists()) mkdirs() }
            val cleanBase = StorageRenameCoordinator.sanitizeForStorageFileName(baseTitle, fallback = "Document")
            var outFile = File(secDir, "${cleanBase}_unlocked.pdf")
            var suffix = 1
            while (outFile.exists()) {
                outFile = File(secDir, "${cleanBase}_unlocked_$suffix.pdf")
                suffix++
            }
            val res = PdfSecurityEngine.decryptPdf(
                context = context,
                sourceFile = sourceFile,
                password = password,
                outputFile = outFile
            )
            if (res.isSuccess) {
                val pages = runCatching { countPdfPagesSafely(outFile) }.getOrDefault(1)
                val newDoc = DocumentEntity(
                    uriString = Uri.fromFile(outFile).toString(),
                    title = outFile.nameWithoutExtension,
                    filePath = outFile.absolutePath,
                    pageCount = pages,
                    fileSizeBytes = outFile.length(),
                    lastOpenedTimestamp = System.currentTimeMillis()
                )
                val id = dao.insertDocument(newDoc)
                val saved = newDoc.copy(id = id)
                withContext(Dispatchers.Main) {
                    _statusMessage.value = "Unlocked PDF saved: ${saved.title}.pdf"
                    onDone(Result.success(saved))
                }
            } else {
                val err = res.exceptionOrNull() ?: IllegalStateException("Unlock failed.")
                withContext(Dispatchers.Main) {
                    _statusMessage.value = err.localizedMessage ?: "Unlock failed."
                    onDone(Result.failure(err))
                }
            }
        }
    }

    fun updatePdfMetadata(
        doc: DocumentEntity?,
        sourceFile: File,
        title: String,
        author: String,
        subject: String,
        keywords: String,
        onDone: (Result<String>) -> Unit
    ) {
        viewModelScope.launch(Dispatchers.IO) {
            val context = getApplication<Application>()
            val outFile = if (doc != null) {
                sourceFile
            } else {
                val dir = File(context.filesDir, "edited_metadata").apply { if (!exists()) mkdirs() }
                File(dir, "${StorageRenameCoordinator.sanitizeForStorageFileName(title, "Document")}_meta.pdf")
            }
            val res = PdfSecurityEngine.updateMetadata(
                context = context,
                sourceFile = sourceFile,
                outputFile = outFile,
                title = title,
                author = author,
                subject = subject,
                keywords = keywords
            )
            if (res.isSuccess) {
                if (doc != null) {
                    dao.updateDocumentMetadata(
                        id = doc.id,
                        author = author.trim(),
                        subject = subject.trim(),
                        keywords = keywords.trim(),
                        fileSizeBytes = outFile.length()
                    )
                }
                withContext(Dispatchers.Main) {
                    val msg = "Updated PDF metadata on ${outFile.name}"
                    _statusMessage.value = msg
                    onDone(Result.success(msg))
                }
            } else {
                val err = res.exceptionOrNull() ?: IllegalStateException("Metadata update failed.")
                withContext(Dispatchers.Main) {
                    _statusMessage.value = err.localizedMessage ?: "Metadata update failed."
                    onDone(Result.failure(err))
                }
            }
        }
    }

    fun batchCompressDocuments(
        docs: List<DocumentEntity>,
        preset: PdfCompressorEngine.CompressionPreset,
        onProgress: (Int, Int) -> Unit,
        onDone: (Int) -> Unit
    ) {
        viewModelScope.launch(Dispatchers.IO) {
            val context = getApplication<Application>()
            val outDir = File(context.filesDir, "compressed_pdfs").apply { if (!exists()) mkdirs() }
            var succeeded = 0
            docs.forEachIndexed { idx, doc ->
                val res = PdfCompressorEngine.compressPdf(
                    context = context,
                    inputFile = File(doc.filePath),
                    options = PdfCompressorEngine.CompressionOptions(preset = preset)
                )
                val produced = res.outputFile
                if (res.isSuccess && produced != null && produced.exists()) {
                    val dest = File(outDir, "${StorageRenameCoordinator.sanitizeForStorageFileName(doc.title)}_compressed_${System.currentTimeMillis()}.pdf")
                    produced.copyTo(dest, overwrite = true)
                    produced.delete()
                    val newDoc = DocumentEntity(
                        uriString = Uri.fromFile(dest).toString(),
                        title = "${doc.title}_compressed",
                        filePath = dest.absolutePath,
                        pageCount = res.pageCount.coerceAtLeast(1),
                        fileSizeBytes = dest.length(),
                        folderCategory = doc.folderCategory
                    )
                    dao.insertDocument(newDoc)
                    succeeded++
                }
                withContext(Dispatchers.Main) { onProgress(idx + 1, docs.size) }
            }
            withContext(Dispatchers.Main) {
                _statusMessage.value = "Batch compressed $succeeded of ${docs.size} PDFs."
                onDone(succeeded)
            }
        }
    }

    fun batchWatermarkDocuments(
        docs: List<DocumentEntity>,
        options: DocumentConverterEngine.WatermarkOptions,
        onProgress: (Int, Int) -> Unit,
        onDone: (Int) -> Unit
    ) {
        viewModelScope.launch(Dispatchers.IO) {
            val context = getApplication<Application>()
            val outDir = File(context.filesDir, "watermarked_pdfs").apply { if (!exists()) mkdirs() }
            var succeeded = 0
            docs.forEachIndexed { idx, doc ->
                val dest = File(outDir, "${StorageRenameCoordinator.sanitizeForStorageFileName(doc.title)}_watermarked_${System.currentTimeMillis()}.pdf")
                val ok = DocumentConverterEngine.stampWatermarkOnPdf(File(doc.filePath), dest, options)
                if (ok && dest.exists() && dest.length() > 0L) {
                    val pages = runCatching { countPdfPagesSafely(dest) }.getOrDefault(doc.pageCount)
                    val newDoc = DocumentEntity(
                        uriString = Uri.fromFile(dest).toString(),
                        title = "${doc.title}_watermarked",
                        filePath = dest.absolutePath,
                        pageCount = pages,
                        fileSizeBytes = dest.length(),
                        folderCategory = doc.folderCategory
                    )
                    dao.insertDocument(newDoc)
                    succeeded++
                } else {
                    dest.delete()
                }
                withContext(Dispatchers.Main) { onProgress(idx + 1, docs.size) }
            }
            withContext(Dispatchers.Main) {
                _statusMessage.value = "Batch watermarked $succeeded of ${docs.size} PDFs."
                onDone(succeeded)
            }
        }
    }

    fun applyPermanentRedaction(
        doc: DocumentEntity,
        overwriteOriginal: Boolean,
        onResult: (PdfRedactionEngine.RedactionResult) -> Unit
    ) {
        viewModelScope.launch(Dispatchers.IO) {
            val context = getApplication<Application>()
            val sourceFile = File(doc.filePath)
            val allAnnotations = dao.getAllAnnotationsForDoc(doc.id)
            val targetOutFile = if (overwriteOriginal) {
                File(context.cacheDir, "redact_overwrite_${doc.id}_${System.currentTimeMillis()}.pdf")
            } else {
                val redactedDir = File(context.filesDir, "redacted_pdfs").apply { if (!exists()) mkdirs() }
                val base = StorageRenameCoordinator.sanitizeForStorageFileName(doc.title, fallback = "Document")
                var candidate = File(redactedDir, "${base}_redacted.pdf")
                var idx = 1
                while (candidate.exists()) {
                    candidate = File(redactedDir, "${base}_redacted_$idx.pdf")
                    idx++
                }
                candidate
            }

            val result = PdfRedactionEngine.applyPermanentRedaction(
                context = context,
                sourceFile = sourceFile,
                outputFile = targetOutFile,
                annotations = allAnnotations
            )

            if (!result.success || !result.verifiedUnrecoverable) {
                if (overwriteOriginal && targetOutFile.exists()) targetOutFile.delete()
                withContext(Dispatchers.Main) {
                    _statusMessage.value = result.errorMessage ?: "Permanent redaction verification failed."
                    onResult(result)
                }
                return@launch
            }

            if (overwriteOriginal) {
                try {
                    rendererManager?.close()
                    rendererManager = null
                    targetOutFile.copyTo(sourceFile, overwrite = true)
                    targetOutFile.delete()

                    // Scrub metadata columns & remove applied REDACT boxes from Room DB
                    allAnnotations.filter { it.type == "REDACT" }.forEach { dao.deleteAnnotationById(it.id) }
                    val updatedDoc = doc.copy(
                        fileSizeBytes = sourceFile.length(),
                        lastOpenedTimestamp = System.currentTimeMillis(),
                        author = "",
                        subject = "",
                        keywords = ""
                    )
                    dao.updateDocument(updatedDoc)
                    cachedExtractedPath = null
                    cachedLayoutFileKey = ""
                    pageLayoutCache.clear()

                    withContext(Dispatchers.Main) {
                        _statusMessage.value = "Permanent redaction verified & applied (${result.boxesApplied} box(es))."
                        openDocument(updatedDoc)
                        onResult(result.copy(outputFile = sourceFile))
                    }
                } catch (e: Exception) {
                    targetOutFile.delete()
                    withContext(Dispatchers.Main) {
                        val err = result.copy(
                            success = false,
                            verifiedUnrecoverable = false,
                            errorMessage = e.localizedMessage ?: "Failed to overwrite original PDF."
                        )
                        _statusMessage.value = err.errorMessage
                        onResult(err)
                    }
                }
            } else {
                val pages = runCatching { countPdfPagesSafely(targetOutFile) }.getOrDefault(doc.pageCount)
                val cleanTitle = targetOutFile.nameWithoutExtension
                val newDoc = DocumentEntity(
                    uriString = Uri.fromFile(targetOutFile).toString(),
                    title = cleanTitle,
                    filePath = targetOutFile.absolutePath,
                    pageCount = pages,
                    fileSizeBytes = targetOutFile.length(),
                    lastOpenedTimestamp = System.currentTimeMillis(),
                    folderCategory = doc.folderCategory
                )
                val newId = dao.insertDocument(newDoc)
                val savedDoc = newDoc.copy(id = newId)
                withContext(Dispatchers.Main) {
                    _statusMessage.value = "Verified sanitized copy saved: $cleanTitle.pdf"
                    openDocument(savedDoc)
                    onResult(result)
                }
            }
        }
    }

    // --- Digital Signature Stamping onto Active Document ---
    fun stampSignature(
        pageIndex: Int,
        isTyped: Boolean,
        typedText: String = "",
        strokePathsJson: String = "",
        colorHex: Long = 0xFF1E3A8AL,
        posX: Float = 0.60f,
        posY: Float = 0.80f,
        saveToVault: Boolean = false,
        vaultTitle: String = "My Signature"
    ) {
        val doc = _currentDocument.value ?: return
        viewModelScope.launch(Dispatchers.IO) {
            if (saveToVault) {
                val pathsToStore = if (isTyped) {
                    val typedObj = org.json.JSONObject()
                    typedObj.put("type", "TYPED")
                    typedObj.put("text", typedText)
                    typedObj.put("colorHex", colorHex)
                    typedObj.toString()
                } else {
                    strokePathsJson
                }
                dao.insertSignature(
                    SignatureEntity(
                        title = vaultTitle.ifBlank { if (isTyped) typedText else "Drawn Signature" },
                        pathsJson = pathsToStore
                    )
                )
            }

            val annotation = AnnotationEntity(
                documentId = doc.id,
                pageIndex = pageIndex,
                type = if (isTyped) "TYPED_SIGNATURE" else "SIGNATURE",
                pointsJson = if (isTyped) "" else strokePathsJson,
                textContent = if (isTyped) typedText else "",
                colorHex = colorHex,
                strokeWidth = 26f,
                posX = posX,
                posY = posY,
                width = if (isTyped) 0f else 0.30f,
                height = if (isTyped) 0f else 0.12f
            )
            val id = dao.insertAnnotation(annotation)
            _currentAnnotations.value = _currentAnnotations.value + annotation.copy(id = id)

            withContext(Dispatchers.Main) {
                setCurrentPage(pageIndex)
                _statusMessage.value = "Signature stamped on Page ${pageIndex + 1}!"
                _isSignatureDialogVisible.value = false
            }
        }
    }

    fun clearStatusMessage() {
        _statusMessage.value = null
    }

    fun toggleLiquidMode() {
        _isLiquidMode.value = !_isLiquidMode.value
    }

    fun startReadAloud(customText: String? = null) {
        val doc = _currentDocument.value ?: return
        val pageIdx = _currentPage.value
        val total = _totalPages.value
        if (!customText.isNullOrBlank()) {
            ttsManager.speak(customText)
            return
        }
        viewModelScope.launch(Dispatchers.IO) {
            val pdfFile = File(doc.filePath)
            val extracted = if (pdfFile.exists() && pdfFile.length() > 0L) {
                extractPdfPageText(pdfFile, pageIdx).trim()
            } else ""
            val textToSpeak = extracted.ifBlank {
                "Reading document: ${doc.title}. Page ${pageIdx + 1} of $total. This page appears to be an image or scanned sheet without embedded text."
            }
            withContext(Dispatchers.Main) {
                ttsManager.speak(textToSpeak)
            }
        }
    }

    fun stopReadAloud() {
        ttsManager.stop()
    }

    fun setTtsSpeed(speed: Float) {
        ttsManager.setSpeed(speed)
    }

    override fun onCleared() {
        super.onCleared()
        rendererManager?.close()
        ttsManager.shutdown()
    }
}

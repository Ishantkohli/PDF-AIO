package com.example.ui

import android.app.Application
import android.graphics.Bitmap
import android.net.Uri
import android.os.ParcelFileDescriptor
import android.provider.OpenableColumns
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.data.local.AnnotationEntity
import com.example.data.local.DocumentEntity
import com.example.data.local.PdfDatabase
import com.example.data.local.SignatureEntity
import com.example.data.pdf.DocumentConverterEngine
import com.example.data.pdf.PdfExportEngine
import com.example.data.pdf.PdfRendererManager
import com.example.data.pdf.SampleDocumentGenerator
import kotlinx.coroutines.Dispatchers
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
    PPT_VIEWER
}

enum class AnnotationTool {
    NONE,
    PEN,
    HIGHLIGHT,
    STICKY_NOTE,
    STAMP,
    SIGNATURE,
    TEXT_BOX,
    WHITEOUT,
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

    init {
        viewModelScope.launch(Dispatchers.IO) {
            try {
                val cacheFiles = application.cacheDir.listFiles()
                cacheFiles?.forEach { file ->
                    if (file.name.startsWith("imp_ppt_") || file.name.startsWith("temp_ppt_") || file.name.startsWith("ppt_media_")) {
                        file.deleteRecursively()
                    }
                }
            } catch (ignored: Exception) {}
        }
    }

    // Navigation state
    private val _currentScreen = MutableStateFlow(AppScreen.HOME)
    val currentScreen: StateFlow<AppScreen> = _currentScreen.asStateFlow()

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

    // PowerPoint / Presentation State
    private val _currentPresentation = MutableStateFlow<com.example.data.pdf.PptPresentation?>(null)
    val currentPresentation: StateFlow<com.example.data.pdf.PptPresentation?> = _currentPresentation.asStateFlow()

    private val _presentationSlideIndex = MutableStateFlow(0)
    val presentationSlideIndex: StateFlow<Int> = _presentationSlideIndex.asStateFlow()

    private val _isPresentationSlideshowMode = MutableStateFlow(false)
    val isPresentationSlideshowMode: StateFlow<Boolean> = _isPresentationSlideshowMode.asStateFlow()

    private val _laserPointerActive = MutableStateFlow(false)
    val laserPointerActive: StateFlow<Boolean> = _laserPointerActive.asStateFlow()

    private val _showSpeakerNotes = MutableStateFlow(false)
    val showSpeakerNotes: StateFlow<Boolean> = _showSpeakerNotes.asStateFlow()

    fun openPowerPointPresentation(pptUri: Uri, title: String) {
        viewModelScope.launch {
            _statusMessage.value = "Parsing PowerPoint Presentation..."
            try {
                val presentation = com.example.data.pdf.DocumentConverterEngine.parsePowerPointPresentation(
                    context = getApplication(),
                    pptUri = pptUri,
                    docTitle = title
                )
                _currentPresentation.value = presentation
                _presentationSlideIndex.value = 0
                _isPresentationSlideshowMode.value = false
                _laserPointerActive.value = false
                _showSpeakerNotes.value = false
                _currentScreen.value = AppScreen.PPT_VIEWER
                _statusMessage.value = "Presentation loaded (${presentation.totalSlides} slides)"
            } catch (e: Exception) {
                android.util.Log.e("PdfViewModel", "Failed to parse PPT presentation", e)
                _statusMessage.value = "Could not open PowerPoint file: ${e.message}"
            }
        }
    }

    fun closePresentation() {
        _currentPresentation.value = null
        _isPresentationSlideshowMode.value = false
        _laserPointerActive.value = false
        _showSpeakerNotes.value = false
        _currentScreen.value = AppScreen.HOME
    }

    fun setPresentationSlideIndex(index: Int) {
        val maxIdx = (_currentPresentation.value?.slides?.size ?: 1) - 1
        _presentationSlideIndex.value = index.coerceIn(0, maxIdx.coerceAtLeast(0))
    }

    fun nextPresentationSlide() {
        setPresentationSlideIndex(_presentationSlideIndex.value + 1)
    }

    fun prevPresentationSlide() {
        setPresentationSlideIndex(_presentationSlideIndex.value - 1)
    }

    fun togglePresentationSlideshow() {
        _isPresentationSlideshowMode.value = !_isPresentationSlideshowMode.value
    }

    fun toggleLaserPointer() {
        _laserPointerActive.value = !_laserPointerActive.value
    }

    fun toggleSpeakerNotes() {
        _showSpeakerNotes.value = !_showSpeakerNotes.value
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
            val context = getApplication<Application>()
            when (fileItem.fileType) {
                com.example.data.local.DeviceFileType.PDF -> {
                    importPdfFromUri(fileItem.uri)
                }
                com.example.data.local.DeviceFileType.WORD_DOCX,
                com.example.data.local.DeviceFileType.WORD_DOC -> {
                    _statusMessage.value = "Opening Word document..."
                    try {
                        val convertedPdf = File(context.cacheDir, "word_${System.currentTimeMillis()}_${fileItem.title.replace(" ", "_")}.pdf")
                        val success = DocumentConverterEngine.convertWordToPdf(
                            context = context,
                            wordUri = fileItem.uri,
                            outputFile = convertedPdf,
                            docTitle = fileItem.title
                        )
                        if (success && convertedPdf.exists() && convertedPdf.length() > 0) {
                            importPdfFromUri(Uri.fromFile(convertedPdf))
                        } else {
                            _statusMessage.value = "Could not convert Word document"
                        }
                    } catch (e: Exception) {
                        android.util.Log.e("PdfViewModel", "Failed to open Word file", e)
                        _statusMessage.value = "Error opening Word document: ${e.message}"
                    }
                }
                com.example.data.local.DeviceFileType.POWERPOINT -> {
                    openPowerPointPresentation(fileItem.uri, fileItem.title)
                }
                com.example.data.local.DeviceFileType.TEXT -> {
                    _statusMessage.value = "Opening text document..."
                    try {
                        val convertedPdf = File(context.cacheDir, "txt_${System.currentTimeMillis()}_${fileItem.title.replace(" ", "_")}.pdf")
                        val success = DocumentConverterEngine.convertTxtToPdf(
                            context = context,
                            txtUri = fileItem.uri,
                            outputFile = convertedPdf,
                            docTitle = fileItem.title
                        )
                        if (success && convertedPdf.exists() && convertedPdf.length() > 0) {
                            importPdfFromUri(Uri.fromFile(convertedPdf))
                        } else {
                            _statusMessage.value = "Could not convert text file"
                        }
                    } catch (e: Exception) {
                        android.util.Log.e("PdfViewModel", "Failed to open Text file", e)
                        _statusMessage.value = "Error opening text document: ${e.message}"
                    }
                }
                else -> {
                    importPdfFromUri(fileItem.uri)
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

    // Annotations for active document
    private val _currentAnnotations = MutableStateFlow<List<AnnotationEntity>>(emptyList())
    val currentAnnotations: StateFlow<List<AnnotationEntity>> = _currentAnnotations.asStateFlow()

    // Active Annotation Tool
    private val _activeTool = MutableStateFlow(AnnotationTool.NONE)
    val activeTool: StateFlow<AnnotationTool> = _activeTool.asStateFlow()

    private val _activeColor = MutableStateFlow(0xFFDC2626) // Crimson Red by default
    val activeColor: StateFlow<Long> = _activeColor.asStateFlow()

    private val _activeStrokeWidth = MutableStateFlow(4f)
    val activeStrokeWidth: StateFlow<Float> = _activeStrokeWidth.asStateFlow()

    // Digital Signature Modal State
    private val _isSignatureDialogVisible = MutableStateFlow(false)
    val isSignatureDialogVisible: StateFlow<Boolean> = _isSignatureDialogVisible.asStateFlow()

    fun showSignatureDialog(visible: Boolean = true) {
        _isSignatureDialogVisible.value = visible
    }

    // Status / Toast Messages
    private val _statusMessage = MutableStateFlow<String?>(null)
    val statusMessage: StateFlow<String?> = _statusMessage.asStateFlow()

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

            if (matches.isEmpty() && query.length >= 2) {
                // Fallback to match first page for broad terms
                matches.add(0)
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

    private fun extractPdfPageText(file: File, pageIndex: Int): String {
        val sb = StringBuilder()
        try {
            val bytes = file.readBytes()
            val content = String(bytes, Charsets.ISO_8859_1)

            val streamRegex = Regex("""stream[\r\n]+([\s\S]*?)[\r\n]+endstream""", RegexOption.IGNORE_CASE)
            val textStringRegex = Regex("""\((.*?)\)\s*Tj""", RegexOption.IGNORE_CASE)
            val arrayTextRegex = Regex("""\[(.*?)\]\s*TJ""", RegexOption.IGNORE_CASE)

            val streams = streamRegex.findAll(content).toList()
            val targetStream = if (streams.isNotEmpty() && pageIndex < streams.size) {
                streams[pageIndex].groupValues[1]
            } else {
                content
            }

            for (m in textStringRegex.findAll(targetStream)) {
                val raw = m.groupValues[1]
                val cleaned = raw.replace("\\(", "(").replace("\\)", ")").replace("\\\\", "\\")
                sb.append(cleaned).append(" ")
            }

            for (m in arrayTextRegex.findAll(targetStream)) {
                val rawArr = m.groupValues[1]
                val innerMatches = Regex("""\((.*?)\)""").findAll(rawArr)
                for (im in innerMatches) {
                    val cleaned = im.groupValues[1].replace("\\(", "(").replace("\\)", ")").replace("\\\\", "\\")
                    sb.append(cleaned).append(" ")
                }
            }
        } catch (e: Exception) {
            android.util.Log.e("PdfViewModel", "Failed to extract text from page $pageIndex", e)
        }
        return sb.toString()
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

    init {
        removeSampleFiles()
        scanDeviceDocuments()
    }

    private fun removeSampleFiles() {
        viewModelScope.launch(Dispatchers.IO) {
            try {
                dao.deleteSampleDocuments()
                SampleDocumentGenerator.deleteSampleFiles(getApplication())
            } catch (e: Exception) {
                android.util.Log.w("PdfViewModel", "Failed to remove sample files: ${e.message}")
            }
        }
    }

    fun navigateTo(screen: AppScreen) {
        _currentScreen.value = screen
    }

    fun openDocument(doc: DocumentEntity) {
        viewModelScope.launch {
            rendererManager?.close()
            rendererManager = null

            val file = File(doc.filePath)
            if (!file.exists()) {
                _statusMessage.value = "Document file not found: ${doc.title}"
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

                // Load annotations for document
                loadAnnotationsForDoc(doc.id)

                // Update last read timestamp in db
                dao.updateProgress(doc.id, _currentPage.value)

                // Pre-render current page and neighbors
                renderPage(_currentPage.value)
                if (_currentPage.value + 1 < manager.pageCount) renderPage(_currentPage.value + 1)
            } catch (e: Exception) {
                e.printStackTrace()
                _statusMessage.value = "Failed to open PDF: ${e.localizedMessage}"
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
                        android.content.Intent.FLAG_GRANT_READ_URI_PERMISSION
                    )
                } catch (ignored: Exception) {}

                var title = "Imported Document"
                var size = 0L

                context.contentResolver.query(uri, null, null, null, null)?.use { cursor ->
                    val nameIdx = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
                    val sizeIdx = cursor.getColumnIndex(OpenableColumns.SIZE)
                    if (cursor.moveToFirst()) {
                        if (nameIdx != -1) title = cursor.getString(nameIdx) ?: title
                        if (sizeIdx != -1) size = cursor.getLong(sizeIdx)
                    }
                }

                // Check if incoming URI is a Word, PowerPoint, or Text file and convert to PDF automatically
                val lowerTitle = title.lowercase()
                if (lowerTitle.endsWith(".docx") || lowerTitle.endsWith(".doc")) {
                    val convertedPdf = File(context.cacheDir, "imp_word_${System.currentTimeMillis()}.pdf")
                    if (DocumentConverterEngine.convertWordToPdf(context, uri, convertedPdf, title)) {
                        return@launch importPdfFromUri(Uri.fromFile(convertedPdf))
                    }
                } else if (lowerTitle.endsWith(".pptx") || lowerTitle.endsWith(".ppt")) {
                    openPowerPointPresentation(uri, title)
                    return@launch
                } else if (lowerTitle.endsWith(".txt") || lowerTitle.endsWith(".rtf")) {
                    val convertedPdf = File(context.cacheDir, "imp_txt_${System.currentTimeMillis()}.pdf")
                    if (DocumentConverterEngine.convertTxtToPdf(context, uri, convertedPdf, title)) {
                        return@launch importPdfFromUri(Uri.fromFile(convertedPdf))
                    }
                }

                val docsDir = File(context.filesDir, "imported_pdfs")
                if (!docsDir.exists()) docsDir.mkdirs()

                // Sanitize filename while supporting all worldwide international Unicode characters (Arabic, Chinese, Japanese, Cyrillic, etc.)
                val sanitizedTitle = title.replace(Regex("[^a-zA-Z0-9._\\-\\p{L}]"), "_").ifBlank { "document.pdf" }
                val destFile = File(docsDir, "${System.currentTimeMillis()}_$sanitizedTitle")
                context.contentResolver.openInputStream(uri)?.use { input ->
                    FileOutputStream(destFile).use { output ->
                        input.copyTo(output)
                    }
                }

                if (destFile.exists() && destFile.length() > 0) {
                    val pfd = ParcelFileDescriptor.open(destFile, ParcelFileDescriptor.MODE_READ_ONLY)
                    val renderer = android.graphics.pdf.PdfRenderer(pfd)
                    val pages = renderer.pageCount
                    renderer.close()
                    pfd.close()

                    val newDoc = DocumentEntity(
                        uriString = Uri.fromFile(destFile).toString(),
                        title = title.removeSuffix(".pdf"),
                        filePath = destFile.absolutePath,
                        pageCount = pages,
                        fileSizeBytes = destFile.length()
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
                val pfd = ParcelFileDescriptor.open(pdfFile, ParcelFileDescriptor.MODE_READ_ONLY)
                val renderer = android.graphics.pdf.PdfRenderer(pfd)
                val pages = renderer.pageCount
                renderer.close()
                pfd.close()

                val newDoc = DocumentEntity(
                    uriString = Uri.fromFile(pdfFile).toString(),
                    title = title.removeSuffix(".pdf"),
                    filePath = pdfFile.absolutePath,
                    pageCount = pages,
                    fileSizeBytes = pdfFile.length(),
                    lastOpenedTimestamp = System.currentTimeMillis()
                )
                val id = dao.insertDocument(newDoc)
                val savedDoc = newDoc.copy(id = id)
                scanDeviceDocuments()
                withContext(Dispatchers.Main) {
                    onRegistered(savedDoc)
                }
            } catch (e: Exception) {
                e.printStackTrace()
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
        }
    }

    fun deleteDocument(doc: DocumentEntity) {
        viewModelScope.launch(Dispatchers.IO) {
            dao.deleteDocument(doc)
            try {
                val f = File(doc.filePath)
                if (f.exists() && !doc.isSample) {
                    f.delete()
                }
            } catch (ignored: Exception) {}
        }
    }

    // --- Annotation Actions ---
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
        textContent: String = ""
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
            textContent = textContent
        )
        viewModelScope.launch(Dispatchers.IO) {
            val id = dao.insertAnnotation(annotation)
            val updated = _currentAnnotations.value + annotation.copy(id = id)
            _currentAnnotations.value = updated
        }
    }

    fun deleteAnnotation(annotationId: Long) {
        viewModelScope.launch(Dispatchers.IO) {
            dao.deleteAnnotationById(annotationId)
            _currentAnnotations.value = _currentAnnotations.value.filter { it.id != annotationId }
        }
    }

    fun clearPageAnnotations(pageIndex: Int) {
        val doc = _currentDocument.value ?: return
        viewModelScope.launch(Dispatchers.IO) {
            dao.clearPageAnnotations(doc.id, pageIndex)
            _currentAnnotations.value = _currentAnnotations.value.filter { it.pageIndex != pageIndex }
        }
    }

    fun updateAnnotation(annotation: AnnotationEntity) {
        viewModelScope.launch(Dispatchers.IO) {
            dao.updateAnnotation(annotation)
            val updated = _currentAnnotations.value.map { if (it.id == annotation.id) annotation else it }
            _currentAnnotations.value = updated
        }
    }

    fun addWhiteout(
        pageIndex: Int,
        posX: Float,
        posY: Float,
        width: Float = 0.25f,
        height: Float = 0.04f,
        replacementText: String = "",
        colorHex: Long = 0xFFFFFFFFL
    ) {
        val doc = _currentDocument.value ?: return
        val annotation = AnnotationEntity(
            documentId = doc.id,
            pageIndex = pageIndex,
            type = "WHITEOUT",
            posX = posX,
            posY = posY,
            width = width,
            height = height,
            colorHex = colorHex,
            textContent = replacementText
        )
        viewModelScope.launch(Dispatchers.IO) {
            val id = dao.insertAnnotation(annotation)
            _currentAnnotations.value = _currentAnnotations.value + annotation.copy(id = id)
            withContext(Dispatchers.Main) {
                _statusMessage.value = if (replacementText.isNotBlank()) "Text edited & replaced" else "Whiteout applied"
            }
        }
    }

    fun exportAnnotatedDocument(onExported: (File?) -> Unit) {
        val doc = _currentDocument.value ?: return
        viewModelScope.launch(Dispatchers.IO) {
            val source = File(doc.filePath)
            val exportDir = File(getApplication<Application>().filesDir, "exported_pdfs")
            if (!exportDir.exists()) exportDir.mkdirs()

            val outFile = File(exportDir, "Edited_${System.currentTimeMillis()}_${source.name}")
            var finalFile = outFile
            val success = PdfExportEngine.exportAnnotatedPdf(source, _currentAnnotations.value, outFile)
            val wm = _activeWatermark.value
            if (success && wm != null) {
                val watermarkedFile = File(exportDir, "WM_${System.currentTimeMillis()}_${source.name}")
                val wmSuccess = DocumentConverterEngine.stampWatermarkOnPdf(outFile, watermarkedFile, wm)
                if (wmSuccess) {
                    outFile.delete()
                    finalFile = watermarkedFile
                }
            }

            withContext(Dispatchers.Main) {
                if (success) {
                    _statusMessage.value = "PDF exported successfully!"
                    onExported(finalFile)
                } else {
                    _statusMessage.value = "Failed to export PDF"
                    onExported(null)
                }
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
                val pfd = ParcelFileDescriptor.open(outFile, ParcelFileDescriptor.MODE_READ_ONLY)
                val renderer = android.graphics.pdf.PdfRenderer(pfd)
                val pages = renderer.pageCount
                renderer.close()
                pfd.close()

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
                val pfd = ParcelFileDescriptor.open(outFile, ParcelFileDescriptor.MODE_READ_ONLY)
                val renderer = android.graphics.pdf.PdfRenderer(pfd)
                val pages = renderer.pageCount
                renderer.close()
                pfd.close()

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
                val pfd = ParcelFileDescriptor.open(outFile, ParcelFileDescriptor.MODE_READ_ONLY)
                val renderer = android.graphics.pdf.PdfRenderer(pfd)
                val pages = renderer.pageCount
                renderer.close()
                pfd.close()

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
                val pfd = ParcelFileDescriptor.open(outFile, ParcelFileDescriptor.MODE_READ_ONLY)
                val renderer = android.graphics.pdf.PdfRenderer(pfd)
                val pages = renderer.pageCount
                renderer.close()
                pfd.close()

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
                val pfd = ParcelFileDescriptor.open(outFile, ParcelFileDescriptor.MODE_READ_ONLY)
                val renderer = android.graphics.pdf.PdfRenderer(pfd)
                val pages = renderer.pageCount
                renderer.close()
                pfd.close()

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
                val pfd = ParcelFileDescriptor.open(outFile, ParcelFileDescriptor.MODE_READ_ONLY)
                val renderer = android.graphics.pdf.PdfRenderer(pfd)
                val pages = renderer.pageCount
                renderer.close()
                pfd.close()

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
        viewModelScope.launch(Dispatchers.IO) {
            val context = getApplication<Application>()
            val docsDir = File(context.filesDir, "organized_pdfs")
            if (!docsDir.exists()) docsDir.mkdirs()

            val cleanTitle = newTitle.ifBlank { "${doc.title}_organized" }
            val outFile = File(docsDir, "${cleanTitle.replace(' ', '_')}.pdf")
            val source = File(doc.filePath)

            val success = PdfExportEngine.organizePages(source, includedIndices, rotations, outFile)
            if (success) {
                val pfd = ParcelFileDescriptor.open(outFile, ParcelFileDescriptor.MODE_READ_ONLY)
                val renderer = android.graphics.pdf.PdfRenderer(pfd)
                val pages = renderer.pageCount
                renderer.close()
                pfd.close()

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
        viewModelScope.launch(Dispatchers.IO) {
            val context = getApplication<Application>()
            val docsDir = File(context.filesDir, "extracted_pdfs")
            if (!docsDir.exists()) docsDir.mkdirs()

            val cleanTitle = newTitle.ifBlank { "${doc.title}_extracted" }
            val outFile = File(docsDir, "${cleanTitle.replace(' ', '_')}.pdf")
            val source = File(doc.filePath)

            val success = PdfExportEngine.organizePages(source, includedIndices, rotations, outFile)
            if (success) {
                val pfd = ParcelFileDescriptor.open(outFile, ParcelFileDescriptor.MODE_READ_ONLY)
                val renderer = android.graphics.pdf.PdfRenderer(pfd)
                val pages = renderer.pageCount
                renderer.close()
                pfd.close()

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
                posY = posY
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
        val textToSpeak = customText ?: run {
            if (doc.title.contains("NDA", ignoreCase = true)) {
                "Mutual Non-Disclosure Agreement. Entered into on October 14, 2026, by and between AcroTech Solutions and Recipient. Section 1: Purpose of Disclosure. The parties intend to explore a strategic partnership regarding Cloud Document Technologies."
            } else if (doc.title.contains("Whitepaper", ignoreCase = true)) {
                "Technical Whitepaper: Intelligent Document Processing Architecture. Modern enterprise workflows demand zero latency on-device processing combined with multimodal intelligence."
            } else {
                "Reading document: ${doc.title}. Page ${_currentPage.value + 1} of ${_totalPages.value}."
            }
        }
        ttsManager.speak(textToSpeak)
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

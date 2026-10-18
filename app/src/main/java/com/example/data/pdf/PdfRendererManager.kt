package com.example.data.pdf

import android.graphics.Bitmap
import android.graphics.pdf.PdfRenderer
import android.os.ParcelFileDescriptor
import android.util.Log
import android.util.LruCache
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File

/**
 * Universal High-Performance PDF Rendering Engine
 * Optimized for ALL Android OEMs (Samsung, OPPO, Realme, Xiaomi, Vivo, Motorola, etc.)
 * Provides resilient bitmap allocation, dynamic scale capping, and OOM recovery for low-RAM devices.
 */
class PdfRendererManager(val file: File) {

    private var fileDescriptor: ParcelFileDescriptor? = null
    private var pdfRenderer: PdfRenderer? = null
    val pageCount: Int

    // Detect device memory tier
    private val maxMemoryKb: Int = (Runtime.getRuntime().maxMemory() / 1024).toInt()
    val isLowMemoryDevice: Boolean = maxMemoryKb < 192 * 1024 // e.g. 2GB/3GB RAM budget phones

    // Dynamic LRU bitmap cache: scales safely to never trigger Android Low Memory Killer (LMK)
    private val memoryCache: LruCache<String, Bitmap>

    init {
        val cacheSize = if (isLowMemoryDevice) (maxMemoryKb / 16).coerceAtLeast(8 * 1024) else maxMemoryKb / 8
        memoryCache = object : LruCache<String, Bitmap>(cacheSize) {
            override fun sizeOf(key: String, bitmap: Bitmap): Int {
                return (bitmap.byteCount / 1024).coerceAtLeast(1)
            }
        }

        try {
            fileDescriptor = ParcelFileDescriptor.open(file, ParcelFileDescriptor.MODE_READ_ONLY)
            pdfRenderer = PdfRenderer(fileDescriptor!!)
            pageCount = pdfRenderer?.pageCount ?: 0
        } catch (e: Exception) {
            Log.e("PdfRendererManager", "Error opening PDF file: ${file.path}", e)
            throw e
        }
    }

    /**
     * Renders a PDF page to a Bitmap with resilient memory scaling and OOM fallback.
     */
    suspend fun renderPage(pageIndex: Int, densityDpi: Int = 2): Bitmap? = withContext(Dispatchers.IO) {
        if (pdfRenderer == null || pageIndex < 0 || pageIndex >= pageCount) {
            return@withContext null
        }

        val cacheKey = "${file.absolutePath}_p${pageIndex}_dpi$densityDpi"
        memoryCache.get(cacheKey)?.let { cached ->
            if (!cached.isRecycled) return@withContext cached
        }

        synchronized(this@PdfRendererManager) {
            val renderer = pdfRenderer ?: return@withContext null
            var page: PdfRenderer.Page? = null
            try {
                page = renderer.openPage(pageIndex)
                val pageWidth = page.width
                val pageHeight = page.height

                // Cap maximum rendering dimension according to device capabilities
                // Prevents OOM crashes on high-res scans or blueprints on 2GB RAM Oppo/Realme/Samsung phones
                val maxDimension = if (isLowMemoryDevice) 1800 else 3200
                val rawW = (pageWidth * densityDpi).coerceAtLeast(1)
                val rawH = (pageHeight * densityDpi).coerceAtLeast(1)
                val downscale = if (rawW > maxDimension || rawH > maxDimension) {
                    minOf(maxDimension.toFloat() / rawW, maxDimension.toFloat() / rawH)
                } else {
                    1f
                }

                val finalW = ((rawW * downscale).toInt()).coerceIn(1, maxDimension)
                val finalH = ((rawH * downscale).toInt()).coerceIn(1, maxDimension)

                // Select pixel config: RGB_565 uses half the RAM, perfect for budget devices
                val config = if (isLowMemoryDevice) Bitmap.Config.RGB_565 else Bitmap.Config.ARGB_8888

                var bitmap: Bitmap? = null
                try {
                    bitmap = Bitmap.createBitmap(finalW, finalH, config)
                } catch (oom: OutOfMemoryError) {
                    Log.w("PdfRendererManager", "OOM allocating ${finalW}x${finalH}, recovering...", oom)
                    memoryCache.evictAll()
                    System.gc()
                    // Emergency fallback: 1x scale with RGB_565
                    val fallbackW = (pageWidth * 1.0f).toInt().coerceAtLeast(1)
                    val fallbackH = (pageHeight * 1.0f).toInt().coerceAtLeast(1)
                    bitmap = Bitmap.createBitmap(fallbackW, fallbackH, Bitmap.Config.RGB_565)
                }

                bitmap?.let { bmp ->
                    page.render(bmp, null, null, PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY)
                    memoryCache.put(cacheKey, bmp)
                }
                return@withContext bitmap
            } catch (t: Throwable) {
                Log.e("PdfRendererManager", "Render failed for page $pageIndex", t)
                return@withContext null
            } finally {
                try {
                    page?.close()
                } catch (ignored: Exception) {}
            }
        }
    }

    fun getPageSize(pageIndex: Int): Pair<Int, Int> {
        if (pdfRenderer == null || pageIndex < 0 || pageIndex >= pageCount) {
            return Pair(595, 842)
        }
        synchronized(this) {
            val renderer = pdfRenderer ?: return Pair(595, 842)
            var page: PdfRenderer.Page? = null
            return try {
                page = renderer.openPage(pageIndex)
                Pair(page.width, page.height)
            } catch (e: Exception) {
                Pair(595, 842)
            } finally {
                try { page?.close() } catch (ignored: Exception) {}
            }
        }
    }

    fun evictMemory() {
        memoryCache.evictAll()
    }

    fun close() {
        try {
            pdfRenderer?.close()
            pdfRenderer = null
        } catch (ignored: Exception) {}
        try {
            fileDescriptor?.close()
            fileDescriptor = null
        } catch (ignored: Exception) {}
        memoryCache.evictAll()
    }
}

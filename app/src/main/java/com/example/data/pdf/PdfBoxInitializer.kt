package com.example.data.pdf

import android.content.Context
import com.tom_roush.pdfbox.android.PDFBoxResourceLoader

/**
 * Thread-safe, idempotent initializer for Apache PDFBox-Android font/CMap resources.
 * Must be called before performing PDFBox document parsing, stripping, redaction, or security operations.
 */
object PdfBoxInitializer {
    @Volatile
    private var initialized = false

    fun ensureInitialized(context: Context) {
        if (initialized) return
        synchronized(this) {
            if (!initialized) {
                PDFBoxResourceLoader.init(context.applicationContext)
                initialized = true
            }
        }
    }
}

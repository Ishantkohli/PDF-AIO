package com.example

import android.app.Application
import com.example.data.pdf.PdfBoxInitializer

/**
 * Copyright © 2026 Ishant Kohli. All rights reserved.
 * Application entry point initializing local on-device PDFBox resources.
 */
class PdfAioApplication : Application() {
    override fun onCreate() {
        super.onCreate()
        PdfBoxInitializer.ensureInitialized(this)
    }
}

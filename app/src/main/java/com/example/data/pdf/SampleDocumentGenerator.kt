package com.example.data.pdf

import android.content.Context
import android.graphics.Color
import android.graphics.Paint
import android.graphics.RectF
import android.graphics.pdf.PdfDocument
import java.io.File
import java.io.FileOutputStream

object SampleDocumentGenerator {

    fun deleteSampleFiles(context: Context) {
        val sampleDir = File(context.filesDir, "sample_pdfs")
        if (sampleDir.exists()) {
            sampleDir.deleteRecursively()
        }
    }

    fun ensureSampleDocuments(context: Context): List<File> {
        val sampleDir = File(context.filesDir, "sample_pdfs")
        if (!sampleDir.exists()) {
            sampleDir.mkdirs()
        }

        val ndaFile = File(sampleDir, "Mutual_Non_Disclosure_Agreement.pdf")
        if (!ndaFile.exists() || ndaFile.length() == 0L) {
            generateNdaPdf(ndaFile)
        }

        val techDocFile = File(sampleDir, "AI_Cloud_Architecture_Whitepaper.pdf")
        if (!techDocFile.exists() || techDocFile.length() == 0L) {
            generateTechWhitepaperPdf(techDocFile)
        }

        val invoiceFile = File(sampleDir, "Consulting_Invoice_Statement_of_Work.pdf")
        if (!invoiceFile.exists() || invoiceFile.length() == 0L) {
            generateInvoicePdf(invoiceFile)
        }

        return listOf(ndaFile, techDocFile, invoiceFile)
    }

    private fun generateNdaPdf(outputFile: File) {
        val doc = PdfDocument()
        val pageWidth = 595 // Standard A4 points (72 dpi)
        val pageHeight = 842

        // Paints
        val titlePaint = Paint().apply {
            color = Color.rgb(20, 20, 25)
            textSize = 18f
            isFakeBoldText = true
            isAntiAlias = true
        }
        val headerPaint = Paint().apply {
            color = Color.rgb(220, 38, 38) // Acrobat Red accent
            textSize = 13f
            isFakeBoldText = true
            isAntiAlias = true
        }
        val bodyPaint = Paint().apply {
            color = Color.rgb(51, 65, 85)
            textSize = 10f
            isAntiAlias = true
        }
        val linePaint = Paint().apply {
            color = Color.rgb(226, 232, 240)
            strokeWidth = 1.5f
            isAntiAlias = true
        }

        // --- PAGE 1 ---
        var pageInfo = PdfDocument.PageInfo.Builder(pageWidth, pageHeight, 1).create()
        var page = doc.startPage(pageInfo)
        var canvas = page.canvas

        // Header Banner
        val bannerPaint = Paint().apply { color = Color.rgb(248, 250, 252) }
        canvas.drawRect(RectF(0f, 0f, pageWidth.toFloat(), 90f), bannerPaint)
        canvas.drawText("CONFIDENTIAL LEGAL AGREEMENT", 40f, 45f, headerPaint)
        canvas.drawText("MUTUAL NON-DISCLOSURE AGREEMENT (NDA)", 40f, 70f, titlePaint)
        canvas.drawLine(40f, 95f, pageWidth - 40f, 95f, linePaint)

        var y = 130f
        canvas.drawText("This Mutual Non-Disclosure Agreement (\"Agreement\") is entered into on October 14, 2026, by and", 40f, y, bodyPaint); y += 18f
        canvas.drawText("between PDF AIO Global Technologies Inc., a Delaware corporation, and the Undersigned Recipient.", 40f, y, bodyPaint); y += 30f

        canvas.drawText("1. PURPOSE OF DISCLOSURE", 40f, y, headerPaint); y += 20f
        val p1Lines = listOf(
            "The parties intend to explore a strategic partnership regarding Cloud Document Technologies,",
            "proprietary machine learning models, and automated document workflow pipelines. In connection with",
            "this prospective collaboration, either party may disclose confidential business and technical information."
        )
        for (l in p1Lines) {
            canvas.drawText(l, 40f, y, bodyPaint); y += 16f
        }
        y += 20f

        canvas.drawText("2. DEFINITION OF CONFIDENTIAL INFORMATION", 40f, y, headerPaint); y += 20f
        val p2Lines = listOf(
            "\"Confidential Information\" means all non-public information disclosed by one party (\"Disclosing Party\")",
            "to the other party (\"Receiving Party\"), whether orally or in writing, that is designated as confidential",
            "or that reasonably should be understood to be confidential given the nature of the information.",
            "This includes source code, proprietary algorithms, financial estimates, customer records, and product roadmaps."
        )
        for (l in p2Lines) {
            canvas.drawText(l, 40f, y, bodyPaint); y += 16f
        }
        y += 20f

        canvas.drawText("3. NON-DISCLOSURE AND NON-USE OBLIGATIONS", 40f, y, headerPaint); y += 20f
        val p3Lines = listOf(
            "The Receiving Party agrees to: (a) protect Confidential Information with at least reasonable care,",
            "(b) not disclose Confidential Information to any third party without prior written consent, and",
            "(c) use Confidential Information solely to evaluate the prospective business relationship."
        )
        for (l in p3Lines) {
            canvas.drawText(l, 40f, y, bodyPaint); y += 16f
        }

        // Page number
        canvas.drawText("Page 1 of 3 — Mutual NDA", 40f, pageHeight - 35f, bodyPaint)
        doc.finishPage(page)

        // --- PAGE 2 ---
        pageInfo = PdfDocument.PageInfo.Builder(pageWidth, pageHeight, 2).create()
        page = doc.startPage(pageInfo)
        canvas = page.canvas

        canvas.drawText("MUTUAL NON-DISCLOSURE AGREEMENT — CONTINUED", 40f, 50f, headerPaint)
        canvas.drawLine(40f, 65f, pageWidth - 40f, 65f, linePaint)
        y = 100f

        canvas.drawText("4. EXCLUSIONS FROM CONFIDENTIALITY", 40f, y, headerPaint); y += 20f
        val p4Lines = listOf(
            "Confidential Information does not include information that: (a) is or becomes publicly known through no breach,",
            "(b) was already known to the Receiving Party prior to disclosure, or (c) is independently developed without reference",
            "to Disclosing Party's Confidential Information."
        )
        for (l in p4Lines) {
            canvas.drawText(l, 40f, y, bodyPaint); y += 16f
        }
        y += 30f

        canvas.drawText("5. TERM AND TERMINATION", 40f, y, headerPaint); y += 20f
        val p5Lines = listOf(
            "This Agreement remains in effect for a term of three (3) years from the effective date.",
            "Upon written notice, the Receiving Party must promptly return or destroy all physical and electronic copies",
            "of Confidential Information within fourteen (14) business days."
        )
        for (l in p5Lines) {
            canvas.drawText(l, 40f, y, bodyPaint); y += 16f
        }
        y += 30f

        canvas.drawText("6. GOVERNING LAW AND REMEDIES", 40f, y, headerPaint); y += 20f
        val p6Lines = listOf(
            "This Agreement shall be governed by the laws of the State of California without regard to conflict of law principles.",
            "The parties acknowledge that unauthorized disclosure will cause irreparable harm for which damages would be inadequate,",
            "entitling the Disclosing Party to seek immediate injunctive relief."
        )
        for (l in p6Lines) {
            canvas.drawText(l, 40f, y, bodyPaint); y += 16f
        }

        canvas.drawText("Page 2 of 3 — Mutual NDA", 40f, pageHeight - 35f, bodyPaint)
        doc.finishPage(page)

        // --- PAGE 3 (Signature Page) ---
        pageInfo = PdfDocument.PageInfo.Builder(pageWidth, pageHeight, 3).create()
        page = doc.startPage(pageInfo)
        canvas = page.canvas

        canvas.drawText("EXECUTION AND SIGNATURE BLOCK", 40f, 50f, headerPaint)
        canvas.drawLine(40f, 65f, pageWidth - 40f, 65f, linePaint)
        y = 110f

        canvas.drawText("IN WITNESS WHEREOF, the parties have executed this Agreement as of the date first above written.", 40f, y, bodyPaint)
        y += 60f

        // Party 1 Box
        val boxPaint = Paint().apply {
            color = Color.rgb(241, 245, 249)
            style = Paint.Style.FILL
        }
        val borderPaint = Paint().apply {
            color = Color.rgb(203, 213, 225)
            style = Paint.Style.STROKE
            strokeWidth = 1f
        }
        canvas.drawRoundRect(RectF(40f, y, 270f, y + 180f), 8f, 8f, boxPaint)
        canvas.drawRoundRect(RectF(40f, y, 270f, y + 180f), 8f, 8f, borderPaint)

        canvas.drawText("DISCLOSING PARTY:", 55f, y + 25f, headerPaint)
        canvas.drawText("AcroTech Solutions Inc.", 55f, y + 45f, titlePaint)
        canvas.drawText("Signature:", 55f, y + 90f, bodyPaint)
        canvas.drawLine(55f, y + 120f, 250f, y + 120f, linePaint)
        canvas.drawText("Sarah Jenkins, VP of Engineering", 55f, y + 140f, bodyPaint)
        canvas.drawText("Date: October 14, 2026", 55f, y + 160f, bodyPaint)

        // Party 2 Box (for user to sign!)
        canvas.drawRoundRect(RectF(300f, y, 555f, y + 180f), 8f, 8f, boxPaint)
        canvas.drawRoundRect(RectF(300f, y, 555f, y + 180f), 8f, 8f, borderPaint)

        canvas.drawText("RECEIVING PARTY:", 315f, y + 25f, headerPaint)
        canvas.drawText("Client / Partner Organization", 315f, y + 45f, titlePaint)
        canvas.drawText("Authorized Signature (Sign Below):", 315f, y + 90f, bodyPaint)
        canvas.drawLine(315f, y + 120f, 530f, y + 120f, linePaint)
        canvas.drawText("Name: _______________________", 315f, y + 140f, bodyPaint)
        canvas.drawText("Date:  _______________________", 315f, y + 160f, bodyPaint)

        y += 220f
        val notePaint = Paint().apply {
            color = Color.rgb(220, 38, 38)
            textSize = 9.5f
            isAntiAlias = true
        }
        canvas.drawText("Tip: Use the Fill & Sign tool or Signature Vault in the toolbar to place your digital signature above.", 40f, y, notePaint)

        canvas.drawText("Page 3 of 3 — Mutual NDA", 40f, pageHeight - 35f, bodyPaint)
        doc.finishPage(page)

        FileOutputStream(outputFile).use { out -> doc.writeTo(out) }
        doc.close()
    }

    private fun generateTechWhitepaperPdf(outputFile: File) {
        val doc = PdfDocument()
        val pageWidth = 595
        val pageHeight = 842

        val titlePaint = Paint().apply {
            color = Color.rgb(15, 23, 42)
            textSize = 18f
            isFakeBoldText = true
            isAntiAlias = true
        }
        val subPaint = Paint().apply {
            color = Color.rgb(220, 38, 38)
            textSize = 12f
            isFakeBoldText = true
            isAntiAlias = true
        }
        val bodyPaint = Paint().apply {
            color = Color.rgb(51, 65, 85)
            textSize = 10f
            isAntiAlias = true
        }
        val linePaint = Paint().apply {
            color = Color.rgb(226, 232, 240)
            strokeWidth = 1f
            isAntiAlias = true
        }

        // --- PAGE 1 ---
        var pageInfo = PdfDocument.PageInfo.Builder(pageWidth, pageHeight, 1).create()
        var page = doc.startPage(pageInfo)
        var canvas = page.canvas

        canvas.drawText("TECHNICAL WHITEPAPER", 40f, 45f, subPaint)
        canvas.drawText("Next-Gen Intelligent Document Processing Architecture", 40f, 75f, titlePaint)
        canvas.drawLine(40f, 95f, pageWidth - 40f, 95f, linePaint)

        var y = 130f
        canvas.drawText("EXECUTIVE ABSTRACT", 40f, y, subPaint); y += 20f
        val abstractLines = listOf(
            "Modern enterprise document workflows demand zero-latency on-device processing combined with",
            "high-reasoning multimodal cloud intelligence. This paper presents an end-to-end architecture for",
            "client-side PDF rendering, vector annotation persistence, and contextual document reasoning with",
            "thinking models that parse dense technical specifications in sub-second timeframes."
        )
        for (l in abstractLines) {
            canvas.drawText(l, 40f, y, bodyPaint); y += 16f
        }
        y += 25f

        canvas.drawText("SYSTEM COMPONENT BREAKDOWN", 40f, y, subPaint); y += 20f
        val compLines = listOf(
            "1. Client Native Rendering Engine: Leverages OS hardware accelerated rasterization.",
            "2. Non-Destructive Annotation Store: Maintains stroke vectors without altering original streams.",
            "3. Flattening and Export Pipeline: Synthesizes vector overlays into standardized PDF 1.7 standards.",
            "4. Multimodal Reasoning Assistant: Streams semantic queries through specialized thinking models."
        )
        for (l in compLines) {
            canvas.drawText(l, 40f, y, bodyPaint); y += 18f
        }

        canvas.drawText("Page 1 of 2 — Architecture Whitepaper", 40f, pageHeight - 35f, bodyPaint)
        doc.finishPage(page)

        // --- PAGE 2 ---
        pageInfo = PdfDocument.PageInfo.Builder(pageWidth, pageHeight, 2).create()
        page = doc.startPage(pageInfo)
        canvas = page.canvas

        canvas.drawText("PERFORMANCE BENCHMARKS & LATENCY COMPARISON", 40f, 50f, subPaint)
        canvas.drawLine(40f, 65f, pageWidth - 40f, 65f, linePaint)
        y = 100f

        canvas.drawText("Table 1. Rendering and AI Inference Metrics Across Formats", 40f, y, bodyPaint); y += 20f

        // Draw Table Header
        val thPaint = Paint().apply { color = Color.rgb(241, 245, 249) }
        canvas.drawRect(RectF(40f, y, pageWidth - 40f, y + 25f), thPaint)
        val thText = Paint().apply { color = Color.rgb(15, 23, 42); textSize = 9.5f; isFakeBoldText = true; isAntiAlias = true }
        canvas.drawText("Document Size", 50f, y + 17f, thText)
        canvas.drawText("Pages", 180f, y + 17f, thText)
        canvas.drawText("Raster Latency", 270f, y + 17f, thText)
        canvas.drawText("AI Summary Latency", 400f, y + 17f, thText)
        y += 25f

        val rows = listOf(
            listOf("Standard NDA (1-5 MB)", "3 pages", "12 ms", "1.1 s"),
            listOf("Technical Spec (15 MB)", "28 pages", "44 ms", "1.8 s"),
            listOf("Financial Ledger (45 MB)", "120 pages", "110 ms", "2.4 s")
        )
        for (r in rows) {
            canvas.drawLine(40f, y, pageWidth - 40f, y, linePaint)
            canvas.drawText(r[0], 50f, y + 18f, bodyPaint)
            canvas.drawText(r[1], 180f, y + 18f, bodyPaint)
            canvas.drawText(r[2], 270f, y + 18f, bodyPaint)
            canvas.drawText(r[3], 400f, y + 18f, bodyPaint)
            y += 28f
        }
        canvas.drawLine(40f, y, pageWidth - 40f, y, linePaint)

        y += 40f
        canvas.drawText("CONCLUSION", 40f, y, subPaint); y += 20f
        canvas.drawText("The hybrid architecture delivers exceptional user responsiveness while guaranteeing complete document privacy.", 40f, y, bodyPaint)

        canvas.drawText("Page 2 of 2 — Architecture Whitepaper", 40f, pageHeight - 35f, bodyPaint)
        doc.finishPage(page)

        FileOutputStream(outputFile).use { out -> doc.writeTo(out) }
        doc.close()
    }

    private fun generateInvoicePdf(outputFile: File) {
        val doc = PdfDocument()
        val pageWidth = 595
        val pageHeight = 842

        val titlePaint = Paint().apply {
            color = Color.rgb(15, 23, 42)
            textSize = 20f
            isFakeBoldText = true
            isAntiAlias = true
        }
        val redPaint = Paint().apply {
            color = Color.rgb(220, 38, 38)
            textSize = 12f
            isFakeBoldText = true
            isAntiAlias = true
        }
        val bodyPaint = Paint().apply {
            color = Color.rgb(71, 85, 105)
            textSize = 10f
            isAntiAlias = true
        }
        val linePaint = Paint().apply {
            color = Color.rgb(226, 232, 240)
            strokeWidth = 1f
            isAntiAlias = true
        }

        val pageInfo = PdfDocument.PageInfo.Builder(pageWidth, pageHeight, 1).create()
        val page = doc.startPage(pageInfo)
        val canvas = page.canvas

        // Header
        canvas.drawText("INVOICE & STATEMENT OF WORK", 40f, 50f, redPaint)
        canvas.drawText("INVOICE #INV-2026-0891", 40f, 75f, titlePaint)
        canvas.drawText("Status: PENDING APPROVAL", pageWidth - 200f, 75f, redPaint)
        canvas.drawLine(40f, 95f, pageWidth - 40f, 95f, linePaint)

        var y = 130f
        canvas.drawText("Billed To: Global Tech Innovations Corp.", 40f, y, bodyPaint)
        canvas.drawText("Date: October 14, 2026", pageWidth - 180f, y, bodyPaint); y += 18f
        canvas.drawText("Attn: Accounts Payable & Legal Department", 40f, y, bodyPaint)
        canvas.drawText("Due Date: Net 30 Days", pageWidth - 180f, y, bodyPaint); y += 35f

        // Table Header
        val thPaint = Paint().apply { color = Color.rgb(241, 245, 249) }
        canvas.drawRect(RectF(40f, y, pageWidth - 40f, y + 25f), thPaint)
        val thText = Paint().apply { color = Color.rgb(15, 23, 42); textSize = 9.5f; isFakeBoldText = true; isAntiAlias = true }
        canvas.drawText("Description of Deliverables", 50f, y + 17f, thText)
        canvas.drawText("Hours", 320f, y + 17f, thText)
        canvas.drawText("Rate", 400f, y + 17f, thText)
        canvas.drawText("Amount", 480f, y + 17f, thText)
        y += 25f

        val items = listOf(
            listOf("PDF Viewer Native Engine Architecture", "40 hrs", "$150/hr", "$6,000.00"),
            listOf("Vector Annotation Layer & Pen Engine", "32 hrs", "$150/hr", "$4,800.00"),
            listOf("Digital Signature Capture & Stamp Vault", "24 hrs", "$150/hr", "$3,600.00"),
            listOf("AI Document Assistant Integration (High Thinking)", "28 hrs", "$150/hr", "$4,200.00")
        )

        for (it in items) {
            canvas.drawLine(40f, y, pageWidth - 40f, y, linePaint)
            canvas.drawText(it[0], 50f, y + 18f, bodyPaint)
            canvas.drawText(it[1], 320f, y + 18f, bodyPaint)
            canvas.drawText(it[2], 400f, y + 18f, bodyPaint)
            canvas.drawText(it[3], 480f, y + 18f, bodyPaint)
            y += 28f
        }
        canvas.drawLine(40f, y, pageWidth - 40f, y, linePaint)

        y += 30f
        canvas.drawText("Subtotal:", 400f, y, bodyPaint)
        canvas.drawText("$18,600.00", 480f, y, bodyPaint); y += 18f
        canvas.drawText("Tax (0% Professional):", 400f, y, bodyPaint)
        canvas.drawText("$0.00", 480f, y, bodyPaint); y += 22f

        val totalPaint = Paint().apply { color = Color.rgb(220, 38, 38); textSize = 13f; isFakeBoldText = true; isAntiAlias = true }
        canvas.drawText("Total Due:", 400f, y, totalPaint)
        canvas.drawText("$18,600.00", 480f, y, totalPaint)

        y += 80f
        canvas.drawText("Approval Authorization:", 40f, y, bodyPaint); y += 25f
        canvas.drawLine(40f, y, 250f, y, linePaint)
        canvas.drawText("Authorized Signer (Stamp or sign here)", 40f, y + 16f, bodyPaint)

        doc.finishPage(page)
        FileOutputStream(outputFile).use { out -> doc.writeTo(out) }
        doc.close()
    }
}

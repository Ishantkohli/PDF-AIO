package com.example

import org.junit.Assert.*
import org.junit.Test

/**
 * Example local unit test, which will execute on the development machine (host).
 *
 * See [testing documentation](http://d.android.com/tools/testing).
 */
class ExampleUnitTest {
  @Test
  fun addition_isCorrect() {
    assertEquals(4, 2 + 2)
  }

  @Test
  fun pdfViewerState_navigationAndBoundsCheck() {
    val state = com.example.ui.components.PdfViewerState(initialPage = 0)
    assertEquals(0, state.currentPage)
    assertFalse(state.canGoPrevious)
    assertFalse(state.canGoNext)

    // Simulate 5 pages loaded
    state.pageCount = 5
    assertTrue(state.canGoNext)
    assertFalse(state.canGoPrevious)

    // Advance to next page
    val advanced = state.nextPage()
    assertTrue(advanced)
    assertEquals(1, state.currentPage)
    assertTrue(state.canGoPrevious)
    assertTrue(state.canGoNext)

    // Jump to last page
    state.lastPage()
    assertEquals(4, state.currentPage)
    assertFalse(state.canGoNext)
    assertTrue(state.canGoPrevious)

    // Cannot advance past last page
    assertFalse(state.nextPage())
    assertEquals(4, state.currentPage)

    // Revert to previous
    assertTrue(state.previousPage())
    assertEquals(3, state.currentPage)

    // Jump to specific page
    state.goToPage(2)
    assertEquals(2, state.currentPage)

    // Clamp beyond upper bound
    state.goToPage(99)
    assertEquals(4, state.currentPage)

    // Jump to first page
    state.firstPage()
    assertEquals(0, state.currentPage)
    assertFalse(state.canGoPrevious)
  }

  @Test
  fun pdfRenderResult_hierarchy() {
    val idle = com.example.ui.components.PdfRenderResult.Idle
    val loading = com.example.ui.components.PdfRenderResult.Loading
    val error = com.example.ui.components.PdfRenderResult.Error("Test failure")

    assertNotNull(idle)
    assertNotNull(loading)
    assertEquals("Test failure", error.message)
  }

  @Test
  fun versionComparator_correctlyEvaluatesVersions() {
    // Newer version detection
    assertTrue(com.example.update.VersionComparator.isNewer("v1.1.0", "1.0"))
    assertTrue(com.example.update.VersionComparator.isNewer("1.0.1", "1.0.0"))
    assertTrue(com.example.update.VersionComparator.isNewer("2.0.0", "1.9.9"))
    assertTrue(com.example.update.VersionComparator.isNewer("v1.2", "v1.1.5"))

    // Equal or older version detection
    assertFalse(com.example.update.VersionComparator.isNewer("1.0", "1.0"))
    assertFalse(com.example.update.VersionComparator.isNewer("v1.0.0", "1.0.0"))
    assertFalse(com.example.update.VersionComparator.isNewer("1.0.0", "1.1.0"))
    assertFalse(com.example.update.VersionComparator.isNewer("0.9.9", "1.0.0"))

    // Version code preference when available
    assertTrue(com.example.update.VersionComparator.isNewer("1.0", "1.0", remoteCode = 2, currentCode = 1))
    assertFalse(com.example.update.VersionComparator.isNewer("2.0", "1.0", remoteCode = 1, currentCode = 2))
  }

  @Test
  fun pdfTextExtractor_extractsFlateDecodeAndToUnicodeCleanly() {
    val cmap = """
      /CIDInit /ProcSet findresource begin
      12 dict begin
      begincmap
      1 begincodespacerange
      <0000> <FFFF>
      endcodespacerange
      3 beginbfchar
      <0001> <0048>
      <0002> <0069>
      <0003> <0021>
      endbfchar
      endcmap
    """.trimIndent().toByteArray(Charsets.US_ASCII)

    val stream1Raw = "BT /F1 12 Tf 72 720 Td <000100020003> Tj ET".toByteArray(Charsets.US_ASCII)
    val stream1Deflated = deflateBytes(stream1Raw)

    val stream2Raw = "BT /F2 12 Tf 72 700 Td [(Hello ) -120 (World)] TJ ET".toByteArray(Charsets.US_ASCII)
    val stream2Deflated = deflateBytes(stream2Raw)

    val pdfBytes = buildMinimalPdf(
      listOf(
        1 to "<< /Type /Catalog /Pages 2 0 R >>",
        2 to "<< /Type /Pages /Kids [3 0 R 4 0 R] /Count 2 >>",
        3 to "<< /Type /Page /Parent 2 0 R /Resources << /Font << /F1 5 0 R >> >> /Contents 6 0 R >>",
        4 to "<< /Type /Page /Parent 2 0 R /Resources << /Font << /F2 7 0 R >> >> /Contents 8 0 R >>",
        5 to "<< /Type /Font /Subtype /Type0 /Encoding /Identity-H /ToUnicode 9 0 R >>",
        7 to "<< /Type /Font /Subtype /Type1 /BaseFont /Helvetica /Encoding /WinAnsiEncoding >>"
      ),
      mapOf(
        6 to ("<< /Length ${stream1Deflated.size} /Filter /FlateDecode >>" to stream1Deflated),
        8 to ("<< /Length ${stream2Deflated.size} /Filter /FlateDecode >>" to stream2Deflated),
        9 to ("<< /Length ${cmap.size} >>" to cmap)
      )
    )

    val tmp = java.io.File.createTempFile("test_extract_", ".pdf")
    try {
      tmp.writeBytes(pdfBytes)
      val pages = com.example.data.pdf.PdfTextExtractor.extractPages(tmp)
      assertEquals(2, pages.size)
      assertTrue("Expected 'Hi!' on page 1, got: ${pages[0].fullText}", pages[0].fullText.contains("Hi!"))
      assertTrue("Expected 'Hello World' on page 2, got: ${pages[1].fullText}", pages[1].fullText.contains("Hello World"))
    } finally {
      tmp.delete()
    }
  }

  @Test
  fun pdfTextExtractor_filtersControlCharsAndGarbage() {
    val sanitized = com.example.data.pdf.PdfTextExtractor.sanitizeExtractedLine("Hello\u0000\u0007 World\u001F")
    assertFalse(sanitized.contains('\u0000'))
    assertFalse(sanitized.contains('\u0007'))
    assertTrue(sanitized.contains("Hello"))
    assertTrue(sanitized.contains("World"))
  }

  @Test
  fun pdfTextExtractor_handlesMissingAndMalformedFilesGracefully() {
    val missing = java.io.File("/tmp/non_existent_pdf_aio_${System.currentTimeMillis()}.pdf")
    assertTrue(com.example.data.pdf.PdfTextExtractor.extractPages(missing).isEmpty())

    val malformed = java.io.File.createTempFile("malformed_", ".pdf")
    try {
      malformed.writeText("NOT_A_REAL_PDF_HEADER_OR_STREAM_12345")
      val pages = com.example.data.pdf.PdfTextExtractor.extractPages(malformed)
      assertTrue(pages.isEmpty() || pages.all { it.fullText.isBlank() })
    } finally {
      malformed.delete()
    }
  }

  private fun deflateBytes(input: ByteArray): ByteArray {
    val deflater = java.util.zip.Deflater()
    deflater.setInput(input)
    deflater.finish()
    val out = java.io.ByteArrayOutputStream()
    val buf = ByteArray(1024)
    while (!deflater.finished()) {
      val count = deflater.deflate(buf)
      out.write(buf, 0, count)
    }
    deflater.end()
    return out.toByteArray()
  }

  private fun buildMinimalPdf(
    plainObjs: List<Pair<Int, String>>,
    streamObjs: Map<Int, Pair<String, ByteArray>>
  ): ByteArray {
    val out = java.io.ByteArrayOutputStream()
    out.write("%PDF-1.4\n".toByteArray(Charsets.US_ASCII))
    for ((id, dict) in plainObjs) {
      out.write("$id 0 obj\n$dict\nendobj\n".toByteArray(Charsets.US_ASCII))
    }
    for ((id, pair) in streamObjs) {
      out.write("$id 0 obj\n${pair.first}\nstream\n".toByteArray(Charsets.US_ASCII))
      out.write(pair.second)
      out.write("\nendstream\nendobj\n".toByteArray(Charsets.US_ASCII))
    }
    out.write("%%EOF\n".toByteArray(Charsets.US_ASCII))
    return out.toByteArray()
  }
}

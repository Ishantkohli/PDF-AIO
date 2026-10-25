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
}

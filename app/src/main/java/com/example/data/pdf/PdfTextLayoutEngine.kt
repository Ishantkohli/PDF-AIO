package com.example.data.pdf

import android.content.Context
import android.graphics.RectF
import com.tom_roush.pdfbox.pdmodel.PDDocument
import com.tom_roush.pdfbox.text.PDFTextStripper
import com.tom_roush.pdfbox.text.TextPosition
import java.io.File
import java.io.StringWriter
import kotlin.math.abs
import kotlin.math.hypot
import kotlin.math.max
import kotlin.math.min

/**
 * Extracts per-character normalized bounding boxes [0f..1f] from native PDF text streams
 * using Apache PDFBox-Android so text selection aligns across zoom, pan, crop, and page rotation.
 * Zero AI/ML — 100% deterministic on-device PDF glyph layout geometry.
 */
object PdfTextLayoutEngine {

    data class TextGlyph(
        val charIndex: Int,
        val ch: Char,
        val left: Float,
        val top: Float,
        val right: Float,
        val bottom: Float,
        val lineId: Int,
        val wordId: Int
    )

    data class PageTextLayout(
        val pageIndex: Int,
        val isSelectable: Boolean,
        val unavailableReason: String?,
        val fullText: String,
        val glyphs: List<TextGlyph>,
        val wordRanges: Map<Int, IntRange>
    )

    fun extractPageLayout(
        context: Context,
        pdfFile: File,
        pageIndex: Int
    ): PageTextLayout {
        if (!pdfFile.exists() || pdfFile.length() == 0L) {
            return PageTextLayout(
                pageIndex = pageIndex,
                isSelectable = false,
                unavailableReason = "Document file is missing or empty.",
                fullText = "",
                glyphs = emptyList(),
                wordRanges = emptyMap()
            )
        }

        PdfBoxInitializer.ensureInitialized(context)

        return try {
            PDDocument.load(pdfFile).use { document ->
                if (pageIndex < 0 || pageIndex >= document.numberOfPages) {
                    return@use PageTextLayout(
                        pageIndex = pageIndex,
                        isSelectable = false,
                        unavailableReason = "Page index out of range.",
                        fullText = "",
                        glyphs = emptyList(),
                        wordRanges = emptyMap()
                    )
                }

                if (document.isEncrypted) {
                    return@use PageTextLayout(
                        pageIndex = pageIndex,
                        isSelectable = false,
                        unavailableReason = "Encrypted PDF: unlock document to select text.",
                        fullText = "",
                        glyphs = emptyList(),
                        wordRanges = emptyMap()
                    )
                }

                val capturedPositions = mutableListOf<List<TextPosition>>()
                val stripper = object : PDFTextStripper() {
                    init {
                        sortByPosition = true
                        startPage = pageIndex + 1
                        endPage = pageIndex + 1
                    }

                    override fun writeString(text: String?, textPositions: MutableList<TextPosition>?) {
                        if (!textPositions.isNullOrEmpty()) {
                            capturedPositions.add(textPositions.toList())
                        }
                        super.writeString(text, textPositions)
                    }
                }

                val writer = StringWriter()
                stripper.writeText(document, writer)

                val page = document.getPage(pageIndex)
                val cropBox = page.cropBox ?: page.mediaBox
                val rotation = ((page.rotation % 360) + 360) % 360
                val rawPageW = if (rotation == 90 || rotation == 270) cropBox.height else cropBox.width
                val rawPageH = if (rotation == 90 || rotation == 270) cropBox.width else cropBox.height

                buildPageLayout(
                    pageIndex = pageIndex,
                    chunks = capturedPositions,
                    fallbackPageWidth = rawPageW.coerceAtLeast(1f),
                    fallbackPageHeight = rawPageH.coerceAtLeast(1f)
                )
            }
        } catch (e: Exception) {
            PageTextLayout(
                pageIndex = pageIndex,
                isSelectable = false,
                unavailableReason = "Unable to inspect embedded text layer on this page.",
                fullText = "",
                glyphs = emptyList(),
                wordRanges = emptyMap()
            )
        }
    }

    internal fun buildPageLayout(
        pageIndex: Int,
        chunks: List<List<TextPosition>>,
        fallbackPageWidth: Float,
        fallbackPageHeight: Float
    ): PageTextLayout {
        val rawEntries = mutableListOf<RawGlyphEntry>()

        for (chunk in chunks) {
            for (tp in chunk) {
                val unicode = tp.unicode ?: continue
                if (unicode.isEmpty()) continue

                val pageW = tp.pageWidth.takeIf { it > 0f } ?: fallbackPageWidth
                val pageH = tp.pageHeight.takeIf { it > 0f } ?: fallbackPageHeight

                val glyphWidth = tp.widthDirAdj.coerceAtLeast(1f)
                val glyphHeight = (tp.heightDir.takeIf { it > 0f } ?: (tp.fontSizeInPt * 0.85f)).coerceAtLeast(4f)
                val xStart = tp.xDirAdj
                val yBaseline = tp.yDirAdj

                val subChars = unicode.length
                val subWidth = glyphWidth / subChars.coerceAtLeast(1)

                for (i in unicode.indices) {
                    val ch = unicode[i]
                    val left = ((xStart + subWidth * i) / pageW).coerceIn(0f, 1f)
                    val right = ((xStart + subWidth * (i + 1)) / pageW).coerceIn(0f, 1f)
                    val top = ((yBaseline - glyphHeight * 1.05f) / pageH).coerceIn(0f, 1f)
                    val bottom = ((yBaseline + glyphHeight * 0.25f) / pageH).coerceIn(0f, 1f)

                    rawEntries.add(
                        RawGlyphEntry(
                            ch = ch,
                            left = min(left, right),
                            top = min(top, bottom),
                            right = max(left, right),
                            bottom = max(top, bottom)
                        )
                    )
                }
            }
        }

        if (rawEntries.none { !it.ch.isWhitespace() }) {
            return PageTextLayout(
                pageIndex = pageIndex,
                isSelectable = false,
                unavailableReason = "Scanned or image-only page: no embedded text layer available.",
                fullText = "",
                glyphs = emptyList(),
                wordRanges = emptyMap()
            )
        }

        // Order lines top-to-bottom within column bands cleanly while inserting spaces/newlines where needed
        val glyphs = mutableListOf<TextGlyph>()
        val textBuilder = StringBuilder()
        val wordRanges = mutableMapOf<Int, IntRange>()

        var lineId = 0
        var wordId = 0
        var currentWordStart = -1
        var prevEntry: RawGlyphEntry? = null

        for (entry in rawEntries) {
            if (prevEntry != null) {
                val avgHeight = max(
                    0.008f,
                    ((prevEntry.bottom - prevEntry.top) + (entry.bottom - entry.top)) * 0.5f
                )
                val prevMidY = (prevEntry.top + prevEntry.bottom) * 0.5f
                val currMidY = (entry.top + entry.bottom) * 0.5f
                val verticalJump = currMidY - prevMidY
                val horizontalGap = entry.left - prevEntry.right

                val isNewLine = abs(verticalJump) > avgHeight * 0.72f
                val isSpaceGap = !isNewLine && horizontalGap > avgHeight * 0.28f

                if (isNewLine) {
                    if (currentWordStart != -1) {
                        wordRanges[wordId] = currentWordStart until glyphs.size
                        wordId++
                        currentWordStart = -1
                    }
                    val isParagraphBreak = verticalJump > avgHeight * 2.25f
                    lineId++
                    val nlIdx = glyphs.size
                    val nlX = prevEntry.right.coerceIn(0f, 1f)
                    textBuilder.append('\n')
                    glyphs.add(
                        TextGlyph(
                            charIndex = nlIdx,
                            ch = '\n',
                            left = nlX,
                            top = prevEntry.top,
                            right = nlX,
                            bottom = prevEntry.bottom,
                            lineId = lineId - 1,
                            wordId = -1
                        )
                    )
                    if (isParagraphBreak) {
                        val nl2Idx = glyphs.size
                        textBuilder.append('\n')
                        glyphs.add(
                            TextGlyph(
                                charIndex = nl2Idx,
                                ch = '\n',
                                left = nlX,
                                top = prevEntry.bottom,
                                right = nlX,
                                bottom = entry.top.coerceAtLeast(prevEntry.bottom),
                                lineId = lineId - 1,
                                wordId = -1
                            )
                        )
                    }
                } else if (isSpaceGap && !entry.ch.isWhitespace() && !prevEntry.ch.isWhitespace()) {
                    if (currentWordStart != -1) {
                        wordRanges[wordId] = currentWordStart until glyphs.size
                        wordId++
                        currentWordStart = -1
                    }
                    val spIdx = glyphs.size
                    textBuilder.append(' ')
                    glyphs.add(
                        TextGlyph(
                            charIndex = spIdx,
                            ch = ' ',
                            left = prevEntry.right,
                            top = min(prevEntry.top, entry.top),
                            right = entry.left,
                            bottom = max(prevEntry.bottom, entry.bottom),
                            lineId = lineId,
                            wordId = -1
                        )
                    )
                }
            }

            val idx = glyphs.size
            val isWordChar = !entry.ch.isWhitespace()
            val assignedWordId = if (isWordChar) {
                if (currentWordStart == -1) currentWordStart = idx
                wordId
            } else {
                if (currentWordStart != -1) {
                    wordRanges[wordId] = currentWordStart until idx
                    wordId++
                    currentWordStart = -1
                }
                -1
            }

            textBuilder.append(entry.ch)
            glyphs.add(
                TextGlyph(
                    charIndex = idx,
                    ch = entry.ch,
                    left = entry.left,
                    top = entry.top,
                    right = entry.right,
                    bottom = entry.bottom,
                    lineId = lineId,
                    wordId = assignedWordId
                )
            )
            prevEntry = entry
        }

        if (currentWordStart != -1) {
            wordRanges[wordId] = currentWordStart until glyphs.size
        }

        return PageTextLayout(
            pageIndex = pageIndex,
            isSelectable = true,
            unavailableReason = null,
            fullText = textBuilder.toString(),
            glyphs = glyphs,
            wordRanges = wordRanges
        )
    }

    fun findNearestGlyphIndex(layout: PageTextLayout, nx: Float, ny: Float, maxDist: Float = 0.085f): Int? {
        if (!layout.isSelectable || layout.glyphs.isEmpty()) return null

        // First try strict hit inside glyph box (tiny vertical slack), picking the closest glyph center
        val strictMatch = layout.glyphs
            .filter { g ->
                !g.ch.isWhitespace() &&
                    nx >= (g.left - 0.001f) && nx <= (g.right + 0.001f) &&
                    ny >= (g.top - 0.008f) && ny <= (g.bottom + 0.008f)
            }
            .minByOrNull { g ->
                val cx = (g.left + g.right) * 0.5f
                val cy = (g.top + g.bottom) * 0.5f
                hypot(cx - nx, cy - ny)
            }
        if (strictMatch != null) return strictMatch.charIndex

        // Nearest non-whitespace center within maxDist (prefer same line vertically)
        var bestIdx: Int? = null
        var bestScore = Float.MAX_VALUE
        for (g in layout.glyphs) {
            if (g.ch.isWhitespace()) continue
            val cx = (g.left + g.right) * 0.5f
            val cy = (g.top + g.bottom) * 0.5f
            val dist = hypot(cx - nx, (cy - ny) * 1.8f)
            if (dist < bestScore) {
                bestScore = dist
                bestIdx = g.charIndex
            }
        }
        return if (bestScore <= maxDist * 1.8f) bestIdx else null
    }

    fun expandToWord(layout: PageTextLayout, index: Int): IntRange {
        val clamped = index.coerceIn(0, (layout.glyphs.size - 1).coerceAtLeast(0))
        if (layout.glyphs.isEmpty()) return 0..0
        val glyph = layout.glyphs[clamped]
        if (glyph.wordId >= 0) {
            layout.wordRanges[glyph.wordId]?.let { return it }
        }
        // Fallback: find nearest word on same line
        val sameLineWord = layout.glyphs
            .filter { it.lineId == glyph.lineId && it.wordId >= 0 }
            .minByOrNull { abs((it.left + it.right) * 0.5f - (glyph.left + glyph.right) * 0.5f) }
        return sameLineWord?.let { layout.wordRanges[it.wordId] } ?: (clamped..clamped)
    }

    fun expandToSentence(layout: PageTextLayout, index: Int): IntRange {
        if (layout.glyphs.isEmpty()) return 0..0
        val text = layout.fullText
        val pos = index.coerceIn(0, text.lastIndex)
        var start = pos
        while (start > 0) {
            val c = text[start - 1]
            if (c == '.' || c == '!' || c == '?') break
            if (c == '\n' && start - 2 >= 0 && text[start - 2] == '\n') break
            start--
        }
        while (start < text.length && text[start].isWhitespace()) start++

        var end = pos
        while (end < text.lastIndex) {
            val c = text[end]
            if (c == '.' || c == '!' || c == '?') break
            if (c == '\n' && end + 1 < text.length && text[end + 1] == '\n') {
                end--
                break
            }
            end++
        }
        if (start > end) return expandToWord(layout, pos)
        return start..end
    }

    fun expandToParagraph(layout: PageTextLayout, index: Int): IntRange {
        if (layout.glyphs.isEmpty()) return 0..0
        val text = layout.fullText
        val pos = index.coerceIn(0, text.lastIndex)
        var start = pos
        while (start > 1) {
            if (text[start - 1] == '\n' && text[start - 2] == '\n') break
            start--
        }
        while (start < text.length && text[start].isWhitespace()) start++

        var end = pos
        while (end < text.lastIndex) {
            if (text[end] == '\n' && end + 1 < text.length && text[end + 1] == '\n') {
                end--
                break
            }
            end++
        }
        while (end > start && text[end].isWhitespace()) end--
        if (start > end) return expandToWord(layout, pos)
        return start..end
    }

    fun getSelectedText(layout: PageTextLayout, range: IntRange): String {
        if (layout.fullText.isEmpty() || range.isEmpty()) return ""
        val start = range.first.coerceIn(0, layout.fullText.lastIndex)
        val end = range.last.coerceIn(start, layout.fullText.lastIndex)
        return layout.fullText.substring(start, end + 1)
    }

    fun getSelectionHighlightRects(layout: PageTextLayout, range: IntRange): List<RectF> {
        if (layout.glyphs.isEmpty() || range.isEmpty()) return emptyList()
        val start = range.first.coerceIn(0, layout.glyphs.lastIndex)
        val end = range.last.coerceIn(start, layout.glyphs.lastIndex)

        val lineRects = linkedMapOf<Int, RectF>()
        for (i in start..end) {
            val g = layout.glyphs[i]
            if (g.ch == '\n') continue
            if (g.right <= g.left && g.bottom <= g.top) continue
            val existing = lineRects[g.lineId]
            if (existing == null) {
                lineRects[g.lineId] = RectF(g.left, g.top, g.right, g.bottom)
            } else {
                existing.left = min(existing.left, g.left)
                existing.top = min(existing.top, g.top)
                existing.right = max(existing.right, g.right)
                existing.bottom = max(existing.bottom, g.bottom)
            }
        }
        return lineRects.values.toList()
    }

    private data class RawGlyphEntry(
        val ch: Char,
        val left: Float,
        val top: Float,
        val right: Float,
        val bottom: Float
    )
}

package com.example.data.pdf

import android.graphics.Bitmap
import android.graphics.Color
import kotlin.math.abs

/**
 * Deterministic Myers Shortest-Edit-Script line diff + pixel difference bitmap utility.
 * 100% offline, zero AI/ML.
 */
object MyersDiff {

    enum class OpType { EQUAL, DELETE, INSERT }

    data class DiffLine(
        val type: OpType,
        val text: String,
        val lineNumberA: Int? = null,
        val lineNumberB: Int? = null
    )

    data class DiffSummary(
        val lines: List<DiffLine>,
        val equalCount: Int,
        val removedCount: Int,
        val addedCount: Int,
        val changePositions: List<Int>
    )

    fun computeLineDiff(a: List<String>, b: List<String>): DiffSummary {
        val n = a.size
        val m = b.size
        if (n == 0 && m == 0) {
            return DiffSummary(emptyList(), 0, 0, 0, emptyList())
        }
        if (n == 0) {
            val added = b.mapIndexed { idx, s -> DiffLine(OpType.INSERT, s, null, idx + 1) }
            return DiffSummary(added, 0, 0, m, added.indices.toList())
        }
        if (m == 0) {
            val removed = a.mapIndexed { idx, s -> DiffLine(OpType.DELETE, s, idx + 1, null) }
            return DiffSummary(removed, 0, n, 0, removed.indices.toList())
        }

        // Guard memory on exceptionally large documents by chunking or falling back to LCS DP within limit
        val maxSize = 2500
        val left = if (n > maxSize) a.take(maxSize) else a
        val right = if (m > maxSize) b.take(maxSize) else b
        val ln = left.size
        val rm = right.size

        // Myers O((N+M)*D) shortest edit script with trace snapshots
        val maxD = ln + rm
        val offset = maxD
        var v = IntArray(2 * maxD + 1)
        val trace = ArrayList<IntArray>()

        outer@ for (d in 0..maxD) {
            trace.add(v.copyOf())
            for (k in -d..d step 2) {
                var x = if (k == -d || (k != d && v[k - 1 + offset] < v[k + 1 + offset])) {
                    v[k + 1 + offset]
                } else {
                    v[k - 1 + offset] + 1
                }
                var y = x - k
                while (x < ln && y < rm && left[x] == right[y]) {
                    x++
                    y++
                }
                v[k + offset] = x
                if (x >= ln && y >= rm) {
                    break@outer
                }
            }
        }

        // Backtrack trace
        val opsReversed = mutableListOf<DiffLine>()
        var x = ln
        var y = rm
        for (d in trace.size - 1 downTo 0) {
            val vPrev = trace[d]
            val k = x - y
            val prevK = if (k == -d || (k != d && vPrev[k - 1 + offset] < vPrev[k + 1 + offset])) {
                k + 1
            } else {
                k - 1
            }
            val prevX = vPrev[prevK + offset]
            val prevY = prevX - prevK

            while (x > prevX && y > prevY) {
                x--
                y--
                opsReversed.add(DiffLine(OpType.EQUAL, left[x], x + 1, y + 1))
            }

            if (d > 0) {
                if (x == prevX) {
                    y--
                    opsReversed.add(DiffLine(OpType.INSERT, right[y], null, y + 1))
                } else {
                    x--
                    opsReversed.add(DiffLine(OpType.DELETE, left[x], x + 1, null))
                }
            }
        }

        val result = opsReversed.asReversed()
        var eq = 0
        var rem = 0
        var add = 0
        val changeIndices = mutableListOf<Int>()
        result.forEachIndexed { idx, line ->
            when (line.type) {
                OpType.EQUAL -> eq++
                OpType.DELETE -> {
                    rem++
                    changeIndices.add(idx)
                }
                OpType.INSERT -> {
                    add++
                    changeIndices.add(idx)
                }
            }
        }
        return DiffSummary(
            lines = result,
            equalCount = eq,
            removedCount = rem,
            addedCount = add,
            changePositions = changeIndices
        )
    }

    /**
     * Generates a visual pixel-difference overlay bitmap highlighting altered pixels in magenta
     * while rendering unchanged regions as dimmed grayscale. Returns (diffBitmap, percentPixelsChanged).
     */
    fun computePixelDiffBitmap(bitmapA: Bitmap, bitmapB: Bitmap): Pair<Bitmap, Double> {
        val targetW = maxOf(bitmapA.width, bitmapB.width).coerceAtLeast(1)
        val targetH = maxOf(bitmapA.height, bitmapB.height).coerceAtLeast(1)
        val scaledA = if (bitmapA.width == targetW && bitmapA.height == targetH) bitmapA
        else Bitmap.createScaledBitmap(bitmapA, targetW, targetH, true)
        val scaledB = if (bitmapB.width == targetW && bitmapB.height == targetH) bitmapB
        else Bitmap.createScaledBitmap(bitmapB, targetW, targetH, true)

        val total = targetW * targetH
        val pixelsA = IntArray(total)
        val pixelsB = IntArray(total)
        val outPixels = IntArray(total)
        scaledA.getPixels(pixelsA, 0, targetW, 0, 0, targetW, targetH)
        scaledB.getPixels(pixelsB, 0, targetW, 0, 0, targetW, targetH)

        var changedCount = 0
        val threshold = 24
        for (i in 0 until total) {
            val pa = pixelsA[i]
            val pb = pixelsB[i]
            val dr = abs(Color.red(pa) - Color.red(pb))
            val dg = abs(Color.green(pa) - Color.green(pb))
            val db = abs(Color.blue(pa) - Color.blue(pb))
            if (dr > threshold || dg > threshold || db > threshold) {
                changedCount++
                outPixels[i] = Color.rgb(236, 72, 153) // High-contrast magenta highlight
            } else {
                val luma = (Color.red(pb) * 30 + Color.green(pb) * 59 + Color.blue(pb) * 11) / 100
                val muted = 255 - ((255 - luma) / 3)
                outPixels[i] = Color.rgb(muted, muted, muted)
            }
        }

        if (scaledA !== bitmapA) scaledA.recycle()
        if (scaledB !== bitmapB) scaledB.recycle()

        val outBitmap = Bitmap.createBitmap(targetW, targetH, Bitmap.Config.ARGB_8888)
        outBitmap.setPixels(outPixels, 0, targetW, 0, 0, targetW, targetH)
        val percent = (changedCount * 100.0) / total.coerceAtLeast(1)
        return outBitmap to percent
    }

    fun generateComparisonReport(
        nameA: String,
        nameB: String,
        pageCountA: Int,
        pageCountB: Int,
        summary: DiffSummary,
        pixelDiffPercent: Double?
    ): String {
        val sb = StringBuilder()
        sb.appendLine("# PDF (AIO) Local Document Comparison Report")
        sb.appendLine()
        sb.appendLine("- **Document A (Original):** $nameA ($pageCountA pages)")
        sb.appendLine("- **Document B (Revised):** $nameB ($pageCountB pages)")
        if (pageCountA != pageCountB) {
            val delta = pageCountB - pageCountA
            sb.appendLine("- **Page Count Change:** ${if (delta > 0) "+$delta added" else "$delta removed"}")
        } else {
            sb.appendLine("- **Page Count:** Identical ($pageCountA pages)")
        }
        pixelDiffPercent?.let {
            sb.appendLine("- **Active Page Pixel Variance:** ${"%.2f".format(it)}%")
        }
        sb.appendLine("- **Text Summary:** ${summary.removedCount} removed lines, ${summary.addedCount} added lines, ${summary.equalCount} unchanged lines")
        sb.appendLine()
        sb.appendLine("## Line-by-Line Myers Diff")
        sb.appendLine()
        for (line in summary.lines) {
            when (line.type) {
                OpType.DELETE -> sb.appendLine("- ${line.text}")
                OpType.INSERT -> sb.appendLine("+ ${line.text}")
                OpType.EQUAL -> sb.appendLine("  ${line.text}")
            }
        }
        return sb.toString()
    }
}

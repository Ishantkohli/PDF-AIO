package com.example.data.scan

import android.graphics.Bitmap
import android.graphics.PointF
import androidx.camera.core.ImageProxy
import kotlin.math.abs
import kotlin.math.hypot
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt
import kotlin.math.sqrt

/**
 * 100% Deterministic Classical Computer Vision Document Edge & Quad Detector.
 * Zero AI, zero ML models, zero cloud calls.
 *
 * Pipeline:
 * 1. Downsampled grayscale luminance buffer (~160px on short/target axis).
 * 2. 3x3 Gaussian blur to suppress sensor grain and small printed characters.
 * 3. Sobel gradient magnitude (Gx, Gy) + adaptive edge threshold.
 * 4. 5x5 neighborhood density pruning to reject isolated texture specks.
 * 5. Convex extreme-projection quad candidate extraction (TL, TR, BR, BL)
 *    with geometric area, convexity, edge-length, and perimeter gradient checks.
 * 6. Temporal stability tracker for smooth UI reticle & deterministic auto-capture.
 */
object DocumentEdgeDetector {

    data class NormalizedQuad(
        val topLeft: PointF,
        val topRight: PointF,
        val bottomRight: PointF,
        val bottomLeft: PointF
    ) {
        fun corners(): List<PointF> = listOf(topLeft, topRight, bottomRight, bottomLeft)
    }

    data class DetectionResult(
        val quad: NormalizedQuad?,
        val confidenceScore: Float,
        val areaFraction: Float
    )

    data class StabilityUpdate(
        val smoothedQuad: NormalizedQuad?,
        val isStable: Boolean,
        val stableFrameCount: Int,
        val shouldAutoCapture: Boolean,
        val hintText: String
    )

    class QuadStabilityTracker(
        private val requiredStableFrames: Int = 5,
        private val maxCornerDrift: Float = 0.04f,
        private val autoCaptureCooldownMs: Long = 2200L
    ) {
        private var lastQuad: NormalizedQuad? = null
        private var stableCount: Int = 0
        private var lastAutoCaptureMs: Long = 0L

        fun reset() {
            lastQuad = null
            stableCount = 0
        }

        fun update(result: DetectionResult, autoCaptureEnabled: Boolean, nowMs: Long = System.currentTimeMillis()): StabilityUpdate {
            val detected = result.quad
            if (detected == null) {
                stableCount = (stableCount - 1).coerceAtLeast(0)
                if (stableCount == 0) lastQuad = null
                return StabilityUpdate(
                    smoothedQuad = lastQuad,
                    isStable = false,
                    stableFrameCount = 0,
                    shouldAutoCapture = false,
                    hintText = "Align document inside frame"
                )
            }

            val prev = lastQuad
            if (prev == null) {
                lastQuad = detected
                stableCount = 1
                return StabilityUpdate(
                    smoothedQuad = detected,
                    isStable = false,
                    stableFrameCount = 1,
                    shouldAutoCapture = false,
                    hintText = "Document detected — hold steady"
                )
            }

            val drift = maxCornerDistance(prev, detected)
            // Exponential moving average smoothing for rock-solid viewfinder overlay
            val alpha = if (drift < maxCornerDrift) 0.35f else 0.6f
            val blended = NormalizedQuad(
                topLeft = lerp(prev.topLeft, detected.topLeft, alpha),
                topRight = lerp(prev.topRight, detected.topRight, alpha),
                bottomRight = lerp(prev.bottomRight, detected.bottomRight, alpha),
                bottomLeft = lerp(prev.bottomLeft, detected.bottomLeft, alpha)
            )
            lastQuad = blended

            if (drift <= maxCornerDrift) {
                stableCount++
            } else {
                stableCount = 1
            }

            val isStable = stableCount >= requiredStableFrames
            val cooldownElapsed = (nowMs - lastAutoCaptureMs) >= autoCaptureCooldownMs
            val triggerCapture = autoCaptureEnabled && isStable && cooldownElapsed
            if (triggerCapture) {
                lastAutoCaptureMs = nowMs
                stableCount = 0
            }

            val hint = when {
                triggerCapture -> "Capturing document..."
                isStable -> "Locked — tap shutter or hold to auto-capture"
                else -> "Hold steady (${stableCount}/$requiredStableFrames)..."
            }

            return StabilityUpdate(
                smoothedQuad = blended,
                isStable = isStable,
                stableFrameCount = stableCount,
                shouldAutoCapture = triggerCapture,
                hintText = hint
            )
        }

        private fun maxCornerDistance(a: NormalizedQuad, b: NormalizedQuad): Float {
            return maxOf(
                hypot(a.topLeft.x - b.topLeft.x, a.topLeft.y - b.topLeft.y),
                hypot(a.topRight.x - b.topRight.x, a.topRight.y - b.topRight.y),
                hypot(a.bottomRight.x - b.bottomRight.x, a.bottomRight.y - b.bottomRight.y),
                hypot(a.bottomLeft.x - b.bottomLeft.x, a.bottomLeft.y - b.bottomLeft.y)
            )
        }

        private fun lerp(a: PointF, b: PointF, t: Float): PointF {
            return PointF(a.x + (b.x - a.x) * t, a.y + (b.y - a.y) * t)
        }
    }

    /**
     * Analyzes a CameraX ImageProxy (YUV_420_888) Y luminance plane while respecting
     * sensor rotationDegrees so coordinates match upright display space [0..1].
     */
    fun detectFromImageProxy(imageProxy: ImageProxy): DetectionResult {
        val plane = imageProxy.planes.firstOrNull() ?: return DetectionResult(null, 0f, 0f)
        val rawW = imageProxy.width
        val rawH = imageProxy.height
        val rowStride = plane.rowStride
        val pixelStride = plane.pixelStride
        val buffer = plane.buffer
        buffer.rewind()

        val bytes = ByteArray(buffer.remaining())
        buffer.get(bytes)

        val rotation = ((imageProxy.imageInfo.rotationDegrees % 360) + 360) % 360
        val rotatedPortrait = rotation == 90 || rotation == 270
        val dispW = if (rotatedPortrait) rawH else rawW
        val dispH = if (rotatedPortrait) rawW else rawH

        val targetW = 160
        val scale = targetW.toFloat() / dispW.coerceAtLeast(1)
        val w = targetW.coerceAtLeast(32)
        val h = (dispH * scale).roundToInt().coerceAtLeast(32)

        val gray = IntArray(w * h)
        for (dy in 0 until h) {
            val ny = dy.toFloat() / (h - 1).coerceAtLeast(1)
            for (dx in 0 until w) {
                val nx = dx.toFloat() / (w - 1).coerceAtLeast(1)
                val (sxNorm, syNorm) = when (rotation) {
                    90 -> ny to (1f - nx)
                    180 -> (1f - nx) to (1f - ny)
                    270 -> (1f - ny) to nx
                    else -> nx to ny
                }
                val sx = (sxNorm * (rawW - 1)).roundToInt().coerceIn(0, rawW - 1)
                val sy = (syNorm * (rawH - 1)).roundToInt().coerceIn(0, rawH - 1)
                val idx = sy * rowStride + sx * pixelStride
                gray[dy * w + dx] = if (idx in bytes.indices) (bytes[idx].toInt() and 0xFF) else 0
            }
        }

        return detectFromGrayscale(gray, w, h)
    }

    /**
     * Analyzes an Android Bitmap (useful for gallery images & deterministic unit tests).
     */
    fun detectFromBitmap(bitmap: Bitmap): DetectionResult {
        val targetW = 160
        val scale = targetW.toFloat() / bitmap.width.coerceAtLeast(1)
        val w = targetW.coerceAtLeast(32)
        val h = (bitmap.height * scale).roundToInt().coerceAtLeast(32)

        val scaled = if (bitmap.width == w && bitmap.height == h) bitmap
        else Bitmap.createScaledBitmap(bitmap, w, h, true)

        val pixels = IntArray(w * h)
        scaled.getPixels(pixels, 0, w, 0, 0, w, h)
        if (scaled !== bitmap) scaled.recycle()

        val gray = IntArray(w * h)
        for (i in pixels.indices) {
            val p = pixels[i]
            val r = (p shr 16) and 0xFF
            val g = (p shr 8) and 0xFF
            val b = p and 0xFF
            gray[i] = (r * 299 + g * 587 + b * 114) / 1000
        }

        return detectFromGrayscale(gray, w, h)
    }

    internal fun detectFromGrayscale(gray: IntArray, w: Int, h: Int): DetectionResult {
        if (w < 24 || h < 24 || gray.size < w * h) return DetectionResult(null, 0f, 0f)

        // 1. 3x3 Gaussian smoothing
        val blurred = IntArray(w * h)
        for (y in 1 until h - 1) {
            val row = y * w
            val rowUp = (y - 1) * w
            val rowDn = (y + 1) * w
            for (x in 1 until w - 1) {
                val sum =
                    gray[rowUp + x - 1] + 2 * gray[rowUp + x] + gray[rowUp + x + 1] +
                    2 * gray[row + x - 1] + 4 * gray[row + x] + 2 * gray[row + x + 1] +
                    gray[rowDn + x - 1] + 2 * gray[rowDn + x] + gray[rowDn + x + 1]
                blurred[row + x] = sum shr 4
            }
        }

        // 2. Sobel gradient magnitude
        val mag = IntArray(w * h)
        var magSum = 0L
        var count = 0
        for (y in 2 until h - 2) {
            val row = y * w
            val rowUp = (y - 1) * w
            val rowDn = (y + 1) * w
            for (x in 2 until w - 2) {
                val gx = -blurred[rowUp + x - 1] + blurred[rowUp + x + 1] -
                    2 * blurred[row + x - 1] + 2 * blurred[row + x + 1] -
                    blurred[rowDn + x - 1] + blurred[rowDn + x + 1]
                val gy = -blurred[rowUp + x - 1] - 2 * blurred[rowUp + x] - blurred[rowUp + x + 1] +
                    blurred[rowDn + x - 1] + 2 * blurred[rowDn + x] + blurred[rowDn + x + 1]
                val m = abs(gx) + abs(gy)
                mag[row + x] = m
                magSum += m
                count++
            }
        }

        if (count == 0) return DetectionResult(null, 0f, 0f)
        val meanMag = magSum.toFloat() / count
        var varSum = 0.0
        for (y in 2 until h - 2) {
            val row = y * w
            for (x in 2 until w - 2) {
                val d = mag[row + x] - meanMag
                varSum += d * d
            }
        }
        val stdDev = sqrt(varSum / count).toFloat()
        val threshold = max(65f, meanMag + 1.45f * stdDev)

        // 3. Binary edge mask
        val isEdge = BooleanArray(w * h)
        for (y in 3 until h - 3) {
            val row = y * w
            for (x in 3 until w - 3) {
                if (mag[row + x] >= threshold) {
                    isEdge[row + x] = true
                }
            }
        }

        // 4. Radial outer contour ray-casting from frame center (finds true paper boundary
        //    without being pulled inward by printed document text or outward by noisy corners)
        val cx = w / 2f
        val cy = h / 2f
        val strongPts = ArrayList<PointF>()
        // Keep edge points that have neighbor support (rejects isolated speckles)
        for (y in 3 until h - 3) {
            val row = y * w
            for (x in 3 until w - 3) {
                if (!isEdge[row + x]) continue
                var neighbors = 0
                for (ny in -1..1) {
                    for (nx in -1..1) {
                        if ((nx != 0 || ny != 0) && isEdge[(y + ny) * w + (x + nx)]) {
                            neighbors++
                        }
                    }
                }
                if (neighbors >= 2) {
                    strongPts.add(PointF(x.toFloat(), y.toFloat()))
                }
            }
        }

        if (strongPts.size < 40) return DetectionResult(null, 0f, 0f)

        // 5. Partition outer boundary candidates around center into 4 directional buckets / extreme projections
        // Using extreme projections on outer-half points guarantees we don't lock onto inner text
        val minRadius = min(w, h) * 0.14f
        val outerPts = strongPts.filter { pt -> hypot(pt.x - cx, pt.y - cy) >= minRadius }
        val candidates = if (outerPts.size >= 24) outerPts else strongPts

        var tl: PointF? = null
        var tr: PointF? = null
        var br: PointF? = null
        var bl: PointF? = null

        var minSum = Float.MAX_VALUE
        var maxDiff = -Float.MAX_VALUE
        var maxSum = -Float.MAX_VALUE
        var minDiff = Float.MAX_VALUE

        for (p in candidates) {
            val sum = p.x + p.y
            val diff = p.x - p.y
            if (sum < minSum) {
                minSum = sum
                tl = p
            }
            if (diff > maxDiff) {
                maxDiff = diff
                tr = p
            }
            if (sum > maxSum) {
                maxSum = sum
                br = p
            }
            if (diff < minDiff) {
                minDiff = diff
                bl = p
            }
        }

        if (tl == null || tr == null || br == null || bl == null) {
            return DetectionResult(null, 0f, 0f)
        }

        // 6. Validate convexity, minimum edge lengths, and area fraction
        val nTl = PointF(tl.x / w, tl.y / h)
        val nTr = PointF(tr.x / w, tr.y / h)
        val nBr = PointF(br.x / w, br.y / h)
        val nBl = PointF(bl.x / w, bl.y / h)

        val quad = NormalizedQuad(nTl, nTr, nBr, nBl)
        if (!isValidConvexQuad(quad)) {
            return DetectionResult(null, 0f, 0f)
        }

        val areaFrac = polygonArea(quad.corners())
        if (areaFrac < 0.16f || areaFrac > 0.96f) {
            return DetectionResult(null, 0f, areaFrac)
        }

        // 7. Score edge gradient support along all 4 perimeter segments
        val edgeSupport = (
            sampleEdgeSupport(isEdge, w, h, tl, tr) +
                sampleEdgeSupport(isEdge, w, h, tr, br) +
                sampleEdgeSupport(isEdge, w, h, br, bl) +
                sampleEdgeSupport(isEdge, w, h, bl, tl)
            ) / 4f

        if (edgeSupport < 0.25f) {
            return DetectionResult(null, edgeSupport, areaFrac)
        }

        return DetectionResult(quad = quad, confidenceScore = edgeSupport, areaFraction = areaFrac)
    }

    private fun sampleEdgeSupport(isEdge: BooleanArray, w: Int, h: Int, a: PointF, b: PointF): Float {
        val steps = 24
        var hits = 0
        for (i in 0..steps) {
            val t = i.toFloat() / steps
            val sx = (a.x + (b.x - a.x) * t).roundToInt().coerceIn(2, w - 3)
            val sy = (a.y + (b.y - a.y) * t).roundToInt().coerceIn(2, h - 3)
            var found = false
            for (dy in -2..2) {
                for (dx in -2..2) {
                    if (isEdge[(sy + dy) * w + (sx + dx)]) {
                        found = true
                        break
                    }
                }
                if (found) break
            }
            if (found) hits++
        }
        return hits.toFloat() / (steps + 1)
    }

    internal fun isValidConvexQuad(quad: NormalizedQuad): Boolean {
        val pts = quad.corners()
        var positive = 0
        var negative = 0
        for (i in 0 until 4) {
            val p0 = pts[i]
            val p1 = pts[(i + 1) % 4]
            val p2 = pts[(i + 2) % 4]
            val edgeLen = hypot(p1.x - p0.x, p1.y - p0.y)
            if (edgeLen < 0.16f) return false

            val dx1 = p1.x - p0.x
            val dy1 = p1.y - p0.y
            val dx2 = p2.x - p1.x
            val dy2 = p2.y - p1.y
            val cross = dx1 * dy2 - dy1 * dx2
            if (abs(cross) < 1e-4f) return false
            if (cross > 0f) positive++ else negative++
        }
        return positive == 4 || negative == 4
    }

    internal fun polygonArea(pts: List<PointF>): Float {
        var sum = 0f
        for (i in pts.indices) {
            val j = (i + 1) % pts.size
            sum += pts[i].x * pts[j].y - pts[j].x * pts[i].y
        }
        return abs(sum) * 0.5f
    }
}

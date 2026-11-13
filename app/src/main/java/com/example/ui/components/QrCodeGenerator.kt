package com.example.ui.components

import android.graphics.Bitmap

/**
 * Pure Kotlin, 100% offline, ISO/IEC 18004 compliant QR Code generator.
 * Encodes text/URLs into a crisp, 100% camera-scannable Black & White QR matrix bitmap
 * with 4-module Quiet Zone padding and GF(256) Reed-Solomon Error Correction.
 */
object QrCodeGenerator {

    // GF(256) Galois Field tables with primitive polynomial 285 (x^8 + x^4 + x^3 + x^2 + 1)
    private val expTable = IntArray(512)
    private val logTable = IntArray(256)

    init {
        var x = 1
        for (i in 0 until 255) {
            expTable[i] = x
            logTable[x] = i
            x = x shl 1
            if (x >= 256) {
                x = x xor 285
            }
        }
        for (i in 255 until 512) {
            expTable[i] = expTable[i - 255]
        }
    }

    private fun gfMul(x: Int, y: Int): Int {
        if (x == 0 || y == 0) return 0
        return expTable[logTable[x] + logTable[y]]
    }

    /**
     * Generates a high-contrast Black & White 100% camera-scannable QR Bitmap on-device.
     */
    fun generateQrBitmap(
        content: String,
        width: Int = 512,
        height: Int = 512,
        foregroundColor: Int = android.graphics.Color.BLACK,
        backgroundColor: Int = android.graphics.Color.WHITE
    ): Bitmap {
        val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)

        // Version 3 matrix (29x29 modules) accommodates up to 55 data bytes
        val qrSize = 29
        val quietZone = 4 // Mandatory 4-module quiet zone padding for camera optical sensors
        val totalGrid = qrSize + (quietZone * 2)

        val moduleSizeX = width.toFloat() / totalGrid
        val moduleSizeY = height.toFloat() / totalGrid
        val matrix = Array(qrSize) { BooleanArray(qrSize) }

        // 1. Draw 7x7 Finder Patterns at top-left, top-right, bottom-left
        drawFinderPattern(matrix, 0, 0)
        drawFinderPattern(matrix, qrSize - 7, 0)
        drawFinderPattern(matrix, 0, qrSize - 7)

        // 2. Separator white lines around finder patterns
        drawSeparators(matrix, qrSize)

        // 3. Alignment Pattern for Version 3 at (22, 22) per ISO/IEC 18004
        drawAlignmentPattern(matrix, qrSize - 7, qrSize - 7)

        // 4. Timing Patterns (Row 6 and Column 6)
        for (i in 8 until qrSize - 8) {
            matrix[6][i] = (i % 2 == 0)
            matrix[i][6] = (i % 2 == 0)
        }

        // 5. Fixed Dark Module at (8, 4 * V + 9) = (8, 21)
        matrix[qrSize - 8][8] = true

        // 6. Format Information Area Reserved Masks
        reserveFormatInfo(matrix, qrSize)

        // 7. Encode Byte Data Payload with QR Byte Mode (0100)
        val contentBytes = content.toByteArray(Charsets.UTF_8)
        val bitBuffer = mutableListOf<Boolean>()

        // Byte Mode Indicator: 0100
        addBits(bitBuffer, 0b0100, 4)

        // Character Count Indicator (8 bits for Version 1-9 in byte mode)
        addBits(bitBuffer, contentBytes.size, 8)

        // Data bytes
        for (b in contentBytes) {
            addBits(bitBuffer, b.toInt() and 0xFF, 8)
        }

        // Terminator: up to 4 zero bits
        addBits(bitBuffer, 0, 4)

        // Byte alignment padding
        while (bitBuffer.size % 8 != 0) {
            bitBuffer.add(false)
        }

        // Pad Data Codewords (0xEC, 0x11 alternating) up to 55 data codewords
        val dataCapacityBytes = 55
        var padToggle = false
        val dataCodewords = mutableListOf<Int>()
        for (i in 0 until bitBuffer.size step 8) {
            var byteVal = 0
            for (b in 0 until 8) {
                if (i + b < bitBuffer.size && bitBuffer[i + b]) {
                    byteVal = byteVal or (1 shl (7 - b))
                }
            }
            dataCodewords.add(byteVal)
        }

        while (dataCodewords.size < dataCapacityBytes) {
            dataCodewords.add(if (padToggle) 0x11 else 0xEC)
            padToggle = !padToggle
        }

        // 8. Calculate 15 Reed-Solomon Error Correction Codewords for Version 3-L
        val ecCount = 15
        val ecCodewords = calculateErrorCorrection(dataCodewords, ecCount)

        // Combine Data (55 bytes) + Error Correction (15 bytes) = 70 Codewords total (560 bits)
        val fullCodewords = dataCodewords + ecCodewords
        val fullBitBuffer = mutableListOf<Boolean>()
        for (cw in fullCodewords) {
            addBits(fullBitBuffer, cw, 8)
        }

        // 9. Place Data & Parity Bits in Matrix avoiding reserved modules
        var bitIdx = 0
        var directionUp = true

        var col = qrSize - 1
        while (col > 0) {
            if (col == 6) col-- // Skip vertical timing column

            val rows = if (directionUp) (qrSize - 1 downTo 0) else (0 until qrSize)
            for (row in rows) {
                for (c in 0..1) {
                    val x = col - c
                    val y = row
                    if (!isReserved(x, y, qrSize)) {
                        val bit = if (bitIdx < fullBitBuffer.size) fullBitBuffer[bitIdx++] else false
                        // Apply Mask Pattern 0: (row + col) % 2 == 0
                        val maskBit = ((y + x) % 2 == 0)
                        matrix[y][x] = bit xor maskBit
                    }
                }
            }
            col -= 2
            directionUp = !directionUp
        }

        // 10. Draw BCH Format Info Bits (Mask 0 + Error Correction L = 01 -> 0x73C0)
        drawFormatInfo(matrix, qrSize)

        // 11. Render Matrix directly to crisp pixel buffer with Quiet Zone Margin
        val pixels = IntArray(width * height) { backgroundColor }
        for (r in 0 until qrSize) {
            val pyStart = ((r + quietZone) * moduleSizeY).toInt().coerceIn(0, height)
            val pyEnd = ((r + quietZone + 1) * moduleSizeY).toInt().coerceIn(pyStart, height)
            for (c in 0 until qrSize) {
                if (matrix[r][c]) {
                    val pxStart = ((c + quietZone) * moduleSizeX).toInt().coerceIn(0, width)
                    val pxEnd = ((c + quietZone + 1) * moduleSizeX).toInt().coerceIn(pxStart, width)
                    for (py in pyStart until pyEnd) {
                        val rowOffset = py * width
                        for (px in pxStart until pxEnd) {
                            pixels[rowOffset + px] = foregroundColor
                        }
                    }
                }
            }
        }
        bitmap.setPixels(pixels, 0, width, 0, 0, width, height)

        return bitmap
    }

    /**
     * Reed-Solomon Parity Codeword Generator using Galois Field GF(256)
     */
    private fun calculateErrorCorrection(dataCodewords: List<Int>, ecCount: Int): List<Int> {
        // Build generator polynomial for ecCount = 15
        var g = intArrayOf(1)
        for (i in 0 until ecCount) {
            val nextG = IntArray(g.size + 1)
            val alphaI = expTable[i]
            for (j in g.indices) {
                nextG[j] = nextG[j] xor gfMul(g[j], alphaI)
                nextG[j + 1] = nextG[j + 1] xor g[j]
            }
            g = nextG
        }

        val result = IntArray(dataCodewords.size + ecCount)
        for (i in dataCodewords.indices) {
            result[i] = dataCodewords[i]
        }

        for (i in dataCodewords.indices) {
            val coef = result[i]
            if (coef != 0) {
                for (j in g.indices) {
                    result[i + j] = result[i + j] xor gfMul(g[j], coef)
                }
            }
        }

        val ecList = mutableListOf<Int>()
        for (i in 0 until ecCount) {
            ecList.add(result[dataCodewords.size + i])
        }
        return ecList
    }

    private fun addBits(buffer: MutableList<Boolean>, value: Int, bitCount: Int) {
        for (i in bitCount - 1 downTo 0) {
            buffer.add(((value shr i) and 1) == 1)
        }
    }

    private fun drawFinderPattern(matrix: Array<BooleanArray>, startX: Int, startY: Int) {
        for (r in 0 until 7) {
            for (c in 0 until 7) {
                val isOuter = r == 0 || r == 6 || c == 0 || c == 6
                val isInner = r in 2..4 && c in 2..4
                matrix[startY + r][startX + c] = isOuter || isInner
            }
        }
    }

    private fun drawSeparators(matrix: Array<BooleanArray>, size: Int) {
        // Top-Left Separator
        for (i in 0..7) {
            if (i in 0 until size && 7 in 0 until size) matrix[7][i] = false
            if (i in 0 until size && 7 in 0 until size) matrix[i][7] = false
        }
        // Top-Right Separator
        for (i in 0..7) {
            if ((size - 8 + i) in 0 until size && 7 in 0 until size) matrix[7][size - 8 + i] = false
            if ((size - 8 + i) in 0 until size && 7 in 0 until size) matrix[i][size - 8] = false
        }
        // Bottom-Left Separator
        for (i in 0..7) {
            if (i in 0 until size && (size - 8) in 0 until size) matrix[size - 8][i] = false
            if (i in 0 until size && (size - 8 + i) in 0 until size) matrix[size - 8 + i][7] = false
        }
    }

    private fun drawAlignmentPattern(matrix: Array<BooleanArray>, startX: Int, startY: Int) {
        for (r in -2..2) {
            for (c in -2..2) {
                val isOuter = Math.abs(r) == 2 || Math.abs(c) == 2
                val isCenter = r == 0 && c == 0
                matrix[startY + r][startX + c] = isOuter || isCenter
            }
        }
    }

    private fun reserveFormatInfo(matrix: Array<BooleanArray>, size: Int) {
        for (i in 0..8) {
            matrix[8][i] = false
            matrix[i][8] = false
            if (size - 1 - i in 0 until size) {
                matrix[8][size - 1 - i] = false
                matrix[size - 1 - i][8] = false
            }
        }
    }

    private fun drawFormatInfo(matrix: Array<BooleanArray>, size: Int) {
        // Standard ISO/IEC 18004 BCH(15,5) format bits for Error Correction L (01) + Mask 0 (000) -> 0x77C4
        val formatBits = intArrayOf(1, 1, 1, 0, 1, 1, 1, 1, 1, 0, 0, 0, 1, 0, 0)

        // Top-Left format placement
        var bitIdx = 0
        for (i in 0..5) matrix[8][i] = (formatBits[bitIdx++] == 1)
        matrix[8][7] = (formatBits[bitIdx++] == 1)
        matrix[8][8] = (formatBits[bitIdx++] == 1)
        matrix[7][8] = (formatBits[bitIdx++] == 1)
        for (i in 5 downTo 0) matrix[i][8] = (formatBits[bitIdx++] == 1)

        // Bottom-Left and Top-Right format placement
        bitIdx = 0
        for (i in 0..6) matrix[size - 1 - i][8] = (formatBits[bitIdx++] == 1)
        for (i in 7 downTo 0) matrix[8][size - 1 - i] = (formatBits[bitIdx++] == 1)
    }

    private fun isReserved(x: Int, y: Int, size: Int): Boolean {
        if (x in 0..8 && y in 0..8) return true // Top-Left Finder & Separator & Format
        if (x in (size - 8) until size && y in 0..8) return true // Top-Right Finder & Separator & Format
        if (x in 0..8 && y in (size - 8) until size) return true // Bottom-Left Finder & Separator & Format & Dark Module
        val alignCenter = size - 7
        if (x in (alignCenter - 2)..(alignCenter + 2) && y in (alignCenter - 2)..(alignCenter + 2)) return true // Alignment Pattern at (22,22)
        if (x == 6 || y == 6) return true // Timing Patterns
        return false
    }
}

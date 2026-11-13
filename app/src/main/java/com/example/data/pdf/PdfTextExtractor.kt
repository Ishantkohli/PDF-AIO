package com.example.data.pdf

import android.util.Log
import java.io.ByteArrayOutputStream
import java.io.File
import java.util.zip.Inflater

/**
 * Pure-Kotlin PDF Text & CMap Extraction Engine.
 *
 * Properly handles:
 * - Cross-reference / indirect objects (X Y obj ... endobj)
 * - PDF 1.5+ Object Streams (/Type /ObjStm)
 * - FlateDecode (zlib / raw deflate + PNG/TIFF predictors), ASCII85Decode, ASCIIHexDecode
 * - Page tree traversal (/Catalog -> /Pages -> /Kids) preserving reading order
 * - Inherited /Resources (/Font and Form /XObject)
 * - /ToUnicode CMap tables (begincodespacerange, beginbfchar, beginbfrange)
 * - WinAnsiEncoding, MacRomanEncoding, and custom /Differences glyph tables
 * - Balanced literal strings (...), octal escapes, hex strings <...>, UTF-16BE BOM
 * - Text positioning (BT/ET, Tf, Td/TD, Tm, T*, Tj, TJ, ', ") & Form XObjects (Do)
 */
object PdfTextExtractor {

    private const val TAG = "PdfTextExtractor"

    data class ExtractedPage(
        val pageNumber: Int,
        val paragraphs: List<String>
    ) {
        val fullText: String get() = paragraphs.joinToString("\n")
    }

    private data class PdfObj(
        val id: Int,
        val dict: String,
        val rawBody: String,
        val streamBytes: ByteArray?
    )

    private class ToUnicodeCMap {
        var codeByteLength: Int = 2
        val charMap = HashMap<Int, String>()

        fun decode(bytes: ByteArray): String {
            if (bytes.isEmpty()) return ""
            val sb = StringBuilder()
            val step = if (codeByteLength == 1) 1 else if (bytes.size >= 2 && bytes.size % 2 == 0) 2 else 1
            var i = 0
            while (i < bytes.size) {
                if (step == 2 && i + 1 < bytes.size) {
                    val code = ((bytes[i].toInt() and 0xFF) shl 8) or (bytes[i + 1].toInt() and 0xFF)
                    val mapped = charMap[code]
                    if (mapped != null) {
                        sb.append(mapped)
                    } else if (code in 0x20..0x7E) {
                        sb.append(code.toChar())
                    } else {
                        // Try 1-byte fallback if 2-byte lookup missed
                        val b1 = bytes[i].toInt() and 0xFF
                        val b2 = bytes[i + 1].toInt() and 0xFF
                        charMap[b1]?.let { sb.append(it) } ?: if (b1 in 0x20..0x7E) sb.append(b1.toChar()) else Unit
                        charMap[b2]?.let { sb.append(it) } ?: if (b2 in 0x20..0x7E) sb.append(b2.toChar()) else Unit
                    }
                    i += 2
                } else {
                    val code = bytes[i].toInt() and 0xFF
                    val mapped = charMap[code]
                    if (mapped != null) {
                        sb.append(mapped)
                    } else if (code in 0x20..0x7E || code in 0xA0..0xFF) {
                        sb.append(code.toChar())
                    }
                    i += 1
                }
            }
            return sb.toString()
        }
    }

    private data class PdfFontInfo(
        val toUnicode: ToUnicodeCMap? = null,
        val encodingOverrides: Map<Int, Char> = emptyMap(),
        val baseEncoding: String = "WinAnsiEncoding",
        val isTwoByteCid: Boolean = false
    ) {
        fun decodeBytes(bytes: ByteArray, isHexString: Boolean): String {
            if (bytes.isEmpty()) return ""

            // 1. UTF-16BE BOM check
            if (bytes.size >= 2 && (bytes[0].toInt() and 0xFF) == 0xFE && (bytes[1].toInt() and 0xFF) == 0xFF) {
                return String(bytes, 2, bytes.size - 2, Charsets.UTF_16BE)
            }

            // 2. ToUnicode CMap takes highest priority (accurate for all CID/embedded fonts)
            if (toUnicode != null && toUnicode.charMap.isNotEmpty()) {
                return toUnicode.decode(bytes)
            }

            // 3. If Type0 / Identity-H hex string without ToUnicode, avoid emitting garbage
            if (isTwoByteCid && isHexString) {
                // Only decode if it clearly looks like UTF-16BE ASCII (high byte 0x00, low byte printable)
                if (bytes.size % 2 == 0) {
                    val sb = StringBuilder()
                    var validPairs = 0
                    val totalPairs = bytes.size / 2
                    for (i in bytes.indices step 2) {
                        val hi = bytes[i].toInt() and 0xFF
                        val lo = bytes[i + 1].toInt() and 0xFF
                        if (hi == 0 && (lo in 0x20..0x7E || lo in 0xA0..0xFF)) {
                            sb.append(lo.toChar())
                            validPairs++
                        } else if (hi in 0x01..0x06 || hi in 0x09..0x0D || hi in 0x4E..0x9F) {
                            val ch = ((hi shl 8) or lo).toChar()
                            sb.append(ch)
                            validPairs++
                        }
                    }
                    if (totalPairs > 0 && validPairs * 100 / totalPairs >= 70) {
                        return sb.toString()
                    }
                }
                return ""
            }

            // 4. Single-byte font encoding (WinAnsi / MacRoman / Differences)
            val sb = StringBuilder(bytes.size)
            for (b in bytes) {
                val code = b.toInt() and 0xFF
                val overrideChar = encodingOverrides[code]
                if (overrideChar != null) {
                    sb.append(overrideChar)
                } else {
                    val decoded = decodeSingleByte(code, baseEncoding)
                    if (decoded != null) {
                        sb.append(decoded)
                    }
                }
            }
            return sb.toString()
        }
    }

    /**
     * Extracts clean, structured text page-by-page from a PDF file.
     */
    fun extractPages(pdfFile: File): List<ExtractedPage> {
        if (!pdfFile.exists() || pdfFile.length() == 0L) return emptyList()
        return try {
            val bytes = pdfFile.readBytes()
            extractPagesFromBytes(bytes)
        } catch (e: Exception) {
            Log.e(TAG, "Failed extracting PDF pages", e)
            emptyList()
        }
    }

    fun extractPagesFromBytes(pdfBytes: ByteArray): List<ExtractedPage> {
        val objects = parseAllObjects(pdfBytes)
        if (objects.isEmpty()) return emptyList()

        val fontCache = HashMap<Int, PdfFontInfo>()
        val pageObjects = collectOrderedPageObjects(objects)

        val pages = mutableListOf<ExtractedPage>()
        pageObjects.forEachIndexed { idx, pageObj ->
            val inheritedResources = resolveResourcesForPage(pageObj, objects)
            val fonts = buildFontsMap(inheritedResources, objects, fontCache)
            val xObjects = buildXObjectRefMap(inheritedResources, objects)
            val contentBytes = collectPageContentBytes(pageObj, objects)

            val rawText = if (contentBytes.isNotEmpty()) {
                extractTextFromContentStream(
                    streamBytes = contentBytes,
                    fonts = fonts,
                    xObjects = xObjects,
                    objects = objects,
                    fontCache = fontCache,
                    depth = 0
                )
            } else {
                ""
            }

            val paragraphs = normalizeToParagraphs(rawText)
            pages.add(ExtractedPage(pageNumber = idx + 1, paragraphs = paragraphs))
        }

        return pages
    }

    // =========================================================================
    // 1. PDF OBJECT & STREAM PARSING
    // =========================================================================

    private fun parseAllObjects(pdfBytes: ByteArray): Map<Int, PdfObj> {
        val text = String(pdfBytes, Charsets.ISO_8859_1)
        val objMap = LinkedHashMap<Int, PdfObj>()

        val headerRegex = Regex("""(?:^|[\r\n])\s*(\d+)\s+\d+\s+obj\b""")
        val matches = headerRegex.findAll(text).toList()

        for (match in matches) {
            val objId = match.groupValues[1].toIntOrNull() ?: continue
            var pos = match.range.last + 1

            // Skip whitespace after "obj"
            while (pos < text.length && text[pos].isWhitespace()) pos++

            // Find matching "endobj", safely jumping over "stream ... endstream"
            val bodyStart = pos
            var streamDataStart = -1
            var streamDataEnd = -1
            var dictEndPos = -1

            var cursor = bodyStart
            var endObjPos = -1
            while (cursor < text.length) {
                val nextStream = text.indexOf("stream", cursor)
                val nextEndObj = text.indexOf("endobj", cursor)
                if (nextEndObj == -1) break

                if (nextStream != -1 && nextStream < nextEndObj && isKeywordBoundary(text, nextStream, 6)) {
                    // Check that this "stream" actually follows ">>" (not "endstream" or a string)
                    val isEndStream = nextStream >= 3 && text.substring(nextStream - 3, nextStream) == "end"
                    if (!isEndStream) {
                        var sPos = nextStream + 6
                        if (sPos < text.length && text[sPos] == '\r') sPos++
                        if (sPos < text.length && text[sPos] == '\n') sPos++
                        val ePos = text.indexOf("endstream", sPos)
                        if (ePos != -1) {
                            dictEndPos = nextStream
                            streamDataStart = sPos
                            streamDataEnd = ePos
                            val afterEndStream = ePos + 9
                            val finalEndObj = text.indexOf("endobj", afterEndStream)
                            endObjPos = if (finalEndObj != -1) finalEndObj else afterEndStream
                            break
                        }
                    }
                }
                if (isKeywordBoundary(text, nextEndObj, 6)) {
                    endObjPos = nextEndObj
                    break
                }
                cursor = nextEndObj + 6
            }

            if (endObjPos == -1) continue

            val rawBody = if (dictEndPos != -1) {
                text.substring(bodyStart, dictEndPos).trim()
            } else {
                text.substring(bodyStart, endObjPos).trim()
            }

            val dict = extractTopDictionary(rawBody)
            var decompressedStream: ByteArray? = null
            if (streamDataStart in 0 until streamDataEnd && streamDataEnd <= pdfBytes.size) {
                // Trim trailing CRLF right before "endstream" if needed (keep exact bytes for zlib safety, Inflater stops at stream end anyway)
                val rawSlice = pdfBytes.copyOfRange(streamDataStart, streamDataEnd)
                decompressedStream = decodeStreamBytes(rawSlice, dict)
            }

            objMap[objId] = PdfObj(
                id = objId,
                dict = dict,
                rawBody = rawBody,
                streamBytes = decompressedStream
            )
        }

        // Expand PDF 1.5+ /ObjStm compressed object streams
        val objStmList = objMap.values.filter { it.dict.contains("/ObjStm") && it.streamBytes != null }
        for (objStm in objStmList) {
            parseObjectStream(objStm, objMap)
        }

        return objMap
    }

    private fun isKeywordBoundary(text: String, index: Int, len: Int): Boolean {
        val beforeOk = index == 0 || text[index - 1].isWhitespace() || text[index - 1] in charArrayOf('>', ']', ')', '/')
        val afterIdx = index + len
        val afterOk = afterIdx >= text.length || text[afterIdx].isWhitespace() || text[afterIdx] in charArrayOf('<', '[', '(', '/')
        return beforeOk && afterOk
    }

    private fun parseObjectStream(objStm: PdfObj, targetMap: MutableMap<Int, PdfObj>) {
        val bytes = objStm.streamBytes ?: return
        val n = Regex("""/N\s+(\d+)""").find(objStm.dict)?.groupValues?.get(1)?.toIntOrNull() ?: return
        val first = Regex("""/First\s+(\d+)""").find(objStm.dict)?.groupValues?.get(1)?.toIntOrNull() ?: return
        if (first <= 0 || first >= bytes.size || n <= 0) return

        val headerStr = String(bytes, 0, first, Charsets.ISO_8859_1)
        val nums = Regex("""\d+""").findAll(headerStr).mapNotNull { it.value.toIntOrNull() }.toList()
        if (nums.size < n * 2) return

        val bodyStr = String(bytes, first, bytes.size - first, Charsets.ISO_8859_1)
        for (i in 0 until n) {
            val objId = nums[i * 2]
            val offset = nums[i * 2 + 1]
            val nextOffset = if (i + 1 < n) nums[(i + 1) * 2 + 1] else bodyStr.length
            if (offset in 0..nextOffset && nextOffset <= bodyStr.length) {
                val rawBody = bodyStr.substring(offset, nextOffset).trim()
                if (!targetMap.containsKey(objId)) {
                    targetMap[objId] = PdfObj(
                        id = objId,
                        dict = extractTopDictionary(rawBody),
                        rawBody = rawBody,
                        streamBytes = null
                    )
                }
            }
        }
    }

    private fun extractTopDictionary(raw: String): String {
        val start = raw.indexOf("<<")
        if (start == -1) return ""
        var depth = 0
        var i = start
        while (i < raw.length - 1) {
            if (raw[i] == '<' && raw[i + 1] == '<') {
                depth++
                i += 2
                continue
            } else if (raw[i] == '>' && raw[i + 1] == '>') {
                depth--
                i += 2
                if (depth == 0) {
                    return raw.substring(start, i)
                }
                continue
            }
            i++
        }
        return raw.substring(start)
    }

    private fun decodeStreamBytes(rawBytes: ByteArray, dict: String): ByteArray {
        val filters = extractFilters(dict)
        if (filters.isEmpty()) return rawBytes

        var data = rawBytes
        for (filter in filters) {
            data = when (filter) {
                "ASCIIHexDecode", "AHx" -> decodeAsciiHex(data)
                "ASCII85Decode", "A85" -> decodeAscii85(data)
                "FlateDecode", "Fl" -> {
                    val inflated = inflateBytes(data)
                    applyPredictorIfNeeded(inflated, dict)
                }
                "RunLengthDecode", "RL" -> decodeRunLength(data)
                else -> data
            }
        }
        return data
    }

    private fun extractFilters(dict: String): List<String> {
        val arrayMatch = Regex("""/Filter\s*\[(.*?)\]""", RegexOption.DOT_MATCHES_ALL).find(dict)
        if (arrayMatch != null) {
            return Regex("""/([A-Za-z0-9]+)""").findAll(arrayMatch.groupValues[1])
                .map { it.groupValues[1] }
                .toList()
        }
        val singleMatch = Regex("""/Filter\s*/([A-Za-z0-9]+)""").find(dict)
        return if (singleMatch != null) listOf(singleMatch.groupValues[1]) else emptyList()
    }

    private fun inflateBytes(input: ByteArray): ByteArray {
        // Trim leading/trailing newlines if present before zlib header
        var start = 0
        while (start < input.size && (input[start] == '\r'.code.toByte() || input[start] == '\n'.code.toByte())) {
            start++
        }
        val slice = if (start > 0) input.copyOfRange(start, input.size) else input

        fun tryInflate(nowrap: Boolean): ByteArray? {
            val inflater = Inflater(nowrap)
            return try {
                inflater.setInput(slice)
                val out = ByteArrayOutputStream(slice.size * 2)
                val buf = ByteArray(8192)
                while (!inflater.finished()) {
                    val count = inflater.inflate(buf)
                    if (count == 0) {
                        if (inflater.needsInput() || inflater.needsDictionary()) break
                    } else {
                        out.write(buf, 0, count)
                    }
                }
                val res = out.toByteArray()
                if (res.isNotEmpty()) res else null
            } catch (_: Exception) {
                null
            } finally {
                inflater.end()
            }
        }

        return tryInflate(false) ?: tryInflate(true) ?: input
    }

    private fun applyPredictorIfNeeded(data: ByteArray, dict: String): ByteArray {
        val predictor = Regex("""/Predictor\s+(\d+)""").find(dict)?.groupValues?.get(1)?.toIntOrNull() ?: return data
        if (predictor < 10) return data
        val columns = Regex("""/Columns\s+(\d+)""").find(dict)?.groupValues?.get(1)?.toIntOrNull() ?: 1
        val colors = Regex("""/Colors\s+(\d+)""").find(dict)?.groupValues?.get(1)?.toIntOrNull() ?: 1
        val bpc = Regex("""/BitsPerComponent\s+(\d+)""").find(dict)?.groupValues?.get(1)?.toIntOrNull() ?: 8

        val bytesPerPixel = ((colors * bpc) + 7) / 8
        val rowLength = ((columns * colors * bpc) + 7) / 8
        val stride = rowLength + 1
        if (stride <= 1 || data.size < stride) return data

        val out = ByteArrayOutputStream()
        var prevRow = ByteArray(rowLength)
        var offset = 0
        while (offset + stride <= data.size) {
            val filterType = data[offset].toInt() and 0xFF
            val curRow = ByteArray(rowLength)
            for (i in 0 until rowLength) {
                val raw = data[offset + 1 + i].toInt() and 0xFF
                val a = if (i >= bytesPerPixel) curRow[i - bytesPerPixel].toInt() and 0xFF else 0
                val b = prevRow[i].toInt() and 0xFF
                val c = if (i >= bytesPerPixel) prevRow[i - bytesPerPixel].toInt() and 0xFF else 0
                val recon = when (filterType) {
                    0 -> raw
                    1 -> (raw + a) and 0xFF
                    2 -> (raw + b) and 0xFF
                    3 -> (raw + ((a + b) / 2)) and 0xFF
                    4 -> {
                        val p = a + b - c
                        val pa = kotlin.math.abs(p - a)
                        val pb = kotlin.math.abs(p - b)
                        val pc = kotlin.math.abs(p - c)
                        val pr = if (pa <= pb && pa <= pc) a else if (pb <= pc) b else c
                        (raw + pr) and 0xFF
                    }
                    else -> raw
                }
                curRow[i] = recon.toByte()
            }
            out.write(curRow)
            prevRow = curRow
            offset += stride
        }
        return out.toByteArray()
    }

    private fun decodeAsciiHex(input: ByteArray): ByteArray {
        val out = ByteArrayOutputStream()
        var highNibble = -1
        for (b in input) {
            val ch = b.toInt().toChar()
            if (ch == '>') break
            val digit = Character.digit(ch, 16)
            if (digit >= 0) {
                if (highNibble == -1) {
                    highNibble = digit
                } else {
                    out.write((highNibble shl 4) or digit)
                    highNibble = -1
                }
            }
        }
        if (highNibble != -1) out.write(highNibble shl 4)
        return out.toByteArray()
    }

    private fun decodeAscii85(input: ByteArray): ByteArray {
        val out = ByteArrayOutputStream()
        val tuple = LongArray(5)
        var count = 0
        var i = 0
        while (i < input.size) {
            val ch = input[i].toInt().toChar()
            i++
            if (ch.isWhitespace()) continue
            if (ch == '~' && i < input.size && input[i].toInt().toChar() == '>') break
            if (ch == 'z' && count == 0) {
                out.write(0); out.write(0); out.write(0); out.write(0)
                continue
            }
            if (ch < '!' || ch > 'u') continue
            tuple[count++] = (ch.code - 33).toLong()
            if (count == 5) {
                var val32 = 0L
                for (k in 0 until 5) val32 = val32 * 85L + tuple[k]
                out.write(((val32 shr 24) and 0xFF).toInt())
                out.write(((val32 shr 16) and 0xFF).toInt())
                out.write(((val32 shr 8) and 0xFF).toInt())
                out.write((val32 and 0xFF).toInt())
                count = 0
            }
        }
        if (count > 1) {
            for (k in count until 5) tuple[k] = 84L
            var val32 = 0L
            for (k in 0 until 5) val32 = val32 * 85L + tuple[k]
            for (k in 0 until count - 1) {
                out.write(((val32 shr (24 - 8 * k)) and 0xFF).toInt())
            }
        }
        return out.toByteArray()
    }

    private fun decodeRunLength(input: ByteArray): ByteArray {
        val out = ByteArrayOutputStream()
        var i = 0
        while (i < input.size) {
            val len = input[i++].toInt() and 0xFF
            if (len == 128) break
            if (len < 128) {
                val count = minOf(len + 1, input.size - i)
                out.write(input, i, count)
                i += count
            } else if (i < input.size) {
                val count = 257 - len
                val b = input[i++]
                repeat(count) { out.write(b.toInt()) }
            }
        }
        return out.toByteArray()
    }

    // =========================================================================
    // 2. PAGE TREE & RESOURCE RESOLUTION
    // =========================================================================

    private fun collectOrderedPageObjects(objects: Map<Int, PdfObj>): List<PdfObj> {
        val catalog = objects.values.firstOrNull { Regex("""/Type\s*/Catalog\b""").containsMatchIn(it.dict) }
        val rootPagesRef = catalog?.let { extractSingleRef(it.dict, "Pages") }
        val rootPagesObj = rootPagesRef?.let { objects[it] }
            ?: objects.values.firstOrNull { Regex("""/Type\s*/Pages\b""").containsMatchIn(it.dict) }

        if (rootPagesObj != null) {
            val collected = mutableListOf<PdfObj>()
            val visited = HashSet<Int>()
            traversePagesTree(rootPagesObj, objects, collected, visited)
            if (collected.isNotEmpty()) return collected
        }

        // Fallback: all /Type /Page objects (not /Pages)
        return objects.values.filter {
            Regex("""/Type\s*/Page\b""").containsMatchIn(it.dict) &&
                !Regex("""/Type\s*/Pages\b""").containsMatchIn(it.dict)
        }
    }

    private fun traversePagesTree(
        node: PdfObj,
        objects: Map<Int, PdfObj>,
        out: MutableList<PdfObj>,
        visited: MutableSet<Int>
    ) {
        if (!visited.add(node.id)) return
        val kidsRefs = extractRefArray(node.dict, "Kids")
        if (kidsRefs.isNotEmpty()) {
            for (kidId in kidsRefs) {
                val kid = objects[kidId] ?: continue
                if (Regex("""/Type\s*/Pages\b""").containsMatchIn(kid.dict) || kid.dict.contains("/Kids")) {
                    traversePagesTree(kid, objects, out, visited)
                } else {
                    out.add(kid)
                }
            }
        } else if (Regex("""/Type\s*/Page\b""").containsMatchIn(node.dict)) {
            out.add(node)
        }
    }

    private fun resolveResourcesForPage(pageObj: PdfObj, objects: Map<Int, PdfObj>): List<String> {
        val dicts = mutableListOf<String>()
        var current: PdfObj? = pageObj
        val visited = HashSet<Int>()
        while (current != null && visited.add(current.id)) {
            val resInline = extractNamedSubDict(current.dict, "Resources")
            if (resInline.isNotEmpty()) dicts.add(resInline)
            val resRef = extractSingleRef(current.dict, "Resources")
            if (resRef != null) {
                objects[resRef]?.let { resObj ->
                    dicts.add(if (resObj.dict.isNotEmpty()) resObj.dict else resObj.rawBody)
                }
            }
            val parentId = extractSingleRef(current.dict, "Parent")
            current = parentId?.let { objects[it] }
        }
        return dicts
    }

    private fun collectPageContentBytes(pageObj: PdfObj, objects: Map<Int, PdfObj>): ByteArray {
        val out = ByteArrayOutputStream()
        val arrayMatch = Regex("""/Contents\s*\[(.*?)\]""", RegexOption.DOT_MATCHES_ALL).find(pageObj.dict)
        if (arrayMatch != null) {
            val refs = Regex("""(\d+)\s+\d+\s+R""").findAll(arrayMatch.groupValues[1])
                .mapNotNull { it.groupValues[1].toIntOrNull() }
            for (refId in refs) {
                objects[refId]?.streamBytes?.let { bytes ->
                    out.write(bytes)
                    out.write('\n'.code)
                }
            }
        } else {
            val singleRef = extractSingleRef(pageObj.dict, "Contents")
            if (singleRef != null) {
                objects[singleRef]?.streamBytes?.let { out.write(it) }
            }
        }
        return out.toByteArray()
    }

    // =========================================================================
    // 3. FONT & TOUNICODE CMAP PARSING
    // =========================================================================

    private fun buildFontsMap(
        resourceDicts: List<String>,
        objects: Map<Int, PdfObj>,
        fontCache: MutableMap<Int, PdfFontInfo>
    ): Map<String, PdfFontInfo> {
        val result = HashMap<String, PdfFontInfo>()
        // Reverse order so child Page /Font entries override Parent /Pages entries
        for (resDict in resourceDicts.asReversed()) {
            var fontBlock = extractNamedSubDict(resDict, "Font")
            if (fontBlock.isEmpty()) {
                val fontBlockRef = extractSingleRef(resDict, "Font")
                if (fontBlockRef != null) {
                    val obj = objects[fontBlockRef]
                    fontBlock = obj?.dict?.ifEmpty { obj.rawBody } ?: ""
                }
            }
            if (fontBlock.isEmpty()) continue

            val entryRegex = Regex("""/([A-Za-z0-9_+.\-]+)\s+(\d+)\s+\d+\s+R""")
            for (m in entryRegex.findAll(fontBlock)) {
                val fontName = m.groupValues[1]
                val fontObjId = m.groupValues[2].toIntOrNull() ?: continue
                val fontInfo = fontCache.getOrPut(fontObjId) {
                    parseFontObject(fontObjId, objects)
                }
                result[fontName] = fontInfo
            }
        }
        return result
    }

    private fun buildXObjectRefMap(
        resourceDicts: List<String>,
        objects: Map<Int, PdfObj>
    ): Map<String, Int> {
        val result = HashMap<String, Int>()
        for (resDict in resourceDicts.asReversed()) {
            var xObjBlock = extractNamedSubDict(resDict, "XObject")
            if (xObjBlock.isEmpty()) {
                val ref = extractSingleRef(resDict, "XObject")
                if (ref != null) {
                    val obj = objects[ref]
                    xObjBlock = obj?.dict?.ifEmpty { obj.rawBody } ?: ""
                }
            }
            if (xObjBlock.isEmpty()) continue
            val entryRegex = Regex("""/([A-Za-z0-9_+.\-]+)\s+(\d+)\s+\d+\s+R""")
            for (m in entryRegex.findAll(xObjBlock)) {
                val name = m.groupValues[1]
                val refId = m.groupValues[2].toIntOrNull() ?: continue
                result[name] = refId
            }
        }
        return result
    }

    private fun parseFontObject(fontObjId: Int, objects: Map<Int, PdfObj>): PdfFontInfo {
        val fontObj = objects[fontObjId] ?: return PdfFontInfo()
        val dict = fontObj.dict.ifEmpty { fontObj.rawBody }

        val isType0 = Regex("""/Subtype\s*/Type0\b""").containsMatchIn(dict)

        // Parse /ToUnicode CMap
        val toUnicodeRef = extractSingleRef(dict, "ToUnicode")
        val toUnicodeCMap = toUnicodeRef?.let { ref ->
            objects[ref]?.streamBytes?.let { parseToUnicodeCMap(it) }
        }

        // Parse /Encoding
        var baseEncoding = "WinAnsiEncoding"
        val overrides = HashMap<Int, Char>()

        val encNameMatch = Regex("""/Encoding\s*/([A-Za-z0-9_\-]+)""").find(dict)
        if (encNameMatch != null) {
            baseEncoding = encNameMatch.groupValues[1]
        } else {
            val encRef = extractSingleRef(dict, "Encoding")
            val encDict = if (encRef != null) {
                objects[encRef]?.let { it.dict.ifEmpty { it.rawBody } } ?: ""
            } else {
                extractNamedSubDict(dict, "Encoding")
            }
            if (encDict.isNotEmpty()) {
                Regex("""/BaseEncoding\s*/([A-Za-z0-9_\-]+)""").find(encDict)?.let {
                    baseEncoding = it.groupValues[1]
                }
                val diffMatch = Regex("""/Differences\s*\[(.*?)\]""", RegexOption.DOT_MATCHES_ALL).find(encDict)
                if (diffMatch != null) {
                    parseDifferencesArray(diffMatch.groupValues[1], overrides)
                }
            }
        }

        return PdfFontInfo(
            toUnicode = toUnicodeCMap,
            encodingOverrides = overrides,
            baseEncoding = baseEncoding,
            isTwoByteCid = isType0 || baseEncoding.contains("Identity", ignoreCase = true)
        )
    }

    private fun parseToUnicodeCMap(bytes: ByteArray): ToUnicodeCMap {
        val cmap = ToUnicodeCMap()
        val text = String(bytes, Charsets.ISO_8859_1)

        // Code space range determines 1-byte vs 2-byte codes
        val csrMatch = Regex("""begincodespacerange\s*(.*?)\s*endcodespacerange""", RegexOption.DOT_MATCHES_ALL).find(text)
        if (csrMatch != null) {
            val firstHex = Regex("""<([0-9A-Fa-f]+)>""").find(csrMatch.groupValues[1])?.groupValues?.get(1)
            if (firstHex != null && firstHex.length <= 2) {
                cmap.codeByteLength = 1
            } else {
                cmap.codeByteLength = 2
            }
        }

        // beginbfchar ... endbfchar
        val bfCharRegex = Regex("""beginbfchar\s*(.*?)\s*endbfchar""", RegexOption.DOT_MATCHES_ALL)
        for (block in bfCharRegex.findAll(text)) {
            val pairRegex = Regex("""<([0-9A-Fa-f]+)>\s*<([0-9A-Fa-f]+)>""")
            for (pair in pairRegex.findAll(block.groupValues[1])) {
                val src = pair.groupValues[1].toIntOrNull(16) ?: continue
                val dst = hexToUtf16String(pair.groupValues[2])
                if (dst.isNotEmpty()) cmap.charMap[src] = dst
            }
        }

        // beginbfrange ... endbfrange
        val bfRangeRegex = Regex("""beginbfrange\s*(.*?)\s*endbfrange""", RegexOption.DOT_MATCHES_ALL)
        for (block in bfRangeRegex.findAll(text)) {
            val body = block.groupValues[1]
            // Case A: <start> <end> [<dst1> <dst2> ...]
            val arrayRangeRegex = Regex("""<([0-9A-Fa-f]+)>\s*<([0-9A-Fa-f]+)>\s*\[(.*?)\]""", RegexOption.DOT_MATCHES_ALL)
            for (m in arrayRangeRegex.findAll(body)) {
                val start = m.groupValues[1].toIntOrNull(16) ?: continue
                val end = m.groupValues[2].toIntOrNull(16) ?: continue
                val targets = Regex("""<([0-9A-Fa-f]+)>""").findAll(m.groupValues[3]).map { it.groupValues[1] }.toList()
                var code = start
                var idx = 0
                while (code <= end && idx < targets.size) {
                    val dst = hexToUtf16String(targets[idx])
                    if (dst.isNotEmpty()) cmap.charMap[code] = dst
                    code++
                    idx++
                }
            }
            // Case B: <start> <end> <dstStart>
            val singleRangeRegex = Regex("""<([0-9A-Fa-f]+)>\s*<([0-9A-Fa-f]+)>\s*<([0-9A-Fa-f]+)>""")
            for (m in singleRangeRegex.findAll(body)) {
                val start = m.groupValues[1].toIntOrNull(16) ?: continue
                val end = m.groupValues[2].toIntOrNull(16) ?: continue
                if (end - start > 4096 || end < start) continue
                val dstHex = m.groupValues[3]
                val dstStart = dstHex.toIntOrNull(16) ?: continue
                for (offset in 0..(end - start)) {
                    val targetCode = dstStart + offset
                    val formatted = if (dstHex.length <= 4) {
                        targetCode.toChar().toString()
                    } else {
                        String(Character.toChars(targetCode))
                    }
                    cmap.charMap[start + offset] = formatted
                }
            }
        }

        return cmap
    }

    private fun hexToUtf16String(hexRaw: String): String {
        val hex = hexRaw.trim()
        if (hex.isEmpty()) return ""
        val padded = if (hex.length % 2 == 1) "0$hex" else hex
        val bytes = ByteArray(padded.length / 2)
        for (i in bytes.indices) {
            bytes[i] = padded.substring(i * 2, i * 2 + 2).toInt(16).toByte()
        }
        return if (bytes.size == 1) {
            (bytes[0].toInt() and 0xFF).toChar().toString()
        } else {
            String(bytes, Charsets.UTF_16BE)
        }
    }

    private fun parseDifferencesArray(diffStr: String, out: MutableMap<Int, Char>) {
        val tokenRegex = Regex("""(\d+)|/([A-Za-z0-9_.\-]+)""")
        var currentCode = 0
        for (m in tokenRegex.findAll(diffStr)) {
            if (m.groupValues[1].isNotEmpty()) {
                currentCode = m.groupValues[1].toIntOrNull() ?: currentCode
            } else if (m.groupValues[2].isNotEmpty()) {
                val glyphName = m.groupValues[2]
                glyphNameToChar(glyphName)?.let { out[currentCode] = it }
                currentCode++
            }
        }
    }

    // =========================================================================
    // 4. CONTENT STREAM TOKENIZER & TEXT INTERPRETER
    // =========================================================================

    private sealed class PdfToken {
        data class LiteralStr(val bytes: ByteArray) : PdfToken()
        data class HexStr(val bytes: ByteArray) : PdfToken()
        data class Name(val value: String) : PdfToken()
        data class Num(val value: Double) : PdfToken()
        data class Arr(val items: List<PdfToken>) : PdfToken()
        data class Op(val name: String) : PdfToken()
    }

    private fun extractTextFromContentStream(
        streamBytes: ByteArray,
        fonts: Map<String, PdfFontInfo>,
        xObjects: Map<String, Int>,
        objects: Map<Int, PdfObj>,
        fontCache: MutableMap<Int, PdfFontInfo>,
        depth: Int
    ): String {
        if (depth > 5) return ""
        val tokens = tokenizeContentStream(String(streamBytes, Charsets.ISO_8859_1))
        val sb = StringBuilder()

        var activeFont: PdfFontInfo? = null
        var fontSize = 11.0
        var lastTmX = 0.0
        var lastTmY = Double.NaN
        val operands = mutableListOf<PdfToken>()

        for (tok in tokens) {
            if (tok !is PdfToken.Op) {
                operands.add(tok)
                continue
            }

            when (tok.name) {
                "BT" -> {
                    // Begin text block
                }
                "ET" -> {
                    if (sb.isNotEmpty() && sb.last() != '\n') {
                        sb.append('\n')
                    }
                    lastTmY = Double.NaN
                }
                "Tf" -> {
                    if (operands.size >= 2) {
                        val nameTok = operands[operands.size - 2] as? PdfToken.Name
                        val sizeTok = operands[operands.size - 1] as? PdfToken.Num
                        if (nameTok != null) {
                            activeFont = fonts[nameTok.value]
                        }
                        if (sizeTok != null && sizeTok.value > 0.0) {
                            fontSize = sizeTok.value
                        }
                    }
                }
                "Td", "TD" -> {
                    if (operands.size >= 2) {
                        val tx = (operands[operands.size - 2] as? PdfToken.Num)?.value ?: 0.0
                        val ty = (operands[operands.size - 1] as? PdfToken.Num)?.value ?: 0.0
                        if (kotlin.math.abs(ty) > fontSize * 0.25) {
                            appendNewLine(sb)
                        } else if (tx > fontSize * 0.15) {
                            appendSpace(sb)
                        }
                    }
                }
                "Tm" -> {
                    if (operands.size >= 6) {
                        val tx = (operands[operands.size - 2] as? PdfToken.Num)?.value ?: 0.0
                        val ty = (operands[operands.size - 1] as? PdfToken.Num)?.value ?: 0.0
                        if (!lastTmY.isNaN() && kotlin.math.abs(ty - lastTmY) > fontSize * 0.35) {
                            appendNewLine(sb)
                        } else if (!lastTmY.isNaN() && tx - lastTmX > fontSize * 0.2) {
                            appendSpace(sb)
                        }
                        lastTmX = tx
                        lastTmY = ty
                    }
                }
                "T*" -> {
                    appendNewLine(sb)
                }
                "Tj" -> {
                    operands.lastOrNull()?.let { arg ->
                        val decoded = decodeStringToken(arg, activeFont)
                        sb.append(decoded)
                    }
                }
                "'", "\"" -> {
                    appendNewLine(sb)
                    operands.lastOrNull()?.let { arg ->
                        val decoded = decodeStringToken(arg, activeFont)
                        sb.append(decoded)
                    }
                }
                "TJ" -> {
                    val arr = operands.lastOrNull() as? PdfToken.Arr
                    if (arr != null) {
                        for (item in arr.items) {
                            when (item) {
                                is PdfToken.Num -> {
                                    // Negative kerning adjustment moves text right; large negative values indicate space
                                    if (item.value <= -120.0) {
                                        appendSpace(sb)
                                    }
                                }
                                is PdfToken.LiteralStr, is PdfToken.HexStr -> {
                                    sb.append(decodeStringToken(item, activeFont))
                                }
                                else -> {}
                            }
                        }
                    }
                }
                "Do" -> {
                    val xObjName = (operands.lastOrNull() as? PdfToken.Name)?.value
                    val xObjId = xObjName?.let { xObjects[it] }
                    val xObj = xObjId?.let { objects[it] }
                    if (xObj != null && xObj.streamBytes != null && Regex("""/Subtype\s*/Form\b""").containsMatchIn(xObj.dict)) {
                        val subRes = extractNamedSubDict(xObj.dict, "Resources")
                        val subResList = if (subRes.isNotEmpty()) listOf(subRes) else emptyList()
                        val subFonts = if (subResList.isNotEmpty()) buildFontsMap(subResList, objects, fontCache) else fonts
                        val subXObj = if (subResList.isNotEmpty()) buildXObjectRefMap(subResList, objects) else xObjects
                        val nested = extractTextFromContentStream(xObj.streamBytes, subFonts, subXObj, objects, fontCache, depth + 1)
                        if (nested.isNotBlank()) {
                            appendNewLine(sb)
                            sb.append(nested)
                        }
                    }
                }
            }
            operands.clear()
        }

        return sb.toString()
    }

    private fun decodeStringToken(token: PdfToken, font: PdfFontInfo?): String {
        val active = font ?: PdfFontInfo()
        return when (token) {
            is PdfToken.LiteralStr -> active.decodeBytes(token.bytes, isHexString = false)
            is PdfToken.HexStr -> active.decodeBytes(token.bytes, isHexString = true)
            else -> ""
        }
    }

    private fun appendNewLine(sb: StringBuilder) {
        if (sb.isNotEmpty() && sb.last() != '\n') {
            sb.append('\n')
        }
    }

    private fun appendSpace(sb: StringBuilder) {
        if (sb.isNotEmpty() && !sb.last().isWhitespace()) {
            sb.append(' ')
        }
    }

    private fun tokenizeContentStream(src: String): List<PdfToken> {
        val tokens = mutableListOf<PdfToken>()
        var i = 0
        val n = src.length

        while (i < n) {
            val ch = src[i]
            when {
                ch.isWhitespace() -> i++
                ch == '%' -> {
                    while (i < n && src[i] != '\n' && src[i] != '\r') i++
                }
                ch == '(' -> {
                    val (bytes, nextPos) = readLiteralString(src, i)
                    tokens.add(PdfToken.LiteralStr(bytes))
                    i = nextPos
                }
                ch == '<' -> {
                    if (i + 1 < n && src[i + 1] == '<') {
                        // Dictionary << ... >>
                        i = skipDictionary(src, i)
                    } else {
                        val (bytes, nextPos) = readHexString(src, i)
                        tokens.add(PdfToken.HexStr(bytes))
                        i = nextPos
                    }
                }
                ch == '[' -> {
                    val (arr, nextPos) = readArray(src, i)
                    tokens.add(arr)
                    i = nextPos
                }
                ch == '/' -> {
                    i++
                    val start = i
                    while (i < n && !src[i].isWhitespace() && src[i] !in charArrayOf('/', '<', '>', '[', ']', '(', ')', '%')) {
                        i++
                    }
                    tokens.add(PdfToken.Name(src.substring(start, i)))
                }
                ch == ']' || ch == '>' || ch == ')' -> {
                    i++
                }
                else -> {
                    val start = i
                    while (i < n && !src[i].isWhitespace() && src[i] !in charArrayOf('/', '<', '>', '[', ']', '(', ')', '%')) {
                        i++
                    }
                    val word = src.substring(start, i)
                    val num = word.toDoubleOrNull()
                    if (num != null) {
                        tokens.add(PdfToken.Num(num))
                    } else if (word.isNotEmpty()) {
                        tokens.add(PdfToken.Op(word))
                    }
                }
            }
        }
        return tokens
    }

    private fun readLiteralString(src: String, startIndex: Int): Pair<ByteArray, Int> {
        val out = ByteArrayOutputStream()
        var i = startIndex + 1
        var depth = 1
        val n = src.length
        while (i < n && depth > 0) {
            val ch = src[i]
            when (ch) {
                '\\' -> {
                    i++
                    if (i >= n) break
                    val esc = src[i]
                    when (esc) {
                        'n' -> { out.write('\n'.code); i++ }
                        'r' -> { out.write('\r'.code); i++ }
                        't' -> { out.write('\t'.code); i++ }
                        'b' -> { out.write('\b'.code); i++ }
                        'f' -> { out.write(0x0C); i++ }
                        '(', ')', '\\' -> { out.write(esc.code); i++ }
                        '\r' -> {
                            i++
                            if (i < n && src[i] == '\n') i++
                        }
                        '\n' -> i++
                        in '0'..'7' -> {
                            var oct = esc - '0'
                            i++
                            repeat(2) {
                                if (i < n && src[i] in '0'..'7') {
                                    oct = (oct * 8) + (src[i] - '0')
                                    i++
                                }
                            }
                            out.write(oct and 0xFF)
                        }
                        else -> { out.write(esc.code); i++ }
                    }
                }
                '(' -> {
                    depth++
                    out.write('('.code)
                    i++
                }
                ')' -> {
                    depth--
                    if (depth > 0) out.write(')'.code)
                    i++
                }
                else -> {
                    out.write(ch.code and 0xFF)
                    i++
                }
            }
        }
        return out.toByteArray() to i
    }

    private fun readHexString(src: String, startIndex: Int): Pair<ByteArray, Int> {
        val out = ByteArrayOutputStream()
        var i = startIndex + 1
        val n = src.length
        var high = -1
        while (i < n) {
            val ch = src[i++]
            if (ch == '>') break
            val d = Character.digit(ch, 16)
            if (d >= 0) {
                if (high == -1) {
                    high = d
                } else {
                    out.write((high shl 4) or d)
                    high = -1
                }
            }
        }
        if (high != -1) out.write(high shl 4)
        return out.toByteArray() to i
    }

    private fun readArray(src: String, startIndex: Int): Pair<PdfToken.Arr, Int> {
        val items = mutableListOf<PdfToken>()
        var i = startIndex + 1
        val n = src.length
        while (i < n) {
            val ch = src[i]
            when {
                ch.isWhitespace() -> i++
                ch == ']' -> {
                    i++
                    break
                }
                ch == '(' -> {
                    val (b, next) = readLiteralString(src, i)
                    items.add(PdfToken.LiteralStr(b))
                    i = next
                }
                ch == '<' -> {
                    if (i + 1 < n && src[i + 1] == '<') {
                        i = skipDictionary(src, i)
                    } else {
                        val (b, next) = readHexString(src, i)
                        items.add(PdfToken.HexStr(b))
                        i = next
                    }
                }
                ch == '[' -> {
                    val (sub, next) = readArray(src, i)
                    items.add(sub)
                    i = next
                }
                ch == '/' -> {
                    i++
                    val s = i
                    while (i < n && !src[i].isWhitespace() && src[i] !in charArrayOf('/', '<', '>', '[', ']', '(', ')', '%')) i++
                    items.add(PdfToken.Name(src.substring(s, i)))
                }
                else -> {
                    val s = i
                    while (i < n && !src[i].isWhitespace() && src[i] !in charArrayOf('/', '<', '>', '[', ']', '(', ')', '%')) i++
                    val word = src.substring(s, i)
                    word.toDoubleOrNull()?.let { items.add(PdfToken.Num(it)) }
                }
            }
        }
        return PdfToken.Arr(items) to i
    }

    private fun skipDictionary(src: String, startIndex: Int): Int {
        var i = startIndex
        var depth = 0
        while (i < src.length - 1) {
            if (src[i] == '<' && src[i + 1] == '<') {
                depth++; i += 2; continue
            }
            if (src[i] == '>' && src[i + 1] == '>') {
                depth--; i += 2
                if (depth <= 0) return i
                continue
            }
            i++
        }
        return src.length
    }

    // =========================================================================
    // 5. TEXT NORMALIZATION & SANITATION
    // =========================================================================

    private fun normalizeToParagraphs(rawText: String): List<String> {
        if (rawText.isBlank()) return emptyList()

        val cleanedLines = mutableListOf<String>()
        val rawLines = rawText.replace("\r\n", "\n").replace('\r', '\n').split('\n')
        val paragraphBuilder = StringBuilder()

        for (rawLine in rawLines) {
            val sanitized = sanitizeExtractedLine(rawLine)
            if (sanitized.isBlank()) {
                if (paragraphBuilder.isNotEmpty()) {
                    cleanedLines.add(paragraphBuilder.toString().trim())
                    paragraphBuilder.clear()
                }
                continue
            }

            // Start new paragraph if bullet, heading, or previous line ended a sentence
            val startsAsBullet = sanitized.startsWith("•") || sanitized.startsWith("-") ||
                sanitized.startsWith("*") || Regex("""^\d+[.)]\s""").containsMatchIn(sanitized)
            val prevEndsSentence = paragraphBuilder.isNotEmpty() &&
                paragraphBuilder.last() in charArrayOf('.', '!', '?', ':')

            if (startsAsBullet || prevEndsSentence || paragraphBuilder.length > 350) {
                if (paragraphBuilder.isNotEmpty()) {
                    cleanedLines.add(paragraphBuilder.toString().trim())
                    paragraphBuilder.clear()
                }
            }

            if (paragraphBuilder.isNotEmpty()) {
                if (paragraphBuilder.endsWith("-")) {
                    paragraphBuilder.deleteCharAt(paragraphBuilder.length - 1)
                } else {
                    paragraphBuilder.append(' ')
                }
            }
            paragraphBuilder.append(sanitized)
        }

        if (paragraphBuilder.isNotEmpty()) {
            cleanedLines.add(paragraphBuilder.toString().trim())
        }

        // Filter out lines that are pure non-readable control/noise artifacts
        return cleanedLines.filter { line ->
            line.isNotBlank() && line.any { it.isLetterOrDigit() }
        }
    }

    fun sanitizeExtractedLine(input: String): String {
        val sb = StringBuilder(input.length)
        var lastWasSpace = false
        for (ch in input) {
            when {
                ch == '\t' || ch == ' ' || ch == '\u00A0' -> {
                    if (!lastWasSpace && sb.isNotEmpty()) {
                        sb.append(' ')
                        lastWasSpace = true
                    }
                }
                ch.code < 0x20 || ch == '\uFFFD' || ch == '\u007F' -> {
                    // Skip control characters & replacement glyphs
                }
                Character.isISOControl(ch) -> {
                    // Skip
                }
                else -> {
                    sb.append(ch)
                    lastWasSpace = false
                }
            }
        }
        return sb.toString().trim()
    }

    // =========================================================================
    // 6. DICTIONARY & GLYPH HELPERS
    // =========================================================================

    private fun extractSingleRef(dict: String, key: String): Int? {
        val m = Regex("""/$key\s+(\d+)\s+\d+\s+R\b""").find(dict) ?: return null
        return m.groupValues[1].toIntOrNull()
    }

    private fun extractRefArray(dict: String, key: String): List<Int> {
        val arrMatch = Regex("""/$key\s*\[(.*?)\]""", RegexOption.DOT_MATCHES_ALL).find(dict) ?: return emptyList()
        return Regex("""(\d+)\s+\d+\s+R""").findAll(arrMatch.groupValues[1])
            .mapNotNull { it.groupValues[1].toIntOrNull() }
            .toList()
    }

    private fun extractNamedSubDict(dict: String, key: String): String {
        val keyPattern = Regex("""/$key\s*<<""")
        val m = keyPattern.find(dict) ?: return ""
        val start = m.range.last - 1
        var depth = 0
        var i = start
        while (i < dict.length - 1) {
            if (dict[i] == '<' && dict[i + 1] == '<') {
                depth++; i += 2; continue
            }
            if (dict[i] == '>' && dict[i + 1] == '>') {
                depth--; i += 2
                if (depth == 0) return dict.substring(start, i)
                continue
            }
            i++
        }
        return ""
    }

    private fun decodeSingleByte(code: Int, baseEncoding: String): Char? {
        if (code in 0x20..0x7E) return code.toChar()
        if (baseEncoding == "MacRomanEncoding") {
            return macRomanExtra[code] ?: if (code in 0xA0..0xFF) code.toChar() else null
        }
        // Default: WinAnsiEncoding (Windows-1252)
        if (code in 0x80..0x9F) {
            return winAnsi128To159[code]
        }
        if (code in 0xA0..0xFF) return code.toChar()
        return null
    }

    private val winAnsi128To159 = mapOf(
        0x80 to '€', 0x82 to '‚', 0x83 to 'ƒ', 0x84 to '„', 0x85 to '…',
        0x86 to '†', 0x87 to '‡', 0x88 to 'ˆ', 0x89 to '‰', 0x8A to 'Š',
        0x8B to '‹', 0x8C to 'Œ', 0x8E to 'Ž', 0x91 to '‘', 0x92 to '’',
        0x93 to '“', 0x94 to '”', 0x95 to '•', 0x96 to '–', 0x97 to '—',
        0x98 to '˜', 0x99 to '™', 0x9A to 'š', 0x9B to '›', 0x9C to 'œ',
        0x9E to 'ž', 0x9F to 'Ÿ'
    )

    private val macRomanExtra = mapOf(
        0x80 to 'Ä', 0x81 to 'Å', 0x82 to 'Ç', 0x83 to 'É', 0x84 to 'Ñ',
        0x85 to 'Ö', 0x86 to 'Ü', 0x87 to 'á', 0x88 to 'à', 0x89 to 'â',
        0x8A to 'ä', 0x8B to 'ã', 0x8C to 'å', 0x8D to 'ç', 0x8E to 'é',
        0x8F to 'è', 0x90 to 'ê', 0x91 to 'ë', 0x92 to 'í', 0x93 to 'ì',
        0x94 to 'î', 0x95 to 'ï', 0x96 to 'ñ', 0x97 to 'ó', 0x98 to 'ò',
        0x99 to 'ô', 0x9A to 'ö', 0x9B to 'õ', 0x9C to 'ú', 0x9D to 'ù',
        0x9E to 'û', 0x9F to 'ü', 0xA0 to '†', 0xA1 to '°', 0xA2 to '¢',
        0xA3 to '£', 0xA4 to '§', 0xA5 to '•', 0xA6 to '¶', 0xA7 to 'ß',
        0xA8 to '®', 0xA9 to '©', 0xAA to '™', 0xD0 to '–', 0xD1 to '—',
        0xD2 to '“', 0xD3 to '”', 0xD4 to '‘', 0xD5 to '’', 0xD6 to '÷'
    )

    private fun glyphNameToChar(name: String): Char? {
        if (name.length == 1) return name[0]
        return when (name) {
            "space" -> ' '
            "exclam" -> '!'
            "quotedbl" -> '"'
            "numbersign" -> '#'
            "dollar" -> '$'
            "percent" -> '%'
            "ampersand" -> '&'
            "quotesingle", "quoteright" -> '’'
            "quoteleft" -> '‘'
            "quotedblleft" -> '“'
            "quotedblright" -> '”'
            "parenleft" -> '('
            "parenright" -> ')'
            "asterisk" -> '*'
            "plus" -> '+'
            "comma" -> ','
            "hyphen", "minus" -> '-'
            "endash" -> '–'
            "emdash" -> '—'
            "period" -> '.'
            "slash" -> '/'
            "zero" -> '0'; "one" -> '1'; "two" -> '2'; "three" -> '3'; "four" -> '4'
            "five" -> '5'; "six" -> '6'; "seven" -> '7'; "eight" -> '8'; "nine" -> '9'
            "colon" -> ':'
            "semicolon" -> ';'
            "less" -> '<'
            "equal" -> '='
            "greater" -> '>'
            "question" -> '?'
            "at" -> '@'
            "bracketleft" -> '['
            "backslash" -> '\\'
            "bracketright" -> ']'
            "underscore" -> '_'
            "bullet" -> '•'
            "ellipsis" -> '…'
            "Euro" -> '€'
            "trademark" -> '™'
            "copyright" -> '©'
            "registered" -> '®'
            else -> null
        }
    }
}

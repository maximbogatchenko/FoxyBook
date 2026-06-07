package com.foxybook.app.core.reader

import android.content.Context
import android.util.Log
import com.foxybook.app.core.models.MobiBook
import com.foxybook.app.core.models.MobiChapter
import com.foxybook.app.core.utils.BookImageCache
import java.io.File
import java.io.RandomAccessFile
import java.nio.charset.Charset

class MobiParser(private val context: Context? = null) {

    companion object {
        private const val TAG = "BOOK_IMAGES"
    }

    private val cp1252: Charset = Charset.forName("windows-1252")

    fun parse(file: File, bookId: Int = 0): MobiBook? {
        return try {
            val raf = RandomAccessFile(file, "r")
            val result = parseMobi(raf, bookId)
            raf.close()
            result
        } catch (e: Exception) {
            Log.e(TAG, "MOBI: Parse error for book $bookId", e)
            null
        }
    }

    private fun parseMobi(raf: RandomAccessFile, bookId: Int): MobiBook? {
        val name = readString(raf, 0, 32).trim { it <= '\u0000' }

        raf.seek(76)
        val numRecords = raf.readShort().toInt() and 0xFFFF
        if (numRecords < 1) return null

        val recordOffsets = IntArray(numRecords)
        for (i in 0 until numRecords) {
            raf.seek(78 + i * 8L)
            recordOffsets[i] = raf.readInt()
        }

        val firstRecordOffset = recordOffsets[0]
        raf.seek(firstRecordOffset.toLong())

        val compression = raf.readShort().toInt() and 0xFFFF
        raf.skipBytes(6) // unused + textLength high
        val textLength = raf.readInt()
        val recordCount = raf.readShort().toInt() and 0xFFFF
        val recordSize = raf.readShort().toInt() and 0xFFFF

        // Check for MOBI header
        raf.seek(firstRecordOffset.toLong() + 16)
        val mobiMagic = readString(raf, firstRecordOffset + 16, 4)

        var title = name
        var author = "Unknown"
        var firstImageIndex = -1

        if (mobiMagic == "MOBI") {
            try {
                // Get first image index
                raf.seek(firstRecordOffset.toLong() + 36)
                firstImageIndex = raf.readInt()

                // Full name
                raf.seek(firstRecordOffset.toLong() + 84)
                val fullNameOffset = raf.readInt()
                val fullNameLength = raf.readInt()

                if (fullNameOffset > 0 && fullNameLength > 0 && fullNameLength < 10000) {
                    raf.seek(firstRecordOffset.toLong() + fullNameOffset)
                    val nameBytes = ByteArray(fullNameLength)
                    raf.read(nameBytes)
                    title = String(nameBytes, Charsets.UTF_8).trim()
                }
            } catch (_: Exception) {}
        }

        // ─── Extract text records ───
        val textBuilder = StringBuilder()
        val startRecord = 1
        val endRecord = minOf(startRecord + recordCount, numRecords)

        for (i in startRecord until endRecord) {
            if (i >= recordOffsets.size) break
            val offset = recordOffsets[i]
            val nextOffset = if (i + 1 < recordOffsets.size) recordOffsets[i + 1] else raf.length().toInt()
            val size = nextOffset - offset

            if (size <= 0 || size > 2_000_000) continue

            try {
                raf.seek(offset.toLong())
                val bytes = ByteArray(size)
                raf.read(bytes)

                val text = when (compression) {
                    1 -> String(bytes, cp1252)
                    2 -> decompressPalmDoc(bytes)
                    else -> String(bytes, cp1252)
                }
                textBuilder.append(text)
            } catch (_: Exception) { continue }
        }

        // ─── Extract image records ───
        val imageMap = mutableMapOf<String, String>() // recindex -> file path
        var imagesFound = 0
        var imagesSaved = 0

        // Image records start after text records
        val imageStartIndex = if (firstImageIndex > 0) firstImageIndex else endRecord
        for (i in imageStartIndex until numRecords) {
            if (i >= recordOffsets.size) break
            val offset = recordOffsets[i]
            val nextOffset = if (i + 1 < recordOffsets.size) recordOffsets[i + 1] else raf.length().toInt()
            val size = nextOffset - offset

            if (size <= 0 || size > 5_000_000) continue

            try {
                raf.seek(offset.toLong())
                val header = ByteArray(minOf(8, size))
                raf.read(header)

                val imageType = detectImageType(header)
                if (imageType != null) {
                    imagesFound++
                    raf.seek(offset.toLong())
                    val fullBytes = ByteArray(size)
                    raf.read(fullBytes)

                    val recindex = (i - imageStartIndex + 1).toString()
                    val fileName = "image_$recindex.$imageType"

                    if (context != null && bookId > 0) {
                        val cachedFile = BookImageCache.saveImage(context, bookId, fileName, fullBytes)
                        val fileUrl = "file://${cachedFile.absolutePath}"
                        imageMap[recindex] = fileUrl
                        // Also map by various patterns used in MOBI HTML
                        imageMap["img$recindex"] = fileUrl
                        imagesSaved++
                    } else {
                        val mime = BookImageCache.getMimeType(fileName)
                        val b64 = android.util.Base64.encodeToString(fullBytes, android.util.Base64.NO_WRAP)
                        imageMap[recindex] = "data:$mime;base64,$b64"
                        imageMap["img$recindex"] = "data:$mime;base64,$b64"
                        imagesSaved++
                    }
                }
            } catch (_: Exception) { continue }
        }

        Log.d(TAG, "MOBI: Found $imagesFound images, saved $imagesSaved for book $bookId")

        // ─── Convert to chapters ───
        val rawText = textBuilder.toString()
        val chapters = convertToChapters(rawText, imageMap)

        if (chapters.isEmpty()) {
            Log.w(TAG, "MOBI: No chapters found for book $bookId")
            return null
        }

        Log.d(TAG, "MOBI: Parsed ${chapters.size} chapters for book $bookId")
        return MobiBook(title = title.ifBlank { "Unknown" }, author = author, chapters = chapters)
    }

    private fun detectImageType(header: ByteArray): String? {
        if (header.size < 4) return null
        // JPEG: FF D8 FF
        if (header[0] == 0xFF.toByte() && header[1] == 0xD8.toByte() && header[2] == 0xFF.toByte()) return "jpg"
        // PNG: 89 50 4E 47
        if (header[0] == 0x89.toByte() && header[1] == 0x50.toByte() && header[2] == 0x4E.toByte() && header[3] == 0x47.toByte()) return "png"
        // GIF: 47 49 46 38
        if (header[0] == 0x47.toByte() && header[1] == 0x49.toByte() && header[2] == 0x46.toByte() && header[3] == 0x38.toByte()) return "gif"
        // WEBP: RIFF....WEBP
        if (header.size >= 8 && header[0] == 0x52.toByte() && header[1] == 0x49.toByte() &&
            header[2] == 0x46.toByte() && header[3] == 0x46.toByte() &&
            header[8] == 0x57.toByte() && header[9] == 0x45.toByte() &&
            header[10] == 0x42.toByte() && header[11] == 0x50.toByte()) return "webp"
        // BMP: 42 4D
        if (header[0] == 0x42.toByte() && header[1] == 0x4D.toByte()) return "bmp"
        return null
    }

    private fun convertToChapters(rawText: String, imageMap: Map<String, String>): List<MobiChapter> {
        var html = rawText
            .replace("<mbp:pagebreak/>", "<div class=\"page-break\"></div>")
            .replace("<mbp:pagebreak>", "<div class=\"page-break\"></div>")

        // ─── Replace MOBI image references ───
        // Pattern 1: <img recindex="1"/> or <img recindex="1">
        html = Regex("""<img\s+[^>]*recindex\s*=\s*["']?(\d+)["']?[^>]*/?>""", RegexOption.IGNORE_CASE)
            .replace(html) { match ->
                val recindex = match.groupValues[1]
                val src = imageMap[recindex] ?: imageMap["img$recindex"] ?: ""
                if (src.isNotBlank()) {
                    "<img src=\"$src\" alt=\"\"/>"
                } else {
                    Log.w(TAG, "MOBI: No image found for recindex=$recindex")
                    match.value
                }
            }

        // Pattern 2: <img src="kindle:embed:img1?mime=image/jpg"/>
        html = Regex("""kindle:embed:img(\d+)""", RegexOption.IGNORE_CASE)
            .replace(html) { match ->
                val recindex = match.groupValues[1]
                imageMap[recindex] ?: imageMap["img$recindex"] ?: match.value
            }

        // Pattern 3: <img src="mobi-img-001"/>
        html = Regex("""mobi-img-(\d+)""", RegexOption.IGNORE_CASE)
            .replace(html) { match ->
                val recindex = match.groupValues[1]
                imageMap[recindex] ?: imageMap["img$recindex"] ?: match.value
            }

        // Split by headings
        val headingPattern = Regex("(<h[1-3][^>]*>.*?</h[1-3]>)", RegexOption.IGNORE_CASE)
        val splits = headingPattern.findAll(html).map { it.range.first }.toList()

        if (splits.size < 2) {
            val cleanHtml = cleanHtml(html)
            if (cleanHtml.isNotBlank()) {
                return listOf(MobiChapter(title = "Книга", htmlContent = cleanHtml))
            }
            return emptyList()
        }

        val chapters = mutableListOf<MobiChapter>()

        if (splits[0] > 0) {
            val pre = html.substring(0, splits[0])
            val cleanPre = cleanHtml(pre)
            if (cleanPre.isNotBlank()) {
                chapters.add(MobiChapter(title = "Введение", htmlContent = cleanPre))
            }
        }

        for (i in splits.indices) {
            val start = splits[i]
            val end = if (i + 1 < splits.size) splits[i + 1] else html.length
            val chunk = html.substring(start, end)

            val titleMatch = Regex("<h[1-3][^>]*>(.*?)</h[1-3]>", RegexOption.IGNORE_CASE).find(chunk)
            val chapterTitle = titleMatch?.groupValues?.get(1)?.trim()?.ifBlank { "Глава ${i + 1}" } ?: "Глава ${i + 1}"

            val cleanChunk = cleanHtml(chunk)
            if (cleanChunk.isNotBlank()) {
                chapters.add(MobiChapter(title = chapterTitle, htmlContent = cleanChunk))
            }
        }

        return if (chapters.isEmpty()) {
            val cleanAll = cleanHtml(html)
            if (cleanAll.isNotBlank()) listOf(MobiChapter(title = "Книга", htmlContent = cleanAll))
            else emptyList()
        } else chapters
    }

    private fun cleanHtml(html: String): String {
        return html
            .replace(Regex("<script[^>]*>.*?</script>", setOf(RegexOption.IGNORE_CASE, RegexOption.DOT_MATCHES_ALL)), "")
            .replace(Regex("<style[^>]*>.*?</style>", setOf(RegexOption.IGNORE_CASE, RegexOption.DOT_MATCHES_ALL)), "")
            .replace(Regex("<mbp:.*?>"), "")
            .replace(Regex("<guide.*?</guide>", setOf(RegexOption.IGNORE_CASE, RegexOption.DOT_MATCHES_ALL)), "")
            .trim()
    }

    private fun decompressPalmDoc(data: ByteArray): String {
        val output = mutableListOf<Byte>()
        var i = 0
        while (i < data.size) {
            val byte = data[i].toInt() and 0xFF
            when {
                byte == 0 -> { output.add(0); i++ }
                byte in 1..0x7F -> { output.add(data[i]); i++ }
                byte in 0x80..0xBF -> {
                    if (i + 1 >= data.size) break
                    val next = data[i + 1].toInt() and 0xFF
                    val distance = ((byte shl 8) or next) ushr 2 and 0x3FF
                    val length = (next and 0x03) + 3
                    val start = output.size - distance
                    if (start >= 0) {
                        for (j in 0 until length) {
                            if (start + j < output.size) output.add(output[start + j])
                        }
                    }
                    i += 2
                }
                byte in 0xC0..0xFF -> {
                    val length = (byte and 0x3F) + 1
                    for (j in 0 until length) {
                        if (i + 1 + j < data.size) {
                            val c = data[i + 1 + j].toInt() and 0xFF
                            output.add(if (c == 0) ' '.code.toByte() else c.toByte())
                        }
                    }
                    i += 1 + length
                }
            }
        }
        return String(output.toByteArray(), cp1252)
    }

    private fun readString(raf: RandomAccessFile, offset: Int, length: Int): String {
        raf.seek(offset.toLong())
        val bytes = ByteArray(length)
        raf.read(bytes)
        return String(bytes, cp1252)
    }
}

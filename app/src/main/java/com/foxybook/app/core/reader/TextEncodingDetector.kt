package com.foxybook.app.core.reader

import java.nio.ByteBuffer
import java.nio.charset.CharacterCodingException
import java.nio.charset.Charset
import java.nio.charset.CodingErrorAction

/**
 * Определение кодировки текстовых книг.
 *
 * Файлы FB2 и TXT у русских источников чаще всего приходят в windows-1251,
 * поэтому простое чтение как UTF-8 даёт «кракозябры».
 */
object TextEncodingDetector {

    /** Читает XML-декларацию `<?xml ... encoding="..."?>` из начала файла. */
    fun declaredEncoding(bytes: ByteArray): String? {
        val head = bytes.copyOfRange(0, minOf(bytes.size, 512))
        val latin = String(head, Charsets.ISO_8859_1)
        return XML_ENCODING_REGEX.find(latin)
            ?.groupValues?.get(1)
            ?.lowercase()
            ?.trim()
    }

/** Декодирует XML-книгу: BOM → декларация → валидный UTF-8 → windows-1251. */
    fun decodeXml(bytes: ByteArray): String =
        decode(bytes, charsetFromName(declaredEncoding(bytes)))

    /** Декодирует обычный текст: BOM → валидный UTF-8 → windows-1251. */
    fun decodePlainText(bytes: ByteArray): String = decode(bytes, null)

    private fun decode(bytes: ByteArray, declared: Charset?): String {
        if (bytes.isEmpty()) return ""

        bomCharset(bytes)?.let { return decodeStrict(bytes, it, skipBom = true) }

        if (declared != null) return decodeStrict(bytes, declared, skipBom = false)

        // Строгий UTF-8: если байты невалидны, отбрасываем этот вариант.
        decodeStrictOrNull(bytes, Charsets.UTF_8)?.let { return it }

        return decodeStrict(bytes, Charset.forName("windows-1251"), skipBom = false)
    }

    private fun bomCharset(bytes: ByteArray): Charset? {
        if (bytes.size >= 3 &&
            bytes[0] == BOM_UTF8_0 && bytes[1] == BOM_UTF8_1 && bytes[2] == BOM_UTF8_2
        ) {
            return Charsets.UTF_8
        }
        if (bytes.size >= 2 && bytes[0] == 0xFF.toByte() && bytes[1] == 0xFE.toByte()) {
            return Charsets.UTF_16LE
        }
        if (bytes.size >= 2 && bytes[0] == 0xFE.toByte() && bytes[1] == 0xFF.toByte()) {
            return Charsets.UTF_16BE
        }
        return null
    }

    private fun decodeStrict(bytes: ByteArray, charset: Charset, skipBom: Boolean): String {
        val offset = if (skipBom && charset == Charsets.UTF_8) 3 else 0
        val payload = if (offset > 0) bytes.copyOfRange(offset, bytes.size) else bytes
        return decodeStrictOrNull(payload, charset) ?: String(payload, charset)
    }

    private fun decodeStrictOrNull(bytes: ByteArray, charset: Charset): String? = try {
        charset.newDecoder()
            .onMalformedInput(CodingErrorAction.REPORT)
            .onUnmappableCharacter(CodingErrorAction.REPORT)
            .decode(ByteBuffer.wrap(bytes))
            .toString()
    } catch (_: CharacterCodingException) {
        null
    } catch (_: Exception) {
        null
    }

    private fun charsetFromName(name: String?): Charset? {
        val normalized = name?.replace("_", "-")?.trim()?.lowercase() ?: return null
        return when (normalized) {
            "utf-8", "utf8" -> Charsets.UTF_8
            "us-ascii", "ascii" -> Charsets.US_ASCII
            "windows-1251", "cp1251", "win-1251", "1251" -> charsetOrNull("windows-1251")
            "koi8-r" -> charsetOrNull("koi8-r")
            "koi8-u" -> charsetOrNull("koi8-u")
            "iso-8859-5", "iso8859-5" -> charsetOrNull("iso-8859-5")
            "utf-16", "utf-16le", "utf-16be" -> charsetOrNull(normalized)
            else -> null
        }
    }

    private fun charsetOrNull(name: String): Charset? = try {
        if (Charset.isSupported(name)) Charset.forName(name) else null
    } catch (_: Exception) {
        null
    }

    private const val BOM_UTF8_0: Byte = 0xEF.toByte()
    private const val BOM_UTF8_1: Byte = 0xBB.toByte()
    private const val BOM_UTF8_2: Byte = 0xBF.toByte()

    private val XML_ENCODING_REGEX =
        Regex("""encoding\s*=\s*["']([^"']+)["']""", RegexOption.IGNORE_CASE)
}
package com.foxybook.app.core.reader

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import java.nio.charset.Charset

class TextEncodingDetectorTest {

    private val cyrillicUtf8 = "Привет"
    private val cyrillic1251 = "Привет"

    @Test
    fun `plain text utf8 stays utf8`() {
        val bytes = cyrillicUtf8.toByteArray(Charsets.UTF_8)
        assertEquals(cyrillicUtf8, TextEncodingDetector.decodePlainText(bytes))
    }

    @Test
    fun `plain text in windows-1251 is decoded correctly`() {
        val bytes = cyrillic1251.toByteArray(Charset.forName("windows-1251"))
        // Байты невалидны как UTF-8, поэтому ожидаем fallback на windows-1251
        assertEquals(cyrillic1251, TextEncodingDetector.decodePlainText(bytes))
    }

    @Test
    fun `utf8 BOM is skipped`() {
        val bom = byteArrayOf(0xEF.toByte(), 0xBB.toByte(), 0xBF.toByte())
        val bytes = bom + cyrillicUtf8.toByteArray(Charsets.UTF_8)
        assertEquals(cyrillicUtf8, TextEncodingDetector.decodePlainText(bytes))
    }

    @Test
    fun `xml declaration is honoured`() {
        assertEquals("windows-1251", TextEncodingDetector.declaredEncoding(
            """<?xml version="1.0" encoding="windows-1251"?>""".toByteArray(Charsets.ISO_8859_1)
        ))
        assertEquals("utf-8", TextEncodingDetector.declaredEncoding(
            """<?xml version="1.0" encoding="UTF-8"?>""".toByteArray(Charsets.ISO_8859_1)
        ))
        assertEquals("koi8-r", TextEncodingDetector.declaredEncoding(
            "<?xml version=\"1.0\" encoding=\"KOI8-R\"?>".toByteArray(Charsets.ISO_8859_1)
        ))
    }

    @Test
    fun `xml without declaration returns null`() {
        assertNull(TextEncodingDetector.declaredEncoding("<html><body>text</body></html>".toByteArray()))
    }

    @Test
    fun `xml in declared windows-1251 is decoded correctly`() {
        val header = """<?xml version="1.0" encoding="windows-1251"?><FictionBook><body>"""
        val bytes = (header + cyrillic1251 + "</body></FictionBook>")
            .toByteArray(Charset.forName("windows-1251"))
        val decoded = TextEncodingDetector.decodeXml(bytes)
        assertEquals(true, decoded.contains(cyrillic1251))
    }

    @Test
    fun `xml in declared koi8-r is decoded correctly`() {
        val header = """<?xml version="1.0" encoding="koi8-r"?><body>"""
        val bytes = (header + cyrillic1251 + "</body>")
            .toByteArray(Charset.forName("koi8-r"))
        val decoded = TextEncodingDetector.decodeXml(bytes)
        assertEquals(true, decoded.contains(cyrillic1251))
    }

    @Test
    fun `empty input produces empty string`() {
        assertEquals("", TextEncodingDetector.decodePlainText(ByteArray(0)))
        assertEquals("", TextEncodingDetector.decodeXml(ByteArray(0)))
    }
}
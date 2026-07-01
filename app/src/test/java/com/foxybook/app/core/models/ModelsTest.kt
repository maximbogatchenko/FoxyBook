package com.foxybook.app.core.models

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ModelsTest {

    @Test
    fun `BookFormat fromExtension recognizes extensions`() {
        assertEquals(BookFormat.EPUB, BookFormat.fromExtension("epub"))
        assertEquals(BookFormat.FB2, BookFormat.fromExtension("fb2"))
        assertEquals(BookFormat.MOBI, BookFormat.fromExtension("mobi"))
        assertEquals(BookFormat.TXT, BookFormat.fromExtension("txt"))
        assertEquals(BookFormat.PDF, BookFormat.fromExtension("pdf"))
    }

    @Test
    fun `BookFormat fromExtension handles case insensitivity`() {
        assertEquals(BookFormat.EPUB, BookFormat.fromExtension("EPUB"))
        assertEquals(BookFormat.FB2, BookFormat.fromExtension("FB2"))
    }

    @Test
    fun `BookFormat fromExtension returns null for unknown`() {
        assertNull(BookFormat.fromExtension("docx"))
        assertNull(BookFormat.fromExtension(""))
        assertNull(BookFormat.fromExtension(null))
    }

    @Test
    fun `BookFormat isNativelySupported`() {
        assertTrue(BookFormat.EPUB.isNativelySupported())
        assertTrue(BookFormat.FB2.isNativelySupported())
        assertTrue(BookFormat.TXT.isNativelySupported())
    }

    @Test
    fun `BookFormat MOBI and PDF are not natively supported`() {
        assertTrue(!BookFormat.MOBI.isNativelySupported())
        assertTrue(!BookFormat.PDF.isNativelySupported())
    }

    @Test
    fun `ReaderSettings defaults`() {
        val s = ReaderSettings()
        assertEquals(18, s.fontSize)
        assertEquals(1.8f, s.lineHeight)
        assertEquals(16, s.margins)
        assertEquals("HORIZONTAL", s.readerMode)
        assertEquals("SYSTEM", s.readerTheme)
    }

    @Test
    fun `Book has correct defaults`() {
        val book = Book(
            id = 1,
            title = "Test Book",
            author = "Test Author",
            link = "/b/1",
            sendLink = "/send/1"
        )
        assertEquals(1, book.id)
        assertEquals("Test Book", book.title)
        assertEquals("", book.coverUrl)
        assertTrue(book.genres.isEmpty())
        assertEquals(0, book.sequenceNumber)
        assertEquals("", book.description)
    }

    @Test
    fun `DownloadProgress defaults`() {
        val p = DownloadProgress()
        assertEquals(DownloadStatus.IDLE, p.status)
        assertEquals(0, p.percent)
        assertNull(p.error)
        assertEquals("", p.filePath)
    }
}

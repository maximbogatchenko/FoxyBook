package com.foxybook.app.core.reader

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class HtmlBlockParserTest {

    @Test
    fun `parse simple paragraph`() {
        val html = "<body><p>Hello world</p></body>"
        val blocks = HtmlBlockParser.parse(html)

        assertEquals(1, blocks.size)
        assertTrue(blocks[0] is ContentBlock.Paragraph)
        assertEquals("Hello world", (blocks[0] as ContentBlock.Paragraph).text)
    }

    @Test
    fun `parse heading levels`() {
        val html = """
            <body>
                <h1>Title</h1>
                <h2>Chapter</h2>
                <h3>Section</h3>
                <p>Text</p>
            </body>
        """.trimIndent()
        val blocks = HtmlBlockParser.parse(html)

        assertEquals(4, blocks.size)
        assertEquals(ContentBlock.Heading("Title", 1), blocks[0])
        assertEquals(ContentBlock.Heading("Chapter", 2), blocks[1])
        assertEquals(ContentBlock.Heading("Section", 3), blocks[2])
        assertTrue(blocks[3] is ContentBlock.Paragraph)
    }

    @Test
    fun `parse blockquote`() {
        val html = "<body><blockquote><p>Quote text</p></blockquote></body>"
        val blocks = HtmlBlockParser.parse(html)

        assertEquals(1, blocks.size)
        val quote = blocks[0] as? ContentBlock.Quote
        assertTrue(quote != null)
        assertEquals("Quote text", quote?.text)
    }

    @Test
    fun `parse empty content returns empty paragraph`() {
        val blocks = HtmlBlockParser.parse("")
        assertEquals(1, blocks.size)
        assertEquals(ContentBlock.Paragraph(""), blocks[0])
    }

    @Test
    fun `parse invalid html gracefully`() {
        val blocks = HtmlBlockParser.parse("<not>valid<html")
        assertTrue(blocks.isNotEmpty())
    }

    @Test
    fun `parse list items`() {
        val html = "<body><ul><li>Item 1</li><li>Item 2</li></ul></body>"
        val blocks = HtmlBlockParser.parse(html)

        assertEquals(2, blocks.size)
        assertTrue((blocks[0] as ContentBlock.Paragraph).text.startsWith("•"))
        assertTrue((blocks[1] as ContentBlock.Paragraph).text.startsWith("•"))
    }

    @Test
    fun `parse nested sections`() {
        val html = "<body><section><h1>Nested</h1><p>Nested text</p></section></body>"
        val blocks = HtmlBlockParser.parse(html)

        assertEquals(2, blocks.size)
        assertEquals(ContentBlock.Heading("Nested", 1), blocks[0])
        assertEquals("Nested text", (blocks[1] as ContentBlock.Paragraph).text)
    }

    @Test
    fun `originalIndex is set on all blocks`() {
        val html = "<body><h1>A</h1><p>B</p><p>C</p></body>"
        val blocks = HtmlBlockParser.parse(html)

        blocks.forEachIndexed { index, block ->
            assertEquals(index, block.originalIndex)
        }
    }
}

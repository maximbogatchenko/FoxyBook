package com.foxybook.app.core.reader

import org.jsoup.Jsoup
import org.jsoup.nodes.Element
import org.jsoup.nodes.TextNode

/**
 * Parses HTML content from any format (EPUB/FB2/MOBI) into List<ContentBlock>.
 * Runs once per chapter. Result is cached and never re-parsed during scrolling.
 */
object HtmlBlockParser {

    fun parse(htmlContent: String): List<ContentBlock> {
        val blocks = mutableListOf<ContentBlock>()
        try {
            val doc = Jsoup.parse(htmlContent)
            val body = doc.body() ?: doc
            walkElement(body, blocks)
        } catch (_: Exception) {
            // Fallback: treat entire content as one paragraph
            val text = Jsoup.parse(htmlContent).text()
            if (text.isNotBlank()) {
                blocks.add(ContentBlock.Paragraph(text))
            }
        }
        return blocks.ifEmpty { listOf(ContentBlock.Paragraph("")) }
    }

    private fun walkElement(element: Element, blocks: MutableList<ContentBlock>) {
        for (child in element.children()) {
            when (child.tagName().lowercase()) {
                "h1" -> blocks.add(ContentBlock.Heading(child.text().trim(), 1))
                "h2" -> blocks.add(ContentBlock.Heading(child.text().trim(), 2))
                "h3" -> blocks.add(ContentBlock.Heading(child.text().trim(), 3))
                "h4" -> blocks.add(ContentBlock.Heading(child.text().trim(), 4))
                "h5" -> blocks.add(ContentBlock.Heading(child.text().trim(), 5))
                "h6" -> blocks.add(ContentBlock.Heading(child.text().trim(), 6))
                "p" -> {
                    val text = child.text().trim()
                    if (text.isNotBlank()) {
                        // Check if it's inside a quote/epigraph/poem
                        val parentClass = child.parent()?.className() ?: ""
                        val parentTag = child.parent()?.tagName() ?: ""
                        when {
                            parentClass.contains("epigraph") || parentTag == "blockquote" -> {
                                // Handled by parent
                            }
                            parentClass.contains("poem") || parentClass.contains("stanza") -> {
                                // Handled by parent
                            }
                            else -> blocks.add(ContentBlock.Paragraph(text))
                        }
                    }
                }
                "blockquote" -> {
                    val ps = child.select("p")
                    val text = if (ps.isNotEmpty()) ps.joinToString("\n\n") { it.text().trim() } else child.text().trim()
                    val authorEl = child.selectFirst(".epigraph-author, cite, footer")
                    val author = authorEl?.text()?.trim()
                    if (text.isNotBlank()) {
                        blocks.add(ContentBlock.Quote(text, author))
                    }
                }
                "div" -> {
                    val cls = child.className()
                    when {
                        cls.contains("epigraph") -> {
                            val ps = child.select("p")
                            val text = if (ps.isNotEmpty()) ps.joinToString("\n\n") { it.text().trim() } else child.text().trim()
                            val authorEl = child.selectFirst(".epigraph-author, .author")
                            val author = authorEl?.text()?.trim()
                            if (text.isNotBlank()) blocks.add(ContentBlock.Quote(text, author))
                        }
                        cls.contains("poem") -> {
                            val title = child.selectFirst("h3, .poem-title")?.text()?.trim()
                            val lines = child.select(".verse-line, .stanza p, .stanza v").map { it.text().trim() }.filter { it.isNotBlank() }
                            val author = child.selectFirst(".poem-author, .author")?.text()?.trim()
                            if (lines.isNotEmpty()) {
                                blocks.add(ContentBlock.Poem(lines, title, author))
                            } else {
                                // Fallback: extract all text lines
                                val allText = child.text().trim()
                                if (allText.isNotBlank()) blocks.add(ContentBlock.Paragraph(allText))
                            }
                        }
                        else -> walkElement(child, blocks)
                    }
                }
                "br" -> blocks.add(ContentBlock.EmptyLine(4))
                "hr" -> blocks.add(ContentBlock.EmptyLine(16))
                "img", "image" -> {
                    val src = child.attr("src").ifBlank { child.attr("l:href") }
                    val alt = child.attr("alt").ifBlank { child.attr("title") }
                    if (src.isNotBlank()) blocks.add(ContentBlock.Image(src, alt))
                }
                "ul", "ol" -> {
                    for (li in child.select("> li")) {
                        val text = li.text().trim()
                        if (text.isNotBlank()) blocks.add(ContentBlock.Paragraph("• $text"))
                    }
                }
                "table" -> {
                    // Simple table: flatten to paragraphs
                    for (row in child.select("tr")) {
                        val cells = row.select("td, th").joinToString("  ") { it.text().trim() }
                        if (cells.isNotBlank()) blocks.add(ContentBlock.Paragraph(cells))
                    }
                }
                "section", "article", "main", "header", "footer", "nav", "aside" -> {
                    walkElement(child, blocks)
                }
                "span", "a", "strong", "em", "b", "i", "small", "big", "code", "pre" -> {
                    // Inline elements: collect text if parent didn't handle it
                    val text = child.text().trim()
                    if (text.isNotBlank() && element.tagName() == "body") {
                        blocks.add(ContentBlock.Paragraph(text))
                    }
                }
                // Skip script, style, etc.
            }
        }

        // Catch text nodes directly inside the element (rare but possible)
        for (node in element.textNodes()) {
            val text = node.text().trim()
            if (text.isNotBlank() && element.tagName() == "body") {
                blocks.add(ContentBlock.Paragraph(text))
            }
        }
    }
}

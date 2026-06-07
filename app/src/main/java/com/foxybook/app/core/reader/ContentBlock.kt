package com.foxybook.app.core.reader

/**
 * Parsed content block for native Compose rendering.
 * Each block is one visual unit (paragraph, heading, quote, etc.).
 */
sealed class ContentBlock {
    data class Heading(val text: String, val level: Int) : ContentBlock()
    data class Paragraph(val text: String) : ContentBlock()
    data class Quote(val text: String, val author: String? = null) : ContentBlock()
    data class Poem(val lines: List<String>, val title: String? = null, val author: String? = null) : ContentBlock()
    data class EmptyLine(val height: Int = 8) : ContentBlock()
    data class Image(val src: String, val alt: String = "") : ContentBlock()
}

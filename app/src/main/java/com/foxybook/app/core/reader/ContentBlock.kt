package com.foxybook.app.core.reader

/**
 * Parsed content block for native Compose rendering.
 * Each block is one visual unit (paragraph, heading, quote, etc.).
 */
sealed class ContentBlock(val isSplitAtBottom: Boolean = false, var originalIndex: Int = -1) {
    data class Heading(val text: String, val level: Int) : ContentBlock()
    
    data class Paragraph(
        val text: String,
        val isSplit: Boolean = false
    ) : ContentBlock(isSplitAtBottom = isSplit)
    
    data class Quote(
        val text: String,
        val author: String? = null,
        val isSplit: Boolean = false
    ) : ContentBlock(isSplitAtBottom = isSplit)
    
    data class Poem(
        val lines: List<String>,
        val title: String? = null,
        val author: String? = null,
        val isSplit: Boolean = false
    ) : ContentBlock(isSplitAtBottom = isSplit)
    
    data class EmptyLine(val height: Int = 8) : ContentBlock()

    data class Image(val src: String, val alt: String = "") : ContentBlock()

    fun getTextContent(): String = when (this) {
        is Heading -> text
        is Paragraph -> text
        is Quote -> text
        is Poem -> lines.joinToString(" ")
        else -> ""
    }
}

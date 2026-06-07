package com.foxybook.app.core.reader

import com.foxybook.app.core.models.ReaderSettings

/**
 * Paginates ContentBlocks into screen-sized pages for Page Mode.
 * Uses character-count estimation based on font size, line height, and screen dimensions.
 * Runs once per chapter. Result is cached.
 */
object TextPaginator {

    data class Page(
        val blocks: List<ContentBlock>,
        val chapterIndex: Int,
        val pageIndexInChapter: Int
    )

    fun paginate(
        blocks: List<ContentBlock>,
        chapterIndex: Int,
        pageWidthPx: Int,
        pageHeightPx: Int,
        settings: ReaderSettings
    ): List<Page> {
        if (blocks.isEmpty()) return listOf(Page(blocks, chapterIndex, 0))

        val marginPx = (settings.margins * 2.5f).toInt() // dp to px approximation
        val contentWidth = (pageWidthPx - marginPx * 2).coerceAtLeast(100)
        val contentHeight = (pageHeightPx - marginPx * 2).coerceAtLeast(100)
        val fontSizePx = settings.fontSize * 2.2f // sp to px approximation
        val lineHeightPx = fontSizePx * settings.lineHeight

        val charsPerLine = (contentWidth / (fontSizePx * 0.52f)).toInt().coerceAtLeast(10)
        val linesPerPage = (contentHeight / lineHeightPx).toInt().coerceAtLeast(3)

        val pages = mutableListOf<Page>()
        var currentPageBlocks = mutableListOf<ContentBlock>()
        var currentLines = 0

        for (block in blocks) {
            val blockLines = estimateLines(block, charsPerLine, lineHeightPx, contentHeight)

            // If a single block exceeds a page, split it
            if (blockLines > linesPerPage && block is ContentBlock.Paragraph) {
                val sentences = splitIntoChunks(block.text, charsPerLine * linesPerPage)
                for (chunk in sentences) {
                    val chunkLines = estimateTextLines(chunk, charsPerLine)
                    if (currentLines + chunkLines > linesPerPage && currentPageBlocks.isNotEmpty()) {
                        pages.add(Page(currentPageBlocks.toList(), chapterIndex, pages.size))
                        currentPageBlocks = mutableListOf()
                        currentLines = 0
                    }
                    currentPageBlocks.add(ContentBlock.Paragraph(chunk))
                    currentLines += chunkLines
                }
                continue
            }

            // If adding this block exceeds the page, start a new page
            if (currentLines + blockLines > linesPerPage && currentPageBlocks.isNotEmpty()) {
                pages.add(Page(currentPageBlocks.toList(), chapterIndex, pages.size))
                currentPageBlocks = mutableListOf()
                currentLines = 0
            }

            currentPageBlocks.add(block)
            currentLines += blockLines
        }

        if (currentPageBlocks.isNotEmpty()) {
            pages.add(Page(currentPageBlocks.toList(), chapterIndex, pages.size))
        }

        return pages.ifEmpty { listOf(Page(blocks, chapterIndex, 0)) }
    }

    private fun estimateLines(block: ContentBlock, charsPerLine: Int, lineHeightPx: Float, contentHeight: Int): Int {
        return when (block) {
            is ContentBlock.Heading -> {
                val textLines = estimateTextLines(block.text, (charsPerLine * 0.85).toInt().coerceAtLeast(5))
                textLines + 1 // spacing
            }
            is ContentBlock.Paragraph -> {
                estimateTextLines(block.text, charsPerLine)
            }
            is ContentBlock.Quote -> {
                val textLines = estimateTextLines(block.text, (charsPerLine * 0.9).toInt().coerceAtLeast(5))
                textLines + 2 // padding + border
            }
            is ContentBlock.Poem -> {
                block.lines.size + (if (block.title != null) 2 else 0) + (if (block.author != null) 1 else 0) + 1
            }
            is ContentBlock.EmptyLine -> (block.height / lineHeightPx).toInt().coerceAtLeast(1)
            is ContentBlock.Image -> 4 // Approximate image height in lines
        }
    }

    private fun estimateTextLines(text: String, charsPerLine: Int): Int {
        if (text.isBlank()) return 1
        return ((text.length + charsPerLine - 1) / charsPerLine).coerceAtLeast(1)
    }

    private fun splitIntoChunks(text: String, charsPerChunk: Int): List<String> {
        if (text.length <= charsPerChunk) return listOf(text)
        val chunks = mutableListOf<String>()
        var remaining = text
        while (remaining.length > charsPerChunk) {
            // Try to split at sentence boundary
            val splitAt = remaining.lastIndexOf('.', charsPerChunk).let {
                if (it > charsPerChunk / 2) it + 1
                else remaining.lastIndexOf(' ', charsPerChunk).let { s ->
                    if (s > charsPerChunk / 2) s + 1 else charsPerChunk
                }
            }
            chunks.add(remaining.substring(0, splitAt).trim())
            remaining = remaining.substring(splitAt).trim()
        }
        if (remaining.isNotBlank()) chunks.add(remaining)
        return chunks
    }
}

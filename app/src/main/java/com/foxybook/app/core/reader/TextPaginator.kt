package com.foxybook.app.core.reader

import android.util.Log
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.text.TextMeasurer
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.foxybook.app.core.models.ReaderSettings

/**
 * Paginates ContentBlocks into screen-sized pages for Page Mode.
 * Uses exact text measurements via Jetpack Compose TextMeasurer.
 */
object TextPaginator {

    data class Page(
        val blocks: List<ContentBlock>,
        val chapterIndex: Int,
        val pageIndexInChapter: Int,
        val startOffset: Int = 0,
        val endOffset: Int = 0
    )

    fun paginate(
        blocks: List<ContentBlock>,
        chapterIndex: Int,
        pageWidthPx: Int,
        pageHeightPx: Int,
        settings: ReaderSettings,
        textMeasurer: TextMeasurer,
        density: Density
    ): List<Page> {
        if (blocks.isEmpty()) return listOf(Page(emptyList(), chapterIndex, 0))

        val pages = mutableListOf<Page>()
        var currentPageBlocks = mutableListOf<ContentBlock>()
        var currentHeight = 0f
        var cumulativeOffsetInChapter = 0

        // SAFE READING AREA: 
        // We add a safety buffer at the bottom to ensure the last line is never clipped
        // and to leave some breathing room (1-2 lines).
        val lineHeightPx = with(density) { (settings.fontSize * settings.lineHeight).sp.toPx() }
        val bottomSafetyBuffer = (lineHeightPx * 1.5f).coerceAtLeast(with(density) { 16.dp.toPx() })
        val usableHeightPx = pageHeightPx - bottomSafetyBuffer

        // Text Styles - MUST MATCH BlockComposable
        val headingStyles = mapOf(
            1 to TextStyle(fontSize = 28.sp, lineHeight = (28 * settings.lineHeight * 0.85).sp, fontWeight = FontWeight.Bold),
            2 to TextStyle(fontSize = 24.sp, lineHeight = (24 * settings.lineHeight * 0.85).sp, fontWeight = FontWeight.Bold),
            3 to TextStyle(fontSize = 21.sp, lineHeight = (21 * settings.lineHeight * 0.85).sp, fontWeight = FontWeight.Bold),
            4 to TextStyle(fontSize = 19.sp, lineHeight = (19 * settings.lineHeight * 0.85).sp, fontWeight = FontWeight.Bold),
            5 to TextStyle(fontSize = 17.sp, lineHeight = (17 * settings.lineHeight * 0.85).sp, fontWeight = FontWeight.Bold),
            6 to TextStyle(fontSize = 15.sp, lineHeight = (15 * settings.lineHeight * 0.85).sp, fontWeight = FontWeight.Bold)
        )
        val defaultHeadingStyle = TextStyle(fontSize = 18.sp, lineHeight = (18 * settings.lineHeight * 0.85).sp, fontWeight = FontWeight.Bold)

        val paragraphStyle = TextStyle(fontSize = settings.fontSize.sp, lineHeight = (settings.fontSize * settings.lineHeight).sp)
        val quoteTextStyle = TextStyle(fontSize = (settings.fontSize - 1).sp, lineHeight = (settings.fontSize * settings.lineHeight).sp, fontStyle = FontStyle.Italic)
        val quoteAuthorStyle = TextStyle(fontSize = (settings.fontSize - 2).sp)
        val poemLineStyle = TextStyle(fontSize = (settings.fontSize - 1).sp, lineHeight = (settings.fontSize * settings.lineHeight * 0.9).sp)
        val poemTitleStyle = TextStyle(fontSize = (settings.fontSize - 1).sp, fontWeight = FontWeight.SemiBold)
        val poemAuthorStyle = TextStyle(fontSize = (settings.fontSize - 2).sp)

        // Paddings - MUST MATCH BlockComposable
        val headingTopPadding = with(density) { 16.dp.toPx() }
        val headingBottomPadding = with(density) { 8.dp.toPx() }
        val paragraphBottomPadding = with(density) { (settings.fontSize * 0.4).dp.toPx() }
        val quoteOuterPadding = with(density) { 6.dp.toPx() }
        val quoteInnerPaddingVertical = with(density) { 8.dp.toPx() }
        val quoteAuthorTopPadding = with(density) { 4.dp.toPx() }
        val poemVerticalPadding = with(density) { 8.dp.toPx() }
        val poemTitleBottomPadding = with(density) { 6.dp.toPx() }
        val poemAuthorTopPadding = with(density) { 6.dp.toPx() }
        val imageBlockHeight = with(density) { 120.dp.toPx() }

        val remainingBlocks = ArrayDeque(blocks)

        while (remainingBlocks.isNotEmpty()) {
            val block = remainingBlocks.removeFirst()
            // Small tolerance prevents floating-point accumulation from causing overflows
            val remainingHeight = (usableHeightPx - currentHeight).coerceAtLeast(0f) + 0.5f

            // If less than one line remains, finalize the page so next block
            // starts on a fresh page instead of being force-added and overflowing.
            if (remainingHeight < lineHeightPx && currentPageBlocks.isNotEmpty()) {
                val page = createPage(currentPageBlocks, chapterIndex, pages.size, cumulativeOffsetInChapter)
                pages.add(page)
                cumulativeOffsetInChapter = page.endOffset
                currentPageBlocks = mutableListOf()
                currentHeight = 0f
            }

            when (block) {
                is ContentBlock.Heading -> {
                    val style = headingStyles[block.level] ?: defaultHeadingStyle
                    val textLayout = textMeasurer.measure(
                        text = block.text,
                        style = style,
                        constraints = Constraints(maxWidth = pageWidthPx)
                    )
                    val blockHeight = textLayout.size.height + headingTopPadding + headingBottomPadding
                    if (blockHeight <= remainingHeight || currentPageBlocks.isEmpty()) {
                        currentPageBlocks.add(block)
                        currentHeight += blockHeight
                    } else {
                        remainingBlocks.addFirst(block)
                        val page = createPage(currentPageBlocks, chapterIndex, pages.size, cumulativeOffsetInChapter)
                        pages.add(page)
                        cumulativeOffsetInChapter = page.endOffset
                        currentPageBlocks = mutableListOf()
                        currentHeight = 0f
                    }
                }
                is ContentBlock.Paragraph -> {
                    val textLayout = textMeasurer.measure(
                        text = block.text,
                        style = paragraphStyle,
                        constraints = Constraints(maxWidth = pageWidthPx)
                    )
                    val needsBottomPadding = !block.isSplitAtBottom
                    val blockHeight = textLayout.size.height + (if (needsBottomPadding) paragraphBottomPadding else 0f)
                    
                    if (blockHeight <= remainingHeight) {
                        currentPageBlocks.add(block)
                        currentHeight += blockHeight
                    } else {
                        // Split paragraph
                        var lastFittingLine = -1
                        for (i in 0 until textLayout.lineCount) {
                            if (textLayout.getLineBottom(i) <= remainingHeight) {
                                lastFittingLine = i
                            } else {
                                break
                            }
                        }

                        if (lastFittingLine >= 0) {
                            val splitIndex = textLayout.getLineEnd(lastFittingLine)
                            if (splitIndex > 0 && splitIndex < block.text.length) {
                                val firstPart = block.text.substring(0, splitIndex)
                                val secondPart = block.text.substring(splitIndex)

                                currentPageBlocks.add(ContentBlock.Paragraph(text = firstPart, isSplit = true).apply { originalIndex = block.originalIndex })
                                val page = createPage(currentPageBlocks, chapterIndex, pages.size, cumulativeOffsetInChapter)
                                pages.add(page)
                                cumulativeOffsetInChapter = page.endOffset

                                remainingBlocks.addFirst(ContentBlock.Paragraph(text = secondPart, isSplit = block.isSplit).apply { originalIndex = block.originalIndex })
                                currentPageBlocks = mutableListOf()
                                currentHeight = 0f
                                continue
                            } else if (splitIndex > 0) {
                                // All text content fits, only bottom padding overflows.
                                // Add without padding and finalize the page.
                                currentPageBlocks.add(ContentBlock.Paragraph(text = block.text, isSplit = true).apply { originalIndex = block.originalIndex })
                                val page = createPage(currentPageBlocks, chapterIndex, pages.size, cumulativeOffsetInChapter)
                                pages.add(page)
                                cumulativeOffsetInChapter = page.endOffset
                                currentPageBlocks = mutableListOf()
                                currentHeight = 0f
                                continue
                            }
                        }

                        if (currentPageBlocks.isEmpty()) {
                            currentPageBlocks.add(block)
                            currentHeight += blockHeight
                        } else {
                            remainingBlocks.addFirst(block)
                            val page = createPage(currentPageBlocks, chapterIndex, pages.size, cumulativeOffsetInChapter)
                            pages.add(page)
                            cumulativeOffsetInChapter = page.endOffset
                            currentPageBlocks = mutableListOf()
                            currentHeight = 0f
                        }
                    }
                }
                is ContentBlock.Quote -> {
                    val qHMargin = with(density) { (12 + 4).dp.roundToPx() }
                    val quoteLayout = textMeasurer.measure(
                        text = block.text,
                        style = quoteTextStyle,
                        constraints = Constraints(maxWidth = pageWidthPx - qHMargin)
                    )
                    var authorHeight = 0f
                    if (block.author != null) {
                        val authorLayout = textMeasurer.measure(
                            text = block.author,
                            style = quoteAuthorStyle,
                            constraints = Constraints(maxWidth = pageWidthPx - qHMargin)
                        )
                        authorHeight = authorLayout.size.height + quoteAuthorTopPadding
                    }
                    val blockContentHeight = quoteLayout.size.height + authorHeight
                    val blockHeight = blockContentHeight + (quoteOuterPadding * 2) + (quoteInnerPaddingVertical * 2)

                    if (blockHeight <= remainingHeight) {
                        currentPageBlocks.add(block)
                        currentHeight += blockHeight
                    } else {
                        // Split quote
                        val spaceForText = remainingHeight - (quoteOuterPadding * 2) - (quoteInnerPaddingVertical * 2)
                        var lastFittingLine = -1
                        for (i in 0 until quoteLayout.lineCount) {
                            if (quoteLayout.getLineBottom(i) <= spaceForText) {
                                lastFittingLine = i
                            } else {
                                break
                            }
                        }

                        if (lastFittingLine >= 0) {
                            val splitIndex = quoteLayout.getLineEnd(lastFittingLine)
                            if (splitIndex > 0 && splitIndex < block.text.length) {
                                val firstPart = block.text.substring(0, splitIndex)
                                val secondPart = block.text.substring(splitIndex)

                                currentPageBlocks.add(ContentBlock.Quote(text = firstPart, author = null, isSplit = true).apply { originalIndex = block.originalIndex })
                                val page = createPage(currentPageBlocks, chapterIndex, pages.size, cumulativeOffsetInChapter)
                                pages.add(page)
                                cumulativeOffsetInChapter = page.endOffset

                                remainingBlocks.addFirst(ContentBlock.Quote(text = secondPart, author = block.author, isSplit = block.isSplit).apply { originalIndex = block.originalIndex })
                                currentPageBlocks = mutableListOf()
                                currentHeight = 0f
                                continue
                            }
                        }

                        if (currentPageBlocks.isEmpty()) {
                            currentPageBlocks.add(block)
                            currentHeight += blockHeight
                        } else {
                            remainingBlocks.addFirst(block)
                            val page = createPage(currentPageBlocks, chapterIndex, pages.size, cumulativeOffsetInChapter)
                            pages.add(page)
                            cumulativeOffsetInChapter = page.endOffset
                            currentPageBlocks = mutableListOf()
                            currentHeight = 0f
                        }
                    }
                }
                is ContentBlock.Poem -> {
                    var titleHeight = 0f
                    if (block.title != null) {
                        val titleLayout = textMeasurer.measure(
                            text = block.title,
                            style = poemTitleStyle,
                            constraints = Constraints(maxWidth = pageWidthPx)
                        )
                        titleHeight = titleLayout.size.height + poemTitleBottomPadding
                    }
                    var authorHeight = 0f
                    if (block.author != null) {
                        val authorLayout = textMeasurer.measure(
                            text = block.author,
                            style = poemAuthorStyle,
                            constraints = Constraints(maxWidth = pageWidthPx)
                        )
                        authorHeight = authorLayout.size.height + poemAuthorTopPadding
                    }

                    val lineLayouts: List<TextLayoutResult> = block.lines.map { 
                        textMeasurer.measure(it, poemLineStyle, constraints = Constraints(maxWidth = pageWidthPx)) 
                    }
                    val poemContentHeight = titleHeight + lineLayouts.sumOf { it.size.height.toDouble() }.toFloat() + authorHeight
                    val blockHeight = poemContentHeight + (poemVerticalPadding * 2)

                    if (blockHeight <= remainingHeight) {
                        currentPageBlocks.add(block)
                        currentHeight += blockHeight
                    } else {
                        // Split poem by lines
                        var linesFitted = 0
                        var currentPoemHeight = titleHeight + poemVerticalPadding
                        for (layout in lineLayouts) {
                            if (currentPoemHeight + layout.size.height <= remainingHeight) {
                                currentPoemHeight += layout.size.height
                                linesFitted++
                            } else {
                                break
                            }
                        }

                        if (linesFitted > 0) {
                            val firstPartLines = block.lines.take(linesFitted)
                            val secondPartLines = block.lines.drop(linesFitted)

                            currentPageBlocks.add(ContentBlock.Poem(lines = firstPartLines, title = block.title, author = null, isSplit = true).apply { originalIndex = block.originalIndex })
                            val page = createPage(currentPageBlocks, chapterIndex, pages.size, cumulativeOffsetInChapter)
                            pages.add(page)
                            cumulativeOffsetInChapter = page.endOffset

                            remainingBlocks.addFirst(ContentBlock.Poem(lines = secondPartLines, title = null, author = block.author, isSplit = block.isSplit).apply { originalIndex = block.originalIndex })
                            currentPageBlocks = mutableListOf()
                            currentHeight = 0f
                        } else {
                            if (currentPageBlocks.isEmpty()) {
                                currentPageBlocks.add(block)
                                currentHeight += blockHeight
                            } else {
                                remainingBlocks.addFirst(block)
                                val page = createPage(currentPageBlocks, chapterIndex, pages.size, cumulativeOffsetInChapter)
                                pages.add(page)
                                cumulativeOffsetInChapter = page.endOffset
                                currentPageBlocks = mutableListOf()
                                currentHeight = 0f
                            }
                        }
                    }
                }
                is ContentBlock.EmptyLine -> {
                    val h = with(density) { block.height.dp.toPx() }
                    if (h <= remainingHeight || currentPageBlocks.isEmpty()) {
                        currentPageBlocks.add(block)
                        currentHeight += h
                    } else {
                        remainingBlocks.addFirst(block)
                        val page = createPage(currentPageBlocks, chapterIndex, pages.size, cumulativeOffsetInChapter)
                        pages.add(page)
                        cumulativeOffsetInChapter = page.endOffset
                        currentPageBlocks = mutableListOf()
                        currentHeight = 0f
                    }
                }
                is ContentBlock.Image -> {
                    if (imageBlockHeight <= remainingHeight || currentPageBlocks.isEmpty()) {
                        currentPageBlocks.add(block)
                        currentHeight += imageBlockHeight
                    } else {
                        remainingBlocks.addFirst(block)
                        val page = createPage(currentPageBlocks, chapterIndex, pages.size, cumulativeOffsetInChapter)
                        pages.add(page)
                        cumulativeOffsetInChapter = page.endOffset
                        currentPageBlocks = mutableListOf()
                        currentHeight = 0f
                    }
                }
            }
        }

        if (currentPageBlocks.isNotEmpty()) {
            pages.add(createPage(currentPageBlocks, chapterIndex, pages.size, cumulativeOffsetInChapter))
        }

        return pages.ifEmpty { listOf(Page(emptyList(), chapterIndex, 0)) }
    }

    private fun createPage(blocks: List<ContentBlock>, chapterIndex: Int, pageIndex: Int, startOffset: Int): Page {
        val charsCount = blocks.sumOf { it.getTextContent().length }
        val endOffset = startOffset + charsCount
        Log.d("TextPaginator", "chapterIndex: $chapterIndex, pageIndex: $pageIndex, startOffset: $startOffset, endOffset: $endOffset, charsCount: $charsCount")
        return Page(blocks.toList(), chapterIndex, pageIndex, startOffset, endOffset)
    }

    private fun ContentBlock.getTextContent(): String {
        return when (this) {
            is ContentBlock.Heading -> text
            is ContentBlock.Paragraph -> text
            is ContentBlock.Quote -> text + (author ?: "")
            is ContentBlock.Poem -> (title ?: "") + lines.joinToString("") + (author ?: "")
            is ContentBlock.EmptyLine -> ""
            is ContentBlock.Image -> ""
        }
    }
}

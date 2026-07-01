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

    // ─── Cached layout ───

    private data class LayoutStyles(
        val headingStyles: Map<Int, TextStyle>,
        val defaultHeadingStyle: TextStyle,
        val paragraphStyle: TextStyle,
        val quoteTextStyle: TextStyle,
        val quoteAuthorStyle: TextStyle,
        val poemLineStyle: TextStyle,
        val poemTitleStyle: TextStyle,
        val poemAuthorStyle: TextStyle
    )

    private data class Paddings(
        val headingTopPadding: Float,
        val headingBottomPadding: Float,
        val paragraphBottomPadding: Float,
        val quoteOuterPadding: Float,
        val quoteInnerPaddingVertical: Float,
        val quoteAuthorTopPadding: Float,
        val poemVerticalPadding: Float,
        val poemTitleBottomPadding: Float,
        val poemAuthorTopPadding: Float,
        val imageBlockHeight: Float,
        val lineHeightPx: Float,
        val bottomSafetyBuffer: Float
    )

    // Cache keys (settings + density determine layouts)
    // Кэш пересчитывается при изменении настроек (fontSize/lineHeight), так что
    // утечки нет — старые значения заменяются новыми при любом изменении параметров.
    private var lastSettingsKey: Long = 0
    private var lastDensityKey: Long = 0L
    private var lastStyles: LayoutStyles? = null
    private var lastPaddings: Paddings? = null

    private fun getStyles(settings: ReaderSettings): LayoutStyles {
        val key = settings.fontSize.toLong() +
            (settings.lineHeight * 100).toLong() * 1000L
        if (lastStyles != null && key == lastSettingsKey) return lastStyles!!
        lastSettingsKey = key
        val s = LayoutStyles(
            headingStyles = mapOf(
                1 to TextStyle(fontSize = 28.sp, lineHeight = (28 * settings.lineHeight * 0.85).sp, fontWeight = FontWeight.Bold),
                2 to TextStyle(fontSize = 24.sp, lineHeight = (24 * settings.lineHeight * 0.85).sp, fontWeight = FontWeight.Bold),
                3 to TextStyle(fontSize = 21.sp, lineHeight = (21 * settings.lineHeight * 0.85).sp, fontWeight = FontWeight.Bold),
                4 to TextStyle(fontSize = 19.sp, lineHeight = (19 * settings.lineHeight * 0.85).sp, fontWeight = FontWeight.Bold),
                5 to TextStyle(fontSize = 17.sp, lineHeight = (17 * settings.lineHeight * 0.85).sp, fontWeight = FontWeight.Bold),
                6 to TextStyle(fontSize = 15.sp, lineHeight = (15 * settings.lineHeight * 0.85).sp, fontWeight = FontWeight.Bold)
            ),
            defaultHeadingStyle = TextStyle(fontSize = 18.sp, lineHeight = (18 * settings.lineHeight * 0.85).sp, fontWeight = FontWeight.Bold),
            paragraphStyle = TextStyle(fontSize = settings.fontSize.sp, lineHeight = (settings.fontSize * settings.lineHeight).sp),
            quoteTextStyle = TextStyle(fontSize = (settings.fontSize - 1).sp, lineHeight = (settings.fontSize * settings.lineHeight).sp, fontStyle = FontStyle.Italic),
            quoteAuthorStyle = TextStyle(fontSize = (settings.fontSize - 2).sp),
            poemLineStyle = TextStyle(fontSize = (settings.fontSize - 1).sp, lineHeight = (settings.fontSize * settings.lineHeight * 0.9).sp),
            poemTitleStyle = TextStyle(fontSize = (settings.fontSize - 1).sp, fontWeight = FontWeight.SemiBold),
            poemAuthorStyle = TextStyle(fontSize = (settings.fontSize - 2).sp)
        )
        lastStyles = s
        return s
    }

    private fun getPaddings(settings: ReaderSettings, density: Density): Paddings {
        val key = (settings.fontSize.toLong() * 100) +
            (settings.lineHeight * 100).toLong() +
            (density.density * 1000).toLong() * 100_000L
        if (lastPaddings != null && key == lastDensityKey) return lastPaddings!!
        lastDensityKey = key
        val p = Paddings(
            headingTopPadding = with(density) { 16.dp.toPx() },
            headingBottomPadding = with(density) { 8.dp.toPx() },
            paragraphBottomPadding = with(density) { (settings.fontSize * 0.4).dp.toPx() },
            quoteOuterPadding = with(density) { 6.dp.toPx() },
            quoteInnerPaddingVertical = with(density) { 8.dp.toPx() },
            quoteAuthorTopPadding = with(density) { 4.dp.toPx() },
            poemVerticalPadding = with(density) { 8.dp.toPx() },
            poemTitleBottomPadding = with(density) { 6.dp.toPx() },
            poemAuthorTopPadding = with(density) { 6.dp.toPx() },
            imageBlockHeight = with(density) { 200.dp.toPx() },
            lineHeightPx = with(density) { (settings.fontSize * settings.lineHeight).sp.toPx() },
            bottomSafetyBuffer = (with(density) { (settings.fontSize * settings.lineHeight).sp.toPx() } * 1.5f)
                .coerceAtLeast(with(density) { 16.dp.toPx() })
        )
        lastPaddings = p
        return p
    }

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

        val ls = getStyles(settings)
        val pad = getPaddings(settings, density)

        val pages = mutableListOf<Page>()
        var currentPageBlocks = mutableListOf<ContentBlock>()
        var currentHeight = 0f
        var cumulativeOffsetInChapter = 0

        val usableHeightPx = pageHeightPx - pad.bottomSafetyBuffer

        val remainingBlocks = ArrayDeque(blocks)

        while (remainingBlocks.isNotEmpty()) {
            val block = remainingBlocks.removeFirst()
            val remainingHeight = (usableHeightPx - currentHeight).coerceAtLeast(0f) + 0.5f

            if (remainingHeight < pad.lineHeightPx && currentPageBlocks.isNotEmpty()) {
                val page = createPage(currentPageBlocks, chapterIndex, pages.size, cumulativeOffsetInChapter)
                pages.add(page)
                cumulativeOffsetInChapter = page.endOffset
                currentPageBlocks = mutableListOf()
                currentHeight = 0f
            }

            when (block) {
                is ContentBlock.Heading -> {
                    val style = ls.headingStyles[block.level] ?: ls.defaultHeadingStyle
                    val textLayout = textMeasurer.measure(
                        text = block.text,
                        style = style,
                        constraints = Constraints(maxWidth = pageWidthPx)
                    )
                    val blockHeight = textLayout.size.height + pad.headingTopPadding + pad.headingBottomPadding
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
                        style = ls.paragraphStyle,
                        constraints = Constraints(maxWidth = pageWidthPx)
                    )
                    val needsBottomPadding = !block.isSplitAtBottom
                    val blockHeight = textLayout.size.height + (if (needsBottomPadding) pad.paragraphBottomPadding else 0f)

                    if (blockHeight <= remainingHeight) {
                        currentPageBlocks.add(block)
                        currentHeight += blockHeight
                    } else {
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
                        style = ls.quoteTextStyle,
                        constraints = Constraints(maxWidth = pageWidthPx - qHMargin)
                    )
                    var authorHeight = 0f
                    if (block.author != null) {
                        val authorLayout = textMeasurer.measure(
                            text = block.author,
                            style = ls.quoteAuthorStyle,
                            constraints = Constraints(maxWidth = pageWidthPx - qHMargin)
                        )
                        authorHeight = authorLayout.size.height + pad.quoteAuthorTopPadding
                    }
                    val blockContentHeight = quoteLayout.size.height + authorHeight
                    val blockHeight = blockContentHeight + (pad.quoteOuterPadding * 2) + (pad.quoteInnerPaddingVertical * 2)

                    if (blockHeight <= remainingHeight) {
                        currentPageBlocks.add(block)
                        currentHeight += blockHeight
                    } else {
                        val spaceForText = remainingHeight - (pad.quoteOuterPadding * 2) - (pad.quoteInnerPaddingVertical * 2)
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
                            style = ls.poemTitleStyle,
                            constraints = Constraints(maxWidth = pageWidthPx)
                        )
                        titleHeight = titleLayout.size.height + pad.poemTitleBottomPadding
                    }
                    var authorHeight = 0f
                    if (block.author != null) {
                        val authorLayout = textMeasurer.measure(
                            text = block.author,
                            style = ls.poemAuthorStyle,
                            constraints = Constraints(maxWidth = pageWidthPx)
                        )
                        authorHeight = authorLayout.size.height + pad.poemAuthorTopPadding
                    }

                    val lineLayouts: List<TextLayoutResult> = block.lines.map {
                        textMeasurer.measure(it, ls.poemLineStyle, constraints = Constraints(maxWidth = pageWidthPx))
                    }
                    val poemContentHeight = titleHeight + lineLayouts.sumOf { it.size.height.toDouble() }.toFloat() + authorHeight
                    val blockHeight = poemContentHeight + (pad.poemVerticalPadding * 2)

                    if (blockHeight <= remainingHeight) {
                        currentPageBlocks.add(block)
                        currentHeight += blockHeight
                    } else {
                        var linesFitted = 0
                        var currentPoemHeight = titleHeight + pad.poemVerticalPadding
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
                    if (pad.imageBlockHeight <= remainingHeight || currentPageBlocks.isEmpty()) {
                        currentPageBlocks.add(block)
                        currentHeight += pad.imageBlockHeight
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

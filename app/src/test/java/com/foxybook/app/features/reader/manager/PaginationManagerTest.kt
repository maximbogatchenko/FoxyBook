package com.foxybook.app.features.reader.manager

import com.foxybook.app.core.models.ParsedBook
import com.foxybook.app.core.models.ParsedChapter
import com.foxybook.app.core.reader.TextPaginator
import com.foxybook.app.features.reader.ReaderState
import kotlinx.coroutines.flow.MutableStateFlow
import org.junit.Assert.assertEquals
import org.junit.Test

class PaginationManagerTest {

    private fun state(
        chapterPages: Map<Int, List<TextPaginator.Page>>,
        totalContentLength: Long,
        chapterLengths: List<Int>,
        chapterCount: Int,
    ): ReaderState {
        val book = ParsedBook(
            title = "Т",
            author = "А",
            format = "fmt",
            filePath = "p",
            chapters = List(chapterCount) { i -> ParsedChapter(title = "Глава ${i + 1}") },
        )
        return ReaderState(
            book = book,
            chapterPages = chapterPages,
            chapterLengths = chapterLengths,
            totalContentLength = totalContentLength,
            totalBookPages = 0,
            paginationVersion = 1,
        )
    }

    @Test
    fun `total book pages equals exact sum when all chapters are paginated`() {
        val pm = PaginationManager()
        val chapterPages = mapOf(
            0 to listOf(TextPaginator.Page(emptyList(), 0, 0, 0, 100)),
            1 to listOf(
                TextPaginator.Page(emptyList(), 1, 0, 0, 50),
                TextPaginator.Page(emptyList(), 1, 1, 50, 90),
            ),
        )
        val state = state(
            chapterPages = chapterPages,
            totalContentLength = 200,
            chapterLengths = listOf(100, 100),
            chapterCount = 2,
        )
        val flow = MutableStateFlow(state)
        val result = pm.refreshTotalBookPages(state, flow)

        // 1 страница в главе 0 + 2 страницы в главе 1 = 3 всего (точная сумма, а не оценка)
        assertEquals(3, result.totalBookPages)
        assertEquals(false, result.isCalculatingPages)
    }

    @Test
    fun `total book pages stays an estimate when not all chapters are paginated`() {
        val pm = PaginationManager()
        val chapterPages = mapOf(
            0 to listOf(TextPaginator.Page(emptyList(), 0, 0, 0, 100)),
        )
        val state = state(
            chapterPages = chapterPages,
            totalContentLength = 1000,
            chapterLengths = listOf(100, 900),
            chapterCount = 2,
        )
        val flow = MutableStateFlow(state)
        val result = pm.refreshTotalBookPages(state, flow)

        // Только 1 из 2 глав свёрстана — оценка должна быть не меньше реального количества страниц
        assertEquals(true, result.totalBookPages >= 1)
        assertEquals(false, result.isCalculatingPages)
    }
}

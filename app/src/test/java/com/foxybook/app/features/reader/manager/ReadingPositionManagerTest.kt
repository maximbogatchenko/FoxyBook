package com.foxybook.app.features.reader.manager

import com.foxybook.app.core.database.BookDataRepository
import com.foxybook.app.core.datastore.DataStoreManager
import com.foxybook.app.core.models.ParsedBook
import com.foxybook.app.core.models.ParsedChapter
import com.foxybook.app.core.models.ReaderSettings
import com.foxybook.app.core.models.ReadingPosition
import com.foxybook.app.features.reader.ReaderState
import io.mockk.*
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Test

class ReadingPositionManagerTest {

    private fun book(chapterCount: Int = 3): ParsedBook =
        ParsedBook("Книга", "Автор",
            List(chapterCount) { i -> ParsedChapter("Глава ${i + 1}") }, "fmt", "path")

    private fun state(
        currentChapter: Int = 0,
        pageCurrent: Int = 0,
        textOffset: Int = 0,
        scrollY: Int = 0,
        chapterLengths: List<Int> = listOf(1000, 1000, 1000),
        totalContentLength: Long = 3000,
        pageTotal: Int = 10,
        readingPercentage: Int = 0,
        isLoading: Boolean = false
    ): ReaderState {
        val b = book(chapterLengths.size)
        return ReaderState(
            book = b,
            currentChapter = currentChapter,
            chapterLengths = chapterLengths,
            totalContentLength = totalContentLength,
            pageCurrent = pageCurrent,
            pageTotal = pageTotal,
            settings = ReaderSettings(),
            readingPercentage = readingPercentage,
            isLoading = isLoading
        )
    }

    private fun newManager(
        repo: BookDataRepository = mockk(relaxed = true),
        dataStore: DataStoreManager = mockk(relaxed = true)
    ) = ReadingPositionManager(repo, dataStore)

    @Test
    fun `trackScroll updates scroll fields`() {
        val m = newManager()
        val s = state()
        val updated = m.trackScroll(50, 5, 100, 250, s)
        assertEquals(50, updated.scrollPercentage)
        assertEquals(5, updated.scrollY)
        assertEquals(100, updated.scrollOffset)
        assertEquals(250, updated.textOffset)
    }

    @Test
    fun `trackPageInfo updates page fields`() {
        val m = newManager()
        val s = state(pageCurrent = 3, pageTotal = 10)
        val updated = m.trackPageInfo(3, 10, 300, s)
        assertEquals(3, updated.pageCurrent)
        assertEquals(10, updated.pageTotal)
        assertEquals(300, updated.textOffset)
    }

    @Test
    fun `updateReadingPercentage chapter relative`() {
        val m = newManager()
        val s = state(currentChapter = 1, pageCurrent = 5, pageTotal = 10)
        val updated = m.updateReadingPercentage(s)
        assertEquals(50, updated.readingPercentage)
    }

    @Test
    fun `updateReadingPercentage zero chapters`() {
        val m = newManager()
        val s = state(chapterLengths = emptyList(), totalContentLength = 0)
        val updated = m.updateReadingPercentage(s)
        assertEquals(0, updated.readingPercentage)
    }

    @Test
    fun `requestSaveImmediate writes position to repository`() = runBlocking {
        val repo = mockk<BookDataRepository>(relaxed = true)
        val m = newManager(repo)
        val s = state(currentChapter = 2, pageCurrent = 4, textOffset = 999, scrollY = 0, readingPercentage = 33)

        val job = m.requestSaveImmediate(7, "epub", s, this)
        job.join()

        coVerify(exactly = 1) { repo.saveReadingPosition(any()) }
        coVerify(exactly = 1) { repo.updateLastReadDate(7, "epub") }
        coVerify(exactly = 1) { repo.updateReadingProgress(7, "epub", 33) }
    }

    @Test
    fun `identical position is not written twice`() = runBlocking {
        val repo = mockk<BookDataRepository>(relaxed = true)
        val m = newManager(repo)
        val s = state(currentChapter = 1, pageCurrent = 2, textOffset = 500, readingPercentage = 20)

        val job1 = m.requestSaveImmediate(7, "epub", s, this)
        val job2 = m.requestSaveImmediate(7, "epub", s, this)
        job1.join()
        job2.join()

        coVerify(exactly = 1) { repo.saveReadingPosition(any()) }
    }

    @Test
    fun `bookId negative is ignored`() = runBlocking {
        val repo = mockk<BookDataRepository>(relaxed = true)
        val m = newManager(repo)
        val job = m.requestSaveImmediate(-1, "epub", state(), this)
        job.join()
        coVerify(exactly = 0) { repo.saveReadingPosition(any()) }
    }
}

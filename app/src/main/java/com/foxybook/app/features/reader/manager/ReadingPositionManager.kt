package com.foxybook.app.features.reader.manager

import com.foxybook.app.core.database.BookDataRepository
import com.foxybook.app.core.datastore.DataStoreManager
import com.foxybook.app.core.models.ReadingPosition
import com.foxybook.app.features.reader.ReaderState
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

import kotlinx.coroutines.flow.MutableStateFlow

class ReadingPositionManager(
    private val bookDataRepository: BookDataRepository,
    private val dataStoreManager: DataStoreManager
) {
    private var saveJob: Job? = null

    fun trackScroll(
        percentage: Int, blockIndex: Int, scrollOffset: Int, offset: Int, state: ReaderState
    ): ReaderState {
        if (state.isLoading) return state
        return state.copy(
            scrollPercentage = percentage,
            scrollY = blockIndex,
            scrollOffset = scrollOffset,
            textOffset = offset
        )
    }

    fun trackPageInfo(current: Int, total: Int, offset: Int, state: ReaderState): ReaderState {
        if (state.isLoading) return state
        return state.copy(pageCurrent = current, pageTotal = total, textOffset = offset)
    }

    fun updateReadingPercentage(state: ReaderState): ReaderState {
        val book = state.book ?: return state
        if (book.chapters.isEmpty()) return state

        val mode = com.foxybook.app.core.models.ReaderMode.safeValueOf(state.settings.readerMode)
        val chapterPct = if (mode == com.foxybook.app.core.models.ReaderMode.HORIZONTAL) {
            if (state.pageTotal > 0) state.pageCurrent * 100f / state.pageTotal else 0f
        } else {
            state.scrollPercentage.toFloat()
        }

        val pct = if (state.totalContentLength > 0 && state.chapterLengths.size == book.chapters.size) {
            val consumedBefore = state.chapterLengths.take(state.currentChapter).sumOf { it.toLong() }
            val currentChapterLen = state.chapterLengths.getOrElse(state.currentChapter) { 0 }
            val consumedInChapter = (currentChapterLen * chapterPct / 100f).toLong()
            val totalConsumed = consumedBefore + consumedInChapter
            ((totalConsumed * 100) / state.totalContentLength).toInt().coerceIn(0, 100)
        } else {
            ((state.currentChapter + chapterPct / 100f) / book.chapters.size * 100).toInt().coerceIn(0, 100)
        }
        return state.copy(readingPercentage = pct)
    }

    fun debounceSave(bookId: Int, format: String, stateFlow: MutableStateFlow<ReaderState>, scope: CoroutineScope) {
        saveJob?.cancel()
        saveJob = scope.launch {
            delay(500)
            val s = stateFlow.value
            if (bookId >= 0 && s.book != null) {
                savePosition(bookId, format, s)
            }
        }
    }

    fun savePositionNow(bookId: Int, format: String, stateFlow: MutableStateFlow<ReaderState>, saveScope: CoroutineScope) {
        if (bookId < 0) return
        saveJob?.cancel()
        saveJob = saveScope.launch(NonCancellable) {
            val s = stateFlow.value
            if (s.book != null) {
                savePosition(bookId, format, s)
            }
        }
    }

    suspend fun savePosition(bookId: Int, format: String, state: ReaderState) {
        val pos = ReadingPosition(
            bookId = bookId,
            format = format,
            chapterIndex = state.currentChapter,
            pageIndex = state.pageCurrent,
            scrollPosition = state.scrollY,
            scrollOffset = state.scrollOffset,
            textOffset = state.textOffset,
            lastUpdated = System.currentTimeMillis()
        )
        bookDataRepository.saveReadingPosition(pos)
        bookDataRepository.updateLastReadDate(bookId, format)
        bookDataRepository.updateReadingProgress(bookId, format, state.readingPercentage)
    }

    fun stopSaveJob() {
        saveJob?.cancel()
        saveJob = null
    }
}

package com.foxybook.app.features.reader.manager

import com.foxybook.app.core.database.BookDataRepository
import com.foxybook.app.core.datastore.DataStoreManager
import com.foxybook.app.core.models.ReadingPosition
import com.foxybook.app.features.reader.ReaderState
import kotlinx.coroutines.CoroutineScope
<<<<<<< HEAD
=======
import kotlinx.coroutines.Dispatchers
>>>>>>> d24a20e (FoxyBook v1.9)
import kotlinx.coroutines.Job
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
<<<<<<< HEAD
import kotlinx.coroutines.withContext

import kotlinx.coroutines.flow.MutableStateFlow

=======
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.flow.MutableStateFlow
import java.util.concurrent.atomic.AtomicLong

/**
 * Хранение позиции чтения.
 *
 * Принципы работы:
 * 1. Листование страниц (PageInfo) и смена главы сохраняются НЕМгновенно — без debounce.
 *    Это гарантирует, что позиция не теряется, если приложение убили во время быстрого
 *    листования.
 * 2. Непрерывный скролл (ScrollProgress, режим прокрутки) сохраняется с debounce 300 мс,
 *    чтобы не бить в БД на каждый касание.
 * 3. Каждая запись получает возрастающий порядковый номер при постановке в очередь.
 *    Запись с устаревшим номером отбрасывается — «запаздывающая» debounce-запись не
 *    сможет перезаписать более новую позицию.
 * 4. Identical-позиция не пишется дважды (сверка по последнему сохранённому снимку).
 * 5. Все запросы к БД идут через [writeMutex], поэтому порядок записи строго соответствует
 *    порядку номеров.
 * 6. Во время программного пролистывания (восстановление позиции, перевёрстка) сохранение
 *    блокируется флагом [isProgrammaticScroll], чтобы не сохранить промежуточную страницу.
 */
>>>>>>> d24a20e (FoxyBook v1.9)
class ReadingPositionManager(
    private val bookDataRepository: BookDataRepository,
    private val dataStoreManager: DataStoreManager
) {
<<<<<<< HEAD
    private var saveJob: Job? = null
=======
    private val writeMutex = Mutex()
    private val sequence = AtomicLong(0)
    private val writtenSequence = AtomicLong(0)

    /** Последний сохранённый снимок позиции по книге — для пропуска повторных записей. */
    private val lastSaved = mutableMapOf<String, PositionSnapshot>()

    private var scrollDebounceJob: Job? = null

    /** Флаг, что пейджер сейчас мотается программно (восстановление / перевёрстка). */
    var isProgrammaticScroll: Boolean = false
        private set

    fun beginProgrammaticScroll() { isProgrammaticScroll = true }

    fun endProgrammaticScroll() { isProgrammaticScroll = false }

    private data class PositionSnapshot(
        val chapter: Int,
        val page: Int,
        val scroll: Int,
        val scrollOffset: Int,
        val textOffset: Int,
        val progress: Int
    ) {
        fun sameAs(other: PositionSnapshot): Boolean =
            chapter == other.chapter && page == other.page && scroll == other.scroll &&
                scrollOffset == other.scrollOffset && textOffset == other.textOffset &&
                progress == other.progress
    }

    private data class PendingSave(
        val seq: Long,
        val bookId: Int,
        val format: String,
        val position: ReadingPosition,
        val progress: Int
    )

    private fun positionKey(bookId: Int, format: String) = "$bookId|$format"

    private fun snapshotOf(state: ReaderState): PositionSnapshot =
        PositionSnapshot(
            state.currentChapter, state.pageCurrent, state.scrollY,
            state.scrollOffset, state.textOffset, state.readingPercentage
        )

    // ─── Scroll tracking (режим прокрутки) ───
>>>>>>> d24a20e (FoxyBook v1.9)

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

<<<<<<< HEAD
=======
    // ─── Page tracking (режим страниц) ───

>>>>>>> d24a20e (FoxyBook v1.9)
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

<<<<<<< HEAD
    fun debounceSave(bookId: Int, format: String, stateFlow: MutableStateFlow<ReaderState>, scope: CoroutineScope) {
        saveJob?.cancel()
        saveJob = scope.launch {
            delay(500)
            val s = stateFlow.value
            if (bookId >= 0 && s.book != null) {
                savePosition(bookId, format, s)
=======
    /**
     * Немедленное сохранение — для листования страниц и смены главы.
     * Пишется в IO-потоке, не ждёт debounce, не теряет позицию при закрытии приложения.
     * Возвращает Job записи, чтобы вызывающий мог дождаться её завершения.
     */
    fun requestSaveImmediate(
        bookId: Int, format: String, state: ReaderState, scope: CoroutineScope
    ): Job {
        if (bookId < 0 || state.book == null) return scope.launch(Dispatchers.IO) { }
        val pending = PendingSave(
            sequence.incrementAndGet(), bookId, format,
            toPosition(bookId, format, state), state.readingPercentage
        )
        return scope.launch(Dispatchers.IO) { write(pending) }
    }

    /**
     * Отложенное сохранение — только для непрерывного скролла (Scroll mode).
     * Каждое новое движение сбрасывает таймер; запись уходит после паузы 300 мс.
     */
    fun debounceScrollSave(
        bookId: Int, format: String, stateFlow: MutableStateFlow<ReaderState>,
        scope: CoroutineScope
    ) {
        scrollDebounceJob?.cancel()
        scrollDebounceJob = scope.launch {
            delay(300)
            val s = stateFlow.value
            if (bookId >= 0 && s.book != null) {
                requestSaveImmediate(bookId, format, s, scope)
>>>>>>> d24a20e (FoxyBook v1.9)
            }
        }
    }

<<<<<<< HEAD
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
=======
    /**
     * Немедленное сохранение при выходе с экрана / очистке ViewModel.
     * Работает под NonCancellable, чтобы пережить отмену saveScope и очистку ViewModel.
     */
    fun savePositionNow(
        bookId: Int, format: String, stateFlow: MutableStateFlow<ReaderState>,
        saveScope: CoroutineScope
    ) {
        if (bookId < 0) return
        val s = stateFlow.value
        if (s.book == null) return
        scrollDebounceJob?.cancel()
        val pending = PendingSave(
            sequence.incrementAndGet(), bookId, format,
            toPosition(bookId, format, s), s.readingPercentage
        )
        saveScope.launch(NonCancellable) { write(pending) }
    }

    private suspend fun write(pending: PendingSave) {
        writeMutex.withLock {
            // Запаздывающая запись не должна перезаписать более новую позицию.
            if (pending.seq <= writtenSequence.get()) return

            val key = positionKey(pending.bookId, pending.format)
            val snapshot = snapshotOfState(pending.position, pending.progress)
            if (lastSaved[key]?.sameAs(snapshot) == true) {
                // Позиция не изменилась — запись избыточна, но номер продолжаем, чтобы
                // порядок записи не нарушался для более новых позиций этой книги.
                writtenSequence.set(pending.seq)
                return
            }

            bookDataRepository.saveReadingPosition(pending.position)
            bookDataRepository.updateLastReadDate(pending.bookId, pending.format)
            bookDataRepository.updateReadingProgress(pending.bookId, pending.format, pending.progress)

            lastSaved[key] = snapshot
            writtenSequence.set(pending.seq)
        }
    }

    private fun toPosition(bookId: Int, format: String, state: ReaderState): ReadingPosition =
        ReadingPosition(
>>>>>>> d24a20e (FoxyBook v1.9)
            bookId = bookId,
            format = format,
            chapterIndex = state.currentChapter,
            pageIndex = state.pageCurrent,
            scrollPosition = state.scrollY,
            scrollOffset = state.scrollOffset,
            textOffset = state.textOffset,
            lastUpdated = System.currentTimeMillis()
        )
<<<<<<< HEAD
        bookDataRepository.saveReadingPosition(pos)
        bookDataRepository.updateLastReadDate(bookId, format)
        bookDataRepository.updateReadingProgress(bookId, format, state.readingPercentage)
    }

    fun stopSaveJob() {
        saveJob?.cancel()
        saveJob = null
=======

    private fun snapshotOfState(position: ReadingPosition, progress: Int): PositionSnapshot =
        PositionSnapshot(
            position.chapterIndex, position.pageIndex, position.scrollPosition,
            position.scrollOffset, position.textOffset, progress
        )

    fun stopSaveJob() {
        scrollDebounceJob?.cancel()
        scrollDebounceJob = null
>>>>>>> d24a20e (FoxyBook v1.9)
    }
}

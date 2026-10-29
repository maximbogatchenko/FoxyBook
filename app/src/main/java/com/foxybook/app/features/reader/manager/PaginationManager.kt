package com.foxybook.app.features.reader.manager

import android.util.Log
import androidx.compose.ui.text.TextMeasurer
import androidx.compose.ui.unit.Density
import com.foxybook.app.core.reader.ContentBlock
import com.foxybook.app.core.reader.TextPaginator
<<<<<<< HEAD
=======
import com.foxybook.app.core.models.ReaderSettings
>>>>>>> d24a20e (FoxyBook v1.9)
import com.foxybook.app.features.reader.ReaderState
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class PaginationManager {
    private val paginationJobMap = mutableMapOf<Int, Job>()
    private var lastPageRefreshTime = 0L
<<<<<<< HEAD
    private var totalBookPagesLocked = false
    private val MIN_CHAPTERS_FOR_LOCK = 3
=======

    /** Типографика, под которую последний раз свёрстана текущая книга. */
    private var lastPaginatedTypography: ReaderSettings? = null
>>>>>>> d24a20e (FoxyBook v1.9)

    fun updateDimensions(
        width: Int, height: Int, textMeasurer: TextMeasurer, density: Density,
        state: ReaderState
    ): ReaderState {
<<<<<<< HEAD
        if (state.pageWidth == width && state.pageHeight == height) {
            return state
        }
        Log.d("ReaderNav", "updateDimensions: width=$width, height=$height")
=======
        // Повторная вёрстка нужна не только при смене размера страницы, но и при
        // изменении типографики: иначе страницы остаются старой вёрстки, а текст
        // рисуется новым шрифтом (обрезается и не лечится пользователем).
        val current = state.settings
        val typographyChanged = lastPaginatedTypography?.let {
            it.fontSize != current.fontSize ||
                it.lineHeight != current.lineHeight ||
                it.margins != current.margins
        } ?: true
        val sizeChanged = state.pageWidth != width || state.pageHeight != height

        if (!sizeChanged && !typographyChanged) return state

        if (sizeChanged || typographyChanged) {
            lastPaginatedTypography = current
        }
        Log.d("ReaderNav", "updateDimensions: width=$width, height=$height, sizeChanged=$sizeChanged, typographyChanged=$typographyChanged")
>>>>>>> d24a20e (FoxyBook v1.9)
        return state.copy(
            pageWidth = width,
            pageHeight = height,
            textMeasurer = textMeasurer,
            density = density,
            paginationVersion = state.paginationVersion + 1
        )
    }

    fun paginateChapter(
        index: Int, state: ReaderState, scope: CoroutineScope,
        getBlocks: suspend (Int) -> List<ContentBlock>,
        stateFlow: MutableStateFlow<ReaderState>? = null
    ) {
        val book = state.book ?: return
        if (index < 0 || index >= book.chapters.size) return
        if (state.pageWidth <= 0 || state.pageHeight <= 0) return

        val cachedVersion = state.pageVersions.getOrDefault(index, -1)
        if (state.chapterPages.containsKey(index) && cachedVersion == state.paginationVersion) return

        Log.d("ReaderNav", "PAGINATION START: $index")
        paginationJobMap[index]?.cancel()
        paginationJobMap[index] = scope.launch(Dispatchers.Default) {
            val blocks = getBlocks(index)

            // Если блоки ещё не загружены — не создаём пустые страницы
            if (blocks.isEmpty()) {
                Log.d("ReaderNav", "PAGINATION SKIP $index: blocks not loaded yet")
                return@launch
            }

            val pages = TextPaginator.paginate(
                blocks = blocks,
                chapterIndex = index,
                pageWidthPx = state.pageWidth,
                pageHeightPx = state.pageHeight,
                settings = state.settings,
                textMeasurer = state.textMeasurer ?: return@launch,
                density = state.density ?: return@launch
            )
            Log.d("ReaderNav", "PAGINATION DONE: $index (pages=${pages.size})")

            if (stateFlow != null) {
                stateFlow.update {
                    val newPages = it.chapterPages + (index to pages)
                    val isCurrentChapter = index == it.currentChapter
<<<<<<< HEAD
=======
                    val newPageCount = if (pages.isNotEmpty()) pages.size else 0
                    val clampedCurrent = if (isCurrentChapter && newPageCount > 0) {
                        it.pageCurrent.coerceIn(0, newPageCount - 1)
                    } else {
                        it.pageCurrent
                    }
>>>>>>> d24a20e (FoxyBook v1.9)
                    it.copy(
                        chapterPages = newPages,
                        pageVersions = it.pageVersions + (index to it.paginationVersion),
                        pageTotal = if (isCurrentChapter && pages.isNotEmpty()) pages.size else it.pageTotal,
<<<<<<< HEAD
                        pageCurrent = if (isCurrentChapter && pages.isNotEmpty() && it.pageCurrent == 0 && pages.size == 1) 0 else it.pageCurrent
=======
                        pageCurrent = clampedCurrent
>>>>>>> d24a20e (FoxyBook v1.9)
                    )
                }
                // Не обновляем positionRestored/lastPositionRestoreTrigger здесь —
                // они устанавливаются только в loadBook/goToChapter. Иначе
                // LaunchedEffect в ReaderPageMode/ScrollMode будет постоянно
                // перезапускаться и мотать пейджер обратно к сохранённой позиции.
                // Сбрасываем флаг расчёта и обновляем оценку общего числа страниц
                refreshTotalBookPages(stateFlow.value, stateFlow)
            }
        }
    }

    suspend fun paginateChapterSync(
        index: Int, state: ReaderState, stateFlow: MutableStateFlow<ReaderState>,
        getBlocks: suspend (Int) -> List<ContentBlock>
    ) {
        val book = state.book ?: return
        if (index < 0 || index >= book.chapters.size) return
        if (state.pageWidth <= 0 || state.pageHeight <= 0) return
<<<<<<< HEAD
        if (state.chapterPages.containsKey(index)) return

        Log.d("ReaderNav", "PAGINATION SYNC START: $index")
        val blocks = getBlocks(index)
=======
        val cachedVersion = state.pageVersions.getOrDefault(index, -1)
        if (state.chapterPages.containsKey(index) && cachedVersion == state.paginationVersion) return

        Log.d("ReaderNav", "PAGINATION SYNC START: $index")
        val blocks = getBlocks(index)
        if (blocks.isEmpty()) return
>>>>>>> d24a20e (FoxyBook v1.9)
        val pages = TextPaginator.paginate(
            blocks = blocks,
            chapterIndex = index,
            pageWidthPx = state.pageWidth,
            pageHeightPx = state.pageHeight,
            settings = state.settings,
            textMeasurer = state.textMeasurer ?: return,
            density = state.density ?: return
        )
        Log.d("ReaderNav", "PAGINATION SYNC DONE: $index (pages=${pages.size})")

        stateFlow.update {
<<<<<<< HEAD
            val currentChapter = it.currentChapter
            val chaptersToKeep = (currentChapter - 10..currentChapter + 10)
            val filteredPages = it.chapterPages.filterKeys { key -> key in chaptersToKeep }
            it.copy(chapterPages = filteredPages + (index to pages))
        }
    }

=======
            val newPages = it.chapterPages + (index to pages)
            val isCurrentChapter = index == it.currentChapter
            val newPageCount = if (pages.isNotEmpty()) pages.size else 0
            val clampedCurrent = if (isCurrentChapter && newPageCount > 0) {
                it.pageCurrent.coerceIn(0, newPageCount - 1)
            } else {
                it.pageCurrent
            }
            it.copy(
                chapterPages = newPages,
                pageCurrent = clampedCurrent
            )
        }
    }

    /** Сбрасывает состояние — вызывать при загрузке новой книги. */
    fun resetLock() {
        lastPageRefreshTime = 0L
    }

>>>>>>> d24a20e (FoxyBook v1.9)
    fun ensurePaginated(
        chapterIndex: Int, state: ReaderState, scope: CoroutineScope,
        getBlocks: suspend (Int) -> List<ContentBlock>,
        stateFlow: MutableStateFlow<ReaderState>? = null
    ) {
        val textMeasurer = state.textMeasurer
        val density = state.density
        val cachedVersion = state.pageVersions.getOrDefault(chapterIndex, -1)

        if (textMeasurer != null && density != null &&
            state.pageWidth > 0 && state.pageHeight > 0 &&
            !(state.chapterPages.containsKey(chapterIndex) && cachedVersion == state.paginationVersion)
        ) {
            Log.d("ReaderNav", "ensurePaginated: Requesting pagination for chapter $chapterIndex")
            paginateChapter(chapterIndex, state, scope, getBlocks, stateFlow)
        }
    }

    fun refreshTotalBookPages(state: ReaderState, stateFlow: MutableStateFlow<ReaderState>): ReaderState {
        val now = System.currentTimeMillis()
        if (now - lastPageRefreshTime < 800 && !state.isCalculatingPages) return state
        lastPageRefreshTime = now

<<<<<<< HEAD
        // Если оценка уже заблокирована — просто сбрасываем флаг
        if (totalBookPagesLocked) {
            stateFlow.update { it.copy(isCalculatingPages = false) }
            return state
        }

=======
>>>>>>> d24a20e (FoxyBook v1.9)
        val book = state.book ?: run {
            stateFlow.update { it.copy(isCalculatingPages = false) }
            return state
        }

        // Если страниц ещё нет — сбрасываем флаг, чтобы убрать "..."
        if (state.chapterPages.isEmpty()) {
            stateFlow.update { it.copy(isCalculatingPages = false) }
            return state
        }

        var totalChars = 0L
        var totalPages = 0
        var paginatedChapters = 0

        for ((chIndex, pages) in state.chapterPages) {
            totalPages += pages.size
            paginatedChapters++
            if (chIndex < state.chapterLengths.size) {
                totalChars += state.chapterLengths[chIndex]
            }
        }

        if (totalPages == 0) {
            stateFlow.update { it.copy(isCalculatingPages = false) }
            return state
        }

        val bookPages = if (totalChars > 0) {
<<<<<<< HEAD
            val avgCharsPerPage = totalChars.toDouble() / totalPages
            (state.totalContentLength / avgCharsPerPage).toInt().coerceAtLeast(totalPages)
        } else {
            (totalPages * book.chapters.size.toDouble() / paginatedChapters).toInt().coerceAtLeast(totalPages)
=======
            // Если свёрстаны все главы — точная сумма страниц, иначе оценка по
            // среднему числу знаков на страницу.
            if (paginatedChapters >= book.chapters.size) {
                totalPages
            } else {
                val avgCharsPerPage = totalChars.toDouble() / totalPages
                (state.totalContentLength / avgCharsPerPage).toInt().coerceAtLeast(totalPages)
            }
        } else {
            (totalPages.toDouble() * book.chapters.size / paginatedChapters).toInt().coerceAtLeast(totalPages)
>>>>>>> d24a20e (FoxyBook v1.9)
        }

        val updated = state.copy(totalBookPages = bookPages, isCalculatingPages = false)
        stateFlow.update { updated }

<<<<<<< HEAD
        // Блокируем после набора достаточного количества данных
        if (paginatedChapters >= MIN_CHAPTERS_FOR_LOCK || paginatedChapters >= book.chapters.size) {
            Log.d("ReaderNav", "Total book pages locked at $bookPages (after $paginatedChapters chapters)")
            totalBookPagesLocked = true
        }

=======
>>>>>>> d24a20e (FoxyBook v1.9)
        return updated
    }

    fun cancelAllJobs() {
        paginationJobMap.values.forEach { it.cancel() }
        paginationJobMap.clear()
    }
}

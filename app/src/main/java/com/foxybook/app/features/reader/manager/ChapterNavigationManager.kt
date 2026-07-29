package com.foxybook.app.features.reader.manager

import android.util.Log
import com.foxybook.app.core.database.BookDataRepository
import com.foxybook.app.core.reader.BookParser
import com.foxybook.app.core.reader.ContentBlock
import com.foxybook.app.core.reader.HtmlBlockParser
import com.foxybook.app.features.reader.ReaderState
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class ChapterNavigationManager(
    private val bookParser: BookParser,
    private val bookDataRepository: BookDataRepository
) {
    private var bookId: Int = -1
    private var format: String = ""
    private var scopeJob: Job? = null
    private var scopeJob2: Job? = null

    fun setBookInfo(bookId: Int, format: String) {
        this.bookId = bookId
        this.format = format
    }

    fun getBookId() = bookId
    fun getFormat() = format

    /**
     * Загружает книгу, возвращает обновлённый state.
     */
    suspend fun loadBook(
        filePath: String, bId: Int, fmt: String,
        state: ReaderState, stateFlow: MutableStateFlow<ReaderState>
    ): ReaderState {
        Log.d("ReaderNav", "loadBook: path=$filePath, id=$bId, format=$fmt")
        if (state.book?.filePath == filePath && !state.isLoading) {
            Log.d("ReaderNav", "loadBook: Book already loaded, skipping")
            return state
        }

        bookId = bId
        format = fmt
        stateFlow.update { it.copy(isLoading = true, error = null) }

        Log.d("ReaderNav", "loadBook: STEP 1 - parsing book")
        val book = try {
            bookParser.parse(filePath, fmt, bId)
        } catch (e: Exception) {
            Log.e("ReaderNav", "loadBook: parse failed", e)
            stateFlow.update { it.copy(isLoading = false, error = "Ошибка открытия книги: ${e.localizedMessage ?: "неизвестная ошибка"}") }
            return stateFlow.value
        }

        if (book == null) {
            stateFlow.update { it.copy(isLoading = false, error = "Ошибка открытия книги") }
            return stateFlow.value
        }
        Log.d("ReaderNav", "loadBook: STEP 2 - parsed OK, chapters=${book.chapters.size}")

        stateFlow.update { it.copy(book = book) }

        // Precompute text content lengths per chapter
        val chapterLengths = book.chapters.map { chapter ->
            chapter.htmlContent.replace(Regex("<[^>]*>"), "").replace(Regex("\\s+"), " ").trim().length
        }
        val totalContentLength = chapterLengths.sumOf { it.toLong() }
        val initialPageEstimate = if (totalContentLength > 0)
            (totalContentLength / 600).toInt().coerceAtLeast(1) else 0
        stateFlow.update { it.copy(chapterLengths = chapterLengths, totalContentLength = totalContentLength, totalBookPages = initialPageEstimate) }

        // Load bookmarks
        scopeJob?.cancel()
        scopeJob = CoroutineScope(SupervisorJob() + Dispatchers.IO).launch {
            try {
                bookDataRepository.getBookmarksForBook(bId).collect { list ->
                    stateFlow.update { it.copy(bookmarks = list) }
                }
            } catch (e: Exception) {
                Log.e("ReaderNav", "loadBook: bookmarks flow failed", e)
            }
        }

        Log.d("ReaderNav", "loadBook: STEP 3 - restoring position")
        // Restore position
        val pos = bookDataRepository.getReadingPosition(bId, fmt).first()
        if (pos != null && pos.chapterIndex < book.chapters.size) {
            stateFlow.update {
                it.copy(
                    currentChapter = pos.chapterIndex,
                    textOffset = pos.textOffset,
                    scrollY = pos.scrollPosition,
                    scrollOffset = pos.scrollOffset,
                    pageCurrent = pos.pageIndex,
                    positionRestored = true,
                    lastPositionRestoreTrigger = System.currentTimeMillis()
                )
            }
            Log.d("ReaderNav", "loadBook: STEP 4 - loading chapter ${pos.chapterIndex}")
            getBlocksSync(pos.chapterIndex, stateFlow.value, stateFlow)
            // Если сохранённая глава пустая — переключаемся на первую непустую
            val chBlocks = stateFlow.value.chapterBlocks[pos.chapterIndex]
            if (chBlocks.isNullOrEmpty()) {
                skipPastEmptyChapters(pos.chapterIndex, stateFlow)
            }
        } else {
            stateFlow.update { it.copy(currentChapter = 0, positionRestored = true) }
            Log.d("ReaderNav", "loadBook: STEP 4 - loading chapter 0")
            getBlocksSync(0, stateFlow.value, stateFlow)
            skipPastEmptyChapters(0, stateFlow)
        }

        Log.d("ReaderNav", "loadBook: STEP 5 - setting isLoading=false")
        stateFlow.update { it.copy(isLoading = false) }

        // Preload all other chapters in background
        scopeJob2?.cancel()
        scopeJob2 = CoroutineScope(Dispatchers.IO).launch {
            try {
                val totalChapters = book.chapters.size
                val currentCh = stateFlow.value.currentChapter
                for (i in 0 until totalChapters) {
                    if (i == currentCh) continue
                    getBlocksSync(i, stateFlow.value, stateFlow)
                }
            } catch (e: Exception) {
                Log.e("ReaderNav", "loadBook: preload background failed", e)
            }
        }

        return stateFlow.value
    }

    fun goToChapter(index: Int, state: ReaderState, stateFlow: MutableStateFlow<ReaderState>): ReaderState {
        val book = state.book ?: return state
        if (index < 0 || index >= book.chapters.size) return state

        stateFlow.update {
            it.copy(
                currentChapter = index,
                scrollY = 0,
                pageCurrent = 0,
                textOffset = 0,
                scrollOffset = 0
            )
        }
        return stateFlow.value
    }

    fun nextChapter(state: ReaderState): Int {
        val next = state.currentChapter + 1
        val book = state.book ?: return -1
        return if (next < book.chapters.size) next else -1
    }

    fun previousChapter(state: ReaderState): Int {
        val prev = state.currentChapter - 1
        return if (prev >= 0) prev else -1
    }

    fun skipPastEmptyChapters(startChapter: Int, stateFlow: MutableStateFlow<ReaderState>): Int {
        val book = stateFlow.value.book ?: return startChapter
        var current = startChapter
        while (current < book.chapters.size) {
            val blocks = stateFlow.value.chapterBlocks[current]
            if (blocks != null && blocks.isNotEmpty()) {
                if (current != startChapter) {
                    Log.d("ReaderNav", "Skipped empty chapter $startChapter, moving to chapter $current")
                    stateFlow.update {
                        it.copy(
                            currentChapter = current,
                            textOffset = 0, scrollY = 0, pageCurrent = 0, scrollOffset = 0,
                            positionRestored = true,
                            lastPositionRestoreTrigger = System.currentTimeMillis()
                        )
                    }
                }
                return current
            }
            current++
        }
        return startChapter
    }

    fun preloadChapter(index: Int, state: ReaderState, scope: CoroutineScope) {
        val book = state.book ?: return
        if (index < 0 || index >= book.chapters.size) return
        if (state.chapterBlocks.containsKey(index)) return

        scope.launch(Dispatchers.IO) {
            getBlocksSync(index, state, null)
        }
    }

    suspend fun getBlocksSync(
        chapterIndex: Int,
        state: ReaderState,
        stateFlow: MutableStateFlow<ReaderState>?
    ): List<ContentBlock> {
        val cached = state.chapterBlocks[chapterIndex]
        if (cached != null) return cached

        val book = state.book ?: return emptyList()
        val chapter = book.chapters.getOrNull(chapterIndex) ?: return emptyList()

        Log.d("ReaderNav", "START LOAD CHAPTER: $chapterIndex")
        return withContext(Dispatchers.IO) {
            val html = if (chapter.htmlContent.isBlank() && chapter.contentId.isNotBlank()) {
                bookParser.loadChapterContent(book.filePath, chapter, bookId, format)
            } else {
                chapter.htmlContent
            }

            val blocks = HtmlBlockParser.parse(html)
            Log.d("ReaderNav", "LOAD CHAPTER SUCCESS: $chapterIndex, blocks count: ${blocks.size}")

            val extractedTitle = HtmlBlockParser.extractFirstTitle(blocks)

            stateFlow?.update {
                val titles = it.chapterTitlesExtracted.toMutableMap()
                if (extractedTitle != null) {
                    titles[chapterIndex] = extractedTitle
                } else if (blocks.isEmpty()) {
                    titles[chapterIndex] = ""
                }
                it.copy(
                    chapterBlocks = it.chapterBlocks + (chapterIndex to blocks),
                    chapterPages = it.chapterPages,
                    chapterTitlesExtracted = titles
                )
            }
            blocks
        }
    }

    fun cancelPendingJobs() {
        scopeJob?.cancel()
        scopeJob2?.cancel()
    }
}

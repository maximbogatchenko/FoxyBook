package com.foxybook.app.features.reader.manager

import com.foxybook.app.core.reader.ContentBlock
import com.foxybook.app.features.reader.ReaderState
import com.foxybook.app.features.reader.SearchResult
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class SearchManager {

    private var searchJob: kotlinx.coroutines.Job? = null

    fun performSearch(
        query: String,
        state: ReaderState,
        getBlocks: suspend (Int) -> List<ContentBlock>,
        stateFlow: MutableStateFlow<ReaderState>
    ) {
        // Отменяем предыдущий поиск, чтобы не было гонки
        searchJob?.cancel()

        stateFlow.update { it.copy(searchQuery = query, searchResults = emptyList(), searchCurrentIndex = -1, searchIsSearching = true) }
        if (query.isBlank()) {
            stateFlow.update { it.copy(searchIsSearching = false) }
            return
        }

        searchJob = kotlinx.coroutines.CoroutineScope(Dispatchers.IO + kotlinx.coroutines.SupervisorJob()).launch {
            try {
                val book = state.book ?: return@launch
                val results = mutableListOf<SearchResult>()

                for (chapterIndex in book.chapters.indices) {
                    // Проверяем отмену на каждой главе
                    if (!isActive) return@launch

                    val blocks = try {
                        getBlocks(chapterIndex)
                    } catch (e: Exception) {
                        android.util.Log.e("SearchManager", "getBlocks error chapter $chapterIndex", e)
                        continue
                    }
                    val chapterTitle = book.chapters[chapterIndex].title.ifBlank {
                        state.chapterTitlesExtracted[chapterIndex] ?: "Глава ${chapterIndex + 1}"
                    }

                    for (blockIndex in blocks.indices) {
                        val text = blocks[blockIndex].getTextContent()
                        var startIndex = text.indexOf(query, ignoreCase = true)
                        while (startIndex >= 0) {
                            val endIndex = startIndex + query.length
                            results.add(
                                SearchResult(
                                    text = text,
                                    chapterIndex = chapterIndex,
                                    blockIndex = blockIndex,
                                    matchStart = startIndex,
                                    matchEnd = endIndex,
                                    chapterTitle = chapterTitle
                                )
                            )
                            startIndex = text.indexOf(query, startIndex + 1, ignoreCase = true)
                        }
                    }
                }

                if (!isActive) return@launch

                stateFlow.update {
                    it.copy(
                        searchResults = results,
                        searchCurrentIndex = -1,
                        searchIsSearching = false
                    )
                }

                // Не переходим к результату автоматически — ждём нажатия пользователя
            } catch (e: Exception) {
                android.util.Log.e("SearchManager", "search error", e)
                stateFlow.update { it.copy(searchIsSearching = false) }
            }
        }
    }

    suspend fun goToSearchResult(
        index: Int,
        state: ReaderState,
        stateFlow: MutableStateFlow<ReaderState>,
        getBlocks: suspend (Int) -> List<ContentBlock>
    ) {
        val results = state.searchResults
        if (index < 0 || index >= results.size) return

        stateFlow.update { it.copy(searchCurrentIndex = index) }
        val result = results[index]

        try {
            withContext(Dispatchers.IO) {
                try { getBlocks(result.chapterIndex) } catch (_: Exception) { }

                val mode = com.foxybook.app.core.models.ReaderMode.safeValueOf(state.settings.readerMode)
                if (mode == com.foxybook.app.core.models.ReaderMode.HORIZONTAL) {
                    val pages = state.chapterPages[result.chapterIndex]
                    if (pages != null) {
                        var offset = 0
                        var targetPage = 0
                        val blocks = try { getBlocks(result.chapterIndex) } catch (_: Exception) { emptyList() }
                        for (i in blocks.indices) {
                            val blockLen = blocks[i].getTextContent().length
                            if (i == result.blockIndex) {
                                targetPage = pages.indexOfFirst { it.startOffset <= offset && offset < it.endOffset }
                                break
                            }
                            offset += blockLen
                        }
                        val pageIndex = targetPage.coerceAtLeast(0)

                        withContext(Dispatchers.Main) {
                            stateFlow.update {
                                it.copy(
                                    currentChapter = result.chapterIndex,
                                    pageCurrent = pageIndex,
                                    textOffset = offset,
                                    positionRestored = true,
                                    lastPositionRestoreTrigger = System.currentTimeMillis()
                                )
                            }
                        }
                    } else {
                        withContext(Dispatchers.Main) {
                            stateFlow.update {
                                it.copy(
                                    currentChapter = result.chapterIndex,
                                    positionRestored = true,
                                    lastPositionRestoreTrigger = System.currentTimeMillis()
                                )
                            }
                        }
                    }
                } else {
                    withContext(Dispatchers.Main) {
                        stateFlow.update {
                            it.copy(
                                currentChapter = result.chapterIndex,
                                scrollY = result.blockIndex,
                                scrollOffset = 0,
                                textOffset = result.matchStart,
                                positionRestored = true,
                                lastPositionRestoreTrigger = System.currentTimeMillis()
                            )
                        }
                    }
                }

                // Preload adjacent chapters
                withContext(Dispatchers.Main) {
                    stateFlow.update {
                        it.copy(
                            currentChapter = result.chapterIndex,
                            positionRestored = true,
                            lastPositionRestoreTrigger = System.currentTimeMillis()
                        )
                    }
                }
            }
        } catch (e: Exception) {
            android.util.Log.e("SearchManager", "goToSearchResult error", e)
        }
    }

    fun navigateSearchResults(direction: Int, state: ReaderState, stateFlow: MutableStateFlow<ReaderState>,
        getBlocks: suspend (Int) -> List<ContentBlock>
    ) {
        val results = state.searchResults
        if (results.isEmpty()) return
        val newIndex = (state.searchCurrentIndex + direction).coerceIn(0, results.size - 1)
        kotlinx.coroutines.CoroutineScope(Dispatchers.Main + kotlinx.coroutines.SupervisorJob()).launch {
            try {
                goToSearchResult(newIndex, state, stateFlow, getBlocks)
            } catch (e: Exception) {
                android.util.Log.e("SearchManager", "navigateSearchResults error", e)
            }
        }
    }
}

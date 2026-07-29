package com.foxybook.app.features.reader

import android.util.Log
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Density
import com.foxybook.app.R
import androidx.compose.ui.unit.dp
import androidx.compose.ui.text.TextMeasurer
import com.foxybook.app.core.models.ReaderSettings
import com.foxybook.app.core.reader.TextPaginator
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.collect

private data class GlobalPage(
    val page: TextPaginator.Page,
    val chapterIndex: Int,
    val pageIndexInChapter: Int
)

@Composable
fun PageModeContent(
    viewModel: ReaderViewModel,
    state: ReaderState,
    settings: ReaderSettings,
    colors: ReaderColors,
    contentWidthPx: Int,
    contentHeightPx: Int,
    textMeasurer: TextMeasurer,
    density: Density,
    topPadDp: androidx.compose.ui.unit.Dp = 0.dp,
    onToggleImmersive: () -> Unit
) {
    LaunchedEffect(contentWidthPx, contentHeightPx, settings.fontSize, settings.lineHeight, settings.margins) {
        Log.d("ReaderNav", "PageMode: Dimensions changed")
        viewModel.onEvent(ReaderEvent.UpdatePageDimensions(contentWidthPx, contentHeightPx, textMeasurer, density))
    }

    val book = state.book ?: return

    var globalPages by remember { mutableStateOf(emptyList<GlobalPage>()) }

    LaunchedEffect(state.chapterPages) {
        globalPages = buildList {
            val paginatedChapters = state.chapterPages.keys.sorted()

            paginatedChapters.forEach { chIdx ->
                val pages = state.chapterPages[chIdx] ?: emptyList()
                pages.forEachIndexed { pIdx, page ->
                    add(GlobalPage(page, chIdx, pIdx))
                }
            }

            Log.d("ReaderNav", "PageMode: Built ${size} pages from chapters $paginatedChapters")
        }
    }

    LaunchedEffect(Unit) {
        snapshotFlow { if (globalPages.isEmpty()) -1 else globalPages[kotlin.math.min(state.pageCurrent, globalPages.size - 1)].chapterIndex }
            .collect { visibleChapter ->
                if (visibleChapter >= 0) {
                    for (i in 1..3) {
                        val nextChapter = visibleChapter + i
                        if (nextChapter < book.chapters.size) {
                            if (!state.chapterBlocks.containsKey(nextChapter)) {
                                Log.d("ReaderNav", "PageMode: Loading chapter $nextChapter")
                                viewModel.getBlocks(nextChapter)
                            }
                            viewModel.ensurePaginated(nextChapter)
                        }
                    }
                }
            }
    }

    LaunchedEffect(state.currentChapter) {
        for (i in 0..3) {
            val chIdx = state.currentChapter + i
            if (chIdx < book.chapters.size) {
                if (!state.chapterBlocks.containsKey(chIdx)) {
                    viewModel.getBlocks(chIdx)
                }
                viewModel.ensurePaginated(chIdx)
            }
        }
    }

    // Retry pagination when blocks load for chapters that don't have pages yet
    LaunchedEffect(state.chapterBlocks) {
        for ((chIdx, blocks) in state.chapterBlocks) {
            if (blocks.isNotEmpty() && !state.chapterPages.containsKey(chIdx)) {
                Log.d("ReaderNav", "PageMode: Retry pagination for ch$chIdx after blocks loaded")
                viewModel.ensurePaginated(chIdx)
            }
        }
    }

    if (globalPages.isEmpty()) {
        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            CircularProgressIndicator()
        }
        return
    }

    val initialPage = remember(globalPages) {
        val idx = globalPages.indexOfFirst {
            it.chapterIndex == state.currentChapter &&
            (state.textOffset in it.page.startOffset until it.page.endOffset ||
             (it.pageIndexInChapter == 0 && state.textOffset == 0))
        }.coerceAtLeast(globalPages.indexOfFirst { it.chapterIndex == state.currentChapter })

        if (idx >= 0) idx else 0
    }

    val pagerState = rememberPagerState(
        initialPage = initialPage,
        pageCount = { globalPages.size.coerceAtLeast(1) }
    )

    var isRestoring by remember { mutableStateOf(true) }
    var lastHandledRestoreTrigger by remember { mutableLongStateOf(-1L) }

    LaunchedEffect(state.lastPositionRestoreTrigger, globalPages) {
        if (globalPages.isEmpty() || state.lastPositionRestoreTrigger == lastHandledRestoreTrigger) {
            isRestoring = false
            return@LaunchedEffect
        }

        val targetIdx = globalPages.indexOfFirst {
            it.chapterIndex == state.currentChapter &&
            (state.textOffset in it.page.startOffset until it.page.endOffset ||
             (it.pageIndexInChapter == 0 && state.textOffset == 0))
        }.let { if (it == -1) globalPages.indexOfFirst { p -> p.chapterIndex == state.currentChapter } else it }

        if (targetIdx >= 0) {
            Log.d("ReaderNav", "PageMode: Restoring to page $targetIdx (ch=${state.currentChapter}, offset=${state.textOffset})")
            isRestoring = true
            pagerState.scrollToPage(targetIdx)
            lastHandledRestoreTrigger = state.lastPositionRestoreTrigger
            delay(250)
        }
        // Если targetIdx < 0 — не сохраняем lastHandledRestoreTrigger,
        // чтобы при дозагрузке глав LaunchedEffect сработал снова
        isRestoring = false
    }

    LaunchedEffect(pagerState, globalPages) {
        snapshotFlow {
            pagerState.currentPage to pagerState.isScrollInProgress
        }.collect { (page, isScrolling) ->
            if (!isScrolling && page >= 0 && page < globalPages.size) {
                val currentPage = globalPages[page]

                if (!isRestoring && currentPage.chapterIndex != state.currentChapter) {
                    Log.d("ReaderNav", "PageMode: Chapter changed to ${currentPage.chapterIndex} via swipe")
                    viewModel.onEvent(ReaderEvent.ChapterChanged(currentPage.chapterIndex, resetPosition = false))
                }

                val totalInChapter = globalPages.count { it.chapterIndex == currentPage.chapterIndex }.coerceAtLeast(1)
                viewModel.onEvent(ReaderEvent.PageInfo(
                    currentPage.pageIndexInChapter,
                    totalInChapter,
                    currentPage.page.startOffset
                ))
            }
        }
    }

    Box(modifier = Modifier.fillMaxSize()) {
        HorizontalPager(
            state = pagerState,
            modifier = Modifier.fillMaxSize(),
            beyondViewportPageCount = 2,
            key = { index ->
                val gp = globalPages.getOrNull(index)
                if (gp != null) "ch${gp.chapterIndex}_p${gp.pageIndexInChapter}" else "empty_$index"
            }
        ) { index ->
            val gp = globalPages.getOrNull(index)
            if (gp != null) {
                Box(modifier = Modifier.fillMaxSize()) {
                    val marginsDp = settings.margins.dp
                    Column(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(start = marginsDp, end = marginsDp, top = topPadDp),
                        verticalArrangement = Arrangement.Top
                    ) {
                        gp.page.blocks.forEach { block ->
                            BlockComposable(
                                block = block,
                                fontSize = settings.fontSize,
                                lineHeight = settings.lineHeight,
                                colors = colors,
                                isSelectionMode = state.isSelectingTtsStartPosition,
                                isCurrentTtsBlock = state.currentTtsChapter == gp.chapterIndex && state.currentTtsBlockIndex == block.originalIndex,
                                onTtsClick = {
                                    viewModel.onEvent(ReaderEvent.StartTts(gp.chapterIndex, block.originalIndex))
                                },
                                onToggleImmersive = onToggleImmersive
                            )
                        }
                    }
                    val chapterPageCount = globalPages.count { it.chapterIndex == gp.chapterIndex }
                    Text(
                        text = stringResource(R.string.reader_page_label, gp.chapterIndex + 1, gp.pageIndexInChapter + 1, chapterPageCount),
                        style = MaterialTheme.typography.labelSmall,
                        color = colors.textSecondary.copy(alpha = 0.4f),
                        modifier = Modifier.align(Alignment.BottomCenter).padding(bottom = 8.dp)
                    )
                }
            }
        }
    }
}

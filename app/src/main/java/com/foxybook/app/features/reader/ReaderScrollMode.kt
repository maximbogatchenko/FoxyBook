package com.foxybook.app.features.reader

import android.util.Log
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.CircularProgressIndicator
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
import com.foxybook.app.R
import androidx.compose.ui.unit.dp
import com.foxybook.app.core.models.ReaderSettings
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map

private data class GlobalBlock(
    val block: com.foxybook.app.core.reader.ContentBlock,
    val chapterIndex: Int,
    val blockIndexInChapter: Int,
    val offsetInChapter: Int
)

@Composable
fun ScrollModeContent(
    viewModel: ReaderViewModel,
    state: ReaderState,
    settings: ReaderSettings,
    colors: ReaderColors,
    onToggleImmersive: () -> Unit
) {
    val lazyListState = rememberLazyListState()
    val book = state.book ?: return

    val cutoutTopDp = 24.dp

    var globalBlocks by remember { mutableStateOf(emptyList<GlobalBlock>()) }
    val includedChapters = remember { mutableSetOf<Int>() }

    // Добавляем блоки только recién загруженных глав. Блоки внутри главы после
    // парсинга больше не меняются, поэтому полная пересборка всего списка при
    // каждой новой главе не нужна — это убирает O(n²) перерисовку LazyColumn.
    LaunchedEffect(state.chapterBlocks) {
        val loadedChapters = state.chapterBlocks.keys.sorted()
        val newChapters = loadedChapters.filter { it !in includedChapters }
        if (newChapters.isEmpty()) return@LaunchedEffect

        val appended = buildList {
            for (chIdx in newChapters) {
                val blocks = state.chapterBlocks[chIdx] ?: emptyList()
                var currentOffset = 0
                blocks.forEachIndexed { bIdx, block ->
                    add(GlobalBlock(block, chIdx, bIdx, currentOffset))
                    currentOffset += block.getTextContent().length
                }
            }
        }

        globalBlocks = globalBlocks + appended
        includedChapters += newChapters

        Log.d("ReaderNav", "ScrollMode: Appended ${appended.size} blocks from chapters $newChapters (total ${globalBlocks.size})")
    }

    LaunchedEffect(Unit) {
        snapshotFlow { lazyListState.firstVisibleItemIndex }
            .collect { index ->
                if (index >= 0 && index < globalBlocks.size) {
                    val currentBlock = globalBlocks[index]
                    val chapterIndex = currentBlock.chapterIndex

                    for (i in 1..3) {
                        val nextChapter = chapterIndex + i
                        if (nextChapter < book.chapters.size && !state.chapterBlocks.containsKey(nextChapter)) {
                            Log.d("ReaderNav", "ScrollMode: Loading chapter $nextChapter")
                            viewModel.getBlocks(nextChapter)
                        }
                    }
                }
            }
    }

    LaunchedEffect(lazyListState) {
        snapshotFlow {
            val idx = lazyListState.firstVisibleItemIndex
            val block = globalBlocks.getOrNull(idx)
            Pair(idx, block?.chapterIndex ?: -1)
        }.collect { (idx, chIdx) ->
            if (chIdx >= 0 && idx + 20 < globalBlocks.size) {
                val currentChapterBlocks = globalBlocks.filter { it.chapterIndex == chIdx }
                val currentBlockInChapter = globalBlocks.indexOfFirst { it.chapterIndex == chIdx && it.blockIndexInChapter == globalBlocks[idx].blockIndexInChapter }
                val chapterBlockCount = currentChapterBlocks.size
                if (currentBlockInChapter > chapterBlockCount * 2 / 3) {
                    val nextCh = chIdx + 1
                    if (nextCh < book.chapters.size && !state.chapterBlocks.containsKey(nextCh)) {
                        Log.d("ReaderNav", "ScrollMode: Early loading chapter $nextCh (near end of ch$chIdx)")
                        viewModel.getBlocks(nextCh)
                    }
                }
            }
        }
    }

    LaunchedEffect(state.currentChapter) {
        for (i in 0..3) {
            val chIdx = state.currentChapter + i
            if (chIdx < book.chapters.size && !state.chapterBlocks.containsKey(chIdx)) {
                viewModel.getBlocks(chIdx)
            }
        }
    }

    var isRestoring by remember { mutableStateOf(true) }
    var lastHandledRestoreTrigger by remember { mutableLongStateOf(-1L) }

    LaunchedEffect(state.lastPositionRestoreTrigger, globalBlocks) {
        if (globalBlocks.isEmpty()) {
            isRestoring = false
            return@LaunchedEffect
        }

        val triggerChanged = state.lastPositionRestoreTrigger != lastHandledRestoreTrigger

        // Если блоков для текущей главы стало больше, но мы ещё не восстановили позицию — пробуем снова.
        // Если триггер не менялся и мы уже восстанавливались — выходим.
        if (!triggerChanged && lastHandledRestoreTrigger == state.lastPositionRestoreTrigger) {
            isRestoring = false
            return@LaunchedEffect
        }

        val targetIdx = findRestoreIndex(
            items = globalBlocks.map {
                RestoreAnchor(it.chapterIndex, it.offsetInChapter, it.blockIndexInChapter)
            },
            chapterIndex = state.currentChapter,
            offset = state.textOffset,
            savedIndexInChapter = state.scrollY
        )

        if (targetIdx >= 0) {
            Log.d("ReaderNav", "ScrollMode: Restoring to index $targetIdx (block=${state.scrollY})")
            isRestoring = true
            // Сначала скроллим к элементу без смещения, чтобы элементы замерились,
            // затем применяем точное пиксельное смещение
            lazyListState.scrollToItem(targetIdx)
            delay(100)
            lazyListState.scrollToItem(targetIdx, state.scrollOffset)
            lastHandledRestoreTrigger = state.lastPositionRestoreTrigger

            // Ждём, пока список реально дойдёт до целевого элемента, и только тогда
            // снимаем флаг восстановления.
            var waited = 0L
            while (waited < 2000L && lazyListState.firstVisibleItemIndex != targetIdx) {
                delay(100)
                waited += 100
            }
        }
        // Если targetIdx < 0 — не сохраняем lastHandledRestoreTrigger,
        // чтобы при дозагрузке глав LaunchedEffect сработал снова
        isRestoring = false

        // После восстановления фиксируем фактическую позицию в базе
        val saveIdx = if (targetIdx >= 0 && targetIdx < globalBlocks.size) targetIdx else lazyListState.firstVisibleItemIndex
        if (saveIdx >= 0 && saveIdx < globalBlocks.size) {
            val block = globalBlocks[saveIdx]
            val chBlocks = globalBlocks.filter { pg -> pg.chapterIndex == block.chapterIndex }
            val totalChars = chBlocks.sumOf { it.block.getTextContent().length }
            val charPct = if (totalChars > 0) (block.offsetInChapter * 100 / totalChars).coerceIn(0, 100) else 0
            viewModel.onEvent(ReaderEvent.ScrollProgress(
                charPct,
                block.blockIndexInChapter,
                lazyListState.firstVisibleItemScrollOffset,
                block.offsetInChapter
            ))
        }
    }

    LaunchedEffect(lazyListState) {
        snapshotFlow {
            lazyListState.firstVisibleItemIndex
        }.collect { index ->
            if (!isRestoring && index >= 0 && index < globalBlocks.size) {
                val block = globalBlocks[index]

                if (block.chapterIndex != state.currentChapter) {
                    Log.d("ReaderNav", "ScrollMode: Chapter changed to ${block.chapterIndex} via scroll")
                    viewModel.onEvent(ReaderEvent.ChapterChanged(block.chapterIndex, resetPosition = false))
                }

                val chBlocks = globalBlocks.filter { it.chapterIndex == block.chapterIndex }
                val totalChars = chBlocks.sumOf { it.block.getTextContent().length }
                val charPct = if (totalChars > 0) (block.offsetInChapter * 100 / totalChars).coerceIn(0, 100) else 0
                viewModel.onEvent(ReaderEvent.ScrollProgress(
                    charPct,
                    block.blockIndexInChapter,
                    lazyListState.firstVisibleItemScrollOffset,
                    block.offsetInChapter
                ))
            }
        }
    }

    LazyColumn(
        state = lazyListState,
        contentPadding = PaddingValues(horizontal = settings.margins.dp, vertical = settings.margins.dp + cutoutTopDp),
        verticalArrangement = Arrangement.spacedBy(2.dp),
        modifier = Modifier.fillMaxSize()
    ) {
        items(count = globalBlocks.size, key = { index ->
            val gb = globalBlocks[index]
            "ch${gb.chapterIndex}_b${gb.blockIndexInChapter}"
        }) { index ->
            val gb = globalBlocks[index]
            BlockComposable(
                block = gb.block,
                fontSize = settings.fontSize,
                lineHeight = settings.lineHeight,
                colors = colors,
                isSelectionMode = state.isSelectingTtsStartPosition,
                isCurrentTtsBlock = state.currentTtsChapter == gb.chapterIndex && state.currentTtsBlockIndex == gb.blockIndexInChapter,
                onTtsClick = { viewModel.onEvent(ReaderEvent.StartTts(gb.chapterIndex, gb.blockIndexInChapter)) },
                onToggleImmersive = onToggleImmersive
            )
        }

        if (globalBlocks.isNotEmpty()) {
            val lastChapter = globalBlocks.last().chapterIndex
            if (lastChapter == book.chapters.size - 1) {
                item {
                    Spacer(modifier = Modifier.height(100.dp))
                    Text(
                        stringResource(R.string.reader_end),
                        modifier = Modifier.fillMaxWidth(),
                        textAlign = TextAlign.Center,
                        color = colors.textSecondary
                    )
                    Spacer(modifier = Modifier.height(100.dp))
                }
            }
        }
    }
}

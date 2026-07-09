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

    var globalBlocks by remember { mutableStateOf(emptyList<GlobalBlock>()) }

    LaunchedEffect(state.chapterBlocks) {
        globalBlocks = buildList {
            val loadedChapters = state.chapterBlocks.keys.sorted()

            loadedChapters.forEach { chIdx ->
                val blocks = state.chapterBlocks[chIdx] ?: emptyList()
                var currentOffset = 0
                blocks.forEachIndexed { bIdx, block ->
                    add(GlobalBlock(block, chIdx, bIdx, currentOffset))
                    currentOffset += block.getTextContent().length
                }
            }

            Log.d("ReaderNav", "ScrollMode: Built ${size} blocks from chapters $loadedChapters")
        }
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
        if (globalBlocks.isEmpty() || state.lastPositionRestoreTrigger == lastHandledRestoreTrigger) {
            isRestoring = false
            return@LaunchedEffect
        }

        val targetIdx = globalBlocks.indexOfFirst {
            it.chapterIndex == state.currentChapter &&
            state.textOffset in it.offsetInChapter until (it.offsetInChapter + it.block.getTextContent().length)
        }.let { if (it == -1) globalBlocks.indexOfFirst { gb -> gb.chapterIndex == state.currentChapter && gb.blockIndexInChapter == state.scrollY } else it }

        if (targetIdx >= 0) {
            Log.d("ReaderNav", "ScrollMode: Restoring to index $targetIdx (block=${state.scrollY})")
            isRestoring = true
            lazyListState.scrollToItem(targetIdx, state.scrollOffset)
            lastHandledRestoreTrigger = state.lastPositionRestoreTrigger
            delay(50)

            val block = globalBlocks.getOrNull(targetIdx)
            if (block != null) {
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
        } else {
            lastHandledRestoreTrigger = state.lastPositionRestoreTrigger
        }
        isRestoring = false
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
        contentPadding = PaddingValues(horizontal = settings.margins.dp, vertical = 16.dp),
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

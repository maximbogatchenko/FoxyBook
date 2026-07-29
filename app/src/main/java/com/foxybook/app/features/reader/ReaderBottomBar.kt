package com.foxybook.app.features.reader

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.foxybook.app.R
import com.foxybook.app.core.models.ReaderMode

@Composable
fun ReaderBottomBar(
    state: ReaderState,
    viewModel: ReaderViewModel,
    colors: ReaderColors,
    modifier: Modifier = Modifier
) {
    val book = state.book ?: return
    val chapter = book.chapters.getOrNull(state.currentChapter)
    val mode = ReaderMode.safeValueOf(state.settings.readerMode)

    Row(modifier = modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
        IconButton(onClick = { viewModel.onEvent(ReaderEvent.PreviousChapter) }, enabled = state.currentChapter > 0, modifier = Modifier.size(36.dp)) {
            Icon(Icons.Default.PlayArrow, stringResource(R.string.cd_previous), modifier = Modifier.size(24.dp).rotate(180f),
                tint = if (state.currentChapter > 0) MaterialTheme.colorScheme.primary else colors.text.copy(alpha = 0.2f))
        }
        Column(modifier = Modifier.weight(1f), horizontalAlignment = Alignment.CenterHorizontally) {
            Text(text = chapter?.title ?: "", style = MaterialTheme.typography.labelSmall, maxLines = 1, overflow = TextOverflow.Ellipsis, color = colors.text)
            Text(text = if (state.settings.showProgressAsPercentage) {
                "${state.readingPercentage}%"
            } else if (mode == ReaderMode.HORIZONTAL) {
                if (state.isCalculatingPages || state.totalBookPages == 0) {
                    "…"
                } else {
                    val pagesBefore = (0 until state.currentChapter).sumOf { ch ->
                        state.chapterPages[ch]?.size ?: 0
                    }
                    val bookPage = pagesBefore + state.pageCurrent + 1
                    val bookTotal = state.totalBookPages.coerceAtLeast(bookPage)
                    "$bookPage/$bookTotal"
                }
            } else {
                val blocks = state.chapterBlocks[state.currentChapter]
                val blockProgress = if (blocks != null && blocks.size > 1) {
                    (state.scrollY + 1).coerceIn(1, blocks.size)
                } else 1
                "$blockProgress/${blocks?.size ?: 1}"
            },
                style = MaterialTheme.typography.labelSmall, color = colors.text.copy(alpha = 0.6f))
        }
        IconButton(onClick = { viewModel.onEvent(ReaderEvent.NextChapter) }, enabled = state.currentChapter < book.chapters.size - 1, modifier = Modifier.size(36.dp)) {
            Icon(Icons.Default.PlayArrow, stringResource(R.string.cd_next), modifier = Modifier.size(24.dp),
                tint = if (state.currentChapter < book.chapters.size - 1) MaterialTheme.colorScheme.primary else colors.text.copy(alpha = 0.2f))
        }
    }
}

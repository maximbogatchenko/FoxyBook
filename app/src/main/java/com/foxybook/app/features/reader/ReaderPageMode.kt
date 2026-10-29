package com.foxybook.app.features.reader

import android.util.Log
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.PagerState
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.mutableIntStateOf
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

/**
 * Ищет страницу, на которой читатель остановился. Общий алгоритм —
 * см. [findRestoreIndex].
 */
private fun findRestorePageIndex(
    globalPages: List<GlobalPage>,
    chapterIndex: Int,
    textOffset: Int,
    savedPageIndex: Int
): Int {
    if (globalPages.isEmpty()) return -1
    // Не фильтруем заранее по главе — findRestoreIndex сам отфильтрует и
    // вернёт ГЛОБАЛЬНЫЙ индекс в списке globalPages. Если отфильтровать здесь,
    // withIndex() внутри findRestoreIndex даст индекс относительно отфильтрованного
    // списка, и scrollToPage(0) уйдёт на первую страницу книги, а не главы.
    val idx = findRestoreIndex(
        items = globalPages.map {
            RestoreAnchor(it.chapterIndex, it.page.startOffset, it.pageIndexInChapter)
        },
        chapterIndex = chapterIndex,
        offset = textOffset,
        savedIndexInChapter = savedPageIndex
    )
    return idx
}

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
    onToggleImmersive: () -> Unit
) {
    LaunchedEffect(contentWidthPx, contentHeightPx, settings.fontSize, settings.lineHeight, settings.margins) {
        Log.d("ReaderNav", "PageMode: Dimensions changed")
        viewModel.onEvent(ReaderEvent.UpdatePageDimensions(contentWidthPx, contentHeightPx, textMeasurer, density))
    }

    val book = state.book ?: return

    // globalPages вычисляется синхронно из chapterPages при каждом изменении,
    // без задержки LaunchedEffect. Это исключает скачки пейджера при дозагрузке
    // новых глав: список страниц обновляется в ту же рекомпозицию, в которой
    // меняется chapterPages.
    val globalPages = remember(state.chapterPages, book.chapters.size) {
        buildList {
            for (chIdx in book.chapters.indices) {
                val pages = state.chapterPages[chIdx] ?: emptyList()
                pages.forEachIndexed { pIdx, page ->
                    add(GlobalPage(page, chIdx, pIdx))
                }
            }
        }
    }

    LaunchedEffect(Unit) {
        snapshotFlow { if (globalPages.isEmpty()) -1 else globalPages[kotlin.math.min(state.pageCurrent, globalPages.size - 1)].chapterIndex }
            .collect { visibleChapter ->
                if (visibleChapter >= 0) {
                    for (i in 1..5) {
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
        for (i in 0..5) {
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

    // initialPage только для первоначальной установки позиции (смена главы,
    // реставрация). Не зависит от state.pageCurrent и globalPages.size — иначе
    // PagerState пересоздаётся при каждом фоновом дописывании страниц и
    // текст дергается при листовании.
    val initialPage = remember(state.currentChapter, state.lastPositionRestoreTrigger, globalPages.size) {
        if (globalPages.isEmpty()) 0
        else {
            val idx = findRestorePageIndex(
                globalPages = globalPages,
                chapterIndex = state.currentChapter,
                textOffset = state.textOffset,
                savedPageIndex = state.pageCurrent
            )
            if (idx >= 0) idx.coerceIn(0, globalPages.size - 1)
            else {
                // Глава ещё не свёрстана — сохранённую главу-относительную страницу
                // нельзя использовать как глобальный индекс, иначе пейджер откроет
                // страницу чужой главы. Считаем глобальную страницу: все страницы
                // предыдущих глав + страница внутри текущей.
                val pagesBefore = (0 until state.currentChapter).sumOf { ch ->
                    state.chapterPages[ch]?.size ?: 0
                }
                (pagesBefore + state.pageCurrent).coerceIn(0, globalPages.size - 1)
            }
        }
    }

    // PagerState пересоздаётся при изменении количества страниц, потому что
    // rememberPagerState вычисляет pageCount lambda только один раз при создании.
    // Без пересоздания pageCount застревает на начальном значении — при фоновой
    // достраничке новых глав пейджер думает, что страниц всего 1, и счётчик
    // / листование ломаются.
    var preservedPage by remember { mutableIntStateOf(0) }
    var pagerStateRef by remember { mutableStateOf<PagerState?>(null) }

    key(globalPages.size) {
        val pageCount = globalPages.size.coerceAtLeast(1)
        val initial = if (globalPages.isNotEmpty()) {
            initialPage.coerceIn(0, pageCount - 1)
        } else {
            preservedPage.coerceIn(0, pageCount - 1)
        }
        val pagerState = rememberPagerState(
            initialPage = initial,
            pageCount = { pageCount }
        )
        pagerStateRef = pagerState

        // Сохраняем текущую страницу, чтобы при пересоздании PagerState
        // (добавление страниц фоновой пагинацией) позиция чтения не терялась.
        LaunchedEffect(pagerState) {
            snapshotFlow { pagerState.currentPage }.collect { page ->
                preservedPage = page
            }
        }

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
            if (gp == null) {
                Box(modifier = Modifier.fillMaxSize())
            } else {
                val chapterIdx = gp.chapterIndex
                val pageIdx = gp.pageIndexInChapter
                val pageBlocks = gp.page.blocks
                Box(modifier = Modifier.fillMaxSize()) {
                    val marginsDp = settings.margins.dp
                    val cutoutDp = 24.dp
                    Column(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(
                                start = marginsDp,
                                end = marginsDp,
                                top = marginsDp + cutoutDp,
                                bottom = marginsDp + cutoutDp
                            ),
                        verticalArrangement = Arrangement.Top
                    ) {
                        for (block in pageBlocks) {
                            BlockComposable(
                                block = block,
                                fontSize = settings.fontSize,
                                lineHeight = settings.lineHeight,
                                colors = colors,
                                isSelectionMode = state.isSelectingTtsStartPosition,
                                isCurrentTtsBlock = state.currentTtsChapter == chapterIdx && state.currentTtsBlockIndex == block.originalIndex,
                                onTtsClick = {
                                    viewModel.onEvent(ReaderEvent.StartTts(chapterIdx, block.originalIndex))
                                },
                                onToggleImmersive = onToggleImmersive
                            )
                        }
                    }
                    val chapterPageCount = globalPages.count { pg -> pg.chapterIndex == chapterIdx }
                    Text(
                        text = stringResource(R.string.reader_page_label, chapterIdx + 1, pageIdx + 1, chapterPageCount),
                        style = MaterialTheme.typography.labelSmall,
                        color = colors.textSecondary.copy(alpha = 0.4f),
                        modifier = Modifier.align(Alignment.BottomCenter).padding(bottom = marginsDp + 4.dp)
                    )
                }
            }
        }
        }

    val pagerState = pagerStateRef ?: return

    // Когда глава меняется программно (из BookSheet), lastPositionRestoreTrigger
    // меняется и срабатывает эффект восстановления ниже. Свайп пользователя не
    // должен перебивать пейджер scrollToPage, поэтому этого эффекта здесь нет.

    var isRestoring by remember { mutableStateOf(true) }
    var lastHandledRestoreTrigger by remember { mutableLongStateOf(-1L) }

    LaunchedEffect(state.lastPositionRestoreTrigger, globalPages) {
        if (globalPages.isEmpty()) {
            isRestoring = false
            return@LaunchedEffect
        }

        val triggerChanged = state.lastPositionRestoreTrigger != lastHandledRestoreTrigger

        if (!triggerChanged) {
            // Страницы изменились (фоновая пагинация), но триггер реставрации тот же.
            // Уже восстанавливались — ничего не делаем.
            isRestoring = false
            return@LaunchedEffect
        }

        val targetIdx = findRestorePageIndex(
            globalPages = globalPages,
            chapterIndex = state.currentChapter,
            textOffset = state.textOffset,
            savedPageIndex = state.pageCurrent
        )

        var reached = false
        if (targetIdx >= 0) {
            Log.d("ReaderNav", "PageMode: Restoring to page $targetIdx (ch=${state.currentChapter}, offset=${state.textOffset})")
            isRestoring = true
            viewModel.beginProgrammaticScroll()
            pagerState.scrollToPage(targetIdx)
<<<<<<< HEAD
            lastHandledRestoreTrigger = state.lastPositionRestoreTrigger
            delay(250)
=======

            // Ждём, пока пейджер реально дойдёт до целевой страницы.
            // Программный флаг удерживается пока идёт анимация, чтобы промежуточные
            // PageInfo не сохранили неправильную позицию.
            var waited = 0L
            while (waited < 3000L && !reached) {
                delay(100)
                waited += 100
                if (pagerState.currentPage == targetIdx) {
                    reached = true
                }
            }
            viewModel.endProgrammaticScroll()

            if (reached) {
                lastHandledRestoreTrigger = state.lastPositionRestoreTrigger
                Log.d("ReaderNav", "PageMode: Restore completed at page $targetIdx")
            } else {
                Log.d("ReaderNav", "PageMode: Restore timeout at page $targetIdx (current=${pagerState.currentPage})")
            }
>>>>>>> d24a20e (FoxyBook v1.9)
        }
        // Если targetIdx < 0 — не сохраняем lastHandledRestoreTrigger,
        // чтобы при дозагрузке глав LaunchedEffect сработал снова
        isRestoring = false

        // Повторная попытка реставрации при дозагрузке страниц, если предыдущая
        // не удалась или страницы появились после первого запуска эффекта.
        if (targetIdx < 0 || !reached) {
            lastHandledRestoreTrigger = -1L
        }

        // После восстановления фиксируем фактическую страницу в базе.
        // Сохраняем вне программного скролла, иначе запись будет проигнорирована.
        val saveIdx = if (targetIdx >= 0 && targetIdx < globalPages.size) targetIdx else pagerState.currentPage
        if (saveIdx in globalPages.indices && state.isLoading == false) {
            val restored = globalPages[saveIdx]
            val totalInChapter = globalPages.count { pg -> pg.chapterIndex == restored.chapterIndex }.coerceAtLeast(1)
            viewModel.onEvent(ReaderEvent.PageInfo(
                restored.pageIndexInChapter,
                totalInChapter,
                restored.page.startOffset
            ))
        }
    }

    // После перевёрстки (смена шрифта/межстрочного интервала/полей/размера экрана)
    // chapterPages пересчитываются асинхронно, но pagerState не сбрасывается.
    // Явно мотаем пейджер на страницу, соответствующую текущей позиции чтения
    // (текстовому смещению), а не к state.pageCurrent — иначе после перевёрстки
    // номер страницы может не совпадать с текстом.
    var lastRepaginateTarget by remember { mutableIntStateOf(-1) }
        LaunchedEffect(
            state.paginationVersion,
            globalPages.size,
            state.currentChapter,
            state.textOffset,
            state.pageCurrent
        ) {
            if (isRestoring || state.isLoading || globalPages.isEmpty()) return@LaunchedEffect

            val targetIdx = findRestorePageIndex(
                globalPages = globalPages,
                chapterIndex = state.currentChapter,
                textOffset = state.textOffset,
                savedPageIndex = state.pageCurrent
            )

            if (targetIdx >= 0 && targetIdx != lastRepaginateTarget && targetIdx != pagerState.currentPage) {
                lastRepaginateTarget = targetIdx
                viewModel.beginProgrammaticScroll()
                pagerState.scrollToPage(targetIdx)
                // Ждём окончания анимации, иначе промежуточные PageInfo сохранят
                // неправильную позицию во время перевёрстки.
                var waited = 0L
                while (waited < 3000L && pagerState.isScrollInProgress) {
                    delay(100)
                    waited += 100
                }
                viewModel.endProgrammaticScroll()
            }
        }

        LaunchedEffect(pagerState) {
            snapshotFlow {
                pagerState.currentPage to pagerState.isScrollInProgress
            }.collect { (page, isScrolling) ->
            if (page in globalPages.indices) {
                val currentPage = globalPages[page]

                if (isRestoring) return@collect

                if (currentPage.chapterIndex != state.currentChapter) {
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

    }

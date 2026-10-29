package com.foxybook.app.features.reader

import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * Позиция чтения — самое важное, что есть в приложении. Эти тесты закрывают
 * случаи, из-за которых книга открывалась на первой или неправильной странице.
 */
class RestoreAnchorTest {

    /** Три страницы одной главы + по одной странице соседних глав. */
    private val pages = listOf(
        RestoreAnchor(chapterIndex = 0, offset = 0, indexInChapter = 0),
        RestoreAnchor(chapterIndex = 1, offset = 0, indexInChapter = 0),
        RestoreAnchor(chapterIndex = 1, offset = 1000, indexInChapter = 1),
        RestoreAnchor(chapterIndex = 1, offset = 2000, indexInChapter = 2),
        RestoreAnchor(chapterIndex = 2, offset = 0, indexInChapter = 0)
    )

    @Test
    fun `offset landing exactly on page start restores that page`() {
        assertEquals(2, findRestoreIndex(pages, chapterIndex = 1, offset = 1000, savedIndexInChapter = 0))
    }

    @Test
    fun `offset inside a page restores that page`() {
        assertEquals(2, findRestoreIndex(pages, chapterIndex = 1, offset = 1500, savedIndexInChapter = 0))
    }

    @Test
    fun `offset beyond last page stays on last page instead of wrapping`() {
        assertEquals(3, findRestoreIndex(pages, chapterIndex = 1, offset = 999_999, savedIndexInChapter = 0))
    }

    @Test
    fun `zero offset restores first page of the chapter`() {
        assertEquals(1, findRestoreIndex(pages, chapterIndex = 1, offset = 0, savedIndexInChapter = 7))
    }

    /**
     * Главный регресс: после перевёрстки (новый шрифт/поля/поворот) сохранённое
     * смещение не попадает ни в один диапазон. Старый код возвращал первую
     * страницу главы — теперь возвращается ближайшая предыдущая.
     */
    @Test
    fun `re-paginated chapter does not fall back to first page`() {
        val repaginated = listOf(
            RestoreAnchor(chapterIndex = 1, offset = 0, indexInChapter = 0),
            RestoreAnchor(chapterIndex = 1, offset = 137, indexInChapter = 1),
            RestoreAnchor(chapterIndex = 1, offset = 274, indexInChapter = 2)
        )
        // Смещение из старой вёрстки (2000) больше любого старого диапазона,
        // но попадает в конец новой главы.
        assertEquals(2, findRestoreIndex(repaginated, chapterIndex = 1, offset = 2000, savedIndexInChapter = 2))
    }

    @Test
    fun `falls back to saved index when offset precedes chapter start`() {
        // Смещение меньше любого смещения в главе — якорь смещения непригоден.
        val shifted = listOf(
            RestoreAnchor(chapterIndex = 2, offset = 500, indexInChapter = 0),
            RestoreAnchor(chapterIndex = 2, offset = 1500, indexInChapter = 1)
        )
        assertEquals(1, findRestoreIndex(shifted, chapterIndex = 2, offset = -10, savedIndexInChapter = 1))
    }

    @Test
    fun `falls back to first page when saved index is unknown too`() {
        val shifted = listOf(
            RestoreAnchor(chapterIndex = 2, offset = 500, indexInChapter = 0),
            RestoreAnchor(chapterIndex = 2, offset = 1500, indexInChapter = 1)
        )
        assertEquals(0, findRestoreIndex(shifted, chapterIndex = 2, offset = -10, savedIndexInChapter = 99))
    }

    @Test
    fun `never returns an index from another chapter`() {
        for (offset in listOf(-5, 0, 999, 1500, 5000)) {
            val idx = findRestoreIndex(pages, chapterIndex = 1, offset = offset, savedIndexInChapter = 2)
            assertEquals("offset=$offset", 1, pages[idx].chapterIndex)
        }
    }

    @Test
    fun `chapter without items reports no index`() {
        assertEquals(-1, findRestoreIndex(pages, chapterIndex = 99, offset = 0, savedIndexInChapter = 0))
        assertEquals(-1, findRestoreIndex(emptyList(), chapterIndex = 0, offset = 0, savedIndexInChapter = 0))
    }

    @Test
    fun `single page chapter restores reliably`() {
        val single = listOf(RestoreAnchor(chapterIndex = 5, offset = 0, indexInChapter = 0))
        assertEquals(0, findRestoreIndex(single, chapterIndex = 5, offset = 0, savedIndexInChapter = 0))
        assertEquals(0, findRestoreIndex(single, chapterIndex = 5, offset = 42, savedIndexInChapter = 0))
    }

    @Test
    fun `duplicate offsets resolve to the last one`() {
        val duplicated = listOf(
            RestoreAnchor(chapterIndex = 1, offset = 0, indexInChapter = 0),
            RestoreAnchor(chapterIndex = 1, offset = 0, indexInChapter = 1)
        )
        assertEquals(1, findRestoreIndex(duplicated, chapterIndex = 1, offset = 0, savedIndexInChapter = 0))
    }
}

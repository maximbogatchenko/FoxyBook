package com.foxybook.app.features.library

import com.foxybook.app.core.models.BookReadState
import com.foxybook.app.core.models.LibraryBook
import com.foxybook.app.core.models.LibrarySortField
import com.foxybook.app.core.models.LibrarySortSpec
import com.foxybook.app.core.models.LibraryTab
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Test

/**
 * Проверяет путь, который UI дёргает на每一 перерисовке:
 * [LibraryState.groupsForTab] / [LibraryState.groupsForCollection].
 * Если тут порядок не меняется — сортировка «не работает» на экране.
 */
class LibraryStateSortingTest {

    private fun book(id: Int, title: String, author: String, added: Long) = LibraryBook(
        id = id, title = title, author = author, format = "fb2",
        filePath = "/b/$id.fb2", downloadDate = added,
        // Чтобы все вкладки (Все / Избранное / История) содержали книги.
        isFavorite = true, lastReadDate = added
    )

    private val books = listOf(
        book(1, "Волкодав", "Семёнов", 30),
        book(2, "Азилия", "Кошелев", 10),
        book(3, "Тьма", "Андреев", 20)
    )

    private fun stateWith(spec: LibrarySortSpec) = LibraryState(allBooks = books, sortSpec = spec)

    @Test
    fun `changing sort field changes the order shown on the tab`() {
        val byAdded = stateWith(LibrarySortSpec(field = LibrarySortField.DATE_ADDED, descending = false))
            .groupsForTab(LibraryTab.ALL).flatMap { g -> g.books }
        val byAuthor = stateWith(LibrarySortSpec(field = LibrarySortField.AUTHOR, descending = false))
            .groupsForTab(LibraryTab.ALL).flatMap { g -> g.books }

        assertEquals(listOf(2, 3, 1), byAdded.map { it.id })
        assertEquals(listOf(3, 2, 1), byAuthor.map { it.id })
        assertNotEquals(byAdded, byAuthor)
    }

    @Test
    fun `toggling direction flips the order`() {
        val asc = stateWith(LibrarySortSpec(field = LibrarySortField.TITLE, descending = false))
            .groupsForTab(LibraryTab.ALL).flatMap { g -> g.books }
        val desc = stateWith(LibrarySortSpec(field = LibrarySortField.TITLE, descending = true))
            .groupsForTab(LibraryTab.ALL).flatMap { g -> g.books }
        assertEquals(asc.map { it.id }.reversed(), desc.map { it.id })
    }

    @Test
    fun `sorting applies on every tab and inside a collection`() {
        val spec = LibrarySortSpec(field = LibrarySortField.TITLE, descending = false)
        val state = LibraryState(allBooks = books, sortSpec = spec)
        val expected = listOf("Азилия", "Волкодав", "Тьма")

        for (tab in listOf(LibraryTab.ALL, LibraryTab.FAVORITES, LibraryTab.HISTORY)) {
            val titles = state.groupsForTab(tab).flatMap { g -> g.books }.map { it.title }
            assertEquals("tab=$tab", expected, titles)
        }
        assertEquals(expected, state.groupsForCollection(null).flatMap { g -> g.books }.map { it.title })
    }

    @Test
    fun `grouping by status does not destroy the sort order inside shelves`() {
        val mixed = listOf(
            book(1, "C-finished", "Z", 1).copy(readingProgress = 100),
            book(2, "A-reading", "Y", 2).copy(readingProgress = 20),
            // lastReadDate = 0 → статус AUTO без прогресса = «не прочитано».
            book(3, "B-unread", "X", 3).copy(lastReadDate = 0),
            book(4, "D-manual", "W", 4).copy(readState = BookReadState.FINISHED)
        )
        val state = LibraryState(
            allBooks = mixed,
            sortSpec = LibrarySortSpec(field = LibrarySortField.TITLE, descending = false, groupByStatus = true)
        )
        val groups = state.groupsForTab(LibraryTab.ALL)

        assertEquals(listOf("A-reading"), groups.first { it.books.any { b -> b.id == 2 } }.books.map { it.title })
        assertEquals(
            listOf("C-finished", "D-manual"),
            groups.first { it.books.any { b -> b.id == 1 } }.books.map { it.title }
        )
    }

    @Test
    fun `empty library yields no groups so the empty state stays visible`() {
        val state = LibraryState(allBooks = emptyList(), sortSpec = LibrarySortSpec.DEFAULT)
        assertEquals(emptyList<Any>(), state.groupsForTab(LibraryTab.ALL))
    }
}

package com.foxybook.app.core.models

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class LibrarySortingTest {

    private fun book(
        id: Int,
        title: String,
        author: String = "",
        added: Long = 0,
        lastRead: Long = 0,
        progress: Int = 0,
        readState: String = BookReadState.AUTO
    ) = LibraryBook(
        id = id, title = title, author = author, format = "fb2",
        filePath = "/books/$id.fb2", downloadDate = added,
        lastReadDate = lastRead, readingProgress = progress, readState = readState
    )

    // ── Сериализация настройки ────────────────────────────────────────

    @Test
    fun `sort spec survives encode decode roundtrip`() {
        val specs = listOf(
            LibrarySortSpec.DEFAULT,
            LibrarySortSpec(LibrarySortField.TITLE, descending = false, groupByStatus = false),
            LibrarySortSpec(LibrarySortField.PROGRESS, descending = true, groupByStatus = false),
            LibrarySortSpec(LibrarySortField.LAST_READ, descending = false, groupByStatus = true)
        )
        for (spec in specs) {
            assertEquals(spec, LibrarySortSpec.decode(spec.encode()))
        }
    }

    @Test
    fun `decode falls back to default on garbage`() {
        assertEquals(LibrarySortSpec.DEFAULT, LibrarySortSpec.decode(null))
        assertEquals(LibrarySortSpec.DEFAULT, LibrarySortSpec.decode(""))
        assertEquals(LibrarySortSpec.DEFAULT, LibrarySortSpec.decode("not-a-spec"))
        assertEquals(LibrarySortSpec.DEFAULT, LibrarySortSpec.decode("all|vertical|sideways"))
    }

    @Test
    fun `decode keeps field but repairs unknown direction and grouping flags`() {
        val spec = LibrarySortSpec.decode("AUTHOR|sideways|maybe")
        assertEquals(LibrarySortField.AUTHOR, spec.field)
        assertTrue(spec.descending)
        assertTrue(spec.groupByStatus)
    }

    // ── Статусы чтения ────────────────────────────────────────────────

    @Test
    fun `explicit read state wins over progress`() {
        val finishedWithZeroProgress = book(1, "A", progress = 0, readState = BookReadState.FINISHED)
        val unreadWithFullProgress = book(2, "B", progress = 100, readState = BookReadState.UNREAD)
        assertEquals(ReadStatus.FINISHED, LibrarySorter.readStatus(finishedWithZeroProgress))
        assertEquals(ReadStatus.UNREAD, LibrarySorter.readStatus(unreadWithFullProgress))
    }

    @Test
    fun `auto status is derived from progress`() {
        assertEquals(ReadStatus.UNREAD, LibrarySorter.readStatus(book(1, "A", progress = 0)))
        assertEquals(ReadStatus.READING, LibrarySorter.readStatus(book(2, "B", progress = 50)))
        assertEquals(ReadStatus.FINISHED, LibrarySorter.readStatus(book(3, "C", progress = 100)))
    }

    @Test
    fun `opening a book counts as reading even without progress`() {
        assertEquals(ReadStatus.READING, LibrarySorter.readStatus(book(1, "A", progress = 0, lastRead = 99)))
    }

    @Test
    fun `normalize rejects unknown states`() {
        assertEquals(BookReadState.AUTO, BookReadState.normalize(null))
        assertEquals(BookReadState.AUTO, BookReadState.normalize("WHATEVER"))
        assertEquals(BookReadState.FINISHED, BookReadState.normalize(" finished "))
    }

    // ── Сортировка ─────────────────────────────────────────────────────

    @Test
    fun `sorts by title ignoring case`() {
        val books = listOf(book(1, "banana"), book(2, "Apple"), book(3, "cherry"))
        val sorted = LibrarySorter.sort(books, LibrarySortSpec(field = LibrarySortField.TITLE, descending = false))
        assertEquals(listOf("Apple", "banana", "cherry"), sorted.map { it.title })
    }

    @Test
    fun `descending flag reverses primary order`() {
        val books = listOf(book(1, "A", added = 10), book(2, "B", added = 20), book(3, "C", added = 30))
        val asc = LibrarySorter.sort(books, LibrarySortSpec(field = LibrarySortField.DATE_ADDED, descending = false))
        val desc = LibrarySorter.sort(books, LibrarySortSpec(field = LibrarySortField.DATE_ADDED, descending = true))
        assertEquals(listOf(10L, 20L, 30L), asc.map { it.downloadDate })
        assertEquals(listOf(30L, 20L, 10L), desc.map { it.downloadDate })
    }

    @Test
    fun `ties are broken deterministically regardless of input order`() {
        val spec = LibrarySortSpec(field = LibrarySortField.DATE_ADDED, descending = false)
        val forwards = listOf(book(3, "Same", added = 5), book(1, "Same", added = 5), book(2, "Same", added = 5))
        val backwards = forwards.reversed()
        assertEquals(listOf(1, 2, 3), LibrarySorter.sort(forwards, spec).map { it.id })
        assertEquals(LibrarySorter.sort(forwards, spec), LibrarySorter.sort(backwards, spec))
    }

    @Test
    fun `sorting by progress and last read uses numeric fields`() {
        val books = listOf(
            book(1, "A", progress = 90, lastRead = 100),
            book(2, "B", progress = 10, lastRead = 300),
            book(3, "C", progress = 50, lastRead = 200)
        )
        assertEquals(
            listOf(1, 3, 2),
            LibrarySorter.sort(books, LibrarySortSpec(field = LibrarySortField.LAST_READ, descending = false)).map { it.id }
        )
        assertEquals(
            listOf(2, 3, 1),
            LibrarySorter.sort(books, LibrarySortSpec(field = LibrarySortField.PROGRESS, descending = false)).map { it.id }
        )
    }

    @Test
    fun `sorting by author is case insensitive`() {
        val books = listOf(book(1, "X", author = "Zweig"), book(2, "Y", author="abbey"), book(3, "Z", author = "Булгаков"))
        val sorted = LibrarySorter.sort(books, LibrarySortSpec(field = LibrarySortField.AUTHOR, descending = false))
        assertEquals(listOf("abbey", "Zweig", "Булгаков"), sorted.map { it.author })
    }

    @Test
    fun `single book list is returned untouched`() {
        val books = listOf(book(1, "only"))
        assertEquals(books, LibrarySorter.sort(books, LibrarySortSpec.DEFAULT))
        assertEquals(emptyList<LibraryBook>(), LibrarySorter.sort(emptyList(), LibrarySortSpec.DEFAULT))
    }

    // ── Группировка ────────────────────────────────────────────────────

    @Test
    fun `grouping disabled returns one unnamed group`() {
        val books = listOf(book(1, "A", progress = 0), book(2, "B", progress = 100))
        val groups = LibrarySorter.group(books, LibrarySortSpec(groupByStatus = false))
        assertEquals(1, groups.size)
        assertNull(groups.first().status)
        assertEquals(2, groups.first().books.size)
    }

    @Test
    fun `grouping splits by status in shelf order`() {
        val books = listOf(
            book(1, "done", progress = 100),
            book(2, "unread", progress = 0),
            book(3, "reading", progress = 40)
        )
        val groups = LibrarySorter.group(books, LibrarySortSpec(groupByStatus = true))
        assertEquals(
            listOf(ReadStatus.READING, ReadStatus.UNREAD, ReadStatus.FINISHED),
            groups.map { it.status }
        )
        assertEquals(listOf("reading", "unread", "done"), groups.flatMap { g -> g.books.map { it.title } })
    }

    @Test
    fun `empty shelves are not returned`() {
        val books = listOf(book(1, "only reading", progress = 40))
        val groups = LibrarySorter.group(books, LibrarySortSpec(groupByStatus = true))
        assertEquals(1, groups.size)
        assertEquals(ReadStatus.READING, groups.single().status)
    }

    @Test
    fun `grouping empty list yields empty groups`() {
        assertTrue(LibrarySorter.group(emptyList(), LibrarySortSpec(groupByStatus = true)).isEmpty())
        assertTrue(LibrarySorter.group(emptyList(), LibrarySortSpec(groupByStatus = false)).isEmpty())
    }
}

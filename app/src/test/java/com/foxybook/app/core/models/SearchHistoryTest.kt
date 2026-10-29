package com.foxybook.app.core.models

import org.junit.Assert.assertEquals
import org.junit.Test

class SearchHistoryTest {

    @Test
    fun `new query goes to the top`() {
        val history = SearchHistory.add(listOf("старый"), "новый")
        assertEquals(listOf("новый", "старый"), history)
    }

    @Test
    fun `repeat moves to top instead of duplicating`() {
        val history = SearchHistory.add(listOf("a", "b", "c"), "a")
        assertEquals(listOf("a", "b", "c"), history)
    }

    @Test
    fun `repeat is matched ignoring case and surrounding spaces`() {
        val history = SearchHistory.add(listOf("Шерлок"), "  шерлок ")
        assertEquals(listOf("шерлок"), history)
    }

    @Test
    fun `blank query is ignored`() {
        val history = SearchHistory.add(listOf("a"), "   ")
        assertEquals(listOf("a"), history)
    }

    @Test
    fun `blank query on empty history stays empty`() {
        assertEquals(emptyList<String>(), SearchHistory.add(emptyList(), ""))
        assertEquals(emptyList<String>(), SearchHistory.add(emptyList(), "\n\t"))
    }

    @Test
    fun `history is capped and oldest entries fall off`() {
        var history = emptyList<String>()
        repeat(20) { history = SearchHistory.add(history, "q$it") }

        assertEquals(SearchHistory.LIMIT, history.size)
        // Новейший — первый, самый старый вытеснен.
        assertEquals("q19", history.first())
        assertEquals("q8", history.last())
        assertEquals((0..19).reversed().take(SearchHistory.LIMIT).map { "q$it" }, history)
    }

    @Test
    fun `custom limit is respected`() {
        val history = SearchHistory.add(listOf("a", "b", "c"), "d", limit = 2)
        assertEquals(listOf("d", "a"), history)
    }

    @Test
    fun `sanitize drops blanks and case insensitive duplicates`() {
        val sanitized = SearchHistory.sanitize(listOf(" a ", "", "A", "b", "  ", "B"))
        assertEquals(listOf("a", "b"), sanitized)
    }

    @Test
    fun `add keeps first seen spelling of a duplicate pair`() {
        // «Шерлок» идёт раньше «шерлок», поэтому он и остаётся в истории.
        val history = SearchHistory.add(listOf("Шерлок"), "шерлок")
        assertEquals(listOf("шерлок"), history)
    }
}

package com.foxybook.app.core.models

import org.junit.Assert.assertEquals
import org.junit.Test

class StartScreenTest {

    @Test
    fun `normalize keeps known start screens`() {
        assertEquals(StartScreen.NEW_BOOKS, StartScreen.normalize(StartScreen.NEW_BOOKS))
        assertEquals(StartScreen.LIBRARY, StartScreen.normalize(StartScreen.LIBRARY))
        assertEquals(StartScreen.SEARCH, StartScreen.normalize(StartScreen.SEARCH))
    }

    @Test
    fun `normalize falls back to new books for missing value`() {
        assertEquals(StartScreen.NEW_BOOKS, StartScreen.normalize(null))
        assertEquals(StartScreen.NEW_BOOKS, StartScreen.normalize(""))
    }

    @Test
    fun `normalize falls back to new books for unknown value`() {
        assertEquals(StartScreen.NEW_BOOKS, StartScreen.normalize("settings"))
        assertEquals(StartScreen.NEW_BOOKS, StartScreen.normalize("splash"))
        assertEquals(StartScreen.NEW_BOOKS, StartScreen.normalize("reader/1/epub/path"))
    }

    @Test
    fun `route ids match navigation routes`() {
        // Значения хранятся в DataStore как есть, поэтому должны совпадать с Routes
        assertEquals(listOf("new_books", "library", "search"), StartScreen.ALL)
    }
}
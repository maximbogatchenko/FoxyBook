package com.foxybook.app.core.updater

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class UpdateCheckerVersionTest {

    @Test
    fun `newer patch version is detected`() {
        assertTrue(isNewer("1.8", "1.7"))
        assertTrue(isNewer("1.9", "1.8.9"))
    }

    @Test
    fun `equal versions are not newer`() {
        assertFalse(isNewer("1.8", "1.8"))
        assertFalse(isNewer("1.8.0", "1.8"))
    }

    @Test
    fun `older versions are rejected`() {
        assertFalse(isNewer("1.7", "1.8"))
        assertFalse(isNewer("1.8", "1.8.1"))
    }

    @Test
    fun `multi digit components compare numerically not lexically`() {
        // Строковое сравнение здесь дало бы обратный результат
        assertTrue(isNewer("1.10", "1.9"))
        assertTrue(isNewer("2.0", "1.99"))
        assertFalse(isNewer("1.9", "1.10"))
    }

    @Test
    fun `v prefix is tolerated`() {
        assertTrue(isNewer("v1.9", "1.8"))
        assertFalse(isNewer("V1.8", "v1.8"))
    }

    @Test
    fun `non numeric parts do not crash and are treated as zero`() {
        assertFalse(isNewer("1.beta", "1.8"))
        assertTrue(isNewer("1.9.beta", "1.8"))
    }

    @Test
    fun `debug suffix is handled by caller`() {
        // getCurrentVersion() отрезает суффикс "-debug" до сравнения
        assertTrue(isNewer("1.9", "1.8"))
    }
}
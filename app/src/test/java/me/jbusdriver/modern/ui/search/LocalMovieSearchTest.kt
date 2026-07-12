package me.jbusdriver.modern.ui.search

import me.jbusdriver.modern.ui.MovieUiModel
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class LocalMovieSearchTest {

    private fun movie(code: String = "", title: String = "") =
        MovieUiModel(title = title, imageUrl = "", code = code, date = "", link = "")

    @Test
    fun `normalize lowercases and strips dash underscore and whitespace`() {
        assertEquals("abc123", normalizeSearchText("ABC-123"))
        assertEquals("abc0123", normalizeSearchText("ABC_0123"))
        assertEquals("abc123", normalizeSearchText("ABC 123"))
        assertEquals("abc", normalizeSearchText(" a B_c "))
        assertEquals("", normalizeSearchText("- _ -"))
        assertEquals("", normalizeSearchText(""))
    }

    @Test
    fun `matches code by normalized substring, case and separator insensitive`() {
        assertTrue(movie(code = "ABC-123").matchesLocal("abc123"))
        assertTrue(movie(code = "ABC-123").matchesLocal("ABC_123"))
        assertTrue(movie(code = "ABC_0123").matchesLocal("abc0123"))
        assertTrue(movie(code = "ABC-123").matchesLocal("abc"))
    }

    @Test
    fun `matches title by normalized substring`() {
        assertTrue(movie(title = "女教師的課外授業").matchesLocal("女教師"))
        assertTrue(movie(title = "My Great Title").matchesLocal("great title"))
    }

    @Test
    fun `does not match when neither code nor title contains query`() {
        assertFalse(movie(code = "ABC-123", title = "Hello").matchesLocal("xyz"))
    }

    @Test
    fun `separator-only or blank query never matches`() {
        val m = movie(code = "ABC-123", title = "Hello")
        assertFalse(m.matchesLocal("-"))
        assertFalse(m.matchesLocal("_"))
        assertFalse(m.matchesLocal(" - _ "))
        assertFalse(m.matchesLocal(""))
    }
}

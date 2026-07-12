package me.jbusdriver.modern.ui

import me.jbusdriver.modern.data.db.convertDBItem
import me.jbusdriver.modern.domain.model.Movie
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class MovieUiModelMapperTest {

    @Test
    fun `LinkItem to MovieUiModel restores urls with base url and keeps metadata`() {
        val movie = Movie(
            title = "Movie",
            imageUrl = "https://old.test/images/cover.jpg",
            code = "ABC-001",
            date = "2026-06-19",
            link = "https://old.test/movies/ABC-001"
        )
        val item = movie.convertDBItem(categoryId = 3).copy(createTime = 1234L)

        val ui = item.toMovieUiModel("https://mirror.test")

        assertEquals("ABC-001", ui?.code)
        assertEquals("Movie", ui?.title)
        assertEquals("https://mirror.test/movies/ABC-001", ui?.link)
        assertEquals("https://mirror.test/images/cover.jpg", ui?.imageUrl)
        assertEquals(1234L, ui?.createTime)
        assertEquals(3, ui?.categoryId)
        assertTrue(ui?.isUncensoredCollected == true)
    }

    @Test
    fun `toMovieUiModel returns null for invalid json`() {
        val item = Movie("M", "http://x", "ABC-1", "2024-01-01", "http://l")
            .convertDBItem().copy(jsonStr = "{")
        assertNull(item.toMovieUiModel("https://mirror.test"))
    }

    @Test
    fun `isUncensoredCollected is false for default censored category`() {
        val ui = Movie("M", "http://x", "ABC-1", "2024-01-01", "http://l")
            .convertDBItem(categoryId = 1).toMovieUiModel("https://x")
        assertFalse(ui?.isUncensoredCollected == true)
    }
}

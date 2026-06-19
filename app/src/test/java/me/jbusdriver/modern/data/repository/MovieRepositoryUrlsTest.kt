package me.jbusdriver.modern.data.repository

import me.jbusdriver.modern.domain.model.DataSourceType
import org.junit.Assert.assertEquals
import org.junit.Test

class MovieRepositoryUrlsTest {
    @Test
    fun `movie page uses section base for first page`() {
        assertEquals("https://example.test", MovieRepositoryUrls.moviePage("https://example.test", DataSourceType.CENSORED, 1))
        assertEquals("https://example.test/uncensored", MovieRepositoryUrls.moviePage("https://example.test", DataSourceType.UNCENSORED, 1))
        assertEquals("https://example.test/xyz", MovieRepositoryUrls.moviePage("https://example.test", DataSourceType.XYZ, 1))
    }

    @Test
    fun `movie page appends prefix for later pages`() {
        assertEquals("https://example.test/page/3", MovieRepositoryUrls.moviePage("https://example.test", DataSourceType.CENSORED, 3))
        assertEquals("https://example.test/uncensored/page/3", MovieRepositoryUrls.moviePage("https://example.test", DataSourceType.UNCENSORED, 3))
    }

    @Test
    fun `actress page switches base by type and paginates`() {
        assertEquals("https://example.test/actresses", MovieRepositoryUrls.actressPage("https://example.test", DataSourceType.ACTRESSES, 1))
        assertEquals("https://example.test/actresses/2", MovieRepositoryUrls.actressPage("https://example.test", DataSourceType.ACTRESSES, 2))
        assertEquals("https://example.test/uncensored/actresses", MovieRepositoryUrls.actressPage("https://example.test", DataSourceType.UNCENSORED_ACTRESSES, 1))
    }

    @Test
    fun `genre categories switch uncensored base only for uncensored genre`() {
        assertEquals("https://example.test/genre", MovieRepositoryUrls.genreCategories("https://example.test", DataSourceType.GENRE))
        assertEquals("https://example.test/uncensored/genre", MovieRepositoryUrls.genreCategories("https://example.test", DataSourceType.UNCENSORED_GENRE))
    }

    @Test
    fun `resolved and page by url handle relative and first-page urls`() {
        assertEquals("https://example.test/path", MovieRepositoryUrls.resolvedPageUrl("https://example.test", "/path"))
        assertEquals("https://cdn.example/path", MovieRepositoryUrls.resolvedPageUrl("https://example.test", "https://cdn.example/path"))
        assertEquals("https://example.test/path", MovieRepositoryUrls.pageByUrl("https://example.test/path", 1))
        assertEquals("https://example.test/path/4", MovieRepositoryUrls.pageByUrl("https://example.test/path", 4))
    }
}

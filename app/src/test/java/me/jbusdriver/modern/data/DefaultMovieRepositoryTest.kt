package me.jbusdriver.modern.data

import kotlinx.coroutines.test.runTest
import me.jbusdriver.ui.data.enums.DataSourceType
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

class DefaultMovieRepositoryTest {

    private val repository = DefaultMovieRepository()

    @Test
    fun loadPage_returnsMoviePageResult() = runTest {
        val result = repository.loadPage(DataSourceType.CENSORED, 1)
        assertNotNull(result)
        assertTrue("Page should have active page >= 0", result.pageInfo.activePage >= 0)
    }

    @Test
    fun loadPage_firstPage_returnsMovies() = runTest {
        val result = repository.loadPage(DataSourceType.CENSORED, 1)
        assertTrue("First page should have movies", result.movies.isNotEmpty())
        if (result.movies.isNotEmpty()) {
            val movie = result.movies.first()
            assertTrue("Movie should have a title", movie.title.isNotBlank())
            assertTrue("Movie should have a code", movie.code.isNotBlank())
            assertTrue("Movie should have a link", movie.link.isNotBlank())
        }
    }

    @Test
    fun loadPage_pageInfo_hasCorrectActivePage() = runTest {
        val result = repository.loadPage(DataSourceType.CENSORED, 1)
        assertEquals("Active page should be 1", 1, result.pageInfo.activePage)
    }
}

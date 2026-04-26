package me.jbusdriver.modern.data

import kotlinx.coroutines.test.runTest
import me.jbusdriver.modern.data.model.MoviePageResult
import me.jbusdriver.modern.data.model.PageInfo
import me.jbusdriver.modern.data.model.hasNext
import me.jbusdriver.mvp.bean.Movie
import me.jbusdriver.ui.data.enums.DataSourceType
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class FakeMovieRepositoryTest {

    // Tests the MovieRepository interface contract with a fake implementation
    private val fakeMovies = listOf(
        Movie("Test Movie 1", "http://img1.jpg", "ABC-001", "2024-01-01", "http://link1"),
        Movie("Test Movie 2", "http://img2.jpg", "DEF-002", "2024-01-02", "http://link2")
    )

    private val repository = object : MovieRepository {
        override suspend fun loadPage(type: DataSourceType, page: Int, showAll: Boolean) =
            MoviePageResult(
                pageInfo = PageInfo(page, page + 1, listOf(page, page + 1)),
                movies = fakeMovies
            )
    }

    @Test
    fun loadPage_returnsMoviePageResult() = runTest {
        val result = repository.loadPage(DataSourceType.CENSORED, 1)
        assertEquals(2, result.movies.size)
        assertEquals(1, result.pageInfo.activePage)
        assertEquals(2, result.pageInfo.nextPage)
    }

    @Test
    fun loadPage_firstPage_returnsMoviesWithRequiredFields() = runTest {
        val result = repository.loadPage(DataSourceType.CENSORED, 1)
        assertTrue(result.movies.isNotEmpty())
        val movie = result.movies.first()
        assertTrue(movie.title.isNotBlank())
        assertTrue(movie.code.isNotBlank())
        assertTrue(movie.link.isNotBlank())
    }

    @Test
    fun loadPage_pageInfo_hasCorrectActivePage() = runTest {
        val result = repository.loadPage(DataSourceType.CENSORED, 1)
        assertEquals(1, result.pageInfo.activePage)
        assertTrue(result.pageInfo.hasNext)
    }
}

package me.jbusdriver.modern.data.repository

import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.test.runTest
import me.jbusdriver.modern.core.cache.CachedLoadEvent
import me.jbusdriver.modern.domain.model.ActressDetail
import me.jbusdriver.modern.domain.model.ActressInfo
import me.jbusdriver.modern.domain.model.DataSourceType
import me.jbusdriver.modern.domain.model.GenreGroup
import me.jbusdriver.modern.domain.model.Movie
import me.jbusdriver.modern.domain.model.MoviePageResult
import me.jbusdriver.modern.domain.model.PageInfo
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.IOException

/**
 * 覆盖 [MovieRepository] 接口中默认实现的 observe* 方法（try/catch → Fresh/Failure 分支），
 * 使用 lambda 配置的匿名实现隔离 [MovieRepository] 的抽象 load 方法。
 */
class MovieRepositoryDefaultObserveTest {

    private fun movieRepo(
        loadPage: suspend () -> MoviePageResult = { result() },
        loadActresses: suspend () -> Pair<List<ActressInfo>, PageInfo> =
            { emptyList<ActressInfo>() to PageInfo(activePage = 1, nextPage = 2) },
        loadGenres: suspend () -> List<GenreGroup> = { emptyList() }
    ): MovieRepository = object : MovieRepository {
        override suspend fun loadPage(
            type: DataSourceType, page: Int, showAll: Boolean, forceRefresh: Boolean
        ) = loadPage()

        override suspend fun loadActresses(
            type: DataSourceType, page: Int, forceRefresh: Boolean
        ) = loadActresses()

        override suspend fun loadGenreCategories(
            type: DataSourceType, forceRefresh: Boolean
        ) = loadGenres()

        override suspend fun loadPageByUrl(
            url: String, page: Int, showAll: Boolean, forceRefresh: Boolean
        ) = loadPage()

        override suspend fun loadActressDetail(url: String, forceRefresh: Boolean): ActressDetail? = null
    }

    private fun result() = MoviePageResult(
        pageInfo = PageInfo(activePage = 1, nextPage = 2),
        movies = listOf(Movie("t", "img", "ABC-1", "2024-01-01", "link"))
    )

    @Test
    fun observePage_emitsFreshOnSuccess() = runTest {
        val repo = movieRepo()
        val events = repo.observePage(DataSourceType.CENSORED, page = 1).toList()
        assertEquals(1, events.size)
        val fresh = events.single() as CachedLoadEvent.Fresh
        assertEquals(1, fresh.entry.value.movies.size)
        assertFalse(fresh.entry.isExpired)
    }

    @Test
    fun observePage_emitsFailureOnException() = runTest {
        val repo = movieRepo(loadPage = { throw IOException("down") })
        val events = repo.observePage(DataSourceType.CENSORED, page = 1).toList()
        val failure = events.single() as CachedLoadEvent.Failure
        assertEquals("down", failure.throwable.message)
        assertFalse(failure.hadCachedValue)
    }

    @Test
    fun observeActresses_emitsFreshOnSuccess() = runTest {
        val repo = movieRepo()
        val events = repo.observeActresses(DataSourceType.ACTRESSES, page = 1).toList()
        val fresh = events.single() as CachedLoadEvent.Fresh
        assertEquals(2, fresh.entry.value.second.nextPage)
    }

    @Test
    fun observeGenreCategories_emitsFreshOnSuccess() = runTest {
        val repo = movieRepo(loadGenres = { listOf(GenreGroup("g", emptyList())) })
        val events = repo.observeGenreCategories(DataSourceType.CENSORED).toList()
        val fresh = events.single() as CachedLoadEvent.Fresh
        assertEquals(1, fresh.entry.value.size)
    }

    @Test
    fun observeGenreCategories_emitsFailureOnException() = runTest {
        val repo = movieRepo(loadGenres = { throw IOException("down") })
        val events = repo.observeGenreCategories(DataSourceType.CENSORED).toList()
        assertTrue(events.single() is CachedLoadEvent.Failure)
    }

    @Test
    fun observePageByUrl_emitsFreshOnSuccess() = runTest {
        val repo = movieRepo()
        val events = repo.observePageByUrl("/genre/1", page = 1).toList()
        val fresh = events.single() as CachedLoadEvent.Fresh
        assertEquals("ABC-1", fresh.entry.value.movies.single().code)
    }

    @Test
    fun observePageByUrl_emitsFailureOnException() = runTest {
        val repo = movieRepo(loadPage = { throw IOException("down") })
        val events = repo.observePageByUrl("/genre/1", page = 1).toList()
        assertTrue(events.single() is CachedLoadEvent.Failure)
    }
}

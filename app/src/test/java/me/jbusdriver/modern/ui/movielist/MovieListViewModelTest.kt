package me.jbusdriver.modern.ui.movielist

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import me.jbusdriver.R
import me.jbusdriver.modern.data.MovieRepository
import me.jbusdriver.modern.core.cache.CacheEntry
import me.jbusdriver.modern.core.cache.CacheSource
import me.jbusdriver.modern.core.cache.CachedLoadEvent
import me.jbusdriver.modern.domain.model.MovieFilterInfo
import me.jbusdriver.modern.domain.model.MoviePageResult
import me.jbusdriver.modern.domain.model.PageInfo
import me.jbusdriver.modern.domain.model.Movie
import me.jbusdriver.modern.domain.model.DataSourceType
import me.jbusdriver.modern.domain.model.ActressInfo
import me.jbusdriver.modern.domain.model.ActressDetail
import me.jbusdriver.modern.domain.model.GenreGroup
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class MovieListViewModelTest {

    // Use a shared dispatcher for both Main and IO so test can control execution
    private val testDispatcher = StandardTestDispatcher()

    private val testMovies = listOf(
        Movie("Test Movie 1", "http://img1.jpg", "ABC-001", "2024-01-01", "http://link1"),
        Movie("Test Movie 2", "http://img2.jpg", "DEF-002", "2024-01-02", "http://link2")
    )

    private fun fullFakeRepo(
        onLoadPage: (DataSourceType, Int) -> MoviePageResult
    ) = object : MovieRepository {
        override suspend fun loadPage(type: DataSourceType, page: Int, showAll: Boolean, forceRefresh: Boolean) =
            onLoadPage(type, page)
        override suspend fun loadActresses(type: DataSourceType, page: Int, forceRefresh: Boolean) =
            emptyList<ActressInfo>() to PageInfo()
        override suspend fun loadGenreCategories(type: DataSourceType, forceRefresh: Boolean) = emptyList<GenreGroup>()
        override suspend fun loadPageByUrl(url: String, page: Int, showAll: Boolean, forceRefresh: Boolean) =
            MoviePageResult(PageInfo(), emptyList())
        override suspend fun loadActressDetail(url: String, forceRefresh: Boolean): ActressDetail? = null
    }

    private fun flowFakeRepo(
        flows: MutableList<Flow<CachedLoadEvent<MoviePageResult>>>,
        revalidateArgs: MutableList<Boolean>
    ) = object : MovieRepository {
        override fun observePage(
            type: DataSourceType,
            page: Int,
            showAll: Boolean,
            forceRefresh: Boolean,
            revalidate: Boolean,
            nowMillis: () -> Long
        ): Flow<CachedLoadEvent<MoviePageResult>> {
            revalidateArgs += revalidate
            return flows.removeAt(0)
        }

        override suspend fun loadPage(type: DataSourceType, page: Int, showAll: Boolean, forceRefresh: Boolean) =
            error("not used")
        override suspend fun loadActresses(type: DataSourceType, page: Int, forceRefresh: Boolean) =
            emptyList<ActressInfo>() to PageInfo()
        override suspend fun loadGenreCategories(type: DataSourceType, forceRefresh: Boolean) = emptyList<GenreGroup>()
        override suspend fun loadPageByUrl(url: String, page: Int, showAll: Boolean, forceRefresh: Boolean) =
            MoviePageResult(PageInfo(), emptyList())
        override suspend fun loadActressDetail(url: String, forceRefresh: Boolean): ActressDetail? = null
    }

    @Before
    fun setup() {
        Dispatchers.setMain(testDispatcher)
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun loadFirstPage_loadsMoviesAndPageInfo() = runTest(testDispatcher) {
        val repository = fullFakeRepo { _, _ ->
            MoviePageResult(PageInfo(1, 2, listOf(1, 2)), testMovies)
        }
        val viewModel = MovieListViewModel(repository)

        viewModel.loadFirstPage()
        // ViewModel launches on Dispatchers.IO; give real IO threads time to complete
        advanceUntilIdle()
        Thread.sleep(100)
        advanceUntilIdle()

        assertEquals(2, viewModel.uiState.value.movies.size)
        assertEquals(1, viewModel.uiState.value.pageInfo.activePage)
        assertTrue(viewModel.uiState.value.hasMore)
        assertFalse(viewModel.uiState.value.isLoading)
        assertNull(viewModel.uiState.value.error)
    }

    @Test
    fun loadMore_appendsMovies() = runTest(testDispatcher) {
        val page2Movies = listOf(Movie("Movie 3", "http://img3.jpg", "GHI-003", "2024-01-03", "http://link3"))
        var callCount = 0
        val repository = fullFakeRepo { _, page ->
            callCount++
            when (page) {
                1 -> MoviePageResult(PageInfo(1, 2, listOf(1, 2)), testMovies)
                else -> MoviePageResult(PageInfo(2, 3, listOf(1, 2, 3)), page2Movies)
            }
        }
        val viewModel = MovieListViewModel(repository)

        viewModel.loadFirstPage()
        advanceUntilIdle()
        Thread.sleep(100)
        advanceUntilIdle()
        assertEquals(1, callCount)

        viewModel.loadMore()
        advanceUntilIdle()
        Thread.sleep(100)
        advanceUntilIdle()
        assertEquals(2, callCount)

        assertEquals(3, viewModel.uiState.value.movies.size)
        assertEquals(2, viewModel.uiState.value.pageInfo.activePage)
        assertTrue(viewModel.uiState.value.hasMore)
    }

    @Test
    fun loadFirstPage_handlesError() = runTest(testDispatcher) {
        val repository = fullFakeRepo { _, _ -> throw RuntimeException("Network error") }
        val viewModel = MovieListViewModel(repository)

        viewModel.loadFirstPage()
        advanceUntilIdle()
        Thread.sleep(100)
        advanceUntilIdle()

        assertTrue(viewModel.uiState.value.error?.contains("Network error") == true)
        assertFalse(viewModel.uiState.value.isLoading)
    }

    @Test
    fun refresh_resetsMovies() = runTest(testDispatcher) {
        var callCount = 0
        val repository = fullFakeRepo { _, _ ->
            callCount++
            MoviePageResult(PageInfo(1, 2, listOf(1, 2)), testMovies)
        }
        val viewModel = MovieListViewModel(repository)

        viewModel.loadFirstPage()
        advanceUntilIdle()
        Thread.sleep(100)
        advanceUntilIdle()

        viewModel.refresh()
        advanceUntilIdle()
        Thread.sleep(100)
        advanceUntilIdle()

        assertEquals(2, callCount)
        assertEquals(2, viewModel.uiState.value.movies.size)
        assertFalse(viewModel.uiState.value.isRefreshing)
    }

    @Test
    fun loadMore_doesNotLoadWhenNoMorePages() = runTest(testDispatcher) {
        val repository = fullFakeRepo { _, _ ->
            MoviePageResult(PageInfo(1, 1, listOf(1)), testMovies)
        }
        val viewModel = MovieListViewModel(repository)

        viewModel.loadFirstPage()
        advanceUntilIdle()
        Thread.sleep(100)
        advanceUntilIdle()

        assertFalse(viewModel.uiState.value.hasMore)

        viewModel.loadMore()
        advanceUntilIdle()

        assertEquals(2, viewModel.uiState.value.movies.size)
        assertFalse(viewModel.uiState.value.isLoadingMore)
    }

    @Test
    fun toggleShowAll_reloadsMoviesWithShowAll() = runTest(testDispatcher) {
        var showAllCapture = false
        val repository = object : MovieRepository {
            override suspend fun loadPage(type: DataSourceType, page: Int, showAll: Boolean, forceRefresh: Boolean): MoviePageResult {
                showAllCapture = showAll
                return MoviePageResult(PageInfo(1, 2, listOf(1, 2)), testMovies, MovieFilterInfo(5, 10))
            }
            override suspend fun loadActresses(type: DataSourceType, page: Int, forceRefresh: Boolean) =
                emptyList<ActressInfo>() to PageInfo()
            override suspend fun loadGenreCategories(type: DataSourceType, forceRefresh: Boolean) = emptyList<GenreGroup>()
            override suspend fun loadPageByUrl(url: String, page: Int, showAll: Boolean, forceRefresh: Boolean) =
                MoviePageResult(PageInfo(), emptyList())
            override suspend fun loadActressDetail(url: String, forceRefresh: Boolean): ActressDetail? = null
        }
        val viewModel = MovieListViewModel(repository)

        viewModel.setDataSourceType(DataSourceType.CENSORED)
        advanceUntilIdle()
        Thread.sleep(100)
        advanceUntilIdle()
        assertFalse(showAllCapture)

        viewModel.toggleShowAll()
        // Immediately after toggle: isFilterSwitching should be true, movies NOT cleared
        assertTrue(viewModel.uiState.value.isFilterSwitching)
        assertEquals(2, viewModel.uiState.value.movies.size) // old movies still present
        assertTrue(viewModel.uiState.value.showAll)

        advanceUntilIdle()
        Thread.sleep(100)
        advanceUntilIdle()
        assertTrue(showAllCapture)
        assertFalse(viewModel.uiState.value.isFilterSwitching)
        assertEquals(5, viewModel.uiState.value.filterInfo?.magnetCount)
    }

    @Test
    fun revalidate_awayFromTop_keepsVisibleDataAndStoresPendingFresh() = runTest(testDispatcher) {
        val initial = MoviePageResult(PageInfo(1, 2), testMovies)
        val freshMovies = testMovies + Movie("Movie 3", "img3", "GHI-003", "2024-01-03", "link3")
        val fresh = MoviePageResult(PageInfo(1, 2), freshMovies)
        val revalidateArgs = mutableListOf<Boolean>()
        val repository = flowFakeRepo(
            mutableListOf(
                flow { emit(CachedLoadEvent.Fresh(CacheEntry(initial, 1L, CacheSource.Network, false))) },
                flow {
                    emit(CachedLoadEvent.Cached(CacheEntry(initial, 1L, CacheSource.Memory, true)))
                    emit(CachedLoadEvent.Fresh(CacheEntry(fresh, 2L, CacheSource.Network, false)))
                }
            ),
            revalidateArgs
        )
        val viewModel = MovieListViewModel(repository)
        viewModel.loadFirstPage()
        advanceUntilIdle()
        viewModel.setAtTopForFreshUpdates(false)

        viewModel.revalidate()
        advanceUntilIdle()

        assertEquals(2, viewModel.uiState.value.movies.size)
        assertEquals(3, viewModel.uiState.value.pendingFreshResult?.movies?.size)
        assertEquals(R.string.new_data_available, viewModel.uiState.value.refreshMessage)
        assertEquals(listOf(false, false), revalidateArgs)
    }

    @Test
    fun revalidate_atTop_appliesFreshImmediately() = runTest(testDispatcher) {
        val initial = MoviePageResult(PageInfo(1, 2), testMovies)
        val freshMovies = testMovies + Movie("Movie 3", "img3", "GHI-003", "2024-01-03", "link3")
        val fresh = MoviePageResult(PageInfo(1, 2), freshMovies)
        val repository = flowFakeRepo(
            mutableListOf(
                flow { emit(CachedLoadEvent.Fresh(CacheEntry(initial, 1L, CacheSource.Network, false))) },
                flow {
                    emit(CachedLoadEvent.Cached(CacheEntry(initial, 1L, CacheSource.Memory, true)))
                    emit(CachedLoadEvent.Fresh(CacheEntry(fresh, 2L, CacheSource.Network, false)))
                }
            ),
            mutableListOf()
        )
        val viewModel = MovieListViewModel(repository)
        viewModel.loadFirstPage()
        advanceUntilIdle()

        viewModel.revalidate()
        advanceUntilIdle()

        assertEquals(3, viewModel.uiState.value.movies.size)
        assertNull(viewModel.uiState.value.pendingFreshResult)
        assertNull(viewModel.uiState.value.refreshMessage)
    }
}

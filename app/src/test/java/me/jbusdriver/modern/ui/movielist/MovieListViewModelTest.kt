package me.jbusdriver.modern.ui.movielist

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import me.jbusdriver.modern.data.MovieRepository
import me.jbusdriver.modern.data.model.MoviePageResult
import me.jbusdriver.modern.data.model.PageInfo
import me.jbusdriver.modern.ui.MovieUiModel
import me.jbusdriver.mvp.bean.Movie
import me.jbusdriver.ui.data.enums.DataSourceType
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class MovieListViewModelTest {

    private val testDispatcher = StandardTestDispatcher()

    @Before
    fun setup() {
        Dispatchers.setMain(testDispatcher)
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    private val testMovies = listOf(
        Movie("Test Movie 1", "http://img1.jpg", "ABC-001", "2024-01-01", "http://link1"),
        Movie("Test Movie 2", "http://img2.jpg", "DEF-002", "2024-01-02", "http://link2")
    )

    private val testMovieUiModels = testMovies.map {
        MovieUiModel(it.title, it.imageUrl, it.code, it.date, it.link)
    }

    private fun createViewModel(repository: MovieRepository): MovieListViewModel {
        return MovieListViewModel(repository)
    }

    @Test
    fun loadFirstPage_loadsMoviesAndPageInfo() = runTest(testDispatcher) {
        val repository = object : MovieRepository {
            override suspend fun loadPage(type: DataSourceType, page: Int, showAll: Boolean) =
                MoviePageResult(PageInfo(1, 2, listOf(1, 2)), testMovies)
        }
        val viewModel = createViewModel(repository)

        viewModel.loadFirstPage()
        advanceUntilIdle()

        assertEquals(testMovieUiModels, viewModel.uiState.value.movies)
        assertEquals(1, viewModel.uiState.value.pageInfo.activePage)
        assertTrue(viewModel.uiState.value.hasMore)
        assertFalse(viewModel.uiState.value.isLoading)
        assertNull(viewModel.uiState.value.error)
    }

    @Test
    fun loadMore_appendsMovies() = runTest(testDispatcher) {
        val page2Movies = listOf(
            Movie("Movie 3", "http://img3.jpg", "GHI-003", "2024-01-03", "http://link3")
        )
        val page2UiModels = page2Movies.map {
            MovieUiModel(it.title, it.imageUrl, it.code, it.date, it.link)
        }
        var callCount = 0
        val repository = object : MovieRepository {
            override suspend fun loadPage(type: DataSourceType, page: Int, showAll: Boolean) =
                when (page) {
                    1 -> MoviePageResult(PageInfo(1, 2, listOf(1, 2)), testMovies).also { callCount++ }
                    else -> MoviePageResult(PageInfo(2, 3, listOf(1, 2, 3)), page2Movies).also { callCount++ }
                }
        }
        val viewModel = createViewModel(repository)

        viewModel.loadFirstPage()
        advanceUntilIdle()
        assertEquals(1, callCount)

        viewModel.loadMore()
        advanceUntilIdle()
        assertEquals(2, callCount)

        assertEquals(testMovieUiModels + page2UiModels, viewModel.uiState.value.movies)
        assertEquals(2, viewModel.uiState.value.pageInfo.activePage)
        assertTrue(viewModel.uiState.value.hasMore)
    }

    @Test
    fun loadFirstPage_handlesError() = runTest(testDispatcher) {
        val repository = object : MovieRepository {
            override suspend fun loadPage(type: DataSourceType, page: Int, showAll: Boolean) =
                throw RuntimeException("Network error")
        }
        val viewModel = createViewModel(repository)

        viewModel.loadFirstPage()
        advanceUntilIdle()

        assertTrue(viewModel.uiState.value.error?.contains("Network error") == true)
        assertFalse(viewModel.uiState.value.isLoading)
    }

    @Test
    fun refresh_resetsMovies() = runTest(testDispatcher) {
        var callCount = 0
        val repository = object : MovieRepository {
            override suspend fun loadPage(type: DataSourceType, page: Int, showAll: Boolean) =
                MoviePageResult(PageInfo(1, 2, listOf(1, 2)), testMovies).also { callCount++ }
        }
        val viewModel = createViewModel(repository)

        viewModel.loadFirstPage()
        advanceUntilIdle()

        viewModel.refresh()
        advanceUntilIdle()

        assertEquals(2, callCount)
        assertEquals(testMovieUiModels, viewModel.uiState.value.movies)
        assertFalse(viewModel.uiState.value.isRefreshing)
    }

    @Test
    fun loadMore_doesNotLoadWhenNoMorePages() = runTest(testDispatcher) {
        val repository = object : MovieRepository {
            override suspend fun loadPage(type: DataSourceType, page: Int, showAll: Boolean) =
                MoviePageResult(PageInfo(1, 1, listOf(1)), testMovies)
        }
        val viewModel = createViewModel(repository)

        viewModel.loadFirstPage()
        advanceUntilIdle()

        assertFalse(viewModel.uiState.value.hasMore)

        viewModel.loadMore()
        advanceUntilIdle()

        assertEquals(testMovieUiModels, viewModel.uiState.value.movies)
        assertFalse(viewModel.uiState.value.isLoadingMore)
    }
}

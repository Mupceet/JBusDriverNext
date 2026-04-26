package me.jbusdriver.modern.ui.detail

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import me.jbusdriver.modern.data.MovieDetailRepository
import me.jbusdriver.mvp.bean.Header
import me.jbusdriver.mvp.bean.MovieDetail
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class MovieDetailViewModelTest {

    private val testDispatcher = StandardTestDispatcher()

    private val testDetail = MovieDetail(
        title = "Test Movie",
        content = "Test description",
        cover = "http://cover.jpg",
        headers = listOf(Header("番号", "ABC-001", "")),
        genres = emptyList(),
        actress = emptyList(),
        imageSamples = emptyList(),
        relatedMovies = emptyList()
    )

    @Before
    fun setup() {
        Dispatchers.setMain(testDispatcher)
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun loadDetail_loadsMovieDetail() = runTest(testDispatcher) {
        val repository = object : MovieDetailRepository {
            override suspend fun getMovieDetail(url: String) = testDetail
        }
        val viewModel = MovieDetailViewModel(repository)

        viewModel.loadDetail("http://example.com/ABC-001")
        advanceUntilIdle()

        assertNotNull(viewModel.uiState.value.movieDetail)
        assertEquals("Test Movie", viewModel.uiState.value.movieDetail!!.title)
        assertFalse(viewModel.uiState.value.isLoading)
        assertNull(viewModel.uiState.value.error)
    }

    @Test
    fun loadDetail_handlesError() = runTest(testDispatcher) {
        val repository = object : MovieDetailRepository {
            override suspend fun getMovieDetail(url: String) =
                throw RuntimeException("Network error")
        }
        val viewModel = MovieDetailViewModel(repository)

        viewModel.loadDetail("http://example.com/ABC-001")
        advanceUntilIdle()

        assertTrue(viewModel.uiState.value.error?.contains("Network error") == true)
        assertFalse(viewModel.uiState.value.isLoading)
    }

    @Test
    fun refresh_reloadsDetail() = runTest(testDispatcher) {
        var callCount = 0
        val repository = object : MovieDetailRepository {
            override suspend fun getMovieDetail(url: String) = testDetail.also { callCount++ }
        }
        val viewModel = MovieDetailViewModel(repository)

        viewModel.loadDetail("http://example.com/ABC-001")
        advanceUntilIdle()
        assertEquals(1, callCount)

        viewModel.refresh()
        advanceUntilIdle()
        assertEquals(2, callCount)

        assertFalse(viewModel.uiState.value.isRefreshing)
    }
}

package me.jbusdriver.modern.ui.movielist

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import me.jbusdriver.modern.data.MovieRepository
import me.jbusdriver.modern.domain.model.ActressDetail
import me.jbusdriver.modern.domain.model.ActressInfo
import me.jbusdriver.modern.domain.model.DataSourceType
import me.jbusdriver.modern.domain.model.Movie
import me.jbusdriver.modern.domain.model.MoviePageResult
import me.jbusdriver.modern.domain.model.PageInfo
import me.jbusdriver.modern.ui.GenreUiModel
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class GenreListViewModelTest {

    private val testDispatcher = StandardTestDispatcher()

    private val testGenreCategories = listOf(
        GenreCategory("热门标签", listOf(GenreUiModel("高清", "/genre/hd"), GenreUiModel("VR", "/genre/vr")))
    )

    private fun fullFakeRepo(
        onLoadGenreCategories: (DataSourceType) -> List<GenreCategory>
    ) = object : MovieRepository {
        override suspend fun loadPage(type: DataSourceType, page: Int, showAll: Boolean, forceRefresh: Boolean) =
            MoviePageResult(PageInfo(), emptyList())
        override suspend fun loadActresses(type: DataSourceType, page: Int, forceRefresh: Boolean) =
            emptyList<ActressInfo>() to PageInfo()
        override suspend fun loadGenreCategories(type: DataSourceType, forceRefresh: Boolean) =
            onLoadGenreCategories(type)
        override suspend fun loadPageByUrl(url: String, page: Int, forceRefresh: Boolean) =
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
    fun setDataSourceType_loadsGenres() = runTest(testDispatcher) {
        val repository = fullFakeRepo { testGenreCategories }
        val viewModel = GenreListViewModel(repository)

        viewModel.setDataSourceType(DataSourceType.GENRE)
        advanceUntilIdle()
        Thread.sleep(100)
        advanceUntilIdle()

        assertEquals(1, viewModel.uiState.value.genreCategories.size)
        assertEquals("热门标签", viewModel.uiState.value.genreCategories.first().title)
        assertFalse(viewModel.uiState.value.isLoading)
        assertNull(viewModel.uiState.value.error)
    }

    @Test
    fun setDataSourceType_handlesError() = runTest(testDispatcher) {
        val repository = fullFakeRepo { throw RuntimeException("Network error") }
        val viewModel = GenreListViewModel(repository)

        viewModel.setDataSourceType(DataSourceType.GENRE)
        advanceUntilIdle()
        Thread.sleep(100)
        advanceUntilIdle()

        assertTrue(viewModel.uiState.value.error?.contains("Network error") == true)
        assertFalse(viewModel.uiState.value.isLoading)
    }

    @Test
    fun refresh_reloadsGenres() = runTest(testDispatcher) {
        var callCount = 0
        val repository = fullFakeRepo {
            callCount++
            testGenreCategories
        }
        val viewModel = GenreListViewModel(repository)

        viewModel.setDataSourceType(DataSourceType.GENRE)
        advanceUntilIdle()
        Thread.sleep(100)
        advanceUntilIdle()

        viewModel.refresh()
        advanceUntilIdle()
        Thread.sleep(100)
        advanceUntilIdle()

        assertEquals(2, callCount)
        assertFalse(viewModel.uiState.value.isRefreshing)
    }
}

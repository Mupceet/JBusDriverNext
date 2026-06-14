package me.jbusdriver.modern.ui.movielist

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import me.jbusdriver.R
import me.jbusdriver.modern.data.MovieRepository
import me.jbusdriver.modern.domain.model.ActressDetail
import me.jbusdriver.modern.domain.model.ActressInfo
import me.jbusdriver.modern.domain.model.DataSourceType
import me.jbusdriver.modern.domain.model.Genre
import me.jbusdriver.modern.domain.model.GenreGroup
import me.jbusdriver.modern.domain.model.MoviePageResult
import me.jbusdriver.modern.domain.model.PageInfo
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class GenreListViewModelTest {

    private lateinit var testDispatcher: TestDispatcher

    private val testGenreCategories = listOf(
        GenreGroup("热门标签", listOf(Genre("高清", "/genre/hd"), Genre("VR", "/genre/vr")))
    )

    private fun fullFakeRepo(
        onLoadGenreCategories: (DataSourceType) -> List<GenreGroup>
    ) = object : MovieRepository {
        override suspend fun loadPage(
            type: DataSourceType,
            page: Int,
            showAll: Boolean,
            forceRefresh: Boolean
        ) =
            MoviePageResult(PageInfo(), emptyList())

        override suspend fun loadActresses(type: DataSourceType, page: Int, forceRefresh: Boolean) =
            emptyList<ActressInfo>() to PageInfo()

        override suspend fun loadGenreCategories(type: DataSourceType, forceRefresh: Boolean) =
            onLoadGenreCategories(type)

        override suspend fun loadPageByUrl(
            url: String,
            page: Int,
            showAll: Boolean,
            forceRefresh: Boolean
        ) =
            MoviePageResult(PageInfo(), emptyList())

        override suspend fun loadActressDetail(url: String, forceRefresh: Boolean): ActressDetail? =
            null
    }

    @Before
    fun setup() {
        testDispatcher = StandardTestDispatcher()
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

        assertEquals(R.string.load_failed, viewModel.uiState.value.error)
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

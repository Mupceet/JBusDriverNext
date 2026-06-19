package me.jbusdriver.modern.ui.movielist

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import me.jbusdriver.R
import me.jbusdriver.modern.core.cache.CacheEntry
import me.jbusdriver.modern.core.cache.CacheSource
import me.jbusdriver.modern.core.cache.CachedLoadEvent
import me.jbusdriver.modern.data.repository.MovieRepository
import me.jbusdriver.modern.domain.model.ActressDetail
import me.jbusdriver.modern.domain.model.ActressInfo
import me.jbusdriver.modern.domain.model.DataSourceType
import me.jbusdriver.modern.domain.model.GenreGroup
import me.jbusdriver.modern.domain.model.MoviePageResult
import me.jbusdriver.modern.domain.model.PageInfo
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class ActressListViewModelTest {

    private lateinit var testDispatcher: TestDispatcher

    private val testActresses = listOf(
        ActressInfo("Alice", "http://avatar1.jpg", "http://link1"),
        ActressInfo("Bob", "http://avatar2.jpg", "http://link2")
    )

    private fun fullFakeRepo(
        onLoadActresses: (DataSourceType, Int) -> Pair<List<ActressInfo>, PageInfo>
    ) = object : MovieRepository {
        override suspend fun loadPage(
            type: DataSourceType,
            page: Int,
            showAll: Boolean,
            forceRefresh: Boolean
        ) =
            MoviePageResult(PageInfo(), emptyList())

        override suspend fun loadActresses(type: DataSourceType, page: Int, forceRefresh: Boolean) =
            onLoadActresses(type, page)

        override suspend fun loadGenreCategories(type: DataSourceType, forceRefresh: Boolean) =
            emptyList<GenreGroup>()

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
    fun setDataSourceType_loadsActresses() = runTest(testDispatcher) {
        val repository = fullFakeRepo { _, _ -> testActresses to PageInfo(1, 2) }
        val viewModel = ActressListViewModel(repository)

        viewModel.setDataSourceType(DataSourceType.ACTRESSES)
        advanceUntilIdle()
        Thread.sleep(100)
        advanceUntilIdle()

        assertEquals(2, viewModel.uiState.value.actresses.size)
        assertEquals("Alice", viewModel.uiState.value.actresses.first().name)
        assertFalse(viewModel.uiState.value.isLoading)
        assertTrue(viewModel.uiState.value.hasMore)
    }

    @Test
    fun setDataSourceType_handlesError() = runTest(testDispatcher) {
        val repository = fullFakeRepo { _, _ -> throw RuntimeException("Network error") }
        val viewModel = ActressListViewModel(repository)

        viewModel.setDataSourceType(DataSourceType.ACTRESSES)
        advanceUntilIdle()
        Thread.sleep(100)
        advanceUntilIdle()

        assertEquals(R.string.load_failed, viewModel.uiState.value.error)
        assertFalse(viewModel.uiState.value.isLoading)
    }

    @Test
    fun refresh_reloadsActresses() = runTest(testDispatcher) {
        var callCount = 0
        val repository = fullFakeRepo { _, _ ->
            callCount++
            testActresses to PageInfo(1, 2)
        }
        val viewModel = ActressListViewModel(repository)

        viewModel.setDataSourceType(DataSourceType.ACTRESSES)
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

    @Test
    fun staleRefreshResultDoesNotOverwriteDataSourceSwitch() = runTest(testDispatcher) {
        val oldRefresh = CompletableDeferred<Pair<List<ActressInfo>, PageInfo>>()
        val censored = listOf(ActressInfo("Censored", "http://avatar.jpg", "http://censored"))
        val uncensored = listOf(ActressInfo("Uncensored", "http://avatar.jpg", "http://uncensored"))
        val repository = object : MovieRepository {
            override fun observeActresses(
                type: DataSourceType,
                page: Int,
                forceRefresh: Boolean,
                revalidate: Boolean,
                nowMillis: () -> Long
            ): Flow<CachedLoadEvent<Pair<List<ActressInfo>, PageInfo>>> = flow {
                val value = when {
                    forceRefresh -> oldRefresh.await()
                    type == DataSourceType.UNCENSORED_ACTRESSES -> uncensored to PageInfo(1, 2)
                    else -> censored to PageInfo(1, 2)
                }
                emit(CachedLoadEvent.Fresh(CacheEntry(value, 1L, CacheSource.Network, false)))
            }

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
                emptyList<GenreGroup>()

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
        val viewModel = ActressListViewModel(repository)

        viewModel.setDataSourceType(DataSourceType.ACTRESSES)
        advanceUntilIdle()
        assertEquals("Censored", viewModel.uiState.value.actresses.single().name)

        viewModel.refresh()
        runCurrent()
        assertTrue(viewModel.uiState.value.isRefreshing)

        viewModel.setDataSourceType(DataSourceType.UNCENSORED_ACTRESSES)
        advanceUntilIdle()
        assertEquals("Uncensored", viewModel.uiState.value.actresses.single().name)

        oldRefresh.complete(
            listOf(ActressInfo("Stale", "http://avatar.jpg", "http://stale")) to PageInfo(1, 2)
        )
        advanceUntilIdle()

        assertEquals("Uncensored", viewModel.uiState.value.actresses.single().name)
        assertFalse(viewModel.uiState.value.isRefreshing)
    }
}

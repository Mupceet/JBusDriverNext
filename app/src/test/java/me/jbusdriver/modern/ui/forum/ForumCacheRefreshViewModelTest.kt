package me.jbusdriver.modern.ui.forum

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import me.jbusdriver.modern.core.cache.CachedLoadEvent
import me.jbusdriver.modern.core.cache.CacheEntry
import me.jbusdriver.modern.core.cache.CacheSource
import me.jbusdriver.modern.data.ForumFloorOrder
import me.jbusdriver.modern.data.ForumRepository
import me.jbusdriver.modern.domain.model.ForumHomeData
import me.jbusdriver.modern.domain.model.ForumThreadDetail
import me.jbusdriver.modern.domain.model.ForumThreadPageResult
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class ForumCacheRefreshViewModelTest {
    private val testDispatcher = UnconfinedTestDispatcher()

    @Before
    fun setup() {
        Dispatchers.setMain(testDispatcher)
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    private class FakeHomeRepository(
        private val flows: MutableList<Flow<CachedLoadEvent<ForumHomeData>>>
    ) : ForumRepository {
        private var callIndex = 0
        override fun observeForumBoards(forceRefresh: Boolean, revalidate: Boolean, nowMillis: () -> Long): Flow<CachedLoadEvent<ForumHomeData>> {
            return if (callIndex < flows.size) flows[callIndex++] else flows.last()
        }
        override fun observeThreads(fid: Int, page: Int, typeId: Int?, forceRefresh: Boolean, revalidate: Boolean, nowMillis: () -> Long): Flow<CachedLoadEvent<ForumThreadPageResult>> = flow { error("not used") }
        override fun observeThreadDetail(tid: Int, page: Int, floorOrder: ForumFloorOrder, forceRefresh: Boolean, revalidate: Boolean, nowMillis: () -> Long): Flow<CachedLoadEvent<ForumThreadDetail>> = flow { error("not used") }
        override suspend fun loadForumBoards(forceRefresh: Boolean): ForumHomeData = error("not used")
        override suspend fun loadThreads(fid: Int, page: Int, typeId: Int?, forceRefresh: Boolean): ForumThreadPageResult = error("not used")
        override suspend fun loadThreadDetail(tid: Int, page: Int, floorOrder: ForumFloorOrder, forceRefresh: Boolean): ForumThreadDetail = error("not used")
        override fun destroySession() = Unit
    }

    private fun homeData(groups: Int = 0) = ForumHomeData(
        banners = emptyList(),
        summary = me.jbusdriver.modern.domain.model.ForumHomeSummary(),
        boardGroups = List(groups) { me.jbusdriver.modern.domain.model.ForumBoardGroup("G$it", emptyList()) }
    )

    @Test
    fun `home shows cached data then updates with fresh`() = runTest(testDispatcher) {
        val cached = homeData(0)
        val fresh = homeData(1)
        val repository = FakeHomeRepository(mutableListOf(flow {
            emit(CachedLoadEvent.Cached(CacheEntry(cached, 1_000L, CacheSource.Disk, false)))
            emit(CachedLoadEvent.Fresh(CacheEntry(fresh, 2_000L, CacheSource.Network, false)))
        }))
        val viewModel = ForumBoardsViewModel(repository)
        advanceUntilIdle()

        val state = viewModel.uiState.value
        assertEquals(1, state.groups.size)
        assertFalse(state.isLoading)
        assertFalse(state.isRevalidating)
    }

    @Test
    fun `home keeps cached data on background failure`() = runTest(testDispatcher) {
        val cached = homeData(1)
        val repository = FakeHomeRepository(mutableListOf(flow {
            emit(CachedLoadEvent.Cached(CacheEntry(cached, 1_000L, CacheSource.Disk, true)))
            emit(CachedLoadEvent.Failure(IllegalStateException("offline"), hadCachedValue = true))
        }))
        val viewModel = ForumBoardsViewModel(repository)
        advanceUntilIdle()

        val state = viewModel.uiState.value
        assertEquals(1, state.groups.size)
        assertNull(state.error)
        assertFalse(state.isRevalidating)
    }

    @Test
    fun `home shows error when no cache and fetch fails`() = runTest(testDispatcher) {
        val repository = FakeHomeRepository(mutableListOf(flow {
            emit(CachedLoadEvent.Failure(IllegalStateException("offline"), hadCachedValue = false))
        }))
        val viewModel = ForumBoardsViewModel(repository)
        advanceUntilIdle()

        val state = viewModel.uiState.value
        assertTrue(state.groups.isEmpty())
        assertEquals("offline", state.error)
    }

    @Test
    fun `home refresh replaces with fresh data`() = runTest(testDispatcher) {
        val initial = homeData(1)
        val refreshed = homeData(2)
        val repository = FakeHomeRepository(mutableListOf(
            flow { emit(CachedLoadEvent.Fresh(CacheEntry(initial, 1_000L, CacheSource.Network, false))) },
            flow { emit(CachedLoadEvent.Fresh(CacheEntry(refreshed, 2_000L, CacheSource.Network, false))) }
        ))
        val viewModel = ForumBoardsViewModel(repository)
        advanceUntilIdle()

        assertEquals(1, viewModel.uiState.value.groups.size)

        viewModel.refresh()
        advanceUntilIdle()

        val state = viewModel.uiState.value
        assertEquals(2, state.groups.size)
        assertFalse(state.isRefreshing)
    }
}
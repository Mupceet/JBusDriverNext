package me.jbusdriver.modern.ui.forum

import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import me.jbusdriver.modern.core.cache.CacheEntry
import me.jbusdriver.modern.core.cache.CacheSource
import me.jbusdriver.modern.core.cache.CachedLoadEvent
import me.jbusdriver.modern.core.site.SiteConfig
import me.jbusdriver.modern.data.ForumFloorOrder
import me.jbusdriver.modern.data.ForumRepository
import me.jbusdriver.modern.data.ForumSettingsReader
import me.jbusdriver.modern.data.LoadedGifTracker
import me.jbusdriver.modern.domain.model.ForumHomeData
import me.jbusdriver.modern.domain.model.ForumThreadDetail
import me.jbusdriver.modern.domain.model.ForumThreadPageResult
import me.jbusdriver.modern.domain.model.PageInfo
import me.jbusdriver.modern.ui.RouteForumThreadDetail
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class ForumThreadDetailViewModelTest {
    private lateinit var testDispatcher: TestDispatcher

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
    fun `stale refresh result does not overwrite floor order switch`() = runTest(testDispatcher) {
        val staleRefresh = CompletableDeferred<ForumThreadDetail>()
        val repository = FakeForumDetailRepository(staleRefresh)
        val viewModel = ForumThreadDetailViewModel(
            repository = repository,
            forumSettingsReader = FakeForumSettingsReader(),
            loadedGifTracker = FakeLoadedGifTracker(),
            siteConfig = FakeSiteConfig("https://forum.example.test/root"),
            navKey = RouteForumThreadDetail(42)
        )
        advanceUntilIdle()
        assertEquals("Regular", viewModel.uiState.value.detail?.title)

        viewModel.refresh()
        runCurrent()
        assertEquals(true, viewModel.uiState.value.isRefreshing)

        viewModel.setFloorOrder(ForumFloorOrder.REVERSE)
        advanceUntilIdle()
        assertEquals(ForumFloorOrder.REVERSE, viewModel.uiState.value.floorOrder)
        assertEquals("Reverse", viewModel.uiState.value.detail?.title)

        staleRefresh.complete(detail("Stale Regular", ForumFloorOrder.REGULAR))
        advanceUntilIdle()

        assertEquals(ForumFloorOrder.REVERSE, viewModel.uiState.value.floorOrder)
        assertEquals("Reverse", viewModel.uiState.value.detail?.title)
        assertFalse(viewModel.uiState.value.isRefreshing)
    }

    @Test
    fun `share thread url uses injected site config`() = runTest(testDispatcher) {
        val viewModel = ForumThreadDetailViewModel(
            repository = FakeForumDetailRepository(CompletableDeferred()),
            forumSettingsReader = FakeForumSettingsReader(),
            loadedGifTracker = FakeLoadedGifTracker(),
            siteConfig = FakeSiteConfig("https://Mirror.Example.test/base/"),
            navKey = RouteForumThreadDetail(42)
        )

        assertEquals(
            "https://Mirror.Example.test/base/forum/forum.php?mod=viewthread&tid=42",
            viewModel.shareThreadUrl
        )
    }

    private class FakeForumDetailRepository(
        private val staleRefresh: CompletableDeferred<ForumThreadDetail>
    ) : ForumRepository {
        override fun observeThreadDetail(
            tid: Int,
            page: Int,
            floorOrder: ForumFloorOrder,
            forceRefresh: Boolean,
            revalidate: Boolean,
            nowMillis: () -> Long
        ): Flow<CachedLoadEvent<ForumThreadDetail>> = flow {
            val next = when {
                forceRefresh && floorOrder == ForumFloorOrder.REGULAR -> staleRefresh.await()
                floorOrder == ForumFloorOrder.REVERSE -> detail("Reverse", floorOrder)
                else -> detail("Regular", floorOrder)
            }
            emit(CachedLoadEvent.Fresh(CacheEntry(next, page.toLong(), CacheSource.Network, false)))
        }

        override suspend fun loadThreadDetail(
            tid: Int,
            page: Int,
            floorOrder: ForumFloorOrder,
            forceRefresh: Boolean
        ): ForumThreadDetail = detail("Page $page", floorOrder, page)

        override fun observeForumBoards(
            forceRefresh: Boolean,
            revalidate: Boolean,
            nowMillis: () -> Long
        ): Flow<CachedLoadEvent<ForumHomeData>> = flow { error("not used") }

        override fun observeThreads(
            fid: Int,
            page: Int,
            typeId: Int?,
            forceRefresh: Boolean,
            revalidate: Boolean,
            nowMillis: () -> Long
        ): Flow<CachedLoadEvent<ForumThreadPageResult>> = flow { error("not used") }

        override suspend fun loadForumBoards(forceRefresh: Boolean): ForumHomeData =
            error("not used")

        override suspend fun loadThreads(
            fid: Int,
            page: Int,
            typeId: Int?,
            forceRefresh: Boolean
        ): ForumThreadPageResult = error("not used")
    }

    private class FakeForumSettingsReader : ForumSettingsReader {
        override val autoLoadGifs: StateFlow<Boolean> = MutableStateFlow(false)
        override suspend fun currentForumFloorOrder(): ForumFloorOrder = ForumFloorOrder.REGULAR
    }

    private class FakeLoadedGifTracker : LoadedGifTracker {
        override suspend fun loadedUrls(): Set<String> = emptySet()
        override suspend fun markLoaded(url: String) = Unit
    }

    private class FakeSiteConfig(initialBaseUrl: String) : SiteConfig {
        override var baseUrl: String = initialBaseUrl
        override fun resolve(pathOrUrl: String): String =
            baseUrl.trimEnd('/') + if (pathOrUrl.startsWith("/")) pathOrUrl else "/$pathOrUrl"
    }
}

private fun detail(
    title: String,
    floorOrder: ForumFloorOrder,
    page: Int = 1
): ForumThreadDetail = ForumThreadDetail(
    tid = 42,
    typeId = 0,
    typeName = floorOrder.name,
    typeColor = "",
    title = title,
    viewCount = 0,
    replyCount = 0,
    author = "",
    authorUid = 0,
    authorAvatar = "",
    postTime = "",
    contentBlocks = emptyList(),
    comments = emptyList(),
    replies = emptyList(),
    pageInfo = PageInfo(activePage = page, nextPage = page, referPages = listOf(page))
)

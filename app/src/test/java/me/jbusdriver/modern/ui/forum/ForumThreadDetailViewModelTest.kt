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
import me.jbusdriver.R
import me.jbusdriver.modern.core.cache.CacheEntry
import me.jbusdriver.modern.core.cache.CacheSource
import me.jbusdriver.modern.core.cache.CachedLoadEvent
import me.jbusdriver.modern.core.site.SiteConfig
import me.jbusdriver.modern.data.settings.ForumFloorOrder
import me.jbusdriver.modern.data.repository.ForumRepository
import me.jbusdriver.modern.data.settings.ForumSettingsReader
import me.jbusdriver.modern.data.session.LoadedGifTracker
import me.jbusdriver.modern.domain.model.Comment
import me.jbusdriver.modern.domain.model.ContentBlock
import me.jbusdriver.modern.domain.model.ForumCommentPageResult
import me.jbusdriver.modern.domain.model.ForumHomeData
import me.jbusdriver.modern.domain.model.ForumReply
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

    @Test
    fun `openReplyCommentsSheet uses parsed reply comments and content for floor`() = runTest(testDispatcher) {
        val viewModel = ForumThreadDetailViewModel(
            repository = FakeForumDetailRepository(CompletableDeferred()),
            forumSettingsReader = FakeForumSettingsReader(),
            loadedGifTracker = FakeLoadedGifTracker(),
            siteConfig = FakeSiteConfig("https://forum.example.test/root"),
            navKey = RouteForumThreadDetail(42)
        )
        advanceUntilIdle()

        viewModel.openReplyCommentsSheet(2)

        val sheet = viewModel.uiState.value.commentSheet
        assertEquals(4773820, sheet?.pid)
        assertEquals(2, sheet?.floor)
        assertEquals("Reply Author", sheet?.author)
        assertEquals(listOf(replyBlock), sheet?.contentBlocks)
        assertEquals(listOf(comment("inline reply comment")), sheet?.comments)
        assertEquals(PageInfo(activePage = 1, nextPage = 2), sheet?.pageInfo)
    }

    @Test
    fun `floor comment sheet state does not expose localized floor label`() {
        assertFalse(
            FloorCommentSheetState::class.java.declaredFields.any { it.name == "floorLabel" }
        )
    }

    @Test
    fun `loadMoreFloorComments appends comments and updates pageInfo`() = runTest(testDispatcher) {
        val repository = FakeForumDetailRepository(CompletableDeferred()).apply {
            floorCommentResult = ForumCommentPageResult(
                pid = 4773820,
                comments = listOf(comment("page two reply comment")),
                pageInfo = PageInfo(activePage = 2, nextPage = 3)
            )
        }
        val viewModel = ForumThreadDetailViewModel(
            repository = repository,
            forumSettingsReader = FakeForumSettingsReader(),
            loadedGifTracker = FakeLoadedGifTracker(),
            siteConfig = FakeSiteConfig("https://forum.example.test/root"),
            navKey = RouteForumThreadDetail(42)
        )
        advanceUntilIdle()
        viewModel.openReplyCommentsSheet(2)

        viewModel.loadMoreFloorComments()
        advanceUntilIdle()

        assertEquals(listOf(FloorCommentRequest(tid = 42, pid = 4773820, page = 2)), repository.floorCommentRequests)
        val sheet = viewModel.uiState.value.commentSheet
        assertEquals(
            listOf(comment("inline reply comment"), comment("page two reply comment")),
            sheet?.comments
        )
        assertEquals(PageInfo(activePage = 2, nextPage = 3), sheet?.pageInfo)
        assertEquals(false, sheet?.isLoadingMore)
        assertEquals(null, sheet?.error)
    }

    @Test
    fun `loadMoreFloorComments failure keeps comments visible and exposes retryable error`() = runTest(testDispatcher) {
        val repository = FakeForumDetailRepository(CompletableDeferred()).apply {
            floorCommentFailure = IllegalStateException("network failed")
        }
        val viewModel = ForumThreadDetailViewModel(
            repository = repository,
            forumSettingsReader = FakeForumSettingsReader(),
            loadedGifTracker = FakeLoadedGifTracker(),
            siteConfig = FakeSiteConfig("https://forum.example.test/root"),
            navKey = RouteForumThreadDetail(42)
        )
        advanceUntilIdle()
        viewModel.openReplyCommentsSheet(2)

        viewModel.loadMoreFloorComments()
        advanceUntilIdle()

        val sheet = viewModel.uiState.value.commentSheet
        assertEquals(listOf(comment("inline reply comment")), sheet?.comments)
        assertEquals(PageInfo(activePage = 1, nextPage = 2), sheet?.pageInfo)
        assertEquals(false, sheet?.isLoadingMore)
        assertEquals(R.string.load_failed, sheet?.error)
    }

    @Test
    fun `loadMoreFloorComments ignores stale same pid completion after dismiss and reopen`() = runTest(testDispatcher) {
        val stalePageTwo = CompletableDeferred<ForumCommentPageResult>()
        val currentPageTwo = CompletableDeferred<ForumCommentPageResult>()
        val repository = FakeForumDetailRepository(CompletableDeferred()).apply {
            deferredFloorCommentResults += stalePageTwo
            deferredFloorCommentResults += currentPageTwo
        }
        val viewModel = ForumThreadDetailViewModel(
            repository = repository,
            forumSettingsReader = FakeForumSettingsReader(),
            loadedGifTracker = FakeLoadedGifTracker(),
            siteConfig = FakeSiteConfig("https://forum.example.test/root"),
            navKey = RouteForumThreadDetail(42)
        )
        advanceUntilIdle()

        viewModel.openReplyCommentsSheet(2)
        viewModel.loadMoreFloorComments()
        runCurrent()
        viewModel.dismissCommentsSheet()
        viewModel.openReplyCommentsSheet(2)
        viewModel.loadMoreFloorComments()
        runCurrent()

        assertEquals(
            listOf(
                FloorCommentRequest(tid = 42, pid = 4773820, page = 2),
                FloorCommentRequest(tid = 42, pid = 4773820, page = 2)
            ),
            repository.floorCommentRequests
        )

        stalePageTwo.complete(
            ForumCommentPageResult(
                pid = 4773820,
                comments = listOf(comment("stale page two reply comment")),
                pageInfo = PageInfo(activePage = 2, nextPage = 2)
            )
        )
        advanceUntilIdle()

        assertEquals(listOf(comment("inline reply comment")), viewModel.uiState.value.commentSheet?.comments)
        assertEquals(true, viewModel.uiState.value.commentSheet?.isLoadingMore)

        currentPageTwo.complete(
            ForumCommentPageResult(
                pid = 4773820,
                comments = listOf(comment("current page two reply comment")),
                pageInfo = PageInfo(activePage = 2, nextPage = 2)
            )
        )
        advanceUntilIdle()

        val sheet = viewModel.uiState.value.commentSheet
        assertEquals(
            listOf(comment("inline reply comment"), comment("current page two reply comment")),
            sheet?.comments
        )
        assertEquals(PageInfo(activePage = 2, nextPage = 2), sheet?.pageInfo)
        assertEquals(false, sheet?.isLoadingMore)
    }

    private class FakeForumDetailRepository(
        private val staleRefresh: CompletableDeferred<ForumThreadDetail>
    ) : ForumRepository {
        var floorCommentResult: ForumCommentPageResult = ForumCommentPageResult(
            pid = 4773820,
            comments = listOf(comment("page two reply comment")),
            pageInfo = PageInfo(activePage = 2, nextPage = 2)
        )
        var floorCommentFailure: Throwable? = null
        val deferredFloorCommentResults = mutableListOf<CompletableDeferred<ForumCommentPageResult>>()
        val floorCommentRequests = mutableListOf<FloorCommentRequest>()

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

        override suspend fun loadFloorComments(
            tid: Int,
            pid: Int,
            page: Int,
            forceRefresh: Boolean
        ): ForumCommentPageResult {
            floorCommentRequests += FloorCommentRequest(tid, pid, page)
            floorCommentFailure?.let { throw it }
            if (deferredFloorCommentResults.isNotEmpty()) {
                return deferredFloorCommentResults.removeAt(0).await()
            }
            return floorCommentResult
        }

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

private data class FloorCommentRequest(
    val tid: Int,
    val pid: Int,
    val page: Int
)

private val firstPostBlock = ContentBlock.RichText(emptyList())
private val replyBlock = ContentBlock.RichText(emptyList())

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
    author = "Original Poster",
    authorUid = 0,
    authorAvatar = "",
    postTime = "",
    contentBlocks = listOf(firstPostBlock),
    comments = listOf(comment("inline first post comment")),
    replies = listOf(
        ForumReply(
            floor = 2,
            author = "Reply Author",
            authorUid = 0,
            authorAvatar = "",
            authorGroup = "",
            contentBlocks = listOf(replyBlock),
            postTime = "",
            pid = 4773820,
            comments = listOf(comment("inline reply comment")),
            commentPageInfo = PageInfo(activePage = 1, nextPage = 2)
        )
    ),
    pageInfo = PageInfo(activePage = page, nextPage = page, referPages = listOf(page)),
    pid = 4773811,
    commentPageInfo = PageInfo(activePage = 1, nextPage = 2)
)

private fun comment(content: String): Comment =
    Comment(
        author = "Commenter",
        authorAvatar = "",
        content = content,
        time = "2026-6-10"
    )

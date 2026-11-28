package me.jbusdriver.modern.ui.forum.account

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.TestDispatcher
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import me.jbusdriver.R
import me.jbusdriver.modern.core.cache.CacheEntry
import me.jbusdriver.modern.core.cache.CacheSource
import me.jbusdriver.modern.core.cache.CachedLoadEvent
import me.jbusdriver.modern.data.repository.ForumRepository
import me.jbusdriver.modern.data.settings.ForumFloorOrder
import me.jbusdriver.modern.data.settings.ForumThreadOrder
import me.jbusdriver.modern.domain.model.ForumCommentPageResult
import me.jbusdriver.modern.domain.model.ForumFavoritePageResult
import me.jbusdriver.modern.domain.model.ForumFavoriteThread
import me.jbusdriver.modern.domain.model.ForumHomeData
import me.jbusdriver.modern.domain.model.ForumLoginChallenge
import me.jbusdriver.modern.domain.model.ForumLoginState
import me.jbusdriver.modern.domain.model.ForumThreadDetail
import me.jbusdriver.modern.domain.model.ForumThreadPageResult
import me.jbusdriver.modern.domain.model.ForumUserProfile
import me.jbusdriver.modern.domain.model.ForumUserThread
import me.jbusdriver.modern.domain.model.ForumUserThreadPageResult
import me.jbusdriver.modern.domain.model.ForumUserThreadType
import me.jbusdriver.modern.domain.model.PageInfo
import me.jbusdriver.modern.ui.RouteUserSpace
import me.jbusdriver.modern.ui.UserMessage
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

/**
 * 个人主页 ViewModel：页签懒加载、首页 SWR、按分页信息翻页、失败态与整页刷新的收敛。
 * 所有首页观察流都同步完成，因此 Main 用 Unconfined 调度器即可得到确定性的终态。
 */
@OptIn(ExperimentalCoroutinesApi::class)
class UserSpaceViewModelTest {
    private lateinit var dispatcher: TestDispatcher

    @Before
    fun setUp() {
        dispatcher = UnconfinedTestDispatcher()
        Dispatchers.setMain(dispatcher)
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun `cached profile and threads render first, then fresh replaces them`() {
        val repository = repository().apply {
            profileFlow = {
                flow {
                    emit(cached(profile("cached")))
                    emit(fresh(profile("fresh")))
                }
            }
            threadsFlow = { _, _ ->
                flow {
                    emit(cached(page(next = 2, ids = intArrayOf(1, 2))))
                    emit(fresh(page(next = 2, ids = intArrayOf(1, 2, 3))))
                }
            }
        }

        val state = UserSpaceViewModel(repository, RouteUserSpace(UID)).uiState.value

        assertEquals("fresh", state.profile?.username)
        assertFalse(state.profileLoading)
        assertEquals(listOf(1, 2, 3), state.threads.tids)
        assertFalse(state.threads.isLoading)
        assertFalse(state.threads.endReached)
        assertEquals("next page comes from pagination info", 2, state.threads.nextPage)
    }

    @Test
    fun `replies load once on first selection and survive tab switches`() {
        val repository = repository().apply {
            threadsFlow = { type, _ ->
                flowOf(fresh(page(next = 0, ids = intArrayOf(1, 2))))
            }
        }
        val viewModel = UserSpaceViewModel(repository, RouteUserSpace(UID))

        viewModel.selectTab(UserSpaceTab.REPLIES)
        viewModel.selectTab(UserSpaceTab.THREADS)
        viewModel.selectTab(UserSpaceTab.REPLIES)

        assertEquals(UserSpaceTab.REPLIES, viewModel.uiState.value.selectedTab)
        assertEquals(listOf(1, 2), viewModel.uiState.value.replies.tids)
        assertEquals(
            "switching back must not refetch an already loaded tab",
            1,
            repository.observed.count { it == "threads:reply|false" }
        )
    }

    @Test
    fun `load more appends the page announced by pagination, dedupes rows and stops at the end`() {
        val repository = repository().apply {
            threadsFlow = { _, _ -> flowOf(fresh(page(next = 2, ids = intArrayOf(1, 2, 3)))) }
            loadThreadsPage = { type, page ->
                check(type == ForumUserThreadType.THREAD && page == 2)
                // 站点翻页会重复上一页末行；分页信息显示已是最后一页
                ForumUserThreadPageResult(
                    listOf(thread(3), thread(4), thread(5)),
                    PageInfo(activePage = 2, nextPage = 2)
                )
            }
        }
        val viewModel = UserSpaceViewModel(repository, RouteUserSpace(UID))

        viewModel.loadMore()

        val threads = viewModel.uiState.value.threads
        assertEquals(listOf(1, 2, 3, 4, 5), threads.tids)
        assertTrue(threads.endReached)
        assertFalse(threads.isLoadingMore)
        viewModel.loadMore()
        assertEquals("no request after the end is reached", listOf(2), repository.requestedPages)
    }

    @Test
    fun `load more failure keeps rows and retry requests the same page`() {
        var failing = true
        val repository = repository().apply {
            threadsFlow = { _, _ -> flowOf(fresh(page(next = 2, ids = intArrayOf(1, 2)))) }
            loadThreadsPage = { _, _ ->
                if (failing) throw IllegalStateException("boom")
                ForumUserThreadPageResult(listOf(thread(3)), PageInfo(activePage = 2, nextPage = 0))
            }
        }
        val viewModel = UserSpaceViewModel(repository, RouteUserSpace(UID))

        viewModel.loadMore()

        val failed = viewModel.uiState.value.threads
        assertEquals(listOf(1, 2), failed.tids)
        assertTrue(failed.error)
        assertFalse(failed.isLoadingMore)

        failing = false
        viewModel.retry()

        val recovered = viewModel.uiState.value.threads
        assertEquals(listOf(1, 2, 3), recovered.tids)
        assertFalse(recovered.error)
        assertTrue(recovered.endReached)
        assertEquals("retry replays the failed page", listOf(2, 2), repository.requestedPages)
    }

    @Test
    fun `first page failure flags error only when there is no content to show`() {
        val failure = CachedLoadEvent.Failure(IllegalStateException("boom"), hadCachedValue = false)
        val withContent = UserSpaceViewModel(
            repository().apply {
                threadsFlow = { _, _ ->
                    flow {
                        emit(cached(page(next = 0, ids = intArrayOf(1))))
                        emit(failure)
                    }
                }
            },
            RouteUserSpace(UID)
        ).uiState.value.threads
        assertEquals(listOf(1), withContent.tids)
        assertFalse(withContent.error)
        assertFalse(withContent.isLoading)

        val withoutContent = UserSpaceViewModel(
            repository().apply { threadsFlow = { _, _ -> flowOf(failure) } },
            RouteUserSpace(UID)
        ).uiState.value.threads
        assertTrue(withoutContent.items.isEmpty())
        assertTrue(withoutContent.error)
        assertFalse(withoutContent.isLoading)
    }

    @Test
    fun `profile failure leaves loading and retry recovers`() {
        val repository = repository().apply {
            profileFlow = { flowOf(CachedLoadEvent.Failure(IllegalStateException("boom"), false)) }
        }
        val viewModel = UserSpaceViewModel(repository, RouteUserSpace(UID))

        assertNull(viewModel.uiState.value.profile)
        assertFalse(viewModel.uiState.value.profileLoading)

        repository.profileFlow = { flowOf(fresh(profile("recovered"))) }
        viewModel.retry()

        assertEquals("recovered", viewModel.uiState.value.profile?.username)
    }

    @Test
    fun `favorites tab is rejected for other users`() {
        val repository = repository()
        val viewModel = UserSpaceViewModel(repository, RouteUserSpace(UID))

        viewModel.selectTab(UserSpaceTab.FAVORITES)

        assertFalse(viewModel.uiState.value.isSelf)
        assertEquals(UserSpaceTab.THREADS, viewModel.uiState.value.selectedTab)
        assertTrue(repository.observed.none { it.startsWith("favorites") })
    }

    @Test
    fun `favorites load for the logged-in user viewing their own page`() {
        val repository = repository(loggedInAs = UID).apply {
            favoritesFlow = {
                flowOf(
                    fresh(
                        ForumFavoritePageResult(
                            listOf(ForumFavoriteThread(favId = 11L, tid = 5, title = "f", time = "")),
                            PageInfo(activePage = 1, nextPage = 0)
                        )
                    )
                )
            }
        }
        val viewModel = UserSpaceViewModel(repository, RouteUserSpace(UID))

        assertTrue(viewModel.uiState.value.isSelf)
        viewModel.selectTab(UserSpaceTab.FAVORITES)

        assertEquals(UserSpaceTab.FAVORITES, viewModel.uiState.value.selectedTab)
        assertEquals(listOf(5L), viewModel.uiState.value.favorites.items.map { it.tid.toLong() })
        assertTrue(viewModel.uiState.value.favorites.endReached)
    }

    @Test
    fun `refresh force-reloads the profile and the selected tab only`() {
        val repository = repository().apply {
            profileFlow = { force ->
                flowOf(if (force) fresh(profile("new")) else cached(profile("old")))
            }
            threadsFlow = { _, force ->
                flowOf(fresh(page(next = 0, ids = intArrayOf(if (force) 9 else 1))))
            }
        }
        val viewModel = UserSpaceViewModel(repository, RouteUserSpace(UID))
        viewModel.selectTab(UserSpaceTab.REPLIES)

        viewModel.refresh()

        val state = viewModel.uiState.value
        assertFalse(state.isRefreshing)
        assertEquals("new", state.profile?.username)
        assertEquals(listOf(9), state.replies.tids)
        assertEquals("unselected tabs are not refetched", listOf(1), state.threads.tids)
        assertTrue(repository.observed.containsAll(listOf("profile|true", "threads:reply|true")))
    }

    @Test
    fun `refresh on the about tab waits for the profile alone`() {
        val repository = repository().apply {
            profileFlow = { force ->
                flowOf(if (force) fresh(profile("new")) else cached(profile("old")))
            }
        }
        val viewModel = UserSpaceViewModel(repository, RouteUserSpace(UID))

        viewModel.selectTab(UserSpaceTab.ABOUT)
        viewModel.refresh()

        val state = viewModel.uiState.value
        assertFalse("a single-path refresh must not leave the spinner up", state.isRefreshing)
        assertEquals("new", state.profile?.username)
    }

    @Test
    fun `refresh failure reports a message and still stops the spinner`() = runTest(dispatcher) {
        val repository = repository().apply {
            profileFlow = { force ->
                if (force) flowOf(CachedLoadEvent.Failure(IllegalStateException("boom"), true))
                else flowOf(cached(profile("old")))
            }
            threadsFlow = { _, force ->
                if (force) flowOf(CachedLoadEvent.Failure(IllegalStateException("boom"), true))
                else flowOf(cached(page(next = 0, ids = intArrayOf(1))))
            }
        }
        val viewModel = UserSpaceViewModel(repository, RouteUserSpace(UID))
        val messages = mutableListOf<UserMessage>()
        backgroundScope.launch(dispatcher) {
            viewModel.messages.collect { messages += it }
        }

        viewModel.refresh()

        assertEquals(listOf(R.string.refresh_failed), messages.map { it.resId })
        assertFalse(viewModel.uiState.value.isRefreshing)
        assertEquals("already loaded content survives a failed refresh", listOf(1), viewModel.uiState.value.threads.tids)
    }

    @Test
    fun `logout drops self-only state and asks the page to close`() = runTest(dispatcher) {
        val repository = repository(loggedInAs = UID).apply {
            favoritesFlow = {
                flowOf(
                    fresh(
                        ForumFavoritePageResult(
                            listOf(ForumFavoriteThread(favId = 11L, tid = 5, title = "f", time = "")),
                            PageInfo(activePage = 1, nextPage = 0)
                        )
                    )
                )
            }
        }
        val viewModel = UserSpaceViewModel(repository, RouteUserSpace(UID))
        viewModel.selectTab(UserSpaceTab.FAVORITES)
        assertEquals(1, viewModel.uiState.value.favorites.items.size)
        var closed = 0
        backgroundScope.launch(dispatcher) {
            viewModel.loggedOut.collect { closed++ }
        }

        viewModel.logout()

        val state = viewModel.uiState.value
        assertEquals(1, repository.logoutCalls)
        assertFalse(state.isSelf)
        assertFalse(state.isLoggingOut)
        assertTrue("private favorites must not linger after logout", state.favorites.items.isEmpty())
        assertEquals(UserSpaceTab.THREADS, state.selectedTab)
        assertEquals(1, closed)
    }

    private fun repository(loggedInAs: Int? = null) = FakeUserSpaceRepository(loggedInAs)

    private fun cached(value: ForumUserProfile) =
        CachedLoadEvent.Cached(CacheEntry(value, 1_000L, CacheSource.Disk, false))

    private fun fresh(value: ForumUserProfile) =
        CachedLoadEvent.Fresh(CacheEntry(value, 2_000L, CacheSource.Network, false))

    private fun cached(value: ForumUserThreadPageResult) =
        CachedLoadEvent.Cached(CacheEntry(value, 1_000L, CacheSource.Disk, false))

    private fun fresh(value: ForumUserThreadPageResult) =
        CachedLoadEvent.Fresh(CacheEntry(value, 2_000L, CacheSource.Network, false))

    private fun fresh(value: ForumFavoritePageResult) =
        CachedLoadEvent.Fresh(CacheEntry(value, 2_000L, CacheSource.Network, false))

    private fun profile(username: String) = ForumUserProfile(
        uid = UID,
        username = username,
        groupName = "路人乘客",
        replyCount = 185,
        topicCount = 4,
    )

    private fun thread(id: Int) = ForumUserThread(tid = id, title = "t$id", boardName = "版塊", time = "昨天")

    /** 首页结果：[next] 为分页信息给出的下一页页码（0 = 已是最后一页）。 */
    private fun page(next: Int, ids: IntArray) =
        ForumUserThreadPageResult(ids.map(::thread), PageInfo(activePage = 1, nextPage = next))

    private val UserListState<ForumUserThread>.tids get() = items.map { it.tid }

    private companion object {
        const val UID = 7
    }

    /** 只实现个人主页用到的成员；其余接口成员留桩，调用即失败。 */
    private class FakeUserSpaceRepository(loggedInAs: Int?) : ForumRepository {
        override val loginState: StateFlow<ForumLoginState> = MutableStateFlow(
            loggedInAs?.let { ForumLoginState.LoggedIn(uid = it, username = "me") }
                ?: ForumLoginState.Anonymous
        )

        var profileFlow: (forceRefresh: Boolean) -> Flow<CachedLoadEvent<ForumUserProfile>> = { flowOf() }
        var threadsFlow: (ForumUserThreadType, forceRefresh: Boolean) -> Flow<CachedLoadEvent<ForumUserThreadPageResult>> =
            { _, _ -> flowOf() }
        var favoritesFlow: (forceRefresh: Boolean) -> Flow<CachedLoadEvent<ForumFavoritePageResult>> = { flowOf() }
        var loadThreadsPage: (ForumUserThreadType, Int) -> ForumUserThreadPageResult =
            { _, _ -> error("unexpected load more") }
        var loadFavoritesPage: (Int) -> ForumFavoritePageResult = { error("unexpected load more") }

        /** 首页观察记录："kind|forceRefresh"。 */
        val observed = mutableListOf<String>()
        /** 追加页请求记录（页码），用于断言翻页与重试行为。 */
        val requestedPages = mutableListOf<Int>()
        var logoutCalls = 0

        override suspend fun refreshLoginState() = Unit
        override suspend fun tryRestoreCachedLoginState() = Unit
        override suspend fun requestLoginChallenge(): ForumLoginChallenge = error("unused")
        override suspend fun login(
            challenge: ForumLoginChallenge,
            username: String,
            password: String,
            seccode: String
        ): Result<ForumLoginState> = error("unused")

        override suspend fun logout() {
            logoutCalls++
        }

        override suspend fun addFavoriteThread(tid: Int, actionUrl: String?): Result<Unit> = error("unused")
        override suspend fun isFavoritedThread(tid: Int): Boolean = error("unused")

        override suspend fun loadUserProfile(uid: Int): ForumUserProfile = error("unused")

        override suspend fun loadUserThreads(
            uid: Int,
            type: ForumUserThreadType,
            page: Int
        ): ForumUserThreadPageResult {
            requestedPages += page
            return loadThreadsPage(type, page)
        }

        override suspend fun loadUserFavorites(page: Int): ForumFavoritePageResult {
            requestedPages += page
            return loadFavoritesPage(page)
        }

        override fun observeUserProfile(
            uid: Int,
            forceRefresh: Boolean,
            revalidate: Boolean
        ): Flow<CachedLoadEvent<ForumUserProfile>> =
            profileFlow(forceRefresh).also { observed += "profile|$forceRefresh" }

        override fun observeUserThreads(
            uid: Int,
            type: ForumUserThreadType,
            page: Int,
            forceRefresh: Boolean
        ): Flow<CachedLoadEvent<ForumUserThreadPageResult>> =
            threadsFlow(type, forceRefresh).also { observed += "threads:${type.param}|$forceRefresh" }

        override fun observeUserFavorites(
            page: Int,
            forceRefresh: Boolean
        ): Flow<CachedLoadEvent<ForumFavoritePageResult>> =
            favoritesFlow(forceRefresh).also { observed += "favorites|$forceRefresh" }

        override fun observeForumBoards(
            forceRefresh: Boolean,
            revalidate: Boolean,
            nowMillis: () -> Long
        ): Flow<CachedLoadEvent<ForumHomeData>> = flow { error("not used") }

        override fun observeThreads(
            fid: Int,
            page: Int,
            typeId: Int?,
            threadOrder: ForumThreadOrder,
            forceRefresh: Boolean,
            revalidate: Boolean,
            nowMillis: () -> Long
        ): Flow<CachedLoadEvent<ForumThreadPageResult>> = flow { error("not used") }

        override fun observeThreadDetail(
            tid: Int,
            page: Int,
            floorOrder: ForumFloorOrder,
            authorUid: Int?,
            forceRefresh: Boolean,
            revalidate: Boolean,
            nowMillis: () -> Long
        ): Flow<CachedLoadEvent<ForumThreadDetail>> = flow { error("not used") }

        override suspend fun loadForumBoards(forceRefresh: Boolean): ForumHomeData = error("not used")

        override suspend fun loadThreads(
            fid: Int,
            page: Int,
            typeId: Int?,
            threadOrder: ForumThreadOrder,
            forceRefresh: Boolean
        ): ForumThreadPageResult = error("not used")

        override suspend fun loadThreadDetail(
            tid: Int,
            page: Int,
            floorOrder: ForumFloorOrder,
            authorUid: Int?,
            forceRefresh: Boolean
        ): ForumThreadDetail = error("not used")

        override suspend fun loadFloorJumpPage(
            tid: Int,
            pid: Int,
            floorOrder: ForumFloorOrder
        ): ForumThreadDetail? = error("not used")

        override suspend fun loadFloorComments(
            tid: Int,
            pid: Int,
            page: Int,
            forceRefresh: Boolean
        ): ForumCommentPageResult = error("not used")
    }
}

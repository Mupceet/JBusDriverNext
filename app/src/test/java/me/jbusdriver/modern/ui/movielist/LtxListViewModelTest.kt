package me.jbusdriver.modern.ui.movielist

import android.net.Uri
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.emitAll
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import me.jbusdriver.R
import me.jbusdriver.modern.core.GSON
import me.jbusdriver.modern.core.site.SiteConfig
import me.jbusdriver.modern.data.db.entity.LtxMovie
import me.jbusdriver.modern.data.db.entity.LtxResolveState
import me.jbusdriver.modern.data.ltx.LtxListQuery
import me.jbusdriver.modern.data.ltx.LtxSnapshotExpiredException
import me.jbusdriver.modern.data.repository.LocalVideoRepository
import me.jbusdriver.modern.data.repository.LtxCatalogCache
import me.jbusdriver.modern.data.repository.LtxMovieRepository
import me.jbusdriver.modern.data.repository.LtxPage
import me.jbusdriver.modern.data.repository.LtxRepository
import me.jbusdriver.modern.data.settings.LtxSettingsSource
import me.jbusdriver.modern.domain.model.DeleteResult
import me.jbusdriver.modern.domain.model.LocalVideo
import me.jbusdriver.modern.domain.model.LocalVideoGroup
import me.jbusdriver.modern.core.http.LtxWebSession
import me.jbusdriver.modern.core.http.LtxWebViewSession
import me.jbusdriver.modern.domain.model.LocalVideoSummary
import me.jbusdriver.modern.domain.model.LtxEntry
import me.jbusdriver.modern.domain.model.LtxLink
import me.jbusdriver.modern.domain.model.LtxLinkType
import me.jbusdriver.modern.domain.model.Movie
import me.jbusdriver.modern.test.FakeCollectionUiPrefs
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.io.IOException

@OptIn(ExperimentalCoroutinesApi::class)
class LtxListViewModelTest {

    private lateinit var testDispatcher: TestDispatcher

    /** 一次 loadPage 调用的参数快照。 */
    private data class PageCall(val query: LtxListQuery, val snapshot: String?, val offset: Int)

    private class FakeLtxRepository : LtxRepository {
        val calls = mutableListOf<PageCall>()

        /** 动态页工厂：按调用参数返回页；返回 null 抛 IOException。 */
        var pageFactory: ((query: LtxListQuery, snapshot: String?, offset: Int) -> LtxPage?)? = null

        /** 一次性异常：下一次 loadPage 抛出后清空。 */
        var nextError: Exception? = null

        /** 目录首页缓存（SWR stale 侧）；null = 无缓存。 */
        var cachedCatalog: LtxCatalogCache? = null

        override suspend fun loadPage(query: LtxListQuery, snapshot: String?, offset: Int): LtxPage {
            calls += PageCall(query, snapshot, offset)
            nextError?.let { nextError = null; throw it }
            val page = pageFactory?.invoke(query, snapshot, offset)
                ?: throw IOException("no scripted page for offset=$offset")
            return page
        }

        override suspend fun peekCachedPage(query: LtxListQuery): LtxCatalogCache? = cachedCatalog
        override suspend fun findByCode(code: String): LtxEntry? = null
        override val verificationRequests: MutableSharedFlow<Unit> = MutableSharedFlow()
        override val sessionAdopted: MutableSharedFlow<Unit> = MutableSharedFlow(extraBufferCapacity = 1)
        override fun adoptWebSession(session: LtxWebSession) = Unit
        override fun adoptCryptoSession(session: LtxWebViewSession) = Unit
        override suspend fun currentOrigin(): String = "https://ltx.example.test"
    }

    private class FakeLtxMovieRepository : LtxMovieRepository {
        val resolveCalls = mutableListOf<String>()
        var resolveResult: String = LtxResolveState.RESOLVED

        /** 非空时冻结所有 resolve（用于构造队列积压场景），完成后记录调用。 */
        var gate: CompletableDeferred<Unit>? = null

        /** 非空时冻结补全快照首次发射（模拟 Room 首查竞态）。 */
        var snapshotsGate: CompletableDeferred<Unit>? = null
        val rows = MutableStateFlow<List<LtxMovie>>(emptyList())

        override suspend fun resolve(code: String): String {
            gate?.await()
            resolveCalls.add(code)
            return resolveResult
        }

        override fun observeAll(): Flow<List<LtxMovie>> = flow {
            snapshotsGate?.await()
            emitAll(rows)
        }
        override suspend fun findResolvedMovie(code: String, baseUrl: String): Movie? = null
    }

    private class FakeLtxSettings(enabled: Boolean = true) : LtxSettingsSource {
        override val ltxEnabled = MutableStateFlow(enabled)
        override suspend fun currentLtxBaseUrl() = "https://ltx.example.test"
        override suspend fun setLtxBaseUrl(baseUrl: String) = Unit
    }

    private class FakeSiteConfig : SiteConfig {
        override var baseUrl: String = "https://javbus.example.test"
        override fun resolve(pathOrUrl: String): String =
            if (pathOrUrl.startsWith("http")) pathOrUrl else baseUrl.trimEnd('/') + pathOrUrl
    }

    private class StubLocalVideoRepo(downloaded: Set<String> = emptySet()) : LocalVideoRepository {
        val downloadedCodes = MutableStateFlow(downloaded)
        override fun observeForCode(code: String) = flowOf(emptyList<LocalVideo>())
        override fun observeDownloadedCodes(): Flow<Set<String>> = downloadedCodes
        override fun observeSummary() = flowOf(LocalVideoSummary())
        override fun hasFolder() = flowOf(true)
        override suspend fun setFolder(uri: Uri) {}
        override suspend fun clearFolder() {}
        override suspend fun rescan() = 0
        override fun observeAllGroupedByCode() = flowOf(emptyList<LocalVideoGroup>())
        override fun observeShowUncollectedLocal() = flowOf(false)
        override suspend fun setShowUncollectedLocal(value: Boolean) {}
        override suspend fun deleteVideos(ids: List<Int>) = DeleteResult(0, 0)
        override suspend fun snapshotMetadata(code: String, title: String, imageUrl: String, date: String, censorType: String?) {}
    }

    private fun entry(
        code: String,
        isPending: Boolean = false,
        addedAtMillis: Long = 0L,
        score: Double? = null,
        date: String? = null,
        withLink: Boolean = false,
    ) = LtxEntry(
        code = code,
        links = if (withLink) {
            listOf(LtxLink(LtxLinkType.ED2K, "ed2k://|file|$code|1|00000000000000000000000000000000|/", code, 1L))
        } else {
            emptyList()
        },
        isPending = isPending,
        addedAtMillis = addedAtMillis,
        score = score,
        date = date,
    )

    /** 构造一页；snapshot 缺省为递增 id（null 可显式传旧镜像语义）。 */
    private fun page(
        entries: List<LtxEntry>,
        total: Int = entries.size,
        snapshot: String? = "snap",
        updatedAt: Long = 42L,
        offset: Int = 0,
    ) = LtxPage(entries = entries, total = total, snapshot = snapshot, updatedAtMillis = updatedAt, offset = offset)

    private fun createViewModel(
        ltxRepo: LtxRepository = FakeLtxRepository(),
        movieRepo: FakeLtxMovieRepository = FakeLtxMovieRepository(),
        settings: LtxSettingsSource = FakeLtxSettings(),
        prefs: FakeCollectionUiPrefs = FakeCollectionUiPrefs(),
        localVideoRepo: LocalVideoRepository = StubLocalVideoRepo(),
    ) = LtxListViewModel(
        ltxRepository = ltxRepo,
        ltxMovieRepository = movieRepo,
        ltxSettings = settings,
        uiPrefsStore = prefs,
        siteConfig = FakeSiteConfig(),
        localVideoRepository = localVideoRepo,
        ioDispatcher = testDispatcher,
    )

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
    fun startLoadsFirstPageInServerOrderAndRebasesSnapshots() = runTest(testDispatcher) {
        // AAA-003 已有补全快照（路径化 URL，应被重建为绝对地址）
        val movieRepo = FakeLtxMovieRepository().apply {
            rows.value = listOf(
                LtxMovie(
                    code = "AAA-003",
                    resolveState = LtxResolveState.RESOLVED,
                    movieUrlPath = "/AAA-003",
                    movieJson = GSON.toJson(
                        Movie(
                            title = "Resolved",
                            imageUrl = "/pics/aaa3.jpg",
                            code = "AAA-003",
                            date = "2026-01-01",
                            link = "/AAA-003",
                        )
                    ),
                )
            )
        }
        val repo = FakeLtxRepository().apply {
            pageFactory = { _, _, _ ->
                // 服务端顺序（评分倒序），客户端不得重排
                page(
                    entries = listOf(
                        entry("AAA-003", addedAtMillis = 30L, score = 4.1, withLink = true),
                        entry("AAA-002", addedAtMillis = 20L),
                        entry("AAA-001", isPending = true, addedAtMillis = 10L, score = 4.9),
                    ),
                    total = 3,
                    snapshot = "s0",
                )
            }
        }
        val viewModel = createViewModel(ltxRepo = repo, movieRepo = movieRepo)

        viewModel.start()
        advanceUntilIdle()

        val state = viewModel.uiState.value
        // 首查免快照（服务端新建）
        assertEquals(PageCall(LtxListQuery(), null, 0), repo.calls.single())
        assertEquals(3, state.totalCount)
        assertEquals(42L, state.updatedAtMillis)
        // 服务端顺序原样保留
        assertEquals(listOf("AAA-003", "AAA-002", "AAA-001"), state.items.map { it.code })
        assertFalse(state.canLoadMore)
        // 已补全条目携带重建 URL 的快照
        val resolved = state.items.first()
        assertEquals(LtxResolveState.RESOLVED, resolved.resolveState)
        assertNotNull(resolved.movie)
        assertEquals("https://javbus.example.test/AAA-003", resolved.movie!!.link)
        assertEquals("https://javbus.example.test/pics/aaa3.jpg", resolved.movie!!.imageUrl)
        // 未补全条目无快照、状态为 UNRESOLVED
        assertNull(state.items[1].movie)
        assertEquals(LtxResolveState.UNRESOLVED, state.items[1].resolveState)
    }

    @Test
    fun entryDatePrefersLtxOriginalDateOverSnapshot() = runTest(testDispatcher) {
        val movieRepo = FakeLtxMovieRepository().apply {
            rows.value = listOf(
                LtxMovie(
                    code = "AAA-003",
                    resolveState = LtxResolveState.RESOLVED,
                    movieUrlPath = "/AAA-003",
                    movieJson = GSON.toJson(
                        Movie(title = "R", imageUrl = "", code = "AAA-003", date = "2026-06-01", link = "/AAA-003")
                    ),
                )
            )
        }
        val repo = FakeLtxRepository().apply {
            pageFactory = { _, _, _ ->
                page(entries = listOf(entry("AAA-003", addedAtMillis = 30L, date = "2025-01-01")))
            }
        }
        val viewModel = createViewModel(ltxRepo = repo, movieRepo = movieRepo)
        viewModel.start()
        advanceUntilIdle()

        // 卡片日期取 LTX 原始值，补全快照日期不修订
        assertEquals("2025-01-01", viewModel.uiState.value.items.single().date)
    }

    @Test
    fun statusFilterTriggersServerReloadWithStatusParam() = runTest(testDispatcher) {
        val repo = FakeLtxRepository().apply {
            pageFactory = { query, _, _ ->
                if (query.status == "pending") {
                    page(entries = listOf(entry("AAA-001", isPending = true)), snapshot = "s0")
                } else {
                    page(
                        entries = listOf(entry("AAA-003"), entry("AAA-002"), entry("AAA-001", isPending = true)),
                        snapshot = "s0",
                    )
                }
            }
        }
        val viewModel = createViewModel(ltxRepo = repo)
        viewModel.start()
        advanceUntilIdle()

        viewModel.updateStatusFilter(LtxStatusFilter.PENDING)
        advanceUntilIdle()

        // 确认状态是服务端参数：以 status=pending 重查第 0 页
        assertEquals(2, repo.calls.size)
        assertEquals("pending", repo.calls[1].query.status)
        assertEquals(0, repo.calls[1].offset)
        assertEquals(listOf("AAA-001"), viewModel.uiState.value.items.map { it.code })
    }

    @Test
    fun updateSortFieldAndDirectionReloadWithServerSortAndPersistPreference() = runTest(testDispatcher) {
        val prefs = FakeCollectionUiPrefs()
        val repo = FakeLtxRepository().apply {
            pageFactory = { query, _, _ ->
                page(entries = listOf(entry("AAA-001"), entry("AAA-002"), entry("AAA-003")), snapshot = "s0")
            }
        }
        val viewModel = createViewModel(ltxRepo = repo, prefs = prefs)
        viewModel.start()
        advanceUntilIdle()

        viewModel.updateSortField(LtxSortField.CODE)
        viewModel.updateSortDirection(LtxSortDirection.ASC)
        advanceUntilIdle()

        // 排序是服务端参数：重查第 0 页（复用会话快照），客户端不重排返回结果
        assertEquals(3, repo.calls.size)
        assertEquals("s0", repo.calls[1].snapshot)
        assertEquals("code", repo.calls[1].query.sort.field)
        assertEquals("desc", repo.calls[1].query.sort.dir)
        assertEquals("asc", repo.calls[2].query.sort.dir)
        assertEquals(listOf("AAA-001", "AAA-002", "AAA-003"), viewModel.uiState.value.items.map { it.code })
        assertEquals("CODE", prefs.ltxSortField.value)
        assertEquals("ASC", prefs.ltxSortDirection.value)
    }

    @Test
    fun toggleResolveFilterIsLocalAndDoesNotRefetch() = runTest(testDispatcher) {
        val movieRepo = FakeLtxMovieRepository().apply {
            rows.value = listOf(LtxMovie(code = "AAA-002", resolveState = LtxResolveState.NOT_FOUND))
        }
        val repo = FakeLtxRepository().apply {
            pageFactory = { _, _, _ ->
                page(entries = listOf(entry("AAA-003"), entry("AAA-002"), entry("AAA-001", isPending = true)), snapshot = "s0")
            }
        }
        val viewModel = createViewModel(ltxRepo = repo, movieRepo = movieRepo)
        viewModel.start()
        advanceUntilIdle()

        // 默认（已收錄 + 未檢測）隐藏未收錄，且不计入筛选角标
        assertEquals(listOf("AAA-003", "AAA-001"), viewModel.uiState.value.items.map { it.code })
        assertEquals(0, viewModel.uiState.value.activeFilterCount)

        viewModel.toggleResolveFilter(LtxResolveFilter.NOT_FOUND)
        advanceUntilIdle()

        // 纯本地筛选：不发起网络重查，条目即时重算
        assertEquals(1, repo.calls.size)
        assertEquals(listOf("AAA-003", "AAA-002", "AAA-001"), viewModel.uiState.value.items.map { it.code })
        assertEquals(1, viewModel.uiState.value.activeFilterCount)
    }

    @Test
    fun resolveFilterToggleOffLastResetsToAll() = runTest(testDispatcher) {
        val repo = FakeLtxRepository().apply {
            pageFactory = { _, _, _ -> page(entries = listOf(entry("AAA-003"), entry("AAA-002"), entry("AAA-001")), snapshot = "s0") }
        }
        val viewModel = createViewModel(ltxRepo = repo)
        viewModel.start()
        advanceUntilIdle()

        viewModel.toggleResolveFilter(LtxResolveFilter.NOT_FOUND)
        advanceUntilIdle()
        assertEquals(LtxResolveFilter.entries.toSet(), viewModel.uiState.value.resolveFilter)

        // 逐个关掉直到只剩未收錄，再关掉最后一项 → 回到全选
        viewModel.toggleResolveFilter(LtxResolveFilter.RESOLVED)
        viewModel.toggleResolveFilter(LtxResolveFilter.UNRESOLVED)
        viewModel.toggleResolveFilter(LtxResolveFilter.NOT_FOUND)
        advanceUntilIdle()

        assertEquals(LtxResolveFilter.entries.toSet(), viewModel.uiState.value.resolveFilter)
        assertEquals(3, viewModel.uiState.value.items.size)
    }

    @Test
    fun resolveNowIsIdempotentAndSkipsTerminalStates() = runTest(testDispatcher) {
        val movieRepo = FakeLtxMovieRepository().apply {
            // AAA-002 站内未收录（终态，点击不应触发解析）
            rows.value = listOf(LtxMovie(code = "AAA-002", resolveState = LtxResolveState.NOT_FOUND))
        }
        val repo = FakeLtxRepository().apply {
            pageFactory = { _, _, _ -> page(entries = listOf(entry("AAA-003"), entry("AAA-002"), entry("AAA-001")), snapshot = "s0") }
        }
        val viewModel = createViewModel(ltxRepo = repo, movieRepo = movieRepo)
        viewModel.start()
        advanceUntilIdle()

        viewModel.resolveNow("AAA-003")
        // 入队即出现在 resolvingCodes（排队/在途集合）
        assertTrue("AAA-003" in viewModel.uiState.value.resolvingCodes)
        // 重复点击幂等
        viewModel.resolveNow("aaa-003")
        viewModel.resolveNow("AAA-002") // 终态跳过
        advanceUntilIdle()

        assertEquals(listOf("AAA-003"), movieRepo.resolveCalls)
        assertTrue(viewModel.uiState.value.resolvingCodes.isEmpty())
    }

    @Test
    fun resolveNowJumpsAheadOfDiscoveryQueue() = runTest(testDispatcher) {
        // 5 个未补全条目；gate 冻结 worker，途经先入队制造积压
        val repo = FakeLtxRepository().apply {
            pageFactory = { _, _, _ ->
                page(entries = (1..5).map { entry("SCREEN-00$it", addedAtMillis = (6 - it).toLong()) }, snapshot = "s0")
            }
        }
        val pauseGate = CompletableDeferred<Unit>()
        val movieRepo = FakeLtxMovieRepository().apply { gate = pauseGate }
        val viewModel = createViewModel(ltxRepo = repo, movieRepo = movieRepo)
        viewModel.start()
        advanceUntilIdle()

        // 途经全量入队：3 个 worker 冻结在队首三项上，SCREEN-004/005 仍在队列
        viewModel.requestResolveVisible(0, 16)
        assertEquals(emptyList<String>(), movieRepo.resolveCalls)

        // 用户点击仍在队列中的 SCREEN-005 → 应插队到队首，先于 SCREEN-004 执行
        viewModel.resolveNow("SCREEN-005")
        pauseGate.complete(Unit)
        advanceUntilIdle()

        val calls = movieRepo.resolveCalls
        assertEquals(5, calls.size)
        assertTrue(
            "interacted SCREEN-005 should jump ahead of queued SCREEN-004, calls=$calls",
            calls.indexOf("SCREEN-005") < calls.indexOf("SCREEN-004")
        )
    }

    @Test
    fun sortSwitchCancelsPendingResolveWork() = runTest(testDispatcher) {
        // 切排序 = 整表替换：旧列表的在途/排队补全任务应全部作废，
        // 新列表的补全由 UI 层停留窗口重新入队（单元测试不模拟 UI 层）
        val repo = FakeLtxRepository().apply {
            pageFactory = { _, _, _ -> page(entries = (1..5).map { entry("SCREEN-00$it") }, snapshot = "s0") }
        }
        val pauseGate = CompletableDeferred<Unit>()
        val movieRepo = FakeLtxMovieRepository().apply { gate = pauseGate }
        val viewModel = createViewModel(ltxRepo = repo, movieRepo = movieRepo)
        viewModel.start()
        advanceUntilIdle()

        // 途经全量入队：3 个在途（冻结在 gate 上）、2 个排队
        viewModel.requestResolveVisible(0, 16)
        assertEquals(5, viewModel.uiState.value.resolvingCodes.size)

        viewModel.updateSortField(LtxSortField.CODE)
        advanceUntilIdle()
        pauseGate.complete(Unit)
        advanceUntilIdle()

        // 在途被取消、排队被清空：补全指示清空，且不产生任何解析调用
        // （fake 的 resolve 过 gate 后才记录，取消发生在 await 处）
        assertTrue(viewModel.uiState.value.resolvingCodes.isEmpty())
        assertEquals(emptyList<String>(), movieRepo.resolveCalls)
    }

    @Test
    fun enqueueBeforeSnapshotsLoadedIsDroppedAndAcceptedAfter() = runTest(testDispatcher) {
        // 竞态回归：冷启动缓存首页发布可能早于补全快照（Room）首查完成。
        // 快照未就绪时入队一律丢弃（无法区分终态，会把已补全条目重复排队）；
        // 就绪后 recompute 刷新条目状态，停留窗口重新入队只收未补全条目。
        val repo = FakeLtxRepository().apply {
            pageFactory = { _, _, _ ->
                page(entries = listOf(entry("AAA-003"), entry("AAA-002"), entry("AAA-001")), snapshot = "s0")
            }
        }
        val movieRepo = FakeLtxMovieRepository().apply {
            rows.value = listOf(LtxMovie(code = "AAA-002", resolveState = LtxResolveState.NOT_FOUND))
            snapshotsGate = CompletableDeferred()
        }
        val viewModel = createViewModel(ltxRepo = repo, movieRepo = movieRepo)
        viewModel.start()
        advanceUntilIdle()

        // 快照未就绪：入队丢弃，无补全任务
        viewModel.requestResolveVisible(0, 16)
        assertTrue(viewModel.uiState.value.resolvingCodes.isEmpty())

        // 快照就绪（AAA-002 = NOT_FOUND）：重新入队只收未补全条目
        movieRepo.snapshotsGate?.complete(Unit)
        advanceUntilIdle()
        viewModel.requestResolveSettled(0, 16)
        advanceUntilIdle()
        assertEquals(listOf("AAA-001", "AAA-003"), movieRepo.resolveCalls.sorted())
    }

    @Test
    fun requestResolveSettledEnqueuesVisibleUnresolvedEntries() = runTest(testDispatcher) {        val movieRepo = FakeLtxMovieRepository().apply {
            // AAA-002 站内未收录（终态，停留补全应跳过）
            rows.value = listOf(LtxMovie(code = "AAA-002", resolveState = LtxResolveState.NOT_FOUND))
        }
        val repo = FakeLtxRepository().apply {
            pageFactory = { _, _, _ -> page(entries = listOf(entry("AAA-003"), entry("AAA-002"), entry("AAA-001")), snapshot = "s0") }
        }
        val viewModel = createViewModel(ltxRepo = repo, movieRepo = movieRepo)
        viewModel.start()
        advanceUntilIdle()

        viewModel.requestResolveSettled(0, 10)
        advanceUntilIdle()

        assertEquals(listOf("AAA-001", "AAA-003"), movieRepo.resolveCalls.sorted())
    }

    @Test
    fun requestResolveSettledJumpsAheadOfDiscoveryQueue() = runTest(testDispatcher) {
        // 5 个未补全条目；gate 冻结全部 worker 制造途经队列积压
        val repo = FakeLtxRepository().apply {
            pageFactory = { _, _, _ ->
                page(entries = (1..5).map { entry("SCREEN-00$it", addedAtMillis = (6 - it).toLong()) }, snapshot = "s0")
            }
        }
        val pauseGate = CompletableDeferred<Unit>()
        val movieRepo = FakeLtxMovieRepository().apply { gate = pauseGate }
        val viewModel = createViewModel(ltxRepo = repo, movieRepo = movieRepo)
        viewModel.start()
        advanceUntilIdle()

        // 全量窗口途经一遍：3 个 worker 冻结在 SCREEN-001..003 上，004/005 在途经队列
        viewModel.requestResolveVisible(0, 16)
        // 停留窗口只覆盖屏幕最后一项 SCREEN-005 → 应从途经队列提到队首
        viewModel.requestResolveSettled(4, 1)
        pauseGate.complete(Unit)
        advanceUntilIdle()

        val calls = movieRepo.resolveCalls
        assertEquals(5, calls.size)
        assertTrue(
            "settled SCREEN-005 should jump ahead of discovery-queued SCREEN-004, calls=$calls",
            calls.indexOf("SCREEN-005") < calls.indexOf("SCREEN-004")
        )
    }

    @Test
    fun requestResolveVisibleKeepsScreenOrderAndSkipsDuplicates() = runTest(testDispatcher) {
        val repo = FakeLtxRepository().apply {
            pageFactory = { _, _, _ ->
                page(
                    entries = (1..5).map { entry("DISC-00$it", addedAtMillis = (6 - it).toLong()) },
                    snapshot = "s0",
                )
            }
        }
        val pauseGate = CompletableDeferred<Unit>()
        val movieRepo = FakeLtxMovieRepository().apply { gate = pauseGate }
        val viewModel = createViewModel(ltxRepo = repo, movieRepo = movieRepo)
        viewModel.start()
        advanceUntilIdle()

        // 全量窗口途经一遍 + 重复触发：按屏幕顺序入队一次，已排队/在途不重复
        viewModel.requestResolveVisible(0, 16)
        viewModel.requestResolveVisible(0, 16)
        pauseGate.complete(Unit)
        advanceUntilIdle()

        val calls = movieRepo.resolveCalls
        assertEquals(listOf("DISC-001", "DISC-002", "DISC-003", "DISC-004", "DISC-005"), calls)
    }

    @Test
    fun requestResolveVisibleSkipsTerminalStates() = runTest(testDispatcher) {
        val movieRepo = FakeLtxMovieRepository().apply {
            // AAA-002 站内未收录（终态，途经入队应跳过）
            rows.value = listOf(LtxMovie(code = "AAA-002", resolveState = LtxResolveState.NOT_FOUND))
        }
        val repo = FakeLtxRepository().apply {
            pageFactory = { _, _, _ -> page(entries = listOf(entry("AAA-003"), entry("AAA-002"), entry("AAA-001")), snapshot = "s0") }
        }
        val viewModel = createViewModel(ltxRepo = repo, movieRepo = movieRepo)
        viewModel.start()
        advanceUntilIdle()

        viewModel.requestResolveVisible(0, 10)
        advanceUntilIdle()

        assertEquals(listOf("AAA-001", "AAA-003"), movieRepo.resolveCalls.sorted())
        assertTrue(viewModel.uiState.value.resolvingCodes.isEmpty())
    }

    @Test
    fun loadMoreAppendsNextPageAndDedupesCrossPageDuplicates() = runTest(testDispatcher) {
        val repo = FakeLtxRepository().apply {
            pageFactory = { _, _, offset ->
                if (offset == 0) {
                    // 40 行（≥自动补页阈值）+ 总数 150：不触发自动补页，留手动滚动触发
                    page(entries = (1..40).map { entry("ROW-%03d".format(it)) }, total = 150, snapshot = "s0")
                } else {
                    // 第二页含与第一页重复的 ROW-001（快照同码双行分处两页的情形）
                    page(
                        entries = listOf(entry("ROW-001")) + (41..80).map { entry("ROW-%03d".format(it)) },
                        total = 150,
                        snapshot = "s0",
                        offset = offset,
                    )
                }
            }
        }
        val viewModel = createViewModel(ltxRepo = repo)
        viewModel.start()
        advanceUntilIdle()

        assertEquals(40, viewModel.uiState.value.items.size)
        assertTrue(viewModel.uiState.value.canLoadMore)

        viewModel.loadMore()
        advanceUntilIdle()

        // 跨页同码去重：ROW-001 不重复入列
        val items = viewModel.uiState.value.items
        assertEquals(80, items.size)
        assertEquals(80, items.map { it.code }.distinct().size)
        // 已消耗行数 80 < 总数 150 → 仍可继续
        assertTrue(viewModel.uiState.value.canLoadMore)
        assertEquals(2, repo.calls.size)
        assertEquals(40, repo.calls[1].offset)
        assertEquals("s0", repo.calls[1].snapshot)
    }

    @Test
    fun loadMoreStopsAtTotalAndIgnoresFurtherTriggers() = runTest(testDispatcher) {
        val repo = FakeLtxRepository().apply {
            pageFactory = { _, _, offset ->
                if (offset == 0) {
                    page(entries = (1..40).map { entry("ROW-%03d".format(it)) }, total = 80, snapshot = "s0")
                } else {
                    page(entries = (41..80).map { entry("ROW-%03d".format(it)) }, total = 80, snapshot = "s0", offset = offset)
                }
            }
        }
        val viewModel = createViewModel(ltxRepo = repo)
        viewModel.start()
        advanceUntilIdle()

        viewModel.loadMore()
        advanceUntilIdle()
        assertTrue(!viewModel.uiState.value.canLoadMore)

        // 已到总数：再次触发不再发请求
        viewModel.loadMore()
        advanceUntilIdle()
        assertEquals(2, repo.calls.size)
        assertEquals(80, viewModel.uiState.value.items.size)
    }

    @Test
    fun snapshotExpiredOnSortSwitchRefetchesWithoutSnapshot() = runTest(testDispatcher) {
        val repo = FakeLtxRepository().apply {
            pageFactory = { _, snapshot, _ ->
                if (snapshot == "s0") throw LtxSnapshotExpiredException()
                // 首查（snapshot=null）建快照 s0；重查（丢快照）服务端返回新快照
                page(entries = listOf(entry("AAA-001"), entry("AAA-002")), snapshot = snapshot ?: "s0")
            }
        }
        val viewModel = createViewModel(ltxRepo = repo)
        viewModel.start()
        advanceUntilIdle()

        viewModel.updateSortField(LtxSortField.CODE)
        advanceUntilIdle()

        // 第 2 次调用带旧快照失败 → 第 3 次丢快照重查成功，条目整体替换
        assertEquals(3, repo.calls.size)
        assertEquals("s0", repo.calls[1].snapshot)
        assertNull(repo.calls[2].snapshot)
        assertEquals(0, repo.calls[2].offset)
        assertEquals(listOf("AAA-001", "AAA-002"), viewModel.uiState.value.items.map { it.code })
        assertFalse(viewModel.uiState.value.isRefreshing)
    }

    @Test
    fun snapshotExpiredOnLoadMoreRebuildsCurrentQuery() = runTest(testDispatcher) {
        var expiredThrown = false
        val repo = FakeLtxRepository().apply {
            pageFactory = { _, snapshot, offset ->
                if (offset > 0 && snapshot == "s0" && !expiredThrown) {
                    // 既有快照过期（一次性），之后同参数重试成功
                    expiredThrown = true
                    throw LtxSnapshotExpiredException()
                }
                if (offset == 0) {
                    page(entries = (1..40).map { entry("ROW-%03d".format(it)) }, total = 150, snapshot = "s0")
                } else {
                    page(
                        entries = (41..80).map { entry("ROW-%03d".format(it)) },
                        total = 150,
                        snapshot = snapshot,
                        offset = offset,
                    )
                }
            }
        }
        val viewModel = createViewModel(ltxRepo = repo)
        viewModel.start()
        advanceUntilIdle()

        viewModel.loadMore()
        advanceUntilIdle()

        // 翻页遇快照过期 → 丢快照从第 0 页重建当前查询（第 3 次调用）
        assertEquals(3, repo.calls.size)
        assertNull(repo.calls[2].snapshot)
        assertEquals(0, repo.calls[2].offset)
        assertFalse(viewModel.uiState.value.isRefreshing)
        assertEquals(40, viewModel.uiState.value.items.size)

        // 重建后再次加载下一页成功（快照已恢复）
        viewModel.loadMore()
        advanceUntilIdle()
        assertEquals(4, repo.calls.size)
        assertEquals("s0", repo.calls[3].snapshot)
        assertEquals(40, repo.calls[3].offset)
        assertEquals(80, viewModel.uiState.value.items.size)
    }

    @Test
    fun loadMoreFailureRetainsLoadedItems() = runTest(testDispatcher) {
        val repo = FakeLtxRepository().apply {
            pageFactory = { _, _, offset ->
                if (offset == 0) {
                    page(entries = (1..40).map { entry("ROW-%03d".format(it)) }, total = 150, snapshot = "s0")
                } else {
                    throw IOException("network error")
                }
            }
        }
        val viewModel = createViewModel(ltxRepo = repo)
        viewModel.start()
        advanceUntilIdle()

        viewModel.loadMore()
        advanceUntilIdle()

        // 翻页失败：保留已加载内容，不清空不报错（再次滚动会重试）
        assertEquals(40, viewModel.uiState.value.items.size)
        assertNull(viewModel.uiState.value.error)
        assertTrue(viewModel.uiState.value.canLoadMore)
        assertFalse(viewModel.uiState.value.isLoadingMore)
    }

    @Test
    fun firstPageFailureShowsErrorAndRetryRecovers() = runTest(testDispatcher) {
        val repo = FakeLtxRepository().apply {
            nextError = IOException("offline")
            pageFactory = { _, _, _ -> page(entries = listOf(entry("AAA-001")), snapshot = "s0") }
        }
        val viewModel = createViewModel(ltxRepo = repo)
        viewModel.start()
        advanceUntilIdle()

        // 首查失败且无已加载内容 → 错误态
        val state = viewModel.uiState.value
        assertFalse(state.isLoading)
        assertTrue(state.items.isEmpty())
        assertEquals(R.string.load_failed, state.error)

        // 重试成功 → 清错误态并载入
        viewModel.retry()
        advanceUntilIdle()
        assertNull(viewModel.uiState.value.error)
        assertEquals(listOf("AAA-001"), viewModel.uiState.value.items.map { it.code })
    }

    @Test
    fun protocolUpgradeFailureShowsProtocolError() = runTest(testDispatcher) {
        val repo = FakeLtxRepository().apply {
            nextError = me.jbusdriver.modern.data.ltx.LtxProtocolException("https://ltx.example.test")
        }
        val viewModel = createViewModel(ltxRepo = repo)
        viewModel.start()
        advanceUntilIdle()

        assertEquals(R.string.ltx_protocol_upgraded, viewModel.uiState.value.error)
    }

    @Test
    fun sessionAdoptedTriggersSnapshotResetReload() = runTest(testDispatcher) {
        // 验证浏览器路由注入会话后经仓库 sessionAdopted 事件回流：丢弃快照重建
        // 数据集（事件在验证页压栈期间发出，本 VM 挂在 RouteMain 不丢事件）
        val repo = FakeLtxRepository().apply {
            pageFactory = { _, _, _ -> page(entries = listOf(entry("AAA-001")), snapshot = "s0") }
        }
        val viewModel = createViewModel(ltxRepo = repo)
        viewModel.start()
        advanceUntilIdle()
        assertEquals(1, repo.calls.size)

        repo.sessionAdopted.emit(Unit)
        advanceUntilIdle()

        assertEquals(2, repo.calls.size)
        assertEquals(0, repo.calls[1].offset)
        assertNull(repo.calls[1].snapshot)
        assertEquals(listOf("AAA-001"), viewModel.uiState.value.items.map { it.code })
    }

    @Test
    fun autoFillCapsConsecutivePagesWhenFilterHidesMostRows() = runTest(testDispatcher) {
        val movieRepo = FakeLtxMovieRepository().apply {
            // 7/8 的行站内未收录（默认筛选隐藏），每页仅 5 行可见
            rows.value = (1..500).filter { it % 8 != 0 }.map {
                LtxMovie(code = "ROW-%03d".format(it), resolveState = LtxResolveState.NOT_FOUND)
            }
        }
        val repo = FakeLtxRepository().apply {
            pageFactory = { _, _, offset ->
                val start = offset + 1
                page(
                    entries = (start until start + 40).map { entry("ROW-%03d".format(it)) },
                    total = 500,
                    snapshot = "s0",
                    offset = offset,
                )
            }
        }
        val viewModel = createViewModel(ltxRepo = repo, movieRepo = movieRepo)
        viewModel.start()
        advanceUntilIdle()

        // 首页可见 5 < 30 → 自动补页至多 5 页（1 首查 + 5 补页 = 6 次调用后停止）
        assertEquals(6, repo.calls.size)
        val offsets = repo.calls.map { it.offset }
        assertEquals(listOf(0, 40, 80, 120, 160, 200), offsets)
        // 仍在能力范围内继续滚动可再触发
        assertTrue(viewModel.uiState.value.canLoadMore)
    }

    @Test
    fun freshCatalogCacheShowsInstantlyAndSkipsRevalidate() = runTest(testDispatcher) {
        val repo = FakeLtxRepository().apply {
            cachedCatalog = LtxCatalogCache(
                page = page(
                    entries = listOf(entry("CACHE-001"), entry("CACHE-002")),
                    total = 150,
                    snapshot = null,
                    updatedAt = 42L,
                ),
                storedAtMillis = System.currentTimeMillis(),
            )
            pageFactory = { _, _, offset ->
                // 翻页走网络（缓存页快照为 null → 服务端新建快照）
                page(entries = listOf(entry("ROW-$offset")), total = 150, snapshot = "fresh", offset = offset)
            }
        }
        val viewModel = createViewModel(ltxRepo = repo)
        viewModel.start()
        advanceUntilIdle()

        // 缓存新鲜（<5min）：即时展示缓存首页，不做整页网络重查；
        // 可见条目不足触发的自动补页快照为 null（服务端新建）
        assertEquals("CACHE-001", viewModel.uiState.value.items.firstOrNull()?.code)
        assertEquals("CACHE-002", viewModel.uiState.value.items.getOrNull(1)?.code)
        assertEquals(150, viewModel.uiState.value.totalCount)
        assertEquals(42L, viewModel.uiState.value.updatedAtMillis)
        assertFalse(viewModel.uiState.value.isLoading)
        assertTrue(viewModel.uiState.value.canLoadMore)
        assertTrue(repo.calls.isNotEmpty())
        assertEquals(2, repo.calls[0].offset)
        assertNull(repo.calls[0].snapshot)
    }

    @Test
    fun staleCatalogCacheShowsFirstThenRevalidateReplaces() = runTest(testDispatcher) {
        val repo = FakeLtxRepository().apply {
            cachedCatalog = LtxCatalogCache(
                page = page(entries = listOf(entry("CACHE-OLD")), total = 2, updatedAt = 1L),
                storedAtMillis = System.currentTimeMillis() - LTX_CATALOG_CACHE_FRESH_MILLIS - 1,
            )
            pageFactory = { _, _, _ ->
                page(entries = listOf(entry("NET-001"), entry("NET-002")), total = 2, snapshot = "s9", updatedAt = 99L)
            }
        }
        val viewModel = createViewModel(ltxRepo = repo)
        viewModel.start()
        advanceUntilIdle()

        // 过期缓存先展示（无加载转圈），随后网络重查整体替换
        assertEquals(1, repo.calls.size)
        assertEquals(listOf("NET-001", "NET-002"), viewModel.uiState.value.items.map { it.code })
        assertEquals(99L, viewModel.uiState.value.updatedAtMillis)
        assertFalse(viewModel.uiState.value.isRefreshing)
    }

    @Test
    fun disabledSettingYieldsEmptyList() = runTest(testDispatcher) {
        val viewModel = createViewModel(settings = FakeLtxSettings(enabled = false))
        viewModel.start()
        advanceUntilIdle()

        val state = viewModel.uiState.value
        assertFalse(state.isLoading)
        assertTrue(state.items.isEmpty())
        assertEquals(0, state.totalCount)
        assertNull(state.error)
    }
}

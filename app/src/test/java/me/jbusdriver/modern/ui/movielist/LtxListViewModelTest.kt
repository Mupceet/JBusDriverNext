package me.jbusdriver.modern.ui.movielist

import android.net.Uri
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import me.jbusdriver.modern.core.GSON
import me.jbusdriver.modern.core.cache.CacheEntry
import me.jbusdriver.modern.core.cache.CacheSource
import me.jbusdriver.modern.core.cache.CachedLoadEvent
import me.jbusdriver.modern.core.site.SiteConfig
import me.jbusdriver.modern.data.db.entity.LtxMovie
import me.jbusdriver.modern.data.db.entity.LtxResolveState
import me.jbusdriver.modern.data.repository.LocalVideoRepository
import me.jbusdriver.modern.data.repository.LtxMovieRepository
import me.jbusdriver.modern.data.repository.LtxRepository
import me.jbusdriver.modern.data.settings.LtxSettingsSource
import me.jbusdriver.modern.domain.model.DeleteResult
import me.jbusdriver.modern.domain.model.LocalVideo
import me.jbusdriver.modern.domain.model.LocalVideoGroup
import me.jbusdriver.modern.core.http.LtxWebSession
import me.jbusdriver.modern.domain.model.LocalVideoSummary
import me.jbusdriver.modern.domain.model.LtxEntry
import me.jbusdriver.modern.domain.model.LtxLibrary
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

@OptIn(ExperimentalCoroutinesApi::class)
class LtxListViewModelTest {

    private lateinit var testDispatcher: TestDispatcher

    private val library = LtxLibrary(
        updatedAtMillis = 42L,
        entries = listOf(
            LtxEntry(
                code = "AAA-003",
                links = listOf(LtxLink(LtxLinkType.ED2K, "ed2k://|file|a3|1|00000000000000000000000000000000|/", "a3", 1L)),
                isPending = false,
                addedAtMillis = 30L,
                score = 4.1,
            ),
            LtxEntry(code = "AAA-001", isPending = true, addedAtMillis = 10L, score = 4.9),
            LtxEntry(
                code = "AAA-002",
                links = listOf(LtxLink(LtxLinkType.ED2K, "ed2k://|file|a2|5|00000000000000000000000000000000|/", "a2", 5L)),
                isPending = false,
                addedAtMillis = 20L,
                score = null,
            ),
        )
    )

    private class FakeLtxRepository(val library: LtxLibrary) : LtxRepository {
        override fun observeLibrary(forceRefresh: Boolean): Flow<CachedLoadEvent<LtxLibrary>> =
            flowOf(CachedLoadEvent.Fresh(CacheEntry(library, 0L, CacheSource.Network, false)))

        override suspend fun currentLibrary(): LtxLibrary? = library
        override suspend fun findByCode(code: String): LtxEntry? = library.find(code)
        override suspend fun refresh(): LtxLibrary? = library
        override val verificationRequests: SharedFlow<Unit> = MutableSharedFlow()
        override val isFetching: StateFlow<Boolean> = MutableStateFlow(false)
        override fun adoptWebSession(session: LtxWebSession) = Unit
        override suspend fun currentOrigin(): String = "https://ltx.example.test"
        override suspend fun cachedPayloadBytes(): Long = 0L
    }

    private class FakeLtxMovieRepository : LtxMovieRepository {
        val resolveCalls = mutableListOf<String>()
        var resolveResult: String = LtxResolveState.RESOLVED

        /** 非空时冻结所有 resolve（用于构造队列积压场景），完成后记录调用。 */
        var gate: CompletableDeferred<Unit>? = null
        val rows = MutableStateFlow<List<LtxMovie>>(emptyList())

        override suspend fun resolve(code: String): String {
            gate?.await()
            resolveCalls.add(code)
            return resolveResult
        }

        override fun observeAll(): Flow<List<LtxMovie>> = rows
        override suspend fun findResolvedMovie(code: String, baseUrl: String): Movie? = null
    }

    private class FakeLtxSettings(enabled: Boolean = true) : LtxSettingsSource {
        override val ltxEnabled = MutableStateFlow(enabled)
        override suspend fun currentLtxBaseUrl() = "https://ltx.example.test"
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

    private fun createViewModel(
        ltxRepo: LtxRepository = FakeLtxRepository(library),
        movieRepo: FakeLtxMovieRepository = FakeLtxMovieRepository(),
        settings: LtxSettingsSource = FakeLtxSettings(),
        prefs: FakeCollectionUiPrefs = FakeCollectionUiPrefs(),
        collectRepo: me.jbusdriver.modern.data.repository.CollectRepository =
            me.jbusdriver.modern.test.StubCollectRepository(),
        localVideoRepo: LocalVideoRepository = StubLocalVideoRepo(),
    ) = LtxListViewModel(
        ltxRepository = ltxRepo,
        ltxMovieRepository = movieRepo,
        ltxSettings = settings,
        uiPrefsStore = prefs,
        siteConfig = FakeSiteConfig(),
        collectRepository = collectRepo,
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
    fun startLoadsLibrarySortedByAddedAtDescAndRebasesSnapshots() = runTest(testDispatcher) {
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
        val viewModel = createViewModel(movieRepo = movieRepo)

        viewModel.start()
        advanceUntilIdle()

        val state = viewModel.uiState.value
        assertEquals(3, state.totalCount)
        assertEquals(42L, state.updatedAtMillis)
        // 默认按入库时间倒序
        assertEquals(listOf("AAA-003", "AAA-002", "AAA-001"), state.items.map { it.code })
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
    fun statusFilterKeepsOnlyPendingEntries() = runTest(testDispatcher) {
        val viewModel = createViewModel()
        viewModel.start()
        advanceUntilIdle()

        viewModel.updateStatusFilter(LtxStatusFilter.PENDING)
        advanceUntilIdle()

        assertEquals(listOf("AAA-001"), viewModel.uiState.value.items.map { it.code })
    }

    @Test
    fun updateSortFieldAndDirectionReorderAndPersistPreference() = runTest(testDispatcher) {
        val prefs = FakeCollectionUiPrefs()
        val viewModel = createViewModel(prefs = prefs)
        viewModel.start()
        advanceUntilIdle()

        viewModel.updateSortField(LtxSortField.CODE)
        viewModel.updateSortDirection(LtxSortDirection.ASC)
        advanceUntilIdle()

        assertEquals(listOf("AAA-001", "AAA-002", "AAA-003"), viewModel.uiState.value.items.map { it.code })
        assertEquals("CODE", prefs.ltxSortField.value)
        assertEquals("ASC", prefs.ltxSortDirection.value)
    }

    @Test
    fun startAutoResolvesOnlyCollectedAndDownloadedCodes() = runTest(testDispatcher) {
        val movieRepo = FakeLtxMovieRepository()
        // 已收藏 AAA-001；本地已下载 AAA-002；AAA-003 两者皆非 → 不自动补全
        val collectRepo = object : me.jbusdriver.modern.test.StubCollectRepository() {
            override suspend fun getCollectedMovies(): List<Movie> =
                listOf(Movie("Collected", "", "AAA-001", "2026-01-01", "/AAA-001"))
        }
        val viewModel = createViewModel(
            movieRepo = movieRepo,
            collectRepo = collectRepo,
            localVideoRepo = StubLocalVideoRepo(downloaded = setOf("aaa-002")),
        )

        viewModel.start()
        advanceUntilIdle()

        assertEquals(listOf("AAA-001", "AAA-002"), movieRepo.resolveCalls.sorted())
    }

    @Test
    fun resolveNowIsIdempotentAndSkipsTerminalStates() = runTest(testDispatcher) {
        val movieRepo = FakeLtxMovieRepository().apply {
            // AAA-002 站内未收录（终态，点击不应触发解析）
            rows.value = listOf(LtxMovie(code = "AAA-002", resolveState = LtxResolveState.NOT_FOUND))
        }
        val viewModel = createViewModel(movieRepo = movieRepo)
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
    fun resolveNowJumpsAheadOfQueuedBatchWork() = runTest(testDispatcher) {
        // 5 个已下载条目构成批量队列；gate 冻结全部 worker，制造队列积压
        val bigLibrary = LtxLibrary(
            entries = (1..5).map { i ->
                LtxEntry(code = "BATCH-00$i", isPending = false, addedAtMillis = i.toLong())
            }
        )
        val pauseGate = CompletableDeferred<Unit>()
        val movieRepo = FakeLtxMovieRepository().apply { gate = pauseGate }
        val viewModel = createViewModel(
            ltxRepo = FakeLtxRepository(bigLibrary),
            movieRepo = movieRepo,
            localVideoRepo = StubLocalVideoRepo(downloaded = (1..5).map { "BATCH-00$it" }.toSet()),
        )
        viewModel.start()
        advanceUntilIdle()
        // 3 个 worker 冻结在队首三项上，BATCH-004/005 仍在队列
        assertEquals(emptyList<String>(), movieRepo.resolveCalls)

        // 用户点击仍在队列中的 BATCH-005 → 应插队到队首，先于 BATCH-004 执行
        viewModel.resolveNow("BATCH-005")
        pauseGate.complete(Unit)
        advanceUntilIdle()

        val calls = movieRepo.resolveCalls
        assertEquals(5, calls.size)
        assertTrue(
            "interacted BATCH-005 should jump ahead of queued BATCH-004, calls=$calls",
            calls.indexOf("BATCH-005") < calls.indexOf("BATCH-004")
        )
    }

    @Test
    fun requestResolveSettledEnqueuesVisibleUnresolvedEntries() = runTest(testDispatcher) {
        val movieRepo = FakeLtxMovieRepository().apply {
            // AAA-002 站内未收录（终态，停留补全应跳过）
            rows.value = listOf(LtxMovie(code = "AAA-002", resolveState = LtxResolveState.NOT_FOUND))
        }
        val viewModel = createViewModel(movieRepo = movieRepo)
        viewModel.start()
        advanceUntilIdle()

        viewModel.requestResolveSettled(0, 10)
        advanceUntilIdle()

        assertEquals(listOf("AAA-001", "AAA-003"), movieRepo.resolveCalls.sorted())
    }

    @Test
    fun requestResolveSettledJumpsAheadOfQueuedBatchWork() = runTest(testDispatcher) {
        // 5 个已下载条目构成批量队列；gate 冻结全部 worker，制造队列积压
        val bigLibrary = LtxLibrary(
            entries = (1..5).map { i ->
                LtxEntry(code = "BATCH-00$i", isPending = false, addedAtMillis = i.toLong())
            }
        )
        val pauseGate = CompletableDeferred<Unit>()
        val movieRepo = FakeLtxMovieRepository().apply { gate = pauseGate }
        val viewModel = createViewModel(
            ltxRepo = FakeLtxRepository(bigLibrary),
            movieRepo = movieRepo,
            localVideoRepo = StubLocalVideoRepo(downloaded = (1..5).map { "BATCH-00$it" }.toSet()),
        )
        viewModel.start()
        advanceUntilIdle()
        // 3 个 worker 冻结在批量队首三项上，BATCH-004/005 仍在队列
        assertEquals(emptyList<String>(), movieRepo.resolveCalls)

        // 停留窗口覆盖屏幕前两项（BATCH-005/004）→ 按屏幕顺序整体插队，
        // BATCH-005 应先于仍排在队尾的 BATCH-004 执行
        viewModel.requestResolveSettled(1, 4)
        pauseGate.complete(Unit)
        advanceUntilIdle()

        val calls = movieRepo.resolveCalls
        assertEquals(5, calls.size)
        assertTrue(
            "settled BATCH-005 should jump ahead of queued BATCH-004, calls=$calls",
            calls.indexOf("BATCH-005") < calls.indexOf("BATCH-004")
        )
    }

    @Test
    fun requestResolveVisibleQueuesInScreenOrderAheadOfBatchTail() = runTest(testDispatcher) {
        // 库：3 个未补全途经条目（屏幕序 DISC-001..003）+ 5 个已下载批量条目
        val bigLibrary = LtxLibrary(
            updatedAtMillis = 42L,
            entries = listOf(
                LtxEntry(code = "DISC-001", isPending = false, addedAtMillis = 800L),
                LtxEntry(code = "DISC-002", isPending = false, addedAtMillis = 700L),
                LtxEntry(code = "DISC-003", isPending = false, addedAtMillis = 600L),
                LtxEntry(code = "BATCH-001", isPending = false, addedAtMillis = 500L),
                LtxEntry(code = "BATCH-002", isPending = false, addedAtMillis = 400L),
                LtxEntry(code = "BATCH-003", isPending = false, addedAtMillis = 300L),
                LtxEntry(code = "BATCH-004", isPending = false, addedAtMillis = 200L),
                LtxEntry(code = "BATCH-005", isPending = false, addedAtMillis = 100L),
            )
        )
        val pauseGate = CompletableDeferred<Unit>()
        val movieRepo = FakeLtxMovieRepository().apply { gate = pauseGate }
        val viewModel = createViewModel(
            ltxRepo = FakeLtxRepository(bigLibrary),
            movieRepo = movieRepo,
            localVideoRepo = StubLocalVideoRepo(downloaded = (1..5).map { "BATCH-00$it" }.toSet()),
        )
        viewModel.start()
        advanceUntilIdle()
        // 3 个 worker 冻结在批量队首三项上，BATCH-004/005 仍在批量队列
        assertEquals(emptyList<String>(), movieRepo.resolveCalls)

        // 全库窗口途经一遍：途经条目按屏幕顺序入队，先于仍在排队的批量条目；
        // 已排队（批量）/在途的条目不重复入队
        viewModel.requestResolveVisible(0, 16)
        pauseGate.complete(Unit)
        advanceUntilIdle()

        val calls = movieRepo.resolveCalls
        assertEquals(8, calls.size)
        assertTrue(
            "discovery should keep screen order DISC-001<002<003, calls=$calls",
            calls.indexOf("DISC-001") < calls.indexOf("DISC-002") &&
                calls.indexOf("DISC-002") < calls.indexOf("DISC-003")
        )
        assertTrue(
            "discovery DISC-003 should run ahead of queued batch BATCH-004, calls=$calls",
            calls.indexOf("DISC-003") < calls.indexOf("BATCH-004")
        )
    }

    @Test
    fun requestResolveSettledJumpsAheadOfDiscoveryQueue() = runTest(testDispatcher) {
        // 库：5 个未补全条目，无批量任务；gate 冻结全部 worker 制造途经队列积压
        val bigLibrary = LtxLibrary(
            updatedAtMillis = 42L,
            entries = (1..5).map { i ->
                LtxEntry(code = "SCREEN-00$i", isPending = false, addedAtMillis = (6 - i).toLong())
            }
        )
        val pauseGate = CompletableDeferred<Unit>()
        val movieRepo = FakeLtxMovieRepository().apply { gate = pauseGate }
        val viewModel = createViewModel(ltxRepo = FakeLtxRepository(bigLibrary), movieRepo = movieRepo)
        viewModel.start()
        advanceUntilIdle()

        // 全库窗口途经一遍：3 个 worker 冻结在 SCREEN-001..003 上，004/005 在途经队列
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
    fun requestResolveVisibleSkipsTerminalStatesAndDuplicates() = runTest(testDispatcher) {
        val movieRepo = FakeLtxMovieRepository().apply {
            // AAA-002 站内未收录（终态，途经入队应跳过）
            rows.value = listOf(LtxMovie(code = "AAA-002", resolveState = LtxResolveState.NOT_FOUND))
        }
        val viewModel = createViewModel(movieRepo = movieRepo)
        viewModel.start()
        advanceUntilIdle()

        viewModel.requestResolveVisible(0, 10)
        viewModel.requestResolveVisible(0, 10) // 同窗口重复触发幂等
        advanceUntilIdle()

        assertEquals(listOf("AAA-001", "AAA-003"), movieRepo.resolveCalls.sorted())
        assertTrue(viewModel.uiState.value.resolvingCodes.isEmpty())
    }

    @Test
    fun resolveFilterDefaultsToHidingNotFoundEntries() = runTest(testDispatcher) {
        val movieRepo = FakeLtxMovieRepository().apply {
            // AAA-002 站内未收录（终态）
            rows.value = listOf(LtxMovie(code = "AAA-002", resolveState = LtxResolveState.NOT_FOUND))
        }
        val viewModel = createViewModel(movieRepo = movieRepo)
        viewModel.start()
        advanceUntilIdle()

        // 默认（已收錄 + 未檢測）隐藏未收錄，且不计入筛选角标
        assertEquals(listOf("AAA-003", "AAA-001"), viewModel.uiState.value.items.map { it.code })
        assertEquals(0, viewModel.uiState.value.activeFilterCount)

        // 勾选未收錄 → 三态全选，全部展示
        viewModel.toggleResolveFilter(LtxResolveFilter.NOT_FOUND)
        advanceUntilIdle()
        assertEquals(listOf("AAA-003", "AAA-002", "AAA-001"), viewModel.uiState.value.items.map { it.code })
        assertEquals(1, viewModel.uiState.value.activeFilterCount)
    }

    @Test
    fun resolveFilterToggleOffLastResetsToAll() = runTest(testDispatcher) {
        val viewModel = createViewModel()
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
    fun sort_ByScore() = runTest(testDispatcher) {
        val viewModel = createViewModel()
        viewModel.start()
        advanceUntilIdle()

        viewModel.updateSortField(LtxSortField.SCORE)
        advanceUntilIdle()
        // 网站同款加权评分：票数先验把低票高分（AAA-001 pending 4.9）拉回均值，
        // 与 AAA-003（4.1、无票）同分；AAA-001 pending 再扣 1 分排到 AAA-003 之后，
        // 无评分（AAA-002）稳定排尾部（默认倒序）
        assertEquals(listOf("AAA-003", "AAA-001", "AAA-002"), viewModel.uiState.value.items.map { it.code })
    }

    @Test
    fun sort_ByDatePrefersLtxOriginalDateOverSnapshot() = runTest(testDispatcher) {
        val movieRepo = FakeLtxMovieRepository().apply {
            // AAA-003 补全后携带更新的站内日期，但排序须以 LTX 库原始 date 为准
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
        val datedLibrary = LtxLibrary(
            updatedAtMillis = 42L,
            entries = listOf(
                LtxEntry(code = "AAA-001", isPending = true, addedAtMillis = 10L, date = "2024-02-02"),
                // 无 LTX date 且无快照 → 无日期，稳定排尾
                LtxEntry(code = "AAA-002", isPending = false, addedAtMillis = 20L),
                LtxEntry(code = "AAA-003", isPending = false, addedAtMillis = 30L, date = "2025-01-01"),
            )
        )
        val viewModel = createViewModel(
            ltxRepo = FakeLtxRepository(datedLibrary),
            movieRepo = movieRepo,
        )
        viewModel.start()
        advanceUntilIdle()

        viewModel.updateSortField(LtxSortField.DATE)
        advanceUntilIdle()
        // 倒序：2025-01-01 > 2024-02-02 > 无日期排尾（站内 2026-06-01 不得干扰）
        assertEquals(
            listOf("AAA-003", "AAA-001", "AAA-002"),
            viewModel.uiState.value.items.map { it.code }
        )
        // 卡片日期取 LTX 原始值，补全不修订
        assertEquals("2025-01-01", viewModel.uiState.value.items[0].date)
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

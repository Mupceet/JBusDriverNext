package me.jbusdriver.modern.ui.detail

import android.net.Uri
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestDispatcher
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import me.jbusdriver.R
import me.jbusdriver.modern.data.repository.CollectRepository
import me.jbusdriver.modern.data.repository.LocalVideoRepository
import me.jbusdriver.modern.data.repository.LtxRepository
import me.jbusdriver.modern.data.repository.MagnetRepository
import me.jbusdriver.modern.data.repository.MovieDetailRepository
import me.jbusdriver.modern.data.db.entity.LinkItem
import me.jbusdriver.modern.data.settings.LtxSettingsSource
import me.jbusdriver.modern.data.settings.TranslationSettingsReader
import me.jbusdriver.modern.data.translation.TranslationProvider
import me.jbusdriver.modern.data.translation.TranslationRepository
import me.jbusdriver.modern.data.translation.TranslatorConfig
import me.jbusdriver.modern.core.cache.CacheEntry
import me.jbusdriver.modern.core.cache.CacheSource
import me.jbusdriver.modern.core.cache.CachedLoadEvent
import me.jbusdriver.modern.core.site.SiteConfig
import me.jbusdriver.modern.domain.model.ActressInfo
import me.jbusdriver.modern.domain.model.DeleteResult
import me.jbusdriver.modern.domain.model.Header
import me.jbusdriver.modern.domain.model.LocalVideo
import me.jbusdriver.modern.domain.model.LtxEntry
import me.jbusdriver.modern.domain.model.LtxLibrary
import me.jbusdriver.modern.domain.model.LtxLink
import me.jbusdriver.modern.domain.model.LtxLinkType
import me.jbusdriver.modern.domain.model.LocalVideoGroup
import me.jbusdriver.modern.domain.model.LocalVideoSummary
import me.jbusdriver.modern.domain.model.Magnet
import me.jbusdriver.modern.domain.model.Movie
import me.jbusdriver.modern.domain.model.MovieDetail
import me.jbusdriver.modern.ui.UserMessage
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class MovieDetailViewModelTest {

    private lateinit var testDispatcher: TestDispatcher

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

    private val stubCollectRepo = object : CollectRepository {
        override suspend fun isCollected(linkItem: LinkItem) = false
        override suspend fun addCollect(linkItem: LinkItem) = true
        override suspend fun removeCollect(linkItem: LinkItem) = true
        override suspend fun isMovieCollected(movie: Movie) = false
        override suspend fun toggleMovieCollect(movie: Movie, categoryId: Int?) = true
        override suspend fun isActressCollected(actress: ActressInfo) = false
        override suspend fun toggleActressCollect(actress: ActressInfo, categoryId: Int?) = true
        override suspend fun getCollectedMovies(): List<Movie> = emptyList()
        override suspend fun getCollectedActresses(): List<ActressInfo> = emptyList()
        override suspend fun getCollectedLinkItems(dbType: Int): List<LinkItem> = emptyList()
        override suspend fun exportCollectionsJson() = "{}"
        override suspend fun importCollectionsFromJson(json: String) = 0 to 0
    }

    private val stubMagnetRepo = object : MagnetRepository {
        override suspend fun fetchMagnets(gid: String, uc: String): List<Magnet> = emptyList()
    }

    private val stubLocalVideoRepo = object : LocalVideoRepository {
        override fun observeForCode(code: String) =
            flowOf(listOf(LocalVideo("ABC-001", "ABC-001.mp4", "content://x/ABC-001", "video/mp4", 1L)))
        override fun observeDownloadedCodes() = flowOf(emptySet<String>())
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

    private val stubTranslationRepo = object : TranslationRepository {
        var calls = 0
        override suspend fun translate(text: String): Result<String> {
            calls++
            return Result.success("譯文:$text")
        }
    }

    private val stubTranslationSettings = object : TranslationSettingsReader {
        override val translateByDefault = MutableStateFlow(false)
        override suspend fun currentTranslatorConfig() =
            TranslatorConfig(TranslationProvider.GOOGLE, "", "")
    }

    /** 每个测试方法独立的 JUnit 实例上可覆写的 LTX 桩数据。 */
    private var stubLtxEntry: LtxEntry? = null
    private var stubLtxLookupCalls = 0

    private val stubLtxRepo = object : LtxRepository {
        override fun observeLibrary(forceRefresh: Boolean) = flowOf(
            CachedLoadEvent.Fresh(CacheEntry(LtxLibrary(), 0L, CacheSource.Network, false))
        )
        override suspend fun currentLibrary(): LtxLibrary? = null
        override suspend fun findByCode(code: String): LtxEntry? {
            stubLtxLookupCalls++
            return stubLtxEntry
        }
        override suspend fun refresh(): LtxLibrary? = null
    }

    private val stubLtxSettings = object : LtxSettingsSource {
        override val ltxEnabled = MutableStateFlow(true)
        override suspend fun currentLtxBaseUrl() = "https://ltx.example.test"
    }

    private val stubSiteConfig = object : SiteConfig {
        override var baseUrl: String = "https://www.javbus.com"
        override fun resolve(pathOrUrl: String): String =
            if (pathOrUrl.startsWith("http")) {
                pathOrUrl
            } else {
                baseUrl.trimEnd('/') + (if (pathOrUrl.startsWith("/")) "" else "/") + pathOrUrl
            }
    }

    private fun defaultOnSettings() = object : TranslationSettingsReader {
        override val translateByDefault = MutableStateFlow(true)
        override suspend fun currentTranslatorConfig() =
            TranslatorConfig(TranslationProvider.GOOGLE, "", "")
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
    fun loadDetail_loadsMovieDetail() = runTest(testDispatcher) {
        val detailRepo = object : MovieDetailRepository {
            override suspend fun getMovieDetail(url: String, forceRefresh: Boolean) = testDetail
        }
        val viewModel = MovieDetailViewModel(detailRepo, stubCollectRepo, stubMagnetRepo, stubLocalVideoRepo, stubTranslationRepo, stubTranslationSettings, stubLtxRepo, stubLtxSettings, stubSiteConfig)

        viewModel.loadDetail("http://example.com/ABC-001")
        advanceUntilIdle()

        assertNotNull(viewModel.uiState.value.movieDetail)
        assertEquals("Test Movie", viewModel.uiState.value.movieDetail!!.title)
        assertFalse(viewModel.uiState.value.isLoading)
        assertNull(viewModel.uiState.value.error)
    }

    @Test
    fun loadDetail_handlesError() = runTest(testDispatcher) {
        val detailRepo = object : MovieDetailRepository {
            override suspend fun getMovieDetail(url: String, forceRefresh: Boolean) =
                throw RuntimeException("Network error")
        }
        val viewModel = MovieDetailViewModel(detailRepo, stubCollectRepo, stubMagnetRepo, stubLocalVideoRepo, stubTranslationRepo, stubTranslationSettings, stubLtxRepo, stubLtxSettings, stubSiteConfig)

        viewModel.loadDetail("http://example.com/ABC-001")
        advanceUntilIdle()

        assertEquals(R.string.load_failed, viewModel.uiState.value.error)
        assertFalse(viewModel.uiState.value.isLoading)
    }

    @Test
    fun loadDetail_censoredUrlFails_fallsBackToUncensored() = runTest(testDispatcher) {
        val requestedUrls = mutableListOf<String>()
        val detailRepo = object : MovieDetailRepository {
            override suspend fun getMovieDetail(url: String, forceRefresh: Boolean): MovieDetail {
                requestedUrls += url
                if ("/uncensored/" !in url) throw RuntimeException("not found")
                return testDetail
            }
        }
        val viewModel = MovieDetailViewModel(detailRepo, stubCollectRepo, stubMagnetRepo, stubLocalVideoRepo, stubTranslationRepo, stubTranslationSettings, stubLtxRepo, stubLtxSettings, stubSiteConfig)

        viewModel.loadDetail(stubSiteConfig.resolve("/ABC-001"))
        advanceUntilIdle()

        assertEquals(2, requestedUrls.size)
        assertEquals(stubSiteConfig.resolve("/uncensored/ABC-001"), requestedUrls[1])
        assertNotNull(viewModel.uiState.value.movieDetail)
        assertNull(viewModel.uiState.value.error)
    }

    @Test
    fun loadDetail_uncensoredFallbackFails_showsError() = runTest(testDispatcher) {
        val requestedUrls = mutableListOf<String>()
        val detailRepo = object : MovieDetailRepository {
            override suspend fun getMovieDetail(url: String, forceRefresh: Boolean): MovieDetail {
                requestedUrls += url
                throw RuntimeException("not found")
            }
        }
        val viewModel = MovieDetailViewModel(detailRepo, stubCollectRepo, stubMagnetRepo, stubLocalVideoRepo, stubTranslationRepo, stubTranslationSettings, stubLtxRepo, stubLtxSettings, stubSiteConfig)

        viewModel.loadDetail(stubSiteConfig.resolve("/ABC-001"))
        advanceUntilIdle()

        assertEquals(2, requestedUrls.size)
        assertEquals(R.string.load_failed, viewModel.uiState.value.error)
    }

    @Test
    fun loadDetail_explicitCensorType_doesNotFallback() = runTest(testDispatcher) {
        val requestedUrls = mutableListOf<String>()
        val detailRepo = object : MovieDetailRepository {
            override suspend fun getMovieDetail(url: String, forceRefresh: Boolean): MovieDetail {
                requestedUrls += url
                throw RuntimeException("not found")
            }
        }
        val viewModel = MovieDetailViewModel(detailRepo, stubCollectRepo, stubMagnetRepo, stubLocalVideoRepo, stubTranslationRepo, stubTranslationSettings, stubLtxRepo, stubLtxSettings, stubSiteConfig)

        viewModel.loadDetail(stubSiteConfig.resolve("/ABC-001"), censorType = "UNCENSORED")
        advanceUntilIdle()

        assertEquals(1, requestedUrls.size)
        assertEquals(R.string.load_failed, viewModel.uiState.value.error)
    }

    @Test
    fun refresh_reloadsDetail() = runTest(testDispatcher) {
        var callCount = 0
        val detailRepo = object : MovieDetailRepository {
            override suspend fun getMovieDetail(url: String, forceRefresh: Boolean) =
                testDetail.also { callCount++ }
        }
        val viewModel = MovieDetailViewModel(detailRepo, stubCollectRepo, stubMagnetRepo, stubLocalVideoRepo, stubTranslationRepo, stubTranslationSettings, stubLtxRepo, stubLtxSettings, stubSiteConfig)

        viewModel.loadDetail("http://example.com/ABC-001")
        advanceUntilIdle()
        assertEquals(1, callCount)

        viewModel.refresh()
        advanceUntilIdle()
        assertEquals(2, callCount)

        assertFalse(viewModel.uiState.value.isRefreshing)
    }

    @Test
    fun loadDetail_usesReleaseDateHeaderWhenCheckingCollection() = runTest(testDispatcher) {
        var capturedMovie: Movie? = null
        val detail = testDetail.copy(
            headers = listOf(
                Header("番号", "ABC-001", ""),
                Header("發行日期", "2024-01-01", "")
            )
        )
        val collectRepo = object : CollectRepository by stubCollectRepo {
            override suspend fun isMovieCollected(movie: Movie): Boolean {
                capturedMovie = movie
                return false
            }
        }
        val detailRepo = object : MovieDetailRepository {
            override suspend fun getMovieDetail(url: String, forceRefresh: Boolean) = detail
        }
        val viewModel = MovieDetailViewModel(detailRepo, collectRepo, stubMagnetRepo, stubLocalVideoRepo, stubTranslationRepo, stubTranslationSettings, stubLtxRepo, stubLtxSettings, stubSiteConfig)

        viewModel.loadDetail("http://example.com/ABC-001")
        advanceUntilIdle()

        assertEquals("2024-01-01", capturedMovie?.date)
    }

    @Test
    fun loadDetail_storesGidAndUc() = runTest(testDispatcher) {
        val detailWithGid = testDetail.copy(gid = "12345", uc = "67890")
        val detailRepo = object : MovieDetailRepository {
            override suspend fun getMovieDetail(url: String, forceRefresh: Boolean) = detailWithGid
        }
        val viewModel = MovieDetailViewModel(detailRepo, stubCollectRepo, stubMagnetRepo, stubLocalVideoRepo, stubTranslationRepo, stubTranslationSettings, stubLtxRepo, stubLtxSettings, stubSiteConfig)

        viewModel.loadDetail("http://example.com/ABC-001")
        advanceUntilIdle()

        assertEquals("12345", viewModel.uiState.value.gid)
        assertEquals("67890", viewModel.uiState.value.uc)
    }

    @Test
    fun loadDetail_loadsLocalVideos() = runTest(testDispatcher) {
        val detailRepo = object : MovieDetailRepository {
            override suspend fun getMovieDetail(url: String, forceRefresh: Boolean) = testDetail
        }
        val viewModel = MovieDetailViewModel(detailRepo, stubCollectRepo, stubMagnetRepo, stubLocalVideoRepo, stubTranslationRepo, stubTranslationSettings, stubLtxRepo, stubLtxSettings, stubSiteConfig)

        viewModel.loadDetail("http://example.com/ABC-001")
        advanceUntilIdle()

        val videos = viewModel.uiState.value.localVideos
        assertEquals(1, videos.size)
        assertEquals("ABC-001", videos.first().code)
    }

    @Test
    fun loadDetail_noCode_leavesLocalVideosEmpty() = runTest(testDispatcher) {
        val detailNoCode = testDetail.copy(headers = emptyList())
        val detailRepo = object : MovieDetailRepository {
            override suspend fun getMovieDetail(url: String, forceRefresh: Boolean) = detailNoCode
        }
        val viewModel = MovieDetailViewModel(detailRepo, stubCollectRepo, stubMagnetRepo, stubLocalVideoRepo, stubTranslationRepo, stubTranslationSettings, stubLtxRepo, stubLtxSettings, stubSiteConfig)

        viewModel.loadDetail("http://example.com/none")
        advanceUntilIdle()

        assertTrue(viewModel.uiState.value.localVideos.isEmpty())
    }

    @Test
    fun loadDetail_snapshotsMetadataForCode() = runTest(testDispatcher) {
        var snapped: Triple<String, String, String>? = null
        val localRepo = object : LocalVideoRepository by stubLocalVideoRepo {
            override suspend fun snapshotMetadata(
                code: String, title: String, imageUrl: String, date: String, censorType: String?
            ) { snapped = Triple(code, title, imageUrl) }
        }
        val detailRepo = object : MovieDetailRepository {
            override suspend fun getMovieDetail(url: String, forceRefresh: Boolean) = testDetail
        }
        val viewModel = MovieDetailViewModel(detailRepo, stubCollectRepo, stubMagnetRepo, localRepo, stubTranslationRepo, stubTranslationSettings, stubLtxRepo, stubLtxSettings, stubSiteConfig)

        viewModel.loadDetail("http://example.com/ABC-001")
        advanceUntilIdle()

        assertEquals("ABC-001", snapped?.first)
        assertEquals("Test Movie", snapped?.second)
        assertEquals("http://cover.jpg", snapped?.third)
    }

    @Test
    fun loadDetail_defaultOff_keepsTitleTranslationHidden() = runTest(testDispatcher) {
        val detailRepo = object : MovieDetailRepository {
            override suspend fun getMovieDetail(url: String, forceRefresh: Boolean) = testDetail
        }
        val viewModel = MovieDetailViewModel(detailRepo, stubCollectRepo, stubMagnetRepo, stubLocalVideoRepo, stubTranslationRepo, stubTranslationSettings, stubLtxRepo, stubLtxSettings, stubSiteConfig)

        viewModel.loadDetail("http://example.com/ABC-001")
        advanceUntilIdle()

        assertEquals(TitleTranslationState.Hidden, viewModel.uiState.value.titleTranslation)
    }

    @Test
    fun loadDetail_defaultOn_autoTranslatesTitle() = runTest(testDispatcher) {
        val detailRepo = object : MovieDetailRepository {
            override suspend fun getMovieDetail(url: String, forceRefresh: Boolean) = testDetail
        }
        val viewModel = MovieDetailViewModel(detailRepo, stubCollectRepo, stubMagnetRepo, stubLocalVideoRepo, stubTranslationRepo, defaultOnSettings(), stubLtxRepo, stubLtxSettings, stubSiteConfig)

        viewModel.loadDetail("http://example.com/ABC-001")
        advanceUntilIdle()

        assertEquals(TitleTranslationState.Translated("譯文:Test Movie"), viewModel.uiState.value.titleTranslation)
    }

    @Test
    fun toggleTitleTranslation_switchesBetweenOriginalAndTranslated() = runTest(testDispatcher) {
        val detailRepo = object : MovieDetailRepository {
            override suspend fun getMovieDetail(url: String, forceRefresh: Boolean) = testDetail
        }
        val viewModel = MovieDetailViewModel(detailRepo, stubCollectRepo, stubMagnetRepo, stubLocalVideoRepo, stubTranslationRepo, stubTranslationSettings, stubLtxRepo, stubLtxSettings, stubSiteConfig)

        viewModel.loadDetail("http://example.com/ABC-001")
        advanceUntilIdle()
        assertEquals(TitleTranslationState.Hidden, viewModel.uiState.value.titleTranslation)

        viewModel.toggleTitleTranslation()
        advanceUntilIdle()
        assertEquals(TitleTranslationState.Translated("譯文:Test Movie"), viewModel.uiState.value.titleTranslation)

        viewModel.toggleTitleTranslation()
        advanceUntilIdle()
        assertEquals(TitleTranslationState.Hidden, viewModel.uiState.value.titleTranslation)
    }

    @Test
    fun toggleTitleTranslation_failureResetsAndEmitsMessage() = runTest(testDispatcher) {
        val detailRepo = object : MovieDetailRepository {
            override suspend fun getMovieDetail(url: String, forceRefresh: Boolean) = testDetail
        }
        val failingRepo = object : TranslationRepository {
            override suspend fun translate(text: String): Result<String> =
                Result.failure(RuntimeException("boom"))
        }
        val viewModel = MovieDetailViewModel(detailRepo, stubCollectRepo, stubMagnetRepo, stubLocalVideoRepo, failingRepo, stubTranslationSettings, stubLtxRepo, stubLtxSettings, stubSiteConfig)
        val messages = mutableListOf<UserMessage>()
        backgroundScope.launch(UnconfinedTestDispatcher(testDispatcher.scheduler)) {
            viewModel.messages.collect { messages.add(it) }
        }

        viewModel.loadDetail("http://example.com/ABC-001")
        advanceUntilIdle()
        viewModel.toggleTitleTranslation()
        advanceUntilIdle()

        assertEquals(TitleTranslationState.Hidden, viewModel.uiState.value.titleTranslation)
        assertEquals(R.string.translate_failed, messages.single().resId)
    }

    @Test
    fun toggleTitleTranslation_configErrorEmitsConfigMessage() = runTest(testDispatcher) {
        val detailRepo = object : MovieDetailRepository {
            override suspend fun getMovieDetail(url: String, forceRefresh: Boolean) = testDetail
        }
        val configErrorRepo = object : TranslationRepository {
            override suspend fun translate(text: String): Result<String> =
                Result.failure(me.jbusdriver.modern.data.translation.TranslationConfigException("bad"))
        }
        val viewModel = MovieDetailViewModel(detailRepo, stubCollectRepo, stubMagnetRepo, stubLocalVideoRepo, configErrorRepo, stubTranslationSettings, stubLtxRepo, stubLtxSettings, stubSiteConfig)
        val messages = mutableListOf<UserMessage>()
        backgroundScope.launch(UnconfinedTestDispatcher(testDispatcher.scheduler)) {
            viewModel.messages.collect { messages.add(it) }
        }

        viewModel.loadDetail("http://example.com/ABC-001")
        advanceUntilIdle()
        viewModel.toggleTitleTranslation()
        advanceUntilIdle()

        assertEquals(R.string.translate_config_error, messages.single().resId)
    }

    @Test
    fun toggleTitleTranslation_rateLimitedEmitsDedicatedMessage() = runTest(testDispatcher) {
        val detailRepo = object : MovieDetailRepository {
            override suspend fun getMovieDetail(url: String, forceRefresh: Boolean) = testDetail
        }
        val rateLimitedRepo = object : TranslationRepository {
            override suspend fun translate(text: String): Result<String> =
                Result.failure(me.jbusdriver.modern.data.translation.TranslationHttpException(429))
        }
        val viewModel = MovieDetailViewModel(detailRepo, stubCollectRepo, stubMagnetRepo, stubLocalVideoRepo, rateLimitedRepo, stubTranslationSettings, stubLtxRepo, stubLtxSettings, stubSiteConfig)
        val messages = mutableListOf<UserMessage>()
        backgroundScope.launch(UnconfinedTestDispatcher(testDispatcher.scheduler)) {
            viewModel.messages.collect { messages.add(it) }
        }

        viewModel.loadDetail("http://example.com/ABC-001")
        advanceUntilIdle()
        viewModel.toggleTitleTranslation()
        advanceUntilIdle()

        assertEquals(TitleTranslationState.Hidden, viewModel.uiState.value.titleTranslation)
        assertEquals(R.string.translate_rate_limited, messages.single().resId)
    }

    @Test
    fun toggleTitleTranslation_otherHttpErrorEmitsGenericMessage() = runTest(testDispatcher) {
        val detailRepo = object : MovieDetailRepository {
            override suspend fun getMovieDetail(url: String, forceRefresh: Boolean) = testDetail
        }
        val httpErrorRepo = object : TranslationRepository {
            override suspend fun translate(text: String): Result<String> =
                Result.failure(me.jbusdriver.modern.data.translation.TranslationHttpException(500))
        }
        val viewModel = MovieDetailViewModel(detailRepo, stubCollectRepo, stubMagnetRepo, stubLocalVideoRepo, httpErrorRepo, stubTranslationSettings, stubLtxRepo, stubLtxSettings, stubSiteConfig)
        val messages = mutableListOf<UserMessage>()
        backgroundScope.launch(UnconfinedTestDispatcher(testDispatcher.scheduler)) {
            viewModel.messages.collect { messages.add(it) }
        }

        viewModel.loadDetail("http://example.com/ABC-001")
        advanceUntilIdle()
        viewModel.toggleTitleTranslation()
        advanceUntilIdle()

        assertEquals(R.string.translate_failed, messages.single().resId)
    }

    @Test
    fun refresh_reappliesDefaultTranslationPolicyForNewTitle() = runTest(testDispatcher) {
        var title = "Title One"
        val detailRepo = object : MovieDetailRepository {
            override suspend fun getMovieDetail(url: String, forceRefresh: Boolean) =
                testDetail.copy(title = title)
        }
        val viewModel = MovieDetailViewModel(detailRepo, stubCollectRepo, stubMagnetRepo, stubLocalVideoRepo, stubTranslationRepo, defaultOnSettings(), stubLtxRepo, stubLtxSettings, stubSiteConfig)

        viewModel.loadDetail("http://example.com/ABC-001")
        advanceUntilIdle()
        assertEquals(TitleTranslationState.Translated("譯文:Title One"), viewModel.uiState.value.titleTranslation)

        title = "Title Two"
        viewModel.refresh()
        advanceUntilIdle()
        assertEquals(TitleTranslationState.Translated("譯文:Title Two"), viewModel.uiState.value.titleTranslation)
    }

    @Test
    fun refresh_staleRequestDoesNotOverwriteNewerTitle() = runTest(testDispatcher) {
        var title = "Title One"
        val detailRepo = object : MovieDetailRepository {
            override suspend fun getMovieDetail(url: String, forceRefresh: Boolean) =
                testDetail.copy(title = title)
        }
        // 每次请求阻塞在自己的门上,手动放行以控制完成顺序
        val gates = mutableListOf<CompletableDeferred<Unit>>()
        val gatedRepo = object : TranslationRepository {
            override suspend fun translate(text: String): Result<String> {
                val gate = CompletableDeferred<Unit>()
                gates.add(gate)
                gate.await()
                return Result.success("譯文:$text")
            }
        }
        val viewModel = MovieDetailViewModel(detailRepo, stubCollectRepo, stubMagnetRepo, stubLocalVideoRepo, gatedRepo, defaultOnSettings(), stubLtxRepo, stubLtxSettings, stubSiteConfig)

        viewModel.loadDetail("http://example.com/ABC-001")
        advanceUntilIdle()
        assertEquals(1, gates.size) // 请求 A:Title One,飞行中

        title = "Title Two"
        viewModel.refresh()
        advanceUntilIdle()
        assertEquals(2, gates.size) // 请求 B:Title Two,飞行中;策略已重置为 Loading

        // 区分性窗口:旧请求在新请求仍在 Loading 时完成 —— 身份守卫必须拒绝它
        gates[0].complete(Unit)
        advanceUntilIdle()
        assertEquals(TitleTranslationState.Loading, viewModel.uiState.value.titleTranslation)

        gates[1].complete(Unit) // 新请求完成,正常落地
        advanceUntilIdle()
        assertEquals(TitleTranslationState.Translated("譯文:Title Two"), viewModel.uiState.value.titleTranslation)
    }

    @Test
    fun loadDetail_populatesLtxLinksFromLibrary() = runTest(testDispatcher) {
        val detailRepo = object : MovieDetailRepository {
            override suspend fun getMovieDetail(url: String, forceRefresh: Boolean) = testDetail
        }
        stubLtxEntry = LtxEntry(
            code = "ABC-001",
            links = listOf(
                LtxLink(LtxLinkType.ED2K, "ed2k://|file|ABC-001.mp4|1024|00000000000000000000000000000000|/", "ABC-001.mp4", 1024L)
            ),
            pendingLinks = listOf(
                LtxLink(LtxLinkType.MAGNET, "magnet:?xt=urn:btih:x&dn=ABC-001", "ABC-001", null)
            ),
        )
        val viewModel = MovieDetailViewModel(detailRepo, stubCollectRepo, stubMagnetRepo, stubLocalVideoRepo, stubTranslationRepo, stubTranslationSettings, stubLtxRepo, stubLtxSettings, stubSiteConfig)

        viewModel.loadDetail("http://example.com/ABC-001")
        advanceUntilIdle()

        assertEquals(2, viewModel.uiState.value.ltxLinks.size)
        assertFalse(viewModel.uiState.value.ltxLinks[0].isPending)
        assertEquals(LtxLinkType.ED2K, viewModel.uiState.value.ltxLinks[0].type)
        assertTrue(viewModel.uiState.value.ltxLinks[1].isPending)
        assertEquals(1, stubLtxLookupCalls)
    }

    @Test
    fun loadDetail_dedupesLtxLinksSharedByConfirmedAndPending() = runTest(testDispatcher) {
        val detailRepo = object : MovieDetailRepository {
            override suspend fun getMovieDetail(url: String, forceRefresh: Boolean) = testDetail
        }
        // 同一 URI 同时出现在 links 与 pending_links（上游确认过渡期 / 旧磁盘缓存）：
        // 磁力弹窗以 uri 作 LazyColumn key，必须去重且保留已确认一项
        val duplicate = LtxLink(LtxLinkType.ED2K, "ed2k://|file|ABC-001.mp4|1024|00000000000000000000000000000000|/", "ABC-001.mp4", 1024L)
        stubLtxEntry = LtxEntry(
            code = "ABC-001",
            links = listOf(duplicate),
            pendingLinks = listOf(duplicate),
        )
        val viewModel = MovieDetailViewModel(detailRepo, stubCollectRepo, stubMagnetRepo, stubLocalVideoRepo, stubTranslationRepo, stubTranslationSettings, stubLtxRepo, stubLtxSettings, stubSiteConfig)

        viewModel.loadDetail("http://example.com/ABC-001")
        advanceUntilIdle()

        val links = viewModel.uiState.value.ltxLinks
        assertEquals(1, links.size)
        assertFalse(links[0].isPending)
    }

    @Test
    fun loadDetail_skipsLtxLookupWhenDisabled() = runTest(testDispatcher) {
        val detailRepo = object : MovieDetailRepository {
            override suspend fun getMovieDetail(url: String, forceRefresh: Boolean) = testDetail
        }
        stubLtxSettings.ltxEnabled.value = false
        val viewModel = MovieDetailViewModel(detailRepo, stubCollectRepo, stubMagnetRepo, stubLocalVideoRepo, stubTranslationRepo, stubTranslationSettings, stubLtxRepo, stubLtxSettings, stubSiteConfig)

        viewModel.loadDetail("http://example.com/ABC-001")
        advanceUntilIdle()

        assertEquals(0, stubLtxLookupCalls)
        assertTrue(viewModel.uiState.value.ltxLinks.isEmpty())
    }
}

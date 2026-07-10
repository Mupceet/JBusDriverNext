package me.jbusdriver.modern.ui.movielist

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOf
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
import me.jbusdriver.modern.data.repository.CollectRepository
import me.jbusdriver.modern.data.repository.LocalVideoRepository
import me.jbusdriver.modern.data.repository.MovieRepository
import me.jbusdriver.modern.data.db.entity.LinkItem
import me.jbusdriver.modern.domain.model.ActressDetail
import me.jbusdriver.modern.domain.model.ActressInfo
import me.jbusdriver.modern.domain.model.ActressCategory
import me.jbusdriver.modern.domain.model.UncensoredActressCategory
import me.jbusdriver.modern.domain.model.DataSourceType
import me.jbusdriver.modern.domain.model.DeleteResult
import me.jbusdriver.modern.domain.model.GenreGroup
import me.jbusdriver.modern.domain.model.LocalVideo
import me.jbusdriver.modern.domain.model.LocalVideoGroup
import me.jbusdriver.modern.domain.model.LocalVideoSummary
import me.jbusdriver.modern.domain.model.Movie
import me.jbusdriver.modern.domain.model.MovieFilterInfo
import me.jbusdriver.modern.domain.model.MoviePageResult
import me.jbusdriver.modern.domain.model.PageInfo
import me.jbusdriver.modern.ui.RouteLinkMovies
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class LinkMovieListViewModelTest {

    private lateinit var testDispatcher: TestDispatcher

    private val testMovies = listOf(
        Movie("Linked Movie", "http://img.jpg", "ABC-001", "2024-01-01", "http://link1")
    )

    private val testNavKey = RouteLinkMovies("")

    private val stubLocalVideoRepo = object : LocalVideoRepository {
        override fun observeForCode(code: String) = flowOf(emptyList<LocalVideo>())
        override fun observeDownloadedCodes() = flowOf(emptySet<String>())
        override fun observeSummary() = flowOf(LocalVideoSummary())
        override fun hasFolder() = flowOf(false)
        override suspend fun setFolder(uri: android.net.Uri) {}
        override suspend fun clearFolder() {}
        override suspend fun rescan() = 0
        override fun observeAllGroupedByCode() = flowOf(emptyList<LocalVideoGroup>())
        override fun observeShowUncollectedLocal() = flowOf(false)
        override suspend fun setShowUncollectedLocal(value: Boolean) {}
        override suspend fun deleteVideos(ids: List<Int>) = DeleteResult(0, 0)
        override suspend fun snapshotMetadata(code: String, title: String, imageUrl: String, date: String, censorType: String?) {}
    }

    private val stubCollectRepo = object : CollectRepository {
        override suspend fun isCollected(linkItem: LinkItem) = false
        override suspend fun addCollect(linkItem: LinkItem) = true
        override suspend fun removeCollect(linkItem: LinkItem) = true
        override suspend fun isMovieCollected(movie: Movie) = false
        override suspend fun toggleMovieCollect(movie: Movie, categoryId: Int?) = true
        override suspend fun isActressCollected(actress: ActressInfo) = false
        override suspend fun toggleActressCollect(actress: ActressInfo, categoryId: Int?) = true
        override suspend fun getCollectedMovies() = emptyList<Movie>()
        override suspend fun getCollectedActresses() = emptyList<ActressInfo>()
        override suspend fun getCollectedLinkItems(dbType: Int): List<LinkItem> = emptyList()
        override suspend fun exportCollectionsJson() = "{}"
        override suspend fun importCollectionsFromJson(json: String) = 0 to 0
    }

    private fun fullFakeRepo(
        onLoadPageByUrl: (String, Int) -> MoviePageResult
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
            emptyList<GenreGroup>()

        override suspend fun loadPageByUrl(
            url: String,
            page: Int,
            showAll: Boolean,
            forceRefresh: Boolean
        ) =
            onLoadPageByUrl(url, page)

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
    fun setLink_loadsMovies() = runTest(testDispatcher) {
        val repository = fullFakeRepo { _, _ ->
            MoviePageResult(PageInfo(1, 2, listOf(1, 2)), testMovies)
        }
        val viewModel = LinkMovieListViewModel(repository, stubCollectRepo, stubLocalVideoRepo, testNavKey)

        viewModel.setLink("http://example.com/actress/abc")
        advanceUntilIdle()

        assertEquals(1, viewModel.uiState.value.movies.size)
        assertEquals("Linked Movie", viewModel.uiState.value.movies.first().title)
        assertFalse(viewModel.uiState.value.isLoading)
        assertTrue(viewModel.uiState.value.hasMore)
        assertNull(viewModel.uiState.value.error)
    }

    @Test
    fun setLink_handlesError() = runTest(testDispatcher) {
        val repository = fullFakeRepo { _, _ -> throw RuntimeException("Network error") }
        val viewModel = LinkMovieListViewModel(repository, stubCollectRepo, stubLocalVideoRepo, testNavKey)

        viewModel.setLink("http://example.com/actress/abc")
        advanceUntilIdle()

        assertEquals(R.string.load_failed, viewModel.uiState.value.error)
        assertFalse(viewModel.uiState.value.isLoading)
    }

    @Test
    fun refresh_reloadsMovies() = runTest(testDispatcher) {
        var callCount = 0
        val repository = fullFakeRepo { _, _ ->
            callCount++
            MoviePageResult(PageInfo(1, 2, listOf(1, 2)), testMovies)
        }
        val viewModel = LinkMovieListViewModel(repository, stubCollectRepo, stubLocalVideoRepo, testNavKey)

        viewModel.setLink("http://example.com/actress/abc")
        advanceUntilIdle()
        assertEquals(1, callCount)

        viewModel.refresh()
        advanceUntilIdle()
        assertEquals(2, callCount)

        assertFalse(viewModel.uiState.value.isRefreshing)
    }

    @Test
    fun refresh_emptyContentUsesInitialLoadingState() = runTest(testDispatcher) {
        val response = CompletableDeferred<MoviePageResult>()
        var calls = 0
        val repository = object : MovieRepository {
            override fun observePageByUrl(
                url: String,
                page: Int,
                showAll: Boolean,
                forceRefresh: Boolean,
                revalidate: Boolean,
                nowMillis: () -> Long
            ): Flow<CachedLoadEvent<MoviePageResult>> = flow {
                calls += 1
                if (calls == 1) {
                    emit(CachedLoadEvent.Failure(RuntimeException("first load failed"), hadCachedValue = false))
                } else {
                    emit(CachedLoadEvent.Fresh(CacheEntry(response.await(), 1L, CacheSource.Network, false)))
                }
            }

            override suspend fun loadPage(
                type: DataSourceType,
                page: Int,
                showAll: Boolean,
                forceRefresh: Boolean
            ) = MoviePageResult(PageInfo(), emptyList())

            override suspend fun loadActresses(type: DataSourceType, page: Int, forceRefresh: Boolean) =
                emptyList<ActressInfo>() to PageInfo()

            override suspend fun loadGenreCategories(type: DataSourceType, forceRefresh: Boolean) =
                emptyList<GenreGroup>()

            override suspend fun loadPageByUrl(
                url: String,
                page: Int,
                showAll: Boolean,
                forceRefresh: Boolean
            ) = MoviePageResult(PageInfo(), emptyList())

            override suspend fun loadActressDetail(url: String, forceRefresh: Boolean): ActressDetail? =
                null
        }
        val viewModel = LinkMovieListViewModel(repository, stubCollectRepo, stubLocalVideoRepo, testNavKey)
        viewModel.setLink("http://example.com/star/abc")
        advanceUntilIdle()
        assertEquals(R.string.load_failed, viewModel.uiState.value.error)

        viewModel.refresh()
        runCurrent()

        assertTrue(viewModel.uiState.value.isLoading)
        assertFalse(viewModel.uiState.value.isRefreshing)

        response.complete(MoviePageResult(PageInfo(), emptyList()))
        advanceUntilIdle()
    }

    @Test
    fun toggleShowAll_reloadsMoviesWithShowAll() = runTest(testDispatcher) {
        var showAllCapture = false
        val repository = object : MovieRepository {
            override suspend fun loadPage(
                type: DataSourceType,
                page: Int,
                showAll: Boolean,
                forceRefresh: Boolean
            ) =
                MoviePageResult(PageInfo(), emptyList())

            override suspend fun loadActresses(
                type: DataSourceType,
                page: Int,
                forceRefresh: Boolean
            ) =
                emptyList<ActressInfo>() to PageInfo()

            override suspend fun loadGenreCategories(type: DataSourceType, forceRefresh: Boolean) =
                emptyList<GenreGroup>()

            override suspend fun loadPageByUrl(
                url: String,
                page: Int,
                showAll: Boolean,
                forceRefresh: Boolean
            ): MoviePageResult {
                showAllCapture = showAll
                return MoviePageResult(
                    PageInfo(1, 2, listOf(1, 2)),
                    testMovies,
                    MovieFilterInfo(5, 10)
                )
            }

            override suspend fun loadActressDetail(
                url: String,
                forceRefresh: Boolean
            ): ActressDetail? = null
        }
        val viewModel = LinkMovieListViewModel(repository, stubCollectRepo, stubLocalVideoRepo, testNavKey)

        viewModel.setLink("http://example.com/star/abc")
        advanceUntilIdle()
        assertFalse(showAllCapture)

        viewModel.toggleShowAll()
        // Immediately after toggle: isFilterSwitching should be true, movies NOT cleared
        assertTrue(viewModel.uiState.value.isFilterSwitching)
        assertEquals(1, viewModel.uiState.value.movies.size) // old movies still present
        assertTrue(viewModel.uiState.value.showAll)

        advanceUntilIdle()
        assertTrue(showAllCapture)
        assertFalse(viewModel.uiState.value.isFilterSwitching)
        assertEquals(5, viewModel.uiState.value.filterInfo?.magnetCount)
        assertEquals(10, viewModel.uiState.value.filterInfo?.totalCount)
    }

    @Test
    fun setDefaultShowAll_reloadsCurrentLinkWithDefaultMode() = runTest(testDispatcher) {
        val showAllCalls = mutableListOf<Boolean>()
        val repository = object : MovieRepository {
            override fun observePageByUrl(
                url: String,
                page: Int,
                showAll: Boolean,
                forceRefresh: Boolean,
                revalidate: Boolean,
                nowMillis: () -> Long
            ): Flow<CachedLoadEvent<MoviePageResult>> = flow {
                showAllCalls += showAll
                emit(
                    CachedLoadEvent.Fresh(
                        CacheEntry(
                            MoviePageResult(PageInfo(1, 2), testMovies),
                            1L,
                            CacheSource.Network,
                            false
                        )
                    )
                )
            }

            override suspend fun loadPage(
                type: DataSourceType,
                page: Int,
                showAll: Boolean,
                forceRefresh: Boolean
            ) = MoviePageResult(PageInfo(), emptyList())

            override suspend fun loadActresses(type: DataSourceType, page: Int, forceRefresh: Boolean) =
                emptyList<ActressInfo>() to PageInfo()

            override suspend fun loadGenreCategories(type: DataSourceType, forceRefresh: Boolean) =
                emptyList<GenreGroup>()

            override suspend fun loadPageByUrl(
                url: String,
                page: Int,
                showAll: Boolean,
                forceRefresh: Boolean
            ) = MoviePageResult(PageInfo(), emptyList())

            override suspend fun loadActressDetail(url: String, forceRefresh: Boolean): ActressDetail? =
                null
        }
        val viewModel = LinkMovieListViewModel(repository, stubCollectRepo, stubLocalVideoRepo, testNavKey)

        // 先以默认筛选（showAll=false）加载链接，再变更默认值，验证 setDefaultShowAll
        // 会以新的默认值原地重载当前链接（同一链接、仅默认值变化的情况）。
        viewModel.setLink("http://example.com/star/abc")
        advanceUntilIdle()
        showAllCalls.clear()

        viewModel.setDefaultShowAll(true)
        advanceUntilIdle()

        assertEquals(listOf(true), showAllCalls)
        assertTrue(viewModel.uiState.value.showAll)
    }

    @Test
    fun staleRefreshResultDoesNotOverwriteShowAllSwitch() = runTest(testDispatcher) {
        val oldRefresh = CompletableDeferred<MoviePageResult>()
        val repository = object : MovieRepository {
            override suspend fun loadPage(
                type: DataSourceType,
                page: Int,
                showAll: Boolean,
                forceRefresh: Boolean
            ) =
                MoviePageResult(PageInfo(), emptyList())

            override suspend fun loadActresses(
                type: DataSourceType,
                page: Int,
                forceRefresh: Boolean
            ) =
                emptyList<ActressInfo>() to PageInfo()

            override suspend fun loadGenreCategories(type: DataSourceType, forceRefresh: Boolean) =
                emptyList<GenreGroup>()

            override suspend fun loadPageByUrl(
                url: String,
                page: Int,
                showAll: Boolean,
                forceRefresh: Boolean
            ): MoviePageResult {
                return when {
                    forceRefresh -> oldRefresh.await()
                    showAll -> MoviePageResult(
                        PageInfo(1, 2, listOf(1, 2)),
                        listOf(Movie("Show All", "http://img.jpg", "ALL-001", "2024-01-02", "http://all"))
                    )
                    else -> MoviePageResult(
                        PageInfo(1, 2, listOf(1, 2)),
                        listOf(Movie("Initial", "http://img.jpg", "INI-001", "2024-01-01", "http://initial"))
                    )
                }
            }

            override suspend fun loadActressDetail(
                url: String,
                forceRefresh: Boolean
            ): ActressDetail? = null
        }
        val viewModel = LinkMovieListViewModel(repository, stubCollectRepo, stubLocalVideoRepo, testNavKey)

        viewModel.setLink("http://example.com/star/abc")
        advanceUntilIdle()
        assertEquals("Initial", viewModel.uiState.value.movies.single().title)

        viewModel.refresh()
        runCurrent()
        assertTrue(viewModel.uiState.value.isRefreshing)

        viewModel.toggleShowAll()
        advanceUntilIdle()
        assertEquals("Show All", viewModel.uiState.value.movies.single().title)

        oldRefresh.complete(
            MoviePageResult(
                PageInfo(1, 2, listOf(1, 2)),
                listOf(Movie("Stale Refresh", "http://img.jpg", "OLD-001", "2024-01-03", "http://old"))
            )
        )
        advanceUntilIdle()

        assertEquals("Show All", viewModel.uiState.value.movies.single().title)
        assertFalse(viewModel.uiState.value.isRefreshing)
    }

    @Test
    fun staleLoadMoreResultDoesNotAppendAfterShowAllSwitch() = runTest(testDispatcher) {
        val oldLoadMore = CompletableDeferred<MoviePageResult>()
        val repository = object : MovieRepository {
            override suspend fun loadPage(
                type: DataSourceType,
                page: Int,
                showAll: Boolean,
                forceRefresh: Boolean
            ) =
                MoviePageResult(PageInfo(), emptyList())

            override suspend fun loadActresses(
                type: DataSourceType,
                page: Int,
                forceRefresh: Boolean
            ) =
                emptyList<ActressInfo>() to PageInfo()

            override suspend fun loadGenreCategories(type: DataSourceType, forceRefresh: Boolean) =
                emptyList<GenreGroup>()

            override suspend fun loadPageByUrl(
                url: String,
                page: Int,
                showAll: Boolean,
                forceRefresh: Boolean
            ): MoviePageResult {
                return when {
                    page == 2 -> oldLoadMore.await()
                    showAll -> MoviePageResult(
                        PageInfo(1, 2, listOf(1, 2)),
                        listOf(Movie("Show All", "http://img.jpg", "ALL-001", "2024-01-02", "http://all"))
                    )
                    else -> MoviePageResult(
                        PageInfo(1, 2, listOf(1, 2)),
                        listOf(Movie("Initial", "http://img.jpg", "INI-001", "2024-01-01", "http://initial"))
                    )
                }
            }

            override suspend fun loadActressDetail(
                url: String,
                forceRefresh: Boolean
            ): ActressDetail? = null
        }
        val viewModel = LinkMovieListViewModel(repository, stubCollectRepo, stubLocalVideoRepo, testNavKey)

        viewModel.setLink("http://example.com/star/abc")
        advanceUntilIdle()
        assertEquals("Initial", viewModel.uiState.value.movies.single().title)

        viewModel.loadMore()
        runCurrent()
        assertTrue(viewModel.uiState.value.isLoadingMore)

        viewModel.toggleShowAll()
        advanceUntilIdle()
        assertEquals(listOf("Show All"), viewModel.uiState.value.movies.map { it.title })

        oldLoadMore.complete(
            MoviePageResult(
                PageInfo(2, 3, listOf(1, 2, 3)),
                listOf(Movie("Stale Page 2", "http://img.jpg", "OLD-002", "2024-01-03", "http://old2"))
            )
        )
        advanceUntilIdle()

        assertEquals(listOf("Show All"), viewModel.uiState.value.movies.map { it.title })
        assertFalse(viewModel.uiState.value.isLoadingMore)
    }

    @Test
    fun staleRevalidateResultDoesNotOverwriteShowAllSwitch() = runTest(testDispatcher) {
        val oldRevalidate = CompletableDeferred<MoviePageResult>()
        var defaultLoadCount = 0
        val repository = object : MovieRepository {
            override suspend fun loadPage(
                type: DataSourceType,
                page: Int,
                showAll: Boolean,
                forceRefresh: Boolean
            ) =
                MoviePageResult(PageInfo(), emptyList())

            override suspend fun loadActresses(
                type: DataSourceType,
                page: Int,
                forceRefresh: Boolean
            ) =
                emptyList<ActressInfo>() to PageInfo()

            override suspend fun loadGenreCategories(type: DataSourceType, forceRefresh: Boolean) =
                emptyList<GenreGroup>()

            override suspend fun loadPageByUrl(
                url: String,
                page: Int,
                showAll: Boolean,
                forceRefresh: Boolean
            ): MoviePageResult {
                return when {
                    showAll -> MoviePageResult(
                        PageInfo(1, 2, listOf(1, 2)),
                        listOf(Movie("Show All", "http://img.jpg", "ALL-001", "2024-01-02", "http://all"))
                    )
                    defaultLoadCount++ == 0 -> MoviePageResult(
                        PageInfo(1, 2, listOf(1, 2)),
                        listOf(Movie("Initial", "http://img.jpg", "INI-001", "2024-01-01", "http://initial"))
                    )
                    else -> oldRevalidate.await()
                }
            }

            override suspend fun loadActressDetail(
                url: String,
                forceRefresh: Boolean
            ): ActressDetail? = null
        }
        val viewModel = LinkMovieListViewModel(repository, stubCollectRepo, stubLocalVideoRepo, testNavKey)

        viewModel.setLink("http://example.com/star/abc")
        advanceUntilIdle()
        assertEquals("Initial", viewModel.uiState.value.movies.single().title)

        viewModel.revalidate()
        runCurrent()
        assertTrue(viewModel.uiState.value.isRevalidating)

        viewModel.toggleShowAll()
        advanceUntilIdle()
        assertEquals("Show All", viewModel.uiState.value.movies.single().title)

        oldRevalidate.complete(
            MoviePageResult(
                PageInfo(1, 2, listOf(1, 2)),
                listOf(Movie("Stale Revalidate", "http://img.jpg", "OLD-003", "2024-01-03", "http://old3"))
            )
        )
        advanceUntilIdle()

        assertEquals("Show All", viewModel.uiState.value.movies.single().title)
        assertFalse(viewModel.uiState.value.isRevalidating)
        assertNull(viewModel.uiState.value.pendingFreshResult)
    }

    @Test
    fun toggleActressCollect_uncensoredLink_passesUncensoredCategoryId() = runTest(testDispatcher) {
        val recordingRepo = RecordingCollectRepository()
        val repository = repoWithActressDetail()
        val viewModel =
            LinkMovieListViewModel(repository, recordingRepo, stubLocalVideoRepo, testNavKey)

        viewModel.setLink("https://example.test/uncensored/star/alice", type = "actress")
        advanceUntilIdle()
        // detail 必须已加载，否则 toggleActressCollect 直接 return
        assertTrue(viewModel.uiState.value.actressHeader.detail != null)

        viewModel.toggleActressCollect()
        advanceUntilIdle()

        assertEquals(UncensoredActressCategory.id, recordingRepo.lastActressCategoryId)
    }

    @Test
    fun toggleActressCollect_censoredLink_passesDefaultCategoryId() = runTest(testDispatcher) {
        val recordingRepo = RecordingCollectRepository()
        val repository = repoWithActressDetail()
        val viewModel =
            LinkMovieListViewModel(repository, recordingRepo, stubLocalVideoRepo, testNavKey)

        viewModel.setLink("https://example.test/star/alice", type = "actress")
        advanceUntilIdle()
        assertTrue(viewModel.uiState.value.actressHeader.detail != null)

        viewModel.toggleActressCollect()
        advanceUntilIdle()

        assertEquals(ActressCategory.id, recordingRepo.lastActressCategoryId)
    }

    /** 返回一个 loadActressDetail 返回非空 ActressDetail 的 MovieRepository，用于让 actressHeader.detail 就绪。 */
    private fun repoWithActressDetail(): MovieRepository = object : MovieRepository {
        override suspend fun loadPage(
            type: DataSourceType,
            page: Int,
            showAll: Boolean,
            forceRefresh: Boolean
        ) = MoviePageResult(PageInfo(), emptyList())

        override suspend fun loadActresses(
            type: DataSourceType,
            page: Int,
            forceRefresh: Boolean
        ) = emptyList<ActressInfo>() to PageInfo()

        override suspend fun loadGenreCategories(type: DataSourceType, forceRefresh: Boolean) =
            emptyList<GenreGroup>()

        override suspend fun loadPageByUrl(
            url: String,
            page: Int,
            showAll: Boolean,
            forceRefresh: Boolean
        ) = MoviePageResult(PageInfo(1, 1, emptyList()), testMovies)

        override suspend fun loadActressDetail(
            url: String,
            forceRefresh: Boolean
        ): ActressDetail? = ActressDetail(
            name = "Alice",
            avatar = "http://img.jpg/avatar",
            info = emptyList()
        )
    }
}

/** 记录最后一次 toggleActressCollect 调用的 categoryId，用于断言 URL 判定逻辑。 */
private class RecordingCollectRepository : CollectRepository {
    var lastActressCategoryId: Int? = null

    override suspend fun isCollected(linkItem: LinkItem) = false
    override suspend fun addCollect(linkItem: LinkItem) = true
    override suspend fun removeCollect(linkItem: LinkItem) = true
    override suspend fun isMovieCollected(movie: Movie) = false
    override suspend fun toggleMovieCollect(movie: Movie, categoryId: Int?) = true
    override suspend fun isActressCollected(actress: ActressInfo) = false
    override suspend fun toggleActressCollect(actress: ActressInfo, categoryId: Int?): Boolean {
        lastActressCategoryId = categoryId
        return true
    }
    override suspend fun getCollectedMovies() = emptyList<Movie>()
    override suspend fun getCollectedActresses() = emptyList<ActressInfo>()
    override suspend fun getCollectedLinkItems(dbType: Int) = emptyList<LinkItem>()
    override suspend fun exportCollectionsJson() = "{}"
    override suspend fun importCollectionsFromJson(json: String) = 0 to 0
}

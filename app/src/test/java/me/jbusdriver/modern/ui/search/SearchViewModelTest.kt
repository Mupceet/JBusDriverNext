package me.jbusdriver.modern.ui.search

import androidx.lifecycle.SavedStateHandle
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.setMain
import me.jbusdriver.R
import me.jbusdriver.modern.core.site.SiteConfig
import me.jbusdriver.modern.data.db.MovieDBType
import me.jbusdriver.modern.data.db.convertDBItem
import me.jbusdriver.modern.data.repository.CollectRepository
import me.jbusdriver.modern.data.repository.LocalVideoRepository
import me.jbusdriver.modern.data.repository.SearchRepository
import me.jbusdriver.modern.data.settings.SearchHistoryStore
import me.jbusdriver.modern.domain.model.ActressInfo
import me.jbusdriver.modern.domain.model.DeleteResult
import me.jbusdriver.modern.domain.model.LocalVideo
import me.jbusdriver.modern.domain.model.LocalVideoGroup
import me.jbusdriver.modern.domain.model.LocalVideoSummary
import me.jbusdriver.modern.domain.model.Movie
import me.jbusdriver.modern.domain.model.MoviePageResult
import me.jbusdriver.modern.domain.model.PageInfo
import me.jbusdriver.modern.domain.model.SearchType
import me.jbusdriver.modern.test.StubCollectRepository
import me.jbusdriver.modern.ui.MovieUiModel
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class SearchViewModelTest {

    private lateinit var testDispatcher: TestDispatcher

    private val testMovies = listOf(
        Movie("Result 1", "http://img1.jpg", "ABC-001", "2024-01-01", "http://link1")
    )

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

    private fun fakeHistoryStore() = object : SearchHistoryStore {
        private val history = mutableListOf<String>()
        override suspend fun getHistory(): List<String> = history.toList()
        override suspend fun addQuery(query: String) {
            history.remove(query)
            history.add(0, query)
        }

        override suspend fun removeQuery(query: String) {
            history.remove(query)
        }

        override suspend fun clearHistory() {
            history.clear()
        }
    }

    private fun fakeSiteConfig() = object : SiteConfig {
        override var baseUrl: String = "https://example.test"
        override fun resolve(pathOrUrl: String): String = pathOrUrl
    }

    private fun stubSearchRepository() = object : SearchRepository {
        override suspend fun searchMovies(
            type: SearchType, query: String, page: Int, forceRefresh: Boolean
        ) = MoviePageResult(PageInfo(), emptyList())

        override suspend fun searchActresses(
            query: String, page: Int
        ): Pair<PageInfo, List<ActressInfo>> = PageInfo() to emptyList()
    }

    private fun makeViewModel(
        repository: SearchRepository,
        collectRepository: CollectRepository = StubCollectRepository()
    ) = SearchViewModel(
        repository, fakeHistoryStore(), stubLocalVideoRepo, collectRepository, fakeSiteConfig(),
        // 注入测试调度器作为 IO 线程：使 collectedMovies 的 flowOn 跑在 TestScheduler 上，
        // runTest 才能确定性地驱动（真实 Dispatchers.IO 不受 TestScheduler 控制）。
        testDispatcher,
        SavedStateHandle()
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
    fun search_loadsResults() = runTest(testDispatcher) {
        val repository = object : SearchRepository {
            override suspend fun searchMovies(
                type: SearchType,
                query: String,
                page: Int,
                forceRefresh: Boolean
            ) =
                MoviePageResult(PageInfo(1, 2, listOf(1, 2)), testMovies)

            override suspend fun searchActresses(
                query: String,
                page: Int
            ): Pair<PageInfo, List<ActressInfo>> =
                PageInfo() to emptyList()
        }
        val viewModel = makeViewModel(repository)

        viewModel.search("test")
        advanceUntilIdle()

        assertEquals(1, viewModel.uiState.value.results.size)
        assertEquals("Result 1", viewModel.uiState.value.results.first().title)
        assertFalse(viewModel.uiState.value.isLoading)
        assertTrue(viewModel.uiState.value.hasMore)
        assertNull(viewModel.uiState.value.error)
    }

    @Test
    fun search_handlesError() = runTest(testDispatcher) {
        val repository = object : SearchRepository {
            override suspend fun searchMovies(
                type: SearchType,
                query: String,
                page: Int,
                forceRefresh: Boolean
            ) =
                throw RuntimeException("Search failed")

            override suspend fun searchActresses(
                query: String,
                page: Int
            ): Pair<PageInfo, List<ActressInfo>> =
                PageInfo() to emptyList()
        }
        val viewModel = makeViewModel(repository)

        viewModel.search("test")
        advanceUntilIdle()

        assertEquals(R.string.search_failed, viewModel.uiState.value.error)
        assertFalse(viewModel.uiState.value.isLoading)
    }

    @Test
    fun search_emptyQuery_doesNotLoad() = runTest(testDispatcher) {
        val repository = object : SearchRepository {
            override suspend fun searchMovies(
                type: SearchType,
                query: String,
                page: Int,
                forceRefresh: Boolean
            ) =
                error("Should not be called")

            override suspend fun searchActresses(query: String, page: Int) =
                error("Should not be called")
        }
        val viewModel = makeViewModel(repository)

        viewModel.search("")
        advanceUntilIdle()

        assertTrue(viewModel.uiState.value.results.isEmpty())
        assertFalse(viewModel.uiState.value.isLoading)
    }

    @Test
    fun actressSearch_loadsActressResultsAndClearsMovieResults() = runTest(testDispatcher) {
        val repository = object : SearchRepository {
            override suspend fun searchMovies(
                type: SearchType,
                query: String,
                page: Int,
                forceRefresh: Boolean
            ) = MoviePageResult(PageInfo(), testMovies)

            override suspend fun searchActresses(
                query: String,
                page: Int
            ): Pair<PageInfo, List<ActressInfo>> =
                PageInfo(1, 1) to listOf(ActressInfo("Alice", "http://avatar.jpg", "http://alice"))
        }
        val viewModel = makeViewModel(repository)

        viewModel.search("alice", SearchType.ACTRESS)
        advanceUntilIdle()

        assertTrue(viewModel.uiState.value.results.isEmpty())
        assertEquals("Alice", viewModel.uiState.value.actressResults.single().name)
        assertFalse(viewModel.uiState.value.hasMore)
    }

    @Test
    fun loadMore_appendsNextPageAndThenStopsWhenNoMore() = runTest(testDispatcher) {
        val repository = object : SearchRepository {
            override suspend fun searchMovies(
                type: SearchType,
                query: String,
                page: Int,
                forceRefresh: Boolean
            ): MoviePageResult {
                val movie = Movie("Page $page", "http://img$page.jpg", "ABC-00$page", "2024-01-0$page", "http://link$page")
                return MoviePageResult(PageInfo(page, if (page == 1) 2 else 2), listOf(movie))
            }

            override suspend fun searchActresses(query: String, page: Int) =
                PageInfo() to emptyList<ActressInfo>()
        }
        val viewModel = makeViewModel(repository)

        viewModel.search("abc")
        advanceUntilIdle()
        viewModel.loadMore()
        advanceUntilIdle()
        viewModel.loadMore()
        advanceUntilIdle()

        assertEquals(listOf("Page 1", "Page 2"), viewModel.uiState.value.results.map { it.title })
        assertFalse(viewModel.uiState.value.hasMore)
        assertFalse(viewModel.uiState.value.isLoadingMore)
    }

    @Test
    fun loadMoreErrorKeepsExistingResultsAndReportsError() = runTest(testDispatcher) {
        val repository = object : SearchRepository {
            override suspend fun searchMovies(
                type: SearchType,
                query: String,
                page: Int,
                forceRefresh: Boolean
            ): MoviePageResult {
                if (page > 1) error("next page failed")
                return MoviePageResult(PageInfo(1, 2), testMovies)
            }

            override suspend fun searchActresses(query: String, page: Int) =
                PageInfo() to emptyList<ActressInfo>()
        }
        val viewModel = makeViewModel(repository)

        viewModel.search("abc")
        advanceUntilIdle()
        viewModel.loadMore()
        advanceUntilIdle()

        assertEquals(listOf("Result 1"), viewModel.uiState.value.results.map { it.title })
        assertEquals(R.string.search_failed, viewModel.uiState.value.error)
        assertFalse(viewModel.uiState.value.isLoadingMore)
    }

    @Test
    fun staleRefreshResultDoesNotOverwriteNewSearch() = runTest(testDispatcher) {
        val oldRefresh = CompletableDeferred<MoviePageResult>()
        val oldMovie = Movie("Old", "http://img-old.jpg", "OLD-001", "2024-01-01", "http://old")
        val newMovie = Movie("New", "http://img-new.jpg", "NEW-001", "2024-01-02", "http://new")
        val staleMovie = Movie("Stale", "http://img-stale.jpg", "OLD-002", "2024-01-03", "http://stale")
        val repository = object : SearchRepository {
            override suspend fun searchMovies(
                type: SearchType,
                query: String,
                page: Int,
                forceRefresh: Boolean
            ): MoviePageResult {
                if (query == "old" && forceRefresh) return oldRefresh.await()
                val movie = if (query == "new") newMovie else oldMovie
                return MoviePageResult(PageInfo(1, 1, listOf(1)), listOf(movie))
            }

            override suspend fun searchActresses(
                query: String,
                page: Int
            ): Pair<PageInfo, List<ActressInfo>> =
                PageInfo() to emptyList()
        }
        val viewModel = makeViewModel(repository)

        viewModel.search("old")
        advanceUntilIdle()
        viewModel.refresh()
        runCurrent()
        viewModel.search("new")
        advanceUntilIdle()

        oldRefresh.complete(MoviePageResult(PageInfo(1, 1, listOf(1)), listOf(staleMovie)))
        advanceUntilIdle()

        assertEquals("new", viewModel.uiState.value.query)
        assertEquals(listOf("New"), viewModel.uiState.value.results.map { it.title })
        assertFalse(viewModel.uiState.value.isRefreshing)
    }

    @Test
    fun localResults_matchByCodeAndFilterByCensorChip() = runTest(testDispatcher) {
        val censored = Movie("Cen", "http://c.jpg", "ABC-001", "2024-01-01", "http://lc")
            .convertDBItem(categoryId = 1)
        val uncensored = Movie("Un", "http://u.jpg", "ABC-002", "2024-01-02", "http://lu")
            .convertDBItem(categoryId = 3)
        val collectRepo = object : StubCollectRepository() {
            override fun observeCollectedLinkItems(dbType: Int) =
                flowOf(listOf(censored, uncensored))
        }
        val viewModel = makeViewModel(stubSearchRepository(), collectRepo)

        val collected = mutableListOf<List<MovieUiModel>>()
        val job = launch { viewModel.localResults.collect { collected += it } }
        runCurrent()

        viewModel.onSearchInputChanged("abc")
        advanceUntilIdle()
        // default chip = CENSORED -> only the censored one
        assertEquals(listOf("ABC-001"), collected.last().map { it.code })

        viewModel.setSearchType(SearchType.UNCENSORED)
        advanceUntilIdle()
        assertEquals(listOf("ABC-002"), collected.last().map { it.code })

        viewModel.setSearchType(SearchType.ACTRESS)
        advanceUntilIdle()
        assertTrue(collected.last().isEmpty())

        job.cancel()
    }

    @Test
    fun localResults_normalizeQueryMatchTitleAndSortByCreateTimeDesc() = runTest(testDispatcher) {
        val older = Movie("Old Title", "http://o.jpg", "ABC-001", "2024-01-01", "http://lo")
            .convertDBItem(categoryId = 1).copy(createTime = 1000L)
        val newer = Movie("New Title", "http://n.jpg", "ABC-002", "2024-01-02", "http://ln")
            .convertDBItem(categoryId = 1).copy(createTime = 2000L)
        val collectRepo = object : StubCollectRepository() {
            override fun observeCollectedLinkItems(dbType: Int) = flowOf(listOf(older, newer))
        }
        val viewModel = makeViewModel(stubSearchRepository(), collectRepo)

        val collected = mutableListOf<List<MovieUiModel>>()
        val job = launch { viewModel.localResults.collect { collected += it } }
        runCurrent()

        // separator-insensitive code match
        viewModel.onSearchInputChanged("ABC_002")
        advanceUntilIdle()
        assertEquals(listOf("ABC-002"), collected.last().map { it.code })

        // title substring matches both; sorted newest-collected first
        viewModel.onSearchInputChanged("title")
        advanceUntilIdle()
        assertEquals(listOf("ABC-002", "ABC-001"), collected.last().map { it.code })

        job.cancel()
    }
}

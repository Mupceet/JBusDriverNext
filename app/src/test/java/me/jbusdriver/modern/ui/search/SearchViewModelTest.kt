package me.jbusdriver.modern.ui.search

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.setMain
import me.jbusdriver.R
import me.jbusdriver.modern.data.settings.SearchHistoryStore
import me.jbusdriver.modern.data.repository.LocalVideoRepository
import me.jbusdriver.modern.data.repository.SearchRepository
import me.jbusdriver.modern.domain.model.ActressInfo
import me.jbusdriver.modern.domain.model.DeleteResult
import me.jbusdriver.modern.domain.model.LocalVideo
import me.jbusdriver.modern.domain.model.LocalVideoGroup
import me.jbusdriver.modern.domain.model.LocalVideoSummary
import me.jbusdriver.modern.domain.model.Movie
import me.jbusdriver.modern.domain.model.MoviePageResult
import me.jbusdriver.modern.domain.model.PageInfo
import me.jbusdriver.modern.domain.model.SearchType
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
        val viewModel = SearchViewModel(repository, fakeHistoryStore(), stubLocalVideoRepo)

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
        val viewModel = SearchViewModel(repository, fakeHistoryStore(), stubLocalVideoRepo)

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
        val viewModel = SearchViewModel(repository, fakeHistoryStore(), stubLocalVideoRepo)

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
        val viewModel = SearchViewModel(repository, fakeHistoryStore(), stubLocalVideoRepo)

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
        val viewModel = SearchViewModel(repository, fakeHistoryStore(), stubLocalVideoRepo)

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
        val viewModel = SearchViewModel(repository, fakeHistoryStore(), stubLocalVideoRepo)

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
        val viewModel = SearchViewModel(repository, fakeHistoryStore(), stubLocalVideoRepo)

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
}

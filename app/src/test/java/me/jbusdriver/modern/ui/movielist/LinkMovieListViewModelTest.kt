package me.jbusdriver.modern.ui.movielist

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import me.jbusdriver.R
import me.jbusdriver.modern.data.CollectRepository
import me.jbusdriver.modern.data.MovieRepository
import me.jbusdriver.modern.data.db.entity.LinkItem
import me.jbusdriver.modern.domain.model.ActressDetail
import me.jbusdriver.modern.domain.model.ActressInfo
import me.jbusdriver.modern.domain.model.DataSourceType
import me.jbusdriver.modern.domain.model.GenreGroup
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

    private val stubCollectRepo = object : CollectRepository {
        override suspend fun isCollected(linkItem: LinkItem) = false
        override suspend fun addCollect(linkItem: LinkItem) = true
        override suspend fun removeCollect(linkItem: LinkItem) = true
        override suspend fun isMovieCollected(movie: Movie) = false
        override suspend fun toggleMovieCollect(movie: Movie) = true
        override suspend fun isActressCollected(actress: ActressInfo) = false
        override suspend fun toggleActressCollect(actress: ActressInfo) = true
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
        val viewModel = LinkMovieListViewModel(repository, stubCollectRepo, testNavKey)

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
        val viewModel = LinkMovieListViewModel(repository, stubCollectRepo, testNavKey)

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
        val viewModel = LinkMovieListViewModel(repository, stubCollectRepo, testNavKey)

        viewModel.setLink("http://example.com/actress/abc")
        advanceUntilIdle()
        assertEquals(1, callCount)

        viewModel.refresh()
        advanceUntilIdle()
        assertEquals(2, callCount)

        assertFalse(viewModel.uiState.value.isRefreshing)
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
        val viewModel = LinkMovieListViewModel(repository, stubCollectRepo, testNavKey)

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
        val viewModel = LinkMovieListViewModel(repository, stubCollectRepo, testNavKey)

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
        val viewModel = LinkMovieListViewModel(repository, stubCollectRepo, testNavKey)

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
        val viewModel = LinkMovieListViewModel(repository, stubCollectRepo, testNavKey)

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
}

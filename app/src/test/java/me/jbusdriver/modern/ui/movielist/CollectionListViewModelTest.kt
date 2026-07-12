package me.jbusdriver.modern.ui.movielist

import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import me.jbusdriver.R
import me.jbusdriver.modern.core.site.SiteConfig
import me.jbusdriver.modern.core.toJsonString
import me.jbusdriver.modern.data.repository.CollectRepository
import me.jbusdriver.modern.data.repository.LocalVideoRepository
import me.jbusdriver.modern.data.db.ActressDBType
import me.jbusdriver.modern.data.db.MovieDBType
import me.jbusdriver.modern.data.db.entity.LinkItem
import me.jbusdriver.modern.data.db.toILink
import me.jbusdriver.modern.domain.model.ActressInfo
import me.jbusdriver.modern.domain.model.DeleteResult
import me.jbusdriver.modern.domain.model.LocalVideo
import me.jbusdriver.modern.domain.model.LocalVideoGroup
import me.jbusdriver.modern.domain.model.LocalVideoSummary
import me.jbusdriver.modern.domain.model.Movie
import me.jbusdriver.modern.test.FakeCollectionUiPrefs
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class CollectionListViewModelTest {

    private lateinit var testDispatcher: TestDispatcher
    private lateinit var viewModel: CollectionListViewModel

    private val testMovies = listOf(
        Movie("Collected Movie", "http://img.jpg", "ABC-001", "2024-01-01", "http://link1")
    )

    private val testActresses = listOf(
        ActressInfo("Alice", "http://avatar.jpg", "http://link1")
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

    private fun Movie.toLinkItem(createTime: Long = 1_000L) = LinkItem(
        dbType = MovieDBType,
        key = link,
        jsonStr = toJsonString(),
        createTime = createTime,
        categoryId = 1
    )

    private fun ActressInfo.toLinkItem(createTime: Long = 1_000L, categoryId: Int = 2) = LinkItem(
        dbType = ActressDBType,
        key = link,
        jsonStr = toJsonString(),
        createTime = createTime,
        categoryId = categoryId
    )

    @Before
    fun setup() {
        testDispatcher = StandardTestDispatcher()
        Dispatchers.setMain(testDispatcher)
    }

    @After
    fun tearDown() {
        // Cancel any viewModelScope coroutine still running on Dispatchers.IO so it doesn't
        // access Dispatchers.Main after resetMain() (which surfaces as UncaughtExceptionsBeforeTest).
        if (this::viewModel.isInitialized) viewModel.viewModelScope.cancel()
        Dispatchers.resetMain()
    }

    @Test
    fun loadCollection_movieType_loadsMovies() = runTest(testDispatcher) {
        val collectRepo = object : CollectRepository {
            override suspend fun isCollected(linkItem: LinkItem) = false
            override suspend fun addCollect(linkItem: LinkItem) = true
            override suspend fun removeCollect(linkItem: LinkItem) = true
            override suspend fun isMovieCollected(movie: Movie) = false
            override suspend fun toggleMovieCollect(movie: Movie, categoryId: Int?) = true
            override suspend fun isActressCollected(actress: ActressInfo) = false
            override suspend fun toggleActressCollect(actress: ActressInfo, categoryId: Int?) = true
            override suspend fun getCollectedMovies() = testMovies
            override suspend fun getCollectedActresses() = testActresses
            override suspend fun getCollectedLinkItems(dbType: Int): List<LinkItem> =
                when (dbType) {
                    MovieDBType -> testMovies.map { it.toLinkItem() }
                    ActressDBType -> testActresses.map { it.toLinkItem() }
                    else -> emptyList()
                }

            override suspend fun exportCollectionsJson() = "{}"
            override suspend fun importCollectionsFromJson(json: String) = 0 to 0
        }
        viewModel = CollectionListViewModel(collectRepo, FakeCollectionUiPrefs(), FakeSiteConfig(), stubLocalVideoRepo)

        assertTrue(testMovies.first().toLinkItem().toILink(FakeSiteConfig().baseUrl) is Movie)
        viewModel.loadCollection(1) // MovieDBType
        advanceUntilIdle()
        Thread.sleep(500)
        advanceUntilIdle()
        val state = viewModel.uiState.value

        assertEquals(1, state.movies.size)
        assertEquals("Collected Movie", state.movies.first().title)
        assertFalse(state.isLoading)
        assertNull(state.error)
    }

    @Test
    fun loadCollection_actressType_loadsActresses() = runTest(testDispatcher) {
        val collectRepo = object : CollectRepository {
            override suspend fun isCollected(linkItem: LinkItem) = false
            override suspend fun addCollect(linkItem: LinkItem) = true
            override suspend fun removeCollect(linkItem: LinkItem) = true
            override suspend fun isMovieCollected(movie: Movie) = false
            override suspend fun toggleMovieCollect(movie: Movie, categoryId: Int?) = true
            override suspend fun isActressCollected(actress: ActressInfo) = false
            override suspend fun toggleActressCollect(actress: ActressInfo, categoryId: Int?) = true
            override suspend fun getCollectedMovies() = testMovies
            override suspend fun getCollectedActresses() = testActresses
            override suspend fun getCollectedLinkItems(dbType: Int): List<LinkItem> =
                when (dbType) {
                    MovieDBType -> testMovies.map { it.toLinkItem() }
                    ActressDBType -> testActresses.map { it.toLinkItem() }
                    else -> emptyList()
                }

            override suspend fun exportCollectionsJson() = "{}"
            override suspend fun importCollectionsFromJson(json: String) = 0 to 0
        }
        viewModel = CollectionListViewModel(collectRepo, FakeCollectionUiPrefs(), FakeSiteConfig(), stubLocalVideoRepo)

        assertTrue(testActresses.first().toLinkItem().toILink(FakeSiteConfig().baseUrl) is ActressInfo)
        viewModel.loadCollection(2) // ActressDBType
        advanceUntilIdle()
        Thread.sleep(500)
        advanceUntilIdle()
        val state = viewModel.uiState.value

        assertEquals(1, state.actresses.size)
        assertEquals("Alice", state.actresses.first().name)
        assertFalse(state.isLoading)
    }

    @Test
    fun updateFilter_uncensored_keepsOnlyUncensoredActresses() = runTest(testDispatcher) {
        // Two actresses: censored (categoryId=2) and uncensored (categoryId=4)
        val actresses = listOf(
            ActressInfo("CensoredA", "http://avatar.jpg", "http://link1"),
            ActressInfo("UncensoredB", "http://avatar.jpg", "http://link2")
        )
        val collectRepo = object : CollectRepository {
            override suspend fun isCollected(linkItem: LinkItem) = false
            override suspend fun addCollect(linkItem: LinkItem) = true
            override suspend fun removeCollect(linkItem: LinkItem) = true
            override suspend fun isMovieCollected(movie: Movie) = false
            override suspend fun toggleMovieCollect(movie: Movie, categoryId: Int?) = true
            override suspend fun isActressCollected(actress: ActressInfo) = false
            override suspend fun toggleActressCollect(actress: ActressInfo, categoryId: Int?) = true
            override suspend fun getCollectedMovies() = emptyList<Movie>()
            override suspend fun getCollectedActresses() = actresses
            override suspend fun getCollectedLinkItems(dbType: Int): List<LinkItem> =
                when (dbType) {
                    MovieDBType -> emptyList()
                    ActressDBType -> actresses.mapIndexed { i, a ->
                        a.toLinkItem(categoryId = if (i == 0) 2 else 4)
                    }
                    else -> emptyList()
                }

            override suspend fun exportCollectionsJson() = "{}"
            override suspend fun importCollectionsFromJson(json: String) = 0 to 0
        }
        viewModel = CollectionListViewModel(collectRepo, FakeCollectionUiPrefs(), FakeSiteConfig(), stubLocalVideoRepo)

        viewModel.loadCollection(2) // ActressDBType
        advanceUntilIdle()
        Thread.sleep(500)
        advanceUntilIdle()

        // Default filter is ALL: both actresses present
        assertEquals(2, viewModel.uiState.value.actresses.size)

        viewModel.updateFilter(CollectionFilterState(censorFilter = CensorFilter.UNCENSORED))
        advanceUntilIdle()

        val filtered = viewModel.uiState.value.actresses
        assertEquals(1, filtered.size)
        assertEquals("UncensoredB", filtered.first().name)
    }

    @Test
    fun loadCollection_collectTime_filtersByCollectMonth() = runTest(testDispatcher) {
        val mayMillis = mktime(2026, 5, 10)
        val juneMillis = mktime(2026, 6, 1)
        val movies = listOf(
            Movie("May Movie", "http://img.jpg", "ABC-001", "2026-05-10", "http://link1"),
            Movie("June Movie", "http://img.jpg", "ABC-002", "2026-06-01", "http://link2")
        )
        val collectRepo = object : CollectRepository {
            override suspend fun isCollected(linkItem: LinkItem) = false
            override suspend fun addCollect(linkItem: LinkItem) = true
            override suspend fun removeCollect(linkItem: LinkItem) = true
            override suspend fun isMovieCollected(movie: Movie) = false
            override suspend fun toggleMovieCollect(movie: Movie, categoryId: Int?) = true
            override suspend fun isActressCollected(actress: ActressInfo) = false
            override suspend fun toggleActressCollect(actress: ActressInfo, categoryId: Int?) = true
            override suspend fun getCollectedMovies() = movies
            override suspend fun getCollectedActresses() = emptyList<ActressInfo>()
            override suspend fun getCollectedLinkItems(dbType: Int): List<LinkItem> =
                if (dbType == MovieDBType) listOf(
                    movies[0].toLinkItem(createTime = mayMillis),
                    movies[1].toLinkItem(createTime = juneMillis)
                ) else emptyList()

            override suspend fun exportCollectionsJson() = "{}"
            override suspend fun importCollectionsFromJson(json: String) = 0 to 0
        }
        viewModel = CollectionListViewModel(collectRepo, FakeCollectionUiPrefs(), FakeSiteConfig(), stubLocalVideoRepo)

        viewModel.loadCollection(MovieDBType)
        advanceUntilIdle()
        Thread.sleep(500)
        advanceUntilIdle()

        viewModel.updateFilter(CollectionFilterState(collectYear = 2026, collectMonth = 6))
        advanceUntilIdle()

        val state = viewModel.uiState.value
        // Only the June-collected item remains
        assertEquals(1, state.movies.size)
        assertEquals("June Movie", state.movies.first().title)
        // availableCollectMonths should include 6 for the selected collectYear
        assertTrue(state.availableCollectMonths.contains(6))
    }

    @Test
    fun updateFilter_downloaded_keepsOnlyMoviesWithLocalVideo() = runTest(testDispatcher) {
        val movies = listOf(
            Movie("Has Video", "http://img.jpg", "ABC-001", "2026-05-10", "http://link1"),
            Movie("No Video", "http://img.jpg", "ABC-002", "2026-06-01", "http://link2")
        )
        val collectRepo = object : CollectRepository {
            override suspend fun isCollected(linkItem: LinkItem) = false
            override suspend fun addCollect(linkItem: LinkItem) = true
            override suspend fun removeCollect(linkItem: LinkItem) = true
            override suspend fun isMovieCollected(movie: Movie) = false
            override suspend fun toggleMovieCollect(movie: Movie, categoryId: Int?) = true
            override suspend fun isActressCollected(actress: ActressInfo) = false
            override suspend fun toggleActressCollect(actress: ActressInfo, categoryId: Int?) = true
            override suspend fun getCollectedMovies() = movies
            override suspend fun getCollectedActresses() = emptyList<ActressInfo>()
            override suspend fun getCollectedLinkItems(dbType: Int): List<LinkItem> =
                if (dbType == MovieDBType) movies.map { it.toLinkItem() } else emptyList()

            override suspend fun exportCollectionsJson() = "{}"
            override suspend fun importCollectionsFromJson(json: String) = 0 to 0
        }
        val downloadedRepo = object : LocalVideoRepository {
            override fun observeForCode(code: String) = flowOf(emptyList<LocalVideo>())
            override fun observeDownloadedCodes() = flowOf(setOf("ABC-001"))
            override fun observeSummary() = flowOf(LocalVideoSummary())
            override fun hasFolder() = flowOf(true)
            override suspend fun setFolder(uri: android.net.Uri) {}
            override suspend fun clearFolder() {}
            override suspend fun rescan() = 0
            override fun observeAllGroupedByCode() = flowOf(emptyList<LocalVideoGroup>())
            override fun observeShowUncollectedLocal() = flowOf(false)
            override suspend fun setShowUncollectedLocal(value: Boolean) {}
            override suspend fun deleteVideos(ids: List<Int>) = DeleteResult(0, 0)
            override suspend fun snapshotMetadata(code: String, title: String, imageUrl: String, date: String, censorType: String?) {}
        }
        viewModel =
            CollectionListViewModel(collectRepo, FakeCollectionUiPrefs(), FakeSiteConfig(), downloadedRepo)

        viewModel.loadCollection(MovieDBType)
        advanceUntilIdle()
        Thread.sleep(500)
        advanceUntilIdle()

        // Default: both movies present
        assertEquals(2, viewModel.uiState.value.movies.size)

        viewModel.updateFilter(CollectionFilterState(localVideoFilter = LocalVideoFilter.DOWNLOADED))
        advanceUntilIdle()

        val filtered = viewModel.uiState.value.movies
        assertEquals(1, filtered.size)
        assertEquals("ABC-001", filtered.first().code)
    }

    @Test
    fun updateFilter_notDownloaded_keepsOnlyMoviesWithoutLocalVideo() = runTest(testDispatcher) {
        val movies = listOf(
            Movie("Has Video", "http://img.jpg", "ABC-001", "2026-05-10", "http://link1"),
            Movie("No Video", "http://img.jpg", "ABC-002", "2026-06-01", "http://link2")
        )
        val collectRepo = object : CollectRepository {
            override suspend fun isCollected(linkItem: LinkItem) = false
            override suspend fun addCollect(linkItem: LinkItem) = true
            override suspend fun removeCollect(linkItem: LinkItem) = true
            override suspend fun isMovieCollected(movie: Movie) = false
            override suspend fun toggleMovieCollect(movie: Movie, categoryId: Int?) = true
            override suspend fun isActressCollected(actress: ActressInfo) = false
            override suspend fun toggleActressCollect(actress: ActressInfo, categoryId: Int?) = true
            override suspend fun getCollectedMovies() = movies
            override suspend fun getCollectedActresses() = emptyList<ActressInfo>()
            override suspend fun getCollectedLinkItems(dbType: Int): List<LinkItem> =
                if (dbType == MovieDBType) movies.map { it.toLinkItem() } else emptyList()

            override suspend fun exportCollectionsJson() = "{}"
            override suspend fun importCollectionsFromJson(json: String) = 0 to 0
        }
        val downloadedRepo = object : LocalVideoRepository {
            override fun observeForCode(code: String) = flowOf(emptyList<LocalVideo>())
            override fun observeDownloadedCodes() = flowOf(setOf("ABC-001"))
            override fun observeSummary() = flowOf(LocalVideoSummary())
            override fun hasFolder() = flowOf(true)
            override suspend fun setFolder(uri: android.net.Uri) {}
            override suspend fun clearFolder() {}
            override suspend fun rescan() = 0
            override fun observeAllGroupedByCode() = flowOf(emptyList<LocalVideoGroup>())
            override fun observeShowUncollectedLocal() = flowOf(false)
            override suspend fun setShowUncollectedLocal(value: Boolean) {}
            override suspend fun deleteVideos(ids: List<Int>) = DeleteResult(0, 0)
            override suspend fun snapshotMetadata(code: String, title: String, imageUrl: String, date: String, censorType: String?) {}
        }
        viewModel =
            CollectionListViewModel(collectRepo, FakeCollectionUiPrefs(), FakeSiteConfig(), downloadedRepo)

        viewModel.loadCollection(MovieDBType)
        advanceUntilIdle()
        Thread.sleep(500)
        advanceUntilIdle()

        // Default: both movies present
        assertEquals(2, viewModel.uiState.value.movies.size)

        viewModel.updateFilter(CollectionFilterState(localVideoFilter = LocalVideoFilter.NOT_DOWNLOADED))
        advanceUntilIdle()

        val filtered = viewModel.uiState.value.movies
        assertEquals(1, filtered.size)
        assertEquals("ABC-002", filtered.first().code)
    }

    @Test
    fun showUncollectedLocal_listsCodesNotInCollection() = runTest(testDispatcher) {
        val collectRepo = object : CollectRepository {
            override suspend fun isCollected(linkItem: LinkItem) = false
            override suspend fun addCollect(linkItem: LinkItem) = true
            override suspend fun removeCollect(linkItem: LinkItem) = true
            override suspend fun isMovieCollected(movie: Movie) = false
            override suspend fun toggleMovieCollect(movie: Movie, categoryId: Int?) = true
            override suspend fun isActressCollected(actress: ActressInfo) = false
            override suspend fun toggleActressCollect(actress: ActressInfo, categoryId: Int?) = true
            override suspend fun getCollectedMovies() = listOf(Movie("Collected", "i", "ABC-001", "2024-01-01", "link1"))
            override suspend fun getCollectedActresses() = emptyList<ActressInfo>()
            override suspend fun getCollectedLinkItems(dbType: Int): List<LinkItem> =
                if (dbType == MovieDBType) listOf(Movie("Collected", "i", "ABC-001", "2024-01-01", "link1").toLinkItem()) else emptyList()
            override suspend fun exportCollectionsJson() = "{}"
            override suspend fun importCollectionsFromJson(json: String) = 0 to 0
        }
        val localRepo = object : LocalVideoRepository {
            override fun observeForCode(code: String) = flowOf(emptyList<LocalVideo>())
            override fun observeDownloadedCodes() = flowOf(emptySet<String>())
            override fun observeSummary() = flowOf(LocalVideoSummary())
            override fun hasFolder() = flowOf(true)
            override suspend fun setFolder(uri: android.net.Uri) {}
            override suspend fun clearFolder() {}
            override suspend fun rescan() = 0
            override fun observeAllGroupedByCode() = flowOf(
                listOf(
                    LocalVideoGroup("ABC-001", null, null, null, null, emptyList()), // 已收藏 → 不出现
                    LocalVideoGroup("DEF-002", "DEF Title", "http://def", null, null, emptyList()), // 未收藏 → 出现
                )
            )
            override fun observeShowUncollectedLocal() = flowOf(true)
            override suspend fun setShowUncollectedLocal(value: Boolean) {}
            override suspend fun deleteVideos(ids: List<Int>) = DeleteResult(0, 0)
            override suspend fun snapshotMetadata(code: String, title: String, imageUrl: String, date: String, censorType: String?) {}
        }
        viewModel = CollectionListViewModel(collectRepo, FakeCollectionUiPrefs(), FakeSiteConfig(), localRepo)

        viewModel.loadCollection(MovieDBType)
        advanceUntilIdle(); Thread.sleep(500); advanceUntilIdle()

        val uncollected = viewModel.uiState.value.uncollectedVideos
        assertEquals(1, uncollected.size)
        assertEquals("DEF-002", uncollected.first().code)
        assertEquals("DEF Title", uncollected.first().title)
        // movieCount 仅含已收藏，未被污染
        assertEquals(1, viewModel.uiState.value.movieCount)
    }

    private fun mktime(year: Int, month: Int, day: Int): Long =
        java.util.Calendar.getInstance().apply {
            clear()
            set(year, month - 1, day, 12, 0, 0)
        }.timeInMillis

    @Test
    fun loadCollection_handlesError() = runTest(testDispatcher) {
        val collectRepo = object : CollectRepository {
            override suspend fun isCollected(linkItem: LinkItem) = false
            override suspend fun addCollect(linkItem: LinkItem) = true
            override suspend fun removeCollect(linkItem: LinkItem) = true
            override suspend fun isMovieCollected(movie: Movie) = false
            override suspend fun toggleMovieCollect(movie: Movie, categoryId: Int?) = true
            override suspend fun isActressCollected(actress: ActressInfo) = false
            override suspend fun toggleActressCollect(actress: ActressInfo, categoryId: Int?) = true
            override suspend fun getCollectedMovies() = throw RuntimeException("DB error")
            override suspend fun getCollectedActresses() = throw RuntimeException("DB error")
            override suspend fun getCollectedLinkItems(dbType: Int): List<LinkItem> =
                throw RuntimeException("DB error")

            override suspend fun exportCollectionsJson() = "{}"
            override suspend fun importCollectionsFromJson(json: String) = 0 to 0
        }
        viewModel = CollectionListViewModel(collectRepo, FakeCollectionUiPrefs(), FakeSiteConfig(), stubLocalVideoRepo)

        viewModel.loadCollection(1)
        advanceUntilIdle()
        Thread.sleep(100)
        advanceUntilIdle()

        assertEquals(R.string.collect_load_failed, viewModel.uiState.value.error)
        assertFalse(viewModel.uiState.value.isLoading)
    }

    private class FakeSiteConfig : SiteConfig {
        override var baseUrl: String = "https://www.javbus.com"
        override fun resolve(pathOrUrl: String): String = pathOrUrl
    }
}

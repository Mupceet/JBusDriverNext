package me.jbusdriver.modern.ui.movielist

import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.cancel
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
import me.jbusdriver.modern.data.db.ActressDBType
import me.jbusdriver.modern.data.db.MovieDBType
import me.jbusdriver.modern.data.db.entity.LinkItem
import me.jbusdriver.modern.data.db.toILink
import me.jbusdriver.modern.domain.model.ActressInfo
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

    private fun Movie.toLinkItem(createTime: Long = 1_000L) = LinkItem(
        dbType = MovieDBType,
        key = link,
        jsonStr = toJsonString(),
        createTime = createTime,
        categoryId = 1
    )

    private fun ActressInfo.toLinkItem(createTime: Long = 1_000L) = LinkItem(
        dbType = ActressDBType,
        key = link,
        jsonStr = toJsonString(),
        createTime = createTime,
        categoryId = 2
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
            override suspend fun toggleActressCollect(actress: ActressInfo) = true
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
        viewModel = CollectionListViewModel(collectRepo, FakeCollectionUiPrefs(), FakeSiteConfig())

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
            override suspend fun toggleActressCollect(actress: ActressInfo) = true
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
        viewModel = CollectionListViewModel(collectRepo, FakeCollectionUiPrefs(), FakeSiteConfig())

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
    fun loadCollection_handlesError() = runTest(testDispatcher) {
        val collectRepo = object : CollectRepository {
            override suspend fun isCollected(linkItem: LinkItem) = false
            override suspend fun addCollect(linkItem: LinkItem) = true
            override suspend fun removeCollect(linkItem: LinkItem) = true
            override suspend fun isMovieCollected(movie: Movie) = false
            override suspend fun toggleMovieCollect(movie: Movie, categoryId: Int?) = true
            override suspend fun isActressCollected(actress: ActressInfo) = false
            override suspend fun toggleActressCollect(actress: ActressInfo) = true
            override suspend fun getCollectedMovies() = throw RuntimeException("DB error")
            override suspend fun getCollectedActresses() = throw RuntimeException("DB error")
            override suspend fun getCollectedLinkItems(dbType: Int): List<LinkItem> =
                throw RuntimeException("DB error")

            override suspend fun exportCollectionsJson() = "{}"
            override suspend fun importCollectionsFromJson(json: String) = 0 to 0
        }
        viewModel = CollectionListViewModel(collectRepo, FakeCollectionUiPrefs(), FakeSiteConfig())

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

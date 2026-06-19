package me.jbusdriver.modern.ui.detail

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import me.jbusdriver.R
import me.jbusdriver.modern.data.repository.CollectRepository
import me.jbusdriver.modern.data.repository.MagnetRepository
import me.jbusdriver.modern.data.repository.MovieDetailRepository
import me.jbusdriver.modern.data.db.entity.LinkItem
import me.jbusdriver.modern.domain.model.ActressInfo
import me.jbusdriver.modern.domain.model.Header
import me.jbusdriver.modern.domain.model.Magnet
import me.jbusdriver.modern.domain.model.Movie
import me.jbusdriver.modern.domain.model.MovieDetail
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
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
        override suspend fun toggleActressCollect(actress: ActressInfo) = true
        override suspend fun getCollectedMovies(): List<Movie> = emptyList()
        override suspend fun getCollectedActresses(): List<ActressInfo> = emptyList()
        override suspend fun getCollectedLinkItems(dbType: Int): List<LinkItem> = emptyList()
        override suspend fun exportCollectionsJson() = "{}"
        override suspend fun importCollectionsFromJson(json: String) = 0 to 0
    }

    private val stubMagnetRepo = object : MagnetRepository {
        override suspend fun fetchMagnets(gid: String, uc: String): List<Magnet> = emptyList()
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
        val viewModel = MovieDetailViewModel(detailRepo, stubCollectRepo, stubMagnetRepo)

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
        val viewModel = MovieDetailViewModel(detailRepo, stubCollectRepo, stubMagnetRepo)

        viewModel.loadDetail("http://example.com/ABC-001")
        advanceUntilIdle()

        assertEquals(R.string.load_failed, viewModel.uiState.value.error)
        assertFalse(viewModel.uiState.value.isLoading)
    }

    @Test
    fun refresh_reloadsDetail() = runTest(testDispatcher) {
        var callCount = 0
        val detailRepo = object : MovieDetailRepository {
            override suspend fun getMovieDetail(url: String, forceRefresh: Boolean) =
                testDetail.also { callCount++ }
        }
        val viewModel = MovieDetailViewModel(detailRepo, stubCollectRepo, stubMagnetRepo)

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
        val viewModel = MovieDetailViewModel(detailRepo, collectRepo, stubMagnetRepo)

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
        val viewModel = MovieDetailViewModel(detailRepo, stubCollectRepo, stubMagnetRepo)

        viewModel.loadDetail("http://example.com/ABC-001")
        advanceUntilIdle()

        assertEquals("12345", viewModel.uiState.value.gid)
        assertEquals("67890", viewModel.uiState.value.uc)
    }
}

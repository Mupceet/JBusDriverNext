package me.jbusdriver.modern.ui.movielist

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import me.jbusdriver.modern.data.CollectRepository
import me.jbusdriver.modern.data.db.entity.LinkItem
import me.jbusdriver.modern.domain.model.ActressInfo
import me.jbusdriver.modern.domain.model.Movie
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class CollectionListViewModelTest {

    private val testDispatcher = StandardTestDispatcher()

    private val testMovies = listOf(
        Movie("Collected Movie", "http://img.jpg", "ABC-001", "2024-01-01", "http://link1")
    )

    private val testActresses = listOf(
        ActressInfo("Alice", "http://avatar.jpg", "http://link1")
    )

    @Before
    fun setup() {
        Dispatchers.setMain(testDispatcher)
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun loadCollection_movieType_loadsMovies() = runTest(testDispatcher) {
        val collectRepo = object : CollectRepository {
            override suspend fun isCollected(linkItem: LinkItem) = false
            override suspend fun addCollect(linkItem: LinkItem) = true
            override suspend fun removeCollect(linkItem: LinkItem) = true
            override suspend fun isMovieCollected(movie: Movie) = false
            override suspend fun toggleMovieCollect(movie: Movie) = true
            override suspend fun isActressCollected(actress: ActressInfo) = false
            override suspend fun toggleActressCollect(actress: ActressInfo) = true
            override suspend fun getCollectedMovies() = testMovies
            override suspend fun getCollectedActresses() = testActresses
        }
        val viewModel = CollectionListViewModel(collectRepo)

        viewModel.loadCollection(1) // MovieDBType
        advanceUntilIdle()
        Thread.sleep(100)
        advanceUntilIdle()

        assertEquals(1, viewModel.uiState.value.movies.size)
        assertEquals("Collected Movie", viewModel.uiState.value.movies.first().title)
        assertFalse(viewModel.uiState.value.isLoading)
        assertNull(viewModel.uiState.value.error)
    }

    @Test
    fun loadCollection_actressType_loadsActresses() = runTest(testDispatcher) {
        val collectRepo = object : CollectRepository {
            override suspend fun isCollected(linkItem: LinkItem) = false
            override suspend fun addCollect(linkItem: LinkItem) = true
            override suspend fun removeCollect(linkItem: LinkItem) = true
            override suspend fun isMovieCollected(movie: Movie) = false
            override suspend fun toggleMovieCollect(movie: Movie) = true
            override suspend fun isActressCollected(actress: ActressInfo) = false
            override suspend fun toggleActressCollect(actress: ActressInfo) = true
            override suspend fun getCollectedMovies() = testMovies
            override suspend fun getCollectedActresses() = testActresses
        }
        val viewModel = CollectionListViewModel(collectRepo)

        viewModel.loadCollection(2) // ActressDBType
        advanceUntilIdle()
        Thread.sleep(100)
        advanceUntilIdle()

        assertEquals(1, viewModel.uiState.value.actresses.size)
        assertEquals("Alice", viewModel.uiState.value.actresses.first().name)
        assertFalse(viewModel.uiState.value.isLoading)
    }

    @Test
    fun loadCollection_handlesError() = runTest(testDispatcher) {
        val collectRepo = object : CollectRepository {
            override suspend fun isCollected(linkItem: LinkItem) = false
            override suspend fun addCollect(linkItem: LinkItem) = true
            override suspend fun removeCollect(linkItem: LinkItem) = true
            override suspend fun isMovieCollected(movie: Movie) = false
            override suspend fun toggleMovieCollect(movie: Movie) = true
            override suspend fun isActressCollected(actress: ActressInfo) = false
            override suspend fun toggleActressCollect(actress: ActressInfo) = true
            override suspend fun getCollectedMovies() = throw RuntimeException("DB error")
            override suspend fun getCollectedActresses() = throw RuntimeException("DB error")
        }
        val viewModel = CollectionListViewModel(collectRepo)

        viewModel.loadCollection(1)
        advanceUntilIdle()
        Thread.sleep(100)
        advanceUntilIdle()

        assertEquals("DB error", viewModel.uiState.value.error)
        assertFalse(viewModel.uiState.value.isLoading)
    }
}

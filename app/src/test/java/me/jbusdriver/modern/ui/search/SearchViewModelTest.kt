package me.jbusdriver.modern.ui.search

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import me.jbusdriver.modern.data.SearchHistoryStore
import me.jbusdriver.modern.data.SearchRepository
import me.jbusdriver.modern.domain.model.MoviePageResult
import me.jbusdriver.modern.domain.model.PageInfo
import me.jbusdriver.modern.domain.model.Movie
import me.jbusdriver.modern.domain.model.SearchType
import me.jbusdriver.modern.domain.model.ActressInfo
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class SearchViewModelTest {

    private val testDispatcher = StandardTestDispatcher()

    private val testMovies = listOf(
        Movie("Result 1", "http://img1.jpg", "ABC-001", "2024-01-01", "http://link1")
    )

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
        Dispatchers.setMain(testDispatcher)
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun search_loadsResults() = runTest(testDispatcher) {
        val repository = object : SearchRepository {
            override suspend fun searchMovies(type: SearchType, query: String, page: Int, forceRefresh: Boolean) =
                MoviePageResult(PageInfo(1, 2, listOf(1, 2)), testMovies)
            override suspend fun searchActresses(query: String, page: Int): Pair<PageInfo, List<ActressInfo>> =
                PageInfo() to emptyList()
        }
        val viewModel = SearchViewModel(repository, fakeHistoryStore())

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
            override suspend fun searchMovies(type: SearchType, query: String, page: Int, forceRefresh: Boolean) =
                throw RuntimeException("Search failed")
            override suspend fun searchActresses(query: String, page: Int): Pair<PageInfo, List<ActressInfo>> =
                PageInfo() to emptyList()
        }
        val viewModel = SearchViewModel(repository, fakeHistoryStore())

        viewModel.search("test")
        advanceUntilIdle()

        assertTrue(viewModel.uiState.value.error?.contains("Search failed") == true)
        assertFalse(viewModel.uiState.value.isLoading)
    }

    @Test
    fun search_emptyQuery_doesNotLoad() = runTest(testDispatcher) {
        val repository = object : SearchRepository {
            override suspend fun searchMovies(type: SearchType, query: String, page: Int, forceRefresh: Boolean) =
                error("Should not be called")
            override suspend fun searchActresses(query: String, page: Int) =
                error("Should not be called")
        }
        val viewModel = SearchViewModel(repository, fakeHistoryStore())

        viewModel.search("")
        advanceUntilIdle()

        assertTrue(viewModel.uiState.value.results.isEmpty())
        assertFalse(viewModel.uiState.value.isLoading)
    }
}

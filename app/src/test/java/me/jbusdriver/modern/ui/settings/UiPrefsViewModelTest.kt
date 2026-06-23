package me.jbusdriver.modern.ui.settings

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import me.jbusdriver.modern.data.settings.MovieListStyle
import me.jbusdriver.modern.data.settings.MovieListStyleSettings
import org.junit.After
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class UiPrefsViewModelTest {
    private val dispatcher = StandardTestDispatcher()

    @Before
    fun setUp() {
        Dispatchers.setMain(dispatcher)
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun uiStateReflectsGridPreferenceAndIntents() = runTest(dispatcher) {
        val store = FakeMovieListStyleSettings()
        val viewModel = UiPrefsViewModel(store)
        advanceUntilIdle()

        assertFalse(viewModel.uiState.value.isGrid)

        viewModel.setGrid(true)
        advanceUntilIdle()
        assertTrue(viewModel.uiState.value.isGrid)

        viewModel.toggleGrid()
        advanceUntilIdle()
        assertFalse(viewModel.uiState.value.isGrid)
    }

    private class FakeMovieListStyleSettings : MovieListStyleSettings {
        override val movieListStyle = MutableStateFlow(MovieListStyle.LIST)

        override suspend fun setMovieListStyle(style: MovieListStyle) {
            movieListStyle.value = style
        }
    }
}

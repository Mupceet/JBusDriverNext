package me.jbusdriver.modern.ui.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import me.jbusdriver.modern.data.settings.MovieLoadMode
import me.jbusdriver.modern.data.settings.MovieListStyle
import me.jbusdriver.modern.data.settings.MovieListSettings
import javax.inject.Inject

data class UiPrefsUiState(
    val style: MovieListStyle = MovieListStyle.LIST,
    val loadMode: MovieLoadMode = MovieLoadMode.WITH_MAGNET
) {
    val isGrid: Boolean get() = style.isGrid
    val defaultShowAll: Boolean get() = loadMode.showAll
}

@HiltViewModel
class UiPrefsViewModel @Inject constructor(
    private val store: MovieListSettings
) : ViewModel() {
    val uiState: StateFlow<UiPrefsUiState> = combine(
        store.movieListStyle,
        store.movieLoadMode
    ) { style, loadMode ->
        UiPrefsUiState(style = style, loadMode = loadMode)
    }
        .stateIn(viewModelScope, SharingStarted.Eagerly, UiPrefsUiState())

    fun setGrid(isGrid: Boolean) {
        viewModelScope.launch {
            store.setMovieListStyle(if (isGrid) MovieListStyle.GRID else MovieListStyle.LIST)
        }
    }

    fun toggleGrid() {
        setGrid(!uiState.value.isGrid)
    }
}

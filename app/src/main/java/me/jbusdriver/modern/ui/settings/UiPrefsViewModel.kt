package me.jbusdriver.modern.ui.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import me.jbusdriver.modern.data.settings.MovieListStyle
import me.jbusdriver.modern.data.settings.MovieListStyleSettings
import javax.inject.Inject

data class UiPrefsUiState(
    val style: MovieListStyle = MovieListStyle.LIST
) {
    val isGrid: Boolean get() = style.isGrid
}

@HiltViewModel
class UiPrefsViewModel @Inject constructor(
    private val store: MovieListStyleSettings
) : ViewModel() {
    val uiState: StateFlow<UiPrefsUiState> = store.movieListStyle
        .map { UiPrefsUiState(style = it) }
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

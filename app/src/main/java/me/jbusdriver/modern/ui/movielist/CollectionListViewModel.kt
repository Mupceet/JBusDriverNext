package me.jbusdriver.modern.ui.movielist

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import me.jbusdriver.modern.data.CollectRepository
import me.jbusdriver.modern.ui.ActressUiModel
import me.jbusdriver.modern.ui.MovieUiModel
import me.jbusdriver.modern.ui.toActressUiModel
import me.jbusdriver.modern.ui.toUiModel
import me.jbusdriver.modern.domain.model.ActressDBType
import me.jbusdriver.modern.domain.model.MovieDBType
import javax.inject.Inject

data class CollectionListUiState(
    val movies: List<MovieUiModel> = emptyList(),
    val actresses: List<ActressUiModel> = emptyList(),
    val isLoading: Boolean = false,
    val error: String? = null
)

@HiltViewModel
class CollectionListViewModel @Inject constructor(
    private val collectRepository: CollectRepository
) : ViewModel() {

    private val _uiState = MutableStateFlow(CollectionListUiState())
    val uiState: StateFlow<CollectionListUiState> = _uiState.asStateFlow()

    fun loadCollection(dbType: Int) {
        viewModelScope.launch(Dispatchers.IO) {
            _uiState.update { it.copy(isLoading = true, error = null, movies = emptyList(), actresses = emptyList()) }
            try {
                if (dbType == MovieDBType) {
                    val movies = collectRepository.getCollectedMovies().map { it.toUiModel() }
                    _uiState.update { it.copy(movies = movies, isLoading = false) }
                } else {
                    val actresses = collectRepository.getCollectedActresses().map { it.toActressUiModel() }
                    _uiState.update { it.copy(actresses = actresses, isLoading = false) }
                }
            } catch (e: Exception) {
                _uiState.update { it.copy(isLoading = false, error = e.message ?: "加载收藏失败") }
            }
        }
    }

    fun refresh(dbType: Int) {
        loadCollection(dbType)
    }
}
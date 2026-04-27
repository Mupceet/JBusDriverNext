package me.jbusdriver.modern.ui.detail

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import me.jbusdriver.modern.data.MovieDetailRepository
import me.jbusdriver.modern.ui.MovieDetailUiModel
import me.jbusdriver.modern.ui.toUiModel
import javax.inject.Inject

data class MovieDetailUiState(
    val isLoading: Boolean = false,
    val isRefreshing: Boolean = false,
    val movieDetail: MovieDetailUiModel? = null,
    val error: String? = null
)

@HiltViewModel
class MovieDetailViewModel @Inject constructor(
    private val repository: MovieDetailRepository
) : ViewModel() {

    private val _uiState = MutableStateFlow(MovieDetailUiState())
    val uiState: StateFlow<MovieDetailUiState> = _uiState.asStateFlow()

    private var currentUrl: String = ""

    fun loadDetail(url: String) {
        if (_uiState.value.isLoading) return
        currentUrl = url
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true, error = null) }
            try {
                val detail = repository.getMovieDetail(url)
                _uiState.update { it.copy(movieDetail = detail.toUiModel(), isLoading = false) }
            } catch (e: Exception) {
                _uiState.update { it.copy(isLoading = false, error = e.message ?: "加载失败") }
            }
        }
    }

    fun refresh() {
        if (currentUrl.isBlank()) return
        viewModelScope.launch {
            _uiState.update { it.copy(isRefreshing = true, error = null) }
            try {
                val detail = repository.getMovieDetail(currentUrl)
                _uiState.update { it.copy(movieDetail = detail.toUiModel(), isRefreshing = false) }
            } catch (e: Exception) {
                _uiState.update { it.copy(isRefreshing = false, error = e.message) }
            }
        }
    }

    fun clearError() {
        _uiState.update { it.copy(error = null) }
    }
}

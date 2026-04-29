package me.jbusdriver.modern.ui.search

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import me.jbusdriver.modern.data.SearchRepository
import me.jbusdriver.modern.data.model.hasNext
import me.jbusdriver.modern.ui.MovieUiModel
import me.jbusdriver.modern.ui.toUiModel
import me.jbusdriver.modern.domain.model.SearchType
import javax.inject.Inject

data class SearchUiState(
    val query: String = "",
    val searchType: SearchType = SearchType.CENSORED,
    val results: List<MovieUiModel> = emptyList(),
    val isLoading: Boolean = false,
    val isRefreshing: Boolean = false,
    val isLoadingMore: Boolean = false,
    val hasMore: Boolean = true,
    val error: String? = null,
    val currentPage: Int = 0
)

@HiltViewModel
class SearchViewModel @Inject constructor(
    private val repository: SearchRepository
) : ViewModel() {

    private val _uiState = MutableStateFlow(SearchUiState())
    val uiState: StateFlow<SearchUiState> = _uiState.asStateFlow()

    fun search(query: String, type: SearchType = SearchType.CENSORED) {
        if (query.isBlank()) return
        viewModelScope.launch {
            _uiState.update {
                it.copy(query = query, searchType = type, isLoading = true, error = null, results = emptyList(), currentPage = 1)
            }
            try {
                val result = repository.searchMovies(type, query, 1)
                _uiState.update {
                    it.copy(
                        results = result.movies.map { it.toUiModel() },
                        isLoading = false,
                        hasMore = result.pageInfo.hasNext,
                        currentPage = result.pageInfo.activePage
                    )
                }
            } catch (e: Exception) {
                _uiState.update { it.copy(isLoading = false, error = e.message) }
            }
        }
    }

    fun refresh() {
        val state = _uiState.value
        if (state.isRefreshing || state.query.isBlank()) return
        viewModelScope.launch {
            _uiState.update { it.copy(isRefreshing = true, error = null) }
            try {
                val result = repository.searchMovies(state.searchType, state.query, 1, forceRefresh = true)
                _uiState.update {
                    it.copy(
                        results = result.movies.map { m -> m.toUiModel() },
                        isRefreshing = false,
                        hasMore = result.pageInfo.hasNext,
                        currentPage = result.pageInfo.activePage
                    )
                }
            } catch (e: Exception) {
                _uiState.update { it.copy(isRefreshing = false, error = e.message) }
            }
        }
    }

    fun loadMore() {
        val state = _uiState.value
        if (state.isLoading || state.isLoadingMore || !state.hasMore) return
        val nextPage = state.currentPage + 1

        viewModelScope.launch {
            _uiState.update { it.copy(isLoadingMore = true) }
            try {
                val result = repository.searchMovies(state.searchType, state.query, nextPage)
                _uiState.update {
                    it.copy(
                        results = it.results + result.movies.map { m -> m.toUiModel() },
                        isLoadingMore = false,
                        hasMore = result.pageInfo.hasNext,
                        currentPage = result.pageInfo.activePage
                    )
                }
            } catch (e: Exception) {
                _uiState.update { it.copy(isLoadingMore = false, error = e.message) }
            }
        }
    }

    fun setSearchType(type: SearchType) {
        val query = _uiState.value.query
        if (query.isNotBlank()) {
            search(query, type)
        } else {
            _uiState.update { it.copy(searchType = type) }
        }
    }

    fun clearError() {
        _uiState.update { it.copy(error = null) }
    }
}

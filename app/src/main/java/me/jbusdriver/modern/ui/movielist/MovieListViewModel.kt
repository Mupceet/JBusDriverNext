package me.jbusdriver.modern.ui.movielist

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import me.jbusdriver.modern.data.MovieRepository
import me.jbusdriver.modern.data.model.PageInfo
import me.jbusdriver.modern.data.model.hasNext
import me.jbusdriver.modern.ui.MovieUiModel
import me.jbusdriver.modern.ui.toUiModel
import me.jbusdriver.modern.domain.model.DataSourceType
import javax.inject.Inject

data class MovieListUiState(
    val movies: List<MovieUiModel> = emptyList(),
    val pageInfo: PageInfo = PageInfo(),
    val isLoading: Boolean = false,
    val isRefreshing: Boolean = false,
    val isLoadingMore: Boolean = false,
    val error: String? = null,
    val hasMore: Boolean = true
)

@HiltViewModel
class MovieListViewModel @Inject constructor(
    private val repository: MovieRepository
) : ViewModel() {

    private val _uiState = MutableStateFlow(MovieListUiState())
    val uiState: StateFlow<MovieListUiState> = _uiState.asStateFlow()

    private var currentPage = 0
    private var dataSourceType: DataSourceType = DataSourceType.CENSORED

    fun setDataSourceType(type: DataSourceType) {
        if (dataSourceType == type && _uiState.value.movies.isNotEmpty()) return
        dataSourceType = type
        currentPage = 0
        _uiState.value = MovieListUiState()
        loadFirstPage()
    }

    fun loadFirstPage() {
        if (_uiState.value.isLoading) return
        currentPage = 1
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true, error = null) }
            try {
                val result = repository.loadPage(dataSourceType, 1)
                _uiState.update {
                    it.copy(
                        movies = result.movies.map { it.toUiModel() },
                        pageInfo = result.pageInfo,
                        isLoading = false,
                        hasMore = result.pageInfo.hasNext,
                        error = if (result.movies.isEmpty()) "没有数据" else null
                    )
                }
            } catch (e: Exception) {
                _uiState.update { it.copy(isLoading = false, error = e.message ?: "加载失败") }
            }
        }
    }

    fun refresh() {
        if (_uiState.value.isRefreshing) return
        currentPage = 1
        viewModelScope.launch {
            _uiState.update { it.copy(isRefreshing = true, error = null) }
            try {
                val result = repository.loadPage(dataSourceType, 1, forceRefresh = true)
                _uiState.update {
                    it.copy(
                        movies = result.movies.map { it.toUiModel() },
                        pageInfo = result.pageInfo,
                        isRefreshing = false,
                        hasMore = result.pageInfo.hasNext
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
        val nextPage = state.pageInfo.nextPage
        if (nextPage <= currentPage) return

        currentPage = nextPage
        viewModelScope.launch {
            _uiState.update { it.copy(isLoadingMore = true) }
            try {
                val result = repository.loadPage(dataSourceType, nextPage)
                _uiState.update {
                    it.copy(
                        movies = it.movies + result.movies.map { m -> m.toUiModel() },
                        pageInfo = result.pageInfo,
                        isLoadingMore = false,
                        hasMore = result.pageInfo.hasNext
                    )
                }
            } catch (e: Exception) {
                currentPage = state.pageInfo.activePage
                _uiState.update { it.copy(isLoadingMore = false, error = e.message) }
            }
        }
    }

    fun clearError() {
        _uiState.update { it.copy(error = null) }
    }
}

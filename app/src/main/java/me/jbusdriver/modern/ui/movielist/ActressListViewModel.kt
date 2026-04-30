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
import me.jbusdriver.modern.data.MovieRepository
import me.jbusdriver.modern.data.model.PageInfo
import me.jbusdriver.modern.data.model.hasNext
import me.jbusdriver.modern.ui.ActressUiModel
import me.jbusdriver.modern.ui.toActressUiModel
import me.jbusdriver.modern.domain.model.DataSourceType
import javax.inject.Inject

data class ActressListUiState(
    val actresses: List<ActressUiModel> = emptyList(),
    val pageInfo: PageInfo = PageInfo(),
    val isLoading: Boolean = false,
    val isRefreshing: Boolean = false,
    val isLoadingMore: Boolean = false,
    val hasMore: Boolean = true,
    val error: String? = null
)

@HiltViewModel
class ActressListViewModel @Inject constructor(
    private val repository: MovieRepository
) : ViewModel() {

    private val _uiState = MutableStateFlow(ActressListUiState())
    val uiState: StateFlow<ActressListUiState> = _uiState.asStateFlow()

    private var currentPage = 0
    private var dataSourceType: DataSourceType = DataSourceType.ACTRESSES

    fun setDataSourceType(type: DataSourceType) {
        if (dataSourceType == type && _uiState.value.actresses.isNotEmpty()) return
        dataSourceType = type
        currentPage = 0
        _uiState.value = ActressListUiState()
        loadFirstPage()
    }

    fun loadFirstPage() {
        if (_uiState.value.isLoading) return
        currentPage = 1
        viewModelScope.launch(Dispatchers.IO) {
            _uiState.update { it.copy(isLoading = true, error = null) }
            try {
                val result = repository.loadActresses(dataSourceType, 1)
                _uiState.update {
                    it.copy(
                        actresses = result.first.map { a -> a.toActressUiModel() },
                        pageInfo = result.second,
                        isLoading = false,
                        hasMore = result.second.hasNext,
                        error = if (result.first.isEmpty()) "没有数据" else null
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
        viewModelScope.launch(Dispatchers.IO) {
            _uiState.update { it.copy(isRefreshing = true, error = null) }
            try {
                val result = repository.loadActresses(dataSourceType, 1, forceRefresh = true)
                _uiState.update {
                    it.copy(
                        actresses = result.first.map { a -> a.toActressUiModel() },
                        pageInfo = result.second,
                        isRefreshing = false,
                        hasMore = result.second.hasNext
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
        viewModelScope.launch(Dispatchers.IO) {
            _uiState.update { it.copy(isLoadingMore = true) }
            try {
                val result = repository.loadActresses(dataSourceType, nextPage)
                _uiState.update {
                    it.copy(
                        actresses = it.actresses + result.first.map { a -> a.toActressUiModel() },
                        pageInfo = result.second,
                        isLoadingMore = false,
                        hasMore = result.second.hasNext
                    )
                }
            } catch (e: Exception) {
                currentPage = state.pageInfo.activePage
                _uiState.update { it.copy(isLoadingMore = false, error = e.message) }
            }
        }
    }
}

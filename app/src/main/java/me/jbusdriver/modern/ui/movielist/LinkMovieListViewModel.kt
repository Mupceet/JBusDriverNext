package me.jbusdriver.modern.ui.movielist

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import me.jbusdriver.modern.data.MovieRepository
import me.jbusdriver.modern.data.model.ActressDetail
import me.jbusdriver.modern.data.model.PageInfo
import me.jbusdriver.modern.data.model.hasNext
import me.jbusdriver.modern.ui.ActressDetailUiModel
import me.jbusdriver.modern.ui.MovieUiModel
import me.jbusdriver.modern.ui.toUiModel
import javax.inject.Inject

data class LinkMovieListUiState(
    val movies: List<MovieUiModel> = emptyList(),
    val pageInfo: PageInfo = PageInfo(),
    val isLoading: Boolean = false,
    val isLoadingMore: Boolean = false,
    val hasMore: Boolean = true,
    val error: String? = null,
    val actressDetail: ActressDetailUiModel? = null,
    val isLoadingActress: Boolean = false
)

@HiltViewModel
class LinkMovieListViewModel @Inject constructor(
    private val repository: MovieRepository,
    savedStateHandle: SavedStateHandle
) : ViewModel() {

    private val _uiState = MutableStateFlow(LinkMovieListUiState())
    val uiState: StateFlow<LinkMovieListUiState> = _uiState.asStateFlow()

    private var currentPage = 0
    private var linkUrl: String = savedStateHandle.get<String>("linkUrl") ?: ""
    private var listType: String = ""

    fun setLink(url: String, type: String = "", avatarUrl: String = "") {
        if (linkUrl == url && _uiState.value.movies.isNotEmpty()) return
        linkUrl = url
        listType = type
        currentPage = 0
        _uiState.value = LinkMovieListUiState()
        loadFirstPage()
        if (type == "actress" && avatarUrl.isNotBlank()) {
            loadActressDetail()
        }
    }

    fun loadFirstPage() {
        if (_uiState.value.isLoading || linkUrl.isBlank()) return
        currentPage = 1
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true, error = null) }
            try {
                val result = repository.loadPageByUrl(linkUrl, 1)
                _uiState.update {
                    it.copy(
                        movies = result.movies.map { m -> m.toUiModel() },
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

    fun loadMore() {
        val state = _uiState.value
        if (state.isLoading || state.isLoadingMore || !state.hasMore) return
        val nextPage = state.pageInfo.nextPage
        if (nextPage <= currentPage) return

        currentPage = nextPage
        viewModelScope.launch {
            _uiState.update { it.copy(isLoadingMore = true) }
            try {
                val result = repository.loadPageByUrl(linkUrl, nextPage)
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

    private fun loadActressDetail() {
        viewModelScope.launch {
            _uiState.update { it.copy(isLoadingActress = true) }
            try {
                val detail = repository.loadActressDetail(linkUrl)
                if (detail != null) {
                    _uiState.update {
                        it.copy(
                            actressDetail = ActressDetailUiModel(detail.name, detail.avatar, detail.info),
                            isLoadingActress = false
                        )
                    }
                } else {
                    _uiState.update { it.copy(isLoadingActress = false) }
                }
            } catch (_: Exception) {
                _uiState.update { it.copy(isLoadingActress = false) }
            }
        }
    }
}

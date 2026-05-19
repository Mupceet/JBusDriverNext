package me.jbusdriver.modern.ui.forum

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import me.jbusdriver.modern.data.ForumRepository
import me.jbusdriver.modern.domain.model.ForumBoard
import me.jbusdriver.modern.domain.model.ForumThread
import me.jbusdriver.modern.domain.model.ForumThreadDetail
import me.jbusdriver.modern.domain.model.ForumTypeFilter
import me.jbusdriver.modern.domain.model.PageInfo
import me.jbusdriver.modern.domain.model.hasNext
import javax.inject.Inject

data class ForumBoardsUiState(
    val boards: List<ForumBoard> = emptyList(),
    val isLoading: Boolean = false,
    val error: String? = null
)

data class ForumThreadListUiState(
    val threads: List<ForumThread> = emptyList(),
    val pageInfo: PageInfo = PageInfo(),
    val currentTypeId: Int? = null,
    val typeFilters: List<ForumTypeFilter> = emptyList(),
    val isLoading: Boolean = false,
    val isLoadingMore: Boolean = false,
    val isRefreshing: Boolean = false,
    val error: String? = null,
    val hasMore: Boolean = true
)

data class ForumThreadDetailUiState(
    val detail: ForumThreadDetail? = null,
    val isLoading: Boolean = false,
    val isRefreshing: Boolean = false,
    val error: String? = null,
    val isLoadingMore: Boolean = false
)

@HiltViewModel
class ForumBoardsViewModel @Inject constructor(
    private val repository: ForumRepository
) : ViewModel() {
    private val _uiState = MutableStateFlow(ForumBoardsUiState())
    val uiState: StateFlow<ForumBoardsUiState> = _uiState.asStateFlow()

    init { loadBoards() }

    fun loadBoards() {
        if (_uiState.value.isLoading) return
        viewModelScope.launch(Dispatchers.IO) {
            _uiState.update { it.copy(isLoading = true, error = null) }
            try {
                val boards = repository.loadForumBoards()
                _uiState.update { it.copy(boards = boards, isLoading = false) }
            } catch (e: Exception) {
                _uiState.update { it.copy(isLoading = false, error = e.message ?: "載入失敗") }
            }
        }
    }

    fun refresh() {
        viewModelScope.launch(Dispatchers.IO) {
            _uiState.update { it.copy(isLoading = true, error = null) }
            try {
                val boards = repository.loadForumBoards(forceRefresh = true)
                _uiState.update { it.copy(boards = boards, isLoading = false) }
            } catch (e: Exception) {
                _uiState.update { it.copy(isLoading = false, error = e.message ?: "載入失敗") }
            }
        }
    }
}

@HiltViewModel
class ForumThreadListViewModel @Inject constructor(
    private val repository: ForumRepository,
    savedStateHandle: SavedStateHandle
) : ViewModel() {
    private val fid: Int = savedStateHandle["fid"] ?: 2
    private var currentPage = 0

    private val _uiState = MutableStateFlow(ForumThreadListUiState())
    val uiState: StateFlow<ForumThreadListUiState> = _uiState.asStateFlow()

    init { loadFirstPage() }

    fun loadFirstPage() {
        if (_uiState.value.isLoading) return
        currentPage = 1
        viewModelScope.launch(Dispatchers.IO) {
            _uiState.update { it.copy(isLoading = true, error = null) }
            try {
                val result = repository.loadThreads(fid, 1, _uiState.value.currentTypeId)
                _uiState.update {
                    it.copy(
                        threads = result.threads,
                        pageInfo = result.pageInfo,
                        typeFilters = result.typeFilters,
                        isLoading = false,
                        hasMore = result.pageInfo.hasNext
                    )
                }
            } catch (e: Exception) {
                _uiState.update { it.copy(isLoading = false, error = e.message ?: "載入失敗") }
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
                val result = repository.loadThreads(fid, nextPage, state.currentTypeId)
                _uiState.update {
                    it.copy(
                        threads = it.threads + result.threads,
                        pageInfo = result.pageInfo,
                        isLoadingMore = false,
                        hasMore = result.pageInfo.hasNext
                    )
                }
            } catch (e: Exception) {
                currentPage = _uiState.value.pageInfo.activePage
                _uiState.update { it.copy(isLoadingMore = false) }
            }
        }
    }

    fun refresh() {
        if (_uiState.value.isRefreshing) return
        viewModelScope.launch(Dispatchers.IO) {
            _uiState.update { it.copy(isRefreshing = true, error = null) }
            try {
                val result = repository.loadThreads(fid, 1, _uiState.value.currentTypeId, forceRefresh = true)
                currentPage = 1
                _uiState.update {
                    it.copy(
                        threads = result.threads,
                        pageInfo = result.pageInfo,
                        typeFilters = result.typeFilters,
                        isRefreshing = false,
                        hasMore = result.pageInfo.hasNext
                    )
                }
            } catch (e: Exception) {
                _uiState.update { it.copy(isRefreshing = false, error = e.message ?: "載入失敗") }
            }
        }
    }

    fun filterByType(typeId: Int?) {
        if (_uiState.value.currentTypeId == typeId) return
        currentPage = 0
        _uiState.update { it.copy(currentTypeId = typeId, threads = emptyList(), pageInfo = PageInfo()) }
        loadFirstPage()
    }
}

@HiltViewModel
class ForumThreadDetailViewModel @Inject constructor(
    private val repository: ForumRepository,
    savedStateHandle: SavedStateHandle
) : ViewModel() {
    private val tid: Int = savedStateHandle["tid"] ?: 0
    private var currentPage = 1

    private val _uiState = MutableStateFlow(ForumThreadDetailUiState())
    val uiState: StateFlow<ForumThreadDetailUiState> = _uiState.asStateFlow()

    init { loadDetail() }

    fun loadDetail() {
        if (_uiState.value.isLoading) return
        viewModelScope.launch(Dispatchers.IO) {
            _uiState.update { it.copy(isLoading = true, error = null) }
            try {
                val detail = repository.loadThreadDetail(tid, currentPage)
                _uiState.update { it.copy(detail = detail, isLoading = false) }
            } catch (e: Exception) {
                _uiState.update { it.copy(isLoading = false, error = e.message ?: "載入失敗") }
            }
        }
    }

    fun refresh() {
        if (_uiState.value.isRefreshing) return
        viewModelScope.launch(Dispatchers.IO) {
            _uiState.update { it.copy(isRefreshing = true, error = null) }
            try {
                val detail = repository.loadThreadDetail(tid, currentPage, forceRefresh = true)
                _uiState.update { it.copy(detail = detail, isRefreshing = false) }
            } catch (e: Exception) {
                _uiState.update { it.copy(isRefreshing = false, error = e.message ?: "載入失敗") }
            }
        }
    }

    fun loadMoreReplies() {
        val detail = _uiState.value.detail ?: return
        if (_uiState.value.isLoadingMore) return
        val nextPage = detail.pageInfo.nextPage
        if (nextPage <= currentPage) return

        currentPage = nextPage
        viewModelScope.launch(Dispatchers.IO) {
            _uiState.update { it.copy(isLoadingMore = true) }
            try {
                val nextDetail = repository.loadThreadDetail(tid, nextPage)
                _uiState.update {
                    it.copy(
                        detail = detail.copy(
                            replies = detail.replies + nextDetail.replies,
                            pageInfo = nextDetail.pageInfo
                        ),
                        isLoadingMore = false
                    )
                }
            } catch (e: Exception) {
                currentPage = detail.pageInfo.activePage
                _uiState.update { it.copy(isLoadingMore = false) }
            }
        }
    }
}

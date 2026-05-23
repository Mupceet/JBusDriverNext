package me.jbusdriver.modern.ui.forum

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.assisted.Assisted
import dagger.assisted.AssistedFactory
import dagger.assisted.AssistedInject
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import android.content.SharedPreferences
import androidx.core.content.edit
import me.jbusdriver.modern.JBus
import me.jbusdriver.modern.KLog
import me.jbusdriver.modern.data.ForumRepository
import me.jbusdriver.modern.domain.model.ForumBanner
import me.jbusdriver.modern.domain.model.ForumBoardGroup
import me.jbusdriver.modern.domain.model.ForumHomeSummary
import me.jbusdriver.modern.domain.model.ForumSummaryThread
import me.jbusdriver.modern.domain.model.ForumThread
import me.jbusdriver.modern.domain.model.ForumThreadDetail
import me.jbusdriver.modern.domain.model.ForumTypeFilter
import me.jbusdriver.modern.domain.model.PageInfo
import me.jbusdriver.modern.domain.model.hasNext
import me.jbusdriver.modern.ui.RouteForumThreadDetail
import me.jbusdriver.modern.ui.RouteForumThreadList
import javax.inject.Inject

private const val TAG = "ForumVM"

data class ForumBoardsUiState(
    val banners: List<ForumBanner> = emptyList(),
    val summary: ForumHomeSummary = ForumHomeSummary(),
    val groups: List<ForumBoardGroup> = emptyList(),
    val isLoading: Boolean = false,
    val isRefreshing: Boolean = false,
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

    init {
        KLog.d("[Forum] ForumBoardsViewModel init", TAG)
        loadBoards()
    }

    fun loadBoards() {
        if (_uiState.value.isLoading) return
        KLog.d("[Forum] loadBoards started", TAG)
        viewModelScope.launch(Dispatchers.IO) {
            _uiState.update { it.copy(isLoading = true, error = null) }
            try {
                val data = repository.loadForumBoards()
                KLog.d("[Forum] loadBoards success: ${data.banners.size} banners, ${data.boardGroups.size} groups", TAG)
                _uiState.update { it.copy(banners = data.banners, summary = data.summary, groups = data.boardGroups, isLoading = false) }
            } catch (e: Exception) {
                KLog.e("[Forum] loadBoards failed: ${e.message}", e, TAG)
                _uiState.update { it.copy(isLoading = false, error = e.message ?: "載入失敗") }
            }
        }
    }

    fun refresh() {
        viewModelScope.launch(Dispatchers.IO) {
            _uiState.update { it.copy(isRefreshing = true, error = null) }
            try {
                val data = repository.loadForumBoards(forceRefresh = true)
                _uiState.update { it.copy(banners = data.banners, summary = data.summary, groups = data.boardGroups, isRefreshing = false) }
            } catch (e: Exception) {
                _uiState.update { it.copy(isRefreshing = false, error = e.message ?: "載入失敗") }
            }
        }
    }

    override fun onCleared() {
        super.onCleared()
        repository.destroySession()
    }
}

@HiltViewModel(assistedFactory = ForumThreadListViewModel.Factory::class)
class ForumThreadListViewModel @AssistedInject constructor(
    private val repository: ForumRepository,
    @Assisted private val navKey: RouteForumThreadList
) : ViewModel() {
    private val fid: Int = navKey.fid
    private val initialTypeId: Int? = navKey.typeId
    private var currentPage = 0

    private val _uiState = MutableStateFlow(ForumThreadListUiState())
    val uiState: StateFlow<ForumThreadListUiState> = _uiState.asStateFlow()

    init {
        KLog.d("[Forum] ForumThreadListViewModel init: fid=$fid, typeId=$initialTypeId", TAG)
        if (initialTypeId != null) {
            _uiState.update { it.copy(currentTypeId = initialTypeId) }
        }
        loadFirstPage()
    }

    fun loadFirstPage() {
        if (_uiState.value.isLoading) return
        currentPage = 1
        KLog.d("[Forum] loadFirstPage: fid=$fid, typeId=${_uiState.value.currentTypeId}", TAG)
        viewModelScope.launch(Dispatchers.IO) {
            _uiState.update { it.copy(isLoading = true, error = null) }
            try {
                val result = repository.loadThreads(fid, 1, _uiState.value.currentTypeId)
                KLog.d("[Forum] loadFirstPage success: ${result.threads.size} threads, ${result.typeFilters.size} filters", TAG)
                _uiState.update {
                    it.copy(
                        threads = result.threads,
                        pageInfo = result.pageInfo,
                        typeFilters = result.typeFilters.ifEmpty { it.typeFilters },
                        isLoading = false,
                        hasMore = result.pageInfo.hasNext
                    )
                }
            } catch (e: Exception) {
                KLog.e("[Forum] loadFirstPage failed: ${e.message}", e, TAG)
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

    @AssistedFactory
    interface Factory {
        fun create(navKey: RouteForumThreadList): ForumThreadListViewModel
    }
}

@HiltViewModel(assistedFactory = ForumThreadDetailViewModel.Factory::class)
class ForumThreadDetailViewModel @AssistedInject constructor(
    private val repository: ForumRepository,
    @Assisted private val navKey: RouteForumThreadDetail
) : ViewModel() {
    private val tid: Int = navKey.tid
    private var currentPage = 1

    private val _uiState = MutableStateFlow(ForumThreadDetailUiState())
    val uiState: StateFlow<ForumThreadDetailUiState> = _uiState.asStateFlow()

    private val gifPrefs: SharedPreferences = JBus.getSharedPreferences("gif_loaded_urls", 0)

    private val _loadedGifUrls = MutableStateFlow(loadPersistedGifUrls())
    val loadedGifUrlsFlow: StateFlow<Set<String>> = _loadedGifUrls
    val loadedGifUrls: Set<String> get() = _loadedGifUrls.value

    fun onLoadGif(url: String) {
        _loadedGifUrls.update { it + url }
        persistGifUrls(_loadedGifUrls.value)
    }

    private fun loadPersistedGifUrls(): Set<String> {
        return gifPrefs.getStringSet("urls", emptySet()) ?: emptySet()
    }

    private fun persistGifUrls(urls: Set<String>) {
        val trimmed = if (urls.size > MAX_GIF_CACHE) urls.toList().takeLast(MAX_GIF_CACHE).toSet() else urls
        gifPrefs.edit { putStringSet("urls", trimmed) }
    }

    init {
        KLog.d("[Forum] ForumThreadDetailViewModel init: tid=$tid", TAG)
        loadDetail()
    }

    fun loadDetail() {
        if (_uiState.value.isLoading) return
        KLog.d("[Forum] loadDetail: tid=$tid, page=$currentPage", TAG)
        viewModelScope.launch(Dispatchers.IO) {
            _uiState.update { it.copy(isLoading = true, error = null) }
            try {
                val detail = repository.loadThreadDetail(tid, currentPage)
                KLog.d("[Forum] loadDetail success: title=${detail.title}, replies=${detail.replies.size}", TAG)
                _uiState.update { it.copy(detail = detail, isLoading = false) }
            } catch (e: Exception) {
                KLog.e("[Forum] loadDetail failed: ${e.message}", e, TAG)
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

    @AssistedFactory
    interface Factory {
        fun create(navKey: RouteForumThreadDetail): ForumThreadDetailViewModel
    }

    companion object {
        private const val MAX_GIF_CACHE = 50
    }
}

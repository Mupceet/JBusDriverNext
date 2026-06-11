package me.jbusdriver.modern.ui.forum

import kotlinx.coroutines.flow.Flow
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
import kotlinx.coroutines.launch
import me.jbusdriver.modern.KLog
import me.jbusdriver.modern.core.cache.CachedLoadEvent
import me.jbusdriver.modern.data.ForumFloorOrder
import me.jbusdriver.modern.data.ForumRepository
import me.jbusdriver.modern.data.LabSettingsStore
import me.jbusdriver.modern.domain.model.ForumBanner
import me.jbusdriver.modern.domain.model.ForumBoardGroup
import me.jbusdriver.modern.domain.model.ForumHomeSummary
import me.jbusdriver.modern.domain.model.ForumThread
import me.jbusdriver.modern.domain.model.ForumThreadPageResult
import me.jbusdriver.modern.domain.model.ForumThreadDetail
import me.jbusdriver.modern.domain.model.ForumTypeFilter
import me.jbusdriver.modern.domain.model.PageInfo
import me.jbusdriver.modern.domain.model.hasNext
import me.jbusdriver.modern.ui.RouteForumThreadDetail
import me.jbusdriver.modern.ui.RouteForumThreadList
import javax.inject.Inject


// TODO: remove after testing cache refresh UX
private fun <T> List<T>.shuffledForTesting(): List<T> =
    if (size < 2) this else toMutableList().apply {
        val target = (1..lastIndex).random()
        val temp = this[0]
        this[0] = this[target]
        this[target] = temp
    }

private const val TAG = "ForumVM"

data class ForumBoardsUiState(
    val banners: List<ForumBanner> = emptyList(),
    val summary: ForumHomeSummary = ForumHomeSummary(),
    val groups: List<ForumBoardGroup> = emptyList(),
    val isLoading: Boolean = false,
    val isRefreshing: Boolean = false,
    val error: String? = null,
    val isRevalidating: Boolean = false,
    val lastUpdatedAtMillis: Long? = null,
    val refreshMessage: String? = null
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
    val hasMore: Boolean = true,
    val isRevalidating: Boolean = false,
    val lastUpdatedAtMillis: Long? = null,
    val pendingFreshThreads: ForumThreadPageResult? = null,
    val refreshMessage: String? = null
)

data class ForumThreadDetailUiState(
    val detail: ForumThreadDetail? = null,
    val floorOrder: ForumFloorOrder = ForumFloorOrder.REGULAR,
    val isLoading: Boolean = false,
    val isRefreshing: Boolean = false,
    val isRevalidating: Boolean = false,
    val lastUpdatedAtMillis: Long? = null,
    val pendingFreshDetail: ForumThreadDetail? = null,
    val refreshMessage: String? = null,
    val error: String? = null,
    val isLoadingMore: Boolean = false,
    val isChangingFloorOrder: Boolean = false
)

fun ForumThreadDetailUiState.prepareFloorOrderReload(order: ForumFloorOrder): ForumThreadDetailUiState =
    copy(
        floorOrder = order,
        error = null,
        isLoading = false,
        isLoadingMore = false,
        isChangingFloorOrder = true
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
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true, error = null, refreshMessage = null) }
            var hasContent = false
            repository.observeForumBoards(revalidate = true)
                .collect { event ->
                    when (event) {
                        is CachedLoadEvent.Cached -> {
                            hasContent = true
                            _uiState.update {
                                it.copy(
                                    banners = event.entry.value.banners,
                                    summary = event.entry.value.summary,
                                    groups = event.entry.value.boardGroups,
                                    isLoading = false,
                                    isRevalidating = true,
                                    lastUpdatedAtMillis = event.entry.storedAtMillis
                                )
                            }
                        }
                        is CachedLoadEvent.Fresh -> {
                            _uiState.update {
                                it.copy(
                                    banners = event.entry.value.banners,
                                    summary = event.entry.value.summary,
                                    groups = event.entry.value.boardGroups,
                                    isLoading = false,
                                    isRevalidating = false,
                                    lastUpdatedAtMillis = event.entry.storedAtMillis
                                )
                            }
                        }
                        is CachedLoadEvent.Failure -> {
                            _uiState.update {
                                if (event.hadCachedValue || hasContent) {
                                    it.copy(isLoading = false, isRevalidating = false)
                                } else {
                                    it.copy(isLoading = false, isRevalidating = false, error = event.throwable.message ?: "Loading failed")
                                }
                            }
                        }
                    }
                }
        }
    }


    fun refresh() {
        viewModelScope.launch {
            _uiState.update { it.copy(isRefreshing = true, error = null, refreshMessage = null) }
            repository.observeForumBoards(forceRefresh = true, revalidate = false)
                .collect { event ->
                    when (event) {
                        is CachedLoadEvent.Cached -> Unit
                        is CachedLoadEvent.Fresh -> {
                            _uiState.update {
                                it.copy(
                                    banners = event.entry.value.banners,
                                    summary = event.entry.value.summary,
                                    groups = event.entry.value.boardGroups,
                                    isRefreshing = false,
                                    lastUpdatedAtMillis = event.entry.storedAtMillis
                                )
                            }
                        }
                        is CachedLoadEvent.Failure -> {
                            _uiState.update {
                                it.copy(
                                    isRefreshing = false,
                                    error = if (it.groups.isEmpty()) event.throwable.message ?: "Loading failed" else it.error,
                                    refreshMessage = if (it.groups.isNotEmpty()) "Refresh failed" else null
                                )
                            }
                        }
                    }
                }
        }
    }
    fun consumeRefreshMessage() {
        _uiState.update { it.copy(refreshMessage = null) }
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

    private var isAtTopForFreshUpdates: Boolean = true

    fun setAtTopForFreshUpdates(isAtTop: Boolean) {
        isAtTopForFreshUpdates = isAtTop
    }

    fun applyPendingFreshThreads() {
        val pending = _uiState.value.pendingFreshThreads ?: return
        currentPage = 1
        _uiState.update {
            it.copy(
                threads = pending.threads,
                pageInfo = pending.pageInfo,
                typeFilters = pending.typeFilters,
                pendingFreshThreads = null,
                refreshMessage = null,
                hasMore = pending.pageInfo.hasNext
            )
        }
    }



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
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true, error = null, refreshMessage = null) }
            var hasContent = false
            repository.observeThreads(fid, 1, _uiState.value.currentTypeId, revalidate = true)
                .collect { event ->
                    when (event) {
                        is CachedLoadEvent.Cached -> {
                            hasContent = true
                            KLog.d("[CacheTest] ThreadList CACHED", TAG)
                            _uiState.update {
                                it.copy(
                                    threads = event.entry.value.threads.shuffledForTesting(),
                                    pageInfo = event.entry.value.pageInfo,
                                    typeFilters = event.entry.value.typeFilters.ifEmpty { it.typeFilters },
                                    isLoading = false,
                                    isRevalidating = true,
                                    lastUpdatedAtMillis = event.entry.storedAtMillis
                                )
                            }
                        }
                        is CachedLoadEvent.Fresh -> {
                            KLog.d("[CacheTest] ThreadList FRESH, isAtTop=$isAtTopForFreshUpdates", TAG)
                            if (isAtTopForFreshUpdates) {
                                _uiState.update {
                                    it.copy(
                                        threads = event.entry.value.threads,
                                        pageInfo = event.entry.value.pageInfo,
                                        typeFilters = event.entry.value.typeFilters,
                                        isLoading = false,
                                        isRevalidating = false,
                                        pendingFreshThreads = null,
                                        refreshMessage = null,
                                        lastUpdatedAtMillis = event.entry.storedAtMillis
                                    )
                                }
                            } else {
                                _uiState.update {
                                    it.copy(
                                        isLoading = false,
                                        isRevalidating = false,
                                        pendingFreshThreads = event.entry.value,
                                        refreshMessage = "Post updated"
                                    )
                                }
                                KLog.d("[CacheTest] ThreadList: PENDING fresh set", TAG)
                            }
                        }
                        is CachedLoadEvent.Failure -> {
                            _uiState.update {
                                if (event.hadCachedValue || hasContent) {
                                    it.copy(isLoading = false, isRevalidating = false)
                                } else {
                                    it.copy(isLoading = false, isRevalidating = false, error = event.throwable.message ?: "Loading failed")
                                }
                            }
                        }
                    }
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
                val result = repository.loadThreads(fid, nextPage, state.currentTypeId)
                _uiState.update {
                    it.copy(
                        threads = (it.threads + result.threads).distinctBy { it.tid },
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
        viewModelScope.launch {
            _uiState.update { it.copy(isRefreshing = true, error = null, refreshMessage = null) }
            repository.observeThreads(fid, 1, _uiState.value.currentTypeId, forceRefresh = true, revalidate = false)
                .collect { event ->
                    when (event) {
                        is CachedLoadEvent.Cached -> Unit
                        is CachedLoadEvent.Fresh -> {
                            currentPage = 1
                            _uiState.update {
                                it.copy(
                                    threads = event.entry.value.threads,
                                    pageInfo = event.entry.value.pageInfo,
                                    typeFilters = event.entry.value.typeFilters,
                                    isRefreshing = false,
                                    hasMore = event.entry.value.pageInfo.hasNext,
                                    pendingFreshThreads = null,
                                    refreshMessage = null
                                )
                            }
                        }
                        is CachedLoadEvent.Failure -> {
                            _uiState.update {
                                it.copy(
                                    isRefreshing = false,
                                    error = if (it.threads.isEmpty()) event.throwable.message ?: "Loading failed" else it.error
                                )
                            }
                        }
                    }
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
    private val forumSettingsReader: me.jbusdriver.modern.data.ForumSettingsReader,
    private val loadedGifTracker: me.jbusdriver.modern.data.LoadedGifTracker,
    @Assisted private val navKey: RouteForumThreadDetail
) : ViewModel() {
    private val tid: Int = navKey.tid
    private var currentPage = 1

    private val _uiState = MutableStateFlow(ForumThreadDetailUiState())
    val uiState: StateFlow<ForumThreadDetailUiState> = _uiState.asStateFlow()

    private val _loadedGifUrls = MutableStateFlow<Set<String>>(emptySet())
    val loadedGifUrlsFlow: StateFlow<Set<String>> = _loadedGifUrls
    val loadedGifUrls: Set<String> get() = _loadedGifUrls.value
    val autoLoadGifs: StateFlow<Boolean> = forumSettingsReader.autoLoadGifs

    fun onLoadGif(url: String) {
        _loadedGifUrls.update { it + url }
        viewModelScope.launch { persistGifUrls(setOf(url)) }
    }

    fun onLoadAllGifs() {
        val detail = _uiState.value.detail ?: return
        val allGifUrls = collectUnloadedGifUrls(detail)
        if (allGifUrls.isEmpty()) return
        _loadedGifUrls.update { it + allGifUrls }
        viewModelScope.launch { persistGifUrls(allGifUrls) }
    }

    private fun collectUnloadedGifUrls(detail: ForumThreadDetail): Set<String> {
        val loaded = _loadedGifUrls.value
        val allBlocks = detail.contentBlocks + detail.replies.flatMap { it.contentBlocks }
        return allBlocks
            .filterIsInstance<me.jbusdriver.modern.domain.model.ContentBlock.Image>()
            .map { it.url }
            .filter { it !in loaded }
            .toSet()
    }

    private suspend fun loadPersistedGifUrls(): Set<String> {
        return loadedGifTracker.loadedUrls()
    }

    private suspend fun persistGifUrls(urls: Set<String>) {
        for (url in urls) loadedGifTracker.markLoaded(url)
    }

    init {
        KLog.d("[Forum] ForumThreadDetailViewModel init: tid=$tid", TAG)
        viewModelScope.launch { _loadedGifUrls.value = loadPersistedGifUrls() }
        viewModelScope.launch {
            val defaultOrder = forumSettingsReader.currentForumFloorOrder()
            _uiState.update { it.copy(floorOrder = defaultOrder) }
            loadDetail()
        }
    }

    fun loadDetail(forceRefresh: Boolean = false, showLoading: Boolean = true) {
        if (showLoading && _uiState.value.isLoading) return
        val floorOrder = _uiState.value.floorOrder
        KLog.d("[Forum] loadDetail: tid=$tid, page=$currentPage, floorOrder=$floorOrder", TAG)
        viewModelScope.launch {
            var hasContent = _uiState.value.detail != null
            _uiState.update {
                when {
                    showLoading && !hasContent -> it.copy(isLoading = true, error = null, refreshMessage = null)
                    showLoading -> it.copy(isRevalidating = true, error = null, refreshMessage = null)
                    else -> it.copy(error = null, isChangingFloorOrder = true, refreshMessage = null)
                }
            }
            repository.observeThreadDetail(tid, currentPage, floorOrder, forceRefresh = forceRefresh, revalidate = showLoading)
                .collect { event ->
                    when (event) {
                        is CachedLoadEvent.Cached -> {
                            hasContent = true
                            KLog.d("[CacheTest] Detail CACHED", TAG)
                            _uiState.update {
                                it.copy(
                                    detail = event.entry.value.copy(replies = event.entry.value.replies.shuffledForTesting()),
                                    isLoading = false,
                                    isRevalidating = true,
                                    isChangingFloorOrder = false,
                                    lastUpdatedAtMillis = event.entry.storedAtMillis
                                )
                            }
                        }
                        is CachedLoadEvent.Fresh -> {
                            KLog.d("[CacheTest] Detail FRESH, isAtTop=$isAtTopForFreshUpdates", TAG)
                            if (isAtTopForFreshUpdates || forceRefresh || !showLoading) {
                                _uiState.update {
                                    it.copy(
                                        detail = event.entry.value,
                                        isLoading = false,
                                        isRevalidating = false,
                                        isChangingFloorOrder = false,
                                        pendingFreshDetail = null,
                                        lastUpdatedAtMillis = event.entry.storedAtMillis
                                    )
                                }
                            } else {
                                _uiState.update {
                                    it.copy(
                                        isLoading = false,
                                        isRevalidating = false,
                                        isChangingFloorOrder = false,
                                        pendingFreshDetail = event.entry.value.copy(replies = event.entry.value.replies.shuffledForTesting()),
                                        refreshMessage = "Post updated"
                                    )
                                }
                                KLog.d("[CacheTest] Detail: PENDING fresh set", TAG)
                            }
                        }
                        is CachedLoadEvent.Failure -> {
                            _uiState.update {
                                if (event.hadCachedValue || hasContent) {
                                    it.copy(isLoading = false, isRevalidating = false, isChangingFloorOrder = false)
                                } else {
                                    it.copy(isLoading = false, isRevalidating = false, isChangingFloorOrder = false, error = event.throwable.message ?: "Loading failed")
                                }
                            }
                        }
                    }
                }
        }
    }

    fun refresh() {
        if (_uiState.value.isRefreshing) return
        val floorOrder = _uiState.value.floorOrder
        currentPage = 1
        viewModelScope.launch {
            _uiState.update { it.copy(isRefreshing = true, error = null, refreshMessage = null, pendingFreshDetail = null) }
            repository.observeThreadDetail(tid, 1, floorOrder, forceRefresh = true, revalidate = false)
                .collect { event ->
                    when (event) {
                        is CachedLoadEvent.Cached -> Unit
                        is CachedLoadEvent.Fresh -> {
                            _uiState.update {
                                it.copy(
                                    detail = event.entry.value,
                                    isRefreshing = false,
                                    lastUpdatedAtMillis = event.entry.storedAtMillis
                                )
                            }
                        }
                        is CachedLoadEvent.Failure -> {
                            _uiState.update {
                                it.copy(
                                    isRefreshing = false,
                                    error = if (it.detail == null) event.throwable.message ?: "Loading failed" else it.error,
                                    refreshMessage = if (it.detail != null) "Refresh failed" else null
                                )
                            }
                        }
                    }
                }
        }
    }

    fun loadMoreReplies() {
        val detail = _uiState.value.detail ?: return
        if (_uiState.value.isLoadingMore) return
        val nextPage = detail.pageInfo.nextPage
        if (nextPage <= currentPage) return

        currentPage = nextPage
        val floorOrder = _uiState.value.floorOrder
        viewModelScope.launch {
            _uiState.update { it.copy(isLoadingMore = true) }
            try {
                val nextDetail = repository.loadThreadDetail(tid, nextPage, floorOrder)
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

    fun setFloorOrder(order: ForumFloorOrder) {
        if (_uiState.value.floorOrder == order || _uiState.value.isLoading || _uiState.value.isChangingFloorOrder) return
        currentPage = 1
        _uiState.update { it.prepareFloorOrderReload(order) }
        loadDetail(forceRefresh = true, showLoading = false)
    }

    private var isAtTopForFreshUpdates: Boolean = true

    fun setAtTopForFreshUpdates(isAtTop: Boolean) {
        isAtTopForFreshUpdates = isAtTop
    }

    fun applyPendingFreshDetail() {
        val pending = _uiState.value.pendingFreshDetail ?: return
        currentPage = 1
        _uiState.update {
            it.copy(
                detail = pending,
                pendingFreshDetail = null,
                refreshMessage = null,
                lastUpdatedAtMillis = System.currentTimeMillis()
            )
        }
    }

    @AssistedFactory
    interface Factory {
        fun create(navKey: RouteForumThreadDetail): ForumThreadDetailViewModel
    }
}

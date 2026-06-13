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
import me.jbusdriver.modern.core.cache.simulateCacheRefreshChange
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


private const val TAG = "ForumVM"

private fun logThreadDiff(oldThreads: List<ForumThread>, newThreads: List<ForumThread>, context: String) {
    me.jbusdriver.modern.core.logListDiff(
        oldItems = oldThreads,
        newItems = newThreads,
        context = context,
        tag = TAG,
        keySelector = { it.tid },
        describe = { "${it.tid}:${it.title.take(20)}" },
        diffFields = { old, new ->
            buildList {
                if (old.replyCount != new.replyCount) add("replyCount:${old.replyCount}→${new.replyCount}")
                if (old.viewCount != new.viewCount) add("viewCount:${old.viewCount}→${new.viewCount}")
                if (old.title != new.title) add("title改變")
                if (old.isPinned != new.isPinned) add("isPinned:${old.isPinned}→${new.isPinned}")
                if (old.isDigest != new.isDigest) add("isDigest:${old.isDigest}→${new.isDigest}")
                if (old.lastReplyAuthor != new.lastReplyAuthor) add("lastReply:${old.lastReplyAuthor}→${new.lastReplyAuthor}")
                if (old.lastReplyTime != new.lastReplyTime) add("lastTime:${old.lastReplyTime}→${new.lastReplyTime}")
            }
        }
    )
}

private fun logReplyDiff(oldReplies: List<me.jbusdriver.modern.domain.model.ForumReply>, newReplies: List<me.jbusdriver.modern.domain.model.ForumReply>, context: String) {
    me.jbusdriver.modern.core.logListDiff(
        oldItems = oldReplies,
        newItems = newReplies,
        context = context,
        tag = TAG,
        keySelector = { it.floor },
        describe = { "floor=${it.floor}" },
        diffFields = { old, new ->
            buildList {
                if (old.author != new.author) add("author:${old.author}→${new.author}")
                if (old.postTime != new.postTime) add("postTime:${old.postTime}→${new.postTime}")
                if (old.isPinned != new.isPinned) add("isPinned:${old.isPinned}→${new.isPinned}")
            }
        }
    )
}

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
            repository.observeForumBoards(revalidate = false)
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
                                    isRevalidating = event.entry.isExpired,
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

    fun revalidate() {
        if (_uiState.value.groups.isNotEmpty()) loadBoards()
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
        logThreadDiff(_uiState.value.threads, pending.threads, "ThreadList.applyPending")
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
            repository.observeThreads(fid, 1, _uiState.value.currentTypeId, revalidate = false)
                .collect { event ->
                    when (event) {
                        is CachedLoadEvent.Cached -> {
                            hasContent = true
                            KLog.d("[CacheTest] ThreadList CACHED", TAG)
                            _uiState.update {
                                it.copy(
                                    threads = event.entry.value.threads,
                                    pageInfo = event.entry.value.pageInfo,
                                    typeFilters = event.entry.value.typeFilters.ifEmpty { it.typeFilters },
                                    isLoading = false,
                                    isRevalidating = event.entry.isExpired,
                                    lastUpdatedAtMillis = event.entry.storedAtMillis
                                )
                            }
                        }
                        is CachedLoadEvent.Fresh -> {
                            KLog.d("[CacheTest] ThreadList FRESH, isAtTop=$isAtTopForFreshUpdates", TAG)
                            val fresh = event.entry.value.copy(
                                threads = event.entry.value.threads.simulateCacheRefreshChange()
                            )
                            val oldThreads = _uiState.value.threads
                            val oldFirstPage = oldThreads.take(fresh.threads.size)
                            val hasChanged = oldFirstPage != fresh.threads
                            logThreadDiff(oldFirstPage, fresh.threads, "ThreadList.loadFirstPage")
                            if (isAtTopForFreshUpdates) {
                                _uiState.update {
                                    it.copy(
                                        threads = fresh.threads,
                                        pageInfo = fresh.pageInfo,
                                        typeFilters = fresh.typeFilters,
                                        hasMore = fresh.pageInfo.hasNext,
                                        isLoading = false,
                                        isRevalidating = false,
                                        pendingFreshThreads = null,
                                        refreshMessage = null,
                                        lastUpdatedAtMillis = event.entry.storedAtMillis
                                    )
                                }
                            } else if (hasChanged) {
                                _uiState.update {
                                    it.copy(
                                        isLoading = false,
                                        isRevalidating = false,
                                        pendingFreshThreads = fresh,
                                        refreshMessage = "有新數據"
                                    )
                                }
                                KLog.d("[CacheTest] ThreadList: PENDING fresh set", TAG)
                            } else {
                                _uiState.update { it.copy(isLoading = false, isRevalidating = false) }
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

    fun revalidate() {
        val state = _uiState.value
        if (state.threads.isEmpty() || state.isLoading || state.isRevalidating || state.isRefreshing) return
        viewModelScope.launch {
            _uiState.update { it.copy(isRevalidating = true) }
            repository.observeThreads(fid, 1, state.currentTypeId, revalidate = false)
                .collect { event ->
                    when (event) {
                        is CachedLoadEvent.Cached -> {
                            _uiState.update { it.copy(isRevalidating = event.entry.isExpired) }
                        }
                        is CachedLoadEvent.Fresh -> {
                            val fresh = event.entry.value.copy(
                                threads = event.entry.value.threads.simulateCacheRefreshChange()
                            )
                            val oldThreads = _uiState.value.threads
                            val oldFirstPage = oldThreads.take(fresh.threads.size)
                            val hasChanged = oldFirstPage != fresh.threads
                            logThreadDiff(oldFirstPage, fresh.threads, "ThreadList.revalidate")
                            if (isAtTopForFreshUpdates) {
                                currentPage = 1
                                _uiState.update {
                                    it.copy(
                                        threads = fresh.threads,
                                        pageInfo = fresh.pageInfo,
                                        typeFilters = fresh.typeFilters,
                                        hasMore = fresh.pageInfo.hasNext,
                                        isRevalidating = false,
                                        pendingFreshThreads = null,
                                        refreshMessage = null,
                                        lastUpdatedAtMillis = event.entry.storedAtMillis
                                    )
                                }
                            } else if (hasChanged) {
                                _uiState.update {
                                    it.copy(
                                        isRevalidating = false,
                                        pendingFreshThreads = fresh,
                                        refreshMessage = "有新數據"
                                    )
                                }
                            } else {
                                _uiState.update { it.copy(isRevalidating = false) }
                            }
                        }
                        is CachedLoadEvent.Failure -> {
                            _uiState.update { it.copy(isRevalidating = false) }
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
        isAtTopForFreshUpdates = true
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
            repository.observeThreadDetail(tid, currentPage, floorOrder, forceRefresh = forceRefresh, revalidate = false)
                .collect { event ->
                    when (event) {
                        is CachedLoadEvent.Cached -> {
                            hasContent = true
                            KLog.d("[CacheTest] Detail CACHED", TAG)
                            _uiState.update {
                                it.copy(
                                    detail = event.entry.value,
                                    isLoading = false,
                                    isRevalidating = event.entry.isExpired,
                                    isChangingFloorOrder = false,
                                    lastUpdatedAtMillis = event.entry.storedAtMillis
                                )
                            }
                        }
                        is CachedLoadEvent.Fresh -> {
                            KLog.d("[CacheTest] Detail FRESH, isAtTop=$isAtTopForFreshUpdates", TAG)
                            val fresh = event.entry.value.copy(
                                replies = event.entry.value.replies.simulateCacheRefreshChange()
                            )
                            val oldDetail = _uiState.value.detail
                            val hasDetailChanged = if (oldDetail != null) {
                                // Compare non-paged fields + first-page replies only
                                val headerChanged = oldDetail.title != fresh.title ||
                                    oldDetail.viewCount != fresh.viewCount ||
                                    oldDetail.replyCount != fresh.replyCount ||
                                    oldDetail.typeName != fresh.typeName ||
                                    oldDetail.contentBlocks != fresh.contentBlocks
                                val oldFirstPageReplies = oldDetail.replies.take(fresh.replies.size)
                                headerChanged || oldFirstPageReplies != fresh.replies
                            } else true
                            if (oldDetail != null) {
                                logReplyDiff(oldDetail.replies.take(fresh.replies.size), fresh.replies, "Detail.loadDetail")
                                if (oldDetail.viewCount != fresh.viewCount || oldDetail.replyCount != fresh.replyCount) {
                                    KLog.d("[Detail.loadDetail] viewCount:${oldDetail.viewCount}→${fresh.viewCount}, replyCount:${oldDetail.replyCount}→${fresh.replyCount}", TAG)
                                }
                            }
                            if (isAtTopForFreshUpdates || forceRefresh || !showLoading) {
                                _uiState.update {
                                    it.copy(
                                        detail = fresh,
                                        isLoading = false,
                                        isRevalidating = false,
                                        isChangingFloorOrder = false,
                                        pendingFreshDetail = null,
                                        lastUpdatedAtMillis = event.entry.storedAtMillis
                                    )
                                }
                            } else if (hasDetailChanged) {
                                _uiState.update {
                                    it.copy(
                                        isLoading = false,
                                        isRevalidating = false,
                                        isChangingFloorOrder = false,
                                        pendingFreshDetail = fresh,
                                        refreshMessage = "有新數據"
                                    )
                                }
                                KLog.d("[CacheTest] Detail: PENDING fresh set", TAG)
                            } else {
                                _uiState.update {
                                    it.copy(isLoading = false, isRevalidating = false, isChangingFloorOrder = false)
                                }
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

    fun revalidate() {
        val state = _uiState.value
        if (state.detail == null || state.isLoading || state.isRevalidating || state.isRefreshing) return
        viewModelScope.launch {
            _uiState.update { it.copy(isRevalidating = true) }
            repository.observeThreadDetail(tid, 1, state.floorOrder, revalidate = false)
                .collect { event ->
                    when (event) {
                        is CachedLoadEvent.Cached -> {
                            _uiState.update { it.copy(isRevalidating = event.entry.isExpired) }
                        }
                        is CachedLoadEvent.Fresh -> {
                            val fresh = event.entry.value.copy(
                                replies = event.entry.value.replies.simulateCacheRefreshChange()
                            )
                            val oldDetail = _uiState.value.detail
                            val hasDetailChanged = if (oldDetail != null) {
                                val headerChanged = oldDetail.title != fresh.title ||
                                    oldDetail.viewCount != fresh.viewCount ||
                                    oldDetail.replyCount != fresh.replyCount ||
                                    oldDetail.typeName != fresh.typeName ||
                                    oldDetail.contentBlocks != fresh.contentBlocks
                                val oldFirstPageReplies = oldDetail.replies.take(fresh.replies.size)
                                headerChanged || oldFirstPageReplies != fresh.replies
                            } else true
                            if (oldDetail != null) {
                                logReplyDiff(oldDetail.replies.take(fresh.replies.size), fresh.replies, "Detail.revalidate")
                                if (oldDetail.viewCount != fresh.viewCount || oldDetail.replyCount != fresh.replyCount) {
                                    KLog.d("[Detail.revalidate] viewCount:${oldDetail.viewCount}→${fresh.viewCount}, replyCount:${oldDetail.replyCount}→${fresh.replyCount}", TAG)
                                }
                            }
                            if (isAtTopForFreshUpdates) {
                                currentPage = 1
                                _uiState.update {
                                    it.copy(
                                        detail = fresh,
                                        isRevalidating = false,
                                        pendingFreshDetail = null,
                                        refreshMessage = null,
                                        lastUpdatedAtMillis = event.entry.storedAtMillis
                                    )
                                }
                            } else if (hasDetailChanged) {
                                _uiState.update {
                                    it.copy(
                                        isRevalidating = false,
                                        pendingFreshDetail = fresh,
                                        refreshMessage = "有新數據"
                                    )
                                }
                            } else {
                                _uiState.update { it.copy(isRevalidating = false) }
                            }
                        }
                        is CachedLoadEvent.Failure -> {
                            _uiState.update { it.copy(isRevalidating = false) }
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
        val oldDetail = _uiState.value.detail
        if (oldDetail != null) {
            logReplyDiff(oldDetail.replies, pending.replies, "Detail.applyPending")
            if (oldDetail.viewCount != pending.viewCount || oldDetail.replyCount != pending.replyCount) {
                KLog.d("[Detail.applyPending] viewCount:${oldDetail.viewCount}→${pending.viewCount}, replyCount:${oldDetail.replyCount}→${pending.replyCount}", TAG)
            }
        }
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

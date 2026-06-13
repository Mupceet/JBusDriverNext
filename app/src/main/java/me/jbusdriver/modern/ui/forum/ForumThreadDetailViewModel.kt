package me.jbusdriver.modern.ui.forum

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.assisted.Assisted
import dagger.assisted.AssistedFactory
import dagger.assisted.AssistedInject
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import me.jbusdriver.R
import me.jbusdriver.modern.KLog
import me.jbusdriver.modern.core.cache.CachedLoadEvent
import me.jbusdriver.modern.core.cache.simulateCacheRefreshChange
import me.jbusdriver.modern.data.ForumFloorOrder
import me.jbusdriver.modern.data.ForumRepository
import me.jbusdriver.modern.domain.model.ForumThreadDetail
import me.jbusdriver.modern.ui.RouteForumThreadDetail

private const val TAG = "ForumVM"

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

data class ForumThreadDetailUiState(
    val detail: ForumThreadDetail? = null,
    val floorOrder: ForumFloorOrder = ForumFloorOrder.REGULAR,
    val isLoading: Boolean = false,
    val isRefreshing: Boolean = false,
    val isRevalidating: Boolean = false,
    val lastUpdatedAtMillis: Long? = null,
    val pendingFreshDetail: ForumThreadDetail? = null,
    val refreshMessage: Int? = null,
    val error: Int? = null,
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
                                        refreshMessage = R.string.new_data_available
                                    )
                                }
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
                                    it.copy(isLoading = false, isRevalidating = false, isChangingFloorOrder = false, error = R.string.load_failed)
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
                                        refreshMessage = R.string.new_data_available
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
                                    error = if (it.detail == null) R.string.load_failed else it.error,
                                    refreshMessage = if (it.detail != null) R.string.refresh_failed else null
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

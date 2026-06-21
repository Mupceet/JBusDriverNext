package me.jbusdriver.modern.ui.forum

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.assisted.Assisted
import dagger.assisted.AssistedFactory
import dagger.assisted.AssistedInject
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import me.jbusdriver.R
import me.jbusdriver.modern.KLog
import me.jbusdriver.modern.core.cache.CachedLoadEvent
import me.jbusdriver.modern.core.cache.FreshRevalidateOutcome
import me.jbusdriver.modern.core.cache.simulateCacheRefreshChange
import me.jbusdriver.modern.core.site.SiteConfig
import me.jbusdriver.modern.data.settings.ForumFloorOrder
import me.jbusdriver.modern.data.repository.ForumRepository
import me.jbusdriver.modern.domain.model.Comment
import me.jbusdriver.modern.domain.model.ContentBlock
import me.jbusdriver.modern.domain.model.ForumThreadDetail
import me.jbusdriver.modern.domain.model.PageInfo
import me.jbusdriver.modern.domain.model.hasNext
import me.jbusdriver.modern.ui.RouteForumThreadDetail

private const val TAG = "ForumVM"

private fun logReplyDiff(
    oldReplies: List<me.jbusdriver.modern.domain.model.ForumReply>,
    newReplies: List<me.jbusdriver.modern.domain.model.ForumReply>,
    context: String
) {
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

data class FloorCommentSheetState(
    val pid: Int,
    val floor: Int?,
    val floorLabel: String,
    val author: String,
    val contentBlocks: List<ContentBlock>,
    val comments: List<Comment>,
    val pageInfo: PageInfo,
    val isLoadingMore: Boolean = false,
    val error: Int? = null
)

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
    val isChangingFloorOrder: Boolean = false,
    val commentSheet: FloorCommentSheetState? = null
)

private data class DetailRequestIdentity(
    val tid: Int,
    val page: Int,
    val floorOrder: ForumFloorOrder
)

fun ForumThreadDetailUiState.prepareFloorOrderReload(order: ForumFloorOrder): ForumThreadDetailUiState =
    copy(
        floorOrder = order,
        error = null,
        isLoading = false,
        isRefreshing = false,
        isRevalidating = false,
        isLoadingMore = false,
        isChangingFloorOrder = true
    )

@HiltViewModel(assistedFactory = ForumThreadDetailViewModel.Factory::class)
class ForumThreadDetailViewModel @AssistedInject constructor(
    private val repository: ForumRepository,
    private val forumSettingsReader: me.jbusdriver.modern.data.settings.ForumSettingsReader,
    private val loadedGifTracker: me.jbusdriver.modern.data.session.LoadedGifTracker,
    private val siteConfig: SiteConfig,
    @Assisted private val navKey: RouteForumThreadDetail
) : ViewModel() {
    private val tid: Int = navKey.tid
    val shareThreadUrl: String
        get() = siteConfig.resolve("/forum/forum.php?mod=viewthread&tid=$tid")
    private var currentPage = 1
    private var requestGeneration = 0L
    private var activeIdentity: DetailRequestIdentity? = null
    private var commentSheetGeneration = 0L

    private val _uiState = MutableStateFlow(ForumThreadDetailUiState())
    val uiState: StateFlow<ForumThreadDetailUiState> = _uiState.asStateFlow()

    private val _loadedGifUrls = MutableStateFlow<Set<String>>(emptySet())
    val loadedGifUrlsFlow: StateFlow<Set<String>> = _loadedGifUrls
    val loadedGifUrls: Set<String> get() = _loadedGifUrls.value
    val autoLoadGifs: StateFlow<Boolean> = forumSettingsReader.autoLoadGifs

    private fun beginRequest(identity: DetailRequestIdentity): Long {
        requestGeneration += 1
        activeIdentity = identity
        return requestGeneration
    }

    private fun isCurrent(generation: Long, identity: DetailRequestIdentity): Boolean =
        generation == requestGeneration && activeIdentity == identity

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
        val page = currentPage
        val identity = DetailRequestIdentity(tid, page, floorOrder)
        val generation = beginRequest(identity)
        KLog.d("[Forum] loadDetail: tid=$tid, page=$page, floorOrder=$floorOrder", TAG)
        viewModelScope.launch {
            var hasContent = _uiState.value.detail != null
            _uiState.update {
                when {
                    showLoading && !hasContent -> it.copy(
                        isLoading = true,
                        error = null,
                        refreshMessage = null
                    )

                    showLoading -> it.copy(
                        isRevalidating = true,
                        error = null,
                        refreshMessage = null
                    )

                    else -> it.copy(
                        error = null,
                        isChangingFloorOrder = true,
                        refreshMessage = null
                    )
                }
            }
            repository.observeThreadDetail(
                tid,
                page,
                floorOrder,
                forceRefresh = forceRefresh,
                revalidate = false
            )
                .collect { event ->
                    if (!isCurrent(generation, identity)) return@collect
                    when (event) {
                        is CachedLoadEvent.Cached -> {
                            hasContent = true
                            _uiState.update { it.applyLoadDetailCached(event.entry) }
                        }

                        is CachedLoadEvent.Fresh -> {
                            val fresh = event.entry.value.copy(
                                replies = event.entry.value.replies.simulateCacheRefreshChange()
                            )
                            val oldDetail = _uiState.value.detail
                            if (oldDetail != null) {
                                logReplyDiff(
                                    oldDetail.replies.take(fresh.replies.size),
                                    fresh.replies,
                                    "Detail.loadDetail"
                                )
                                if (oldDetail.viewCount != fresh.viewCount || oldDetail.replyCount != fresh.replyCount) {
                                    KLog.d(
                                        "[Detail.loadDetail] viewCount:${oldDetail.viewCount}→${fresh.viewCount}, replyCount:${oldDetail.replyCount}→${fresh.replyCount}",
                                        TAG
                                    )
                                }
                            }
                            val reduction = _uiState.value.applyLoadDetailFresh(
                                event.entry.copy(value = fresh),
                                isAtTop = isAtTopForFreshUpdates,
                                forceApply = forceRefresh || !showLoading
                            )
                            _uiState.value = reduction.state
                        }

                        is CachedLoadEvent.Failure -> {
                            _uiState.update { it.applyLoadDetailFailure(event, hasContent) }
                        }
                    }
                }
        }
    }

    fun revalidate() {
        val state = _uiState.value
        if (state.detail == null || state.isLoading || state.isRevalidating || state.isRefreshing) return
        val identity = DetailRequestIdentity(tid, 1, state.floorOrder)
        val generation = beginRequest(identity)
        viewModelScope.launch {
            _uiState.update { it.copy(isRevalidating = true) }
            repository.observeThreadDetail(tid, 1, state.floorOrder, revalidate = false)
                .collect { event ->
                    if (!isCurrent(generation, identity)) return@collect
                    when (event) {
                        is CachedLoadEvent.Cached -> {
                            _uiState.update { it.copy(isRevalidating = event.entry.isExpired) }
                        }

                        is CachedLoadEvent.Fresh -> {
                            val fresh = event.entry.value.copy(
                                replies = event.entry.value.replies.simulateCacheRefreshChange()
                            )
                            val oldDetail = _uiState.value.detail
                            if (oldDetail != null) {
                                logReplyDiff(
                                    oldDetail.replies.take(fresh.replies.size),
                                    fresh.replies,
                                    "Detail.revalidate"
                                )
                                if (oldDetail.viewCount != fresh.viewCount || oldDetail.replyCount != fresh.replyCount) {
                                    KLog.d(
                                        "[Detail.revalidate] viewCount:${oldDetail.viewCount}→${fresh.viewCount}, replyCount:${oldDetail.replyCount}→${fresh.replyCount}",
                                        TAG
                                    )
                                }
                            }
                            val reduction = _uiState.value.applyDetailRevalidateFresh(
                                event.entry.copy(value = fresh),
                                isAtTop = isAtTopForFreshUpdates
                            )
                            if (reduction.outcome == FreshRevalidateOutcome.ApplyImmediately) {
                                currentPage = 1
                            }
                            _uiState.value = reduction.state
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
        val identity = DetailRequestIdentity(tid, 1, floorOrder)
        val generation = beginRequest(identity)
        viewModelScope.launch {
            _uiState.update {
                it.copy(
                    isRefreshing = true,
                    error = null,
                    refreshMessage = null,
                    pendingFreshDetail = null
                )
            }
            repository.observeThreadDetail(
                tid,
                1,
                floorOrder,
                forceRefresh = true,
                revalidate = false
            )
                .collect { event ->
                    if (!isCurrent(generation, identity)) return@collect
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
        val identity = DetailRequestIdentity(tid, nextPage, floorOrder)
        val generation = beginRequest(identity)
        viewModelScope.launch {
            _uiState.update { it.copy(isLoadingMore = true) }
            try {
                val nextDetail = repository.loadThreadDetail(tid, nextPage, floorOrder)
                if (!isCurrent(generation, identity)) return@launch
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
                if (e is CancellationException) throw e
                if (!isCurrent(generation, identity)) return@launch
                currentPage = detail.pageInfo.activePage
                _uiState.update { it.copy(isLoadingMore = false) }
            }
        }
    }

    fun openFirstPostCommentsSheet() {
        val detail = _uiState.value.detail ?: return
        if (detail.pid == 0) return
        commentSheetGeneration += 1
        _uiState.update {
            it.copy(
                commentSheet = FloorCommentSheetState(
                    pid = detail.pid,
                    floor = null,
                    floorLabel = "楼主",
                    author = detail.author,
                    contentBlocks = detail.contentBlocks,
                    comments = detail.comments,
                    pageInfo = detail.commentPageInfo
                )
            )
        }
    }

    fun openReplyCommentsSheet(floor: Int) {
        val reply = _uiState.value.detail?.replies?.firstOrNull { it.floor == floor } ?: return
        if (reply.pid == 0) return
        commentSheetGeneration += 1
        _uiState.update {
            it.copy(
                commentSheet = FloorCommentSheetState(
                    pid = reply.pid,
                    floor = reply.floor,
                    floorLabel = "${reply.floor}楼",
                    author = reply.author,
                    contentBlocks = reply.contentBlocks,
                    comments = reply.comments,
                    pageInfo = reply.commentPageInfo
                )
            )
        }
    }

    fun dismissCommentsSheet() {
        commentSheetGeneration += 1
        _uiState.update { it.copy(commentSheet = null) }
    }

    fun loadMoreFloorComments() {
        val sheet = _uiState.value.commentSheet ?: return
        if (sheet.isLoadingMore || !sheet.pageInfo.hasNext) return
        val pid = sheet.pid
        val nextPage = sheet.pageInfo.nextPage
        val generation = commentSheetGeneration
        viewModelScope.launch {
            var shouldLoad = false
            _uiState.update {
                val current = it.commentSheet ?: return@update it
                if (
                    generation != commentSheetGeneration ||
                    current.pid != pid ||
                    current.isLoadingMore ||
                    current.pageInfo.nextPage != nextPage ||
                    !current.pageInfo.hasNext
                ) {
                    it
                } else {
                    shouldLoad = true
                    it.copy(commentSheet = current.copy(isLoadingMore = true, error = null))
                }
            }
            if (!shouldLoad) return@launch

            try {
                val result = repository.loadFloorComments(tid, pid, nextPage)
                _uiState.update {
                    val current = it.commentSheet ?: return@update it
                    if (
                        generation != commentSheetGeneration ||
                        current.pid != pid ||
                        current.pageInfo.nextPage != nextPage
                    ) {
                        it
                    } else {
                        it.copy(
                            commentSheet = current.copy(
                                comments = current.comments + result.comments,
                                pageInfo = result.pageInfo,
                                isLoadingMore = false,
                                error = null
                            )
                        )
                    }
                }
            } catch (e: Exception) {
                if (e is CancellationException) throw e
                _uiState.update {
                    val current = it.commentSheet ?: return@update it
                    if (
                        generation != commentSheetGeneration ||
                        current.pid != pid ||
                        current.pageInfo.nextPage != nextPage
                    ) {
                        it
                    } else {
                        it.copy(
                            commentSheet = current.copy(
                                isLoadingMore = false,
                                error = R.string.load_failed
                            )
                        )
                    }
                }
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
                KLog.d(
                    "[Detail.applyPending] viewCount:${oldDetail.viewCount}→${pending.viewCount}, replyCount:${oldDetail.replyCount}→${pending.replyCount}",
                    TAG
                )
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

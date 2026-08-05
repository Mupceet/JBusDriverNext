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
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
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
    private val gifCacheReader: me.jbusdriver.modern.data.session.GifCacheReader,
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
    val autoLoadGifs: StateFlow<Boolean> = forumSettingsReader.autoLoadGifs

    /** 历史加载过（DataStore 持久化）的 GIF，仅用于和磁盘缓存做交集确认。 */
    private var persistedGifUrls: Set<String> = emptySet()
    /** 本会话内用户点击加载过的 GIF，始终直接展示（正在写入缓存，无需再次确认）。 */
    private val sessionTappedGifs = mutableSetOf<String>()
    /** 防止延迟到达的旧重算结果覆盖新结果。 */
    private var loadedGifsGeneration = 0L

    private fun beginRequest(identity: DetailRequestIdentity): Long {
        requestGeneration += 1
        activeIdentity = identity
        return requestGeneration
    }

    private fun isCurrent(generation: Long, identity: DetailRequestIdentity): Boolean =
        generation == requestGeneration && activeIdentity == identity

    fun onLoadGif(url: String) {
        if (sessionTappedGifs.add(url)) {
            _loadedGifUrls.update { it + url }
        }
        viewModelScope.launch { persistGifUrls(setOf(url)) }
    }

    fun onLoadAllGifs() {
        val detail = _uiState.value.detail ?: return
        val unloaded = collectThreadGifUrls(detail) - _loadedGifUrls.value
        if (unloaded.isEmpty()) return
        sessionTappedGifs += unloaded
        _loadedGifUrls.update { it + unloaded }
        viewModelScope.launch { persistGifUrls(unloaded) }
    }

    /**
     * 重算并刷新暴露给 UI 的"已加载 GIF"集合：
     * 本会话点击过的 ∪ (历史加载过 ∩ 本帖 GIF ∩ 磁盘缓存仍在)。
     *
     * 历史记录里"加载过"但磁盘缓存已被 LRU 淘汰的 GIF 会被剔除，回退为占位等待用户再次点击，
     * 避免直接渲染触发网络重下载而浪费流量。
     */
    private fun recomputeLoadedGifs() {
        val detail = _uiState.value.detail ?: return
        val candidates = collectThreadGifUrls(detail) intersect persistedGifUrls
        val generation = ++loadedGifsGeneration
        viewModelScope.launch {
            val cached = if (candidates.isEmpty()) {
                emptySet()
            } else {
                gifCacheReader.presentInDiskCache(candidates)
            }
            if (generation != loadedGifsGeneration) return@launch
            _loadedGifUrls.value = sessionTappedGifs + cached
        }
    }

    private fun collectThreadGifUrls(detail: ForumThreadDetail): Set<String> =
        (detail.contentBlocks.asSequence() + detail.replies.asSequence().flatMap { it.contentBlocks.asSequence() })
            .filterIsInstance<ContentBlock.Image>()
            .filter { it.isGif }
            .map { it.url }
            .toSet()

    private suspend fun loadPersistedGifUrls(): Set<String> = loadedGifTracker.loadedUrls()

    private suspend fun persistGifUrls(urls: Set<String>) {
        for (url in urls) loadedGifTracker.markLoaded(url)
    }

    init {
        KLog.d("[Forum] ForumThreadDetailViewModel init: tid=$tid", TAG)
        viewModelScope.launch {
            persistedGifUrls = loadPersistedGifUrls()
            recomputeLoadedGifs()
        }
        // 首次加载 / 刷新 / 翻页合并都会替换 detail，触发重算"已加载且磁盘仍在"的 GIF 集合。
        viewModelScope.launch {
            _uiState
                .map { it.detail }
                .distinctUntilChanged()
                .collect { detail -> if (detail != null) recomputeLoadedGifs() }
        }
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
        _uiState.update { it.copy(isLoadingMore = true) }
        viewModelScope.launch {
            try {
                val nextDetail = repository.loadThreadDetail(tid, nextPage, floorOrder)
                if (!isCurrent(generation, identity)) return@launch
                // 置顶/广播楼层会在每一页重复出现，直接拼接会产生重复 floor，
                // 导致 LazyColumn 的 key 冲突崩溃。按 floor 去重，保留已展示的首现版本。
                val existingFloors = detail.replies.mapTo(HashSet()) { it.floor }
                val mergedReplies =
                    detail.replies + nextDetail.replies.filter { it.floor !in existingFloors }
                _uiState.update {
                    it.copy(
                        detail = detail.copy(
                            replies = mergedReplies,
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

package me.jbusdriver.modern.ui.forum

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.assisted.Assisted
import dagger.assisted.AssistedFactory
import dagger.assisted.AssistedInject
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
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
import me.jbusdriver.modern.domain.model.ForumLoginState
import me.jbusdriver.modern.domain.model.ForumThreadDetail
import me.jbusdriver.modern.domain.model.ForumUserPermissions
import me.jbusdriver.modern.domain.model.PageInfo
import me.jbusdriver.modern.domain.model.hasNext
import me.jbusdriver.modern.domain.model.normalizeMovieCode
import me.jbusdriver.modern.ui.RouteForumThreadDetail
import me.jbusdriver.modern.ui.UserMessage

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
    /** 是否只看楼主（authorid 过滤）。 */
    val showAuthorOnly: Boolean = false,
    /** 全部评论计数：加载“全部”页后由回复数得到，加载完成前为 null。 */
    val allReplyCount: Int? = null,
    /** 只看楼主计数：加载 authorid 页后由楼主帖子数得到，加载完成前为 null。 */
    val authorReplyCount: Int? = null,
    val isLoading: Boolean = false,
    val isRefreshing: Boolean = false,
    val isRevalidating: Boolean = false,
    val lastUpdatedAtMillis: Long? = null,
    val pendingFreshDetail: ForumThreadDetail? = null,
    val error: Int? = null,
    val isLoadingMore: Boolean = false,
    /** 引用楼层定位中（findpost 拉取目标页并合并）。 */
    val isLocatingFloor: Boolean = false,
    /** 正在补齐的楼层断层的下沿楼层号（断档分隔行点击后循环加载中）。 */
    val gapLoadingBelowFloor: Int? = null,
    val isChangingFloorOrder: Boolean = false,
    val isChangingAuthorFilter: Boolean = false,
    val commentSheet: FloorCommentSheetState? = null,
    /** 已登录（写操作前置；匿名点击收藏/点赞由 UI 引导全局登录弹窗）。 */
    val isLoggedIn: Boolean = false,
    /** 等级门控（内置表）：可回帖才显示底栏回复入口；匿名恒为 false。 */
    val canReply: Boolean = false,
    /** 本会话已收藏（本地收藏库命中或收藏成功后置位）。 */
    val isFavorited: Boolean = false,
    /** 收藏后台校验中（提交锁，静默拦截并发点击；乐观状态已生效，UI 无须区分）。 */
    val isSubmittingAction: Boolean = false
)

/** [ForumThreadDetailViewModel.locateQuotedFloor] 的结果。 */
enum class FloorLocateResult {
    /** 目标已在列表中（含本次合并后命中），可定位滚动。 */
    Located,

    /** 无法定位（接口失败/落地页无目标/只看楼主过滤中），调用方回退浏览器。 */
    NotFound,

    /** 已有定位在途或期间状态被替换，静默放弃。 */
    Superseded
}

private data class DetailRequestIdentity(
    val tid: Int,
    val page: Int,
    val floorOrder: ForumFloorOrder,
    val authorUid: Int?
)

fun ForumThreadDetailUiState.prepareFloorOrderReload(order: ForumFloorOrder): ForumThreadDetailUiState =
    copy(
        floorOrder = order,
        error = null,
        isLoading = false,
        isRefreshing = false,
        isRevalidating = false,
        isLoadingMore = false,
        isChangingFloorOrder = true,
        isChangingAuthorFilter = false
    )

/**
 * 根据刚加载的详情维护“全部评论 / 只看楼主”计数：
 * - 全部页：计数来自页面回复数（[ForumThreadDetail.replyCount]）；
 * - 只看楼主页：计数 = 楼主首帖 + 该页解析出的楼主回复数。
 */
private fun ForumThreadDetailUiState.withFilterCounts(authorUid: Int?): ForumThreadDetailUiState {
    val current = detail ?: return this
    return copy(
        allReplyCount = if (authorUid == null) current.replyCount else allReplyCount,
        authorReplyCount = if (authorUid != null) 1 + current.replies.size else authorReplyCount,
        isChangingAuthorFilter = false
    )
}

@HiltViewModel(assistedFactory = ForumThreadDetailViewModel.Factory::class)
class ForumThreadDetailViewModel @AssistedInject constructor(
    private val repository: ForumRepository,
    private val forumSettingsReader: me.jbusdriver.modern.data.settings.ForumSettingsReader,
    private val loadedGifTracker: me.jbusdriver.modern.data.session.LoadedGifTracker,
    private val gifCacheReader: me.jbusdriver.modern.data.session.GifCacheReader,
    private val siteConfig: SiteConfig,
    @Assisted private val navKey: RouteForumThreadDetail,
    private val savedStateHandle: SavedStateHandle
) : ViewModel() {
    private val tid: Int = navKey.tid
    val shareThreadUrl: String
        get() = siteConfig.resolve("/forum/forum.php?mod=viewthread&tid=$tid")

    /** 帖子中识别出的番号 → 影片详情页绝对 URL（`{baseUrl}/{番号}`，规范化补连字符/大写）。 */
    fun movieUrlForCode(code: String): String =
        siteConfig.resolve("/" + (normalizeMovieCode(code) ?: code.trim().uppercase()))
    private var currentPage = 1
    private var requestGeneration = 0L
    private var activeIdentity: DetailRequestIdentity? = null
    private var commentSheetGeneration = 0L

    private val _uiState = MutableStateFlow(ForumThreadDetailUiState())
    val uiState: StateFlow<ForumThreadDetailUiState> = _uiState.asStateFlow()

    /** 一次性用户消息（Snackbar/Toast），UI 展示后即视为消费 */
    private val _messages = MutableSharedFlow<UserMessage>(extraBufferCapacity = 4)
    val messages: SharedFlow<UserMessage> = _messages.asSharedFlow()

    private val _loadedGifUrls = MutableStateFlow<Set<String>>(emptySet())
    val loadedGifUrlsFlow: StateFlow<Set<String>> = _loadedGifUrls
    val autoLoadGifs: StateFlow<Boolean> = forumSettingsReader.autoLoadGifs

    /** 历史加载过（DataStore 持久化）的 GIF，仅用于和磁盘缓存做交集确认。 */
    private var persistedGifUrls: Set<String> = emptySet()
    /** 本会话内用户点击加载过的 GIF，始终直接展示（正在写入缓存，无需再次确认）。 */
    private val sessionTappedGifs = mutableSetOf<String>()
    /** 防止延迟到达的旧重算结果覆盖新结果。 */
    private var loadedGifsGeneration = 0L

    /** 已收藏预标记探针只跑一次（登录态就绪后）。 */
    private var favoritesProbeDone = false

    private fun beginRequest(identity: DetailRequestIdentity): Long {
        requestGeneration += 1
        activeIdentity = identity
        return requestGeneration
    }

    /** 楼主 uid（用于 authorid 参数）；是否启用过滤由 [ForumThreadDetailUiState.showAuthorOnly] 决定。 */
    private fun currentAuthorUid(): Int? =
        _uiState.value.detail?.authorUid?.takeIf { it > 0 }

    private fun isCurrent(generation: Long, identity: DetailRequestIdentity): Boolean =
        generation == requestGeneration && activeIdentity == identity

    /** 静默合并楼层增减时的调试日志（楼层内容变化走 StorePending 提示，另有日志）。 */
    private fun logSilentMerge(
        source: String,
        oldDetail: ForumThreadDetail?,
        merged: ForumThreadDetail?
    ) {
        if (oldDetail == null || merged == null) return
        val newCount = merged.replies.size
        if (newCount > oldDetail.replies.size) {
            KLog.d("[Forum] $source 静默合并楼层 tid=$tid：${oldDetail.replies.size}→$newCount", TAG)
        }
    }

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
        // 登录态 + 内置等级权限表驱动底栏写操作入口（匿名 → 隐藏回复入口、写操作引导登录）
        viewModelScope.launch {
            repository.loginState.collect { login ->
                val permissions = ForumUserPermissions.forGroupName(
                    (login as? ForumLoginState.LoggedIn)?.groupName
                )
                _uiState.update {
                    it.copy(
                        isLoggedIn = login is ForumLoginState.LoggedIn,
                        canReply = login is ForumLoginState.LoggedIn && permissions.canReply
                    )
                }
                // 已收藏预标记：登录后查本地收藏库镜像（Room，无网络请求、不限收藏首页），
                // 库内容随个人主页收藏页浏览与收藏操作持续同步
                if (login is ForumLoginState.LoggedIn && !favoritesProbeDone) {
                    favoritesProbeDone = true
                    runCatching {
                        if (repository.isFavoritedThread(tid)) {
                            _uiState.update { it.copy(isFavorited = true) }
                        }
                    } // 探针失败静默：未知态由首次点击的服务端校验收敛
                }
            }
        }
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
            val restoredOrder = savedStateHandle.get<String>(KEY_FLOOR_ORDER)
                ?.let { name -> runCatching { ForumFloorOrder.valueOf(name) }.getOrNull() }
            val restoredAuthorOnly = savedStateHandle.get<Boolean>(KEY_SHOW_AUTHOR_ONLY) ?: false
            _uiState.update {
                it.copy(
                    floorOrder = restoredOrder ?: defaultOrder,
                    showAuthorOnly = restoredAuthorOnly
                )
            }
            loadDetail()
        }
    }

    fun loadDetail(forceRefresh: Boolean = false, showLoading: Boolean = true) {
        if (showLoading && _uiState.value.isLoading) return
        val floorOrder = _uiState.value.floorOrder
        val authorUid = if (_uiState.value.showAuthorOnly) currentAuthorUid() else null
        val page = currentPage
        val identity = DetailRequestIdentity(tid, page, floorOrder, authorUid)
        val generation = beginRequest(identity)
        KLog.d(
            "[Forum] loadDetail: tid=$tid, page=$page, floorOrder=$floorOrder, authorUid=$authorUid",
            TAG
        )
        viewModelScope.launch {
            var hasContent = _uiState.value.detail != null
            _uiState.update {
                when {
                    showLoading && !hasContent -> it.copy(
                        isLoading = true,
                        error = null
                    )

                    showLoading -> it.copy(
                        isRevalidating = true,
                        error = null
                    )

                    else -> it.copy(
                        error = null,
                        isChangingFloorOrder = true
                    )
                }
            }
            repository.observeThreadDetail(
                tid,
                page,
                floorOrder,
                authorUid = authorUid,
                forceRefresh = forceRefresh,
                revalidate = false
            )
                .collect { event ->
                    if (!isCurrent(generation, identity)) return@collect
                    when (event) {
                        is CachedLoadEvent.Cached -> {
                            hasContent = true
                            _uiState.update {
                                it.applyLoadDetailCached(event.entry)
                                    .withFilterCounts(authorUid)
                            }
                        }

                        is CachedLoadEvent.Fresh -> {
                            val fresh = event.entry.value.copy(
                                replies = event.entry.value.replies.simulateCacheRefreshChange()
                            )
                            val oldDetail = _uiState.value.detail
                            if (oldDetail != null) {
                                // 按楼层号对齐（与 detailChangeReason 一致），纯新增楼层不再显示为逐行内容变化
                                val freshFloors = fresh.replies.mapTo(HashSet()) { it.floor }
                                logReplyDiff(
                                    oldDetail.replies.filter { it.floor in freshFloors },
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
                                forceApply = forceRefresh || !showLoading,
                                hasLoadedBeyondFirstPage = currentPage > 1
                            )
                            if (reduction.outcome == FreshRevalidateOutcome.StorePending) {
                                KLog.d("[Forum] loadDetail 有新数据提示 tid=$tid：${reduction.changeReason ?: "未知"}", TAG)
                            } else if (reduction.outcome == FreshRevalidateOutcome.ApplyInPlace) {
                                logSilentMerge("loadDetail", oldDetail, reduction.state.detail)
                            }
                            _uiState.value = reduction.state.withFilterCounts(authorUid)
                            if (reduction.outcome == FreshRevalidateOutcome.StorePending) {
                                _messages.emit(UserMessage(R.string.new_data_available))
                            }
                        }

                        is CachedLoadEvent.Failure -> {
                            _uiState.update {
                                it.applyLoadDetailFailure(event, hasContent)
                                    .copy(isChangingAuthorFilter = false)
                            }
                        }
                    }
                }
        }
    }

    fun revalidate() {
        val state = _uiState.value
        if (state.detail == null || state.isLoading || state.isRevalidating || state.isRefreshing) return
        val authorUid = if (state.showAuthorOnly) currentAuthorUid() else null
        val identity = DetailRequestIdentity(tid, 1, state.floorOrder, authorUid)
        val generation = beginRequest(identity)
        viewModelScope.launch {
            _uiState.update { it.copy(isRevalidating = true) }
            repository.observeThreadDetail(
                tid,
                1,
                state.floorOrder,
                authorUid = authorUid,
                revalidate = false
            )
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
                                // 按楼层号对齐（与 detailChangeReason 一致），纯新增楼层不再显示为逐行内容变化
                                val freshFloors = fresh.replies.mapTo(HashSet()) { it.floor }
                                logReplyDiff(
                                    oldDetail.replies.filter { it.floor in freshFloors },
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
                                isAtTop = isAtTopForFreshUpdates,
                                hasLoadedBeyondFirstPage = currentPage > 1
                            )
                            if (reduction.outcome == FreshRevalidateOutcome.ApplyImmediately) {
                                currentPage = 1
                            }
                            if (reduction.outcome == FreshRevalidateOutcome.StorePending) {
                                KLog.d("[Forum] revalidate 有新数据提示 tid=$tid：${reduction.changeReason ?: "未知"}", TAG)
                            } else if (reduction.outcome == FreshRevalidateOutcome.ApplyInPlace) {
                                logSilentMerge("revalidate", oldDetail, reduction.state.detail)
                            }
                            _uiState.value = reduction.state
                            if (reduction.outcome == FreshRevalidateOutcome.StorePending) {
                                _messages.emit(UserMessage(R.string.new_data_available))
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
        val authorUid = if (_uiState.value.showAuthorOnly) currentAuthorUid() else null
        currentPage = 1
        val identity = DetailRequestIdentity(tid, 1, floorOrder, authorUid)
        val generation = beginRequest(identity)
        viewModelScope.launch {
            _uiState.update {
                it.copy(
                    isRefreshing = true,
                    error = null,
                    pendingFreshDetail = null
                )
            }
            repository.observeThreadDetail(
                tid,
                1,
                floorOrder,
                authorUid = authorUid,
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
                                ).withFilterCounts(authorUid)
                            }
                        }

                        is CachedLoadEvent.Failure -> {
                            val hadContent = _uiState.value.detail != null
                            _uiState.update {
                                it.copy(
                                    isRefreshing = false,
                                    error = if (it.detail == null) R.string.load_failed else it.error
                                )
                            }
                            if (hadContent) {
                                _messages.emit(UserMessage(R.string.refresh_failed))
                            }
                        }
                    }
                }
        }
    }

    fun loadMoreReplies() {
        if (_uiState.value.detail == null || _uiState.value.isLoadingMore) return
        viewModelScope.launch { loadNextRepliesPage() }
    }

    /**
     * 拉取线性翻页链的下一页并合并。在 update 内读取当前 detail 合并（与引用定位合并并发安全），
     * 按 floor 去重 + 按当前楼层顺序全局排序（保持列表整体有序，断层派生才成立）。
     * 返回 false：无更多页/已有加载在途/加载失败；被更新的请求接管时返回 true 由新请求维护链条。
     */
    private suspend fun loadNextRepliesPage(): Boolean {
        val detail = _uiState.value.detail ?: return false
        if (_uiState.value.isLoadingMore) return false
        val nextPage = detail.pageInfo.nextPage
        if (nextPage <= currentPage) return false

        currentPage = nextPage
        val floorOrder = _uiState.value.floorOrder
        val authorUid = if (_uiState.value.showAuthorOnly) currentAuthorUid() else null
        val identity = DetailRequestIdentity(tid, nextPage, floorOrder, authorUid)
        val generation = beginRequest(identity)
        _uiState.update { it.copy(isLoadingMore = true) }
        return try {
            val nextDetail = repository.loadThreadDetail(
                tid,
                nextPage,
                floorOrder,
                authorUid = authorUid
            )
            if (!isCurrent(generation, identity)) return true
            _uiState.update { state ->
                val current = state.detail
                if (current == null) {
                    state.copy(isLoadingMore = false)
                } else {
                    state.copy(
                        detail = current.copy(
                            replies = mergeReplyPages(current.replies, nextDetail.replies, floorOrder),
                            pageInfo = nextDetail.pageInfo
                        ),
                        isLoadingMore = false
                    )
                }
            }
            true
        } catch (e: Exception) {
            if (e is CancellationException) throw e
            if (isCurrent(generation, identity)) {
                currentPage = detail.pageInfo.activePage
                _uiState.update { it.copy(isLoadingMore = false) }
            }
            false
        }
    }

    /**
     * 引用楼层点击定位：目标不在已加载楼层时，经 findpost 重定向拉取目标楼层所在页并合并
     * （不改 [me.jbusdriver.modern.domain.model.PageInfo]，线性翻页链保持完整，
     * 后续翻页撞到已加载楼层由去重兜底）。调用方随后重新定位滚动。
     */
    suspend fun locateQuotedFloor(pid: Int): FloorLocateResult {
        if (pid <= 0) return FloorLocateResult.NotFound
        val state = _uiState.value
        val detail = state.detail ?: return FloorLocateResult.Superseded
        if (pid == detail.pid || detail.replies.any { it.pid == pid }) return FloorLocateResult.Located
        // 只看楼主：目标页为未过滤数据，合并会破坏过滤语义，交由调用方回退浏览器。
        if (state.showAuthorOnly) return FloorLocateResult.NotFound
        if (state.isLocatingFloor) return FloorLocateResult.Superseded

        _uiState.update { it.copy(isLocatingFloor = true) }
        return try {
            val jumpPage = repository.loadFloorJumpPage(tid, pid, state.floorOrder)
            // 契约上落地页必含目标 pid；防御兜底避免合并无关楼层
            if (jumpPage == null || (jumpPage.pid != pid && jumpPage.replies.none { it.pid == pid })) {
                return FloorLocateResult.NotFound
            }
            var applied = false
            _uiState.update { s ->
                val current = s.detail
                if (current == null || s.showAuthorOnly) {
                    s // 期间被刷新清空/切换过滤，放弃合并
                } else {
                    applied = true
                    s.copy(
                        detail = current.copy(
                            replies = mergeReplyPages(current.replies, jumpPage.replies, s.floorOrder)
                        )
                    )
                }
            }
            if (applied) FloorLocateResult.Located else FloorLocateResult.Superseded
        } catch (e: Exception) {
            if (e is CancellationException) throw e
            KLog.e("[Forum] locateQuotedFloor failed: tid=$tid, pid=$pid, error=${e.message}", TAG)
            FloorLocateResult.NotFound
        } finally {
            _uiState.update { it.copy(isLocatingFloor = false) }
        }
    }

    /**
     * 补齐楼层断层：循环加载线性翻页链下一页，直到 [belowFloor] 断层闭合、
     * 无更多页（剩余断档视为已删楼层）或加载失败（保留分隔行供再次点击）。
     * 同一时刻仅一个补齐任务。
     */
    fun loadGapFloors(belowFloor: Int) {
        if (_uiState.value.detail == null) return
        if (_uiState.value.gapLoadingBelowFloor != null) return
        viewModelScope.launch {
            _uiState.update { it.copy(gapLoadingBelowFloor = belowFloor) }
            try {
                while (true) {
                    val state = _uiState.value
                    val current = state.detail ?: break
                    val gapClosed = floorGaps(current.replies, state.floorOrder)
                        .none { it.belowFloor == belowFloor }
                    if (gapClosed) break
                    if (!current.pageInfo.hasNext) break
                    if (!loadNextRepliesPage()) break
                }
            } finally {
                _uiState.update { it.copy(gapLoadingBelowFloor = null) }
            }
        }
    }

    /** findpost 重定向绝对链接（引用定位失败时的浏览器回退目标）。 */
    fun findPostUrl(pid: Int): String =
        siteConfig.resolve("/forum/forum.php?mod=redirect&goto=findpost&pid=$pid&ptid=$tid")

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

    /**
     * 收藏帖子（只新增，删除走个人主页）：点击即乐观生效（点亮+计数+1，动画反馈），
     * 后台以仓库层服务端定值校验，失败才回滚并提示站点文案；提交锁静默拦截并发。
     */
    fun toggleFavorite() {
        val detail = _uiState.value.detail ?: return
        if (_uiState.value.isSubmittingAction) return
        if (_uiState.value.isFavorited) {
            viewModelScope.launch { _messages.emit(UserMessage(R.string.forum_action_already_favorited)) }
            return
        }
        _uiState.update { state ->
            state.copy(
                isFavorited = true,
                isSubmittingAction = true,
                detail = state.detail?.copy(favoriteCount = state.detail.favoriteCount + 1)
            )
        }
        viewModelScope.launch {
            repository.addFavoriteThread(detail.tid, detail.favoriteActionUrl)
                .onSuccess {
                    // 静默确认：乐观状态即终态
                    _uiState.update { it.copy(isSubmittingAction = false) }
                }
                .onFailure { e ->
                    _uiState.update { state ->
                        state.copy(
                            isFavorited = false,
                            isSubmittingAction = false,
                            detail = state.detail?.copy(
                                favoriteCount = (state.detail.favoriteCount - 1).coerceAtLeast(0)
                            )
                        )
                    }
                    _messages.emit(UserMessage.ofText(e.message ?: "收藏失敗，請稍後重試"))
                }
        }
    }

    fun setFloorOrder(order: ForumFloorOrder) {
        if (_uiState.value.floorOrder == order || _uiState.value.isLoading || _uiState.value.isChangingFloorOrder) return
        savedStateHandle[KEY_FLOOR_ORDER] = order.name
        currentPage = 1
        _uiState.update { it.prepareFloorOrderReload(order) }
        loadDetail(forceRefresh = true, showLoading = false)
    }

    /** 切换“全部评论 / 只看楼主”过滤（authorid 参数），切换后重新加载第 1 页。 */
    fun setAuthorFilter(showAuthorOnly: Boolean) {
        if (
            _uiState.value.showAuthorOnly == showAuthorOnly ||
            _uiState.value.isLoading ||
            _uiState.value.isChangingAuthorFilter
        ) {
            return
        }
        if (showAuthorOnly && currentAuthorUid() == null) return
        savedStateHandle[KEY_SHOW_AUTHOR_ONLY] = showAuthorOnly
        currentPage = 1
        _uiState.update {
            it.copy(
                showAuthorOnly = showAuthorOnly,
                isChangingAuthorFilter = true,
                isChangingFloorOrder = false,
                error = null
            )
        }
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
                lastUpdatedAtMillis = System.currentTimeMillis()
            ).withFilterCounts(
                if (_uiState.value.showAuthorOnly) currentAuthorUid() else null
            )
        }
    }

    private companion object {
        const val KEY_FLOOR_ORDER = "forum_thread_detail_floor_order"
        const val KEY_SHOW_AUTHOR_ONLY = "forum_thread_detail_show_author_only"
    }

    @AssistedFactory
    interface Factory {
        fun create(navKey: RouteForumThreadDetail): ForumThreadDetailViewModel
    }
}

package me.jbusdriver.modern.ui.movielist

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.assisted.Assisted
import dagger.assisted.AssistedFactory
import dagger.assisted.AssistedInject
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import me.jbusdriver.R
import me.jbusdriver.modern.KLog
import me.jbusdriver.modern.core.cache.AtTopGate
import me.jbusdriver.modern.core.cache.CachedLoadEvent
import me.jbusdriver.modern.core.cache.FreshRevalidateOutcome
import me.jbusdriver.modern.core.cache.PageTracker
import me.jbusdriver.modern.data.repository.CollectRepository
import me.jbusdriver.modern.data.repository.LocalVideoRepository
import me.jbusdriver.modern.data.repository.MovieRepository
import me.jbusdriver.modern.domain.model.ActressCategory
import me.jbusdriver.modern.domain.model.ActressInfo
import me.jbusdriver.modern.domain.model.MovieFilterInfo
import me.jbusdriver.modern.domain.model.MoviePageResult
import me.jbusdriver.modern.domain.model.PageInfo
import me.jbusdriver.modern.domain.model.UncensoredActressCategory
import me.jbusdriver.modern.domain.model.hasNext
import me.jbusdriver.modern.ui.MovieUiModel
import me.jbusdriver.modern.ui.RouteLinkMovies
import me.jbusdriver.modern.ui.toUiModel

/**
 * 关联电影列表页的 UI 状态。
 *
 * 包含电影列表数据、分页信息、女优详情数据、收藏状态及各加载/错误标志。
 */
/**
 * VM 解析出的页面标题来源（不含本地化文案）：UI 层据此用 stringResource 格式化，
 * 避免在 ViewModel 中拼装带本地化文案的字符串。
 */
sealed interface ResolvedTitle {
    val name: String

    data class Actress(override val name: String) : ResolvedTitle
    data class Genre(override val name: String) : ResolvedTitle
}

data class LinkMovieListUiState(
    /** 当前已加载的电影列表 */
    val movies: List<MovieUiModel> = emptyList(),
    /** 分页信息，包含当前页码和下一页码 */
    val pageInfo: PageInfo = PageInfo(),
    /** 是否正在加载首页数据 */
    val isLoading: Boolean = false,
    /** 是否正在下拉刷新 */
    val isRefreshing: Boolean = false,
    /** 是否正在加载更多（翻页） */
    val isLoadingMore: Boolean = false,
    /** 是否还有更多数据可加载 */
    val hasMore: Boolean = true,
    /** 错误信息，正常时为 null */
    val error: Int? = null,
    /** 女优头部详情、加载、错误和收藏状态 */
    val actressHeader: ActressHeaderState = ActressHeaderState(),
    /** 是否显示全部影片（含无磁力链接的影片） */
    val showAll: Boolean = false,
    /** 筛选信息（磁力数量与总数），仅在筛选模式下有值 */
    val filterInfo: MovieFilterInfo? = null,
    /** 是否正在切换筛选条件（保留旧列表，显示顶部刷新指示器） */
    val isFilterSwitching: Boolean = false,
    /** 从页面加载的真实标题（外部链接打开时使用） */
    val resolvedTitle: ResolvedTitle? = null,
    /** 后台刷新中（有缓存数据时显示顶部进度条） */
    val isRevalidating: Boolean = false,
    /** 缓存数据的时间戳 */
    val lastUpdatedAtMillis: Long? = null,
    /** 后台刷新获得的新数据，等待用户应用 */
    val pendingFreshResult: MoviePageResult? = null,
    /** 轻量刷新反馈消息（Snackbar） */
    val refreshMessage: Int? = null
)

/**
 * 关联电影列表页 ViewModel。
 *
 * 职责：管理通过 URL 链接加载电影列表（如女优关联影片、分类关联影片），
 * 支持分页加载、下拉刷新、女优详情展示和女优收藏状态切换。
 * 采用 stale-while-revalidate 策略：先显示缓存数据，后台静默刷新后无缝更新。
 *
 * 使用场景：在从女优或分类页面点击进入关联电影列表时使用，通过 Hilt 注入。
 * 当链接类型为女优时，会额外加载女优详情信息并支持收藏操作。
 * 页面 URL 通过 Navigation 的 SavedStateHandle 传入。
 *
 * 线程：网络请求通过 Repository 内部调度器执行，UI 状态通过 [MutableStateFlow] 更新。
 *
 * @param repository 电影数据仓库，负责从网络获取和解析电影列表及女优详情
 * @param collectRepository 收藏仓库，负责查询和切换女优收藏状态
 * @param savedStateHandle Navigation 参数持有者，用于获取传入的链接 URL
 */
@HiltViewModel(assistedFactory = LinkMovieListViewModel.Factory::class)
class LinkMovieListViewModel @AssistedInject constructor(
    private val repository: MovieRepository,
    private val collectRepository: CollectRepository,
    private val localVideoRepository: LocalVideoRepository,
    @Assisted private val navKey: RouteLinkMovies
) : ViewModel() {

    /** 内部可变的 UI 状态 */
    private val _uiState = MutableStateFlow(LinkMovieListUiState())

    /** 对外暴露的只读 UI 状态流 */
    val uiState: StateFlow<LinkMovieListUiState> = _uiState.asStateFlow()

    /** 已下载（关联本地视频）的番号集合，用于卡片角标展示 */
    val downloadedCodes: StateFlow<Set<String>> =
        localVideoRepository.observeDownloadedCodes()
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptySet())

    /** 当前已加载到的页码 */
    private val pages = PageTracker()

    /** 当前加载的链接 URL */
    private var linkUrl: String = navKey.linkUrl

    /** 列表类型，如 "actress" 表示女优关联影片 */
    private var listType: String = ""
    private val atTop = AtTopGate()
    private var firstPageJob: Job? = null
    private var requestGeneration = 0L
    private var activeListIdentity: ListRequestIdentity? = null

    private data class ListRequestIdentity(
        val linkUrl: String,
        val listType: String,
        val showAll: Boolean
    )

    fun setAtTopForFreshUpdates(isAtTop: Boolean) {
        atTop.isAtTop = isAtTop
    }

    /**
     * 设置链接 URL 并加载关联电影列表。
     *
     * 如果 URL 未变化且列表中已有数据，则跳过重复加载。
     * 当类型为女优时，会同时加载女优详情数据。
     *
     * @param url 关联页面的完整 URL
     * @param type 列表类型，"actress" 表示女优关联影片，空字符串表示其他类型
     * @param avatarUrl 头像 URL（保留参数，当前未使用）
     * @param defaultShowAll 是否显示全部影片（含无磁力链接的影片），作为该链接的初始筛选值
     */
    fun setLink(url: String, type: String = "", avatarUrl: String = "", defaultShowAll: Boolean = false) {
        if (linkUrl == url && _uiState.value.movies.isNotEmpty()) return
        linkUrl = url
        listType = type
        pages.reset()
        _uiState.value = LinkMovieListUiState(showAll = defaultShowAll)
        if (type == "actress") {
            _uiState.update { it.copy(actressHeader = it.actressHeader.startLoading()) }
        }
        loadFirstPage()
        if (type == "actress" && linkUrl.isNotBlank()) {
            loadActressDetail()
        }
    }

    fun setDefaultShowAll(showAll: Boolean) {
        val state = _uiState.value
        if (state.showAll == showAll) return
        val shouldReload = linkUrl.isNotBlank() && (
            state.movies.isNotEmpty() ||
                state.isLoading ||
                state.isRefreshing ||
                state.isRevalidating
            )
        if (shouldReload) firstPageJob?.cancel()
        _uiState.update {
            it.copy(
                showAll = showAll,
                isFilterSwitching = shouldReload,
                isLoading = false,
                isRefreshing = false,
                isRevalidating = false,
                pendingFreshResult = null,
                refreshMessage = null
            )
        }
        if (shouldReload) {
            pages.reset()
            loadFirstPage()
        }
    }

    /**
     * 应用后台刷新获得的待定数据。
     */
    fun applyPendingFreshResult() {
        val pending = _uiState.value.pendingFreshResult ?: return
        pages.startFirstPage()
        _uiState.update {
            it.copy(
                movies = pending.movies.map { m -> m.toUiModel() },
                pageInfo = pending.pageInfo,
                hasMore = pending.pageInfo.hasNext,
                filterInfo = pending.filterInfo,
                pendingFreshResult = null,
                refreshMessage = null
            )
        }
    }

    /**
     * 加载关联电影列表的第一页数据（stale-while-revalidate）。
     *
     * 先显示缓存数据，再后台刷新。如果已在加载中或 URL 为空则跳过。
     */
    fun loadFirstPage() {
        if (_uiState.value.isLoading || linkUrl.isBlank()) return
        pages.startFirstPage()
        firstPageJob?.cancel()
        val identity = currentListIdentity()
        val generation = beginListRequest(identity)
        firstPageJob = viewModelScope.launch {
            _uiState.update {
                it.copy(
                    isLoading = true,
                    isRefreshing = false,
                    isLoadingMore = false,
                    error = null,
                    refreshMessage = null
                )
            }
            var hasContent = false
            repository.observePageByUrl(
                identity.linkUrl,
                1,
                showAll = identity.showAll,
                revalidate = false
            )
                .collect { event ->
                    if (!isCurrent(generation, identity)) return@collect
                    when (event) {
                        is CachedLoadEvent.Cached -> {
                            hasContent = true
                            _uiState.update { it.applyFirstPageCached(event.entry) }
                        }

                        is CachedLoadEvent.Fresh -> {
                            // loadFirstPage 仅在列表为空时调用，直接应用
                            _uiState.update { it.applyFirstPageFresh(event.entry) }
                        }

                        is CachedLoadEvent.Failure -> {
                            _uiState.update { it.applyFirstPageFailure(event, hasContent) }
                        }
                    }
                }
        }
    }

    /**
     * 加载下一页关联电影数据，追加到现有列表末尾。
     *
     * 如果正在加载、没有更多数据或下一页码不大于当前页码则跳过。
     * 加载失败时回退页码计数器。
     */
    fun loadMore() {
        val state = _uiState.value
        if (state.isLoading || state.isLoadingMore || !state.hasMore) return
        val nextPage = state.pageInfo.nextPage
        if (!pages.shouldLoadMore(state.pageInfo)) return

        pages.advanceTo(nextPage)
        val identity = currentListIdentity()
        val generation = beginListRequest(identity)
        _uiState.update { it.copy(isLoadingMore = true) }
        viewModelScope.launch {
            try {
                val result =
                    repository.loadPageByUrl(identity.linkUrl, nextPage, showAll = identity.showAll)
                if (!isCurrent(generation, identity)) return@launch
                _uiState.update {
                    it.copy(
                        movies = it.movies + result.movies.map { m -> m.toUiModel() },
                        pageInfo = result.pageInfo,
                        isLoadingMore = false,
                        hasMore = result.pageInfo.hasNext,
                        filterInfo = result.filterInfo ?: it.filterInfo
                    )
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                if (!isCurrent(generation, identity)) return@launch
                pages.rollbackTo(state.pageInfo.activePage)
                _uiState.update { it.copy(isLoadingMore = false, error = R.string.load_failed) }
            }
        }
    }

    /**
     * 后台重新验证数据（从后台恢复时触发）。
     *
     * 根据 TTL 判断，缓存过期时一律走 pending + Snackbar。
     */
    fun revalidate() {
        val state = _uiState.value
        if (state.isRevalidating || state.isLoading || state.isRefreshing) return
        if (state.movies.isEmpty() || linkUrl.isBlank()) return
        val identity = currentListIdentity()
        val generation = beginListRequest(identity)
        viewModelScope.launch {
            _uiState.update { it.copy(isRevalidating = true) }
            repository.observePageByUrl(
                identity.linkUrl,
                1,
                showAll = identity.showAll,
                revalidate = false
            )
                .collect { event ->
                    if (!isCurrent(generation, identity)) return@collect
                    when (event) {
                        is CachedLoadEvent.Cached -> {
                            _uiState.update { it.copy(isRevalidating = event.entry.isExpired) }
                        }

                        is CachedLoadEvent.Fresh -> {
                            val reduction = _uiState.value.applyFreshRevalidate(
                                event.entry,
                                isAtTop = atTop.isAtTop
                            )
                            when (reduction.outcome) {
                                FreshRevalidateOutcome.ApplyImmediately -> {
                                    pages.startFirstPage()
                                    _uiState.value = reduction.state
                                }

                                FreshRevalidateOutcome.StorePending -> {
                                    _uiState.value = reduction.state
                                }

                                FreshRevalidateOutcome.NoChange -> {
                                    _uiState.value = reduction.state
                                }
                            }
                        }

                        is CachedLoadEvent.Failure -> {
                            _uiState.update { it.copy(isRevalidating = false) }
                        }
                    }
                }
        }
    }

    /**
     * 下拉刷新关联电影列表和女优详情。
     *
     * 强制从网络重新获取第一页数据，忽略缓存。
     * 如果当前为女优类型，同时刷新女优详情。
     */
    fun refresh() {
        val state = _uiState.value
        if (state.isRefreshing || state.isLoading || linkUrl.isBlank()) return
        val useInitialLoading = state.movies.isEmpty()
        pages.startFirstPage()
        val identity = currentListIdentity()
        val generation = beginListRequest(identity)
        viewModelScope.launch {
            _uiState.update {
                it.copy(
                    isLoading = useInitialLoading,
                    isRefreshing = !useInitialLoading,
                    error = null,
                    refreshMessage = null
                )
            }
            repository.observePageByUrl(
                identity.linkUrl,
                1,
                showAll = identity.showAll,
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
                                    movies = event.entry.value.movies.map { m -> m.toUiModel() },
                                    pageInfo = event.entry.value.pageInfo,
                                    isLoading = false,
                                    isRefreshing = false,
                                    hasMore = event.entry.value.pageInfo.hasNext,
                                    filterInfo = event.entry.value.filterInfo
                                )
                            }
                        }

                        is CachedLoadEvent.Failure -> {
                            _uiState.update {
                                it.copy(
                                    isLoading = false,
                                    isRefreshing = false,
                                    error = if (it.movies.isEmpty()) R.string.load_failed else it.error,
                                    refreshMessage = if (it.movies.isNotEmpty()) R.string.refresh_failed else null
                                )
                            }
                        }
                    }
                }
        }
        if (listType == "actress" && linkUrl.isNotBlank()) {
            loadActressDetail(forceRefresh = true)
        }
    }

    /**
     * 加载女优详情数据并检查收藏状态。
     *
     * @param forceRefresh 是否强制从网络重新获取，忽略缓存
     */
    private fun loadActressDetail(forceRefresh: Boolean = false) {
        viewModelScope.launch {
            _uiState.update { it.copy(actressHeader = it.actressHeader.startLoading()) }
            try {
                val detail = repository.loadActressDetail(linkUrl, forceRefresh = forceRefresh)
                if (detail != null) {
                    _uiState.update {
                        it.copy(
                            actressHeader = it.actressHeader.applyLoaded(detail),
                            resolvedTitle = ResolvedTitle.Actress(detail.name)
                        )
                    }
                    val actress = ActressInfo(
                        name = detail.name,
                        avatar = detail.avatar,
                        link = linkUrl
                    )
                    val collected = collectRepository.isActressCollected(actress)
                    _uiState.update {
                        it.copy(actressHeader = it.actressHeader.withCollected(collected))
                    }
                } else {
                    _uiState.update {
                        it.copy(actressHeader = it.actressHeader.finishWithoutDetail())
                    }
                }
            } catch (e: Exception) {
                KLog.e("loadActressDetail failed", e)
                _uiState.update {
                    it.copy(actressHeader = it.actressHeader.finishWithError(e.message))
                }
            }
        }
    }

    /**
     * 切换当前女优的收藏状态。
     *
     * 如果已收藏则取消收藏，反之则添加收藏。仅在女优详情已加载时生效。
     * 根据当前 [linkUrl] 是否包含 `/uncensored/` 判定有码/无码，并传入对应的收藏分类 ID：
     * 无码（含 `/uncensored/`）→ [UncensoredActressCategory]；否则 → [ActressCategory]。
     */
    fun toggleActressCollect() {
        val actressDetail = _uiState.value.actressHeader.detail ?: return
        viewModelScope.launch {
            val isUncensored = linkUrl.contains("/uncensored/")
            val categoryId = if (isUncensored) {
                UncensoredActressCategory.id
            } else {
                ActressCategory.id
            }
            val actress = ActressInfo(
                name = actressDetail.name,
                avatar = actressDetail.avatar,
                link = linkUrl
            )
            val newState = collectRepository.toggleActressCollect(actress, categoryId)
            _uiState.update {
                it.copy(actressHeader = it.actressHeader.withCollected(newState))
            }
        }
    }

    /**
     * 切换是否显示全部影片（含无磁力链接的影片）。
     *
     * 切换后重置页码并重新加载第一页数据。
     */
    fun toggleShowAll() {
        val newState = !_uiState.value.showAll
        _uiState.update { it.copy(showAll = newState, isFilterSwitching = true) }
        pages.reset()
        loadFirstPage()
    }

    /** 消费轻量刷新消息（Snackbar） */
    fun consumeRefreshMessage() {
        _uiState.update { it.copy(refreshMessage = null) }
    }

    private fun currentListIdentity(): ListRequestIdentity =
        ListRequestIdentity(linkUrl, listType, _uiState.value.showAll)

    private fun beginListRequest(identity: ListRequestIdentity): Long {
        requestGeneration += 1
        activeListIdentity = identity
        return requestGeneration
    }

    private fun isCurrent(generation: Long, identity: ListRequestIdentity): Boolean =
        generation == requestGeneration && activeListIdentity == identity

    @AssistedFactory
    interface Factory {
        fun create(navKey: RouteLinkMovies): LinkMovieListViewModel
    }
}

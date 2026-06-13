package me.jbusdriver.modern.ui.movielist

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import me.jbusdriver.modern.core.cache.AtTopGate
import me.jbusdriver.modern.core.cache.CachedLoadEvent
import me.jbusdriver.modern.core.cache.FreshRevalidateOutcome
import me.jbusdriver.modern.core.cache.PageTracker
import me.jbusdriver.modern.core.cache.decideFreshRevalidate
import me.jbusdriver.modern.core.cache.simulateCacheRefreshChange
import me.jbusdriver.modern.data.MovieRepository
import me.jbusdriver.modern.domain.model.PageInfo
import me.jbusdriver.modern.domain.model.hasNext
import me.jbusdriver.modern.domain.model.DataSourceType
import me.jbusdriver.modern.domain.model.ActressInfo
import me.jbusdriver.modern.ui.ActressUiModel
import me.jbusdriver.modern.ui.toActressUiModel
import javax.inject.Inject

/**
 * 女优列表页的 UI 状态。
 *
 * 包含女优列表数据、分页信息、各加载/错误标志以及是否还有更多数据。
 */
data class ActressListUiState(
    /** 当前已加载的女优列表 */
    val actresses: List<ActressUiModel> = emptyList(),
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
    val error: String? = null,
    /** 后台刷新中（有缓存数据时显示顶部进度条） */
    val isRevalidating: Boolean = false,
    /** 缓存数据的时间戳 */
    val lastUpdatedAtMillis: Long? = null,
    /** 后台刷新获得的新数据，等待用户应用 */
    val pendingFreshActresses: Pair<List<ActressInfo>, PageInfo>? = null,
    /** 轻量刷新反馈消息（Snackbar） */
    val refreshMessage: String? = null
)

/**
 * 女优列表页 ViewModel。
 *
 * 职责：管理按分类分页加载女优列表，支持下拉刷新和加载更多。
 * 采用 stale-while-revalidate 策略：先显示缓存数据，后台静默刷新后提示用户更新。
 *
 * 使用场景：在主界面的女优列表 Tab 页面中使用，通过 Hilt 注入。
 * 用户切换女优分类标签时切换数据源，支持分页加载和下拉刷新。
 *
 * 线程：所有网络请求在 [Dispatchers.IO] 上执行，UI 状态通过 [MutableStateFlow] 更新。
 *
 * @param repository 电影数据仓库，负责从网络获取和解析女优列表
 */
@HiltViewModel
class ActressListViewModel @Inject constructor(
    private val repository: MovieRepository
) : ViewModel() {

    /** 内部可变的 UI 状态 */
    private val _uiState = MutableStateFlow(ActressListUiState())

    /** 对外暴露的只读 UI 状态流 */
    val uiState: StateFlow<ActressListUiState> = _uiState.asStateFlow()

    /** 当前已加载到的页码 */
    private val pages = PageTracker()

    /** 当前的数据源类型（默认为女优分类） */
    private var dataSourceType: DataSourceType = DataSourceType.ACTRESSES
    private val atTop = AtTopGate()
    private var firstPageJob: Job? = null

    fun setAtTopForFreshUpdates(isAtTop: Boolean) {
        atTop.isAtTop = isAtTop
    }

    /**
     * 设置数据源类型并重新加载列表。
     *
     * 如果类型未变化且列表中已有数据，则触发后台 revalidate。
     *
     * @param type 数据源类型，如 [DataSourceType.ACTRESSES] 等
     */
    fun setDataSourceType(type: DataSourceType) {
        if (dataSourceType == type && _uiState.value.actresses.isNotEmpty()) {
            return
        }
        dataSourceType = type
        pages.reset()
        _uiState.value = ActressListUiState()
        loadFirstPage()
    }

    /**
     * 应用后台刷新获得的待定数据。
     *
     * 重置为第 1 页数据，用户需要重新加载更多。
     */
    fun applyPendingFreshActresses() {
        val pending = _uiState.value.pendingFreshActresses ?: return
        pages.startFirstPage()
        _uiState.update {
            it.copy(
                actresses = pending.first.map { a -> a.toActressUiModel() },
                pageInfo = pending.second,
                hasMore = pending.second.hasNext,
                pendingFreshActresses = null,
                refreshMessage = null
            )
        }
    }

    /**
     * 加载女优列表的第一页数据（stale-while-revalidate）。
     *
     * 先显示缓存数据，再后台刷新。仅在列表为空时调用，Fresh 数据直接应用。
     */
    fun loadFirstPage() {
        if (_uiState.value.isLoading) return
        pages.startFirstPage()
        firstPageJob?.cancel()
        firstPageJob = viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true, error = null, refreshMessage = null) }
            var hasContent = false
            repository.observeActresses(dataSourceType, 1, revalidate = false)
                .collect { event ->
                    when (event) {
                        is CachedLoadEvent.Cached -> {
                            hasContent = true
                            _uiState.update {
                                it.copy(
                                    actresses = event.entry.value.first.map { a -> a.toActressUiModel() },
                                    pageInfo = event.entry.value.second,
                                    isLoading = false,
                                    isRefreshing = false,
                                    hasMore = event.entry.value.second.hasNext,
                                    error = if (event.entry.value.first.isEmpty()) "沒有數據" else null,
                                    isRevalidating = event.entry.isExpired,
                                    lastUpdatedAtMillis = event.entry.storedAtMillis
                                )
                            }
                        }
                        is CachedLoadEvent.Fresh -> {
                            // loadFirstPage 仅在列表为空时调用，直接应用
                            _uiState.update {
                                it.copy(
                                    actresses = event.entry.value.first.simulateCacheRefreshChange().map { a -> a.toActressUiModel() },
                                    pageInfo = event.entry.value.second,
                                    isLoading = false,
                                    isRefreshing = false,
                                    isRevalidating = false,
                                    hasMore = event.entry.value.second.hasNext,
                                    lastUpdatedAtMillis = event.entry.storedAtMillis
                                )
                            }
                        }
                        is CachedLoadEvent.Failure -> {
                            _uiState.update {
                                if (event.hadCachedValue || hasContent) {
                                    it.copy(isLoading = false, isRefreshing = false, isRevalidating = false)
                                } else {
                                    it.copy(isLoading = false, isRefreshing = false, isRevalidating = false, error = event.throwable.message ?: "載入失敗")
                                }
                            }
                        }
                    }
                }
        }
    }

    /**
     * 后台重新验证数据（Tab 切换回来或从后台恢复时触发）。
     *
     * 根据 TTL 判断是否需要网络请求。缓存过期时走 pending + Snackbar，
     * 避免已加载多页时直接替换导致跳位和加载更多失效。
     */
    fun revalidate() {
        val state = _uiState.value
        if (state.isRevalidating || state.isLoading || state.isRefreshing) return
        if (state.actresses.isEmpty()) return
        viewModelScope.launch {
            _uiState.update { it.copy(isRevalidating = true) }
            repository.observeActresses(dataSourceType, 1, revalidate = false)
                .collect { event ->
                    when (event) {
                        is CachedLoadEvent.Cached -> {
                            _uiState.update { it.copy(isRevalidating = event.entry.isExpired) }
                        }
                        is CachedLoadEvent.Fresh -> {
                            val fresh = event.entry.value.copy(
                                first = event.entry.value.first.simulateCacheRefreshChange()
                            )
                            val freshUiModels = fresh.first.map { it.toActressUiModel() }
                            when (decideFreshRevalidate(_uiState.value.actresses, freshUiModels, atTop.isAtTop)) {
                                FreshRevalidateOutcome.ApplyImmediately -> {
                                    pages.startFirstPage()
                                    _uiState.update {
                                        it.copy(
                                            actresses = freshUiModels,
                                            pageInfo = fresh.second,
                                            hasMore = fresh.second.hasNext,
                                            isRevalidating = false,
                                            pendingFreshActresses = null,
                                            refreshMessage = null,
                                            lastUpdatedAtMillis = event.entry.storedAtMillis
                                        )
                                    }
                                }
                                FreshRevalidateOutcome.StorePending -> {
                                    _uiState.update {
                                        it.copy(
                                            isRevalidating = false,
                                            pendingFreshActresses = fresh,
                                            refreshMessage = "有新數據"
                                        )
                                    }
                                }
                                FreshRevalidateOutcome.NoChange -> {
                                    _uiState.update { it.copy(isRevalidating = false) }
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
     * 下拉刷新女优列表。
     *
     * 强制从网络重新获取第一页数据，忽略缓存。
     */
    fun refresh() {
        if (_uiState.value.isRefreshing) return
        pages.startFirstPage()
        viewModelScope.launch {
            _uiState.update { it.copy(isRefreshing = true, error = null, refreshMessage = null) }
            repository.observeActresses(dataSourceType, 1, forceRefresh = true, revalidate = false)
                .collect { event ->
                    when (event) {
                        is CachedLoadEvent.Cached -> Unit
                        is CachedLoadEvent.Fresh -> {
                            _uiState.update {
                                it.copy(
                                    actresses = event.entry.value.first.map { a -> a.toActressUiModel() },
                                    pageInfo = event.entry.value.second,
                                    isRefreshing = false,
                                    hasMore = event.entry.value.second.hasNext
                                )
                            }
                        }
                        is CachedLoadEvent.Failure -> {
                            _uiState.update {
                                it.copy(
                                    isRefreshing = false,
                                    error = if (it.actresses.isEmpty()) event.throwable.message ?: "載入失敗" else it.error,
                                    refreshMessage = if (it.actresses.isNotEmpty()) "刷新失敗" else null
                                )
                            }
                        }
                    }
                }
        }
    }

    /**
     * 加载下一页女优数据，追加到现有列表末尾。
     */
    fun loadMore() {
        val state = _uiState.value
        if (state.isLoading || state.isLoadingMore || !state.hasMore) return
        val nextPage = state.pageInfo.nextPage
        if (!pages.shouldLoadMore(state.pageInfo)) return

        pages.advanceTo(nextPage)
        viewModelScope.launch(Dispatchers.IO) {
            _uiState.update { it.copy(isLoadingMore = true) }
            try {
                val result = repository.loadActresses(dataSourceType, nextPage)
                _uiState.update {
                    it.copy(
                        actresses = it.actresses + result.first.map { a -> a.toActressUiModel() },
                        pageInfo = result.second,
                        isLoadingMore = false,
                        hasMore = result.second.hasNext
                    )
                }
            } catch (e: Exception) {
                pages.rollbackTo(state.pageInfo.activePage)
                _uiState.update { it.copy(isLoadingMore = false, error = e.message) }
            }
        }
    }

    /** 消费轻量刷新消息（Snackbar） */
    fun consumeRefreshMessage() {
        _uiState.update { it.copy(refreshMessage = null) }
    }
}

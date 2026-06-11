package me.jbusdriver.modern.ui.movielist

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import me.jbusdriver.modern.core.cache.CachedLoadEvent
import me.jbusdriver.modern.data.MovieRepository
import me.jbusdriver.modern.domain.model.MovieFilterInfo
import me.jbusdriver.modern.domain.model.PageInfo
import me.jbusdriver.modern.domain.model.hasNext
import me.jbusdriver.modern.domain.model.DataSourceType
import me.jbusdriver.modern.domain.model.MoviePageResult
import me.jbusdriver.modern.ui.MovieUiModel
import me.jbusdriver.modern.ui.toUiModel
import javax.inject.Inject

// TODO: remove after testing cache refresh UX — 随机交换首元素，模拟缓存与新鲜数据的视觉差异
private fun <T> List<T>.shuffledForTesting(): List<T> =
    if (size < 2) this else toMutableList().apply {
        val target = (1..lastIndex).random()
        val temp = this[0]
        this[0] = this[target]
        this[target] = temp
    }

/**
 * 电影列表页的 UI 状态。
 *
 * 包含电影列表数据、分页信息、各加载/错误标志以及是否还有更多数据。
 */
data class MovieListUiState(
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
    /** 错误信息，正常时为 null */
    val error: String? = null,
    /** 是否还有更多数据可加载 */
    val hasMore: Boolean = true,
    /** 是否显示全部影片（含无磁力链接的影片） */
    val showAll: Boolean = false,
    /** 筛选信息（磁力数量与总数），仅在筛选模式下有值 */
    val filterInfo: MovieFilterInfo? = null,
    /** 是否正在切换筛选条件（保留旧列表，显示顶部刷新指示器） */
    val isFilterSwitching: Boolean = false,
    /** 后台刷新中（有缓存数据时显示顶部进度条） */
    val isRevalidating: Boolean = false,
    /** 缓存数据的时间戳 */
    val lastUpdatedAtMillis: Long? = null,
    /** 后台刷新获得的新数据，等待用户应用 */
    val pendingFreshResult: MoviePageResult? = null,
    /** 轻量刷新反馈消息（Snackbar） */
    val refreshMessage: String? = null
)

/**
 * 电影列表页 ViewModel。
 *
 * 职责：管理按分类（有码/无码/欧美等）分页加载电影列表，支持下拉刷新和加载更多。
 * 采用 stale-while-revalidate 策略：先显示缓存数据，后台静默刷新后无缝更新。
 *
 * 使用场景：在主界面的电影列表 Tab 页面中使用，通过 Hilt 注入。
 * 用户切换分类标签时切换数据源，支持分页加载和下拉刷新。
 *
 * 线程：所有网络请求在 [Dispatchers.IO] 上执行，UI 状态通过 [MutableStateFlow] 更新。
 *
 * @param repository 电影数据仓库，负责从网络获取和解析电影列表
 */
@HiltViewModel
class MovieListViewModel @Inject constructor(
    private val repository: MovieRepository
) : ViewModel() {

    /** 内部可变的 UI 状态 */
    private val _uiState = MutableStateFlow(MovieListUiState())

    /** 对外暴露的只读 UI 状态流 */
    val uiState: StateFlow<MovieListUiState> = _uiState.asStateFlow()

    /** 当前已加载到的页码 */
    private var currentPage = 0

    /** 当前的数据源类型（有码/无码/欧美等） */
    private var dataSourceType: DataSourceType = DataSourceType.CENSORED

    /** 当前 genre 过滤的 URL，非 null 时优先使用 URL 加载 */
    private var genreUrl: String? = null

    /** 用户是否在列表顶部（用于决定是否自动应用后台刷新数据） */
    private var isAtTopForFreshUpdates: Boolean = true

    /**
     * 设置数据源类型并重新加载列表。
     *
     * 如果类型未变化且列表中已有数据，则跳过重复加载。
     * 重置页码和 UI 状态后自动调用 [loadFirstPage]。
     *
     * @param type 数据源类型，如 [DataSourceType.CENSORED]、[DataSourceType.UNCENSORED] 等
     */
    fun setDataSourceType(type: DataSourceType) {
        if (dataSourceType == type && _uiState.value.movies.isNotEmpty()) {
            revalidate()
            return
        }
        dataSourceType = type
        currentPage = 0
        _uiState.update { it.copy(isFilterSwitching = true, hasMore = true, error = null) }
        loadFirstPage()
    }

    /**
     * 设置 genre URL 并重新加载列表。
     *
     * 如果 URL 未变化且列表中已有数据，则跳过重复加载。
     * 重置页码和 UI 状态后自动调用 [loadFirstPage]。
     *
     * @param url genre 过滤页面的完整 URL，传 null 则回退到按数据源类型加载
     */
    fun setGenreUrl(url: String?) {
        if (genreUrl == url && _uiState.value.movies.isNotEmpty()) return
        genreUrl = url
        currentPage = 0
        _uiState.value = MovieListUiState()
        loadFirstPage()
    }

    /**
     * 设置用户是否在列表顶部，用于决定后台刷新数据是否自动应用。
     */
    fun setAtTopForFreshUpdates(isAtTop: Boolean) {
        isAtTopForFreshUpdates = isAtTop
    }

    /**
     * 应用后台刷新获得的待定数据。
     */
    fun applyPendingFreshResult() {
        val pending = _uiState.value.pendingFreshResult ?: return
        currentPage = 1
        _uiState.update {
            it.copy(
                movies = pending.movies.shuffledForTesting().map { m -> m.toUiModel() },
                pageInfo = pending.pageInfo,
                hasMore = pending.pageInfo.hasNext,
                filterInfo = pending.filterInfo,
                pendingFreshResult = null,
                refreshMessage = null
            )
        }
    }

    /**
     * 加载电影列表的第一页数据（stale-while-revalidate）。
     *
     * 先显示缓存数据，再后台刷新。如果已在加载中则跳过。
     */
    fun loadFirstPage() {
        if (_uiState.value.isLoading) return
        currentPage = 1
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true, error = null, refreshMessage = null) }
            var hasContent = false
            val flow = if (genreUrl != null) {
                repository.observePageByUrl(genreUrl!!, 1, showAll = _uiState.value.showAll, revalidate = true)
            } else {
                repository.observePage(dataSourceType, 1, showAll = _uiState.value.showAll, revalidate = true)
            }
            flow.collect { event ->
                when (event) {
                    is CachedLoadEvent.Cached -> {
                        hasContent = true
                        _uiState.update {
                            it.copy(
                                movies = event.entry.value.movies.map { m -> m.toUiModel() },
                                pageInfo = event.entry.value.pageInfo,
                                filterInfo = event.entry.value.filterInfo,
                                isLoading = false,
                                isFilterSwitching = false,
                                hasMore = event.entry.value.pageInfo.hasNext,
                                error = if (event.entry.value.movies.isEmpty()) "沒有數據" else null,
                                isRevalidating = true,
                                lastUpdatedAtMillis = event.entry.storedAtMillis
                            )
                        }
                    }
                    is CachedLoadEvent.Fresh -> {
                        if (isAtTopForFreshUpdates) {
                            _uiState.update {
                                it.copy(
                                    movies = event.entry.value.movies.shuffledForTesting().map { m -> m.toUiModel() },
                                    pageInfo = event.entry.value.pageInfo,
                                    filterInfo = event.entry.value.filterInfo,
                                    isLoading = false,
                                    isFilterSwitching = false,
                                    isRevalidating = false,
                                    hasMore = event.entry.value.pageInfo.hasNext,
                                    pendingFreshResult = null,
                                    refreshMessage = null,
                                    lastUpdatedAtMillis = event.entry.storedAtMillis,
                                    error = if (event.entry.value.movies.isEmpty()) "沒有數據" else null
                                )
                            }
                        } else {
                            _uiState.update {
                                it.copy(
                                    isLoading = false,
                                    isFilterSwitching = false,
                                    isRevalidating = false,
                                    pendingFreshResult = event.entry.value,
                                    refreshMessage = "有新數據"
                                )
                            }
                        }
                    }
                    is CachedLoadEvent.Failure -> {
                        _uiState.update {
                            if (event.hadCachedValue || hasContent) {
                                it.copy(isLoading = false, isFilterSwitching = false, isRevalidating = false)
                            } else {
                                it.copy(isLoading = false, isFilterSwitching = false, isRevalidating = false, error = event.throwable.message ?: "載入失敗")
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
     * 不显示全屏 loading，仅展示顶部进度条，收集缓存+网络事件。
     */
    fun revalidate() {
        val state = _uiState.value
        if (state.isRevalidating || state.isLoading || state.isRefreshing) return
        if (state.movies.isEmpty()) return
        viewModelScope.launch {
            _uiState.update { it.copy(isRevalidating = true) }
            val flow = if (genreUrl != null) {
                repository.observePageByUrl(genreUrl!!, 1, showAll = _uiState.value.showAll, revalidate = true)
            } else {
                repository.observePage(dataSourceType, 1, showAll = _uiState.value.showAll, revalidate = true)
            }
            flow.collect { event ->
                when (event) {
                    is CachedLoadEvent.Cached -> Unit // 数据已在屏幕上，无需更新
                    is CachedLoadEvent.Fresh -> {
                        if (isAtTopForFreshUpdates) {
                            _uiState.update {
                                it.copy(
                                    movies = event.entry.value.movies.shuffledForTesting().map { m -> m.toUiModel() },
                                    pageInfo = event.entry.value.pageInfo,
                                    hasMore = event.entry.value.pageInfo.hasNext,
                                    filterInfo = event.entry.value.filterInfo,
                                    isRevalidating = false,
                                    lastUpdatedAtMillis = event.entry.storedAtMillis
                                )
                            }
                        } else {
                            _uiState.update {
                                it.copy(
                                    isRevalidating = false,
                                    pendingFreshResult = event.entry.value,
                                    refreshMessage = "有新數據"
                                )
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
     * 下拉刷新电影列表。
     *
     * 强制从网络重新获取第一页数据，忽略缓存。
     */
    fun refresh() {
        if (_uiState.value.isRefreshing) return
        currentPage = 1
        viewModelScope.launch {
            _uiState.update { it.copy(isRefreshing = true, error = null, refreshMessage = null) }
            val flow = if (genreUrl != null) {
                repository.observePageByUrl(genreUrl!!, 1, showAll = _uiState.value.showAll, forceRefresh = true, revalidate = false)
            } else {
                repository.observePage(dataSourceType, 1, showAll = _uiState.value.showAll, forceRefresh = true, revalidate = false)
            }
            flow.collect { event ->
                when (event) {
                    is CachedLoadEvent.Cached -> Unit
                    is CachedLoadEvent.Fresh -> {
                        _uiState.update {
                            it.copy(
                                movies = event.entry.value.movies.map { m -> m.toUiModel() },
                                pageInfo = event.entry.value.pageInfo,
                                isRefreshing = false,
                                hasMore = event.entry.value.pageInfo.hasNext,
                                filterInfo = event.entry.value.filterInfo
                            )
                        }
                    }
                    is CachedLoadEvent.Failure -> {
                        _uiState.update {
                            it.copy(
                                isRefreshing = false,
                                error = if (it.movies.isEmpty()) event.throwable.message ?: "載入失敗" else it.error,
                                refreshMessage = if (it.movies.isNotEmpty()) "刷新失敗" else null
                            )
                        }
                    }
                }
            }
        }
    }

    /**
     * 加载下一页电影数据，追加到现有列表末尾。
     *
     * 如果正在加载、没有更多数据或下一页码不大于当前页码则跳过。
     * 加载失败时回退页码计数器。
     */
    fun loadMore() {
        val state = _uiState.value
        if (state.isLoading || state.isLoadingMore || !state.hasMore) return
        val nextPage = state.pageInfo.nextPage
        if (nextPage <= currentPage) return

        currentPage = nextPage
        viewModelScope.launch(Dispatchers.IO) {
            _uiState.update { it.copy(isLoadingMore = true) }
            try {
                val result = if (genreUrl != null) {
                    repository.loadPageByUrl(genreUrl!!, nextPage, showAll = _uiState.value.showAll)
                } else {
                    repository.loadPage(dataSourceType, nextPage, showAll = _uiState.value.showAll)
                }
                _uiState.update {
                    it.copy(
                        movies = it.movies + result.movies.map { m -> m.toUiModel() },
                        pageInfo = result.pageInfo,
                        isLoadingMore = false,
                        hasMore = result.pageInfo.hasNext,
                        filterInfo = result.filterInfo ?: it.filterInfo
                    )
                }
            } catch (e: Exception) {
                currentPage = _uiState.value.pageInfo.activePage
                _uiState.update { it.copy(isLoadingMore = false, error = e.message) }
            }
        }
    }

    /** 清除当前的错误信息 */
    fun clearError() {
        _uiState.update { it.copy(error = null) }
    }

    /** 消费轻量刷新消息（Snackbar） */
    fun consumeRefreshMessage() {
        _uiState.update { it.copy(refreshMessage = null) }
    }

    /**
     * 切换是否显示全部影片（含无磁力链接的影片）。
     *
     * 切换后重置页码并重新加载第一页数据。
     */
    fun toggleShowAll() {
        val newState = !_uiState.value.showAll
        _uiState.update { it.copy(showAll = newState, isFilterSwitching = true) }
        currentPage = 0
        loadFirstPage()
    }
}

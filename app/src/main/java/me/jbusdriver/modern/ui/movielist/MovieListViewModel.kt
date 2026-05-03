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
import me.jbusdriver.modern.data.MovieRepository
import me.jbusdriver.modern.domain.model.PageInfo
import me.jbusdriver.modern.domain.model.hasNext
import me.jbusdriver.modern.domain.model.DataSourceType
import me.jbusdriver.modern.domain.model.MoviePageResult
import me.jbusdriver.modern.ui.MovieUiModel
import me.jbusdriver.modern.ui.toUiModel
import javax.inject.Inject

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
    val hasMore: Boolean = true
)

/**
 * 电影列表页 ViewModel。
 *
 * 职责：管理按分类（有码/无码/欧美等）分页加载电影列表，支持下拉刷新和加载更多。
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

    /**
     * 设置数据源类型并重新加载列表。
     *
     * 如果类型未变化且列表中已有数据，则跳过重复加载。
     * 重置页码和 UI 状态后自动调用 [loadFirstPage]。
     *
     * @param type 数据源类型，如 [DataSourceType.CENSORED]、[DataSourceType.UNCENSORED] 等
     */
    fun setDataSourceType(type: DataSourceType) {
        if (dataSourceType == type && _uiState.value.movies.isNotEmpty()) return
        dataSourceType = type
        currentPage = 0
        _uiState.value = MovieListUiState()
        loadFirstPage()
    }

    /**
     * 加载电影列表的第一页数据。
     *
     * 如果已在加载中则跳过。加载完成后更新分页信息和是否有更多数据的状态。
     */
    fun loadFirstPage() {
        if (_uiState.value.isLoading) return
        currentPage = 1
        loadMovies(
            page = 1,
            loadingFlag = { copy(isLoading = true, error = null) },
            onSuccess = { result, state ->
                state.copy(
                    movies = result.movies.map { it.toUiModel() },
                    pageInfo = result.pageInfo,
                    isLoading = false,
                    hasMore = result.pageInfo.hasNext,
                    error = if (result.movies.isEmpty()) "沒有數據" else null
                )
            },
            onError = { e, state -> state.copy(isLoading = false, error = e.message ?: "載入失敗") }
        )
    }

    /**
     * 下拉刷新电影列表。
     *
     * 强制从网络重新获取第一页数据，忽略缓存。
     */
    fun refresh() {
        if (_uiState.value.isRefreshing) return
        currentPage = 1
        loadMovies(
            page = 1,
            forceRefresh = true,
            loadingFlag = { copy(isRefreshing = true, error = null) },
            onSuccess = { result, state ->
                state.copy(
                    movies = result.movies.map { it.toUiModel() },
                    pageInfo = result.pageInfo,
                    isRefreshing = false,
                    hasMore = result.pageInfo.hasNext
                )
            },
            onError = { e, state -> state.copy(isRefreshing = false, error = e.message) }
        )
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
        loadMovies(
            page = nextPage,
            loadingFlag = { copy(isLoadingMore = true) },
            onSuccess = { result, state ->
                state.copy(
                    movies = state.movies + result.movies.map { it.toUiModel() },
                    pageInfo = result.pageInfo,
                    isLoadingMore = false,
                    hasMore = result.pageInfo.hasNext
                )
            },
            onError = { e, state -> state.copy(isLoadingMore = false, error = e.message) },
            onFailure = { currentPage = _uiState.value.pageInfo.activePage }
        )
    }

    private inline fun loadMovies(
        page: Int,
        forceRefresh: Boolean = false,
        crossinline loadingFlag: MovieListUiState.() -> MovieListUiState,
        crossinline onSuccess: (MoviePageResult, MovieListUiState) -> MovieListUiState,
        crossinline onError: (Exception, MovieListUiState) -> MovieListUiState,
        noinline onFailure: () -> Unit = {}
    ) {
        viewModelScope.launch(Dispatchers.IO) {
            _uiState.update(loadingFlag)
            try {
                val result = repository.loadPage(dataSourceType, page, forceRefresh = forceRefresh)
                _uiState.update { onSuccess(result, it) }
            } catch (e: Exception) {
                onFailure()
                _uiState.update { onError(e, it) }
            }
        }
    }

    /** 清除当前的错误信息 */
    fun clearError() {
        _uiState.update { it.copy(error = null) }
    }
}

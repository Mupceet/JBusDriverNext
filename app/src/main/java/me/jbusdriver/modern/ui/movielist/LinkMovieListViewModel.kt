package me.jbusdriver.modern.ui.movielist

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
import me.jbusdriver.modern.data.CollectRepository
import me.jbusdriver.modern.data.MovieRepository
import me.jbusdriver.modern.domain.model.MovieFilterInfo
import me.jbusdriver.modern.domain.model.PageInfo
import me.jbusdriver.modern.domain.model.hasNext
import me.jbusdriver.modern.domain.model.ActressInfo
import me.jbusdriver.modern.ui.ActressDetailUiModel
import me.jbusdriver.modern.ui.MovieUiModel
import me.jbusdriver.modern.ui.RouteLinkMovies
import me.jbusdriver.modern.ui.toUiModel

/**
 * 关联电影列表页的 UI 状态。
 *
 * 包含电影列表数据、分页信息、女优详情数据、收藏状态及各加载/错误标志。
 */
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
    val error: String? = null,
    /** 女优详情数据，仅在女优页面时有值 */
    val actressDetail: ActressDetailUiModel? = null,
    /** 是否正在加载女优详情 */
    val isLoadingActress: Boolean = false,
    /** 女优详情加载错误信息 */
    val actressError: String? = null,
    /** 当前女优是否已收藏 */
    val isCollected: Boolean = false,
    /** 是否显示全部影片（含无磁力链接的影片） */
    val showAll: Boolean = false,
    /** 筛选信息（磁力数量与总数），仅在筛选模式下有值 */
    val filterInfo: MovieFilterInfo? = null,
    /** 是否正在切换筛选条件（保留旧列表，显示顶部刷新指示器） */
    val isFilterSwitching: Boolean = false,
    /** 从页面加载的真实标题（外部链接打开时使用） */
    val resolvedTitle: String? = null
)

/**
 * 关联电影列表页 ViewModel。
 *
 * 职责：管理通过 URL 链接加载电影列表（如女优关联影片、分类关联影片），
 * 支持分页加载、下拉刷新、女优详情展示和女优收藏状态切换。
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
    @Assisted private val navKey: RouteLinkMovies
) : ViewModel() {

    /** 内部可变的 UI 状态 */
    private val _uiState = MutableStateFlow(LinkMovieListUiState())

    /** 对外暴露的只读 UI 状态流 */
    val uiState: StateFlow<LinkMovieListUiState> = _uiState.asStateFlow()

    /** 当前已加载到的页码 */
    private var currentPage = 0

    /** 当前加载的链接 URL */
    private var linkUrl: String = navKey.linkUrl

    /** 列表类型，如 "actress" 表示女优关联影片 */
    private var listType: String = ""

    /**
     * 设置链接 URL 并加载关联电影列表。
     *
     * 如果 URL 未变化且列表中已有数据，则跳过重复加载。
     * 当类型为女优时，会同时加载女优详情数据。
     *
     * @param url 关联页面的完整 URL
     * @param type 列表类型，"actress" 表示女优关联影片，空字符串表示其他类型
     * @param avatarUrl 头像 URL（保留参数，当前未使用）
     */
    fun setLink(url: String, type: String = "", avatarUrl: String = "") {
        if (linkUrl == url && _uiState.value.movies.isNotEmpty()) return
        linkUrl = url
        listType = type
        currentPage = 0
        _uiState.value = LinkMovieListUiState()
        if (type == "actress") {
            _uiState.update { it.copy(isLoadingActress = true, actressError = null) }
        }
        loadFirstPage()
        if (type == "actress" && linkUrl.isNotBlank()) {
            loadActressDetail()
        }
    }

    /**
     * 加载关联电影列表的第一页数据。
     *
     * 如果已在加载中或 URL 为空则跳过。加载完成后更新分页信息和是否有更多数据的状态。
     */
    fun loadFirstPage() {
        if (_uiState.value.isLoading || linkUrl.isBlank()) return
        currentPage = 1
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true, error = null) }
            try {
                val result = repository.loadPageByUrl(linkUrl, 1, showAll = _uiState.value.showAll)
                _uiState.update { state ->
                    state.copy(
                        movies = result.movies.map { m -> m.toUiModel() },
                        pageInfo = result.pageInfo,
                        isLoading = false,
                        isFilterSwitching = false,
                        hasMore = result.pageInfo.hasNext,
                        error = if (result.movies.isEmpty()) "沒有數據" else null,
                        filterInfo = result.filterInfo,
                        resolvedTitle = result.filterInfo?.let {
                            if (it.breadcrumbType != null && it.breadcrumbName != null) {
                                val typeLabel = when (it.breadcrumbType) {
                                    "女優" -> "演員"
                                    "有碼類別", "無碼類別" -> "類別"
                                    else -> it.breadcrumbType
                                }
                                "$typeLabel: ${it.breadcrumbName}"
                            } else null
                        }
                    )
                }
            } catch (e: Exception) {
                _uiState.update { it.copy(isLoading = false, isFilterSwitching = false, error = e.message ?: "載入失敗") }
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
        if (nextPage <= currentPage) return

        currentPage = nextPage
        viewModelScope.launch {
            _uiState.update { it.copy(isLoadingMore = true) }
            try {
                val result = repository.loadPageByUrl(linkUrl, nextPage, showAll = _uiState.value.showAll)
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
                currentPage = state.pageInfo.activePage
                _uiState.update { it.copy(isLoadingMore = false, error = e.message) }
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
        if (_uiState.value.isRefreshing || linkUrl.isBlank()) return
        currentPage = 1
        viewModelScope.launch {
            _uiState.update { it.copy(isRefreshing = true, error = null) }
            try {
                val result = repository.loadPageByUrl(linkUrl, 1, showAll = _uiState.value.showAll, forceRefresh = true)
                _uiState.update {
                    it.copy(
                        movies = result.movies.map { m -> m.toUiModel() },
                        pageInfo = result.pageInfo,
                        isRefreshing = false,
                        hasMore = result.pageInfo.hasNext,
                        filterInfo = result.filterInfo
                    )
                }
            } catch (e: Exception) {
                _uiState.update { it.copy(isRefreshing = false, error = e.message) }
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
            _uiState.update { it.copy(isLoadingActress = true) }
            try {
                val detail = repository.loadActressDetail(linkUrl, forceRefresh = forceRefresh)
                if (detail != null) {
                    _uiState.update {
                        it.copy(
                            actressDetail = ActressDetailUiModel(
                                detail.name,
                                detail.avatar,
                                detail.info
                            ),
                            isLoadingActress = false,
                            resolvedTitle = "演員: ${detail.name}"
                        )
                    }
                    val actress = ActressInfo(
                        name = detail.name,
                        avatar = detail.avatar,
                        link = linkUrl
                    )
                    val collected = collectRepository.isActressCollected(actress)
                    _uiState.update { it.copy(isCollected = collected) }
                } else {
                    _uiState.update { it.copy(isLoadingActress = false) }
                }
            } catch (_: Exception) {
                _uiState.update { it.copy(isLoadingActress = false) }
            }
        }
    }

    /**
     * 切换当前女优的收藏状态。
     *
     * 如果已收藏则取消收藏，反之则添加收藏。仅在女优详情已加载时生效。
     */
    fun toggleActressCollect() {
        val actressDetail = _uiState.value.actressDetail ?: return
        viewModelScope.launch {
            val actress = ActressInfo(
                name = actressDetail.name,
                avatar = actressDetail.avatar,
                link = linkUrl
            )
            val newState = collectRepository.toggleActressCollect(actress)
            _uiState.update { it.copy(isCollected = newState) }
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
        currentPage = 0
        loadFirstPage()
    }

    @AssistedFactory
    interface Factory {
        fun create(navKey: RouteLinkMovies): LinkMovieListViewModel
    }
}

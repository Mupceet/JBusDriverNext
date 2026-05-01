package me.jbusdriver.modern.ui.movielist

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import me.jbusdriver.modern.data.MovieRepository
import me.jbusdriver.modern.data.model.PageInfo
import me.jbusdriver.modern.data.model.hasNext
import me.jbusdriver.modern.ui.ActressUiModel
import me.jbusdriver.modern.ui.toActressUiModel
import me.jbusdriver.modern.domain.model.DataSourceType
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
    val error: String? = null
)

/**
 * 女优列表页 ViewModel。
 *
 * 职责：管理按分类分页加载女优列表，支持下拉刷新和加载更多。
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
    private var currentPage = 0
    /** 当前的数据源类型（默认为女优分类） */
    private var dataSourceType: DataSourceType = DataSourceType.ACTRESSES

    /**
     * 设置数据源类型并重新加载列表。
     *
     * 如果类型未变化且列表中已有数据，则跳过重复加载。
     * 重置页码和 UI 状态后自动调用 [loadFirstPage]。
     *
     * @param type 数据源类型，如 [DataSourceType.ACTRESSES] 等
     */
    fun setDataSourceType(type: DataSourceType) {
        if (dataSourceType == type && _uiState.value.actresses.isNotEmpty()) return
        dataSourceType = type
        currentPage = 0
        _uiState.value = ActressListUiState()
        loadFirstPage()
    }

    /**
     * 加载女优列表的第一页数据。
     *
     * 如果已在加载中则跳过。加载完成后更新分页信息和是否有更多数据的状态。
     */
    fun loadFirstPage() {
        if (_uiState.value.isLoading) return
        currentPage = 1
        viewModelScope.launch(Dispatchers.IO) {
            _uiState.update { it.copy(isLoading = true, error = null) }
            try {
                val result = repository.loadActresses(dataSourceType, 1)
                _uiState.update {
                    it.copy(
                        actresses = result.first.map { a -> a.toActressUiModel() },
                        pageInfo = result.second,
                        isLoading = false,
                        hasMore = result.second.hasNext,
                        error = if (result.first.isEmpty()) "没有数据" else null
                    )
                }
            } catch (e: Exception) {
                _uiState.update { it.copy(isLoading = false, error = e.message ?: "加载失败") }
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
        currentPage = 1
        viewModelScope.launch(Dispatchers.IO) {
            _uiState.update { it.copy(isRefreshing = true, error = null) }
            try {
                val result = repository.loadActresses(dataSourceType, 1, forceRefresh = true)
                _uiState.update {
                    it.copy(
                        actresses = result.first.map { a -> a.toActressUiModel() },
                        pageInfo = result.second,
                        isRefreshing = false,
                        hasMore = result.second.hasNext
                    )
                }
            } catch (e: Exception) {
                _uiState.update { it.copy(isRefreshing = false, error = e.message) }
            }
        }
    }

    /**
     * 加载下一页女优数据，追加到现有列表末尾。
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
                currentPage = state.pageInfo.activePage
                _uiState.update { it.copy(isLoadingMore = false, error = e.message) }
            }
        }
    }
}

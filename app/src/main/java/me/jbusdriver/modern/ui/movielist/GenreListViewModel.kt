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
import me.jbusdriver.modern.domain.model.DataSourceType
import javax.inject.Inject

/**
 * 分类列表页的 UI 状态。
 *
 * 包含按类别分组的分类列表数据、加载和刷新状态以及错误信息。
 */
data class GenreListUiState(
    /** 按类别分组的分类列表，每个元素包含类别标题和该类别下的分类列表 */
    val genreCategories: List<GenreCategory> = emptyList(),
    /** 是否正在加载分类数据 */
    val isLoading: Boolean = false,
    /** 是否正在下拉刷新 */
    val isRefreshing: Boolean = false,
    /** 错误信息，正常时为 null */
    val error: String? = null
)

/**
 * 分类列表页 ViewModel。
 *
 * 职责：管理按分类类型（类型/玩法等）加载分类列表，支持下拉刷新。
 *
 * 使用场景：在主界面的分类 Tab 页面中使用，通过 Hilt 注入。
 * 用户切换分类类型标签时切换数据源并重新加载分类列表。
 *
 * 线程：网络请求在 [Dispatchers.IO] 上执行，UI 状态通过 [MutableStateFlow] 更新。
 *
 * @param repository 电影数据仓库，负责从网络获取和解析分类列表
 */
@HiltViewModel
class GenreListViewModel @Inject constructor(
    private val repository: MovieRepository
) : ViewModel() {

    /** 内部可变的 UI 状态 */
    private val _uiState = MutableStateFlow(GenreListUiState())
    /** 对外暴露的只读 UI 状态流 */
    val uiState: StateFlow<GenreListUiState> = _uiState.asStateFlow()

    /** 当前的数据源类型（默认为分类类型） */
    private var dataSourceType: DataSourceType = DataSourceType.GENRE

    /**
     * 设置数据源类型并重新加载分类列表。
     *
     * 如果类型未变化且列表中已有数据，则跳过重复加载。
     * 重置 UI 状态后自动调用内部的 [loadGenres] 方法。
     *
     * @param type 数据源类型，如 [DataSourceType.GENRE] 等
     */
    fun setDataSourceType(type: DataSourceType) {
        if (dataSourceType == type && _uiState.value.genreCategories.isNotEmpty()) return
        dataSourceType = type
        _uiState.value = GenreListUiState()
        loadGenres()
    }

    /**
     * 从网络加载分类列表数据。
     *
     * 如果已在加载中则跳过。加载完成后将分类数据按类别分组存储。
     */
    private fun loadGenres() {
        if (_uiState.value.isLoading) return
        viewModelScope.launch(Dispatchers.IO) {
            _uiState.update { it.copy(isLoading = true, error = null) }
            try {
                val categories = repository.loadGenreCategories(dataSourceType)
                _uiState.update {
                    it.copy(
                        genreCategories = categories,
                        isLoading = false,
                        error = if (categories.isEmpty()) "没有数据" else null
                    )
                }
            } catch (e: Exception) {
                _uiState.update { it.copy(isLoading = false, error = e.message ?: "加载失败") }
            }
        }
    }

    /**
     * 下拉刷新分类列表。
     *
     * 强制从网络重新获取分类数据，忽略缓存。如果正在刷新中则跳过。
     */
    fun refresh() {
        if (_uiState.value.isRefreshing) return
        viewModelScope.launch(Dispatchers.IO) {
            _uiState.update { it.copy(isRefreshing = true, error = null) }
            try {
                val categories = repository.loadGenreCategories(dataSourceType, forceRefresh = true)
                _uiState.update {
                    it.copy(
                        genreCategories = categories,
                        isRefreshing = false
                    )
                }
            } catch (e: Exception) {
                _uiState.update { it.copy(isRefreshing = false, error = e.message) }
            }
        }
    }
}

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
import me.jbusdriver.modern.domain.model.DataSourceType
import me.jbusdriver.modern.ui.GenreCategory
import me.jbusdriver.modern.ui.toUiModel
import javax.inject.Inject

// TODO: remove after testing cache refresh UX — 随机删除或重复前几项，模拟数据变化
private fun <T> List<T>.shuffledForTesting(): List<T> {
    if (size < 3) return this
    val result = toMutableList()
    val ops = (1..2).random()
    repeat(ops) {
        when ((0..1).random()) {
            0 -> {
                val idx = (0 until minOf(3, result.size - 1)).random()
                result.removeAt(idx)
            }
            1 -> {
                val idx = (0 until minOf(3, result.size)).random()
                result.add(idx, result[idx])
            }
        }
    }
    return result
}

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
    val error: String? = null,
    /** 后台刷新中（有缓存数据时显示顶部进度条） */
    val isRevalidating: Boolean = false,
    /** 缓存数据的时间戳 */
    val lastUpdatedAtMillis: Long? = null,
    /** 轻量刷新反馈消息（Snackbar） */
    val refreshMessage: String? = null
)

/**
 * 分类列表页 ViewModel。
 *
 * 职责：管理按分类类型（类型/玩法等）加载分类列表，支持下拉刷新。
 * 采用 stale-while-revalidate 策略：先显示缓存数据，后台静默刷新后无缝更新。
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
        if (dataSourceType == type && _uiState.value.genreCategories.isNotEmpty()) {
            revalidate()
            return
        }
        dataSourceType = type
        _uiState.value = GenreListUiState()
        loadGenres()
    }

    /**
     * 从缓存和网络加载分类列表数据（stale-while-revalidate）。
     *
     * 先显示缓存数据，再后台刷新。如果已在加载中则跳过。
     */
    private fun loadGenres() {
        if (_uiState.value.isLoading) return
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true, error = null, refreshMessage = null) }
            var hasContent = false
            repository.observeGenreCategories(dataSourceType, revalidate = true)
                .collect { event ->
                    when (event) {
                        is CachedLoadEvent.Cached -> {
                            hasContent = true
                            val categories = event.entry.value.map { it.toUiModel() }
                            _uiState.update {
                                it.copy(
                                    genreCategories = categories,
                                    isLoading = false,
                                    error = if (categories.isEmpty()) "沒有數據" else null,
                                    isRevalidating = true,
                                    lastUpdatedAtMillis = event.entry.storedAtMillis
                                )
                            }
                        }
                        is CachedLoadEvent.Fresh -> {
                            val categories = event.entry.value.shuffledForTesting().map { it.toUiModel() }
                            _uiState.update {
                                it.copy(
                                    genreCategories = categories,
                                    isLoading = false,
                                    isRevalidating = false,
                                    lastUpdatedAtMillis = event.entry.storedAtMillis
                                )
                            }
                        }
                        is CachedLoadEvent.Failure -> {
                            _uiState.update {
                                if (event.hadCachedValue || hasContent) {
                                    it.copy(isLoading = false, isRevalidating = false)
                                } else {
                                    it.copy(isLoading = false, isRevalidating = false, error = event.throwable.message ?: "載入失敗")
                                }
                            }
                        }
                    }
                }
        }
    }

    /**
     * 后台重新验证数据（Tab 切换回来或从后台恢复时触发）。
     */
    fun revalidate() {
        val state = _uiState.value
        if (state.isRevalidating || state.isLoading || state.isRefreshing) return
        if (state.genreCategories.isEmpty()) return
        viewModelScope.launch {
            repository.observeGenreCategories(dataSourceType, revalidate = false)
                .collect { event ->
                    when (event) {
                        is CachedLoadEvent.Cached -> Unit
                        is CachedLoadEvent.Fresh -> {
                            val categories = event.entry.value.shuffledForTesting().map { it.toUiModel() }
                            _uiState.update {
                                it.copy(
                                    genreCategories = categories,
                                    isRevalidating = false,
                                    lastUpdatedAtMillis = event.entry.storedAtMillis
                                )
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
     * 下拉刷新分类列表。
     *
     * 强制从网络重新获取分类数据，忽略缓存。如果正在刷新中则跳过。
     */
    fun refresh() {
        if (_uiState.value.isRefreshing) return
        viewModelScope.launch {
            _uiState.update { it.copy(isRefreshing = true, error = null, refreshMessage = null) }
            repository.observeGenreCategories(dataSourceType, forceRefresh = true, revalidate = false)
                .collect { event ->
                    when (event) {
                        is CachedLoadEvent.Cached -> Unit
                        is CachedLoadEvent.Fresh -> {
                            val categories = event.entry.value.shuffledForTesting().map { it.toUiModel() }
                            _uiState.update {
                                it.copy(
                                    genreCategories = categories,
                                    isRefreshing = false
                                )
                            }
                        }
                        is CachedLoadEvent.Failure -> {
                            _uiState.update {
                                it.copy(
                                    isRefreshing = false,
                                    error = if (it.genreCategories.isEmpty()) event.throwable.message ?: "載入失敗" else it.error,
                                    refreshMessage = if (it.genreCategories.isNotEmpty()) "刷新失敗" else null
                                )
                            }
                        }
                    }
                }
        }
    }

    /** 消费轻量刷新消息（Snackbar） */
    fun consumeRefreshMessage() {
        _uiState.update { it.copy(refreshMessage = null) }
    }
}

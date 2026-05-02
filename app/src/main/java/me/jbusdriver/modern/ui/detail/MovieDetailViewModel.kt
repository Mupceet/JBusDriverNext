package me.jbusdriver.modern.ui.detail

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import me.jbusdriver.modern.data.CollectRepository
import me.jbusdriver.modern.data.MovieDetailRepository
import me.jbusdriver.modern.data.magnet.Magnet
import me.jbusdriver.modern.data.magnet.MagnetManager
import me.jbusdriver.modern.domain.model.Movie
import me.jbusdriver.modern.ui.MagnetUiModel
import me.jbusdriver.modern.ui.MovieDetailUiModel
import me.jbusdriver.modern.ui.toUiModel
import org.json.JSONArray
import javax.inject.Inject

/**
 * 电影详情页的 UI 状态。
 *
 * 包含电影详情数据、磁力链接列表、收藏状态及各加载/错误标志。
 */
data class MovieDetailUiState(
    /** 是否正在加载电影详情 */
    val isLoading: Boolean = false,
    /** 是否正在下拉刷新 */
    val isRefreshing: Boolean = false,
    /** 电影详情数据，加载成功后非 null */
    val movieDetail: MovieDetailUiModel? = null,
    /** 错误信息，正常时为 null */
    val error: String? = null,
    /** 当前已加载的磁力链接列表 */
    val magnets: List<MagnetUiModel> = emptyList(),
    /** 是否正在加载磁力链接 */
    val isLoadingMagnets: Boolean = false,
    /** 磁力链接加载错误信息 */
    val magnetsError: String? = null,
    /** 是否还有更多磁力链接可加载 */
    val hasMoreMagnets: Boolean = true,
    /** 当前电影是否已收藏 */
    val isCollected: Boolean = false
)

/**
 * 电影详情页 ViewModel。
 *
 * 职责：管理电影详情数据的加载、磁力链接的分页获取、以及收藏状态的切换。
 *
 * 使用场景：在电影详情页面（MovieDetailScreen）中使用，通过 Hilt 注入。
 * 用户进入详情页时加载电影信息和磁力链接，支持下拉刷新和磁力链接分页加载更多，
 * 以及切换电影的收藏状态。
 *
 * 线程：网络请求通过 Repository 内部的调度器执行；磁力链接解析使用 [Dispatchers.IO]。
 * UI 状态更新通过 [MutableStateFlow] 在主线程进行。
 *
 * @param repository 电影详情数据仓库，负责获取电影详情 HTML 并解析
 * @param collectRepository 收藏仓库，负责查询和切换收藏状态
 */
@HiltViewModel
class MovieDetailViewModel @Inject constructor(
    private val repository: MovieDetailRepository,
    private val collectRepository: CollectRepository
) : ViewModel() {

    /** 内部可变的 UI 状态 */
    private val _uiState = MutableStateFlow(MovieDetailUiState())

    /** 对外暴露的只读 UI 状态流 */
    val uiState: StateFlow<MovieDetailUiState> = _uiState.asStateFlow()

    /** 当前正在查看的电影页面 URL */
    private var currentUrl: String = ""

    /** 磁力链接的当前分页页码 */
    private var magnetPage: Int = 0

    /**
     * 加载指定 URL 的电影详情。
     *
     * 加载成功后会自动检查该电影的收藏状态。如果已在加载中则跳过。
     *
     * @param url 电影详情页的完整 URL
     */
    fun loadDetail(url: String) {
        if (_uiState.value.isLoading) return
        currentUrl = url
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true, error = null) }
            try {
                val detail = repository.getMovieDetail(url)
                _uiState.update { it.copy(movieDetail = detail.toUiModel(), isLoading = false) }
                // Check collection state
                val movie = Movie(
                    title = detail.title,
                    imageUrl = detail.cover,
                    code = detail.headers.firstOrNull()?.value ?: "",
                    date = "",
                    link = url
                )
                val collected = collectRepository.isMovieCollected(movie)
                _uiState.update { it.copy(isCollected = collected) }
            } catch (e: Exception) {
                _uiState.update { it.copy(isLoading = false, error = e.message ?: "加载失败") }
            }
        }
    }

    /**
     * 下拉刷新当前电影的详情数据。
     *
     * 强制从网络重新获取，忽略缓存。仅在已加载过详情后才生效。
     */
    fun refresh() {
        if (currentUrl.isBlank()) return
        viewModelScope.launch {
            _uiState.update { it.copy(isRefreshing = true, error = null) }
            try {
                val detail = repository.getMovieDetail(currentUrl, forceRefresh = true)
                _uiState.update { it.copy(movieDetail = detail.toUiModel(), isRefreshing = false) }
            } catch (e: Exception) {
                _uiState.update { it.copy(isRefreshing = false, error = e.message) }
            }
        }
    }

    /**
     * 加载当前电影的磁力链接（第一页）。
     *
     * 通过 [MagnetManager] 获取磁力链接数据并解析为 UI 模型列表。
     * 重置分页计数器为 1。
     */
    fun loadMagnets() {
        if (_uiState.value.isLoadingMagnets) return
        val url = currentUrl.ifBlank { return }
        magnetPage = 1
        viewModelScope.launch {
            _uiState.update { it.copy(isLoadingMagnets = true, magnetsError = null) }
            try {
                val magnets = withContext(kotlinx.coroutines.Dispatchers.IO) {
                    fetchMagnets(url, 1)
                }
                _uiState.update {
                    it.copy(
                        magnets = magnets,
                        isLoadingMagnets = false,
                        hasMoreMagnets = MagnetManager.hasNext("default")
                    )
                }
            } catch (e: Exception) {
                _uiState.update { it.copy(isLoadingMagnets = false, magnetsError = e.message) }
            }
        }
    }

    /**
     * 加载下一页磁力链接，追加到现有列表末尾。
     *
     * 如果正在加载或没有更多数据则跳过。加载失败时回退页码计数器。
     */
    fun loadMoreMagnets() {
        if (_uiState.value.isLoadingMagnets || !_uiState.value.hasMoreMagnets) return
        val url = currentUrl.ifBlank { return }
        magnetPage++
        viewModelScope.launch {
            try {
                val more = withContext(kotlinx.coroutines.Dispatchers.IO) {
                    fetchMagnets(url, magnetPage)
                }
                _uiState.update {
                    it.copy(
                        magnets = it.magnets + more,
                        hasMoreMagnets = MagnetManager.hasNext("default")
                    )
                }
            } catch (e: Exception) {
                magnetPage--
            }
        }
    }

    /**
     * 通过 MagnetManager 获取指定关键词和页码的磁力链接，并解析为 UI 模型列表。
     *
     * @param keyword 搜索关键词（通常是电影页面的 URL）
     * @param page 磁力链接的分页页码
     * @return 解析后的磁力链接 UI 模型列表
     */
    private fun fetchMagnets(keyword: String, page: Int): List<MagnetUiModel> {
        val json = MagnetManager.getMagnets("default", keyword, page)
        val arr = JSONArray(json)
        return (0 until arr.length()).mapNotNull { i ->
            val obj = arr.optJSONObject(i) ?: return@mapNotNull null
            Magnet(
                name = obj.optString("name", ""),
                size = obj.optString("size", ""),
                date = obj.optString("date", ""),
                link = obj.optString("link", "")
            ).toUiModel()
        }
    }

    /** 清除当前的错误信息 */
    fun clearError() {
        _uiState.update { it.copy(error = null) }
    }

    /**
     * 切换当前电影的收藏状态。
     *
     * 如果已收藏则取消收藏，反之则添加收藏。仅在详情已加载时生效。
     */
    fun toggleCollect() {
        val detail = _uiState.value.movieDetail ?: return
        val url = currentUrl
        viewModelScope.launch {
            val movie = Movie(
                title = detail.title,
                imageUrl = detail.cover,
                code = detail.headers.firstOrNull()?.value ?: "",
                date = "",
                link = url
            )
            val newState = collectRepository.toggleMovieCollect(movie)
            _uiState.update { it.copy(isCollected = newState) }
        }
    }
}

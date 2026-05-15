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
import me.jbusdriver.modern.data.CollectRepository
import me.jbusdriver.modern.data.db.ActressDBType
import me.jbusdriver.modern.data.db.MovieDBType
import me.jbusdriver.modern.data.db.entity.LinkItem
import me.jbusdriver.modern.domain.model.urlPath
import me.jbusdriver.modern.ui.ActressUiModel
import me.jbusdriver.modern.ui.MovieUiModel
import me.jbusdriver.modern.ui.toActressUiModel
import me.jbusdriver.modern.ui.toUiModel
import javax.inject.Inject

/**
 * 收藏列表页的 UI 状态。
 *
 * 包含电影收藏列表和女优收藏列表（互斥展示）、加载状态和错误信息。
 */
data class CollectionListUiState(
    /** 已收藏的电影列表 */
    val movies: List<MovieUiModel> = emptyList(),
    /** 已收藏的女优列表 */
    val actresses: List<ActressUiModel> = emptyList(),
    /** 已收藏的电影总数 */
    val movieCount: Int = 0,
    /** 已收藏的女优总数 */
    val actressCount: Int = 0,
    /** 是否正在加载收藏数据 */
    val isLoading: Boolean = false,
    /** 错误信息，正常时为 null */
    val error: String? = null
)

/**
 * 收藏列表页 ViewModel。
 *
 * 职责：从本地数据库加载用户收藏的电影或女优列表。
 *
 * 使用场景：在收藏页面中使用，通过 Hilt 注入。用户切换收藏类型（电影/女优）时
 * 调用 [loadCollection] 加载对应数据。收藏数据来自本地 Room 数据库，不涉及网络请求。
 *
 * 线程：数据库查询在 [Dispatchers.IO] 上执行。
 *
 * @param collectRepository 收藏仓库，负责查询本地收藏数据
 */
@HiltViewModel
class CollectionListViewModel @Inject constructor(
    private val collectRepository: CollectRepository
) : ViewModel() {

    /** 内部可变的 UI 状态 */
    private val _uiState = MutableStateFlow(CollectionListUiState())

    /** 对外暴露的只读 UI 状态流 */
    val uiState: StateFlow<CollectionListUiState> = _uiState.asStateFlow()

    /**
     * 加载指定类型的收藏列表。
     *
     * 根据 [dbType] 区分加载电影收藏还是女优收藏。每次加载会清空之前的列表数据。
     *
     * @param dbType 数据库类型常量，[MovieDBType] 加载电影收藏，[ActressDBType] 加载女优收藏
     */
    fun loadCollection(dbType: Int) {
        viewModelScope.launch(Dispatchers.IO) {
            _uiState.update {
                it.copy(
                    isLoading = true,
                    error = null,
                    movies = emptyList(),
                    actresses = emptyList()
                )
            }
            try {
                if (dbType == MovieDBType) {
                    val movies = collectRepository.getCollectedMovies().map { it.toUiModel() }
                    val actressCount = collectRepository.getCollectedActresses().size
                    _uiState.update {
                        it.copy(
                            movies = movies,
                            movieCount = movies.size,
                            actressCount = actressCount,
                            isLoading = false
                        )
                    }
                } else {
                    val actresses = collectRepository.getCollectedActresses().map { it.toActressUiModel() }
                    val movieCount = collectRepository.getCollectedMovies().size
                    _uiState.update {
                        it.copy(
                            actresses = actresses,
                            actressCount = actresses.size,
                            movieCount = movieCount,
                            isLoading = false
                        )
                    }
                }
            } catch (e: Exception) {
                _uiState.update { it.copy(isLoading = false, error = e.message ?: "載入收藏失敗") }
            }
        }
    }

    /**
     * 从收藏中移除指定电影。
     *
     * 使用电影详情页 URL 路径作为 key（与 [ILink.uniqueKey] 一致），
     * 移除后自动重新加载电影收藏列表。
     *
     * @param movie 要移除的电影 UI 模型
     */
    fun removeMovie(movie: MovieUiModel) {
        viewModelScope.launch(Dispatchers.IO) {
            try {
                val linkItem = LinkItem(
                    dbType = MovieDBType,
                    key = movie.link.urlPath,
                    jsonStr = "",
                    createTime = System.currentTimeMillis()
                )
                collectRepository.removeCollect(linkItem)
                loadCollection(MovieDBType)
            } catch (_: Exception) {}
        }
    }

    /**
     * 从收藏中移除指定演员。
     *
     * 使用演员页面 URL 路径作为 key（与 [ILink.uniqueKey] 一致），
     * 移除后自动重新加载演员收藏列表。
     *
     * @param actress 要移除的演员 UI 模型
     */
    fun removeActress(actress: ActressUiModel) {
        viewModelScope.launch(Dispatchers.IO) {
            try {
                val linkItem = LinkItem(
                    dbType = ActressDBType,
                    key = actress.link.urlPath,
                    jsonStr = "",
                    createTime = System.currentTimeMillis()
                )
                collectRepository.removeCollect(linkItem)
                loadCollection(ActressDBType)
            } catch (_: Exception) {}
        }
    }

    /**
     * 刷新收藏列表，等同于重新加载。
     *
     * @param dbType 数据库类型常量，同 [loadCollection]
     */
    fun refresh(dbType: Int) {
        loadCollection(dbType)
    }
}

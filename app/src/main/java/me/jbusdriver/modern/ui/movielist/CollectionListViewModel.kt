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
import me.jbusdriver.modern.data.db.toILink
import me.jbusdriver.modern.domain.model.ActressInfo
import me.jbusdriver.modern.domain.model.Movie
import me.jbusdriver.modern.domain.model.urlPath
import me.jbusdriver.modern.ui.ActressUiModel
import me.jbusdriver.modern.ui.MovieUiModel
import me.jbusdriver.modern.ui.toActressUiModel
import me.jbusdriver.modern.ui.toUiModel
import java.util.Calendar
import javax.inject.Inject

/**
 * 收藏列表页的 UI 状态。
 *
 * [movies] 和 [actresses] 为经过筛选和排序后的展示列表。
 * [movieCount] 和 [actressCount] 为未筛选的总数（用于 tab badge）。
 */
data class CollectionListUiState(
    val movies: List<MovieUiModel> = emptyList(),
    val actresses: List<ActressUiModel> = emptyList(),
    val movieCount: Int = 0,
    val actressCount: Int = 0,
    val isLoading: Boolean = false,
    val error: String? = null,
    val filterState: CollectionFilterState = CollectionFilterState(),
    val availableYears: AvailableYears = AvailableYears()
)

/**
 * 收藏列表页 ViewModel。
 *
 * 职责：从本地数据库加载用户收藏，支持筛选和排序。
 * 筛选和排序在内存中完成，不涉及数据库查询变更。
 *
 * @param collectRepository 收藏仓库，负责查询本地收藏数据
 */
@HiltViewModel
class CollectionListViewModel @Inject constructor(
    val collectRepository: CollectRepository
) : ViewModel() {

    private val _uiState = MutableStateFlow(CollectionListUiState())
    val uiState: StateFlow<CollectionListUiState> = _uiState.asStateFlow()

    /** 未筛选的原始影片数据 */
    private var allMovies: List<MovieUiModel> = emptyList()
    /** 未筛选的原始演员数据 */
    private var allActresses: List<ActressUiModel> = emptyList()
    private var currentDbType: Int = MovieDBType

    /**
     * 加载指定类型的收藏列表。
     *
     * 同时加载两种类型的数据（用于 tab badge 计数和收藏时间年份提取），
     * 但仅对 [dbType] 对应的列表应用当前筛选。
     */
    fun loadCollection(dbType: Int) {
        currentDbType = dbType
        viewModelScope.launch(Dispatchers.IO) {
            _uiState.update { it.copy(isLoading = true, error = null) }
            try {
                val movieItems = collectRepository.getCollectedLinkItems(MovieDBType)
                val actressItems = collectRepository.getCollectedLinkItems(ActressDBType)

                allMovies = movieItems.mapNotNull { item ->
                    ((item.toILink() as? Movie)?.toUiModel())
                        ?.copy(createTime = item.createTime)
                }
                allActresses = actressItems.mapNotNull { item ->
                    ((item.toILink() as? ActressInfo)?.toActressUiModel())
                        ?.copy(createTime = item.createTime)
                }

                updateAvailableYears()
                applyFilterAndSort()
            } catch (e: Exception) {
                _uiState.update { it.copy(isLoading = false, error = e.message ?: "載入收藏失敗") }
            }
        }
    }

    /**
     * 更新筛选/排序条件，立即重新计算展示列表。
     */
    fun updateFilter(filterState: CollectionFilterState) {
        _uiState.update { it.copy(filterState = filterState) }
        applyFilterAndSort()
    }

    /**
     * 从收藏中移除指定电影。
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
     * 刷新收藏列表。
     */
    fun refresh(dbType: Int) {
        loadCollection(dbType)
    }

    // region 内部方法

    /** 从原始数据中提取可用年份，用于生成筛选 Chip */
    private fun updateAvailableYears() {
        val publishYears = allMovies
            .mapNotNull { it.date.take(4).toIntOrNull() }
            .distinct()
            .sortedDescending()

        val collectYears = (allMovies.map { it.createTime } + allActresses.map { it.createTime })
            .map { it.toYear() }
            .distinct()
            .sortedDescending()

        _uiState.update { it.copy(availableYears = AvailableYears(publishYears, collectYears)) }
    }

    /** 对原始数据应用当前筛选和排序，更新 UI 状态 */
    private fun applyFilterAndSort() {
        val filter = _uiState.value.filterState
        val years = _uiState.value.availableYears

        val filteredMovies = allMovies
            .filterByCensor(filter.censorFilter)
            .filterByPublishYear(filter.publishYear, years.publishYears)
            .filterByPublishMonth(filter.publishMonth)
            .filterByCollectYear(filter.collectYear, years.collectYears) { it.createTime }
            .sortedWith(filter.sortOption.toMovieComparator())

        val filteredActresses = allActresses
            .filterByCollectYear(filter.collectYear, years.collectYears) { it.createTime }
            .sortedWith(
                if (filter.sortOption in SortOption.actressOptions) filter.sortOption.toActressComparator()
                else SortOption.COLLECT_DESC.toActressComparator()
            )

        _uiState.update {
            it.copy(
                movies = filteredMovies,
                actresses = filteredActresses,
                movieCount = allMovies.size,
                actressCount = allActresses.size,
                isLoading = false
            )
        }
    }

// endregion
}

// region 筛选扩展函数

private fun List<MovieUiModel>.filterByCensor(censor: CensorFilter): List<MovieUiModel> =
    when (censor) {
        CensorFilter.ALL -> this
        CensorFilter.CENSORED -> filter { !it.link.urlPath.startsWith("/uncensored/") }
        CensorFilter.UNCENSORED -> filter { it.link.urlPath.startsWith("/uncensored/") }
    }

private fun List<MovieUiModel>.filterByPublishYear(
    year: Int?,
    available: List<Int>
): List<MovieUiModel> =
    if (year == null) this
    else filter { m ->
        val mYear = m.date.take(4).toIntOrNull() ?: return@filter false
        if (year == -1) mYear < (available.minOrNull() ?: Int.MAX_VALUE)
        else mYear == year
    }

private fun List<MovieUiModel>.filterByPublishMonth(month: Int?): List<MovieUiModel> =
    if (month == null) this
    else filter { m ->
        m.date.length >= 7 && m.date.substring(5, 7).toIntOrNull() == month
    }

private fun <T> List<T>.filterByCollectYear(
    year: Int?,
    available: List<Int>,
    getTime: (T) -> Long
): List<T> =
    if (year == null) this
    else filter { item ->
        val itemYear = getTime(item).toYear()
        if (year == -1) itemYear < (available.minOrNull() ?: Int.MAX_VALUE)
        else itemYear == year
    }

private fun SortOption.toMovieComparator(): Comparator<MovieUiModel> = when (this) {
    SortOption.COLLECT_DESC -> compareByDescending { it.createTime }
    SortOption.COLLECT_ASC -> compareBy { it.createTime }
    SortOption.PUBLISH_DESC -> compareByDescending { it.date }
    SortOption.PUBLISH_ASC -> compareBy { it.date }
}

private fun SortOption.toActressComparator(): Comparator<ActressUiModel> = when (this) {
    SortOption.COLLECT_DESC -> compareByDescending { it.createTime }
    SortOption.COLLECT_ASC -> compareBy { it.createTime }
    else -> compareByDescending { it.createTime }
}

/** 将毫秒时间戳转换为年份 */
private fun Long.toYear(): Int =
    Calendar.getInstance().apply { timeInMillis = this@toYear }.get(Calendar.YEAR)

// endregion

package me.jbusdriver.modern.ui.movielist

import me.jbusdriver.modern.ui.movielist.SortOption.Companion.actressOptions
import me.jbusdriver.modern.ui.movielist.SortOption.Companion.movieOptions


/**
 * 內容類型篩選（有碼/無碼）
 */
enum class CensorFilter { ALL, CENSORED, UNCENSORED }

/**
 * 排序選項。
 *
 * [label] 為 Bottom Sheet 排序下拉中顯示的文本。
 * [movieOptions] 影片列表可用的排序選項。
 * [actressOptions] 演員列表可用的排序選項（僅收藏時間）。
 */
enum class SortOption(val label: String) {
    COLLECT_DESC("收藏時間倒序"),
    COLLECT_ASC("收藏時間正序"),
    PUBLISH_DESC("發佈時間倒序"),
    PUBLISH_ASC("發佈時間正序");

    companion object {
        val movieOptions: List<SortOption> = entries
        val actressOptions: List<SortOption> = listOf(COLLECT_DESC, COLLECT_ASC)
    }
}

/**
 * 收藏列表的筛选和排序状态。
 *
 * 年份字段用 Int? 表示：null = 全部，正整数 = 具体年份，-1 = 更早（早于数据中最早年份）。
 */
data class CollectionFilterState(
    val censorFilter: CensorFilter = CensorFilter.ALL,
    val publishYear: Int? = null,
    val publishMonth: Int? = null,
    val collectYear: Int? = null,
    val collectMonth: Int? = null,
    val onlyDownloaded: Boolean = false,
    val sortOption: SortOption = SortOption.COLLECT_DESC
) {
    /** 是否有非默认的筛选条件（排序不算） */
    val hasActiveFilters: Boolean
        get() = censorFilter != CensorFilter.ALL
                || publishYear != null
                || publishMonth != null
                || collectYear != null
                || collectMonth != null
                || onlyDownloaded

    /** 激活的筛选条件数量（用于筛选按钮上的 badge） */
    val activeFilterCount: Int
        get() = listOf(
            censorFilter != CensorFilter.ALL,
            publishYear != null,
            publishMonth != null,
            collectYear != null,
            collectMonth != null,
            onlyDownloaded
        ).count { it }
}

/**
 * 从收藏数据中动态提取的可用年份列表（降序）。
 *
 * 用于生成筛选面板中的年份 Chip。
 */
data class AvailableYears(
    val publishYears: List<Int> = emptyList(),
    val collectYears: List<Int> = emptyList()
)

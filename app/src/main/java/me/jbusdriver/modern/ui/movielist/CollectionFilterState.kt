package me.jbusdriver.modern.ui.movielist

import androidx.annotation.StringRes
import me.jbusdriver.R


/**
 * 內容類型篩選（有碼/無碼）
 */
enum class CensorFilter { ALL, CENSORED, UNCENSORED }

/**
 * 本地視頻篩選（全部 / 已下載 / 未下載）。
 */
enum class LocalVideoFilter { ALL, DOWNLOADED, NOT_DOWNLOADED }

/**
 * 排序內容（依據的欄位），篩選彈窗排序下拉顯示文本見 [labelRes]。
 */
enum class SortField(@StringRes val labelRes: Int) {
    COLLECT(R.string.collect_time),
    PUBLISH(R.string.sort_field_publish),
    CODE(R.string.sort_field_code)
}

/**
 * 排序順序，篩選彈窗排序下拉顯示文本見 [labelRes]。
 */
enum class SortDirection(@StringRes val labelRes: Int) {
    DESC(R.string.sort_dir_desc),
    ASC(R.string.sort_dir_asc)
}

/**
 * 合併排序選項（內容 + 順序），同時是收藏排序的持久化格式（DataStore 存枚舉名），
 * 新增項不改既有枚舉名以保持舊存檔兼容。
 *
 * [movieOptions] 影片列表可用的排序選項。
 * [actressOptions] 演員列表可用的排序選項（僅收藏時間）。
 */
enum class SortOption {
    COLLECT_DESC,
    COLLECT_ASC,
    PUBLISH_DESC,
    PUBLISH_ASC,
    CODE_DESC,
    CODE_ASC;

    /** 排序內容。 */
    val field: SortField
        get() = when (this) {
            COLLECT_DESC, COLLECT_ASC -> SortField.COLLECT
            PUBLISH_DESC, PUBLISH_ASC -> SortField.PUBLISH
            CODE_DESC, CODE_ASC -> SortField.CODE
        }

    /** 排序順序。 */
    val direction: SortDirection
        get() = if (name.endsWith("_ASC")) SortDirection.ASC else SortDirection.DESC

    companion object {
        val movieOptions: List<SortOption> = entries.toList()
        val actressOptions: List<SortOption> = listOf(COLLECT_DESC, COLLECT_ASC)

        /** 排序內容 + 順序 → 合併排序選項。 */
        fun of(field: SortField, direction: SortDirection): SortOption = when (direction) {
            SortDirection.ASC -> when (field) {
                SortField.COLLECT -> COLLECT_ASC
                SortField.PUBLISH -> PUBLISH_ASC
                SortField.CODE -> CODE_ASC
            }
            SortDirection.DESC -> when (field) {
                SortField.COLLECT -> COLLECT_DESC
                SortField.PUBLISH -> PUBLISH_DESC
                SortField.CODE -> CODE_DESC
            }
        }
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
    val localVideoFilter: LocalVideoFilter = LocalVideoFilter.ALL,
    val sortOption: SortOption = SortOption.COLLECT_DESC
) {
    /** 是否有非默认的筛选条件（排序不算） */
    val hasActiveFilters: Boolean
        get() = censorFilter != CensorFilter.ALL
                || publishYear != null
                || publishMonth != null
                || collectYear != null
                || collectMonth != null
                || localVideoFilter != LocalVideoFilter.ALL

    /** 激活的筛选条件数量（用于筛选按钮上的 badge） */
    val activeFilterCount: Int
        get() = listOf(
            censorFilter != CensorFilter.ALL,
            publishYear != null,
            publishMonth != null,
            collectYear != null,
            collectMonth != null,
            localVideoFilter != LocalVideoFilter.ALL
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

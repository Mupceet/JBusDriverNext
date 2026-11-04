package me.jbusdriver.modern.ui.movielist

import androidx.annotation.StringRes
import me.jbusdriver.R
import me.jbusdriver.modern.data.db.entity.LtxResolveState
import me.jbusdriver.modern.domain.model.LtxEntry
import me.jbusdriver.modern.domain.model.LtxLibrary
import me.jbusdriver.modern.domain.model.LtxLink
import me.jbusdriver.modern.domain.model.Movie
import me.jbusdriver.modern.ui.MovieUiModel
import me.jbusdriver.modern.ui.toUiModel

/**
 * LTX 列表排序选项。
 *
 * 入庫時間 = LTX 库 added_at；評分/大小来自库数据（全部条目可排）；
 * 發行時間仅对已补全条目有效（未补全排尾部）。
 */
enum class LtxSortOption(@StringRes val labelRes: Int) {
    ADDED_DESC(R.string.ltx_sort_added_desc),
    ADDED_ASC(R.string.ltx_sort_added_asc),
    CODE_DESC(R.string.ltx_sort_code_desc),
    CODE_ASC(R.string.ltx_sort_code_asc),
    DATE_DESC(R.string.ltx_sort_date_desc),
    DATE_ASC(R.string.ltx_sort_date_asc),
    SCORE_DESC(R.string.ltx_sort_score_desc),
    SCORE_ASC(R.string.ltx_sort_score_asc),
    SIZE_DESC(R.string.ltx_sort_size_desc),
    SIZE_ASC(R.string.ltx_sort_size_asc);

    companion object {
        fun fromName(name: String?): LtxSortOption =
            entries.firstOrNull { it.name == name } ?: ADDED_DESC
    }
}

/** LTX 链接确认状态筛选。 */
enum class LtxStatusFilter(@StringRes val labelRes: Int) {
    ALL(R.string.ltx_status_all),
    CONFIRMED(R.string.ltx_status_confirmed),
    PENDING(R.string.ltx_pending);
}

/** LTX 站内补全状态筛选。 */
enum class LtxResolveFilter(@StringRes val labelRes: Int) {
    ALL(R.string.ltx_status_all),
    RESOLVED(R.string.ltx_resolve_resolved),
    UNRESOLVED(R.string.ltx_resolve_unresolved);
}

/** LTX 列表单条 UI 模型：番号 + 状态 + 下载链接 + 站内补全快照。 */
data class LtxItemUiModel(
    val code: String,
    val isPending: Boolean,
    val isMissing: Boolean,
    val addedAtMillis: Long,
    val links: List<LtxLink>,
    /** 评分（源站数据，用于排序；无评分为 null）。 */
    val score: Double? = null,
    /** 已确认链接最大体积（字节），用于「大小」排序。 */
    val maxSizeBytes: Long = 0L,
    /** 站内补全的影片快照（URL 已按当前 baseUrl 重建）；未补全为 null。 */
    val movie: MovieUiModel? = null,
    /** 补全状态，见 [LtxResolveState]。 */
    val resolveState: String = LtxResolveState.UNRESOLVED,
)

data class LtxListUiState(
    val isLoading: Boolean = false,
    val isRefreshing: Boolean = false,
    val error: Int? = null,
    val items: List<LtxItemUiModel> = emptyList(),
    val totalCount: Int = 0,
    /** LTX 全库更新时间（毫秒）。 */
    val updatedAtMillis: Long = 0L,
    val sortOption: LtxSortOption = LtxSortOption.ADDED_DESC,
    val statusFilter: LtxStatusFilter = LtxStatusFilter.ALL,
    val resolveFilter: LtxResolveFilter = LtxResolveFilter.ALL,
    /** 排队或解析中的番号集合（归一化大写），用于「補全中」标记与进度指示。 */
    val resolvingCodes: Set<String> = emptySet(),
) {
    /** 筛选弹窗角标数：非默认的筛选条件个数（排序不计入）。 */
    val activeFilterCount: Int
        get() = listOf(statusFilter != LtxStatusFilter.ALL, resolveFilter != LtxResolveFilter.ALL)
            .count { it }

    val hasActiveFilters: Boolean get() = activeFilterCount > 0
}

/** 补全快照的 UI 投影：状态 + 已重建 URL 的影片快照（RESOLVED 时非空）。 */
data class LtxSnapshotUi(
    val resolveState: String,
    val movie: Movie? = null,
)

/**
 * 纯计算：LtxLibrary + 补全快照 → 应用排序/筛选后的列表项。
 *
 * @param snapshots 归一化番号 → 补全快照投影
 */
internal fun computeLtxListItems(
    library: LtxLibrary?,
    snapshots: Map<String, LtxSnapshotUi>,
    sortOption: LtxSortOption,
    statusFilter: LtxStatusFilter,
    resolveFilter: LtxResolveFilter,
): List<LtxItemUiModel> {
    if (library == null) return emptyList()
    val filtered = library.entries.asSequence()
        .filter { entry ->
            when (statusFilter) {
                LtxStatusFilter.ALL -> true
                LtxStatusFilter.CONFIRMED -> !entry.isPending
                LtxStatusFilter.PENDING -> entry.isPending
            }
        }
        .filter { entry ->
            val resolved = snapshots[entry.code.uppercase()]?.resolveState == LtxResolveState.RESOLVED
            when (resolveFilter) {
                LtxResolveFilter.ALL -> true
                LtxResolveFilter.RESOLVED -> resolved
                LtxResolveFilter.UNRESOLVED -> !resolved
            }
        }
        .map { entry -> entry.toLtxItemUiModel(snapshots[entry.code.uppercase()]) }
    return when (sortOption) {
        LtxSortOption.ADDED_DESC -> filtered.sortedByDescending { it.addedAtMillis }
        LtxSortOption.ADDED_ASC -> filtered.sortedBy { it.addedAtMillis }
        LtxSortOption.CODE_DESC -> filtered.sortedByDescending { it.code }
        LtxSortOption.CODE_ASC -> filtered.sortedBy { it.code }
        // 日期排序：未补全（无 date）的条目稳定排在尾部
        LtxSortOption.DATE_DESC -> filtered.sortedWith(
            compareByDescending<LtxItemUiModel> { it.movie?.date != null }
                .thenByDescending { it.movie?.date.orEmpty() }
        )

        LtxSortOption.DATE_ASC -> filtered.sortedWith(
            compareBy<LtxItemUiModel> { it.movie?.date != null }
                .thenBy { it.movie?.date.orEmpty() }
        )

        // 评分排序：无评分的条目稳定排在尾部
        LtxSortOption.SCORE_DESC -> filtered.sortedWith(
            compareByDescending<LtxItemUiModel> { it.score != null }
                .thenByDescending { it.score ?: 0.0 }
        )

        LtxSortOption.SCORE_ASC -> filtered.sortedWith(
            compareBy<LtxItemUiModel> { it.score != null }
                .thenBy { it.score ?: 0.0 }
        )

        LtxSortOption.SIZE_DESC -> filtered.sortedByDescending { it.maxSizeBytes }
        LtxSortOption.SIZE_ASC -> filtered.sortedBy { it.maxSizeBytes }
    }.toList()
}

/** LtxEntry + 可选补全快照 → UI 模型。 */
private fun LtxEntry.toLtxItemUiModel(snapshot: LtxSnapshotUi?): LtxItemUiModel = LtxItemUiModel(
    code = code.uppercase(),
    isPending = isPending,
    isMissing = isMissing,
    addedAtMillis = addedAtMillis,
    links = links + pendingLinks,
    score = score,
    maxSizeBytes = maxSizeBytes,
    movie = snapshot?.movie?.takeIf { snapshot.resolveState == LtxResolveState.RESOLVED }?.toUiModel(),
    resolveState = snapshot?.resolveState ?: LtxResolveState.UNRESOLVED,
)

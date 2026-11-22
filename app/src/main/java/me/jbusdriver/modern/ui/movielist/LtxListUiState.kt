package me.jbusdriver.modern.ui.movielist

import androidx.annotation.StringRes
import me.jbusdriver.R
import me.jbusdriver.modern.data.db.entity.LtxResolveState
import me.jbusdriver.modern.data.ltx.LtxListQuery
import me.jbusdriver.modern.data.ltx.LtxListSort
import me.jbusdriver.modern.domain.model.LtxEntry
import me.jbusdriver.modern.domain.model.LtxLink
import me.jbusdriver.modern.domain.model.Movie
import me.jbusdriver.modern.ui.MovieUiModel
import me.jbusdriver.modern.ui.toUiModel

/**
 * LTX 列表排序依据（服务端排序：切换即以新参数重查第 0 页，客户端不再排序）。
 * 默认评分降序，与站点首页一致。
 *
 * 入庫時間 = LTX 库 added_at；評分 = 站点加权展示分；發行時間 = 库内 date。
 */
enum class LtxSortField(@StringRes val labelRes: Int) {
    ADDED(R.string.ltx_sort_field_added),
    SCORE(R.string.ltx_sort_field_score),
    DATE(R.string.ltx_sort_field_date),
    CODE(R.string.sort_field_code);

    /** 站点查询排序字段名（与 [LtxListSort] 的 field 枚举一致）。 */
    internal val serverField: String get() = name.lowercase()

    companion object {
        fun fromName(name: String?): LtxSortField =
            entries.firstOrNull { it.name == name } ?: SCORE
    }
}

/** LTX 列表排序方向：倒序（降序）为默认（服务端排序）。 */
enum class LtxSortDirection(@StringRes val labelRes: Int) {
    DESC(R.string.sort_dir_desc),
    ASC(R.string.sort_dir_asc);

    /** 站点查询方向名。 */
    internal val serverDir: String get() = name.lowercase()

    companion object {
        fun fromName(name: String?): LtxSortDirection =
            entries.firstOrNull { it.name == name } ?: DESC
    }
}

/** LTX 链接确认状态筛选（服务端筛选：切换即以新参数重查第 0 页）。 */
enum class LtxStatusFilter(@StringRes val labelRes: Int) {
    ALL(R.string.ltx_status_all),
    CONFIRMED(R.string.ltx_status_confirmed),
    PENDING(R.string.ltx_pending);

    /** 站点查询状态名：`all`/`ok`（已确认）/`pending`（待确认）。 */
    internal val serverStatus: String
        get() = when (this) {
            ALL -> "all"
            CONFIRMED -> "ok"
            PENDING -> "pending"
        }
}

/**
 * LTX 站内检测状态筛选（单一维度三态，多选；应用本地数据，纯客户端过滤）。
 *
 * 三态由同一份补全快照状态推导：[RESOLVED] 已收錄 / [NOT_FOUND] 未收錄 /
 * [UNRESOLVED] 未檢測（未补全，收录状态未知）。多选集合为空时由调用方回退为全选。
 */
enum class LtxResolveFilter(@StringRes val labelRes: Int) {
    RESOLVED(R.string.ltx_site_included),
    NOT_FOUND(R.string.ltx_site_not_found),
    UNRESOLVED(R.string.ltx_resolve_unchecked);

    companion object {
        /** 默认选择：已收錄 + 未檢測（隐藏站内未收錄）。 */
        val DEFAULT: Set<LtxResolveFilter> = setOf(RESOLVED, UNRESOLVED)
    }
}

/** 当前排序/状态筛选 → 站点分页查询参数（服务端排序与确认状态筛选）。 */
internal fun LtxListUiState.toListQuery(): LtxListQuery = LtxListQuery(
    sort = LtxListSort(field = sortField.serverField, dir = sortDirection.serverDir),
    status = statusFilter.serverStatus,
)

/** LTX 列表单条 UI 模型：番号 + 状态 + 下载链接 + 站内补全快照。 */
data class LtxItemUiModel(
    val code: String,
    val isPending: Boolean,
    val isMissing: Boolean,
    val addedAtMillis: Long,
    val links: List<LtxLink>,
    /** 站点加权评分（展示/服务端排序依据；无评分为 null）。 */
    val score: Double? = null,
    /** 发行时间：LTX 库原始 date 优先，回退站内补全快照日期；均无为 ""。 */
    val date: String = "",
    /** 站内补全的影片快照（URL 已按当前 baseUrl 重建）；未补全为 null。 */
    val movie: MovieUiModel? = null,
    /** 补全状态，见 [LtxResolveState]。 */
    val resolveState: String = LtxResolveState.UNRESOLVED,
)

data class LtxListUiState(
    /** 默认 true：页面组合到 start() 置位前先显示加载态，避免首帧闪现「暫無資料」。 */
    val isLoading: Boolean = true,
    val isRefreshing: Boolean = false,
    /** 加载下一页进行中（列表尾部指示，不与下拉刷新指示混用）。 */
    val isLoadingMore: Boolean = false,
    val error: Int? = null,
    /** 已加载页聚合条目（服务端排序，本地仅应用站内检测筛选）。 */
    val items: List<LtxItemUiModel> = emptyList(),
    /** 当前查询（排序/确认状态筛选后）的服务端总数。 */
    val totalCount: Int = 0,
    /** LTX 库更新时间（毫秒）。 */
    val updatedAtMillis: Long = 0L,
    val sortField: LtxSortField = LtxSortField.SCORE,
    val sortDirection: LtxSortDirection = LtxSortDirection.DESC,
    val statusFilter: LtxStatusFilter = LtxStatusFilter.ALL,
    /** 站内检测状态多选，默认隐藏站内未收錄，见 [LtxResolveFilter]。 */
    val resolveFilter: Set<LtxResolveFilter> = LtxResolveFilter.DEFAULT,
    /** 排队或解析中的番号集合（归一化大写），用于「補全中」标记与进度指示。 */
    val resolvingCodes: Set<String> = emptySet(),
    /** 服务端还有下一页（已加载行数 < 当前查询总数）。 */
    val canLoadMore: Boolean = false,
) {
    /** 筛选弹窗角标数：偏离默认值的筛选条件个数（排序不计入）。 */
    val activeFilterCount: Int
        get() = listOf(
            statusFilter != LtxStatusFilter.ALL,
            resolveFilter != LtxResolveFilter.DEFAULT,
        ).count { it }

    val hasActiveFilters: Boolean get() = activeFilterCount > 0
}

/** 补全快照的 UI 投影：状态 + 已重建 URL 的影片快照（RESOLVED 时非空）。 */
data class LtxSnapshotUi(
    val resolveState: String,
    val movie: Movie? = null,
)

/**
 * 纯计算：已加载页条目 + 补全快照 → 列表项。
 *
 * 顺序保持服务端排序结果不动（分页协议下客户端排序会破坏翻页一致性），
 * 仅应用站内检测筛选（应用本地补全数据）。筛选导致可见条目不足时由
 * ViewModel 自动补拉后续页。
 *
 * @param snapshots 归一化番号 → 补全快照投影
 */
internal fun computeLtxListItems(
    entries: List<LtxEntry>,
    snapshots: Map<String, LtxSnapshotUi>,
    resolveFilter: Set<LtxResolveFilter>,
): List<LtxItemUiModel> = entries.asSequence()
    .filter { entry ->
        val resolveState = snapshots[entry.code.uppercase()]?.resolveState
            ?: LtxResolveState.UNRESOLVED
        val bucket = when (resolveState) {
            LtxResolveState.RESOLVED -> LtxResolveFilter.RESOLVED
            LtxResolveState.NOT_FOUND -> LtxResolveFilter.NOT_FOUND
            else -> LtxResolveFilter.UNRESOLVED
        }
        bucket in resolveFilter
    }
    .map { entry -> entry.toLtxItemUiModel(snapshots[entry.code.uppercase()]) }
    .toList()

/** LtxEntry + 可选补全快照 → UI 模型。 */
private fun LtxEntry.toLtxItemUiModel(snapshot: LtxSnapshotUi?): LtxItemUiModel {
    val snapshotMovie = snapshot?.movie
        ?.takeIf { snapshot.resolveState == LtxResolveState.RESOLVED }
        ?.toUiModel()
    return LtxItemUiModel(
        code = code.uppercase(),
        isPending = isPending,
        isMissing = isMissing,
        addedAtMillis = addedAtMillis,
        // 同一 URI 可能同时存在于 links 与 pending_links，复制前按 URI 去重
        links = (links + pendingLinks).distinctBy { it.uri },
        score = score,
        movie = snapshotMovie,
        resolveState = snapshot?.resolveState ?: LtxResolveState.UNRESOLVED,
        // 發行時間以 LTX 库原始 date 为准，站内快照日期仅作回退——补全前后展示不漂移
        date = date ?: snapshotMovie?.date.orEmpty(),
    )
}

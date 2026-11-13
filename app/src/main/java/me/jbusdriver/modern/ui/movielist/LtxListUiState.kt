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
import kotlin.math.pow

/**
 * LTX 列表排序依据。
 *
 * 入庫時間 = LTX 库 added_at；評分来自库数据（全部条目可排）；
 * 發行時間仅对已补全条目有效（无效值与有效值分离、不参与混排）。
 */
enum class LtxSortField(@StringRes val labelRes: Int) {
    ADDED(R.string.ltx_sort_field_added),
    SCORE(R.string.ltx_sort_field_score),
    DATE(R.string.ltx_sort_field_date),
    CODE(R.string.ltx_sort_field_code);

    companion object {
        fun fromName(name: String?): LtxSortField =
            entries.firstOrNull { it.name == name } ?: ADDED
    }
}

/** LTX 列表排序方向：倒序（降序）为默认。 */
enum class LtxSortDirection(@StringRes val labelRes: Int) {
    DESC(R.string.ltx_sort_dir_desc),
    ASC(R.string.ltx_sort_dir_asc);

    companion object {
        fun fromName(name: String?): LtxSortDirection =
            entries.firstOrNull { it.name == name } ?: DESC
    }
}

/** LTX 链接确认状态筛选。 */
enum class LtxStatusFilter(@StringRes val labelRes: Int) {
    ALL(R.string.ltx_status_all),
    CONFIRMED(R.string.ltx_status_confirmed),
    PENDING(R.string.ltx_pending);
}

/**
 * LTX 站内检测状态筛选（单一维度三态，多选）。
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

/** LTX 列表单条 UI 模型：番号 + 状态 + 下载链接 + 站内补全快照。 */
data class LtxItemUiModel(
    val code: String,
    val isPending: Boolean,
    val isMissing: Boolean,
    val addedAtMillis: Long,
    val links: List<LtxLink>,
    /** 评分（源站数据，用于排序；无评分为 null）。 */
    val score: Double? = null,
    /** 评分数（加权评分排序的先验强度）。 */
    val votes: Int? = null,
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
    val error: Int? = null,
    val items: List<LtxItemUiModel> = emptyList(),
    val totalCount: Int = 0,
    /** LTX 全库更新时间（毫秒）。 */
    val updatedAtMillis: Long = 0L,
    /** 缓存写入时间（毫秒），下拉时据此判断「数据仍新鲜需确认强刷」。 */
    val cacheStoredAtMillis: Long = 0L,
    val sortField: LtxSortField = LtxSortField.ADDED,
    val sortDirection: LtxSortDirection = LtxSortDirection.DESC,
    val statusFilter: LtxStatusFilter = LtxStatusFilter.ALL,
    /** 站内检测状态多选，默认隐藏站内未收錄，见 [LtxResolveFilter]。 */
    val resolveFilter: Set<LtxResolveFilter> = LtxResolveFilter.DEFAULT,
    /** 排队或解析中的番号集合（归一化大写），用于「補全中」标记与进度指示。 */
    val resolvingCodes: Set<String> = emptySet(),
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
 * 纯计算：LtxLibrary + 补全快照 → 应用排序/筛选后的列表项。
 *
 * @param snapshots 归一化番号 → 补全快照投影
 */
internal fun computeLtxListItems(
    library: LtxLibrary?,
    snapshots: Map<String, LtxSnapshotUi>,
    sortField: LtxSortField,
    sortDirection: LtxSortDirection,
    statusFilter: LtxStatusFilter,
    resolveFilter: Set<LtxResolveFilter>,
    nowMillis: Long = System.currentTimeMillis(),
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
    val ascending = sortDirection == LtxSortDirection.ASC
    return when (sortField) {
        LtxSortField.ADDED ->
            if (ascending) filtered.sortedBy { it.addedAtMillis }
            else filtered.sortedByDescending { it.addedAtMillis }

        LtxSortField.CODE ->
            if (ascending) filtered.sortedBy { it.code }
            else filtered.sortedByDescending { it.code }

        // 日期排序：以 LTX 库原始 date 为准（回退补全快照日期），无日期条目不参与混排
        LtxSortField.DATE ->
            if (ascending) filtered.sortedWith(
                compareBy<LtxItemUiModel> { it.date.isNotEmpty() }
                    .thenBy { it.date }
            )
            else filtered.sortedWith(
                compareByDescending<LtxItemUiModel> { it.date.isNotEmpty() }
                    .thenByDescending { it.date }
            )

        // 评分排序：网站同款加权评分——贝叶斯加权（票数 25 分位为先验）+ 新入库
        // 指数衰减加成 − pending 扣减，无评分条目稳定排尾。评论活跃加成所需的
        // people/likers 字段不在 /api/items 契约内，按无评论（0 加成）处理，故
        // 极少数评论较多条目与站点显示值存在 ≤0.15 的偏差（次序偶有微调）
        LtxSortField.SCORE -> {
            val stats = ltxScoreStats(library.entries)
            if (stats == null) {
                filtered
            } else {
                val (meanScore, voteQuartile) = stats
                val weighted = filtered.associate { it.code to weightedLtxScore(it, meanScore, voteQuartile, nowMillis) }
                if (ascending) filtered.sortedWith(
                    compareByDescending<LtxItemUiModel> { weighted[it.code] != null }
                        .thenBy { weighted[it.code] ?: 0.0 }
                )
                else filtered.sortedWith(
                    compareByDescending<LtxItemUiModel> { weighted[it.code] != null }
                        .thenByDescending { weighted[it.code] ?: 0.0 }
                )
            }
        }
    }.toList()
}

// region 加权评分（对齐站点 2026-11 前端算法，常量自其脚本逆向）

private const val LTX_SCORE_RECENT_BONUS = 0.3
private const val LTX_SCORE_RECENT_HALF_LIFE_DAYS = 14.0
private const val LTX_SCORE_PENDING_PENALTY = 1.0
private const val LTX_SCORE_CAP = 5.0
private const val LTX_MILLIS_PER_DAY = 86_400_000.0

/** 全库评分统计：原始分均值 + 票数 25 分位（贝叶斯先验）。无评分条目时返回 null。 */
internal fun ltxScoreStats(entries: List<LtxEntry>): Pair<Double, Int>? {
    val scored = entries.filter { it.score != null }
    if (scored.isEmpty()) return null
    val mean = scored.sumOf { it.score!! } / scored.size
    val quartile = scored.map { it.votes ?: 0 }.sorted()[scored.size / 4].coerceAtLeast(1)
    return mean to quartile
}

/**
 * 网站同款加权评分：`(票数×原始分 + 先验×均值) / (票数+先验)` + 新入库加成
 * `0.3 × 2^(−库龄天/14)`，封顶 5 分，pending 再扣 1 分。无评分返回 null。
 */
internal fun weightedLtxScore(
    model: LtxItemUiModel,
    meanScore: Double,
    voteQuartile: Int,
    nowMillis: Long,
): Double? {
    val raw = model.score ?: return null
    val votes = (model.votes ?: 0).coerceAtLeast(0)
    val bayes = (votes * raw + voteQuartile * meanScore) / (votes + voteQuartile)
    val ageDays = (nowMillis - model.addedAtMillis).coerceAtLeast(0L) / LTX_MILLIS_PER_DAY
    val recentBonus = LTX_SCORE_RECENT_BONUS * 2.0.pow(-ageDays / LTX_SCORE_RECENT_HALF_LIFE_DAYS)
    val capped = minOf(LTX_SCORE_CAP, bayes + recentBonus)
    return if (model.isPending) capped - LTX_SCORE_PENDING_PENALTY else capped
}

// endregion

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
        // 同一 URI 可能同时存在于 links 与 pending_links（旧磁盘缓存尤甚），复制前按 URI 去重
        links = (links + pendingLinks).distinctBy { it.uri },
        score = score,
        votes = votes,
        movie = snapshotMovie,
        resolveState = snapshot?.resolveState ?: LtxResolveState.UNRESOLVED,
        // 發行時間以 LTX 库原始 date 为准，站内快照日期仅作回退——补全前后排序/展示不漂移
        date = date ?: snapshotMovie?.date.orEmpty(),
    )
}

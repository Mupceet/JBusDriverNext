package me.jbusdriver.modern.data.db.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.PrimaryKey

/** LTX 条目的站内补全状态。 */
object LtxResolveState {
    /** 尚未在站内解析过（或上次解析失败，可重试）。 */
    const val UNRESOLVED = "UNRESOLVED"
    /** 已解析为站内影片，持有 Movie 快照。 */
    const val RESOLVED = "RESOLVED"
    /** 站内搜索无精确匹配（终态，不重试）。 */
    const val NOT_FOUND = "NOT_FOUND"
}

/**
 * LTX 条目的站内补全快照，对应 `t_ltx_movie` 表。
 *
 * 职责：按番号把 LTX 库条目解析为站内影片（搜索精确匹配），并以
 * 路径化 URL 的 [Movie][me.jbusdriver.modern.domain.model.Movie] JSON 快照持久化，
 * 展示时按当前 baseUrl 重建完整 URL（与 [LinkItem] 同一套镜像安全策略）。
 *
 * 使用场景：收藏 tab LTX 页的卡片展示与后台补全队列。
 */
@Entity(tableName = "t_ltx_movie")
data class LtxMovie(
    /** 归一化番号（大写、去首尾空白），与 LTX 库条目一一对应。 */
    @PrimaryKey val code: String,
    /** 补全状态，见 [LtxResolveState]。 */
    @ColumnInfo(name = "resolveState") val resolveState: String,
    /** 站内影片页 URL 路径（已剥离域名）；未解析为空。 */
    @ColumnInfo(name = "movieUrlPath") val movieUrlPath: String = "",
    /** URL 已剥离的 Movie 快照 JSON；未解析为空。 */
    @ColumnInfo(name = "movieJson") val movieJson: String = "",
    /** 最近一次解析时间戳（毫秒）。 */
    @ColumnInfo(name = "resolvedAt") val resolvedAt: Long = 0L
)

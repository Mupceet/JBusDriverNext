package me.jbusdriver.modern.data.ltx

import com.google.gson.JsonArray
import com.google.gson.JsonNull
import com.google.gson.JsonObject
import java.io.IOException

/*
 * LTX 分页查询协议（2026-11 起）：列表数据经 `POST /api/items/query` 按页获取
 * （服务端承担排序/状态筛选，`limit` 上限实测 100）。本文件承载请求体构造
 * （与站点 SPA 逐字段对齐）与查询参数类型。
 *
 * 实测契约（2026-11-16，浏览器过门禁后解密验证）：
 * - 基础筛选字段的显式 null（actor/maker/type/randomSeed）与 SPA 一致、被接受；
 * - `wantCodes`/`hideCodes` 显式 null 会被 400 `invalidQuery` 拒绝 → 永不声明；
 * - `snapshot`/`metadata` 省略即服务端默认；免快照查询合法且返回新快照；
 * - 快照过期时站点报 `snapshotExpired`，调用方应丢弃快照从第 0 页重查。
 */

/** LTX 分页查询端点路径（POST，加密会话通道；翻页/排序/状态筛选）。 */
const val LTX_QUERY_PATH = "/api/items/query"

/** 分页查询页大小（服务端实测上限 100；超限请求会被服务端压缩到 100）。 */
const val LTX_PAGE_LIMIT = 100

/** 查询排序参数：字段为站点枚举（score/date/added/size/code/comments）。 */
data class LtxListSort(val field: String, val dir: String) {
    companion object {
        /** 默认排序：入库时间倒序（与站点默认一致）。 */
        val ADDED_DESC = LtxListSort(field = "added", dir = "desc")
    }
}

/**
 * 列表查询参数（排序 + 确认状态 + 可选搜索词）。
 * 状态为站点枚举：`all`/`ok`（已确认）/`pending`（待确认）。
 */
data class LtxListQuery(
    val sort: LtxListSort = LtxListSort.ADDED_DESC,
    val status: String = "all",
    val q: String = "",
)

/** 快照失效（站点报 `snapshotExpired`）：调用方应丢弃快照从第 0 页重查。 */
class LtxSnapshotExpiredException : IOException("LTX snapshot expired")

/**
 * 构造 `POST /api/items/query` 请求体。
 *
 * 基础筛选字段始终在场：null 值与站点 SPA 提交形状一致（显式 null 被服务端
 * 接受）。[snapshot] 省略时服务端新建快照；[metadata]=false 为瘦身载荷
 * （翻页/列表场景，updated_at/total 仍在）。
 */
internal fun buildLtxQueryBody(
    query: LtxListQuery = LtxListQuery(),
    snapshot: String? = null,
    offset: Int = 0,
    limit: Int = LTX_PAGE_LIMIT,
    metadata: Boolean? = false,
): String = JsonObject().apply {
    addProperty("q", query.q)
    add("actor", JsonNull.INSTANCE)
    addProperty("status", query.status)
    add("genres", JsonArray())
    add("maker", JsonNull.INSTANCE)
    add("type", JsonNull.INSTANCE)
    addProperty("sub", false)
    add(
        "sort",
        JsonObject().apply {
            addProperty("field", query.sort.field)
            addProperty("dir", query.sort.dir)
        },
    )
    add("randomSeed", JsonNull.INSTANCE)
    snapshot?.let { addProperty("snapshot", it) }
    addProperty("offset", offset)
    addProperty("limit", limit)
    metadata?.let { addProperty("metadata", it) }
}.toString()

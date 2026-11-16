package me.jbusdriver.modern.data.ltx

import com.google.gson.JsonArray
import com.google.gson.JsonNull
import com.google.gson.JsonObject
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import me.jbusdriver.modern.KLog
import java.io.IOException

/*
 * LTX 分页协议（2026-11 起）：`GET /api/items` 只返回首页条目 + 快照 id + 全库
 * 总数，翻页与筛选/排序迁移到 `POST /api/items/query`。本文件承载与站点形态
 * 无关的协议编排：查询请求体构造（与站点 SPA 逐字段对齐）与全库分页同步。
 *
 * 实测契约（2026-11-16，浏览器过门禁后解密验证）：
 * - 服务端页大小上限 100（请求更大值会被压缩到 100）；
 * - 基础筛选字段的显式 null（actor/maker/type/randomSeed）与 SPA 一致、被接受；
 * - `wantCodes`/`hideCodes` 显式 null 会被 400 `invalidQuery` 拒绝 → 永不声明；
 * - `snapshot`/`metadata` 省略即服务端默认（快照可选、元数据默认附带）；
 * - 免快照查询合法且返回新快照；快照过期时站点报 `snapshotExpired`。
 */

/** LTX 分页查询端点路径（POST，加密会话通道；承载翻页与筛选/排序参数）。 */
const val LTX_QUERY_PATH = "/api/items/query"

/** 分页查询页大小（服务端实测上限 100；超限请求会被服务端压缩到 100）。 */
const val LTX_PAGE_LIMIT = 100

/** 查询体排序字段：入库时间倒序（与全库默认顺序一致，解析边界再统一排序）。 */
private const val QUERY_SORT_FIELD = "added"
private const val QUERY_SORT_DIR = "desc"

/**
 * 构造 `POST /api/items/query` 请求体。
 *
 * 基础筛选字段始终在场：App 全库同步只用默认形态（全部状态、入库倒序），
 * null 值与站点 SPA 提交形状一致（显式 null 被服务端接受）。
 * [snapshot] 省略时服务端新建快照；[metadata]=false 用于翻页（瘦身载荷）。
 */
internal fun buildLtxQueryBody(
    snapshot: String?,
    offset: Int,
    limit: Int = LTX_PAGE_LIMIT,
    metadata: Boolean? = null,
): String = JsonObject().apply {
    addProperty("q", "")
    add("actor", JsonNull.INSTANCE)
    addProperty("status", "all")
    add("genres", JsonArray())
    add("maker", JsonNull.INSTANCE)
    add("type", JsonNull.INSTANCE)
    addProperty("sub", false)
    add(
        "sort",
        JsonObject().apply {
            addProperty("field", QUERY_SORT_FIELD)
            addProperty("dir", QUERY_SORT_DIR)
        },
    )
    add("randomSeed", JsonNull.INSTANCE)
    snapshot?.let { addProperty("snapshot", it) }
    addProperty("offset", offset)
    addProperty("limit", limit)
    metadata?.let { addProperty("metadata", it) }
}.toString()

/** 同步期间站点数据更新导致快照漂移；调用方应以新快照重启一次同步。 */
class LtxSnapshotChangedException : IOException("LTX snapshot changed during paged sync")

/**
 * 分页全库同步编排：以 [bootstrap]（首页载荷）的快照为准，从首页条目数起按
 * [LTX_PAGE_LIMIT] 并发翻页（并发度 [concurrency]）直至 [LtxItemsPayloadDto.total]，
 * 返回合并后的完整载荷。任一页快照与首页不一致（同步期间站点更新）抛
 * [LtxSnapshotChangedException]；[LtxItemsPayloadDto.updatedAt] 首页缺失时取首个翻页值。
 *
 * 纯编排函数（网络经 [fetchPage] 注入），便于单元测试。
 */
internal suspend fun syncLtxLibraryPages(
    bootstrap: LtxItemsPayloadDto,
    fetchPage: suspend (snapshot: String, offset: Int) -> LtxItemsPayloadDto,
    concurrency: Int,
): LtxItemsPayloadDto {
    val snapshot = requireNotNull(bootstrap.snapshot) { "bootstrap payload has no snapshot" }
    val firstPageItems = bootstrap.items.orEmpty()
    val total = bootstrap.total ?: firstPageItems.size
    if (firstPageItems.size >= total) return bootstrap.copy(items = firstPageItems)

    var updatedAt = bootstrap.updatedAt
    val merged = firstPageItems.toMutableList()
    val offsets = firstPageItems.size until total step LTX_PAGE_LIMIT
    var fetchedPages = 0
    for (chunk in offsets.chunked(concurrency)) {
        val pages = coroutineScope {
            val jobs = chunk.map { offset ->
                async { fetchPage(snapshot, offset) }
            }
            try {
                jobs.map { it.await() }
            } catch (e: Throwable) {
                jobs.forEach { it.cancel() }
                throw e
            }
        }
        for (page in pages) {
            if (page.snapshot != snapshot) throw LtxSnapshotChangedException()
            if (updatedAt == null) updatedAt = page.updatedAt
            merged += page.items.orEmpty()
        }
        fetchedPages += chunk.size
    }
    KLog.t(TAG).i("LTX paged sync complete: total=$total items=${merged.size} pages=$fetchedPages")
    return LtxItemsPayloadDto(items = merged, updatedAt = updatedAt, total = total, snapshot = snapshot)
}

private val TAG = "LtxPagedSync"

package me.jbusdriver.modern.data.ltx

import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test
import java.io.IOException

/**
 * LTX 分页协议：query 请求体构造（与站点 SPA 提交形状逐字段对齐）与全库
 * 分页同步编排。fixture 均为合成数据。
 */
class LtxPagedSyncTest {

    // region buildLtxQueryBody

    @Test
    fun `query body with snapshot matches site shape field by field`() {
        val body = buildLtxQueryBody(snapshot = "snap-1", offset = 100, limit = 100, metadata = false)
        // 基础筛选字段始终在场（显式 null 与 SPA 一致）；wantCodes/hideCodes 绝不出现
        // （显式 null 会被服务端 400 invalidQuery 拒绝）；snapshot/metadata 按需在场
        val expected = "{\"q\":\"\",\"actor\":null,\"status\":\"all\",\"genres\":[],\"maker\":null,\"type\":null," +
            "\"sub\":false,\"sort\":{\"field\":\"added\",\"dir\":\"desc\"},\"randomSeed\":null," +
            "\"snapshot\":\"snap-1\",\"offset\":100,\"limit\":100,\"metadata\":false}"
        assertEquals(expected, body)
        assertFalse(body.contains("wantCodes"))
        assertFalse(body.contains("hideCodes"))
    }

    @Test
    fun `query body omits snapshot and metadata when unset`() {
        val body = buildLtxQueryBody(snapshot = null, offset = 0)
        // 免快照查询合法（服务端新建快照）；metadata 省略即服务端默认
        assertFalse(body.contains("snapshot"))
        assertFalse(body.contains("metadata"))
        assertTrue(body.contains("\"offset\":0"))
        // 页大小默认 100（服务端实测上限，请求更大值会被压缩）
        assertTrue(body.contains("\"limit\":100"))
    }

    // endregion

    // region syncLtxLibraryPages

    /** 合成条目（ed2k 哈希段固定 32 位十六进制，满足链接解析）。 */
    private fun item(index: Int) = LtxItemDto(
        code = "TEST-${index.toString().padStart(4, '0')}",
        link = "ed2k://|file|TEST-$index.mp4|1024|${index.toString().padStart(32, '0')}|/",
        addedAt = index.toLong(),
    )

    @Test
    fun `sync fetches remaining pages until total and merges in offset order`() = runTest {
        // 全库 250 条：首页 60 + 翻页偏移 [60, 160]（60 until 250 step 100）
        val all = (0 until 250).map(::item)
        val requested = mutableListOf<Int>()
        val merged = syncLtxLibraryPages(
            bootstrap = LtxItemsPayloadDto(
                items = all.take(60),
                updatedAt = 1000L,
                total = 250,
                snapshot = "snap-1",
            ),
            fetchPage = { snapshot, offset ->
                assertEquals("snap-1", snapshot)
                requested.add(offset)
                LtxItemsPayloadDto(
                    items = all.subList(offset, minOf(offset + LTX_PAGE_LIMIT, all.size)),
                    total = 250,
                    snapshot = snapshot,
                )
            },
            concurrency = 4,
        )

        assertEquals(listOf(60, 160), requested)
        assertEquals(250, merged.items?.size)
        // 合并结果与源集合逐位一致（偏移序拼接，无重复无遗漏）
        assertEquals((0 until 250).map { "TEST-${it.toString().padStart(4, '0')}" }, merged.items?.map { it.code })
        // updatedAt 首页优先，翻页缺失不覆盖
        assertEquals(1000L, merged.updatedAt)
        assertEquals("snap-1", merged.snapshot)
        assertEquals(250, merged.total)
    }

    @Test
    fun `bootstrap covering total returns as is without page fetch`() = runTest {
        val merged = syncLtxLibraryPages(
            bootstrap = LtxItemsPayloadDto(
                items = (0 until 60).map(::item),
                updatedAt = 1000L,
                total = 60,
                snapshot = "snap-1",
            ),
            fetchPage = { _, _ ->
                fail("must not fetch pages")
                throw IllegalStateException("unreachable")
            },
            concurrency = 4,
        )
        assertEquals(60, merged.items?.size)
    }

    @Test
    fun `total missing falls back to bootstrap item count as total`() = runTest {
        val merged = syncLtxLibraryPages(
            bootstrap = LtxItemsPayloadDto(
                items = (0 until 60).map(::item),
                snapshot = "snap-1",
                // total 缺省 → 以 items.size 为总数，无翻页
            ),
            fetchPage = { _, _ ->
                fail("must not fetch pages")
                throw IllegalStateException("unreachable")
            },
            concurrency = 4,
        )
        assertEquals(60, merged.items?.size)
    }

    @Test
    fun `updatedAt falls back to first fetched page when bootstrap lacks it`() = runTest {
        val all = (0 until 250).map(::item)
        val merged = syncLtxLibraryPages(
            bootstrap = LtxItemsPayloadDto(items = all.take(60), total = 250, snapshot = "snap-1"),
            fetchPage = { snapshot, offset ->
                LtxItemsPayloadDto(
                    items = all.subList(offset, minOf(offset + LTX_PAGE_LIMIT, all.size)),
                    updatedAt = 2000L + offset,
                    total = 250,
                    snapshot = snapshot,
                )
            },
            concurrency = 4,
        )
        // 首个成功翻页的 updated_at 补位（偏移 60）
        assertEquals(2060L, merged.updatedAt)
    }

    @Test
    fun `snapshot drift on any page aborts sync`() = runTest {
        val all = (0 until 250).map(::item)
        try {
            syncLtxLibraryPages(
                bootstrap = LtxItemsPayloadDto(items = all.take(60), total = 250, snapshot = "snap-1"),
                fetchPage = { snapshot, offset ->
                    LtxItemsPayloadDto(
                        items = all.subList(offset, minOf(offset + LTX_PAGE_LIMIT, all.size)),
                        total = 250,
                        // 偏移 160 的翻页返回新快照：同步期间站点更新
                        snapshot = if (offset >= 160) "snap-2" else snapshot,
                    )
                },
                concurrency = 4,
            )
            fail("expected LtxSnapshotChangedException")
        } catch (_: LtxSnapshotChangedException) {
            // 调用方以新快照重启一次同步
        }
    }

    @Test
    fun `bootstrap without snapshot is rejected`() = runTest {
        try {
            syncLtxLibraryPages(
                bootstrap = LtxItemsPayloadDto(items = emptyList(), total = 10, snapshot = null),
                fetchPage = { _, _ -> throw IllegalStateException("unreachable") },
                concurrency = 4,
            )
            fail("expected IllegalArgumentException")
        } catch (e: IllegalArgumentException) {
            assertTrue(e.message!!.contains("snapshot"))
        }
    }

    @Test
    fun `page failure propagates`() = runTest {
        val all = (0 until 250).map(::item)
        try {
            syncLtxLibraryPages(
                bootstrap = LtxItemsPayloadDto(items = all.take(60), total = 250, snapshot = "snap-1"),
                fetchPage = { _, offset ->
                    if (offset == 160) throw IOException("network error")
                    LtxItemsPayloadDto(
                        items = all.subList(offset, minOf(offset + LTX_PAGE_LIMIT, all.size)),
                        total = 250,
                        snapshot = "snap-1",
                    )
                },
                concurrency = 4,
            )
            fail("expected IOException")
        } catch (e: IOException) {
            assertEquals("network error", e.message)
        }
    }

    // endregion
}

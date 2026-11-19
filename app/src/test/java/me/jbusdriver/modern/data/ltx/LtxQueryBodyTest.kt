package me.jbusdriver.modern.data.ltx

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * LTX 分页查询请求体构造：与站点 SPA 提交形状逐字段对齐。
 * fixture 均为合成数据。
 */
class LtxQueryBodyTest {

    @Test
    fun `query body with snapshot matches site shape field by field`() {
        val body = buildLtxQueryBody(
            query = LtxListQuery(sort = LtxListSort("score", "desc"), status = "all"),
            snapshot = "snap-1",
            offset = 100,
            limit = 100,
            metadata = false,
        )
        // 基础筛选字段始终在场（显式 null 与 SPA 一致）；wantCodes/hideCodes 绝不出现
        // （显式 null 会被服务端 400 invalidQuery 拒绝）；snapshot/metadata 按需在场
        val expected = "{\"q\":\"\",\"actor\":null,\"status\":\"all\",\"genres\":[],\"maker\":null,\"type\":null," +
            "\"sub\":false,\"sort\":{\"field\":\"score\",\"dir\":\"desc\"},\"randomSeed\":null," +
            "\"snapshot\":\"snap-1\",\"offset\":100,\"limit\":100,\"metadata\":false}"
        assertEquals(expected, body)
        assertFalse(body.contains("wantCodes"))
        assertFalse(body.contains("hideCodes"))
    }

    @Test
    fun `query body omits snapshot and metadata when unset`() {
        val body = buildLtxQueryBody()
        // 免快照查询合法（服务端新建快照）；metadata:false 为瘦身载荷
        assertFalse(body.contains("\"snapshot\""))
        assertTrue(body.contains("\"metadata\":false"))
        assertTrue(body.contains("\"offset\":0"))
        // 页大小默认 100（服务端实测上限，请求更大值会被压缩）
        assertTrue(body.contains("\"limit\":100"))
        // 默认查询：入库倒序 + 全部状态
        assertTrue(body.contains("\"sort\":{\"field\":\"added\",\"dir\":\"desc\"}"))
        assertTrue(body.contains("\"status\":\"all\""))
    }

    @Test
    fun `query body carries status and search term for filters`() {
        val body = buildLtxQueryBody(query = LtxListQuery(status = "pending", q = "SSIS-573"), offset = 0, limit = 20)
        assertTrue(body.contains("\"status\":\"pending\""))
        assertTrue(body.contains("\"q\":\"SSIS-573\""))
        assertTrue(body.contains("\"limit\":20"))
    }
}

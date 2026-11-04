package me.jbusdriver.modern.data

import me.jbusdriver.modern.data.ltx.DEFAULT_LTX_BASE_URL
import me.jbusdriver.modern.data.ltx.parseLtxLibrary
import me.jbusdriver.modern.data.ltx.parseLtxLink
import me.jbusdriver.modern.domain.model.LtxLinkType
import me.jbusdriver.modern.domain.model.normalizeLtxCode
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * LTX 载荷解析测试。fixture 全部使用合成番号/哈希，不含真实站点数据。
 */
class LtxPayloadTest {

    @Test
    fun `parse keeps only contract fields and drops metadata`() {
        val json = """
            {
              "items": [
                {
                  "code": "TEST-002",
                  "title": "dropped",
                  "title_zh": "dropped",
                  "actors": ["dropped"],
                  "date": "2026-01-02",
                  "maker": "dropped",
                  "cover": "https://example.test/cover.jpg",
                  "url": "https://example.test/v/xxxx",
                  "score": 4.5,
                  "votes": 100,
                  "genres": ["c1=1"],
                  "links": ["ed2k://|file|TEST-002_restored.mp4|2048|00000000000000000000000000000000|/"],
                  "pending_links": [],
                  "pending": false,
                  "missing": false,
                  "added_at": 2000,
                  "score": 4.35
                },
                {
                  "code": "TEST-001",
                  "links": ["magnet:?xt=urn:btih:0000000000000000000000000000000000000000&dn=TEST-001%20demo"],
                  "pending_links": ["ed2k://|file|TEST-001_pending.mp4|1024|00000000000000000000000000000000|/"],
                  "pending": true,
                  "missing": false,
                  "added_at": 1000
                }
              ],
              "updated_at": 3000,
              "avatars": {"someone": "https://example.test/a.jpg"},
              "genres": {"c1=1": ["x", "y", "z"]},
              "nyaa": {"ok": true}
            }
        """.trimIndent()

        val library = parseLtxLibrary(json)!!

        // entries sorted by added_at descending
        assertEquals(listOf("TEST-002", "TEST-001"), library.entries.map { it.code })
        assertEquals(3000L, library.updatedAtMillis)

        val first = library.entries[0]
        assertEquals(false, first.isPending)
        assertEquals(1, first.links.size)
        assertEquals(LtxLinkType.ED2K, first.links[0].type)
        assertEquals("TEST-002_restored.mp4", first.links[0].fileName)
        assertEquals(2048L, first.links[0].sizeBytes)
        // 库内原始发行时间进契约（排序/展示以此为准，站内快照日期仅作回退）
        assertEquals("2026-01-02", first.date)
        // 评分进契约（仅排序用）；无评分条目为 null
        assertEquals(4.35, first.score!!, 1e-9)
        assertNull(library.entries[1].score)

        val second = library.entries[1]
        assertTrue(second.isPending)
        assertNull(second.date)
        assertEquals(LtxLinkType.MAGNET, second.links[0].type)
        assertEquals("TEST-001 demo", second.links[0].fileName)
        assertNull(second.links[0].sizeBytes)
        assertEquals(1, second.pendingLinks.size)
        assertEquals(LtxLinkType.ED2K, second.pendingLinks[0].type)
    }

    @Test
    fun `duplicate uris across links and pending_links are deduped at parse`() {
        val confirmed = "ed2k://|file|DUP_restored.mp4|2048|00000000000000000000000000000000|/"
        val pendingOnly = "ed2k://|file|DUP_pending_only.mp4|1024|00000000000000000000000000000001|/"
        val json = """
            {
              "items": [
                {
                  "code": "DUP-001",
                  "links": ["$confirmed", "$confirmed"],
                  "pending_links": ["$confirmed", "$pendingOnly"],
                  "pending": true,
                  "added_at": 1000
                }
              ]
            }
        """.trimIndent()

        val library = parseLtxLibrary(json)!!

        val entry = library.entries[0]
        // links 内部去重
        assertEquals(listOf(confirmed), entry.links.map { it.uri })
        // pending 剔除与已确认重复的 URI，仅保留独有项
        assertEquals(listOf(pendingOnly), entry.pendingLinks.map { it.uri })
    }

    @Test
    fun `ed2k link with root hash segment parses`() {
        val link = parseLtxLink(
            "ed2k://|file|demo@TEST-003.mp4|4096|00000000000000000000000000000000|h=URAB7FEANT57FLHNLPLURXGJK2EGB4KB|/"
        )!!
        assertEquals(LtxLinkType.ED2K, link.type)
        assertEquals("demo@TEST-003.mp4", link.fileName)
        assertEquals(4096L, link.sizeBytes)
    }

    @Test
    fun `unknown link schemes are dropped and blank codes skipped`() {
        assertNull(parseLtxLink("https://example.test/file.mp4"))
        assertNull(parseLtxLink("ed2k://|file|bad|notanumber|00000000000000000000000000000000|/"))

        val json = """
            {
              "items": [
                {"code": "", "links": [], "added_at": 1},
                {"code": "TEST-004", "links": ["https://example.test/x"], "pending": true, "added_at": 2}
              ],
              "updated_at": 3
            }
        """.trimIndent()
        val library = parseLtxLibrary(json)!!
        assertEquals(listOf("TEST-004"), library.entries.map { it.code })
        assertTrue(library.entries[0].links.isEmpty())
    }

    @Test
    fun `malformed json returns null but valid empty payload yields empty library`() {
        // 非 JSON 载荷 → null（不可缓存）
        assertNull(parseLtxLibrary("not json"))
        // 合法 JSON 但缺少 items（NullSafeFactory 填充为空列表）→ 空库，
        // 由 isCacheable(entries.isNotEmpty()) 拦截，不落缓存
        assertEquals(0, parseLtxLibrary("""{"updated_at":1}""")!!.entries.size)
        assertEquals(0, parseLtxLibrary("""{"items":null,"updated_at":1}""")!!.entries.size)
    }

    @Test
    fun `find matches codes case and whitespace insensitively`() {
        val json = """
            {"items":[{"code":"abc-001","links":[],"added_at":1}],"updated_at":1}
        """.trimIndent()
        val library = parseLtxLibrary(json)!!
        assertEquals("abc-001", library.find(" abc-001 ")?.code)
        assertNull(library.find("ZZZ-999"))
        assertEquals("ABC-001", normalizeLtxCode(" abc-001 "))
    }

    @Test
    fun `default base url is the workers deployment`() {
        assertEquals("https://ltx.libray.workers.dev", DEFAULT_LTX_BASE_URL)
    }
}

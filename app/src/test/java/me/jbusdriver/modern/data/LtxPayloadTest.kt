package me.jbusdriver.modern.data

import me.jbusdriver.modern.data.ltx.DEFAULT_LTX_BASE_URL
import me.jbusdriver.modern.data.ltx.normalizeLtxBaseUrl
import me.jbusdriver.modern.data.ltx.parseLtxLibrary
import me.jbusdriver.modern.data.ltx.parseLtxLink
import me.jbusdriver.modern.data.ltx.parseLtxMigratedBaseUrl
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
    fun `score parsed for sorting and defaults to null`() {
        val json = """
            {"items":[{"code":"TEST-010","score":4.65}],"updated_at":1}
        """.trimIndent()
        val entry = parseLtxLibrary(json)!!.entries.single()
        assertEquals(4.65, entry.score!!, 1e-9)
        val missing = parseLtxLibrary("""{"items":[{"code":"TEST-011"}]}""")!!
        assertNull(missing.entries.single().score)
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
    fun `paged payload parses snapshot and total and single link contract`() {
        val confirmed = "ed2k://|file|PAGED-001.mp4|4096|00000000000000000000000000000001|/"
        val pending = "ed2k://|file|PAGED-002_pending.mp4|1024|00000000000000000000000000000002|/"
        val json = """
            {
              "items": [
                {
                  "code": "PAGED-001",
                  "title": "dropped",
                  "actors": ["dropped"],
                  "maker": "dropped",
                  "date": "2026-11-01",
                  "link": "$confirmed",
                  "linkPending": false,
                  "linkType": "ed2k",
                  "pending": false,
                  "missing": false,
                  "added_at": 3000,
                  "rawScore": 4.65,
                  "score": 5.0,
                  "votes": 8009
                },
                {
                  "code": "PAGED-002",
                  "link": "$pending",
                  "linkPending": true,
                  "pending": true,
                  "added_at": 2000
                },
                {
                  "code": "PAGED-003",
                  "link": "https://example.test/not-a-link",
                  "added_at": 1000
                }
              ],
              "total": 3,
              "snapshot": "abc123",
              "offset": 0,
              "limit": 60,
              "updated_at": 4000,
              "actors": ["dropped"],
              "genres": {"c1=1": ["x", "y", "z"]}
            }
        """.trimIndent()

        val library = parseLtxLibrary(json)!!

        // 分页协议字段进契约
        assertEquals(4000L, library.updatedAtMillis)
        assertEquals(listOf("PAGED-001", "PAGED-002", "PAGED-003"), library.entries.map { it.code })

        // score 直采站点加权展示分（rawScore/votes 不进契约，忽略）
        val first = library.entries[0]
        assertEquals(5.0, first.score!!, 1e-9)
        // 单链接契约：linkPending=false → 已确认链接，pendingLinks 为空
        assertEquals(listOf(confirmed), first.links.map { it.uri })
        assertEquals(4096L, first.links[0].sizeBytes)
        assertEquals(0, first.pendingLinks.size)

        // linkPending=true → 归入待确认链接，与旧契约 UI 语义对齐
        val second = library.entries[1]
        assertEquals(0, second.links.size)
        assertEquals(listOf(pending), second.pendingLinks.map { it.uri })

        // 无法识别的 scheme 丢弃，条目保留（无链接）
        val third = library.entries[2]
        assertEquals(0, third.links.size)
        assertEquals(0, third.pendingLinks.size)
    }

    @Test
    fun `legacy array contract still parses for old mirrors and caches`() {
        val json = """
            {
              "items": [
                {
                  "code": "OLD-001",
                  "links": ["ed2k://|file|OLD-001.mp4|2048|00000000000000000000000000000009|/"],
                  "pending_links": [],
                  "pending": false,
                  "added_at": 1,
                  "score": 4.1,
                  "votes": 100
                }
              ],
              "updated_at": 2
            }
        """.trimIndent()
        val library = parseLtxLibrary(json)!!
        val entry = library.entries.single()
        assertEquals(1, entry.links.size)
        // 旧契约 score 即原始分，直接透传（排序回退语义）
        assertEquals(4.1, entry.score!!, 1e-9)
    }

    @Test
    fun `duplicate codes in payload merge into one entry with union links`() {
        val ed2k = "ed2k://|file|DUP-100_ed2k.mp4|4096|00000000000000000000000000000001|/"
        val magnet = "magnet:?xt=urn:btih:0000000000000000000000000000000000000001&dn=DUP-100"
        val pendingEd2k = "ed2k://|file|DUP-100_pending.mp4|1024|00000000000000000000000000000002|/"
        val json = """
            {
              "items": [
                {
                  "code": "DUP-100",
                  "link": "$ed2k",
                  "linkPending": false,
                  "pending": false,
                  "added_at": 5000,
                  "date": "2026-11-01",
                  "score": 4.5
                },
                {
                  "code": "dup-100",
                  "link": "$pendingEd2k",
                  "linkPending": true,
                  "pending": true,
                  "added_at": 3000
                },
                {
                  "code": "DUP-100",
                  "link": "$magnet",
                  "linkPending": false,
                  "pending": false,
                  "added_at": 4000
                }
              ],
              "total": 3,
              "updated_at": 6000
            }
        """.trimIndent()

        val library = parseLtxLibrary(json)!!

        // 三行同码（含大小写差异）聚合为一条目，排序取最新入库时间
        val entry = library.entries.single()
        assertEquals("DUP-100", entry.code)
        assertEquals(5000L, entry.addedAtMillis)
        // 已确认链接并集（ed2k + magnet），待确认剔除已确认后附属
        assertEquals(listOf(ed2k, magnet), entry.links.map { it.uri })
        assertEquals(listOf(pendingEd2k), entry.pendingLinks.map { it.uri })
        // 任一行已确认即视为已确认；日期/评分取最新可用值
        assertEquals(false, entry.isPending)
        assertEquals("2026-11-01", entry.date)
        assertEquals(4.5, entry.score!!, 1e-9)
    }

    @Test
    fun `entry stays pending only when every duplicate row is pending`() {
        val pending1 = "ed2k://|file|DUP-200_a.mp4|1024|00000000000000000000000000000003|/"
        val pending2 = "ed2k://|file|DUP-200_b.mp4|1024|00000000000000000000000000000004|/"
        val allPending = """
            {"items":[
              {"code":"DUP-200","link":"$pending1","linkPending":true,"pending":true,"added_at":10},
              {"code":"DUP-200","link":"$pending2","linkPending":true,"pending":true,"added_at":5}
            ]}
        """.trimIndent()
        val entry = parseLtxLibrary(allPending)!!.entries.single()
        assertEquals(listOf(pending1, pending2), entry.pendingLinks.map { it.uri })
        assertEquals(true, entry.isPending)

        // 其余条目不受聚合影响
        val single = parseLtxLibrary(
            """{"items":[{"code":"SOLO-1","link":"$pending1","linkPending":true,"pending":true,"added_at":1}]}"""
        )!!.entries.single()
        assertEquals(true, single.isPending)
    }

    @Test
    fun `page preserves server order regardless of added_at`() {
        // 分页协议下排序（评分/入库/日期/番号）由服务端执行：解析层不得按
        // added_at 重排（遗留重排曾把评分倒序页改写成入库倒序，列表与站点对不上）
        val json = """
            {
              "items": [
                {"code": "S-001", "link": "ed2k://|file|s1.mp4|1024|00000000000000000000000000000001|/", "added_at": 500},
                {"code": "S-002", "link": "ed2k://|file|s2.mp4|1024|00000000000000000000000000000002|/", "added_at": 900},
                {"code": "S-003", "link": "ed2k://|file|s3.mp4|1024|00000000000000000000000000000003|/", "added_at": 100}
              ],
              "total": 3
            }
        """.trimIndent()

        val library = parseLtxLibrary(json)!!
        // added_at 非单调，但服务端顺序（评分倒序页原样）必须原样保留
        assertEquals(listOf("S-001", "S-002", "S-003"), library.entries.map { it.code })
    }

    @Test
    fun `duplicate codes keep first occurrence position after merge`() {
        val json = """
            {
              "items": [
                {"code": "S-001", "link": "ed2k://|file|s1a.mp4|1024|00000000000000000000000000000001|/", "added_at": 100},
                {"code": "S-002", "link": "ed2k://|file|s2.mp4|1024|00000000000000000000000000000002|/", "added_at": 900},
                {"code": "S-001", "link": "ed2k://|file|s1b.mp4|1024|00000000000000000000000000000005|/", "added_at": 800}
              ],
              "total": 3
            }
        """.trimIndent()

        val library = parseLtxLibrary(json)!!

        // 同码两行聚合为一条目，位置取首次出现处（S-001 在前不因新行 added_at 更大而挪位）
        assertEquals(listOf("S-001", "S-002"), library.entries.map { it.code })
        assertEquals(2, library.entries[0].links.size)
    }

    @Test
    fun `default base url is the migrated domain`() {
        // 2026-11-15 起站点迁移至自有域名（旧 workers.dev 接口已 410 停用）
        assertEquals("https://ltxlib.dpdns.org", DEFAULT_LTX_BASE_URL)
    }

    @Test
    fun `normalize maps legacy defaults to current default and trims input`() {
        assertEquals(DEFAULT_LTX_BASE_URL, normalizeLtxBaseUrl("https://ltx.libray.workers.dev"))
        assertEquals(DEFAULT_LTX_BASE_URL, normalizeLtxBaseUrl("https://ltx.libray.workers.dev/"))
        assertEquals(DEFAULT_LTX_BASE_URL, normalizeLtxBaseUrl("  "))
        assertEquals(DEFAULT_LTX_BASE_URL, normalizeLtxBaseUrl(DEFAULT_LTX_BASE_URL))
        // 自定义地址保留，仅去空白与尾斜杠
        assertEquals("https://mirror.example.test", normalizeLtxBaseUrl(" https://mirror.example.test/ "))
    }

    @Test
    fun `parse migrated announcement extracts https target`() {
        val body = """
            {"error":"原站已迁移至 https://ltxlib.dpdns.org/，旧版接口已停用","reason":"migrated","url":"https://ltxlib.dpdns.org/"}
        """.trimIndent()
        assertEquals("https://ltxlib.dpdns.org", parseLtxMigratedBaseUrl(body))
    }

    @Test
    fun `parse migrated announcement rejects other bodies`() {
        // 人机验证门、缺 url、非 https、非 JSON 均不视为迁移公告
        assertNull(parseLtxMigratedBaseUrl("""{"error":"请先完成人机验证","reason":"verify","sitekey":"0x4X"}"""))
        assertNull(parseLtxMigratedBaseUrl("""{"reason":"migrated"}"""))
        assertNull(parseLtxMigratedBaseUrl("""{"reason":"migrated","url":"http://insecure.example.test"}"""))
        assertNull(parseLtxMigratedBaseUrl("<html>migrated</html>"))
        assertNull(parseLtxMigratedBaseUrl(""))
    }
}

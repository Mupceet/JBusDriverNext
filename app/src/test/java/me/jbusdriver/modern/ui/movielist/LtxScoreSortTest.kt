package me.jbusdriver.modern.ui.movielist

import me.jbusdriver.modern.domain.model.LtxEntry
import me.jbusdriver.modern.domain.model.LtxLibrary
import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * 评分排序 = 网站同款加权评分（贝叶斯先验 + 新入库加成 − pending 扣减）。
 * 常量与公式逆向自站点 2026-11 前端脚本；评论活跃加成因 /api/items 无
 * people/likers 字段恒为 0（站点上仅影响少数评论较多条目的次序）。
 */
class LtxScoreSortTest {

    private val nowMillis = 1_700_000_000_000L
    private val dayMillis = 86_400_000L

    private fun entry(
        code: String,
        score: Double?,
        votes: Int?,
        addedDaysAgo: Double,
        isPending: Boolean = false,
    ) = LtxEntry(
        code = code,
        score = score,
        votes = votes,
        addedAtMillis = nowMillis - (addedDaysAgo * dayMillis).toLong(),
        isPending = isPending,
    )

    private fun sortByScoreDesc(library: LtxLibrary): List<String> =
        computeLtxListItems(
            library = library,
            snapshots = emptyMap(),
            sortField = LtxSortField.SCORE,
            sortDirection = LtxSortDirection.DESC,
            statusFilter = LtxStatusFilter.ALL,
            resolveFilter = LtxResolveFilter.DEFAULT,
            nowMillis = nowMillis,
        ).map { it.code }

    @Test
    fun `high-vote recent item outranks raw-higher low-vote veteran`() {
        // 复刻站点案例：XV-611 原始 5.0 分仅 7 票，ABW-087 4.65 分 8009 票新入库——
        // 网站上 ABW-087 排第一
        val library = LtxLibrary(
            entries = listOf(
                entry("XV-611", 5.0, 7, addedDaysAgo = 400.0),
                entry("ABW-087", 4.65, 8009, addedDaysAgo = 2.0),
            )
        )
        assertEquals(listOf("ABW-087", "XV-611"), sortByScoreDesc(library))
    }

    @Test
    fun `pending items are penalized by one point`() {
        val library = LtxLibrary(
            entries = listOf(
                entry("P-1", 4.5, 1000, addedDaysAgo = 1.0, isPending = true),
                entry("C-1", 4.5, 1000, addedDaysAgo = 1.0),
            )
        )
        assertEquals(listOf("C-1", "P-1"), sortByScoreDesc(library))
    }

    @Test
    fun `entries without score always sort last in both directions`() {
        val library = LtxLibrary(
            entries = listOf(
                entry("N-1", null, null, addedDaysAgo = 0.0),
                entry("S-1", 4.0, 100, addedDaysAgo = 100.0),
            )
        )
        assertEquals(listOf("S-1", "N-1"), sortByScoreDesc(library))
        val ascending = computeLtxListItems(
            library = library,
            snapshots = emptyMap(),
            sortField = LtxSortField.SCORE,
            sortDirection = LtxSortDirection.ASC,
            statusFilter = LtxStatusFilter.ALL,
            resolveFilter = LtxResolveFilter.DEFAULT,
            nowMillis = nowMillis,
        ).map { it.code }
        assertEquals(listOf("S-1", "N-1"), ascending)
    }

    @Test
    fun `weighted value matches site formula numerically`() {
        // mean=4.403、票数 25 分位=441 时，ABW-087（4.65/8009 票、入库 2.45 天）
        // 的加权分 ≈ 4.9029，与站点页面渲染值（无评论条目）一致
        val library = LtxLibrary(
            entries = listOf(
                entry("ABW-087", 4.65, 8009, addedDaysAgo = 2.45),
                entry("F-1", 3.981, 441, addedDaysAgo = 400.0),
                entry("F-2", 3.981, 441, addedDaysAgo = 400.0),
                entry("XV-611", 5.0, 7, addedDaysAgo = 400.0),
            )
        )
        val stats = ltxScoreStats(library.entries)!!
        assertEquals(4.403, stats.first, 1e-9)
        assertEquals(441, stats.second)
        val abw = sortByScoreDesc(library).first()
        val model = computeLtxListItems(
            library = library,
            snapshots = emptyMap(),
            sortField = LtxSortField.SCORE,
            sortDirection = LtxSortDirection.DESC,
            statusFilter = LtxStatusFilter.ALL,
            resolveFilter = LtxResolveFilter.DEFAULT,
            nowMillis = nowMillis,
        ).first()
        assertEquals(abw, model.code)
        assertEquals(4.9028, weightedLtxScore(model, stats.first, stats.second, nowMillis)!!, 0.0005)
    }
}

package me.jbusdriver.modern.ui.movielist

import me.jbusdriver.modern.domain.model.LtxEntry
import me.jbusdriver.modern.domain.model.LtxLibrary
import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * 评分排序：直接采用站点加权展示分（2026-11 分页契约起随条目下发，含票数
 * 先验/新库加成/pending 扣减/评论加成），名次与站点逐位一致；无评分条目排尾。
 */
class LtxScoreSortTest {

    private fun entry(code: String, score: Double?) = LtxEntry(
        code = code,
        score = score,
    )

    private fun sortByScore(
        library: LtxLibrary,
        direction: LtxSortDirection,
    ): List<String> =
        computeLtxListItems(
            library = library,
            snapshots = emptyMap(),
            sortField = LtxSortField.SCORE,
            sortDirection = direction,
            statusFilter = LtxStatusFilter.ALL,
            resolveFilter = LtxResolveFilter.DEFAULT,
        ).map { it.code }

    @Test
    fun `sorts by site weighted score descending and ascending`() {
        // 站点加权分：原始分更高但票数少的条目名次更低（站点已算好，直采即可）
        val library = LtxLibrary(
            entries = listOf(
                entry("XV-611", 5.0),
                entry("ABW-087", 4.945),
                entry("C-1", 4.1),
            )
        )
        assertEquals(listOf("XV-611", "ABW-087", "C-1"), sortByScore(library, LtxSortDirection.DESC))
        assertEquals(listOf("C-1", "ABW-087", "XV-611"), sortByScore(library, LtxSortDirection.ASC))
    }

    @Test
    fun `entries without score always sort last in both directions`() {
        val library = LtxLibrary(
            entries = listOf(
                entry("N-1", null),
                entry("S-1", 4.0),
                entry("N-2", null),
            )
        )
        assertEquals(listOf("S-1", "N-1", "N-2"), sortByScore(library, LtxSortDirection.DESC))
        assertEquals(listOf("S-1", "N-1", "N-2"), sortByScore(library, LtxSortDirection.ASC))
    }

    @Test
    fun `library without any scored entries keeps filter order`() {
        val library = LtxLibrary(
            entries = listOf(
                entry("N-1", null),
                entry("N-2", null),
            )
        )
        assertEquals(listOf("N-1", "N-2"), sortByScore(library, LtxSortDirection.DESC))
    }
}

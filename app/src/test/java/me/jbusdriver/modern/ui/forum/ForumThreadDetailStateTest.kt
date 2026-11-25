package me.jbusdriver.modern.ui.forum

import me.jbusdriver.R
import me.jbusdriver.modern.core.cache.CacheEntry
import me.jbusdriver.modern.core.cache.CacheSource
import me.jbusdriver.modern.core.cache.CachedLoadEvent
import me.jbusdriver.modern.core.cache.FreshRevalidateOutcome
import me.jbusdriver.modern.data.settings.ForumFloorOrder
import me.jbusdriver.modern.domain.model.Comment
import me.jbusdriver.modern.domain.model.ForumReply
import me.jbusdriver.modern.domain.model.ForumThreadDetail
import me.jbusdriver.modern.domain.model.PageInfo
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Test

class ForumThreadDetailStateTest {

    @Test
    fun `floor order reload keeps current detail visible without loading state`() {
        val detail = detail("Title")
        val state = ForumThreadDetailUiState(
            detail = detail,
            floorOrder = ForumFloorOrder.REGULAR,
            isLoading = false,
            isLoadingMore = true
        )

        val next = state.prepareFloorOrderReload(ForumFloorOrder.REVERSE)

        assertSame(detail, next.detail)
        assertEquals(ForumFloorOrder.REVERSE, next.floorOrder)
        assertFalse(next.isLoading)
        assertFalse(next.isLoadingMore)
    }

    @Test
    fun `load detail fresh away from top stores changed detail as pending`() {
        // 已加载楼层（共有楼层）内容变化（编辑）→ 提示刷新
        val current = detail("Title", replies = listOf(reply(1), reply(2)))
        val fresh = detail("Title", replies = listOf(reply(1), reply(2).copy(author = "edited")))
        val state = ForumThreadDetailUiState(
            detail = current,
            isRevalidating = true,
            isChangingFloorOrder = true
        )

        val reduction = state.applyLoadDetailFresh(
            entry = cacheEntry(fresh),
            isAtTop = false,
            forceApply = false
        )

        assertEquals(FreshRevalidateOutcome.StorePending, reduction.outcome)
        assertSame(current, reduction.state.detail)
        assertEquals(fresh, reduction.state.pendingFreshDetail)
        assertFalse(reduction.state.isLoading)
        assertFalse(reduction.state.isRevalidating)
        assertFalse(reduction.state.isChangingFloorOrder)
    }

    @Test
    fun `detail revalidate without changes silently refreshes timestamp and keeps detail`() {
        val current = detail("Title", replies = listOf(reply(1), reply(2)))
        val state = ForumThreadDetailUiState(detail = current, isRevalidating = true)

        val reduction = state.applyDetailRevalidateFresh(
            entry = cacheEntry(current),
            isAtTop = false
        )

        assertEquals(FreshRevalidateOutcome.ApplyInPlace, reduction.outcome)
        assertEquals(current, reduction.state.detail)
        assertEquals(1_000L, reduction.state.lastUpdatedAtMillis)
        assertFalse(reduction.state.isRevalidating)
        assertNull(reduction.state.pendingFreshDetail)
    }

    @Test
    fun `counter-only change away from top silently refreshes counters without prompting`() {
        val current = detail("Title", replies = listOf(reply(1))).copy(viewCount = 1_000, replyCount = 50)
        val fresh = detail("Title", replies = listOf(reply(1))).copy(viewCount = 5_000, replyCount = 51)
        val state = ForumThreadDetailUiState(detail = current, isRevalidating = true)

        val reduction = state.applyDetailRevalidateFresh(
            entry = cacheEntry(fresh),
            isAtTop = false
        )

        assertEquals(FreshRevalidateOutcome.ApplyInPlace, reduction.outcome)
        assertNull(reduction.state.pendingFreshDetail)
        assertFalse(reduction.state.isRevalidating)
        assertEquals(5_000, reduction.state.detail?.viewCount)
        assertEquals(51, reduction.state.detail?.replyCount)
    }

    @Test
    fun `counter-only revalidate preserves already loaded later-page replies`() {
        // 用户已翻页合并了 floor 1..3；仅计数器变化的 revalidate 不应丢掉已加载的后续楼层，
        // 且已翻页时保留旧翻页链（fresh 只覆盖第 1 页窗口）。
        val pagedPageInfo = PageInfo(activePage = 2, nextPage = 3, referPages = listOf(1, 2, 3))
        val current = detail("Title", replies = listOf(reply(1), reply(2), reply(3)))
            .copy(viewCount = 1_000, pageInfo = pagedPageInfo)
        val fresh = detail("Title", replies = listOf(reply(1))).copy(viewCount = 5_000)
        val state = ForumThreadDetailUiState(detail = current, isRevalidating = true)

        val reduction = state.applyDetailRevalidateFresh(
            entry = cacheEntry(fresh),
            isAtTop = false,
            hasLoadedBeyondFirstPage = true
        )

        assertEquals(FreshRevalidateOutcome.ApplyInPlace, reduction.outcome)
        assertNull(reduction.state.pendingFreshDetail)
        assertEquals(listOf(1, 2, 3), reduction.state.detail?.replies?.map { it.floor })
        assertEquals(5_000, reduction.state.detail?.viewCount)
        assertEquals(pagedPageInfo, reduction.state.detail?.pageInfo)
    }

    @Test
    fun `reverse order new replies merge silently without prompting`() {
        // 倒序：新增回复进入首屏 → 按楼层号对齐判定为纯新增 → 静默合并（旧索引对齐逻辑会误提示）。
        val current = detail("T", replies = listOf(reply(5), reply(4), reply(3)))
        val fresh = detail("T", replies = listOf(reply(6), reply(5), reply(4)))
        val state = ForumThreadDetailUiState(
            detail = current,
            floorOrder = ForumFloorOrder.REVERSE,
            isRevalidating = true
        )

        val reduction = state.applyDetailRevalidateFresh(
            entry = cacheEntry(fresh),
            isAtTop = false
        )

        assertEquals(FreshRevalidateOutcome.ApplyInPlace, reduction.outcome)
        assertNull(reduction.state.pendingFreshDetail)
        assertEquals(listOf(6, 5, 4, 3), reduction.state.detail?.replies?.map { it.floor })
        assertEquals(3, reduction.state.detail?.replyCount)
    }

    @Test
    fun `first page growth with unchanged floors merges silently and adopts fresh page info`() {
        // 未翻页时首页自身增长（3→6 楼，既有楼层未变）：静默拼接 + 计数器取 fresh + 采纳 fresh 翻页信息。
        val current = detail("T", replies = listOf(reply(1), reply(2), reply(3)))
        val freshPageInfo = PageInfo(activePage = 1, nextPage = 2, referPages = listOf(1, 2))
        val fresh = detail(
            "T",
            replies = listOf(reply(1), reply(2), reply(3), reply(4), reply(5), reply(6))
        ).copy(pageInfo = freshPageInfo)
        val state = ForumThreadDetailUiState(detail = current, isRevalidating = true)

        val reduction = state.applyDetailRevalidateFresh(
            entry = cacheEntry(fresh),
            isAtTop = false
        )

        assertEquals(FreshRevalidateOutcome.ApplyInPlace, reduction.outcome)
        assertNull(reduction.state.pendingFreshDetail)
        assertEquals(
            listOf(1, 2, 3, 4, 5, 6),
            reduction.state.detail?.replies?.map { it.floor }
        )
        assertEquals(6, reduction.state.detail?.replyCount)
        assertEquals(freshPageInfo, reduction.state.detail?.pageInfo)
        assertEquals(1_000L, reduction.state.lastUpdatedAtMillis)
    }

    @Test
    fun `floor missing from fresh is kept silently without prompting`() {
        // 已加载但 fresh 未含的楼层（如被删）：无法区分窗口外与被删，从宽保留、不提示；计数器仍取 fresh。
        val current = detail("T", replies = listOf(reply(4), reply(3), reply(2)))
        val fresh = detail("T", replies = listOf(reply(4), reply(2)))
        val state = ForumThreadDetailUiState(
            detail = current,
            floorOrder = ForumFloorOrder.REVERSE,
            isRevalidating = true
        )

        val reduction = state.applyDetailRevalidateFresh(
            entry = cacheEntry(fresh),
            isAtTop = false
        )

        assertEquals(FreshRevalidateOutcome.ApplyInPlace, reduction.outcome)
        assertNull(reduction.state.pendingFreshDetail)
        assertEquals(listOf(4, 3, 2), reduction.state.detail?.replies?.map { it.floor })
        assertEquals(2, reduction.state.detail?.replyCount)
    }

    @Test
    fun `pinned floor keeps its top slot when new replies merge silently`() {
        // 置顶楼层显示在列表顶部（楼层号乱序）：静默合并只追加新增楼层，不得全局重排
        // 把置顶楼层拉回楼层号顺序位置（mergeReplyPages 的排序会，回归自 3d8eb050）。
        val pinned = reply(12).copy(isPinned = true)
        val current = detail("T", replies = listOf(pinned, reply(2), reply(3), reply(4)))
        val fresh = detail(
            "T",
            replies = listOf(reply(12).copy(isPinned = true), reply(2), reply(3), reply(4), reply(5))
        )
        val state = ForumThreadDetailUiState(detail = current, isRevalidating = true)

        val reduction = state.applyDetailRevalidateFresh(
            entry = cacheEntry(fresh),
            isAtTop = false
        )

        assertEquals(FreshRevalidateOutcome.ApplyInPlace, reduction.outcome)
        assertEquals(
            listOf(12, 2, 3, 4, 5),
            reduction.state.detail?.replies?.map { it.floor }
        )
        assertEquals(true, reduction.state.detail?.replies?.first()?.isPinned)
    }

    @Test
    fun `reverse order pinned floor keeps its slot and new replies insert after it`() {
        // 倒序 + 置顶：置顶楼保持首位，新增楼层（楼层号最大）插在置顶楼之后。
        val current = detail(
            "T",
            replies = listOf(reply(12).copy(isPinned = true), reply(11), reply(10))
        )
        val fresh = detail(
            "T",
            replies = listOf(reply(12).copy(isPinned = true), reply(13), reply(11), reply(10))
        )
        val state = ForumThreadDetailUiState(
            detail = current,
            floorOrder = ForumFloorOrder.REVERSE,
            isRevalidating = true
        )

        val reduction = state.applyDetailRevalidateFresh(
            entry = cacheEntry(fresh),
            isAtTop = false
        )

        assertEquals(FreshRevalidateOutcome.ApplyInPlace, reduction.outcome)
        assertEquals(
            listOf(12, 13, 11, 10),
            reduction.state.detail?.replies?.map { it.floor }
        )
    }

    @Test
    fun `relative comment time drift alone is not treated as new data`() {
        // 点评时间为相对文案（"半小时前"等），会持续刷新而非固定时间戳，不应据此判为有新数据。
        val current = detail("T", replies = listOf(reply(1).copy(comments = listOf(comment("c", "半小时前")))))
        val fresh = detail("T", replies = listOf(reply(1).copy(comments = listOf(comment("c", "1小时前")))))
        val state = ForumThreadDetailUiState(detail = current, isRevalidating = true)

        val reduction = state.applyDetailRevalidateFresh(
            entry = cacheEntry(fresh),
            isAtTop = false
        )

        assertEquals(FreshRevalidateOutcome.ApplyInPlace, reduction.outcome)
        assertNull(reduction.state.pendingFreshDetail)
    }

    @Test
    fun `real comment content change away from top still prompts to refresh`() {
        val current = detail("T", replies = listOf(reply(1).copy(comments = listOf(comment("old")))))
        val fresh = detail("T", replies = listOf(reply(1).copy(comments = listOf(comment("new")))))
        val state = ForumThreadDetailUiState(detail = current, isRevalidating = true)

        val reduction = state.applyDetailRevalidateFresh(
            entry = cacheEntry(fresh),
            isAtTop = false
        )

        assertEquals(FreshRevalidateOutcome.StorePending, reduction.outcome)
        assertEquals("楼层1#点评", reduction.changeReason)
    }

    @Test
    fun `load detail failure without cache shows initial error`() {
        val state = ForumThreadDetailUiState(isLoading = true)

        val result = state.applyLoadDetailFailure(
            event = CachedLoadEvent.Failure(IllegalStateException("offline"), hadCachedValue = false),
            hasContent = false
        )

        assertFalse(result.isLoading)
        assertFalse(result.isRevalidating)
        assertFalse(result.isChangingFloorOrder)
        assertEquals(R.string.load_failed, result.error)
    }

    @Test
    fun `floorGaps returns empty for contiguous or short reply lists`() {
        assertEquals(emptyList<FloorGap>(), floorGaps(emptyList(), ForumFloorOrder.REVERSE))
        assertEquals(emptyList<FloorGap>(), floorGaps(listOf(reply(3)), ForumFloorOrder.REGULAR))
        assertEquals(
            emptyList<FloorGap>(),
            floorGaps(listOf(reply(5), reply(4), reply(3)), ForumFloorOrder.REVERSE)
        )
        assertEquals(
            emptyList<FloorGap>(),
            floorGaps(listOf(reply(3), reply(4), reply(5)), ForumFloorOrder.REGULAR)
        )
    }

    @Test
    fun `floorGaps ignores single missing floor as deleted floor`() {
        // 4→2 缺第 3 楼（单个缺失=已删楼层），两种顺序都不视为断层
        assertEquals(
            emptyList<FloorGap>(),
            floorGaps(listOf(reply(5), reply(4), reply(2), reply(1)), ForumFloorOrder.REVERSE)
        )
        assertEquals(
            emptyList<FloorGap>(),
            floorGaps(listOf(reply(1), reply(2), reply(4), reply(5)), ForumFloorOrder.REGULAR)
        )
    }

    @Test
    fun `floorGaps marks discontinuity with above and below floors in both orders`() {
        val descending = listOf(reply(100), reply(99), reply(12), reply(11))
        assertEquals(
            listOf(FloorGap(aboveFloor = 99, belowFloor = 12)),
            floorGaps(descending, ForumFloorOrder.REVERSE)
        )
        val ascending = listOf(reply(11), reply(12), reply(99), reply(100))
        assertEquals(
            listOf(FloorGap(aboveFloor = 99, belowFloor = 12)),
            floorGaps(ascending, ForumFloorOrder.REGULAR)
        )
    }

    @Test
    fun `mergeReplyPages dedups by floor and sorts by order`() {
        val existing = listOf(reply(100), reply(99), reply(12), reply(11))
        val incoming = listOf(reply(99), reply(50), reply(13)) // 99 跨页重复，保留已展示版本

        val mergedReverse = mergeReplyPages(existing, incoming, ForumFloorOrder.REVERSE)
        assertEquals(listOf(100, 99, 50, 13, 12, 11), mergedReverse.map { it.floor })
        assertSame(existing[1], mergedReverse[1])

        val mergedRegular = mergeReplyPages(listOf(reply(12), reply(11)), incoming, ForumFloorOrder.REGULAR)
        assertEquals(listOf(11, 12, 13, 50, 99), mergedRegular.map { it.floor })
    }

    @Test
    fun `mergeReplyPages keeps pinned floor at top across page merges`() {
        // 翻页/跳转/补洞合并：置顶楼固定最前不参与排序（排序会拉回楼层号数值位置），跨页重复去重。
        val pinned = reply(12).copy(isPinned = true)
        val page1 = listOf(pinned, reply(2), reply(3))
        val page2 = listOf(reply(12), reply(4), reply(5)) // 置顶 12 跨页重复

        val merged = mergeReplyPages(page1, page2, ForumFloorOrder.REGULAR)
        assertEquals(listOf(12, 2, 3, 4, 5), merged.map { it.floor })
        assertEquals(true, merged.first().isPinned)

        val mergedReverse = mergeReplyPages(
            listOf(pinned, reply(3), reply(2)),
            listOf(reply(1)),
            ForumFloorOrder.REVERSE
        )
        assertEquals(listOf(12, 3, 2, 1), mergedReverse.map { it.floor })
    }

    @Test
    fun `buildReplyListItems inserts gap divider below upper floor in reverse order`() {
        val replies = listOf(reply(100), reply(99), reply(12), reply(11))

        val items = buildReplyListItems(replies, ForumFloorOrder.REVERSE, showGapDividers = true)

        assertEquals(
            listOf("reply_100", "reply_99", "gap_12", "reply_12", "reply_11"),
            items.map { it.key }
        )
    }

    @Test
    fun `buildReplyListItems inserts gap divider above lower floor in regular order`() {
        val replies = listOf(reply(11), reply(12), reply(99), reply(100))

        val items = buildReplyListItems(replies, ForumFloorOrder.REGULAR, showGapDividers = true)

        assertEquals(
            listOf("reply_11", "reply_12", "gap_12", "reply_99", "reply_100"),
            items.map { it.key }
        )
    }

    @Test
    fun `buildReplyListItems omits gap dividers when no more pages`() {
        val replies = listOf(reply(100), reply(99), reply(12), reply(11))

        val items = buildReplyListItems(replies, ForumFloorOrder.REVERSE, showGapDividers = false)

        assertEquals(listOf("reply_100", "reply_99", "reply_12", "reply_11"), items.map { it.key })
    }

    @Test
    fun `buildReplyListItems omits divider for single deleted floor`() {
        val replies = listOf(reply(5), reply(4), reply(2), reply(1))

        val items = buildReplyListItems(replies, ForumFloorOrder.REVERSE, showGapDividers = true)

        assertEquals(listOf("reply_5", "reply_4", "reply_2", "reply_1"), items.map { it.key })
    }

    private fun cacheEntry(value: ForumThreadDetail): CacheEntry<ForumThreadDetail> =
        CacheEntry(value, 1_000L, CacheSource.Network, false)

    private fun detail(
        title: String,
        replies: List<ForumReply> = emptyList()
    ) = ForumThreadDetail(
        tid = 168357,
        typeId = 0,
        typeName = "",
        typeColor = "",
        title = title,
        viewCount = 0,
        replyCount = replies.size,
        author = "",
        authorUid = 0,
        authorAvatar = "",
        postTime = "",
        contentBlocks = emptyList(),
        comments = emptyList(),
        replies = replies,
        pageInfo = PageInfo()
    )

    private fun reply(floor: Int) = ForumReply(
        floor = floor,
        author = "A$floor",
        authorUid = floor,
        authorAvatar = "",
        authorGroup = "",
        contentBlocks = emptyList(),
        postTime = ""
    )

    private fun comment(content: String, time: String = "") = Comment(
        author = "C",
        authorAvatar = "",
        content = content,
        time = time
    )
}

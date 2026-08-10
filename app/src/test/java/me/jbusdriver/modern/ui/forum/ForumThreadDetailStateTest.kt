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
        val current = detail("Title", replies = listOf(reply(1), reply(2)))
        val fresh = detail("Title", replies = listOf(reply(10)))
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
        // 用户已翻页合并了 floor 1..3；仅计数器变化的 revalidate 不应丢掉已加载的后续楼层。
        val current = detail("Title", replies = listOf(reply(1), reply(2), reply(3)))
            .copy(viewCount = 1_000)
        val fresh = detail("Title", replies = listOf(reply(1))).copy(viewCount = 5_000)
        val state = ForumThreadDetailUiState(detail = current, isRevalidating = true)

        val reduction = state.applyDetailRevalidateFresh(
            entry = cacheEntry(fresh),
            isAtTop = false
        )

        assertEquals(FreshRevalidateOutcome.ApplyInPlace, reduction.outcome)
        assertNull(reduction.state.pendingFreshDetail)
        assertEquals(listOf(1, 2, 3), reduction.state.detail?.replies?.map { it.floor })
        assertEquals(5_000, reduction.state.detail?.viewCount)
    }

    @Test
    fun `new first-page reply away from top prompts to refresh`() {
        // 倒序：新增回复进入首屏楼层 → 视为内容变化 → 不在顶部时提示刷新。
        val current = detail("T", replies = listOf(reply(5), reply(4), reply(3)))
        val fresh = detail("T", replies = listOf(reply(6), reply(5), reply(4)))
        val state = ForumThreadDetailUiState(detail = current, isRevalidating = true)

        val reduction = state.applyDetailRevalidateFresh(
            entry = cacheEntry(fresh),
            isAtTop = false
        )

        assertEquals(FreshRevalidateOutcome.StorePending, reduction.outcome)
        assertEquals(fresh, reduction.state.pendingFreshDetail)
        assertSame(current, reduction.state.detail)
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

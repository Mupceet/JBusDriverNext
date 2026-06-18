package me.jbusdriver.modern.ui.forum

import me.jbusdriver.R
import me.jbusdriver.modern.core.cache.CacheEntry
import me.jbusdriver.modern.core.cache.CacheSource
import me.jbusdriver.modern.core.cache.CachedLoadEvent
import me.jbusdriver.modern.core.cache.FreshRevalidateOutcome
import me.jbusdriver.modern.data.ForumFloorOrder
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
        assertEquals(R.string.new_data_available, reduction.state.refreshMessage)
        assertFalse(reduction.state.isLoading)
        assertFalse(reduction.state.isRevalidating)
        assertFalse(reduction.state.isChangingFloorOrder)
    }

    @Test
    fun `detail revalidate fresh away from top without changes only stops revalidating`() {
        val current = detail("Title", replies = listOf(reply(1), reply(2)))
        val state = ForumThreadDetailUiState(detail = current, isRevalidating = true)

        val reduction = state.applyDetailRevalidateFresh(
            entry = cacheEntry(current),
            isAtTop = false
        )

        assertEquals(FreshRevalidateOutcome.NoChange, reduction.outcome)
        assertSame(current, reduction.state.detail)
        assertFalse(reduction.state.isRevalidating)
        assertNull(reduction.state.pendingFreshDetail)
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
}

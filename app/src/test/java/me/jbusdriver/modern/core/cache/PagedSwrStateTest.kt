package me.jbusdriver.modern.core.cache

import me.jbusdriver.modern.domain.model.PageInfo
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class PagedSwrStateTest {

    @Test
    fun pageTracker_startsAtZeroAndAdvances() {
        val tracker = PageTracker()
        assertEquals(0, tracker.current)

        tracker.startFirstPage()
        assertEquals(1, tracker.current)

        tracker.advanceTo(3)
        assertEquals(3, tracker.current)

        tracker.reset()
        assertEquals(0, tracker.current)
    }

    @Test
    fun pageTracker_rollbackRestoresActivePage() {
        val tracker = PageTracker().apply { advanceTo(5) }
        tracker.rollbackTo(2)
        assertEquals(2, tracker.current)
    }

    @Test
    fun shouldLoadMore_trueWhenNextPageAheadOfCurrent() {
        val tracker = PageTracker().apply { startFirstPage() } // current = 1
        assertTrue(tracker.shouldLoadMore(PageInfo(activePage = 1, nextPage = 2)))
    }

    @Test
    fun shouldLoadMore_falseWhenNoNextPage() {
        val tracker = PageTracker().apply { advanceTo(2) }
        // nextPage == current → no more
        assertFalse(tracker.shouldLoadMore(PageInfo(activePage = 2, nextPage = 2)))
    }

    @Test
    fun decideFresh_atTop_appliesImmediately() {
        val outcome = decideFreshRevalidate(
            currentItems = listOf("a", "b"),
            freshItems = listOf("a", "b", "c"),
            isAtTop = true
        )
        assertEquals(FreshRevalidateOutcome.ApplyImmediately, outcome)
    }

    @Test
    fun decideFresh_awayFromTopAndChanged_storesPending() {
        val outcome = decideFreshRevalidate(
            currentItems = listOf("a", "b", "c"),
            freshItems = listOf("a", "x", "c"),
            isAtTop = false
        )
        assertEquals(FreshRevalidateOutcome.StorePending, outcome)
    }

    @Test
    fun decideFresh_awayFromTopUnchanged_isNoChange() {
        val outcome = decideFreshRevalidate(
            currentItems = listOf("a", "b", "c"),
            freshItems = listOf("a", "b", "c"),
            isAtTop = false
        )
        assertEquals(FreshRevalidateOutcome.NoChange, outcome)
    }

    @Test
    fun decideFresh_comparesOnlyPrefixMatchingFreshLength() {
        // current has extra appended items (loaded more); only the first-page prefix is compared.
        val outcome = decideFreshRevalidate(
            currentItems = listOf("a", "b", "c", "d", "e"), // pages 1 + 2 loaded
            freshItems = listOf("a", "b", "c"),             // fresh first page
            isAtTop = false
        )
        assertEquals(FreshRevalidateOutcome.NoChange, outcome)
    }

    @Test
    fun pagedSwrHolder_tracksPagesAndTopGate() {
        val holder = PagedSwrStateHolder<String>()
        assertEquals(0, holder.pages.current)
        assertTrue(holder.atTop.isAtTop)

        holder.atTop.isAtTop = false
        holder.pages.startFirstPage()

        assertEquals(1, holder.pages.current)
        assertFalse(holder.atTop.isAtTop)
    }

    @Test
    fun pagedSwrHolder_generationGuardDiscardsStaleIdentity() {
        val holder = PagedSwrStateHolder<String>()
        val stale = holder.begin("list-a")
        val current = holder.begin("list-b")

        // 旧代际 + 旧身份：过期
        assertFalse(holder.isCurrent(stale, "list-a"))
        // 新代际 + 新身份：有效
        assertTrue(holder.isCurrent(current, "list-b"))
        // 新代际 + 旧身份：无效
        assertFalse(holder.isCurrent(current, "list-a"))
    }

    @Test
    fun pagedSwrHolder_resetForNewSourceResetsPagination() {
        val holder = PagedSwrStateHolder<String>()
        holder.pages.advanceTo(4)
        holder.resetForNewSource()
        assertEquals(0, holder.pages.current)
    }
}

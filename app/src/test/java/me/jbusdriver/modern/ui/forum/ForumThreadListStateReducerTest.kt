package me.jbusdriver.modern.ui.forum

import me.jbusdriver.R
import me.jbusdriver.modern.core.cache.CacheEntry
import me.jbusdriver.modern.core.cache.CacheSource
import me.jbusdriver.modern.core.cache.CachedLoadEvent
import me.jbusdriver.modern.core.cache.FreshRevalidateOutcome
import me.jbusdriver.modern.domain.model.ForumThread
import me.jbusdriver.modern.domain.model.ForumThreadPageResult
import me.jbusdriver.modern.domain.model.ForumTypeFilter
import me.jbusdriver.modern.domain.model.PageInfo
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ForumThreadListStateReducerTest {

    @Test
    fun applyFirstPageCached_usesCachedThreadsAndPreservesExistingFiltersWhenCacheHasNone() {
        val existingFilters = listOf(typeFilter(1, "News"))
        val state = ForumThreadListUiState(
            typeFilters = existingFilters,
            isLoading = true
        )

        val result = state.applyFirstPageCached(
            entry = cacheEntry(threadResult(2, filters = emptyList()), isExpired = true)
        )

        assertEquals(listOf(1, 2), result.threads.map { it.tid })
        assertEquals(existingFilters, result.typeFilters)
        assertFalse(result.isLoading)
        assertTrue(result.isRevalidating)
        assertEquals(1_000L, result.lastUpdatedAtMillis)
    }

    @Test
    fun applyFirstPageFresh_atTopAppliesFreshThreadsImmediately() {
        val state = ForumThreadListUiState(
            threads = listOf(thread(1)),
            isLoading = true,
            pendingFreshThreads = threadResult(9)
        )

        val reduction = state.applyFirstPageFresh(
            entry = cacheEntry(threadResult(2)),
            isAtTop = true
        )

        assertEquals(FreshRevalidateOutcome.ApplyImmediately, reduction.outcome)
        assertEquals(listOf(1, 2), reduction.state.threads.map { it.tid })
        assertFalse(reduction.state.isLoading)
        assertFalse(reduction.state.isRevalidating)
        assertNull(reduction.state.pendingFreshThreads)
        assertNull(reduction.state.refreshMessage)
    }

    @Test
    fun applyFirstPageFresh_awayFromTopStoresChangedFreshThreads() {
        val state = ForumThreadListUiState(
            threads = listOf(thread(1), thread(2), thread(3)),
            isLoading = true
        )
        val fresh = threadResult(2, startTid = 10)

        val reduction = state.applyFirstPageFresh(
            entry = cacheEntry(fresh),
            isAtTop = false
        )

        assertEquals(FreshRevalidateOutcome.StorePending, reduction.outcome)
        assertEquals(listOf(1, 2, 3), reduction.state.threads.map { it.tid })
        assertEquals(fresh, reduction.state.pendingFreshThreads)
        assertEquals(R.string.new_data_available, reduction.state.refreshMessage)
        assertFalse(reduction.state.isLoading)
    }

    @Test
    fun applyFirstPageFailure_withoutCacheShowsInitialError() {
        val state = ForumThreadListUiState(isLoading = true)

        val result = state.applyFirstPageFailure(
            event = CachedLoadEvent.Failure(IllegalStateException("offline"), hadCachedValue = false),
            hasContent = false
        )

        assertFalse(result.isLoading)
        assertFalse(result.isRevalidating)
        assertEquals(R.string.load_failed, result.error)
    }

    @Test
    fun applyFreshRevalidate_awayFromTopWithoutChangesOnlyStopsRevalidating() {
        val state = ForumThreadListUiState(
            threads = listOf(thread(1), thread(2)),
            isRevalidating = true
        )

        val reduction = state.applyFreshRevalidate(
            entry = cacheEntry(threadResult(2)),
            isAtTop = false
        )

        assertEquals(FreshRevalidateOutcome.NoChange, reduction.outcome)
        assertEquals(listOf(1, 2), reduction.state.threads.map { it.tid })
        assertFalse(reduction.state.isRevalidating)
        assertNull(reduction.state.pendingFreshThreads)
    }

    private fun cacheEntry(
        value: ForumThreadPageResult,
        isExpired: Boolean = false
    ): CacheEntry<ForumThreadPageResult> =
        CacheEntry(value, 1_000L, CacheSource.Network, isExpired)

    private fun threadResult(
        count: Int,
        startTid: Int = 1,
        filters: List<ForumTypeFilter> = listOf(typeFilter(1, "News"))
    ) = ForumThreadPageResult(
        threads = List(count) { thread(startTid + it) },
        typeFilters = filters,
        pageInfo = PageInfo(1, 2, listOf(1, 2))
    )

    private fun typeFilter(typeId: Int, name: String) =
        ForumTypeFilter(typeId, name, color = "", count = 0)

    private fun thread(tid: Int) = ForumThread(
        tid = tid,
        typeId = 0,
        typeName = "T",
        typeColor = "",
        title = "Thread $tid",
        author = "A",
        authorUid = 0,
        authorAvatar = "",
        dateLine = "",
        viewCount = 0,
        replyCount = 0,
        lastReplyAuthor = "",
        lastReplyTime = "",
        images = emptyList(),
        isPinned = false,
        isDigest = false,
        pages = 1,
        isLocked = false,
        isHot = false
    )
}

package me.jbusdriver.modern.ui.movielist

import me.jbusdriver.R
import me.jbusdriver.modern.core.cache.CacheEntry
import me.jbusdriver.modern.core.cache.CacheSource
import me.jbusdriver.modern.core.cache.CachedLoadEvent
import me.jbusdriver.modern.core.cache.FreshRevalidateOutcome
import me.jbusdriver.modern.domain.model.ActressInfo
import me.jbusdriver.modern.domain.model.PageInfo
import me.jbusdriver.modern.ui.toActressUiModel
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ActressListStateReducerTest {

    @Test
    fun firstPageCached_appliesCachedContentAndMarksExpiredAsRevalidating() {
        val entry = cacheEntry(
            value = listOf(actress("A")) to PageInfo(activePage = 1, nextPage = 2),
            isExpired = true,
            storedAtMillis = 123L
        )

        val state = ActressListUiState(isLoading = true)
            .applyFirstPageCached(entry)

        assertEquals(listOf("A"), state.actresses.map { it.name })
        assertEquals(PageInfo(activePage = 1, nextPage = 2), state.pageInfo)
        assertFalse(state.isLoading)
        assertFalse(state.isRefreshing)
        assertTrue(state.hasMore)
        assertTrue(state.isRevalidating)
        assertEquals(123L, state.lastUpdatedAtMillis)
        assertNull(state.error)
    }

    @Test
    fun firstPageFresh_appliesFreshContentAndClearsLoadingFlags() {
        val entry = cacheEntry(
            value = listOf(actress("B")) to PageInfo(activePage = 1, nextPage = 1),
            storedAtMillis = 456L
        )

        val state = ActressListUiState(
            isLoading = true,
            isRefreshing = true,
            isRevalidating = true
        ).applyFirstPageFresh(entry)

        assertEquals(listOf("B"), state.actresses.map { it.name })
        assertEquals(PageInfo(activePage = 1, nextPage = 1), state.pageInfo)
        assertFalse(state.isLoading)
        assertFalse(state.isRefreshing)
        assertFalse(state.isRevalidating)
        assertFalse(state.hasMore)
        assertEquals(456L, state.lastUpdatedAtMillis)
    }

    @Test
    fun firstPageFailure_withoutContentShowsLoadError() {
        val state = ActressListUiState(isLoading = true, isRevalidating = true)
            .applyFirstPageFailure(
                event = CachedLoadEvent.Failure(RuntimeException("boom"), hadCachedValue = false),
                hasContent = false
            )

        assertFalse(state.isLoading)
        assertFalse(state.isRefreshing)
        assertFalse(state.isRevalidating)
        assertEquals(R.string.load_failed, state.error)
    }

    @Test
    fun firstPageFailure_withContentKeepsCurrentDataAndClearsLoadingFlags() {
        val current = ActressListUiState(
            actresses = listOf(actress("A").toActressUiModel()),
            isLoading = true,
            isRefreshing = true,
            isRevalidating = true
        )

        val state = current.applyFirstPageFailure(
            event = CachedLoadEvent.Failure(RuntimeException("boom"), hadCachedValue = true),
            hasContent = true
        )

        assertEquals(current.actresses, state.actresses)
        assertFalse(state.isLoading)
        assertFalse(state.isRefreshing)
        assertFalse(state.isRevalidating)
        assertNull(state.error)
    }

    @Test
    fun freshRevalidate_atTopAppliesImmediately() {
        val entry = cacheEntry(
            value = listOf(actress("C")) to PageInfo(activePage = 1, nextPage = 2),
            storedAtMillis = 789L
        )

        val reduction = ActressListUiState(
            actresses = listOf(actress("A").toActressUiModel()),
            isRevalidating = true,
            pendingFreshActresses = listOf(actress("old")) to PageInfo(activePage = 1, nextPage = 1),
            refreshMessage = R.string.new_data_available
        ).applyFreshRevalidate(entry, isAtTop = true)

        assertEquals(FreshRevalidateOutcome.ApplyImmediately, reduction.outcome)
        assertEquals(listOf("C"), reduction.state.actresses.map { it.name })
        assertFalse(reduction.state.isRevalidating)
        assertNull(reduction.state.pendingFreshActresses)
        assertNull(reduction.state.refreshMessage)
        assertEquals(789L, reduction.state.lastUpdatedAtMillis)
        assertTrue(reduction.state.hasMore)
    }

    @Test
    fun freshRevalidate_awayFromTopWithChangedFirstPageStoresPending() {
        val fresh = listOf(actress("C")) to PageInfo(activePage = 1, nextPage = 2)

        val reduction = ActressListUiState(
            actresses = listOf(actress("A").toActressUiModel()),
            isRevalidating = true
        ).applyFreshRevalidate(
            entry = cacheEntry(value = fresh),
            isAtTop = false
        )

        assertEquals(FreshRevalidateOutcome.StorePending, reduction.outcome)
        assertFalse(reduction.state.isRevalidating)
        assertEquals(fresh, reduction.state.pendingFreshActresses)
        assertEquals(R.string.new_data_available, reduction.state.refreshMessage)
    }

    @Test
    fun freshRevalidate_awayFromTopWithUnchangedFirstPageClearsRevalidatingOnly() {
        val current = ActressListUiState(
            actresses = listOf(actress("A").toActressUiModel()),
            isRevalidating = true
        )

        val reduction = current.applyFreshRevalidate(
            entry = cacheEntry(value = listOf(actress("A")) to PageInfo(activePage = 1, nextPage = 2)),
            isAtTop = false
        )

        assertEquals(FreshRevalidateOutcome.NoChange, reduction.outcome)
        assertEquals(current.actresses, reduction.state.actresses)
        assertFalse(reduction.state.isRevalidating)
        assertNull(reduction.state.pendingFreshActresses)
        assertNull(reduction.state.refreshMessage)
    }

    private fun cacheEntry(
        value: Pair<List<ActressInfo>, PageInfo>,
        isExpired: Boolean = false,
        storedAtMillis: Long = 1L
    ) = CacheEntry(value, storedAtMillis, CacheSource.Memory, isExpired)

    private fun actress(name: String) = ActressInfo(
        name = name,
        avatar = "https://image.test/$name.jpg",
        link = "https://actress.test/$name"
    )
}

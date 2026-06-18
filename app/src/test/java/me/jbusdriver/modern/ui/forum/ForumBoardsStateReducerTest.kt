package me.jbusdriver.modern.ui.forum

import me.jbusdriver.R
import me.jbusdriver.modern.core.cache.CacheEntry
import me.jbusdriver.modern.core.cache.CacheSource
import me.jbusdriver.modern.core.cache.CachedLoadEvent
import me.jbusdriver.modern.domain.model.ForumBoardGroup
import me.jbusdriver.modern.domain.model.ForumHomeData
import me.jbusdriver.modern.domain.model.ForumHomeSummary
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ForumBoardsStateReducerTest {

    @Test
    fun applyBoardsCached_updatesContentAndRevalidateState() {
        val state = ForumBoardsUiState(isLoading = true)

        val result = state.applyBoardsCached(
            cacheEntry(homeData(groups = 2), isExpired = true)
        )

        assertEquals(2, result.groups.size)
        assertFalse(result.isLoading)
        assertTrue(result.isRevalidating)
        assertEquals(1_000L, result.lastUpdatedAtMillis)
    }

    @Test
    fun applyBoardsFresh_updatesContentAndStopsLoading() {
        val state = ForumBoardsUiState(isLoading = true, isRevalidating = true)

        val result = state.applyBoardsFresh(cacheEntry(homeData(groups = 3)))

        assertEquals(3, result.groups.size)
        assertFalse(result.isLoading)
        assertFalse(result.isRevalidating)
        assertEquals(1_000L, result.lastUpdatedAtMillis)
    }

    @Test
    fun applyBoardsFailure_withoutCacheShowsInitialError() {
        val state = ForumBoardsUiState(isLoading = true)

        val result = state.applyBoardsFailure(
            event = CachedLoadEvent.Failure(IllegalStateException("offline"), hadCachedValue = false),
            hasContent = false
        )

        assertFalse(result.isLoading)
        assertFalse(result.isRevalidating)
        assertEquals(R.string.load_failed, result.error)
    }

    @Test
    fun applyBoardsRefreshFailure_withContentShowsRefreshMessage() {
        val state = ForumBoardsUiState(groups = homeData(groups = 1).boardGroups, isRefreshing = true)

        val result = state.applyBoardsRefreshFailure()

        assertFalse(result.isRefreshing)
        assertNull(result.error)
        assertEquals(R.string.refresh_failed, result.refreshMessage)
    }

    private fun cacheEntry(
        value: ForumHomeData,
        isExpired: Boolean = false
    ): CacheEntry<ForumHomeData> =
        CacheEntry(value, 1_000L, CacheSource.Network, isExpired)

    private fun homeData(groups: Int) = ForumHomeData(
        banners = emptyList(),
        summary = ForumHomeSummary(),
        boardGroups = List(groups) { ForumBoardGroup("G$it", emptyList()) }
    )
}

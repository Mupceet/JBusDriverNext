package me.jbusdriver.modern.ui.movielist

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class CollectionFilterStateTest {
    @Test
    fun collectMonth_countsAsActiveFilter() {
        val state = CollectionFilterState(collectYear = 2026, collectMonth = 6)
        assertTrue(state.hasActiveFilters)
        assertEquals(2, state.activeFilterCount)
    }

    @Test
    fun defaultState_hasNoCollectMonth() {
        val state = CollectionFilterState()
        assertFalse(state.hasActiveFilters)
        assertEquals(0, state.activeFilterCount)
    }
}

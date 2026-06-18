package me.jbusdriver.modern.ui.movielist

import me.jbusdriver.R
import me.jbusdriver.modern.core.cache.CacheEntry
import me.jbusdriver.modern.core.cache.CacheSource
import me.jbusdriver.modern.core.cache.CachedLoadEvent
import me.jbusdriver.modern.domain.model.Genre
import me.jbusdriver.modern.domain.model.GenreGroup
import me.jbusdriver.modern.ui.toUiModel
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class GenreListStateReducerTest {

    @Test
    fun genresCached_appliesCachedContentAndMarksExpiredAsRevalidating() {
        val entry = cacheEntry(
            value = listOf(group("Hot", "VR")),
            isExpired = true,
            storedAtMillis = 123L
        )

        val state = GenreListUiState(isLoading = true)
            .applyGenresCached(entry)

        assertEquals(listOf("Hot"), state.genreCategories.map { it.title })
        assertEquals("VR", state.genreCategories.single().genres?.single()?.name)
        assertFalse(state.isLoading)
        assertTrue(state.isRevalidating)
        assertEquals(123L, state.lastUpdatedAtMillis)
        assertNull(state.error)
    }

    @Test
    fun genresFresh_appliesFreshContentAndClearsLoadingFlags() {
        val entry = cacheEntry(
            value = listOf(group("Fresh", "HD")),
            storedAtMillis = 456L
        )

        val state = GenreListUiState(isLoading = true, isRevalidating = true)
            .applyGenresFresh(entry)

        assertEquals(listOf("Fresh"), state.genreCategories.map { it.title })
        assertEquals("HD", state.genreCategories.single().genres?.single()?.name)
        assertFalse(state.isLoading)
        assertFalse(state.isRevalidating)
        assertEquals(456L, state.lastUpdatedAtMillis)
    }

    @Test
    fun genresFailure_withoutContentShowsLoadError() {
        val state = GenreListUiState(isLoading = true, isRevalidating = true)
            .applyGenresFailure(
                event = CachedLoadEvent.Failure(RuntimeException("boom"), hadCachedValue = false),
                hasContent = false
            )

        assertFalse(state.isLoading)
        assertFalse(state.isRevalidating)
        assertEquals(R.string.load_failed, state.error)
    }

    @Test
    fun genresFailure_withContentKeepsCurrentDataAndClearsLoadingFlags() {
        val current = GenreListUiState(
            genreCategories = listOf(group("Current", "A").toUiModel()),
            isLoading = true,
            isRevalidating = true
        )

        val state = current.applyGenresFailure(
            event = CachedLoadEvent.Failure(RuntimeException("boom"), hadCachedValue = true),
            hasContent = true
        )

        assertEquals(current.genreCategories, state.genreCategories)
        assertFalse(state.isLoading)
        assertFalse(state.isRevalidating)
        assertNull(state.error)
    }

    @Test
    fun genresRevalidateFresh_appliesFreshContentAndClearsRevalidating() {
        val entry = cacheEntry(
            value = listOf(group("Updated", "B")),
            storedAtMillis = 789L
        )

        val state = GenreListUiState(
            genreCategories = listOf(group("Current", "A").toUiModel()),
            isRevalidating = true
        ).applyGenresRevalidateFresh(entry)

        assertEquals(listOf("Updated"), state.genreCategories.map { it.title })
        assertEquals("B", state.genreCategories.single().genres?.single()?.name)
        assertFalse(state.isRevalidating)
        assertEquals(789L, state.lastUpdatedAtMillis)
    }

    private fun cacheEntry(
        value: List<GenreGroup>,
        isExpired: Boolean = false,
        storedAtMillis: Long = 1L
    ) = CacheEntry(value, storedAtMillis, CacheSource.Memory, isExpired)

    private fun group(title: String, genreName: String) =
        GenreGroup(title, listOf(Genre(genreName, "/genre/$genreName")))
}

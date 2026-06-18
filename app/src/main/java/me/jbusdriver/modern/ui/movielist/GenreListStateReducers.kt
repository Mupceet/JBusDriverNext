package me.jbusdriver.modern.ui.movielist

import me.jbusdriver.R
import me.jbusdriver.modern.core.cache.CacheEntry
import me.jbusdriver.modern.core.cache.CachedLoadEvent
import me.jbusdriver.modern.core.cache.simulateCacheRefreshChange
import me.jbusdriver.modern.domain.model.GenreGroup
import me.jbusdriver.modern.ui.toUiModel

internal fun GenreListUiState.applyGenresCached(
    entry: CacheEntry<List<GenreGroup>>
): GenreListUiState {
    val categories = entry.value.map { it.toUiModel() }
    return copy(
        genreCategories = categories,
        isLoading = false,
        error = if (categories.isEmpty()) R.string.no_data else null,
        isRevalidating = entry.isExpired,
        lastUpdatedAtMillis = entry.storedAtMillis
    )
}

internal fun GenreListUiState.applyGenresFresh(
    entry: CacheEntry<List<GenreGroup>>
): GenreListUiState {
    val categories = entry.value.simulateCacheRefreshChange().map { it.toUiModel() }
    return copy(
        genreCategories = categories,
        isLoading = false,
        isRevalidating = false,
        lastUpdatedAtMillis = entry.storedAtMillis
    )
}

internal fun GenreListUiState.applyGenresFailure(
    event: CachedLoadEvent.Failure,
    hasContent: Boolean
): GenreListUiState =
    if (event.hadCachedValue || hasContent) {
        copy(isLoading = false, isRevalidating = false)
    } else {
        copy(
            isLoading = false,
            isRevalidating = false,
            error = R.string.load_failed
        )
    }

internal fun GenreListUiState.applyGenresRevalidateFresh(
    entry: CacheEntry<List<GenreGroup>>
): GenreListUiState {
    val categories = entry.value.simulateCacheRefreshChange().map { it.toUiModel() }
    return copy(
        genreCategories = categories,
        isRevalidating = false,
        lastUpdatedAtMillis = entry.storedAtMillis
    )
}

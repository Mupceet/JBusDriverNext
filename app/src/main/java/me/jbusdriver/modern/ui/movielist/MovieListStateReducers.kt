package me.jbusdriver.modern.ui.movielist

import me.jbusdriver.R
import me.jbusdriver.modern.core.cache.CacheEntry
import me.jbusdriver.modern.core.cache.CachedLoadEvent
import me.jbusdriver.modern.core.cache.FreshRevalidateOutcome
import me.jbusdriver.modern.core.cache.mergeFirstPage
import me.jbusdriver.modern.core.cache.simulateCacheRefreshChange
import me.jbusdriver.modern.domain.model.MoviePageResult
import me.jbusdriver.modern.domain.model.hasNext
import me.jbusdriver.modern.ui.toUiModel

internal data class MovieListRevalidateReduction(
    val state: MovieListUiState,
    val outcome: FreshRevalidateOutcome,
    val fresh: MoviePageResult
)

internal fun MovieListUiState.applyFirstPageCached(
    entry: CacheEntry<MoviePageResult>
): MovieListUiState =
    copy(
        movies = entry.value.movies.map { m -> m.toUiModel() },
        pageInfo = entry.value.pageInfo,
        filterInfo = entry.value.filterInfo,
        isLoading = false,
        isFilterSwitching = false,
        hasMore = entry.value.pageInfo.hasNext,
        error = if (entry.value.movies.isEmpty()) R.string.no_data else null,
        isRevalidating = entry.isExpired,
        lastUpdatedAtMillis = entry.storedAtMillis
    )

internal fun MovieListUiState.applyFirstPageFresh(
    entry: CacheEntry<MoviePageResult>
): MovieListUiState =
    copy(
        movies = entry.value.movies.simulateCacheRefreshChange().map { m -> m.toUiModel() },
        pageInfo = entry.value.pageInfo,
        filterInfo = entry.value.filterInfo,
        isLoading = false,
        isFilterSwitching = false,
        isRevalidating = false,
        hasMore = entry.value.pageInfo.hasNext,
        pendingFreshResult = null,
        refreshMessage = null,
        lastUpdatedAtMillis = entry.storedAtMillis,
        error = if (entry.value.movies.isEmpty()) R.string.no_data else null
    )

internal fun MovieListUiState.applyFirstPageFailure(
    event: CachedLoadEvent.Failure,
    hasContent: Boolean
): MovieListUiState =
    if (event.hadCachedValue || hasContent) {
        copy(
            isLoading = false,
            isFilterSwitching = false,
            isRevalidating = false
        )
    } else {
        copy(
            isLoading = false,
            isFilterSwitching = false,
            isRevalidating = false,
            error = R.string.load_failed
        )
    }

internal fun MovieListUiState.applyFreshRevalidate(
    entry: CacheEntry<MoviePageResult>,
    isAtTop: Boolean
): MovieListRevalidateReduction {
    val fresh = entry.value.copy(
        movies = entry.value.movies.simulateCacheRefreshChange()
    )
    val freshUiModels = fresh.movies.map { it.toUiModel() }
    val outcome = decideMovieFirstPageRefresh(
        currentFirstPage = movies.take(freshUiModels.size),
        freshFirstPage = freshUiModels,
        isAtTop = isAtTop
    )
    val nextState = when (outcome) {
        FreshRevalidateOutcome.ApplyImmediately -> copy(
            movies = freshUiModels,
            pageInfo = fresh.pageInfo,
            hasMore = fresh.pageInfo.hasNext,
            filterInfo = fresh.filterInfo,
            isRevalidating = false,
            pendingFreshResult = null,
            refreshMessage = null,
            lastUpdatedAtMillis = entry.storedAtMillis
        )

        FreshRevalidateOutcome.ApplyInPlace -> copy(
            movies = movies.mergeFirstPage(freshUiModels),
            isRevalidating = false,
            lastUpdatedAtMillis = entry.storedAtMillis
        )

        FreshRevalidateOutcome.StorePending -> copy(
            isRevalidating = false,
            pendingFreshResult = fresh,
            refreshMessage = R.string.new_data_available
        )

        FreshRevalidateOutcome.NoChange -> copy(isRevalidating = false)
    }

    return MovieListRevalidateReduction(
        state = nextState,
        outcome = outcome,
        fresh = fresh
    )
}

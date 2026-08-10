package me.jbusdriver.modern.ui.movielist

import me.jbusdriver.R
import me.jbusdriver.modern.core.cache.CacheEntry
import me.jbusdriver.modern.core.cache.CachedLoadEvent
import me.jbusdriver.modern.core.cache.FreshRevalidateOutcome
import me.jbusdriver.modern.core.cache.simulateCacheRefreshChange
import me.jbusdriver.modern.domain.model.MovieFilterInfo
import me.jbusdriver.modern.domain.model.MoviePageResult
import me.jbusdriver.modern.domain.model.hasNext
import me.jbusdriver.modern.ui.toUiModel

internal data class LinkMovieListRevalidateReduction(
    val state: LinkMovieListUiState,
    val outcome: FreshRevalidateOutcome,
    val fresh: MoviePageResult
)

internal fun LinkMovieListUiState.applyFirstPageCached(
    entry: CacheEntry<MoviePageResult>
): LinkMovieListUiState {
    val result = entry.value
    return copy(
        movies = result.movies.map { m -> m.toUiModel() },
        pageInfo = result.pageInfo,
        isLoading = false,
        isFilterSwitching = false,
        hasMore = result.pageInfo.hasNext,
        error = if (result.movies.isEmpty()) R.string.no_data else null,
        filterInfo = result.filterInfo,
        isRevalidating = entry.isExpired,
        lastUpdatedAtMillis = entry.storedAtMillis,
        resolvedTitle = result.filterInfo?.toResolvedTitle() ?: resolvedTitle
    )
}

internal fun LinkMovieListUiState.applyFirstPageFresh(
    entry: CacheEntry<MoviePageResult>
): LinkMovieListUiState {
    val result = entry.value
    return copy(
        movies = result.movies.simulateCacheRefreshChange().map { m -> m.toUiModel() },
        pageInfo = result.pageInfo,
        isLoading = false,
        isFilterSwitching = false,
        isRevalidating = false,
        hasMore = result.pageInfo.hasNext,
        filterInfo = result.filterInfo,
        pendingFreshResult = null,
        refreshMessage = null,
        lastUpdatedAtMillis = entry.storedAtMillis,
        resolvedTitle = result.filterInfo?.toResolvedTitle() ?: resolvedTitle
    )
}

internal fun LinkMovieListUiState.applyFirstPageFailure(
    event: CachedLoadEvent.Failure,
    hasContent: Boolean
): LinkMovieListUiState =
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

internal fun LinkMovieListUiState.applyFreshRevalidate(
    entry: CacheEntry<MoviePageResult>,
    isAtTop: Boolean
): LinkMovieListRevalidateReduction {
    val fresh = entry.value.copy(
        movies = entry.value.movies.simulateCacheRefreshChange()
    )
    val freshUiModels = fresh.movies.map { it.toUiModel() }
    val currentFirstPage = movies.take(freshUiModels.size)
    val outcome = when {
        isAtTop -> FreshRevalidateOutcome.ApplyImmediately
        currentFirstPage == freshUiModels -> FreshRevalidateOutcome.NoChange
        // 首页影片同序且仅 tag 字段变化：静默就地刷新，不弹"有新数据"提示。
        currentFirstPage.onlyTagsChanged(freshUiModels) -> FreshRevalidateOutcome.NoChange
        else -> FreshRevalidateOutcome.StorePending
    }
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

        FreshRevalidateOutcome.StorePending -> copy(
            isRevalidating = false,
            pendingFreshResult = fresh,
            refreshMessage = R.string.new_data_available
        )

        FreshRevalidateOutcome.NoChange -> {
            if (currentFirstPage == freshUiModels) {
                copy(isRevalidating = false)
            } else {
                copy(
                    movies = movies.mergeFreshFirstPage(freshUiModels),
                    isRevalidating = false,
                    lastUpdatedAtMillis = entry.storedAtMillis
                )
            }
        }
    }

    return LinkMovieListRevalidateReduction(
        state = nextState,
        outcome = outcome,
        fresh = fresh
    )
}

private fun MovieFilterInfo.toResolvedTitle(): ResolvedTitle? {
    val name = breadcrumbName ?: return null
    if (breadcrumbType == null) return null
    return if (breadcrumbType == "女優") {
        ResolvedTitle.Actress(name)
    } else {
        ResolvedTitle.Genre(name)
    }
}

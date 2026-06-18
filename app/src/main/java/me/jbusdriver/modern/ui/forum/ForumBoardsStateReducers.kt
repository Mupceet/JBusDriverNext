package me.jbusdriver.modern.ui.forum

import me.jbusdriver.R
import me.jbusdriver.modern.core.cache.CacheEntry
import me.jbusdriver.modern.core.cache.CachedLoadEvent
import me.jbusdriver.modern.domain.model.ForumHomeData

internal fun ForumBoardsUiState.applyBoardsCached(
    entry: CacheEntry<ForumHomeData>
): ForumBoardsUiState =
    copy(
        banners = entry.value.banners,
        summary = entry.value.summary,
        groups = entry.value.boardGroups,
        isLoading = false,
        isRevalidating = entry.isExpired,
        lastUpdatedAtMillis = entry.storedAtMillis
    )

internal fun ForumBoardsUiState.applyBoardsFresh(
    entry: CacheEntry<ForumHomeData>
): ForumBoardsUiState =
    copy(
        banners = entry.value.banners,
        summary = entry.value.summary,
        groups = entry.value.boardGroups,
        isLoading = false,
        isRevalidating = false,
        lastUpdatedAtMillis = entry.storedAtMillis
    )

internal fun ForumBoardsUiState.applyBoardsFailure(
    event: CachedLoadEvent.Failure,
    hasContent: Boolean
): ForumBoardsUiState =
    if (event.hadCachedValue || hasContent) {
        copy(isLoading = false, isRevalidating = false)
    } else {
        copy(
            isLoading = false,
            isRevalidating = false,
            error = R.string.load_failed
        )
    }

internal fun ForumBoardsUiState.applyBoardsRefreshFresh(
    entry: CacheEntry<ForumHomeData>
): ForumBoardsUiState =
    copy(
        banners = entry.value.banners,
        summary = entry.value.summary,
        groups = entry.value.boardGroups,
        isRefreshing = false,
        lastUpdatedAtMillis = entry.storedAtMillis
    )

internal fun ForumBoardsUiState.applyBoardsRefreshFailure(): ForumBoardsUiState =
    copy(
        isRefreshing = false,
        error = if (groups.isEmpty()) R.string.load_failed else error,
        refreshMessage = if (groups.isNotEmpty()) R.string.refresh_failed else null
    )

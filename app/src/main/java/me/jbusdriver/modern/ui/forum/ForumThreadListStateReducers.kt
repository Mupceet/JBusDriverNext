package me.jbusdriver.modern.ui.forum

import me.jbusdriver.R
import me.jbusdriver.modern.core.cache.CacheEntry
import me.jbusdriver.modern.core.cache.CachedLoadEvent
import me.jbusdriver.modern.core.cache.FreshRevalidateOutcome
import me.jbusdriver.modern.core.cache.decideFreshRevalidate
import me.jbusdriver.modern.domain.model.ForumThreadPageResult
import me.jbusdriver.modern.domain.model.hasNext

internal data class ForumThreadListFreshReduction(
    val state: ForumThreadListUiState,
    val outcome: FreshRevalidateOutcome
)

internal fun ForumThreadListUiState.applyFirstPageCached(
    entry: CacheEntry<ForumThreadPageResult>
): ForumThreadListUiState =
    copy(
        threads = entry.value.threads,
        pageInfo = entry.value.pageInfo,
        typeFilters = entry.value.typeFilters.ifEmpty { typeFilters },
        isLoading = false,
        isRevalidating = entry.isExpired,
        lastUpdatedAtMillis = entry.storedAtMillis
    )

internal fun ForumThreadListUiState.applyFirstPageFresh(
    entry: CacheEntry<ForumThreadPageResult>,
    isAtTop: Boolean
): ForumThreadListFreshReduction =
    applyFreshEntry(
        entry = entry,
        isAtTop = isAtTop,
        loadingCopy = { state -> state.copy(isLoading = false, isRevalidating = false) }
    )

internal fun ForumThreadListUiState.applyFreshRevalidate(
    entry: CacheEntry<ForumThreadPageResult>,
    isAtTop: Boolean
): ForumThreadListFreshReduction =
    applyFreshEntry(
        entry = entry,
        isAtTop = isAtTop,
        loadingCopy = { state -> state.copy(isRevalidating = false) }
    )

internal fun ForumThreadListUiState.applyFirstPageFailure(
    event: CachedLoadEvent.Failure,
    hasContent: Boolean
): ForumThreadListUiState =
    if (event.hadCachedValue || hasContent) {
        copy(isLoading = false, isRevalidating = false)
    } else {
        copy(
            isLoading = false,
            isRevalidating = false,
            error = R.string.load_failed
        )
    }

private inline fun ForumThreadListUiState.applyFreshEntry(
    entry: CacheEntry<ForumThreadPageResult>,
    isAtTop: Boolean,
    loadingCopy: (ForumThreadListUiState) -> ForumThreadListUiState
): ForumThreadListFreshReduction {
    val fresh = entry.value
    return when (
        val outcome = decideFreshRevalidate(
            currentItems = threads,
            freshItems = fresh.threads,
            isAtTop = isAtTop
        )
    ) {
        FreshRevalidateOutcome.ApplyImmediately -> ForumThreadListFreshReduction(
            state = loadingCopy(
                copy(
                    threads = fresh.threads,
                    pageInfo = fresh.pageInfo,
                    typeFilters = fresh.typeFilters,
                    hasMore = fresh.pageInfo.hasNext,
                    pendingFreshThreads = null,
                    refreshMessage = null,
                    lastUpdatedAtMillis = entry.storedAtMillis
                )
            ),
            outcome = outcome
        )

        FreshRevalidateOutcome.StorePending -> ForumThreadListFreshReduction(
            state = loadingCopy(
                copy(
                    pendingFreshThreads = fresh,
                    refreshMessage = R.string.new_data_available
                )
            ),
            outcome = outcome
        )

        FreshRevalidateOutcome.NoChange -> ForumThreadListFreshReduction(
            state = loadingCopy(this),
            outcome = outcome
        )
    }
}

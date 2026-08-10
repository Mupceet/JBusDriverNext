package me.jbusdriver.modern.ui.movielist

import me.jbusdriver.R
import me.jbusdriver.modern.core.cache.CacheEntry
import me.jbusdriver.modern.core.cache.CachedLoadEvent
import me.jbusdriver.modern.core.cache.FreshRevalidateOutcome
import me.jbusdriver.modern.core.cache.decideFreshRevalidate
import me.jbusdriver.modern.core.cache.simulateCacheRefreshChange
import me.jbusdriver.modern.domain.model.ActressInfo
import me.jbusdriver.modern.domain.model.PageInfo
import me.jbusdriver.modern.domain.model.hasNext
import me.jbusdriver.modern.ui.toActressUiModel

internal data class ActressListRevalidateReduction(
    val state: ActressListUiState,
    val outcome: FreshRevalidateOutcome,
    val fresh: Pair<List<ActressInfo>, PageInfo>
)

internal fun ActressListUiState.applyFirstPageCached(
    entry: CacheEntry<Pair<List<ActressInfo>, PageInfo>>
): ActressListUiState =
    copy(
        actresses = entry.value.first.map { a -> a.toActressUiModel() },
        pageInfo = entry.value.second,
        isLoading = false,
        isRefreshing = false,
        error = if (entry.value.first.isEmpty()) R.string.no_data else null,
        isRevalidating = entry.isExpired,
        lastUpdatedAtMillis = entry.storedAtMillis
    )

internal fun ActressListUiState.applyFirstPageFresh(
    entry: CacheEntry<Pair<List<ActressInfo>, PageInfo>>
): ActressListUiState =
    copy(
        actresses = entry.value.first.simulateCacheRefreshChange()
            .map { a -> a.toActressUiModel() },
        pageInfo = entry.value.second,
        isLoading = false,
        isRefreshing = false,
        isRevalidating = false,
        lastUpdatedAtMillis = entry.storedAtMillis
    )

internal fun ActressListUiState.applyFirstPageFailure(
    event: CachedLoadEvent.Failure,
    hasContent: Boolean
): ActressListUiState =
    if (event.hadCachedValue || hasContent) {
        copy(
            isLoading = false,
            isRefreshing = false,
            isRevalidating = false
        )
    } else {
        copy(
            isLoading = false,
            isRefreshing = false,
            isRevalidating = false,
            error = R.string.load_failed
        )
    }

internal fun ActressListUiState.applyFreshRevalidate(
    entry: CacheEntry<Pair<List<ActressInfo>, PageInfo>>,
    isAtTop: Boolean
): ActressListRevalidateReduction {
    val fresh = entry.value.copy(
        first = entry.value.first.simulateCacheRefreshChange()
    )
    val freshUiModels = fresh.first.map { it.toActressUiModel() }
    val outcome = decideFreshRevalidate(actresses, freshUiModels, isAtTop)
    val nextState = when (outcome) {
        FreshRevalidateOutcome.ApplyImmediately -> copy(
            actresses = freshUiModels,
            pageInfo = fresh.second,
            isRevalidating = false,
            pendingFreshActresses = null,
            lastUpdatedAtMillis = entry.storedAtMillis
        )

        FreshRevalidateOutcome.StorePending -> copy(
            isRevalidating = false,
            pendingFreshActresses = fresh
        )

        // 女优列表暂无静默就地合并策略；决策函数不会产出该值，保留分支保证 when 穷尽。
        FreshRevalidateOutcome.ApplyInPlace -> copy(isRevalidating = false)

        FreshRevalidateOutcome.NoChange -> copy(isRevalidating = false)
    }

    return ActressListRevalidateReduction(
        state = nextState,
        outcome = outcome,
        fresh = fresh
    )
}

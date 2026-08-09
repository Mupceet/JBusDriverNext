package me.jbusdriver.modern.ui.forum

import me.jbusdriver.R
import me.jbusdriver.modern.core.cache.CacheEntry
import me.jbusdriver.modern.core.cache.CachedLoadEvent
import me.jbusdriver.modern.core.cache.FreshRevalidateOutcome
import me.jbusdriver.modern.domain.model.ForumThread
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
    val outcome = when {
        isAtTop -> FreshRevalidateOutcome.ApplyImmediately
        // 主题数量与排序未变（仅浏览数、回复数等卡片内字段变化）：
        // 静默就地刷新，不弹"有新数据"提示，也不打断已滚动的列表。
        threads.take(fresh.threads.size).map { it.tid } == fresh.threads.map { it.tid } ->
            FreshRevalidateOutcome.NoChange
        else -> FreshRevalidateOutcome.StorePending
    }
    return when (outcome) {
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
            state = loadingCopy(
                copy(
                    threads = threads.mergeFreshFirstPage(fresh.threads),
                    lastUpdatedAtMillis = entry.storedAtMillis
                )
            ),
            outcome = outcome
        )
    }
}

/**
 * 把新首页主题按 tid 就地合并进当前列表：已加载的后续页保持不变，
 * 避免用户在列表中部时静默刷新导致已加载内容丢失。
 */
private fun List<ForumThread>.mergeFreshFirstPage(freshFirstPage: List<ForumThread>): List<ForumThread> {
    if (freshFirstPage.isEmpty()) return this
    val freshByTid = freshFirstPage.associateBy { it.tid }
    return map { freshByTid[it.tid] ?: it }
}

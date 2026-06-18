package me.jbusdriver.modern.ui.forum

import me.jbusdriver.R
import me.jbusdriver.modern.core.cache.CacheEntry
import me.jbusdriver.modern.core.cache.CachedLoadEvent
import me.jbusdriver.modern.core.cache.FreshRevalidateOutcome
import me.jbusdriver.modern.domain.model.ForumThreadDetail

internal data class ForumThreadDetailFreshReduction(
    val state: ForumThreadDetailUiState,
    val outcome: FreshRevalidateOutcome
)

internal fun ForumThreadDetailUiState.applyLoadDetailCached(
    entry: CacheEntry<ForumThreadDetail>
): ForumThreadDetailUiState =
    copy(
        detail = entry.value,
        isLoading = false,
        isRevalidating = entry.isExpired,
        isChangingFloorOrder = false,
        lastUpdatedAtMillis = entry.storedAtMillis
    )

internal fun ForumThreadDetailUiState.applyLoadDetailFresh(
    entry: CacheEntry<ForumThreadDetail>,
    isAtTop: Boolean,
    forceApply: Boolean
): ForumThreadDetailFreshReduction =
    applyFreshDetail(
        entry = entry,
        shouldApplyImmediately = isAtTop || forceApply,
        loadingCopy = { state ->
            state.copy(
                isLoading = false,
                isRevalidating = false,
                isChangingFloorOrder = false
            )
        }
    )

internal fun ForumThreadDetailUiState.applyDetailRevalidateFresh(
    entry: CacheEntry<ForumThreadDetail>,
    isAtTop: Boolean
): ForumThreadDetailFreshReduction =
    applyFreshDetail(
        entry = entry,
        shouldApplyImmediately = isAtTop,
        loadingCopy = { state -> state.copy(isRevalidating = false) }
    )

internal fun ForumThreadDetailUiState.applyLoadDetailFailure(
    event: CachedLoadEvent.Failure,
    hasContent: Boolean
): ForumThreadDetailUiState =
    if (event.hadCachedValue || hasContent) {
        copy(
            isLoading = false,
            isRevalidating = false,
            isChangingFloorOrder = false
        )
    } else {
        copy(
            isLoading = false,
            isRevalidating = false,
            isChangingFloorOrder = false,
            error = R.string.load_failed
        )
    }

private inline fun ForumThreadDetailUiState.applyFreshDetail(
    entry: CacheEntry<ForumThreadDetail>,
    shouldApplyImmediately: Boolean,
    loadingCopy: (ForumThreadDetailUiState) -> ForumThreadDetailUiState
): ForumThreadDetailFreshReduction {
    val fresh = entry.value
    val outcome = when {
        shouldApplyImmediately -> FreshRevalidateOutcome.ApplyImmediately
        hasDetailChanged(fresh) -> FreshRevalidateOutcome.StorePending
        else -> FreshRevalidateOutcome.NoChange
    }
    return when (outcome) {
        FreshRevalidateOutcome.ApplyImmediately -> ForumThreadDetailFreshReduction(
            state = loadingCopy(
                copy(
                    detail = fresh,
                    pendingFreshDetail = null,
                    refreshMessage = null,
                    lastUpdatedAtMillis = entry.storedAtMillis
                )
            ),
            outcome = outcome
        )

        FreshRevalidateOutcome.StorePending -> ForumThreadDetailFreshReduction(
            state = loadingCopy(
                copy(
                    pendingFreshDetail = fresh,
                    refreshMessage = R.string.new_data_available
                )
            ),
            outcome = outcome
        )

        FreshRevalidateOutcome.NoChange -> ForumThreadDetailFreshReduction(
            state = loadingCopy(this),
            outcome = outcome
        )
    }
}

private fun ForumThreadDetailUiState.hasDetailChanged(fresh: ForumThreadDetail): Boolean {
    val oldDetail = detail ?: return true
    val headerChanged = oldDetail.title != fresh.title ||
            oldDetail.viewCount != fresh.viewCount ||
            oldDetail.replyCount != fresh.replyCount ||
            oldDetail.typeName != fresh.typeName ||
            oldDetail.contentBlocks != fresh.contentBlocks
    val oldFirstPageReplies = oldDetail.replies.take(fresh.replies.size)
    return headerChanged || oldFirstPageReplies != fresh.replies
}

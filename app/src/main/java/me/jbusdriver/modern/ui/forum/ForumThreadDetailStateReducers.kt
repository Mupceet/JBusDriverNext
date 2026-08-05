package me.jbusdriver.modern.ui.forum

import me.jbusdriver.R
import me.jbusdriver.modern.core.cache.CacheEntry
import me.jbusdriver.modern.core.cache.CachedLoadEvent
import me.jbusdriver.modern.core.cache.FreshRevalidateOutcome
import me.jbusdriver.modern.domain.model.ForumReply
import me.jbusdriver.modern.domain.model.ForumThreadDetail

internal data class ForumThreadDetailFreshReduction(
    val state: ForumThreadDetailUiState,
    val outcome: FreshRevalidateOutcome,
    /** 触发"有新数据"的内容变化原因，仅用于日志确认；无变化时为 null。 */
    val changeReason: String? = null
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
    val changeReason = detailChangeReason(fresh)
    val outcome = when {
        shouldApplyImmediately -> FreshRevalidateOutcome.ApplyImmediately
        changeReason != null -> FreshRevalidateOutcome.StorePending
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
            outcome = outcome,
            changeReason = changeReason
        )

        FreshRevalidateOutcome.StorePending -> ForumThreadDetailFreshReduction(
            state = loadingCopy(
                copy(
                    pendingFreshDetail = fresh,
                    refreshMessage = R.string.new_data_available
                )
            ),
            outcome = outcome,
            changeReason = changeReason
        )

        FreshRevalidateOutcome.NoChange -> ForumThreadDetailFreshReduction(
            // 首屏内容未变（仅浏览数/回复数等高频计数器变动）：静默刷新计数器与时间戳，
            // 既不弹"有新数据"打扰用户，也不替换已加载的多页楼层（避免丢页/回到顶部）。
            state = loadingCopy(
                copy(
                    detail = (detail ?: fresh).copy(
                        viewCount = fresh.viewCount,
                        replyCount = fresh.replyCount
                    ),
                    lastUpdatedAtMillis = entry.storedAtMillis
                )
            ),
            outcome = outcome,
            changeReason = changeReason
        )
    }
}

/**
 * 判定帖子详情首屏可见内容是否变化；变化时返回原因（用于日志），无变化返回 null。
 *
 * 不参与判定的噪声字段：
 * - 浏览数（[ForumThreadDetail.viewCount]）/回复数（[ForumThreadDetail.replyCount]）：高频计数器；
 * - 点评时间字符串（[me.jbusdriver.modern.domain.model.Comment.time]，如"半小时前"）：
 *   为相对时间、会持续刷新而非固定时间戳，故比对前先归一化。
 *
 * 由此倒序下新增回复会进入首屏楼层 → 视为有新数据（提示刷新）；
 * 正序下新增回复落在更后页、首屏楼层不变 → 不视为有新数据（仅 NoChange 分支静默刷新计数器）。
 */
private fun ForumThreadDetailUiState.detailChangeReason(fresh: ForumThreadDetail): String? {
    val old = detail ?: return "首次加载"
    if (old.title != fresh.title) return "标题"
    if (old.typeName != fresh.typeName) return "类型"
    if (old.contentBlocks != fresh.contentBlocks) return "正文"

    val oldReplies = old.replies.take(fresh.replies.size).withStableCommentTimes()
    val freshReplies = fresh.replies.withStableCommentTimes()
    if (oldReplies == freshReplies) return null
    if (oldReplies.size != freshReplies.size) return "楼层数 ${oldReplies.size}→${freshReplies.size}"
    val index = oldReplies.zip(freshReplies).indexOfFirst { (a, b) -> a != b }
    if (index < 0) return null
    val changedFloor = freshReplies[index].floor
    val onlyCommentsChanged =
        oldReplies[index].copy(comments = emptyList()) == freshReplies[index].copy(comments = emptyList())
    return "楼层${changedFloor}#${if (onlyCommentsChanged) "点评" else "内容/属性"}"
}

/** 忽略点评相对时间（[me.jbusdriver.modern.domain.model.Comment.time]）后的楼层列表，用于"是否有新数据"比对。 */
private fun List<ForumReply>.withStableCommentTimes(): List<ForumReply> =
    map { it.copy(comments = it.comments.map { c -> c.copy(time = "") }) }

package me.jbusdriver.modern.ui.forum

import me.jbusdriver.R
import me.jbusdriver.modern.core.cache.CacheEntry
import me.jbusdriver.modern.core.cache.CachedLoadEvent
import me.jbusdriver.modern.core.cache.FreshRevalidateOutcome
import me.jbusdriver.modern.data.settings.ForumFloorOrder
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
        isChangingAuthorFilter = false,
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
                isChangingFloorOrder = false,
                isChangingAuthorFilter = false
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
            isChangingFloorOrder = false,
            isChangingAuthorFilter = false
        )
    } else {
        copy(
            isLoading = false,
            isRevalidating = false,
            isChangingFloorOrder = false,
            isChangingAuthorFilter = false,
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
        else -> FreshRevalidateOutcome.ApplyInPlace
    }
    return when (outcome) {
        FreshRevalidateOutcome.ApplyImmediately -> ForumThreadDetailFreshReduction(
            state = loadingCopy(
                copy(
                    detail = fresh,
                    pendingFreshDetail = null,
                    lastUpdatedAtMillis = entry.storedAtMillis
                )
            ),
            outcome = outcome,
            changeReason = changeReason
        )

        FreshRevalidateOutcome.StorePending -> ForumThreadDetailFreshReduction(
            state = loadingCopy(
                copy(
                    pendingFreshDetail = fresh
                )
            ),
            outcome = outcome,
            changeReason = changeReason
        )

        FreshRevalidateOutcome.ApplyInPlace -> ForumThreadDetailFreshReduction(
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

        // 当前策略不会产出该值（无内容变化时也会静默刷新计数器）；保留分支保证 when 穷尽。
        FreshRevalidateOutcome.NoChange -> ForumThreadDetailFreshReduction(
            state = loadingCopy(this),
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
 * 正序下新增回复落在更后页、首屏楼层不变 → 不视为有新数据（仅 ApplyInPlace 分支静默刷新计数器）。
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

/**
 * 楼层断层：跳转合并/翻页后列表中相邻楼层号断档的区间。
 * [aboveFloor]/[belowFloor] 为断层两侧已加载楼层号（above > below，与显示方向无关）。
 */
data class FloorGap(val aboveFloor: Int, val belowFloor: Int)

/**
 * 从已按 [order] 排序的楼层列表派生断层（缺失 ≥2 层的相邻断档）。
 * 单个缺失楼层（above - below == 2，楼层被删）与无更多页时的断档一样不视为断层，
 * 避免为不可恢复的已删楼层显示补齐提示。
 */
fun floorGaps(replies: List<ForumReply>, order: ForumFloorOrder): List<FloorGap> {
    if (replies.size < 2) return emptyList()
    return buildList {
        for (index in 1 until replies.size) {
            val prev = replies[index - 1].floor
            val cur = replies[index].floor
            val (above, below) = if (order == ForumFloorOrder.REVERSE) prev to cur else cur to prev
            if (above - below > 2) add(FloorGap(aboveFloor = above, belowFloor = below))
        }
    }
}

/**
 * 合并已加载楼层与新增页楼层：按 floor 去重（保留已展示的首现版本，置顶/广播楼层跨页重复），
 * 并按 [order] 全局排序——跳转合并/断层补齐后列表仍须整体有序，断层派生与展示位置才成立。
 */
fun mergeReplyPages(
    existing: List<ForumReply>,
    incoming: List<ForumReply>,
    order: ForumFloorOrder
): List<ForumReply> {
    val existingFloors = existing.mapTo(HashSet()) { it.floor }
    return (existing + incoming.filter { it.floor !in existingFloors })
        .sortedBy { if (order == ForumFloorOrder.REVERSE) -it.floor else it.floor }
}

/** 回复区组合列表项：楼层或楼层断层分隔行（[key] 供 LazyColumn 复用）。 */
internal sealed interface ReplyListItem {
    val key: String

    data class Reply(val reply: ForumReply) : ReplyListItem {
        override val key: String get() = "reply_${reply.floor}"
    }

    data class Gap(val gap: FloorGap) : ReplyListItem {
        override val key: String get() = "gap_${gap.belowFloor}"
    }
}

/**
 * 楼层 + 断层分隔行组合列表（须传入已按 [order] 排序的楼层）。
 * 分隔行插在断层上沿楼层的下方；仅 [showGapDividers]（仍有更多页）时插入，
 * 无更多页的断档与单个缺失楼层（已删楼层，[floorGaps] 已过滤）不渲染分隔行。
 */
internal fun buildReplyListItems(
    replies: List<ForumReply>,
    order: ForumFloorOrder,
    showGapDividers: Boolean
): List<ReplyListItem> {
    if (replies.isEmpty()) return emptyList()
    val gapAfterFloor: Map<Int, FloorGap> = if (!showGapDividers) {
        emptyMap()
    } else {
        // 倒序（楼层号降序）分隔行跟在上侧（较大楼层号）楼层后；正序反之。
        val anchorSelector: (FloorGap) -> Int = if (order == ForumFloorOrder.REVERSE) {
            { it.aboveFloor }
        } else {
            { it.belowFloor }
        }
        floorGaps(replies, order).associateBy(anchorSelector)
    }
    return buildList {
        for (reply in replies) {
            add(ReplyListItem.Reply(reply))
            gapAfterFloor[reply.floor]?.let { add(ReplyListItem.Gap(it)) }
        }
    }
}

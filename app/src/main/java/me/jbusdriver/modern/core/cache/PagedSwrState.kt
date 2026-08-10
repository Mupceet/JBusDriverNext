package me.jbusdriver.modern.core.cache

import me.jbusdriver.modern.domain.model.PageInfo

/**
 * 分页加载的页码跟踪器，封装首页/翻页/回滚的页码状态。
 *
 * 替代各列表 ViewModel 中重复的 `private var currentPage` 字段及
 * `nextPage <= currentPage` 翻页守卫——集中这一处易错的页码判断。
 */
class PageTracker {
    /** 当前已加载到的页码，0 表示尚未加载 */
    var current: Int = 0
        private set

    /** 标记开始加载首页（页码置 1） */
    fun startFirstPage() {
        current = 1
    }

    /** 重置为未加载状态（切换数据源时使用） */
    fun reset() {
        current = 0
    }

    /** 翻页：前进到下一页 */
    fun advanceTo(nextPage: Int) {
        current = nextPage
    }

    /** 加载更多失败时回退到当前实际页码 */
    fun rollbackTo(activePage: Int) {
        current = activePage
    }

    /**
     * 是否还有更多页可加载：存在下一页且尚未加载到。
     *
     * 等价于原各处的 `pageInfo.nextPage > currentPage` 守卫。
     */
    fun shouldLoadMore(pageInfo: PageInfo): Boolean = pageInfo.nextPage > current
}

/**
 * “列表是否处于顶部”的开关，决定后台刷新拿到新首页数据后是直接应用还是暂存待提示。
 *
 * 替代各列表 ViewModel 中重复的 `isAtTopForFreshUpdates` 字段。
 */
class AtTopGate(var isAtTop: Boolean = true)

/**
 * 后台 revalidate 拿到新首页数据后的处理结果。
 */
enum class FreshRevalidateOutcome {
    /** 用户在列表顶部：整体替换为新数据（并重置分页） */
    ApplyImmediately,

    /** 内容无实质变化但可静默应用新数据（如仅 tag/计数变化）：就地合并，不提示 */
    ApplyInPlace,

    /** 用户已下滑且数据有变化：暂存为 pending 并提示“有新数据” */
    StorePending,

    /** 数据完全一致：仅结束 revalidate 状态，不修改列表 */
    NoChange
}

/**
 * 决定后台 revalidate 拿到新首页数据后的处理方式。
 *
 * 这是无“静默就地合并”策略的简单列表（如 ActressList）的默认三段判断：
 * 在顶部则直接应用；否则按新数据长度截取旧首页做比较，有差异则暂存 pending，
 * 无差异则不处理。带静默合并策略的屏幕（影片列表、论坛列表/详情）应各自提供
 * 决策函数并产出 [FreshRevalidateOutcome.ApplyInPlace]。
 *
 * @param currentItems 当前展示的列表（UI 模型）
 * @param freshItems 后台刷新得到的新首页列表（已做 simulateCacheRefreshChange）
 * @param isAtTop 用户当前是否在列表顶部
 */
fun <I> decideFreshRevalidate(
    currentItems: List<I>,
    freshItems: List<I>,
    isAtTop: Boolean
): FreshRevalidateOutcome = when {
    isAtTop -> FreshRevalidateOutcome.ApplyImmediately
    currentItems.take(freshItems.size) != freshItems -> FreshRevalidateOutcome.StorePending
    else -> FreshRevalidateOutcome.NoChange
}

/**
 * 将新首页按顺序就地合并进当前列表：已加载的后续页保持不变，
 * 避免用户在列表中部时静默刷新导致已加载内容丢失。
 * 要求新旧首页同序，否则合并结果没有意义。
 */
fun <T> List<T>.mergeFirstPage(freshFirstPage: List<T>): List<T> =
    if (freshFirstPage.isEmpty()) this else freshFirstPage + drop(freshFirstPage.size)

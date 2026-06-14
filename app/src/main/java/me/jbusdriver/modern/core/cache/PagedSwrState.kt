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
    /** 用户在列表顶部：直接替换为新数据 */
    ApplyImmediately,

    /** 用户已下滑且数据有变化：暂存为 pending 并提示“有新数据” */
    StorePending,

    /** 数据无变化：仅结束 revalidate 状态 */
    NoChange
}

/**
 * 决定后台 revalidate 拿到新首页数据后的处理方式。
 *
 * 这是各分页 ViewModel 的 `revalidate()` Fresh 分支中逐字重复的三段判断：
 * 在顶部则直接应用；否则按新数据长度截取旧首页做比较，有差异则暂存 pending，
 * 无差异则不处理。
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

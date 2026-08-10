package me.jbusdriver.modern.ui.movielist

import me.jbusdriver.modern.core.cache.FreshRevalidateOutcome
import me.jbusdriver.modern.ui.MovieUiModel

/**
 * 影片列表后台刷新拿到新首页后的统一决策：
 * - 在顶部：整体替换；
 * - 与当前首页完全一致：无变化；
 * - 同序且仅 tag 变化：静默就地刷新，不弹提示；
 * - 其余实质变化：暂存并提示“有新数据”。
 */
internal fun decideMovieFirstPageRefresh(
    currentFirstPage: List<MovieUiModel>,
    freshFirstPage: List<MovieUiModel>,
    isAtTop: Boolean
): FreshRevalidateOutcome = when {
    isAtTop -> FreshRevalidateOutcome.ApplyImmediately
    currentFirstPage == freshFirstPage -> FreshRevalidateOutcome.NoChange
    currentFirstPage.onlyTagsChanged(freshFirstPage) -> FreshRevalidateOutcome.ApplyInPlace
    else -> FreshRevalidateOutcome.StorePending
}

/**
 * 新首页与当前首页是否同序、同内容，仅 [MovieUiModel.tags] 字段可能不同。
 */
private fun List<MovieUiModel>.onlyTagsChanged(fresh: List<MovieUiModel>): Boolean {
    if (size != fresh.size) return false
    // 调用方已排除完全相同的情况，因此任一元素“仅 tags 不同”即满足。
    return indices.all { i -> this[i].copy(tags = fresh[i].tags) == fresh[i] }
}

package me.jbusdriver.modern.ui.movielist

import me.jbusdriver.modern.ui.MovieUiModel

/**
 * 判断新首页与当前首页是否同序、同内容，仅 [MovieUiModel.tags] 字段可能不同。
 * 用于后台刷新只带来 tag 变化时静默就地刷新，避免打扰已滚动的用户。
 */
internal fun List<MovieUiModel>.onlyTagsChanged(fresh: List<MovieUiModel>): Boolean {
    if (size != fresh.size) return false
    var hasTagChange = false
    for (index in indices) {
        val old = this[index]
        val new = fresh[index]
        if (old.code != new.code ||
            old.title != new.title ||
            old.imageUrl != new.imageUrl ||
            old.date != new.date ||
            old.link != new.link
        ) {
            return false
        }
        if (old.tags != new.tags) hasTagChange = true
    }
    return hasTagChange
}

/**
 * 将新首页电影按顺序就地合并进当前列表：已加载的后续页保持不变，
 * 避免用户在列表中部时静默刷新导致已加载内容丢失。
 */
internal fun List<MovieUiModel>.mergeFreshFirstPage(freshFirstPage: List<MovieUiModel>): List<MovieUiModel> =
    if (freshFirstPage.isEmpty()) this else freshFirstPage + drop(freshFirstPage.size)

package me.jbusdriver.modern.ui.search

import me.jbusdriver.modern.ui.MovieUiModel

/**
 * 本地收藏搜索的归一化与匹配工具（纯函数，便于单测）。
 *
 * 归一化规则：转小写，去掉所有 `-`、`_`、空白字符；随后按"子串包含"匹配番号或标题。
 * 例：`abc123` 可命中 `ABC-123` / `ABC_0123`。
 */
private val normalizeSeparator = Regex("[-_\\s]+")

internal fun normalizeSearchText(input: String): String =
    input.lowercase().replace(normalizeSeparator, "")

/**
 * 判断该影片是否匹配本地搜索查询 [query]（对番号 code 与标题 title 做归一化子串匹配）。
 * 查询归一化后为空（仅由分隔符组成或为空）时返回 false。
 */
internal fun MovieUiModel.matchesLocal(query: String): Boolean {
    val q = normalizeSearchText(query)
    if (q.isEmpty()) return false
    return normalizeSearchText(code).contains(q) ||
        normalizeSearchText(title).contains(q)
}

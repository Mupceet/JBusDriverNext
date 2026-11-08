package me.jbusdriver.modern.domain.model

/**
 * 影片番号识别与规范化。
 *
 * 论坛帖子正文/引用块中经常出现番号（如 `ABP-123`），渲染层用这里的正则把命中文本
 * 做成可点击跳转；详情页用 [movieCodeFromUrl] 判断 URL 是否由番号构成，
 * 以便加载失败时给出「未找到影片」提示并回退尝试无码 URL。
 *
 * 只识别大写字母前缀（允许至多 3 位数字开头，覆盖 `200GANA`/`300MIUM` 等厂牌；
 * 2-6 字母 + 2-5 位数字，连字符可省略），显著降低误报；
 * FC2 番号（`FC2-PPV-1234567`）形状特殊，单独一条模式。
 */
private val MOVIE_CODE_REGEX =
    Regex("""(?<![A-Za-z0-9])([0-9]{0,3}[A-Z]{2,6})-?([0-9]{2,5})(?![0-9A-Za-z])""")

private val FC2_MOVIE_CODE_REGEX =
    Regex("""(?<![A-Za-z0-9])FC2-PPV-([0-9]{5,8})(?![0-9A-Za-z])""", RegexOption.IGNORE_CASE)

/** 文本中所有番号命中（FC2 与常规番号合并），按出现位置排序、去除跨模式重叠。 */
fun findMovieCodeMatches(text: String): List<MatchResult> {
    val matches = (FC2_MOVIE_CODE_REGEX.findAll(text) + MOVIE_CODE_REGEX.findAll(text))
        .sortedBy { it.range.first }
    val result = mutableListOf<MatchResult>()
    var lastEnd = -1
    for (match in matches) {
        if (match.range.first >= lastEnd) {
            result += match
            lastEnd = match.range.last + 1
        }
    }
    return result
}

/** 判断单个片段文本是否为一个番号（如链接文字恰好是 `ABP-123` 时），返回规范化形式。 */
fun normalizeMovieCode(raw: String): String? {
    val trimmed = raw.trim()
    FC2_MOVIE_CODE_REGEX.matchEntire(trimmed)?.let {
        return "FC2-PPV-" + it.groupValues[1]
    }
    MOVIE_CODE_REGEX.matchEntire(trimmed)?.let {
        return "${it.groupValues[1]}-${it.groupValues[2]}"
    }
    return null
}

/**
 * 从详情页 URL 提取番号；非番号型 URL（列表页、演员页等）返回 null。
 * 兼容有码 `.../ABP-123` 与无码 `.../uncensored/ABP-123` 两种路径。
 */
fun movieCodeFromUrl(url: String): String? {
    val segments = runCatching { java.net.URI(url).path.orEmpty() }
        .getOrElse { "" }
        .split('/')
        .filter { it.isNotBlank() }
    val last = segments.lastOrNull() ?: return null
    return normalizeMovieCode(last)
}

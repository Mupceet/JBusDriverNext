package me.jbusdriver.modern.domain.model

/**
 * 帖子正文裸链接类型。枚举顺序即识别优先级：重叠命中时取序号更小（更高优先级）的类型，
 * 例如磁力链接里嵌的番号、网盘域名上的完整 URL 都会被更高优先级的类型吞并。
 */
enum class ForumLinkKind {
    URL, MAGNET, ED2K, CLOUD, MOVIE_CODE
}

/**
 * 纯文本裸链接的一次命中；[range] 为闭区间字符区间，[value] 为归一化后的
 * 链接值（去尾部标点；番号为大写规范形式）。
 */
data class BareLinkMatch(
    val range: IntRange,
    val kind: ForumLinkKind,
    val value: String
)

// 链接正文排除 CJK 与全角字符:真实 URL 不含中文,避免把紧随其后的中文句子吞进链接
private val LINK_BODY = """[^\s"'<>\u2E80-\u9FFF\uF900-\uFAFF\uFF00-\uFFEF]+"""

private val URL_REGEX = Regex("""(?i)\bhttps?://$LINK_BODY""")
private val MAGNET_REGEX = Regex("""(?i)magnet:\?$LINK_BODY""")
private val ED2K_REGEX = Regex("""(?i)ed2k://\|file\|$LINK_BODY""")

/**
 * 常见网盘分享链接，支持无 scheme 的裸域名形态（如 `pan.baidu.com/s/xxx`）。
 * 域名清单与主流资源帖一致：百度盘/夸克/阿里云盘(alipan)/UC/115/迅雷/123盘/微云/蓝奏云。
 */
private val CLOUD_REGEX = Regex(
    """(?i)(?:https?://)?(?:pan\.baidu\.com/s/|pan\.quark\.cn/s/|(?:www\.)?aliyundrive\.com/s/|(?:www\.)?alipan\.com/s/|drive\.uc\.cn/s/|115\.com/s/|pan\.xunlei\.com/s/|(?:www\.)?123pan\.com/s/|share\.weiyun\.com/|lanzou[a-z0-9]*\.com/)$LINK_BODY"""
)

/** 识别时从命中尾部剔除的中英标点，避免复制/高亮带上句读。 */
private val TRAILING_PUNCTUATION_CHARS = ",.;，。；！？!?]）」』》…".toSet()

/** 网盘裸域名补全 scheme，供外部打开使用；其余类型原样返回。 */
fun BareLinkMatch.toExternalUrl(): String = when (kind) {
    ForumLinkKind.CLOUD -> if (value.startsWith("http", ignoreCase = true)) value else "https://$value"
    else -> value
}

/**
 * 识别纯文本中的裸链接与番号：URL/磁力/ED2K/网盘/番号，重叠命中按 [ForumLinkKind]
 * 优先级合并（高优先级吞并低优先级，范围取并集），结果按出现位置排序、互不重叠。
 */
fun findBareLinkMatches(text: String): List<BareLinkMatch> {
    if (text.isBlank()) return emptyList()
    val collected = mutableListOf<BareLinkMatch>()
    collectKind(URL_REGEX, ForumLinkKind.URL, text, collected)
    collectKind(MAGNET_REGEX, ForumLinkKind.MAGNET, text, collected)
    collectKind(ED2K_REGEX, ForumLinkKind.ED2K, text, collected)
    collectKind(CLOUD_REGEX, ForumLinkKind.CLOUD, text, collected)
    findMovieCodeMatches(text).forEach { match ->
        collected += BareLinkMatch(
            range = match.range,
            kind = ForumLinkKind.MOVIE_CODE,
            value = normalizeMovieCode(match.value) ?: match.value
        )
    }

    val sorted = collected.sortedWith(compareBy({ it.range.first }, { it.kind }))
    val merged = mutableListOf<BareLinkMatch>()
    for (match in sorted) {
        val last = merged.lastOrNull()
        when {
            last == null || match.range.first > last.range.last -> merged += match

            match.kind < last.kind ->
                // 与更高优先级命中重叠：吞并进高优先级类型，范围取并集
                merged[merged.lastIndex] = last.copy(
                    range = last.range.first..maxOf(last.range.last, match.range.last)
                )

            match.kind > last.kind -> Unit // 被已入列的高优先级命中覆盖

            else ->
                // 同类型相邻/重叠命中：合并范围
                merged[merged.lastIndex] = last.copy(
                    range = last.range.first..maxOf(last.range.last, match.range.last)
                )
        }
    }
    return merged
}

private fun collectKind(
    regex: Regex,
    kind: ForumLinkKind,
    text: String,
    out: MutableList<BareLinkMatch>
) {
    regex.findAll(text).forEach { match ->
        var end = match.range.last
        while (end >= match.range.first && text[end] in TRAILING_PUNCTUATION_CHARS) end--
        if (end >= match.range.first) {
            val range = match.range.first..end
            out += BareLinkMatch(range, kind, text.substring(range))
        }
    }
}

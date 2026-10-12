package me.jbusdriver.modern.data.localvideo.javcode

import me.jbusdriver.modern.data.localvideo.VideoCodeMatcher

/**
 * 分级匹配管线的编排入口：
 * Normalizer/Noise Filter -> FC2/Standard/VR/Legacy/Site-specific Parsers -> Candidate[]
 * -> Prefix Database + Scoring Engine -> confident / ambiguous。
 *
 * 兜底层：分级 parser 都无候选时，回退到 [VideoCodeMatcher.extractCode]。
 * 它覆盖真实站点采样（见 test resources localvideo 样本）中不以字母开头或过短的
 * 合法番号形态（数字前缀素人码 259LUXU-1895、无码日期码 080126_001、单字母前缀 C-1534、
 * 数字+k 054k）。仅在无候选时启用，避免其子串匹配干扰站点码（如 1PONDO-123456-789
 * 中的日期段）与"双候选 -> ambiguous"的判定。
 *
 * 返回的番号保留名称里的原始分隔符写法（见 [verbatimForm]）：站点 URL 由番号直接构成
 * （详情页 /番号），而番号是否带分隔符因厂商而异（TAD8802 无、ABC-123 有），无法从
 * 文件名推断站点形态时，保留文件名写法（用户命名几乎照抄站点）比统一补 `-` 更可靠。
 */
object JavCodeResolver {

    fun resolve(name: String): CodeResolution {
        val cleaned = NoiseFilter.clean(name)
        val tiered = buildList {
            addAll(Fc2Parser.parse(cleaned))
            addAll(StandardParser.parse(cleaned))
            addAll(VrParser.parse(cleaned))
            addAll(LegacyParser.parse(cleaned))
            addAll(SiteSpecificParser.parse(cleaned))
        }
        val candidates = if (tiered.isEmpty()) {
            VideoCodeMatcher.extractCode(cleaned)
                ?.let { listOf(CodeCandidate(it, CandidateSource.LEGACY)) }
                .orEmpty()
        } else {
            tiered
        }
        val scored = CodeScoringEngine.score(candidates)
        return if (scored.code == null) scored
        else CodeResolution(verbatimForm(scored.code, name), scored.confidence)
    }

    /**
     * 把打分用的规范形态（统一补 `-`）还原为名称里的原始写法：在原名中定位番号片段，
     * 保留其分隔符形态（`_` 原样、空格折叠为 `-`、无分隔符则不补）。原名中找不到
     * 对应片段时回退规范形态。尾部仅排斥数字（允许 VR 等字母后缀跟随在片段之后）。
     */
    internal fun verbatimForm(canonicalCode: String, originalName: String): String {
        val pattern = canonicalCode.split('-', '_')
            .joinToString("[-_ ]*") { Regex.escape(it) }
            .let { "(?<![A-Z0-9])$it(?!\\d)" }
        val span = Regex(pattern).find(originalName.uppercase())?.value ?: return canonicalCode
        return span.replace(Regex(" +"), "-")
    }
}

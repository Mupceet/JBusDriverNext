package me.jbusdriver.modern.data.localvideo

/**
 * 番号提取/匹配规则（大小写不敏感，结果归一化为大写）。
 *
 * 番号的本质特征：字母开头的字母数字串，且含一段连续数字（番号的"序号"）。据此：
 * - `ABC-123` / `ABC123` / `ABC-123-C` / `ABC_123` / `FC2-PPV-1234567` 都能识别；
 * - `_` 仅在"字母前缀与数字序号之间"视为分隔符；出现在序号之后（如 `ABC-123_4K`）
 *   会作为画质后缀的分界被截断，使 `ABC-123` 与 `ABC-123_4K` 归并到同一番号；
 * - 空格、`.`、`()` 等同样截断；前方括号里的纯文字（`[Group] ABC-123`）因不含数字被跳过；
 * - 不含连续数字的片段（`clip`、`Extras`、`Group`、`4K-trailer`）返回 null，使无番号文件可被丢弃。
 *
 * 站点真实数据（见 test resources 下 localvideo 目录的番号样本文件）中还有四类不以字母开头或过短、但同样合法的番号：
 * - 素人系列数字前缀番号：`259LUXU-1895`、`300MIUM-1377`、`390JAC-240`、`3DSVR-2009`；
 * - 无码日期型番号：`080126_001` / `060126-001`（MMDDYY + 分隔符 + 序号，两种分隔符写法并存）；
 * - 单字母前缀番号：`C-1534`、`P-2982`；
 * - 无码数字+k 后缀番号：`054k`（站点原样小写，提取后归一化为大写 `054K`）。
 */
object VideoCodeMatcher {

    // 候选片段，按优先级排列：
    // 1. 无码日期型：6 位日期数字 + -/_ 分隔 + 2~4 位序号；
    // 2. 无码数字+k：2~3 位数字 + k/K（后不可紧跟字母数字，避免吞掉普通单词）；
    // 3. 单字母前缀：1 个字母 + -/_ 分隔 + 序号（要求分隔后紧跟数字，
    //    使 `1080p_ABC-123` 中的 `p_` 不被误认为前缀，仍提取到 `ABC-123`）；
    // 4. 数字前缀：1~4 位系列号 + 至少 2 个字母 + -/_ 分隔 + 序号（要求字母≥2，
    //    使 `1080p_ABC-123` 中的 `1080p` 不被误认为前缀，仍提取到 `ABC-123`）；
    // 5. 字母开头：允许中间 -/_ 作为前缀与序号的分隔，尾部允许字母/数字/-（如 -C）。
    //    下划线不计入尾部，避免吞掉 _4K/_HD 等画质后缀。
    private val tokenRegex =
        Regex(
            """\d{6}[-_]\d{2,4}""" +
                """|\d{2,3}[kK](?![A-Za-z0-9])""" +
                """|[A-Za-z][-_]\d{2,5}""" +
                """|\d{1,4}[A-Za-z]{2,}[-_]\d{2,}""" +
                """|[A-Za-z][A-Za-z0-9]{1,9}[-_]?[A-Za-z0-9-]*"""
        )

    // 番号必须含至少 2 位连续数字（序号段）。
    private val digitRun = Regex("""\d{2,}""")

    /** 从文件名中提取番号（大写），无法识别返回 null。 */
    fun extractCode(fileName: String): String? {
        val withoutExt = fileName.substringBeforeLast('.')
        return tokenRegex.findAll(withoutExt)
            .map { it.value.trimEnd('-', '_') }
            .firstOrNull { it.length >= 3 && digitRun.containsMatchIn(it) }
            ?.uppercase()
    }

    /** 文件名是否属于给定番号（大小写不敏感）。 */
    fun matchesCode(fileName: String, code: String): Boolean {
        val target = code.trim()
        if (target.isBlank()) return false
        return extractCode(fileName)?.equals(target, ignoreCase = true) == true
    }

    /** 把 `-`/`_` 视为同一分隔符的归一化：大写并把 `_` 统一为 `-`。 */
    fun normalizeSeparators(name: String): String =
        name.trim().uppercase().replace('_', '-')

    /**
     * 收藏影片名称匹配：本地视频名称与收藏番号按 `-`/`_` 等价比较。
     * 整名相等，或名称以"番号 + 非字母数字分隔符"开头（允许 1080p/4K/-C 等后缀）即命中。
     */
    fun matchesMovieName(name: String, code: String): Boolean {
        val target = code.trim()
        if (target.isBlank()) return false
        val normalizedName = normalizeSeparators(name)
        val normalizedCode = normalizeSeparators(target)
        if (normalizedName == normalizedCode) return true
        return normalizedName.length > normalizedCode.length &&
            normalizedName.startsWith(normalizedCode) &&
            !normalizedName[normalizedCode.length].isLetterOrDigit()
    }
}

package me.jbusdriver.modern.data.localvideo

/**
 * 番号提取/匹配规则（大小写不敏感，结果归一化为大写）。
 *
 * 番号的本质特征：字母开头的字母数字串，且含一段连续数字（番弓的"序号"）。据此：
 * - `ABC-123` / `ABC123` / `ABC-123-C` / `ABC_123` / `FC2-PPV-1234567` 都能识别；
 * - `_` 仅在"字母前缀与数字序号之间"视为分隔符；出现在序号之后（如 `ABC-123_4K`）
 *   会作为画质后缀的分界被截断，使 `ABC-123` 与 `ABC-123_4K` 归并到同一番号；
 * - 空格、`.`、`()` 等同样截断；前导方括号里的纯文字（`[Group] ABC-123`）因不含数字被跳过；
 * - 不含连续数字的片段（`clip`、`Extras`、`Group`、`4K-trailer`）返回 null，使无番号文件可被丢弃。
 */
object VideoCodeMatcher {

    // 候选片段：字母开头，允许中间 -/_ 作为前缀与序号的分隔，尾部允许字母/数字/-（如 -C）。
    // 下划线不计入尾部，避免吞掉 _4K/_HD 等画质后缀。
    private val tokenRegex = Regex("""[A-Za-z][A-Za-z0-9]{1,9}[-_]?[A-Za-z0-9-]*""")

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
}

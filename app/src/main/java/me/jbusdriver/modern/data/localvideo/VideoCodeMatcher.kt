package me.jbusdriver.modern.data.localvideo

/**
 * 番号提取/匹配规则（大小写不敏感，结果归一化为大写）。
 *
 * 提取正则在文件名（去扩展名）中找首个 `[字母]{2,6}-?[数字]{2,5}[字母数字-]*` 的片段，
 * 末尾再裁掉尾部连字符。这样：
 * - `ABC-123` / `ABC-123_4K` / `ABC-123 (1080p)` 都提取为 `ABC-123`（分隔符截断）；
 * - `ABC-123-C` / `ABC-123D` 整体提取为不同番号；
 * - 前导方括号（`[ABC-123]`、`[Group] ABC-123`）会被跳过。
 */
object VideoCodeMatcher {

    private val codeRegex = Regex("""[A-Za-z]{2,6}-?\d{2,5}[A-Za-z0-9-]*""")

    /** 从文件名中提取番号（大写），无法识别返回 null。 */
    fun extractCode(fileName: String): String? {
        val withoutExt = fileName.substringBeforeLast('.')
        val raw = codeRegex.find(withoutExt)?.value?.trimEnd('-') ?: return null
        if (raw.length < 3) return null
        return raw.uppercase()
    }

    /** 文件名是否属于给定番号（大小写不敏感）。 */
    fun matchesCode(fileName: String, code: String): Boolean {
        val target = code.trim()
        if (target.isBlank()) return false
        return extractCode(fileName)?.equals(target, ignoreCase = true) == true
    }
}

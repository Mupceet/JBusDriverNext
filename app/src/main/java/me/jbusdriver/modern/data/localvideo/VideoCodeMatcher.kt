package me.jbusdriver.modern.data.localvideo

/**
 * 番号提取/匹配规则（大小写不敏感，结果归一化为大写）。
 *
 */
object VideoCodeMatcher {

    // 优化的正则表达式：
    // 1. 支持字母或数字开头 ([A-Za-z0-9]{2,10})
    // 2. 中间可以是可选的横杠或下划线 ([-_]?)
    // 3. 后面紧跟字母或数字组合 ([A-Za-z0-9_-]+)
    private val codeRegex = Regex("""([A-Za-z0-9]{2,10}[-_]?[A-Za-z0-9_-]+)""")

    /** 从文件名中提取番号（大写），无法识别返回 null。 */
    fun extractCode(fileName: String): String? {
        val withoutExt = fileName.substringBeforeLast('.')

        // 寻找匹配项
        val matchResult = codeRegex.find(withoutExt) ?: return null
        var raw = matchResult.value

        // 清理末尾可能多余的连接符或空格
        raw = raw.replace(Regex("""[-_\s]+$"""), "")

        if (raw.length < 3) return null
        return raw.uppercase()
    }

    /** 文件名是否属于给定番号（大小写不敏感）。 */
    fun matchesCode(fileName: String, code: String): Boolean {
        val target = code.trim()
        if (target.isBlank()) return false

        val extracted = extractCode(fileName) ?: return false
        return extracted.equals(target, ignoreCase = true)
    }
}

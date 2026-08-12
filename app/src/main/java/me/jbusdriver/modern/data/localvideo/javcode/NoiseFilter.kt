package me.jbusdriver.modern.data.localvideo.javcode

/**
 * 噪声过滤：在解析前把分辨率、画质、编码、来源标签等非番号噪声去掉，
 * 并统一 `-`/`_` 分隔符（`_` 视为 `-`），保留含数字的方括号内容（如 [ABC-123]）。
 */
object NoiseFilter {

    private val noiseWords = listOf(
        Regex("""\b\d{3,4}P\b"""),
        Regex("""\b[248]K\b"""),
        Regex("""\bHDR\b"""),
        Regex("""\bX?264\b"""),
        Regex("""\bX?265\b"""),
        Regex("""\bHEVC\b"""),
        Regex("""\b10BIT\b"""),
        Regex("""\b8BIT\b"""),
        Regex("""\bFHD\b"""),
        Regex("""\bUHD\b"""),
        Regex("""\bHDTV\b"""),
        Regex("""\bBLURAY\b"""),
        Regex("""\bBDRIP\b"""),
        Regex("""\bDVDRIP\b"""),
        Regex("""\bWEBRIP\b"""),
        Regex("""\bWEBDL\b"""),
        Regex("""\bSAMPLE\b"""),
        Regex("""\bTRAILER\b"""),
        Regex("""\bPREVIEW\b"""),
        Regex("""\bHD\b"""),
    )

    private val bracketGroup = Regex("""\[([^\[\]]*)]""")
    private val parenGroup = Regex("""\(([^()]*)\)""")

    /** 清洗名称：大写、`_`→`-`、去噪声词、去无数字的括号组、规整空白与边缘分隔符。 */
    fun clean(input: String): String {
        var s = input.uppercase().replace('_', '-')
        noiseWords.forEach { s = it.replace(s, " ") }
        s = stripNonDigitGroups(s, bracketGroup)
        s = stripNonDigitGroups(s, parenGroup)
        s = s.replace(Regex("""\s+"""), " ")
        s = s.trim().trim('-').trim(' ')
        return s
    }

    private fun stripNonDigitGroups(input: String, regex: Regex): String =
        regex.replace(input) { m -> if (m.groupValues[1].any(Char::isDigit)) m.value else " " }
}

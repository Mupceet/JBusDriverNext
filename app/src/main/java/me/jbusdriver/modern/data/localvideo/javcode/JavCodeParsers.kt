package me.jbusdriver.modern.data.localvideo.javcode

/**
 * 分级匹配管线的各级 parser。每个 parser 从清洗后的名称中产出候选番号
 * （大写、统一分隔符的规范形式），供打分引擎聚合。
 */
object Fc2Parser {
    private val regex = Regex("""(?i)(?<![a-z0-9])FC2[-_ ]?(?:PPV[-_ ]?)?(\d{6,8})(?![a-z0-9])""")

    fun parse(name: String): List<CodeCandidate> = regex.findAll(name).map { m ->
        val digits = m.groupValues[1]
        val hasPpv = m.value.uppercase().contains("PPV")
        CodeCandidate(if (hasPpv) "FC2-PPV-$digits" else "FC2-$digits", CandidateSource.FC2)
    }.toList()
}

object StandardParser {

    private val tokenRegex = Regex(
        """(?<![A-Z0-9])[A-Z]{2,6}[-_]?\d{2,6}(?:-?[A-Z]{1,3})?(?![A-Z0-9])"""
    )

    fun parse(name: String): List<CodeCandidate> =
        tokenRegex.findAll(name.uppercase())
            .map { it.value }
            .map(::canonicalize)
            .distinct()
            .map { CodeCandidate(it, CandidateSource.STANDARD) }
            .toList()

    /**
     * 规范化：`_`→`-`；字母数字间补 `-`；剥离尾部的 VR 标记（如 ABC-001VR）。
     * 首部 VR 不剥离：站点存在本身以 VR 开头的厂商标签（VRPRD/VRKM/VRNC），
     * 当成标记剥掉会让入库番号与站点不符，详情 URL `/番号` 取不到内容。
     */
    internal fun canonicalize(raw: String): String {
        var t = raw.uppercase().replace('_', '-')
        if (t.length > 2 && t.endsWith("VR")) t = t.dropLast(2).trimEnd('-')
        t = t.replace(Regex("""([A-Z])(\d)"""), "$1-$2")
        return t.trimEnd('-')
    }
}

/**
 * 独立的 VR 标记（`VR ABC-001` / `ABC-001 VR` / `ABC-001VR`）。
 * 紧贴写法（`VRABC-001`）不算标记：站点番号本身可能以 VR 开头（VRPRD-202、VRKM-1874），
 * 与"标记 + 番号"在字符串上无法区分，按"文件名照抄站点"的启发式保留原样。
 */
object VrParser {
    private val prefixSpaced = Regex("""(?i)\bVR\s+([A-Z]{2,6}[-_]?\d{2,6}(?:-?[A-Z]{1,3})?)\b""")
    private val suffixTight = Regex("""(?i)\b([A-Z]{2,6}[-_]?\d{2,6}(?:-?[A-Z]{1,3})?)VR\b""")
    private val suffixSpaced = Regex("""(?i)\b([A-Z]{2,6}[-_]?\d{2,6}(?:-?[A-Z]{1,3})?)\s+VR\b""")

    fun parse(name: String): List<CodeCandidate> = buildList {
        prefixSpaced.findAll(name).forEach { m -> add(CodeCandidate(StandardParser.canonicalize(m.groupValues[1]), CandidateSource.VR)) }
        suffixTight.findAll(name).forEach { m -> add(CodeCandidate(StandardParser.canonicalize(m.groupValues[1]), CandidateSource.VR)) }
        suffixSpaced.findAll(name).forEach { m -> add(CodeCandidate(StandardParser.canonicalize(m.groupValues[1]), CandidateSource.VR)) }
    }
}

object LegacyParser {
    private val patterns = listOf(
        Regex("""\bHEYZO[-_]?(\d{4})\b""") to { m: MatchResult -> "HEYZO-${m.groupValues[1]}" },
        Regex("""\bSIRO[-_]?(\d{4})\b""") to { m: MatchResult -> "SIRO-${m.groupValues[1]}" },
        Regex("""\b200GANA[-_]?(\d{4})\b""") to { m: MatchResult -> "200GANA-${m.groupValues[1]}" },
        Regex("""\bTOKYO[-_]HOT[-_]?(N?)(\d{3,4})\b""") to { m: MatchResult ->
            "TOKYO-HOT-${m.groupValues[1]}${m.groupValues[2]}"
        },
        Regex("""\bXXX[-_]AV[-_]?(\d{4,5})\b""") to { m: MatchResult -> "XXX-AV-${m.groupValues[1]}" },
        Regex("""\bMGS[-_]?(\d{4,7})\b""") to { m: MatchResult -> "MGS-${m.groupValues[1]}" },
    )

    fun parse(name: String): List<CodeCandidate> = buildList {
        val upper = name.uppercase()
        for ((regex, fmt) in patterns) {
            regex.findAll(upper).forEach { m -> add(CodeCandidate(fmt(m), CandidateSource.LEGACY)) }
        }
    }
}

object SiteSpecificParser {
    private val patterns = listOf(
        Regex("""\b1PONDO[-_ ]?(\d{6})[-_]?(\d{2,3})\b""") to { m: MatchResult -> "1PONDO-${m.groupValues[1]}_${m.groupValues[2]}" },
        Regex("""\b10MUSUME[-_ ]?(\d{6})[-_]?(\d{2,3})\b""") to { m: MatchResult -> "10MUSUME-${m.groupValues[1]}_${m.groupValues[2]}" },
        Regex("""\bCARIBBEANCOM[-_ ]?(\d{6})[-_]?(\d{2,3})\b""") to { m: MatchResult -> "CARIBBEANCOM-${m.groupValues[1]}_${m.groupValues[2]}" },
        Regex("""\bCARIB[-_ ]?(\d{6})[-_]?(\d{2,3})\b""") to { m: MatchResult -> "CARIB-${m.groupValues[1]}_${m.groupValues[2]}" },
        Regex("""\bPACOPACOMAMA[-_ ]?(\d{6})[-_]?(\d{2,3})\b""") to { m: MatchResult -> "PACOPACOMAMA-${m.groupValues[1]}_${m.groupValues[2]}" },
        Regex("""\bMURAMURA[-_ ]?(\d{6})[-_]?(\d{2,3})\b""") to { m: MatchResult -> "MURAMURA-${m.groupValues[1]}_${m.groupValues[2]}" },
        Regex("""\bGACHINCO[-_]?(\d{4,6})\b""") to { m: MatchResult -> "GACHINCO-${m.groupValues[1]}" },
    )

    fun parse(name: String): List<CodeCandidate> = buildList {
        val upper = name.uppercase()
        for ((regex, fmt) in patterns) {
            regex.findAll(upper).forEach { m ->
                add(CodeCandidate(fmt(m), CandidateSource.SITE_SPECIFIC))
            }
        }
    }
}

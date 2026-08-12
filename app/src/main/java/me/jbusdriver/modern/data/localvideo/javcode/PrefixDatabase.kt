package me.jbusdriver.modern.data.localvideo.javcode

/**
 * 已知厂商/站点前缀库。用于打分引擎的置信度加成：
 * 命中知名前缀的候选比随机字母串更可信。
 */
object PrefixDatabase {

    private val knownPrefixes = setOf(
        // 主流有码厂商
        "ABP", "ABF", "ADN", "ATID", "BBI", "BBAN", "BLK", "BOKU", "CEMD", "CJOD",
        "DASD", "DASS", "DDK", "DVAJ", "EBD", "EKDV", "EMUZ", "FSDSS", "GDHH", "HMN",
        "HND", "HODV", "HUNT", "HZGD", "IPX", "IPZZ", "JUL", "JUFE", "JUR", "JUX",
        "JUY", "KAAD", "KAVR", "KBI", "KIRE", "KISD", "KKRK", "KONM", "KSBJ", "KTDS",
        "LULU", "MBM", "MDBK", "MDTM", "MEYD", "MIAE", "MIAA", "MIDV", "MIDE", "MIMK",
        "MIUM", "MKCK", "MKMP", "MOKO", "MOPP", "MRSS", "MVSD", "NNPJ", "NTRD", "OFJE",
        "OKS", "OLMX", "ONEX", "OYC", "PACP", "PGSD", "PRED", "RKI", "ROCK", "RYGN",
        "SAME", "SBAM", "SCM", "SDAB", "SDDE", "SGA", "SGM", "SHKD", "SIMG", "SKY",
        "SMD", "SNIS", "SORA", "SOE", "SON", "SPA", "SQIS", "SSIS", "SSNI", "SSPD",
        "SSVR", "STARS", "START", "STC", "SUKE", "SVDVD", "SW",
        // 无码 / 素人 / 独立站点
        "FC2", "FC2-PPV", "1PONDO", "10MUSUME", "200GANA", "CARIB", "CARIBBEANCOM",
        "GACHINCO", "HEYZO", "MGS", "MURAMURA", "PACOPACOMAMA", "SIRO", "TOKYO-HOT", "XXX-AV",
    )

    /** code 是否命中已知前缀：整码相等，或码以"前缀+分隔符"开头。 */
    fun isKnownPrefix(code: String): Boolean {
        val upper = code.uppercase()
        return knownPrefixes.any { upper == it || upper.startsWith("$it-") || upper.startsWith("${it}_") }
    }
}


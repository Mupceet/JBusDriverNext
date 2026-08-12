package me.jbusdriver.modern.data.localvideo.javcode

/**
 * 分级匹配管线的编排入口：
 * Normalizer/Noise Filter -> FC2/Standard/VR/Legacy/Site-specific Parsers -> Candidate[]
 * -> Prefix Database + Scoring Engine -> confident / ambiguous。
 */
object JavCodeResolver {

    fun resolve(name: String): CodeResolution {
        val cleaned = NoiseFilter.clean(name)
        val candidates = buildList {
            addAll(Fc2Parser.parse(cleaned))
            addAll(StandardParser.parse(cleaned))
            addAll(VrParser.parse(cleaned))
            addAll(LegacyParser.parse(cleaned))
            addAll(SiteSpecificParser.parse(cleaned))
        }
        return CodeScoringEngine.score(candidates)
    }
}

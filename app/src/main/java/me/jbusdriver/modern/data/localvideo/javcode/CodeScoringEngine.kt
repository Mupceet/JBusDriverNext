package me.jbusdriver.modern.data.localvideo.javcode

/**
 * 打分引擎：聚合各级 parser 的候选，按"来源基础分 + 已知前缀加成 + 多源一致加成"打分，
 * 只有唯一高分候选（与次高差距 >= [AMBIGUITY_GAP]）才判定为 confident 并自动选定番号，
 * 否则视为 ambiguous（不自动选，交由手动/元数据兜底）。
 */
object CodeScoringEngine {

    private const val PREFIX_BONUS = 1.0
    private const val CONSENSUS_BONUS = 1.0
    private const val CONFIDENT_THRESHOLD = 2.0
    private const val AMBIGUITY_GAP = 1.0

    fun score(candidates: List<CodeCandidate>): CodeResolution {
        if (candidates.isEmpty()) return CodeResolution.NONE

        val scored = candidates.groupBy { it.code }.map { (code, list) ->
            var s = list.maxOf { it.source.baseScore }
            if (PrefixDatabase.isKnownPrefix(code)) s += PREFIX_BONUS
            if (list.size >= 2) s += CONSENSUS_BONUS
            s to code
        }

        val ranked = scored.sortedByDescending { it.first }
        val best = ranked.first()
        val second = ranked.getOrNull(1)
        val confident = best.first >= CONFIDENT_THRESHOLD &&
            (second == null || best.first - second.first >= AMBIGUITY_GAP)
        return if (confident) CodeResolution(best.second, MatchConfidence.CONFIDENT)
        else CodeResolution.NONE
    }
}

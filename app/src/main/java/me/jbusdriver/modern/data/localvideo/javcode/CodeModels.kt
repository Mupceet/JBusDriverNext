package me.jbusdriver.modern.data.localvideo.javcode

/** 匹配结果的置信度。confident -> 自动选定番号；ambiguous -> 不自动选，交由手动/元数据兜底。 */
enum class MatchConfidence { CONFIDENT, AMBIGUOUS }

/** 候选来源（分级匹配管线的一级 parser）。baseScore 用于后续打分。 */
enum class CandidateSource(val baseScore: Double) {
    FC2(3.0),
    STANDARD(2.0),
    VR(2.0),
    LEGACY(2.0),
    SITE_SPECIFIC(1.8),
}

/** 单个候选番号。 */
data class CodeCandidate(val code: String, val source: CandidateSource)

/** 解析结果：confident 时 [code] 非空；ambiguous 时 [code] 为 null。 */
data class CodeResolution(val code: String?, val confidence: MatchConfidence) {
    companion object {
        val NONE = CodeResolution(null, MatchConfidence.AMBIGUOUS)
    }
}

package me.jbusdriver.modern.domain.model

/**
 * LTX 库（ltx.libray.workers.dev）领域模型。
 *
 * 模糊化契约：仅保留番号 → 下载链接 + 状态标记 + 入库时间的最小数据；
 * 站点转引的标题/演员/封面/评分等元数据在解析边界即丢弃，
 * 展示信息由应用内按本地收藏快照逻辑补全。
 */

/** LTX 下载链接类型。 */
enum class LtxLinkType { ED2K, MAGNET }

/** LTX 站点提供的一条下载链接。ed2k 链接额外解析出文件名与字节数。 */
data class LtxLink(
    val type: LtxLinkType,
    val uri: String,
    val fileName: String? = null,
    val sizeBytes: Long? = null,
)

/** LTX 库中一个番号的条目。`isPending=true` 表示链接待确认。 */
data class LtxEntry(
    val code: String,
    val links: List<LtxLink> = emptyList(),
    val pendingLinks: List<LtxLink> = emptyList(),
    val isPending: Boolean = false,
    val isMissing: Boolean = false,
    val addedAtMillis: Long = 0L,
    /** 库内原始发行时间（yyyy-MM-dd）；补全前即可用，排序/展示以此为准。 */
    val date: String? = null,
    /** 评分（源站数据，仅用于排序展示）。 */
    val score: Double? = null,
) {
    val hasConfirmedLinks: Boolean get() = links.isNotEmpty()
}

/** LTX 全库快照，entries 默认按入库时间倒序。 */
data class LtxLibrary(
    val updatedAtMillis: Long = 0L,
    val entries: List<LtxEntry> = emptyList(),
) {
    /** 按番号归一化匹配条目。 */
    fun find(code: String): LtxEntry? =
        entries.firstOrNull { normalizeLtxCode(it.code) == normalizeLtxCode(code) }
}

/** 番号归一化：去首尾空白、统一大写，用于站内匹配。 */
fun normalizeLtxCode(code: String): String = code.trim().uppercase()

package me.jbusdriver.modern.data.localvideo

import me.jbusdriver.modern.data.db.entity.LocalVideoEntity
import me.jbusdriver.modern.data.localvideo.javcode.JavCodeResolver
import me.jbusdriver.modern.data.localvideo.javcode.MatchConfidence

/** 未分类本地视频的哨兵番号：空串表示"尚未确认番号"。 */
const val UNCLASSIFIED_CODE = ""

/**
 * 把扫描到的文件列表映射为索引实体（纯函数，无 Android 依赖）。
 *
 * 新语义（2026-08 优化）：
 * 1. 先全量收集名称、不过滤：每条记录的展示名 = 第一级（root 直接子）文件夹名；
 *    文件直接在 root 下则用视频文件名（去扩展名）。
 * 2. 收藏影片番号匹配优先：`-`/`_` 视为同一分隔符，命中则直接归属该番号（confident）。
 * 3. 其余走分级匹配管线（FC2/Standard/VR/Legacy/Site + 打分）确认番号；
 *    confident -> 自动选定；ambiguous/无结果 -> code 置空串（未分类，仍保留记录，供排除/手动兜底）。
 */
fun scanVideoFiles(
    files: List<ScannedFile>,
    collectedCodes: Set<String>,
    scannedAt: Long,
): List<LocalVideoEntity> = files.map { f ->
    val displayName = displayNameOf(f)
    val code = favoriteCode(f, collectedCodes)
        ?: JavCodeResolver.resolve(displayName)
            .takeIf { it.confidence == MatchConfidence.CONFIDENT }
            ?.code
        ?: UNCLASSIFIED_CODE
    LocalVideoEntity(
        code = code,
        name = displayName,
        uri = f.uri,
        mime = f.mime,
        size = f.size,
        scannedAt = scannedAt,
    )
}

/** 展示名：第一级（root 直接子）文件夹名优先；文件直接在 root 下则用视频文件名（去扩展名）。 */
internal fun displayNameOf(f: ScannedFile): String =
    f.parentFolderNames.lastOrNull() ?: f.name.substringBeforeLast('.')

private fun favoriteCode(f: ScannedFile, collectedCodes: Set<String>): String? {
    val name = displayNameOf(f)
    return collectedCodes.firstOrNull { VideoCodeMatcher.matchesMovieName(name, it) }?.uppercase()
}

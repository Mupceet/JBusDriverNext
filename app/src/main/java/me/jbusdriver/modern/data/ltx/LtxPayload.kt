package me.jbusdriver.modern.data.ltx

import androidx.annotation.Keep
import com.google.gson.annotations.SerializedName
import me.jbusdriver.modern.core.GSON
import me.jbusdriver.modern.core.fromJson
import me.jbusdriver.modern.domain.model.LtxEntry
import me.jbusdriver.modern.domain.model.LtxLibrary
import me.jbusdriver.modern.domain.model.LtxLink
import me.jbusdriver.modern.domain.model.LtxLinkType

/** LTX 库默认地址（Cloudflare Workers 部署，可在设置中替换）。 */
const val DEFAULT_LTX_BASE_URL = "https://ltx.libray.workers.dev"

/** LTX 全量数据端点路径（单包 JSON，无分页、无鉴权）。 */
const val LTX_ITEMS_PATH = "/api/items"

/*
 * 模糊化契约：DTO 只声明番号/链接/状态/时间字段。标题、演员、封面、评分等
 * 转引元数据一律不声明（Gson 忽略未知字段），从解析边界即丢弃。
 */
@Keep
data class LtxItemsPayloadDto(
    val items: List<LtxItemDto>? = null,
    @SerializedName("updated_at") val updatedAt: Long? = null,
)

@Keep
data class LtxItemDto(
    val code: String? = null,
    val links: List<String>? = null,
    @SerializedName("pending_links") val pendingLinks: List<String>? = null,
    val pending: Boolean? = null,
    val missing: Boolean? = null,
    @SerializedName("added_at") val addedAt: Long? = null,
    /** 发行时间（库内原始值；排序/展示以此为准，站内快照日期仅作回退）。 */
    val date: String? = null,
    /** 评分（仅用于排序，2026-11-04 用户要求扩展进契约）。 */
    val score: Double? = null,
)

/**
 * 解析 `GET {base}/api/items` 响应为 [LtxLibrary]。
 *
 * 载荷非法或缺少 items 时返回 null（调用方据此判定不可缓存）。
 * entries 按入库时间倒序，保证默认展示顺序稳定。
 */
internal fun parseLtxLibrary(json: String): LtxLibrary? {
    val payload = GSON.fromJson<LtxItemsPayloadDto>(json) ?: return null
    val items = payload.items ?: return null
    val entries = items.asSequence()
        .filter { !it.code.isNullOrBlank() }
        .map { dto ->
            // 同一 URI 可能同时出现在 links 与 pending_links（确认过渡期），
            // 在解析边界去重：已确认优先，pending 剔除与已确认重复的项
            val confirmed = dto.links.orEmpty().mapNotNull(::parseLtxLink).distinctBy { it.uri }
            val confirmedUris = confirmed.asSequence().map { it.uri }.toSet()
            val pending = dto.pendingLinks.orEmpty().mapNotNull(::parseLtxLink)
                .distinctBy { it.uri }
                .filter { it.uri !in confirmedUris }
            LtxEntry(
                code = dto.code.orEmpty().trim(),
                links = confirmed,
                pendingLinks = pending,
                isPending = dto.pending ?: false,
                isMissing = dto.missing ?: false,
                addedAtMillis = dto.addedAt ?: 0L,
                date = dto.date?.trim()?.takeIf { it.isNotEmpty() },
                score = dto.score,
            )
        }
        .sortedByDescending { it.addedAtMillis }
        .toList()
    return LtxLibrary(updatedAtMillis = payload.updatedAt ?: 0L, entries = entries)
}

/** 解析单条下载链接；无法识别的 scheme 返回 null。 */
internal fun parseLtxLink(uri: String): LtxLink? {
    val trimmed = uri.trim()
    return when {
        trimmed.startsWith("ed2k://", ignoreCase = true) -> parseEd2kLink(trimmed)
        trimmed.startsWith("magnet:", ignoreCase = true) -> parseMagnetLink(trimmed)
        else -> null
    }
}

/**
 * ed2k 链接格式：`ed2k://|file|<文件名>|<字节数>|<MD4哈希>|[|h=<根哈希>|]/`
 * 仅要求文件名/字节数/哈希三段有效，可选的 h= 段与结尾斜杠兼容。
 */
private val ED2K_PATTERN =
    Regex("""^ed2k://\|file\|([^|]+)\|(\d+)\|[0-9A-Fa-f]{32}(\|h=[^|]*)?\|/?$""", RegexOption.IGNORE_CASE)

private fun parseEd2kLink(uri: String): LtxLink? {
    val match = ED2K_PATTERN.matchEntire(uri) ?: return null
    val fileName = match.groupValues[1]
    val sizeBytes = match.groupValues[2].toLongOrNull() ?: return null
    return LtxLink(
        type = LtxLinkType.ED2K,
        uri = uri,
        fileName = fileName,
        sizeBytes = sizeBytes,
    )
}

/** magnet 链接：不改动 uri（含 tracker），尽力解出 `dn` 参数作为展示文件名。 */
private fun parseMagnetLink(uri: String): LtxLink {
    val fileName = uri.substringAfter('?', "")
        .split('&')
        .firstOrNull { it.startsWith("dn=", ignoreCase = true) }
        ?.substring(3)
        ?.let { raw -> runCatching { urlDecode(raw) }.getOrNull() }
    return LtxLink(type = LtxLinkType.MAGNET, uri = uri, fileName = fileName)
}

private fun urlDecode(value: String): String =
    java.net.URLDecoder.decode(value, "UTF-8")

package me.jbusdriver.modern.data.ltx

import androidx.annotation.Keep
import com.google.gson.annotations.SerializedName
import me.jbusdriver.modern.core.GSON
import me.jbusdriver.modern.core.fromJson
import me.jbusdriver.modern.domain.model.LtxEntry
import me.jbusdriver.modern.domain.model.LtxLibrary
import me.jbusdriver.modern.domain.model.LtxLink
import me.jbusdriver.modern.domain.model.LtxLinkType

/** LTX 库默认地址（2026-11-13 起站点迁移至自有域名，Cloudflare 前置，可在设置中替换）。 */
const val DEFAULT_LTX_BASE_URL = "https://ltxlib.dpdns.org"

/** 历史默认域名（旧接口已 410 停用）：归一化时视为未自定义，回退当前默认。 */
val LEGACY_LTX_BASE_URLS = setOf("https://ltx.libray.workers.dev")

/** LTX 全量数据端点路径（单包 JSON，无分页、无鉴权）。 */
const val LTX_ITEMS_PATH = "/api/items"

/** 加密会话协商端点路径（迁移站协议，2026-11-15 起）。 */
const val LTX_SESSION_PATH = "/api/crypto/session"

/**
 * 归一化 LTX 基址：去空白与尾斜杠；旧默认域迁移到当前默认，避免存量用户
 * 的已保存偏好锁死在已停用的 workers.dev 上。
 */
fun normalizeLtxBaseUrl(url: String): String {
    val trimmed = url.trim().trimEnd('/')
    return if (trimmed.isEmpty() || trimmed in LEGACY_LTX_BASE_URLS) DEFAULT_LTX_BASE_URL else trimmed
}

/**
 * 解析旧域 410 迁移公告体 `{"error":..,"reason":"migrated","url":<新址>}` 中的
 * 新站点地址（仅接受 https，去尾斜杠）；非迁移响应返回 null。
 */
fun parseLtxMigratedBaseUrl(body: String): String? {
    if (!body.contains("\"migrated\"")) return null
    val url = runCatching { GSON.fromJson<Map<String, String>>(body)?.get("url") }.getOrNull()
    return url?.trim()?.trimEnd('/')?.takeIf { it.startsWith("https://") }
}

/*
 * 模糊化契约：DTO 只声明番号/链接/状态/时间字段。标题、演员、封面、评分等
 * 转引元数据一律不声明（Gson 忽略未知字段），从解析边界即丢弃。
 */
@Keep
data class LtxItemsPayloadDto(
    val items: List<LtxItemDto>? = null,
    @SerializedName("updated_at") val updatedAt: Long? = null,
    /** 全库条目总数（2026-11 分页协议起；旧镜像单包无此字段）。 */
    val total: Int? = null,
    /** 服务端快照 id（分页协议起）：翻页请求原样回传，保证翻页期间数据集一致。 */
    val snapshot: String? = null,
)

@Keep
data class LtxItemDto(
    val code: String? = null,
    /** 单链接契约（2026-11 分页协议起）：ed2k 或 magnet 二选一。 */
    val link: String? = null,
    /** 单链接待确认标记：true 时 [link] 归入待确认链接。 */
    val linkPending: Boolean? = null,
    /** 旧单包契约的已确认链接数组（旧镜像兼容，分页协议下不再下发）。 */
    val links: List<String>? = null,
    @SerializedName("pending_links") val pendingLinks: List<String>? = null,
    val pending: Boolean? = null,
    val missing: Boolean? = null,
    @SerializedName("added_at") val addedAt: Long? = null,
    /** 发行时间（库内原始值；排序/展示以此为准，站内快照日期仅作回退）。 */
    val date: String? = null,
    /** JavDB 原始分（2026-11 分页契约起；加权评分公式的输入）。 */
    val rawScore: Double? = null,
    /** 评分（旧契约 = 原始分；新契约 = 站点加权展示分，仅作 rawScore 缺省回退）。 */
    val score: Double? = null,
    /** 评分数（加权评分排序的先验强度，2026-11-13 扩展进契约）。 */
    val votes: Int? = null,
)

/**
 * 解析 `GET {base}/api/items` 响应为 [LtxLibrary]。
 *
 * 载荷非法或缺少 items 时返回 null（调用方据此判定不可缓存）。
 * entries 按入库时间倒序，保证默认展示顺序稳定。
 */
internal fun parseLtxLibrary(json: String): LtxLibrary? {
    val payload = GSON.fromJson<LtxItemsPayloadDto>(json) ?: return null
    return parseLtxItems(payload)
}

/** 载荷 DTO → 领域库；items 缺失返回 null（不可缓存）。 */
internal fun parseLtxItems(payload: LtxItemsPayloadDto): LtxLibrary? {
    val items = payload.items ?: return null
    val entries = items.asSequence()
        .filter { !it.code.isNullOrBlank() }
        .map { it.toEntry() }
        .sortedByDescending { it.addedAtMillis }
        .toList()
    return LtxLibrary(updatedAtMillis = payload.updatedAt ?: 0L, entries = entries)
}

/** 条目 DTO → 领域条目（分页单链接契约与旧单包数组契约双形态兼容）。 */
private fun LtxItemDto.toEntry(): LtxEntry {
    // 旧契约：links 数组为已确认集合；同一 URI 可能同时出现在 links 与
    // pending_links（确认过渡期），解析边界去重：已确认优先，pending 剔除重复项
    val confirmed = links.orEmpty().mapNotNull(::parseLtxLink).distinctBy { it.uri }.toMutableList()
    val confirmedUris = confirmed.asSequence().map { it.uri }.toSet()
    val pendingParsed = pendingLinks.orEmpty().mapNotNull(::parseLtxLink)
        .distinctBy { it.uri }
        .filter { it.uri !in confirmedUris }
        .toMutableList()
    // 新契约：单 link + linkPending（无链接数组）。链接待确认时归入 pendingLinks，
    // 与旧契约的「待确认链接」UI 语义对齐
    if (confirmed.isEmpty() && pendingParsed.isEmpty() && !link.isNullOrBlank()) {
        parseLtxLink(link)?.let { parsed ->
            if (linkPending == true) pendingParsed += parsed else confirmed += parsed
        }
    }
    return LtxEntry(
        code = code.orEmpty().trim(),
        links = confirmed,
        pendingLinks = pendingParsed,
        isPending = pending ?: false,
        isMissing = missing ?: false,
        addedAtMillis = addedAt ?: 0L,
        date = date?.trim()?.takeIf { it.isNotEmpty() },
        // 原始分为加权公式输入：分页契约取 rawScore，旧契约回退 score
        score = rawScore ?: score,
        votes = votes,
    )
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

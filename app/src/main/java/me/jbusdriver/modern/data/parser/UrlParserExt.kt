package me.jbusdriver.modern.data.parser

import me.jbusdriver.modern.domain.model.movieCodeFromUrl

fun String.wrapImage(baseUrl: String): String = when {
    isBlank() -> ""
    startsWith("http") -> this
    startsWith("//") -> "https:$this"
    else -> {
        // 与 resolveUrl 对齐：路径若不以 "/" 开头需补一个分隔符，
        // 否则 baseUrl + "ABCD-123" 会拼成非法的 "https://hostABCD-123"。
        val prefix = if (startsWith("/")) "" else "/"
        baseUrl.trimEnd('/') + prefix + this
    }
}

fun String.wrapForumImage(baseUrl: String): String = when {
    isBlank() -> ""
    startsWith("http") -> this
    startsWith("//") -> "https:$this"
    startsWith("/") -> baseUrl.trimEnd('/') + this
    else -> baseUrl.trimEnd('/') + "/forum/" + this
}

fun String.wrapForumLink(baseUrl: String): String = when {
    isBlank() -> ""
    startsWith("http") -> this
    startsWith("//") -> "https:$this"
    startsWith("/") -> baseUrl.trimEnd('/') + this
    startsWith("#") -> this
    else -> baseUrl.trimEnd('/') + "/forum/" + this
}

/**
 * 帖内楼层指路链接的目标；[threadId] 为 0 表示 URL 未携带帖子 id（无法判定跨帖）。
 */
data class FloorTarget(val threadId: Int, val postId: Int)

private val findPostPidRegex = Regex("pid=(\\d+)")
private val findPostPtidRegex = Regex("ptid=(\\d+)")
private val anchorPidRegex = Regex("#pid(\\d+)")
private val threadUrlTidRegex = Regex("thread-(\\d+)-")
private val queryTidRegex = Regex("[?&]tid=(\\d+)")

/**
 * 解析 Discuz 楼层指路链接，供帖子详情页做"定位到目标楼层"路由：
 * - 跳转形式：`forum.php?mod=redirect&goto=findpost&pid=<楼层id>&ptid=<帖子id>`
 * - 锚点形式：`thread-<tid>-<page>-1.html#pid<楼层id>` 或 `viewthread&tid=<tid>…#pid<楼层id>`
 *
 * 两种形式都不匹配时返回 null；跳转形式缺 ptid 时 [FloorTarget.threadId] 为 0。
 */
fun extractFloorTarget(url: String): FloorTarget? {
    if (url.isBlank()) return null
    if (url.contains("goto=findpost")) {
        val pid = findPostPidRegex.find(url)?.groupValues?.get(1)?.toIntOrNull() ?: return null
        if (pid <= 0) return null
        val tid = findPostPtidRegex.find(url)?.groupValues?.get(1)?.toIntOrNull() ?: 0
        return FloorTarget(threadId = tid, postId = pid)
    }
    val anchorPid = anchorPidRegex.find(url)?.groupValues?.get(1)?.toIntOrNull() ?: return null
    if (anchorPid <= 0) return null
    val tid = threadUrlTidRegex.find(url)?.groupValues?.get(1)?.toIntOrNull()
        ?: queryTidRegex.find(url)?.groupValues?.get(1)?.toIntOrNull()
        ?: 0
    return FloorTarget(threadId = tid, postId = anchorPid)
}

/** 帖子详情内点击链接的应用内路由目标。 */
sealed interface ForumLinkTarget {
    /**
     * 帖子链接；[postId] 非空表示带楼层锚点；[threadId] 为 0 表示 URL 未携带帖子 id
     * （按本帖处理，见 [extractFloorTarget]）。
     */
    data class Thread(val threadId: Int, val postId: Int?) : ForumLinkTarget

    /** 板块链接（`fid=` / `forum-<fid>-`）。 */
    data class Board(val forumId: Int) : ForumLinkTarget

    /** 图片直链（按扩展名判定，域无关），可直接进应用内查看器。 */
    data class Image(val url: String) : ForumLinkTarget

    /**
     * 影片详情链接（路径为 `/{番号}` 或 `/uncensored/{番号}`，如
     * `https://…/NHDT-876`，域无关）；[code] 为规范化番号，调用方按当前站点重建加载。
     */
    data class Movie(val code: String) : ForumLinkTarget

    /**
     * 站内列表页链接（`/{star|genre|director|studio|label|series|publisher}/<id>` 前缀，
     * 如 `https://…/star/sv3`，域无关）。[type] 为 RouteLinkMovies 的列表类型词汇
     * （见 [LIST_PAGE_PATH_TYPES]，star→actress、genre→genre，其余用原样路径段供
     * UI 匹配类别标签）；[censorType] 仅为 uncensored 前缀路径标注。调用方按 URL
     * 原域名加载（演员/导演 id 跨站不通）。
     */
    data class ListPage(
        val url: String,
        val type: String,
        val censorType: String? = null
    ) : ForumLinkTarget

    /** 无结构特征：交由浏览器/外部打开。 */
    data class Web(val url: String) : ForumLinkTarget
}

private val boardQueryFidRegex = Regex("[?&]fid=(\\d+)")
private val boardPathFidRegex = Regex("forum-(\\d+)-")

private val IMAGE_LINK_EXTENSIONS = listOf(".jpg", ".jpeg", ".png", ".gif", ".webp")

/**
 * 列表页路径前缀 → RouteLinkMovies 列表类型。star/genre 映射到既有词汇，
 * director/studio/label/series/publisher 用原样路径段（供 UI 层匹配类别标签），
 * 论坛路由、外部深链 resolveJavbusRoute、影片详情 header 点击共用本映射。
 */
internal val LIST_PAGE_PATH_TYPES = mapOf(
    "star" to "actress",
    "genre" to "genre",
    "director" to "director",
    "studio" to "studio",
    "label" to "label",
    "series" to "series",
    "publisher" to "publisher"
)

/** 无码内容结构前缀段；"uncensored" 同时决定 censorType，"xyz" 仅作前缀跳过。 */
private const val UNCENSORED_PREFIX_SEGMENT = "uncensored"

/**
 * 从链接路径识别站内列表页类型（actress/genre/director/studio/label/series/publisher），
 * 可选 uncensored/xyz 前缀；非列表页返回 null。供顶栏类别标签按链接归类。
 */
fun listPageTypeFromPath(url: String): String? {
    val segments = runCatching { java.net.URI(url).path.orEmpty() }
        .getOrElse { return null }
        .split('/')
        .filter { it.isNotBlank() }
    var typeIndex = 0
    when (segments.firstOrNull()) {
        UNCENSORED_PREFIX_SEGMENT, "xyz" -> typeIndex = 1
    }
    return LIST_PAGE_PATH_TYPES[segments.getOrNull(typeIndex)]
}

/**
 * 识别站内列表页链接（`/{star|genre|director|…}/<id>`，可选 uncensored/xyz 前缀），
 * 返回 [ForumLinkTarget.ListPage]；裸索引页（如 `/genre`）与其他路径返回 null。
 * 需在番号识别之前调用：番号型 URL 永不含这些结构前缀，先判列表页可避免
 * `/star/<番号形id>` 被误判为影片详情。
 */
private fun listPageTarget(url: String): ForumLinkTarget.ListPage? {
    val segments = runCatching { java.net.URI(url).path.orEmpty() }
        .getOrElse { return null }
        .split('/')
        .filter { it.isNotBlank() }
    var typeIndex = 0
    var censorType: String? = null
    when (segments.firstOrNull()) {
        UNCENSORED_PREFIX_SEGMENT -> {
            censorType = "UNCENSORED"
            typeIndex = 1
        }

        "xyz" -> typeIndex = 1
    }
    val type = LIST_PAGE_PATH_TYPES[segments.getOrNull(typeIndex)] ?: return null
    // 必须携带具体条目 id（/star/sv3）；裸索引页（/genre）不在站内路由范围
    if (segments.size <= typeIndex + 1) return null
    return ForumLinkTarget.ListPage(url, type, censorType)
}

/**
 * 按结构特征把链接解析为应用内路由目标，**不检查域名**：
 * 镜像站与主站路径同构，`thread-<tid>-…` / `tid=<tid>` / `fid=<fid>` / `/{番号}` 等结构
 * 本身就是站内凭证；提取 id/番号后由调用方用当前站点加载，任何未收录镜像都能正确路由。
 * 优先级：楼层指路 > 图片直链 > 站内列表页 > 影片详情 > 帖子 > 板块；
 * 全不命中时 [ForumLinkTarget.Web]。
 */
fun resolveForumLinkTarget(url: String): ForumLinkTarget {
    if (url.isBlank()) return ForumLinkTarget.Web(url)
    extractFloorTarget(url)?.let { return ForumLinkTarget.Thread(it.threadId, it.postId) }
    val path = url.substringBefore('#').substringBefore('?').lowercase()
    if (IMAGE_LINK_EXTENSIONS.any(path::endsWith)) return ForumLinkTarget.Image(url)
    listPageTarget(url)?.let { return it }
    movieCodeFromUrl(url)?.let { return ForumLinkTarget.Movie(it) }
    (queryTidRegex.find(url) ?: threadUrlTidRegex.find(url))?.let { match ->
        val tid = match.groupValues[1].toIntOrNull()
        if (tid != null && tid > 0) return ForumLinkTarget.Thread(tid, null)
    }
    (boardQueryFidRegex.find(url) ?: boardPathFidRegex.find(url))?.let { match ->
        val fid = match.groupValues[1].toIntOrNull()
        if (fid != null && fid > 0) return ForumLinkTarget.Board(fid)
    }
    return ForumLinkTarget.Web(url)
}

internal fun String.isGifUrl(): Boolean {
    val path = substringBefore("?").substringBefore("#").lowercase()
    return path.endsWith(".gif")
}

/**
 * 去掉 URL 的 scheme 与 host，只保留 path（含 query）。
 *
 * 同一内容在各个镜像站下的 URL 仅 host 不同，去掉 host 后即得到镜像无关的标识：
 * - 收藏入库时用它归一化存储（见 [me.jbusdriver.modern.data.db.LinkMappers]）；
 * - Coil 图片请求用它作为内存/磁盘缓存键（见 ui/components/AppAsyncImage.kt），
 *   避免切换镜像后缓存全部 miss 而重复下载。
 *
 * 非 http(s)/协议相对形式（相对路径、file:// 等）原样返回。
 */
fun String.stripToPath(): String = when {
    isBlank() -> ""
    startsWith("http") -> {
        val afterScheme = indexOf("://")
        if (afterScheme < 0) this
        else {
            val pathStart = indexOf('/', afterScheme + 3)
            if (pathStart < 0) "/" else substring(pathStart)
        }
    }

    startsWith("//") -> {
        val pathStart = indexOf('/', 2)
        if (pathStart < 0) "/" else substring(pathStart)
    }

    else -> this
}

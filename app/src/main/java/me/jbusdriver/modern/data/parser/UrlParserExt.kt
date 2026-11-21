package me.jbusdriver.modern.data.parser

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

    /** 无 Discuz 结构特征：交由浏览器/外部打开。 */
    data class Web(val url: String) : ForumLinkTarget
}

private val boardQueryFidRegex = Regex("[?&]fid=(\\d+)")
private val boardPathFidRegex = Regex("forum-(\\d+)-")

private val IMAGE_LINK_EXTENSIONS = listOf(".jpg", ".jpeg", ".png", ".gif", ".webp")

/**
 * 按结构特征把链接解析为应用内路由目标，**不检查域名**：
 * 镜像站与主站路径同构，`thread-<tid>-…` / `tid=<tid>` / `fid=<fid>` 等结构本身就是
 * 站内凭证；提取 id 后由调用方用当前站点加载，任何未收录镜像都能正确路由。
 * 优先级：楼层指路 > 图片直链 > 帖子 > 板块；全不命中时 [ForumLinkTarget.Web]。
 */
fun resolveForumLinkTarget(url: String): ForumLinkTarget {
    if (url.isBlank()) return ForumLinkTarget.Web(url)
    extractFloorTarget(url)?.let { return ForumLinkTarget.Thread(it.threadId, it.postId) }
    val path = url.substringBefore('#').substringBefore('?').lowercase()
    if (IMAGE_LINK_EXTENSIONS.any(path::endsWith)) return ForumLinkTarget.Image(url)
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

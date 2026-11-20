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

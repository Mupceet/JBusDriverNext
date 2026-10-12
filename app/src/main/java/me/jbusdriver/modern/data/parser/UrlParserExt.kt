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

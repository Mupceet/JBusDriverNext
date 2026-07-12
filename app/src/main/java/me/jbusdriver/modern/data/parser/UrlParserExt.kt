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

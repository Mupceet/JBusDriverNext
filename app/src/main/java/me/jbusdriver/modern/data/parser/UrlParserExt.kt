package me.jbusdriver.modern.data.parser

fun String.wrapImage(baseUrl: String): String = when {
    isBlank() -> ""
    startsWith("http") -> this
    startsWith("//") -> "https:$this"
    else -> baseUrl.trimEnd('/') + this
}

fun String.wrapForumImage(baseUrl: String): String = when {
    isBlank() -> ""
    startsWith("http") -> this
    startsWith("//") -> "https:$this"
    startsWith("/") -> baseUrl.trimEnd('/') + this
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

package me.jbusdriver.modern.ui.browser

import java.net.URI

/**
 * 内置浏览器对导航 URL 的分派决策与纯工具函数（无 Android 依赖，便于单测）。
 *
 * http(s) 与页面内部协议留在 WebView 内加载（return false，保留 POST 导航）；
 * 其余 scheme（magnet/ed2k/intent/mailto/tel 等）交给能处理的系统应用。
 */
sealed interface BrowserUrlDecision {
    /** 留在 WebView 内继续加载。 */
    data object LoadInWebView : BrowserUrlDecision

    /** 非网页 scheme：由调用方发起系统 ACTION_VIEW（无处理应用时 Toast 提示）。 */
    data object LaunchExternal : BrowserUrlDecision
}

object BrowserUrlPolicy {

    /** 页面内部协议也交给 WebView，避免 about:blank 之类的内部跳转被外抛。 */
    private val IN_APP_SCHEMES = setOf("http", "https", "about", "data", "blob")

    /** 解析失败（无 scheme/非法字符）按外抛处理，与磁力/ed2k 同一路径。 */
    fun decide(url: String?): BrowserUrlDecision {
        val scheme = url?.let { runCatching { URI(it).scheme }.getOrNull() }
            ?: return BrowserUrlDecision.LaunchExternal
        return if (scheme.lowercase() in IN_APP_SCHEMES) {
            BrowserUrlDecision.LoadInWebView
        } else {
            BrowserUrlDecision.LaunchExternal
        }
    }

    /** 初始标题兜底：取 host（去掉 www. 前缀），解析失败返回 null。 */
    fun hostOf(url: String): String? =
        runCatching { URI(url).host }.getOrNull()?.removePrefix("www.")

    /** 下载请求头三件套（Cookie/Referer/UA，缺省项过滤），否则多数站点会拒绝下载。 */
    fun downloadHeaders(cookie: String?, referer: String?, userAgent: String?): Map<String, String> =
        buildMap {
            cookie?.takeIf { it.isNotBlank() }?.let { put("Cookie", it) }
            referer?.takeIf { it.isNotBlank() }?.let { put("Referer", it) }
            userAgent?.takeIf { it.isNotBlank() }?.let { put("User-Agent", it) }
        }
}

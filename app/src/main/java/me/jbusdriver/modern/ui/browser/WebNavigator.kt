package me.jbusdriver.modern.ui.browser

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.platform.LocalUriHandler

/**
 * 打开网页的统一入口：由导航层提供（push [me.jbusdriver.modern.ui.RouteBrowser]）。
 * 未提供时（Preview 等场景）调用方回退系统浏览器。
 */
fun interface WebNavigator {
    fun open(url: String)
}

val LocalWebNavigator = staticCompositionLocalOf<WebNavigator?> { null }

/**
 * 当前环境可用的网页打开函数：内置浏览器优先，未提供时回退系统浏览器。
 * 替代散落各处的 `uriHandler.openUri(...)`，避免污染系统浏览历史。
 */
@Composable
fun rememberWebOpener(): (String) -> Unit {
    val navigator = LocalWebNavigator.current
    val uriHandler = LocalUriHandler.current
    return remember(navigator, uriHandler) {
        { url: String ->
            val nav = navigator
            if (nav != null) {
                nav.open(url)
            } else {
                runCatching { uriHandler.openUri(url) }
            }
        }
    }
}

package me.jbusdriver.modern.core.http

import android.webkit.CookieManager
import android.webkit.WebView
import androidx.core.net.toUri
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
import me.jbusdriver.modern.KLog
import me.jbusdriver.modern.core.http.WebViewHelper.currentWebSession
import me.jbusdriver.modern.core.http.WebViewHelper.fetchPageTextAwait
import me.jbusdriver.modern.core.http.WebViewHelper.loadUrlAwait
import javax.inject.Inject
import javax.inject.Singleton

private const val TAG = "LtxWebGate"

/**
 * WebView 过 LTX 人机验证后的会话凭证。
 *
 * Worker 端以 Cookie 识别已验证客户端，`cf_clearance` 类云端 Cookie 之外还有
 * 站点自种的标识 Cookie；验证与出口 IP 绑定，VPN 出口变化后重新
 * [LtxWebViewGateway.fetchViaWebView] 即可自愈。Cookie 与 UA 必须成对使用。
 */
data class LtxWebSession(
    val cookieHeader: String,
    val userAgent: String,
)

/** WebView 直接抓取的结果：响应原文 + 可供 OkHttp 路径复用的会话。 */
data class LtxWebFetch(
    val body: String,
    val session: LtxWebSession,
)

/**
 * LTX 站点的人机验证网关。
 *
 * LTX Worker 在应用层实现 Turnstile 门禁（403 + `reason=verify` + sitekey）：
 * 网页 SPA 在页面内完成 Turnstile 校验后换取放行凭证（Cookie），纯 OkHttp
 * 无法复制该流程。本网关用隐形 WebView 加载站点首页，让 SPA 自行完成验证，
 * 随后在页面内同步 XHR 直接抓取目标 JSON，并把 WebView 的 Cookie 导出供
 * OkHttp 快路径复用。
 *
 * 与主站 [BrowserSessionManager] 相互独立：LTX 是第三方站点，Cookie 只按 LTX
 * 域读写（系统 [CookieManager] 按 host 隔离）；且加载时不屏蔽任何资源——
 * Turnstile 与 SPA 脚本必须真实执行才能通过验证。
 */
interface LtxWebViewGateway {
    /**
     * 加载 [url] 所属站点，待人机验证通过后抓取 [url] 的响应原文。
     *
     * @return 失败（超时、验证未通过、页面错误）返回 null，调用方降级为普通失败。
     */
    suspend fun fetchViaWebView(url: String): LtxWebFetch?
}

@Singleton
class DefaultLtxWebViewGateway @Inject constructor(
    private val webViewFactory: WebViewFactory
) : LtxWebViewGateway {

    /** 同一时刻只允许一次抓取（单 WebView 一次只能加载一个页面）。 */
    private val mutex = Mutex()

    override suspend fun fetchViaWebView(url: String): LtxWebFetch? = mutex.withLock {
        withContext(Dispatchers.Main) {
            withTimeout(FETCH_TIMEOUT_MILLIS) { fetchInternal(url) }
        }
    }

    private suspend fun fetchInternal(url: String): LtxWebFetch? {
        val origin = url.toUri().let { uri ->
            val host = uri.host ?: return null
            "${uri.scheme ?: "https"}://$host"
        }
        val cookieManager = CookieManager.getInstance()
        val webView = webViewFactory.createWebView()
        try {
            KLog.t(TAG).i("WebView gateway: loading $origin for SPA human verification")
            // 首页是验证型 SPA：加载即自动执行 Turnstile 门禁；首次 finish 时多半仍在验证中
            val loadResult = runCatching { webView.loadUrlAwait(origin) }
            loadResult.exceptionOrNull()?.let {
                KLog.t(TAG).w("Gateway page load failed: ${it.message}")
            }
            // 门禁通过前后，页面内 XHR 会从 403(verify) 变为 200；轮询至成功或超时。
            // 自动模式只给短窗口：非交互门禁数秒内即过，交互式复选框由可见验证弹窗兜底
            val deadline = System.currentTimeMillis() + VERIFY_WAIT_MILLIS
            while (System.currentTimeMillis() < deadline) {
                val body = runCatching { webView.fetchPageTextAwait(url) }.getOrNull()
                if (body != null) {
                    val session = webView.currentWebSession(url)
                    if (session != null) {
                        cookieManager.flush()
                        KLog.t(TAG).i("WebView gateway: verified & fetched (len=${body.length})")
                        return LtxWebFetch(body, session)
                    }
                }
                delay(POLL_INTERVAL_MILLIS)
            }
            KLog.t(TAG).w("WebView gateway: auto verification not passed within ${VERIFY_WAIT_MILLIS / 1000}s")
            return null
        } finally {
            webView.stopLoading()
            webView.destroy()
        }
    }

    private companion object {
        const val FETCH_TIMEOUT_MILLIS = 30_000L
        const val VERIFY_WAIT_MILLIS = 8_000L
        const val POLL_INTERVAL_MILLIS = 2_000L
    }
}

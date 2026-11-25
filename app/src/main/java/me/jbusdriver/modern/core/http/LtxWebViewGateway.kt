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
import me.jbusdriver.modern.core.http.WebViewHelper.probeStatusAwait
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
 * WebView 在页面内完成加密会话握手后的产物（2026-11-15 迁移站协议）。
 *
 * 页面自身先过 Turnstile 门禁，注入脚本随后以 WebCrypto 完成
 * `POST /api/crypto/session`（ECDH P-256 + HKDF-SHA256 → AES-256-GCM 密钥），
 * 把 sid/密钥/过期时间交还宿主；同时导出 Cookie 与 UA 供 OkHttp 复用
 * （验证 Cookie 与 UA 绑定，后续请求必须同 UA）。
 */
data class LtxWebViewSession(
    val sid: String,
    /** AES-256 密钥的 base64url（无填充）编码；由仓库层包装为 SecretKey。 */
    val aesKeyBase64Url: String,
    val expiresAtMillis: Long,
    val webSession: LtxWebSession,
)

/**
 * LTX 站点的人机验证网关。
 *
 * - 旧协议（自定义镜像域）：[fetchViaWebView] 加载站点让 SPA 过验证后，
 *   页面内同步 XHR 直接抓取目标 JSON，并导出 Cookie 供 OkHttp 快路径复用。
 * - 新协议（迁移站加密会话）：[establishSessionViaWebView] 以「items 428 =
 *   门禁已过」为探测信号，随后在页面内完成会话握手，交出 sid+密钥。
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

    /**
     * 加载 [url] 所属站点，待门禁通过（探测目标 [url] 返回 428 cryptoSession）
     * 后在页面内完成加密会话握手。
     *
     * @return 失败（超时、验证未通过、握手失败）返回 null，调用方降级为普通失败。
     */
    suspend fun establishSessionViaWebView(url: String): LtxWebViewSession?
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

    override suspend fun establishSessionViaWebView(url: String): LtxWebViewSession? = mutex.withLock {
        withContext(Dispatchers.Main) {
            withTimeout(SESSION_TIMEOUT_MILLIS) { establishSessionInternal(url) }
        }
    }

    private suspend fun fetchInternal(url: String): LtxWebFetch? {
        val origin = url.originOrNull() ?: return null
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
                        CookieManager.getInstance().flush()
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

    /**
     * 新协议会话建立：探测 [url]（items）状态——428 = 门禁已过（边缘质询与
     * Turnstile Cookie 均就绪）；随后在页内推进加密握手（[LtxPageHandshake]，
     * 失败自动复位重试，等页内 SPA 补完 Turnstile 后自愈）并轮询收割。
     */
    private suspend fun establishSessionInternal(url: String): LtxWebViewSession? {
        val origin = url.originOrNull() ?: return null
        val webView = webViewFactory.createWebView()
        try {
            KLog.t(TAG).i("WebView gateway: loading $origin for crypto session handshake")
            val loadResult = runCatching { webView.loadUrlAwait(origin) }
            loadResult.exceptionOrNull()?.let {
                KLog.t(TAG).w("Gateway page load failed: ${it.message}")
            }
            val deadline = System.currentTimeMillis() + SESSION_WAIT_MILLIS
            var handshakeStarted = false
            while (System.currentTimeMillis() < deadline) {
                val probe = runCatching { webView.probeStatusAwait(url) }.getOrNull()
                if (probe != null && probe.status == STATUS_PRECONDITION_REQUIRED) {
                    if (!handshakeStarted) {
                        handshakeStarted = true
                        KLog.t(TAG).i("WebView gateway: gate passed (428), handshake started")
                    }
                    val handshake = runCatching { LtxPageHandshake.pump(webView) }.getOrNull()
                    if (handshake != null) {
                        val webSession = webView.currentWebSession(url)
                        if (webSession != null) {
                            CookieManager.getInstance().flush()
                            KLog.t(TAG).i("WebView gateway: session established (sid=${handshake.sid}, expires=${handshake.expiresAtMillis})")
                            return LtxWebViewSession(
                                sid = handshake.sid,
                                aesKeyBase64Url = handshake.aesKeyBase64Url,
                                expiresAtMillis = handshake.expiresAtMillis,
                                webSession = webSession,
                            )
                        }
                    }
                } else {
                    KLog.t(TAG).i("WebView gateway: gate not passed yet (probe=${probe?.status ?: "none"})")
                }
                delay(POLL_INTERVAL_MILLIS)
            }
            KLog.t(TAG).w("WebView gateway: session not established within ${SESSION_WAIT_MILLIS / 1000}s")
            return null
        } finally {
            webView.stopLoading()
            webView.destroy()
        }
    }

    private fun String.originOrNull(): String? = toUri().let { uri ->
        val host = uri.host ?: return null
        "${uri.scheme ?: "https"}://$host"
    }

    private companion object {
        const val FETCH_TIMEOUT_MILLIS = 30_000L
        const val VERIFY_WAIT_MILLIS = 8_000L
        const val SESSION_TIMEOUT_MILLIS = 40_000L

        // 自动通过窗口：非交互式托管质询通常数秒内自过；过不了的基本是交互式，
        // 应尽早升级到人工验证页（等待全烧在这个窗口里）。轮询 1s 保证门禁一过
        // 立刻被检测到，不额外增加延迟。
        const val SESSION_WAIT_MILLIS = 10_000L
        const val POLL_INTERVAL_MILLIS = 1_000L
        const val STATUS_PRECONDITION_REQUIRED = 428
    }
}

package me.jbusdriver.modern.core.http

import android.annotation.SuppressLint
import android.content.Context
import android.webkit.CookieManager
import android.webkit.WebResourceError
import android.webkit.WebResourceRequest
import android.webkit.WebView
import android.webkit.WebViewClient
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withTimeout
import org.json.JSONObject
import java.io.IOException
import java.net.URI
import kotlin.time.Duration.Companion.milliseconds

internal class PageLoadGuard(url: String) {
    private val expectedHost = url.hostOrNull()
    private var navigationStarted = false

    fun onPageStarted() {
        navigationStarted = true
    }

    fun shouldAcceptFinish(pageUrl: String?): Boolean {
        if (!navigationStarted) return false
        val finishedHost = pageUrl?.hostOrNull()
        return expectedHost == null || finishedHost == null || expectedHost == finishedHost
    }

    private fun String.hostOrNull(): String? = runCatching { URI(this).host }.getOrNull()
}

object WebViewHelper {

    @SuppressLint("SetJavaScriptEnabled")
    fun createWebView(context: Context): WebView {
        return WebView(context.applicationContext).apply {
            // Required for forum session bootstrap and mirror verification pages.
            settings.javaScriptEnabled = true
            settings.domStorageEnabled = true
        }.also {
            CookieManager.getInstance().setAcceptCookie(true)
            CookieManager.getInstance().setAcceptThirdPartyCookies(it, true)
        }
    }

    suspend fun WebView.loadUrlAwait(url: String, timeoutMs: Long = 20_000): String {
        return withTimeout(timeoutMs.milliseconds) {
            suspendCancellableCoroutine { cont ->
                val guard = PageLoadGuard(url)
                webViewClient = object : WebViewClient() {
                    override fun onPageStarted(
                        view: WebView?,
                        pageUrl: String?,
                        favicon: android.graphics.Bitmap?
                    ) {
                        guard.onPageStarted()
                    }

                    override fun onPageFinished(view: WebView?, pageUrl: String?) {
                        if (!guard.shouldAcceptFinish(pageUrl)) return
                        if (cont.isActive) {
                            webViewClient = WebViewClient()
                            cont.resume(pageUrl ?: url) { _, _, _ -> }
                        }
                    }

                    override fun onReceivedError(
                        view: WebView?,
                        request: WebResourceRequest?,
                        error: WebResourceError?
                    ) {
                        if (request?.isForMainFrame == true && cont.isActive) {
                            webViewClient = WebViewClient()
                            cont.resumeWith(
                                Result.failure(IOException("WebView error: ${error?.description}"))
                            )
                        }
                    }
                }
                cont.invokeOnCancellation {
                    post {
                        stopLoading()
                        webViewClient = WebViewClient()
                    }
                }
                loadUrl(url)
            }
        }
    }

    suspend fun WebView.evaluateJs(js: String): String? {
        return withTimeout(10_000.milliseconds) {
            suspendCancellableCoroutine { cont ->
                cont.invokeOnCancellation {
                    post {
                        stopLoading()
                    }
                }
                evaluateJavascript(js) { result ->
                    if (cont.isActive) {
                        cont.resume(result) { _, _, _ -> }
                    }
                }
            }
        }
    }

    fun unescapeJsString(s: String): String {
        if (s.length < 2 || s[0] != '"') return s
        val raw = s.substring(1, s.length - 1)
        val sb = StringBuilder(raw.length)
        var i = 0
        while (i < raw.length) {
            if (raw[i] == '\\' && i + 1 < raw.length) {
                when (raw[i + 1]) {
                    'n' -> {
                        sb.append('\n'); i += 2
                    }

                    'r' -> {
                        sb.append('\r'); i += 2
                    }

                    't' -> {
                        sb.append('\t'); i += 2
                    }

                    '"' -> {
                        sb.append('"'); i += 2
                    }

                    '\\' -> {
                        sb.append('\\'); i += 2
                    }

                    '/' -> {
                        sb.append('/'); i += 2
                    }

                    'u' -> {
                        if (i + 5 < raw.length) {
                            val hex = raw.substring(i + 2, i + 6)
                            try {
                                sb.append(hex.toInt(16).toChar())
                                i += 6
                            } catch (_: NumberFormatException) {
                                sb.append(raw[i]); i++
                            }
                        } else {
                            sb.append(raw[i]); i++
                        }
                    }

                    else -> {
                        sb.append(raw[i]); i++
                    }
                }
            } else {
                sb.append(raw[i]); i++
            }
        }
        return sb.toString()
    }

    /**
     * 页面内同步 XHR 抓取 [url] 响应原文：与页面自身的 fetch 同源同权（浏览器自动
     * 附加 Cookie 与 Sec-Fetch 特征），用于需要真实浏览器环境的人机验证场景。
     * 同步模式使脚本可在 evaluateJavascript 中直接返回结果（参考 fetchAjaxDocument）。
     * 仅在 HTTP 200 且响应非空时返回 body，其余情况返回 null。
     */
    suspend fun WebView.fetchPageTextAwait(url: String): String? {
        val script = """
            (function() {
              try {
                var xhr = new XMLHttpRequest();
                xhr.open('GET', ${JSONObject.quote(url)}, false);
                xhr.send(null);
                return JSON.stringify({
                  ok: true,
                  status: xhr.status,
                  text: xhr.responseText || ''
                });
              } catch (error) {
                return JSON.stringify({ ok: false, error: String(error) });
              }
            })()
        """.trimIndent()
        val payload = runCatching { evaluateJs(script) }.getOrNull() ?: return null
        val json = runCatching { JSONObject(unescapeJsString(payload)) }.getOrNull() ?: return null
        if (!json.optBoolean("ok") || json.optInt("status") != 200) return null
        val body = json.optString("text")
        return body.takeIf { it.isNotEmpty() }
    }

    /** 导出当前 WebView 的会话凭证（Cookie + UA 成对使用）；无 Cookie 时返回 null。 */
    fun WebView.currentWebSession(url: String): LtxWebSession? {
        val cookies = CookieManager.getInstance().getCookie(url) ?: return null
        if (cookies.isEmpty()) return null
        return LtxWebSession(cookieHeader = cookies, userAgent = settings.userAgentString)
    }
}

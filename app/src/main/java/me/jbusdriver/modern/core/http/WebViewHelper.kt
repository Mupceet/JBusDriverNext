package me.jbusdriver.modern.core.http

import android.webkit.CookieManager
import android.webkit.WebResourceError
import android.webkit.WebResourceRequest
import android.webkit.WebView
import android.webkit.WebViewClient
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.isActive
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withTimeout
import me.jbusdriver.modern.JBus
import me.jbusdriver.modern.KLog
import java.io.IOException

object WebViewHelper {

    fun createWebView(): WebView {
        return WebView(JBus).apply {
            settings.javaScriptEnabled = true
            settings.domStorageEnabled = true
        }.also {
            CookieManager.getInstance().setAcceptCookie(true)
            CookieManager.getInstance().setAcceptThirdPartyCookies(it, true)
        }
    }

    suspend fun WebView.loadUrlAwait(url: String, timeoutMs: Long = 20_000): String {
        return withTimeout(timeoutMs) {
            suspendCancellableCoroutine { cont ->
                webViewClient = object : WebViewClient() {
                    override fun onPageFinished(view: WebView?, pageUrl: String?) {
                        if (cont.isActive) {
                            cont.resume(pageUrl ?: url) { _, _, _ -> }
                        }
                    }

                    override fun onReceivedError(
                        view: WebView?,
                        request: WebResourceRequest?,
                        error: WebResourceError?
                    ) {
                        if (request?.isForMainFrame == true && cont.isActive) {
                            cont.resumeWith(
                                Result.failure(IOException("WebView error: ${error?.description}"))
                            )
                        }
                    }
                }
                loadUrl(url)
            }
        }
    }

    suspend fun WebView.evaluateJs(js: String): String? {
        return withTimeout(10_000) {
            suspendCancellableCoroutine { cont ->
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
                    'n' -> { sb.append('\n'); i += 2 }
                    'r' -> { sb.append('\r'); i += 2 }
                    't' -> { sb.append('\t'); i += 2 }
                    '"' -> { sb.append('"'); i += 2 }
                    '\\' -> { sb.append('\\'); i += 2 }
                    '/' -> { sb.append('/'); i += 2 }
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
                    else -> { sb.append(raw[i]); i++ }
                }
            } else {
                sb.append(raw[i]); i++
            }
        }
        return sb.toString()
    }
}

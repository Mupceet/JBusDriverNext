package me.jbusdriver.modern.data

import android.app.Activity
import android.view.ViewGroup
import android.webkit.CookieManager
import android.webkit.WebResourceError
import android.webkit.WebResourceRequest
import android.webkit.WebView
import android.webkit.WebViewClient
import android.widget.FrameLayout
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.isActive
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
import me.jbusdriver.modern.KLog
import me.jbusdriver.modern.core.http.NetClient
import org.jsoup.Jsoup
import org.jsoup.nodes.Document
import java.io.IOException
import java.util.concurrent.atomic.AtomicBoolean
import javax.inject.Inject
import javax.inject.Singleton

private const val TAG = "ForumSession"

/**
 * WebView-based forum page fetcher.
 *
 * The site uses a quiz-based age verification that can only be passed
 * by a real browser engine. This class keeps a hidden WebView alive
 * and uses it to fetch all forum pages, extracting HTML for Jsoup parsing.
 *
 * Lifecycle: WebView is created on first use and kept alive until
 * [destroy] is called (typically when the user leaves the forum tab).
 */
@Singleton
class ForumSessionManager @Inject constructor() {

    @Volatile
    private var webView: WebView? = null
    private val initialized = AtomicBoolean(false)
    private val mutex = Mutex()

    fun isInitialized(): Boolean = initialized.get()

    /**
     * Initialize the hidden WebView and warm up the session.
     * Must be called before [fetchDocument].
     */
    suspend fun ensureSession(activity: Activity) {
        if (initialized.get()) return
        mutex.withLock {
            if (initialized.get()) return
            initWebView(activity)
        }
    }

    private suspend fun initWebView(activity: Activity) {
        withTimeout(15_000) {
            withContext(Dispatchers.Main) {
                val wv = WebView(activity).apply {
                    settings.javaScriptEnabled = true
                    settings.domStorageEnabled = true
                    visibility = android.view.View.INVISIBLE
                }
                CookieManager.getInstance().setAcceptCookie(true)
                CookieManager.getInstance().setAcceptThirdPartyCookies(wv, true)

                val rootView = activity.findViewById<ViewGroup>(android.R.id.content)
                rootView.addView(wv, FrameLayout.LayoutParams(1, 1))

                webView = wv

                // Load main site to establish session
                val mainUrl = "${NetClient.defaultFastUrl}/"
                KLog.d("[Forum] Loading main site: $mainUrl", TAG)
                loadPageUrl(wv, mainUrl)

                initialized.set(true)
                KLog.d("[Forum] WebView session initialized", TAG)
            }
        }
    }

    /**
     * Fetch a forum page URL and return the parsed Jsoup Document.
     * The page is loaded in the WebView, HTML is extracted via JS,
     * then parsed with Jsoup.
     */
    suspend fun fetchDocument(url: String): Document {
        val wv = webView ?: throw IllegalStateException("Forum WebView not initialized. Call ensureSession first.")
        val html = withContext(Dispatchers.Main) {
            withTimeout(20_000) {
                loadPageHtml(wv, url)
            }
        }
        return Jsoup.parse(html)
    }

    /**
     * Destroy the WebView and release resources.
     * Call when the user leaves the forum feature.
     */
    fun destroy() {
        val wv = webView
        if (wv != null) {
            KLog.d("[Forum] Destroying WebView", TAG)
            wv.stopLoading()
            (wv.parent as? ViewGroup)?.removeView(wv)
            wv.destroy()
            webView = null
        }
        initialized.set(false)
    }

    /**
     * Load a URL in the WebView, wait for onPageFinished,
     * then extract the full HTML via evaluateJavascript.
     */
    private suspend fun loadPageHtml(webView: WebView, url: String): String {
        // First wait for page to load
        val pageUrl = loadPageUrl(webView, url)
        KLog.d("[Forum] Page loaded: $pageUrl (requested: $url)", TAG)

        // Then extract HTML
        return suspendCancellableCoroutine { cont ->
            webView.evaluateJavascript("document.documentElement.outerHTML") { result ->
                if (cont.isActive) {
                    if (result != null) {
                        // evaluateJavascript returns JSON-encoded string
                        val html = unescapeJsonString(result)
                        KLog.d("[Forum] HTML extracted: ${html.length} chars", TAG)
                        cont.resume(html) {}
                    } else {
                        cont.resumeWith(Result.failure(IOException("Failed to extract HTML from $url")))
                    }
                }
            }
        }
    }

    /**
     * Load a URL and wait for onPageFinished. Returns the final URL.
     */
    private suspend fun loadPageUrl(webView: WebView, url: String): String {
        return suspendCancellableCoroutine { cont ->
            webView.webViewClient = object : WebViewClient() {
                override fun onPageFinished(view: WebView?, pageUrl: String?) {
                    if (cont.isActive) {
                        cont.resume(pageUrl ?: url) {}
                    }
                }

                override fun onReceivedError(
                    view: WebView?,
                    request: WebResourceRequest?,
                    error: WebResourceError?
                ) {
                    if (request?.isForMainFrame == true && cont.isActive) {
                        KLog.e("[Forum] WebView error: ${error?.description}", TAG)
                        cont.resumeWith(
                            Result.failure(
                                IOException("WebView error loading $url: ${error?.description}")
                            )
                        )
                    }
                }
            }
            webView.loadUrl(url)
        }
    }
}

/**
 * Unescape a JSON-encoded string returned by evaluateJavascript.
 * Removes surrounding quotes and converts escape sequences.
 */
private fun unescapeJsonString(s: String): String {
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

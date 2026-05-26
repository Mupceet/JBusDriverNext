package me.jbusdriver.modern.data

import android.app.Activity
import android.webkit.WebResourceRequest
import android.webkit.WebResourceResponse
import android.webkit.WebView
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.io.ByteArrayInputStream
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
import me.jbusdriver.modern.KLog
import me.jbusdriver.modern.core.http.WebViewHelper
import me.jbusdriver.modern.core.http.WebViewHelper.evaluateJs
import me.jbusdriver.modern.core.http.WebViewHelper.loadUrlAwait
import me.jbusdriver.modern.core.http.WebViewHelper.unescapeJsString
import me.jbusdriver.modern.core.site.SiteConfig
import org.jsoup.Jsoup
import org.jsoup.nodes.Document
import java.io.IOException
import java.util.concurrent.atomic.AtomicBoolean
import javax.inject.Inject
import javax.inject.Singleton

private const val TAG = "ForumSession"

private val BLOCKED_EXTENSIONS = setOf(
    "css", "js",
    "woff", "woff2", "ttf", "eot", "otf",
    "jpg", "jpeg", "png", "gif", "webp", "svg", "ico"
)

private val IMAGE_EXTENSIONS = setOf("jpg", "jpeg", "png", "gif", "webp", "svg", "ico")

private val TRANSPARENT_PNG = byteArrayOf(
    0x89.toByte(), 0x50, 0x4E, 0x47, 0x0D, 0x0A, 0x1A, 0x0A,
    0x00, 0x00, 0x00, 0x0D, 0x49, 0x48, 0x44, 0x52,
    0x00, 0x00, 0x00, 0x01, 0x00, 0x00, 0x00, 0x01,
    0x08, 0x06, 0x00, 0x00, 0x00, 0x1F, 0x15.toByte(), 0xC4.toByte(),
    0x89.toByte(), 0x00, 0x00, 0x00, 0x0A, 0x49, 0x44, 0x41,
    0x54, 0x08, 0xD7.toByte(), 0x63, 0x00, 0x01,
    0x00, 0x00, 0x00, 0x02, 0x00, 0x01, 0xE2.toByte(), 0x21, 0xBC.toByte(),
    0x33, 0x00, 0x00, 0x00, 0x00, 0x49, 0x45, 0x4E,
    0x44, 0xAE.toByte(), 0x42, 0x60, 0x82.toByte()
)

private fun isBlockedResource(url: String): Boolean {
    val path = url.substringBefore("?").substringBefore("#").lowercase()
    val ext = path.substringAfterLast('.', "")
    return ext in BLOCKED_EXTENSIONS
}

private fun isImageResource(url: String): Boolean {
    val path = url.substringBefore("?").substringBefore("#").lowercase()
    val ext = path.substringAfterLast('.', "")
    return ext in IMAGE_EXTENSIONS
}

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
class ForumSessionManager @Inject constructor(
    private val siteConfig: SiteConfig
) {

    @Volatile
    private var webView: WebView? = null
    private val initialized = AtomicBoolean(false)
    private val mutex = Mutex()
    private val cookieStore = SessionCookieStore()

    fun isInitialized(): Boolean = initialized.get()

    suspend fun ensureSession(activity: Activity) {
        if (initialized.get()) return
        mutex.withLock {
            if (initialized.get()) return

            val url = siteConfig.referer()
            if (cookieStore.isSessionValid(url)) {
                cookieStore.restoreCookies(url)
                initialized.set(true)
                KLog.d("[Forum] Session restored from persisted cookies", TAG)
                return
            }

            initWebView(activity)
        }
    }

    private suspend fun initWebView(activity: Activity) {
        withTimeout(15_000) {
            withContext(Dispatchers.Main) {
                val wv = WebViewHelper.createWebView()
                webView = wv
                try {
                    val mainUrl = siteConfig.referer()
                    KLog.d("[Forum] Loading main site: $mainUrl", TAG)
                    loadPageWithBlockedResources(wv, mainUrl)

                    delay(1000)

                    cookieStore.saveCookies(mainUrl)
                    initialized.set(true)
                    KLog.d("[Forum] WebView session initialized", TAG)
                } catch (e: Exception) {
                    KLog.e("[Forum] initWebView failed: ${e.message}", TAG)
                    wv.destroy()
                    webView = null
                    throw e
                }
            }
        }
    }

    suspend fun fetchDocument(url: String): Document {
        val wv = webView ?: throw IllegalStateException("Forum WebView not initialized. Call ensureSession first.")
        val html = withContext(Dispatchers.Main) {
            withTimeout(20_000) {
                loadPageWithBlockedResources(wv, url)
                KLog.d("[Forum] Page loaded: $url", TAG)
                val raw = wv.evaluateJs("document.documentElement.outerHTML")
                    ?: throw IOException("Failed to extract HTML from $url")
                val html = unescapeJsString(raw)
                KLog.d("[Forum] HTML extracted: ${html.length} chars", TAG)
                html
            }
        }
        return Jsoup.parse(html, url)
    }

    fun persistCookies() {
        cookieStore.saveCookies(siteConfig.referer())
    }

    fun destroy() {
        val wv = webView
        if (wv != null) {
            KLog.d("[Forum] Destroying WebView", TAG)
            wv.stopLoading()
            wv.destroy()
            webView = null
        }
        initialized.set(false)
    }

    /**
     * Load a URL with resource blocking (CSS/JS/images) for faster forum page loading.
     */
    private suspend fun loadPageWithBlockedResources(webView: WebView, url: String): String {
        return withTimeout(20_000) {
            suspendCancellableCoroutine { cont ->
                webView.webViewClient = object : android.webkit.WebViewClient() {
                    override fun shouldInterceptRequest(
                        view: WebView?,
                        request: WebResourceRequest?
                    ): WebResourceResponse? {
                        if (request != null && !request.isForMainFrame) {
                            val reqUrl = request.url.toString()
                            if (isBlockedResource(reqUrl)) {
                                return if (isImageResource(reqUrl)) {
                                    WebResourceResponse("image/png", "utf-8", ByteArrayInputStream(TRANSPARENT_PNG))
                                } else {
                                    WebResourceResponse("text/plain", "utf-8", null)
                                }
                            }
                        }
                        return super.shouldInterceptRequest(view, request)
                    }

                    override fun onPageFinished(view: WebView?, pageUrl: String?) {
                        if (cont.isActive) {
                            cont.resume(pageUrl ?: url) { _, _, _ -> }
                        }
                    }

                    override fun onReceivedError(
                        view: WebView?,
                        request: WebResourceRequest?,
                        error: android.webkit.WebResourceError?
                    ) {
                        if (request?.isForMainFrame == true && cont.isActive) {
                            cont.resumeWith(Result.failure(IOException("WebView error: ${error?.description}")))
                        }
                    }
                }
                webView.loadUrl(url)
            }
        }
    }
}

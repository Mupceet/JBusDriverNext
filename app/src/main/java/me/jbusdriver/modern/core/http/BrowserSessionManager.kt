package me.jbusdriver.modern.core.http

import android.graphics.Bitmap
import android.webkit.WebResourceRequest
import android.webkit.WebResourceResponse
import android.webkit.WebView
import androidx.core.net.toUri
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
import me.jbusdriver.modern.KLog
import me.jbusdriver.modern.core.http.WebViewHelper.evaluateJs
import me.jbusdriver.modern.core.http.WebViewHelper.unescapeJsString
import me.jbusdriver.modern.core.site.SiteConfig
import org.jsoup.Jsoup
import org.jsoup.nodes.Document
import org.json.JSONObject
import java.io.ByteArrayInputStream
import java.io.IOException
import java.util.concurrent.atomic.AtomicBoolean
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.time.Duration.Companion.milliseconds

private const val TAG = "BrowserSession"

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
 * Persists the browser session's cookies (e.g. after a forum page fetch captures Discuz!
 * session cookies). Implemented by [BrowserSessionManager].
 */
interface BrowserCookiePersister {
    suspend fun persistCookies()
}

/**
 * WebView-backed implementation of [BrowserSessionClient].
 *
 * The site gates HTML pages behind a `/doc/driver-verify` interstitial that only a real
 * browser engine can pass. This class keeps a hidden WebView alive that has passed the
 * gate, and uses it to fetch any page or ajax fragment the OkHttp client cannot reach on
 * its own — used by both the movie/list/detail pipeline and the forum pipeline.
 *
 * Lifecycle: the WebView is created on first use (cold start) and kept alive until
 * [destroy] is called. All navigations are serialized on [mutex] because the single shared
 * WebView can only load one URL at a time.
 */
@Singleton
class BrowserSessionManager @Inject constructor(
    private val siteConfig: SiteConfig,
    private val cookieStore: SessionCookieStore,
    private val webViewFactory: WebViewFactory
) : BrowserSessionClient, BrowserCookiePersister {

    @Volatile
    private var webView: WebView? = null
    private val initialized = AtomicBoolean(false)
    private val mutex = Mutex()

    // Optimization: the main-site page loaded during cold-start warm-up is the same page
    // the home screen will request next. Cache its HTML so that first fetch reuses it
    // instead of navigating the WebView a second time.
    @Volatile
    private var primedMainUrl: String? = null
    @Volatile
    private var primedMainHtml: String? = null

    override suspend fun warmUp() = ensureSession()

    private suspend fun ensureSession() {
        if (initialized.get()) return
        mutex.withLock {
            if (initialized.get()) return

            val url = siteConfig.referer()
            if (cookieStore.isSessionValid(url)) {
                cookieStore.restoreCookies(url)
                ensureWebViewCreated()
                initialized.set(true)
                KLog.d("[Browser] Session restored from persisted cookies", TAG)
                return
            }

            initWebView()
        }
    }

    private suspend fun ensureWebViewCreated() {
        if (webView != null) return
        withContext(Dispatchers.Main) {
            if (webView == null) {
                webView = webViewFactory.createWebView()
            }
        }
    }

    private suspend fun initWebView() {
        withTimeout(15_000.milliseconds) {
            withContext(Dispatchers.Main) {
                val wv = webViewFactory.createWebView()
                webView = wv
                try {
                    val mainUrl = siteConfig.referer()
                    KLog.d("[Browser] Loading main site: $mainUrl", TAG)
                    loadPageWithBlockedResources(wv, mainUrl)
                    capturePrimedHtml(wv, mainUrl)
                    cookieStore.saveCookies(mainUrl)
                    initialized.set(true)
                    KLog.d("[Browser] WebView session initialized", TAG)
                } catch (e: Exception) {
                    KLog.e("[Browser] initWebView failed: ${e.message}", TAG)
                    wv.destroy()
                    webView = null
                    throw e
                }
            }
        }
    }

    override suspend fun fetchDocument(url: String): Document {
        ensureSession()
        // Reuse the HTML captured during warm-up if it matches this URL (avoids a duplicate
        // navigation of the home page right after cold start).
        consumePrimed(url)?.let {
            KLog.i("WebView url=$url primed=reused", "FetchTiming")
            return it
        }
        // Serialize all navigations on the single shared WebView. Without this, two
        // concurrent fetches (e.g. a duplicate load + a cache retry) each install their
        // own WebViewClient and clobber each other's callbacks, so one fetch resumes on
        // the other's page or never resumes at all.
        val tStart = System.nanoTime()
        val html = mutex.withLock {
            val lockWaitMs = (System.nanoTime() - tStart) / 1_000_000
            val wv = webView
                ?: throw IllegalStateException("Browser WebView not initialized")
            withContext(Dispatchers.Main) {
                withTimeout(20_000.milliseconds) {
                    val tLoad = System.nanoTime()
                    loadPageWithBlockedResources(wv, url)
                    val loadMs = (System.nanoTime() - tLoad) / 1_000_000
                    KLog.d("[Browser] Page loaded: $url", TAG)
                    val tExtract = System.nanoTime()
                    val raw = wv.evaluateJs("document.documentElement.outerHTML")
                        ?: throw IOException("Failed to extract HTML from $url")
                    val html = unescapeJsString(raw)
                    val extractMs = (System.nanoTime() - tExtract) / 1_000_000
                    KLog.i(
                        "WebView url=$url lockWait=${lockWaitMs}ms load=${loadMs}ms extract=${extractMs}ms len=${html.length}",
                        "FetchTiming"
                    )
                    html
                }
            }
        }
        return Jsoup.parse(html, url)
    }

    override suspend fun fetchAjaxDocument(url: String, referer: String): Document {
        ensureSession()
        val html = mutex.withLock {
            val wv = webView
                ?: throw IllegalStateException("Browser WebView not initialized")
            withContext(Dispatchers.Main) {
                withTimeout(20_000.milliseconds) {
                    KLog.d("[Browser] Ajax fetch start: url=$url, referer=$referer, currentUrl=${wv.url}", TAG)
                    ensureSameOriginPage(wv, referer)
                    val raw = wv.evaluateJs(buildFetchHtmlScript(url))
                        ?: throw IOException("Failed to extract ajax HTML from $url")
                    val payload = unescapeJsString(raw)
                    val json = JSONObject(payload)
                    if (!json.optBoolean("ok")) {
                        KLog.e("[Browser] Ajax fetch failed in JS: ${json.optString("error")}", TAG)
                        throw IOException("Ajax fetch failed: ${json.optString("error")}")
                    }
                    val html = json.optString("text")
                    KLog.d(
                        "[Browser] Ajax fetch done: status=${json.optInt("status")}, " +
                                "contentType=${json.optString("contentType")}, " +
                                "finalUrl=${json.optString("url")}, " +
                                "length=${html.length}",
                        TAG
                    )
                    html
                }
            }
        }
        return Jsoup.parse(html, url)
    }

    private suspend fun ensureSameOriginPage(webView: WebView, referer: String) {
        val expectedHost = runCatching { referer.toUri().host }.getOrNull()
        val currentHost = webView.url?.let { runCatching { it.toUri().host }.getOrNull() }
        if (expectedHost != null && currentHost == expectedHost) {
            KLog.d("[Browser] Ajax same-origin page ready: currentUrl=${webView.url}", TAG)
            return
        }
        KLog.d(
            "[Browser] Ajax same-origin page missing: currentUrl=${webView.url}, loading referer=$referer",
            TAG
        )
        loadPageWithBlockedResources(webView, referer)
        KLog.d("[Browser] Ajax same-origin page loaded: currentUrl=${webView.url}", TAG)
    }

    private fun buildFetchHtmlScript(url: String): String {
        return """
            (function() {
              try {
                var xhr = new XMLHttpRequest();
                xhr.open('GET', ${JSONObject.quote(url)}, false);
                xhr.setRequestHeader('X-Requested-With', 'XMLHttpRequest');
                xhr.setRequestHeader('Accept', '*/*');
                xhr.send(null);
                return JSON.stringify({
                  ok: true,
                  status: xhr.status,
                  contentType: xhr.getResponseHeader('content-type') || '',
                  url: xhr.responseURL || ${JSONObject.quote(url)},
                  text: xhr.responseText || ''
                });
              } catch (error) {
                return JSON.stringify({
                  ok: false,
                  error: String(error)
                });
              }
            })()
        """.trimIndent()
    }

    private suspend fun capturePrimedHtml(wv: WebView, url: String) {
        runCatching {
            val raw = wv.evaluateJs("document.documentElement.outerHTML") ?: return
            val html = unescapeJsString(raw)
            if (html.isNotBlank()) {
                primedMainHtml = html
                primedMainUrl = url
                KLog.d("[Browser] Primed home HTML: ${html.length} chars for $url", TAG)
            }
        }.onFailure { KLog.w("[Browser] Primed capture failed: ${it.message}", TAG) }
    }

    private fun consumePrimed(url: String): Document? {
        val primedUrl = primedMainUrl ?: return null
        if (!url.trimEnd('/').equals(primedUrl.trimEnd('/'), ignoreCase = true)) return null
        val html = primedMainHtml ?: return null
        primedMainHtml = null
        primedMainUrl = null
        return Jsoup.parse(html, url)
    }

    override suspend fun persistCookies() {
        cookieStore.saveCookies(siteConfig.referer())
    }

    override suspend fun destroy() {
        mutex.withLock {
            withContext(Dispatchers.Main.immediate) {
                val wv = webView
                if (wv != null) {
                    KLog.d("[Browser] Destroying WebView", TAG)
                    wv.stopLoading()
                    wv.destroy()
                    webView = null
                }
                initialized.set(false)
                primedMainHtml = null
                primedMainUrl = null
            }
        }
    }

    /**
     * Load a URL with resource blocking (CSS/JS/images) for faster page loading.
     */
    private suspend fun loadPageWithBlockedResources(webView: WebView, url: String): String {
        val expectedHost = runCatching { url.toUri().host }.getOrNull()
        return withTimeout(20_000.milliseconds) {
            suspendCancellableCoroutine { cont ->
                // A freshly installed WebViewClient can receive onPageFinished for a
                // navigation it never started — e.g. a late hop of the previous page's
                // redirect chain (the main-site load performed during session init).
                // Resuming on that stale event captures the wrong DOM. Gate the resume
                // on having seen onPageStarted for *our* loadUrl so a stale finish from
                // an already-settled page (navigationStarted still false) is ignored.
                // All WebView callbacks run on the main thread, so a plain var is safe.
                var navigationStarted = false
                webView.webViewClient = object : android.webkit.WebViewClient() {
                    override fun onPageStarted(view: WebView?, pageUrl: String?, favicon: Bitmap?) {
                        navigationStarted = true
                    }

                    override fun shouldInterceptRequest(
                        view: WebView?,
                        request: WebResourceRequest?
                    ): WebResourceResponse? {
                        if (request != null && !request.isForMainFrame) {
                            val reqUrl = request.url.toString()
                            if (isBlockedResource(reqUrl)) {
                                return if (isImageResource(reqUrl)) {
                                    WebResourceResponse(
                                        "image/png",
                                        "utf-8",
                                        ByteArrayInputStream(TRANSPARENT_PNG)
                                    )
                                } else {
                                    WebResourceResponse("text/plain", "utf-8", null)
                                }
                            }
                        }
                        return super.shouldInterceptRequest(view, request)
                    }

                    override fun onPageFinished(view: WebView?, pageUrl: String?) {
                        if (!cont.isActive) return
                        // Ignore finishes that belong to a prior, stale navigation.
                        if (!navigationStarted) return
                        // Sanity check: a cross-host interstitial (e.g. an age gate on a
                        // different domain) is not our page. Same-host redirects still pass.
                        val finishHost = pageUrl?.let { runCatching { it.toUri().host }.getOrNull() }
                        if (expectedHost != null && finishHost != null && finishHost != expectedHost) return
                        webView.webViewClient = android.webkit.WebViewClient()
                        cont.resume(pageUrl ?: url) { _, _, _ -> }
                    }

                    override fun onReceivedError(
                        view: WebView?,
                        request: WebResourceRequest?,
                        error: android.webkit.WebResourceError?
                    ) {
                        if (request?.isForMainFrame == true && cont.isActive) {
                            webView.webViewClient = android.webkit.WebViewClient()
                            cont.resumeWith(Result.failure(IOException("WebView error: ${error?.description}")))
                        }
                    }
                }
                cont.invokeOnCancellation {
                    webView.post {
                        webView.stopLoading()
                        webView.webViewClient = android.webkit.WebViewClient()
                    }
                }
                webView.loadUrl(url)
            }
        }
    }
}

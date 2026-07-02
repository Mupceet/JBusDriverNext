package me.jbusdriver.modern.core.http

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import me.jbusdriver.BuildConfig
import me.jbusdriver.modern.KLog
import okhttp3.OkHttpClient
import org.jsoup.Jsoup
import org.jsoup.nodes.Document
import javax.inject.Inject
import javax.inject.Singleton

interface HtmlClient {
    val imageOkHttpClient: OkHttpClient

    suspend fun fetchHtml(url: String, showAll: Boolean = false, referer: String? = null): String

    suspend fun fetchDocument(url: String, showAll: Boolean = false): Document
}

/**
 * 判断响应是否命中"driver-verify"拦截页（URL 或正文包含特征串）。
 *
 * 抽成纯函数以便单元测试：[DefaultHtmlClient] 的重试/回退策略完全依赖该判定。
 */
internal fun isDriverVerifyPage(finalUrl: String, body: String): Boolean =
    finalUrl.contains("/doc/driver-verify", ignoreCase = true) ||
            body.contains("/doc/driver-verify", ignoreCase = true) ||
            body.contains("driver-verify", ignoreCase = true)

@Singleton
class DefaultHtmlClient @Inject constructor(
    private val browserSessionClient: BrowserSessionClient
) : HtmlClient {
    override val imageOkHttpClient: OkHttpClient
        get() = NetClient.glideOkHttpClient

    override suspend fun fetchHtml(url: String, showAll: Boolean, referer: String?): String {
        // fetchHtml serves ajax endpoints (e.g. the magnet list) which are NOT behind the
        // driver-verify gate, so OkHttp fetches them directly even without bus_auth. (A
        // WebView navigation of an ajax URL returns a truncated body, so keep these on OkHttp.)
        return fetchViaOkHttpWithFallback(url, showAll, referer).body
    }

    override suspend fun fetchDocument(url: String, showAll: Boolean): Document {
        val response = fetchPage(url, showAll)
        return withContext(Dispatchers.Default) { Jsoup.parse(response.body, response.finalUrl) }
    }

    /**
     * Page fetch strategy.
     *
     * Default (no built-in bus_auth): the site's /doc/driver-verify gate only opens for a real
     * browser engine, so OkHttp (a non-browser) cannot fetch pages directly — route through the
     * shared WebView session instead, exactly like the forum. Images still load via OkHttp/Coil
     * because they don't require bus_auth.
     *
     * Optional fast-path: when a user-supplied bus_auth is configured (JAVBUS_AUTH_COOKIE),
     * fetch via OkHttp, falling back to the WebView session only if the token is rejected.
     */
    private suspend fun fetchPage(url: String, showAll: Boolean): NetClient.HtmlResponse {
        if (BuildConfig.JAVBUS_AUTH_COOKIE.isNotBlank()) {
            return fetchViaOkHttpWithFallback(url, showAll, referer = null)
        }
        val tStart = System.nanoTime()
        // Mirror the OkHttp interceptor via the shared existMagCookieValue(): write existmag on
        // EVERY fetch (mag/all) so toggling 全部/仅磁力 takes effect immediately. The WebView
        // session retains cookies for the process lifetime, so a stale "all" would otherwise
        // persist after switching back to 仅磁力 until the app restarts.
        android.webkit.CookieManager.getInstance()
            .setCookie(url, "$EXIST_MAG_COOKIE=${existMagCookieValue(showAll)}; path=/")
        val doc = browserSessionClient.fetchDocument(url)
        KLog.i(
            "page[WebView] url=$url took=${(System.nanoTime() - tStart) / 1_000_000}ms len=${doc.html().length}",
            "FetchTiming"
        )
        return NetClient.HtmlResponse(doc.location(), doc.html())
    }

    private suspend fun fetchViaOkHttpWithFallback(
        url: String,
        showAll: Boolean,
        referer: String?
    ): NetClient.HtmlResponse {
        val tStart = System.nanoTime()
        val first = NetClient.fetchHtmlResponse(url, showAll, referer)
        if (!first.isDriverVerify()) {
            KLog.i(
                "ajax[OkHttp-direct] url=$url took=${(System.nanoTime() - tStart) / 1_000_000}ms len=${first.body.length}",
                "FetchTiming"
            )
            return first
        }

        KLog.w("HtmlClient hit driver verification for $url; warming browser session")
        browserSessionClient.warmUp()
        val retry = NetClient.fetchHtmlResponse(url, showAll, referer)
        if (!retry.isDriverVerify()) {
            KLog.i(
                "ajax[OkHttp-retry] url=$url took=${(System.nanoTime() - tStart) / 1_000_000}ms len=${retry.body.length}",
                "FetchTiming"
            )
            return retry
        }

        KLog.w("HtmlClient retry still hit verification for $url; falling back to browser fetch")
        val doc = browserSessionClient.fetchDocument(url)
        KLog.i(
            "ajax[WebView-fallback] url=$url took=${(System.nanoTime() - tStart) / 1_000_000}ms len=${doc.html().length}",
            "FetchTiming"
        )
        return NetClient.HtmlResponse(doc.location(), doc.html())
    }

    private fun NetClient.HtmlResponse.isDriverVerify(): Boolean =
        isDriverVerifyPage(finalUrl, body)
}

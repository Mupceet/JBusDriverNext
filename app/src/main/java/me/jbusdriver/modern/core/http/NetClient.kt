package me.jbusdriver.modern.core.http

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import me.jbusdriver.BuildConfig
import me.jbusdriver.modern.KLog
import me.jbusdriver.modern.core.http.NetClient.fetchDocument
import okhttp3.Call
import okhttp3.Callback
import okhttp3.Interceptor
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import okhttp3.logging.HttpLoggingInterceptor
import org.jsoup.Jsoup
import org.jsoup.nodes.Document
import java.io.IOException
import java.util.concurrent.TimeUnit

internal fun mergeControlledCookie(
    existingCookies: String,
    name: String,
    value: String
): String {
    val retainedCookies = existingCookies.split(";")
        .map(String::trim)
        .filter { cookie ->
            cookie.isNotEmpty() &&
                    !cookie.substringBefore("=", missingDelimiterValue = cookie)
                        .equals(name, ignoreCase = true)
        }
    return (retainedCookies + "$name=$value").joinToString("; ")
}

/**
 * 全局 HTTP 客户端配置中心，管理 OkHttpClient 实例和网页获取。
 *
 * 职责：
 * - 提供共享的 OkHttpClient（Cookie 管理、拦截器、超时配置）
 * - 通过 [fetchDocument] 获取网页 HTML 并解析为 Jsoup Document
 *
 * 线程：OkHttp 内部管理线程池，调用方可安全在任意线程发起请求
 */
object NetClient {

    internal data class HtmlResponse(
        val finalUrl: String,
        val body: String
    )

    /** 通用 User-Agent，模拟桌面浏览器避免被目标网站拒绝 */
    const val USER_AGENT =
        "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/70.0.3538.67 Safari/537.36"

    /**
     * 磁力链接 / 列表 Cookie 拦截器
     *
     * 注入 Cookie：existmag=all/mag 控制是否显示全部磁力链接。
     * 站点 HTML 页面由 WebView 会话获取（driver-verify 门槛），这里只服务
     * OkHttp 的 ajax/图片请求，与 CookieJar 中的 cookies 合并而非覆盖。
     */
    private val EXIST_MAGNET_INTERCEPTOR by lazy {
        Interceptor { chain ->
            val request = chain.request()
            // Preserve any cookies already set by CookieJar
            val existingCookies = request.header("Cookie") ?: ""
            // The existmag header (set by fetchHtmlResponse via existMagCookieValue) carries the
            // desired cookie value; requests that don't set it default to magnet-only.
            val existMagValue = request.header(EXIST_MAG_COOKIE) ?: existMagCookieValue(false)
            val mergedCookies = mergeControlledCookie(existingCookies, EXIST_MAG_COOKIE, existMagValue)
            val prepared = request.newBuilder()
                .header("User-Agent", USER_AGENT)
                .header("Cookie", mergedCookies)
                .build()
            KLog.d("Prepared cookies for ${prepared.url}", "NetClient")
            chain.proceed(prepared)
        }
    }

    /** OkHttp 客户端，配置超时、拦截器、Cookie 管理 */
    private val okHttpClient by lazy {
        val client = OkHttpClient.Builder()
            .writeTimeout(30 * 1000L, TimeUnit.MILLISECONDS)
            .readTimeout(20 * 1000L, TimeUnit.MILLISECONDS)
            .connectTimeout(15 * 1000L, TimeUnit.MILLISECONDS)
            .addNetworkInterceptor(EXIST_MAGNET_INTERCEPTOR)
            .cookieJar(CookieManagerCookieJar())
        if (BuildConfig.DEBUG) {
            client.addInterceptor(
                HttpLoggingInterceptor().apply { level = HttpLoggingInterceptor.Level.BASIC }
            )
        }
        client.build()
    }

    /** Coil 图片加载器使用的 OkHttpClient，复用连接池和拦截器配置 */
    val glideOkHttpClient: OkHttpClient by lazy { okHttpClient }

    /**
     * 使用 OkHttp 异步请求获取 URL 的 HTML 内容
     */
    internal suspend fun fetchHtml(
        url: String,
        showAll: Boolean = false,
        referer: String? = null
    ): String =
        fetchHtmlResponse(url, showAll, referer).body

    internal suspend fun fetchHtmlResponse(
        url: String,
        showAll: Boolean = false,
        referer: String? = null
    ): HtmlResponse =
        suspendCancellableCoroutine { cont ->
            val request = Request.Builder()
                .url(url)
                .header(EXIST_MAG_COOKIE, existMagCookieValue(showAll))
                .apply { referer?.let { header("Referer", it) } }
                .build()
            val call = okHttpClient.newCall(request)
            cont.invokeOnCancellation { call.cancel() }
            call.enqueue(object : Callback {
                override fun onFailure(call: Call, e: IOException) {
                    cont.resumeWith(Result.failure(e))
                }

                override fun onResponse(call: Call, response: Response) {
                    try {
                        if (!response.isSuccessful) {
                            response.close()
                            cont.resumeWith(
                                Result.failure(
                                    IOException("HTTP ${response.code} for $url")
                                )
                            )
                            return
                        }
                        val body = response.body.string()
                        if (body.isNotBlank()) {
                            cont.resumeWith(
                                Result.success(
                                    HtmlResponse(
                                        response.request.url.toString(),
                                        body
                                    )
                                )
                            )
                        } else {
                            cont.resumeWith(Result.failure(IllegalStateException("Empty response for $url")))
                        }
                    } catch (e: Exception) {
                        cont.resumeWith(Result.failure(e))
                    }
                }
            })
        }

    /**
     * 获取 URL 的 HTML 并解析为 Jsoup Document
     *
     * 封装 fetchHtml + Jsoup.parse 流程，
     * 网络请求在 OkHttp 内部线程、HTML 解析在 Default 线程执行。
     *
     * @param url 目标页面完整 URL
     * @param showAll true 时请求头添加 existmag=all
     * @return 解析好的 Jsoup Document，可直接用于 CSS 选择器查询
     */
    suspend fun fetchDocument(url: String, showAll: Boolean = false): Document {
        val html = fetchHtml(url, showAll)
        return withContext(Dispatchers.Default) { Jsoup.parse(html, url) }
    }

    /**
     * Check if a URL is reachable via HEAD request.
     * Returns response latency in ms, or -1 if unreachable.
     */
    suspend fun checkReachable(url: String): Long = withContext(Dispatchers.IO) {
        try {
            val request = Request.Builder()
                .url(url)
                .head()
                .header("User-Agent", USER_AGENT)
                .build()
            val start = System.currentTimeMillis()
            val success = okHttpClient.newCall(request).execute().use { it.isSuccessful }
            if (success) System.currentTimeMillis() - start else -1L
        } catch (e: Exception) {
            -1L
        }
    }

}

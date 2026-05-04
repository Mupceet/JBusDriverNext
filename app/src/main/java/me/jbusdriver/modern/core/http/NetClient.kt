package me.jbusdriver.modern.core.http

import android.text.TextUtils
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import me.jbusdriver.BuildConfig
import okhttp3.Call
import okhttp3.Callback
import okhttp3.Cookie
import okhttp3.CookieJar
import okhttp3.HttpUrl
import okhttp3.Interceptor
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import okhttp3.logging.HttpLoggingInterceptor
import org.jsoup.Jsoup
import org.jsoup.nodes.Document
import java.io.IOException
import java.util.concurrent.TimeUnit

/**
 * 全局 HTTP 客户端配置中心，管理 OkHttpClient 实例和网页获取。
 *
 * 职责：
 * - 提供共享的 OkHttpClient（Cookie 管理、拦截器、超时配置）
 * - 通过 [fetchDocument] 获取网页 HTML 并解析为 Jsoup Document
 * - 管理 [defaultFastUrl] 站点基础 URL 配置
 *
 * 线程：OkHttp 内部管理线程池，调用方可安全在任意线程发起请求
 */
object NetClient {

    /** 默认站点 URL */
    var defaultFastUrl = "https://www.javbus.com"

    /** 通用 User-Agent，模拟桌面浏览器避免被目标网站拒绝 */
    const val USER_AGENT =
        "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/70.0.3538.67 Safari/537.36"

    /**
     * 磁力链接专用拦截器
     *
     * 注入 Cookie：existmag=all/mag 控制是否显示全部磁力链接，
     * bus_auth 为站点认证 token
     */
    private val EXIST_MAGNET_INTERCEPTOR by lazy {
        Interceptor { chain ->
            var request = chain.request()
            val builder = request.newBuilder().header("User-Agent", USER_AGENT)
            val sb = buildString {
                append(
                    if (!TextUtils.isEmpty(request.header("existmag"))) {
                        "existmag=all"
                    } else {
                        "existmag=mag"
                    }
                )
                append(";")
                append("bus_auth=4b85UbbfIo1f9unsrObLRtu0aYAe8VOgu7OjJJBPE95b9jKg0Jqj7xGmCEzb9VJOGoJO")
            }
            builder.header("Cookie", sb)
            request = builder.build()
            chain.proceed(request)
        }
    }

    /** OkHttp 客户端，配置超时、拦截器、Cookie 管理 */
    private val okHttpClient by lazy {
        val client = OkHttpClient.Builder()
            .writeTimeout(30 * 1000L, TimeUnit.MILLISECONDS)
            .readTimeout(20 * 1000L, TimeUnit.MILLISECONDS)
            .connectTimeout(15 * 1000L, TimeUnit.MILLISECONDS)
            .addNetworkInterceptor(EXIST_MAGNET_INTERCEPTOR)
            .cookieJar(object : CookieJar {
                private val cookieStore = HashMap<String, List<Cookie>>()

                override fun saveFromResponse(url: HttpUrl, cookies: List<Cookie>) {
                    cookieStore[url.host] = cookies
                }

                override fun loadForRequest(url: HttpUrl) = cookieStore[url.host] ?: emptyList()
            })
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
    internal suspend fun fetchHtml(url: String, showAll: Boolean = false, referer: String? = null): String =
        suspendCancellableCoroutine { cont ->
            val request = Request.Builder()
                .url(url)
                .header("existmag", if (showAll) "all" else "")
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
                        val body = response.body.string()
                        if (body.isNotBlank()) {
                            cont.resumeWith(Result.success(body))
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
        return withContext(Dispatchers.Default) { Jsoup.parse(html) }
    }
}

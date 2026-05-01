package me.jbusdriver.modern.core.http

import android.content.Context
import android.net.ConnectivityManager
import android.text.TextUtils
import android.util.Log
import com.google.gson.JsonObject
import me.jbusdriver.BuildConfig
import me.jbusdriver.modern.core.GSON
import okhttp3.*
import retrofit2.Converter
import retrofit2.Retrofit
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import org.jsoup.Jsoup
import org.jsoup.nodes.Document
import java.io.IOException
import java.lang.reflect.Type
import java.util.concurrent.TimeUnit

/**
 * 职责：全局 HTTP 客户端配置中心，管理 OkHttpClient 和 Retrofit 实例
 *
 * 使用场景：
 * - JAVBusService 通过 getRetrofit() 创建 Retrofit 实例
 * - Coil 通过 glideOkHttpClient 复用同一 OkHttp 连接池和拦截器配置
 * - Repository 通过 apiClient 发起网络请求
 *
 * 线程：OkHttp 内部管理线程池，调用方可安全在任意线程发起请求
 */
object NetClient {
    private const val TAG = "NetClient"

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
                append(if (!TextUtils.isEmpty(request.header("existmag"))) {
                    "existmag=all"
                } else {
                    "existmag=mag"
                })
                append(";")
                append("bus_auth=4b85UbbfIo1f9unsrObLRtu0aYAe8VOgu7OjJJBPE95b9jKg0Jqj7xGmCEzb9VJOGoJO")
            }
            builder.header("Cookie", sb)
            request = builder.build()
            chain.proceed(request)
        }
    }

    /** 全局共享的 OkHttpClient 实例 */
    val apiClient: OkHttpClient by lazy { okHttpClient }

    /**
     * 响应体转 String 的 Converter Factory
     *
     * 用于 Retrofit 接口返回原始 HTML 字符串（如 JAVBusService.get()）
     */
    private val strConv = object : Converter.Factory() {
        override fun responseBodyConverter(
            type: Type?,
            annotations: Array<out Annotation>?,
            retrofit: Retrofit?
        ): Converter<ResponseBody, *> =
            Converter<ResponseBody, String> { it.string() }
    }

    /**
     * 响应体转 JsonObject 的 Converter Factory
     *
     * 解析 JSON 并校验 code==200，失败时抛异常
     */
    private val jsonConv = object : Converter.Factory() {
        override fun responseBodyConverter(
            type: Type?,
            annotations: Array<out Annotation>?,
            retrofit: Retrofit?
        ): Converter<ResponseBody, *> =
            Converter<ResponseBody, JsonObject> {
                val s = it.string()
                val json = GSON.fromJson(s, JsonObject::class.java)
                if (json == null || json.isJsonNull || json.entrySet().isEmpty()) {
                    error("json is null")
                }
                if (json.get("code")?.asInt == 200) {
                    return@Converter json
                } else {
                    error(json.get("message")?.asString ?: "未知错误")
                }
            }
    }

    /**
     * 创建 Retrofit 实例
     *
     * @param baseUrl 基础 URL，末尾需带 /
     * @param handleJson true 使用 JSON 校验 Converter，false 使用原始 String Converter
     * @param client 自定义 OkHttpClient，默认使用全局共享实例
     * @return 配置好的 Retrofit 实例
     */
    fun getRetrofit(
        baseUrl: String = "https://raw.githubusercontent.com/",
        handleJson: Boolean = false,
        client: OkHttpClient = okHttpClient
    ): Retrofit =
        Retrofit.Builder().client(client).apply {
            if (baseUrl.isNotEmpty()) this.baseUrl(baseUrl)
        }.addConverterFactory(if (handleJson) jsonConv else strConv)
            .build()

    /** OkHttp 客户端，配置超时、拦截器、Cookie 管理 */
    private val okHttpClient by lazy {
        val client = OkHttpClient.Builder()
            .writeTimeout(30 * 1000L, TimeUnit.MILLISECONDS)
            .readTimeout(20 * 1000L, TimeUnit.MILLISECONDS)
            .connectTimeout(15 * 1000L, TimeUnit.MILLISECONDS)
            .addNetworkInterceptor(EXIST_MAGNET_INTERCEPTOR)
            // 内存级 Cookie 存储，同一 host 的请求自动携带 Cookie
            .cookieJar(object : CookieJar {
                private val cookieStore = HashMap<String, List<Cookie>>()

                override fun saveFromResponse(url: HttpUrl, cookies: List<Cookie>) {
                    cookieStore[url.host] = cookies
                }

                override fun loadForRequest(url: HttpUrl) = cookieStore[url.host] ?: emptyList()
            })
        if (BuildConfig.DEBUG) {
            client.addInterceptor(LoggerInterceptor("OK_HTTP"))
        }
        client.build()
    }

    /** Coil 图片加载器使用的 OkHttpClient，复用连接池和拦截器配置 */
    val glideOkHttpClient: OkHttpClient by lazy { okHttpClient }

    /**
     * 检查网络是否可用
     *
     * @param context 用于获取 ConnectivityManager
     * @return true 表示有可用网络
     */
    fun isNetAvailable(context: Context): Boolean = try {
        val cm = context.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
        cm.activeNetworkInfo?.isAvailable ?: false
    } catch (e: Exception) {
        false
    }

    /**
     * 使用 OkHttp 异步请求获取 URL 的 HTML 内容
     *
     * 通过 suspendCancellableCoroutine 将 OkHttp 的 Callback 转为协程挂起，
     * 协程取消时自动取消底层 OkHttp Call
     *
     * @param url 目标页面完整 URL
     * @param showAll true 时请求头添加 existmag=all，否则默认 existmag=mag
     * @return HTML 字符串
     * @throws IOException 网络请求失败
     * @throws IllegalStateException 响应体为空
     */
    suspend fun fetchHtml(url: String, showAll: Boolean = false): String =
        suspendCancellableCoroutine { cont ->
            val request = Request.Builder()
                .url(url)
                .header("existmag", if (showAll) "all" else "")
                .build()
            val call = apiClient.newCall(request)
            cont.invokeOnCancellation { call.cancel() }
            call.enqueue(object : Callback {
                override fun onFailure(call: Call, e: IOException) {
                    cont.resumeWith(Result.failure(e))
                }

                override fun onResponse(call: Call, response: Response) {
                    try {
                        val body = response.body?.string() ?: ""
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
     * 封装 fetchHtml + withContext(Default) + Jsoup.parse 三步流程，
     * 确保网络请求在 IO 线程、HTML 解析在 Default 线程执行。
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

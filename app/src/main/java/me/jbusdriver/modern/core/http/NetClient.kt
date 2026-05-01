package me.jbusdriver.modern.core.http

import android.content.Context
import android.net.ConnectivityManager
import android.text.TextUtils
import android.util.Log
import com.google.gson.JsonObject
import me.jbusdriver.BuildConfig
import me.jbusdriver.modern.core.GSON
import okhttp3.*
import retrofit2.CallAdapter
import retrofit2.Converter
import retrofit2.Retrofit
import retrofit2.adapter.rxjava3.RxJava3CallAdapterFactory
import java.lang.reflect.Type
import java.util.*
import java.util.concurrent.TimeUnit


/**
 * Created by Administrator on 2016/7/22 0022.
 */
object NetClient {
    private const val TAG = "NetClient"
    const val USER_AGENT =
        "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/70.0.3538.67 Safari/537.36"
    // private val gsonConverterFactory = GsonConverterFactory.create(GSON)

    private val EXIST_MAGNET_INTERCEPTOR by lazy {
        Interceptor { chain ->
            var request = chain.request()
            val builder = request.newBuilder().header("User-Agent", USER_AGENT)
            val sb = buildString {
                append(if (!TextUtils.isEmpty(request.header("existmag"))){
                    "existmag=all"
                }else{
                    "existmag=mag"
                } )
                append(";")
                append("bus_auth=4b85UbbfIo1f9unsrObLRtu0aYAe8VOgu7OjJJBPE95b9jKg0Jqj7xGmCEzb9VJOGoJO")
            }
            builder.header("Cookie",sb)
            request = builder.build()
            chain.proceed(request)
        }
    }
    val RxJavaCallAdapterFactory: CallAdapter.Factory = RxJava3CallAdapterFactory.create()

    val apiClient: OkHttpClient by lazy { okHttpClient }

    private val strConv = object : Converter.Factory() {

        override fun requestBodyConverter(
            type: Type,
            parameterAnnotations: Array<Annotation>,
            methodAnnotations: Array<Annotation>,
            retrofit: Retrofit
        ): Converter<*, RequestBody>? {
            Log.d(TAG, "requestBodyConverter: type=$type, params=${parameterAnnotations.contentToString()}, methods=${methodAnnotations.contentToString()}")
            for (parameterAnnotation in parameterAnnotations) {
                Log.d(TAG, "parameterAnnotation: $parameterAnnotation")
            }
            return super.requestBodyConverter(
                type,
                parameterAnnotations,
                methodAnnotations,
                retrofit
            )
        }

        override fun responseBodyConverter(
            type: Type?,
            annotations: Array<out Annotation>?,
            retrofit: Retrofit?
        ): Converter<ResponseBody, *> =
            Converter<ResponseBody, String> { it.string() }
    }

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

    fun getRetrofit(
        baseUrl: String = "https://raw.githubusercontent.com/",
        handleJson: Boolean = false,
        client: OkHttpClient = okHttpClient
    ): Retrofit =
        Retrofit.Builder().client(client).apply {
            if (baseUrl.isNotEmpty()) this.baseUrl(baseUrl)
        }.addConverterFactory(if (handleJson) jsonConv else strConv)
            .addCallAdapterFactory(RxJavaCallAdapterFactory)
            .build()

    //endregion

    private val okHttpClient by lazy {
        //设置缓存路径

        // .addNetworkInterceptor(StethoInterceptor())
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
            client.addInterceptor(LoggerInterceptor("OK_HTTP"))
        }
        client.build()
    }

    val glideOkHttpClient: OkHttpClient  by lazy { okHttpClient }

    /**
     * 判断是否有网络可用

     * @param context
     * *
     * @return
     */
    fun isNetAvailable(context: Context): Boolean = try {
        val cm = context.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
        cm.activeNetworkInfo?.isAvailable ?: false
    } catch (e: Exception) {
        false
    }
}

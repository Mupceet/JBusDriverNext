package me.jbusdriver.modern

import android.util.Log
import coil.ImageLoader
import coil.ImageLoaderFactory
import coil.decode.GifDecoder
import coil.disk.DiskCache
import dagger.hilt.android.HiltAndroidApp
import me.jbusdriver.BuildConfig
import me.jbusdriver.modern.core.http.HtmlClient
import me.jbusdriver.modern.core.http.NetClient
import me.jbusdriver.modern.core.site.SiteConfig
import okhttp3.Interceptor
import javax.inject.Inject

/**
 * 职责：Hilt Application 入口，同时提供 Coil ImageLoader 配置
 *
 * 使用场景：AndroidManifest 中声明的 Application 类，Hilt 在此完成依赖图初始化
 * 线程：onCreate 在主线程；newImageLoader 由 Coil 在需要时调用
 *
 * 继承关系：JBusApplication → AppContext → Application
 */
@HiltAndroidApp
class JBusApplication : AppContext(), ImageLoaderFactory {

    @Inject
    lateinit var htmlClient: HtmlClient

    @Inject
    lateinit var siteConfig: SiteConfig

    override fun onCreate() {
        super.onCreate()
        NetClient.siteConfig = siteConfig
        if (BuildConfig.DEBUG) {
            Log.d("JBusApplication", "Hilt initialization complete")
        }
    }

    /**
     * 提供 Coil 图片加载器
     *
     * 配置 Referer 头以绕过图片源站的防盗链检查，
     * 使用 OkHttp 作为底层网络客户端以复用 Cookie 和拦截器配置
     */
    override fun newImageLoader(): ImageLoader {
        // 添加 Referer 头，网站防盗链校验需要
        val imageClient = htmlClient.imageOkHttpClient.newBuilder()
            .addInterceptor(Interceptor { chain ->
                val original = chain.request()
                val request = original.newBuilder()
                    .header("Referer", siteConfig.referer())
                    .build()
                chain.proceed(request)
            })
            .build()

        return ImageLoader.Builder(this)
            .okHttpClient(imageClient)
            .diskCache {
                DiskCache.Builder()
                    .directory(cacheDir.resolve("image_cache"))
                    .maxSizeBytes(800L * 1024 * 1024)
                    .build()
            }
            .components { add(GifDecoder.Factory()) }
            .crossfade(true)
            .build()
    }
}

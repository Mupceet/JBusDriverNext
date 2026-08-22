package me.jbusdriver.modern

import android.app.Application
import coil.EventListener
import coil.ImageLoader
import coil.ImageLoaderFactory
import coil.decode.GifDecoder
import coil.disk.DiskCache
import coil.fetch.FetchResult
import coil.fetch.Fetcher
import coil.fetch.SourceResult
import coil.request.ImageRequest
import coil.request.Options
import dagger.hilt.android.HiltAndroidApp
import me.jbusdriver.BuildConfig
import me.jbusdriver.modern.core.http.HtmlClient
import me.jbusdriver.modern.core.site.SiteConfig
import okhttp3.Interceptor
import javax.inject.Inject

/**
 * 职责：Hilt Application 入口，同时提供 Coil ImageLoader 配置
 *
 * 使用场景：AndroidManifest 中声明的 Application 类，Hilt 在此完成依赖图初始化
 * 线程：onCreate 在主线程；newImageLoader 由 Coil 在需要时调用
 *
 * 继承关系：JBusApplication → Application
 */
@HiltAndroidApp
class JBusApplication : Application(), ImageLoaderFactory {

    @Inject
    lateinit var htmlClient: HtmlClient

    @Inject
    lateinit var siteConfig: SiteConfig

    override fun onCreate() {
        super.onCreate()
        KLog.d("Hilt initialization complete", "JBusApplication")
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
            .respectCacheHeaders(false)
            .crossfade(true)
            .eventListenerFactory(if (BuildConfig.DEBUG) {
                EventListener.Factory { request ->
                    object : EventListener {
                        override fun fetchEnd(
                            request: ImageRequest,
                            fetcher: Fetcher,
                            options: Options,
                            result: FetchResult?
                        ) {
                            val dataSource = (result as? SourceResult)?.dataSource?.name
                            KLog.d(
                                "fetch ${request.data} ds=$dataSource",
                                "CoilCache",
                            )
                        }
                    }
                }
            } else {
                EventListener.Factory.NONE
            })
            .build()
    }
}

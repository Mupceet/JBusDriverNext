package me.jbusdriver.modern

import android.app.Application
import coil.EventListener
import coil.ImageLoader
import coil.ImageLoaderFactory
import coil.decode.GifDecoder
import coil.disk.DiskCache
import coil.request.ImageRequest
import coil.request.SuccessResult
import dagger.hilt.android.HiltAndroidApp
import me.jbusdriver.BuildConfig
import me.jbusdriver.modern.core.http.HtmlClient
import me.jbusdriver.modern.core.site.SiteConfig
import me.jbusdriver.modern.ui.settings.AppUpdateDownloader
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
open class JBusApplication : Application(), ImageLoaderFactory {

    @Inject
    lateinit var htmlClient: HtmlClient

    @Inject
    lateinit var siteConfig: SiteConfig

    @Inject
    lateinit var appUpdateDownloader: AppUpdateDownloader

    override fun onCreate() {
        super.onCreate()
        KLog.d("Hilt initialization complete", "JBusApplication")
        // 进程重启后恢复进行中的更新下载监听（下载本身由系统 DownloadManager 持续）
        appUpdateDownloader.resumePending()
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
                // 用 onSuccess 而非 fetchEnd：内存缓存命中不经过 fetcher（fetchEnd 不触发），
                // 而 onSuccess 三种来源（MEMORY_CACHE/DISK/NETWORK）都会回调，便于核对缓存键效果
                EventListener.Factory { request ->
                    object : EventListener {
                        override fun onSuccess(request: ImageRequest, result: SuccessResult) {
                            KLog.d(
                                "load ${request.data} ds=${result.dataSource.name}",
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

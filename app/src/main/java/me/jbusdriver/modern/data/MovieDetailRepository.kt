package me.jbusdriver.modern.data

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import me.jbusdriver.modern.core.CacheLoader
import me.jbusdriver.modern.core.GSON
import me.jbusdriver.modern.core.fromJson
import me.jbusdriver.modern.core.urlPath
import me.jbusdriver.modern.core.http.NetClient
import me.jbusdriver.modern.data.remote.JAVBusService
import me.jbusdriver.modern.domain.model.MovieDetail
import me.jbusdriver.modern.domain.model.parseMovieDetails
import okhttp3.Request
import org.jsoup.Jsoup
import java.io.IOException
import javax.inject.Inject
import javax.inject.Singleton

/**
 * 影片详情数据仓库接口，定义获取影片详情的异步方法。
 *
 * 职责：作为影片详情数据的获取入口，屏蔽底层缓存和网络请求细节。
 *
 * 使用场景：影片详情 ViewModel 通过此接口获取指定影片的完整详情信息。
 *
 * 线程：方法为 suspend 函数，应在协程中调用；内部网络请求在 IO 调度器执行，
 * HTML 解析在 [Dispatchers.Default] 执行。
 */
interface MovieDetailRepository {
    /**
     * 获取指定 URL 的影片详情。
     *
     * 优先从 LRU 内存缓存和磁盘缓存中读取，缓存未命中或 [forceRefresh] 为 true 时
     * 从网络获取 HTML 并解析。
     *
     * @param url 影片详情页 URL
     * @param forceRefresh 是否强制刷新缓存
     * @return 影片详情数据
     */
    suspend fun getMovieDetail(url: String, forceRefresh: Boolean = false): MovieDetail
}

/**
 * [MovieDetailRepository] 的默认实现，使用两级缓存（LRU + 磁盘）+ 网络请求。
 *
 * 职责：组合 LRU 内存缓存、磁盘缓存、OkHttp 网络请求和 Jsoup HTML 解析，
 * 为影片详情页提供透明缓存的数据访问。详情数据使用持久缓存策略（LRU + 磁盘），
 * 应用重启后仍可命中缓存。
 *
 * 使用场景：由 [DataModule] 通过 Hilt 绑定为 [MovieDetailRepository] 的单例实现。
 *
 * 线程：网络请求通过 [suspendCancellableCoroutine] 将 OkHttp 异步回调转为协程挂起，
 * HTML 解析在 [Dispatchers.Default] 执行，确保不阻塞主线程。
 */
@Singleton
class DefaultMovieDetailRepository @Inject constructor() : MovieDetailRepository {

    override suspend fun getMovieDetail(url: String, forceRefresh: Boolean): MovieDetail {
        val cacheKey = url.urlPath

        if (!forceRefresh) {
            // Check LRU memory cache
            CacheLoader.lru.get(cacheKey)?.let {
                return GSON.fromJson<MovieDetail>(it) ?: error("Corrupt cache for $cacheKey")
            }

            // Check disk cache
            CacheLoader.acache.getAsString(cacheKey)?.let {
                GSON.fromJson<MovieDetail>(it)?.let { cached ->
                    CacheLoader.lru.put(cacheKey, GSON.toJson(cached))
                    return cached
                }
            }
        }

        // Fetch from network
        val html = suspendCancellableCoroutine<String> { cont ->
            val request = Request.Builder().url(url).build()
            val call = NetClient.apiClient.newCall(request)
            cont.invokeOnCancellation { call.cancel() }
            call.enqueue(object : okhttp3.Callback {
                override fun onFailure(call: okhttp3.Call, e: IOException) {
                    cont.resumeWith(Result.failure(e))
                }
                override fun onResponse(call: okhttp3.Call, response: okhttp3.Response) {
                    try {
                        val body = response.body?.string() ?: ""
                        if (body.isNotBlank()) {
                            cont.resumeWith(Result.success(body))
                        } else {
                            cont.resumeWith(Result.failure(IllegalStateException("Empty response")))
                        }
                    } catch (e: Exception) {
                        cont.resumeWith(Result.failure(e))
                    }
                }
            })
        }

        val detail = withContext(Dispatchers.Default) {
            val doc = Jsoup.parse(html)
            parseMovieDetails(doc)
        }

        // Cache to LRU + disk
        CacheLoader.cacheLruAndDisk(cacheKey to detail)

        return detail
    }
}

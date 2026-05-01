package me.jbusdriver.modern.data

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import me.jbusdriver.modern.core.CacheLoader
import me.jbusdriver.modern.core.http.NetClient
import me.jbusdriver.modern.data.remote.JAVBusService
import me.jbusdriver.modern.data.model.MoviePageResult
import me.jbusdriver.modern.data.model.PageInfo
import me.jbusdriver.modern.domain.model.ActressInfo
import me.jbusdriver.modern.domain.model.loadMovieFromDoc
import me.jbusdriver.modern.domain.model.parseActressList
import me.jbusdriver.modern.domain.model.SearchType
import org.jsoup.Jsoup
import java.net.URLEncoder
import javax.inject.Inject
import javax.inject.Singleton

/**
 * 搜索数据仓库接口，定义影片搜索和演员搜索的异步方法。
 *
 * 职责：作为搜索功能的统一数据入口，屏蔽底层缓存和网络请求细节。
 *
 * 使用场景：搜索 ViewModel 通过此接口执行影片搜索和演员搜索。
 *
 * 线程：所有方法为 suspend 函数，应在协程中调用；内部网络请求在 IO 调度器执行，
 * HTML 解析在 [Dispatchers.Default] 执行。
 */
interface SearchRepository {
    /**
     * 按搜索类型和关键词搜索影片。
     *
     * @param type 搜索类型（按编号、演员、标签等）
     * @param query 搜索关键词
     * @param page 页码（从 1 开始）
     * @param forceRefresh 是否强制刷新缓存
     * @return 包含分页信息和影片列表的结果
     */
    suspend fun searchMovies(type: SearchType, query: String, page: Int, forceRefresh: Boolean = false): MoviePageResult

    /**
     * 搜索演员。
     *
     * @param query 搜索关键词
     * @param page 页码（从 1 开始）
     * @return 分页信息与演员列表的配对
     */
    suspend fun searchActresses(query: String, page: Int): Pair<PageInfo, List<ActressInfo>>
}

/**
 * [SearchRepository] 的默认实现，通过 OkHttp 获取 HTML 并使用 Jsoup 解析。
 *
 * 职责：组合网络请求、LRU 内存缓存和 HTML 解析，为搜索功能提供透明缓存的数据访问。
 * 搜索结果使用 LRU 缓存策略，应用重启后缓存失效以获取最新结果。
 *
 * 使用场景：由 [DataModule] 通过 Hilt 绑定为 [SearchRepository] 的单例实现。
 *
 * 线程：网络请求通过 [suspendCancellableCoroutine] 将 OkHttp 异步回调转为协程挂起，
 * HTML 解析在 [Dispatchers.Default] 执行，确保不阻塞主线程。
 */
@Singleton
class DefaultSearchRepository @Inject constructor() : SearchRepository {

    override suspend fun searchMovies(type: SearchType, query: String, page: Int, forceRefresh: Boolean): MoviePageResult {
        val baseUrl = JAVBusService.defaultFastUrl
        val url = "${baseUrl}${type.urlPathFormater.format(query)}${if (page > 1) "/$page" else ""}"
        val cacheKey = "search_${type.name}_${URLEncoder.encode(query, "UTF-8")}_$page"

        return CacheLoader.lruCached(cacheKey, forceRefresh) {
            val html = NetClient.fetchHtml(url)
            withContext(Dispatchers.Default) {
                val doc = Jsoup.parse(html)
                val pageInfo = parsePageInfo(doc) ?: PageInfo(activePage = page, nextPage = page)
                val movies = loadMovieFromDoc(doc)
                MoviePageResult(pageInfo, movies)
            }
        }
    }

    override suspend fun searchActresses(query: String, page: Int): Pair<PageInfo, List<ActressInfo>> {
        val baseUrl = JAVBusService.defaultFastUrl
        val type = SearchType.ACTRESS
        val url = "${baseUrl}${type.urlPathFormater.format(query)}${if (page > 1) "/$page" else ""}"
        val cacheKey = "search_actress_${URLEncoder.encode(query, "UTF-8")}_$page"

        return CacheLoader.lruCached(cacheKey) {
            val html = NetClient.fetchHtml(url)
            withContext(Dispatchers.Default) {
                val doc = Jsoup.parse(html)
                val pageInfo = parsePageInfo(doc) ?: PageInfo(activePage = page, nextPage = page)
                val actresses = parseActressList(doc)
                pageInfo to actresses
            }
        }
    }

    /**
     * 从 HTML 文档的分页组件解析当前页、下一页和可用页码信息。
     *
     * @param doc Jsoup 解析后的 HTML 文档
     * @return 分页信息，无分页组件时返回 null
     */
    private fun parsePageInfo(doc: org.jsoup.nodes.Document): PageInfo? {
        val current = doc.select(".pagination .active > a").attr("href")
        if (current.isNullOrEmpty()) return null

        val next = doc.select(".pagination .active ~ li > a").let {
            if (it.isEmpty()) current else it.attr("href")
        }
        val pages = doc.select(".pagination a:not([id])")
            .mapNotNull { it.attr("href").split("/").lastOrNull()?.toIntOrNull() }

        return PageInfo(
            activePage = current.split("/").lastOrNull()?.toIntOrNull() ?: 0,
            nextPage = next.split("/").lastOrNull()?.toIntOrNull() ?: 0,
            referPages = pages
        )
    }

}

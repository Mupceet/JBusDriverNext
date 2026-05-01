
package me.jbusdriver.modern.data

import androidx.collection.ArrayMap
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import me.jbusdriver.modern.core.CacheLoader
import me.jbusdriver.modern.core.GSON
import me.jbusdriver.modern.core.C
import me.jbusdriver.modern.core.fromJson
import me.jbusdriver.modern.core.http.NetClient
import me.jbusdriver.modern.core.urlPath
import me.jbusdriver.modern.data.remote.JAVBusService
import me.jbusdriver.modern.data.model.ActressDetail
import me.jbusdriver.modern.data.model.MoviePageResult
import me.jbusdriver.modern.data.model.PageInfo
import me.jbusdriver.modern.ui.movielist.GenreCategory
import me.jbusdriver.modern.ui.GenreUiModel
import me.jbusdriver.modern.domain.model.ActressInfo
import me.jbusdriver.modern.domain.model.ActressAttrs
import me.jbusdriver.modern.domain.model.Genre
import me.jbusdriver.modern.domain.model.loadMovieFromDoc
import me.jbusdriver.modern.domain.model.parseActressAttrs
import me.jbusdriver.modern.domain.model.parseActressList
import me.jbusdriver.modern.domain.model.DataSourceType
import org.jsoup.Jsoup
import javax.inject.Inject
import javax.inject.Singleton

/**
 * 影片列表数据仓库接口，定义按分类、URL 加载影片和演员列表的异步方法。
 *
 * 职责：作为影片/演员/类型数据的统一获取入口，屏蔽底层数据源（网络、缓存）细节。
 *
 * 使用场景：影片列表 ViewModel 通过此接口获取首页、分类页、演员列表、类型列表及演员详情数据。
 *
 * 线程：所有方法为 suspend 函数，应在协程中调用；内部网络请求在 IO 调度器执行，
 * HTML 解析在 [Dispatchers.Default] 执行。
 */
interface MovieRepository {
    /**
     * 按数据源类型分页加载影片列表。
     *
     * @param type 数据源类型（首页、无码、XYZ 等）
     * @param page 页码（从 1 开始）
     * @param showAll 是否显示全部（包括无磁力链接的影片）
     * @param forceRefresh 是否强制刷新缓存
     * @return 包含分页信息和影片列表的结果
     */
    suspend fun loadPage(type: DataSourceType, page: Int, showAll: Boolean = false, forceRefresh: Boolean = false): MoviePageResult

    /**
     * 按数据源类型分页加载演员列表。
     *
     * @param type 数据源类型
     * @param page 页码（从 1 开始）
     * @param forceRefresh 是否强制刷新缓存
     * @return 演员列表与分页信息的配对
     */
    suspend fun loadActresses(type: DataSourceType, page: Int, forceRefresh: Boolean = false): Pair<List<ActressInfo>, PageInfo>

    /**
     * 加载类型分类列表（如"热门标签"、"题材"等分组）。
     *
     * @param type 数据源类型
     * @param forceRefresh 是否强制刷新缓存
     * @return 类型分组列表
     */
    suspend fun loadGenreCategories(type: DataSourceType, forceRefresh: Boolean = false): List<GenreCategory>

    /**
     * 通过完整 URL 分页加载影片列表（用于分类筛选、类型点击等场景）。
     *
     * @param url 完整的列表页 URL
     * @param page 页码（从 1 开始）
     * @param forceRefresh 是否强制刷新缓存
     * @return 包含分页信息和影片列表的结果
     */
    suspend fun loadPageByUrl(url: String, page: Int, forceRefresh: Boolean = false): MoviePageResult

    /**
     * 加载演员详情。
     *
     * @param url 演员详情页 URL
     * @param forceRefresh 是否强制刷新缓存
     * @return 演员详情数据，加载失败时返回 null
     */
    suspend fun loadActressDetail(url: String, forceRefresh: Boolean = false): ActressDetail?
}

/**
 * [MovieRepository] 的默认实现，通过 OkHttp 获取 HTML 并使用 Jsoup 解析。
 *
 * 职责：组合网络请求、LRU 内存缓存、磁盘缓存和 HTML 解析，
 * 为上层提供透明缓存的数据访问。
 *
 * 使用场景：由 [DataModule] 通过 Hilt 绑定为 [MovieRepository] 的单例实现。
 *
 * 线程：网络请求通过 [suspendCancellableCoroutine] 将 OkHttp 异步回调转为协程挂起，
 * HTML 解析在 [Dispatchers.Default] 执行，确保不阻塞主线程。
 */
@Singleton
class DefaultMovieRepository @Inject constructor() : MovieRepository {

    /**
     * 从磁盘缓存读取可切换 URL 配置映射表。
     * 每次调用都从缓存读取，保证切换 URL 后立即生效。
     */
    private suspend fun loadUrls(): ArrayMap<String, String>? =
        CacheLoader.getString(C.Cache.BUS_URLS)?.let {
            GSON.fromJson<ArrayMap<String, String>>(it)
        }

    override suspend fun loadPage(
        type: DataSourceType,
        page: Int,
        showAll: Boolean,
        forceRefresh: Boolean
    ): MoviePageResult {
        val urls = loadUrls()
        val baseUrl = urls?.get(type.key) ?: JAVBusService.defaultFastUrl
        val basePath = when (type) {
            DataSourceType.UNCENSORED -> "/uncensored"
            DataSourceType.XYZ -> "/xyz"
            else -> ""
        }
        val url = if (page == 1) "$baseUrl$basePath" else "$baseUrl$basePath${type.prefix}$page"
        val cacheKey = "${type.key}_${showAll}_$page"

        return CacheLoader.lruCached(cacheKey, forceRefresh) {
            val html = NetClient.fetchHtml(url, showAll)
            withContext(Dispatchers.Default) {
                val doc = Jsoup.parse(html)
                val pageInfo = parsePageInfo(doc) ?: PageInfo(activePage = page, nextPage = page)
                val movies = loadMovieFromDoc(doc)
                MoviePageResult(pageInfo, movies)
            }
        }
    }

    override suspend fun loadActresses(type: DataSourceType, page: Int, forceRefresh: Boolean): Pair<List<ActressInfo>, PageInfo> {
        val urls = loadUrls()
        val baseUrl = urls?.get(type.key)
            ?: when (type) {
                DataSourceType.UNCENSORED_ACTRESSES -> JAVBusService.defaultFastUrl + "/uncensored/actresses"
                else -> JAVBusService.defaultFastUrl + "/actresses"
            }
        val url = if (page == 1) baseUrl else "$baseUrl/$page"
        val cacheKey = "actresses_${type.key}_$page"

        return CacheLoader.lruCached(cacheKey, forceRefresh) {
            val html = NetClient.fetchHtml(url)
            withContext(Dispatchers.Default) {
                val doc = Jsoup.parse(html)
                val actresses = parseActressList(doc)
                val pageInfo = parsePageInfo(doc) ?: PageInfo(activePage = page, nextPage = if (actresses.size >= 20) page + 1 else page)
                actresses to pageInfo
            }
        }
    }

    override suspend fun loadGenreCategories(type: DataSourceType, forceRefresh: Boolean): List<GenreCategory> {
        val urls = loadUrls()
        val baseUrl = urls?.get(type.key)
            ?: when (type) {
                DataSourceType.UNCENSORED_GENRE -> JAVBusService.defaultFastUrl + "/uncensored/genre"
                else -> JAVBusService.defaultFastUrl + "/genre"
            }
        val cacheKey = "genres_${type.key}"

        return CacheLoader.persistentCached(cacheKey) {
            val html = NetClient.fetchHtml(baseUrl)
            withContext(Dispatchers.Default) {
                val doc = Jsoup.parse(html)

                val genreBoxes = doc.select(".genre-box")
                val titles = genreBoxes.prev().map { it.text() }
                val genreLists = genreBoxes.map { box ->
                    box.select("a").map { Genre(it.text(), it.attr("href")) }
                }

                titles.zip(genreLists).map { (title, genres) ->
                    GenreCategory(title, genres.map { GenreUiModel(it.name, it.link) })
                }
            }
        }
    }

    override suspend fun loadPageByUrl(url: String, page: Int, forceRefresh: Boolean): MoviePageResult {
        val cacheKey = "page_${url.urlPath}_$page"

        return CacheLoader.lruCached(cacheKey, forceRefresh) {
            val fullUrl = if (page == 1) url else "$url/$page"
            val html = NetClient.fetchHtml(fullUrl)
            withContext(Dispatchers.Default) {
                val doc = Jsoup.parse(html)
                val pageInfo = parsePageInfo(doc) ?: PageInfo(activePage = page, nextPage = page)
                val movies = loadMovieFromDoc(doc)
                MoviePageResult(pageInfo, movies)
            }
        }
    }

    override suspend fun loadActressDetail(url: String, forceRefresh: Boolean): ActressDetail? {
        val cacheKey = "actress_${url.urlPath}"

        return CacheLoader.persistentCached(cacheKey) {
            val html = NetClient.fetchHtml(url)
            withContext(Dispatchers.Default) {
                val doc = Jsoup.parse(html)
                val attrs = parseActressAttrs(doc)
                ActressDetail(attrs.title, attrs.imageUrl, attrs.info)
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

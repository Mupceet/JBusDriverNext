package me.jbusdriver.modern.data

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.CancellationException
import me.jbusdriver.modern.KLog
import me.jbusdriver.modern.core.cache.CacheEntry
import me.jbusdriver.modern.core.cache.CacheSource
import me.jbusdriver.modern.core.cache.CachedLoadEvent
import me.jbusdriver.modern.core.cache.CacheStore
import me.jbusdriver.modern.core.cache.MovieCacheTtl
import me.jbusdriver.modern.core.cache.firstCachedOrFresh
import me.jbusdriver.modern.core.cache.lruCached
import me.jbusdriver.modern.core.cache.observeCached
import me.jbusdriver.modern.core.cache.persistentCached
import me.jbusdriver.modern.core.http.HtmlClient
import me.jbusdriver.modern.core.site.SiteConfig
import me.jbusdriver.modern.data.parser.loadMovieFromDoc
import me.jbusdriver.modern.data.parser.parseActressAttrs
import me.jbusdriver.modern.data.parser.parseActressList
import me.jbusdriver.modern.data.parser.parseGenreCategories
import me.jbusdriver.modern.data.parser.parseMovieFilterInfo
import me.jbusdriver.modern.data.parser.parsePageInfo
import me.jbusdriver.modern.domain.model.ActressDetail
import me.jbusdriver.modern.domain.model.ActressInfo
import me.jbusdriver.modern.domain.model.DataSourceType
import me.jbusdriver.modern.domain.model.GenreGroup
import me.jbusdriver.modern.domain.model.MoviePageResult
import me.jbusdriver.modern.domain.model.PageInfo
import me.jbusdriver.modern.domain.model.urlPath
import javax.inject.Inject
import javax.inject.Singleton

private const val TAG = "MovieRepo"

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
    suspend fun loadPage(
        type: DataSourceType,
        page: Int,
        showAll: Boolean = false,
        forceRefresh: Boolean = false
    ): MoviePageResult

    /**
     * 按数据源类型分页加载演员列表。
     *
     * @param type 数据源类型
     * @param page 页码（从 1 开始）
     * @param forceRefresh 是否强制刷新缓存
     * @return 演员列表与分页信息的配对
     */
    suspend fun loadActresses(
        type: DataSourceType,
        page: Int,
        forceRefresh: Boolean = false
    ): Pair<List<ActressInfo>, PageInfo>

    /**
     * 加载类型分类列表（如"热门标签"、"题材"等分组）。
     *
     * @param type 数据源类型
     * @param forceRefresh 是否强制刷新缓存
     * @return 类型分组列表
     */
    suspend fun loadGenreCategories(
        type: DataSourceType,
        forceRefresh: Boolean = false
    ): List<GenreGroup>

    /**
     * 通过完整 URL 分页加载影片列表（用于分类筛选、类型点击等场景）。
     *
     * @param url 完整的列表页 URL
     * @param page 页码（从 1 开始）
     * @param showAll 是否显示全部（包括无磁力链接的影片）
     * @param forceRefresh 是否强制刷新缓存
     * @return 包含分页信息和影片列表的结果
     */
    suspend fun loadPageByUrl(
        url: String,
        page: Int,
        showAll: Boolean = false,
        forceRefresh: Boolean = false
    ): MoviePageResult

    /**
     * 加载演员详情。
     *
     * @param url 演员详情页 URL
     * @param forceRefresh 是否强制刷新缓存
     * @return 演员详情数据，加载失败时返回 null
     */
    suspend fun loadActressDetail(url: String, forceRefresh: Boolean = false): ActressDetail?

    /**
     * 观察按数据源类型分页的影片列表（stale-while-revalidate）。
     *
     * 先发射缓存数据（[CachedLoadEvent.Cached]），再后台刷新发射新数据（[CachedLoadEvent.Fresh]）。
     */
    fun observePage(
        type: DataSourceType,
        page: Int,
        showAll: Boolean = false,
        forceRefresh: Boolean = false,
        revalidate: Boolean = true,
        nowMillis: () -> Long = { System.currentTimeMillis() }
    ): Flow<CachedLoadEvent<MoviePageResult>> = flow {
        try {
            val value = loadPage(type, page, showAll, forceRefresh)
            emit(CachedLoadEvent.Fresh(CacheEntry(value, nowMillis(), CacheSource.Network, false)))
        } catch (throwable: Throwable) {
            if (throwable is CancellationException) throw throwable
            emit(CachedLoadEvent.Failure(throwable, hadCachedValue = false))
        }
    }

    /**
     * 观察按数据源类型分页的演员列表（stale-while-revalidate）。
     */
    fun observeActresses(
        type: DataSourceType,
        page: Int,
        forceRefresh: Boolean = false,
        revalidate: Boolean = true,
        nowMillis: () -> Long = { System.currentTimeMillis() }
    ): Flow<CachedLoadEvent<Pair<List<ActressInfo>, PageInfo>>> = flow {
        try {
            val value = loadActresses(type, page, forceRefresh)
            emit(CachedLoadEvent.Fresh(CacheEntry(value, nowMillis(), CacheSource.Network, false)))
        } catch (throwable: Throwable) {
            if (throwable is CancellationException) throw throwable
            emit(CachedLoadEvent.Failure(throwable, hadCachedValue = false))
        }
    }

    /**
     * 观察类型分类列表（stale-while-revalidate）。
     */
    fun observeGenreCategories(
        type: DataSourceType,
        forceRefresh: Boolean = false,
        revalidate: Boolean = true,
        nowMillis: () -> Long = { System.currentTimeMillis() }
    ): Flow<CachedLoadEvent<List<GenreGroup>>> = flow {
        try {
            val value = loadGenreCategories(type, forceRefresh)
            emit(CachedLoadEvent.Fresh(CacheEntry(value, nowMillis(), CacheSource.Network, false)))
        } catch (throwable: Throwable) {
            if (throwable is CancellationException) throw throwable
            emit(CachedLoadEvent.Failure(throwable, hadCachedValue = false))
        }
    }

    /**
     * 观察通过 URL 分页的影片列表（stale-while-revalidate）。
     */
    fun observePageByUrl(
        url: String,
        page: Int,
        showAll: Boolean = false,
        forceRefresh: Boolean = false,
        revalidate: Boolean = true,
        nowMillis: () -> Long = { System.currentTimeMillis() }
    ): Flow<CachedLoadEvent<MoviePageResult>> = flow {
        try {
            val value = loadPageByUrl(url, page, showAll, forceRefresh)
            emit(CachedLoadEvent.Fresh(CacheEntry(value, nowMillis(), CacheSource.Network, false)))
        } catch (throwable: Throwable) {
            if (throwable is CancellationException) throw throwable
            emit(CachedLoadEvent.Failure(throwable, hadCachedValue = false))
        }
    }
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
class DefaultMovieRepository @Inject constructor(
    private val htmlClient: HtmlClient,
    private val cacheStore: CacheStore,
    private val siteConfig: SiteConfig
) : MovieRepository {

    // ── Flow-based observe methods (stale-while-revalidate) ──

    override fun observePage(
        type: DataSourceType,
        page: Int,
        showAll: Boolean,
        forceRefresh: Boolean,
        revalidate: Boolean,
        nowMillis: () -> Long
    ): Flow<CachedLoadEvent<MoviePageResult>> {
        val baseUrl = siteConfig.baseUrl
        val basePath = when (type) {
            DataSourceType.UNCENSORED -> "/uncensored"
            DataSourceType.XYZ -> "/xyz"
            else -> ""
        }
        val url = if (page == 1) "$baseUrl$basePath" else "$baseUrl$basePath${type.prefix}$page"
        val cacheKey = "${movieCachePrefix()}:${type.key}_${showAll}_$page"

        return cacheStore.observeCached(
            key = cacheKey,
            ttlMillis = if (page == 1) MovieCacheTtl.MOVIE_LIST_FIRST_PAGE_MILLIS else MovieCacheTtl.MOVIE_LIST_NEXT_PAGE_MILLIS,
            disk = true,
            forceRefresh = forceRefresh,
            revalidate = revalidate && page == 1,
            nowMillis = nowMillis
        ) {
            fetchMoviePage(url, showAll)
        }
    }

    override fun observeActresses(
        type: DataSourceType,
        page: Int,
        forceRefresh: Boolean,
        revalidate: Boolean,
        nowMillis: () -> Long
    ): Flow<CachedLoadEvent<Pair<List<ActressInfo>, PageInfo>>> {
        val baseUrl = when (type) {
            DataSourceType.UNCENSORED_ACTRESSES -> siteConfig.baseUrl + "/uncensored/actresses"
            else -> siteConfig.baseUrl + "/actresses"
        }
        val url = if (page == 1) baseUrl else "$baseUrl/$page"
        val cacheKey = "${movieCachePrefix()}:actresses_${type.key}_$page"

        return cacheStore.observeCached(
            key = cacheKey,
            ttlMillis = if (page == 1) MovieCacheTtl.ACTRESS_LIST_FIRST_PAGE_MILLIS else MovieCacheTtl.ACTRESS_LIST_NEXT_PAGE_MILLIS,
            disk = true,
            forceRefresh = forceRefresh,
            revalidate = revalidate && page == 1,
            nowMillis = nowMillis
        ) {
            fetchActressPage(url, baseUrl)
        }
    }

    override fun observeGenreCategories(
        type: DataSourceType,
        forceRefresh: Boolean,
        revalidate: Boolean,
        nowMillis: () -> Long
    ): Flow<CachedLoadEvent<List<GenreGroup>>> {
        val baseUrl = when (type) {
            DataSourceType.UNCENSORED_GENRE -> siteConfig.baseUrl + "/uncensored/genre"
            else -> siteConfig.baseUrl + "/genre"
        }
        val cacheKey = "${movieCachePrefix()}:genres_v2_${type.key}"

        return cacheStore.observeCached(
            key = cacheKey,
            ttlMillis = MovieCacheTtl.GENRE_CATEGORIES_MILLIS,
            disk = true,
            forceRefresh = forceRefresh,
            revalidate = revalidate,
            nowMillis = nowMillis
        ) {
            fetchGenreCategories(baseUrl)
        }
    }

    override fun observePageByUrl(
        url: String,
        page: Int,
        showAll: Boolean,
        forceRefresh: Boolean,
        revalidate: Boolean,
        nowMillis: () -> Long
    ): Flow<CachedLoadEvent<MoviePageResult>> {
        val resolvedUrl = siteConfig.resolve(url)
        val cacheKey = "${movieCachePrefix()}:page_${resolvedUrl.urlPath}_${showAll}_$page"

        return cacheStore.observeCached(
            key = cacheKey,
            ttlMillis = if (page == 1) MovieCacheTtl.MOVIE_BY_URL_FIRST_PAGE_MILLIS else MovieCacheTtl.MOVIE_BY_URL_NEXT_PAGE_MILLIS,
            disk = true,
            forceRefresh = forceRefresh,
            revalidate = revalidate && page == 1,
            nowMillis = nowMillis
        ) {
            val fullUrl = if (page == 1) resolvedUrl else "$resolvedUrl/$page"
            fetchMoviePage(fullUrl, showAll)
        }
    }

    // ── Suspend load methods (delegate to observe + firstCachedOrFresh) ──

    override suspend fun loadPage(
        type: DataSourceType,
        page: Int,
        showAll: Boolean,
        forceRefresh: Boolean
    ): MoviePageResult =
        observePage(type, page, showAll, forceRefresh = forceRefresh, revalidate = false)
            .firstCachedOrFresh()

    override suspend fun loadActresses(
        type: DataSourceType,
        page: Int,
        forceRefresh: Boolean
    ): Pair<List<ActressInfo>, PageInfo> =
        observeActresses(type, page, forceRefresh = forceRefresh, revalidate = false)
            .firstCachedOrFresh()

    override suspend fun loadGenreCategories(
        type: DataSourceType,
        forceRefresh: Boolean
    ): List<GenreGroup> =
        observeGenreCategories(type, forceRefresh = forceRefresh, revalidate = false)
            .firstCachedOrFresh()

    override suspend fun loadPageByUrl(
        url: String,
        page: Int,
        showAll: Boolean,
        forceRefresh: Boolean
    ): MoviePageResult =
        observePageByUrl(url, page, showAll, forceRefresh = forceRefresh, revalidate = false)
            .firstCachedOrFresh()

    override suspend fun loadActressDetail(url: String, forceRefresh: Boolean): ActressDetail {
        val cacheKey = "actress_${url.urlPath}"

        return cacheStore.persistentCached(cacheKey, forceRefresh) {
            val doc = htmlClient.fetchDocument(siteConfig.resolve(url))
            val attrs = parseActressAttrs(doc, siteConfig.baseUrl)
            ActressDetail(attrs.title, attrs.imageUrl, attrs.info)
        }
    }

    // ── Private helpers ──

    private fun movieCachePrefix(): String = "movie:${siteConfig.baseUrl}"

    private suspend fun fetchMoviePage(url: String, showAll: Boolean): MoviePageResult {
        KLog.d("fetchMoviePage: url=$url, showAll=$showAll", TAG)
        val doc = htmlClient.fetchDocument(url, showAll)
        val pageInfo = parsePageInfo(doc) ?: PageInfo(activePage = 1, nextPage = 1)
        val movies = loadMovieFromDoc(doc, siteConfig.baseUrl)
        val filterInfo = parseMovieFilterInfo(doc)
        return MoviePageResult(pageInfo, movies, filterInfo)
    }

    private suspend fun fetchActressPage(
        url: String,
        baseUrl: String
    ): Pair<List<ActressInfo>, PageInfo> {
        KLog.d("fetchActressPage: url=$url", TAG)
        val doc = htmlClient.fetchDocument(url)
        val actresses = parseActressList(doc, siteConfig.baseUrl)
        val pageInfo = parsePageInfo(doc) ?: PageInfo(
            activePage = 1,
            nextPage = if (actresses.size >= 20) 2 else 1
        )
        return actresses to pageInfo
    }

    private suspend fun fetchGenreCategories(url: String): List<GenreGroup> {
        KLog.d("fetchGenreCategories: url=$url", TAG)
        val doc = htmlClient.fetchDocument(url)
        val rawCategories = parseGenreCategories(doc)
        val allGenres = rawCategories.flatMap { it.second }
        allGenres.groupBy { it.link }
            .filter { it.value.size > 1 }
            .forEach { (link, items) ->
                KLog.w("Duplicate genre link=$link, names=${items.map { it.name }}")
            }
        val seen = mutableSetOf<String>()
        return rawCategories.mapNotNull { (title, genres) ->
            val deduped = genres.filter { seen.add(it.link) }
            if (deduped.isEmpty()) null else GenreGroup(title, deduped)
        }
    }
}

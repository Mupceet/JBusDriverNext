
package me.jbusdriver.modern.data

import androidx.collection.ArrayMap
import kotlinx.coroutines.suspendCancellableCoroutine
import me.jbusdriver.base.CacheLoader
import me.jbusdriver.base.GSON
import me.jbusdriver.base.common.C
import me.jbusdriver.base.fromJson
import me.jbusdriver.base.urlPath
import me.jbusdriver.http.JAVBusService
import me.jbusdriver.modern.data.model.ActressDetail
import me.jbusdriver.modern.data.model.MoviePageResult
import me.jbusdriver.modern.data.model.PageInfo
import me.jbusdriver.modern.ui.movielist.GenreCategory
import me.jbusdriver.modern.ui.GenreUiModel
import me.jbusdriver.mvp.bean.ActressInfo
import me.jbusdriver.mvp.bean.ActressAttrs
import me.jbusdriver.mvp.bean.Genre
import me.jbusdriver.mvp.bean.loadMovieFromDoc
import me.jbusdriver.mvp.bean.parseActressAttrs
import me.jbusdriver.mvp.bean.parseActressList
import me.jbusdriver.ui.data.enums.DataSourceType
import org.jsoup.Jsoup
import javax.inject.Inject
import javax.inject.Singleton

interface MovieRepository {
    suspend fun loadPage(type: DataSourceType, page: Int, showAll: Boolean = false): MoviePageResult
    suspend fun loadActresses(type: DataSourceType, page: Int): Pair<List<ActressInfo>, PageInfo>
    suspend fun loadGenreCategories(type: DataSourceType): List<GenreCategory>
    suspend fun loadPageByUrl(url: String, page: Int): MoviePageResult
    suspend fun loadActressDetail(url: String): ActressDetail?
}

@Singleton
class DefaultMovieRepository @Inject constructor() : MovieRepository {

    private val urls: ArrayMap<String, String>? by lazy {
        CacheLoader.acache.getAsString(C.Cache.BUS_URLS)?.let {
            GSON.fromJson<ArrayMap<String, String>>(it)
        }
    }

    override suspend fun loadPage(
        type: DataSourceType,
        page: Int,
        showAll: Boolean
    ): MoviePageResult {
        val baseUrl = urls?.get(type.key) ?: JAVBusService.defaultFastUrl
        val url = if (page == 1) baseUrl else "$baseUrl${type.prefix}$page"
        val cacheKey = "${type.key}_${showAll}_$page"

        return lruCachedOrFetch(cacheKey) {
            val html = fetchHtml(url, showAll)
            val doc = Jsoup.parse(html)
            val pageInfo = parsePageInfo(doc) ?: PageInfo(activePage = page, nextPage = page)
            val movies = loadMovieFromDoc(doc)
            MoviePageResult(pageInfo, movies)
        }
    }

    override suspend fun loadActresses(type: DataSourceType, page: Int): Pair<List<ActressInfo>, PageInfo> {
        val baseUrl = urls?.get(type.key)
            ?: when (type) {
                DataSourceType.UNCENSORED_ACTRESSES -> JAVBusService.defaultFastUrl + "/uncensored/actresses"
                else -> JAVBusService.defaultFastUrl + "/actresses"
            }
        val url = if (page == 1) baseUrl else "$baseUrl/$page"
        val cacheKey = "actresses_${type.key}_$page"

        return lruCachedOrFetch(cacheKey) {
            val html = fetchHtml(url)
            val doc = Jsoup.parse(html)
            val actresses = parseActressList(doc)
            val pageInfo = parsePageInfo(doc) ?: PageInfo(activePage = page, nextPage = if (actresses.size >= 20) page + 1 else page)
            actresses to pageInfo
        }
    }

    override suspend fun loadGenreCategories(type: DataSourceType): List<GenreCategory> {
        val baseUrl = urls?.get(type.key)
            ?: when (type) {
                DataSourceType.UNCENSORED_GENRE -> JAVBusService.defaultFastUrl + "/uncensored/genre"
                else -> JAVBusService.defaultFastUrl + "/genre"
            }
        val cacheKey = "genres_${type.key}"

        return persistentCachedOrFetch(cacheKey) {
            val html = fetchHtml(baseUrl)
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

    override suspend fun loadPageByUrl(url: String, page: Int): MoviePageResult {
        val cacheKey = "page_${url.urlPath}_$page"

        return lruCachedOrFetch(cacheKey) {
            val fullUrl = if (page == 1) url else "$url/$page"
            val html = fetchHtml(fullUrl)
            val doc = Jsoup.parse(html)
            val pageInfo = parsePageInfo(doc) ?: PageInfo(activePage = page, nextPage = page)
            val movies = loadMovieFromDoc(doc)
            MoviePageResult(pageInfo, movies)
        }
    }

    override suspend fun loadActressDetail(url: String): ActressDetail? {
        val cacheKey = "actress_${url.urlPath}"

        return persistentCachedOrFetch(cacheKey) {
            val html = fetchHtml(url)
            val doc = Jsoup.parse(html)
            val attrs = parseActressAttrs(doc)
            ActressDetail(attrs.title, attrs.imageUrl, attrs.info)
        }
    }

    /** LRU + disk cache, for static data (details, genres). Survives app restarts. */
    private inline fun <reified T> persistentCachedOrFetch(cacheKey: String, fetch: () -> T): T {
        CacheLoader.lru.get(cacheKey)?.let {
            GSON.fromJson<T>(it)?.let { return it }
        }
        CacheLoader.acache.getAsString(cacheKey)?.let {
            GSON.fromJson<T>(it)?.let { cached ->
                CacheLoader.lru.put(cacheKey, GSON.toJson(cached))
                return cached
            }
        }
        val result = fetch()
        CacheLoader.cacheLruAndDisk(cacheKey to (result as Any))
        return result
    }

    /** LRU-only cache, for list data. Fresh data on next app launch. */
    private inline fun <reified T> lruCachedOrFetch(cacheKey: String, fetch: () -> T): T {
        CacheLoader.lru.get(cacheKey)?.let {
            GSON.fromJson<T>(it)?.let { return it }
        }
        val result = fetch()
        CacheLoader.cacheLru(cacheKey to (result as Any))
        return result
    }

    private suspend fun fetchHtml(url: String, showAll: Boolean = false): String {
        return suspendCancellableCoroutine { cont ->
            val service = JAVBusService.INSTANCE
            val disposable = service.get(url, if (showAll) "all" else "")
                .subscribe({ html ->
                    if (html.isNotBlank()) {
                        cont.resumeWith(Result.success(html))
                    } else {
                        cont.resumeWith(Result.failure(IllegalStateException("Empty response")))
                    }
                }, { error ->
                    cont.resumeWith(Result.failure(error))
                })
            cont.invokeOnCancellation { disposable.dispose() }
        }
    }

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

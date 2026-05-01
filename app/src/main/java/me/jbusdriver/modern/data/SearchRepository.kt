package me.jbusdriver.modern.data

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import me.jbusdriver.modern.core.CacheLoader
import me.jbusdriver.modern.core.GSON
import me.jbusdriver.modern.core.fromJson
import me.jbusdriver.modern.core.http.NetClient
import me.jbusdriver.modern.data.remote.JAVBusService
import me.jbusdriver.modern.data.model.MoviePageResult
import me.jbusdriver.modern.data.model.PageInfo
import me.jbusdriver.modern.domain.model.ActressInfo
import me.jbusdriver.modern.domain.model.loadMovieFromDoc
import me.jbusdriver.modern.domain.model.parseActressList
import me.jbusdriver.modern.domain.model.SearchType
import okhttp3.Request
import org.jsoup.Jsoup
import java.io.IOException
import java.net.URLEncoder
import javax.inject.Inject
import javax.inject.Singleton

interface SearchRepository {
    suspend fun searchMovies(type: SearchType, query: String, page: Int, forceRefresh: Boolean = false): MoviePageResult
    suspend fun searchActresses(query: String, page: Int): Pair<PageInfo, List<ActressInfo>>
}

@Singleton
class DefaultSearchRepository @Inject constructor() : SearchRepository {

    override suspend fun searchMovies(type: SearchType, query: String, page: Int, forceRefresh: Boolean): MoviePageResult {
        val baseUrl = JAVBusService.defaultFastUrl
        val url = "${baseUrl}${type.urlPathFormater.format(query)}${if (page > 1) "/$page" else ""}"
        val cacheKey = "search_${type.name}_${URLEncoder.encode(query, "UTF-8")}_$page"

        return lruCachedOrFetch(cacheKey, forceRefresh) {
            val html = fetchHtml(url)
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

        return lruCachedOrFetch(cacheKey) {
            val html = fetchHtml(url)
            withContext(Dispatchers.Default) {
                val doc = Jsoup.parse(html)
                val pageInfo = parsePageInfo(doc) ?: PageInfo(activePage = page, nextPage = page)
                val actresses = parseActressList(doc)
                pageInfo to actresses
            }
        }
    }

    private suspend fun fetchHtml(url: String): String = suspendCancellableCoroutine { cont ->
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

    /** LRU-only cache with optional force-refresh. */
    private inline fun <reified T> lruCachedOrFetch(cacheKey: String, forceRefresh: Boolean = false, fetch: () -> T): T {
        if (!forceRefresh) {
            CacheLoader.lru.get(cacheKey)?.let {
                GSON.fromJson<T>(it)?.let { return it }
            }
        }
        val result = fetch()
        CacheLoader.cacheLru(cacheKey to (result as Any))
        return result
    }
}

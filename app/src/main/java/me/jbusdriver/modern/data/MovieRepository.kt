
package me.jbusdriver.modern.data

import androidx.collection.ArrayMap
import kotlinx.coroutines.suspendCancellableCoroutine
import me.jbusdriver.base.CacheLoader
import me.jbusdriver.base.GSON
import me.jbusdriver.base.common.C
import me.jbusdriver.base.fromJson
import me.jbusdriver.http.JAVBusService
import me.jbusdriver.modern.data.model.MoviePageResult
import me.jbusdriver.modern.data.model.PageInfo
import me.jbusdriver.mvp.bean.loadMovieFromDoc
import me.jbusdriver.ui.data.enums.DataSourceType
import org.jsoup.Jsoup
import javax.inject.Inject
import javax.inject.Singleton

interface MovieRepository {
    suspend fun loadPage(type: DataSourceType, page: Int, showAll: Boolean = false): MoviePageResult
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

        val service = JAVBusService.getInstance(baseUrl)
        val html = suspendCancellableCoroutine<String> { cont ->
            val disposable = service.get(url, if (showAll) "all" else "")
                .subscribe({ html ->
                    if (html.isNotBlank()) {
                        if (page == 1) CacheLoader.lru.put("${type.key}$showAll", html)
                        cont.resumeWith(Result.success(html))
                    } else {
                        cont.resumeWith(Result.failure(IllegalStateException("Empty response")))
                    }
                }, { error ->
                    cont.resumeWith(Result.failure(error))
                })
            cont.invokeOnCancellation { disposable.dispose() }
        }

        val doc = Jsoup.parse(html)

        val pageInfo = parsePageInfo(doc) ?: PageInfo(activePage = page, nextPage = page)
        val movies = loadMovieFromDoc(doc)

        return MoviePageResult(pageInfo, movies)
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

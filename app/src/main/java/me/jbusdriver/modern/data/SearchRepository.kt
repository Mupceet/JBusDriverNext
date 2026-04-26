package me.jbusdriver.modern.data

import kotlinx.coroutines.suspendCancellableCoroutine
import me.jbusdriver.http.JAVBusService
import me.jbusdriver.modern.data.model.MoviePageResult
import me.jbusdriver.modern.data.model.PageInfo
import me.jbusdriver.mvp.bean.ActressInfo
import me.jbusdriver.mvp.bean.loadMovieFromDoc
import me.jbusdriver.mvp.bean.parseActressList
import me.jbusdriver.ui.data.enums.SearchType
import org.jsoup.Jsoup
import javax.inject.Inject
import javax.inject.Singleton

interface SearchRepository {
    suspend fun searchMovies(type: SearchType, query: String, page: Int): MoviePageResult
    suspend fun searchActresses(query: String, page: Int): Pair<PageInfo, List<ActressInfo>>
}

@Singleton
class DefaultSearchRepository @Inject constructor() : SearchRepository {

    override suspend fun searchMovies(type: SearchType, query: String, page: Int): MoviePageResult {
        val baseUrl = JAVBusService.defaultFastUrl
        val url = "${baseUrl}${type.urlPathFormater.format(query)}${if (page > 1) "/$page" else ""}"

        val html = fetchHtml(url)
        val doc = Jsoup.parse(html)

        val pageInfo = parsePageInfo(doc) ?: PageInfo(activePage = page, nextPage = page)
        val movies = loadMovieFromDoc(doc)

        return MoviePageResult(pageInfo, movies)
    }

    override suspend fun searchActresses(query: String, page: Int): Pair<PageInfo, List<ActressInfo>> {
        val baseUrl = JAVBusService.defaultFastUrl
        val type = SearchType.ACTRESS
        val url = "${baseUrl}${type.urlPathFormater.format(query)}${if (page > 1) "/$page" else ""}"

        val html = fetchHtml(url)
        val doc = Jsoup.parse(html)

        val pageInfo = parsePageInfo(doc) ?: PageInfo(activePage = page, nextPage = page)
        val actresses = parseActressList(doc)

        return pageInfo to actresses
    }

    private fun fetchHtml(url: String): String = suspendCancellableCoroutine { cont ->
        val disposable = JAVBusService.INSTANCE.get(url)
            .subscribe(
                { html -> cont.resumeWith(Result.success(html)) },
                { error -> cont.resumeWith(Result.failure(error)) }
            )
        cont.invokeOnCancellation { disposable.dispose() }
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

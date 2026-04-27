
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
import me.jbusdriver.modern.ui.movielist.GenreCategory
import me.jbusdriver.modern.ui.GenreUiModel
import me.jbusdriver.mvp.bean.ActressInfo
import me.jbusdriver.mvp.bean.Genre
import me.jbusdriver.mvp.bean.loadMovieFromDoc
import me.jbusdriver.mvp.bean.parseActressList
import me.jbusdriver.ui.data.enums.DataSourceType
import org.jsoup.Jsoup
import javax.inject.Inject
import javax.inject.Singleton

interface MovieRepository {
    suspend fun loadPage(type: DataSourceType, page: Int, showAll: Boolean = false): MoviePageResult
    suspend fun loadActresses(type: DataSourceType, page: Int): Pair<List<ActressInfo>, PageInfo>
    suspend fun loadGenreCategories(type: DataSourceType): List<GenreCategory>
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

        val html = fetchHtml(url, showAll)
        val doc = Jsoup.parse(html)

        val pageInfo = parsePageInfo(doc) ?: PageInfo(activePage = page, nextPage = page)
        val movies = loadMovieFromDoc(doc)

        return MoviePageResult(pageInfo, movies)
    }

    override suspend fun loadActresses(type: DataSourceType, page: Int): Pair<List<ActressInfo>, PageInfo> {
        val suffix = when (type) {
            DataSourceType.UNCENSORED_ACTRESSES -> "/uncensored/actresses"
            else -> "/actresses"
        }
        val baseUrl = urls?.get(type.key) ?: JAVBusService.defaultFastUrl
        val url = if (page == 1) "$baseUrl$suffix" else "$baseUrl$suffix/page/$page"

        val html = fetchHtml(url)
        val doc = Jsoup.parse(html)
        val actresses = parseActressList(doc)
        val pageInfo = parsePageInfo(doc) ?: PageInfo(activePage = page, nextPage = if (actresses.size >= 20) page + 1 else page)

        return actresses to pageInfo
    }

    override suspend fun loadGenreCategories(type: DataSourceType): List<GenreCategory> {
        val suffix = when (type) {
            DataSourceType.UNCENSORED_GENRE -> "/uncensored/genre"
            else -> "/genre"
        }
        val baseUrl = urls?.get(type.key) ?: JAVBusService.defaultFastUrl
        val url = "$baseUrl$suffix"

        val html = fetchHtml(url)
        val doc = Jsoup.parse(html)

        val genreBoxes = doc.select(".genre-box")
        val titles = genreBoxes.prev().map { it.text() }
        val genreLists = genreBoxes.map { box ->
            box.select("a").map { Genre(it.text(), it.attr("href")) }
        }

        return titles.zip(genreLists).map { (title, genres) ->
            GenreCategory(title, genres.map { GenreUiModel(it.name, it.link) })
        }
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

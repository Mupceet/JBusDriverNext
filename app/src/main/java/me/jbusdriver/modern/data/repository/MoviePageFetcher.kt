package me.jbusdriver.modern.data.repository

import me.jbusdriver.modern.KLog
import me.jbusdriver.modern.core.http.HtmlClient
import me.jbusdriver.modern.core.site.resolveUrl
import me.jbusdriver.modern.data.parser.loadMovieFromDoc
import me.jbusdriver.modern.data.parser.parseActressAttrs
import me.jbusdriver.modern.data.parser.parseActressList
import me.jbusdriver.modern.data.parser.parseGenreCategories
import me.jbusdriver.modern.data.parser.parseMovieFilterInfo
import me.jbusdriver.modern.data.parser.parsePageInfo
import me.jbusdriver.modern.domain.model.ActressDetail
import me.jbusdriver.modern.domain.model.ActressInfo
import me.jbusdriver.modern.domain.model.GenreGroup
import me.jbusdriver.modern.domain.model.MoviePageResult
import me.jbusdriver.modern.domain.model.PageInfo
import javax.inject.Inject

private const val TAG = "MovieRepo"

class MoviePageFetcher @Inject constructor(
    private val htmlClient: HtmlClient
) {
    suspend fun fetchMoviePage(url: String, showAll: Boolean, baseUrl: String): MoviePageResult {
        KLog.d("fetchMoviePage: url=$url, showAll=$showAll", TAG)
        val doc = htmlClient.fetchDocument(url, showAll)
        val pageInfo = parsePageInfo(doc) ?: PageInfo(activePage = 1, nextPage = 1)
        val movies = loadMovieFromDoc(doc, baseUrl)
        val filterInfo = parseMovieFilterInfo(doc)
        return MoviePageResult(pageInfo, movies, filterInfo)
    }

    suspend fun fetchActressPage(url: String, baseUrl: String): Pair<List<ActressInfo>, PageInfo> {
        KLog.d("fetchActressPage: url=$url", TAG)
        val doc = htmlClient.fetchDocument(url)
        val actresses = parseActressList(doc, baseUrl)
        val pageInfo = parsePageInfo(doc) ?: PageInfo(
            activePage = 1,
            nextPage = if (actresses.size >= 20) 2 else 1
        )
        return actresses to pageInfo
    }

    suspend fun fetchGenreCategories(url: String): List<GenreGroup> {
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

    suspend fun fetchActressDetail(baseUrl: String, url: String): ActressDetail {
        val doc = htmlClient.fetchDocument(resolveUrl(baseUrl, url))
        val attrs = parseActressAttrs(doc, baseUrl)
        return ActressDetail(attrs.title, attrs.imageUrl, attrs.info)
    }
}

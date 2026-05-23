package me.jbusdriver.modern.data.parser

import me.jbusdriver.modern.domain.model.ActressInfo
import me.jbusdriver.modern.domain.model.Genre
import me.jbusdriver.modern.domain.model.Header
import me.jbusdriver.modern.domain.model.ImageSample
import me.jbusdriver.modern.domain.model.Movie
import me.jbusdriver.modern.domain.model.MovieDetail
import me.jbusdriver.modern.domain.model.MovieFilterInfo
import me.jbusdriver.modern.domain.model.PageInfo
import org.jsoup.nodes.Document

fun parsePageInfo(doc: Document): PageInfo? {
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

fun loadMovieFromDoc(doc: Document, baseUrl: String): List<Movie> {
    return doc.select(".movie-box").map { element ->
        Movie(
            title = element.select("img").attr("title"),
            imageUrl = element.select("img").attr("src").wrapImage(baseUrl),
            code = element.select("date").getOrNull(0)?.text() ?: "",
            date = element.select("date").getOrNull(1)?.text() ?: "",
            link = element.attr("href"),
            tags = element.select(".item-tag").firstOrNull()?.children()?.map { it.text() }
                ?: element.select(".photo-info button").map { it.text() }
        )
    }
}

fun parseMovieDetails(doc: Document, baseUrl: String): MovieDetail {
    val roeMovie = doc.select("[class=row movie]")
    val bigImage = roeMovie.select(".bigImage")
    val title = bigImage.select("img").attr("title")
    val cover = bigImage.attr("href").wrapImage(baseUrl)

    val html = doc.html()
    val gid = Regex("""var\s+gid\s*=\s*(\d+)""").find(html)?.groupValues?.get(1)
    val uc = Regex("""var\s+uc\s*=\s*(\d+)""").find(html)?.groupValues?.get(1)

    val headers = mutableListOf<Header>()
    val headersContainer = roeMovie.select(".info")

    headersContainer.select("span.header").filterNot { it.parent()?.hasClass("star-show") == true }.forEach { span ->
        val p = span.parent() ?: return@forEach
        val name = span.text().trimEnd(':').trim()
        val linkEl = p.select("a").firstOrNull()
        val value = linkEl?.text() ?: p.text().removePrefix(span.text()).trim()
        val link = linkEl?.attr("href") ?: ""
        headers.add(Header(name, value, link))
    }

    val content = doc.select("[name=description]").attr("content").trim()

    val genres = headersContainer.select(".genre:has(a[href*=genre])").map {
        Genre(it.text(), it.select("a").attr("href"))
    }

    val actresses = doc.select("#avatar-waterfall .avatar-box").map {
        ActressInfo(it.text(), it.select("img").attr("src").wrapImage(baseUrl), it.attr("href"))
    }

    val samples = doc.select("#sample-waterfall .sample-box").map {
        val thumb = it.select("img").attr("src").wrapImage(baseUrl)
        val image = it.attr("href").wrapImage(baseUrl)
        ImageSample(
            it.select("img").attr("title"),
            thumb,
            if (image.isBlank()) thumb else image
        )
    }

    val relatedMovies = doc.select("#related-waterfall .movie-box").map {
        val url = it.attr("href")
        Movie(
            it.attr("title"),
            it.select("img").attr("src").wrapImage(baseUrl),
            url.split("/").last(),
            "",
            url
        )
    }

    return MovieDetail(
        title = title,
        content = content,
        cover = cover,
        headers = headers,
        genres = genres,
        actress = actresses,
        imageSamples = samples,
        relatedMovies = relatedMovies,
        gid = gid,
        uc = uc
    )
}

fun parseMovieFilterInfo(doc: Document): MovieFilterInfo? {
    val alert = doc.selectFirst(".alert-success") ?: return null
    val magnetText = alert.selectFirst("#resultshowmag")?.text() ?: return null
    val allText = alert.selectFirst("#resultshowall")?.text() ?: return null
    val magnetCount = magnetText.filter { it.isDigit() }.toIntOrNull() ?: return null
    val totalCount = allText.filter { it.isDigit() }.toIntOrNull() ?: return null
    val parts = alert.selectFirst("b")?.text()
        ?.split("-")?.map { it.trim() }?.filter { it.isNotBlank() }
    val breadcrumbName = parts?.getOrNull(0)
    val breadcrumbType = parts?.getOrNull(1)
    return MovieFilterInfo(magnetCount, totalCount, breadcrumbName, breadcrumbType)
}

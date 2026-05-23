package me.jbusdriver.modern.data.parser

import me.jbusdriver.modern.domain.model.Genre
import org.jsoup.nodes.Document

fun parseGenreCategories(doc: Document): List<Pair<String, List<Genre>>> {
    val genreBoxes = doc.select(".genre-box")
    val titles = genreBoxes.prev().map { it.text() }
    val genreLists = genreBoxes.map { box ->
        box.select("a").map { Genre(it.text(), it.attr("href")) }
    }
    return titles.zip(genreLists)
}

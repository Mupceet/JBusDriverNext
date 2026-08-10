package me.jbusdriver.modern.data.parser

import me.jbusdriver.modern.domain.model.ActressAttrs
import me.jbusdriver.modern.domain.model.ActressInfo
import org.jsoup.nodes.Document

fun parseActressList(doc: Document, baseUrl: String): List<ActressInfo> {
    return doc.select(".avatar-box").map {
        val img = it.select("img")
        ActressInfo(
            img.attr("title"),
            img.attr("src").wrapImage(baseUrl),
            it.attr("href")
        )
    }
}

fun parseActressAttrs(doc: Document, baseUrl: String): ActressAttrs {
    val frame = doc.select(".avatar-box")
    val photo = frame.select("img")
    val attrs = frame.select("p").map { it.text() }
    return ActressAttrs(
        photo.attr("title"),
        photo.attr("src").wrapImage(baseUrl),
        attrs
    )
}

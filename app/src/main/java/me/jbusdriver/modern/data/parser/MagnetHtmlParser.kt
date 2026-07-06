package me.jbusdriver.modern.data.parser

import me.jbusdriver.modern.domain.model.Magnet
import org.jsoup.Jsoup

fun parseMagnets(ajaxHtml: String): List<Magnet> {
    val doc = Jsoup.parse("<table>$ajaxHtml</table>")
    // JavBus's uncledatoolsbyajax.php returns magnet <tr> rows only — there is no
    // header row, so don't drop the first row. Any non-magnet row (e.g. a stray
    // header) carries no magnet <a> and is filtered out below by the blank link.
    return doc.select("table tr").asSequence().map { tr ->
        val tds = tr.select("td")
        Magnet(
            name = tds.getOrNull(0)?.text().orEmpty(),
            size = tds.getOrNull(1)?.text().orEmpty(),
            date = tds.getOrNull(2)?.text().orEmpty(),
            link = tr.select("a").attr("href").orEmpty()
        )
    }.filter { it.link.isNotBlank() }.toList()
}

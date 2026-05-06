package me.jbusdriver.modern.data.magnet.loaders

import me.jbusdriver.modern.KLog
import me.jbusdriver.modern.core.http.NetClient
import me.jbusdriver.modern.data.magnet.IMagnetLoader
import org.json.JSONObject
import org.jsoup.Jsoup

/**
 * 磁力链接加载器，直接使用预提取的 gid/uc 调用 AJAX 接口。
 *
 * 流程：调用 AJAX 接口 /ajax/uncledatoolsbyajax.php 获取磁力表格，Jsoup 解析提取磁力链接。
 * 相比旧实现省去了首次获取详情页的 HTTP 请求。
 */
class DefaultLoaderImpl : IMagnetLoader {

    override var hasNexPage: Boolean = false

    override suspend fun loadMagnets(key: String, page: Int): List<JSONObject> {
        // Fallback: fetch detail page to extract gid/uc, then call AJAX
        val html = NetClient.fetchHtml(key, showAll = true)
        val gid = Regex("""var\s+gid\s*=\s*(\d+)""").find(html)?.groupValues?.get(1) ?: return emptyList()
        val uc = Regex("""var\s+uc\s*=\s*(\d+)""").find(html)?.groupValues?.get(1) ?: "0"
        return fetchMagnetsAjax(gid, uc, key)
    }

    override suspend fun loadMagnetsWithParams(gid: String, uc: String, movieUrl: String): List<JSONObject> {
        return fetchMagnetsAjax(gid, uc, movieUrl)
    }

    private suspend fun fetchMagnetsAjax(gid: String, uc: String, movieUrl: String): List<JSONObject> {
        val baseUrl = NetClient.defaultFastUrl
        val floor = (Math.random() * 1000 + 1).toInt()
        val ajaxUrl = "$baseUrl/ajax/uncledatoolsbyajax.php?gid=$gid&lang=zh&uc=$uc&floor=$floor"

        KLog.d("Magnet: gid=$gid, uc=$uc, floor=$floor")

        val ajaxHtml = NetClient.fetchHtml(ajaxUrl, showAll = true, referer = "$baseUrl/")
        KLog.d("Magnet: ajax response length=${ajaxHtml.length}")

        val doc = Jsoup.parse("<table>${ajaxHtml}</table>")
        val rows = doc.select("table tr")
        KLog.d("Magnet: table tr count=${rows.size}")

        hasNexPage = false

        return rows.asSequence()
            .drop(1).map {
                val contents = it.select("td")
                val link = it.select("a").attr("href").orEmpty()
                JSONObject().apply {
                    put("name", contents.getOrNull(0)?.text().orEmpty())
                    put("size", contents.getOrNull(1)?.text().orEmpty())
                    put("date", contents.getOrNull(2)?.text().orEmpty())
                    put("link", link)
                }
            }.toList()
    }
}

package me.jbusdriver.modern.data.magnet.loaders

import me.jbusdriver.modern.KLog
import me.jbusdriver.modern.core.http.NetClient
import me.jbusdriver.modern.data.magnet.IMagnetLoader
import org.json.JSONObject
import org.jsoup.Jsoup

/**
 * 磁力链接加载器，通过两次 HTTP 请求获取磁力数据。
 *
 * 流程：
 * 1. 获取影片详情页 HTML，正则提取 gid/uc/img 参数
 * 2. 调用 AJAX 接口 /ajax/uncledatoolsbyajax.php 获取磁力表格
 * 3. Jsoup 解析 #magnet-table 提取磁力链接
 */
class DefaultLoaderImpl : IMagnetLoader {

    override var hasNexPage: Boolean = false

    override suspend fun loadMagnets(key: String, page: Int): List<JSONObject> {
        val html = NetClient.fetchHtml(key, showAll = true)

        val gid = Regex("""var\s+gid\s*=\s*(\d+)""").find(html)?.groupValues?.get(1) ?: return emptyList()
        val uc = Regex("""var\s+uc\s*=\s*(\d+)""").find(html)?.groupValues?.get(1) ?: "0"
        val img = Regex("""var\s+img\s*=\s*['"]([^'"]+)['"]""").find(html)?.groupValues?.get(1) ?: ""

        val baseUrl = NetClient.defaultFastUrl
        val floor = (Math.random() * 1000 + 1).toInt()
        val ajaxUrl = "$baseUrl/ajax/uncledatoolsbyajax.php?gid=$gid&lang=zh&img=$img&uc=$uc&floor=$floor"

        KLog.d("Magnet: gid=$gid, uc=$uc, img=$img, floor=$floor")

        val ajaxHtml = NetClient.fetchHtml(ajaxUrl, showAll = true, referer = "$baseUrl/")
        KLog.d("Magnet: ajax response length=${ajaxHtml.length}")

        // AJAX 返回裸 <tr> 行，无 <table> 包裹，需手动包装以保留 <tr>/<td> 结构
        val doc = Jsoup.parse("<table>${ajaxHtml}</table>")
        val rows = doc.select("table tr")
        KLog.d("Magnet: table tr count=${rows.size}")

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

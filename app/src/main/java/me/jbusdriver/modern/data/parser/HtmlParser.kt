package me.jbusdriver.modern.data.parser

import android.text.TextUtils
import me.jbusdriver.modern.KLog
import me.jbusdriver.modern.core.http.NetClient
import me.jbusdriver.modern.domain.model.*
import org.jsoup.Jsoup
import org.jsoup.nodes.Document

/**
 * HTML 解析工具集，将 Jsoup Document 转换为领域模型。
 *
 * 职责：集中管理所有 HTML → domain model 的解析逻辑，
 * 包括影片列表、影片详情、演员列表、演员详情、分页信息和类型分类。
 *
 * 使用场景：Repository 层获取 HTML 后调用此文件的顶层函数进行解析，
 * 所有 Jsoup CSS 选择器集中在此文件维护。
 *
 * 线程：纯函数无副作用，可在任意线程调用。调用方通常在 [Dispatchers.Default] 上执行解析。
 */

// region 分页解析

/**
 * 从 HTML 文档的分页组件解析当前页、下一页和可用页码信息。
 *
 * Jsoup 选择器说明：
 * - `.pagination .active > a`：当前活跃页码链接，`href` 包含页码
 * - `.pagination .active ~ li > a`：当前页之后的兄弟页码链接
 * - `.pagination a:not([id])`：所有非锚点页码链接
 *
 * @param doc 列表页 HTML 的 Jsoup Document
 * @return 分页信息，无分页组件时返回 null
 */
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

// endregion

// region 影片列表解析

/**
 * 从列表页 HTML 解析影片列表。
 *
 * Jsoup 选择器说明：
 * - `.movie-box`：每个影片卡片容器（`<a class="movie-box">`）
 * - `img`：封面图片，`title` 为影片标题，`src` 为图片 URL
 * - `date`：`<date>` 标签，第一个为番号，第二个为发行日期
 * - `href`：影片详情页链接
 * - `.item-tag`：标签容器，子元素文本为各标签名称
 * - `.photo-info button`：无 `.item-tag` 时的标签回退（如高清、新種等按钮）
 *
 * @param doc 列表页 HTML 的 Jsoup Document
 * @return 解析得到的影片列表
 */
fun loadMovieFromDoc(doc: Document): List<Movie> {
    return doc.select(".movie-box").map { element ->
        Movie(
            title = element.select("img").attr("title"),
            imageUrl = element.select("img").attr("src").wrapImage(),
            code = element.select("date").getOrNull(0)?.text() ?: "",
            date = element.select("date").getOrNull(1)?.text() ?: "",
            link = element.attr("href"),
            tags = element.select(".item-tag").firstOrNull()?.children()?.map { it.text() }
                ?: element.select(".photo-info button").map { it.text() }
        )
    }
}

// endregion

// region 影片详情解析

/**
 * 从影片详情页 HTML 解析完整的 [MovieDetail] 数据。
 *
 * Jsoup 选择器详细说明：
 *
 * 1. **影片主体容器**：`[class=row movie]`
 * 2. **影片标题**：`.bigImage img` 的 `title` 属性
 * 3. **封面大图**：`.bigImage` 的 `href` 属性
 * 4. **元信息容器**：`.info`，包含多个 `<p>` 标签
 * 5. **信息行**：`span.header` 的父 `<p>`，name 取 header 文本，value 取后续内容或链接文本
 * 6. **描述信息**：`[name=description]` 的 `content` 属性
 * 7. **类别标签**：`.genre:has(a[href*=genre])`
 * 8. **出演女优**：`#avatar-waterfall .avatar-box`
 * 9. **截图样本**：`#sample-waterfall .sample-box`
 * 10. **相关推荐**：`#related-waterfall .movie-box`
 *
 * @param doc 影片详情页 HTML 的 Jsoup Document
 * @return 解析得到的 [MovieDetail]
 */
fun parseMovieDetails(doc: Document): MovieDetail {
    val roeMovie = doc.select("[class=row movie]")
    val bigImage = roeMovie.select(".bigImage")
    val title = bigImage.select("img").attr("title")
    val cover = bigImage.attr("href").wrapImage()
    val coverImg = bigImage.select("img")
    val coverWidth = coverImg.attr("width").toIntOrNull() ?: 0
    val coverHeight = coverImg.attr("height").toIntOrNull() ?: 0

    val html = doc.html()
    val gid = Regex("""var\s+gid\s*=\s*(\d+)""").find(html)?.groupValues?.get(1)
    val uc = Regex("""var\s+uc\s*=\s*(\d+)""").find(html)?.groupValues?.get(1)

    val headers = mutableListOf<Header>()
    val headersContainer = roeMovie.select(".info")

    // Collect all <span class="header"> elements as info rows
    headersContainer.select("span.header").filterNot { it.parent()?.hasClass("star-show") == true }.forEach { span ->
        val p = span.parent() ?: return@forEach
        val name = span.text().trimEnd(':').trim()
        val linkEl = p.select("a").firstOrNull()
        val value = linkEl?.text() ?: p.text().removePrefix(span.text()).trim()
        val link = linkEl?.attr("href") ?: ""
        headers.add(Header(name, value, link))
    }

    val content = doc.select("[name=description]").attr("content")?.trim() ?: ""

    val geneses = headersContainer.select(".genre:has(a[href*=genre])").map {
        Genre(it.text(), it.select("a").attr("href"))
    }

    val actresses = doc.select("#avatar-waterfall .avatar-box").map {
        ActressInfo(it.text(), it.select("img").attr("src").wrapImage(), it.attr("href"))
    }

    val samples = doc.select("#sample-waterfall .sample-box").map {
        val thumb = it.select("img").attr("src").wrapImage()
        val image = it.attr("href").wrapImage()
        ImageSample(
            it.select("img").attr("title"),
            thumb,
            if (TextUtils.isEmpty(image)) thumb else image
        )
    }

    val relatedMovies = doc.select("#related-waterfall .movie-box").map {
        val url = it.attr("href")
        Movie(
            it.attr("title"),
            it.select("img").attr("src").wrapImage(),
            url.split("/").last(), "", url
        )
    }

    return MovieDetail(
        title, content, cover, headers, geneses, actresses, samples, relatedMovies,
        gid = gid,
        uc = uc,
        coverWidth = coverWidth,
        coverHeight = coverHeight
    )
}

// endregion

// region 演员解析

/**
 * 从演员列表页 HTML 解析演员列表。
 *
 * Jsoup 选择器说明：
 * - `.avatar-box`：每个演员卡片 `<a class="avatar-box">`
 * - `img`：头像图片，`title` 为演员名称，`src` 为头像 URL
 * - `href`：演员详情页链接
 * - `button`：操作按钮文本
 *
 * @param doc 演员列表页 HTML 的 Jsoup Document
 * @return 解析得到的演员信息列表
 */
fun parseActressList(doc: Document): List<ActressInfo> {
    return doc.select(".avatar-box")?.map {
        val img = it.select("img")
        ActressInfo(
            img.attr("title"), img.attr("src").wrapImage(),
            it.attr("href"), it.select("button").text()
        )
    } ?: emptyList()
}

/**
 * 从演员详情页 HTML 解析演员属性信息。
 *
 * Jsoup 选择器说明：
 * - `.avatar-box`：演员头像信息区域
 * - `img`：头像图片，`title` 为名称，`src` 为头像 URL
 * - `p`：属性信息段落（如 "身高: 165cm"）
 *
 * @param doc 演员详情页 HTML 的 Jsoup Document
 * @return 解析得到的 [ActressAttrs]
 */
fun parseActressAttrs(doc: Document): ActressAttrs {
    val frame = doc.select(".avatar-box")
    val photo = frame.select("img")
    val attrs = frame.select("p").map { it.text() }
    return ActressAttrs(
        photo.attr("title"),
        photo.attr("src").wrapImage(), attrs
    )
}

// endregion

// region 类型分类解析

/**
 * 从类型分类页 HTML 解析类型分组列表。
 *
 * Jsoup 选择器说明：
 * - `.genre-box`：类型列表容器
 * - `.genre-box` 的前一个兄弟元素：分组标题
 * - `.genre-box a`：类型链接，文本为名称，`href` 为列表页 URL
 *
 * @param doc 类型分类页 HTML 的 Jsoup Document
 * @return 分组标题与对应类型列表的配对
 */
fun parseGenreCategories(doc: Document): List<Pair<String, List<Genre>>> {
    val genreBoxes = doc.select(".genre-box")
    val titles = genreBoxes.prev().map { it.text() }
    val genreLists = genreBoxes.map { box ->
        box.select("a").map { Genre(it.text(), it.attr("href")) }
    }
    return titles.zip(genreLists)
}

// endregion

// region 磁力链接获取

/**
 * 通过站内 AJAX 接口获取磁力链接列表。
 *
 * 使用预提取的 gid/uc 参数直接请求 `/ajax/uncledatoolsbyajax.php`，
 * 解析返回的 HTML 表格为 [Magnet] 列表。
 *
 * @param gid 从详情页 HTML 提取的 gid 参数
 * @param uc 从详情页 HTML 提取的 uc 参数
 * @return 解析得到的磁力链接列表
 */
suspend fun fetchMagnets(gid: String, uc: String): List<Magnet> {
    val baseUrl = NetClient.defaultFastUrl
    val floor = (Math.random() * 1000 + 1).toInt()
    val ajaxUrl = "$baseUrl/ajax/uncledatoolsbyajax.php?gid=$gid&lang=zh&uc=$uc&floor=$floor"

    KLog.d("Magnet: gid=$gid, uc=$uc, floor=$floor")

    val ajaxHtml = NetClient.fetchHtml(ajaxUrl, showAll = true, referer = "$baseUrl/")
    KLog.d("Magnet: ajax response length=${ajaxHtml.length}")

    val doc = Jsoup.parse("<table>$ajaxHtml</table>")
    val rows = doc.select("table tr")
    KLog.d("Magnet: table tr count=${rows.size}")

    return rows.asSequence().drop(1).map { tr ->
        val tds = tr.select("td")
        Magnet(
            name = tds.getOrNull(0)?.text().orEmpty(),
            size = tds.getOrNull(1)?.text().orEmpty(),
            date = tds.getOrNull(2)?.text().orEmpty(),
            link = tr.select("a").attr("href").orEmpty()
        )
    }.filter { it.link.isNotBlank() }.toList()
}

// endregion

// region 筛选信息解析

/**
 * 从列表页 HTML 解析筛选信息（磁力影片数与全部影片数）。
 *
 * Jsoup 选择器说明：
 * - `.alert-success`：筛选提示栏容器
 * - `#resultshowmag`：已有磁力影片数
 * - `#resultshowall`：全部影片数
 *
 * @param doc 列表页 HTML 的 Jsoup Document
 * @return 筛选信息，无筛选提示栏时返回 null
 */
fun parseMovieFilterInfo(doc: Document): MovieFilterInfo? {
    val alert = doc.selectFirst(".alert-success") ?: return null
    val magnetText = alert.selectFirst("#resultshowmag")?.text() ?: return null
    val allText = alert.selectFirst("#resultshowall")?.text() ?: return null
    val magnetCount = magnetText.filter { it.isDigit() }.toIntOrNull() ?: return null
    val totalCount = allText.filter { it.isDigit() }.toIntOrNull() ?: return null
    return MovieFilterInfo(magnetCount, totalCount)
}

// endregion

// region URL 工具

/**
 * 将相对图片 URL 转换为完整的绝对 URL。
 *
 * - 以 `http` 开头：已是完整 URL，直接返回
 * - 以 `//` 开头：协议相对 URL，补全为 `https:`
 * - 其他情况：拼接当前服务器基地址
 */
fun String.wrapImage() = when {
    this.startsWith("http") -> this
    this.startsWith("//") -> "https:$this"
    else -> NetClient.defaultFastUrl + this
}

// endregion

package me.jbusdriver.modern.domain.model

import android.text.TextUtils
import me.jbusdriver.modern.data.remote.JAVBusService
import org.jsoup.nodes.Document

/**
 * 从影片详情页的 Jsoup [Document] 中解析完整的 [MovieDetail] 数据。
 *
 * 职责：将影片详情页 HTML 解析为结构化的领域模型，包含封面、元信息、类别、女优、截图和相关推荐。
 *
 * 使用场景：用户进入影片详情页时，Retrofit 获取 HTML 后调用此函数进行解析。
 *
 * Jsoup 选择器详细说明：
 *
 * 1. **影片主体容器**
 *    - `[class=row movie]`：定位影片详情的主容器 `<div class="row movie">`，包含封面和所有信息
 *
 * 2. **影片标题**
 *    - `.container h3`：页面容器下的 `<h3>` 标签，包含影片完整标题
 *
 * 3. **封面大图**
 *    - `.bigImage`（在 `[class=row movie]` 内）：封面大图的 `<a>` 链接，`href` 属性为大图 URL
 *
 * 4. **元信息容器**
 *    - `.info`（在 `[class=row movie]` 内）：信息面板 `<div class="info">`，包含多个 `<p>` 标签
 *
 * 5. **普通信息行（无跳转链接）**
 *    - `p[class!=star-show]:has(span:not([class=genre])):not(:has(a))`：
 *      排除 `class=star-show` 的段落，要求包含 `<span>` 但非类别标签，
 *      且不包含 `<a>` 链接。这些段落以 "名称: 值" 格式呈现（如 "時長: 120分鐘"）
 *
 * 6. **描述信息**
 *    - `[name=description]`：`<meta name="description">` 标签，`content` 属性为影片描述
 *
 * 7. **带跳转链接的信息行**
 *    - `p[class!=star-show]:has(span:not([class=genre])):has(a)`：
 *      与普通信息行类似，但包含 `<a>` 链接（如导演、制作商、发行商、系列等），
 *      链接通过 `p a` 选择器提取 `href` 属性
 *
 * 8. **类别标签**
 *    - `.genre:has(a[href*=genre])`：类别标签 `<span class="genre">`，要求包含指向 genre 页面的链接。
 *      文本为类别名称，`a` 的 `href` 为类别列表页 URL
 *
 * 9. **出演女优**
 *    - `#avatar-waterfall .avatar-box`：女优瀑布流区域 `#avatar-waterfall` 内的女优卡片，
 *      文本为女优名称，`img` 的 `src` 为头像，卡片自身的 `href` 为女优详情页链接
 *
 * 10. **截图样本**
 *     - `#sample-waterfall .sample-box`：截图瀑布流区域 `#sample-waterfall` 内的截图卡片。
 *       `img` 的 `src` 为缩略图，`title` 为标题，卡片自身的 `href` 为全尺寸图 URL。
 *       若 `href` 为空则回退使用缩略图 URL
 *
 * 11. **相关推荐影片**
 *     - `#related-waterfall .movie-box`：相关推荐区域 `#related-waterfall` 内的影片卡片。
 *       `href` 为影片链接，`title` 为标题，`img` 的 `src` 为封面。
 *       番号通过 URL 最后一段路径提取（如 "/ABCD-123" -> "ABCD-123"）
 *
 * @param doc 影片详情页的 Jsoup Document 对象
 * @return 解析得到的 [MovieDetail] 数据
 */
fun parseMovieDetails(doc: Document): MovieDetail {
    val roeMovie = doc.select("[class=row movie]")
    val title = doc.select(".container h3").text()
    val cover = roeMovie.select(".bigImage").attr("href").wrapImage()

    val headers = mutableListOf<Header>()
    val headersContainer = roeMovie.select(".info")

    headersContainer.select("p[class!=star-show]:has(span:not([class=genre])):not(:has(a))")
        .mapTo(headers) {
            val split = it.text().split(":")
            Header(split.first(), split.getOrNull(1)?.trim() ?: "", "")
        }

    val content = doc.select("[name=description]").attr("content")?.trim() ?: ""
    headers.add(Header("描述", content, ""))

    headersContainer.select("p[class!=star-show]:has(span:not([class=genre])):has(a)")
        .mapTo(headers) {
            val split = it.text().split(":")
            Header(
                split.first(), split.getOrNull(1)?.trim()
                    ?: "", it.select("p a").attr("href")
            )
        }

    val geneses = headersContainer.select(".genre:has(a[href*=genre])").map {
        Genre(it.text(), it.select("a").attr("href"))
    }

    val actresses = doc.select("#avatar-waterfall .avatar-box").map {
        ActressInfo(it.text(), it.select("img").attr("src").wrapImage(), it.attr("href"))
    }

    val samples = doc.select("#sample-waterfall .sample-box").map {
        val thumb = it.select("img").attr("src").wrapImage()
        val image = it.attr("href")
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

    return MovieDetail(title, content, cover, headers, geneses, actresses, samples, relatedMovies)
}

/**
 * 从女优详情页的 Jsoup [Document] 中解析女优属性信息。
 *
 * 职责：提取女优的头像、名称和基本属性列表（如身高、罩杯、出生日期等）。
 *
 * 使用场景：用户点击进入女优详情页时，解析 HTML 获取女优的基本属性。
 *
 * Jsoup 选择器详细说明：
 * - `.avatar-box`：定位女优头像信息区域 `<div class="avatar-box">`
 * - `img`（在 `.avatar-box` 内）：女优头像图片，`title` 属性为女优名称，`src` 为头像 URL
 * - `p`（在 `.avatar-box` 内）：属性信息段落，每个 `<p>` 的文本为一条属性（如 "身高: 165cm"）
 *
 * @param doc 女优详情页的 Jsoup Document 对象
 * @return 解析得到的 [ActressAttrs] 数据
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

/**
 * 从女优列表页的 Jsoup [Document] 中解析女优列表。
 *
 * 职责：提取女优列表页中所有女优卡片的信息。
 *
 * 使用场景：女优列表页（如 "有码女优"、"无码女优"）的数据解析。
 *
 * Jsoup 选择器详细说明：
 * - `.avatar-box`：定位页面中每个女优卡片 `<a class="avatar-box">`
 * - `img`（在 `.avatar-box` 内）：女优头像图片，`title` 属性为女优名称，`src` 为头像 URL
 * - `href`（在 `.avatar-box` 上）：女优详情页链接
 * - `button`（在 `.avatar-box` 内）：操作按钮的文本，用作女优的 [tag]
 *
 * @param doc 女优列表页的 Jsoup Document 对象
 * @return 解析得到的女优信息列表，无匹配时返回空列表
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
 * 将相对图片 URL 转换为完整的绝对 URL。
 *
 * 职责：统一处理三种 URL 格式——完整 URL、协议相对 URL、路径相对 URL——
 *       确保所有图片 URL 都是可访问的绝对路径。
 *
 * 使用场景：所有 HTML 解析函数中提取图片 URL 后调用，确保 URL 可被 Coil 等图片加载库使用。
 *
 * 处理逻辑：
 * - 以 `http` 开头：已是完整 URL，直接返回
 * - 以 `//` 开头：协议相对 URL，补全为 `https:`
 * - 其他情况（如 `/pics/123.jpg`）：拼接当前服务器基地址
 *
 * @receiver 原始图片 URL 字符串
 * @return 完整的绝对 URL
 */
fun String.wrapImage() = when {
    this.startsWith("http") -> this
    this.startsWith("//") -> "https:$this"
    else -> JAVBusService.defaultFastUrl + this
}

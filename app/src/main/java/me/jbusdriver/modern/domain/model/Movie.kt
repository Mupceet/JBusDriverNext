package me.jbusdriver.modern.domain.model

import androidx.compose.runtime.Immutable
import com.google.gson.annotations.SerializedName
import org.jsoup.nodes.Document
import java.util.Collections.emptyList

/**
 * 影片数据模型，表示列表页中的单个影片条目。
 *
 * 职责：封装影片的核心展示信息（标题、封面、番号、日期、标签等），
 *       实现 [ILink] 以支持导航到详情页和收藏功能。
 *
 * 使用场景：影片列表页（首页、类别页、搜索结果页）中每个影片卡片的数据载体；
 *           也用于影片详情页的"相关推荐"区域。
 *
 * 线程：标记为 [Immutable]，所有属性为 val，线程安全。
 *       [categoryId] 为可变属性（继承自 [ILink]），仅在初始化时赋值。
 *
 * @property title 影片标题
 * @property imageUrl 封面图片 URL
 * @property code 影片番号（如 "ABCD-123"）
 * @property date 影片发行日期
 * @property link 影片详情页 URL，序列化为 "detailUrl"
 * @property tags 影片标签列表，可为空
 */
@Immutable
data class Movie(
    val title: String,
    val imageUrl: String,
    val code: String,
    val date: String,
    @SerializedName("detailUrl") override val link: String,
    val tags: List<String>? = listOf(),
) : ILink {
    /** 所属收藏分类 ID，默认关联 [MovieCategory] */
    @Transient
    override var categoryId: Int = MovieCategory.id ?: 1
}

/**
 * 从 Jsoup [Document] 中解析影片列表。
 *
 * 职责：解析列表页 HTML，提取所有影片条目为 [Movie] 对象列表。
 *
 * 使用场景：首页、类别页、搜索结果页等列表页的 HTML 响应解析。
 *
 * Jsoup 选择器说明：
 * - `.movie-box`：定位页面中每个影片卡片容器（`<a class="movie-box">`）
 * - `img`（在 `.movie-box` 内）：封面图片，`title` 属性为影片标题，`src` 属性为图片 URL
 * - `date`（在 `.movie-box` 内）：`<date>` 标签，第一个为番号，第二个为发行日期
 * - `href`（在 `.movie-box` 上）：影片详情页链接
 * - `.item-tag`：标签容器，其子元素的文本为各标签名称
 *
 * @param str 列表页的 Jsoup Document 对象
 * @return 解析得到的影片列表
 */
fun loadMovieFromDoc(str: Document): List<Movie> {
    return str.select(".movie-box").mapIndexed { _, element ->
        Movie(
            title = element.select("img").attr("title"),
            imageUrl = element.select("img").attr("src").wrapImage(),
            code = element.select("date").first().text(),
            date = element.select("date").getOrNull(1)?.text() ?: "",
            link = element.attr("href"),
            tags = element.select(".item-tag").firstOrNull()?.children()?.map { it.text() }
                ?: emptyList()
        )
    }
}

/**
 * 创建用于分页导航的占位 [Movie] 实例。
 *
 * 职责：生成分页跳转用的虚拟影片条目，用 [page] 作为标题，
 *       [pages] 拼接为描述（以 `#` 分隔）。
 *
 * 使用场景：列表页底部分页器需要渲染页码按钮时，每页生成一个占位 Movie。
 *
 * @param page 页码
 * @param pages 该分页器包含的所有页码列表
 * @return 占位 Movie 实例
 */
fun newPageMovie(page: Int, pages: List<Int>) =
    Movie(page.toString(), pages.joinToString("#"), "", "", "")

/**
 * 影片的持久化唯一标识，由番号和日期拼接而成。
 *
 * 职责：为缓存和收藏去重提供稳定的 key。
 *
 * 使用场景：保存影片到收藏数据库时用作去重判断依据。
 */
val Movie.saveKey
    inline get() = code.trim() + "_" + date.trim()

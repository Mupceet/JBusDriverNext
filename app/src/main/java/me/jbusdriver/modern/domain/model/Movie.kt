package me.jbusdriver.modern.domain.model

import androidx.compose.runtime.Immutable
import com.google.gson.annotations.SerializedName

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
) : ILink

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

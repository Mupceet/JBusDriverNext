package me.jbusdriver.modern.domain.model

import androidx.compose.runtime.Immutable
import java.io.Serializable

/**
 * 影片详情数据模型，封装影片详情页的全部信息。
 *
 * 职责：作为影片详情页的完整数据载体，包含标题、描述、封面、元信息头部、
 *       类别、女优、截图样本和相关推荐影片。
 *
 * 使用场景：影片详情页 ViewModel 持有的数据模型，从 HTML 解析后传递给 Compose UI 渲染。
 *
 * 线程：标记为 [Immutable]，所有属性为 val，线程安全。
 *
 * @property title 影片标题
 * @property content 影片描述文本（来自 meta description）
 * @property cover 封面大图 URL
 * @property headers 元信息键值对列表（导演、制作商、发行商、系列等）
 * @property genres 类别标签列表
 * @property actress 出演女优列表
 * @property imageSamples 影片截图样本列表
 * @property relatedMovies 相关推荐影片列表
 * @property gid 影片全局 ID（用于磁力链接等场景），可选
 * @property uc 用户校验参数，可选
 * @property coverWidth 封面图宽度（像素），默认 0 表示未知
 * @property coverHeight 封面图高度（像素），默认 0 表示未知
 */
@Immutable
data class MovieDetail(
    val title: String,
    val content: String,
    val cover: String,
    val headers: List<Header>,
    val genres: List<Genre>,
    val actress: List<ActressInfo>,
    val imageSamples: List<ImageSample>,
    val relatedMovies: List<Movie>,
    val gid: String? = null,
    val uc: String? = null,
)

/**
 * 属性接口的标记父类型，用于 [ActressAttrs] 等属性数据模型。
 *
 * 职责：提供统一的序列化标记。
 *
 * 使用场景：女优详情属性数据（[ActressAttrs]）的公共父接口。
 */
interface IAttr : Serializable

/**
 * 元信息头部键值对，表示影片详情页中的一个信息行（如导演、发行商等）。
 *
 * 职责：封装影片详情中的单条元数据（名称-值-链接），可包含跳转链接。
 *
 * 使用场景：影片详情页的信息面板渲染，以及作为可收藏的链接项。
 *
 * 线程：标记为 [Immutable]，线程安全。
 *
 * @property name 信息项名称（如 "導演"、"製作商"）
 * @property value 信息项值
 * @property link 相关跳转链接，无链接时为空字符串
 */
@Immutable
data class Header(val name: String, val value: String, override val link: String) : ILink {
    /** 所属收藏分类 ID，默认关联 [LinkCategory] */
    @Transient
    override var categoryId: Int = LinkCategory.id ?: 10
}

/**
 * 类别标签，表示影片所属的一个分类（如 "高清"、"某系列" 等）。
 *
 * 职责：封装类别名称和对应的分类页面链接。
 *
 * 使用场景：影片详情页的类别标签列表渲染，以及作为可收藏的链接项。
 *
 * 线程：标记为 [Immutable]，线程安全。
 *
 * @property name 类别名称
 * @property link 类别列表页 URL
 */
@Immutable
data class Genre(val name: String, override val link: String) : ILink {
    /** 所属收藏分类 ID，默认关联 [LinkCategory] */
    @Transient
    override var categoryId: Int = LinkCategory.id ?: 10
}

/**
 * 女优信息，表示影片详情页中的一个出演女优。
 *
 * 职责：封装女优名称、头像和详情页链接。
 *
 * 使用场景：影片详情页的女优列表渲染、女优列表页数据模型、收藏功能。
 *
 * 线程：标记为 [Immutable]，[tag] 为可变属性用于运行时标记，需注意线程安全。
 *
 * @property name 女优名称
 * @property avatar 头像图片 URL
 * @property link 女优详情页 URL
 * @property tag 运行时标签（如列表页中的按钮文本），不参与序列化
 */
@Immutable
data class ActressInfo(
    val name: String,
    val avatar: String,
    override val link: String, @Transient var tag: String? = null
) : ILink {

    /** 所属收藏分类 ID，默认关联 [ActressCategory] */
    @Transient
    override var categoryId: Int = ActressCategory.id ?: 2

    override fun toString() =
        "ActressInfo(name='$name', avatar='$avatar', link='$link', tag=$tag  categoryId $categoryId) "
}

/**
 * 影片截图样本，包含缩略图和全尺寸图的 URL。
 *
 * 职责：封装影片截图的缩略图和原图信息，用于图片预览和全屏查看。
 *
 * 使用场景：影片详情页的截图瀑布流渲染、全屏图片查看器。
 *
 * 线程：标记为 [Immutable]，线程安全。
 *
 * @property title 截图标题
 * @property thumb 缩略图 URL
 * @property image 全尺寸图片 URL
 */
@Immutable
data class ImageSample(val title: String, val thumb: String, val image: String)

/**
 * 女优详情属性，包含女优的基本信息和属性列表。
 *
 * 职责：封装女优详情页解析后的属性数据。
 *
 * 使用场景：女优详情页展示女优头像、名称和属性信息（如身高、罩杯等）。
 *
 * 线程：data class，属性不可变，线程安全。
 *
 * @property title 女优名称
 * @property imageUrl 头像图片 URL
 * @property info 属性信息文本列表（如 "身高: 165cm", "罩杯: D" 等）
 */
data class ActressAttrs(val title: String, val imageUrl: String, val info: List<String>) : IAttr

package me.jbusdriver.modern.domain.model

import me.jbusdriver.modern.core.toJsonString
import me.jbusdriver.modern.core.urlPath
import me.jbusdriver.modern.data.db.entity.LinkItem
import me.jbusdriver.modern.core.http.NetClient

/**
 * 列表项展开类型：分组头部。
 *
 * 使用场景：多类型列表适配器中标识分组标题行。
 */
const val Expand_Type_Head = 0

/**
 * 列表项展开类型：普通数据项。
 *
 * 使用场景：多类型列表适配器中标识普通数据行。
 */
const val Expand_Type_Item = 1

/**
 * 获取 [ILink] 实例的人类可读描述文本。
 *
 * 职责：根据实际类型生成用于 UI 展示的描述字符串，如收藏列表、历史记录等。
 *
 * 使用场景：收藏列表页显示每个条目的描述信息。
 *
 * @return 类型对应的描述字符串
 * @throws IllegalStateException 如果遇到未知的 [ILink] 实现类
 */
val ILink.des: String
    inline get() = when (this) {
        is Header -> "$name $value"
        is Genre -> "类别 $name"
        is ActressInfo -> "演员 $name"
        is Movie -> "$code $title"
        is SearchLink -> "搜索 ${type.title} $query"
        is PageLink -> "$title 第 $page 页"
        else -> error(" $this has no matched class for des")
    }

/** 影片类型的数据库类型标识 */
const val MovieDBType = 1

/** 女优类型的数据库类型标识 */
const val ActressDBType = 2

/** 头部信息类型的数据库类型标识 */
const val HeaderDBType = 3

/** 类别标签类型的数据库类型标识 */
const val GenreDBType = 4

/** 搜索链接类型的数据库类型标识 */
const val SearchLinkDBType = 5

/** 分页链接类型的数据库类型标识 */
const val PageLinkDBType = 6

/**
 * 所有数据库类型标识的列表，用于遍历或统计。
 *
 * 使用场景：批量操作所有类型（如导出、清理）时遍历。
 */
val AllDBType by lazy {
    listOf(
        MovieDBType,
        ActressDBType,
        HeaderDBType,
        GenreDBType,
        SearchLinkDBType,
        PageLinkDBType
    )
}

/**
 * 获取 [ILink] 实例对应的数据库类型标识。
 *
 * 职责：将运行时类型映射为整型数据库类型常量，用于 Room 数据库的 type 字段。
 *
 * 使用场景：收藏入库时（[convertDBItem]）确定 [LinkItem.dbType] 值。
 *
 * @return 对应的数据库类型常量（[MovieDBType] 等）
 * @throws IllegalStateException 如果遇到未知的 [ILink] 实现类
 */
val ILink.DBtype: Int
    inline get() = when (this) {
        is Movie -> MovieDBType
        is ActressInfo -> ActressDBType
        is Header -> HeaderDBType
        is Genre -> GenreDBType
        is SearchLink -> SearchLinkDBType
        is PageLink -> PageLinkDBType
        else -> error(" $this has no matched class for des")
    }

/**
 * 获取 [ILink] 实例的唯一标识 key。
 *
 * 职责：为每个数据项生成用于去重的唯一 key。
 *       [SearchLink] 使用查询关键词作为 key，其他类型使用链接路径。
 *
 * 使用场景：收藏数据库中 [LinkItem.key] 字段的值，用于去重判断。
 *
 * @return 唯一标识字符串
 */
val ILink.uniqueKey: String
    inline get() = when (this) {
        is SearchLink -> query
        else -> link.urlPath
    }

/**
 * 将 [ILink] 实例转换为数据库实体 [LinkItem]。
 *
 * 职责：将领域模型转换为 Room 数据库实体，包含序列化的 JSON 数据和分类信息。
 *
 * 使用场景：用户点击"收藏"按钮时，将当前数据项转换后插入收藏数据库。
 *
 * @return 可直接插入数据库的 [LinkItem] 实体
 */
fun ILink.convertDBItem() = LinkItem(
    dbType = this.DBtype,
    createTime = System.currentTimeMillis(),
    key = this.uniqueKey,
    jsonStr = this.toJsonString(),
    categoryId = when {
        this is ICollectCategory && this.categoryId > 0 -> categoryId
        else -> AllFirstParentDBCategoryGroup[this.DBtype]?.id ?: LinkCategory.id ?: -1
    }
)

/**
 * 分页链接数据类，表示类别列表中的一个分页入口。
 *
 * 职责：封装分页导航信息（页码、类别名称、列表页 URL），用于类别浏览时的翻页。
 *
 * 使用场景：类别列表页（如某类别下的影片列表）的分页导航。
 *
 * @property page 页码
 * @property title 类别名称（如 "XX类型"）
 * @property link 该页的完整列表 URL
 */
data class PageLink(val page: Int, val title: String, override val link: String) : ILink {
    /** 所属收藏分类 ID，默认关联 [LinkCategory] */
    @Transient
    override var categoryId: Int = LinkCategory.id ?: 10
}

/**
 * 搜索链接数据类，表示一次搜索操作的可收藏快照。
 *
 * 职责：封装搜索类型和查询关键词，[link] 动态计算生成完整搜索 URL。
 *
 * 使用场景：搜索结果页可被收藏，收藏后通过此数据类保存搜索条件，
 *           再次点击时可恢复到相同的搜索结果。
 *
 * @property type 搜索维度（影片、女优、导演等）
 * @property query 搜索关键词
 */
data class SearchLink(val type: SearchType, var query: String) : ILink {
    /** 所属收藏分类 ID，默认关联 [LinkCategory] */
    @Transient
    override var categoryId: Int = LinkCategory.id ?: 10

    /** 动态计算的搜索 URL，基于当前生效的服务器地址和搜索路径模板 */
    override val link: String
        get() = "${NetClient.defaultFastUrl}${type.urlPathFormater.format(query)}"
}

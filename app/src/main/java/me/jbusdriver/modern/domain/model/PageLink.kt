package me.jbusdriver.modern.domain.model

import me.jbusdriver.modern.core.http.NetClient

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

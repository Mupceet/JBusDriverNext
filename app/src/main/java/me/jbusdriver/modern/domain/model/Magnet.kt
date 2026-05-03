package me.jbusdriver.modern.domain.model

/**
 * 磁力链接数据模型，表示一个从磁力搜索站点解析出的磁力资源条目。
 *
 * 职责：封装磁力链接的元数据（名称、大小、日期、链接地址），并实现 [ILink] 接口
 * 以复用收藏系统的统一数据流。
 *
 * 使用场景：[IMagnetLoader.loadMagnets] 返回磁力列表后在 UI 层展示；
 * 用户可将磁力链接添加到收藏，此时 [categoryId] 关联到当前选中的分类。
 *
 * 线程：纯数据类，无线程限制。
 */
data class Magnet(val name: String, val size: String, val date: String, override val link: String) :
    ILink {
    /**
     * 所属分类 ID，默认取 [LinkCategory] 当前选中的分类，若无则回退到 10。
     * 标记为 @Transient 以排除在 Gson 序列化之外。
     */
    @Transient
    override var categoryId: Int = LinkCategory.id ?: 10
}

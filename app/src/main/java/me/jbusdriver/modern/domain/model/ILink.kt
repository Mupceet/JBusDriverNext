package me.jbusdriver.modern.domain.model

import java.io.Serializable

/**
 * 可链接的数据项接口，代表所有拥有 URL 链接的领域模型。
 *
 * 职责：组合 [ICollectCategory] 的分类能力和 [Serializable] 的序列化能力，
 *       并统一声明 [link] 属性作为数据项的唯一访问路径。
 *
 * 使用场景：作为 [Movie]、[Header]、[Genre]、[ActressInfo]、[PageLink]、[SearchLink]
 *           等所有可导航/可收藏数据模型的公共父类型，在收藏、跳转、列表展示等场景中使用。
 *
 * 线程：不可变接口定义，线程安全。
 */
interface ILink : ICollectCategory, Serializable {
    /** 该数据项对应的目标页面 URL，如影片详情页、女优页、类别页等 */
    val link: String
}

package me.jbusdriver.modern.domain.model

import java.io.Serializable

/**
 * 可链接的数据项接口，代表所有拥有 URL 链接的领域模型。
 *
 * 职责：组合分类能力和序列化能力，
 *       并统一声明 [link] 属性作为数据项的唯一访问路径，
 *       [categoryId] 属性用于关联收藏分类。
 */
interface ILink : Serializable {
    /** 该数据项对应的目标页面 URL */
    val link: String

    /** 该数据项所属的收藏分类 ID */
    var categoryId: Int
}

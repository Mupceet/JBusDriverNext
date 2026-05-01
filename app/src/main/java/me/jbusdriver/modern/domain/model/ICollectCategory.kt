package me.jbusdriver.modern.domain.model

/**
 * 可收藏分类接口，为数据项提供所属分类的能力。
 *
 * 职责：定义 [categoryId] 可变属性，使实现类可被关联到数据库中的某个收藏分类。
 *
 * 使用场景：作为 [ILink] 的父接口，所有可收藏的数据模型（[Movie]、[Header]、[Genre]、
 *           [ActressInfo]、[PageLink]、[SearchLink]）均通过此接口获得分类归属。
 *           在收藏入库时（[convertDBItem]）用于确定 [categoryId]。
 *
 * 线程：[categoryId] 为可变属性（因 Room 实体需要），在外部赋值时应注意线程安全。
 */
interface ICollectCategory {
    /** 该数据项所属的收藏分类 ID，对应 [Category.id] */
    var categoryId: Int
}

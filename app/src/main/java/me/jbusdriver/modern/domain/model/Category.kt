package me.jbusdriver.modern.domain.model

import me.jbusdriver.modern.core.arrayMapof

/**
 * 收藏分类数据类，表示用户可创建的收藏夹分类。
 *
 * 职责：定义收藏分类的树形结构（通过 [tree] 路径表达层级关系），
 *       支持排序和嵌套分类。
 *
 * 使用场景：收藏管理界面展示分类列表，新建/编辑/删除收藏分类，
 *           以及 [ILink] 实现类（[Movie]、[ActressInfo] 等）通过 [id] 关联所属分类。
 *
 * 线程：[id] 和 [order] 为可变属性，在数据库操作中赋值，需注意线程安全。
 *
 * @property name 分类名称
 * @property pid 父分类 ID，-1 表示顶级分类
 * @property tree 层级路径（如 "1/"、"2/3/"），用 `/` 分隔各级分类 ID
 * @property order 排序权重，值越大越靠前
 */
data class Category(val name: String, val pid: Int = -1, val tree: String, var order: Int = 0) {
    /** 数据库自增 ID，由 Room 插入后赋值 */
    var id: Int? = null

    /** 分类深度（层级数），基于 [tree] 路径计算 */
    @delegate:Transient
    val depth: Int by lazy { tree.split("/").filter { it.isNotBlank() }.size }

    override fun equals(other: Any?) =
        other?.let { (it as? Category)?.id == this.id } ?: false
}

/**
 * 预留 [3..9] 的分类 ID 空间供未来扩展。
 *
 * 以下是系统内置的三个顶级分类：
 */

/** 影片收藏分类，ID = 1 */
val MovieCategory = Category("默认电影分类", -1, "1/", Int.MAX_VALUE).apply { id = 1 }

/** 女优收藏分类，ID = 2 */
val ActressCategory = Category("默认演员分类", -1, "2/", Int.MAX_VALUE).apply { id = 2 }

/** 链接收藏分类，ID = 10 */
val LinkCategory = Category("默认链接分类", -1, "10/", Int.MAX_VALUE).apply { id = 10 }

/**
 * 所有顶级收藏分类的映射表，key 为分类 ID。
 *
 * 职责：提供按 DB 类型快速查找对应默认分类的能力。
 *
 * 使用场景：在 [ILink.convertDBItem] 中，当数据项未指定分类时，
 *           根据 DB 类型从该映射表中查找默认分类。
 */
val AllFirstParentDBCategoryGroup by lazy { arrayMapof(1 to MovieCategory, 2 to ActressCategory, 10 to LinkCategory) }

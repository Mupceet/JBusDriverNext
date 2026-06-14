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
 * 不可变：所有字段均为构造参数，[id] 在构造时由调用方提供（内置分类）或由 Room 映射层填充。
 *
 * @property name 分类名称
 * @property pid 父分类 ID，-1 表示顶级分类
 * @property tree 层级路径（如 "1/"、"2/3/"），用 `/` 分隔各级分类 ID
 * @property order 排序权重，值越大越靠前
 * @property id 数据库自增 ID，内置分类在构造时指定
 */
data class Category(
    val name: String,
    val pid: Int = -1,
    val tree: String,
    val order: Int = 0,
    val id: Int? = null
) {
    /** 分类深度（层级数），基于 [tree] 路径计算 */
    @delegate:Transient
    val depth: Int by lazy { tree.split("/").filter { it.isNotBlank() }.size }
}

/**
 * 预留 [3..9] 的分类 ID 空间供未来扩展。
 *
 * 以下是系统内置的三个顶级分类：
 */

/** 影片收藏分类，ID = 1 */
val MovieCategory = Category("預設電影分類", -1, "1/", Int.MAX_VALUE, id = 1)

/** 無碼影片收藏分类，ID = 3 */
val UncensoredMovieCategory = Category("無碼影片分類", -1, "3/", Int.MAX_VALUE, id = 3)

/** 女优收藏分类，ID = 2 */
val ActressCategory = Category("預設演員分類", -1, "2/", Int.MAX_VALUE, id = 2)

/** 链接收藏分类，ID = 10 */
val LinkCategory = Category("預設連結分類", -1, "10/", Int.MAX_VALUE, id = 10)

/**
 * 所有顶级收藏分类的映射表，key 为分类 ID。
 *
 * 职责：提供按 DB 类型快速查找对应默认分类的能力。
 *
 * 使用场景：在 [ILink.convertDBItem] 中，当数据项未指定分类时，
 *           根据 DB 类型从该映射表中查找默认分类。
 */
val AllFirstParentDBCategoryGroup by lazy {
    arrayMapof(
        1 to MovieCategory,
        2 to ActressCategory,
        10 to LinkCategory
    )
}

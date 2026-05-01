package me.jbusdriver.modern.data.db.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.PrimaryKey

/**
 * 收藏分类的 Room 实体，对应 `t_category` 表。
 *
 * 职责：描述一个收藏分类的数据库结构，支持树形层级关系（通过 `tree` 字段记录路径）。
 *
 * 使用场景：用户在收藏管理界面创建、编辑、删除分类时读写此实体；
 * 收藏列表页按分类筛选 LinkItem 时作为分组依据。
 *
 * 线程：Room 会在调用线程执行 DAO 操作，UI 层应通过 ViewModel + 协程确保不在主线程写入。
 */
@Entity(tableName = "t_category")
data class Category(
    /** 主键，自增。新建分类时传入默认值 0 由 Room 自动生成。 */
    @PrimaryKey(autoGenerate = true) val id: Int = 0,
    /** 父分类 ID。-1 表示顶级分类。 */
    @ColumnInfo(name = "p_id") val pId: Int = -1,
    /** 分类显示名称。 */
    val name: String,
    /** 分类路径，格式如 "1/3/7"，记录从根到当前节点的完整路径。 */
    val tree: String,
    /** 同级排序权重，值越小越靠前。 */
    @ColumnInfo(name = "sort_order") val order: Int = 0
) {
    /**
     * 当前分类在树中的深度，通过 `tree` 路径段数计算。
     * 使用 lazy 委迟计算避免 Room 反序列化时的额外开销。
     */
    @delegate:Transient
    val depth: Int by lazy { tree.split("/").filter { it.isNotBlank() }.size }
}

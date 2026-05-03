package me.jbusdriver.modern.data.db.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

/**
 * 收藏条目的 Room 实体，对应 `t_link` 表。
 *
 * 职责：将用户收藏的影片、演员、类型等以 JSON 字符串持久化。
 * 反序列化逻辑见 [me.jbusdriver.modern.data.db.toILink] 扩展函数。
 * 通过 [key] 字段的唯一索引实现收藏去重。
 *
 * 使用场景：用户点击"收藏"按钮时写入；收藏列表页按分类读取展示。
 *
 * 线程：Room 在调用线程执行，DAO 操作应在后台线程执行。
 */
@Entity(
    tableName = "t_link",
    indices = [Index(value = ["key"], unique = true)]
)
data class LinkItem(
    /** 主键，自增。 */
    @PrimaryKey(autoGenerate = true) val id: Int = 0,
    /** 所属分类 ID，关联 [me.jbusdriver.modern.data.db.entity.Category.id]。-1 表示未分类。 */
    @ColumnInfo(name = "categoryId") val categoryId: Int = -1,
    /** 数据类型标识，对应 [me.jbusdriver.modern.data.db] 中定义的 `*DBType` 常量。 */
    @ColumnInfo(name = "dbType") val dbType: Int,
    /** 收藏时间戳（毫秒）。 */
    @ColumnInfo(name = "createTime") val createTime: Long = 0,
    /** 唯一标识键，用于去重（通常为影片/演员的 URL 路径）。 */
    val key: String,
    /** 领域对象的 JSON 序列化字符串。 */
    @ColumnInfo(name = "jsonStr") val jsonStr: String
)

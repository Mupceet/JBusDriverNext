package me.jbusdriver.modern.data.db.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.PrimaryKey

/**
 * 浏览历史的 Room 实体，对应 `t_history` 表。
 *
 * 职责：将用户浏览过的影片、演员、类型等记录以 JSON 字符串形式持久化。
 * 反序列化逻辑见 [me.jbusdriver.modern.data.db.toILink] 扩展函数。
 *
 * 使用场景：用户浏览详情页时自动写入历史记录；历史列表页读取并展示。
 *
 * 线程：Room 在调用线程执行，DAO 操作应在后台线程执行。
 */
@Entity(tableName = "t_history")
data class History(
    /** 主键，自增。 */
    @PrimaryKey(autoGenerate = true) val id: Int = 0,
    /** 数据类型标识，对应 [me.jbusdriver.modern.data.db] 中定义的 `*DBType` 常量。 */
    @ColumnInfo(name = "dbType") val dbType: Int,
    /** 记录创建时间戳（毫秒）。 */
    @ColumnInfo(name = "createTime") val createTime: Long = 0,
    /** 领域对象的 JSON 序列化字符串，用于反序列化恢复原始对象。 */
    @ColumnInfo(name = "jsonStr") val jsonStr: String,
    /** 是否为"全部"标记（预留字段），0 表示否，1 表示是。 */
    @ColumnInfo(name = "isAll") val isAll: Int
)

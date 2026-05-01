package me.jbusdriver.modern.data.db.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.PrimaryKey
import me.jbusdriver.modern.core.GSON
import me.jbusdriver.modern.core.fromJson
import me.jbusdriver.modern.domain.model.*

/**
 * 浏览历史的 Room 实体，对应 `t_history` 表。
 *
 * 职责：将用户浏览过的影片、演员、类型等记录以 JSON 字符串形式持久化，
 * 并在读取时根据 [dbType] 反序列化为对应的领域模型。
 *
 * 使用场景：用户浏览详情页时自动写入历史记录；历史列表页读取并展示。
 *
 * 线程：Room 在调用线程执行，DAO 操作应在后台线程执行。
 */
@Entity(tableName = "t_history")
data class History(
    /** 主键，自增。 */
    @PrimaryKey(autoGenerate = true) val id: Int = 0,
    /** 数据类型标识，对应 [Bean.kt] 中定义的 `*DBType` 常量（如 [MovieDBType]）。 */
    @ColumnInfo(name = "dbType") val dbType: Int,
    /** 记录创建时间戳（毫秒）。 */
    @ColumnInfo(name = "createTime") val createTime: Long = 0,
    /** 领域对象的 JSON 序列化字符串，用于反序列化恢复原始对象。 */
    @ColumnInfo(name = "jsonStr") val jsonStr: String,
    /** 是否为"全部"标记（预留字段），0 表示否，1 表示是。 */
    @ColumnInfo(name = "isAll") val isAll: Int
) {
    /**
     * 根据 [dbType] 将 [jsonStr] 反序列化为对应的 [ILink] 实现类。
     *
     * @return 反序列化后的领域对象
     * @throws IllegalStateException 当 [dbType] 不匹配任何已知类型时
     */
    fun getLinkItem(): ILink = when (dbType) {
        MovieDBType -> GSON.fromJson<Movie>(jsonStr)!!
        ActressDBType -> GSON.fromJson<ActressInfo>(jsonStr)!!
        HeaderDBType -> GSON.fromJson<Header>(jsonStr)!!
        GenreDBType -> GSON.fromJson<Genre>(jsonStr)!!
        SearchLinkDBType -> GSON.fromJson<SearchLink>(jsonStr)!!
        PageLinkDBType -> GSON.fromJson<PageLink>(jsonStr)!!
        else -> error("$dbType : $jsonStr has no matched class")
    }
}

package me.jbusdriver.modern.data.db.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey
import me.jbusdriver.modern.KLog
import me.jbusdriver.modern.core.GSON
import me.jbusdriver.modern.core.fromJson
import me.jbusdriver.modern.domain.model.ActressDBType
import me.jbusdriver.modern.domain.model.ActressInfo
import me.jbusdriver.modern.domain.model.Genre
import me.jbusdriver.modern.domain.model.GenreDBType
import me.jbusdriver.modern.domain.model.Header
import me.jbusdriver.modern.domain.model.HeaderDBType
import me.jbusdriver.modern.domain.model.ILink
import me.jbusdriver.modern.domain.model.Movie
import me.jbusdriver.modern.domain.model.MovieDBType
import me.jbusdriver.modern.domain.model.PageLink
import me.jbusdriver.modern.domain.model.PageLinkDBType
import me.jbusdriver.modern.domain.model.SearchLink
import me.jbusdriver.modern.domain.model.SearchLinkDBType

/**
 * 收藏条目的 Room 实体，对应 `t_link` 表。
 *
 * 职责：将用户收藏的影片、演员、类型等以 JSON 字符串持久化，
 * 并在读取时根据 [dbType] 反序列化为对应的领域模型。
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
    /** 所属分类 ID，关联 [Category.id]。-1 表示未分类。 */
    @ColumnInfo(name = "categoryId") val categoryId: Int = -1,
    /** 数据类型标识，对应 [Bean.kt] 中定义的 `*DBType` 常量。 */
    @ColumnInfo(name = "dbType") val dbType: Int,
    /** 收藏时间戳（毫秒）。 */
    @ColumnInfo(name = "createTime") val createTime: Long = 0,
    /** 唯一标识键，用于去重（通常为影片/演员的 URL 路径）。 */
    val key: String,
    /** 领域对象的 JSON 序列化字符串。 */
    @ColumnInfo(name = "jsonStr") val jsonStr: String
) {
    /**
     * 将数据库记录反序列化为 [ILink] 领域对象。
     * 反序列化失败时返回 null 并记录警告日志，避免因单条数据损坏导致列表崩溃。
     *
     * @return 反序列化后的领域对象，失败时返回 null
     */
    fun getLinkValue(): ILink? {
        return kotlin.runCatching {
            val link = doGet(dbType, jsonStr)
            link.categoryId = this.categoryId
            link
        }.onFailure {
            KLog.w("error getLinkValue : $this")
        }.getOrNull()
    }
}

/**
 * 根据 [type] 将 JSON 字符串反序列化为对应的 [ILink] 实现。
 *
 * @param type 数据类型标识
 * @param jsonStr JSON 序列化字符串
 * @return 反序列化后的领域对象
 * @throws IllegalStateException 当类型不匹配任何已知类型时
 */
private fun doGet(type: Int, jsonStr: String): ILink = when (type) {
    MovieDBType -> GSON.fromJson<Movie>(jsonStr)!!
    ActressDBType -> GSON.fromJson<ActressInfo>(jsonStr)!!
    HeaderDBType -> GSON.fromJson<Header>(jsonStr)!!
    GenreDBType -> GSON.fromJson<Genre>(jsonStr)!!
    SearchLinkDBType -> GSON.fromJson<SearchLink>(jsonStr)!!
    PageLinkDBType -> GSON.fromJson<PageLink>(jsonStr)!!
    else -> error("$type : $jsonStr has no matched class")
}

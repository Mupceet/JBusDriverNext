package me.jbusdriver.db.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey
import me.jbusdriver.base.GSON
import me.jbusdriver.base.fromJson
import me.jbusdriver.common.KLog
import me.jbusdriver.mvp.bean.*
import me.jbusdriver.mvp.bean.ILink

@Entity(
    tableName = "t_link",
    indices = [Index(value = ["key"], unique = true)]
)
data class LinkItem(
    @PrimaryKey(autoGenerate = true) val id: Int = 0,
    @ColumnInfo(name = "categoryId") val categoryId: Int = -1,
    @ColumnInfo(name = "dbType") val dbType: Int,
    @ColumnInfo(name = "createTime") val createTime: Long = 0,
    val key: String,
    @ColumnInfo(name = "jsonStr") val jsonStr: String
) {
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

private fun doGet(type: Int, jsonStr: String): ILink = when (type) {
    MovieDBType -> GSON.fromJson<Movie>(jsonStr)!!
    ActressDBType -> GSON.fromJson<ActressInfo>(jsonStr)!!
    HeaderDBType -> GSON.fromJson<Header>(jsonStr)!!
    GenreDBType -> GSON.fromJson<Genre>(jsonStr)!!
    SearchLinkDBType -> GSON.fromJson<SearchLink>(jsonStr)!!
    PageLinkDBType -> GSON.fromJson<PageLink>(jsonStr)!!
    else -> error("$type : $jsonStr has no matched class")
}

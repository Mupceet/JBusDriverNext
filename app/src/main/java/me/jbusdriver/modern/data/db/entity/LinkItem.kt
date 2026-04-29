package me.jbusdriver.modern.data.db.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey
import me.jbusdriver.modern.core.GSON
import me.jbusdriver.modern.core.fromJson
import me.jbusdriver.modern.KLog
import me.jbusdriver.modern.domain.model.*
import me.jbusdriver.modern.domain.model.ILink

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

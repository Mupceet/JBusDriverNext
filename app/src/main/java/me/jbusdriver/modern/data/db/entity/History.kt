package me.jbusdriver.modern.data.db.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.PrimaryKey
import me.jbusdriver.modern.core.GSON
import me.jbusdriver.modern.core.fromJson
import me.jbusdriver.modern.domain.model.*

@Entity(tableName = "t_history")
data class History(
    @PrimaryKey(autoGenerate = true) val id: Int = 0,
    @ColumnInfo(name = "dbType") val dbType: Int,
    @ColumnInfo(name = "createTime") val createTime: Long = 0,
    @ColumnInfo(name = "jsonStr") val jsonStr: String,
    @ColumnInfo(name = "isAll") val isAll: Int
) {
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

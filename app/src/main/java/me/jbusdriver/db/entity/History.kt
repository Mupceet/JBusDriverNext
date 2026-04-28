package me.jbusdriver.db.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.PrimaryKey
import me.jbusdriver.base.GSON
import me.jbusdriver.base.fromJson
import me.jbusdriver.mvp.bean.*

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

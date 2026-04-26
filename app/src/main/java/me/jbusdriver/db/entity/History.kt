package me.jbusdriver.db.entity

import android.content.Context
import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.PrimaryKey
import me.jbusdriver.base.GSON
import me.jbusdriver.base.fromJson
import me.jbusdriver.base.toast
import me.jbusdriver.mvp.bean.*
import me.jbusdriver.ui.activity.MovieDetailActivity
import me.jbusdriver.ui.activity.MovieListActivity

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

    fun move(context: Context) {
        when (dbType) {
            MovieDBType -> MovieDetailActivity.start(context, getLinkItem() as Movie, true)
            in ActressDBType..PageLinkDBType -> MovieListActivity.reloadFromHistory(context, this)
            else -> toast("没有可以跳转的界面")
        }
    }
}

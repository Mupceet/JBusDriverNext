package me.jbusdriver.modern.data.db.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import me.jbusdriver.modern.data.db.entity.ForumFavorite

/** 论坛收藏镜像表（按账号隔离）的读写操作；Room suspend 自带 IO 切换，调用方主线程安全。 */
@Dao
interface ForumFavoriteDao {
    /** 按页 upsert（正向知识：见过的收藏记录覆盖写入，不做整表替换）。 */
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertAll(favorites: List<ForumFavorite>)

    @Query("SELECT EXISTS(SELECT 1 FROM t_forum_favorite WHERE uid = :uid AND tid = :tid)")
    suspend fun isFavorite(uid: Int, tid: Int): Boolean

    /** 登出清空（收藏为账号私有数据，防换号串状态）。 */
    @Query("DELETE FROM t_forum_favorite")
    suspend fun clearAll()
}

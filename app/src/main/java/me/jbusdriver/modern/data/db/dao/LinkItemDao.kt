package me.jbusdriver.modern.data.db.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import io.reactivex.rxjava3.core.Flowable
import me.jbusdriver.modern.data.db.entity.LinkItem

@Dao
interface LinkItemDao {

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    fun insert(link: LinkItem): Long

    @Update
    fun update(link: LinkItem): Int

    @Query("DELETE FROM t_link WHERE dbType = :dbType AND key = :key")
    fun delete(dbType: Int, key: String): Int

    @Query("SELECT * FROM t_link ORDER BY id DESC")
    fun listAll(): Flowable<List<LinkItem>>

    @Query("SELECT * FROM t_link WHERE dbType = :dbType ORDER BY id DESC")
    fun listByType(dbType: Int): List<LinkItem>

    @Query("SELECT * FROM t_link WHERE dbType NOT IN (1, 2) ORDER BY id DESC")
    fun queryLink(): List<LinkItem>

    @Query("SELECT * FROM t_link WHERE categoryId = :categoryId ORDER BY id DESC")
    fun queryByCategoryId(categoryId: Int): List<LinkItem>

    @Query("UPDATE t_link SET categoryId = :setId WHERE categoryId = :categoryId AND dbType = :dbType")
    fun updateByCategoryId(categoryId: Int, dbType: Int, setId: Int): Int

    @Query("SELECT COUNT(1) FROM t_link WHERE dbType = :dbType AND key = :key")
    fun hasByKey(dbType: Int, key: String): Int
}

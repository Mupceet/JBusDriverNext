package me.jbusdriver.db.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import io.reactivex.rxjava3.core.Observable
import me.jbusdriver.db.entity.History

@Dao
interface HistoryDao {

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    fun insert(history: History): Long

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    fun insertAll(histories: List<History>): List<Long>

    @Query("UPDATE t_history SET dbType = :dbType, jsonStr = :jsonStr, isAll = :isAll WHERE id = :id")
    fun update(id: Int, dbType: Int, jsonStr: String, isAll: Int): Int

    @Query("SELECT * FROM t_history ORDER BY id DESC LIMIT :offset, :size")
    fun queryByLimit(size: Int, offset: Int): Observable<List<History>>

    @Query("SELECT COUNT(1) FROM t_history")
    fun count(): Int

    @Query("DELETE FROM t_history")
    fun deleteAll(): Int

    @Query("UPDATE sqlite_sequence SET seq = 0 WHERE name = 't_history'")
    fun resetAutoIncrement(): Int
}

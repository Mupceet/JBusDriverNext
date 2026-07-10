package me.jbusdriver.modern.data.db.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
import kotlinx.coroutines.flow.Flow
import me.jbusdriver.modern.data.db.entity.LocalVideoEntity

@Dao
interface LocalVideoDao {

    @Query("SELECT * FROM t_local_video WHERE code = :code ORDER BY name ASC")
    fun observeForCode(code: String): Flow<List<LocalVideoEntity>>

    @Query("SELECT COUNT(*) FROM t_local_video")
    fun observeCount(): Flow<Int>

    @Query("SELECT DISTINCT code FROM t_local_video")
    fun observeAllCodes(): Flow<List<String>>

    @Query("DELETE FROM t_local_video")
    suspend fun deleteAll()

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(items: List<LocalVideoEntity>)

    @Query("SELECT * FROM t_local_video")
    fun observeAll(): Flow<List<LocalVideoEntity>>

    @Query("SELECT * FROM t_local_video WHERE id IN (:ids)")
    suspend fun findByIds(ids: List<Int>): List<LocalVideoEntity>

    @Query("DELETE FROM t_local_video WHERE id IN (:ids)")
    suspend fun deleteByIds(ids: List<Int>)

    @Query(
        "UPDATE t_local_video SET title = :title, imageUrl = :imageUrl, " +
            "date = :date, censorType = :censorType WHERE code = :code"
    )
    suspend fun updateSnapshot(
        code: String,
        title: String,
        imageUrl: String,
        date: String,
        censorType: String?
    )

    @Transaction
    suspend fun replaceAll(items: List<LocalVideoEntity>) {
        deleteAll()
        if (items.isNotEmpty()) insertAll(items)
    }
}

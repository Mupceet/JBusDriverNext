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

    @Query("DELETE FROM t_local_video")
    suspend fun deleteAll()

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(items: List<LocalVideoEntity>)

    @Transaction
    suspend fun replaceAll(items: List<LocalVideoEntity>) {
        deleteAll()
        if (items.isNotEmpty()) insertAll(items)
    }
}

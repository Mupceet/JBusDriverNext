package me.jbusdriver.modern.data.db.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import kotlinx.coroutines.flow.Flow
import me.jbusdriver.modern.data.db.entity.LtxMovie

/**
 * LTX 补全快照表的 DAO。
 *
 * 职责：按番号读写 [LtxMovie] 补全快照，供 LTX 列表展示与后台补全队列使用。
 */
@Dao
interface LtxMovieDao {
    /** 写入/覆盖一条补全快照（主键为归一化番号）。 */
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(item: LtxMovie)

    /** 全量快照（一次性读取，用于补全队列比对）。 */
    @Query("SELECT * FROM t_ltx_movie")
    suspend fun getAll(): List<LtxMovie>

    /** 全量快照（响应式，用于列表随补全进展实时刷新）。 */
    @Query("SELECT * FROM t_ltx_movie")
    fun observeAll(): Flow<List<LtxMovie>>

    /** 按番号查单条。 */
    @Query("SELECT * FROM t_ltx_movie WHERE code = :code")
    suspend fun findByCode(code: String): LtxMovie?
}

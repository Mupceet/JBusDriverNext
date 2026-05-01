package me.jbusdriver.modern.data.db.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import kotlinx.coroutines.flow.Flow
import me.jbusdriver.modern.data.db.entity.History

/**
 * 职责：浏览历史表的 Room DAO 接口
 *
 * 使用场景：记录用户浏览过的页面，支持分页查询和清空
 * 线程：Room 自动处理，suspend 方法在调用方的 IO 调度器上执行
 */
@Dao
interface HistoryDao {

    /** 插入历史记录，主键冲突时忽略 */
    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insert(history: History): Long

    /** 批量插入历史记录 */
    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertAll(histories: List<History>): List<Long>

    /** 按 ID 更新历史记录内容 */
    @Query("UPDATE t_history SET dbType = :dbType, jsonStr = :jsonStr, isAll = :isAll WHERE id = :id")
    suspend fun update(id: Int, dbType: Int, jsonStr: String, isAll: Int): Int

    /**
     * 分页查询历史记录
     *
     * 使用 offset/size 分页模式，按 ID 倒序排列（最新在前）
     * 返回 Flow 实现数据变更的实时监听
     */
    @Query("SELECT * FROM t_history ORDER BY id DESC LIMIT :offset, :size")
    fun queryByLimit(size: Int, offset: Int): Flow<List<History>>

    /** 统计历史记录总数 */
    @Query("SELECT COUNT(1) FROM t_history")
    suspend fun count(): Int

    /** 清空所有历史记录 */
    @Query("DELETE FROM t_history")
    suspend fun deleteAll(): Int

    /** 重置自增序列，清空后 ID 从 1 重新开始 */
    @Query("UPDATE sqlite_sequence SET seq = 0 WHERE name = 't_history'")
    suspend fun resetAutoIncrement(): Int
}

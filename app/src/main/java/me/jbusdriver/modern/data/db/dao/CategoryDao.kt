package me.jbusdriver.modern.data.db.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import kotlinx.coroutines.flow.Flow
import me.jbusdriver.modern.data.db.entity.Category

/**
 * 职责：分类表的 Room DAO 接口
 *
 * 使用场景：CategoryService 封装调用，管理收藏分类的 CRUD
 * 线程：Room 自动处理，suspend 方法在调用方的 IO 调度器上执行，Flow 在 Main 收集
 */
@Dao
interface CategoryDao {

    /** 插入分类，主键冲突时忽略 */
    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insert(category: Category): Long

    /** 按 ID 删除分类 */
    @Query("DELETE FROM t_category WHERE id = :id")
    suspend fun delete(id: Int): Int

    /** 按 ID 查找分类 */
    @Query("SELECT * FROM t_category WHERE id = :id")
    suspend fun findById(id: Int): Category?

    /**
     * 按树形路径前缀查询分类列表
     *
     * 如 tree LIKE "1/" 可查出所有电影子分类
     * 返回 Flow 实现数据变更的实时监听
     */
    @Query("SELECT * FROM t_category WHERE tree LIKE :like ORDER BY sort_order DESC")
    fun queryTreeByLike(like: String): Flow<List<Category>>

    /** 更新分类 */
    @Update
    suspend fun update(category: Category): Int

    /** 批量插入分类，返回每条插入结果的 ID 列表 */
    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertAll(categories: List<Category>): List<Long>
}

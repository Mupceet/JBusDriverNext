package me.jbusdriver.modern.data.db.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import kotlinx.coroutines.flow.Flow
import me.jbusdriver.modern.data.db.entity.LinkItem

/**
 * 职责：收藏链接表的 Room DAO 接口
 *
 * 使用场景：CollectRepository 通过此 DAO 管理用户收藏的电影、演员、类别等
 * 线程：Room 自动处理，suspend 方法在调用方的 IO 调度器上执行，Flow 在 Main 收集
 */
@Dao
interface LinkItemDao {

    /** 插入收藏项，主键冲突时忽略（同一 key 不重复收藏） */
    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insert(link: LinkItem): Long

    /** 更新收藏项 */
    @Update
    suspend fun update(link: LinkItem): Int

    /** 按 dbType 和 key 删除收藏项 */
    @Query("DELETE FROM t_link WHERE dbType = :dbType AND key = :key")
    suspend fun delete(dbType: Int, key: String): Int

    /**
     * 查询所有收藏项
     *
     * 返回 Flow 实现数据变更的实时监听（如收藏/取消收藏时 UI 自动更新）
     */
    @Query("SELECT * FROM t_link ORDER BY id DESC")
    fun listAll(): Flow<List<LinkItem>>

    /**
     * 按类型查询收藏项
     *
     * @param dbType 数据类型（MovieDBType=1, ActressDBType=2 等）
     */
    @Query("SELECT * FROM t_link WHERE dbType = :dbType ORDER BY id DESC")
    suspend fun listByType(dbType: Int): List<LinkItem>

    /**
     * 按类型实时观察收藏项（Flow 查询），避免全表读取后在内存过滤。
     *
     * @param dbType 数据类型（MovieDBType=1, ActressDBType=2 等）
     */
    @Query("SELECT * FROM t_link WHERE dbType = :dbType ORDER BY id DESC")
    fun listByTypeFlow(dbType: Int): Flow<List<LinkItem>>

    /** 查询非电影/演员类型的收藏链接（如类别、搜索链接等） */
    @Query("SELECT * FROM t_link WHERE dbType NOT IN (1, 2) ORDER BY id DESC")
    suspend fun queryLink(): List<LinkItem>

    /** 按分类 ID 查询收藏项 */
    @Query("SELECT * FROM t_link WHERE categoryId = :categoryId ORDER BY id DESC")
    suspend fun queryByCategoryId(categoryId: Int): List<LinkItem>

    /** 批量更新分类 ID（移动分类时使用） */
    @Query("UPDATE t_link SET categoryId = :setId WHERE categoryId = :categoryId AND dbType = :dbType")
    suspend fun updateByCategoryId(categoryId: Int, dbType: Int, setId: Int): Int

    /** 按 (dbType, key) 更新单条收藏的分类 ID（用于在收藏页长按调整有码/无码）。返回受影响行数。 */
    @Query("UPDATE t_link SET categoryId = :categoryId WHERE dbType = :dbType AND key = :key")
    suspend fun updateCategoryByKey(dbType: Int, key: String, categoryId: Int): Int

    /**
     * 检查指定 key 是否已收藏
     *
     * @return >=1 表示已收藏，0 表示未收藏
     */
    @Query("SELECT COUNT(1) FROM t_link WHERE dbType = :dbType AND key = :key")
    suspend fun hasByKey(dbType: Int, key: String): Int
}

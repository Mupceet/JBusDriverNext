package me.jbusdriver.modern.data.db

import androidx.room.Database
import androidx.room.RoomDatabase
import me.jbusdriver.modern.data.db.dao.CategoryDao
import me.jbusdriver.modern.data.db.dao.LinkItemDao
import me.jbusdriver.modern.data.db.dao.LtxMovieDao
import me.jbusdriver.modern.data.db.entity.Category
import me.jbusdriver.modern.data.db.entity.LinkItem
import me.jbusdriver.modern.data.db.entity.LtxMovie

/**
 * 收藏数据库的 Room Database 定义。
 *
 * 职责：管理收藏相关的实体表（[Category]、[LinkItem]、[LtxMovie]），并提供对应的 DAO。
 *
 * 使用场景：由 [DatabaseModule] 创建实例并注入到 Hilt 依赖图中。
 * 数据库文件通过标准 Room 构建流程创建，迁移定义集中在数据库构建层。
 *
 * 线程：Room 保证数据库实例本身线程安全；DAO 操作的线程安全由调用方保证。
 */
@Database(entities = [Category::class, LinkItem::class, LtxMovie::class], version = 3, exportSchema = true)
abstract class CollectDatabase : RoomDatabase() {
    /** 提供分类表的增删改查操作。 */
    abstract fun categoryDao(): CategoryDao

    /** 提供收藏条目表的增删改查操作。 */
    abstract fun linkItemDao(): LinkItemDao

    /** 提供 LTX 补全快照表的读写操作。 */
    abstract fun ltxMovieDao(): LtxMovieDao
}

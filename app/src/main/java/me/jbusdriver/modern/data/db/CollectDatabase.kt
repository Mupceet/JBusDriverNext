package me.jbusdriver.modern.data.db

import androidx.room.Database
import androidx.room.RoomDatabase
import me.jbusdriver.modern.data.db.dao.CategoryDao
import me.jbusdriver.modern.data.db.dao.LinkItemDao
import me.jbusdriver.modern.data.db.entity.Category
import me.jbusdriver.modern.data.db.entity.LinkItem

/**
 * 收藏数据库的 Room Database 定义。
 *
 * 职责：管理收藏相关的两个实体表（[Category] 和 [LinkItem]），并提供对应的 DAO。
 *
 * 使用场景：由 [DB] 单例创建实例，通过 [DatabaseModule] 注入到 Hilt 依赖图中。
 * 数据库文件通过 [SDCardDatabaseContext] 存储在 SD 卡上，以在应用卸载后保留收藏数据。
 *
 * 线程：Room 保证数据库实例本身线程安全；DAO 操作的线程安全由调用方保证。
 */
@Database(entities = [Category::class, LinkItem::class], version = 1, exportSchema = true)
abstract class CollectDatabase : RoomDatabase() {
    /** 提供分类表的增删改查操作。 */
    abstract fun categoryDao(): CategoryDao

    /** 提供收藏条目表的增删改查操作。 */
    abstract fun linkItemDao(): LinkItemDao
}

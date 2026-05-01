package me.jbusdriver.modern.data.db

import androidx.room.Database
import androidx.room.RoomDatabase
import me.jbusdriver.modern.data.db.dao.HistoryDao
import me.jbusdriver.modern.data.db.entity.History

/**
 * JBus 主数据库的 Room Database 定义。
 *
 * 职责：管理浏览历史记录表（[History]），并提供对应的 DAO。
 *
 * 使用场景：由 [DB] 单例创建实例，通过 [DatabaseModule] 注入到 Hilt 依赖图中。
 * 数据库文件存储在应用内部存储，跟随应用卸载而删除。
 *
 * 线程：Room 保证数据库实例本身线程安全；DAO 操作的线程安全由调用方保证。
 */
@Database(entities = [History::class], version = 1, exportSchema = true)
abstract class JBusDatabase : RoomDatabase() {
    /** 提供历史记录表的增删改查操作。 */
    abstract fun historyDao(): HistoryDao
}

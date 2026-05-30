package me.jbusdriver.modern.data.db

import android.annotation.SuppressLint
import androidx.room.Room
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import me.jbusdriver.modern.JBus
import me.jbusdriver.modern.KLog
import me.jbusdriver.modern.data.db.DB.collectDatabase
import me.jbusdriver.modern.data.db.DB.jBusDatabase

/**
 * 数据库全局入口单例，提供 [JBusDatabase] 和 [CollectDatabase] 的延迟初始化实例及 DAO 访问。
 *
 * 职责：集中管理两个 Room 数据库的创建和 DAO 获取，避免各处重复构建数据库实例。
 *
 * 使用场景：[DatabaseModule] 通过此单例获取数据库和 DAO 实例并提供给 Hilt 依赖图；
 * 少数遗留代码可能直接通过 `DB.xxxDao` 访问。
 *
 * 线程：数据库实例通过 lazy 委迟初始化，线程安全。
 * 注意 `allowMainThreadQueries()` 仅为兼容遗留代码，新代码应在后台线程执行查询。
 */
@SuppressLint("CheckResult")
object DB {
    /** JBusDatabase 的文件名。 */
    private const val JBUS_DB_NAME = "jbusdriver.db"

    /** 历史记录数据库，存储在应用内部存储。 */
    val jBusDatabase: JBusDatabase by lazy {
        Room.databaseBuilder(
            JBus,
            JBusDatabase::class.java,
            JBUS_DB_NAME
        ).apply {
            if (me.jbusdriver.BuildConfig.DEBUG) {
                KLog.d("JBusDatabase debug mode enabled")
            }
        }.build()
    }

    /** 收藏数据库的文件名。 */
    private const val COLLECT_DB_NAME = "collect.db"

    /** 收藏数据库 1→2 迁移：将唯一索引从仅 [key] 扩展为 ([dbType], [key]) 联合索引。 */
    private val COLLECT_MIGRATION_1_2 = object : Migration(1, 2) {
        override fun migrate(db: SupportSQLiteDatabase) {
            db.execSQL("DROP INDEX IF EXISTS `index_t_link_key`")
            db.execSQL("CREATE UNIQUE INDEX IF NOT EXISTS `index_t_link_dbType_key` ON `t_link` (`dbType`, `key`)")
        }
    }

    val collectDatabase: CollectDatabase by lazy {
        Room.databaseBuilder(
            JBus,
            CollectDatabase::class.java,
            COLLECT_DB_NAME
        ).addMigrations(COLLECT_MIGRATION_1_2).build()
    }

    /** 历史记录 DAO，代理到 [jBusDatabase]。 */
    val historyDao: me.jbusdriver.modern.data.db.dao.HistoryDao by lazy { jBusDatabase.historyDao() }

    /** 分类 DAO，代理到 [collectDatabase]。 */
    val categoryDao: me.jbusdriver.modern.data.db.dao.CategoryDao by lazy { collectDatabase.categoryDao() }

    /** 收藏条目 DAO，代理到 [collectDatabase]。 */
    val linkDao: me.jbusdriver.modern.data.db.dao.LinkItemDao by lazy { collectDatabase.linkItemDao() }
}

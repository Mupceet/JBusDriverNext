package me.jbusdriver.modern.data.db

import android.content.Context
import androidx.room.Room
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import me.jbusdriver.modern.KLog

private const val JBUS_DB_NAME = "jbusdriver.db"
private const val COLLECT_DB_NAME = "collect.db"
private const val LOCAL_VIDEO_DB_NAME = "local_video.db"

internal val COLLECT_MIGRATION_1_2 = object : Migration(1, 2) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL("DROP INDEX IF EXISTS `index_t_link_key`")
        db.execSQL("CREATE UNIQUE INDEX IF NOT EXISTS `index_t_link_dbType_key` ON `t_link` (`dbType`, `key`)")
    }
}

/** v3：新增 LTX 补全快照表（收藏 tab LTX 页的番号 → 站内影片快照缓存）。 */
internal val COLLECT_MIGRATION_2_3 = object : Migration(2, 3) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL(
            """
            CREATE TABLE IF NOT EXISTS `t_ltx_movie` (
                `code` TEXT NOT NULL,
                `resolveState` TEXT NOT NULL,
                `movieUrlPath` TEXT NOT NULL,
                `movieJson` TEXT NOT NULL,
                `resolvedAt` INTEGER NOT NULL,
                PRIMARY KEY(`code`)
            )
            """.trimIndent()
        )
    }
}

internal val LOCAL_VIDEO_MIGRATION_1_2 = object : Migration(1, 2) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL("ALTER TABLE t_local_video ADD COLUMN title TEXT")
        db.execSQL("ALTER TABLE t_local_video ADD COLUMN imageUrl TEXT")
        db.execSQL("ALTER TABLE t_local_video ADD COLUMN date TEXT")
        db.execSQL("ALTER TABLE t_local_video ADD COLUMN censorType TEXT")
    }
}

fun buildJBusDatabase(context: Context): JBusDatabase =
    Room.databaseBuilder(
        context,
        JBusDatabase::class.java,
        JBUS_DB_NAME
    ).apply {
        if (me.jbusdriver.BuildConfig.DEBUG) {
            KLog.d("JBusDatabase debug mode enabled")
        }
    }.build()

fun buildCollectDatabase(context: Context): CollectDatabase =
    Room.databaseBuilder(
        context,
        CollectDatabase::class.java,
        COLLECT_DB_NAME
    ).addMigrations(COLLECT_MIGRATION_1_2, COLLECT_MIGRATION_2_3).build()

fun buildLocalVideoDatabase(context: Context): LocalVideoDatabase =
    Room.databaseBuilder(
        context,
        LocalVideoDatabase::class.java,
        LOCAL_VIDEO_DB_NAME
    ).addMigrations(LOCAL_VIDEO_MIGRATION_1_2).build()

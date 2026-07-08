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
    ).addMigrations(COLLECT_MIGRATION_1_2).build()

fun buildLocalVideoDatabase(context: Context): LocalVideoDatabase =
    Room.databaseBuilder(
        context,
        LocalVideoDatabase::class.java,
        LOCAL_VIDEO_DB_NAME
    ).build()

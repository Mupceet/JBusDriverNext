package me.jbusdriver.db

import android.annotation.SuppressLint
import androidx.room.Room
import me.jbusdriver.base.db.SDCardDatabaseContext
import me.jbusdriver.common.JBus
import me.jbusdriver.common.KLog
import java.io.File

@SuppressLint("CheckResult")
object DB {
    private const val JBUS_DB_NAME = "jbusdriver.db"

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

    private const val COLLECT_DB_NAME = "collect.db"

    val collectDatabase: CollectDatabase by lazy {
        val context = object : SDCardDatabaseContext(JBus) {
            override val dir: String = JBus.packageName + File.separator + "collect"
        }
        Room.databaseBuilder(
            context,
            CollectDatabase::class.java,
            COLLECT_DB_NAME
        ).build()
    }

    val historyDao: me.jbusdriver.db.dao.HistoryDao by lazy { jBusDatabase.historyDao() }
    val categoryDao: me.jbusdriver.db.dao.CategoryDao by lazy { collectDatabase.categoryDao() }
    val linkDao: me.jbusdriver.db.dao.LinkItemDao by lazy { collectDatabase.linkItemDao() }
}

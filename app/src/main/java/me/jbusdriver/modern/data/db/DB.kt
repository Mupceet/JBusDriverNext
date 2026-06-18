package me.jbusdriver.modern.data.db

import android.annotation.SuppressLint
import me.jbusdriver.modern.JBus
import me.jbusdriver.modern.data.db.dao.CategoryDao
import me.jbusdriver.modern.data.db.dao.HistoryDao
import me.jbusdriver.modern.data.db.dao.LinkItemDao

/**
 * Legacy database entry point.
 *
 * New production code should depend on Hilt-provided databases or DAOs from
 * DatabaseModule. This object remains only for migration compatibility with
 * older call sites and delegates to the same Room builders used by Hilt.
 */
@SuppressLint("CheckResult")
object DB {
    val jBusDatabase: JBusDatabase by lazy { buildJBusDatabase(JBus) }

    val collectDatabase: CollectDatabase by lazy { buildCollectDatabase(JBus) }

    val historyDao: HistoryDao by lazy { jBusDatabase.historyDao() }

    val categoryDao: CategoryDao by lazy { collectDatabase.categoryDao() }

    val linkDao: LinkItemDao by lazy { collectDatabase.linkItemDao() }
}

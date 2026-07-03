package me.jbusdriver.modern.data.db

import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import me.jbusdriver.modern.data.db.dao.HistoryDao
import me.jbusdriver.modern.test.aHistory
import me.jbusdriver.modern.test.buildJBusDb
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class HistoryDaoTest {
    private val context = InstrumentationRegistry.getInstrumentation().targetContext
    private lateinit var db: JBusDatabase
    private lateinit var dao: HistoryDao

    @Before
    fun setup() {
        db = buildJBusDb(context)
        dao = db.historyDao()
    }

    @After
    fun teardown() = db.close()

    @Test
    fun insert_and_count() = runTest {
        assertEquals(0, dao.count())
        dao.insert(aHistory(dbType = 1, jsonStr = "{}"))
        dao.insert(aHistory(dbType = 2, jsonStr = "{}"))
        assertEquals(2, dao.count())
    }

    @Test
    fun queryByLimit_orders_newest_first_and_paginates() = runTest {
        (1..5).forEach { dao.insert(aHistory(dbType = it, jsonStr = "{$it}")) }
        // ORDER BY id DESC → newest (id=5) first
        val page1 = dao.queryByLimit(size = 2, offset = 0).first().map { it.jsonStr }
        val page2 = dao.queryByLimit(size = 2, offset = 2).first().map { it.jsonStr }
        assertEquals(listOf("{5}", "{4}"), page1)
        assertEquals(listOf("{3}", "{2}"), page2)
    }

    @Test
    fun update_overwrites_fields() = runTest {
        val id = dao.insert(aHistory(dbType = 1, jsonStr = "old", isAll = 0)).toInt()
        dao.update(id = id, dbType = 2, jsonStr = "new", isAll = 1)
        val row = dao.queryByLimit(size = 1, offset = 0).first().first()
        assertEquals(2, row.dbType)
        assertEquals("new", row.jsonStr)
        assertEquals(1, row.isAll)
    }

    @Test
    fun deleteAll_then_resetAutoIncrement_restarts_ids() = runTest {
        dao.insertAll(listOf(aHistory(dbType = 1), aHistory(dbType = 2)))
        assertEquals(2, dao.count())
        dao.deleteAll()
        assertEquals(0, dao.count())
        dao.resetAutoIncrement()
        val newId = dao.insert(aHistory(dbType = 1)).toInt()
        assertEquals(1, newId)
    }

    @Test
    fun queryByLimit_reflects_deleteAll() = runTest {
        dao.insert(aHistory(dbType = 1, jsonStr = "{}"))
        assertFalse(dao.queryByLimit(size = 10, offset = 0).first().isEmpty())
        dao.deleteAll()
        assertTrue(dao.queryByLimit(size = 10, offset = 0).first().isEmpty())
    }
}

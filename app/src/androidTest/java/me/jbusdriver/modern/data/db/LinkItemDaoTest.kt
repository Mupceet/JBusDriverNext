package me.jbusdriver.modern.data.db

import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import me.jbusdriver.modern.data.db.dao.LinkItemDao
import me.jbusdriver.modern.test.aLinkItem
import me.jbusdriver.modern.test.buildCollectDb
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class LinkItemDaoTest {
    private val context = InstrumentationRegistry.getInstrumentation().targetContext
    private lateinit var db: CollectDatabase
    private lateinit var dao: LinkItemDao

    @Before
    fun setup() {
        db = buildCollectDb(context)
        dao = db.linkItemDao()
    }

    @After
    fun teardown() = db.close()

    @Test
    fun listByType_filters_and_orders_desc() = runTest {
        dao.insert(aLinkItem(dbType = MovieDBType, key = "m1"))
        dao.insert(aLinkItem(dbType = MovieDBType, key = "m2"))
        dao.insert(aLinkItem(dbType = ActressDBType, key = "a1"))
        // ORDER BY id DESC → m2 before m1
        assertEquals(listOf("m2", "m1"), dao.listByType(MovieDBType).map { it.key })
    }

    @Test
    fun insert_ignores_duplicate_dbType_key() = runTest {
        dao.insert(aLinkItem(dbType = MovieDBType, key = "dup"))
        val second = dao.insert(aLinkItem(dbType = MovieDBType, key = "dup"))
        assertEquals(-1L, second)
        assertEquals(1, dao.hasByKey(MovieDBType, "dup"))
    }

    @Test
    fun insert_allows_same_key_under_different_dbType() = runTest {
        dao.insert(aLinkItem(dbType = MovieDBType, key = "shared"))
        val second = dao.insert(aLinkItem(dbType = ActressDBType, key = "shared"))
        assertTrue(second > 0)
    }

    @Test
    fun hasByKey_reports_presence() = runTest {
        assertEquals(0, dao.hasByKey(MovieDBType, "missing"))
        dao.insert(aLinkItem(dbType = MovieDBType, key = "present"))
        assertTrue(dao.hasByKey(MovieDBType, "present") >= 1)
    }

    @Test
    fun queryLink_excludes_movie_and_actress_types() = runTest {
        dao.insert(aLinkItem(dbType = MovieDBType, key = "movie"))
        dao.insert(aLinkItem(dbType = ActressDBType, key = "actress"))
        dao.insert(aLinkItem(dbType = GenreDBType, key = "genre"))
        assertEquals(listOf(GenreDBType), dao.queryLink().map { it.dbType })
    }

    @Test
    fun queryByCategoryId_and_updateByCategoryId() = runTest {
        dao.insert(aLinkItem(dbType = MovieDBType, key = "m1", categoryId = 5))
        dao.insert(aLinkItem(dbType = MovieDBType, key = "m2", categoryId = 5))
        dao.insert(aLinkItem(dbType = MovieDBType, key = "m3", categoryId = 9))
        assertEquals(listOf("m2", "m1"), dao.queryByCategoryId(5).map { it.key })

        val updated = dao.updateByCategoryId(categoryId = 5, dbType = MovieDBType, setId = 9)
        assertEquals(2, updated)
        assertEquals(0, dao.queryByCategoryId(5).size)
        assertEquals(3, dao.queryByCategoryId(9).size)
    }

    @Test
    fun delete_removes_specific_dbType_key() = runTest {
        dao.insert(aLinkItem(dbType = MovieDBType, key = "target"))
        dao.insert(aLinkItem(dbType = MovieDBType, key = "keep"))
        assertEquals(1, dao.delete(MovieDBType, "target"))
        assertEquals(0, dao.hasByKey(MovieDBType, "target"))
        assertEquals(1, dao.hasByKey(MovieDBType, "keep"))
    }

    @Test
    fun listAll_reflects_insert_and_delete() = runTest {
        assertTrue(dao.listAll().first().isEmpty())
        dao.insert(aLinkItem(dbType = MovieDBType, key = "x"))
        assertEquals(listOf("x"), dao.listAll().first().map { it.key })
        dao.delete(MovieDBType, "x")
        assertTrue(dao.listAll().first().isEmpty())
    }
}

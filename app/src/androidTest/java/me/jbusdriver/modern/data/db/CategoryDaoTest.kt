package me.jbusdriver.modern.data.db

import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import me.jbusdriver.modern.data.db.dao.CategoryDao
import me.jbusdriver.modern.test.aCategory
import me.jbusdriver.modern.test.buildCollectDb
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class CategoryDaoTest {
    private val context = InstrumentationRegistry.getInstrumentation().targetContext
    private lateinit var db: CollectDatabase
    private lateinit var dao: CategoryDao

    @Before
    fun setup() {
        db = buildCollectDb(context)
        dao = db.categoryDao()
    }

    @After
    fun teardown() = db.close()

    @Test
    fun insert_then_findById() = runTest {
        val id = dao.insert(aCategory(name = "Movies", tree = "1/"))
        val found = dao.findById(id.toInt())
        assertEquals("Movies", found?.name)
    }

    @Test
    fun insert_ignores_duplicate_primary_key() = runTest {
        dao.insert(aCategory(id = 10, name = "A", tree = "1/"))
        val secondId = dao.insert(aCategory(id = 10, name = "B", tree = "1/"))
        assertEquals(-1L, secondId)
        assertEquals("A", dao.findById(10)?.name)
    }

    @Test
    fun queryTreeByLike_filters_prefix_and_orders_by_sort_order_desc() = runTest {
        dao.insertAll(
            listOf(
                aCategory(name = "c1", tree = "1/2", order = 1),
                aCategory(name = "c2", tree = "1/3", order = 5),
                aCategory(name = "other", tree = "9/1", order = 0)
            )
        )
        val result = dao.queryTreeByLike("1/%").first().map { it.name }
        assertEquals(listOf("c2", "c1"), result)
    }

    @Test
    fun update_changes_fields() = runTest {
        val id = dao.insert(aCategory(name = "old", tree = "1/")).toInt()
        dao.update(aCategory(id = id, name = "new", tree = "1/"))
        assertEquals("new", dao.findById(id)?.name)
    }

    @Test
    fun delete_removes_row() = runTest {
        val id = dao.insert(aCategory(name = "x", tree = "1/")).toInt()
        assertEquals(1, dao.delete(id))
        assertNull(dao.findById(id))
    }

    @Test
    fun queryTreeByLike_reflects_new_insert() = runTest {
        assertTrue(dao.queryTreeByLike("1/%").first().isEmpty())
        dao.insert(aCategory(name = "only", tree = "1/5"))
        assertEquals(listOf("only"), dao.queryTreeByLike("1/%").first().map { it.name })
    }
}

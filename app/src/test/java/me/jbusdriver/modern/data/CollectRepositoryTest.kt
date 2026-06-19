package me.jbusdriver.modern.data

import me.jbusdriver.modern.data.repository.CollectRepository
import me.jbusdriver.modern.data.repository.CollectTransactionRunner
import me.jbusdriver.modern.data.repository.DefaultCollectRepository
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import me.jbusdriver.modern.core.site.SiteConfig
import me.jbusdriver.modern.data.db.ActressDBType
import me.jbusdriver.modern.data.db.MovieDBType
import me.jbusdriver.modern.data.db.convertDBItem
import me.jbusdriver.modern.data.db.dao.LinkItemDao
import me.jbusdriver.modern.data.db.entity.LinkItem
import me.jbusdriver.modern.domain.model.ActressInfo
import me.jbusdriver.modern.domain.model.Movie
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class CollectRepositoryTest {

    private lateinit var repository: FakeCollectRepository

    @Before
    fun setUp() {
        repository = FakeCollectRepository()
    }

    @Test
    fun toggleMovieCollect_addsWhenNotCollected() = runTest {
        val movie = Movie("Test", "http://img.jpg", "ABC-001", "2024-01-01", "http://link1")

        assertFalse(repository.isMovieCollected(movie))
        val result = repository.toggleMovieCollect(movie)
        assertTrue(result)
        assertTrue(repository.isMovieCollected(movie))
    }

    @Test
    fun toggleMovieCollect_removesWhenCollected() = runTest {
        val movie = Movie("Test", "http://img.jpg", "ABC-001", "2024-01-01", "http://link1")

        repository.toggleMovieCollect(movie) // add
        assertTrue(repository.isMovieCollected(movie))

        val result = repository.toggleMovieCollect(movie) // remove
        assertFalse(result)
        assertFalse(repository.isMovieCollected(movie))
    }

    @Test
    fun toggleActressCollect_addsWhenNotCollected() = runTest {
        val actress = ActressInfo("Alice", "http://avatar.jpg", "http://link1")

        assertFalse(repository.isActressCollected(actress))
        val result = repository.toggleActressCollect(actress)
        assertTrue(result)
        assertTrue(repository.isActressCollected(actress))
    }

    @Test
    fun toggleActressCollect_removesWhenCollected() = runTest {
        val actress = ActressInfo("Alice", "http://avatar.jpg", "http://link1")

        repository.toggleActressCollect(actress) // add
        assertTrue(repository.isActressCollected(actress))

        val result = repository.toggleActressCollect(actress) // remove
        assertFalse(result)
        assertFalse(repository.isActressCollected(actress))
    }

    @Test
    fun getCollectedMovies_returnsOnlyMovies() = runTest {
        val movie = Movie("Test", "http://img.jpg", "ABC-001", "2024-01-01", "http://link1")
        val actress = ActressInfo("Alice", "http://avatar.jpg", "http://link2")

        repository.toggleMovieCollect(movie)
        repository.toggleActressCollect(actress)

        val movies = repository.getCollectedMovies()
        assertEquals(1, movies.size)
        assertEquals("ABC-001", movies.first().code)
    }

    @Test
    fun getCollectedActresses_returnsOnlyActresses() = runTest {
        val movie = Movie("Test", "http://img.jpg", "ABC-001", "2024-01-01", "http://link1")
        val actress = ActressInfo("Alice", "http://avatar.jpg", "http://link2")

        repository.toggleMovieCollect(movie)
        repository.toggleActressCollect(actress)

        val actresses = repository.getCollectedActresses()
        assertEquals(1, actresses.size)
        assertEquals("Alice", actresses.first().name)
    }

    @Test
    fun defaultRepository_importRunsInsideSingleTransaction() = runTest {
        val transactionRunner = RecordingTransactionRunner()
        val dao = TransactionCheckingLinkItemDao(transactionRunner)
        val repository = DefaultCollectRepository(
            linkDao = dao,
            siteConfig = fakeSiteConfig(),
            transactionRunner = transactionRunner
        )
        val json = """
            {
              "version": 1,
              "movies": [
                {
                  "title": "Test",
                  "imageUrl": "http://img.jpg",
                  "code": "ABC-001",
                  "date": "2024-01-01",
                  "detailUrl": "http://link1",
                  "categoryId": 1
                }
              ],
              "actresses": []
            }
        """.trimIndent()

        val result = repository.importCollectionsFromJson(json)

        assertEquals(1 to 0, result)
        assertEquals(1, transactionRunner.calls)
        assertEquals(1, dao.items.size)
    }

    @Test
    fun defaultRepository_toggleRunsInsideTransaction() = runTest {
        val transactionRunner = RecordingTransactionRunner()
        val dao = TransactionCheckingLinkItemDao(transactionRunner)
        val repository = DefaultCollectRepository(
            linkDao = dao,
            siteConfig = fakeSiteConfig(),
            transactionRunner = transactionRunner
        )

        val result = repository.toggleMovieCollect(
            Movie("Test", "http://img.jpg", "ABC-001", "2024-01-01", "http://link1")
        )

        assertTrue(result)
        assertEquals(1, transactionRunner.calls)
        assertEquals(1, dao.items.size)
    }

    @Test
    fun defaultRepository_importRollsBackWhenOneItemFails() = runTest {
        val transactionRunner = RollbackTransactionRunner()
        val dao = RollbackCheckingLinkItemDao(transactionRunner, failOnKey = "/link2")
        transactionRunner.dao = dao
        val repository = DefaultCollectRepository(
            linkDao = dao,
            siteConfig = fakeSiteConfig(),
            transactionRunner = transactionRunner
        )
        val json = """
            {
              "version": 1,
              "movies": [
                {
                  "title": "First",
                  "imageUrl": "http://img.jpg",
                  "code": "ABC-001",
                  "date": "2024-01-01",
                  "detailUrl": "https://example.test/link1",
                  "categoryId": 1
                },
                {
                  "title": "Second",
                  "imageUrl": "http://img.jpg",
                  "code": "ABC-002",
                  "date": "2024-01-02",
                  "detailUrl": "https://example.test/link2",
                  "categoryId": 1
                }
              ],
              "actresses": []
            }
        """.trimIndent()

        try {
            repository.importCollectionsFromJson(json)
            org.junit.Assert.fail("Expected import failure")
        } catch (_: IllegalStateException) {
            // Expected.
        }

        assertEquals(1, transactionRunner.calls)
        assertTrue(dao.items.isEmpty())
    }

    private class FakeCollectRepository : CollectRepository {
        private val collectedMovies = mutableMapOf<String, Movie>()
        private val collectedActresses = mutableMapOf<String, ActressInfo>()

        override suspend fun isCollected(linkItem: LinkItem) = false
        override suspend fun addCollect(linkItem: LinkItem) = true
        override suspend fun removeCollect(linkItem: LinkItem) = true

        override suspend fun isMovieCollected(movie: Movie) = movie.link in collectedMovies

        override suspend fun toggleMovieCollect(movie: Movie, categoryId: Int?): Boolean {
            return if (movie.link in collectedMovies) {
                collectedMovies.remove(movie.link)
                false
            } else {
                collectedMovies[movie.link] = movie
                true
            }
        }

        override suspend fun isActressCollected(actress: ActressInfo) =
            actress.link in collectedActresses

        override suspend fun toggleActressCollect(actress: ActressInfo): Boolean {
            return if (actress.link in collectedActresses) {
                collectedActresses.remove(actress.link)
                false
            } else {
                collectedActresses[actress.link] = actress
                true
            }
        }

        override suspend fun getCollectedMovies() = collectedMovies.values.toList()

        override suspend fun getCollectedActresses() = collectedActresses.values.toList()

        override suspend fun getCollectedLinkItems(dbType: Int): List<LinkItem> =
            when (dbType) {
                MovieDBType -> collectedMovies.values.map { it.convertDBItem() }
                ActressDBType -> collectedActresses.values.map { it.convertDBItem() }
                else -> emptyList()
            }

        override suspend fun exportCollectionsJson() = "{}"

        override suspend fun importCollectionsFromJson(json: String) = 0 to 0
    }

    private class RecordingTransactionRunner : CollectTransactionRunner {
        var calls = 0
        var inTransaction = false

        override suspend fun <T> withTransaction(block: suspend () -> T): T {
            calls += 1
            inTransaction = true
            return try {
                block()
            } finally {
                inTransaction = false
            }
        }
    }

    private class TransactionCheckingLinkItemDao(
        private val transactionRunner: RecordingTransactionRunner
    ) : LinkItemDao {
        val items = mutableListOf<LinkItem>()

        override suspend fun insert(link: LinkItem): Long {
            check(transactionRunner.inTransaction) { "insert must run in transaction" }
            if (items.any { it.dbType == link.dbType && it.key == link.key }) return -1
            items += link.copy(id = items.size + 1)
            return items.size.toLong()
        }

        override suspend fun update(link: LinkItem): Int = 0

        override suspend fun delete(dbType: Int, key: String): Int {
            check(transactionRunner.inTransaction) { "delete must run in transaction" }
            val before = items.size
            items.removeAll { it.dbType == dbType && it.key == key }
            return before - items.size
        }

        override fun listAll(): Flow<List<LinkItem>> = flow {
            emit(items.toList())
        }

        override suspend fun listByType(dbType: Int): List<LinkItem> =
            items.filter { it.dbType == dbType }

        override suspend fun queryLink(): List<LinkItem> =
            items.filter { it.dbType !in setOf(MovieDBType, ActressDBType) }

        override suspend fun queryByCategoryId(categoryId: Int): List<LinkItem> =
            items.filter { it.categoryId == categoryId }

        override suspend fun updateByCategoryId(categoryId: Int, dbType: Int, setId: Int): Int = 0

        override suspend fun hasByKey(dbType: Int, key: String): Int =
            items.count { it.dbType == dbType && it.key == key }
    }

    private class RollbackTransactionRunner : CollectTransactionRunner {
        lateinit var dao: RollbackCheckingLinkItemDao
        var calls = 0
        var inTransaction = false

        override suspend fun <T> withTransaction(block: suspend () -> T): T {
            calls += 1
            val snapshot = dao.items.toList()
            inTransaction = true
            return try {
                block()
            } catch (e: Throwable) {
                dao.items.clear()
                dao.items.addAll(snapshot)
                throw e
            } finally {
                inTransaction = false
            }
        }
    }

    private class RollbackCheckingLinkItemDao(
        private val transactionRunner: RollbackTransactionRunner,
        private val failOnKey: String
    ) : LinkItemDao {
        val items = mutableListOf<LinkItem>()

        override suspend fun insert(link: LinkItem): Long {
            check(transactionRunner.inTransaction) { "insert must run in transaction" }
            if (link.key == failOnKey) error("simulated insert failure")
            if (items.any { it.dbType == link.dbType && it.key == link.key }) return -1
            items += link.copy(id = items.size + 1)
            return items.size.toLong()
        }

        override suspend fun update(link: LinkItem): Int = 0

        override suspend fun delete(dbType: Int, key: String): Int {
            check(transactionRunner.inTransaction) { "delete must run in transaction" }
            val before = items.size
            items.removeAll { it.dbType == dbType && it.key == key }
            return before - items.size
        }

        override fun listAll(): Flow<List<LinkItem>> = flow {
            emit(items.toList())
        }

        override suspend fun listByType(dbType: Int): List<LinkItem> =
            items.filter { it.dbType == dbType }

        override suspend fun queryLink(): List<LinkItem> =
            items.filter { it.dbType !in setOf(MovieDBType, ActressDBType) }

        override suspend fun queryByCategoryId(categoryId: Int): List<LinkItem> =
            items.filter { it.categoryId == categoryId }

        override suspend fun updateByCategoryId(categoryId: Int, dbType: Int, setId: Int): Int = 0

        override suspend fun hasByKey(dbType: Int, key: String): Int =
            items.count { it.dbType == dbType && it.key == key }
    }

    private fun fakeSiteConfig() = object : SiteConfig {
        override var baseUrl: String = "https://example.test"
        override fun resolve(pathOrUrl: String): String = pathOrUrl
    }
}

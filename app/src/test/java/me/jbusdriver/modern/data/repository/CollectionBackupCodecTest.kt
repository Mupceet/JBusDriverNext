package me.jbusdriver.modern.data.repository

import com.google.gson.JsonParser
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.runTest
import me.jbusdriver.modern.core.site.SiteConfig
import me.jbusdriver.modern.core.toJsonString
import me.jbusdriver.modern.data.db.ActressDBType
import me.jbusdriver.modern.data.db.MovieDBType
import me.jbusdriver.modern.data.db.convertDBItem
import me.jbusdriver.modern.data.db.dao.LinkItemDao
import me.jbusdriver.modern.data.db.entity.LinkItem
import me.jbusdriver.modern.data.db.toILink
import me.jbusdriver.modern.domain.model.ActressInfo
import me.jbusdriver.modern.domain.model.Movie
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class CollectionBackupCodecTest {
    @Test
    fun `export collections writes movies actresses category ids and absolute urls`() = runTest {
        val dao = RecordingLinkItemDao(
            mutableListOf(
                movie().convertDBItem(categoryId = 7),
                actress().convertDBItem(categoryId = 8)
            )
        )
        val codec = CollectionBackupCodec(dao, fakeSiteConfig("https://mirror.test"))

        val json = codec.exportCollectionsJson()
        val root = JsonParser.parseString(json).asJsonObject

        assertEquals(1, root.get("version").asInt)
        assertTrue(root.has("exportTime"))
        val exportedMovie = root.getAsJsonArray("movies").single().asJsonObject
        val exportedActress = root.getAsJsonArray("actresses").single().asJsonObject
        assertEquals("https://mirror.test/movies/ABC-001", exportedMovie.get("detailUrl").asString)
        assertEquals("https://mirror.test/images/cover.jpg", exportedMovie.get("imageUrl").asString)
        assertEquals(7, exportedMovie.get("categoryId").asInt)
        assertEquals("https://mirror.test/star/alice", exportedActress.get("link").asString)
        assertEquals(8, exportedActress.get("categoryId").asInt)
    }

    @Test
    fun `import new format inserts movies and actresses with category ids`() = runTest {
        val dao = RecordingLinkItemDao()
        val codec = CollectionBackupCodec(dao, fakeSiteConfig("https://example.test"))
        val json = """
            {
              "version": 1,
              "movies": [
                {
                  "title": "Movie",
                  "imageUrl": "/images/cover.jpg",
                  "code": "ABC-001",
                  "date": "2026-06-19",
                  "detailUrl": "https://example.test/movies/ABC-001",
                  "categoryId": 21
                }
              ],
              "actresses": [
                {
                  "name": "Alice",
                  "avatar": "/avatar/alice.jpg",
                  "link": "https://example.test/star/alice",
                  "tag": "12部",
                  "categoryId": 22
                }
              ]
            }
        """.trimIndent()

        val result = codec.importCollectionsFromJson(json)

        assertEquals(2 to 0, result)
        assertEquals(listOf(MovieDBType, ActressDBType), dao.items.map { it.dbType })
        assertEquals(listOf(21, 22), dao.items.map { it.categoryId })
        assertEquals(listOf("/movies/ABC-001", "/star/alice"), dao.items.map { it.key })
        assertTrue(dao.items.first().jsonStr.contains(""""imageUrl":"/images/cover.jpg""""))
    }

    @Test
    fun `import new format skips duplicate keys`() = runTest {
        val existing = movie().convertDBItem(categoryId = 1)
        val dao = RecordingLinkItemDao(mutableListOf(existing))
        val codec = CollectionBackupCodec(dao, fakeSiteConfig("https://example.test"))
        val json = """
            {
              "version": 1,
              "movies": [
                {
                  "title": "Movie",
                  "imageUrl": "/images/cover.jpg",
                  "code": "ABC-001",
                  "date": "2026-06-19",
                  "detailUrl": "https://example.test/movies/ABC-001",
                  "categoryId": 21
                }
              ],
              "actresses": []
            }
        """.trimIndent()

        val result = codec.importCollectionsFromJson(json)

        assertEquals(0 to 1, result)
        assertEquals(1, dao.items.size)
    }

    @Test
    fun `import legacy format handles movie and actress json payloads`() = runTest {
        val dao = RecordingLinkItemDao()
        val codec = CollectionBackupCodec(dao, fakeSiteConfig("https://example.test"))
        val json = """
            [
              {
                "type": $MovieDBType,
                "jsonStr": ${movie().toJsonString().quoteForJson()},
                "categoryId": 31
              },
              {
                "type": $ActressDBType,
                "jsonStr": ${actress().toJsonString().quoteForJson()},
                "categoryId": 32
              },
              {
                "type": 999,
                "jsonStr": "{}",
                "categoryId": 33
              }
            ]
        """.trimIndent()

        val result = codec.importCollectionsFromJson(json)

        assertEquals(2 to 0, result)
        assertEquals(listOf(MovieDBType, ActressDBType), dao.items.map { it.dbType })
        assertEquals(listOf(31, 32), dao.items.map { it.categoryId })
    }

    @Test
    fun `import falls back to default categories when category id is absent`() = runTest {
        val dao = RecordingLinkItemDao()
        val codec = CollectionBackupCodec(dao, fakeSiteConfig("https://example.test"))
        val json = """
            {
              "version": 1,
              "movies": [
                {
                  "title": "Movie",
                  "imageUrl": "/images/cover.jpg",
                  "code": "ABC-001",
                  "date": "2026-06-19",
                  "detailUrl": "https://example.test/movies/ABC-001"
                }
              ],
              "actresses": [
                {
                  "name": "Alice",
                  "avatar": "/avatar/alice.jpg",
                  "link": "https://example.test/star/alice"
                }
              ]
            }
        """.trimIndent()

        val result = codec.importCollectionsFromJson(json)

        assertEquals(2 to 0, result)
        assertEquals(listOf(1, 2), dao.items.map { it.categoryId })
    }

    private class RecordingLinkItemDao(
        val items: MutableList<LinkItem> = mutableListOf()
    ) : LinkItemDao {

        override suspend fun insert(link: LinkItem): Long {
            if (items.any { it.dbType == link.dbType && it.key == link.key }) return -1
            items += link.copy(id = items.size + 1)
            return items.size.toLong()
        }

        override suspend fun update(link: LinkItem): Int = 0

        override suspend fun delete(dbType: Int, key: String): Int {
            val before = items.size
            items.removeAll { it.dbType == dbType && it.key == key }
            return before - items.size
        }

        override fun listAll(): Flow<List<LinkItem>> = flowOf(items)

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

    private fun movie() = Movie(
        title = "Movie",
        imageUrl = "https://example.test/images/cover.jpg",
        code = "ABC-001",
        date = "2026-06-19",
        link = "https://example.test/movies/ABC-001"
    )

    private fun actress() = ActressInfo(
        name = "Alice",
        avatar = "https://example.test/avatar/alice.jpg",
        link = "https://example.test/star/alice",
        tag = "12部"
    )

    private fun fakeSiteConfig(base: String) = object : SiteConfig {
        override var baseUrl: String = base
        override fun resolve(pathOrUrl: String): String = pathOrUrl
    }

    private fun String.quoteForJson(): String = toJsonString()
}

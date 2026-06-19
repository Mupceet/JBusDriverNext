package me.jbusdriver.modern.data

import me.jbusdriver.modern.data.repository.DefaultForumRepository
import me.jbusdriver.modern.data.repository.ForumRepository
import me.jbusdriver.modern.data.session.ForumCookiePersister
import me.jbusdriver.modern.data.session.ForumSessionClient
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.runBlocking
import me.jbusdriver.modern.core.cache.CacheStore
import me.jbusdriver.modern.core.cache.CachedLoadEvent
import me.jbusdriver.modern.core.cache.writeCached
import me.jbusdriver.modern.core.site.SiteConfig
import org.jsoup.Jsoup
import org.jsoup.nodes.Document
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.IOException

class ForumRepositoryCacheFlowTest {

    private val boardsKey = "forum:https://example.test:boards"

    @Test
    fun `observeForumBoards emits cached before fresh`() = runBlocking {
        val cacheStore = FakeCacheStore()
        cacheStore.writeCached(
            key = "forum:https://example.test:boards",
            value = forumHomeData("Cached Board"),
            nowMillis = { 1_000L }
        )
        val sessionClient = FakeSessionClient(successHomeHtml("Fresh Board"))
        val repository = repository(cacheStore, sessionClient)

        val events = repository.observeForumBoards(nowMillis = { 1_100L }).toList()

        assertEquals(2, events.size)
        assertEquals(
            "Cached Board",
            (events[0] as CachedLoadEvent.Cached).entry.value.boardGroups.single().boards.single().name
        )
        assertEquals(
            "Fresh Board",
            (events[1] as CachedLoadEvent.Fresh).entry.value.boardGroups.single().boards.single().name
        )
        assertEquals(1, sessionClient.fetchCount)
    }

    @Test
    fun `observeForumBoards emits cached failure when background refresh fails`() = runBlocking {
        val cacheStore = FakeCacheStore()
        cacheStore.writeCached(
            key = "forum:https://example.test:boards",
            value = forumHomeData("Cached Board"),
            nowMillis = { 1_000L }
        )
        val sessionClient = FakeSessionClient(IOException("network down"))
        val repository = repository(cacheStore, sessionClient)

        val events = repository.observeForumBoards(nowMillis = { 1_100L }).toList()

        assertEquals(2, events.size)
        assertEquals(
            "Cached Board",
            (events[0] as CachedLoadEvent.Cached).entry.value.boardGroups.single().boards.single().name
        )
        val failure = events[1] as CachedLoadEvent.Failure
        assertTrue(failure.hadCachedValue)
        assertEquals("network down", failure.throwable.message)
    }

    @Test
    fun `observeForumBoards emits fresh when cache is missing`() = runBlocking {
        val sessionClient = FakeSessionClient(successHomeHtml("Fresh Board"))
        val repository = repository(FakeCacheStore(), sessionClient)

        val events = repository.observeForumBoards(nowMillis = { 1_000L }).toList()

        assertEquals(1, events.size)
        assertEquals(
            "Fresh Board",
            (events.single() as CachedLoadEvent.Fresh).entry.value.boardGroups.single().boards.single().name
        )
    }

    @Test
    fun `observeForumBoards emits failure without cached value when cache is missing`() =
        runBlocking {
            val repository =
                repository(FakeCacheStore(), FakeSessionClient(IOException("network down")))

            val events = repository.observeForumBoards(nowMillis = { 1_000L }).toList()

            assertEquals(1, events.size)
            val failure = events.single() as CachedLoadEvent.Failure
            assertFalse(failure.hadCachedValue)
            assertEquals("network down", failure.throwable.message)
        }

    @Test
    fun `loadForumBoards refreshes expired cached data and returns fresh`() = runBlocking {
        val cacheStore = FakeCacheStore()
        cacheStore.writeCached(
            key = "forum:https://example.test:boards",
            value = forumHomeData("Expired Board"),
            nowMillis = { 1_000L }
        )
        val sessionClient = FakeSessionClient(successHomeHtml("Fresh Board"))
        val repository = repository(cacheStore, sessionClient)

        val result = repository.loadForumBoards()

        assertEquals("Fresh Board", result.boardGroups.single().boards.single().name)
        assertEquals(1, sessionClient.fetchCount)
    }

    @Test
    fun `loadForumBoards returns expired cached data when refresh fails`() = runBlocking {
        val cacheStore = FakeCacheStore()
        cacheStore.writeCached(
            key = "forum:https://example.test:boards",
            value = forumHomeData("Expired Board"),
            nowMillis = { 1_000L }
        )
        val sessionClient = FakeSessionClient(IOException("network down"))
        val repository = repository(cacheStore, sessionClient)

        val result = repository.loadForumBoards()

        assertEquals("Expired Board", result.boardGroups.single().boards.single().name)
        assertEquals(1, sessionClient.fetchCount)
    }

    @Test
    fun `observeForumBoards retries once when first fetch is empty and caches good result`() =
        runBlocking {
            val cacheStore = FakeCacheStore()
            val sessionClient =
                FakeSessionClient(listOf(emptyHomeHtml(), successHomeHtml("Fresh Board")))
            val repository = repository(cacheStore, sessionClient)

            val events = repository.observeForumBoards(nowMillis = { 1_000L }).toList()

            assertEquals(1, events.size)
            val fresh = events.single() as CachedLoadEvent.Fresh
            assertEquals(
                "Fresh Board",
                fresh.entry.value.boardGroups.single().boards.single().name
            )
            assertEquals(2, sessionClient.fetchCount)
            assertTrue(
                (cacheStore.memory[boardsKey] ?: "").contains("Fresh Board")
            )
        }

    @Test
    fun `observeForumBoards keeps cached value and does not persist when all fetches empty`() =
        runBlocking {
            val cacheStore = FakeCacheStore()
            cacheStore.writeCached(
                key = boardsKey,
                value = forumHomeData("Cached Board"),
                nowMillis = { 1_000L }
            )
            val sessionClient = FakeSessionClient(listOf(emptyHomeHtml(), emptyHomeHtml()))
            val repository = repository(cacheStore, sessionClient)

            val events = repository.observeForumBoards(nowMillis = { 1_100L }).toList()

            assertEquals(1, events.size)
            val cached = events.single() as CachedLoadEvent.Cached
            assertEquals(
                "Cached Board",
                cached.entry.value.boardGroups.single().boards.single().name
            )
            assertEquals(2, sessionClient.fetchCount)
            // 缓存仍为好值，未被空结果覆盖。
            assertTrue(
                (cacheStore.memory[boardsKey] ?: "").contains("Cached Board")
            )
        }

    @Test
    fun `observeForumBoards emits ephemeral empty and does not cache when no cache and all empty`() =
        runBlocking {
            val cacheStore = FakeCacheStore()
            val sessionClient = FakeSessionClient(listOf(emptyHomeHtml(), emptyHomeHtml()))
            val repository = repository(cacheStore, sessionClient)

            val events = repository.observeForumBoards(nowMillis = { 1_000L }).toList()

            assertEquals(1, events.size)
            val fresh = events.single() as CachedLoadEvent.Fresh
            assertTrue(fresh.entry.value.boardGroups.isEmpty())
            assertEquals(2, sessionClient.fetchCount)
            assertNull(cacheStore.memory[boardsKey])
            assertNull(cacheStore.disk[boardsKey])
        }

    private fun repository(
        cacheStore: FakeCacheStore,
        sessionClient: FakeSessionClient
    ): ForumRepository = DefaultForumRepository(
        sessionClient = sessionClient,
        cookiePersister = FakeCookiePersister(),
        cacheStore = cacheStore,
        siteConfig = FakeSiteConfig()
    )

    private class FakeCacheStore : CacheStore {
        val memory = mutableMapOf<String, String>()
        val disk = mutableMapOf<String, String>()

        override fun readMemory(key: String): String? = memory[key]
        override fun writeMemory(key: String, value: String) {
            memory[key] = value
        }

        override suspend fun readDisk(key: String): String? = disk[key]
        override suspend fun writeDisk(key: String, value: String) {
            disk[key] = value
        }
    }

    private class FakeSessionClient(
        private val responses: List<Any>
    ) : ForumSessionClient {
        var fetchCount = 0

        constructor(response: Any) : this(listOf(response))

        override suspend fun warmUp() = Unit

        override suspend fun fetchDocument(url: String): Document {
            fetchCount++
            val value = responses[minOf(fetchCount - 1, responses.lastIndex)]
            if (value is Throwable) throw value
            return Jsoup.parse(value as String, url)
        }

        override suspend fun destroy() = Unit
    }

    private class FakeCookiePersister : ForumCookiePersister {
        override suspend fun persistCookies() = Unit
    }

    private class FakeSiteConfig : SiteConfig {
        override var baseUrl: String = "https://example.test"

        override fun resolve(pathOrUrl: String): String =
            if (pathOrUrl.startsWith("http")) pathOrUrl else "$baseUrl/$pathOrUrl"
    }

    private fun forumHomeData(boardName: String) =
        me.jbusdriver.modern.domain.model.ForumHomeData(
            banners = emptyList(),
            summary = me.jbusdriver.modern.domain.model.ForumHomeSummary(),
            boardGroups = listOf(
                me.jbusdriver.modern.domain.model.ForumBoardGroup(
                    name = "Group",
                    boards = listOf(
                        me.jbusdriver.modern.domain.model.ForumBoard(
                            id = 1,
                            name = boardName,
                            description = "Description",
                            todayPosts = 0,
                            totalThreads = "1",
                            totalPosts = "2",
                            lastPost = me.jbusdriver.modern.domain.model.LastPost("", "", "")
                        )
                    )
                )
            )
        )

    private fun successHomeHtml(boardName: String): String = """
        <html>
          <body>
            <div class="fl bm">
              <div class="bm bmw cl">
                <div class="bm_h cl"><h2><a>Group</a></h2></div>
                <div id="category_1">
                  <table class="fl_tb">
                    <tbody>
                      <tr>
                        <td>
                          <h2><a href="forum.php?mod=forumdisplay&amp;fid=1">$boardName</a></h2>
                          <p class="xg2">Description</p>
                        </td>
                        <td class="fl_i"><span>1</span><span>/</span><span>2</span><em class="xw0 xi1">(0)</em></td>
                        <td class="fl_by"><div class="forumlist"></div></td>
                      </tr>
                    </tbody>
                  </table>
                </div>
              </div>
            </div>
          </body>
        </html>
    """.trimIndent()

    /** 首页退化页（无版块结构），解析得 0 boards。 */
    private fun emptyHomeHtml(): String = """
        <html>
          <head><title>JavBus</title></head>
          <body></body>
        </html>
    """.trimIndent()
}

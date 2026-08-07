package me.jbusdriver.modern.data

import me.jbusdriver.modern.data.repository.DefaultForumRepository
import me.jbusdriver.modern.data.repository.ForumRepository
import me.jbusdriver.modern.core.http.BrowserCookiePersister
import me.jbusdriver.modern.core.http.BrowserSessionClient
import me.jbusdriver.modern.data.settings.ForumFloorOrder
import me.jbusdriver.modern.data.settings.ForumThreadOrder
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.runBlocking
import me.jbusdriver.modern.core.cache.CacheStore
import me.jbusdriver.modern.core.cache.CachedLoadEvent
import me.jbusdriver.modern.core.cache.writeCached
import me.jbusdriver.modern.core.site.SiteConfig
import me.jbusdriver.modern.domain.model.PageInfo
import org.jsoup.Jsoup
import org.jsoup.nodes.Document
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
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

    // ---------- threads / detail / cookie 持久化 ----------

    @Test
    fun `observeThreads builds url without typeId filter when null`() = runBlocking {
        val sessionClient = FakeSessionClient(threadListHtml())
        val repository = repository(FakeCacheStore(), sessionClient)

        repository.observeThreads(fid = 5, page = 1, typeId = null).toList()

        assertEquals(1, sessionClient.urls.size)
        assertTrue(sessionClient.urls.single().contains("fid=5"))
        assertFalse(sessionClient.urls.single().contains("typeid"))
    }

    @Test
    fun `observeThreads builds url with typeId filter when provided`() = runBlocking {
        val sessionClient = FakeSessionClient(threadListHtml())
        val repository = repository(FakeCacheStore(), sessionClient)

        repository.observeThreads(fid = 5, page = 1, typeId = 7).toList()

        assertTrue(sessionClient.urls.single().contains("filter=typeid&typeid=7"))
    }

    @Test
    fun `observeThreads default thread order omits orderby`() = runBlocking {
        val sessionClient = FakeSessionClient(threadListHtml())
        val repository = repository(FakeCacheStore(), sessionClient)

        repository.observeThreads(fid = 5, page = 1, typeId = null).toList()

        assertFalse(sessionClient.urls.single().contains("orderby"))
    }

    @Test
    fun `observeThreads dateline order appends only orderby`() = runBlocking {
        val sessionClient = FakeSessionClient(threadListHtml())
        val repository = repository(FakeCacheStore(), sessionClient)

        repository.observeThreads(fid = 5, page = 1, typeId = null, threadOrder = ForumThreadOrder.DATELINE).toList()

        assertTrue(sessionClient.urls.single().contains("orderby=dateline"))
        assertFalse(sessionClient.urls.single().contains("filter=author"))
    }

    @Test
    fun `loadThreads returns cached value without refetching on second call`() = runBlocking {
        val cacheStore = FakeCacheStore()
        val sessionClient = FakeSessionClient(threadListHtml())
        val repository = repository(cacheStore, sessionClient)

        repository.loadThreads(fid = 5, page = 1)
        repository.loadThreads(fid = 5, page = 1)

        assertEquals(1, sessionClient.fetchCount)
    }

    @Test
    fun `loadThreads returns parsed page result`() = runBlocking {
        val sessionClient = FakeSessionClient(threadListHtml())
        val repository = repository(FakeCacheStore(), sessionClient)

        val result = repository.loadThreads(fid = 5, page = 1)

        assertNotNull(result.pageInfo)
    }

    @Test
    fun `observeThreadDetail builds url with ordertype for reverse floor order`() = runBlocking {
        val sessionClient = FakeSessionClient(threadDetailHtml())
        val repository = repository(FakeCacheStore(), sessionClient)

        repository.observeThreadDetail(tid = 9, page = 1, floorOrder = ForumFloorOrder.REVERSE).toList()

        assertTrue(sessionClient.urls.single().contains("ordertype=1"))
    }

    @Test
    fun `observeThreadDetail builds url without ordertype for regular floor order`() = runBlocking {
        val sessionClient = FakeSessionClient(threadDetailHtml())
        val repository = repository(FakeCacheStore(), sessionClient)

        repository.observeThreadDetail(tid = 9, page = 1, floorOrder = ForumFloorOrder.REGULAR).toList()

        assertFalse(sessionClient.urls.single().contains("ordertype"))
    }

    @Test
    fun `observeThreadDetail builds url with authorid when author filter active`() = runBlocking {
        val sessionClient = FakeSessionClient(threadDetailHtml())
        val repository = repository(FakeCacheStore(), sessionClient)

        repository.observeThreadDetail(
            tid = 9,
            page = 1,
            floorOrder = ForumFloorOrder.REGULAR,
            authorUid = 436478
        ).toList()

        assertTrue(sessionClient.urls.single().contains("authorid=436478"))
    }

    @Test
    fun `loadThreadDetail returns parsed detail title`() = runBlocking {
        val sessionClient = FakeSessionClient(threadDetailHtml())
        val repository = repository(FakeCacheStore(), sessionClient)

        val result = repository.loadThreadDetail(tid = 9, page = 1)

        assertEquals("Thread Title", result.title)
    }

    @Test
    fun `loadThreadDetail caches with detail v3 key to avoid legacy comment metadata`() = runBlocking {
        val cacheStore = FakeCacheStore()
        val sessionClient = FakeSessionClient(threadDetailHtml())
        val repository = repository(cacheStore, sessionClient)

        repository.loadThreadDetail(tid = 9, page = 1, floorOrder = ForumFloorOrder.REGULAR)

        assertTrue(cacheStore.memory.keys.contains("forum:https://example.test:detail:v3:9:1:regular"))
        assertFalse(cacheStore.memory.keys.contains("forum:https://example.test:detail:v2:9:1:regular"))
    }

    @Test
    fun `loadFloorComments builds commentmore url and returns parsed comments`() = runBlocking {
        val sessionClient = FakeSessionClient(commentMoreHtml(page = 2, nextPage = 3))
        val repository = repository(FakeCacheStore(), sessionClient)

        val result = repository.loadFloorComments(tid = 172059, pid = 4773811, page = 2)

        assertEquals(4773811, result.pid)
        assertEquals("Frank", result.comments.single().author)
        assertEquals(PageInfo(activePage = 2, nextPage = 3), result.pageInfo)
        assertEquals(1, sessionClient.ajaxUrls.size)
        assertTrue(sessionClient.ajaxUrls.single().contains("mod=misc&action=commentmore"))
        assertTrue(sessionClient.ajaxUrls.single().contains("tid=172059"))
        assertTrue(sessionClient.ajaxUrls.single().contains("pid=4773811"))
        assertTrue(sessionClient.ajaxUrls.single().contains("page=2"))
        assertTrue(sessionClient.ajaxUrls.single().contains("inajax=1"))
        assertTrue(sessionClient.ajaxUrls.single().contains("ajaxtarget=comment_4773811"))
    }

    @Test
    fun `loadFloorComments uses ajax request with thread referer`() = runBlocking {
        val sessionClient = FakeSessionClient(commentMoreHtml(page = 2, nextPage = 3))
        val repository = repository(FakeCacheStore(), sessionClient)

        repository.loadFloorComments(tid = 172059, pid = 4773811, page = 2)

        assertEquals(emptyList<String>(), sessionClient.urls)
        assertEquals(1, sessionClient.ajaxUrls.size)
        assertTrue(sessionClient.ajaxUrls.single().contains("mod=misc&action=commentmore"))
        assertTrue(sessionClient.ajaxUrls.single().contains("inajax=1"))
        assertTrue(sessionClient.ajaxUrls.single().contains("ajaxtarget=comment_4773811"))
        assertEquals(
            "https://example.test/forum/forum.php?mod=viewthread&tid=172059",
            sessionClient.ajaxReferers.single()
        )
    }

    @Test
    fun `loadFloorComments caches by tid pid and page`() = runBlocking {
        val cacheStore = FakeCacheStore()
        val sessionClient = FakeSessionClient(commentMoreHtml(page = 2, nextPage = 2))
        val repository = repository(cacheStore, sessionClient)

        repository.loadFloorComments(tid = 172059, pid = 4773811, page = 2)
        repository.loadFloorComments(tid = 172059, pid = 4773811, page = 2)

        assertEquals(1, sessionClient.fetchCount)
        assertTrue(cacheStore.memory.keys.any { key ->
            key == "forum:https://example.test:floor-comments:v2:172059:4773811:2"
        })
    }

    @Test
    fun `first successful forum fetch persists cookies`() = runBlocking {
        val persister = FakeCookiePersister()
        val sessionClient = FakeSessionClient(successHomeHtml("Board"))
        val repository = repository(FakeCacheStore(), sessionClient, persister)

        repository.observeForumBoards().toList()

        assertEquals(1, persister.persistCount)
    }

    @Test
    fun `cookie persist failure allows retry on subsequent fetch`() = runBlocking {
        val persister = FakeCookiePersister(error = RuntimeException("persist fail"))
        val sessionClient = FakeSessionClient(successHomeHtml("Board"))
        val repository = repository(FakeCacheStore(), sessionClient, persister)

        repository.observeForumBoards().toList()
        // 首次 persist 抛异常后标志位回退，下次 fetch 应再次尝试 persist
        repository.observeForumBoards(forceRefresh = true).toList()

        assertEquals(2, persister.persistCount)
    }

    private fun repository(
        cacheStore: FakeCacheStore,
        sessionClient: FakeSessionClient,
        cookiePersister: FakeCookiePersister = FakeCookiePersister()
    ): ForumRepository = DefaultForumRepository(
        sessionClient = sessionClient,
        cookiePersister = cookiePersister,
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
    ) : BrowserSessionClient {
        var fetchCount = 0
        val urls = mutableListOf<String>()
        val ajaxUrls = mutableListOf<String>()
        val ajaxReferers = mutableListOf<String?>()

        constructor(response: Any) : this(listOf(response))

        override suspend fun warmUp() = Unit

        override suspend fun fetchDocument(url: String): Document {
            fetchCount++
            urls += url
            val value = responses[minOf(fetchCount - 1, responses.lastIndex)]
            if (value is Throwable) throw value
            return Jsoup.parse(value as String, url)
        }

        override suspend fun fetchAjaxDocument(url: String, referer: String): Document {
            fetchCount++
            ajaxUrls += url
            ajaxReferers += referer
            val value = responses[minOf(fetchCount - 1, responses.lastIndex)]
            if (value is Throwable) throw value
            return Jsoup.parse(value as String, url)
        }

        override suspend fun destroy() = Unit
    }

    private class FakeCookiePersister(
        private val error: Throwable? = null
    ) : BrowserCookiePersister {
        var persistCount = 0
        override suspend fun persistCookies() {
            persistCount++
            error?.let { throw it }
        }
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

    /** 帖子列表页（最小结构），解析得空列表 + 默认分页。 */
    private fun threadListHtml(): String = """
        <html>
          <body>
            <div class="pg"><strong>1</strong></div>
          </body>
        </html>
    """.trimIndent()

    /** 帖子详情页（最小结构），解析得标题。 */
    private fun threadDetailHtml(): String = """
        <html>
          <body>
            <h1 id="thread_subject">Thread Title</h1>
          </body>
        </html>
    """.trimIndent()

    private fun commentMoreHtml(page: Int, nextPage: Int): String {
        val nextLink = if (nextPage > page) {
            """<a href="forum.php?mod=misc&amp;action=commentmore&amp;tid=172059&amp;pid=4773811&amp;page=$nextPage" class="nxt">涓嬩竴闋?/a>"""
        } else {
            ""
        }
        return """
            <html>
              <body>
                <div id="comment_4773811" class="cm">
                  <div class="pstl">
                    <div class="psta"><img src="/avatars/f.jpg"></div>
                    <div class="psti"><a href="home.php?mod=space&amp;uid=6" class="xi2 xw1">Frank</a>&nbsp;page $page comment&nbsp;<span class="xg1">鐧艰〃鏂?2026-6-10 10:00</span></div>
                  </div>
                  <div class="pgs mbm cl"><div class="pg">
                    <strong>$page</strong>
                    $nextLink
                  </div></div>
                </div>
              </body>
            </html>
        """.trimIndent()
    }
}

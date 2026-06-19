package me.jbusdriver.modern.data.repository

import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.test.runTest
import me.jbusdriver.modern.core.cache.CacheStore
import me.jbusdriver.modern.core.cache.CachedLoadEvent
import me.jbusdriver.modern.core.http.HtmlClient
import me.jbusdriver.modern.core.site.SiteConfig
import me.jbusdriver.modern.domain.model.DataSourceType
import okhttp3.OkHttpClient
import org.jsoup.Jsoup
import org.jsoup.nodes.Document
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class DefaultMovieRepositoryCacheTest {
    @Test
    fun `load page returns cached value without fetching when memory cache is valid`() = runTest {
        val cacheStore = MemoryCacheStore()
        val htmlClient = RecordingHtmlClient(movieListHtml("Fresh", "FRESH-001"))
        val repository = repository(cacheStore, htmlClient)

        repository.loadPage(DataSourceType.CENSORED, page = 1, forceRefresh = true)
        htmlClient.html = movieListHtml("Newer", "NEW-001")
        val cached = repository.loadPage(DataSourceType.CENSORED, page = 1)

        assertEquals(1, htmlClient.fetchCount)
        assertEquals("Fresh", cached.movies.single().title)
    }

    @Test
    fun `load page force refresh bypasses cached value`() = runTest {
        val cacheStore = MemoryCacheStore()
        val htmlClient = RecordingHtmlClient(movieListHtml("Fresh", "FRESH-001"))
        val repository = repository(cacheStore, htmlClient)

        repository.loadPage(DataSourceType.CENSORED, page = 1, forceRefresh = true)
        htmlClient.html = movieListHtml("Newer", "NEW-001")
        val refreshed = repository.loadPage(DataSourceType.CENSORED, page = 1, forceRefresh = true)

        assertEquals(2, htmlClient.fetchCount)
        assertEquals("Newer", refreshed.movies.single().title)
    }

    @Test
    fun `observe page emits cached then fresh when revalidating first page`() = runTest {
        val cacheStore = MemoryCacheStore()
        val htmlClient = RecordingHtmlClient(movieListHtml("Cached", "CACHE-001"))
        val repository = repository(cacheStore, htmlClient)
        repository.loadPage(DataSourceType.CENSORED, page = 1, forceRefresh = true)
        htmlClient.html = movieListHtml("Fresh", "FRESH-001")

        val events = repository.observePage(
            DataSourceType.CENSORED,
            page = 1,
            revalidate = true,
            nowMillis = { 1_000L }
        ).toList()

        assertEquals(2, events.size)
        assertEquals("Cached", (events[0] as CachedLoadEvent.Cached).entry.value.movies.single().title)
        assertEquals("Fresh", (events[1] as CachedLoadEvent.Fresh).entry.value.movies.single().title)
    }

    @Test
    fun `load actresses uses actress page parser and cache`() = runTest {
        val htmlClient = RecordingHtmlClient(actressListHtml("Alice"))
        val repository = repository(MemoryCacheStore(), htmlClient)

        val first = repository.loadActresses(DataSourceType.ACTRESSES, page = 1, forceRefresh = true)
        htmlClient.html = actressListHtml("Betty")
        val cached = repository.loadActresses(DataSourceType.ACTRESSES, page = 1)

        assertEquals("Alice", first.first.single().name)
        assertEquals("Alice", cached.first.single().name)
        assertEquals(1, htmlClient.fetchCount)
    }

    @Test
    fun `load genre categories caches result and avoids refetch`() = runTest {
        val cacheStore = MemoryCacheStore()
        val htmlClient = RecordingHtmlClient(genreHtml())
        val repository = repository(cacheStore, htmlClient)

        repository.loadGenreCategories(DataSourceType.CENSORED, forceRefresh = true)
        htmlClient.html = genreHtml()
        repository.loadGenreCategories(DataSourceType.CENSORED)

        assertEquals(1, htmlClient.fetchCount)
    }

    @Test
    fun `observe genre categories emits cached then fresh`() = runTest {
        val cacheStore = MemoryCacheStore()
        val htmlClient = RecordingHtmlClient(genreHtml())
        val repository = repository(cacheStore, htmlClient)
        repository.loadGenreCategories(DataSourceType.CENSORED, forceRefresh = true)

        val events = repository.observeGenreCategories(
            DataSourceType.CENSORED,
            revalidate = true,
            nowMillis = { 1_000L }
        ).toList()

        assertEquals(2, events.size)
        assertTrue(events[0] is CachedLoadEvent.Cached)
        assertTrue(events[1] is CachedLoadEvent.Fresh)
    }

    @Test
    fun `load page by url resolves url and caches on repeat`() = runTest {
        val cacheStore = MemoryCacheStore()
        val htmlClient = RecordingHtmlClient(movieListHtml("ByUrl", "URL-001"))
        val repository = repository(cacheStore, htmlClient)

        repository.loadPageByUrl("/genre/1", page = 1, forceRefresh = true)
        repository.loadPageByUrl("/genre/1", page = 1)

        assertEquals(1, htmlClient.fetchCount)
        assertTrue(htmlClient.urls.single().contains("/genre/1"))
    }

    @Test
    fun `load actress detail caches via persistent cache`() = runTest {
        val cacheStore = MemoryCacheStore()
        val htmlClient = RecordingHtmlClient(actressDetailHtml())
        val repository = repository(cacheStore, htmlClient)

        repository.loadActressDetail("/star/alice", forceRefresh = true)
        repository.loadActressDetail("/star/alice")

        assertEquals(1, htmlClient.fetchCount)
    }

    @Test
    fun `load page next page fetches and returns parsed result`() = runTest {
        val cacheStore = MemoryCacheStore()
        val htmlClient = RecordingHtmlClient(movieListHtml("P2", "PAGE2"))
        val repository = repository(cacheStore, htmlClient)

        val result = repository.loadPage(DataSourceType.CENSORED, page = 2, forceRefresh = true)

        assertEquals("P2", result.movies.single().title)
        assertEquals(1, htmlClient.fetchCount)
    }

    private fun repository(cacheStore: CacheStore, htmlClient: RecordingHtmlClient) =
        DefaultMovieRepository(
            fetcher = MoviePageFetcher(htmlClient),
            cacheStore = cacheStore,
            siteConfig = object : SiteConfig {
                override var baseUrl: String = "https://example.test"
                override fun resolve(pathOrUrl: String): String = pathOrUrl
            }
        )

    private class RecordingHtmlClient(var html: String) : HtmlClient {
        override val imageOkHttpClient: OkHttpClient = OkHttpClient()
        var fetchCount = 0
        val urls = mutableListOf<String>()

        override suspend fun fetchHtml(url: String, showAll: Boolean, referer: String?): String = html

        override suspend fun fetchDocument(url: String, showAll: Boolean): Document {
            fetchCount++
            urls += url
            return Jsoup.parse(html, url)
        }
    }

    private class MemoryCacheStore : CacheStore {
        private val memory = mutableMapOf<String, String>()
        private val disk = mutableMapOf<String, String>()

        override fun readMemory(key: String): String? = memory[key]
        override fun writeMemory(key: String, value: String) {
            memory[key] = value
        }

        override suspend fun readDisk(key: String): String? = disk[key]
        override suspend fun writeDisk(key: String, value: String) {
            disk[key] = value
        }
    }

    private fun movieListHtml(title: String, code: String) = """
        <html><body>
          <a class="movie-box" href="/$code">
            <img title="$title" src="/covers/$code.jpg">
            <date>$code</date>
            <date>2026-06-19</date>
          </a>
          <ul class="pagination">
            <li class="active"><a href="/page/1">1</a></li>
          </ul>
        </body></html>
    """.trimIndent()

    private fun actressListHtml(name: String) = """
        <html><body>
          <a class="avatar-box" href="/star/$name">
            <img title="$name" src="/avatar/$name.jpg">
            <button>1部</button>
          </a>
        </body></html>
    """.trimIndent()

    private fun genreHtml(): String = "<html><body></body></html>"

    private fun actressDetailHtml(): String = """
        <html><body>
          <a class="avatar-box" href="/star/alice">
            <img title="Alice" src="/avatar/alice.jpg">
            <p>B:88 W:58 H:86</p>
          </a>
        </body></html>
    """.trimIndent()
}

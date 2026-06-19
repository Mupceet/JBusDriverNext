package me.jbusdriver.modern.data.repository

import kotlinx.coroutines.test.runTest
import me.jbusdriver.modern.core.cache.CacheStore
import me.jbusdriver.modern.core.http.HtmlClient
import me.jbusdriver.modern.core.site.SiteConfig
import okhttp3.OkHttpClient
import org.jsoup.Jsoup
import org.jsoup.nodes.Document
import org.junit.Assert.assertEquals
import org.junit.Test

class MovieDetailRepositoryTest {
    @Test
    fun `movie detail parses resolved url and stores persistent cache`() = runTest {
        val client = RecordingHtmlClient(detailHtml("First"))
        val cacheStore = MemoryCacheStore()
        val repository = repository(client, cacheStore)

        val detail = repository.getMovieDetail("/ABC-001", forceRefresh = true)

        assertEquals("https://example.test/ABC-001", client.urls.single())
        assertEquals("First", detail.title)
        assertEquals(1, cacheStore.disk.size)
    }

    @Test
    fun `movie detail returns cached value without fetching`() = runTest {
        val client = RecordingHtmlClient(detailHtml("Cached"))
        val repository = repository(client, MemoryCacheStore())

        repository.getMovieDetail("/ABC-001", forceRefresh = true)
        client.html = detailHtml("Fresh")
        val cached = repository.getMovieDetail("/ABC-001")

        assertEquals("Cached", cached.title)
        assertEquals(1, client.fetchCount)
    }

    @Test
    fun `movie detail force refresh bypasses persistent cache`() = runTest {
        val client = RecordingHtmlClient(detailHtml("Cached"))
        val repository = repository(client, MemoryCacheStore())

        repository.getMovieDetail("/ABC-001", forceRefresh = true)
        client.html = detailHtml("Fresh")
        val refreshed = repository.getMovieDetail("/ABC-001", forceRefresh = true)

        assertEquals("Fresh", refreshed.title)
        assertEquals(2, client.fetchCount)
    }

    private fun repository(client: RecordingHtmlClient, cacheStore: CacheStore) =
        DefaultMovieDetailRepository(
            htmlClient = client,
            cacheStore = cacheStore,
            siteConfig = object : SiteConfig {
                override var baseUrl: String = "https://example.test"
                override fun resolve(pathOrUrl: String): String = pathOrUrl
            }
        )

    private class RecordingHtmlClient(var html: String) : HtmlClient {
        override val imageOkHttpClient: OkHttpClient = OkHttpClient()
        val urls = mutableListOf<String>()
        var fetchCount = 0

        override suspend fun fetchHtml(url: String, showAll: Boolean, referer: String?): String = html

        override suspend fun fetchDocument(url: String, showAll: Boolean): Document {
            fetchCount++
            urls += url
            return Jsoup.parse(html, url)
        }
    }

    private class MemoryCacheStore : CacheStore {
        private val memory = mutableMapOf<String, String>()
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

    private fun detailHtml(title: String) = """
        <html>
          <head><meta name="description" content="Description"></head>
          <body>
            <div class="row movie">
              <a class="bigImage" href="/cover.jpg"><img title="$title"></a>
              <div class="info">
                <p><span class="header">發行日期:</span> 2026-06-19</p>
              </div>
            </div>
          </body>
        </html>
    """.trimIndent()
}

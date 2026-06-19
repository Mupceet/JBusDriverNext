package me.jbusdriver.modern.data

import me.jbusdriver.modern.data.repository.DefaultSearchRepository
import kotlinx.coroutines.test.runTest
import me.jbusdriver.modern.core.cache.CacheStore
import me.jbusdriver.modern.core.http.HtmlClient
import me.jbusdriver.modern.core.site.SiteConfig
import me.jbusdriver.modern.domain.model.SearchType
import okhttp3.OkHttpClient
import org.jsoup.Jsoup
import org.jsoup.nodes.Document
import org.junit.Assert.assertEquals
import org.junit.Test

class SearchRepositoryUrlTest {
    @Test
    fun searchMovies_encodesQueryBeforeBuildingUrl() = runTest {
        var capturedUrl = ""
        val repository = DefaultSearchRepository(
            htmlClient = object : HtmlClient {
                override val imageOkHttpClient: OkHttpClient = OkHttpClient()
                override suspend fun fetchHtml(url: String, showAll: Boolean, referer: String?) = ""
                override suspend fun fetchDocument(url: String, showAll: Boolean): Document {
                    capturedUrl = url
                    return Jsoup.parse("""<html><body></body></html>""", url)
                }
            },
            cacheStore = memoryCacheStore(),
            siteConfig = object : SiteConfig {
                override var baseUrl: String = "https://example.test"
                override fun resolve(pathOrUrl: String) = pathOrUrl
            }
        )

        repository.searchMovies(SearchType.CENSORED, "演員 1", page = 1, forceRefresh = true)

        assertEquals("https://example.test/search/%E6%BC%94%E5%93%A1%201", capturedUrl)
    }

    @Test
    fun searchMovies_parsesMoviesAndPageInfo() = runTest {
        val repository = DefaultSearchRepository(
            htmlClient = htmlClient(
                """
                    <html><body>
                      <a class="movie-box" href="/ABC-001">
                        <img title="Movie" src="/cover.jpg">
                        <date>ABC-001</date>
                        <date>2026-06-19</date>
                      </a>
                      <ul class="pagination">
                        <li class="active"><a href="/search/abc/2">2</a></li>
                        <li><a href="/search/abc/3">3</a></li>
                      </ul>
                    </body></html>
                """.trimIndent()
            ),
            cacheStore = memoryCacheStore(),
            siteConfig = fakeSiteConfig()
        )

        val result = repository.searchMovies(SearchType.CENSORED, "abc", page = 2, forceRefresh = true)

        assertEquals("Movie", result.movies.single().title)
        assertEquals("https://example.test/cover.jpg", result.movies.single().imageUrl)
        assertEquals(2, result.pageInfo.activePage)
        assertEquals(3, result.pageInfo.nextPage)
    }

    @Test
    fun searchMovies_usesMemoryCacheWhenNotForced() = runTest {
        val client = mutableHtmlClient(movieHtml("Cached", "CACHE-001"))
        val repository = DefaultSearchRepository(client, memoryCacheStore(), fakeSiteConfig())

        repository.searchMovies(SearchType.CENSORED, "abc", page = 1, forceRefresh = true)
        client.html = movieHtml("Fresh", "FRESH-001")
        val cached = repository.searchMovies(SearchType.CENSORED, "abc", page = 1)

        assertEquals(1, client.fetchCount)
        assertEquals("Cached", cached.movies.single().title)
    }

    @Test
    fun searchActresses_buildsUrlAndParsesResults() = runTest {
        val client = mutableHtmlClient(
            """
                <html><body>
                  <a class="avatar-box" href="/star/alice">
                    <img title="Alice" src="/avatar/alice.jpg">
                    <button>3部</button>
                  </a>
                </body></html>
            """.trimIndent()
        )
        val repository = DefaultSearchRepository(client, memoryCacheStore(), fakeSiteConfig())

        val (pageInfo, actresses) = repository.searchActresses("alice one", page = 2)

        assertEquals("https://example.test/searchstar/alice%20one/2", client.urls.single())
        assertEquals(2, pageInfo.activePage)
        assertEquals("Alice", actresses.single().name)
        assertEquals("https://example.test/avatar/alice.jpg", actresses.single().avatar)
    }

    private fun memoryCacheStore(): CacheStore {
        val memory = mutableMapOf<String, String>()
        return object : CacheStore {
            override fun readMemory(key: String) = memory[key]
            override fun writeMemory(key: String, value: String) {
                memory[key] = value
            }

            override suspend fun readDisk(key: String): String? = null
            override suspend fun writeDisk(key: String, value: String) = Unit
        }
    }

    private fun fakeSiteConfig() = object : SiteConfig {
        override var baseUrl: String = "https://example.test"
        override fun resolve(pathOrUrl: String) = pathOrUrl
    }

    private fun htmlClient(html: String): HtmlClient = mutableHtmlClient(html)

    private fun mutableHtmlClient(initialHtml: String) = object : HtmlClient {
        override val imageOkHttpClient: OkHttpClient = OkHttpClient()
        var html = initialHtml
        var fetchCount = 0
        val urls = mutableListOf<String>()

        override suspend fun fetchHtml(url: String, showAll: Boolean, referer: String?) = html
        override suspend fun fetchDocument(url: String, showAll: Boolean): Document {
            fetchCount++
            urls += url
            return Jsoup.parse(html, url)
        }
    }

    private fun movieHtml(title: String, code: String) = """
        <html><body>
          <a class="movie-box" href="/$code">
            <img title="$title" src="/$code.jpg">
            <date>$code</date>
            <date>2026-06-19</date>
          </a>
        </body></html>
    """.trimIndent()
}

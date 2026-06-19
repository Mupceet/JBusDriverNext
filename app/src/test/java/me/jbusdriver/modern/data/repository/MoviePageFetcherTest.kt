package me.jbusdriver.modern.data.repository

import kotlinx.coroutines.test.runTest
import me.jbusdriver.modern.core.http.HtmlClient
import okhttp3.OkHttpClient
import org.jsoup.Jsoup
import org.jsoup.nodes.Document
import org.junit.Assert.assertEquals
import org.junit.Test

class MoviePageFetcherTest {
    @Test
    fun `fetch genre categories removes duplicate links across groups`() = runTest {
        val fetcher = MoviePageFetcher(htmlClient(
            """
                <h4>主題</h4>
                <div class="genre-box">
                  <a href="/genre/hd">高清</a>
                  <a href="/genre/sub">字幕</a>
                </div>
                <h4>年份</h4>
                <div class="genre-box">
                  <a href="/genre/hd">HD duplicate</a>
                  <a href="/genre/2026">2026</a>
                </div>
            """.trimIndent()
        ))

        val groups = fetcher.fetchGenreCategories("https://example.test/genre")

        assertEquals(listOf("主題", "年份"), groups.map { it.title })
        assertEquals(listOf("/genre/hd", "/genre/sub"), groups[0].genres.map { it.link })
        assertEquals(listOf("/genre/2026"), groups[1].genres.map { it.link })
    }

    @Test
    fun `fetch actress detail resolves requested url and parses attrs`() = runTest {
        val client = recordingHtmlClient(
            """
                <div class="avatar-box">
                  <img title="Alice" src="/avatar/alice.jpg">
                  <p>生日: 2000-01-01</p>
                </div>
            """.trimIndent()
        )
        val fetcher = MoviePageFetcher(client)

        val detail = fetcher.fetchActressDetail("https://example.test", "/star/alice")

        assertEquals("https://example.test/star/alice", client.urls.single())
        assertEquals("Alice", detail.name)
        assertEquals("https://example.test/avatar/alice.jpg", detail.avatar)
        assertEquals(listOf("生日: 2000-01-01"), detail.info)
    }

    private fun htmlClient(html: String): HtmlClient = recordingHtmlClient(html)

    private fun recordingHtmlClient(html: String) = object : HtmlClient {
        override val imageOkHttpClient: OkHttpClient = OkHttpClient()
        val urls = mutableListOf<String>()

        override suspend fun fetchHtml(url: String, showAll: Boolean, referer: String?): String = html

        override suspend fun fetchDocument(url: String, showAll: Boolean): Document {
            urls += url
            return Jsoup.parse(html, url)
        }
    }
}

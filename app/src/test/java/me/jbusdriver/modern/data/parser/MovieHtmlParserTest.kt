package me.jbusdriver.modern.data.parser

import org.jsoup.Jsoup
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class MovieHtmlParserTest {
    @Test
    fun `page info parses active next and referenced pages`() {
        val doc = Jsoup.parse(
            """
                <ul class="pagination">
                  <li><a href="/page/1">1</a></li>
                  <li class="active"><a href="/page/2">2</a></li>
                  <li><a href="/page/3">3</a></li>
                  <li><a href="/page/4">4</a></li>
                  <li><a id="skip" href="/page/99">skip</a></li>
                </ul>
            """.trimIndent()
        )

        val pageInfo = parsePageInfo(doc)

        assertEquals(2, pageInfo?.activePage)
        assertEquals(3, pageInfo?.nextPage)
        assertEquals(listOf(1, 2, 3, 4), pageInfo?.referPages)
    }

    @Test
    fun `page info returns null without active page`() {
        val doc = Jsoup.parse("""<ul class="pagination"><li><a href="/page/1">1</a></li></ul>""")

        assertNull(parsePageInfo(doc))
    }

    @Test
    fun `movie list parses image code date and tags`() {
        val doc = Jsoup.parse(
            """
                <a class="movie-box" href="/ABCD-123">
                  <img title="Sample title" src="/covers/abcd.jpg">
                  <date>ABCD-123</date>
                  <date>2026-06-19</date>
                  <div class="item-tag"><button>HD</button><button>字幕</button></div>
                </a>
            """.trimIndent()
        )

        val movie = loadMovieFromDoc(doc, "https://example.test").single()

        assertEquals("Sample title", movie.title)
        assertEquals("https://example.test/covers/abcd.jpg", movie.imageUrl)
        assertEquals("ABCD-123", movie.code)
        assertEquals("2026-06-19", movie.date)
        assertEquals("/ABCD-123", movie.link)
        assertEquals(listOf("HD", "字幕"), movie.tags)
    }

    @Test
    fun `movie detail parses headers genres actresses samples related and ajax ids`() {
        val doc = Jsoup.parse(
            """
                <html>
                  <head><meta name="description" content="Description text"></head>
                  <body>
                    <script>var gid = 12345; var uc = 67890;</script>
                    <div class="row movie">
                      <a class="bigImage" href="/cover/full.jpg"><img title="Detail title"></a>
                      <div class="info">
                        <p><span class="header">發行日期:</span> 2026-06-19</p>
                        <p><span class="header">製作商:</span> <a href="/studio/one">Studio One</a></p>
                        <p class="star-show"><span class="header">ignored:</span> Someone</p>
                        <span class="genre"><a href="/genre/hd">HD</a></span>
                        <span class="genre"><a href="/genre/sub">字幕</a></span>
                      </div>
                    </div>
                    <div id="avatar-waterfall">
                      <a class="avatar-box" href="/star/alice"><img src="//cdn.example/alice.jpg">Alice</a>
                    </div>
                    <div id="sample-waterfall">
                      <a class="sample-box" href="/samples/full1.jpg"><img src="/samples/thumb1.jpg" title="Sample 1"></a>
                      <a class="sample-box"><img src="/samples/thumb2.jpg" title="Sample 2"></a>
                    </div>
                    <div id="related-waterfall">
                      <a class="movie-box" href="https://example.test/ABCD-999" title="Related movie">
                        <img src="/covers/related.jpg">
                      </a>
                      <a class="movie-box" href="https://example.test/forum/thread-1" title="Forum link">
                        <img src="/forum.jpg">
                      </a>
                    </div>
                  </body>
                </html>
            """.trimIndent(),
            "https://example.test/ABCD-123"
        )

        val detail = parseMovieDetails(doc, "https://example.test")

        assertEquals("Detail title", detail.title)
        assertEquals("Description text", detail.content)
        assertEquals("https://example.test/cover/full.jpg", detail.cover)
        assertEquals("12345", detail.gid)
        assertEquals("67890", detail.uc)
        assertEquals(listOf("發行日期" to "2026-06-19", "製作商" to "Studio One"), detail.headers.map { it.name to it.value })
        assertEquals(listOf("/genre/hd", "/genre/sub"), detail.genres.map { it.link })
        assertEquals("https://cdn.example/alice.jpg", detail.actress.single().avatar)
        assertEquals("https://example.test/samples/full1.jpg", detail.imageSamples.first().image)
        assertEquals(detail.imageSamples[1].thumb, detail.imageSamples[1].image)
        assertEquals(listOf("ABCD-999"), detail.relatedMovies.map { it.code })
    }

    @Test
    fun `movie detail tolerates missing optional fields`() {
        val detail = parseMovieDetails(Jsoup.parse("<html></html>"), "https://example.test")

        assertEquals("", detail.title)
        assertEquals("", detail.cover)
        assertTrue(detail.headers.isEmpty())
        assertTrue(detail.imageSamples.isEmpty())
        assertNull(detail.gid)
        assertNull(detail.uc)
    }

    @Test
    fun `movie filter info parses counts and breadcrumb parts`() {
        val doc = Jsoup.parse(
            """
                <div class="alert-success">
                  <span id="resultshowmag">磁力 12</span>
                  <span id="resultshowall">全部 345</span>
                  <b>高清 - 有碼</b>
                </div>
            """.trimIndent()
        )

        val info = parseMovieFilterInfo(doc)

        assertEquals(12, info?.magnetCount)
        assertEquals(345, info?.totalCount)
        assertEquals("高清", info?.breadcrumbName)
        assertEquals("有碼", info?.breadcrumbType)
    }

    @Test
    fun `movie filter info returns null when required count is absent`() {
        val doc = Jsoup.parse("""<div class="alert-success"><span id="resultshowmag">磁力 12</span></div>""")

        assertNull(parseMovieFilterInfo(doc))
    }
}

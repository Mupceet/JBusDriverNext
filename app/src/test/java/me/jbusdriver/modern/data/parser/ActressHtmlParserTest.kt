package me.jbusdriver.modern.data.parser

import org.jsoup.Jsoup
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ActressHtmlParserTest {
    @Test
    fun `actress list parses name avatar link and tag`() {
        val doc = Jsoup.parse(
            """
                <a class="avatar-box" href="/star/alice">
                  <img title="Alice" src="/avatars/alice.jpg">
                  <button>12部</button>
                </a>
                <a class="avatar-box" href="/star/betty">
                  <img title="Betty" src="//cdn.example/betty.jpg">
                  <button>8部</button>
                </a>
            """.trimIndent()
        )

        val actresses = parseActressList(doc, "https://example.test")

        assertEquals(listOf("Alice", "Betty"), actresses.map { it.name })
        assertEquals("https://example.test/avatars/alice.jpg", actresses[0].avatar)
        assertEquals("https://cdn.example/betty.jpg", actresses[1].avatar)
        assertEquals(listOf("/star/alice", "/star/betty"), actresses.map { it.link })
    }

    @Test
    fun `actress list returns empty for empty document`() {
        assertTrue(parseActressList(Jsoup.parse("<html></html>"), "https://example.test").isEmpty())
    }

    @Test
    fun `actress attrs parse photo and info paragraphs`() {
        val doc = Jsoup.parse(
            """
                <div class="avatar-box">
                  <img title="Alice" src="/avatars/alice.jpg">
                  <p>生日: 2000-01-01</p>
                  <p>身高: 165cm</p>
                </div>
            """.trimIndent()
        )

        val attrs = parseActressAttrs(doc, "https://example.test")

        assertEquals("Alice", attrs.title)
        assertEquals("https://example.test/avatars/alice.jpg", attrs.imageUrl)
        assertEquals(listOf("生日: 2000-01-01", "身高: 165cm"), attrs.info)
    }
}

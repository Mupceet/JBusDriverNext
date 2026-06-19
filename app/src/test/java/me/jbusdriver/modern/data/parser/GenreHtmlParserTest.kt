package me.jbusdriver.modern.data.parser

import org.jsoup.Jsoup
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class GenreHtmlParserTest {
    @Test
    fun `genre categories pair previous titles with links`() {
        val doc = Jsoup.parse(
            """
                <h4>主題</h4>
                <div class="genre-box">
                  <a href="/genre/hd">高清</a>
                  <a href="/genre/sub">字幕</a>
                </div>
                <h4>年份</h4>
                <div class="genre-box">
                  <a href="/genre/2026">2026</a>
                </div>
            """.trimIndent()
        )

        val categories = parseGenreCategories(doc)

        assertEquals(listOf("主題", "年份"), categories.map { it.first })
        assertEquals(listOf("高清", "字幕"), categories[0].second.map { it.name })
        assertEquals(listOf("/genre/hd", "/genre/sub"), categories[0].second.map { it.link })
        assertEquals(listOf("2026"), categories[1].second.map { it.name })
    }

    @Test
    fun `genre categories return empty when no boxes exist`() {
        assertTrue(parseGenreCategories(Jsoup.parse("<h4>Empty</h4>")).isEmpty())
    }
}

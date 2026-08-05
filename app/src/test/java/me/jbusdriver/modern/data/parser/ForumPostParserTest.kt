package me.jbusdriver.modern.data.parser

import me.jbusdriver.modern.domain.model.ContentBlock
import org.jsoup.Jsoup
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ForumPostParserTest {

    private val baseUrl = "https://www.javbus.com"

    @Test
    fun nullRootReturnsEmpty() {
        assertTrue(parseForumPostContent(null, baseUrl).isEmpty())
    }

    @Test
    fun parsesOrderedListWithStartOffsetAndNestedChildren() {
        val html = """
            <div class="post">
              text before
              <ol start="2">
                <li>first <em>italic</em></li>
                <li>second<ul><li><u>nested</u></li></ul></li>
              </ol>
            </div>
        """.trimIndent()
        val root = Jsoup.parse(html, baseUrl).selectFirst("div.post")

        val blocks = parseForumPostContent(root, baseUrl)

        val list = blocks.filterIsInstance<ContentBlock.ListBlock>().single()
        assertTrue("ordered list should be marked ordered", list.list.ordered)
        assertEquals("start offset should come from the ol attribute", 2, list.list.start)
        assertEquals("two top-level items", 2, list.list.items.size)
        assertTrue(
            "second item should carry the nested <ul> as children",
            list.list.items[1].children.isNotEmpty()
        )
    }

    @Test
    fun capturesBoldAndLinkStylingAsDistinctParts() {
        val html =
            """<div class="post"><strong>bold</strong> plain <a href="https://example.com">link</a></div>"""
        val root = Jsoup.parse(html, baseUrl).selectFirst("div.post")

        val blocks = parseForumPostContent(root, baseUrl)

        val rich = blocks.filterIsInstance<ContentBlock.RichText>().first()
        val parts = rich.paragraphs.flatMap { it.parts }
        val combined = parts.joinToString("") { it.text }

        assertTrue("bold fragment present", combined.contains("bold"))
        assertTrue("plain fragment present", combined.contains("plain"))
        assertTrue("link fragment present", combined.contains("link"))
        assertTrue("a part should carry bold styling", parts.any { it.bold })
        val linkPart = parts.single { it.text == "link" }
        assertTrue("a part should be marked as a link", linkPart.isLink)
        assertEquals("link href should be retained", "https://example.com", linkPart.linkUrl)
    }

    @Test
    fun parsesQuoteWithQuotedFloorIdFromFindpostLink() {
        val html = """
            <div class="post">
              <div class="quote">
                <blockquote>
                  <font size="2">
                    <a href="/forum/forum.php?mod=redirect&amp;goto=findpost&amp;pid=4853889&amp;ptid=174488">
                      <font color="#999999">jackf12138 發表於 2026-8-5 23:32</font>
                    </a>
                  </font>
                  <br>
                  quoted body text
                </blockquote>
              </div>
              reply text
            </div>
        """.trimIndent()
        val root = Jsoup.parse(html, baseUrl).selectFirst("div.post")

        val blocks = parseForumPostContent(root, baseUrl)

        val quote = blocks.filterIsInstance<ContentBlock.Quote>().single()
        assertEquals("jackf12138 發表於 2026-8-5 23:32", quote.author)
        assertEquals("quoted body text", quote.content)
        assertEquals(4853889, quote.quotedPid)
    }

    @Test
    fun parsesQuoteWithoutFindpostLinkAsZeroPid() {
        val html = """
            <div class="post">
              <div class="quote">
                <blockquote>
                  <font size="2"><a href="https://example.com">someone</a></font>
                  <br>
                  quoted body
                </blockquote>
              </div>
            </div>
        """.trimIndent()
        val root = Jsoup.parse(html, baseUrl).selectFirst("div.post")

        val blocks = parseForumPostContent(root, baseUrl)

        val quote = blocks.filterIsInstance<ContentBlock.Quote>().single()
        assertEquals(0, quote.quotedPid)
    }
}

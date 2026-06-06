package me.jbusdriver.modern.data.parser

import me.jbusdriver.modern.domain.model.ContentBlock
import me.jbusdriver.modern.domain.model.ForumTextSize
import org.jsoup.Jsoup
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ForumThreadParserTest {
    @Test
    fun `restricted reply remains visible without normal body cell`() {
        val detail = parseForumThreadDetail(
            fixture(
                "restricted-replies.html",
                "https://www.javbus.com/forum/forum.php?mod=viewthread&tid=154969"
            ),
            "https://www.javbus.com"
        )

        assertEquals(1, detail.replies.size)
        assertEquals(2, detail.replies.single().floor)
        assertEquals(
            listOf(ContentBlock.RestrictedNotice("此帖僅作者可見")),
            detail.replies.single().contentBlocks
        )
    }

    @Test
    fun `pinned reply floors are parsed and retain document order`() {
        val replies = parseForumThreadDetail(
            fixture(
                "pinned-rich-replies.html",
                "https://www.javbus.com/forum/forum.php?mod=viewthread&tid=171794&page=2"
            ),
            "https://www.javbus.com"
        ).replies

        assertEquals(listOf(2, 3, 4, 5), replies.map { it.floor })
        assertTrue(replies.take(3).all { it.isPinned })
        assertFalse(replies.last().isPinned)
    }

    @Test
    fun `parses controlled inline styles and preserves separating spaces`() {
        val blocks = parsedPinnedReply(2).contentBlocks
        val parts = blocks.filterIsInstance<ContentBlock.RichText>()
            .flatMap { it.paragraphs }
            .flatMap { it.parts }

        val heading = parts.single { it.text == "观前提醒：" }
        assertTrue(heading.bold)
        assertEquals("#ff0000", heading.color)
        assertEquals(ForumTextSize.HEADING, heading.size)
        assertTrue(parts.joinToString("") { it.text }.contains("normal link text"))
        assertTrue(parts.single { it.text == "link text" }.isLink)
    }

    @Test
    fun `parses ordered and nested unordered lists`() {
        val list = parsedPinnedReply(2).contentBlocks
            .filterIsInstance<ContentBlock.ListBlock>()
            .single()
            .list

        assertTrue(list.ordered)
        assertEquals(2, list.start)
        assertEquals(listOf("first italic", "second"), list.items.map { item ->
            item.paragraphs.flatMap { it.parts }.joinToString("") { it.text }.trim()
        })
        assertTrue(list.items.first().paragraphs.single().parts.single { it.text == "italic" }.italic)
        assertFalse(list.items[1].children.single().ordered)
        val nestedPart = list.items[1].children.single().items.single()
            .paragraphs.single().parts.single()
        assertEquals("nested", nestedPart.text)
        assertTrue(nestedPart.underline)
    }

    @Test
    fun `unknown tags retain text and invalid colors are discarded`() {
        val parts = parsedPinnedReply(2).contentBlocks
            .filterIsInstance<ContentBlock.RichText>()
            .flatMap { it.paragraphs }
            .flatMap { it.parts }

        assertTrue(parts.any { it.text.contains("kept text") })
        assertEquals(null, parts.single { it.text == "removed" }.color)
        assertTrue(parts.single { it.text == "removed" }.strikethrough)
    }

    @Test
    fun `malformed list retains visible descendant text`() {
        val text = parsedPinnedReply(2).contentBlocks
            .filterIsInstance<ContentBlock.RichText>()
            .flatMap { it.paragraphs }
            .flatMap { it.parts }
            .joinToString("") { it.text }

        assertTrue(text.contains("fallback list text"))
    }

    private fun parsedPinnedReply(floor: Int) = parseForumThreadDetail(
        fixture(
            "pinned-rich-replies.html",
            "https://www.javbus.com/forum/forum.php?mod=viewthread&tid=171794&page=2"
        ),
        "https://www.javbus.com"
    ).replies.single { it.floor == floor }

    private fun fixture(name: String, location: String) =
        Jsoup.parse(
            checkNotNull(javaClass.getResourceAsStream("/forum/$name"))
                .bufferedReader(Charsets.UTF_8)
                .use { it.readText() },
            location
        )
}

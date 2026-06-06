package me.jbusdriver.modern.data.parser

import me.jbusdriver.modern.domain.model.ContentBlock
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

    private fun fixture(name: String, location: String) =
        Jsoup.parse(
            checkNotNull(javaClass.getResourceAsStream("/forum/$name"))
                .bufferedReader(Charsets.UTF_8)
                .use { it.readText() },
            location
        )
}

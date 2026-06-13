package me.jbusdriver.modern.ui.forum

import me.jbusdriver.modern.domain.model.ContentBlock
import me.jbusdriver.modern.domain.model.RichList
import me.jbusdriver.modern.domain.model.RichListItem
import me.jbusdriver.modern.domain.model.RichParagraph
import me.jbusdriver.modern.domain.model.TextPart
import org.junit.Assert.assertEquals
import org.junit.Test

class ForumPlainTextTest {
    @Test
    fun `pinned floor label includes pinned prefix`() {
        assertEquals("置頂 · 2#", forumFloorLabel(floor = 2, isPinned = true, pinnedLabel = "置頂"))
        assertEquals("5#", forumFloorLabel(floor = 5, isPinned = false, pinnedLabel = "置頂"))
    }

    @Test
    fun `list indentation stops increasing after three visual levels`() {
        assertEquals(0, forumListIndentStep(depth = 0))
        assertEquals(16, forumListIndentStep(depth = 1))
        assertEquals(16, forumListIndentStep(depth = 2))
        assertEquals(0, forumListIndentStep(depth = 3))
        assertEquals(0, forumListIndentStep(depth = 4))
    }

    @Test
    fun `formats paragraphs lists quotes and restrictions`() {
        val blocks = listOf<ContentBlock>(
            ContentBlock.RichText(listOf(RichParagraph(listOf(TextPart("intro"))))),
            ContentBlock.ListBlock(
                RichList(
                    ordered = true,
                    start = 2,
                    items = listOf(
                        RichListItem(listOf(RichParagraph(listOf(TextPart("second"))))),
                        RichListItem(
                            paragraphs = listOf(RichParagraph(listOf(TextPart("third")))),
                            children = listOf(
                                RichList(
                                    ordered = false,
                                    items = listOf(
                                        RichListItem(listOf(RichParagraph(listOf(TextPart("nested")))))
                                    )
                                )
                            )
                        )
                    )
                )
            ),
            ContentBlock.Quote("Alice", "quoted"),
            ContentBlock.RestrictedNotice("此帖僅作者可見"),
            ContentBlock.Image("ignored.jpg")
        )

        assertEquals(
            "intro\n2. second\n3. third\n  • nested\nAlice：quoted\n此帖僅作者可見",
            buildForumPlainText(blocks)
        )
    }
}

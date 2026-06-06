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

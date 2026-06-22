package me.jbusdriver.modern.ui.forum

import me.jbusdriver.modern.domain.model.ContentBlock
import me.jbusdriver.modern.domain.model.RichParagraph
import me.jbusdriver.modern.domain.model.TextPart
import org.junit.Assert.assertEquals
import org.junit.Test

class ForumPlainTextTest {
    @Test
    fun `dialog preview blocks expand links after their text with spaces`() {
        val blocks = listOf(
            ContentBlock.RichText(
                listOf(
                    RichParagraph(
                        listOf(
                            TextPart("before "),
                            TextPart(
                                text = "link",
                                isLink = true,
                                linkUrl = "https://example.test"
                            ),
                            TextPart(" after")
                        )
                    )
                )
            )
        )

        val expanded = expandForumLinksForPreview(blocks)
            .filterIsInstance<ContentBlock.RichText>()
            .single()
            .paragraphs
            .single()
            .parts

        assertEquals("before ", expanded[0].text)
        assertEquals("link", expanded[1].text)
        assertEquals(" https://example.test ", expanded[2].text)
        assertEquals(" after", expanded[3].text)
    }

    @Test
    fun `plain text expands links after their text with spaces`() {
        val blocks = listOf(
            ContentBlock.RichText(
                listOf(
                    RichParagraph(
                        listOf(
                            TextPart("before "),
                            TextPart(
                                text = "link",
                                isLink = true,
                                linkUrl = "https://example.test"
                            ),
                            TextPart(" after")
                        )
                    )
                )
            )
        )

        assertEquals(
            "before link https://example.test  after",
            buildForumPlainText(blocks)
        )
    }
}

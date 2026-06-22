package me.jbusdriver.modern.ui.forum

import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.test.assertHasClickAction
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import me.jbusdriver.modern.domain.model.ContentBlock
import me.jbusdriver.modern.domain.model.RichList
import me.jbusdriver.modern.domain.model.RichListItem
import me.jbusdriver.modern.domain.model.RichParagraph
import me.jbusdriver.modern.domain.model.TextPart
import org.junit.Rule
import org.junit.Test

class ForumPostContentTest {
    @get:Rule
    val composeRule = createComposeRule()

    @Test
    fun rendersRestrictedNoticeAndListMarkers() {
        composeRule.setContent {
            MaterialTheme {
                ForumPostContent(
                    blocks = listOf(
                        ContentBlock.ListBlock(
                            RichList(
                                ordered = true,
                                items = listOf(
                                    RichListItem(
                                        listOf(RichParagraph(listOf(TextPart("item"))))
                                    )
                                )
                            )
                        ),
                        ContentBlock.RestrictedNotice("此帖僅作者可見")
                    ),
                    onImageClick = { _, _ -> }
                )
            }
        }

        composeRule.onNodeWithText("1.").assertIsDisplayed()
        composeRule.onNodeWithText("此帖僅作者可見").assertIsDisplayed()
        composeRule.onNodeWithTag("restricted_notice").assertIsDisplayed()
    }

    @Test
    fun linkStyledTextHasClickAction() {
        composeRule.setContent {
            MaterialTheme {
                ForumPostContent(
                    blocks = listOf(
                        ContentBlock.RichText(
                            listOf(
                                RichParagraph(
                                    listOf(
                                        TextPart(
                                            text = "link text",
                                            isLink = true,
                                            linkUrl = "https://example.test"
                                        )
                                    )
                                )
                            )
                        )
                    ),
                    onImageClick = { _, _ -> }
                )
            }
        }

        composeRule.onNodeWithText("link text").assertHasClickAction()
    }
}

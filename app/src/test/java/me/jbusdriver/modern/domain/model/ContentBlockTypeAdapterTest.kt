package me.jbusdriver.modern.domain.model

import com.google.gson.JsonParser
import me.jbusdriver.modern.core.GSON
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ContentBlockTypeAdapterTest {
    @Test
    fun `content block hierarchy is sealed`() {
        assertTrue(ContentBlock::class.java.isSealed)
    }

    @Test
    fun `rich text JSON uses richtext discriminator and paragraphs payload`() {
        val json = serialize(ContentBlock.RichText(listOf(paragraph("Body"))))

        assertEquals(setOf("type", "paragraphs"), json.keySet())
        assertEquals("richtext", json["type"].asString)
        assertTrue(json["paragraphs"].isJsonArray)
    }

    @Test
    fun `list JSON uses list discriminator and list payload`() {
        val json = serialize(
            ContentBlock.ListBlock(
                RichList(
                    ordered = false,
                    items = listOf(RichListItem(listOf(paragraph("Item"))))
                )
            )
        )

        assertEquals(setOf("type", "list"), json.keySet())
        assertEquals("list", json["type"].asString)
        assertTrue(json["list"].isJsonObject)
    }

    @Test
    fun `restricted JSON uses restricted discriminator and message payload`() {
        val json = serialize(ContentBlock.RestrictedNotice("Restricted"))

        assertEquals(setOf("type", "message"), json.keySet())
        assertEquals("restricted", json["type"].asString)
        assertEquals("Restricted", json["message"].asString)
    }

    @Test
    fun `styled rich text paragraph round trips`() {
        val block = ContentBlock.RichText(
            paragraphs = listOf(
                RichParagraph(
                    parts = listOf(
                        TextPart(
                            text = "Important link",
                            bold = true,
                            italic = true,
                            underline = true,
                            strikethrough = true,
                            color = "#ff5500",
                            size = ForumTextSize.HEADING,
                            isLink = true
                        )
                    )
                )
            )
        )

        val decoded = roundTrip(block)

        assertEquals(block, decoded)
    }

    @Test
    fun `nested ordered and unordered list round trips`() {
        val block = ContentBlock.ListBlock(
            RichList(
                ordered = true,
                start = 3,
                items = listOf(
                    RichListItem(
                        paragraphs = listOf(paragraph("Parent")),
                        children = listOf(
                            RichList(
                                ordered = false,
                                items = listOf(
                                    RichListItem(paragraphs = listOf(paragraph("Child")))
                                )
                            )
                        )
                    )
                )
            )
        )

        val decoded = roundTrip(block)

        assertEquals(block, decoded)
    }

    @Test
    fun `restricted notice round trips`() {
        val block = ContentBlock.RestrictedNotice("Reply to view this content")

        val decoded = roundTrip(block)

        assertEquals(block, decoded)
    }

    @Test
    fun `legacy rich text parts decode into one paragraph`() {
        val json = """
            {
              "type": "richtext",
              "parts": [
                {"type": "plain", "text": "Hello "},
                {"type": "link", "text": "world", "url": "https://example.test"}
              ]
            }
        """.trimIndent()

        val decoded = GSON.fromJson(json, ContentBlock::class.java)

        assertEquals(
            ContentBlock.RichText(
                listOf(
                    RichParagraph(
                        listOf(
                            TextPart("Hello "),
                            TextPart("world")
                        )
                    )
                )
            ),
            decoded
        )
    }

    @Test
    fun `legacy text block decodes into one paragraph`() {
        val decoded = GSON.fromJson(
            """{"type":"text","content":"Legacy content"}""",
            ContentBlock::class.java
        )

        assertEquals(
            ContentBlock.RichText(listOf(paragraph("Legacy content"))),
            decoded
        )
    }

    @Test
    fun `image JsonNull optional primitives retain defaults`() {
        val decoded = GSON.fromJson(
            """{"type":"image","url":"image.jpg","width":null,"height":null,"fullSize":null,"isGif":null}""",
            ContentBlock::class.java
        )

        assertEquals(ContentBlock.Image("image.jpg"), decoded)
    }

    @Test
    fun `legacy forum reply defaults isPinned to false`() {
        val json = """
            {
              "floor": 1,
              "author": "Alice",
              "authorUid": 42,
              "authorAvatar": "avatar.jpg",
              "authorGroup": "Member",
              "contentBlocks": [],
              "postTime": "2026-06-06"
            }
        """.trimIndent()

        val reply = GSON.fromJson(json, ForumReply::class.java)

        assertFalse(reply.isPinned)
    }

    private fun paragraph(text: String) = RichParagraph(listOf(TextPart(text)))

    private fun serialize(block: ContentBlock) =
        JsonParser.parseString(GSON.toJson(block, ContentBlock::class.java)).asJsonObject

    private fun roundTrip(block: ContentBlock): ContentBlock {
        val json = GSON.toJson(block, ContentBlock::class.java)
        return requireNotNull(GSON.fromJson(json, ContentBlock::class.java))
    }
}

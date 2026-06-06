package me.jbusdriver.modern.domain.model

import androidx.compose.runtime.Immutable
import com.google.gson.Gson
import com.google.gson.JsonObject
import com.google.gson.JsonParser
import com.google.gson.TypeAdapter
import com.google.gson.TypeAdapterFactory
import com.google.gson.reflect.TypeToken
import com.google.gson.stream.JsonReader
import com.google.gson.stream.JsonWriter
import java.lang.reflect.Type

private val RICH_PARAGRAPHS_TYPE: Type = object : TypeToken<List<RichParagraph>>() {}.type
private val RICH_LIST_TYPE: Type = object : TypeToken<RichList>() {}.type

@Immutable
data class ForumBoardGroup(
    val name: String,
    val boards: List<ForumBoard>
)

@Immutable
data class ForumBanner(
    val tid: Int,
    val title: String,
    val imageUrl: String
)

@Immutable
data class ForumSummaryThread(
    val tid: Int,
    val title: String,
    val author: String
)

@Immutable
data class ForumHomeSummary(
    val latestThreads: List<ForumSummaryThread> = emptyList(),
    val latestReplies: List<ForumSummaryThread> = emptyList(),
    val hotTopics: List<ForumSummaryThread> = emptyList()
)

@Immutable
data class ForumHomeData(
    val banners: List<ForumBanner>,
    val summary: ForumHomeSummary,
    val boardGroups: List<ForumBoardGroup>
)

@Immutable
data class ForumBoard(
    val id: Int,
    val name: String,
    val description: String,
    val todayPosts: Int,
    val totalThreads: String,
    val totalPosts: String,
    val lastPost: LastPost,
    val typeId: Int? = null
)

@Immutable
data class LastPost(
    val title: String,
    val author: String,
    val time: String
)

@Immutable
data class ForumThread(
    val tid: Int,
    val typeId: Int,
    val typeName: String,
    val typeColor: String,
    val title: String,
    val author: String,
    val authorUid: Int,
    val authorAvatar: String,
    val dateLine: String,
    val viewCount: Int,
    val replyCount: Int,
    val lastReplyAuthor: String,
    val lastReplyTime: String,
    val images: List<String>,
    val isPinned: Boolean,
    val isDigest: Boolean,
    val pages: Int
)

@Immutable
data class ForumThreadDetail(
    val tid: Int,
    val typeId: Int,
    val typeName: String,
    val typeColor: String,
    val title: String,
    val viewCount: Int,
    val replyCount: Int,
    val author: String,
    val authorUid: Int,
    val authorAvatar: String,
    val postTime: String,
    val contentBlocks: List<ContentBlock>,
    val comments: List<Comment>,
    val replies: List<ForumReply>,
    val pageInfo: PageInfo
)

@Immutable
data class Comment(
    val author: String,
    val authorAvatar: String,
    val content: String,
    val time: String
)

@Immutable
data class ForumReply(
    val floor: Int,
    val author: String,
    val authorUid: Int,
    val authorAvatar: String,
    val authorGroup: String,
    val contentBlocks: List<ContentBlock>,
    val postTime: String,
    val isPinned: Boolean = false
)

@Immutable
enum class ForumTextSize { BODY, EMPHASIS, HEADING }

@Immutable
data class TextPart(
    val text: String,
    val bold: Boolean = false,
    val italic: Boolean = false,
    val underline: Boolean = false,
    val strikethrough: Boolean = false,
    val color: String? = null,
    val size: ForumTextSize = ForumTextSize.BODY,
    val isLink: Boolean = false
)

@Immutable
data class RichParagraph(val parts: List<TextPart>)

@Immutable
data class RichListItem(
    val paragraphs: List<RichParagraph>,
    val children: List<RichList> = emptyList()
)

@Immutable
data class RichList(
    val ordered: Boolean,
    val start: Int = 1,
    val items: List<RichListItem>
)

@Immutable
sealed class ContentBlock {
    @Immutable
    data class RichText(val paragraphs: List<RichParagraph>) : ContentBlock()

    @Immutable
    data class ListBlock(val list: RichList) : ContentBlock()

    @Immutable
    data class Image(val url: String, val width: Int = 0, val height: Int = 0, val isFullSize: Boolean = false, val isGif: Boolean = false) : ContentBlock()

    @Immutable
    data class Quote(val author: String, val content: String) : ContentBlock()

    @Immutable
    data class RestrictedNotice(val message: String) : ContentBlock()
}

class ContentBlockTypeAdapter(private val gson: Gson) : TypeAdapter<ContentBlock>() {
    override fun write(out: JsonWriter, value: ContentBlock?) {
        if (value == null) {
            out.nullValue()
            return
        }
        val json = JsonObject()
        when (value) {
            is ContentBlock.RichText -> {
                json.addProperty("type", "richtext")
                json.add("paragraphs", gson.toJsonTree(value.paragraphs, RICH_PARAGRAPHS_TYPE))
            }
            is ContentBlock.ListBlock -> {
                json.addProperty("type", "list")
                json.add("list", gson.toJsonTree(value.list, RICH_LIST_TYPE))
            }
            is ContentBlock.Image -> {
                json.addProperty("type", "image")
                json.addProperty("url", value.url)
                if (value.width > 0) json.addProperty("width", value.width)
                if (value.height > 0) json.addProperty("height", value.height)
                if (value.isFullSize) json.addProperty("fullSize", true)
                if (value.isGif) json.addProperty("isGif", true)
            }
            is ContentBlock.Quote -> {
                json.addProperty("type", "quote")
                json.addProperty("author", value.author)
                json.addProperty("content", value.content)
            }
            is ContentBlock.RestrictedNotice -> {
                json.addProperty("type", "restricted")
                json.addProperty("message", value.message)
            }
        }
        gson.toJson(json, out)
    }

    override fun read(`in`: JsonReader): ContentBlock? {
        val element = JsonParser.parseReader(`in`)
        if (element.isJsonNull || !element.isJsonObject) return null
        val json = element.asJsonObject
        return when (json.string("type")) {
            "richtext" -> readRichText(json)
            "text" -> ContentBlock.RichText(listOf(RichParagraph(listOf(TextPart(json.string("content"))))))
            "list" -> json["list"]
                ?.takeUnless { it.isJsonNull }
                ?.let { ContentBlock.ListBlock(gson.fromJson(it, RICH_LIST_TYPE)) }
            "image" -> ContentBlock.Image(
                url = json.string("url"),
                width = json.int("width"),
                height = json.int("height"),
                isFullSize = json.boolean("fullSize"),
                isGif = json.boolean("isGif")
            )
            "quote" -> ContentBlock.Quote(json.string("author"), json.string("content"))
            "restricted" -> ContentBlock.RestrictedNotice(json.string("message"))
            else -> null
        }
    }

    private fun readRichText(json: JsonObject): ContentBlock.RichText {
        json["paragraphs"]
            ?.takeUnless { it.isJsonNull }
            ?.let { paragraphs ->
                return ContentBlock.RichText(
                    gson.fromJson<List<RichParagraph>>(paragraphs, RICH_PARAGRAPHS_TYPE)
                )
            }

        val parts = json["parts"]
            ?.takeUnless { it.isJsonNull }
            ?.asJsonArray
            ?.mapNotNull { element ->
                if (!element.isJsonObject) return@mapNotNull null
                val part = element.asJsonObject
                when (val type = part.string("type")) {
                    "plain", "link" -> TextPart(
                        text = part.string("text"),
                        isLink = type == "link"
                    )
                    else -> null
                }
            }
            .orEmpty()
        return ContentBlock.RichText(listOf(RichParagraph(parts)))
    }
}

object ContentBlockAdapterFactory : TypeAdapterFactory {
    override fun <T : Any> create(gson: Gson, type: TypeToken<T>): TypeAdapter<T>? {
        if (!ContentBlock::class.java.isAssignableFrom(type.rawType)) return null
        @Suppress("UNCHECKED_CAST")
        return ContentBlockTypeAdapter(gson) as TypeAdapter<T>
    }
}

private fun JsonObject.string(name: String): String =
    get(name)?.takeUnless { it.isJsonNull }?.asString.orEmpty()

private fun JsonObject.int(name: String): Int =
    get(name)?.takeUnless { it.isJsonNull }?.asInt ?: 0

private fun JsonObject.boolean(name: String): Boolean =
    get(name)?.takeUnless { it.isJsonNull }?.asBoolean ?: false

@Immutable
data class ForumTypeFilter(
    val typeId: Int,
    val name: String,
    val color: String,
    val count: Int
)

@Immutable
data class ForumThreadPageResult(
    val threads: List<ForumThread>,
    val pageInfo: PageInfo,
    val typeFilters: List<ForumTypeFilter>
)

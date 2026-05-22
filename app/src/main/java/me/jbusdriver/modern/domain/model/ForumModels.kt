package me.jbusdriver.modern.domain.model

import androidx.compose.runtime.Immutable
import com.google.gson.Gson
import com.google.gson.TypeAdapter
import com.google.gson.TypeAdapterFactory
import com.google.gson.reflect.TypeToken
import com.google.gson.stream.JsonReader
import com.google.gson.stream.JsonWriter

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
    val postTime: String
)

@Immutable
sealed class TextPart {
    @Immutable
    data class Plain(val text: String) : TextPart()

    @Immutable
    data class Link(val text: String, val url: String) : TextPart()
}

@Immutable
sealed class ContentBlock {
    @Immutable
    data class RichText(val parts: List<TextPart>) : ContentBlock()

    @Immutable
    data class Image(val url: String, val width: Int = 0, val height: Int = 0, val isFullSize: Boolean = false, val isGif: Boolean = false) : ContentBlock()

    @Immutable
    data class Quote(val author: String, val content: String) : ContentBlock()
}

class ContentBlockTypeAdapter : TypeAdapter<ContentBlock>() {
    override fun write(out: JsonWriter, value: ContentBlock?) {
        if (value == null) { out.nullValue(); return }
        out.beginObject()
        when (value) {
            is ContentBlock.RichText -> {
                out.name("type").value("richtext")
                out.name("parts").beginArray()
                value.parts.forEach { part ->
                    out.beginObject()
                    when (part) {
                        is TextPart.Plain -> { out.name("type").value("plain").name("text").value(part.text) }
                        is TextPart.Link -> { out.name("type").value("link").name("text").value(part.text).name("url").value(part.url) }
                    }
                    out.endObject()
                }
                out.endArray()
            }
            is ContentBlock.Image -> {
                out.name("type").value("image").name("url").value(value.url)
                if (value.width > 0) out.name("width").value(value.width)
                if (value.height > 0) out.name("height").value(value.height)
                if (value.isFullSize) out.name("fullSize").value(true)
                if (value.isGif) out.name("isGif").value(true)
            }
            is ContentBlock.Quote -> { out.name("type").value("quote").name("author").value(value.author).name("content").value(value.content) }
        }
        out.endObject()
    }

    override fun read(`in`: JsonReader): ContentBlock? {
        if (`in`.peek() == com.google.gson.stream.JsonToken.NULL) {
            `in`.nextNull()
            return null
        }
        `in`.beginObject()
        var type = ""
        var parts = listOf<TextPart>()
        var url = ""
        var width = 0
        var height = 0
        var fullSize = false
        var isGif = false
        var author = ""
        var content = ""
        while (`in`.hasNext()) {
            when (`in`.nextName()) {
                "type" -> type = `in`.nextString()
                "parts" -> {
                    val list = mutableListOf<TextPart>()
                    `in`.beginArray()
                    while (`in`.hasNext()) {
                        `in`.beginObject()
                        var partType = ""
                        var partText = ""
                        var partUrl = ""
                        while (`in`.hasNext()) {
                            when (`in`.nextName()) {
                                "type" -> partType = `in`.nextString()
                                "text" -> partText = `in`.nextString()
                                "url" -> partUrl = `in`.nextString()
                                else -> `in`.skipValue()
                            }
                        }
                        `in`.endObject()
                        when (partType) {
                            "plain" -> list.add(TextPart.Plain(partText))
                            "link" -> list.add(TextPart.Link(partText, partUrl))
                        }
                    }
                    `in`.endArray()
                    parts = list
                }
                "url" -> url = `in`.nextString()
                "width" -> width = `in`.nextInt()
                "height" -> height = `in`.nextInt()
                "fullSize" -> fullSize = `in`.nextBoolean()
                "isGif" -> isGif = `in`.nextBoolean()
                "author" -> author = `in`.nextString()
                "content" -> content = `in`.nextString()
                else -> `in`.skipValue()
            }
        }
        `in`.endObject()
        return when (type) {
            "richtext" -> ContentBlock.RichText(parts)
            "text" -> ContentBlock.RichText(listOf(TextPart.Plain(content)))
            "image" -> ContentBlock.Image(url, width, height, fullSize, isGif)
            "quote" -> ContentBlock.Quote(author, content)
            else -> null
        }
    }
}

object ContentBlockAdapterFactory : TypeAdapterFactory {
    override fun <T : Any> create(gson: Gson, type: TypeToken<T>): TypeAdapter<T>? {
        if (!ContentBlock::class.java.isAssignableFrom(type.rawType)) return null
        @Suppress("UNCHECKED_CAST")
        return ContentBlockTypeAdapter() as TypeAdapter<T>
    }
}

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

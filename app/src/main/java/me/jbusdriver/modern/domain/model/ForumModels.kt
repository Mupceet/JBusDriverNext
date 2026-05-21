package me.jbusdriver.modern.domain.model

import androidx.compose.runtime.Immutable
import com.google.gson.TypeAdapter
import com.google.gson.stream.JsonReader
import com.google.gson.stream.JsonWriter

@Immutable
data class ForumBoardGroup(
    val name: String,
    val boards: List<ForumBoard>
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
sealed class ContentBlock {
    @Immutable
    data class Text(val text: String) : ContentBlock()

    @Immutable
    data class Image(val url: String) : ContentBlock()

    @Immutable
    data class Quote(val author: String, val content: String) : ContentBlock()
}

class ContentBlockTypeAdapter : TypeAdapter<ContentBlock>() {
    override fun write(out: JsonWriter, value: ContentBlock?) {
        if (value == null) { out.nullValue(); return }
        out.beginObject()
        when (value) {
            is ContentBlock.Text -> { out.name("type").value("text").name("text").value(value.text) }
            is ContentBlock.Image -> { out.name("type").value("image").name("url").value(value.url) }
            is ContentBlock.Quote -> { out.name("type").value("quote").name("author").value(value.author).name("content").value(value.content) }
        }
        out.endObject()
    }

    override fun read(`in`: JsonReader): ContentBlock? {
        `in`.beginObject()
        var type = ""
        var text = ""
        var url = ""
        var author = ""
        var content = ""
        while (`in`.hasNext()) {
            when (`in`.nextName()) {
                "type" -> type = `in`.nextString()
                "text" -> text = `in`.nextString()
                "url" -> url = `in`.nextString()
                "author" -> author = `in`.nextString()
                "content" -> content = `in`.nextString()
                else -> `in`.skipValue()
            }
        }
        `in`.endObject()
        return when (type) {
            "text" -> ContentBlock.Text(text)
            "image" -> ContentBlock.Image(url)
            "quote" -> ContentBlock.Quote(author, content)
            else -> null
        }
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

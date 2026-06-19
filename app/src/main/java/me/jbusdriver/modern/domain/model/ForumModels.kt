package me.jbusdriver.modern.domain.model

import androidx.compose.runtime.Immutable

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
    val pages: Int,
    val isLocked: Boolean = false,
    val isHot: Boolean = false
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
    val isLink: Boolean = false,
    val inlineImageUrl: String = "",
    val inlineImageAlt: String = ""
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
    data class Image(
        val url: String,
        val width: Int = 0,
        val height: Int = 0,
        val isFullSize: Boolean = false,
        val isGif: Boolean = false
    ) : ContentBlock()

    @Immutable
    data class Quote(val author: String, val content: String) : ContentBlock()

    @Immutable
    data class RestrictedNotice(val message: String) : ContentBlock()
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

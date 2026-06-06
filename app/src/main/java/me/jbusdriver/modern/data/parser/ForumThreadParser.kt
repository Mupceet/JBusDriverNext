package me.jbusdriver.modern.data.parser

import me.jbusdriver.modern.domain.model.Comment
import me.jbusdriver.modern.domain.model.ContentBlock
import me.jbusdriver.modern.domain.model.ForumReply
import me.jbusdriver.modern.domain.model.ForumThread
import me.jbusdriver.modern.domain.model.ForumThreadDetail
import me.jbusdriver.modern.domain.model.ForumThreadPageResult
import me.jbusdriver.modern.domain.model.ForumTypeFilter
import me.jbusdriver.modern.domain.model.PageInfo
import me.jbusdriver.modern.domain.model.RichParagraph
import me.jbusdriver.modern.domain.model.TextPart
import org.jsoup.nodes.Document
import org.jsoup.nodes.Element
import org.jsoup.nodes.Node
import org.jsoup.nodes.TextNode

fun parseForumTypeFilters(doc: Document): List<ForumTypeFilter> {
    val currentPageTypeId = Regex("typeid=(\\d+)").find(doc.location())?.groupValues?.get(1)?.toIntOrNull()
    return doc.select("ul#thread_types > li > a").mapNotNull { link ->
        val href = link.attr("href")
        val li = link.parent()
        val isAll = li?.id() == "ttp_all"
        if (isAll) return@mapNotNull null
        val typeId = Regex("typeid=(\\d+)").find(href)?.groupValues?.get(1)?.toIntOrNull()
            ?: if (li?.hasClass("a") == true && currentPageTypeId != null) currentPageTypeId
            else return@mapNotNull null
        val countText = link.select("span.num").text().trim()
        val count = countText.toIntOrNull() ?: 0
        val name = link.text().trim().removeSuffix(countText).trim()
        val color = link.select("font").attr("color").ifBlank { "#666666" }
        ForumTypeFilter(typeId, name, color, count)
    }
}

fun parseForumThreads(doc: Document, baseUrl: String): ForumThreadPageResult {
    val typeFilters = parseForumTypeFilters(doc)
    val threads = mutableListOf<ForumThread>()

    doc.select("tbody[id^=stickthread_]").forEach { tbody ->
        parseSingleThread(tbody, baseUrl, isPinned = true)?.let { threads.add(it) }
    }
    doc.select("tbody[id^=normalthread_]").forEach { tbody ->
        parseSingleThread(tbody, baseUrl, isPinned = false)?.let { threads.add(it) }
    }

    return ForumThreadPageResult(threads, parseForumPageInfo(doc), typeFilters)
}

private fun parseSingleThread(tbody: Element, baseUrl: String, isPinned: Boolean): ForumThread? {
    val titleLink = tbody.select(".post_infolist_tit a.s").firstOrNull() ?: return null
    val tid = titleLink.attr("href")
        .let { Regex("tid=(\\d+)").find(it)?.groupValues?.get(1)?.toIntOrNull() }
        ?: return null

    val typeLink = tbody.select(".post_infolist_tit em a").firstOrNull()
    val typeId = typeLink?.attr("href")
        ?.let { Regex("typeid=(\\d+)").find(it)?.groupValues?.get(1)?.toIntOrNull() } ?: 0
    val typeName = typeLink?.text()?.trim() ?: ""
    val typeColor = typeLink?.select("font")?.attr("color")?.ifBlank { "#666666" } ?: "#666666"

    val authorLink = tbody.select(".author a").firstOrNull()
    val authorUid = authorLink?.attr("href")
        ?.let { Regex("uid=(\\d+)").find(it)?.groupValues?.get(1)?.toIntOrNull() } ?: 0

    val images = tbody.select(".post_infolist_tit a img[src]")
        .map { it.attr("src").wrapForumImage(baseUrl) }
        .filter {
            it.isNotEmpty() &&
                !it.contains("pin_") &&
                !it.contains("small.gif") &&
                !it.contains("hot.jpg") &&
                !it.contains("recommend") &&
                !it.contains("arw_r")
        }

    val pages = tbody.select(".tps a").lastOrNull()?.text()?.toIntOrNull() ?: 1

    return ForumThread(
        tid = tid,
        typeId = typeId,
        typeName = typeName,
        typeColor = typeColor,
        title = titleLink.text().trim(),
        author = authorLink?.text()?.trim() ?: "",
        authorUid = authorUid,
        authorAvatar = tbody.select(".post_avatar img[src]").attr("src"),
        dateLine = tbody.select(".dateline span[title]").attr("title")
            .ifBlank { tbody.select(".dateline span").text().trim() },
        viewCount = tbody.select(".views").text().trim().toIntOrNull() ?: 0,
        replyCount = tbody.select(".reply").text().trim().toIntOrNull() ?: 0,
        lastReplyAuthor = tbody.select(".time a").text().trim(),
        lastReplyTime = tbody.select(".time span[title]").attr("title")
            .ifBlank { tbody.select(".time span").lastOrNull()?.text()?.trim() ?: "" },
        images = images,
        isPinned = isPinned,
        isDigest = tbody.select("img[alt=recommend]").isNotEmpty(),
        pages = pages
    )
}

private fun parseForumPageInfo(doc: Document): PageInfo {
    val currentPage = doc.select(".pg strong").firstOrNull()?.text()?.toIntOrNull() ?: 1
    val nextPage = doc.select(".pg a.nxt").firstOrNull()?.attr("href")
        ?.let { Regex("page=(\\d+)").find(it)?.groupValues?.get(1)?.toIntOrNull() }
        ?: currentPage
    return PageInfo(activePage = currentPage, nextPage = nextPage)
}

fun parseForumThreadDetail(doc: Document, baseUrl: String): ForumThreadDetail {
    val title = doc.select("#thread_subject").firstOrNull()?.text()?.trim() ?: ""

    val typeLink = doc.select("h1.ts a, .nthread_info h1.ts a").firstOrNull()
    val typeId = typeLink?.attr("href")
        ?.let { Regex("typeid=(\\d+)").find(it)?.groupValues?.get(1)?.toIntOrNull() } ?: 0
    val typeName = typeLink?.text()?.replace("[", "")?.replace("]", "")?.trim() ?: ""
    val typeColor = typeLink?.select("font")?.attr("color")?.ifBlank { "#666666" } ?: "#666666"

    val tidMatch = Regex("tid=(\\d+)").find(doc.location())
        ?: Regex("tid=(\\d+)").find(doc.html())

    val viewCount = doc.select(".xi1").firstOrNull()?.text()?.toIntOrNull() ?: 0
    val replyCount = doc.select(".xi1").getOrNull(1)?.text()?.toIntOrNull() ?: 0

    val firstPost = doc.select(".nthread_firstpostbox").firstOrNull()
    val authorLink = firstPost?.select(".authi .au, .nthread_other .au")?.firstOrNull()
        ?: doc.select(".nthread_other .au").firstOrNull()
    val authorName = authorLink?.text()?.trim() ?: ""
    val authorUid = authorLink?.attr("href")
        ?.let { Regex("uid=(\\d+)").find(it)?.groupValues?.get(1)?.toIntOrNull() } ?: 0
    val postTime = doc.select(".nthread_other span.mr10").firstOrNull()?.text()?.trim()
        ?: doc.select(".authi span.mr10").firstOrNull()?.text()?.trim() ?: ""

    val avatarSrc = doc.select(".nthread_firstpost .post_avatar img[src]").attr("src")
        .ifBlank { doc.select("div.viewthread_authorinfo .avatar img[src]").firstOrNull()?.attr("src") ?: "" }
        .ifBlank { doc.select(".pls.favatar .avatar img[src]").firstOrNull()?.attr("src") ?: "" }

    val typeOptionBlocks = parsePostContent(firstPost?.select("div.typeoption")?.firstOrNull(), baseUrl)
    val contentBlocks = typeOptionBlocks + parsePostContent(firstPost?.select("td.t_f")?.firstOrNull(), baseUrl)

    val comments = firstPost?.select("div.cm div.pstl")?.map { pstl ->
        Comment(
            author = pstl.select(".psta a.xi2").text().trim(),
            authorAvatar = pstl.select(".psta img[src]").attr("src"),
            content = pstl.select(".psti").firstOrNull()?.ownText()?.trim()
                ?: pstl.select(".psti").text().trim(),
            time = pstl.select(".pisti .xg1 span[title]").attr("title")
                .ifBlank { pstl.select(".psti .xg1 span[title]").attr("title") }
                .ifBlank { pstl.select(".psti .xg1 span").text().trim() }
        )
    } ?: emptyList()

    val replies = doc.select(".nthread_postbox").filter { !it.hasClass("nthread_firstpostbox") }.mapNotNull { postBox ->
        val floor = parseReplyFloor(postBox) ?: return@mapNotNull null
        val replyAuthorLink = postBox.select("a.xw1").firstOrNull()
        val replyAuthorUid = replyAuthorLink?.attr("href")
            ?.let { Regex("uid=(\\d+)").find(it)?.groupValues?.get(1)?.toIntOrNull() } ?: 0
        val replyAvatar = postBox.select(".favatar .avatar img[src]").attr("src")
            .ifBlank { postBox.select(".pls .avatar img[src]").attr("src") }

        ForumReply(
            floor = floor.number,
            author = replyAuthorLink?.text()?.trim() ?: "",
            authorUid = replyAuthorUid,
            authorAvatar = replyAvatar,
            authorGroup = postBox.select(".pls em a").text().trim(),
            contentBlocks = parseReplyContent(postBox, baseUrl),
            postTime = postBox.select("em[id^=authorposton] span[title]").attr("title")
                .ifBlank { postBox.select("em[id^=authorposton]").text().trim() },
            isPinned = floor.isPinned
        )
    }

    return ForumThreadDetail(
        tid = tidMatch?.groupValues?.get(1)?.toIntOrNull() ?: 0,
        typeId = typeId,
        typeName = typeName,
        typeColor = typeColor,
        title = title,
        viewCount = viewCount,
        replyCount = replyCount,
        author = authorName,
        authorUid = authorUid,
        authorAvatar = avatarSrc,
        postTime = postTime,
        contentBlocks = contentBlocks,
        comments = comments,
        replies = replies,
        pageInfo = parseForumPageInfo(doc)
    )
}

private data class ReplyFloor(val number: Int, val isPinned: Boolean)

private fun parseReplyFloor(postBox: Element): ReplyFloor? {
    val anchor = postBox.selectFirst("a[id^=postnum]") ?: return null
    val number = anchor.selectFirst("em")?.text()?.trim()?.toIntOrNull()
        ?: Regex("(\\d+)\\s*#").find(anchor.text())?.groupValues?.get(1)?.toIntOrNull()
        ?: return null
    val isPinned = anchor.select("img[title*=置頂], img[src*=settop]").isNotEmpty() ||
        anchor.text().contains("來自")
    return ReplyFloor(number, isPinned)
}

private fun parseReplyContent(postBox: Element, baseUrl: String): List<ContentBlock> {
    val restricted = postBox.selectFirst(".locked")?.text()?.trim().orEmpty()
    if (restricted.isNotEmpty()) return listOf(ContentBlock.RestrictedNotice(restricted))
    return parsePostContent(postBox.selectFirst("td.t_f"), baseUrl)
}

private fun parsePostContent(td: Element?, baseUrl: String): List<ContentBlock> {
    if (td == null) return emptyList()

    val blocks = mutableListOf<ContentBlock>()
    val parts = mutableListOf<TextPart>()

    fun flushParts() {
        val nonEmpty = parts.toList()
        parts.clear()
        if (nonEmpty.isNotEmpty()) {
            blocks.add(ContentBlock.RichText(listOf(RichParagraph(nonEmpty))))
        }
    }

    fun processNode(node: Node) {
        when (node) {
            is TextNode -> {
                val text = node.text().trim()
                if (text.isNotEmpty()) parts.add(TextPart(text))
            }
            is Element -> {
                when (node.tagName()) {
                    "br" -> flushParts()
                    "a" -> {
                        val text = node.text().trim()
                        if (text.isNotBlank()) {
                            parts.add(TextPart(text))
                        }
                    }
                    "img" -> {
                        val src = node.attr("src").wrapForumImage(baseUrl)
                        if (src.isNotEmpty() &&
                            !src.contains("arw_r") &&
                            !src.contains("userinfo.gif") &&
                            !src.contains("fav.gif") &&
                            !src.contains("rec_add")
                        ) {
                            flushParts()
                            val width = node.attr("width").toIntOrNull() ?: 0
                            val height = node.attr("height").toIntOrNull() ?: 0
                            blocks.add(ContentBlock.Image(src, width, height, node.hasClass("zoom"), src.isGifUrl()))
                        }
                    }
                    "div" -> {
                        if (node.hasClass("quote")) {
                            flushParts()
                            val blockquote = node.select("blockquote").firstOrNull()
                            val authorName = blockquote?.select("a[href]")?.firstOrNull()?.text()?.trim() ?: ""
                            val quoteText = blockquote?.let { bq ->
                                val clone = bq.clone()
                                clone.select("font > a").remove()
                                clone.text().trim()
                            } ?: ""
                            if (quoteText.isNotEmpty()) {
                                blocks.add(ContentBlock.Quote(authorName, quoteText))
                            }
                        } else if (!node.hasClass("modact") && !node.hasClass("locked") && !node.className().contains("cm")) {
                            node.childNodes().forEach { processNode(it) }
                        }
                    }
                    "font", "table" -> node.childNodes().forEach { processNode(it) }
                    else -> node.childNodes().forEach { processNode(it) }
                }
            }
        }
    }

    td.childNodes().forEach { processNode(it) }
    flushParts()

    return blocks
}

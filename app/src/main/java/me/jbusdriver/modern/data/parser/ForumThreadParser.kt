package me.jbusdriver.modern.data.parser

import me.jbusdriver.modern.domain.model.Comment
import me.jbusdriver.modern.domain.model.ContentBlock
import me.jbusdriver.modern.domain.model.ForumCommentPageResult
import me.jbusdriver.modern.domain.model.ForumReply
import me.jbusdriver.modern.domain.model.ForumThread
import me.jbusdriver.modern.domain.model.ForumThreadDetail
import me.jbusdriver.modern.domain.model.ForumThreadPageResult
import me.jbusdriver.modern.domain.model.ForumTypeFilter
import me.jbusdriver.modern.domain.model.PageInfo
import org.jsoup.nodes.Document
import org.jsoup.nodes.Element

fun parseForumTypeFilters(doc: Document): List<ForumTypeFilter> {
    val currentPageTypeId =
        Regex("typeid=(\\d+)").find(doc.location())?.groupValues?.get(1)?.toIntOrNull()
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

    val typeLink = tbody.select(".post_infolist_tit em a[href*=typeid=]").firstOrNull()
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
                    !it.contains("folder_lock") &&
                    !it.contains("arw_r")
        }

    val pages = tbody.select(".tps a").lastOrNull()?.text()?.toIntOrNull() ?: 1
    val titleImages = tbody.select(".post_infolist_tit img[src]")

    return ForumThread(
        tid = tid,
        typeId = typeId,
        typeName = typeName,
        typeColor = typeColor,
        title = titleLink.text().trim(),
        author = authorLink?.text()?.trim() ?: "",
        authorUid = authorUid,
        authorAvatar = tbody.select(".post_avatar img[src]").attr("src"),
        // 统一取 .dateline 的可见文本：新格式 <span class="dateline"><span title="2026-6-6">前天 00:35</span></span>
        // 显示人性化的相对时间，比 title 里的绝对日期更直观；旧格式叶子 span 的文本同样取到。
        dateLine = tbody.select(".dateline").firstOrNull()?.text()?.trim().orEmpty(),
        viewCount = tbody.select(".views").text().trim().toIntOrNull() ?: 0,
        replyCount = tbody.select(".reply").text().trim().toIntOrNull() ?: 0,
        lastReplyAuthor = tbody.select(".time a").text().trim(),
        lastReplyTime = tbody.select(".time span[title]").attr("title")
            .ifBlank { tbody.select(".time span").lastOrNull()?.text()?.trim() ?: "" },
        images = images,
        isPinned = isPinned,
        isDigest = titleImages.any {
            it.attr("alt").equals("recommend", ignoreCase = true) ||
                    it.attr("src").contains("recommend", ignoreCase = true)
        },
        pages = pages,
        isLocked = titleImages.any {
            it.attr("src").contains("folder_lock", ignoreCase = true)
        },
        isHot = titleImages.any {
            it.attr("alt").equals("heatlevel", ignoreCase = true) ||
                    it.attr("src").contains("hot.jpg", ignoreCase = true)
        }
    )
}

private fun parseForumPageInfo(doc: Document): PageInfo {
    val replyPager = doc.select(".pg")
        .firstOrNull { pager ->
            pager.select("a[href]").any { isForumContentPageLink(it.attr("href")) }
        }
    val currentPage = replyPager?.selectFirst("strong")?.text()?.toIntOrNull() ?: 1
    // 首楼留言分页会注入一条 href="javascript:;" 的 .nxt（真实 page 在 onclick 的 ajaxget 里），
    // 必须跳过它，取第一个携带真实 page= 目标的回复分页链接。
    val nextPage = replyPager?.select("a.nxt[href]")
        ?.firstOrNull { isForumContentPageLink(it.attr("href")) }
        ?.attr("href")
        ?.let { PAGE_PARAM.find(it)?.groupValues?.get(1)?.toIntOrNull() }
        ?: currentPage
    return PageInfo(activePage = currentPage, nextPage = nextPage)
}

fun parseForumThreadDetail(doc: Document, baseUrl: String): ForumThreadDetail {
    val titleSubject = doc.select("#thread_subject").firstOrNull()
    val title = titleSubject?.ownText()?.trim()?.ifEmpty { null }
        ?: titleSubject?.text()?.trim()
        ?: ""

    val typeLink =
        doc.select("h1.ts a[href*=typeid=], .nthread_info h1.ts a[href*=typeid=]").firstOrNull()
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
    val postTime = (
            doc.select(".nthread_other span.mr10").firstOrNull()?.text()?.trim()
                ?: doc.select(".authi span.mr10").firstOrNull()?.text()?.trim()
                ?: ""
            ).cleanForumPostTime()

    val avatarSrc = doc.select(".nthread_firstpost .post_avatar img[src]").attr("src")
        .ifBlank {
            doc.select("div.viewthread_authorinfo .avatar img[src]").firstOrNull()?.attr("src")
                ?: ""
        }
        .ifBlank { doc.select(".pls.favatar .avatar img[src]").firstOrNull()?.attr("src") ?: "" }

    val typeOptionBlocks = parseForumPostContent(firstPost?.selectFirst("div.typeoption"), baseUrl)
    val contentBlocks =
        typeOptionBlocks + parseForumPostContent(firstPost?.selectFirst("td.t_f"), baseUrl)

    val firstPostPid = parsePostPid(firstPost)
    val firstPostCommentBlock = firstPostPid.takeIf { it != 0 }
        ?.let { firstPost?.selectFirst("div#comment_$it.cm") }
    val comments = parseForumComments(firstPostCommentBlock, baseUrl)
    val firstPostCommentPageInfo = parseForumCommentPageInfo(firstPostCommentBlock)

    val replies = doc.select(".nthread_postbox").filter { !it.hasClass("nthread_firstpostbox") }
        .mapNotNull { postBox ->
            val floor = parseReplyFloor(postBox) ?: return@mapNotNull null
            val replyAuthorLink = postBox.select("a.xw1").firstOrNull()
            val replyAuthorUid = replyAuthorLink?.attr("href")
                ?.let { Regex("uid=(\\d+)").find(it)?.groupValues?.get(1)?.toIntOrNull() } ?: 0
            val replyAvatar = postBox.select(".favatar .avatar img[src]").attr("src")
                .ifBlank { postBox.select(".pls .avatar img[src]").attr("src") }
            val replyPid = parsePostPid(postBox)
            val replyCommentBlock = replyPid.takeIf { it != 0 }
                ?.let { postBox.selectFirst("div#comment_$it.cm") }
            val replyComments = parseForumComments(replyCommentBlock, baseUrl)
            val replyCommentPageInfo = parseForumCommentPageInfo(replyCommentBlock)

            ForumReply(
                floor = floor.number,
                author = replyAuthorLink?.text()?.trim() ?: "",
                authorUid = replyAuthorUid,
                authorAvatar = replyAvatar,
                authorGroup = postBox.select(".pls em a").text().trim(),
                contentBlocks = parseReplyContent(postBox, baseUrl),
                postTime = postBox.select("em[id^=authorposton] span[title]").attr("title")
                    .ifBlank { postBox.select("em[id^=authorposton]").text().trim() }
                    .cleanForumPostTime(),
                isPinned = floor.isPinned,
                pid = replyPid,
                comments = replyComments,
                commentPageInfo = replyCommentPageInfo
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
        pageInfo = parseForumPageInfo(doc),
        pid = firstPostPid,
        commentPageInfo = firstPostCommentPageInfo
    )
}

fun parseForumFloorComments(
    doc: Document,
    baseUrl: String,
    pid: Int
): ForumCommentPageResult {
    val root = doc.forumCommentRoot(pid)
    val comments = parseForumComments(root, baseUrl)
    val pageInfo = parseForumCommentPageInfo(root)
    return ForumCommentPageResult(
        pid = pid,
        comments = comments,
        pageInfo = pageInfo
    )
}

private fun Document.forumCommentRoot(pid: Int): Element {
    selectFirst("div#comment_$pid.cm")?.let { return it }
    val cdataHtml = selectFirst("root")
        ?.wholeText()
        ?.trim()
        ?.takeIf { it.contains("pstl") || it.contains("psth") }
    if (!cdataHtml.isNullOrBlank()) {
        return org.jsoup.Jsoup.parseBodyFragment(cdataHtml, location()).body()
    }
    return body()
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
    return parseForumPostContent(postBox.selectFirst("td.t_f"), baseUrl)
}

private fun parsePostPid(postBox: Element?): Int {
    if (postBox == null) return 0
    return listOf(
        postBox.id(),
        postBox.selectFirst("td[id^=postmessage_]")?.id().orEmpty(),
        postBox.selectFirst("div[id^=comment_]")?.id().orEmpty(),
        postBox.selectFirst("div[id^=post_rate_div_]")?.id().orEmpty()
    ).firstNotNullOfOrNull { id ->
        POST_ID_PREFIX.find(id)
            ?.groupValues
            ?.get(1)
            ?.toIntOrNull()
    } ?: 0
}

private fun parseForumComments(root: Element?, baseUrl: String): List<Comment> =
    root?.select("div.pstl")?.mapNotNull { row ->
        val body = row.selectFirst(".psti") ?: return@mapNotNull null
        val authorElement = row.select(".psta a.xw1, .psti a.xi2, .psta a[href*=uid]")
            .firstOrNull { it.text().isNotBlank() }
        val author = authorElement?.text()?.trim().orEmpty()
        val avatar = row.selectFirst(".psta img[src]")?.attr("src").orEmpty().wrapForumImage(baseUrl)
        val timeElement = body.selectFirst(".xg1 span[title]")
            ?: body.selectFirst(".xg1 span")
            ?: body.selectFirst(".xg1")
        val time = timeElement?.attr("title")?.ifBlank { timeElement.text() }?.trim().orEmpty()
        val content = body.clone().also { clone ->
            clone.select("a.xi2, a.xw1, .xg1").remove()
        }.text().trim()

        if (author.isEmpty() && content.isEmpty()) return@mapNotNull null
        Comment(
            author = author,
            authorAvatar = avatar,
            content = content,
            time = time
        )
    } ?: emptyList()

private fun parseForumCommentPageInfo(root: Element?): PageInfo {
    if (root == null) return PageInfo(activePage = 1, nextPage = 1)
    val pager = root.select(".pg").lastOrNull() ?: return PageInfo(activePage = 1, nextPage = 1)
    val activePage = pager.selectFirst("strong")?.text()?.toIntOrNull() ?: 1
    val nextPage = pager.select("a.nxt")
        .firstOrNull { link ->
            link.attr("href").contains("action=commentmore") ||
                    link.attr("onclick").contains("action=commentmore")
        }
        ?.let { link ->
            link.attr("href").takeIf { it.contains("action=commentmore") }
                ?: link.attr("onclick")
        }
        ?.let { PAGE_PARAM.find(it)?.groupValues?.get(1)?.toIntOrNull() }
        ?: activePage
    return PageInfo(activePage = activePage, nextPage = nextPage)
}

private fun isForumContentPageLink(href: String): Boolean =
    PAGE_PARAM.containsMatchIn(href) &&
            !href.contains("action=commentmore") &&
            (href.contains("mod=viewthread") || href.contains("mod=forumdisplay"))

private fun String.cleanForumPostTime(): String =
    trim().replace(POST_TIME_PREFIX, "").trim()

private val POST_TIME_PREFIX = Regex(
    pattern = "^(?:发表于|發表於|发表|發表|鐧艱〃鏂\\?)\\s*"
)

private val PAGE_PARAM = Regex("page=(\\d+)")
private val POST_ID_PREFIX = Regex("(?:post_|postmessage_|comment_|post_rate_div_)(\\d+)")

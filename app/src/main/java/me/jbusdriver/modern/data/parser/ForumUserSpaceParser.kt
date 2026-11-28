package me.jbusdriver.modern.data.parser

import me.jbusdriver.modern.domain.model.ForumFavoritePageResult
import me.jbusdriver.modern.domain.model.ForumFavoriteThread
import me.jbusdriver.modern.domain.model.ForumLoginForm
import me.jbusdriver.modern.domain.model.ForumLoginState
import me.jbusdriver.modern.domain.model.ForumProfileField
import me.jbusdriver.modern.domain.model.ForumUserProfile
import me.jbusdriver.modern.domain.model.ForumUserThread
import me.jbusdriver.modern.domain.model.ForumUserThreadPageResult
import me.jbusdriver.modern.domain.model.PageInfo
import org.jsoup.nodes.Document
import org.jsoup.nodes.Element

/**
 * 论坛账号/用户空间解析（协议依据 docs/FORUM_DESIGN.md §2.3/§2.5，选择器参照 JbForum-ref 并按站点实测校准）。
 */
private val DISCUZ_UID_REGEX = Regex("""discuz_uid\s*[:=]\s*["']?(\d+)["']?""")
private val UID_TEXT_REGEX = Regex("""UID[:：]\s*(\d+)""")
private val UPDATE_SECCODE_REGEX = Regex("""updateseccode\('([^']+)'""")
private val TID_REGEX = Regex("""tid=(\d+)""")
/** 用户组升级进度 tip："里程 533, 距離下一級還需 167 里程" */
private val MILEAGE_TIP_REGEX = Regex("""里程\s*(\d+)\D*?距離下一級還需\s*(\d+)""")

/**
 * 从任意论坛页解析登录态：`discuz_uid = 'N'`（N>0 为登录）。
 * 用户名等补充信息由 [parseForumUserProfile]（自空间页）填充。
 */
fun parseForumLoginUid(html: String): Int {
    val uid = DISCUZ_UID_REGEX.find(html)?.groupValues?.get(1)?.toIntOrNull() ?: 0
    return if (uid > 0) uid else 0
}

/**
 * 从自空间页（home.php?mod=space）解析完整登录态；游客态该页无用户名结构，返回 Anonymous。
 */
fun parseForumLoginState(doc: Document, baseUrl: String): ForumLoginState {
    val uid = parseForumLoginUid(doc.outerHtml())
    if (uid <= 0) return ForumLoginState.Anonymous
    val header = doc.selectFirst("#uhd h2.mt")
    val username = header?.ownText()?.trim().orEmpty()
    if (username.isEmpty()) return ForumLoginState.Anonymous
    return ForumLoginState.LoggedIn(
        uid = uid,
        username = username,
        avatarUrl = doc.selectFirst("#uhd .avt img, .u_profile .avatar img")
            ?.attr("src")?.takeIf { it.isNotBlank() }?.wrapForumImage(baseUrl),
        groupName = parseUserGroup(doc).ifBlank { null },
    )
}

/** 登录页动态表单（action 含 loginhash；seccodehash 仅存在于 inline JS，原始 HTML 无隐藏域）。 */
fun parseLoginForm(doc: Document): ForumLoginForm? {
    val form = doc.selectFirst("form[id^=loginform_]")
        ?: doc.select("form").firstOrNull {
            it.selectFirst("input[name=username]") != null && it.selectFirst("input[name=password]") != null
        }
        ?: return null
    val action = form.attr("action").ifBlank { "member.php?mod=logging&action=login&loginsubmit=yes" }
    val formhash = form.selectFirst("input[name=formhash]")?.attr("value").orEmpty()
    if (formhash.isBlank()) return null
    val referer = form.selectFirst("input[name=referer]")?.attr("value").orEmpty()
    val seccodeHash = UPDATE_SECCODE_REGEX.find(doc.outerHtml())?.groupValues?.get(1).orEmpty()
    return ForumLoginForm(action = action, formhash = formhash, referer = referer, seccodeHash = seccodeHash)
}

/** 用户资料页（home.php?mod=space[&uid=]）。 */
fun parseForumUserProfile(doc: Document, baseUrl: String): ForumUserProfile {
    val header = doc.selectFirst("#uhd h2.mt, .u_profile .mbn h2")
    // UID 标记在 h2 内的 span.xw0（"(UID: 662631)"）；游客视角 discuz_uid=0，须优先取该标记
    val uid = UID_TEXT_REGEX.find(doc.selectFirst("span.xw0")?.text().orEmpty())?.groupValues?.get(1)?.toIntOrNull()
        ?: parseForumLoginUid(doc.outerHtml()).takeIf { it > 0 }
        ?: 0
    val username = doc.selectFirst("#uhd h2.mt")?.ownText()?.trim()
        ?: header?.ownText()?.trim().orEmpty()
    val stats = statsRowText(doc)
    val mileage = parseMileageTip(doc)
    return ForumUserProfile(
        uid = uid,
        username = username,
        avatarUrl = doc.selectFirst("#uhd .avt img, .u_profile .avatar img")
            ?.attr("src")?.takeIf { it.isNotBlank() }
            ?.wrapForumImage(baseUrl),
        groupName = parseUserGroup(doc),
        mileage = mileage?.first,
        mileageToNextLevel = mileage?.second,
        // pf_l 覆盖全页（含 #pbbs/#psts），剔除活跃标签后即为基本信息（郵箱/性別/生日/已用空間/里程/金錢）
        basics = parseProfileFields(doc, "ul.pf_l li")
            .filterNot { ACTIVITY_LABEL_REGEX.containsMatchIn(it.label) },
        // #pbbs 字段按值条件渲染（在線時間需 oltime>0、上次發表需有發帖、IP 僅本人可见），有什么渲染什么
        activity = parseProfileFields(doc, "#pbbs li"),
        friendCount = parseStatsCount(stats, FRIEND_COUNT_LABELS),
        replyCount = parseStatsCount(stats, REPLY_COUNT_LABELS),
        topicCount = parseStatsCount(stats, TOPIC_COUNT_LABELS),
    )
}

/** 活跃状态字段标签（#pbbs，繁简均兼容），用于从全页 pf_l 字段中剔除。 */
private val ACTIVITY_LABEL_REGEX = Regex(
    "在線時間|在线时间|註冊時間|注册时间|最後訪問|最后访问|註冊\\s*IP|注册\\s*IP|" +
        "上次訪問|上次访问|上次活動時間|上次活动时间|上次發表時間|上次发表时间|所在時區|所在时区"
)

private val FRIEND_COUNT_LABELS = listOf("好友數", "好友数")
private val REPLY_COUNT_LABELS = listOf("回帖數", "回帖数")
private val TOPIC_COUNT_LABELS = listOf("主題數", "主题数")

/** 統計信息行（em.xg2 所在 li）：好友數 N | 回帖數 N | 主題數 N。 */
private fun statsRowText(doc: Document): String {
    return doc.selectFirst(".u_profile li:has(em.xg2)")?.text().orEmpty()
}

private fun parseStatsCount(text: String, labels: List<String>): Int? {
    if (text.isEmpty()) return null
    return labels.firstNotNullOfOrNull { label ->
        Regex(Regex.escape(label) + """\s*(\d+)""").find(text)?.groupValues?.get(1)?.toIntOrNull()
    }
}

private fun parseUserGroup(doc: Document): String {
    return doc.selectFirst("""li:has(em:matchesOwn((?i)(用戶組|用户组))) span.xi2 a""")?.text()?.trim().orEmpty()
}

/** 用户组 tip 里的升级进度："里程 533, 距離下一級還需 167 里程" → (533, 167)。 */
private fun parseMileageTip(doc: Document): Pair<Int, Int>? {
    val tip = doc.selectFirst("""li:has(em:matchesOwn((?i)(用戶組|用户组))) span[tip]""")?.attr("tip").orEmpty()
    val match = MILEAGE_TIP_REGEX.find(tip) ?: return null
    val mileage = match.groupValues[1].toIntOrNull() ?: return null
    val toNext = match.groupValues[2].toIntOrNull() ?: return null
    return mileage to toNext
}

private fun parseProfileFields(doc: Document, selector: String): List<ForumProfileField> {
    return doc.select(selector).mapNotNull { li ->
        val label = li.select("em").text().trim()
        if (label.isEmpty()) return@mapNotNull null
        // 值优先取 li 直接文本（不含 em 标签），兜底整体文本
        val value = li.ownText().trim().ifBlank { li.text().trim() }
        ForumProfileField(label, value)
    }
}

/** 用户主题/回复列表（home.php?mod=space&do=thread&view=me&type=thread|reply）。 */
fun parseUserThreadList(doc: Document): ForumUserThreadPageResult {
    val threads = doc.select(".tl table tr, table tr").mapNotNull { tr ->
        val link = tr.selectFirst("th a[href*=viewthread]:not(.xi2), th a[href*='goto=findpost'][href*=ptid=]")
            ?: return@mapNotNull null
        val href = link.attr("href")
        val tid = TID_REGEX.find(href)?.groupValues?.get(1)?.toIntOrNull() ?: return@mapNotNull null
        val title = link.text().trim()
        if (title.isEmpty()) return@mapNotNull null
        ForumUserThread(
            tid = tid,
            title = title,
            boardName = tr.selectFirst("td a[href*=forumdisplay]")?.text()?.trim().orEmpty(),
            time = tr.selectFirst("td.by em span[title]")?.text()?.trim()
                ?: tr.selectFirst("td.by em")?.text()?.trim().orEmpty(),
            // td.num：<a>回復数</a> <em>查看数</em>；表头行无数字（title 缺失已跳过）
            replyCount = tr.selectFirst("td.num > a")?.text()?.trim()?.toIntOrNull() ?: 0,
            viewCount = tr.selectFirst("td.num > em")?.text()?.trim()?.toIntOrNull() ?: 0,
            // td.by 的 cite = 最后回复人（链接文案）
            lastReplyAuthor = tr.selectFirst("td.by cite a, td.by cite")?.text()?.trim()
                ?.takeIf { it.isNotEmpty() },
            replies = collectUserReplyExcerpts(tr).takeIf { it.isNotEmpty() },
        )
    }.distinctBy { it.tid }
    return ForumUserThreadPageResult(threads, parseForumPageInfo(doc))
}

/**
 * 回复视图（type=reply）的回帖摘要：标题行之后、下一个标题行（th 内有链接）之前的
 * 后续行里，td[colspan] 内指向帖子的链接文本即该用户各条回帖摘要（选择器参照 JbForum
 * 的 parseUserThreadListPage）。主题视图标题行彼此紧邻，遍历立即终止返回空表。
 */
private fun collectUserReplyExcerpts(titleRow: Element): List<String> {
    val excerpts = mutableListOf<String>()
    var next = titleRow.nextElementSibling()
    while (next != null && next.selectFirst("th a[href]") == null) {
        next.select("td[colspan] a[href*=viewthread], td[colspan] a[href*='ptid=']")
            .forEach { a -> a.text().trim().takeIf { it.isNotEmpty() }?.let(excerpts::add) }
        next = next.nextElementSibling()
    }
    return excerpts.distinct()
}

/** 我的收藏列表（home.php?mod=space&do=favorite&view=me）。 */
fun parseUserFavorites(doc: Document): ForumFavoritePageResult {
    val favorites = doc.select("#favorite_ul li[id^=fav_]").mapNotNull { li ->
        val favId = li.id().removePrefix("fav_").toLongOrNull() ?: return@mapNotNull null
        val link = li.selectFirst("a[href*=viewthread]") ?: return@mapNotNull null
        val tid = TID_REGEX.find(link.attr("href"))?.groupValues?.get(1)?.toIntOrNull() ?: return@mapNotNull null
        ForumFavoriteThread(
            favId = favId,
            tid = tid,
            title = link.text().trim(),
            time = li.selectFirst("span.xg1")?.text()?.trim().orEmpty(),
        )
    }
    return ForumFavoritePageResult(favorites, parseForumPageInfo(doc))
}

/** Discuz showmessage 提示页文案（登录失败等原因）。 */
fun parseDiscuzMessage(html: String): String? {
    val doc = org.jsoup.Jsoup.parse(html)
    return doc.selectFirst("#messagetext p, .alert_error p, .alert_info p")?.text()?.trim()?.takeIf { it.isNotEmpty() }
}

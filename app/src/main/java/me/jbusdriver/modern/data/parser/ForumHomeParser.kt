package me.jbusdriver.modern.data.parser

import me.jbusdriver.modern.domain.model.ForumBanner
import me.jbusdriver.modern.domain.model.ForumBoard
import me.jbusdriver.modern.domain.model.ForumBoardGroup
import me.jbusdriver.modern.domain.model.ForumHomeData
import me.jbusdriver.modern.domain.model.ForumHomeSummary
import me.jbusdriver.modern.domain.model.ForumSummaryThread
import me.jbusdriver.modern.domain.model.LastPost
import org.jsoup.nodes.Document

fun parseForumHomeData(doc: Document, baseUrl: String): ForumHomeData {
    return ForumHomeData(
        banners = parseBanners(doc, baseUrl),
        summary = parseSummary(doc),
        boardGroups = parseForumBoards(doc)
    )
}

private fun parseBanners(doc: Document, baseUrl: String): List<ForumBanner> {
    return doc.select("ul.slideshow > li").mapNotNull { li ->
        val img = li.select("a.biaoqicn_imga > img").firstOrNull() ?: return@mapNotNull null
        val link = li.select("a[href*=viewthread]").firstOrNull() ?: return@mapNotNull null
        val tid = Regex("tid=(\\d+)").find(link.attr("href"))?.groupValues?.get(1)?.toIntOrNull()
            ?: return@mapNotNull null
        val title = li.select("p.biaoqicn_title").text().trim().ifBlank { img.attr("alt") }
        ForumBanner(tid, title, img.attr("src").wrapForumImage(baseUrl))
    }
}

private fun parseSummary(doc: Document): ForumHomeSummary {
    return ForumHomeSummary(
        latestThreads = parseSummaryList(doc, "#con_NewOne_1 .sideMenu > h3"),
        latestReplies = parseSummaryList(doc, "#con_NewOne_2 .sideMenu > h3"),
        hotTopics = parseSummaryList(doc, "#con_NewOne_3 .sideMenu > h3")
    )
}

private fun parseSummaryList(doc: Document, selector: String): List<ForumSummaryThread> {
    return doc.select(selector).mapNotNull { h3 ->
        val link = h3.select("a[href*=viewthread]").firstOrNull() ?: return@mapNotNull null
        val tid = Regex("tid=(\\d+)").find(link.attr("href"))?.groupValues?.get(1)?.toIntOrNull()
            ?: return@mapNotNull null
        val author = h3.select("em > a").text().trim()
        val title = link.attr("title").ifBlank { link.text().trim() }
        ForumSummaryThread(tid, title, author)
    }
}

fun parseForumBoards(doc: Document): List<ForumBoardGroup> {
    val groups = mutableListOf<ForumBoardGroup>()
    val sectionDivs = doc.select("div.fl.bm > div.bm.bmw.cl")

    for (section in sectionDivs) {
        val groupName = section.select("div.bm_h.cl h2 a").text().trim()
        if (groupName.isEmpty()) continue

        val categoryDiv = section.select("div[id^=category_]").firstOrNull() ?: continue
        val rows = categoryDiv.select("table.fl_tb > tbody > tr, table.fl_tb > tr")
        val boards = mutableListOf<ForumBoard>()

        for (row in rows) {
            val nameLink = row.select("td h2 a").firstOrNull() ?: continue
            val href = nameLink.attr("href")
            val fid = Regex("fid=(\\d+)").find(href)?.groupValues?.get(1)?.toIntOrNull()
                ?: continue
            val typeId = Regex("typeid=(\\d+)").find(href)?.groupValues?.get(1)?.toIntOrNull()

            val todayText = row.select("em.xw0.xi1").text()
            val todayPosts =
                Regex("\\((\\d+)\\)").find(todayText)?.groupValues?.get(1)?.toIntOrNull() ?: 0

            val statsSpans = row.select("td.fl_i span")
            val totalThreads = statsSpans.getOrNull(0)?.text()?.trim() ?: ""
            val totalPosts = statsSpans.getOrNull(2)?.text()?.trim() ?: ""

            val lastPostDiv = row.select("td.fl_by .forumlist").firstOrNull()
            val lastPostTitle =
                lastPostDiv?.select("a.xi2, a[title]")?.firstOrNull()?.text()?.trim() ?: ""
            val lastPostAuthor = lastPostDiv?.select("cite a")?.text()?.trim() ?: ""
            // 优先取带 title 的时间 span，覆盖两种新格式：
            //   <cite><span title="...">文本</span> ...</cite>                      (直接 title)
            //   <cite><span>\t<span title="...">文本</span></span> ...</cite>       (嵌套，只命中内层，避免文本重复)
            // 仅当都没有时（旧版叶子 <cite><span>2026-06-12</span> ...</cite>）才回退到 cite 内任意 span。
            // 用 ifBlank 而非 ?:，因为未命中时 .text() 返回空串而非 null，?: 不会触发回退。
            val lastPostTime = lastPostDiv?.select("cite span[title]")?.text()?.trim().orEmpty()
                .ifBlank { lastPostDiv?.select("cite span")?.text()?.trim() ?: "" }

            boards.add(
                ForumBoard(
                    id = fid,
                    name = nameLink.text().trim(),
                    description = row.select("td p.xg2").text().trim(),
                    todayPosts = todayPosts,
                    totalThreads = totalThreads,
                    totalPosts = totalPosts,
                    lastPost = LastPost(lastPostTitle, lastPostAuthor, lastPostTime),
                    typeId = typeId
                )
            )
        }
        if (boards.isNotEmpty()) {
            groups.add(ForumBoardGroup(name = groupName, boards = boards))
        }
    }
    return groups
}

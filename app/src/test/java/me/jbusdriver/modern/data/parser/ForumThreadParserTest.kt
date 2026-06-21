package me.jbusdriver.modern.data.parser

import me.jbusdriver.modern.domain.model.ContentBlock
import me.jbusdriver.modern.domain.model.ForumTextSize
import me.jbusdriver.modern.domain.model.PageInfo
import org.jsoup.Jsoup
import org.jsoup.nodes.Document
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ForumThreadParserTest {
    @Test
    fun `thread detail parses first post floor comments and comment pagination`() {
        val detail = parseForumThreadDetail(
            fixture(
                "floor-comments.html",
                "https://www.javbus.com/forum/forum.php?mod=viewthread&tid=172059"
            ),
            "https://www.javbus.com"
        )

        assertEquals(4773811, detail.pid)
        assertEquals(4, detail.comments.size)
        assertEquals("Alice", detail.comments[0].author)
        assertEquals("https://www.javbus.com/avatars/a.jpg", detail.comments[0].authorAvatar)
        assertEquals("first comment", detail.comments[0].content)
        assertEquals("鐧艰〃鏂?2026-6-9 08:59", detail.comments[0].time)
        assertEquals("2026-6-9 09:00", detail.comments[1].time)
        assertEquals(PageInfo(activePage = 1, nextPage = 2), detail.commentPageInfo)
    }

    @Test
    fun `thread detail parses reply floor comments independently`() {
        val detail = parseForumThreadDetail(
            fixture(
                "floor-comments.html",
                "https://www.javbus.com/forum/forum.php?mod=viewthread&tid=172059"
            ),
            "https://www.javbus.com"
        )

        val reply = detail.replies.single()
        assertEquals(4773820, reply.pid)
        assertEquals(1, reply.comments.size)
        assertEquals("Eve", reply.comments.single().author)
        assertEquals("reply floor comment", reply.comments.single().content)
        assertEquals(PageInfo(activePage = 1, nextPage = 1), reply.commentPageInfo)
    }

    @Test
    fun `commentmore fragment parser returns comments and local page info`() {
        val doc = commentMoreDocument(
            """
            <div id="comment_4773811" class="cm">
              <div class="pstl">
                <div class="psta"><img src="/avatars/f.jpg"></div>
                <div class="psti"><a href="home.php?mod=space&amp;uid=6" class="xi2 xw1">Frank</a>&nbsp;page two comment&nbsp;<span class="xg1">鐧艰〃鏂?2026-6-10 10:00</span></div>
              </div>
              <div class="pgs mbm cl"><div class="pg">
                <a href="forum.php?mod=misc&amp;action=commentmore&amp;tid=172059&amp;pid=4773811&amp;page=1" class="prev">涓婁竴闋?</a>
                <a href="forum.php?mod=misc&amp;action=commentmore&amp;tid=172059&amp;pid=4773811&amp;page=1">1</a>
                <strong>2</strong>
              </div></div>
            </div>
            """.trimIndent()
        )

        val result = parseForumFloorComments(doc, "https://www.javbus.com", pid = 4773811)

        assertEquals(4773811, result.pid)
        assertEquals("Frank", result.comments.single().author)
        assertEquals("page two comment", result.comments.single().content)
        assertEquals(PageInfo(activePage = 2, nextPage = 2), result.pageInfo)
    }

    @Test
    fun `commentmore parser unwraps ajax xml cdata response`() {
        val doc = Jsoup.parse(
            """
            <?xml version="1.0" encoding="utf-8"?>
            <root><![CDATA[
              <h3 class="psth cm">點評</h3>
              <div class="pstl">
                <div class="psta"><img src="https://uc.javbus22.com/uc/data/avatar/000/57/92/11_avatar_small.jpg"></div>
                <div class="psti">
                  <a href="home.php?mod=space&amp;uid=579211" class="xi2 xw1">孤狼</a>
                  &nbsp;ajax cdata comment&nbsp;
                  <span class="xg1">發表於 2026-6-9 08:59</span>
                </div>
              </div>
              <div class="pgs mbm mtn cl"><div class="pg">
                <a href="forum.php?mod=misc&amp;action=commentmore&amp;tid=172059&amp;pid=4773811&amp;page=1">1</a>
                <strong>2</strong>
                <a href="javascript:;" class="nxt" onclick="ajaxget('forum.php?mod=misc&amp;action=commentmore&amp;tid=172059&amp;pid=4773811&amp;page=3', 'comment_4773811')">下一頁</a>
              </div></div>
            ]]></root>
            """.trimIndent(),
            "https://www.javbus.com/forum/forum.php?mod=misc&action=commentmore&tid=172059&pid=4773811&page=2&inajax=1&ajaxtarget=comment_4773811"
        )

        val result = parseForumFloorComments(doc, "https://www.javbus.com", pid = 4773811)

        assertEquals(4773811, result.pid)
        assertEquals("孤狼", result.comments.single().author)
        assertEquals("ajax cdata comment", result.comments.single().content)
        assertEquals("發表於 2026-6-9 08:59", result.comments.single().time)
        assertEquals(PageInfo(activePage = 2, nextPage = 3), result.pageInfo)
    }

    @Test
    fun `commentmore fragment parser reads ajax next page from onclick`() {
        val doc = commentMoreDocument(
            """
            <div id="comment_4773811" class="cm">
              <div class="pstl">
                <div class="psta"><img src="/avatars/f.jpg"></div>
                <div class="psti"><a href="home.php?mod=space&amp;uid=6" class="xi2 xw1">Frank</a>&nbsp;page two comment&nbsp;<span class="xg1">閻ц壈銆冮弬?2026-6-10 10:00</span></div>
              </div>
              <div class="pgs mbm cl"><div class="pg">
                <a href="forum.php?mod=misc&amp;action=commentmore&amp;tid=172059&amp;pid=4773811&amp;page=1" class="prev">娑撳﹣绔撮棆?</a>
                <strong>2</strong>
                <a href="javascript:;" class="nxt" onclick="ajaxget('forum.php?mod=misc&amp;action=commentmore&amp;tid=172059&amp;pid=4773811&amp;page=3', 'comment_4773811')">娑撳绔撮棆?</a>
              </div></div>
            </div>
            """.trimIndent()
        )

        val result = parseForumFloorComments(doc, "https://www.javbus.com", pid = 4773811)

        assertEquals(PageInfo(activePage = 2, nextPage = 3), result.pageInfo)
    }

    @Test
    fun `thread reply pagination ignores floor comment pagination links`() {
        val doc = Jsoup.parse(
            """
                <html><body>
                  <h1 class="ts"><span id="thread_subject">Thread With Floor Comments</span></h1>
                  <div class="nthread_firstpostbox" id="post_4773811">
                    <table><tbody><tr><td class="t_f" id="postmessage_4773811">First post body</td></tr></tbody></table>
                    <div id="comment_4773811" class="cm">
                      <div class="pgs mbm cl"><div class="pg">
                        <strong>1</strong>
                        <a href="forum.php?mod=misc&amp;action=commentmore&amp;tid=172059&amp;pid=4773811&amp;page=3" class="nxt" ajaxtarget="comment_4773811">涓嬩竴闋?</a>
                      </div></div>
                    </div>
                  </div>
                  <div class="pgs mtm mbm cl"><div class="pg">
                    <strong>1</strong>
                    <a href="forum.php?mod=viewthread&amp;tid=172059&amp;page=2" class="nxt">涓嬩竴闋?</a>
                  </div></div>
                </body></html>
            """.trimIndent(),
            "https://www.javbus.com/forum/forum.php?mod=viewthread&tid=172059"
        )

        val detail = parseForumThreadDetail(doc, "https://www.javbus.com")

        assertEquals(PageInfo(activePage = 1, nextPage = 2), detail.pageInfo)
    }

    @Test
    fun `thread reply active page ignores floor comment active page`() {
        val doc = Jsoup.parse(
            """
                <html><body>
                  <h1 class="ts"><span id="thread_subject">Thread With Floor Comments</span></h1>
                  <div class="nthread_firstpostbox" id="post_4773811">
                    <table><tbody><tr><td class="t_f" id="postmessage_4773811">First post body</td></tr></tbody></table>
                    <div id="comment_4773811" class="cm">
                      <div class="pgs mbm cl"><div class="pg">
                        <a href="forum.php?mod=misc&amp;action=commentmore&amp;tid=172059&amp;pid=4773811&amp;page=1">1</a>
                        <strong>3</strong>
                      </div></div>
                    </div>
                  </div>
                  <div class="pgs mtm mbm cl"><div class="pg">
                    <a href="forum.php?mod=viewthread&amp;tid=172059&amp;page=1">1</a>
                    <strong>2</strong>
                    <a href="forum.php?mod=viewthread&amp;tid=172059&amp;page=3" class="nxt">涓嬩竴闋?</a>
                  </div></div>
                </body></html>
            """.trimIndent(),
            "https://www.javbus.com/forum/forum.php?mod=viewthread&tid=172059&page=2"
        )

        val detail = parseForumThreadDetail(doc, "https://www.javbus.com")

        assertEquals(2, detail.pageInfo.activePage)
        assertEquals(3, detail.pageInfo.nextPage)
    }

    @Test
    fun `thread list pagination accepts forumdisplay next links`() {
        val doc = Jsoup.parse(
            """
                <html><body>
                  <table>
                    <tbody id="normalthread_42">
                      <tr><th>
                        <div class="post_infolist_tit">
                          <a class="s" href="forum.php?mod=viewthread&amp;tid=42">Thread title</a>
                        </div>
                      </th></tr>
                    </tbody>
                  </table>
                  <div class="pgs mtm mbm cl"><div class="pg">
                    <strong>1</strong>
                    <a href="forum.php?mod=forumdisplay&amp;fid=4&amp;page=2" class="nxt">娑撳绔撮棆?</a>
                  </div></div>
                </body></html>
            """.trimIndent(),
            "https://www.javbus.com/forum/forum.php?mod=forumdisplay&fid=4"
        )

        val pageInfo = parseForumThreads(doc, "https://www.javbus.com").pageInfo

        assertEquals(PageInfo(activePage = 1, nextPage = 2), pageInfo)
    }

    @Test
    fun `restricted reply remains visible without normal body cell`() {
        val detail = parseForumThreadDetail(
            fixture(
                "restricted-replies.html",
                "https://www.javbus.com/forum/forum.php?mod=viewthread&tid=154969"
            ),
            "https://www.javbus.com"
        )

        assertEquals(1, detail.replies.size)
        assertEquals(2, detail.replies.single().floor)
        assertEquals(
            listOf(ContentBlock.RestrictedNotice("此帖僅作者可見")),
            detail.replies.single().contentBlocks
        )
    }

    @Test
    fun `pinned reply floors are parsed and retain document order`() {
        val replies = parseForumThreadDetail(
            fixture(
                "pinned-rich-replies.html",
                "https://www.javbus.com/forum/forum.php?mod=viewthread&tid=171794&page=2"
            ),
            "https://www.javbus.com"
        ).replies

        assertEquals(listOf(2, 3, 4, 5), replies.map { it.floor })
        assertTrue(replies.take(3).all { it.isPinned })
        assertFalse(replies.last().isPinned)
    }

    @Test
    fun `parses controlled inline styles and preserves separating spaces`() {
        val blocks = parsedPinnedReply(2).contentBlocks
        val parts = blocks.filterIsInstance<ContentBlock.RichText>()
            .flatMap { it.paragraphs }
            .flatMap { it.parts }

        val heading = parts.single { it.text == "观前提醒：" }
        assertTrue(heading.bold)
        assertEquals("#ff0000", heading.color)
        assertEquals(ForumTextSize.HEADING, heading.size)
        assertTrue(parts.joinToString("") { it.text }.contains("normal link text"))
        assertTrue(parts.single { it.text == "link text" }.isLink)
    }

    @Test
    fun `parses ordered and nested unordered lists`() {
        val list = parsedPinnedReply(2).contentBlocks
            .filterIsInstance<ContentBlock.ListBlock>()
            .single()
            .list

        assertTrue(list.ordered)
        assertEquals(2, list.start)
        assertEquals(listOf("first italic", "second"), list.items.map { item ->
            item.paragraphs.flatMap { it.parts }.joinToString("") { it.text }.trim()
        })
        assertTrue(list.items.first().paragraphs.single().parts.single { it.text == "italic" }.italic)
        assertFalse(list.items[1].children.single().ordered)
        val nestedPart = list.items[1].children.single().items.single()
            .paragraphs.single().parts.single()
        assertEquals("nested", nestedPart.text)
        assertTrue(nestedPart.underline)
    }

    @Test
    fun `unknown tags retain text and invalid colors are discarded`() {
        val parts = parsedPinnedReply(2).contentBlocks
            .filterIsInstance<ContentBlock.RichText>()
            .flatMap { it.paragraphs }
            .flatMap { it.parts }

        assertTrue(parts.any { it.text.contains("kept text") })
        assertEquals(null, parts.single { it.text == "removed" }.color)
        assertTrue(parts.single { it.text == "removed" }.strikethrough)
    }

    @Test
    fun `malformed list retains visible descendant text`() {
        val text = parsedPinnedReply(2).contentBlocks
            .filterIsInstance<ContentBlock.RichText>()
            .flatMap { it.paragraphs }
            .flatMap { it.parts }
            .joinToString("") { it.text }

        assertTrue(text.contains("fallback list text"))
    }

    @Test
    fun `smilie images remain inline with surrounding text`() {
        val post = Jsoup.parseBodyFragment(
            """
                <table><tr>
                <td class="t_f" id="postmessage_4764803">
                车神牛B，现在的里番真的毫无欲望，连雷火剑监督都不咋样了<img src="/static/image/smiley/3.png" smilieid="108" border="0" alt="">，以前我是片都不看就爱看里番。
                </td>
                </tr></table>
            """.trimIndent(),
            "https://www.javbus.com/forum/"
        ).selectFirst("td.t_f")

        assertTrue(post?.selectFirst("img")?.hasAttr("smilieid") == true)
        val blocks = parseForumPostContent(post, "https://www.javbus.com")

        assertEquals(1, blocks.size)
        assertTrue(blocks.none { it is ContentBlock.Image })
        val parts = blocks.filterIsInstance<ContentBlock.RichText>()
            .flatMap { it.paragraphs }
            .flatMap { it.parts }
        assertEquals(
            "https://www.javbus.com/static/image/smiley/3.png",
            parts.single { it.inlineImageUrl.isNotEmpty() }.inlineImageUrl
        )
        val text = parts.joinToString("") { it.text }
        assertEquals(
            "车神牛B，现在的里番真的毫无欲望，连雷火剑监督都不咋样了，以前我是片都不看就爱看里番。",
            text
        )
    }

    @Test
    fun `thread status images become flags instead of previews`() {
        val doc = Jsoup.parse(
            """
                <table>
                  <tbody id="normalthread_42">
                    <tr><th>
                      <div class="post_infolist_tit">
                        <a class="s" href="forum.php?mod=viewthread&amp;tid=42">Thread title</a>
                        <a href="forum.php?mod=viewthread&amp;tid=42">
                          <img src="./static/image/common/folder_lock.gif">
                        </a>
                        <img src="./static/image/common/recommend.png" alt="recommend">
                        <img src="./static/image/common/hot.jpg" alt="heatlevel">
                        <a href="forum.php?mod=viewthread&amp;tid=42">
                          <img src="./preview.jpg">
                        </a>
                      </div>
                    </th></tr>
                  </tbody>
                </table>
            """.trimIndent(),
            "https://www.javbus.com/forum/"
        )

        val thread = parseForumThreads(doc, "https://www.javbus.com").threads.single()

        assertTrue(thread.isLocked)
        assertTrue(thread.isDigest)
        assertTrue(thread.isHot)
        assertEquals(listOf("https://www.javbus.com/forum/./preview.jpg"), thread.images)
    }

    @Test
    fun `thread detail ignores copy link when parsing type tag`() {
        val doc = Jsoup.parse(
            """
                <html><body>
                  <h1 class="ts">
                    <span id="thread_subject">Thread title</span>
                  </h1>
                  <div class="nthread_info">
                    <h1 class="ts">
                      <span id="thread_subject">
                        Thread title
                        <small>
                          <a href="forum.php?mod=viewthread&amp;tid=154969" onclick="return copyThreadUrl(this, 'forum')">[Copy link]</a>
                        </small>
                      </span>
                    </h1>
                  </div>
                </body></html>
            """.trimIndent(),
            "https://www.javbus.com/forum/forum.php?mod=viewthread&tid=154969"
        )

        val detail = parseForumThreadDetail(doc, "https://www.javbus.com")

        assertEquals("Thread title", detail.title)
        assertEquals("", detail.typeName)
        assertEquals(0, detail.typeId)
    }

    @Test
    fun `reply post time removes published prefix`() {
        val doc = Jsoup.parse(
            """
                <html><body>
                  <div class="nthread_postbox">
                    <div class="pi">
                      <a id="postnum4158039"><em>2</em><sup>#</sup></a>
                      <div class="authi">
                        <a class="xw1" href="home.php?mod=space&amp;uid=620784">Alice</a>
                        <em id="authorposton4158039">发表于 2025-4-26 20:28:02</em>
                      </div>
                    </div>
                    <td class="t_f">reply</td>
                  </div>
                </body></html>
            """.trimIndent(),
            "https://www.javbus.com/forum/forum.php?mod=viewthread&tid=154969"
        )

        val reply = parseForumThreadDetail(doc, "https://www.javbus.com").replies.single()

        assertEquals("2025-4-26 20:28:02", reply.postTime)
    }

    @Test
    fun `first-post comment pagination does not shadow reply next page`() {
        // 首楼留言分页会注入一条 href="javascript:;" 的 .nxt（真实 page 在 onclick 的
        // ajaxget 里），它出现在回复楼层分页之前。必须跳过它，取真正携带 page= 的回复分页链接。
        val doc = Jsoup.parse(
            """
                <html><body>
                  <div class="pgs mbm mtn cl">
                    <div class="pg">
                      <a href="javascript:;" class="nxt" onclick="ajaxget('forum.php?mod=misc&amp;action=commentmore&amp;tid=172059&amp;pid=4773811&amp;page=2', 'comment_4773811')">下一頁</a>
                    </div>
                  </div>
                  <div class="pgs mtm mbm cl">
                    <div class="pg">
                      <strong>1</strong>
                      <a href="https://www.javbus.com/forum/forum.php?mod=viewthread&amp;tid=172059&amp;extra=&amp;page=2">2</a>
                      <a href="https://www.javbus.com/forum/forum.php?mod=viewthread&amp;tid=172059&amp;extra=&amp;page=2" class="nxt">下一頁</a>
                    </div>
                  </div>
                </body></html>
            """.trimIndent(),
            "https://www.javbus.com/forum/forum.php?mod=viewthread&tid=172059"
        )

        val pageInfo = parseForumThreadDetail(doc, "https://www.javbus.com").pageInfo

        assertEquals(1, pageInfo.activePage)
        assertEquals(2, pageInfo.nextPage)
    }

    @Test
    fun `legacy plain-text dateline is extracted when the span has no title`() {
        // 旧版板块页发帖时间写作 <span class="dateline">2017-9-9</span>（叶子 span，无 title），
        // 新版则是 <span class="dateline"><span title="...">...</span></span>。
        // 此前用 ".dateline span" 取后代 span，对叶子 span 取不到，旧格式时间丢失。
        val doc = Jsoup.parse(
            """
                <table>
                  <tbody id="normalthread_15172">
                    <tr><th>
                      <div class="post_infolist_tit">
                        <a class="s" href="forum.php?mod=viewthread&amp;tid=15172">Thread title</a>
                      </div>
                    </th></tr>
                    <tr>
                      <td class="by">
                        <span class="dateline">2017-9-9</span>
                      </td>
                    </tr>
                  </tbody>
                </table>
            """.trimIndent(),
            "https://www.javbus.com/forum/"
        )

        val thread = parseForumThreads(doc, "https://www.javbus.com").threads.single()

        assertEquals("2017-9-9", thread.dateLine)
    }

    @Test
    fun `dateline shows humanized text instead of titled absolute date`() {
        // 新格式 <span class="dateline"><span title="2026-6-6">前天 00:35</span></span>
        // 应显示可见文本 "前天 00:35"（更直观），而非 title 里的绝对日期 "2026-6-6"。
        val doc = Jsoup.parse(
            """
                <table>
                  <tbody id="normalthread_42">
                    <tr><th>
                      <div class="post_infolist_tit">
                        <a class="s" href="forum.php?mod=viewthread&amp;tid=42">Thread title</a>
                      </div>
                    </th></tr>
                    <tr>
                      <td class="by">
                        <span class="dateline"><span title="2026-6-6">前天&nbsp;00:35</span></span>
                      </td>
                    </tr>
                  </tbody>
                </table>
            """.trimIndent(),
            "https://www.javbus.com/forum/"
        )

        val thread = parseForumThreads(doc, "https://www.javbus.com").threads.single()

        assertTrue(thread.dateLine.contains("前天"))
        assertTrue(thread.dateLine.contains("00:35"))
        assertFalse(thread.dateLine.contains("2026-6-6"))
    }

    private fun parsedPinnedReply(floor: Int) = parseForumThreadDetail(
        fixture(
            "pinned-rich-replies.html",
            "https://www.javbus.com/forum/forum.php?mod=viewthread&tid=171794&page=2"
        ),
        "https://www.javbus.com"
    ).replies.single { it.floor == floor }

    private fun commentMoreDocument(html: String): Document =
        Jsoup.parse(
            html,
            "https://www.javbus.com/forum/forum.php?mod=misc&action=commentmore&tid=172059&pid=4773811&page=2"
        )

    private fun fixture(name: String, location: String) =
        Jsoup.parse(
            checkNotNull(javaClass.getResourceAsStream("/forum/$name"))
                .bufferedReader(Charsets.UTF_8)
                .use { it.readText() },
            location
        )
}

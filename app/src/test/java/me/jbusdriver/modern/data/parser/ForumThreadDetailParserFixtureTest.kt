package me.jbusdriver.modern.data.parser

import me.jbusdriver.modern.domain.model.ContentBlock
import me.jbusdriver.modern.core.fromJson
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 帖子详情真实页面回归（fixtures：thread_179228_{guest,logged_in,nocookie_login_redirect}）。
 *
 * 协议背景（docs/FORUM_DESIGN.md §2.5）：游客与登录态帖子页同构；
 * 游客 fastpost 显示"请先登录"，登录（低等级）显示"您現在無權發帖"。
 */
class ForumThreadDetailParserFixtureTest {

    private fun guestDetail() =
        parseForumThreadDetail(ForumFixtures.doc("thread_179228_guest.html"), ForumFixtures.SITE_BASE)

    @Test
    fun `guest thread page parses title, counts, first post and replies`() {
        val detail = guestDetail()

        assertEquals(179228, detail.tid)
        assertTrue("title mismatch: ${detail.title}", detail.title.contains("哪位伯乐"))
        assertTrue("viewCount should be positive: ${detail.viewCount}", detail.viewCount > 0)
        assertTrue("OP author blank", detail.author.isNotBlank())
        assertTrue("OP uid invalid: ${detail.authorUid}", detail.authorUid > 0)

        assertTrue("first post content empty", detail.contentBlocks.isNotEmpty())
        val imageUrls = detail.contentBlocks.filterIsInstance<ContentBlock.Image>().map { it.url }
        imageUrls.forEach { assertTrue("image url not absolute: $it", it.startsWith("http")) }

        assertTrue("replies empty", detail.replies.isNotEmpty())
        val floors = detail.replies.map { it.floor }
        assertEquals("floors must be ascending", floors, floors.sorted())
        assertTrue("first reply floor should be > 1, got ${floors.first()}", floors.first() > 1)
        detail.replies.forEach {
            assertTrue("reply pid invalid: $it", it.pid > 0)
            assertTrue("reply author blank: $it", it.author.isNotBlank())
        }
        assertTrue("at least one reply with content", detail.replies.any { it.contentBlocks.isNotEmpty() })

        assertEquals(1, detail.pageInfo.activePage)
    }

    @Test
    fun `logged-in thread page parses the same thread consistently`() {
        val guest = guestDetail()
        val loggedIn = parseForumThreadDetail(
            ForumFixtures.doc("thread_179228_logged_in.html"),
            ForumFixtures.SITE_BASE,
        )

        assertEquals(guest.title, loggedIn.title)
        assertEquals(guest.author, loggedIn.author)
        assertEquals(guest.authorUid, loggedIn.authorUid)
        assertTrue(loggedIn.replies.isNotEmpty())
        assertTrue(loggedIn.replies.map { it.floor } == loggedIn.replies.map { it.floor }.sorted())

        // 登录态独有标志：楼层回复表单存在且登出链接存在（游客样本反之）
        val guestHtml = ForumFixtures.html("thread_179228_guest.html")
        val loggedInHtml = ForumFixtures.html("thread_179228_logged_in.html")
        assertTrue(guestHtml.contains("fastpostform"))
        assertTrue(loggedInHtml.contains("fastpostform"))
        assertTrue("guest must lack logout link", !guestHtml.contains("action=logout"))
        assertTrue("logged-in must have logout link", loggedInHtml.contains("action=logout"))
    }

    @Test
    fun `thread page parses favorite action with count`() {
        // #p_btn 操作区（§2.4-3）：收藏链接含 formhash、自带计数（游客 33，登录 31，
        // 样本采集于不同时刻）。点赞链接不再解析（入口已移除，协议记录见 §2.4-4）。
        val guest = guestDetail()
        assertEquals(33, guest.favoriteCount)
        assertTrue(
            "favorite url should carry formhash: ${guest.favoriteActionUrl}",
            guest.favoriteActionUrl.orEmpty().contains("formhash="),
        )

        val loggedIn = parseForumThreadDetail(
            ForumFixtures.doc("thread_179228_logged_in.html"),
            ForumFixtures.SITE_BASE,
        )
        assertEquals(31, loggedIn.favoriteCount)
        assertTrue(loggedIn.favoriteActionUrl.orEmpty().contains("ac=favorite&type=thread&id=179228"))
    }

    @Test
    fun `legacy cached detail json without action fields parses with nulls`() {
        // 升级前磁盘缓存缺新字段：Gson 绕过构造函数，可空字段为 null、数值为 0，不得崩溃
        // （多出的旧字段——如历史版本写入的点赞数据——Gson 直接忽略）
        val legacy = """{"tid":42,"title":"舊緩存","viewCount":10,"replyCount":2,"recommendCount":7}"""
        val detail = me.jbusdriver.modern.core.GSON.fromJson<me.jbusdriver.modern.domain.model.ForumThreadDetail>(legacy)
        org.junit.Assert.assertNotNull(detail)
        assertEquals(42, detail!!.tid)
        assertEquals(0, detail.favoriteCount)
        org.junit.Assert.assertNull(detail.favoriteActionUrl)
        org.junit.Assert.assertNull(detail.authorGroup)
    }

    @Test
    fun `no-cookie redirect page parses as empty detail`() {
        // TODO(A5 ParseFailure)：登录重定向应与"帖子不存在/空内容"可区分。
        val detail = parseForumThreadDetail(
            ForumFixtures.doc("thread_179228_nocookie_login_redirect.html"),
            ForumFixtures.SITE_BASE,
        )
        assertTrue("title should be blank on redirect page", detail.title.isBlank())
        assertTrue(detail.replies.isEmpty())
        assertTrue(detail.contentBlocks.isEmpty())
    }
}

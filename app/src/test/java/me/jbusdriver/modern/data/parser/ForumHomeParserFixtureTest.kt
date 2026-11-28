package me.jbusdriver.modern.data.parser

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 论坛首页真实页面回归（fixtures：forum_index_{guest,logged_in,nocookie_login_redirect}）。
 *
 * 协议背景（docs/FORUM_DESIGN.md §2.3/§2.5）：
 *  - 游客与登录态首页结构同构；登录态独有 `action=logout` / `guide&view=my` 标志。
 *  - 完全无 Cookie 的请求被服务端重定向到登录页（会话失效形态，非空数据）。
 */
class ForumHomeParserFixtureTest {

    @Test
    fun `logged-in forum index parses board groups and summary`() {
        val data = parseForumHomeData(ForumFixtures.doc("forum_index_logged_in.html"), ForumFixtures.SITE_BASE)

        val fids = data.boardGroups.flatMap { it.boards }.map { it.id }
        assertTrue("board groups should not be empty", data.boardGroups.isNotEmpty())
        assertTrue("should contain main boards, got: $fids", fids.containsAll(listOf(2, 36, 37)))
        data.boardGroups.flatMap { it.boards }.forEach {
            assertTrue("board name blank for fid=${it.id}", it.name.isNotBlank())
        }

        assertTrue(data.summary.latestThreads.isNotEmpty())
        assertTrue(data.summary.hotTopics.isNotEmpty())
        data.summary.latestThreads.forEach {
            assertTrue("summary tid invalid: $it", it.tid > 0)
            assertTrue("summary title blank: $it", it.title.isNotBlank())
        }
    }

    @Test
    fun `forum index parses banner slideshow from raw server html`() {
        // 轮播 ul.slideshow 为服务端输出（标签跨行排版，按 class 选择器可稳定命中）。
        val data = parseForumHomeData(ForumFixtures.doc("forum_index_logged_in.html"), ForumFixtures.SITE_BASE)
        assertTrue("banners should be parsed", data.banners.isNotEmpty())
        data.banners.forEach {
            assertTrue("banner tid invalid: $it", it.tid > 0)
            assertTrue("banner image not absolute: $it", it.imageUrl.startsWith("http"))
            assertTrue("banner title blank: $it", it.title.isNotBlank())
        }
    }

    @Test
    fun `guest forum index parses same boards and login-state markers differ`() {
        val groups = parseForumBoards(ForumFixtures.doc("forum_index_guest.html"))
        val fids = groups.flatMap { it.boards }.map { it.id }
        assertTrue(fids.containsAll(listOf(2, 36, 37)))

        // 登录态检测标志（A2 observeLoginState 的协议依据）
        val guestHtml = ForumFixtures.html("forum_index_guest.html")
        val loggedInHtml = ForumFixtures.html("forum_index_logged_in.html")
        assertTrue(guestHtml.contains("mod=logging&amp;action=login") || guestHtml.contains("mod=logging&action=login"))
        assertFalse("guest page must not contain logout link", guestHtml.contains("action=logout"))
        assertTrue("logged-in page must contain logout link", loggedInHtml.contains("action=logout"))
        assertTrue("logged-in page must contain my-threads guide", loggedInHtml.contains("guide&view=my"))
    }

    @Test
    fun `no-cookie redirect page parses as empty boards`() {
        // 无 Cookie 请求被重定向到登录页：当前解析器返回空列表。
        // TODO(A5 ParseFailure)：会话失效应可诊断，而不是与"无版块"混淆。
        val groups = parseForumBoards(ForumFixtures.doc("forum_index_nocookie_login_redirect.html"))
        assertTrue(groups.isEmpty())
        assertTrue(ForumFixtures.html("forum_index_nocookie_login_redirect.html").contains("登錄"))
    }
}

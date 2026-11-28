package me.jbusdriver.modern.data.parser

import me.jbusdriver.modern.domain.model.hasNext
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 版块主题列表真实页面回归（fixtures：board_fid36_{guest,logged_in,nocookie_login_redirect}）。
 */
class ForumThreadParserFixtureTest {

    @Test
    fun `logged-in board page parses threads, pinned rows, type filters and pagination`() {
        val result = parseForumThreads(
            ForumFixtures.doc("board_fid36_logged_in.html"),
            ForumFixtures.SITE_BASE,
        )

        assertTrue("threads should not be empty", result.threads.isNotEmpty())
        assertTrue("fid=36 first page has 30 threads, got ${result.threads.size}", result.threads.size >= 20)
        result.threads.forEach {
            assertTrue("tid invalid: $it", it.tid > 0)
            assertTrue("title blank: $it", it.title.isNotBlank())
            assertTrue("author blank: $it", it.author.isNotBlank())
            assertTrue("viewCount negative: $it", it.viewCount >= 0)
            assertTrue("replyCount negative: $it", it.replyCount >= 0)
        }
        assertTrue("should contain pinned threads", result.threads.any { it.isPinned })

        assertTrue("type filters should not be empty", result.typeFilters.isNotEmpty())
        result.typeFilters.forEach {
            assertTrue("typeId invalid: $it", it.typeId > 0)
            assertTrue("filter name blank: $it", it.name.isNotBlank())
        }

        assertEquals(1, result.pageInfo.activePage)
        assertTrue("board has more pages", result.pageInfo.hasNext)
    }

    @Test
    fun `guest board page parses the same shapes as logged-in`() {
        val guest = parseForumThreads(
            ForumFixtures.doc("board_fid36_guest.html"),
            ForumFixtures.SITE_BASE,
        )
        val loggedInFilters = parseForumThreads(
            ForumFixtures.doc("board_fid36_logged_in.html"),
            ForumFixtures.SITE_BASE,
        ).typeFilters

        assertTrue(guest.threads.isNotEmpty())
        assertEquals(
            "guest and logged-in expose the same typeid chips",
            loggedInFilters.map { it.typeId },
            guest.typeFilters.map { it.typeId },
        )
        assertEquals(1, guest.pageInfo.activePage)
        assertTrue(guest.pageInfo.hasNext)
    }

    @Test
    fun `no-cookie redirect page parses as empty thread list`() {
        // TODO(A5 ParseFailure)：登录重定向应与空版块可区分。
        val result = parseForumThreads(
            ForumFixtures.doc("board_fid36_nocookie_login_redirect.html"),
            ForumFixtures.SITE_BASE,
        )
        assertTrue(result.threads.isEmpty())
        assertTrue(result.typeFilters.isEmpty())
    }
}

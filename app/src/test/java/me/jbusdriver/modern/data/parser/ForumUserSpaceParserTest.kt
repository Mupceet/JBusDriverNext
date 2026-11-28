package me.jbusdriver.modern.data.parser

import me.jbusdriver.modern.domain.model.ForumLoginState
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 论坛账号/用户空间真实页面回归（fixtures：login_page_guest / user_space_self_logged_in /
 * user_space_guest / user_threads_public / favorites_logged_in_nonempty）。
 *
 * 协议依据 docs/FORUM_DESIGN.md §2.3-2（登录态标志）、§2.4-3（收藏行结构）。
 */
class ForumUserSpaceParserTest {

    @Test
    fun `login uid marker distinguishes guest from logged-in pages`() {
        assertEquals(0, parseForumLoginUid(ForumFixtures.html("forum_index_guest.html")))
        assertEquals(999999, parseForumLoginUid(ForumFixtures.html("forum_index_logged_in.html")))
    }

    @Test
    fun `self space page parses full logged-in state`() {
        val state = parseForumLoginState(
            ForumFixtures.doc("user_space_self_logged_in.html"),
            ForumFixtures.SITE_BASE,
        )
        val loggedIn = state as ForumLoginState.LoggedIn
        assertEquals(999999, loggedIn.uid)
        assertEquals("fixture_user", loggedIn.username)
        assertTrue("group blank: ${loggedIn.groupName}", !loggedIn.groupName.isNullOrBlank())
    }

    @Test
    fun `guest space page parses as anonymous`() {
        val state = parseForumLoginState(
            ForumFixtures.doc("user_space_guest.html"),
            ForumFixtures.SITE_BASE,
        )
        assertEquals(ForumLoginState.Anonymous, state)
    }

    @Test
    fun `login page parses dynamic form with loginhash and seccode hash`() {
        val form = parseLoginForm(ForumFixtures.doc("login_page_guest.html"))
        assertNotNull(form)
        assertTrue("action should carry loginhash: ${form!!.action}", form.action.contains("loginhash="))
        assertEquals("fixt4hash", form.formhash)
        assertTrue("seccode hash should be scraped from updateseccode JS", form.seccodeHash.isNotBlank())
    }

    @Test
    fun `public user profile parses identity and grouped fields`() {
        val profile = parseForumUserProfile(
            ForumFixtures.doc("user_space_guest.html"),
            ForumFixtures.SITE_BASE,
        )
        assertEquals(662631, profile.uid)
        assertEquals("甜你的苞", profile.username)
        assertTrue(profile.groupName.isNotBlank())
        assertTrue("basics missing 性別: ${profile.basics}", profile.basics.any { it.label.contains("性別") })
        assertTrue("basics missing 里程: ${profile.basics}", profile.basics.any { it.label.contains("里程") })
        assertTrue(
            "basics must not leak activity fields: ${profile.basics}",
            profile.basics.none { it.label.contains("註冊時間") },
        )
        assertTrue("activity missing 註冊時間: ${profile.activity}", profile.activity.any { it.label.contains("註冊時間") })
        assertEquals("friend count", 0, profile.friendCount)
        assertEquals("reply count", 185, profile.replyCount)
        assertEquals("topic count", 4, profile.topicCount)
        // 用户组 tip："里程 533, 距離下一級還需 167 里程"
        assertEquals("mileage", 533, profile.mileage)
        assertEquals("mileage to next level", 167, profile.mileageToNextLevel)
    }

    @Test
    fun `self profile keeps visible-to-self IP fields in activity`() {
        val profile = parseForumUserProfile(
            ForumFixtures.doc("user_space_self_logged_in.html"),
            ForumFixtures.SITE_BASE,
        )
        // 采集账号为当日新注册（oltime=0、无发帖），在线时间/上次发表时间按值条件渲染而缺席
        assertTrue("activity missing 註冊 IP: ${profile.activity}", profile.activity.any { it.label.contains("註冊") && it.label.contains("IP") })
        assertTrue("activity missing 上次訪問 IP: ${profile.activity}", profile.activity.any { it.label.contains("上次訪問") })
        assertTrue(
            "IP fields must stay in activity: ${profile.basics}",
            profile.basics.none { it.label.contains("IP") },
        )
        assertEquals("friend count", 0, profile.friendCount)
        assertEquals("reply count", 0, profile.replyCount)
        assertEquals("topic count", 0, profile.topicCount)
    }

    @Test
    fun `user thread list parses rows with tid title and board`() {
        val result = parseUserThreadList(ForumFixtures.doc("user_threads_public.html"))
        assertTrue("threads empty", result.threads.isNotEmpty())
        result.threads.forEach {
            assertTrue("tid invalid: $it", it.tid > 0)
            assertTrue("title blank: $it", it.title.isNotBlank())
            assertTrue("title leaked pager link: $it", it.title.toIntOrNull() == null)
        }
        assertEquals("rows must be deduped by tid", result.threads.size, result.threads.distinctBy { it.tid }.size)
        // td.num：<a>回復数</a> <em>查看数</em>
        val first = result.threads.first { it.tid == 179234 }
        assertEquals("reply count of 179234", 9, first.replyCount)
        assertEquals("view count of 179234", 1378, first.viewCount)
        val second = result.threads.first { it.tid == 168984 }
        assertEquals("reply count of 168984", 34, second.replyCount)
        assertEquals("view count of 168984", 37489, second.viewCount)
    }

    @Test
    fun `favorites page parses rows with favId and tid`() {
        val result = parseUserFavorites(ForumFixtures.doc("favorites_logged_in_nonempty.html"))
        assertEquals(1, result.favorites.size)
        val fav = result.favorites.first()
        assertEquals(13416594L, fav.favId)
        assertEquals(179227, fav.tid)
        assertTrue("title mismatch: ${fav.title}", fav.title.contains("分享几个平时找片"))
    }
}

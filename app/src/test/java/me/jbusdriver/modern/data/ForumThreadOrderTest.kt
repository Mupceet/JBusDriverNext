package me.jbusdriver.modern.data

import me.jbusdriver.modern.data.settings.ForumFloorOrder
import me.jbusdriver.modern.data.settings.ForumThreadOrder
import me.jbusdriver.modern.data.settings.buildForumThreadDetailUrl
import me.jbusdriver.modern.data.settings.buildForumThreadListUrl
import me.jbusdriver.modern.data.settings.forumThreadDetailCacheKey
import org.junit.Assert.assertEquals
import org.junit.Test

class ForumThreadOrderTest {

    @Test
    fun `regular order keeps detail url unchanged`() {
        assertEquals(
            "https://www.javbus.com/forum/forum.php?mod=viewthread&tid=168357&page=1",
            buildForumThreadDetailUrl(
                baseUrl = "https://www.javbus.com",
                tid = 168357,
                page = 1,
                floorOrder = ForumFloorOrder.REGULAR
            )
        )
    }

    @Test
    fun `reverse order appends ordertype parameter`() {
        assertEquals(
            "https://www.javbus.com/forum/forum.php?mod=viewthread&tid=168357&page=1&ordertype=1",
            buildForumThreadDetailUrl(
                baseUrl = "https://www.javbus.com",
                tid = 168357,
                page = 1,
                floorOrder = ForumFloorOrder.REVERSE
            )
        )
    }

    @Test
    fun `detail cache key includes floor order`() {
        assertEquals(
            "forum_detail_v2_168357_1_regular",
            forumThreadDetailCacheKey(168357, 1, ForumFloorOrder.REGULAR)
        )
        assertEquals(
            "forum_detail_v2_168357_1_reverse",
            forumThreadDetailCacheKey(168357, 1, ForumFloorOrder.REVERSE)
        )
    }

    @Test
    fun `lastpost thread order appends orderby parameter`() {
        assertEquals(
            "https://www.javbus.com/forum/forum.php?mod=forumdisplay&fid=2&page=1&orderby=lastpost",
            buildForumThreadListUrl(
                baseUrl = "https://www.javbus.com",
                fid = 2,
                page = 1,
                typeId = null,
                threadOrder = ForumThreadOrder.LASTPOST
            )
        )
    }

    @Test
    fun `dateline thread order appends only orderby parameter`() {
        assertEquals(
            "https://www.javbus.com/forum/forum.php?mod=forumdisplay&fid=2&page=1&orderby=dateline",
            buildForumThreadListUrl(
                baseUrl = "https://www.javbus.com",
                fid = 2,
                page = 1,
                typeId = null,
                threadOrder = ForumThreadOrder.DATELINE
            )
        )
    }

    @Test
    fun `heats thread order appends only orderby parameter`() {
        assertEquals(
            "https://www.javbus.com/forum/forum.php?mod=forumdisplay&fid=2&page=1&orderby=heats",
            buildForumThreadListUrl(
                baseUrl = "https://www.javbus.com",
                fid = 2,
                page = 1,
                typeId = null,
                threadOrder = ForumThreadOrder.HEATS
            )
        )
    }

    @Test
    fun `thread order appends orderby after typeid filter`() {
        assertEquals(
            "https://www.javbus.com/forum/forum.php?mod=forumdisplay&fid=2&page=1&filter=typeid&typeid=7&orderby=dateline",
            buildForumThreadListUrl(
                baseUrl = "https://www.javbus.com",
                fid = 2,
                page = 1,
                typeId = 7,
                threadOrder = ForumThreadOrder.DATELINE
            )
        )
    }

    @Test
    fun `unknown preference value falls back to lastpost`() {
        assertEquals(ForumThreadOrder.LASTPOST, ForumThreadOrder.fromPreferenceValue("garbage"))
        assertEquals(ForumThreadOrder.LASTPOST, ForumThreadOrder.fromPreferenceValue(null))
    }
}

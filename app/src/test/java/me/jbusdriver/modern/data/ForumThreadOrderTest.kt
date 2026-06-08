package me.jbusdriver.modern.data

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
}

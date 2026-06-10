package me.jbusdriver.modern.ui.forum

import org.junit.Assert.assertEquals
import org.junit.Test

class ForumThreadTitleTest {
    @Test
    fun `inline badge width scales with label length`() {
        assertEquals(1.56f, forumInlineBadgeWidthEm("A"))
        assertEquals(3.96f, forumInlineBadgeWidthEm("ABCD"))
    }

    @Test
    fun `inline badge width has a lower bound for empty labels`() {
        assertEquals(0.76f, forumInlineBadgeWidthEm(""))
    }
}

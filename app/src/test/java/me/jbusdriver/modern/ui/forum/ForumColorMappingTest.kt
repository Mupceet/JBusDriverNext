package me.jbusdriver.modern.ui.forum

import androidx.compose.ui.graphics.Color
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ForumColorMappingTest {
    @Test
    fun `low contrast red on light surface stays red and becomes readable`() {
        val source = Color(0xFFFF0000)

        val result = adaptForumTextColor(source, Color.White)

        assertNotEquals(source, result)
        assertTrue(result.red > result.green * 2f)
        assertTrue(result.red > result.blue * 2f)
        assertTrue(forumContrastRatio(result, Color.White) >= 4.5f)
    }

    @Test
    fun `low contrast blue on dark surface stays blue and becomes readable`() {
        val source = Color(0xFF0000FF)
        val surface = Color(0xFF121212)

        val result = adaptForumTextColor(source, surface)

        assertNotEquals(source, result)
        assertTrue(result.blue > result.red)
        assertTrue(result.blue > result.green)
        assertTrue(forumContrastRatio(result, surface) >= 4.5f)
    }

    @Test
    fun `already readable source color remains unchanged`() {
        val source = Color(0xFF006400)

        assertEquals(source, adaptForumTextColor(source, Color.White))
    }
}

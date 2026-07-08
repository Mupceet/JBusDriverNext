package me.jbusdriver.modern.data.localvideo

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class VideoCodeMatcherTest {

    @Test
    fun extractCode_exactMatch() {
        assertEquals("ABC-123", VideoCodeMatcher.extractCode("ABC-123.mp4"))
    }

    @Test
    fun extractCode_caseInsensitive_uppercases() {
        assertEquals("ABC-123", VideoCodeMatcher.extractCode("abc-123.mkv"))
    }

    @Test
    fun extractCode_keepsAlphabeticSuffix_asDistinctCode() {
        // ABC-123-C 是另一部影片，应整体提取，不截成 ABC-123
        assertEquals("ABC-123-C", VideoCodeMatcher.extractCode("ABC-123-C.mp4"))
    }

    @Test
    fun extractCode_stopsAtSeparator() {
        assertEquals("ABC-123", VideoCodeMatcher.extractCode("ABC-123_4K.mkv"))
        assertEquals("ABC-123", VideoCodeMatcher.extractCode("ABC-123 (1080p).mp4"))
        assertEquals("ABC-123", VideoCodeMatcher.extractCode("ABC-123.1080p.mp4"))
    }

    @Test
    fun extractCode_skipsLeadingBrackets() {
        assertEquals("ABC-123", VideoCodeMatcher.extractCode("[ABC-123].mp4"))
        assertEquals("ABC-123", VideoCodeMatcher.extractCode("[Group] ABC-123.mp4"))
    }

    @Test
    fun extractCode_returnsNullForNoCode() {
        assertNull(VideoCodeMatcher.extractCode("clip.mp4"))
        assertNull(VideoCodeMatcher.extractCode("4K-trailer.mp4"))
    }

    @Test
    fun matchesCode_table() {
        assertTrue(VideoCodeMatcher.matchesCode("ABC-123.mp4", "ABC-123"))
        assertTrue(VideoCodeMatcher.matchesCode("abc-123.mkv", "abc-123"))
        assertTrue(VideoCodeMatcher.matchesCode("ABC-123_4K.mkv", "ABC-123"))
        assertTrue(VideoCodeMatcher.matchesCode("ABC-123 (1080p).mp4", "ABC-123"))
        assertFalse(VideoCodeMatcher.matchesCode("ABC-123-C.mp4", "ABC-123"))
        assertFalse(VideoCodeMatcher.matchesCode("ABC-123D.mp4", "ABC-123"))
        assertFalse(VideoCodeMatcher.matchesCode("clip.mp4", "ABC-123"))
    }
}

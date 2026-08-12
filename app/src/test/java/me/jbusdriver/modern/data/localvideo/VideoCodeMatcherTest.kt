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

    @Test
    fun extractCode_supportsUnderscoreSeparator() {
        // 下划线作为"前缀与序号"的分隔符（与 ABC-123 等价的另一种写法）
        assertEquals("ABC_123", VideoCodeMatcher.extractCode("ABC_123.mp4"))
    }

    @Test
    fun extractCode_supportsFc2StyleCode() {
        // 字母数字混合前缀 + 多段 + 长序号
        assertEquals("FC2-PPV-1234567", VideoCodeMatcher.extractCode("FC2-PPV-1234567.mp4"))
    }

    @Test
    fun extractCode_rejectsResolutionPrefix() {
        // 纯分辨率/画质词不是番号；带分辨率前缀时应取后面的真实番号
        assertNull(VideoCodeMatcher.extractCode("4K-trailer.mp4"))
        assertEquals("ABC-123", VideoCodeMatcher.extractCode("1080p_ABC-123.mp4"))
    }

    // ---- -/_ equivalence (favorite movie name matching) ----

    @Test
    fun normalizeSeparators_unifiesDashAndUnderscore_uppercases() {
        assertEquals("ABC-123", VideoCodeMatcher.normalizeSeparators("ABC_123"))
        assertEquals("ABC-123", VideoCodeMatcher.normalizeSeparators("abc-123"))
        assertEquals("FC2-PPV-1234567", VideoCodeMatcher.normalizeSeparators("FC2_PPV_1234567"))
    }

    @Test
    fun matchesMovieName_exactAfterNormalization() {
        assertTrue(VideoCodeMatcher.matchesMovieName("ABC_123", "ABC-123"))
        assertTrue(VideoCodeMatcher.matchesMovieName("abc-123", "ABC-123"))
        assertTrue(VideoCodeMatcher.matchesMovieName("FC2-PPV-1234567", "FC2_PPV_1234567"))
    }

    @Test
    fun matchesMovieName_allowsSuffixAfterSeparator() {
        assertTrue(VideoCodeMatcher.matchesMovieName("ABC-123 (1080p)", "ABC-123"))
        assertTrue(VideoCodeMatcher.matchesMovieName("ABC_123_4K", "ABC-123"))
        assertTrue(VideoCodeMatcher.matchesMovieName("ABC-123-C", "ABC-123-C"))
    }

    @Test
    fun matchesMovieName_rejectsDifferentCodeOrTrailingLetter() {
        assertFalse(VideoCodeMatcher.matchesMovieName("ABCD-123", "ABC-123"))
        assertFalse(VideoCodeMatcher.matchesMovieName("ABC-123D", "ABC-123"))
        assertFalse(VideoCodeMatcher.matchesMovieName("clip", "ABC-123"))
        assertFalse(VideoCodeMatcher.matchesMovieName("", "ABC-123"))
    }
}

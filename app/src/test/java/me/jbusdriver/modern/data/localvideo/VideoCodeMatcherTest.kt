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
        // 下划线作为"前缀与序号"的分隔符（与 ABC-123 等价的另一写法）
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

    @Test
    fun extractCode_supportsDigitPrefixedCode() {
        // 素人系列数字前缀番号（真实站点数据采样）
        assertEquals("259LUXU-1895", VideoCodeMatcher.extractCode("259LUXU-1895.mp4"))
        assertEquals("390JAC-240", VideoCodeMatcher.extractCode("390JAC-240.mp4"))
        assertEquals("300MIUM-1377", VideoCodeMatcher.extractCode("300MIUM-1377.mp4"))
        assertEquals("3DSVR-2009", VideoCodeMatcher.extractCode("3DSVR-2009.mp4"))
    }

    @Test
    fun extractCode_supportsDateStyleUncensoredCode() {
        // 无码日期型番号：MMDDYY + 序号，- 与 _ 两种分隔符写法并存
        assertEquals("060126-001", VideoCodeMatcher.extractCode("060126-001.mp4"))
        assertEquals("080126_001", VideoCodeMatcher.extractCode("080126_001.mp4"))
    }

    @Test
    fun extractCode_supportsSingleLetterPrefixCode() {
        // 单字母前缀番号（真实站点数据采样）
        assertEquals("C-1534", VideoCodeMatcher.extractCode("C-1534.mp4"))
        assertEquals("P-2982", VideoCodeMatcher.extractCode("P-2982.mp4"))
    }

    @Test
    fun extractCode_supportsDigitsPlusKCode() {
        // 无码数字+k 番号：站点原样小写，提取后归一化为大写
        assertEquals("054K", VideoCodeMatcher.extractCode("054k.mp4"))
        assertEquals("031K", VideoCodeMatcher.extractCode("031K.mp4"))
    }

    @Test
    fun extractCode_realSiteCodes_roundTrip() {
        // 真实站点番号样本（debug 构建 MovieCodeSampleCollector 采集，见资源文件头注释）
        for (fileName in listOf("censored_codes.txt", "uncensored_codes.txt")) {
            val codes = javaClass.getResourceAsStream("/localvideo/$fileName")!!
                .bufferedReader()
                .readLines()
                .filter { it.isNotBlank() && !it.startsWith("#") }
            assertTrue("$fileName 样本应至少 500 个，实际 ${codes.size}", codes.size >= 500)
            for (code in codes) {
                // 提取结果归一化为大写（如 054k -> 054K），与番号大写比较
                assertEquals("$fileName 提取失败: $code", code.uppercase(), VideoCodeMatcher.extractCode("$code.mp4"))
                assertTrue("$fileName 匹配失败: $code", VideoCodeMatcher.matchesCode("$code.mp4", code))
            }
        }
    }
}

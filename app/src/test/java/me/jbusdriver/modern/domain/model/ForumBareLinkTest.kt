package me.jbusdriver.modern.domain.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ForumBareLinkTest {

    @Test
    fun `recognizes url and strips trailing punctuation`() {
        val text = "看这个：https://example.com/a?b=1，很好"
        val matches = findBareLinkMatches(text)
        assertEquals(1, matches.size)
        val match = matches.single()
        assertEquals(ForumLinkKind.URL, match.kind)
        assertEquals("https://example.com/a?b=1", match.value)
        // 命中区间不含尾部标点,与 value 一致
        assertEquals(match.value, text.substring(match.range))
    }

    @Test
    fun `recognizes magnet and absorbs embedded movie code`() {
        val text = "magnet:?xt=urn:btih:0123456789abcdef0123456789abcdef01234567 ABP-123"
        val matches = findBareLinkMatches(text)
        assertEquals(2, matches.size)
        assertEquals(ForumLinkKind.MAGNET, matches[0].kind)
        assertEquals(ForumLinkKind.MOVIE_CODE, matches[1].kind)
        assertEquals("ABP-123", matches[1].value)
    }

    @Test
    fun `movie code inside url is absorbed by url match`() {
        val matches = findBareLinkMatches("详情见 https://example.com/topic/ABP-123/page")
        assertEquals(1, matches.size)
        assertEquals(ForumLinkKind.URL, matches.single().kind)
    }

    @Test
    fun `recognizes bare cloud share link without scheme`() {
        val text = "链接 pan.baidu.com/s/1abcDEF 提取码1234"
        val matches = findBareLinkMatches(text)
        assertEquals(1, matches.size)
        assertEquals(ForumLinkKind.CLOUD, matches.single().kind)
        assertEquals("pan.baidu.com/s/1abcDEF", matches.single().value)
        assertEquals("https://pan.baidu.com/s/1abcDEF", matches.single().toExternalUrl())
    }

    @Test
    fun `cloud share url with scheme is absorbed by higher priority url match`() {
        val matches = findBareLinkMatches("https://pan.quark.cn/s/abc123")
        assertEquals(1, matches.size)
        assertEquals(ForumLinkKind.URL, matches.single().kind)
    }

    @Test
    fun `recognizes ed2k link`() {
        val match = findBareLinkMatches("ed2k://|file|abc.mp4|1024|0123|/").single()
        assertEquals(ForumLinkKind.ED2K, match.kind)
    }

    @Test
    fun `normalizes movie code value`() {
        val match = findBareLinkMatches("fc2-ppv-1234567").single()
        assertEquals(ForumLinkKind.MOVIE_CODE, match.kind)
        assertEquals("FC2-PPV-1234567", match.value)
    }

    @Test
    fun `adjacent same kind matches merge into one range`() {
        // URL 后紧跟无空格的二次命中(如引号缺失场景)合并为单条
        val text = "https://a.com/x?t=1"
        val matches = findBareLinkMatches(text)
        assertEquals(1, matches.size)
    }

    @Test
    fun `higher priority kind wins overlapping range`() {
        // 网盘裸域名出现在完整 URL 内部:URL 吞并网盘
        val matches = findBareLinkMatches("https://pan.baidu.com/s/1abcDEF")
        assertEquals(1, matches.size)
        assertEquals(ForumLinkKind.URL, matches.single().kind)
    }

    @Test
    fun `plain chinese text has no matches`() {
        assertTrue(findBareLinkMatches("这段话里什么链接都没有").isEmpty())
        assertTrue(findBareLinkMatches("").isEmpty())
    }
}

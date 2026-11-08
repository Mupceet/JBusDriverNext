package me.jbusdriver.modern.domain.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class MovieCodeTest {

    // ---- findMovieCodeMatches：识别 ----

    @Test
    fun `finds standard code embedded in text`() {
        assertEquals(
            listOf("ABP-123"),
            findMovieCodeMatches("強推 ABP-123 不好看嗎").map { it.value }
        )
    }

    @Test
    fun `finds code without hyphen`() {
        assertEquals(
            listOf("ABP123"),
            findMovieCodeMatches("分享 ABP123 資源").map { it.value }
        )
    }

    @Test
    fun `finds code adjacent to chinese characters`() {
        assertEquals(
            listOf("SSIS-001"),
            findMovieCodeMatches("番號SSIS-001推薦").map { it.value }
        )
    }

    @Test
    fun `finds multiple codes in order`() {
        assertEquals(
            listOf("ABP-123", "SSIS-456"),
            findMovieCodeMatches("先看 ABP-123 再看 SSIS-456").map { it.value }
        )
    }

    @Test
    fun `finds digit-prefixed label codes`() {
        assertEquals(
            listOf("200GANA-1234"),
            findMovieCodeMatches("素人系列 200GANA-1234").map { it.value }
        )
        assertEquals(
            listOf("300MIUM-001"),
            findMovieCodeMatches("300MIUM-001 泡姬").map { it.value }
        )
    }

    @Test
    fun `finds code with trailing suffix`() {
        assertEquals(
            listOf("IPX-001"),
            findMovieCodeMatches("中文字幕 IPX-001-C 已出").map { it.value }
        )
    }

    @Test
    fun `finds fc2 code and does not double match`() {
        assertEquals(
            listOf("FC2-PPV-1234567"),
            findMovieCodeMatches("作品 FC2-PPV-1234567 不错").map { it.value }
        )
        assertEquals(
            listOf("fc2-ppv-1185270"),
            findMovieCodeMatches("FC2 fc2-ppv-1185270").map { it.value }
        )
    }

    @Test
    fun `ignores lowercase prefixes and long digit runs`() {
        assertEquals(emptyList<String>(), findMovieCodeMatches("abp-123").map { it.value })
        assertEquals(emptyList<String>(), findMovieCodeMatches("看看 SSIS12345678 這串").map { it.value })
        assertEquals(emptyList<String>(), findMovieCodeMatches("iPhone-15").map { it.value })
        assertEquals(emptyList<String>(), findMovieCodeMatches("AV-1").map { it.value })
        assertEquals(emptyList<String>(), findMovieCodeMatches("T-28 退役").map { it.value })
        assertEquals(emptyList<String>(), findMovieCodeMatches("沒有番號的普通句子").map { it.value })
    }

    // ---- normalizeMovieCode：规范化 ----

    @Test
    fun `normalize keeps hyphenated code as is`() {
        assertEquals("ABP-123", normalizeMovieCode("ABP-123"))
        assertEquals("ABP-123", normalizeMovieCode(" ABP-123 "))
    }

    @Test
    fun `normalize inserts hyphen for missing separator`() {
        assertEquals("ABP-123", normalizeMovieCode("ABP123"))
        assertEquals("200GANA-1234", normalizeMovieCode("200GANA1234"))
    }

    @Test
    fun `normalize uppercases fc2 code`() {
        assertEquals("FC2-PPV-1234567", normalizeMovieCode("fc2-ppv-1234567"))
        assertEquals("FC2-PPV-1234567", normalizeMovieCode("FC2-PPV-1234567"))
    }

    @Test
    fun `normalize rejects non codes`() {
        assertNull(normalizeMovieCode("hello"))
        assertNull(normalizeMovieCode("ABP-1"))
        assertNull(normalizeMovieCode("iPhone-15"))
        assertNull(normalizeMovieCode("ABP-123-C"))
    }

    // ---- movieCodeFromUrl：URL 解析 ----

    @Test
    fun `extracts code from censored detail url`() {
        assertEquals("ABP-123", movieCodeFromUrl("https://www.javbus.com/ABP-123"))
    }

    @Test
    fun `extracts code from uncensored detail url`() {
        assertEquals("ABP-123", movieCodeFromUrl("https://www.javbus.com/uncensored/ABP-123"))
    }

    @Test
    fun `extracts fc2 code from detail url`() {
        assertEquals("FC2-PPV-1234567", movieCodeFromUrl("https://www.javbus.com/FC2-PPV-1234567"))
    }

    @Test
    fun `returns null for non movie urls`() {
        assertNull(movieCodeFromUrl("https://www.javbus.com/genre/abc"))
        assertNull(movieCodeFromUrl("https://www.javbus.com/star/xyz"))
        assertNull(movieCodeFromUrl("https://www.javbus.com/"))
        assertNull(movieCodeFromUrl("not a url"))
    }
}

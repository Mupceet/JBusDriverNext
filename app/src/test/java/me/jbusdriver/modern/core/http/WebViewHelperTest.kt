package me.jbusdriver.modern.core.http

import org.junit.Assert.assertEquals
import org.junit.Test

class WebViewHelperTest {
    @Test
    fun `unescape js string handles common escaped characters`() {
        assertEquals(
            "line1\nline2\t\"quoted\"/path\\tail",
            WebViewHelper.unescapeJsString(""""line1\nline2\t\"quoted\"\/path\\tail"""")
        )
    }

    @Test
    fun `unescape js string handles unicode escapes and leaves malformed escapes intact`() {
        assertEquals("Alice 字 \\u12GZ", WebViewHelper.unescapeJsString(""""Alice \u5b57 \u12GZ""""))
    }

    @Test
    fun `unescape js string returns non quoted values unchanged`() {
        assertEquals("null", WebViewHelper.unescapeJsString("null"))
    }

    @Test
    fun `page load guard ignores finish before current navigation starts`() {
        val guard = PageLoadGuard("https://example.test/forum")

        assertEquals(false, guard.shouldAcceptFinish("https://example.test/forum"))
        guard.onPageStarted()
        assertEquals(true, guard.shouldAcceptFinish("https://example.test/forum"))
    }

    @Test
    fun `page load guard rejects cross-host finish`() {
        val guard = PageLoadGuard("https://example.test/forum")

        guard.onPageStarted()

        assertEquals(false, guard.shouldAcceptFinish("https://other.test/forum"))
    }
}

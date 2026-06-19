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
}

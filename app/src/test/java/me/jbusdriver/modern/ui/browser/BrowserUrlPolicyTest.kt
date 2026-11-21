package me.jbusdriver.modern.ui.browser

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class BrowserUrlPolicyTest {

    // ---- decide ----

    @Test
    fun decide_httpAndHttps_loadInWebView() {
        assertEquals(BrowserUrlDecision.LoadInWebView, BrowserUrlPolicy.decide("https://example.com/page"))
        assertEquals(BrowserUrlDecision.LoadInWebView, BrowserUrlPolicy.decide("http://example.com"))
        // scheme 大小写不敏感
        assertEquals(BrowserUrlDecision.LoadInWebView, BrowserUrlPolicy.decide("HTTPS://example.com"))
    }

    @Test
    fun decide_pageInternalSchemes_loadInWebView() {
        assertEquals(BrowserUrlDecision.LoadInWebView, BrowserUrlPolicy.decide("about:blank"))
        // data: 内容必须是合法 URI 字符（已编码），否则按解析失败外抛处理
        assertEquals(BrowserUrlDecision.LoadInWebView, BrowserUrlPolicy.decide("data:text/html,%3Chtml%3E%3C/html%3E"))
        assertEquals(BrowserUrlDecision.LoadInWebView, BrowserUrlPolicy.decide("blob:https://example.com/abc"))
    }

    @Test
    fun decide_magnetAndEd2k_launchExternal() {
        assertEquals(BrowserUrlDecision.LaunchExternal, BrowserUrlPolicy.decide("magnet:?xt=urn:btih:0123456789abcdef0123456789abcdef01234567"))
        assertEquals(BrowserUrlDecision.LaunchExternal, BrowserUrlPolicy.decide("ed2k://|file|name|1024|hash|/"))
    }

    @Test
    fun decide_otherExternalSchemes_launchExternal() {
        assertEquals(BrowserUrlDecision.LaunchExternal, BrowserUrlPolicy.decide("intent://example.com#Intent;scheme=https;end"))
        assertEquals(BrowserUrlDecision.LaunchExternal, BrowserUrlPolicy.decide("mailto:a@b.com"))
        assertEquals(BrowserUrlDecision.LaunchExternal, BrowserUrlPolicy.decide("tel:+10000000000"))
    }

    @Test
    fun decide_unparsableUrl_launchExternal() {
        // 无 scheme
        assertEquals(BrowserUrlDecision.LaunchExternal, BrowserUrlPolicy.decide("www.example.com"))
        // 非法字符导致 URI 解析失败
        assertEquals(BrowserUrlDecision.LaunchExternal, BrowserUrlPolicy.decide("http://example.com/a b c"))
        assertEquals(BrowserUrlDecision.LaunchExternal, BrowserUrlPolicy.decide(null))
        assertEquals(BrowserUrlDecision.LaunchExternal, BrowserUrlPolicy.decide(""))
    }

    // ---- hostOf ----

    @Test
    fun hostOf_extractsHostWithoutWww() {
        assertEquals("example.com", BrowserUrlPolicy.hostOf("https://www.example.com/path?q=1"))
        assertEquals("example.com", BrowserUrlPolicy.hostOf("http://example.com"))
    }

    @Test
    fun hostOf_returnsNullForUnparsable() {
        assertNull(BrowserUrlPolicy.hostOf("www.example.com"))
        assertNull(BrowserUrlPolicy.hostOf(""))
    }

    // ---- downloadHeaders ----

    @Test
    fun downloadHeaders_includesAllThreeWhenPresent() {
        val headers = BrowserUrlPolicy.downloadHeaders(
            cookie = "sid=abc; auth=xyz",
            referer = "https://example.com/page",
            userAgent = "Mozilla/5.0",
        )
        assertEquals(
            mapOf(
                "Cookie" to "sid=abc; auth=xyz",
                "Referer" to "https://example.com/page",
                "User-Agent" to "Mozilla/5.0",
            ),
            headers,
        )
    }

    @Test
    fun downloadHeaders_filtersBlankAndMissingValues() {
        assertEquals(
            mapOf("Cookie" to "sid=abc"),
            BrowserUrlPolicy.downloadHeaders(cookie = "sid=abc", referer = "  ", userAgent = null),
        )
        assertEquals(
            emptyMap<String, String>(),
            BrowserUrlPolicy.downloadHeaders(cookie = null, referer = null, userAgent = null),
        )
    }
}

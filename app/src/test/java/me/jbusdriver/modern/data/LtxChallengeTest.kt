package me.jbusdriver.modern.data

import me.jbusdriver.modern.data.ltx.isLtxCloudflareChallenge
import me.jbusdriver.modern.data.ltx.isLtxCryptoSessionRequired
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * [isLtxCloudflareChallenge] 判定边界：正常 JSON 永不误判，各类 Cloudflare
 * 挑战/拦截页均能命中。
 */
class LtxChallengeTest {

    @Test
    fun `valid json payload is not a challenge`() {
        val json = """{"items":[{"code":"ABC-123","links":[]}],"updated_at":1700000000}"""
        assertFalse(isLtxCloudflareChallenge(json))
    }

    @Test
    fun `json array payload with marker-like content is not a challenge`() {
        // JSON 里有任何特征词也不该误判（先按 JSON 开头短路）
        val json = """[{"note":"just a moment, challenge-platform ahead"}]"""
        assertFalse(isLtxCloudflareChallenge(json))
    }

    @Test
    fun `json with leading bom and whitespace is not a challenge`() {
        val json = "\uFEFF\r\n  {\"items\":[]}"
        assertFalse(isLtxCloudflareChallenge(json))
    }

    @Test
    fun `managed challenge waiting page is detected`() {
        val html = """
            <html><head><title>Just a moment...</title></head>
            <body><script src="/cdn-cgi/challenge-platform/h/g/orchestrate/chl_page_v1"></script></body></html>
        """.trimIndent()
        assertTrue(isLtxCloudflareChallenge(html))
    }

    @Test
    fun `challenge redirect page with cf-chl token is detected`() {
        val html = """
            <html><body><script>
            window._cf_chl_opt = { cRay: '8f2a', cType: 'managed' };
            location.replace('/?__cf_chl_rt_tk=abc');
            </script></body></html>
        """.trimIndent()
        assertTrue(isLtxCloudflareChallenge(html))
    }

    @Test
    fun `legacy browser verification page is detected`() {
        val html = "<html><body>Checking your browser before accessing</body></html>"
        assertTrue(isLtxCloudflareChallenge(html))
    }

    @Test
    fun `attention required block page is detected`() {
        val html = "<html><head><title>Attention Required! | Cloudflare</title></head></html>"
        assertTrue(isLtxCloudflareChallenge(html))
    }

    @Test
    fun `cloudflare access login page is detected`() {
        val html = """
            <html><head><title>Sign in · Cloudflare Access</title></head>
            <body><form action="https://team.cloudflareaccess.com/cdn-cgi/access/login"></form></body></html>
        """.trimIndent()
        assertTrue(isLtxCloudflareChallenge(html))
    }

    @Test
    fun `worker verify gate json response is detected`() {
        // 2026-11 实测的 Worker 层 Turnstile 门禁响应（JSON，非挑战 HTML）
        val json = """
            {"error":"请先完成人机验证","reason":"verify","sitekey":"0x4AAAAAAF85x4MDZz2EOrU5"}
        """.trim()
        assertTrue(isLtxCloudflareChallenge(json))
    }

    @Test
    fun `worker automated clients plain text is detected`() {
        val text = "Automated clients are not allowed. 请用浏览器访问。"
        assertTrue(isLtxCloudflareChallenge(text))
    }

    @Test
    fun `plain html without markers is not a challenge`() {
        val html = "<html><body><h1>404 Not Found</h1></body></html>"
        assertFalse(isLtxCloudflareChallenge(html))
    }

    @Test
    fun `empty body is not a challenge`() {
        assertFalse(isLtxCloudflareChallenge(""))
    }

    // ---- 2026-11-15 迁移站新形态：非人机验证，走各自的独立通道 ----

    @Test
    fun `crypto session json is not a human verification challenge`() {
        // 428 `{"reason":"cryptoSession"}` 是加密会话缺失，Turnstile cookie 解决不了
        val json = """{"error":"请重新建立安全会话","reason":"cryptoSession"}"""
        assertFalse(isLtxCloudflareChallenge(json))
    }

    @Test
    fun `migrated json is not a human verification challenge`() {
        // 旧域 410 迁移公告
        val json = """{"error":"原站已迁移至 https://ltxlib.dpdns.org/，旧版接口已停用","reason":"migrated","url":"https://ltxlib.dpdns.org/"}"""
        assertFalse(isLtxCloudflareChallenge(json))
    }

    @Test
    fun `crypto session requirement json is detected`() {
        val json = """{"error":"请重新建立安全会话","reason":"cryptoSession"}"""
        assertTrue(isLtxCryptoSessionRequired(json))
    }

    @Test
    fun `crypto session detector ignores other bodies`() {
        // 正常载荷、人机验证门、缺单一标记的 JSON、HTML 均不判为加密会话缺失
        assertFalse(isLtxCryptoSessionRequired("""{"items":[],"updated_at":1}"""))
        assertFalse(isLtxCryptoSessionRequired("""{"error":"请先完成人机验证","reason":"verify","sitekey":"0x4X"}"""))
        assertFalse(isLtxCryptoSessionRequired("""{"reason":"verify"}"""))
        assertFalse(isLtxCryptoSessionRequired("""{"error":"needs crypto session"}"""))
        assertFalse(isLtxCryptoSessionRequired("<html>cryptoSession reason</html>"))
        assertFalse(isLtxCryptoSessionRequired(""))
    }
}

package me.jbusdriver.modern.core.http

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class HtmlClientVerifyTest {

    private val normalUrl = "https://www.javbus.com/abc-001"
    private val normalBody = "<html><body>normal movie page</body></html>"

    @Test
    fun cleanResponseIsNotVerification() {
        assertFalse(isDriverVerifyPage(normalUrl, normalBody))
    }

    @Test
    fun detectsVerificationInFinalUrl() {
        assertTrue(isDriverVerifyPage("$normalUrl/doc/driver-verify", normalBody))
    }

    @Test
    fun detectsVerificationPathInBody() {
        assertTrue(isDriverVerifyPage(normalUrl, "<a href=\"/doc/driver-verify\">verify</a>"))
    }

    @Test
    fun detectsVerificationMarkerInBody() {
        assertTrue(isDriverVerifyPage(normalUrl, "<div>please complete driver-verify</div>"))
    }

    @Test
    fun detectionIsCaseInsensitive() {
        assertTrue(isDriverVerifyPage(normalUrl, "DRIVER-VERIFY required"))
    }
}

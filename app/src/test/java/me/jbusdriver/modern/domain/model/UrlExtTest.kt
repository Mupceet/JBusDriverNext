package me.jbusdriver.modern.domain.model

import org.junit.Assert.assertEquals
import org.junit.Assert.fail
import org.junit.Test

class UrlExtTest {

    @Test
    fun urlHostFallsBackToJavaUriOnJvm() {
        assertEquals("https://www.javbus.com", "https://www.javbus.com/ABC-001".urlHost)
    }

    @Test
    fun urlHostRejectsInvalidUrls() {
        try {
            "not a url".urlHost
            fail("Expected invalid URL host to throw")
        } catch (_: IllegalArgumentException) {
            // Expected.
        }
    }

    @Test
    fun urlPathFallsBackToJavaUriOnJvm() {
        assertEquals("/ABC-001", "https://www.javbus.com/ABC-001".urlPath)
    }
}

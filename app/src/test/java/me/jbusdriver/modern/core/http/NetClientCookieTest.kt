package me.jbusdriver.modern.core.http

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class NetClientCookieTest {

    @Test
    fun `controlled cookie replaces stale value without dropping session cookies`() {
        val cookies = mergeControlledCookie(
            existingCookies = "PHPSESSID=session; existmag=mag; age=confirmed",
            name = "existmag",
            value = "all"
        )

        assertEquals(1, cookies.split(";").count { it.trim().startsWith("existmag=") })
        assertTrue(cookies.contains("existmag=all"))
        assertTrue(cookies.contains("PHPSESSID=session"))
        assertTrue(cookies.contains("age=confirmed"))
    }

    @Test
    fun `controlled cookie can switch back from all to magnet only`() {
        val cookies = mergeControlledCookie(
            existingCookies = "existmag=all; PHPSESSID=session",
            name = "existmag",
            value = "mag"
        )

        assertEquals("PHPSESSID=session; existmag=mag", cookies)
    }
}

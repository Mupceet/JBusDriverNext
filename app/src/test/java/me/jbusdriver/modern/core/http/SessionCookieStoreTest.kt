package me.jbusdriver.modern.core.http

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class SessionCookieStoreTest {
    @Test
    fun `parse session cookie string trims names and preserves equals in values`() {
        val cookies = parseSessionCookieString(
            " age=confirmed ; PHPSESSID=abc=123 ; empty= ; malformed "
        )

        assertEquals("confirmed", cookies["age"])
        assertEquals("abc=123", cookies["PHPSESSID"])
        assertEquals("", cookies["empty"])
        assertFalse(cookies.containsKey("malformed"))
    }

    @Test
    fun `session is valid when critical cookies exist and are not expired`() {
        val entries = mapOf(
            "age" to SessionCookieStore.PersistedCookie("confirmed", expiresAt = 0L),
            "4fJN_2132_saltkey" to SessionCookieStore.PersistedCookie("salt", expiresAt = 2_000L)
        )

        assertTrue(isPersistedSessionValid(entries, nowSeconds = 1_000L))
    }

    @Test
    fun `session is invalid when critical cookie is missing or expired`() {
        assertFalse(
            isPersistedSessionValid(
                mapOf("age" to SessionCookieStore.PersistedCookie("confirmed", expiresAt = 0L)),
                nowSeconds = 1_000L
            )
        )
        assertFalse(
            isPersistedSessionValid(
                mapOf(
                    "age" to SessionCookieStore.PersistedCookie("confirmed", expiresAt = 0L),
                    "4fJN_2132_saltkey" to SessionCookieStore.PersistedCookie("salt", expiresAt = 999L)
                ),
                nowSeconds = 1_000L
            )
        )
    }
}

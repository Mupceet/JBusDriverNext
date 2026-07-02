package me.jbusdriver.modern.core.http

import org.junit.Assert.assertEquals
import org.junit.Test

class ExistMagTest {
    @Test
    fun `showAll true maps to all`() {
        assertEquals("all", existMagCookieValue(showAll = true))
    }

    @Test
    fun `showAll false maps to mag`() {
        assertEquals("mag", existMagCookieValue(showAll = false))
    }
}

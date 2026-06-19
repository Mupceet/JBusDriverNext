package me.jbusdriver.modern.data.parser

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class MagnetHtmlParserTest {
    @Test
    fun `magnets parse rows after header and keep link rows only`() {
        val magnets = parseMagnets(
            """
                <tr><th>Name</th><th>Size</th><th>Date</th></tr>
                <tr><td><a href="magnet:?xt=one">One</a></td><td>1 GB</td><td>2026-01-01</td></tr>
                <tr><td>No link</td><td>2 GB</td><td>2026-01-02</td></tr>
                <tr><td><a href="magnet:?xt=two">Two</a></td><td>3 GB</td><td>2026-01-03</td></tr>
            """.trimIndent()
        )

        assertEquals(listOf("One", "Two"), magnets.map { it.name })
        assertEquals(listOf("1 GB", "3 GB"), magnets.map { it.size })
        assertEquals(listOf("2026-01-01", "2026-01-03"), magnets.map { it.date })
        assertEquals(listOf("magnet:?xt=one", "magnet:?xt=two"), magnets.map { it.link })
    }

    @Test
    fun `magnets return empty for only header`() {
        assertTrue(parseMagnets("<tr><th>Name</th></tr>").isEmpty())
    }
}

package me.jbusdriver.modern.data.parser

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class MagnetHtmlParserTest {
    @Test
    fun `keeps every magnet row and filters rows without a magnet link`() {
        val magnets = parseMagnets(
            """
            <tr><td><a href="magnet:?xt=one">One</a></td><td>1 GB</td><td>2026-01-01</td></tr>
            <tr><td>No link here</td><td>2 GB</td><td>2026-01-02</td></tr>
            <tr><td><a href="magnet:?xt=two">Two</a></td><td>3 GB</td><td>2026-01-03</td></tr>
            """.trimIndent()
        )
        assertEquals(listOf("One", "Two"), magnets.map { it.name })
        assertEquals(listOf("1 GB", "3 GB"), magnets.map { it.size })
        assertEquals(listOf("2026-01-01", "2026-01-03"), magnets.map { it.date })
        assertEquals(listOf("magnet:?xt=one", "magnet:?xt=two"), magnets.map { it.link })
    }

    @Test
    fun `does not drop the first magnet row when the ajax response has no header`() {
        // JavBus uncledatoolsbyajax.php returns magnet <tr> rows only — no <th> header.
        // A previous drop(1) skipped the first real magnet, losing one result.
        val magnets = parseMagnets(
            """
            <tr><td><a href="magnet:?xt=first">First</a></td><td>1 GB</td><td>2026-01-01</td></tr>
            <tr><td><a href="magnet:?xt=second">Second</a></td><td>2 GB</td><td>2026-01-02</td></tr>
            <tr><td><a href="magnet:?xt=third">Third</a></td><td>3 GB</td><td>2026-01-03</td></tr>
            """.trimIndent()
        )
        assertEquals(3, magnets.size)
        assertEquals("magnet:?xt=first", magnets.first().link)
    }

    @Test
    fun `a leading header row with no magnet link is ignored`() {
        // Robust either way: if a header row appears, it has no magnet link and is filtered.
        val magnets = parseMagnets(
            """
            <tr><th>Name</th><th>Size</th><th>Date</th></tr>
            <tr><td><a href="magnet:?xt=one">One</a></td><td>1 GB</td><td>2026-01-01</td></tr>
            """.trimIndent()
        )
        assertEquals(listOf("One"), magnets.map { it.name })
    }

    @Test
    fun `returns empty when no rows carry a magnet link`() {
        assertTrue(parseMagnets("<tr><th>Name</th></tr>").isEmpty())
        assertTrue(parseMagnets("<tr><td>No link</td></tr>").isEmpty())
    }
}

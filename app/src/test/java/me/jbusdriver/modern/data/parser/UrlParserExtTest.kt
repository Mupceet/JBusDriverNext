package me.jbusdriver.modern.data.parser

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Test

class UrlParserExtTest {

    @Test
    fun `stripToPath removes scheme and host keeping path and query`() {
        assertEquals(
            "/pics/cover/abp-123_b.jpg",
            "https://www.javbus.com/pics/cover/abp-123_b.jpg".stripToPath()
        )
        assertEquals(
            "/pics/sample/1.jpeg?v=2",
            "https://www.javbus.com/pics/sample/1.jpeg?v=2".stripToPath()
        )
    }

    @Test
    fun `stripToPath is identical across mirror hosts`() {
        val mainSite = "https://www.javbus.com/pics/actress/abc.jpg"
        val mirror = "https://mirror.example.net/pics/actress/abc.jpg"

        assertEquals(mainSite.stripToPath(), mirror.stripToPath())
    }

    @Test
    fun `stripToPath handles protocol relative urls`() {
        assertEquals("/x.jpg?a=1", "//cdn.example.com/x.jpg?a=1".stripToPath())
    }

    @Test
    fun `stripToPath returns relative and non-http urls unchanged`() {
        assertEquals("/pics/x.jpg", "/pics/x.jpg".stripToPath())
        assertEquals("file:///tmp/x.jpg", "file:///tmp/x.jpg".stripToPath())
        assertEquals("", "".stripToPath())
    }

    @Test
    fun `stripToPath returns root when url has no path`() {
        assertEquals("/", "https://www.javbus.com".stripToPath())
        assertEquals("/", "//cdn.example.com".stripToPath())
    }

    @Test
    fun `stripToPath distinguishes external hosts with different paths`() {
        val dmm = "https://pics.dmm.co.jp/mono/movie/adult/x/xps.jpg"
        val site = "https://www.javbus.com/pics/cover/xps.jpg"

        assertEquals("/mono/movie/adult/x/xps.jpg", dmm.stripToPath())
        assertNotEquals(dmm.stripToPath(), site.stripToPath())
    }
}

package me.jbusdriver.modern.core.site

import org.junit.Assert.assertEquals
import org.junit.Test

class SiteConfigTest {

    private val base = "https://www.javbus.com"

    @Test
    fun absoluteHttpUrlReturnedAsIs() {
        assertEquals("https://example.com/x", resolveUrl(base, "https://example.com/x"))
    }

    @Test
    fun absoluteHttpsUrlReturnedAsIs() {
        assertEquals("https://example.com/y", resolveUrl(base, "https://example.com/y"))
    }

    @Test
    fun absolutePathAppendedToBaseRoot() {
        assertEquals("$base/genre/1", resolveUrl(base, "/genre/1"))
    }

    @Test
    fun relativePathGetsLeadingSlash() {
        assertEquals("$base/genre/2", resolveUrl(base, "genre/2"))
    }

    @Test
    fun baseUrlTrailingSlashIsTrimmed() {
        assertEquals("$base/abc", resolveUrl("$base/", "/abc"))
    }

    @Test
    fun pathWithMultipleSegmentsResolved() {
        assertEquals("$base/uncensored/actresses/2", resolveUrl(base, "/uncensored/actresses/2"))
    }
}

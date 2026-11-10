package me.jbusdriver.modern.data.settings

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class SiteSettingsLogicTest {

    // region normalizeCustomUrlInput

    @Test
    fun `custom url input normalizes scheme host case and trailing slash`() {
        assertEquals(
            "https://mirror.example.com/PATH",
            normalizeCustomUrlInput(" HTTPS://Mirror.Example.com/PATH/ ")
        )
    }

    @Test
    fun `custom url input rejects blank non-http and hostless input`() {
        assertNull(normalizeCustomUrlInput(""))
        assertNull(normalizeCustomUrlInput("   "))
        assertNull(normalizeCustomUrlInput("example.com"))        // no scheme
        assertNull(normalizeCustomUrlInput("ftp://example.com"))  // non-http scheme
        assertNull(normalizeCustomUrlInput("https:///nohost"))    // no host
    }

    // endregion

    // region composeScanSeeds

    @Test
    fun `scan seeds combine preset selected custom and cached with dedup`() {
        val seeds = composeScanSeeds(
            presetUrl = "https://www.javbus.com/",
            selectedUrl = "https://mirror.example.com",
            customUrls = listOf("https://proxy.example.com/", "https://www.javbus.com"),
            cachedUrls = listOf("https://mirror.example.com/", "https://old.example.com")
        )
        assertEquals(
            listOf(
                "https://www.javbus.com",
                "https://mirror.example.com",
                "https://proxy.example.com",
                "https://old.example.com"
            ),
            seeds
        )
    }

    // endregion

    // region filterScannedForPersistence

    @Test
    fun `persistence filter drops preset and custom urls from discovered set`() {
        val persisted = filterScannedForPersistence(
            discovered = setOf(
                "https://www.javbus.com",
                "https://proxy.example.com",
                "https://found.example.com",
                "https://found2.example.com/"
            ),
            presetUrl = "https://www.javbus.com/",
            customUrls = listOf("https://proxy.example.com")
        )
        assertEquals(setOf("https://found.example.com", "https://found2.example.com"), persisted)
    }

    // endregion
}

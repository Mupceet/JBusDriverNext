package me.jbusdriver.modern.core.site

import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.async
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.withTimeout
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
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

    @Test
    fun awaitReadyWaitsForPersistedBaseUrl() = runTest {
        val persisted = CompletableDeferred<String>()
        val config = DefaultSiteConfig(
            preferenceSource = object : SitePreferenceSource {
                override suspend fun currentSelectedBaseUrl(): String = persisted.await()
            },
            scope = backgroundScope
        )

        val ready = async {
            config.awaitReady()
            config.baseUrl
        }

        kotlinx.coroutines.delay(50)
        assertFalse(ready.isCompleted)

        persisted.complete("https://mirror.example/")

        assertEquals("https://mirror.example", withTimeout(1_000) { ready.await() })
    }

    @Test
    fun awaitReadyFallsBackToDefaultWhenPreferenceReadFails() = runTest {
        val config = DefaultSiteConfig(
            preferenceSource = object : SitePreferenceSource {
                override suspend fun currentSelectedBaseUrl(): String = error("DataStore unavailable")
            },
            scope = backgroundScope
        )

        config.awaitReady()

        assertEquals(DEFAULT_SITE_URL, config.baseUrl)
    }

    @Test
    fun baseUrlNormalizesSchemeAndHostOnly() {
        assertEquals(
            "https://www.javbus.com/CaseSensitivePath",
            normalizeBaseUrl("HTTPS://WWW.JavBus.COM/CaseSensitivePath/")
        )
    }

    @Test
    fun normalizeBaseUrlKeepsExplicitPort() {
        assertEquals("https://host.com:8080", normalizeBaseUrl("https://host.com:8080/"))
    }

    @Test
    fun normalizeBaseUrlFallsBackToTrimmedWhenNoScheme() {
        assertEquals("not-a-url", normalizeBaseUrl("  not-a-url  "))
    }

    @Test
    fun normalizeBaseUrlTrimsWhitespace() {
        assertEquals("https://host.com", normalizeBaseUrl("  https://host.com  "))
    }

    @Test
    fun baseUrlSetterNormalizesAssignedValue() = runTest {
        val config = DefaultSiteConfig(
            preferenceSource = object : SitePreferenceSource {
                override suspend fun currentSelectedBaseUrl(): String = DEFAULT_SITE_URL
            },
            scope = backgroundScope
        )
        config.awaitReady()

        config.baseUrl = "HTTPS://Mirror.Example/Path/"

        assertEquals("https://mirror.example/Path", config.baseUrl)
    }

    @Test
    fun updateBaseUrlNormalizesValue() = runTest {
        val config = DefaultSiteConfig(
            preferenceSource = object : SitePreferenceSource {
                override suspend fun currentSelectedBaseUrl(): String = DEFAULT_SITE_URL
            },
            scope = backgroundScope
        )
        config.awaitReady()

        config.updateBaseUrl("https://mirror.example/")

        assertEquals("https://mirror.example", config.baseUrl)
    }

    @Test
    fun resolveUsesCurrentBaseUrl() = runTest {
        val config = DefaultSiteConfig(
            preferenceSource = object : SitePreferenceSource {
                override suspend fun currentSelectedBaseUrl(): String = "https://mirror.example/"
            },
            scope = backgroundScope
        )
        config.awaitReady()

        assertEquals("https://mirror.example/genre/1", config.resolve("/genre/1"))
    }

    @Test
    fun refererAppendsTrailingSlash() = runTest {
        val config = DefaultSiteConfig(
            preferenceSource = object : SitePreferenceSource {
                override suspend fun currentSelectedBaseUrl(): String = "https://mirror.example"
            },
            scope = backgroundScope
        )
        config.awaitReady()

        assertEquals("https://mirror.example/", config.referer())
    }

    @Test
    fun blankPersistedBaseUrlFallsBackToDefault() = runTest {
        val config = DefaultSiteConfig(
            preferenceSource = object : SitePreferenceSource {
                override suspend fun currentSelectedBaseUrl(): String = "   "
            },
            scope = backgroundScope
        )
        config.awaitReady()

        assertEquals(DEFAULT_SITE_URL, config.baseUrl)
    }
}

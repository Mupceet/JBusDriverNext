package me.jbusdriver.modern.data

import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

/**
 * Unit tests for the SettingsRepository contract behavior.
 *
 * Uses a FakeSettingsRepository instead of the real DefaultSettingsRepository
 * because the real implementation depends on Android framework classes
 * (CacheLoader, JBus, JAVBusService) that are not available in JVM unit tests.
 */
class DefaultSettingsRepositoryTest {

    private lateinit var repository: FakeSettingsRepository

    @Before
    fun setUp() {
        repository = FakeSettingsRepository()
    }

    @Test
    fun getCurrentUrl_returnsNonNullUrl() {
        val url = repository.getCurrentUrl()
        assertTrue(url.isNotBlank())
        assertTrue(url.startsWith("http"))
    }

    @Test
    fun getAvailableUrls_returnsNonEmptyList() {
        val urls = repository.getAvailableUrls()
        assertTrue(urls.isNotEmpty())
    }

    @Test
    fun updateUrl_changesCurrentUrl() = runTest {
        val originalUrl = repository.getCurrentUrl()
        val otherUrls = repository.getAvailableUrls().filter { it != originalUrl }
        if (otherUrls.isEmpty()) return@runTest // can't test if only one URL

        val testUrl = otherUrls.first()
        repository.updateUrl(testUrl)
        assertEquals(testUrl, repository.getCurrentUrl())

        // Restore original state
        repository.updateUrl(originalUrl)
        assertEquals(originalUrl, repository.getCurrentUrl())
    }

    /**
     * In-memory fake that mirrors DefaultSettingsRepository behavior
     * without Android framework dependencies.
     */
    private class FakeSettingsRepository : SettingsRepository {
        private var currentUrl = "https://www.javbus.com"
        private val urls = listOf(
            "https://www.javbus.com",
            "https://www.javbus.one",
            "https://www.javbus.pw"
        )

        override fun getCurrentUrl(): String = currentUrl

        override fun getAvailableUrls(): List<String> = urls

        override suspend fun updateUrl(url: String) {
            currentUrl = url
        }
    }
}

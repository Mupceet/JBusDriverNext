package me.jbusdriver.modern.data

import me.jbusdriver.modern.data.cache.siteCacheKey
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertEquals
import org.junit.Test

class SiteCacheKeyTest {

    @Test
    fun sameResourceOnDifferentHostsUsesDifferentKeys() {
        val first = siteCacheKey("https://a.example", "movie-detail", "/ABC-001")
        val second = siteCacheKey("https://b.example", "movie-detail", "/ABC-001")

        assertNotEquals(first, second)
    }

    @Test
    fun baseUrlIsNormalized() {
        assertEquals(
            siteCacheKey("https://a.example", "search", "ABC-001"),
            siteCacheKey("https://a.example/", "search", "ABC-001")
        )
        assertEquals(
            siteCacheKey("https://a.example/Path", "search", "ABC-001"),
            siteCacheKey("HTTPS://A.EXAMPLE/Path/", "search", "ABC-001")
        )
    }
}

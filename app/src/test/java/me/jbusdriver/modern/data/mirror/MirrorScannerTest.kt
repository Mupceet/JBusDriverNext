package me.jbusdriver.modern.data.mirror

import org.junit.Assert.assertEquals
import org.junit.Test

class MirrorScannerTest {
    @Test
    fun `sort mirror urls keeps default host first even when slower`() {
        val sorted = sortMirrorUrls(
            listOf(
                MirrorUrl("https://fast.example.test", isReachable = true, latencyMs = 10),
                MirrorUrl("https://www.javbus.com", isReachable = true, latencyMs = 500),
                MirrorUrl("https://slow.example.test", isReachable = true, latencyMs = 100)
            )
        )

        assertEquals("https://www.javbus.com", sorted.first().url)
    }

    @Test
    fun `sort mirror urls orders reachable by latency before unreachable`() {
        val sorted = sortMirrorUrls(
            listOf(
                MirrorUrl("https://unreachable.example.test", isReachable = false, latencyMs = -1),
                MirrorUrl("https://slow.example.test", isReachable = true, latencyMs = 200),
                MirrorUrl("https://fast.example.test", isReachable = true, latencyMs = 20)
            )
        )

        assertEquals(
            listOf(
                "https://fast.example.test",
                "https://slow.example.test",
                "https://unreachable.example.test"
            ),
            sorted.map { it.url }
        )
    }
}

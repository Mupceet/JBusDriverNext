package me.jbusdriver.modern.data.gateway

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test

class ImageMediaGatewayTest {
    @Test
    fun `pending media entry is published after successful write`() {
        val calls = mutableListOf<String>()

        writePendingMediaEntry(
            entry = "uri",
            write = {
                calls += "write:$it"
                true
            },
            publish = { calls += "publish:$it" },
            cleanup = { calls += "cleanup:$it" }
        )

        assertEquals(listOf("write:uri", "publish:uri"), calls)
    }

    @Test
    fun `pending media entry is cleaned up when write throws`() {
        val calls = mutableListOf<String>()

        assertThrows(IllegalStateException::class.java) {
            writePendingMediaEntry(
                entry = "uri",
                write = {
                    calls += "write:$it"
                    throw IllegalStateException("disk full")
                },
                publish = { calls += "publish:$it" },
                cleanup = { calls += "cleanup:$it" }
            )
        }

        assertEquals(listOf("write:uri", "cleanup:uri"), calls)
    }

    @Test
    fun `pending media entry treats false write result as failure and cleans up`() {
        var cleaned = false

        assertThrows(IllegalStateException::class.java) {
            writePendingMediaEntry(
                entry = "uri",
                write = { false },
                publish = { error("must not publish") },
                cleanup = { cleaned = true }
            )
        }

        assertTrue(cleaned)
    }

    @Test
    fun `pending media entry does not cleanup after publish succeeds`() {
        var cleaned = false

        writePendingMediaEntry(
            entry = "uri",
            write = { true },
            publish = {},
            cleanup = { cleaned = true }
        )

        assertFalse(cleaned)
    }
}

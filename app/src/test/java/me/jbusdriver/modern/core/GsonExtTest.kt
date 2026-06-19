package me.jbusdriver.modern.core

import androidx.annotation.Keep
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.Date

class GsonExtTest {
    @Keep
    data class JsonFixture(
        val count: Int?,
        val createdAt: Date?,
        val names: List<String>,
        val tags: Set<String>,
        val attrs: Map<String, String>
    )

    @Test
    fun `fromJson supports reified generic list types`() {
        val values = GSON.fromJson<List<String>>("""["a","b"]""")

        assertEquals(listOf("a", "b"), values)
    }

    @Test
    fun `invalid json returns null from extension`() {
        assertNull(GSON.fromJson<JsonFixture>("{"))
    }

    @Test
    fun `int adapter treats empty null and invalid numbers as null`() {
        assertNull(GSON.fromJson<JsonFixture>("""{"count":"","createdAt":0}""")?.count)
        assertNull(GSON.fromJson<JsonFixture>("""{"count":null,"createdAt":0}""")?.count)
        assertNull(GSON.fromJson<JsonFixture>("""{"count":"nan","createdAt":0}""")?.count)
        assertEquals(42, GSON.fromJson<JsonFixture>("""{"count":42,"createdAt":0}""")?.count)
    }

    @Test
    fun `date adapter parses epoch millis and iso timestamps`() {
        assertEquals(Date(1234L), GSON.fromJson<JsonFixture>("""{"createdAt":1234}""")?.createdAt)
        assertEquals(Date(0L), GSON.fromJson<JsonFixture>("""{"createdAt":"1970-01-01T00:00:00Z"}""")?.createdAt)
    }

    @Test
    fun `date adapter falls back to a current date for invalid input`() {
        val before = System.currentTimeMillis()
        val parsed = GSON.fromJson<JsonFixture>("""{"createdAt":"not-a-date"}""")?.createdAt
        val after = System.currentTimeMillis()

        assertNotNull(parsed)
        assertTrue(parsed!!.time in before..after)
    }

    @Test
    fun `null safe factory fills missing non-null collections`() {
        val parsed = GSON.fromJson<JsonFixture>("""{"count":1,"createdAt":0}""")

        assertNotNull(parsed)
        assertEquals(emptyList<String>(), parsed!!.names)
        assertEquals(emptySet<String>(), parsed.tags)
        assertEquals(emptyMap<String, String>(), parsed.attrs)
    }

    @Test
    fun `to json string serializes null values`() {
        assertTrue(mapOf("a" to null).toJsonString().contains(""""a":null"""))
    }
}

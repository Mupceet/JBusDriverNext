package me.jbusdriver.modern.core.cache

import me.jbusdriver.modern.core.GSON
import me.jbusdriver.modern.core.fromJson
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class CacheStoreMetadataTest {
    data class Sample(val title: String)

    private class FakeCacheStore : CacheStore {
        val memory = linkedMapOf<String, String>()
        val disk = linkedMapOf<String, String>()

        override fun readMemory(key: String): String? = memory[key]
        override fun writeMemory(key: String, value: String) { memory[key] = value }
        override suspend fun readDisk(key: String): String? = disk[key]
        override suspend fun writeDisk(key: String, value: String) { disk[key] = value }
    }

    @Test
    fun writeCachedWritesEnvelopeToMemoryAndDisk() = runTest {
        val store = FakeCacheStore()

        val entry = store.writeCached("sample", Sample("fresh"), nowMillis = { 1_000L })

        assertEquals(CacheSource.Network, entry.source)
        assertEquals(1_000L, entry.storedAtMillis)
        assertFalse(entry.isExpired)
        assertEquals(Sample("fresh"), entry.value)

        val memoryEnvelope = GSON.fromJson<CacheEnvelope>(store.memory.getValue("sample"))
        val diskEnvelope = GSON.fromJson<CacheEnvelope>(store.disk.getValue("sample"))
        assertEquals(1_000L, memoryEnvelope?.storedAtMillis)
        assertEquals(1_000L, diskEnvelope?.storedAtMillis)
        assertEquals(Sample("fresh"), GSON.fromJson<Sample>(memoryEnvelope?.payloadJson.orEmpty()))
        assertEquals(Sample("fresh"), GSON.fromJson<Sample>(diskEnvelope?.payloadJson.orEmpty()))
    }

    @Test
    fun readCachedReturnsDiskEntryThenMemoryEntryAfterBackfill() = runTest {
        val store = FakeCacheStore()
        val diskJson = GSON.toJson(CacheEnvelope(1_000L, GSON.toJson(Sample("disk"))))
        store.disk["sample"] = diskJson

        val diskEntry = store.readCached<Sample>("sample", ttlMillis = 10_000L, nowMillis = { 1_500L })

        assertEquals(Sample("disk"), diskEntry?.value)
        assertEquals(CacheSource.Disk, diskEntry?.source)
        assertFalse(diskEntry?.isExpired ?: true)
        assertEquals(diskJson, store.memory["sample"])

        val memoryEntry = store.readCached<Sample>("sample", ttlMillis = 10_000L, nowMillis = { 2_000L })

        assertEquals(Sample("disk"), memoryEntry?.value)
        assertEquals(CacheSource.Memory, memoryEntry?.source)
        assertFalse(memoryEntry?.isExpired ?: true)
    }

    @Test
    fun readCachedMarksExpiredEntriesUsingTtlMillis() = runTest {
        val store = FakeCacheStore()
        store.memory["sample"] = GSON.toJson(CacheEnvelope(1_000L, GSON.toJson(Sample("old"))))

        val entry = store.readCached<Sample>("sample", ttlMillis = 500L, nowMillis = { 1_500L })

        assertEquals(Sample("old"), entry?.value)
        assertEquals(1_000L, entry?.storedAtMillis)
        assertTrue(entry?.isExpired ?: false)
    }

    @Test
    fun readCachedSupportsLegacyPlainJsonAsExpiredCache() = runTest {
        val store = FakeCacheStore()
        store.memory["sample"] = GSON.toJson(Sample("legacy"))

        val entry = store.readCached<Sample>("sample", ttlMillis = 10_000L, nowMillis = { 1_500L })

        assertEquals(Sample("legacy"), entry?.value)
        assertEquals(CacheSource.Memory, entry?.source)
        assertEquals(0L, entry?.storedAtMillis)
        assertTrue(entry?.isExpired ?: false)
    }

    @Test
    fun readCachedReturnsNullForMalformedEnvelopePayload() = runTest {
        val store = FakeCacheStore()
        store.memory["sample"] = GSON.toJson(CacheEnvelope(1_000L, "{not valid json"))

        val entry = store.readCached<Sample>("sample", ttlMillis = 10_000L, nowMillis = { 1_500L })

        assertNull(entry)
    }
}

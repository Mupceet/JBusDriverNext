package me.jbusdriver.modern.core.cache

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.test.runTest
import me.jbusdriver.modern.core.GSON
import me.jbusdriver.modern.core.fromJson
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.IOException

/**
 * 覆盖 [CacheStore] 顶层缓存策略扩展函数（[lruCached]/[persistentCached]/[observeCached]/
 * [firstCachedOrFresh] 等）的分支，使用内存版 [FakeCacheStore] 隔离磁盘与 Android 框架。
 */
class CacheStoreFlowTest {

    data class Sample(val name: String)

    private class FakeCacheStore : CacheStore {
        val memory = linkedMapOf<String, String>()
        val disk = linkedMapOf<String, String>()
        override fun readMemory(key: String): String? = memory[key]
        override fun writeMemory(key: String, value: String) {
            memory[key] = value
        }

        override suspend fun readDisk(key: String): String? = disk[key]
        override suspend fun writeDisk(key: String, value: String) {
            disk[key] = value
        }
    }

    // ---------- lruCached ----------

    @Test
    fun lruCached_returnsMemoryValueWithoutFetching() = runTest {
        val store = FakeCacheStore()
        store.writeMemory("k", GSON.toJson(Sample("mem")))
        var fetched = false
        val result = store.lruCached<Sample>("k") { fetched = true; Sample("fresh") }
        assertEquals(Sample("mem"), result)
        assertFalse(fetched)
    }

    @Test
    fun lruCached_forceRefreshBypassesMemoryAndFetches() = runTest {
        val store = FakeCacheStore()
        store.writeMemory("k", GSON.toJson(Sample("mem")))
        val result = store.lruCached<Sample>("k", forceRefresh = true) { Sample("fresh") }
        assertEquals(Sample("fresh"), result)
        assertEquals(Sample("fresh"), GSON.fromJson<Sample>(store.memory.getValue("k")))
    }

    @Test
    fun lruCached_fetchesAndWritesMemoryOnMiss() = runTest {
        val store = FakeCacheStore()
        val result = store.lruCached<Sample>("k") { Sample("fresh") }
        assertEquals(Sample("fresh"), result)
        assertEquals(Sample("fresh"), GSON.fromJson<Sample>(store.memory.getValue("k")))
    }

    // ---------- persistentCached ----------

    @Test
    fun persistentCached_returnsMemoryValueWhenPresent() = runTest {
        val store = FakeCacheStore()
        store.writeMemory("k", GSON.toJson(Sample("mem")))
        var fetched = false
        val result = store.persistentCached<Sample>("k") { fetched = true; Sample("fresh") }
        assertEquals(Sample("mem"), result)
        assertFalse(fetched)
    }

    @Test
    fun persistentCached_backfillsMemoryFromDisk() = runTest {
        val store = FakeCacheStore()
        store.writeDisk("k", GSON.toJson(Sample("disk")))
        val result = store.persistentCached<Sample>("k") { Sample("fresh") }
        assertEquals(Sample("disk"), result)
        assertEquals(GSON.toJson(Sample("disk")), store.memory.getValue("k"))
    }

    @Test
    fun persistentCached_forceRefreshBypassesBothLayers() = runTest {
        val store = FakeCacheStore()
        store.writeMemory("k", GSON.toJson(Sample("mem")))
        store.writeDisk("k", GSON.toJson(Sample("disk")))
        val result = store.persistentCached<Sample>("k", forceRefresh = true) { Sample("fresh") }
        assertEquals(Sample("fresh"), result)
    }

    @Test
    fun persistentCached_fetchesAndWritesBothLayersOnMiss() = runTest {
        val store = FakeCacheStore()
        val result = store.persistentCached<Sample>("k") { Sample("fresh") }
        assertEquals(Sample("fresh"), result)
        assertEquals(Sample("fresh"), GSON.fromJson<Sample>(store.memory.getValue("k")))
        assertEquals(Sample("fresh"), GSON.fromJson<Sample>(store.disk.getValue("k")))
    }

    // ---------- readCached / writeCached (disk variants) ----------

    @Test
    fun writeCached_diskFalseSkipsDiskWrite() = runTest {
        val store = FakeCacheStore()
        store.writeCached("k", Sample("v"), disk = false, nowMillis = { 1_000L })
        assertNotNull(store.memory["k"])
        assertNull(store.disk["k"])
    }

    @Test
    fun readCached_diskFalseIgnoresDiskLayer() = runTest {
        val store = FakeCacheStore()
        store.writeDisk("k", GSON.toJson(CacheEnvelope(1_000L, GSON.toJson(Sample("d")))))
        val entry = store.readCached<Sample>("k", ttlMillis = 10_000L, disk = false, nowMillis = { 1_500L })
        assertNull(entry)
    }

    // ---------- observeCached ----------

    @Test
    fun observeCached_forceRefreshSkipsCacheAndEmitsFresh() = runTest {
        val store = FakeCacheStore()
        store.writeCached("k", Sample("cached"), nowMillis = { 500L })
        val events = store.observeCached<Sample>(
            key = "k",
            ttlMillis = 10_000L,
            forceRefresh = true,
            nowMillis = { 1_000L }
        ) { Sample("fresh") }.toList()
        assertEquals(1, events.size)
        val fresh = events.single() as CachedLoadEvent.Fresh
        assertEquals(Sample("fresh"), fresh.entry.value)
    }

    @Test
    fun observeCached_emitsCachedThenFreshWhenRevalidating() = runTest {
        val store = FakeCacheStore()
        store.writeCached("k", Sample("cached"), nowMillis = { 500L })
        val events = store.observeCached<Sample>("k", ttlMillis = 10_000L, nowMillis = { 1_000L }) {
            Sample("fresh")
        }.toList()
        assertEquals(2, events.size)
        assertEquals(Sample("cached"), (events[0] as CachedLoadEvent.Cached).entry.value)
        assertEquals(Sample("fresh"), (events[1] as CachedLoadEvent.Fresh).entry.value)
        assertNotNull(store.memory["k"])
    }

    @Test
    fun observeCached_retriesOnceWhenResultNotCacheable() = runTest {
        val store = FakeCacheStore()
        var calls = 0
        val events = store.observeCached<Sample>(
            key = "k",
            ttlMillis = 10_000L,
            nowMillis = { 1_000L },
            isCacheable = { it.name.isNotEmpty() }
        ) {
            calls++
            if (calls == 1) Sample("") else Sample("ok")
        }.toList()
        assertEquals(1, events.size)
        assertEquals(Sample("ok"), (events.single() as CachedLoadEvent.Fresh).entry.value)
        assertEquals(2, calls)
        assertNotNull(store.memory["k"])
    }

    @Test
    fun observeCached_keepsCacheWhenDegradedResultAndHasCache() = runTest {
        val store = FakeCacheStore()
        store.writeCached("k", Sample("cached"), nowMillis = { 500L })
        val events = store.observeCached<Sample>(
            key = "k",
            ttlMillis = 10_000L,
            nowMillis = { 1_000L },
            isCacheable = { it.name.isNotEmpty() }
        ) { Sample("") }.toList()
        // 退化结果 + 已有缓存：只发射 Cached，保留原缓存不被毒化
        assertEquals(1, events.size)
        assertTrue(events.single() is CachedLoadEvent.Cached)
        assertTrue(store.memory.getValue("k").contains("cached"))
    }

    @Test
    fun observeCached_emitsEphemeralWhenDegradedResultAndNoCache() = runTest {
        val store = FakeCacheStore()
        val events = store.observeCached<Sample>(
            key = "k",
            ttlMillis = 10_000L,
            nowMillis = { 1_000L },
            isCacheable = { it.name.isNotEmpty() }
        ) { Sample("") }.toList()
        assertEquals(1, events.size)
        val fresh = events.single() as CachedLoadEvent.Fresh
        assertEquals(Sample(""), fresh.entry.value)
        assertNull(store.memory["k"])
        assertNull(store.disk["k"])
    }

    @Test
    fun observeCached_emitsFailureWithCachedValueWhenFetchThrows() = runTest {
        val store = FakeCacheStore()
        store.writeCached("k", Sample("cached"), nowMillis = { 500L })
        val events = store.observeCached<Sample>("k", ttlMillis = 10_000L, nowMillis = { 1_000L }) {
            throw IOException("down")
        }.toList()
        assertEquals(2, events.size)
        assertTrue(events[0] is CachedLoadEvent.Cached)
        val failure = events[1] as CachedLoadEvent.Failure
        assertEquals("down", failure.throwable.message)
        assertTrue(failure.hadCachedValue)
    }

    @Test
    fun observeCached_emitsFailureWithoutCachedValueWhenNoCacheAndFetchThrows() = runTest {
        val store = FakeCacheStore()
        val events = store.observeCached<Sample>("k", ttlMillis = 10_000L, nowMillis = { 1_000L }) {
            throw IOException("down")
        }.toList()
        assertEquals(1, events.size)
        val failure = events.single() as CachedLoadEvent.Failure
        assertEquals("down", failure.throwable.message)
        assertFalse(failure.hadCachedValue)
    }

    @Test
    fun observeCached_reraisesCancellationException() = runTest {
        val store = FakeCacheStore()
        var thrown: Throwable? = null
        try {
            store.observeCached<Sample>("k", ttlMillis = 10_000L) {
                throw CancellationException("cancel")
            }.collect { }
        } catch (e: Throwable) {
            thrown = e
        }
        assertTrue("expected CancellationException, got $thrown", thrown is CancellationException)
    }

    @Test
    fun observeCached_reraisesCancellationExceptionFromDegradedRetry() = runTest {
        val store = FakeCacheStore()
        var calls = 0
        var thrown: Throwable? = null

        try {
            store.observeCached(
                key = "k",
                ttlMillis = 10_000L,
                isCacheable = { it: Sample -> it.name.isNotEmpty() }
            ) {
                calls += 1
                if (calls == 1) Sample("") else throw CancellationException("cancel retry")
            }.toList()
        } catch (e: Throwable) {
            thrown = e
        }

        assertEquals(2, calls)
        assertTrue("expected CancellationException, got $thrown", thrown is CancellationException)
    }

    // ---------- firstCachedOrFresh ----------

    @Test
    fun firstCachedOrFresh_returnsNonExpiredCachedValue() = runTest {
        val events = flowOf<CachedLoadEvent<Sample>>(
            CachedLoadEvent.Cached(CacheEntry(Sample("c"), 1_000L, CacheSource.Memory, isExpired = false))
        )
        assertEquals(Sample("c"), events.firstCachedOrFresh())
    }

    @Test
    fun firstCachedOrFresh_returnsFreshValueWhenNoCached() = runTest {
        val events = flowOf<CachedLoadEvent<Sample>>(
            CachedLoadEvent.Fresh(CacheEntry(Sample("f"), 1_000L, CacheSource.Network, isExpired = false))
        )
        assertEquals(Sample("f"), events.firstCachedOrFresh())
    }

    @Test
    fun firstCachedOrFresh_returnsExpiredCachedValueOnFailure() = runTest {
        val events = flow<CachedLoadEvent<Sample>> {
            emit(CachedLoadEvent.Cached(CacheEntry(Sample("old"), 0L, CacheSource.Memory, isExpired = true)))
            emit(CachedLoadEvent.Failure(IOException("down"), hadCachedValue = true))
        }
        assertEquals(Sample("old"), events.firstCachedOrFresh())
    }

    @Test
    fun firstCachedOrFresh_throwsWhenFailureAndNoCachedValue() = runTest {
        val events = flow<CachedLoadEvent<Sample>> {
            emit(CachedLoadEvent.Failure(IOException("down"), hadCachedValue = false))
        }
        var thrown: Throwable? = null
        try {
            events.firstCachedOrFresh()
        } catch (e: Throwable) {
            thrown = e
        }
        assertTrue(thrown is IOException)
    }

    // ---------- ephemeralEntry ----------

    @Test
    fun ephemeralEntry_buildsNonExpiredNetworkEntry() {
        val entry = ephemeralEntry(Sample("x"), 1_000L)
        assertEquals(Sample("x"), entry.value)
        assertEquals(1_000L, entry.storedAtMillis)
        assertEquals(CacheSource.Network, entry.source)
        assertFalse(entry.isExpired)
    }
}

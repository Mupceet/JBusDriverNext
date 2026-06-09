package me.jbusdriver.modern.core.cache

import me.jbusdriver.modern.core.CacheLoader
import me.jbusdriver.modern.core.GSON
import me.jbusdriver.modern.core.fromJson
import javax.inject.Inject
import javax.inject.Singleton

interface CacheStore {
    fun readMemory(key: String): String?
    fun writeMemory(key: String, value: String)
    suspend fun readDisk(key: String): String?
    suspend fun writeDisk(key: String, value: String)
}

@Singleton
class DefaultCacheStore @Inject constructor() : CacheStore {
    override fun readMemory(key: String): String? = CacheLoader.getLruString(key)

    override fun writeMemory(key: String, value: String) {
        CacheLoader.putLruString(key, value)
    }

    override suspend fun readDisk(key: String): String? = CacheLoader.getDiskString(key)

    override suspend fun writeDisk(key: String, value: String) {
        CacheLoader.putDiskString(key, value)
    }
}

suspend inline fun <reified T> CacheStore.lruCached(
    key: String,
    forceRefresh: Boolean = false,
    crossinline fetch: suspend () -> T
): T {
    if (!forceRefresh) {
        readMemory(key)?.let { json ->
            GSON.fromJson<T>(json)?.let { return it }
        }
    }
    val result = fetch()
    writeMemory(key, GSON.toJson(result))
    return result
}

suspend inline fun <reified T> CacheStore.persistentCached(
    key: String,
    forceRefresh: Boolean = false,
    crossinline fetch: suspend () -> T
): T {
    if (!forceRefresh) {
        readMemory(key)?.let { json ->
            GSON.fromJson<T>(json)?.let { return it }
        }
        readDisk(key)?.let { json ->
            GSON.fromJson<T>(json)?.let { cached ->
                writeMemory(key, GSON.toJson(cached))
                return cached
            }
        }
    }
    val result = fetch()
    val json = GSON.toJson(result)
    writeMemory(key, json)
    writeDisk(key, json)
    return result
}

suspend inline fun <reified T> CacheStore.readCached(
    key: String,
    ttlMillis: Long,
    disk: Boolean = true,
    noinline nowMillis: () -> Long = { System.currentTimeMillis() }
): CacheEntry<T>? {
    readMemory(key)?.let { json ->
        parseCacheEntry<T>(json, CacheSource.Memory, ttlMillis, nowMillis)?.let { return it }
    }

    if (disk) {
        readDisk(key)?.let { json ->
            parseCacheEntry<T>(json, CacheSource.Disk, ttlMillis, nowMillis)?.let { entry ->
                writeMemory(key, json)
                return entry
            }
        }
    }

    return null
}

suspend inline fun <reified T> CacheStore.writeCached(
    key: String,
    value: T,
    disk: Boolean = true,
    noinline nowMillis: () -> Long = { System.currentTimeMillis() }
): CacheEntry<T> {
    val storedAtMillis = nowMillis()
    val json = GSON.toJson(CacheEnvelope(storedAtMillis, GSON.toJson(value)))
    writeMemory(key, json)
    if (disk) {
        writeDisk(key, json)
    }
    return CacheEntry(
        value = value,
        storedAtMillis = storedAtMillis,
        source = CacheSource.Network,
        isExpired = false
    )
}

@PublishedApi
internal inline fun <reified T> parseCacheEntry(
    json: String,
    source: CacheSource,
    ttlMillis: Long,
    noinline nowMillis: () -> Long
): CacheEntry<T>? {
    val envelope = runCatching { GSON.fromJson<CacheEnvelope>(json) }.getOrNull()
    val payloadJson = envelope?.payloadJson
    if (!payloadJson.isNullOrBlank()) {
        val payload = runCatching { GSON.fromJson<T>(payloadJson) }.getOrNull() ?: return null
        return CacheEntry(
            value = payload,
            storedAtMillis = envelope.storedAtMillis,
            source = source,
            isExpired = nowMillis() - envelope.storedAtMillis >= ttlMillis
        )
    }

    val legacy = runCatching { GSON.fromJson<T>(json) }.getOrNull() ?: return null
    return CacheEntry(
        value = legacy,
        storedAtMillis = 0L,
        source = source,
        isExpired = true
    )
}

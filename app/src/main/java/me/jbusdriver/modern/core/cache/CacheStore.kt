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

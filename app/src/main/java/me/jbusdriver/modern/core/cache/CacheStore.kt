package me.jbusdriver.modern.core.cache

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flow
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

/**
 * Stale-while-revalidate 缓存策略的核心实现。
 *
 * 先尝试读取缓存并立即发射 [CachedLoadEvent.Cached]，然后在后台发起网络请求，
 * 成功后发射 [CachedLoadEvent.Fresh]，失败时发射 [CachedLoadEvent.Failure]。
 *
 * @param key 缓存键
 * @param ttlMillis 缓存存活时间（毫秒）
 * @param disk 是否使用磁盘缓存
 * @param forceRefresh 强制跳过缓存直接网络请求
 * @param revalidate 即使缓存未过期也发起后台刷新
 * @param nowMillis 当前时间戳提供者（用于测试注入）
 * @param fetch 网络数据获取逻辑
 */
inline fun <reified T> CacheStore.observeCached(
    key: String,
    ttlMillis: Long,
    disk: Boolean = true,
    forceRefresh: Boolean = false,
    revalidate: Boolean = true,
    noinline nowMillis: () -> Long = { System.currentTimeMillis() },
    crossinline fetch: suspend () -> T
): Flow<CachedLoadEvent<T>> = flow {
    var emittedCache = false
    val cached = if (forceRefresh) {
        null
    } else {
        readCached<T>(key, ttlMillis, disk, nowMillis)
    }

    if (cached != null) {
        emittedCache = true
        emit(CachedLoadEvent.Cached(cached))
    }

    val shouldFetch = forceRefresh || cached == null || cached.isExpired || revalidate
    if (!shouldFetch) return@flow

    try {
        val fresh = fetch()
        val entry = writeCached(key, fresh, disk, nowMillis)
        emit(CachedLoadEvent.Fresh(entry))
    } catch (throwable: Throwable) {
        if (throwable is CancellationException) throw throwable
        emit(CachedLoadEvent.Failure(throwable, emittedCache))
    }
}

/**
 * 从 [CachedLoadEvent] Flow 中获取最终值。
 *
 * 等待第一个有效结果：非过期的缓存值，或网络返回的新鲜/失败值。
 * 如果网络失败但有缓存值（即使过期），返回缓存的值。
 */
suspend fun <T> Flow<CachedLoadEvent<T>>.firstCachedOrFresh(): T {
    var expiredCached: CacheEntry<T>? = null
    return when (val event = first { event ->
        when (event) {
            is CachedLoadEvent.Cached -> {
                if (event.entry.isExpired) {
                    expiredCached = event.entry
                    false
                } else {
                    true
                }
            }
            is CachedLoadEvent.Fresh,
            is CachedLoadEvent.Failure -> true
        }
    }) {
        is CachedLoadEvent.Cached -> event.entry.value
        is CachedLoadEvent.Fresh -> event.entry.value
        is CachedLoadEvent.Failure -> expiredCached?.value ?: throw event.throwable
    }
}
